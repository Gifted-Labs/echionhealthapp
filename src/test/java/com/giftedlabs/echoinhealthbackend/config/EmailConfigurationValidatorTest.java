package com.giftedlabs.echoinhealthbackend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards the check that stands between a misconfigured sender and silent mail loss.
 *
 * <p>The client reported that onboarding sent no email. The cause was a sender address on a domain
 * the provider had not verified, rejected with a 403 that the old fire-and-forget send swallowed
 * whole.
 *
 * <p>Two properties are under test here and they pull in opposite directions. The check must be
 * <em>exact</em> — a false positive reports a working mailer as broken. And the reaction must be
 * <em>proportionate</em> — email is one feature, and by default a bad sender address must not stop
 * the application from serving reports, the vault or AI. Aborting startup is available, but only
 * when an operator asks for it.
 */
class EmailConfigurationValidatorTest {

    private static final String VERIFIED_DOMAIN = "echionhealth.com";
    private static final String API_KEY = "re_test_key";

    // ============================================================ what counts as misconfigured

    @Nested
    @DisplayName("the check itself")
    class Check {

        @Test
        @DisplayName("a bare address on the verified domain is accepted")
        void bareAddressOnVerifiedDomain() {
            assertThat(check("noreply@echionhealth.com", API_KEY, VERIFIED_DOMAIN).valid()).isTrue();
        }

        @Test
        @DisplayName("a display-name address on the verified domain is accepted")
        void displayNameAddressOnVerifiedDomain() {
            EmailConfigurationStatus status =
                    check("Echion Health <noreply@echionhealth.com>", API_KEY, VERIFIED_DOMAIN);
            assertThat(status.valid()).isTrue();
            assertThat(status.senderDomain()).isEqualTo("echionhealth.com");
        }

        @Test
        @DisplayName("a subdomain of the verified domain is accepted")
        void subdomainIsAccepted() {
            assertThat(check("noreply@mail.echionhealth.com", API_KEY, VERIFIED_DOMAIN).valid()).isTrue();
        }

        @Test
        @DisplayName("the domain check ignores case")
        void domainCheckIgnoresCase() {
            assertThat(check("NoReply@EchionHealth.COM", API_KEY, "EchionHealth.com").valid()).isTrue();
        }

        @Test
        @DisplayName("any one of several verified domains is accepted")
        void multipleVerifiedDomainsAreAccepted() {
            assertThat(check("noreply@echionhealth.com", API_KEY, "echionhealth.com, echion.app").valid())
                    .isTrue();
            assertThat(check("noreply@echion.app", API_KEY, "echionhealth.com, echion.app").valid())
                    .isTrue();
            assertThat(check("noreply@elsewhere.com", API_KEY, "echionhealth.com, echion.app").valid())
                    .isFalse();
        }

        @Test
        @DisplayName("the reported misconfiguration — sending from an unverified domain — is caught")
        void theReportedMisconfigurationIsCaught() {
            EmailConfigurationStatus status =
                    check("noreply@echoinhealth.com", API_KEY, VERIFIED_DOMAIN);
            assertThat(status.valid()).isFalse();
            assertThat(status.problem())
                    .contains("echoinhealth.com")
                    .contains(VERIFIED_DOMAIN);
        }

        @Test
        @DisplayName("a lookalike domain is not mistaken for the verified one")
        void lookalikeDomainIsRejected() {
            assertThat(check("noreply@notechionhealth.com", API_KEY, VERIFIED_DOMAIN).valid()).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = { "not-an-address", "noreply@", "@echionhealth.com", "noreply@localhost" })
        @DisplayName("a malformed sender is caught with a usable message")
        void malformedSenderIsCaught(String malformed) {
            EmailConfigurationStatus status = check(malformed, API_KEY, VERIFIED_DOMAIN);
            assertThat(status.valid()).isFalse();
            assertThat(status.problem()).contains("not a valid sender address");
        }

        @Test
        @DisplayName("a missing sender is caught")
        void missingSenderIsCaught() {
            assertThat(check("   ", API_KEY, VERIFIED_DOMAIN).problem()).contains("EMAIL_FROM is not set");
        }

        @Test
        @DisplayName("a missing API key is caught, since every send would fail")
        void missingApiKeyIsCaught() {
            assertThat(check("noreply@echionhealth.com", "", VERIFIED_DOMAIN).problem())
                    .contains("RESEND_API_KEY");
        }

        @Test
        @DisplayName("an unset verified domain downgrades the domain check to a warning")
        void blankVerifiedDomainOnlyWarns() {
            EmailConfigurationStatus status = check("noreply@anywhere.test", API_KEY, "");
            assertThat(status.valid()).isTrue();
            assertThat(status.warning()).contains("EMAIL_VERIFIED_DOMAIN is not set");
        }
    }

    // ================================================================ what happens about it

    @Nested
    @DisplayName("the startup reaction")
    class Reaction {

        @Test
        @DisplayName("by default a misconfigured sender does not stop the application starting")
        void misconfigurationDoesNotAbortStartupByDefault() {
            assertThatCode(() -> validate("noreply@echoinhealth.com", API_KEY, VERIFIED_DOMAIN, false))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a missing API key does not stop the application starting either")
        void missingApiKeyDoesNotAbortStartupByDefault() {
            assertThatCode(() -> validate("noreply@echionhealth.com", "", VERIFIED_DOMAIN, false))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("fail-fast aborts startup on an unverified sending domain")
        void failFastAbortsOnUnverifiedDomain() {
            assertThatThrownBy(() -> validate("noreply@echoinhealth.com", API_KEY, VERIFIED_DOMAIN, true))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("echoinhealth.com");
        }

        @Test
        @DisplayName("fail-fast aborts startup on a missing sender")
        void failFastAbortsOnMissingSender() {
            assertThatThrownBy(() -> validate("", API_KEY, VERIFIED_DOMAIN, true))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("EMAIL_FROM is not set");
        }

        @Test
        @DisplayName("valid configuration starts cleanly under either setting")
        void validConfigurationStartsCleanly() {
            assertThatCode(() -> validate("noreply@echionhealth.com", API_KEY, VERIFIED_DOMAIN, true))
                    .doesNotThrowAnyException();
            assertThatCode(() -> validate("noreply@echionhealth.com", API_KEY, VERIFIED_DOMAIN, false))
                    .doesNotThrowAnyException();
        }
    }

    // ========================================================================== fixtures

    private static EmailConfigurationChecker checker(String from, String apiKey, String verifiedDomain) {
        EmailConfigurationChecker checker = new EmailConfigurationChecker();
        ReflectionTestUtils.setField(checker, "fromAddress", from);
        ReflectionTestUtils.setField(checker, "apiKey", apiKey);
        ReflectionTestUtils.setField(checker, "verifiedDomain", verifiedDomain);
        return checker;
    }

    private static EmailConfigurationStatus check(String from, String apiKey, String verifiedDomain) {
        return checker(from, apiKey, verifiedDomain).check();
    }

    private static void validate(String from, String apiKey, String verifiedDomain, boolean failFast) {
        EmailConfigurationValidator validator =
                new EmailConfigurationValidator(checker(from, apiKey, verifiedDomain));
        ReflectionTestUtils.setField(validator, "failFast", failFast);
        validator.validate();
    }
}
