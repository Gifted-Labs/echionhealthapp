package com.giftedlabs.echoinhealthbackend.dto.vault;

import com.giftedlabs.echoinhealthbackend.entity.Gender;
import com.giftedlabs.echoinhealthbackend.entity.ReportType;
import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for report summary in list views (minimal fields for performance)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportSummaryResponse {

    private String id;

    // Patient Demographics
    private String patientName;
    private Integer patientAge;
    private Gender patientSex;

    // Scan Details
    private LocalDate scanDate;
    private ScanType scanType;
    private ReportType reportType;

    // Brief content
    private String impressionPreview; // First 200 chars of impression

    // Metadata
    private String[] tags;
    private Boolean isFavorite;
    private Boolean hasFile;

    // Timestamps
    private LocalDateTime createdAt;
}
