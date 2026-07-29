package com.giftedlabs.echoinhealthbackend.dto.vault;

import com.giftedlabs.echoinhealthbackend.entity.Designation;
import com.giftedlabs.echoinhealthbackend.entity.Gender;
import com.giftedlabs.echoinhealthbackend.entity.ReportType;
import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for pre-finalization report preview (UR-040).
 * Contains everything the client needs to render a full preview
 * of the finalized report without actually committing the finalization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviewReportResponse {

    // Report ID
    private String reportId;

    // Organization Branding
    private String hospitalName;
    private String hospitalAddress;
    private String hospitalPhone;
    private String hospitalEmail;
    private String hospitalWebsite;
    private String letterheadUrl;
    private boolean usingDefaultLetterhead;

    // Patient Demographics
    private String patientName;
    private Integer patientAge;
    private Gender patientSex;
    private String patientId;

    // Scan Details
    private LocalDate scanDate;
    private ScanType scanType;
    private ReportType reportType;

    // Report Content
    private String clinicalHistory;
    private String findings;
    private String impression;
    private String recommendation;
    private Map<String, Object> structuredFindings;
    private String[] recommendationOptions;

    // Signature Preview
    private String signatureId;
    private String signatureLabel;
    private boolean signatureImageAvailable;
    private String signatoryName;
    private Designation signatoryDesignation;

    // Validation Status
    private boolean readyToFinalize;
    private Map<String, String> validationErrors;

    // Metadata
    private LocalDateTime previewGeneratedAt;
}
