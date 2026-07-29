package com.giftedlabs.echoinhealthbackend.repository;

import com.giftedlabs.echoinhealthbackend.entity.Organization;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, String> {

    Optional<Organization> findByName(String name);

    /**
     * Serializes tenant quota decisions. Storage usage is computed as a SUM across several
     * tables, so a plain read-then-write leaves a window in which two concurrent uploads both
     * observe headroom and both commit. Callers take this lock inside their transaction and
     * hold it until the new row is committed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Organization o WHERE o.id = :id")
    Optional<Organization> findByIdForUpdate(@Param("id") String id);

    /**
     * Consumes AI credits in a single conditional statement, so the limit check and the
     * increment cannot interleave. Returns 0 when the increment would breach the limit — no
     * row is modified in that case, which the caller treats as "limit exceeded".
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE organizations
               SET ai_credits_used_this_month = ai_credits_used_this_month + :credits
             WHERE id = :organizationId
               AND ai_credits_used_this_month + :credits <= :creditLimit
            """, nativeQuery = true)
    int consumeAiCreditsAtomically(@Param("organizationId") String organizationId,
                                   @Param("credits") int credits,
                                   @Param("creditLimit") int creditLimit);

    /**
     * Rolls the monthly credit window forward exactly once per period, no matter how many
     * requests race to do it. The period boundary is passed in rather than computed in SQL so
     * the statement stays portable across PostgreSQL and the H2 test database.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE organizations
               SET ai_credits_used_this_month = 0,
                   ai_credits_last_reset_at = :now
             WHERE id = :organizationId
               AND (ai_credits_last_reset_at IS NULL OR ai_credits_last_reset_at < :periodStart)
            """, nativeQuery = true)
    int resetAiCreditsIfPeriodElapsed(@Param("organizationId") String organizationId,
                                      @Param("periodStart") LocalDateTime periodStart,
                                      @Param("now") LocalDateTime now);

    /**
     * Returns a reserved credit when the generation it was reserved for did not produce a
     * usable result, so failed provider calls are never billed to the tenant.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE organizations
               SET ai_credits_used_this_month =
                   CASE WHEN ai_credits_used_this_month - :credits < 0
                        THEN 0 ELSE ai_credits_used_this_month - :credits END
             WHERE id = :organizationId
            """, nativeQuery = true)
    int refundAiCredits(@Param("organizationId") String organizationId,
                        @Param("credits") int credits);

    /**
     * Records which usage-alert thresholds an organization has been notified about, so admins
     * are alerted when a threshold is first crossed rather than on every usage read.
     * Returns 0 when the signature is unchanged, meaning no new alert is due.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE organizations
               SET last_usage_alert_signature = :signature,
                   last_usage_alert_at = :now
             WHERE id = :organizationId
               AND (last_usage_alert_signature IS NULL OR last_usage_alert_signature <> :signature)
            """, nativeQuery = true)
    int recordUsageAlertIfChanged(@Param("organizationId") String organizationId,
                                  @Param("signature") String signature,
                                  @Param("now") LocalDateTime now);
}
