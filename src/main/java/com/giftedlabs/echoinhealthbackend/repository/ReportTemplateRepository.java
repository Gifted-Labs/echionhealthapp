package com.giftedlabs.echoinhealthbackend.repository;

import com.giftedlabs.echoinhealthbackend.entity.Gender;
import com.giftedlabs.echoinhealthbackend.entity.ReportTemplate;
import com.giftedlabs.echoinhealthbackend.entity.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportTemplateRepository extends JpaRepository<ReportTemplate, String> {

    // Find templates for a specific user or system templates (userId is null)
    @Query("SELECT t FROM ReportTemplate t WHERE (t.user.id = :userId OR t.user IS NULL) AND t.isActive = true")
    List<ReportTemplate> findAllAvailableTemplates(@Param("userId") String userId);

    // Find templates by criteria
    @Query("SELECT t FROM ReportTemplate t WHERE (t.user.id = :userId OR t.user IS NULL) " +
            "AND t.isActive = true " +
            "AND (:reportType IS NULL OR t.reportType = :reportType) " +
            "AND (:gender IS NULL OR t.gender = :gender OR t.gender IS NULL)")
    List<ReportTemplate> findMatchingTemplates(
            @Param("userId") String userId,
            @Param("reportType") ReportType reportType,
            @Param("gender") Gender gender);

    // Find default template
    @Query("SELECT t FROM ReportTemplate t WHERE (t.user.id = :userId OR t.user IS NULL) " +
            "AND t.isActive = true AND t.isDefault = true AND t.reportType = :reportType")
    Optional<ReportTemplate> findDefaultTemplate(@Param("userId") String userId,
            @Param("reportType") ReportType reportType);

    Optional<ReportTemplate> findByIdAndUserId(String id, String userId);
}
