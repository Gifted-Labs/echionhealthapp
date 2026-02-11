package com.giftedlabs.echoinhealthbackend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AnalyticsDashboardDTO {
    // Top Key Metrics
    private Double aiReportAcceptanceRate;
    private Double totalHoursSaved;
    private Double productivityGain;
    private Double clinicalErrorReduction;

    // Clinical Quality Impact
    private Double diagnosticDelayReduction;
    private Double reportRevisionRate;

    // Adoption & Efficiency
    private Long dailyActiveUsers; // DAU
    private Double avgReportsPerUser;
    private Double aiUsageRate;

    // Report Outcome Tracking
    private Long totalAiReports;
    private Long aiReportsAccepted;
    private Long aiReportsEdited;
    private Long aiReportsRejected; // calculated or tracked

    // Productivity & Error Signals
    private Double avgReportsPerUserTrend; // +13% etc.
    private Long totalReportsPerDay;
    private Long minorEdits;
    private Long majorEdits;

    // Turnaround Time Performance
    private Double averageTurnaroundTimeMinutes;
    private Boolean slaCompliant;

    // Investment & ROI (Estimated)
    private Double monthlyStaffSavings;
    private Long additionalReportsCapacity;
    private Double revenueImpact;
}
