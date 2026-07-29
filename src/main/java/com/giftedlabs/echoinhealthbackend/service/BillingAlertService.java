package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.billing.BillingUsageResponse;
import com.giftedlabs.echoinhealthbackend.dto.billing.UpgradeRequestRequest;
import com.giftedlabs.echoinhealthbackend.entity.NotificationType;
import com.giftedlabs.echoinhealthbackend.entity.Organization;
import com.giftedlabs.echoinhealthbackend.entity.Role;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.repository.OrganizationRepository;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pushes subscription-limit alerts to administrators (UR-026, UR-068).
 *
 * <p>Previously the 80% thresholds were computed correctly but only ever returned in the body
 * of {@code GET /billing/usage} — an admin who never opened that screen was never told
 * anything. This delivers in-app notifications and email when a threshold is first crossed.
 *
 * <p>De-duplication is done with a conditional UPDATE on the organization's alert signature,
 * so repeated usage reads do not spam, and concurrent reads cannot both fire.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BillingAlertService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyIfThresholdCrossed(Organization organization, BillingUsageResponse usage) {
        String signature = signatureOf(usage);
        if (signature.isEmpty()) {
            // Back under every threshold — clear the marker so the next crossing alerts again.
            organizationRepository.recordUsageAlertIfChanged(
                    organization.getId(), "CLEAR", LocalDateTime.now());
            return;
        }

        int changed = organizationRepository.recordUsageAlertIfChanged(
                organization.getId(), signature, LocalDateTime.now());
        if (changed == 0) {
            return; // Already alerted on exactly this combination of thresholds.
        }

        List<User> admins = administratorsOf(organization.getId());
        if (admins.isEmpty()) {
            log.warn("Organization {} crossed a subscription limit but has no admin to notify",
                    organization.getId());
            return;
        }

        String title = usage.isOverUserLimit() || usage.isOverStorageLimit() || usage.isOverAiCreditLimit()
                ? "Subscription limit reached"
                : "Approaching your subscription limit";
        String message = String.join(" ", usage.getAlerts());

        for (User admin : admins) {
            safelyNotify(admin, title, message, usage, organization);
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyUpgradeRequested(Organization organization, User requester,
                                       UpgradeRequestRequest request) {
        String title = "Plan change requested: " + organization.getName();
        String message = String.format("%s requested %s (currently %s). Reason: %s",
                requester.getFullName(),
                request.getRequestedTier(),
                organization.getSubscriptionTier(),
                request.getReason() != null && !request.getReason().isBlank()
                        ? request.getReason() : "not given");

        // Platform operators, not tenant admins: only they can act on this.
        for (User operator : userRepository.findByRole(Role.SUPER_ADMIN)) {
            try {
                emailService.sendEmail(operator.getEmail(), title, upgradeEmailBody(message));
            } catch (RuntimeException e) {
                log.warn("Failed to email upgrade request to {}", operator.getEmail(), e);
            }
        }
        log.info("Upgrade request recorded for organization {} -> {}",
                organization.getId(), request.getRequestedTier());
    }

    private void safelyNotify(User admin, String title, String message,
                              BillingUsageResponse usage, Organization organization) {
        try {
            notificationService.createNotification(
                    admin, null, NotificationType.BILLING_LIMIT_WARNING, null, null, title, message);
        } catch (RuntimeException e) {
            log.warn("Failed to create billing notification for {}", admin.getEmail(), e);
        }
        try {
            emailService.sendEmail(admin.getEmail(),
                    title + " - " + organization.getName(), usageEmailBody(title, usage));
        } catch (RuntimeException e) {
            log.warn("Failed to email billing alert to {}", admin.getEmail(), e);
        }
    }

    private List<User> administratorsOf(String organizationId) {
        Set<User> admins = new LinkedHashSet<>();
        admins.addAll(userRepository.findByOrganizationIdAndRole(organizationId, Role.HOSPITAL_ADMIN));
        admins.addAll(userRepository.findByOrganizationIdAndRole(organizationId, Role.ADMIN));
        return admins.stream().filter(user -> Boolean.TRUE.equals(user.getActive())).toList();
    }

    /**
     * Encodes exactly which thresholds are currently tripped. A change in this string is what
     * makes a new alert due; an unchanged string means the situation has not materially moved.
     */
    private String signatureOf(BillingUsageResponse usage) {
        List<String> parts = new ArrayList<>();
        if (usage.isOverUserLimit()) {
            parts.add("USERS_OVER");
        } else if (usage.isApproachingUserLimit()) {
            parts.add("USERS_80");
        }
        if (usage.isOverStorageLimit()) {
            parts.add("STORAGE_OVER");
        } else if (usage.isApproachingStorageLimit()) {
            parts.add("STORAGE_80");
        }
        if (usage.isOverAiCreditLimit()) {
            parts.add("AI_OVER");
        } else if (usage.isApproachingAiCreditLimit()) {
            parts.add("AI_80");
        }
        return String.join("|", parts);
    }

    private String usageEmailBody(String title, BillingUsageResponse usage) {
        StringBuilder items = new StringBuilder();
        usage.getAlerts().forEach(alert -> items.append("<li>").append(escape(alert)).append("</li>"));
        return """
                <h2>%s</h2>
                <ul>%s</ul>
                <p>Current usage:</p>
                <ul>
                  <li>Users: %d of %d</li>
                  <li>Storage: %d MB of %d MB</li>
                  <li>AI credits this month: %d of %d</li>
                </ul>
                <p>Contact your Echion Health account manager to add capacity or change plan.</p>
                """.formatted(escape(title), items,
                usage.getActiveUsers(), usage.getUserLimit(),
                usage.getStorageUsedBytes() / (1024 * 1024), usage.getStorageLimitMb(),
                usage.getAiCreditsUsedThisMonth(), usage.getAiCreditsLimitThisMonth());
    }

    private String upgradeEmailBody(String message) {
        return "<h2>Plan change requested</h2><p>" + escape(message) + "</p>";
    }

    private String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
