package com.giftedlabs.echoinhealthbackend.repository;

import com.giftedlabs.echoinhealthbackend.entity.SharedTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for SharedTemplate entity operations (UR-058).
 */
@Repository
public interface SharedTemplateRepository extends JpaRepository<SharedTemplate, String> {

    /**
     * Find all templates shared with a specific user within their organization.
     */
    @Query("""
            SELECT st FROM SharedTemplate st
            JOIN FETCH st.template t
            WHERE st.recipient.id = :recipientId
            AND st.organization.id = :organizationId
            AND t.isActive = true
            """)
    List<SharedTemplate> findByRecipientIdAndOrganizationId(
            @Param("recipientId") String recipientId,
            @Param("organizationId") String organizationId);

    /**
     * Find all shares of a specific template.
     */
    List<SharedTemplate> findByTemplateIdAndOrganizationId(String templateId, String organizationId);

    /**
     * Check if a template is shared with a specific user.
     */
    boolean existsByTemplateIdAndRecipientIdAndOrganizationId(String templateId, String recipientId,
            String organizationId);

    /**
     * Find specific share record.
     */
    Optional<SharedTemplate> findByTemplateIdAndRecipientId(String templateId, String recipientId);

    /**
     * Delete all shares for a template (when template is deleted).
     */
    void deleteByTemplateId(String templateId);

    long countByTemplateIdAndOrganizationId(String templateId, String organizationId);
}
