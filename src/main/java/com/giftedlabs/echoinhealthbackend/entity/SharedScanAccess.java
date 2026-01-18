package com.giftedlabs.echoinhealthbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity to track which users have access to a specific shared scan.
 * Used for SPECIFIC_COLLEAGUES sharing level.
 */
@Entity
@Table(name = "shared_scan_access", indexes = {
        @Index(name = "idx_access_shared_scan", columnList = "shared_scan_id"),
        @Index(name = "idx_access_user", columnList = "user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharedScanAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_scan_id", nullable = false)
    private SharedScan sharedScan;

    /**
     * User who has been granted access
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * When access was granted
     */
    @CreationTimestamp
    @Column(name = "granted_at", nullable = false, updatable = false)
    private LocalDateTime grantedAt;

    /**
     * When user first viewed the shared scan (null if not viewed yet)
     */
    @Column(name = "viewed_at")
    private LocalDateTime viewedAt;
}
