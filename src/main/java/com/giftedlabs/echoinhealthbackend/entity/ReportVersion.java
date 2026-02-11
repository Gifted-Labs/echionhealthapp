package com.giftedlabs.echoinhealthbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity for storing report version history.
 * Each update to a report creates a new version snapshot.
 */
@Entity
@Table(name = "report_versions", indexes = {
        @Index(name = "idx_report_versions_report", columnList = "report_id"),
        @Index(name = "idx_report_versions_created", columnList = "created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_report_version", columnNames = { "report_id", "version_number" })
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    /**
     * JSON snapshot of the report data at this version
     */
    @Column(name = "report_data", columnDefinition = "TEXT", nullable = false)
    private String reportData;

    /**
     * User who made this change
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_id")
    private User changedBy;

    /**
     * Description of what changed in this version
     */
    @Column(name = "change_description", length = 500)
    private String changeDescription;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
