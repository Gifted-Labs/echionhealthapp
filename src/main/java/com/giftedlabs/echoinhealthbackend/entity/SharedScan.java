package com.giftedlabs.echoinhealthbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity for shared scans in the collaboration space.
 * Allows sonographers to share scans for peer review and expert consultation.
 */
@Entity
@Table(name = "shared_scans", indexes = {
        @Index(name = "idx_shared_scans_owner", columnList = "owner_id"),
        @Index(name = "idx_shared_scans_report", columnList = "report_id"),
        @Index(name = "idx_shared_scans_status", columnList = "status"),
        @Index(name = "idx_shared_scans_created", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharedScan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * The original report being shared
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    /**
     * User who shared the scan
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /**
     * Level of sharing (specific colleagues, department, facility)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "sharing_level", nullable = false, length = 30)
    private SharingLevel sharingLevel;

    /**
     * Current status in the collaboration workflow
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SharedScanStatus status = SharedScanStatus.PENDING_REVIEW;

    /**
     * Title/subject for the collaboration request
     */
    @Column(length = 255)
    private String title;

    /**
     * Message from owner explaining what feedback is needed
     */
    @Column(columnDefinition = "TEXT")
    private String requestMessage;

    /**
     * Users who have access (for SPECIFIC_COLLEAGUES sharing level)
     */
    @OneToMany(mappedBy = "sharedScan", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SharedScanAccess> accessList = new ArrayList<>();

    /**
     * Comments/feedback on this shared scan
     */
    @OneToMany(mappedBy = "sharedScan", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ScanComment> comments = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * When the owner marked this as resolved
     */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /**
     * Resolution notes from owner
     */
    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;
}
