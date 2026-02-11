package com.giftedlabs.echoinhealthbackend.repository;

import com.giftedlabs.echoinhealthbackend.entity.ReportVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReportVersionRepository extends JpaRepository<ReportVersion, String> {

    /**
     * Get all versions for a report ordered by version number desc
     */
    Page<ReportVersion> findByReportIdOrderByVersionNumberDesc(String reportId, Pageable pageable);

    /**
     * Get a specific version of a report
     */
    Optional<ReportVersion> findByReportIdAndVersionNumber(String reportId, Integer versionNumber);

    /**
     * Get the latest version number for a report
     */
    @Query("SELECT COALESCE(MAX(rv.versionNumber), 0) FROM ReportVersion rv WHERE rv.report.id = :reportId")
    Integer findMaxVersionNumber(@Param("reportId") String reportId);

    /**
     * Count versions for a report
     */
    long countByReportId(String reportId);
}
