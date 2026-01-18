package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.collaboration.NotificationResponse;
import com.giftedlabs.echoinhealthbackend.entity.*;
import com.giftedlabs.echoinhealthbackend.repository.CollaborationNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Service for managing collaboration notifications with SSE real-time support
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final CollaborationNotificationRepository notificationRepository;
    private final AuditService auditService;

    // SSE emitters for real-time notifications (userId -> list of emitters)
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    // SSE timeout (30 minutes)
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    // ========== SSE Real-time Notifications ==========

    /**
     * Subscribe to real-time notifications via SSE
     */
    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(e -> removeEmitter(userId, emitter));

        // Send initial connection event
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("Connected to notification stream"));
        } catch (IOException e) {
            log.error("Error sending initial SSE event", e);
        }

        log.info("User {} subscribed to notifications", userId);
        return emitter;
    }

    private void removeEmitter(String userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                userEmitters.remove(userId);
            }
        }
    }

    /**
     * Send real-time notification to a user
     */
    @Async
    public void sendRealTimeNotification(String userId, NotificationResponse notification) {
        CopyOnWriteArrayList<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters != null && !emitters.isEmpty()) {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("notification")
                            .data(notification));
                } catch (IOException e) {
                    log.warn("Failed to send SSE notification to user {}", userId);
                    removeEmitter(userId, emitter);
                }
            }
        }
    }

    // ========== Notification CRUD ==========

    /**
     * Create and send a notification
     */
    @Transactional
    public NotificationResponse createNotification(
            User recipient,
            User sender,
            NotificationType type,
            SharedScan sharedScan,
            ScanComment comment,
            String title,
            String message) {

        CollaborationNotification notification = CollaborationNotification.builder()
                .recipient(recipient)
                .sender(sender)
                .type(type)
                .sharedScan(sharedScan)
                .comment(comment)
                .title(title)
                .message(message)
                .build();

        notification = notificationRepository.save(notification);
        NotificationResponse response = mapToResponse(notification);

        // Send real-time notification
        sendRealTimeNotification(recipient.getId(), response);

        log.info("Notification created: {} for user {}", type, recipient.getEmail());
        return response;
    }

    /**
     * Get paginated notifications for a user
     */
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(String userId, Pageable pageable) {
        return notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToResponse);
    }

    /**
     * Get unread notifications for a user
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadNotifications(String userId) {
        return notificationRepository
                .findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get unread notification count
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(String userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }

    /**
     * Mark a notification as read
     */
    @Transactional
    public NotificationResponse markAsRead(String notificationId, String userId) {
        CollaborationNotification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        if (!notification.getRecipient().getId().equals(userId)) {
            throw new RuntimeException("Not authorized to mark this notification as read");
        }

        notification.setIsRead(true);
        notification.setReadAt(LocalDateTime.now());
        notification = notificationRepository.save(notification);

        return mapToResponse(notification);
    }

    /**
     * Mark all notifications as read for a user
     */
    @Transactional
    public int markAllAsRead(String userId) {
        return notificationRepository.markAllAsRead(userId, LocalDateTime.now());
    }

    // ========== Mapping ==========

    private NotificationResponse mapToResponse(CollaborationNotification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .senderId(notification.getSender() != null ? notification.getSender().getId() : null)
                .senderName(notification.getSender() != null ? notification.getSender().getFullName() : null)
                .sharedScanId(notification.getSharedScan() != null ? notification.getSharedScan().getId() : null)
                .commentId(notification.getComment() != null ? notification.getComment().getId() : null)
                .isRead(notification.getIsRead())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
