package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.vault.AiSuggestImpressionRequest;
import com.giftedlabs.echoinhealthbackend.dto.vault.AiSuggestImpressionResponse;
import com.giftedlabs.echoinhealthbackend.dto.vault.GenerateAiReportRequest;
import com.giftedlabs.echoinhealthbackend.dto.vault.GenerateAiReportResponse;
import com.giftedlabs.echoinhealthbackend.entity.*;
import com.giftedlabs.echoinhealthbackend.exception.AiGenerationUnavailableException;
import com.giftedlabs.echoinhealthbackend.exception.AiProviderException;
import com.giftedlabs.echoinhealthbackend.exception.SubscriptionLimitExceededException;
import com.giftedlabs.echoinhealthbackend.repository.AiGenerationEventRepository;
import com.giftedlabs.echoinhealthbackend.service.ai.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiReportGenerationService {

    private static final int CREDIT_COST = 1;

    private final BillingService billingService;
    private final AuditService auditService;
    private final AiProviderRouter aiProviderRouter;
    private final AiPromptTemplateService promptTemplateService;
    private final AiPromptSanitizer aiPromptSanitizer;
    private final AiUsageCostEstimator aiUsageCostEstimator;
    private final AiGenerationEventRepository aiGenerationEventRepository;

    @Transactional(noRollbackFor = { AiGenerationUnavailableException.class, SubscriptionLimitExceededException.class })
    public GenerateAiReportResponse generateReport(GenerateAiReportRequest request, User user) {
        Instant startedAt = Instant.now();
        Map<String, Object> sanitizedMeasurements = aiPromptSanitizer.sanitizeMeasurements(request.getMeasurements());
        String prompt = promptTemplateService.reportPrompt(
                request.getScanType(),
                requireText(aiPromptSanitizer.sanitize(request.getRawNotes()), "Clinical notes are required"),
                sanitizedMeasurements);

        // Reserve before calling the provider. Reserving afterwards left a window in which
        // concurrent requests could each pass the check and each generate, taking the
        // organization past its monthly limit.
        reserveOrRecordBlocked(user, AiRequestType.FULL_REPORT_GENERATION, startedAt);

        ProviderOutcome outcome;
        try {
            outcome = executeWithFallback(
                    user,
                    request.getProvider(),
                    AiRequestType.FULL_REPORT_GENERATION,
                    AiProviderRequest.builder()
                            .prompt(prompt)
                            .promptVersion(promptTemplateService.promptVersion())
                            .scanType(request.getScanType())
                            .measurements(sanitizedMeasurements)
                            .build(),
                    startedAt,
                    true);
        } catch (RuntimeException e) {
            // A generation that produced nothing must not be billed.
            billingService.refundAiCredits(user.getOrganizationId(), CREDIT_COST);
            throw e;
        }

        auditService.logAction(user, "ai_report_generated",
                String.format("Generated AI report using %s (%s) for scanType=%s",
                        outcome.response().provider(), outcome.response().model(), request.getScanType()));

        List<String> recommendations = outcome.response().recommendations() == null
                ? List.of()
                : outcome.response().recommendations();

        return GenerateAiReportResponse.builder()
                .findings(outcome.response().findings())
                .impression(outcome.response().impression())
                .recommendation(String.join("\n", recommendations))
                .recommendationOptions(recommendations)
                .structuredFindings(enrichStructuredFindings(request.getScanType(), outcome.response().structuredFindings(), sanitizedMeasurements))
                .scanType(request.getScanType())
                .reportType(request.getReportType())
                .provider(outcome.response().provider())
                .model(outcome.response().model())
                .fallbackUsed(outcome.fallbackUsed())
                .promptTemplateVersion(promptTemplateService.promptVersion())
                .aiCreditsConsumed(CREDIT_COST)
                .processingTimeSeconds((int) Math.max(1, Duration.between(startedAt, Instant.now()).toSeconds()))
                .generatedAt(LocalDateTime.now())
                .build();
    }

    @Transactional(noRollbackFor = { AiGenerationUnavailableException.class, SubscriptionLimitExceededException.class })
    public AiSuggestImpressionResponse suggestImpression(AiSuggestImpressionRequest request, User user) {
        Instant startedAt = Instant.now();
        String sanitizedFindings = requireText(aiPromptSanitizer.sanitize(request.getFindings()), "Findings are required");
        String prompt = promptTemplateService.impressionPrompt(request.getScanType(), sanitizedFindings);

        reserveOrRecordBlocked(user, AiRequestType.IMPRESSION_SUGGESTION, startedAt);

        ProviderOutcome outcome;
        try {
            outcome = executeWithFallback(
                    user,
                    request.getProvider(),
                    AiRequestType.IMPRESSION_SUGGESTION,
                    AiProviderRequest.builder()
                            .prompt(prompt)
                            .promptVersion(promptTemplateService.promptVersion())
                            .scanType(request.getScanType())
                            .measurements(Map.of())
                            .build(),
                    startedAt,
                    false);
        } catch (RuntimeException e) {
            billingService.refundAiCredits(user.getOrganizationId(), CREDIT_COST);
            throw e;
        }

        auditService.logAction(user, "ai_impression_suggested",
                String.format("Suggested AI impression using %s (%s) for scanType=%s",
                        outcome.response().provider(), outcome.response().model(), request.getScanType()));

        return AiSuggestImpressionResponse.builder()
                .impression(outcome.response().impression())
                .provider(outcome.response().provider())
                .model(outcome.response().model())
                .fallbackUsed(outcome.fallbackUsed())
                .promptTemplateVersion(promptTemplateService.promptVersion())
                .aiCreditsConsumed(CREDIT_COST)
                .processingTimeSeconds((int) Math.max(1, Duration.between(startedAt, Instant.now()).toSeconds()))
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Takes the credit up front so the limit check and the deduction cannot interleave, and
     * records a BLOCKED_LIMIT event when the tenant is out of credits.
     */
    private void reserveOrRecordBlocked(User user, AiRequestType requestType, Instant startedAt) {
        try {
            billingService.reserveAiCredits(user.getOrganizationId(), CREDIT_COST);
        } catch (SubscriptionLimitExceededException ex) {
            recordEvent(user, requestType, null, false, AiGenerationStatus.BLOCKED_LIMIT,
                    promptTemplateService.promptVersion(), startedAt, null, ex.getMessage());
            throw ex;
        }
    }

    private ProviderOutcome executeWithFallback(User user,
                                                String requestedProvider,
                                                AiRequestType requestType,
                                                AiProviderRequest providerRequest,
                                                Instant startedAt,
                                                boolean fullReport) {
        AiReportProvider primaryProvider = aiProviderRouter.primaryProvider(requestedProvider);
        if (primaryProvider == null) {
            throw new AiGenerationUnavailableException("AI provider is not configured.");
        }

        try {
            AiModelResponse response = invoke(primaryProvider, providerRequest, fullReport);
            recordEvent(user, requestType, response, false,
                    AiGenerationStatus.SUCCESS, providerRequest.promptVersion(), startedAt, null, null);
            return new ProviderOutcome(response, false);
        } catch (AiProviderException primaryFailure) {
            log.warn("AI primary provider {} failed for {} (scanType={}): {}",
                    primaryProvider.providerType(), requestType, providerRequest.scanType(),
                    primaryFailure.getMessage());

            AiReportProvider fallbackProvider = aiProviderRouter.fallbackProvider(primaryProvider.providerType());
            if (fallbackProvider == null) {
                logGaveUp(requestType, primaryProvider, primaryFailure, null, null);
                recordEvent(user, requestType, null, false, AiGenerationStatus.FAILED,
                        providerRequest.promptVersion(), startedAt, null, primaryFailure.getMessage());
                throw new AiGenerationUnavailableException("AI generation is temporarily unavailable. Please retry or continue manually.");
            }

            try {
                AiModelResponse response = invoke(fallbackProvider, providerRequest, fullReport);
                log.info("AI fallback provider {} succeeded after {} failed",
                        fallbackProvider.providerType(), primaryProvider.providerType());
                recordEvent(user, requestType, response, true,
                        AiGenerationStatus.FALLBACK_SUCCESS, providerRequest.promptVersion(), startedAt, primaryFailure, null);
                return new ProviderOutcome(response, true);
            } catch (AiProviderException fallbackFailure) {
                // Previously this path logged nothing at all: when both providers failed, the only
                // record of *why* was a row in ai_generation_events, and the caller got a generic
                // 503. That made the most important failure in the system the least diagnosable.
                logGaveUp(requestType, primaryProvider, primaryFailure, fallbackProvider, fallbackFailure);
                recordEvent(user, requestType, null, true, AiGenerationStatus.FAILED,
                        providerRequest.promptVersion(), startedAt, fallbackFailure, fallbackFailure.getMessage());
                throw new AiGenerationUnavailableException("AI generation is temporarily unavailable. Please retry or continue manually.");
            }
        }
    }

    /**
     * Emits the one message an operator needs when AI generation returns 503: which providers
     * were tried and exactly what each one said. The client-facing message is deliberately
     * generic; this is where the real cause belongs.
     */
    private void logGaveUp(AiRequestType requestType,
                           AiReportProvider primary, AiProviderException primaryFailure,
                           AiReportProvider fallback, AiProviderException fallbackFailure) {
        StringBuilder report = new StringBuilder()
                .append("AI generation FAILED for ").append(requestType)
                .append(" -- returning 503 to the client.")
                .append("\n  primary  [").append(primary.providerType()).append("]: ")
                .append(primaryFailure.getMessage());

        if (fallback == null) {
            report.append("\n  fallback [none]: no configured fallback provider. ")
                    .append("Set AI_FALLBACK_PROVIDER and its API key, or fix the primary.");
        } else {
            report.append("\n  fallback [").append(fallback.providerType()).append("]: ")
                    .append(fallbackFailure.getMessage());
        }
        report.append("\n  Both providers were tried and both failed; the causes above are the "
                + "whole diagnosis. Run POST /api/admin/ai/verify to re-test configuration.");

        log.error(report.toString());
    }

    private AiModelResponse invoke(AiReportProvider provider, AiProviderRequest request, boolean fullReport) {
        return fullReport ? provider.generateStructuredReport(request) : provider.suggestImpression(request);
    }

    private void recordEvent(User user,
                             AiRequestType requestType,
                             AiModelResponse response,
                             boolean fallbackUsed,
                             AiGenerationStatus status,
                             String promptVersion,
                             Instant startedAt,
                             Exception exception,
                             String failureReason) {
        String provider = response != null ? response.provider() : null;
        String model = response != null ? response.model() : null;
        BigDecimal estimatedCost = response != null
                ? aiUsageCostEstimator.estimate(
                        aiProviderRouter.settings(AiProviderType.from(provider, AiProviderType.GEMINI)),
                        response.inputTokens(),
                        response.outputTokens())
                : null;
        aiGenerationEventRepository.save(AiGenerationEvent.builder()
                .organization(user.getOrganization())
                .user(user)
                .requestType(requestType)
                .provider(provider)
                .model(model)
                .promptVersion(promptVersion)
                .fallbackUsed(fallbackUsed)
                .status(status)
                .inputTokens(response != null ? response.inputTokens() : null)
                .outputTokens(response != null ? response.outputTokens() : null)
                .estimatedCostUsd(estimatedCost)
                .latencyMs(Duration.between(startedAt, Instant.now()).toMillis())
                .failureReason(trimFailureReason(failureReason != null ? failureReason : exception != null ? exception.getMessage() : null))
                .build());
    }

    private Map<String, Object> enrichStructuredFindings(ScanType scanType,
                                                         Map<String, Object> structuredFindings,
                                                         Map<String, Object> measurements) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (structuredFindings != null) {
            result.putAll(structuredFindings);
        }
        result.putIfAbsent("scanType", scanType != null ? scanType.name() : null);
        if (measurements != null && !measurements.isEmpty()) {
            result.putIfAbsent("measurements", measurements);
        }
        result.putIfAbsent("generatedBy", "AI");
        result.putIfAbsent("promptTemplateVersion", promptTemplateService.promptVersion());
        return result;
    }

    private String requireText(String input, String message) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return input;
    }

    private String trimFailureReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.length() > 500 ? reason.substring(0, 500) : reason;
    }

    private record ProviderOutcome(AiModelResponse response, boolean fallbackUsed) {
    }
}
