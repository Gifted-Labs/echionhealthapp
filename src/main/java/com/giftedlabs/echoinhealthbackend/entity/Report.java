package com.giftedlabs.echoinhealthbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Report entity for storing ultrasound reports with full-text search support
 */
@Entity
@Table(name = "reports", indexes = {
        @Index(name = "idx_reports_org_user", columnList = "organization_id,user_id"),
        @Index(name = "idx_reports_user", columnList = "user_id"),
        @Index(name = "idx_reports_scan_date", columnList = "scan_date"),
        @Index(name = "idx_reports_patient_name", columnList = "patient_name")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Patient Demographics
    @Column(name = "patient_name", length = 255)
    private String patientName;

    @Column(name = "patient_age")
    private Integer patientAge;

    @Enumerated(EnumType.STRING)
    @Column(name = "patient_sex", length = 10)
    private Gender patientSex;

    @Column(name = "patient_id", length = 100)
    private String patientId;

    // Scan Details
    @Column(name = "scan_date", nullable = false)
    private LocalDate scanDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_type", length = 100)
    private ScanType scanType;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", length = 100)
    private ReportType reportType;

    // Report Content
    @Column(name = "clinical_history", columnDefinition = "TEXT")
    private String clinicalHistory;

    @Column(name = "findings", columnDefinition = "TEXT", nullable = false)
    private String findings;

    @Column(name = "impression", columnDefinition = "TEXT")
    private String impression;

    @Column(name = "recommendation", columnDefinition = "TEXT")
    private String recommendation;

    @Column(name = "structured_findings", columnDefinition = "TEXT")
    private String structuredFindings;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.ARRAY)
    @Column(name = "recommendation_options")
    private String[] recommendationOptions;

    // File Information (for uploaded documents)
    @Column(name = "original_filename", length = 500)
    private String originalFilename;

    @Column(name = "file_path", length = 1000)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_type", length = 50)
    private String fileType;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", length = 20)
    private StorageType storageType;

    // Search & Metadata
    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    // PostgreSQL full-text search vector
    // This will be populated via database trigger or service layer
    @Column(name = "search_vector", columnDefinition = "tsvector", insertable = false, updatable = false)
    private String searchVector;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.ARRAY)
    @Column(name = "tags")
    private String[] tags;

    @Column(name = "is_favorite")
    @Builder.Default
    private Boolean isFavorite = false;

    // Analytics & Status
    @Column(name = "is_ai_generated")
    @Builder.Default
    private Boolean isAiGenerated = false;

    /**
     * Whether a clinician changed the AI's findings, impression or recommendation before
     * finalizing. This is the measured basis for the AI acceptance and revision rates, which
     * were previously derived from a hardcoded 12% assumption.
     */
    @Column(name = "ai_output_edited", nullable = false)
    @Builder.Default
    private Boolean aiOutputEdited = false;

    @Column(name = "status", length = 20)
    private String status; // e.g., DRAFT, FINAL

    @Column(name = "applied_signature_id", length = 36)
    private String appliedSignatureId;

    @Column(name = "signatory_name", length = 255)
    private String signatoryName;

    @Enumerated(EnumType.STRING)
    @Column(name = "signatory_designation", length = 50)
    private Designation signatoryDesignation;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @Column(name = "processing_time_seconds")
    private Integer processingTimeSeconds;

    @Column(name = "last_auto_save_at")
    private LocalDateTime lastAutoSaveAt;

    // Timestamps
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Get searchable text for full-text indexing
     */
    public String getSearchableText() {
        StringBuilder sb = new StringBuilder();

        if (patientName != null)
            sb.append(patientName).append(" ");
        if (patientId != null)
            sb.append(patientId).append(" ");
        if (clinicalHistory != null)
            sb.append(clinicalHistory).append(" ");
        if (findings != null)
            sb.append(findings).append(" ");
        if (impression != null)
            sb.append(impression).append(" ");
        if (recommendation != null)
            sb.append(recommendation).append(" ");
        if (extractedText != null)
            sb.append(extractedText).append(" ");

        return sb.toString().trim();
    }
}
