package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.billing.BillingAddonsRequest;
import com.giftedlabs.echoinhealthbackend.dto.billing.BillingPlanResponse;
import com.giftedlabs.echoinhealthbackend.dto.billing.BillingUsageResponse;
import com.giftedlabs.echoinhealthbackend.dto.billing.UpgradeRequestRequest;
import com.giftedlabs.echoinhealthbackend.dto.billing.UpgradeSubscriptionRequest;
import com.giftedlabs.echoinhealthbackend.entity.Organization;
import com.giftedlabs.echoinhealthbackend.entity.SubscriptionTier;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.exception.BusinessException;
import com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException;
import com.giftedlabs.echoinhealthbackend.exception.SubscriptionLimitExceededException;
import com.giftedlabs.echoinhealthbackend.repository.OrganizationRepository;
import com.giftedlabs.echoinhealthbackend.repository.ReportRepository;
import com.giftedlabs.echoinhealthbackend.repository.ReportTemplateRepository;
import com.giftedlabs.echoinhealthbackend.repository.SharedScanRepository;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Subscription tier enforcement (Phase 11).
 *
 * <p>Two properties this service is responsible for, both of which it previously lacked:
 *
 * <ul>
 *   <li><b>Quota decisions are atomic.</b> AI credits are reserved with a single conditional
 *       UPDATE rather than read-then-write, and storage checks hold a row lock on the
 *       organization for the life of the caller's transaction. Without this, concurrent
 *       requests could each observe headroom and each commit, taking the tenant over its
 *       tier limit.</li>
 *   <li><b>Entitlement changes are not self-service.</b> Granting a tier or an add-on is a
 *       commercial act; it is restricted at the controller to platform operators. Tenants
 *       raise an upgrade <em>request</em> instead.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    private static final double ALERT_THRESHOLD = 0.80d;

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final ReportTemplateRepository reportTemplateRepository;
    private final SharedScanRepository sharedScanRepository;
    private final AuditService auditService;
    private final BillingAlertService billingAlertService;

    /** Sanity ceilings so an operator mistake cannot grant effectively unbounded quota. */
    @Value("${billing.max-addon-storage-mb:102400}")
    private int maxAddonStorageMb;

    @Value("${billing.max-addon-ai-credits:100000}")
    private int maxAddonAiCredits;

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public BillingPlanResponse getPlan(User user) {
        return mapPlan(requireOrganization(user.getOrganizationId()));
    }

    @Transactional
    public BillingUsageResponse getUsage(User user) {
        Organization organization = requireOrganization(user.getOrganizationId());
        rollCreditPeriodForward(organization.getId());
        organization = requireOrganization(organization.getId());

        BillingUsageResponse usage = buildUsage(organization);
        billingAlertService.notifyIfThresholdCrossed(organization, usage);
        return usage;
    }

    // ------------------------------------------------- entitlement management

    /**
     * Changes an organization's subscription tier. Restricted to platform operators at the
     * controller: this endpoint used to let any hospital admin promote their own tenant to the
     * top tier for free, which nullified every limit the rest of this class enforces.
     */
    @Transactional
    public BillingPlanResponse changeTier(User actor, String organizationId,
                                          UpgradeSubscriptionRequest request) {
        Organization organization = requireOrganization(organizationId);
        SubscriptionTier previous = organization.getSubscriptionTier();
        SubscriptionTier target = request.getSubscriptionTier();

        assertDowngradeIsSafe(organization, target);

        organization.setSubscriptionTier(target);
        Organization saved = organizationRepository.save(organization);

        auditService.logAction(actor, "subscription_tier_changed",
                String.format("Organization %s: %s -> %s", organization.getName(), previous, target));
        return mapPlan(saved);
    }

    @Transactional
    public BillingPlanResponse applyAddons(User actor, String organizationId,
                                           BillingAddonsRequest request) {
        Organization organization = requireOrganization(organizationId);

        int storage = clamp(request.getExtraStorageMb(), maxAddonStorageMb, "extra storage (MB)");
        int credits = clamp(request.getExtraAiCredits(), maxAddonAiCredits, "extra AI credits");

        organization.setAddonStorageMb(storage);
        organization.setAddonAiCredits(credits);
        if (request.getLiteEmrIntegrationEnabled() != null) {
            organization.setLiteEmrIntegrationEnabled(request.getLiteEmrIntegrationEnabled());
        }
        Organization saved = organizationRepository.save(organization);

        auditService.logAction(actor, "billing_addons_updated",
                String.format("Organization %s: storage=+%dMB, aiCredits=+%d, liteEmr=%s",
                        organization.getName(), storage, credits,
                        organization.getLiteEmrIntegrationEnabled()));
        return mapPlan(saved);
    }

    /**
     * A tenant-initiated request to change plan. Records intent and alerts platform operators;
     * it deliberately grants nothing.
     */
    @Transactional
    public void requestUpgrade(User requester, UpgradeRequestRequest request) {
        Organization organization = requireOrganization(requester.getOrganizationId());

        auditService.logAction(requester, "subscription_upgrade_requested",
                String.format("Requested %s (current %s): %s",
                        request.getRequestedTier(),
                        organization.getSubscriptionTier(),
                        request.getReason() != null ? request.getReason() : "no reason given"));

        billingAlertService.notifyUpgradeRequested(organization, requester, request);
    }

    private void assertDowngradeIsSafe(Organization organization, SubscriptionTier target) {
        long activeUsers = userRepository.countByOrganizationIdAndActiveTrue(organization.getId());
        if (activeUsers > target.getMaxUsers()) {
            throw new BusinessException(String.format(
                    "Cannot move %s to %s: it has %d active users but that plan allows %d. "
                            + "Deactivate users first.",
                    organization.getName(), target, activeUsers, target.getMaxUsers()));
        }

        long usedBytes = currentStorageUsageBytes(organization);
        long targetBytes = mbToBytes(target.getStorageLimitMb() + safe(organization.getAddonStorageMb()));
        if (usedBytes > targetBytes) {
            throw new BusinessException(String.format(
                    "Cannot move %s to %s: it is using %d MB but that plan allows %d MB.",
                    organization.getName(), target, usedBytes / (1024 * 1024), targetBytes / (1024 * 1024)));
        }
    }

    private int clamp(Integer requested, int max, String label) {
        int value = requested != null ? requested : 0;
        if (value < 0) {
            value = 0;
        }
        if (value > max) {
            throw new BusinessException(
                    String.format("Requested %s (%d) exceeds the maximum allowed (%d).",
                            label, value, max));
        }
        return value;
    }

    // ------------------------------------------------------ limit enforcement

    /**
     * Takes the organization row lock so that two admins inviting the last available seat
     * simultaneously cannot both succeed.
     */
    @Transactional
    public void assertUserCanBeAdded(Organization organization) {
        Organization locked = lock(organization.getId());
        long activeUsers = userRepository.countByOrganizationIdAndActiveTrue(locked.getId());
        int limit = locked.getEffectiveUserLimit();
        if (activeUsers >= limit) {
            throw new SubscriptionLimitExceededException(
                    String.format("Your %s plan supports up to %d active users. "
                                    + "Upgrade your plan to add more users.",
                            locked.getSubscriptionTier(), limit));
        }
    }

    /**
     * Holds the organization row lock until the caller's transaction commits, so the newly
     * stored file is counted before any competing upload evaluates its own headroom.
     */
    @Transactional
    public void assertStorageCapacity(Organization organization, long additionalBytes) {
        Organization locked = lock(organization.getId());
        long projected = currentStorageUsageBytes(locked) + Math.max(0L, additionalBytes);
        long limitBytes = mbToBytes(locked.getEffectiveStorageLimitMb());
        if (projected > limitBytes) {
            throw new SubscriptionLimitExceededException(
                    String.format("Storage limit exceeded for the %s plan (%d MB of %d MB used). "
                                    + "Upgrade or add storage to continue.",
                            locked.getSubscriptionTier(),
                            currentStorageUsageBytes(locked) / (1024 * 1024),
                            locked.getEffectiveStorageLimitMb()));
        }
    }

    /**
     * Reserves AI credits <em>before</em> the provider is called, in its own transaction, using
     * a single conditional UPDATE. Either the row is updated (credits are ours) or it is not
     * (limit reached) — there is no interleaving window. Callers must
     * {@link #refundAiCredits} if the generation ultimately fails.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserveAiCredits(String organizationId, int credits) {
        rollCreditPeriodForward(organizationId);

        Organization organization = requireOrganization(organizationId);
        int limit = organization.getEffectiveAiCreditsPerMonth();

        int updated = organizationRepository.consumeAiCreditsAtomically(organizationId, credits, limit);
        if (updated == 0) {
            throw new SubscriptionLimitExceededException(
                    String.format("AI credit limit reached for the %s plan (%d of %d used this month). "
                                    + "Upgrade or add AI credits to continue.",
                            organization.getSubscriptionTier(),
                            safe(organization.getAiCreditsUsedThisMonth()), limit));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refundAiCredits(String organizationId, int credits) {
        organizationRepository.refundAiCredits(organizationId, credits);
    }

    /**
     * Cheap pre-flight check. The authoritative decision is {@link #reserveAiCredits}; this
     * only avoids doing work that is obviously going to be rejected.
     */
    @Transactional(readOnly = true)
    public boolean hasAiCreditsAvailable(String organizationId, int credits) {
        Organization organization = requireOrganization(organizationId);
        return safe(organization.getAiCreditsUsedThisMonth()) + credits
                <= organization.getEffectiveAiCreditsPerMonth();
    }

    /** Resets the monthly counter at most once per calendar month, race-free. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rollCreditPeriodForward(String organizationId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime periodStart = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        organizationRepository.resetAiCreditsIfPeriodElapsed(organizationId, periodStart, now);
    }

    // ------------------------------------------------------------------ mapping

    private BillingPlanResponse mapPlan(Organization organization) {
        return BillingPlanResponse.builder()
                .organizationId(organization.getId())
                .organizationName(organization.getName())
                .subscriptionTier(organization.getSubscriptionTier())
                .maxUsers(organization.getEffectiveUserLimit())
                .storageLimitMb(organization.getEffectiveStorageLimitMb())
                .aiCreditsPerMonth(organization.getEffectiveAiCreditsPerMonth())
                .addonStorageMb(organization.getAddonStorageMb())
                .addonAiCredits(organization.getAddonAiCredits())
                .liteEmrIntegrationEnabled(organization.getLiteEmrIntegrationEnabled())
                .autoGrammarCheckEnabled(organization.isAutoGrammarCheckEnabled())
                .build();
    }

    BillingUsageResponse buildUsage(Organization organization) {
        long activeUsers = userRepository.countByOrganizationIdAndActiveTrue(organization.getId());
        long storageUsedBytes = currentStorageUsageBytes(organization);
        int userLimit = organization.getEffectiveUserLimit();
        int storageLimitMb = organization.getEffectiveStorageLimitMb();
        int aiLimit = organization.getEffectiveAiCreditsPerMonth();
        int aiUsed = safe(organization.getAiCreditsUsedThisMonth());

        boolean approachingUsers = userLimit > 0 && activeUsers >= Math.ceil(userLimit * ALERT_THRESHOLD);
        boolean approachingStorage = storageLimitMb > 0
                && storageUsedBytes >= Math.ceil(mbToBytes(storageLimitMb) * ALERT_THRESHOLD);
        boolean approachingAi = aiLimit > 0 && aiUsed >= Math.ceil(aiLimit * ALERT_THRESHOLD);

        List<String> alerts = new ArrayList<>();
        if (approachingUsers) {
            alerts.add("User count is approaching the plan limit. Upgrade to avoid invite failures.");
        }
        if (approachingStorage) {
            alerts.add("Storage usage is above 80% of the plan limit. Add storage or upgrade.");
        }
        if (approachingAi) {
            alerts.add("AI credit usage is above 80% of the monthly limit. Add credits or upgrade.");
        }

        return BillingUsageResponse.builder()
                .activeUsers(activeUsers)
                .userLimit(userLimit)
                .storageUsedBytes(storageUsedBytes)
                .storageLimitMb(storageLimitMb)
                .aiCreditsUsedThisMonth(aiUsed)
                .aiCreditsLimitThisMonth(aiLimit)
                .approachingUserLimit(approachingUsers)
                .approachingStorageLimit(approachingStorage)
                .approachingAiCreditLimit(approachingAi)
                .overUserLimit(activeUsers > userLimit)
                .overStorageLimit(storageUsedBytes > mbToBytes(storageLimitMb))
                .overAiCreditLimit(aiUsed > aiLimit)
                .alerts(alerts)
                .autoGrammarCheckEnabled(organization.isAutoGrammarCheckEnabled())
                .liteEmrIntegrationEnabled(Boolean.TRUE.equals(organization.getLiteEmrIntegrationEnabled()))
                .build();
    }

    private long currentStorageUsageBytes(Organization organization) {
        return reportRepository.sumFileSizeByOrganizationId(organization.getId())
                + reportTemplateRepository.sumFileSizeByOrganizationId(organization.getId())
                + sharedScanRepository.sumImageSizeByOrganizationId(organization.getId());
    }

    private Organization lock(String organizationId) {
        return organizationRepository.findByIdForUpdate(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
    }

    private Organization requireOrganization(String organizationId) {
        if (organizationId == null) {
            throw new ResourceNotFoundException("Organization not found");
        }
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
    }

    private int safe(Integer value) {
        return value != null ? value : 0;
    }

    private long mbToBytes(int mb) {
        return mb * 1024L * 1024L;
    }
}
