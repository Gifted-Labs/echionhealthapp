package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.vault.TerminologySearchRequest;
import com.giftedlabs.echoinhealthbackend.dto.vault.TerminologySearchResponse;
import com.giftedlabs.echoinhealthbackend.entity.AiGenerationEvent;
import com.giftedlabs.echoinhealthbackend.entity.AiGenerationStatus;
import com.giftedlabs.echoinhealthbackend.entity.AiRequestType;
import com.giftedlabs.echoinhealthbackend.entity.Organization;
import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.exception.AiGenerationUnavailableException;
import com.giftedlabs.echoinhealthbackend.exception.AiProviderException;
import com.giftedlabs.echoinhealthbackend.exception.SubscriptionLimitExceededException;
import com.giftedlabs.echoinhealthbackend.repository.AiGenerationEventRepository;
import com.giftedlabs.echoinhealthbackend.service.ai.AiPromptSanitizer;
import com.giftedlabs.echoinhealthbackend.service.ai.AiPromptTemplateService;
import com.giftedlabs.echoinhealthbackend.service.ai.AiProviderRouter;
import com.giftedlabs.echoinhealthbackend.service.ai.AiProviderType;
import com.giftedlabs.echoinhealthbackend.service.ai.AiReportProvider;
import com.giftedlabs.echoinhealthbackend.service.ai.AiTerminologyResult;
import com.giftedlabs.echoinhealthbackend.service.ai.AiUsageCostEstimator;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Terminology lookup is the one AI feature a clinician will call dozens of times an hour, so the
 * properties under test here are mostly economic and safety properties rather than functional
 * ones: a repeated term must not keep costing credits, a failed lookup must give the credit back,
 * and a query that names nothing real must come back empty rather than be retried until some
 * provider invents an answer.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TerminologySearchServiceTest {

    private static final String ORG_ID = "org-1";

    @Mock private BillingService billingService;
    @Mock private AuditService auditService;
    @Mock private AiProviderRouter aiProviderRouter;
    @Mock private AiPromptTemplateService promptTemplateService;
    @Mock private AiUsageCostEstimator aiUsageCostEstimator;
    @Mock private AiGenerationEventRepository aiGenerationEventRepository;
    @Mock private AiReportProvider primaryProvider;
    @Mock private AiReportProvider fallbackProvider;

    private TerminologySearchService service;
    private User user;

    @BeforeEach
    void setUp() {
        CacheManager cacheManager = new CaffeineCacheManager() {{
            setCaffeine(Caffeine.newBuilder().maximumSize(100));
            registerCustomCache(TerminologySearchService.CACHE_NAME,
                    Caffeine.newBuilder().maximumSize(100).build());
        }};

        service = new TerminologySearchService(
                billingService, auditService, aiProviderRouter, promptTemplateService,
                new AiPromptSanitizer(), aiUsageCostEstimator, aiGenerationEventRepository,
                cacheManager);
        ReflectionTestUtils.setField(service, "creditCost", 1);

        Organization organization = Organization.builder().id(ORG_ID).name("St Jude").build();
        user = User.builder().id("user-1").email("sono@stjude.test").organization(organization).build();
        ReflectionTestUtils.setField(user, "organization", organization);

        when(promptTemplateService.terminologyPrompt(anyString(), any(), anyInt())).thenReturn("PROMPT");
        when(promptTemplateService.promptVersion()).thenReturn("v2");
        when(aiProviderRouter.primaryProvider(any())).thenReturn(primaryProvider);
        when(primaryProvider.providerType()).thenReturn(AiProviderType.GEMINI);
    }

    @Test
    @DisplayName("a lookup returns the model's matches and charges one credit")
    void lookupReturnsMatchesAndCharges() {
        when(primaryProvider.searchTerminology(any())).thenReturn(result(
                match("Cholelithiasis", "Gallstones within the gallbladder lumen.")));

        TerminologySearchResponse response = service.search(request("colelithiasis"), user);

        assertThat(response.getMatches()).hasSize(1);
        assertThat(response.getMatches().getFirst().getTerm()).isEqualTo("Cholelithiasis");
        assertThat(response.getAiCreditsUsed()).isEqualTo(1);
        assertThat(response.isServedFromCache()).isFalse();
        assertThat(response.getDisclaimer()).contains("not a validated clinical reference");
        verify(billingService).reserveAiCredits(ORG_ID, 1);
        verify(billingService, never()).refundAiCredits(anyString(), anyInt());
    }

    @Test
    @DisplayName("the same term a second time is served from cache, free and without a provider call")
    void repeatedLookupIsFreeAndCached() {
        when(primaryProvider.searchTerminology(any())).thenReturn(result(
                match("Cholelithiasis", "Gallstones within the gallbladder lumen.")));

        service.search(request("cholelithiasis"), user);
        TerminologySearchResponse second = service.search(request("  CHOLELITHIASIS "), user);

        assertThat(second.isServedFromCache()).isTrue();
        assertThat(second.getAiCreditsUsed()).isZero();
        assertThat(second.getMatches()).hasSize(1);

        // The point of the cache: one provider call and one credit across both lookups.
        verify(primaryProvider, times(1)).searchTerminology(any());
        verify(billingService, times(1)).reserveAiCredits(ORG_ID, 1);
    }

    @Test
    @DisplayName("a term that does not exist comes back empty rather than being retried")
    void emptyResultIsASuccessNotAFallback() {
        when(primaryProvider.searchTerminology(any())).thenReturn(result());

        TerminologySearchResponse response = service.search(request("qwertyosis"), user);

        assertThat(response.getMatches()).isEmpty();
        verify(aiProviderRouter, never()).fallbackProvider(any());

        ArgumentCaptor<AiGenerationEvent> event = ArgumentCaptor.forClass(AiGenerationEvent.class);
        verify(aiGenerationEventRepository).save(event.capture());
        assertThat(event.getValue().getStatus()).isEqualTo(AiGenerationStatus.SUCCESS);
        assertThat(event.getValue().getRequestType()).isEqualTo(AiRequestType.TERMINOLOGY_SEARCH);
    }

    @Test
    @DisplayName("the fallback provider answers when the primary fails")
    void fallbackAnswersWhenPrimaryFails() {
        when(primaryProvider.searchTerminology(any()))
                .thenThrow(new AiProviderException("gemini down", true));
        when(aiProviderRouter.fallbackProvider(AiProviderType.GEMINI)).thenReturn(fallbackProvider);
        when(fallbackProvider.searchTerminology(any())).thenReturn(result(
                match("Hydronephrosis", "Dilatation of the renal collecting system.")));

        TerminologySearchResponse response = service.search(request("hydronephrosis"), user);

        assertThat(response.isFallbackUsed()).isTrue();
        assertThat(response.getMatches()).hasSize(1);
        verify(billingService, never()).refundAiCredits(anyString(), anyInt());
    }

    @Test
    @DisplayName("when both providers fail the credit is refunded")
    void bothProvidersFailingRefundsTheCredit() {
        when(primaryProvider.searchTerminology(any()))
                .thenThrow(new AiProviderException("gemini down", true));
        when(aiProviderRouter.fallbackProvider(AiProviderType.GEMINI)).thenReturn(fallbackProvider);
        when(fallbackProvider.searchTerminology(any()))
                .thenThrow(new AiProviderException("openai down", true));

        assertThatThrownBy(() -> service.search(request("hydronephrosis"), user))
                .isInstanceOf(AiGenerationUnavailableException.class);

        verify(billingService).reserveAiCredits(ORG_ID, 1);
        verify(billingService).refundAiCredits(ORG_ID, 1);
    }

    @Test
    @DisplayName("an organization out of credits is blocked before any provider is called")
    void exhaustedCreditsBlockTheLookup() {
        doThrow(new SubscriptionLimitExceededException("Monthly AI credit limit reached"))
                .when(billingService).reserveAiCredits(ORG_ID, 1);

        assertThatThrownBy(() -> service.search(request("cholelithiasis"), user))
                .isInstanceOf(SubscriptionLimitExceededException.class);

        verify(primaryProvider, never()).searchTerminology(any());

        ArgumentCaptor<AiGenerationEvent> event = ArgumentCaptor.forClass(AiGenerationEvent.class);
        verify(aiGenerationEventRepository).save(event.capture());
        assertThat(event.getValue().getStatus()).isEqualTo(AiGenerationStatus.BLOCKED_LIMIT);
    }

    @Test
    @DisplayName("identifiers in the query are stripped before the query reaches the provider")
    void queryIsSanitisedBeforeLeavingTheSystem() {
        when(primaryProvider.searchTerminology(any())).thenReturn(result(
                match("Cholelithiasis", "Gallstones within the gallbladder lumen.")));

        service.search(request("cholelithiasis for patient 0244123456"), user);

        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(promptTemplateService).terminologyPrompt(query.capture(), any(), anyInt());
        assertThat(query.getValue()).doesNotContain("0244123456");
    }

    @Test
    @DisplayName("scan type separates the cache entries, so context is not lost to a shared key")
    void scanTypeIsPartOfTheCacheKey() {
        when(primaryProvider.searchTerminology(any())).thenReturn(result(
                match("Echogenicity", "The capacity of tissue to reflect ultrasound.")));

        service.search(request("echogenicity"), user);

        TerminologySearchRequest withScanType = request("echogenicity");
        withScanType.setScanType(ScanType.THYROID);
        TerminologySearchResponse second = service.search(withScanType, user);

        assertThat(second.isServedFromCache()).isFalse();
        verify(primaryProvider, times(2)).searchTerminology(any());
    }

    // ============================================================================ fixtures

    private static TerminologySearchRequest request(String query) {
        return TerminologySearchRequest.builder().query(query).limit(5).build();
    }

    private static AiTerminologyResult result(AiTerminologyResult.Match... matches) {
        return AiTerminologyResult.builder()
                .matches(List.of(matches))
                .provider("GEMINI")
                .model("gemini-test")
                .inputTokens(40)
                .outputTokens(120)
                .build();
    }

    private static AiTerminologyResult.Match match(String term, String definition) {
        return AiTerminologyResult.Match.builder()
                .term(term)
                .definition(definition)
                .category("Hepatobiliary")
                .synonyms(List.of("Gallstones"))
                .confusedWith(List.of("Choledocholithiasis"))
                .exampleUsage("Multiple mobile echogenic foci with posterior acoustic shadowing.")
                .relevance(0.95)
                .build();
    }
}
