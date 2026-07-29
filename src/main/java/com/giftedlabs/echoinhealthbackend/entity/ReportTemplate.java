package com.giftedlabs.echoinhealthbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Report template entity for customizable report templates
 */
@Entity
@Table(name = "report_templates", indexes = {
        @Index(name = "idx_templates_org_user", columnList = "organization_id,user_id"),
        @Index(name = "idx_templates_user", columnList = "user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user; // null for system templates

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // Template Content
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender; // MALE, FEMALE, or null for both

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", length = 100)
    private ReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_type", length = 100)
    private ScanType scanType;

    @Column(name = "category", length = 255)
    private String category;

    @Column(name = "default_findings", columnDefinition = "TEXT")
    private String defaultFindings;

    @Column(name = "default_impression", columnDefinition = "TEXT")
    private String defaultImpression;

    @Column(name = "is_default")
    @Builder.Default
    private Boolean isDefault = false;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Whether PHI has been stripped from this template (UR-049)
     */
    @Column(name = "phi_free")
    @Builder.Default
    private Boolean phiFree = false;

    @Column(name = "is_favorite")
    @Builder.Default
    private Boolean isFavorite = false;

    @Column(name = "source_format", length = 20)
    private String sourceFormat;

    /**
     * Custom tags for categorization (UR-051)
     * e.g., complexity, indication, normal/abnormal
     */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.ARRAY)
    @Column(name = "tags")
    private String[] tags;

    /**
     * Original filename if uploaded from a file (UR-047)
     */
    @Column(name = "original_filename", length = 500)
    private String originalFilename;

    /**
     * File path in storage if uploaded from a file (UR-047)
     */
    @Column(name = "file_path", length = 1000)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    /**
     * Whether the underlying blob has actually been removed from object storage. Soft-deleted
     * templates keep occupying real bytes until this flips, so storage accounting keys off
     * this flag rather than off {@code isActive}.
     */
    @Column(name = "blob_deleted", nullable = false)
    @Builder.Default
    private Boolean blobDeleted = false;

    // Usage Analytics (UR-034)
    @Column(name = "usage_count")
    @Builder.Default
    private Integer usageCount = 0;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Increment usage count and update last used timestamp
     */
    public void recordUsage() {
        this.usageCount = (this.usageCount == null ? 0 : this.usageCount) + 1;
        this.lastUsedAt = LocalDateTime.now();
    }
}
