package com.giftedlabs.echoinhealthbackend.repository;

import com.giftedlabs.echoinhealthbackend.entity.EmailDeliveryStatus;
import com.giftedlabs.echoinhealthbackend.entity.EmailOutboxEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EmailOutboxRepository extends JpaRepository<EmailOutboxEntry, String> {

    long countByStatus(EmailDeliveryStatus status);

    long countByStatusAndCreatedAtAfter(EmailDeliveryStatus status, LocalDateTime after);

    Page<EmailOutboxEntry> findByStatusOrderByCreatedAtDesc(EmailDeliveryStatus status, Pageable pageable);

    Page<EmailOutboxEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Work queue for the retry sweep: unfinished entries whose last attempt is old enough to try
     * again. Ordered oldest-first so a backlog drains in the order it accumulated.
     */
    @Query("""
            SELECT e FROM EmailOutboxEntry e
             WHERE e.status IN (com.giftedlabs.echoinhealthbackend.entity.EmailDeliveryStatus.PENDING,
                                com.giftedlabs.echoinhealthbackend.entity.EmailDeliveryStatus.RETRYING)
               AND (e.lastAttemptAt IS NULL OR e.lastAttemptAt < :retryBefore)
             ORDER BY e.createdAt ASC
            """)
    List<EmailOutboxEntry> findRetryable(@Param("retryBefore") LocalDateTime retryBefore, Pageable pageable);
}
