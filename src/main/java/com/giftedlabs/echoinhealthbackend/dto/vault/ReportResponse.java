package com.giftedlabs.echoinhealthbackend.dto.vault;

import com.giftedlabs.echoinhealthbackend.entity.Gender;
import com.giftedlabs.echoinhealthbackend.entity.ReportType;
import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import com.giftedlabs.echoinhealthbackend.entity.StorageType;
import com.giftedlabs.echoinhealthbackend.entity.Designation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for complete report response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {

    private String id;
    private String userId;

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

    // File Information
    private String originalFilename;
    private String filePath;
    private Long fileSize;
    private String fileType;
    private StorageType storageType;
    private String downloadUrl; // Presigned URL for download

    // Metadata
    private String[] tags;
    private Boolean isFavorite;
    private String status;
    private String appliedSignatureId;
    private String signatoryName;
    private Designation signatoryDesignation;
    private LocalDateTime finalizedAt;
    private LocalDateTime lastAutoSaveAt;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
