package com.giftedlabs.echoinhealthbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity for real-time collaboration notifications.
 * Tracks notifications for shared scans, comments, and resolutions.
 */
@Entity
@Table(name = "collaboration_notifications", indexes = {
        @Index(name = "idx_notif_recipient", columnList = "recipient_id"),
        @Index(name = "idx_notif_read", columnList = "is_read"),
        @Index(name = "idx_notif_created", columnList = "created_at"),
        @Index(name = "idx_notif_type", columnList = "type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollaborationNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * User who receives the notification
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    /**
     * User who triggered the notification (e.g., commenter, sharer)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private User sender;

    /**
     * Type of notification
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    /**
     * Related shared scan (if applicable)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_scan_id")
    private SharedScan sharedScan;

    /**
     * Related comment (if applicable)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    private ScanComment comment;

    /**
     * Notification title/headline
     */
    @Column(nullable = false, length = 255)
    private String title;

    /**
     * Notification message body
     */
    @Column(columnDefinition = "TEXT")
    private String message;

    /**
     * Whether the notification has been read
     */
    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    /**
     * When the notification was read
     */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
