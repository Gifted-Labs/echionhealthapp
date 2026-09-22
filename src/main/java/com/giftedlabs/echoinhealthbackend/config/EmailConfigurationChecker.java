package com.giftedlabs.echoinhealthbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides whether outbound email can possibly work, and says why not when it cannot.
 *
 * <p>Pure inspection of configuration — no logging, no throwing, no startup opinion. That split
 * exists because the same answer is needed in three places with three different consequences:
 * {@link EmailConfigurationValidator} at boot, the platform health endpoint at runtime, and tests.
 * Folding the reaction into the check is what previously made a bad {@code EMAIL_FROM} able to
 * abort the entire application.
 */
@Component
public class EmailConfigurationChecker {

    /** Matches either {@code user@host} or {@code Display Name <user@host>}. */
    private static final Pattern FROM_PATTERN =
            Pattern.compile("^(?:[^<>]*<\\s*)?([^<>@\\s]+)@([^<>@\\s]+\\.[^<>@\\s]+)\\s*>?$");

    @Value("${email.from:}")
    private String fromAddress;

    @Value("${email.resend.api-key:}")
    private String apiKey;

    /**
     * Domains verified with the email provider, comma-separated. A provider can legitimately have
     * more than one verified domain, and a deployment that sends from a second one is not a
     * misconfiguration. Leaving this blank downgrades the domain check to a warning, which suits
     * local development.
     */
    @Value("${email.verified-domain:}")
    private String verifiedDomain;

    public EmailConfigurationStatus check() {
        String from = fromAddress == null ? "" : fromAddress.trim();

        if (from.isEmpty()) {
            return EmailConfigurationStatus.invalid(
                    "EMAIL_FROM is not set. Set it to a sender address on a domain verified with "
                            + "the email provider, for example 'Echion Health <noreply@echionhealth.com>'.",
                    from);
        }

        Matcher matcher = FROM_PATTERN.matcher(from);
        if (!matcher.matches()) {
            return EmailConfigurationStatus.invalid(
                    "EMAIL_FROM is not a valid sender address: '" + from
                            + "'. Expected 'user@domain' or 'Display Name <user@domain>'.",
                    from);
        }

        String senderDomain = matcher.group(2).toLowerCase();

        if (apiKey == null || apiKey.isBlank()) {
            return EmailConfigurationStatus.invalid(
                    "RESEND_API_KEY is not set. Without it the provider rejects every send.",
                    from);
        }

        List<String> verified = verifiedDomains();
        if (verified.isEmpty()) {
            return EmailConfigurationStatus.okWithWarning(
                    "EMAIL_VERIFIED_DOMAIN is not set, so the sender domain '" + senderDomain
                            + "' has not been checked; if it is not verified with the provider, "
                            + "every send will be rejected",
                    senderDomain, from);
        }

        boolean matches = verified.stream()
                .anyMatch(domain -> senderDomain.equals(domain) || senderDomain.endsWith("." + domain));

        if (!matches) {
            return EmailConfigurationStatus.invalid(String.format(
                    "EMAIL_FROM sends from '%s' but the verified sending domain%s %s. The provider "
                            + "rejects mail from unverified domains, so onboarding and verification "
                            + "email would never arrive. Either point EMAIL_FROM at the verified "
                            + "domain, or add '%s' to EMAIL_VERIFIED_DOMAIN once it is verified with "
                            + "the provider.",
                    senderDomain,
                    verified.size() == 1 ? " is" : "s are",
                    String.join(", ", verified),
                    senderDomain), from);
        }

        return EmailConfigurationStatus.ok(senderDomain, from);
    }

    private List<String> verifiedDomains() {
        if (verifiedDomain == null || verifiedDomain.isBlank()) {
            return List.of();
        }
        return Arrays.stream(verifiedDomain.split(","))
                .map(String::trim)
                .filter(domain -> !domain.isEmpty())
                .map(String::toLowerCase)
                .toList();
    }
}
