package com.giftedlabs.echoinhealthbackend.controller;

import com.giftedlabs.echoinhealthbackend.dto.admin.AuditLogResponse;
import com.giftedlabs.echoinhealthbackend.dto.common.ApiResponse;
import com.giftedlabs.echoinhealthbackend.dto.platform.*;
import com.giftedlabs.echoinhealthbackend.entity.OrganizationStatus;
import com.giftedlabs.echoinhealthbackend.entity.SubscriptionTier;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.security.CurrentUserService;
import com.giftedlabs.echoinhealthbackend.security.ImpersonationContext;
import com.giftedlabs.echoinhealthbackend.service.PlatformService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.giftedlabs.echoinhealthbackend.security.RoleGroups;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * The super admin's control plane.
 *
 * <p>Mapped under {@code /admin/platform} so it inherits the {@code /admin/**} rule in the
 * security chain, then narrows to SUPER_ADMIN at the class level. Everything here crosses tenant
 * boundaries by design, which is precisely why no other role may reach it — including
 * {@code HOSPITAL_ADMIN}, who is admitted to {@code /admin/**} generally.
 */
@RestController
@RequestMapping("/admin/platform")
@RequiredArgsConstructor
@Tag(name = "Platform", description = "Super admin control plane: tenants, impersonation, health")
@PreAuthorize(RoleGroups.SUPER_ADMIN)
public class PlatformController {

    private final PlatformService platformService;
    private final CurrentUserService currentUserService;

    // ===================================================================== overview

    @GetMapping("/overview")
    @Operation(summary = "Platform overview",
            description = "Cross-tenant totals, capacity sold against consumed, and tenants near a quota")
    public ResponseEntity<ApiResponse<PlatformOverviewResponse>> getOverview() {
        return ResponseEntity.ok(ApiResponse.success(platformService.getOverview()));
    }

    // ================================================================ organizations

    @GetMapping("/organizations")
    @Operation(summary = "List organizations",
            description = "Every hospital on the platform, newest first, searchable and filterable")
    public ResponseEntity<ApiResponse<Page<OrganizationSummaryResponse>>> listOrganizations(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SubscriptionTier tier,
            @RequestParam(required = false) OrganizationStatus status,
            Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                platformService.listOrganizations(search, tier, status, pageable)));
    }

    @GetMapping("/organizations/{id}")
    @Operation(summary = "Organization detail",
            description = "Members, plan, usage and recent activity for one hospital")
    public ResponseEntity<ApiResponse<OrganizationDetailResponse>> getOrganization(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(platformService.getOrganization(id)));
    }

    @PostMapping("/organizations")
    @Operation(summary = "Create organization",
            description = "Provision a hospital and its first administrator without self-service registration")
    public ResponseEntity<ApiResponse<OrganizationSummaryResponse>> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request,
            Authentication authentication) {

        OrganizationSummaryResponse created =
                platformService.createOrganization(request, actor(authentication));
        return ResponseEntity.ok(ApiResponse.success("Organization provisioned successfully", created));
    }

    @PatchMapping("/organizations/{id}/status")
    @Operation(summary = "Suspend or reactivate",
            description = "Block or restore sign-in for every member of a hospital. Nothing is deleted.")
    public ResponseEntity<ApiResponse<OrganizationSummaryResponse>> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateOrganizationStatusRequest request,
            Authentication authentication) {

        OrganizationSummaryResponse updated =
                platformService.updateStatus(id, request, actor(authentication));
        return ResponseEntity.ok(ApiResponse.success(
                "Organization is now " + updated.getStatus(), updated));
    }

    // ================================================================= impersonation

    @PostMapping("/impersonate/{userId}")
    @Operation(summary = "Impersonate a user",
            description = "Issue a short-lived, non-renewable token that acts as the target user. Fully audited.")
    public ResponseEntity<ApiResponse<ImpersonationResponse>> impersonate(
            @PathVariable String userId,
            @Valid @RequestBody ImpersonationRequest request,
            Authentication authentication) {

        ImpersonationResponse response =
                platformService.startImpersonation(userId, request, actor(authentication));
        return ResponseEntity.ok(ApiResponse.success(
                "Impersonating " + response.getTargetUserEmail(), response));
    }

    /**
     * Ends an impersonation session.
     *
     * <p>Reachable two ways: the super admin names the session from their own token, or the
     * impersonated session ends itself, in which case the id is taken from the token in play. The
     * second path needs no SUPER_ADMIN role — the caller is acting as a clinician at that moment —
     * so the class-level rule is relaxed here and the service still requires a live session id.
     */
    @PostMapping("/impersonate/stop")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Stop impersonating",
            description = "End an impersonation session immediately, before its token would expire")
    public ResponseEntity<ApiResponse<Void>> stopImpersonation(
            @RequestParam(required = false) String impersonationId,
            Authentication authentication) {

        User caller = actor(authentication);
        ImpersonationContext.Details active = ImpersonationContext.get();

        String sessionId = impersonationId != null && !impersonationId.isBlank()
                ? impersonationId
                : active != null ? active.impersonationId() : null;

        if (sessionId == null) {
            throw new com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException(
                    "No impersonation session to stop. Pass impersonationId, or call this from the impersonated session.");
        }

        // The real actor is the super admin behind the session, not the borrowed identity.
        String actingEmail = active != null ? active.impersonatorEmail() : caller.getEmail();
        platformService.stopImpersonation(sessionId, actingEmail);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Impersonation ended")
                .build());
    }

    // ======================================================================= health

    @GetMapping("/health")
    @Operation(summary = "System health",
            description = "Database, storage backend and email provider state")
    public ResponseEntity<ApiResponse<PlatformHealthResponse>> getHealth() {
        return ResponseEntity.ok(ApiResponse.success(platformService.getHealth()));
    }

    @PostMapping("/email/test")
    @Operation(summary = "Test email delivery",
            description = "Send a probe message and report exactly what the provider said")
    public ResponseEntity<ApiResponse<EmailTestResponse>> testEmail(
            @Valid @RequestBody EmailTestRequest request,
            Authentication authentication) {

        EmailTestResponse result = platformService.testEmailDelivery(request, actor(authentication));
        return ResponseEntity.ok(ApiResponse.success(
                result.isDelivered() ? "Test message delivered" : "Test message was rejected by the provider",
                result));
    }

    // ======================================================================== audit

    @GetMapping("/audit")
    @Operation(summary = "Cross-tenant audit log",
            description = "Audit trail across every hospital, including actions taken while impersonating")
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> searchAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) String organizationId,
            @RequestParam(required = false) Boolean success,
            @RequestParam(defaultValue = "false") boolean impersonatedOnly,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(platformService.searchAuditLogs(
                action, userEmail, organizationId, success, impersonatedOnly, startDate, endDate, pageable)));
    }

    // ======================================================================= helper

    private User actor(Authentication authentication) {
        return currentUserService.requireUser(authentication);
    }
}
