package com.giftedlabs.echoinhealthbackend.repository;

import com.giftedlabs.echoinhealthbackend.entity.CollaborationNotification;
import com.giftedlabs.echoinhealthbackend.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CollaborationNotificationRepository extends JpaRepository<CollaborationNotification, String> {

    /**
     * Find notifications for a user, newest first
     */
    Page<CollaborationNotification> findByRecipientIdOrderByCreatedAtDesc(String recipientId, Pageable pageable);

    /**
     * Find unread notifications for a user
     */
    List<CollaborationNotification> findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(String recipientId);

    /**
     * Count unread notifications
     */
    long countByRecipientIdAndIsReadFalse(String recipientId);

    /**
     * Mark all notifications as read for a user
     */
    @Modifying
    @Query("UPDATE CollaborationNotification n SET n.isRead = true, n.readAt = :readAt WHERE n.recipient.id = :recipientId AND n.isRead = false")
    int markAllAsRead(@Param("recipientId") String recipientId, @Param("readAt") LocalDateTime readAt);

    /**
     * Find notifications by type for a user
     */
    List<CollaborationNotification> findByRecipientIdAndType(String recipientId, NotificationType type);

    /**
     * Find recent notifications for SSE streaming (created after a timestamp)
     */
    @Query("SELECT n FROM CollaborationNotification n WHERE n.recipient.id = :recipientId AND n.createdAt > :since ORDER BY n.createdAt ASC")
    List<CollaborationNotification> findNewNotifications(@Param("recipientId") String recipientId,
            @Param("since") LocalDateTime since);
}
