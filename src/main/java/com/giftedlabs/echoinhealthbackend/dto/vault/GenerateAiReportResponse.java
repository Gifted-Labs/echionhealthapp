package com.giftedlabs.echoinhealthbackend.dto.vault;

import com.giftedlabs.echoinhealthbackend.entity.ReportType;
import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateAiReportResponse {
    private String findings;
    private String impression;
    private String recommendation;
    private List<String> recommendationOptions;
    private Map<String, Object> structuredFindings;
    private ScanType scanType;
    private ReportType reportType;
    private String provider;
    private String model;
    private Boolean fallbackUsed;
    private String promptTemplateVersion;
    private Integer aiCreditsConsumed;
    private Integer processingTimeSeconds;
    private LocalDateTime generatedAt;
}
