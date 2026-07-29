package com.giftedlabs.echoinhealthbackend.repository;

import com.giftedlabs.echoinhealthbackend.entity.TemplateVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, String> {

    Page<TemplateVersion> findByTemplateIdAndOrganizationIdOrderByVersionNumberDesc(
            String templateId,
            String organizationId,
            Pageable pageable);

    Optional<TemplateVersion> findByTemplateIdAndOrganizationIdAndVersionNumber(
            String templateId,
            String organizationId,
            Integer versionNumber);

    @Query("SELECT COALESCE(MAX(tv.versionNumber), 0) FROM TemplateVersion tv WHERE tv.template.id = :templateId")
    int findMaxVersionNumber(String templateId);
}
