package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.AnalyticsDashboardDTO;
import com.giftedlabs.echoinhealthbackend.entity.Role;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.repository.AuditLogRepository;
import com.giftedlabs.echoinhealthbackend.repository.ReportRepository;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Analytics dashboard metrics.
 *
 * <p>Everything returned here is computed from stored data, with one clearly labelled
 * estimate. The previous implementation shipped invented numbers — a simulated 12% AI edit
 * rate that then drove both the acceptance and revision rates, plus fixed "productivity gain",
 * "clinical error reduction", monthly staff savings and revenue figures carried over from a
 * design mock. Presenting invented clinical and financial outcomes to hospital administrators
 * as measurements is a liability, so metrics without a real basis are now omitted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private static final String UNAVAILABLE_NOTE =
            "Clinical error reduction, diagnostic delay reduction, staff savings and revenue "
                    + "impact are not reported: computing them requires a pre-adoption baseline "
                    + "and cost data that this platform does not collect.";

    private final ReportRepository reportRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    /**
     * Minutes assumed saved per AI-assisted report. Configuration, not a measurement — it is
     * returned alongside the estimate it produces so the reader can judge it.
     */
    @Value("${analytics.estimated-minutes-saved-per-ai-report:25}")
    private double estimatedMinutesSavedPerAiReport;

    @Transactional(readOnly = true)
    public AnalyticsDashboardDTO getDashboardMetrics(User currentUser) {
        boolean globalAdmin = currentUser.getRole() == Role.ADMIN
                || currentUser.getRole() == Role.SUPER_ADMIN;
        String organizationId = currentUser.getOrganizationId();

        long totalReports = globalAdmin
                ? reportRepository.count()
                : reportRepository.countByOrganizationId(organizationId);
        long aiReports = globalAdmin
                ? reportRepository.countByIsAiGeneratedTrue()
                : reportRepository.countByOrganizationIdAndIsAiGeneratedTrue(organizationId);
        long manualReports = globalAdmin
                ? reportRepository.countByIsAiGeneratedFalse()
                : reportRepository.countByOrganizationIdAndIsAiGeneratedFalse(organizationId);

        // Measured, not assumed: set when a clinician changes the AI's findings, impression or
        // recommendation before finalizing.
        long aiReportsEdited = globalAdmin
                ? reportRepository.countByIsAiGeneratedTrueAndAiOutputEditedTrue()
                : reportRepository.countByOrganizationIdAndIsAiGeneratedTrueAndAiOutputEditedTrue(
                        organizationId);
        long aiReportsAccepted = Math.max(0, aiReports - aiReportsEdited);

        Double averageAiSeconds = globalAdmin
                ? reportRepository.getAverageAiProcessingTime()
                : reportRepository.getAverageAiProcessingTimeByOrganization(organizationId);

        long dailyActiveUsers = globalAdmin
                ? auditLogRepository.countDistinctUsersActiveSince(LocalDateTime.now().minusHours(24))
                : auditLogRepository.countDistinctUsersActiveSinceByOrganization(
                        organizationId, LocalDateTime.now().minusHours(24));
        long totalUsers = globalAdmin
                ? userRepository.count()
                : userRepository.countByOrganizationId(organizationId);

        return AnalyticsDashboardDTO.builder()
                .aiReportAcceptanceRate(percentage(aiReportsAccepted, aiReports))
                .reportRevisionRate(percentage(aiReportsEdited, aiReports))
                .aiUsageRate(percentage(aiReports, totalReports))
                .totalReports(totalReports)
                .totalAiReports(aiReports)
                .totalManualReports(manualReports)
                .aiReportsAccepted(aiReportsAccepted)
                .aiReportsEdited(aiReportsEdited)
                .dailyActiveUsers(dailyActiveUsers)
                .avgReportsPerUser(totalUsers > 0 ? round((double) totalReports / totalUsers) : 0.0)
                .averageAiGenerationSeconds(averageAiSeconds != null ? round(averageAiSeconds) : null)
                .estimatedHoursSaved(round((aiReports * estimatedMinutesSavedPerAiReport) / 60.0))
                .estimatedMinutesSavedPerAiReport(estimatedMinutesSavedPerAiReport)
                .allMetricsMeasured(false)
                .unavailableMetricsNote(UNAVAILABLE_NOTE)
                .build();
    }

    private Double percentage(long part, long whole) {
        if (whole <= 0) {
            return 0.0;
        }
        return round(((double) part / whole) * 100.0);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
