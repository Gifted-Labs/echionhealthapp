package com.giftedlabs.echoinhealthbackend.repository;

import com.giftedlabs.echoinhealthbackend.entity.Gender;
import com.giftedlabs.echoinhealthbackend.entity.ReportTemplate;
import com.giftedlabs.echoinhealthbackend.entity.ReportType;
import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportTemplateRepository extends JpaRepository<ReportTemplate, String> {

    // Find templates for a specific user or system templates (userId is null)
    @Query("""
            SELECT t FROM ReportTemplate t
            WHERE ((t.user.id = :userId AND t.organization.id = :organizationId)
                OR (t.user IS NULL AND t.organization IS NULL))
            AND t.isActive = true
            """)
    List<ReportTemplate> findAllAvailableTemplates(@Param("userId") String userId,
            @Param("organizationId") String organizationId);

    // Find templates by criteria
    @Query("SELECT t FROM ReportTemplate t WHERE ((t.user.id = :userId AND t.organization.id = :organizationId) OR (t.user IS NULL AND t.organization IS NULL)) " +
            "AND t.isActive = true " +
            "AND (:reportType IS NULL OR t.reportType = :reportType) " +
            "AND (:scanType IS NULL OR t.scanType = :scanType OR t.scanType IS NULL) " +
            "AND (:gender IS NULL OR t.gender = :gender OR t.gender IS NULL)")
    List<ReportTemplate> findMatchingTemplates(
            @Param("userId") String userId,
            @Param("organizationId") String organizationId,
            @Param("reportType") ReportType reportType,
            @Param("scanType") ScanType scanType,
            @Param("gender") Gender gender);

    // Find default template
    @Query("SELECT t FROM ReportTemplate t WHERE ((t.user.id = :userId AND t.organization.id = :organizationId) OR (t.user IS NULL AND t.organization IS NULL)) " +
            "AND t.isActive = true AND t.isDefault = true AND (:reportType IS NULL OR t.reportType = :reportType) " +
            "AND (:scanType IS NULL OR t.scanType = :scanType OR t.scanType IS NULL)")
    Optional<ReportTemplate> findDefaultTemplate(@Param("userId") String userId,
            @Param("organizationId") String organizationId,
            @Param("reportType") ReportType reportType,
            @Param("scanType") ScanType scanType);

    Optional<ReportTemplate> findByIdAndUserIdAndOrganizationId(String id, String userId, String organizationId);

    /**
     * Vault search (UR-052), executed in the database.
     *
     * <p>This used to load every template visible to the user, map each one to a DTO, filter in
     * Java streams, sort, and then hand-slice the page — so both the query and the mapping cost
     * scaled with the whole library regardless of page size, which put UR-076 (&lt;2s at 5,000
     * templates) out of reach. Filtering, sorting and paging now happen in one indexed query.
     *
     * <p>Native rather than JPQL because tag filtering needs PostgreSQL array containment
     * ({@code = ANY}), which JPQL cannot express over a {@code String[]} column. The explicit
     * CASTs let PostgreSQL infer parameter types when a filter is left null.
     */
    @Query(value = """
            SELECT t.* FROM report_templates t
            WHERE t.is_active = true
              AND (
                    (t.user_id = :userId AND t.organization_id = :organizationId)
                 OR (t.user_id IS NULL AND t.organization_id IS NULL)
                 OR EXISTS (SELECT 1 FROM shared_templates st
                             WHERE st.template_id = t.id
                               AND st.recipient_id = :userId
                               AND st.organization_id = :organizationId)
              )
              AND (CAST(:scanType AS VARCHAR) IS NULL OR t.scan_type = CAST(:scanType AS VARCHAR))
              AND (CAST(:reportType AS VARCHAR) IS NULL OR t.report_type = CAST(:reportType AS VARCHAR))
              AND (:favoritesOnly = FALSE OR t.is_favorite = TRUE)
              AND (CAST(:category AS VARCHAR) IS NULL
                   OR LOWER(COALESCE(t.category, '')) LIKE LOWER('%' || CAST(:category AS VARCHAR) || '%'))
              AND (CAST(:tag AS VARCHAR) IS NULL
                   OR (t.tags IS NOT NULL AND CAST(:tag AS VARCHAR) = ANY(t.tags)))
              AND (CAST(:createdAfter AS TIMESTAMP) IS NULL OR t.created_at >= CAST(:createdAfter AS TIMESTAMP))
              AND (CAST(:createdBefore AS TIMESTAMP) IS NULL OR t.created_at <= CAST(:createdBefore AS TIMESTAMP))
              AND (CAST(:keyword AS VARCHAR) IS NULL OR (
                     LOWER(t.name) LIKE LOWER('%' || CAST(:keyword AS VARCHAR) || '%')
                  OR LOWER(COALESCE(t.description, '')) LIKE LOWER('%' || CAST(:keyword AS VARCHAR) || '%')
                  OR LOWER(COALESCE(t.default_findings, '')) LIKE LOWER('%' || CAST(:keyword AS VARCHAR) || '%')
                  OR LOWER(COALESCE(t.default_impression, '')) LIKE LOWER('%' || CAST(:keyword AS VARCHAR) || '%')
                  OR LOWER(COALESCE(t.category, '')) LIKE LOWER('%' || CAST(:keyword AS VARCHAR) || '%')
              ))
            """,
            countQuery = """
            SELECT COUNT(*) FROM report_templates t
            WHERE t.is_active = true
              AND (
                    (t.user_id = :userId AND t.organization_id = :organizationId)
                 OR (t.user_id IS NULL AND t.organization_id IS NULL)
                 OR EXISTS (SELECT 1 FROM shared_templates st
                             WHERE st.template_id = t.id
                               AND st.recipient_id = :userId
                               AND st.organization_id = :organizationId)
              )
              AND (CAST(:scanType AS VARCHAR) IS NULL OR t.scan_type = CAST(:scanType AS VARCHAR))
              AND (CAST(:reportType AS VARCHAR) IS NULL OR t.report_type = CAST(:reportType AS VARCHAR))
              AND (:favoritesOnly = FALSE OR t.is_favorite = TRUE)
              AND (CAST(:category AS VARCHAR) IS NULL
                   OR LOWER(COALESCE(t.category, '')) LIKE LOWER('%' || CAST(:category AS VARCHAR) || '%'))
              AND (CAST(:tag AS VARCHAR) IS NULL
                   OR (t.tags IS NOT NULL AND CAST(:tag AS VARCHAR) = ANY(t.tags)))
              AND (CAST(:createdAfter AS TIMESTAMP) IS NULL OR t.created_at >= CAST(:createdAfter AS TIMESTAMP))
              AND (CAST(:createdBefore AS TIMESTAMP) IS NULL OR t.created_at <= CAST(:createdBefore AS TIMESTAMP))
              AND (CAST(:keyword AS VARCHAR) IS NULL OR (
                     LOWER(t.name) LIKE LOWER('%' || CAST(:keyword AS VARCHAR) || '%')
                  OR LOWER(COALESCE(t.description, '')) LIKE LOWER('%' || CAST(:keyword AS VARCHAR) || '%')
                  OR LOWER(COALESCE(t.default_findings, '')) LIKE LOWER('%' || CAST(:keyword AS VARCHAR) || '%')
                  OR LOWER(COALESCE(t.default_impression, '')) LIKE LOWER('%' || CAST(:keyword AS VARCHAR) || '%')
                  OR LOWER(COALESCE(t.category, '')) LIKE LOWER('%' || CAST(:keyword AS VARCHAR) || '%')
              ))
            """,
            nativeQuery = true)
    Page<ReportTemplate> searchTemplates(@Param("userId") String userId,
                                         @Param("organizationId") String organizationId,
                                         @Param("keyword") String keyword,
                                         @Param("scanType") String scanType,
                                         @Param("reportType") String reportType,
                                         @Param("category") String category,
                                         @Param("tag") String tag,
                                         @Param("favoritesOnly") boolean favoritesOnly,
                                         @Param("createdAfter") LocalDateTime createdAfter,
                                         @Param("createdBefore") LocalDateTime createdBefore,
                                         Pageable pageable);

    /**
     * Semantic search: matches a template against <em>any</em> of a set of expanded clinical
     * terms, ranked by how many of them it hits.
     *
     * <p>The tenant and sharing scope is identical to {@link #searchTemplates} and deliberately
     * duplicated rather than factored out — this is the boundary that keeps one hospital out of
     * another's vault, and a shared fragment that someone later edits for one caller would move
     * it for both.
     *
     * <p>Ranking is the count of distinct expanded terms a template matches, not a text-similarity
     * score. A template that mentions both "hydronephrosis" and "pelvicalyceal dilatation" is a
     * better answer for "swollen kidney" than one mentioning either alone, and counting says so
     * without needing a vector.
     */
    @Query(value = """
            SELECT t.* FROM report_templates t
            WHERE t.is_active = true
              AND (
                    (t.user_id = :userId AND t.organization_id = :organizationId)
                 OR (t.user_id IS NULL AND t.organization_id IS NULL)
                 OR EXISTS (SELECT 1 FROM shared_templates st
                             WHERE st.template_id = t.id
                               AND st.recipient_id = :userId
                               AND st.organization_id = :organizationId)
              )
              AND (CAST(:scanType AS VARCHAR) IS NULL OR t.scan_type = CAST(:scanType AS VARCHAR))
              AND EXISTS (
                    SELECT 1 FROM unnest(CAST(:patterns AS text[])) AS p
                    WHERE LOWER(t.name) LIKE p
                       OR LOWER(COALESCE(t.description, '')) LIKE p
                       OR LOWER(COALESCE(t.default_findings, '')) LIKE p
                       OR LOWER(COALESCE(t.default_impression, '')) LIKE p
                       OR LOWER(COALESCE(t.category, '')) LIKE p
              )
            ORDER BY (
                    SELECT COUNT(*) FROM unnest(CAST(:patterns AS text[])) AS p
                    WHERE LOWER(t.name) LIKE p
                       OR LOWER(COALESCE(t.description, '')) LIKE p
                       OR LOWER(COALESCE(t.default_findings, '')) LIKE p
                       OR LOWER(COALESCE(t.default_impression, '')) LIKE p
                       OR LOWER(COALESCE(t.category, '')) LIKE p
            ) DESC, t.updated_at DESC NULLS LAST, t.id
            """,
            countQuery = """
            SELECT COUNT(*) FROM report_templates t
            WHERE t.is_active = true
              AND (
                    (t.user_id = :userId AND t.organization_id = :organizationId)
                 OR (t.user_id IS NULL AND t.organization_id IS NULL)
                 OR EXISTS (SELECT 1 FROM shared_templates st
                             WHERE st.template_id = t.id
                               AND st.recipient_id = :userId
                               AND st.organization_id = :organizationId)
              )
              AND (CAST(:scanType AS VARCHAR) IS NULL OR t.scan_type = CAST(:scanType AS VARCHAR))
              AND EXISTS (
                    SELECT 1 FROM unnest(CAST(:patterns AS text[])) AS p
                    WHERE LOWER(t.name) LIKE p
                       OR LOWER(COALESCE(t.description, '')) LIKE p
                       OR LOWER(COALESCE(t.default_findings, '')) LIKE p
                       OR LOWER(COALESCE(t.default_impression, '')) LIKE p
                       OR LOWER(COALESCE(t.category, '')) LIKE p
              )
            """,
            nativeQuery = true)
    Page<ReportTemplate> semanticSearch(@Param("userId") String userId,
                                        @Param("organizationId") String organizationId,
                                        @Param("scanType") String scanType,
                                        @Param("patterns") String[] patterns,
                                        Pageable pageable);

    /**
     * Counts every template whose blob still exists, including soft-deleted ones. Filtering on
     * {@code isActive} instead would let deleted templates stop counting while their bytes were
     * still sitting in object storage, so reported usage drifted below real consumption.
     */
    @Query("""
            SELECT COALESCE(SUM(t.fileSize), 0) FROM ReportTemplate t
            WHERE t.organization.id = :organizationId AND t.blobDeleted = false
            """)
    long sumFileSizeByOrganizationId(@Param("organizationId") String organizationId);

    /** Templates belonging to one tenant. Platform console tenant drill-down. */
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(t) FROM ReportTemplate t WHERE t.organization.id = :organizationId")
    long countByOrganizationId(@org.springframework.data.repository.query.Param("organizationId") String organizationId);
}
