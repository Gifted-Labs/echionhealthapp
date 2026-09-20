package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.entity.EmailOutboxEntry;
import com.giftedlabs.echoinhealthbackend.util.EmailTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Composes the application's transactional emails and hands them to {@link EmailDeliveryService}.
 *
 * <p>Delivery, retries and the outbox record live in that other bean; this one only decides what
 * each message says.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    public static final String TEMPLATE_VERIFICATION = "verification";
    public static final String TEMPLATE_WELCOME = "welcome";
    public static final String TEMPLATE_ORGANIZATION_ONBOARDED = "organization_onboarded";
    public static final String TEMPLATE_DELIVERY_TEST = "delivery_test";
    public static final String TEMPLATE_BILLING_ALERT = "billing_alert";
    public static final String TEMPLATE_UPGRADE_REQUEST = "upgrade_request";

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final EmailDeliveryService deliveryService;

    /**
     * Send verification email
     */
    public void sendVerificationEmail(String to, String firstName, String verificationLink) {
        queueAndSend(to,
                "Verify Your Email - Echion Health",
                EmailTemplate.getVerificationEmail(firstName, verificationLink),
                TEMPLATE_VERIFICATION,
                null);
    }

    /**
     * Send welcome email after verification
     */
    public void sendWelcomeEmail(String to, String firstName) {
        queueAndSend(to,
                "Welcome to Echion Health!",
                EmailTemplate.getWelcomeEmail(firstName),
                TEMPLATE_WELCOME,
                null);
    }

    /**
     * Confirms to the first hospital admin that their organization is live.
     *
     * <p>Sent on every registration path, including the one where email verification is skipped.
     * That branch previously sent nothing at all, which is what the client observed as "no email
     * after onboarding": turning on {@code app.auth.auto-verify-registration} silently removed the
     * only message a new hospital would ever have received.
     */
    public void sendOrganizationOnboardedEmail(String to, String firstName, String hospitalName,
            String loginLink, String organizationId) {
        queueAndSend(to,
                "Your hospital is live on Echion Health",
                EmailTemplate.getOrganizationOnboardedEmail(firstName, hospitalName, to, loginLink),
                TEMPLATE_ORGANIZATION_ONBOARDED,
                organizationId);
    }

    /**
     * Sends a probe message and waits for the provider's verdict, so email wiring can be verified
     * from the platform console instead of by reading logs.
     */
    public EmailDeliveryService.DeliveryResult sendDeliveryTest(String to, String triggeredByEmail) {
        String html = EmailTemplate.getDeliveryTestEmail(triggeredByEmail, LocalDateTime.now().format(TIMESTAMP));
        EmailOutboxEntry entry = deliveryService.queue(
                to, "Echion Health delivery test", html, TEMPLATE_DELIVERY_TEST, null);
        return deliveryService.deliver(entry.getId());
    }

    /**
     * Sends a pre-rendered operational message — billing alerts and upgrade requests, whose bodies
     * are assembled by the billing service rather than from a fixed template.
     */
    public void sendOperationalEmail(String to, String subject, String html, String templateName,
            String organizationId) {
        queueAndSend(to, subject, html, templateName, organizationId);
    }

    private void queueAndSend(String to, String subject, String html, String templateName,
            String organizationId) {
        EmailOutboxEntry entry = deliveryService.queue(to, subject, html, templateName, organizationId);
        deliveryService.deliverAsync(entry.getId());
    }
}
