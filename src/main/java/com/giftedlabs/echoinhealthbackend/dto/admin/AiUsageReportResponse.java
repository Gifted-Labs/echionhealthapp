package com.giftedlabs.echoinhealthbackend.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Operational view of AI usage, derived entirely from recorded generation events.
 * These events were previously written on every call and never read by anything.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsageReportResponse {

    private int windowDays;
    private long totalRequests;
    private long successfulRequests;
    private long failedRequests;
    private long blockedByCreditLimit;
    private long fallbackRequests;
    private double successRate;
    private double fallbackRate;
    private Double averageLatencyMs;
    private BigDecimal estimatedCostUsd;
    private List<ModelUsage> usageByModel;
    private List<FailureReason> topFailureReasons;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelUsage {
        private String provider;
        private String model;
        private long requests;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailureReason {
        private String reason;
        private long occurrences;
    }
}
