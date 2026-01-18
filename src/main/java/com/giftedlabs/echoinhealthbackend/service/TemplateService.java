package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.vault.CreateTemplateRequest;
import com.giftedlabs.echoinhealthbackend.dto.vault.TemplateResponse;
import com.giftedlabs.echoinhealthbackend.entity.ReportTemplate;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException;
import com.giftedlabs.echoinhealthbackend.repository.ReportTemplateRepository;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final ReportTemplateRepository templateRepository;
    private final UserRepository userRepository;

    @Transactional
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
        return mapToResponse(savedTemplate);
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> getAllTemplates(String userId) {
        return templateRepository.findAllAvailableTemplates(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteTemplate(String templateId, String userId) {
        ReportTemplate template = templateRepository.findByIdAndUserId(templateId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

        // Soft delete
        template.setIsActive(false);
        templateRepository.save(template);
    }

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
}
