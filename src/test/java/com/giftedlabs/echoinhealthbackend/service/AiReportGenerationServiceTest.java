package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.vault.GenerateAiReportRequest;
import com.giftedlabs.echoinhealthbackend.dto.vault.GenerateAiReportResponse;
import com.giftedlabs.echoinhealthbackend.entity.*;
import com.giftedlabs.echoinhealthbackend.exception.AiGenerationUnavailableException;
import com.giftedlabs.echoinhealthbackend.exception.AiProviderException;
import com.giftedlabs.echoinhealthbackend.exception.SubscriptionLimitExceededException;
import com.giftedlabs.echoinhealthbackend.repository.AiGenerationEventRepository;
import com.giftedlabs.echoinhealthbackend.service.ai.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiReportGenerationServiceTest {

    @Mock
    private BillingService billingService;

    @Mock
    private AuditService auditService;

    @Mock
    private AiProviderRouter aiProviderRouter;

    @Mock
    private AiPromptTemplateService promptTemplateService;

    @Mock
    private AiPromptSanitizer aiPromptSanitizer;

    @Mock
    private AiUsageCostEstimator aiUsageCostEstimator;

    @Mock
    private AiGenerationEventRepository aiGenerationEventRepository;

    @Mock
    private AiReportProvider geminiProvider;

    @Mock
    private AiReportProvider openAiProvider;

    private AiReportGenerationService service;

    @BeforeEach
    void setUp() {
        service = new AiReportGenerationService(
                billingService,
                auditService,
                aiProviderRouter,
                promptTemplateService,
                aiPromptSanitizer,
                aiUsageCostEstimator,
                aiGenerationEventRepository);
    }

    @Test
    void generateReportUsesPrimaryProviderWhenSuccessful() {
        User user = user();
        GenerateAiReportRequest request = request();
        AiModelResponse primaryResponse = response("GEMINI", "gemini-2.5-flash");

        when(aiPromptSanitizer.sanitize("raw notes")).thenReturn("sanitized notes");
        when(aiPromptSanitizer.sanitizeMeasurements(Map.of("bpd", "32mm"))).thenReturn(Map.of("bpd", "32mm"));
        when(promptTemplateService.reportPrompt(ScanType.ABDOMINAL, "sanitized notes", Map.of("bpd", "32mm")))
                .thenReturn("prompt");
        when(promptTemplateService.promptVersion()).thenReturn("v2");
        when(aiProviderRouter.primaryProvider(null)).thenReturn(geminiProvider);
        when(geminiProvider.generateStructuredReport(any())).thenReturn(primaryResponse);

        GenerateAiReportResponse result = service.generateReport(request, user);

        assertEquals("GEMINI", result.getProvider());
        assertEquals("gemini-2.5-flash", result.getModel());
        assertFalse(Boolean.TRUE.equals(result.getFallbackUsed()));
        verify(aiProviderRouter, never()).fallbackProvider(any());
        // The credit is reserved before the provider call, and kept because it succeeded.
        verify(billingService).reserveAiCredits("org-1", 1);
        verify(billingService, never()).refundAiCredits(any(), anyInt());
    }

    @Test
    void generateReportFallsBackWhenPrimaryFails() {
        User user = user();
        GenerateAiReportRequest request = request();
        AiModelResponse fallbackResponse = response("OPENAI", "gpt-4.1-mini");

        when(aiPromptSanitizer.sanitize("raw notes")).thenReturn("sanitized notes");
        when(aiPromptSanitizer.sanitizeMeasurements(Map.of("bpd", "32mm"))).thenReturn(Map.of("bpd", "32mm"));
        when(promptTemplateService.reportPrompt(ScanType.ABDOMINAL, "sanitized notes", Map.of("bpd", "32mm")))
                .thenReturn("prompt");
        when(promptTemplateService.promptVersion()).thenReturn("v2");
        when(aiProviderRouter.primaryProvider(null)).thenReturn(geminiProvider);
        when(geminiProvider.providerType()).thenReturn(AiProviderType.GEMINI);
        when(geminiProvider.generateStructuredReport(any()))
                .thenThrow(new AiProviderException("Gemini timed out", true));
        when(aiProviderRouter.fallbackProvider(AiProviderType.GEMINI)).thenReturn(openAiProvider);
        when(openAiProvider.generateStructuredReport(any())).thenReturn(fallbackResponse);

        GenerateAiReportResponse result = service.generateReport(request, user);

        assertTrue(Boolean.TRUE.equals(result.getFallbackUsed()));
        assertEquals("OPENAI", result.getProvider());
        verify(aiProviderRouter).fallbackProvider(AiProviderType.GEMINI);
        verify(billingService).reserveAiCredits("org-1", 1);
        verify(billingService, never()).refundAiCredits(any(), anyInt());
    }

    @Test
    void generateReportFailsCleanlyWhenAllProvidersFail() {
        User user = user();
        GenerateAiReportRequest request = request();

        when(aiPromptSanitizer.sanitize("raw notes")).thenReturn("sanitized notes");
        when(aiPromptSanitizer.sanitizeMeasurements(Map.of("bpd", "32mm"))).thenReturn(Map.of("bpd", "32mm"));
        when(promptTemplateService.reportPrompt(ScanType.ABDOMINAL, "sanitized notes", Map.of("bpd", "32mm")))
                .thenReturn("prompt");
        when(promptTemplateService.promptVersion()).thenReturn("v2");
        when(aiProviderRouter.primaryProvider(null)).thenReturn(geminiProvider);
        when(geminiProvider.providerType()).thenReturn(AiProviderType.GEMINI);
        when(geminiProvider.generateStructuredReport(any()))
                .thenThrow(new AiProviderException("Gemini timed out", true));
        when(aiProviderRouter.fallbackProvider(AiProviderType.GEMINI)).thenReturn(openAiProvider);
        when(openAiProvider.generateStructuredReport(any()))
                .thenThrow(new AiProviderException("OpenAI timed out", true));

        assertThrows(AiGenerationUnavailableException.class, () -> service.generateReport(request, user));

        // Reserved up front, then handed back: a generation that produced nothing is not billed.
        verify(billingService).reserveAiCredits("org-1", 1);
        verify(billingService).refundAiCredits("org-1", 1);
        verify(aiGenerationEventRepository, atLeastOnce()).save(any());
    }

    @Test
    void generateReportStopsBeforeProviderWhenCreditLimitExceeded() {
        User user = user();
        GenerateAiReportRequest request = request();

        when(aiPromptSanitizer.sanitize("raw notes")).thenReturn("sanitized notes");
        when(aiPromptSanitizer.sanitizeMeasurements(Map.of("bpd", "32mm"))).thenReturn(Map.of("bpd", "32mm"));
        when(promptTemplateService.reportPrompt(ScanType.ABDOMINAL, "sanitized notes", Map.of("bpd", "32mm")))
                .thenReturn("prompt");
        when(promptTemplateService.promptVersion()).thenReturn("v2");
        doThrow(new SubscriptionLimitExceededException("limit"))
                .when(billingService).reserveAiCredits("org-1", 1);

        assertThrows(SubscriptionLimitExceededException.class, () -> service.generateReport(request, user));

        verify(aiProviderRouter, never()).primaryProvider(any());
        verify(billingService, never()).refundAiCredits(any(), anyInt());
        verify(aiGenerationEventRepository).save(any());
    }

    private GenerateAiReportRequest request() {
        return GenerateAiReportRequest.builder()
                .rawNotes("raw notes")
                .scanType(ScanType.ABDOMINAL)
                .measurements(Map.of("bpd", "32mm"))
                .build();
    }

    private AiModelResponse response(String provider, String model) {
        return AiModelResponse.builder()
                .provider(provider)
                .model(model)
                .findings("Structured findings")
                .impression("Structured impression")
                .recommendations(List.of("Recommendation"))
                .structuredFindings(Map.of("section", "detail"))
                .inputTokens(120)
                .outputTokens(80)
                .build();
    }

    private User user() {
        Organization organization = Organization.builder()
                .id("org-1")
                .name("Org")
                .hospitalName("Hospital")
                .subscriptionTier(SubscriptionTier.BASIC)
                .build();
        return User.builder()
                .id("user-1")
                .email("owner@example.com")
                .organization(organization)
                .firstName("Owner")
                .lastName("User")
                .passwordHash("hash")
                .build();
    }
}
