package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.admin.AiUsageReportResponse;
import com.giftedlabs.echoinhealthbackend.entity.AiGenerationStatus;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.repository.AiGenerationEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Turns recorded AI generation events into an operational report: is the AI actually working,
 * how often does it fall back, and what is it costing.
 */
@Service
@RequiredArgsConstructor
public class AiUsageReportingService {

    private final AiGenerationEventRepository eventRepository;

    @Transactional(readOnly = true)
    public AiUsageReportResponse getUsageReport(User user, int windowDays) {
        int days = Math.max(1, Math.min(windowDays, 365));
        String organizationId = user.getOrganizationId();
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        long total = eventRepository.countByOrganizationIdAndCreatedAtAfter(organizationId, since);
        long success = eventRepository.countByOrganizationIdAndStatusAndCreatedAtAfter(
                organizationId, AiGenerationStatus.SUCCESS, since);
        long fallbackSuccess = eventRepository.countByOrganizationIdAndStatusAndCreatedAtAfter(
                organizationId, AiGenerationStatus.FALLBACK_SUCCESS, since);
        long failed = eventRepository.countByOrganizationIdAndStatusAndCreatedAtAfter(
                organizationId, AiGenerationStatus.FAILED, since);
        long blocked = eventRepository.countByOrganizationIdAndStatusAndCreatedAtAfter(
                organizationId, AiGenerationStatus.BLOCKED_LIMIT, since);
        long fallbackUsed = eventRepository.countByOrganizationIdAndFallbackUsedTrueAndCreatedAtAfter(
                organizationId, since);

        long succeeded = success + fallbackSuccess;
        // Blocked requests never reached a provider, so they are excluded from the
        // denominator: they measure billing pressure, not provider reliability.
        long attempted = total - blocked;

        BigDecimal cost = eventRepository.sumEstimatedCostSince(organizationId, since);

        return AiUsageReportResponse.builder()
                .windowDays(days)
                .totalRequests(total)
                .successfulRequests(succeeded)
                .failedRequests(failed)
                .blockedByCreditLimit(blocked)
                .fallbackRequests(fallbackUsed)
                .successRate(percentage(succeeded, attempted))
                .fallbackRate(percentage(fallbackUsed, attempted))
                .averageLatencyMs(eventRepository.averageSuccessLatencyMsSince(organizationId, since))
                .estimatedCostUsd(cost != null ? cost : BigDecimal.ZERO)
                .usageByModel(eventRepository.countByProviderAndModelSince(organizationId, since).stream()
                        .map(row -> AiUsageReportResponse.ModelUsage.builder()
                                .provider((String) row[0])
                                .model((String) row[1])
                                .requests(((Number) row[2]).longValue())
                                .build())
                        .toList())
                .topFailureReasons(eventRepository.topFailureReasonsSince(organizationId, since).stream()
                        .limit(10)
                        .map(row -> AiUsageReportResponse.FailureReason.builder()
                                .reason((String) row[0])
                                .occurrences(((Number) row[1]).longValue())
                                .build())
                        .toList())
                .build();
    }

    private double percentage(long part, long whole) {
        if (whole <= 0) {
            return 0.0;
        }
        return Math.round(((double) part / whole) * 1000.0) / 10.0;
    }

    /** Convenience accessor used by the analytics dashboard. */
    @Transactional(readOnly = true)
    public List<Object[]> modelBreakdown(String organizationId, LocalDateTime since) {
        return eventRepository.countByProviderAndModelSince(organizationId, since);
    }
}
