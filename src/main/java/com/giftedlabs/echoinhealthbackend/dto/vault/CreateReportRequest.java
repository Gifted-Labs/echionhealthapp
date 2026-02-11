package com.giftedlabs.echoinhealthbackend.dto.vault;

import com.giftedlabs.echoinhealthbackend.entity.Gender;
import com.giftedlabs.echoinhealthbackend.entity.ReportType;
import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for creating a new report from form data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReportRequest {

    // Patient Demographics
    private String patientName;

    @Positive(message = "Patient age must be positive")
    private Integer patientAge;

    private Gender patientSex;

    private String patientId;

    // Scan Details
    @NotNull(message = "Scan date is required")
    @PastOrPresent(message = "Scan date cannot be in the future")
    private LocalDate scanDate;

    private ScanType scanType;

    private ReportType reportType;

    // Report Content
    private String clinicalHistory;

    @NotBlank(message = "Findings are required")
    private String findings;

    private String impression;

    private String recommendation;

    // Metadata
    private String[] tags;

    // Template ID (for usage analytics tracking)
    private String templateId;
}
