package com.giftedlabs.echoinhealthbackend.repository;

import com.giftedlabs.echoinhealthbackend.entity.AiGenerationEvent;
import com.giftedlabs.echoinhealthbackend.entity.AiGenerationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AiGenerationEventRepository extends JpaRepository<AiGenerationEvent, String> {

    Page<AiGenerationEvent> findByOrganizationIdOrderByCreatedAtDesc(String organizationId, Pageable pageable);

    long countByOrganizationIdAndStatusAndCreatedAtAfter(
            String organizationId, AiGenerationStatus status, LocalDateTime after);

    long countByOrganizationIdAndCreatedAtAfter(String organizationId, LocalDateTime after);

    long countByOrganizationIdAndFallbackUsedTrueAndCreatedAtAfter(
            String organizationId, LocalDateTime after);

    @Query("""
            SELECT COALESCE(SUM(e.estimatedCostUsd), 0)
            FROM AiGenerationEvent e
            WHERE e.organization.id = :organizationId AND e.createdAt >= :after
            """)
    BigDecimal sumEstimatedCostSince(@Param("organizationId") String organizationId,
                                     @Param("after") LocalDateTime after);

    @Query("""
            SELECT AVG(e.latencyMs) FROM AiGenerationEvent e
            WHERE e.organization.id = :organizationId
              AND e.status IN (com.giftedlabs.echoinhealthbackend.entity.AiGenerationStatus.SUCCESS,
                               com.giftedlabs.echoinhealthbackend.entity.AiGenerationStatus.FALLBACK_SUCCESS)
              AND e.createdAt >= :after
            """)
    Double averageSuccessLatencyMsSince(@Param("organizationId") String organizationId,
                                        @Param("after") LocalDateTime after);

    @Query("""
            SELECT e.provider, e.model, COUNT(e)
            FROM AiGenerationEvent e
            WHERE e.organization.id = :organizationId AND e.createdAt >= :after
              AND e.provider IS NOT NULL
            GROUP BY e.provider, e.model
            ORDER BY COUNT(e) DESC
            """)
    List<Object[]> countByProviderAndModelSince(@Param("organizationId") String organizationId,
                                                @Param("after") LocalDateTime after);

    @Query("""
            SELECT e.failureReason, COUNT(e)
            FROM AiGenerationEvent e
            WHERE e.organization.id = :organizationId AND e.createdAt >= :after
              AND e.status = com.giftedlabs.echoinhealthbackend.entity.AiGenerationStatus.FAILED
              AND e.failureReason IS NOT NULL
            GROUP BY e.failureReason
            ORDER BY COUNT(e) DESC
            """)
    List<Object[]> topFailureReasonsSince(@Param("organizationId") String organizationId,
                                          @Param("after") LocalDateTime after);
}
