package com.giftedlabs.echoinhealthbackend.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Reports outbound email misconfiguration at startup, loudly.
 *
 * <p>Mail was previously sent from a domain unrelated to the product, which the provider rejects
 * for lack of domain verification, and every send was wrapped in a swallowed catch — so the
 * rejection was invisible and the symptom reached the client as "onboarding sends no email".
 *
 * <p>The first fix for that made a bad sender address abort startup. That over-corrected: it turned
 * a degraded feature into a total outage, taking reporting, the vault, AI and every clinical
 * workflow down because onboarding email might not arrive. The invisibility problem it was really
 * guarding against is now solved elsewhere — {@code EmailOutboxEntry} records every attempt with
 * the provider's verdict, and the platform health endpoint surfaces it — so the remaining job here
 * is to make the misconfiguration impossible to miss, not to refuse to serve patients over it.
 *
 * <p>Set {@code email.validation.fail-fast=true} to restore aborting startup, for environments that
 * would rather not run at all than run without email.
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class EmailConfigurationValidator {

    private final EmailConfigurationChecker checker;

    @Value("${email.validation.fail-fast:false}")
    private boolean failFast;

    @PostConstruct
    public void validate() {
        EmailConfigurationStatus status = checker.check();

        if (status.valid()) {
            if (status.warning() != null) {
                log.warn("Outbound email: {}", status.warning());
            }
            log.info("Outbound email configured: sending from '{}' on verified domain '{}'",
                    status.fromAddress(), status.senderDomain());
            return;
        }

        if (failFast) {
            throw new IllegalStateException(status.problem());
        }

        log.error("""

                ===============================================================================
                 OUTBOUND EMAIL IS MISCONFIGURED — the application is starting anyway.
                ===============================================================================
                 {}

                 Until this is fixed: registration, verification, onboarding, password reset
                 and quota alert email will NOT be delivered. Every failed attempt is recorded
                 in the email outbox, and GET /api/admin/platform/health reports this component
                 as DOWN with the same message.

                 Set email.validation.fail-fast=true to abort startup on this instead.
                ===============================================================================
                """, status.problem());
    }
}
