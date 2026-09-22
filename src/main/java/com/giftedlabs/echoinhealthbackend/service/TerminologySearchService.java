package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.vault.TerminologySearchRequest;
import com.giftedlabs.echoinhealthbackend.dto.vault.TerminologySearchResponse;
import com.giftedlabs.echoinhealthbackend.entity.AiGenerationEvent;
import com.giftedlabs.echoinhealthbackend.entity.AiGenerationStatus;
import com.giftedlabs.echoinhealthbackend.entity.AiRequestType;
import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.exception.AiGenerationUnavailableException;
import com.giftedlabs.echoinhealthbackend.exception.AiProviderException;
import com.giftedlabs.echoinhealthbackend.exception.SubscriptionLimitExceededException;
import com.giftedlabs.echoinhealthbackend.repository.AiGenerationEventRepository;
import com.giftedlabs.echoinhealthbackend.service.ai.AiPromptSanitizer;
import com.giftedlabs.echoinhealthbackend.service.ai.AiPromptTemplateService;
import com.giftedlabs.echoinhealthbackend.service.ai.AiProviderRequest;
import com.giftedlabs.echoinhealthbackend.service.ai.AiProviderRouter;
import com.giftedlabs.echoinhealthbackend.service.ai.AiProviderType;
import com.giftedlabs.echoinhealthbackend.service.ai.AiReportProvider;
import com.giftedlabs.echoinhealthbackend.service.ai.AiTerminologyResult;
import com.giftedlabs.echoinhealthbackend.service.ai.AiUsageCostEstimator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * AI-assisted clinical terminology lookup (UR-074).
 *
 * <p>Answers "what is the correct word, and am I using it right" for a clinician drafting a
 * report. Three things make it different from the report-generation path, and each is a
 * deliberate decision rather than an omission:
 *
 * <p><b>It is cached across tenants.</b> The definition of "cholelithiasis" is not tenant data,
 * so caching it per organization would multiply cost by tenant count for no benefit. The cache
 * key is the normalised query plus scan context, and nothing patient-specific ever reaches it —
 * the query is sanitised before it is used as a key, and the 200-character cap on the request
 * bounds what could be pasted in.
 *
 * <p><b>A cache hit is free.</b> Autocomplete fires on keystrokes. Billing a credit per keystroke
 * would drain a Basic tenant's monthly allowance in an afternoon and put a provider round-trip in
 * front of every character. Only an uncached lookup reserves a credit, and the response says
 * which happened so a client can show it.
 *
 * <p><b>An empty result is a success.</b> If the query names nothing real, the correct answer is
 * no matches — not a retry against the fallback provider. Inventing a plausible term is the
 * specific failure this feature must not have.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TerminologySearchService {

    public static final String CACHE_NAME = "aiTerminology";

    private final BillingService billingService;
    private final AuditService auditService;
    private final AiProviderRouter aiProviderRouter;
    private final AiPromptTemplateService promptTemplateService;
    private final AiPromptSanitizer aiPromptSanitizer;
    private final AiUsageCostEstimator aiUsageCostEstimator;
    private final AiGenerationEventRepository aiGenerationEventRepository;
    private final CacheManager cacheManager;

    /** Credits an uncached lookup costs. Kept configurable because it changes tier economics. */
    @Value("${ai.terminology.credit-cost:1}")
    private int creditCost;

    public TerminologySearchResponse search(TerminologySearchRequest request, User user) {
        Instant startedAt = Instant.now();

        String query = aiPromptSanitizer.sanitize(request.getQuery());
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query is required");
        }
        int limit = request.getLimit() == null ? 5 : Math.clamp(request.getLimit(), 1, 10);
        ScanType scanType = request.getScanType();

        String cacheKey = cacheKey(query, scanType, limit);
        TerminologySearchResponse cached = readCache(cacheKey);
        if (cached != null) {
            return cached.toBuilder()
                    .query(request.getQuery())
                    .aiCreditsUsed(0)
                    .servedFromCache(true)
                    .build();
        }

        reserveOrRecordBlocked(user, startedAt);

        AiProviderRequest providerRequest = AiProviderRequest.builder()
                .prompt(promptTemplateService.terminologyPrompt(query, scanType, limit))
                .promptVersion(promptTemplateService.promptVersion())
                .scanType(scanType)
                .build();

        Outcome outcome;
        try {
            outcome = executeWithFallback(user, providerRequest, startedAt);
        } catch (RuntimeException failure) {
            billingService.refundAiCredits(user.getOrganizationId(), creditCost);
            throw failure;
        }

        AiTerminologyResult result = outcome.result();
        TerminologySearchResponse response = TerminologySearchResponse.builder()
                .query(request.getQuery())
                .matches(result.matches().stream()
                        .limit(limit)
                        .map(TerminologySearchService::toDto)
                        .toList())
                .provider(result.provider())
                .model(result.model())
                .fallbackUsed(outcome.fallbackUsed())
                .aiCreditsUsed(creditCost)
                .servedFromCache(false)
                .build();

        writeCache(cacheKey, response);

        auditService.logAction(user, "ai_terminology_search",
                String.format("Terminology lookup '%s' returned %d match(es) via %s",
                        query, response.getMatches().size(), result.provider()));

        return response;
    }

    // ============================================================================ credits

    private void reserveOrRecordBlocked(User user, Instant startedAt) {
        try {
            billingService.reserveAiCredits(user.getOrganizationId(), creditCost);
        } catch (SubscriptionLimitExceededException ex) {
            recordEvent(user, null, false, AiGenerationStatus.BLOCKED_LIMIT, startedAt, ex.getMessage());
            throw ex;
        }
    }

    // =========================================================================== providers

    private Outcome executeWithFallback(User user, AiProviderRequest providerRequest, Instant startedAt) {
        AiReportProvider primary = aiProviderRouter.primaryProvider(null);
        if (primary == null) {
            recordEvent(user, null, false, AiGenerationStatus.FAILED, startedAt,
                    "no provider configured");
            throw new AiGenerationUnavailableException("AI provider is not configured.");
        }

        try {
            AiTerminologyResult result = primary.searchTerminology(providerRequest);
            recordEvent(user, result, false, AiGenerationStatus.SUCCESS, startedAt, null);
            return new Outcome(result, false);
        } catch (AiProviderException primaryFailure) {
            log.warn("Terminology search primary provider {} failed: {}",
                    primary.providerType(), primaryFailure.getMessage());

            AiReportProvider fallback = aiProviderRouter.fallbackProvider(primary.providerType());
            if (fallback == null) {
                recordEvent(user, null, false, AiGenerationStatus.FAILED, startedAt,
                        primaryFailure.getMessage());
                throw new AiGenerationUnavailableException(
                        "Terminology search is temporarily unavailable. Please retry.");
            }

            try {
                AiTerminologyResult result = fallback.searchTerminology(providerRequest);
                recordEvent(user, result, true, AiGenerationStatus.FALLBACK_SUCCESS, startedAt, null);
                return new Outcome(result, true);
            } catch (AiProviderException fallbackFailure) {
                log.error("""
                        Terminology search FAILED -- returning 503 to the client.
                          primary  [{}]: {}
                          fallback [{}]: {}""",
                        primary.providerType(), primaryFailure.getMessage(),
                        fallback.providerType(), fallbackFailure.getMessage());
                recordEvent(user, null, true, AiGenerationStatus.FAILED, startedAt,
                        fallbackFailure.getMessage());
                throw new AiGenerationUnavailableException(
                        "Terminology search is temporarily unavailable. Please retry.");
            }
        }
    }

    // =============================================================================== cache

    private String cacheKey(String query, ScanType scanType, int limit) {
        return query.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim()
                + "|" + (scanType == null ? "ANY" : scanType.name())
                + "|" + limit;
    }

    private TerminologySearchResponse readCache(String key) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        return cache == null ? null : cache.get(key, TerminologySearchResponse.class);
    }

    private void writeCache(String key, TerminologySearchResponse response) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.put(key, response);
        }
    }

    // =========================================================================== telemetry

    private void recordEvent(User user,
                             AiTerminologyResult result,
                             boolean fallbackUsed,
                             AiGenerationStatus status,
                             Instant startedAt,
                             String failureReason) {
        String provider = result != null ? result.provider() : null;
        BigDecimal estimatedCost = result != null
                ? aiUsageCostEstimator.estimate(
                        aiProviderRouter.settings(AiProviderType.from(provider, AiProviderType.GEMINI)),
                        result.inputTokens(), result.outputTokens())
                : null;

        aiGenerationEventRepository.save(AiGenerationEvent.builder()
                .organization(user.getOrganization())
                .user(user)
                .requestType(AiRequestType.TERMINOLOGY_SEARCH)
                .provider(provider)
                .model(result != null ? result.model() : null)
                .promptVersion(promptTemplateService.promptVersion())
                .fallbackUsed(fallbackUsed)
                .status(status)
                .inputTokens(result != null ? result.inputTokens() : null)
                .outputTokens(result != null ? result.outputTokens() : null)
                .estimatedCostUsd(estimatedCost)
                .latencyMs(Duration.between(startedAt, Instant.now()).toMillis())
                .failureReason(trim(failureReason))
                .build());
    }

    private String trim(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() > 1000 ? reason.substring(0, 1000) : reason;
    }

    private static TerminologySearchResponse.TerminologyMatch toDto(AiTerminologyResult.Match match) {
        return TerminologySearchResponse.TerminologyMatch.builder()
                .term(match.term())
                .definition(match.definition())
                .category(match.category())
                .synonyms(match.synonyms() == null ? List.of() : match.synonyms())
                .confusedWith(match.confusedWith() == null ? List.of() : match.confusedWith())
                .exampleUsage(match.exampleUsage())
                .relevance(match.relevance())
                .build();
    }

    private record Outcome(AiTerminologyResult result, boolean fallbackUsed) {
    }
}
