package com.giftedlabs.echoinhealthbackend.repository;

import com.giftedlabs.echoinhealthbackend.entity.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, String> {

        // Find all reports for a user
        Page<Report> findByUserId(String userId, Pageable pageable);

        // Find favorite reports
        List<Report> findByUserIdAndIsFavoriteTrue(String userId);

        // Find recent reports
        List<Report> findTop10ByUserIdOrderByCreatedAtDesc(String userId);

        // Full-text search with ranking
        // This query uses PostgreSQL's native full-text search capabilities
        // Using plainto_tsquery to handle plain text searches (not formatted tsquery
        // syntax)
        @Query(value = """
                        SELECT r.* FROM reports r
                        WHERE r.user_id = :userId
                        AND (:query IS NULL OR :query = '' OR r.search_vector @@ plainto_tsquery('english', :query))
                        AND (cast(:scanType as text) IS NULL OR r.scan_type = cast(:scanType as text))
                        AND (cast(:reportType as text) IS NULL OR r.report_type = cast(:reportType as text))
                        AND (:patientName IS NULL OR r.patient_name ILIKE CONCAT('%', :patientName, '%'))
                        AND (:favoritesOnly IS FALSE OR r.is_favorite = TRUE)
                        ORDER BY
                            CASE WHEN :query IS NOT NULL AND :query != '' THEN ts_rank(r.search_vector, plainto_tsquery('english', :query)) ELSE 0 END DESC,
                            r.scan_date DESC
                        """, countQuery = """
                        SELECT count(*) FROM reports r
                        WHERE r.user_id = :userId
                        AND (:query IS NULL OR :query = '' OR r.search_vector @@ plainto_tsquery('english', :query))
                        AND (cast(:scanType as text) IS NULL OR r.scan_type = cast(:scanType as text))
                        AND (cast(:reportType as text) IS NULL OR r.report_type = cast(:reportType as text))
                        AND (:patientName IS NULL OR r.patient_name ILIKE CONCAT('%', :patientName, '%'))
                        AND (:favoritesOnly IS FALSE OR r.is_favorite = TRUE)
                        """, nativeQuery = true)
        Page<Report> searchReports(
                        @Param("userId") String userId,
                        @Param("query") String query,
                        @Param("scanType") String scanType,
                        @Param("reportType") String reportType,
                        @Param("patientName") String patientName,
                        @Param("favoritesOnly") Boolean favoritesOnly,
                        Pageable pageable);

        Optional<Report> findByIdAndUserId(String id, String userId);

        // Update search vector for full-text search
        @org.springframework.data.jpa.repository.Modifying
        @Query(value = """
                        UPDATE reports
                        SET search_vector = setweight(to_tsvector('english', coalesce(patient_name,'')), 'A') ||
                                            setweight(to_tsvector('english', coalesce(patient_id,'')), 'A') ||
                                            setweight(to_tsvector('english', coalesce(findings,'')), 'B') ||
                                            setweight(to_tsvector('english', coalesce(impression,'')), 'B') ||
                                            setweight(to_tsvector('english', coalesce(clinical_history,'')), 'C') ||
                                            setweight(to_tsvector('english', coalesce(recommendation,'')), 'C') ||
                                            setweight(to_tsvector('english', coalesce(extracted_text,'')), 'D')
                        WHERE id = :reportId
                        """, nativeQuery = true)
        void updateSearchVector(@Param("reportId") String reportId);
}
