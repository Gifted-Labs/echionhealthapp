package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.admin.AdminUserResponse;
import com.giftedlabs.echoinhealthbackend.dto.admin.AuditLogResponse;
import com.giftedlabs.echoinhealthbackend.dto.platform.*;
import com.giftedlabs.echoinhealthbackend.entity.*;
import com.giftedlabs.echoinhealthbackend.exception.AccessDeniedException;
import com.giftedlabs.echoinhealthbackend.exception.BusinessException;
import com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException;
import com.giftedlabs.echoinhealthbackend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The super admin's control plane: cross-tenant visibility, tenant administration, and the
 * ability to step into a user's session to test a feature.
 *
 * <p>Everything here is platform-scoped and therefore deliberately separate from
 * {@link AdminService}, which is tenant-scoped and shared with hospital admins. Keeping the two
 * apart means a tenant-facing endpoint can never accidentally inherit a cross-tenant query.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformService {

    /** A tenant is flagged once it passes this share of any quota. */
    private static final int QUOTA_ALERT_THRESHOLD_PERCENT = 80;

    /** Members and activity rows returned inline on a tenant's detail view. */
    private static final int TENANT_DETAIL_PAGE_SIZE = 50;
    private static final int TENANT_ACTIVITY_LIMIT = 25;

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final AuditLogRepository auditLogRepository;
    private final SharedScanRepository sharedScanRepository;
    private final ReportTemplateRepository reportTemplateRepository;
    private final EmailOutboxRepository emailOutboxRepository;

    private final BillingService billingService;
    private final AuditService auditService;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final ImpersonationSessionRegistry impersonationSessionRegistry;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;

    /**
     * How long an impersonated session lasts. Short on purpose, and never renewed: the token is
     * issued without a refresh token so it expires rather than persisting.
     */
    @Value("${app.impersonation.ttl-minutes:30}")
    private int impersonationTtlMinutes;

    @Value("${email.from:}")
    private String configuredFromAddress;

    /** Front-end origin, used for the sign-in link in the onboarding email. */
    @Value("${app.frontend-url:}")
    private String frontendUrl;

    @Value("${app.base-url:}")
    private String baseUrl;

    // ===================================================================== overview

    /**
     * Platform-wide state, including which tenants are close to a limit.
     *
     * <p>Walks every organization to compute entitlement totals. That is acceptable at the tenant
     * counts this product operates at and keeps the numbers exactly consistent with what each
     * tenant's own billing view reports; if tenant count grows by orders of magnitude this becomes
     * the first thing to move to a projection.
     */
    @Transactional(readOnly = true)
    public PlatformOverviewResponse getOverview() {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime sevenDaysAgo = startOfToday.minusDays(7);
        LocalDateTime thirtyDaysAgo = startOfToday.minusDays(30);
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);

        List<Organization> organizations = organizationRepository.findAllForAggregation();

        long storageUsedMb = 0;
        long storageAllocatedMb = 0;
        long aiCreditsAllocated = 0;
        List<PlatformOverviewResponse.TenantQuotaAlert> alerts = new ArrayList<>();

        for (Organization organization : organizations) {
            long usedMb = billingService.currentStorageUsageBytes(organization) / (1024 * 1024);
            int storageLimitMb = organization.getEffectiveStorageLimitMb();
            int seatLimit = organization.getEffectiveUserLimit();
            int creditLimit = organization.getEffectiveAiCreditsPerMonth();
            long activeUsers = userRepository.countByOrganizationIdAndActiveTrue(organization.getId());

            storageUsedMb += usedMb;
            storageAllocatedMb += storageLimitMb;
            aiCreditsAllocated += creditLimit;

            addAlertIfOverThreshold(alerts, organization, "SEATS", activeUsers, seatLimit);
            addAlertIfOverThreshold(alerts, organization, "STORAGE", usedMb, storageLimitMb);
            addAlertIfOverThreshold(alerts, organization, "AI_CREDITS",
                    organization.getAiCreditsUsedThisMonth() != null
                            ? organization.getAiCreditsUsedThisMonth() : 0,
                    creditLimit);
        }

        alerts.sort(Comparator.comparingInt(PlatformOverviewResponse.TenantQuotaAlert::getPercentUsed).reversed());

        Map<String, Long> usersByRole = new HashMap<>();
        for (Role role : Role.values()) {
            usersByRole.put(role.name(), userRepository.countByRole(role));
        }

        Map<String, Long> organizationsByTier = new HashMap<>();
        for (SubscriptionTier tier : SubscriptionTier.values()) {
            organizationsByTier.put(tier.name(), organizationRepository.countBySubscriptionTier(tier));
        }

        return PlatformOverviewResponse.builder()
                .totalOrganizations(organizations.size())
                .activeOrganizations(organizationRepository.countByStatus(OrganizationStatus.ACTIVE))
                .suspendedOrganizations(organizationRepository.countByStatus(OrganizationStatus.SUSPENDED))
                .organizationsOnboardedLast7Days(organizationRepository.countByCreatedAtAfter(sevenDaysAgo))
                .organizationsOnboardedLast30Days(organizationRepository.countByCreatedAtAfter(thirtyDaysAgo))
                .organizationsByTier(organizationsByTier)
                .totalUsers(userRepository.count())
                .activeUsers(userRepository.countByActiveTrue())
                .verifiedUsers(userRepository.countByEmailVerifiedTrue())
                .lockedUsers(userRepository.countByAccountLockedTrue())
                .usersByRole(usersByRole)
                .totalReports(reportRepository.count())
                .reportsToday(reportRepository.countByCreatedAtAfter(startOfToday))
                .reportsLast7Days(reportRepository.countByCreatedAtAfter(sevenDaysAgo))
                .totalSharedScans(sharedScanRepository.count())
                .storageUsedMb(storageUsedMb)
                .storageAllocatedMb(storageAllocatedMb)
                .aiCreditsUsedThisMonth(organizationRepository.sumAiCreditsUsedThisMonth())
                .aiCreditsAllocatedThisMonth(aiCreditsAllocated)
                .failedLoginsToday(auditLogRepository.countByActionAndSuccessFalseAndCreatedAtAfter(
                        "login_failed", startOfToday))
                .failedActionsToday(auditLogRepository.countFailedActionsSince(startOfToday))
                .emailsFailed(emailOutboxRepository.countByStatus(EmailDeliveryStatus.FAILED))
                .emailsPending(emailOutboxRepository.countByStatus(EmailDeliveryStatus.PENDING)
                        + emailOutboxRepository.countByStatus(EmailDeliveryStatus.RETRYING))
                .emailsSentLast24Hours(emailOutboxRepository.countByStatusAndCreatedAtAfter(
                        EmailDeliveryStatus.SENT, twentyFourHoursAgo))
                .quotaAlerts(alerts)
                .build();
    }

    private void addAlertIfOverThreshold(List<PlatformOverviewResponse.TenantQuotaAlert> alerts,
            Organization organization, String quota, long used, long limit) {
        if (limit <= 0) {
            return;
        }
        int percent = (int) Math.min(100, Math.round(used * 100.0 / limit));
        if (percent < QUOTA_ALERT_THRESHOLD_PERCENT) {
            return;
        }

        alerts.add(PlatformOverviewResponse.TenantQuotaAlert.builder()
                .organizationId(organization.getId())
                .organizationName(organization.getName())
                .hospitalName(organization.getHospitalName())
                .subscriptionTier(organization.getSubscriptionTier() != null
                        ? organization.getSubscriptionTier().name() : null)
                .quota(quota)
                .percentUsed(percent)
                .used(used)
                .limit(limit)
                .build());
    }

    // ================================================================= organizations

    /**
     * The tenant list the client asked for when a newly onboarded hospital appeared nowhere.
     */
    @Transactional(readOnly = true)
    public Page<OrganizationSummaryResponse> listOrganizations(String search, SubscriptionTier tier,
            OrganizationStatus status, Pageable pageable) {
        return organizationRepository
                .searchOrganizations(blankToNull(search), tier, status, pageable)
                .map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public OrganizationDetailResponse getOrganization(String organizationId) {
        Organization organization = requireOrganization(organizationId);

        List<AdminUserResponse> users = userRepository
                .searchUsersByOrganization(organizationId, null, null, null, null,
                        PageRequest.of(0, TENANT_DETAIL_PAGE_SIZE))
                .map(this::toAdminUser)
                .getContent();

        List<AuditLogResponse> recentActivity = auditLogRepository
                .findByOrganizationIdOrderByCreatedAtDesc(organizationId,
                        PageRequest.of(0, TENANT_ACTIVITY_LIMIT))
                .map(this::toAuditLog)
                .getContent();

        return OrganizationDetailResponse.builder()
                .organization(toSummary(organization))
                .aiCreditsUsedThisMonth(organization.getAiCreditsUsedThisMonth() != null
                        ? organization.getAiCreditsUsedThisMonth() : 0)
                .aiCreditsLimitThisMonth(organization.getEffectiveAiCreditsPerMonth())
                .liteEmrIntegrationEnabled(Boolean.TRUE.equals(organization.getLiteEmrIntegrationEnabled()))
                .sharedScanCount(sharedScanRepository.countByOrganizationId(organizationId))
                .templateCount(reportTemplateRepository.countByOrganizationId(organizationId))
                .users(users)
                .recentActivity(recentActivity)
                .build();
    }

    /**
     * Provisions a hospital and its first administrator in one step, so an operator can onboard a
     * client without going through self-service registration.
     */
    @Transactional
    public OrganizationSummaryResponse createOrganization(CreateOrganizationRequest request, User actor) {
        if (userRepository.existsByEmail(request.getAdminEmail())) {
            throw new IllegalArgumentException(
                    "A user already exists with email " + request.getAdminEmail());
        }
        if (organizationRepository.findByName(request.getName()).isPresent()) {
            throw new IllegalArgumentException(
                    "An organization named '" + request.getName() + "' already exists");
        }

        Organization organization = organizationRepository.save(Organization.builder()
                .name(request.getName())
                .hospitalName(request.getHospitalName())
                .address(request.getAddress())
                .phone(request.getPhone())
                .email(request.getEmail() != null ? request.getEmail() : request.getAdminEmail())
                .website(request.getWebsite())
                .subscriptionTier(request.getSubscriptionTier() != null
                        ? request.getSubscriptionTier() : SubscriptionTier.BASIC)
                .status(OrganizationStatus.ACTIVE)
                .addonStorageMb(0)
                .addonAiCredits(0)
                .liteEmrIntegrationEnabled(false)
                .aiCreditsUsedThisMonth(0)
                .aiCreditsLastResetAt(LocalDateTime.now())
                .build());

        User admin = userRepository.save(User.builder()
                .organization(organization)
                .email(request.getAdminEmail())
                .passwordHash(passwordEncoder.encode(request.getAdminPassword()))
                .firstName(request.getAdminFirstName())
                .lastName(request.getAdminLastName())
                .phone(request.getPhone())
                .hospitalName(request.getHospitalName())
                .role(Role.HOSPITAL_ADMIN)
                // Operator-provisioned, so the address is taken as confirmed: there is no
                // self-service step in which the recipient would prove it.
                .emailVerified(true)
                .accountLocked(false)
                .active(true)
                .mfaEnabled(false)
                .build());

        emailService.sendOrganizationOnboardedEmail(
                admin.getEmail(),
                admin.getFirstName(),
                organization.getHospitalName(),
                loginUrl(),
                organization.getId());

        auditService.logAction(actor, "platform_organization_created",
                String.format("Provisioned organization %s (%s) with admin %s",
                        organization.getName(), organization.getSubscriptionTier(), admin.getEmail()));

        log.info("Platform admin {} provisioned organization {} with admin {}",
                actor.getEmail(), organization.getId(), admin.getEmail());

        return toSummary(organization);
    }

    /**
     * Suspends or reactivates a tenant. Suspension blocks sign-in for every member and deletes
     * nothing, so it is fully reversible.
     */
    @Transactional
    public OrganizationSummaryResponse updateStatus(String organizationId,
            UpdateOrganizationStatusRequest request, User actor) {
        Organization organization = requireOrganization(organizationId);

        if (organizationId.equals(actor.getOrganizationId())
                && request.getStatus() == OrganizationStatus.SUSPENDED) {
            throw new BusinessException(
                    "You cannot suspend the organization your own account belongs to");
        }

        if (request.getStatus() == OrganizationStatus.SUSPENDED) {
            if (blankToNull(request.getReason()) == null) {
                throw new IllegalArgumentException("A reason is required when suspending an organization");
            }
            organization.setStatus(OrganizationStatus.SUSPENDED);
            organization.setSuspendedAt(LocalDateTime.now());
            organization.setSuspensionReason(request.getReason());
        } else {
            organization.setStatus(OrganizationStatus.ACTIVE);
            organization.setSuspendedAt(null);
            organization.setSuspensionReason(null);
        }

        Organization saved = organizationRepository.save(organization);
        auditService.logAction(actor, "platform_organization_status_changed",
                String.format("Organization %s set to %s%s", saved.getName(), saved.getStatus(),
                        request.getReason() != null ? ": " + request.getReason() : ""));

        return toSummary(saved);
    }

    // ================================================================= impersonation

    /**
     * Issues a token that acts as the target user, so a super admin can test a feature as the
     * people who actually use it.
     *
     * <p>Constrained deliberately: another super admin cannot be impersonated, nor can a locked or
     * deactivated account; the token expires and carries no refresh token; and every action taken
     * under it records both identities in the audit trail.
     */
    @Transactional
    public ImpersonationResponse startImpersonation(String targetUserId, ImpersonationRequest request,
            User actor) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUserId));

        if (target.getId().equals(actor.getId())) {
            throw new BusinessException("You are already signed in as yourself");
        }
        if (target.getRole() == Role.SUPER_ADMIN) {
            throw new AccessDeniedException("A super admin cannot be impersonated");
        }
        if (Boolean.TRUE.equals(target.getAccountLocked())) {
            throw new BusinessException("Cannot impersonate " + target.getEmail() + ": the account is locked");
        }
        if (!Boolean.TRUE.equals(target.getActive())) {
            throw new BusinessException("Cannot impersonate " + target.getEmail() + ": the account is deactivated");
        }

        Duration ttl = Duration.ofMinutes(impersonationTtlMinutes);
        var principal = userDetailsService.loadUserByUsername(target.getEmail());
        String token = jwtService.generateImpersonationToken(
                principal, actor.getId(), actor.getEmail(), ttl.toMillis());
        String impersonationId = jwtService.extractImpersonationId(token);

        impersonationSessionRegistry.register(impersonationId, actor.getId(), target.getId(), ttl);

        auditService.logAction(actor, "impersonation_started",
                String.format("Started impersonating %s (%s) in %s. Reason: %s [impersonationId=%s]",
                        target.getEmail(), target.getRole(),
                        target.getOrganization() != null ? target.getOrganization().getName() : "no organization",
                        request.getReason(), impersonationId));

        log.warn("Super admin {} started impersonating {} — reason: {}",
                actor.getEmail(), target.getEmail(), request.getReason());

        return ImpersonationResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresInSeconds(ttl.toSeconds())
                .impersonationId(impersonationId)
                .targetUserId(target.getId())
                .targetUserEmail(target.getEmail())
                .targetUserFullName(target.getFullName())
                .targetUserRole(target.getRole())
                .targetOrganizationId(target.getOrganizationId())
                .targetOrganizationName(target.getOrganization() != null
                        ? target.getOrganization().getName() : null)
                .impersonatorEmail(actor.getEmail())
                .build();
    }

    /**
     * Ends an impersonation session immediately, before its token would have expired.
     *
     * <p>Callable either by the super admin from their own session, naming the session, or from
     * inside the impersonated session itself, where the id comes from the token.
     */
    @Transactional
    public void stopImpersonation(String impersonationId, String actorEmail) {
        boolean stopped = impersonationSessionRegistry.stop(impersonationId);
        if (!stopped) {
            throw new ResourceNotFoundException("No active impersonation session with that id");
        }

        auditService.logAction(actorEmail, "impersonation_stopped",
                "Ended impersonation session " + impersonationId, true);
        log.info("Impersonation session {} ended by {}", impersonationId, actorEmail);
    }

    // ======================================================================= health

    /** Operational state of the moving parts a super admin is accountable for. */
    @Transactional(readOnly = true)
    public PlatformHealthResponse getHealth() {
        List<PlatformHealthResponse.ComponentHealth> components = new ArrayList<>();

        components.add(check("database", () -> {
            long tenants = organizationRepository.count();
            return "UP:" + tenants + " organizations reachable";
        }));

        components.add(check("storage", () -> {
            StorageType backend = fileStorageService.getCurrentStorageType();
            return "UP:backend " + backend;
        }));

        long failedEmails = emailOutboxRepository.countByStatus(EmailDeliveryStatus.FAILED);
        long pendingEmails = emailOutboxRepository.countByStatus(EmailDeliveryStatus.PENDING)
                + emailOutboxRepository.countByStatus(EmailDeliveryStatus.RETRYING);
        components.add(PlatformHealthResponse.ComponentHealth.builder()
                .name("email")
                .status(failedEmails > 0 ? "DEGRADED" : "UP")
                .detail(String.format("sending from %s; %d failed, %d awaiting retry",
                        configuredFromAddress, failedEmails, pendingEmails))
                .build());

        String overall = components.stream().anyMatch(c -> "DOWN".equals(c.getStatus())) ? "DOWN"
                : components.stream().anyMatch(c -> "DEGRADED".equals(c.getStatus())) ? "DEGRADED"
                        : "UP";

        return PlatformHealthResponse.builder()
                .status(overall)
                .components(components)
                .build();
    }

    private PlatformHealthResponse.ComponentHealth check(String name, ProbeSupplier probe) {
        try {
            String result = probe.probe();
            String[] parts = result.split(":", 2);
            return PlatformHealthResponse.ComponentHealth.builder()
                    .name(name)
                    .status(parts[0])
                    .detail(parts.length > 1 ? parts[1] : null)
                    .build();
        } catch (Exception e) {
            return PlatformHealthResponse.ComponentHealth.builder()
                    .name(name)
                    .status("DOWN")
                    .detail(e.getMessage())
                    .build();
        }
    }

    @FunctionalInterface
    private interface ProbeSupplier {
        String probe() throws Exception;
    }

    /**
     * Sends a probe message and reports what the provider said, so email wiring can be verified
     * from the console rather than by reading logs.
     */
    public EmailTestResponse testEmailDelivery(EmailTestRequest request, User actor) {
        EmailDeliveryService.DeliveryResult result =
                emailService.sendDeliveryTest(request.getRecipient(), actor.getEmail());

        auditService.logAction(actor, "platform_email_test",
                String.format("Delivery test to %s: %s (provider status %s)",
                        request.getRecipient(),
                        result.isDelivered() ? "delivered" : "failed",
                        result.getProviderStatusCode()));

        return EmailTestResponse.builder()
                .delivered(result.isDelivered())
                .recipient(request.getRecipient())
                .sentFrom(configuredFromAddress)
                .providerStatusCode(result.getProviderStatusCode())
                .providerMessageId(result.getProviderMessageId())
                .error(result.getError())
                .outboxEntryId(result.getOutboxEntryId())
                .build();
    }

    // ================================================================ audit

    /**
     * Audit trail across every tenant, including actions taken through an impersonated session.
     *
     * <p>The tenant-scoped audit view cannot answer "what did this super admin do, and where", which
     * is exactly the question an operator needs after an impersonated session.
     */
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> searchAuditLogs(String action, String userEmail, String organizationId,
            Boolean success, boolean impersonatedOnly, LocalDateTime startDate, LocalDateTime endDate,
            Pageable pageable) {
        return auditLogRepository.searchPlatformAuditLogs(
                        blankToNull(action),
                        blankToNull(userEmail),
                        blankToNull(organizationId),
                        success,
                        impersonatedOnly,
                        startDate,
                        endDate,
                        pageable)
                .map(this::toAuditLog);
    }

    // ======================================================================= mapping

    private OrganizationSummaryResponse toSummary(Organization organization) {
        long storageUsedMb = billingService.currentStorageUsageBytes(organization) / (1024 * 1024);

        return OrganizationSummaryResponse.builder()
                .id(organization.getId())
                .name(organization.getName())
                .hospitalName(organization.getHospitalName())
                .email(organization.getEmail())
                .phone(organization.getPhone())
                .address(organization.getAddress())
                .website(organization.getWebsite())
                .subscriptionTier(organization.getSubscriptionTier())
                .status(organization.getStatus())
                .userCount(userRepository.countByOrganizationId(organization.getId()))
                .activeUserCount(userRepository.countByOrganizationIdAndActiveTrue(organization.getId()))
                .seatLimit(organization.getEffectiveUserLimit())
                .reportCount(reportRepository.countByOrganizationId(organization.getId()))
                .storageUsedMb(storageUsedMb)
                .storageLimitMb(organization.getEffectiveStorageLimitMb())
                .hasLetterhead(organization.getLetterheadUrl() != null
                        && !organization.getLetterheadUrl().isBlank())
                .createdAt(organization.getCreatedAt())
                .suspendedAt(organization.getSuspendedAt())
                .suspensionReason(organization.getSuspensionReason())
                .build();
    }

    private AdminUserResponse toAdminUser(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .hospitalName(user.getHospitalName())
                .department(user.getDepartment())
                .role(user.getRole())
                .designation(user.getDesignation())
                .emailVerified(user.getEmailVerified())
                .accountLocked(user.getAccountLocked())
                .active(user.getActive())
                .canUploadSignature(user.getCanUploadSignature())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    private AuditLogResponse toAuditLog(AuditLog auditLog) {
        return AuditLogResponse.builder()
                .id(auditLog.getId())
                .userEmail(auditLog.getUserEmail())
                .action(auditLog.getAction())
                .details(auditLog.getDetails())
                .ipAddress(auditLog.getIpAddress())
                .success(auditLog.getSuccess())
                .errorMessage(auditLog.getErrorMessage())
                .createdAt(auditLog.getCreatedAt())
                .impersonatedByUserId(auditLog.getImpersonatedByUserId())
                .impersonatedByEmail(auditLog.getImpersonatedByEmail())
                .build();
    }

    private Organization requireOrganization(String organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + organizationId));
    }

    private String loginUrl() {
        String base = frontendUrl != null && !frontendUrl.isBlank() ? frontendUrl : baseUrl;
        if (base == null || base.isBlank()) {
            return "/login";
        }
        return base.endsWith("/") ? base + "login" : base + "/login";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
