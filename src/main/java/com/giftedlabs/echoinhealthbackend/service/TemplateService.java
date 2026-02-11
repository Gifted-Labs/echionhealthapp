package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.vault.CreateTemplateRequest;
import com.giftedlabs.echoinhealthbackend.dto.vault.TemplateAnalyticsResponse;
import com.giftedlabs.echoinhealthbackend.dto.vault.TemplateResponse;
import com.giftedlabs.echoinhealthbackend.entity.ReportTemplate;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException;
import com.giftedlabs.echoinhealthbackend.repository.ReportTemplateRepository;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static com.giftedlabs.echoinhealthbackend.util.CacheNames.TEMPLATES;

/**
 * Service for managing report templates.
 * Implements caching for template retrieval to reduce database load.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateService {

    private final ReportTemplateRepository templateRepository;
    private final UserRepository userRepository;

    // ========== Write Operations (Cache Evicting) ==========

    /**
     * Create a new report template.
     * Evicts the template cache for this user.
     *
     * @param request Template creation details
     * @param userId  User ID
     * @return Created template response
     */
    @Transactional
    @CacheEvict(value = TEMPLATES, key = "#userId")
    public TemplateResponse createTemplate(CreateTemplateRequest request, String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ReportTemplate template = ReportTemplate.builder()
                .user(user)
                .name(request.getName())
                .description(request.getDescription())
                .gender(request.getGender())
                .reportType(request.getReportType())
                .defaultFindings(request.getDefaultFindings())
                .defaultImpression(request.getDefaultImpression())
                .isDefault(request.getIsDefault())
                .isActive(true)
                .usageCount(0)
                .build();

        // If this is set as default, unset other defaults for same type
        if (request.getIsDefault() && request.getReportType() != null) {
            templateRepository.findDefaultTemplate(userId, request.getReportType())
                    .ifPresent(oldDefault -> {
                        oldDefault.setIsDefault(false);
                        templateRepository.save(oldDefault);
                    });
        }

        ReportTemplate savedTemplate = templateRepository.save(template);
        log.info("Template created: {} for user: {}", savedTemplate.getId(), userId);
        return mapToResponse(savedTemplate);
    }

    /**
     * Duplicate an existing template (UR-032).
     * Creates a copy with a new name.
     *
     * @param templateId Template ID to duplicate
     * @param userId     User ID
     * @param newName    Name for the duplicated template (optional)
     * @return Duplicated template response
     */
    @Transactional
    @CacheEvict(value = TEMPLATES, key = "#userId")
    public TemplateResponse duplicateTemplate(String templateId, String userId, String newName) {
        ReportTemplate original = templateRepository.findByIdAndUserId(templateId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

        String duplicateName = (newName != null && !newName.isEmpty())
                ? newName
                : original.getName() + " (Copy)";

        ReportTemplate duplicate = ReportTemplate.builder()
                .user(original.getUser())
                .name(duplicateName)
                .description(original.getDescription())
                .gender(original.getGender())
                .reportType(original.getReportType())
                .defaultFindings(original.getDefaultFindings())
                .defaultImpression(original.getDefaultImpression())
                .isDefault(false) // Duplicates are never default
                .isActive(true)
                .usageCount(0)
                .build();

        ReportTemplate savedDuplicate = templateRepository.save(duplicate);
        log.info("Template duplicated: {} -> {} for user: {}", templateId, savedDuplicate.getId(), userId);
        return mapToResponse(savedDuplicate);
    }

    /**
     * Record template usage when used to create a report (UR-034).
     *
     * @param templateId Template ID
     */
    @Transactional
    public void recordTemplateUsage(String templateId) {
        templateRepository.findById(templateId).ifPresent(template -> {
            template.recordUsage();
            templateRepository.save(template);
            log.debug("Recorded usage for template: {}", templateId);
        });
    }

    // ========== Read Operations (Cached) ==========

    /**
     * Get all templates available to a user.
     * Results are cached per user.
     *
     * @param userId User ID
     * @return List of template responses
     */
    @Transactional(readOnly = true)
    @Cacheable(value = TEMPLATES, key = "#userId")
    public List<TemplateResponse> getAllTemplates(String userId) {
        log.debug("Cache miss: fetching templates for user: {}", userId);
        return templateRepository.findAllAvailableTemplates(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get template usage analytics (UR-034).
     * Returns templates sorted by usage count.
     *
     * @param userId User ID
     * @return List of template analytics
     */
    @Transactional(readOnly = true)
    public List<TemplateAnalyticsResponse> getTemplateAnalytics(String userId) {
        return templateRepository.findAllAvailableTemplates(userId).stream()
                .sorted((a, b) -> {
                    int countA = a.getUsageCount() != null ? a.getUsageCount() : 0;
                    int countB = b.getUsageCount() != null ? b.getUsageCount() : 0;
                    return Integer.compare(countB, countA); // Descending
                })
                .map(this::mapToAnalytics)
                .collect(Collectors.toList());
    }

    // ========== Delete Operations (Cache Evicting) ==========

    /**
     * Delete a template (soft delete).
     * Evicts the template cache for this user.
     *
     * @param templateId Template ID to delete
     * @param userId     User ID
     */
    @Transactional
    @CacheEvict(value = TEMPLATES, key = "#userId")
    public void deleteTemplate(String templateId, String userId) {
        ReportTemplate template = templateRepository.findByIdAndUserId(templateId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

        // Soft delete
        template.setIsActive(false);
        templateRepository.save(template);
        log.info("Template soft deleted: {} for user: {}", templateId, userId);
    }

    // ========== Private Helper Methods ==========

    private TemplateResponse mapToResponse(ReportTemplate template) {
        return TemplateResponse.builder()
                .id(template.getId())
                .userId(template.getUser() != null ? template.getUser().getId() : null)
                .name(template.getName())
                .description(template.getDescription())
                .gender(template.getGender())
                .reportType(template.getReportType())
                .defaultFindings(template.getDefaultFindings())
                .defaultImpression(template.getDefaultImpression())
                .isDefault(template.getIsDefault())
                .isActive(template.getIsActive())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }

    private TemplateAnalyticsResponse mapToAnalytics(ReportTemplate template) {
        return TemplateAnalyticsResponse.builder()
                .templateId(template.getId())
                .templateName(template.getName())
                .usageCount(template.getUsageCount() != null ? template.getUsageCount() : 0)
                .lastUsedAt(template.getLastUsedAt())
                .createdAt(template.getCreatedAt())
                .build();
    }
}
