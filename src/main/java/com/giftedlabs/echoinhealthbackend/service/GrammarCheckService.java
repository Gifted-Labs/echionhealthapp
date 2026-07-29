package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.vault.GrammarCheckRequest;
import com.giftedlabs.echoinhealthbackend.dto.vault.GrammarCheckResponse;
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
import com.giftedlabs.echoinhealthbackend.repository.OrganizationRepository;
import com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException;
import com.giftedlabs.echoinhealthbackend.service.ai.AiGrammarCheckResult;
import com.giftedlabs.echoinhealthbackend.service.ai.AiPromptTemplateService;
import com.giftedlabs.echoinhealthbackend.service.ai.AiProviderRequest;
import com.giftedlabs.echoinhealthbackend.service.ai.AiProviderRouter;
import com.giftedlabs.echoinhealthbackend.service.ai.AiProviderType;
import com.giftedlabs.echoinhealthbackend.service.ai.AiReportProvider;
import com.giftedlabs.echoinhealthbackend.service.ai.AiUsageCostEstimator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Auto-Grammar Check — a Pro/Ultimate tier feature (UR-065).
 *
 * <p>The tier flag for this existed and was surfaced in the billing DTOs, but no feature sat
 * behind it. This is that feature.
 *
 * <p>Corrections are returned for review and never applied automatically. An LLM silently
 * rewriting a finalized clinical document is not an acceptable default, so the decision to
 * accept an edit stays with the reporting clinician.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GrammarCheckService {

    private static final int CREDIT_COST = 1;

    private final BillingService billingService;
    private final AuditService auditService;
    private final AiProviderRouter aiProviderRouter;
    private final AiPromptTemplateService promptTemplateService;
    private final AiUsageCostEstimator aiUsageCostEstimator;
    private final AiGenerationEventRepository aiGenerationEventRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional(noRollbackFor = {
            AiGenerationUnavailableException.class,
            SubscriptionLimitExceededException.class })
    public GrammarCheckResponse checkGrammar(GrammarCheckRequest request, User user) {
        Instant startedAt = Instant.now();
        Organization organization = organizationRepository.findById(user.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        assertTierIncludesGrammarCheck(organization);

        String prompt = promptTemplateService.grammarPrompt(request.getText());

        try {
            billingService.reserveAiCredits(organization.getId(), CREDIT_COST);
        } catch (SubscriptionLimitExceededException ex) {
            recordEvent(user, null, false, AiGenerationStatus.BLOCKED_LIMIT, startedAt, ex.getMessage());
            throw ex;
        }

        AiReportProvider primary = aiProviderRouter.primaryProvider(request.getProvider());
        if (primary == null) {
            billingService.refundAiCredits(organization.getId(), CREDIT_COST);
            throw new AiGenerationUnavailableException("AI provider is not configured.");
        }

        AiProviderRequest providerRequest = AiProviderRequest.builder()
                .prompt(prompt)
                .promptVersion(promptTemplateService.promptVersion())
                .scanType(ScanType.GENERAL)
                .measurements(Map.of())
                .build();

        boolean fallbackUsed = false;
        AiGrammarCheckResult result;
        try {
            result = primary.checkGrammar(providerRequest, request.getText());
        } catch (AiProviderException primaryFailure) {
            log.warn("Grammar check failed on {}", primary.providerType(), primaryFailure);
            AiReportProvider fallback = aiProviderRouter.fallbackProvider(primary.providerType());
            if (fallback == null) {
                billingService.refundAiCredits(organization.getId(), CREDIT_COST);
                recordEvent(user, null, false, AiGenerationStatus.FAILED, startedAt,
                        primaryFailure.getMessage());
                throw new AiGenerationUnavailableException(
                        "Grammar check is temporarily unavailable. Please retry or continue manually.");
            }
            try {
                result = fallback.checkGrammar(providerRequest, request.getText());
                fallbackUsed = true;
            } catch (AiProviderException fallbackFailure) {
                billingService.refundAiCredits(organization.getId(), CREDIT_COST);
                recordEvent(user, null, true, AiGenerationStatus.FAILED, startedAt,
                        fallbackFailure.getMessage());
                throw new AiGenerationUnavailableException(
                        "Grammar check is temporarily unavailable. Please retry or continue manually.");
            }
        }

        recordEvent(user, result, fallbackUsed,
                fallbackUsed ? AiGenerationStatus.FALLBACK_SUCCESS : AiGenerationStatus.SUCCESS,
                startedAt, null);
        auditService.logAction(user, "grammar_check_run",
                String.format("Ran grammar check using %s (%s), %d suggestion(s)",
                        result.provider(), result.model(), result.corrections().size()));

        return GrammarCheckResponse.builder()
                .originalText(result.originalText())
                .correctedText(result.correctedText())
                .corrections(result.corrections().stream()
                        .map(correction -> GrammarCheckResponse.Correction.builder()
                                .original(correction.original())
                                .corrected(correction.corrected())
                                .reason(correction.reason())
                                .build())
                        .toList())
                .changesSuggested(!result.corrections().isEmpty())
                .provider(result.provider())
                .model(result.model())
                .fallbackUsed(fallbackUsed)
                .promptTemplateVersion(promptTemplateService.promptVersion())
                .aiCreditsConsumed(CREDIT_COST)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private void assertTierIncludesGrammarCheck(Organization organization) {
        if (!organization.isAutoGrammarCheckEnabled()) {
            throw new SubscriptionLimitExceededException(String.format(
                    "Auto-Grammar Check is available on the Pro and Ultimate plans. "
                            + "Your organization is on %s. Upgrade to enable it.",
                    organization.getSubscriptionTier()));
        }
    }

    private void recordEvent(User user, AiGrammarCheckResult result, boolean fallbackUsed,
                             AiGenerationStatus status, Instant startedAt, String failureReason) {
        aiGenerationEventRepository.save(AiGenerationEvent.builder()
                .organization(user.getOrganization())
                .user(user)
                .requestType(AiRequestType.GRAMMAR_CHECK)
                .provider(result != null ? result.provider() : null)
                .model(result != null ? result.model() : null)
                .promptVersion(promptTemplateService.promptVersion())
                .fallbackUsed(fallbackUsed)
                .status(status)
                .inputTokens(result != null ? result.inputTokens() : null)
                .outputTokens(result != null ? result.outputTokens() : null)
                .estimatedCostUsd(result != null
                        ? aiUsageCostEstimator.estimate(
                                aiProviderRouter.settings(
                                        AiProviderType.from(result.provider(), AiProviderType.GEMINI)),
                                result.inputTokens(), result.outputTokens())
                        : null)
                .latencyMs(Duration.between(startedAt, Instant.now()).toMillis())
                .failureReason(failureReason != null && failureReason.length() > 500
                        ? failureReason.substring(0, 500) : failureReason)
                .build());
    }
}
