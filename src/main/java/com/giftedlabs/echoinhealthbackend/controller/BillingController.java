package com.giftedlabs.echoinhealthbackend.controller;

import com.giftedlabs.echoinhealthbackend.dto.billing.BillingAddonsRequest;
import com.giftedlabs.echoinhealthbackend.dto.billing.BillingPlanResponse;
import com.giftedlabs.echoinhealthbackend.dto.billing.BillingUsageResponse;
import com.giftedlabs.echoinhealthbackend.dto.billing.UpgradeRequestRequest;
import com.giftedlabs.echoinhealthbackend.dto.billing.UpgradeSubscriptionRequest;
import com.giftedlabs.echoinhealthbackend.dto.common.ApiResponse;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.security.CurrentUserService;
import com.giftedlabs.echoinhealthbackend.service.BillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Subscription and usage APIs.
 *
 * <p>Authority is deliberately split. A tenant admin can read their plan and usage and
 * <em>request</em> a change; only a platform operator ({@code SUPER_ADMIN}) can grant a tier
 * or an add-on. Before this split, any hospital admin could POST themselves onto the top tier
 * with unlimited add-on credits and storage for free, which made every limit in
 * {@link BillingService} advisory.
 */
@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
@Tag(name = "Billing", description = "Subscription tiers, add-ons, and usage APIs")
@PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
public class BillingController {

    private final BillingService billingService;
    private final CurrentUserService currentUserService;

    @GetMapping("/plan")
    @Operation(summary = "Current billing plan",
            description = "Subscription tier and enabled add-ons for the caller's organization")
    public ResponseEntity<ApiResponse<BillingPlanResponse>> getPlan(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                billingService.getPlan(currentUserService.requireUser(authentication))));
    }

    @GetMapping("/usage")
    @Operation(summary = "Billing usage",
            description = "Users, storage, and AI credit usage against plan limits. Crossing an "
                    + "80% threshold also notifies organization admins.")
    public ResponseEntity<ApiResponse<BillingUsageResponse>> getUsage(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                billingService.getUsage(currentUserService.requireUser(authentication))));
    }

    @PostMapping("/upgrade-request")
    @Operation(summary = "Request a plan change",
            description = "Records a tenant's request for a different tier and alerts platform "
                    + "operators. Does not change entitlement.")
    public ResponseEntity<ApiResponse<Void>> requestUpgrade(
            @Valid @RequestBody UpgradeRequestRequest request,
            Authentication authentication) {
        billingService.requestUpgrade(currentUserService.requireUser(authentication), request);
        return ResponseEntity.ok(ApiResponse.success(
                "Upgrade request submitted. Our team will be in touch.", null));
    }

    @PostMapping("/upgrade")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Change an organization's tier (platform operators only)",
            description = "Grants a subscription tier. Rejects downgrades that would leave the "
                    + "organization over the target plan's user or storage limits.")
    public ResponseEntity<ApiResponse<BillingPlanResponse>> changeTier(
            @Valid @RequestBody UpgradeSubscriptionRequest request,
            @RequestParam(required = false) String organizationId,
            Authentication authentication) {
        User actor = currentUserService.requireUser(authentication);
        return ResponseEntity.ok(ApiResponse.success("Subscription updated successfully",
                billingService.changeTier(actor, targetOrganization(actor, organizationId), request)));
    }

    @PostMapping("/addons")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Apply add-ons (platform operators only)",
            description = "Grants extra storage, extra AI credits, or the Lite EMR integration.")
    public ResponseEntity<ApiResponse<BillingPlanResponse>> applyAddons(
            @Valid @RequestBody BillingAddonsRequest request,
            @RequestParam(required = false) String organizationId,
            Authentication authentication) {
        User actor = currentUserService.requireUser(authentication);
        return ResponseEntity.ok(ApiResponse.success("Billing add-ons updated successfully",
                billingService.applyAddons(actor, targetOrganization(actor, organizationId), request)));
    }

    private String targetOrganization(User actor, String organizationId) {
        return organizationId != null && !organizationId.isBlank()
                ? organizationId
                : actor.getOrganizationId();
    }
}
