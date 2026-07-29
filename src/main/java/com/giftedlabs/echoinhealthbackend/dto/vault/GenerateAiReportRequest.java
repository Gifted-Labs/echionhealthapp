package com.giftedlabs.echoinhealthbackend.dto.vault;

import com.giftedlabs.echoinhealthbackend.entity.ReportType;
import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateAiReportRequest {
    @NotBlank(message = "Raw notes are required")
    private String rawNotes;

    @NotNull(message = "Scan type is required")
    private ScanType scanType;

    private ReportType reportType;
    private Map<String, Object> measurements;
    private String provider;
}
