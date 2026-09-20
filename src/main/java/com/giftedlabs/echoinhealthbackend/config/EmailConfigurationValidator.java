package com.giftedlabs.echoinhealthbackend.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fails startup when outbound email cannot possibly work.
 *
 * <p>Mail was previously sent from a domain unrelated to the product, which the provider rejects
 * for lack of domain verification. Because every send was wrapped in a swallowed catch, the
 * rejection was invisible and the symptom reached the client as "onboarding sends no email". A
 * misconfigured sender is a deployment error, so it belongs at boot rather than in a log line
 * nobody reads.
 */
@Component
@Profile("!test")
@Slf4j
public class EmailConfigurationValidator {

    /** Matches either {@code user@host} or {@code Display Name <user@host>}. */
    private static final Pattern FROM_PATTERN =
            Pattern.compile("^(?:[^<>]*<\\s*)?([^<>@\\s]+)@([^<>@\\s]+\\.[^<>@\\s]+)\\s*>?$");

    @Value("${email.from:}")
    private String fromAddress;

    @Value("${email.resend.api-key:}")
    private String apiKey;

    /**
     * Domain the sender address must belong to. Set it to the domain verified with the email
     * provider; leaving it blank downgrades the check to a warning, which suits local development.
     */
    @Value("${email.verified-domain:}")
    private String verifiedDomain;

    @PostConstruct
    public void validate() {
        String from = fromAddress == null ? "" : fromAddress.trim();

        if (from.isEmpty()) {
            throw new IllegalStateException(
                    "EMAIL_FROM must be configured; without it no transactional email can be sent");
        }

        Matcher matcher = FROM_PATTERN.matcher(from);
        if (!matcher.matches()) {
            throw new IllegalStateException(
                    "EMAIL_FROM is not a valid sender address: '" + from + "'. "
                            + "Expected 'user@domain' or 'Display Name <user@domain>'");
        }

        String senderDomain = matcher.group(2).toLowerCase();

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "RESEND_API_KEY must be configured; without it every email send is rejected");
        }

        String expected = verifiedDomain == null ? "" : verifiedDomain.trim().toLowerCase();
        if (expected.isEmpty()) {
            log.warn("email.verified-domain is not set, so the sender domain '{}' cannot be checked. "
                    + "If it is not verified with the email provider, every send will be rejected.",
                    senderDomain);
            return;
        }

        if (!senderDomain.equals(expected) && !senderDomain.endsWith("." + expected)) {
            throw new IllegalStateException(String.format(
                    "EMAIL_FROM sends from '%s' but the verified sending domain is '%s'. "
                            + "The provider rejects mail from unverified domains, so onboarding and "
                            + "verification email would silently never arrive.",
                    senderDomain, expected));
        }

        log.info("Outbound email configured: sending from '{}' on verified domain '{}'", from, expected);
    }
}
