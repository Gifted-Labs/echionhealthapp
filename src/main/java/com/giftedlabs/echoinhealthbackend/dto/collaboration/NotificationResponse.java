package com.giftedlabs.echoinhealthbackend.dto.collaboration;

import com.giftedlabs.echoinhealthbackend.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for collaboration notifications
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private String id;
    private NotificationType type;
    private String title;
    private String message;

    // Sender info
    private String senderId;
    private String senderName;

    // Related entities
    private String sharedScanId;
    private String commentId;

    // Status
    private Boolean isRead;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
