package com.giftedlabs.echoinhealthbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.giftedlabs.echoinhealthbackend.entity.Organization;
import com.giftedlabs.echoinhealthbackend.entity.ReportTemplate;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.exception.SharedTemplateDeletionConflictException;
import com.giftedlabs.echoinhealthbackend.repository.ReportTemplateRepository;
import com.giftedlabs.echoinhealthbackend.repository.SharedTemplateRepository;
import com.giftedlabs.echoinhealthbackend.repository.TemplateVersionRepository;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import com.giftedlabs.echoinhealthbackend.util.TextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock
    private ReportTemplateRepository templateRepository;

    @Mock
    private SharedTemplateRepository sharedTemplateRepository;

    @Mock
    private TemplateVersionRepository templateVersionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TextExtractor textExtractor;

    @Mock
    private PhiStrippingService phiStrippingService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private BillingService billingService;

    @Mock
    private FileValidationService fileValidationService;

    @Mock
    private AuditService auditService;

    private TemplateService templateService;

    @BeforeEach
    void setUp() {
        templateService = new TemplateService(
                templateRepository,
                sharedTemplateRepository,
                templateVersionRepository,
                userRepository,
                textExtractor,
                phiStrippingService,
                fileStorageService,
                new ObjectMapper(),
                billingService,
                fileValidationService,
                auditService);
    }

    @Test
    void deleteTemplateBlocksWhenTemplateIsStillShared() {
        User user = user();
        ReportTemplate template = template(user);

        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(templateRepository.findByIdAndUserIdAndOrganizationId("template-1", "user-1", "org-1"))
                .thenReturn(Optional.of(template));
        when(sharedTemplateRepository.countByTemplateIdAndOrganizationId("template-1", "org-1")).thenReturn(2L);

        assertThrows(SharedTemplateDeletionConflictException.class,
                () -> templateService.deleteTemplate("template-1", "user-1", false));

        verify(sharedTemplateRepository, never()).deleteByTemplateId("template-1");
        verify(templateRepository, never()).save(any(ReportTemplate.class));
    }

    @Test
    void deleteTemplateCascadesSharesWhenExplicitlyConfirmed() {
        User user = user();
        ReportTemplate template = template(user);

        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(templateRepository.findByIdAndUserIdAndOrganizationId("template-1", "user-1", "org-1"))
                .thenReturn(Optional.of(template));
        when(sharedTemplateRepository.countByTemplateIdAndOrganizationId("template-1", "org-1")).thenReturn(2L);
        when(templateRepository.save(any(ReportTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(templateVersionRepository.findMaxVersionNumber("template-1")).thenReturn(0);

        templateService.deleteTemplate("template-1", "user-1", true);

        assertFalse(template.getIsActive());
        verify(sharedTemplateRepository).deleteByTemplateId("template-1");
        verify(templateRepository).save(template);
    }

    private User user() {
        Organization organization = Organization.builder().id("org-1").name("Org").build();
        return User.builder()
                .id("user-1")
                .email("owner@example.com")
                .organization(organization)
                .build();
    }

    private ReportTemplate template(User user) {
        return ReportTemplate.builder()
                .id("template-1")
                .organization(user.getOrganization())
                .user(user)
                .name("Shared Template")
                .isActive(true)
                .build();
    }
}
