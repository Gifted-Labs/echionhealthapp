package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.org.OrganizationBrandingResponse;
import com.giftedlabs.echoinhealthbackend.dto.org.UpdateOrganizationBrandingRequest;
import com.giftedlabs.echoinhealthbackend.entity.Organization;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException;
import com.giftedlabs.echoinhealthbackend.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrganizationBrandingService {

    private static final long MAX_LETTERHEAD_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/svg+xml");
    private static final String DEFAULT_LETTERHEAD_LABEL = "Echion Health Default Letterhead";

    private final OrganizationRepository organizationRepository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;
    private final BillingService billingService;
    private final FileValidationService fileValidationService;

    @Transactional(readOnly = true)
    public OrganizationBrandingResponse getBranding(User user) {
        return mapToResponse(requireOrganization(user));
    }

    @Transactional
    public OrganizationBrandingResponse updateBranding(User user, UpdateOrganizationBrandingRequest request) {
        Organization organization = requireOrganization(user);
        organization.setHospitalName(request.getHospitalName());
        organization.setAddress(request.getAddress());
        organization.setPhone(request.getPhone());
        organization.setEmail(request.getEmail());
        organization.setWebsite(request.getWebsite());

        Organization saved = organizationRepository.save(organization);
        auditService.logAction(user, "organization_branding_updated", "Updated organization branding profile");
        return mapToResponse(saved);
    }

    @Transactional
    public OrganizationBrandingResponse uploadLetterhead(User user, MultipartFile file) {
        validateLetterhead(file);

        Organization organization = requireOrganization(user);
        billingService.assertStorageCapacity(organization, file.getSize());
        String path = fileStorageService.storeFile(file, organization.getId(), "branding");
        organization.setLetterheadUrl(path);

        Organization saved = organizationRepository.save(organization);
        auditService.logAction(user, "organization_letterhead_uploaded",
                "Uploaded organization letterhead: " + file.getOriginalFilename());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public OrganizationBrandingResponse previewBranding(User user) {
        return mapToResponse(requireOrganization(user));
    }

    public String resolveLetterheadLabel(Organization organization) {
        return organization.getLetterheadUrl() != null && !organization.getLetterheadUrl().isBlank()
                ? "Custom Letterhead"
                : DEFAULT_LETTERHEAD_LABEL;
    }

    private void validateLetterhead(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Letterhead file is required");
        }
        if (file.getSize() > MAX_LETTERHEAD_SIZE_BYTES) {
            throw new IllegalArgumentException("Letterhead file must not exceed 5MB");
        }
        fileValidationService.requireAllowedContentTypeWithDeclaredFallback(file, ALLOWED_CONTENT_TYPES,
                "Letterhead must be PNG, JPG, or SVG");
    }

    private Organization requireOrganization(User user) {
        if (user.getOrganizationId() == null) {
            throw new ResourceNotFoundException("Organization not found");
        }
        return organizationRepository.findById(user.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
    }

    private OrganizationBrandingResponse mapToResponse(Organization organization) {
        boolean usingDefault = organization.getLetterheadUrl() == null || organization.getLetterheadUrl().isBlank();
        return OrganizationBrandingResponse.builder()
                .organizationId(organization.getId())
                .organizationName(organization.getName())
                .hospitalName(organization.getHospitalName())
                .address(organization.getAddress())
                .phone(organization.getPhone())
                .email(organization.getEmail())
                .website(organization.getWebsite())
                .letterheadUrl(organization.getLetterheadUrl())
                .usingDefaultLetterhead(usingDefault)
                .defaultLetterheadLabel(DEFAULT_LETTERHEAD_LABEL)
                .build();
    }
}
