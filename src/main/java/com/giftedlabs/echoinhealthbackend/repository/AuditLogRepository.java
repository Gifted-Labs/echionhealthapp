package com.giftedlabs.echoinhealthbackend.repository;

import com.giftedlabs.echoinhealthbackend.entity.AuditLog;
import com.giftedlabs.echoinhealthbackend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for AuditLog entity operations
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

    /**
     * Find all audit logs for a specific user
     */
    Page<AuditLog> findByUser(User user, Pageable pageable);

    /**
     * Find audit logs by user ID
     */
    Page<AuditLog> findByUserId(String userId, Pageable pageable);

    /**
     * Find audit logs by action type
     */
    List<AuditLog> findByAction(String action);

    /**
     * Find audit logs within a date range
     */
    List<AuditLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Find failed actions for security monitoring
     */
    List<AuditLog> findBySuccessFalse();

    // ========== Admin Methods ==========

    /**
     * Search audit logs with filters
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:action IS NULL OR :action = '' OR a.action = :action)
            AND (:userEmail IS NULL OR :userEmail = '' OR LOWER(a.userEmail) LIKE LOWER(CONCAT('%', :userEmail, '%')))
            AND (:success IS NULL OR a.success = :success)
            AND (:startDate IS NULL OR a.createdAt >= :startDate)
            AND (:endDate IS NULL OR a.createdAt <= :endDate)
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> searchAuditLogs(
            @Param("action") String action,
            @Param("userEmail") String userEmail,
            @Param("success") Boolean success,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    /**
     * Get distinct action types
     */
    @Query("SELECT DISTINCT a.action FROM AuditLog a ORDER BY a.action")
    List<String> findDistinctActions();

    /**
     * Count logs by action
     */
    long countByAction(String action);

    /**
     * Count failed actions in date range
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.success = false AND a.createdAt >= :since")
    long countFailedActionsSince(@Param("since") LocalDateTime since);

    /**
     * Get recent activity (last N hours)
     */
    @Query("SELECT a FROM AuditLog a WHERE a.createdAt >= :since ORDER BY a.createdAt DESC")
    List<AuditLog> findRecentActivity(@Param("since") LocalDateTime since);

    /**
     * Count logs in date range
     */
    long countByCreatedAtAfter(LocalDateTime since);
}
