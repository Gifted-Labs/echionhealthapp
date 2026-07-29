package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.vault.GrammarCheckRequest;
import com.giftedlabs.echoinhealthbackend.dto.vault.GrammarCheckResponse;
import com.giftedlabs.echoinhealthbackend.entity.Organization;
import com.giftedlabs.echoinhealthbackend.entity.SubscriptionTier;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.exception.SubscriptionLimitExceededException;
import com.giftedlabs.echoinhealthbackend.repository.AiGenerationEventRepository;
import com.giftedlabs.echoinhealthbackend.repository.OrganizationRepository;
import com.giftedlabs.echoinhealthbackend.service.ai.AiGrammarCheckResult;
import com.giftedlabs.echoinhealthbackend.service.ai.AiPromptTemplateService;
import com.giftedlabs.echoinhealthbackend.service.ai.AiProviderRouter;
import com.giftedlabs.echoinhealthbackend.service.ai.AiReportProvider;
import com.giftedlabs.echoinhealthbackend.service.ai.AiUsageCostEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Auto-Grammar Check is a Pro/Ultimate entitlement. Before this feature existed, the tier flag
 * was surfaced in the billing DTOs with nothing behind it — the checklist marked it done.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GrammarCheckTierGatingTest {

    @Mock private BillingService billingService;
    @Mock private AuditService auditService;
    @Mock private AiProviderRouter aiProviderRouter;
    @Mock private AiPromptTemplateService promptTemplateService;
    @Mock private AiUsageCostEstimator aiUsageCostEstimator;
    @Mock private AiGenerationEventRepository aiGenerationEventRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private AiReportProvider provider;

    private GrammarCheckService service;

    @BeforeEach
    void setUp() {
        service = new GrammarCheckService(billingService, auditService, aiProviderRouter,
                promptTemplateService, aiUsageCostEstimator, aiGenerationEventRepository,
                organizationRepository);
        when(promptTemplateService.promptVersion()).thenReturn("v2");
        when(promptTemplateService.grammarPrompt(anyString())).thenReturn("prompt");
    }

    private User userOn(SubscriptionTier tier) {
        Organization organization = Organization.builder()
                .id("org-1")
                .name("Org")
                .hospitalName("Hospital")
                .subscriptionTier(tier)
                .build();
        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(organization));
        return User.builder()
                .id("user-1")
                .email("user@example.com")
                .firstName("Test")
                .lastName("User")
                .passwordHash("hash")
                .organization(organization)
                .build();
    }

    @Test
    void basicTierIsRejectedWithAnUpgradePromptAndIsNotCharged() {
        User user = userOn(SubscriptionTier.BASIC);

        SubscriptionLimitExceededException error = assertThrows(
                SubscriptionLimitExceededException.class,
                () -> service.checkGrammar(
                        GrammarCheckRequest.builder().text("teh liver is enlarged").build(), user));

        assertTrue(error.getMessage().contains("Pro"), "should tell the admin how to enable it");
        // Rejected before any credit is reserved or any provider is called.
        verify(billingService, never()).reserveAiCredits(anyString(), anyInt());
        verify(aiProviderRouter, never()).primaryProvider(any());
    }

    @Test
    void proTierRunsTheCheckAndReturnsDiscreteCorrections() {
        User user = userOn(SubscriptionTier.PRO);
        when(aiProviderRouter.primaryProvider(any())).thenReturn(provider);
        when(provider.checkGrammar(any(), anyString())).thenReturn(new AiGrammarCheckResult(
                "teh liver is enlarged",
                "The liver is enlarged.",
                List.of(new AiGrammarCheckResult.Correction("teh", "The", "Spelling")),
                "GEMINI", "gemini-test", 20, 12));

        GrammarCheckResponse response = service.checkGrammar(
                GrammarCheckRequest.builder().text("teh liver is enlarged").build(), user);

        assertEquals("The liver is enlarged.", response.getCorrectedText());
        assertEquals(1, response.getCorrections().size());
        assertEquals("teh", response.getCorrections().get(0).getOriginal());
        assertTrue(response.isChangesSuggested());
        verify(billingService).reserveAiCredits("org-1", 1);
        verify(billingService, never()).refundAiCredits(anyString(), anyInt());
    }

    @Test
    void ultimateTierIsAlsoEntitled() {
        User user = userOn(SubscriptionTier.ULTIMATE);
        when(aiProviderRouter.primaryProvider(any())).thenReturn(provider);
        when(provider.checkGrammar(any(), anyString())).thenReturn(new AiGrammarCheckResult(
                "findings", "findings", List.of(), "OPENAI", "openai-test", 5, 5));

        GrammarCheckResponse response = service.checkGrammar(
                GrammarCheckRequest.builder().text("findings").build(), user);

        assertEquals("findings", response.getCorrectedText());
        assertTrue(response.getCorrections().isEmpty());
        assertEquals(false, response.isChangesSuggested());
    }

    @Test
    void aFailedCheckIsRefundedRatherThanBilled() {
        User user = userOn(SubscriptionTier.PRO);
        when(aiProviderRouter.primaryProvider(any())).thenReturn(null);

        assertThrows(RuntimeException.class, () -> service.checkGrammar(
                GrammarCheckRequest.builder().text("findings").build(), user));

        verify(billingService).reserveAiCredits("org-1", 1);
        verify(billingService).refundAiCredits("org-1", 1);
    }
}
