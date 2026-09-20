package com.giftedlabs.echoinhealthbackend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards the startup check that stands between a misconfigured sender and silent mail loss.
 *
 * <p>The client reported that onboarding sent no email. The cause was a sender address on a domain
 * the provider had not verified, rejected with a 403 that the old fire-and-forget send swallowed
 * whole. This validator turns that into a boot failure. Because it can now stop the application
 * from starting, its own behaviour needs to be exact — a false positive here is an outage.
 */
class EmailConfigurationValidatorTest {

    private static final String VERIFIED_DOMAIN = "echionhealth.com";
    private static final String API_KEY = "re_test_key";

    @Test
    @DisplayName("a bare address on the verified domain is accepted")
    void bareAddressOnVerifiedDomain() {
        assertThatCode(() -> validate("noreply@echionhealth.com", API_KEY, VERIFIED_DOMAIN))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a display-name address on the verified domain is accepted")
    void displayNameAddressOnVerifiedDomain() {
        assertThatCode(() -> validate("Echion Health <noreply@echionhealth.com>", API_KEY, VERIFIED_DOMAIN))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a subdomain of the verified domain is accepted")
    void subdomainIsAccepted() {
        assertThatCode(() -> validate("noreply@mail.echionhealth.com", API_KEY, VERIFIED_DOMAIN))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the domain check is case-insensitive")
    void domainCheckIgnoresCase() {
        assertThatCode(() -> validate("NoReply@EchionHealth.COM", API_KEY, "EchionHealth.com"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the previously configured unverified sender is rejected")
    void theReportedMisconfigurationIsRejected() {
        assertThatThrownBy(() -> validate(
                "Echion Healtth App <echionehelath@merbsconnect.com>", API_KEY, VERIFIED_DOMAIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("merbsconnect.com")
                .hasMessageContaining(VERIFIED_DOMAIN);
    }

    @Test
    @DisplayName("a domain that merely ends with the verified one is still rejected")
    void lookalikeDomainIsRejected() {
        assertThatThrownBy(() -> validate("noreply@notechionhealth.com", API_KEY, VERIFIED_DOMAIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("notechionhealth.com");
    }

    @ParameterizedTest
    @ValueSource(strings = { "not-an-address", "noreply@", "@echionhealth.com", "noreply@localhost" })
    @DisplayName("a malformed sender is rejected with a usable message")
    void malformedSenderIsRejected(String malformed) {
        assertThatThrownBy(() -> validate(malformed, API_KEY, VERIFIED_DOMAIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a valid sender address");
    }

    @Test
    @DisplayName("a missing sender is rejected")
    void missingSenderIsRejected() {
        assertThatThrownBy(() -> validate("   ", API_KEY, VERIFIED_DOMAIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EMAIL_FROM must be configured");
    }

    @Test
    @DisplayName("a missing API key is rejected, since every send would fail")
    void missingApiKeyIsRejected() {
        assertThatThrownBy(() -> validate("noreply@echionhealth.com", "", VERIFIED_DOMAIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESEND_API_KEY");
    }

    @Test
    @DisplayName("an unset verified domain downgrades the check to a warning")
    void blankVerifiedDomainOnlyWarns() {
        assertThatCode(() -> validate("noreply@anywhere.test", API_KEY, ""))
                .doesNotThrowAnyException();
    }

    private void validate(String from, String apiKey, String verifiedDomain) {
        EmailConfigurationValidator validator = new EmailConfigurationValidator();
        ReflectionTestUtils.setField(validator, "fromAddress", from);
        ReflectionTestUtils.setField(validator, "apiKey", apiKey);
        ReflectionTestUtils.setField(validator, "verifiedDomain", verifiedDomain);
        validator.validate();
    }
}
