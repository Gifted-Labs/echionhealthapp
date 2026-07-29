package com.giftedlabs.echoinhealthbackend.controller;

import com.giftedlabs.echoinhealthbackend.dto.common.ApiResponse;
import com.giftedlabs.echoinhealthbackend.dto.org.OrganizationBrandingResponse;
import com.giftedlabs.echoinhealthbackend.dto.org.UpdateOrganizationBrandingRequest;
import com.giftedlabs.echoinhealthbackend.security.CurrentUserService;
import com.giftedlabs.echoinhealthbackend.service.OrganizationBrandingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/org")
@RequiredArgsConstructor
@Tag(name = "Organization Branding", description = "Hospital branding and letterhead APIs")
@PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
public class OrganizationBrandingController {

    private final OrganizationBrandingService organizationBrandingService;
    private final CurrentUserService currentUserService;

    @GetMapping("/branding")
    @Operation(summary = "Get organization branding", description = "Fetch current hospital profile and letterhead configuration")
    public ResponseEntity<ApiResponse<OrganizationBrandingResponse>> getBranding(Authentication authentication) {
        OrganizationBrandingResponse response = organizationBrandingService.getBranding(
                currentUserService.requireUser(authentication));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/branding")
    @Operation(summary = "Update organization branding", description = "Update hospital profile fields used in report branding")
    public ResponseEntity<ApiResponse<OrganizationBrandingResponse>> updateBranding(
            @Valid @org.springframework.web.bind.annotation.RequestBody UpdateOrganizationBrandingRequest request,
            Authentication authentication) {
        OrganizationBrandingResponse response = organizationBrandingService.updateBranding(
                currentUserService.requireUser(authentication),
                request);
        return ResponseEntity.ok(ApiResponse.success("Branding profile updated successfully", response));
    }

    @PostMapping(value = "/letterhead", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload letterhead", description = "Upload a hospital letterhead/logo image for report branding")
    public ResponseEntity<ApiResponse<OrganizationBrandingResponse>> uploadLetterhead(
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        OrganizationBrandingResponse response = organizationBrandingService.uploadLetterhead(
                currentUserService.requireUser(authentication),
                file);
        return ResponseEntity.ok(ApiResponse.success("Letterhead uploaded successfully", response));
    }

    @GetMapping("/letterhead/preview")
    @Operation(summary = "Preview letterhead", description = "Preview the hospital branding that will be applied to generated reports")
    public ResponseEntity<ApiResponse<OrganizationBrandingResponse>> preview(Authentication authentication) {
        OrganizationBrandingResponse response = organizationBrandingService.previewBranding(
                currentUserService.requireUser(authentication));
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
