package com.giftedlabs.echoinhealthbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Analytics dashboard payload.
 *
 * <p>Every field here is either measured from stored data or an explicitly labelled estimate.
 * An earlier version returned invented clinical and financial outcomes — a hardcoded 12% AI
 * edit rate, a 32% "productivity gain", a 45% "clinical error reduction", plus fixed monthly
 * staff savings and revenue figures carried over from a design mock — all presented to
 * hospital administrators as measurements. Metrics the platform lacks the baseline data to
 * compute are now omitted rather than fabricated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsDashboardDTO {

    // ---- Measured from stored reports ----
    private Double aiReportAcceptanceRate;
    private Double reportRevisionRate;
    private Double aiUsageRate;
    private Long totalReports;
    private Long totalAiReports;
    private Long totalManualReports;
    private Long aiReportsAccepted;
    private Long aiReportsEdited;
    private Long dailyActiveUsers;
    private Double avgReportsPerUser;
    private Double averageAiGenerationSeconds;

    // ---- Explicitly labelled estimate ----
    /**
     * {@code totalAiReports * estimatedMinutesSavedPerAiReport}. A planning estimate, not a
     * measurement: the product does not time manual reporting, so there is no measured
     * baseline to compare against.
     */
    private Double estimatedHoursSaved;

    /** The assumption behind {@link #estimatedHoursSaved}, surfaced so it can be judged. */
    private Double estimatedMinutesSavedPerAiReport;

    /**
     * False whenever any value above is an estimate rather than a measurement, so the UI can
     * label it instead of presenting everything as observed fact.
     */
    private Boolean allMetricsMeasured;

    /**
     * Why outcome and financial metrics (clinical error reduction, diagnostic delay reduction,
     * staff savings, revenue impact) are absent, rather than an invented number in their place.
     */
    private String unavailableMetricsNote;
}
