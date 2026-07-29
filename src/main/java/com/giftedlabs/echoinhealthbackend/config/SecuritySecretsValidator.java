package com.giftedlabs.echoinhealthbackend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Profile("!test")
public class SecuritySecretsValidator {

    private static final Set<String> DISALLOWED_JWT_SECRETS = Set.of(
            "",
            "zA9FQW6nT7Jm8L3CkD5bX2p4R0YHUsN1eMZBqfGvVhEoK_iwSaPtdOcrxIyljA7");
    private static final Set<String> DISALLOWED_ENCRYPTION_KEYS = Set.of(
            "",
            "EchionHealthAES256SecretKey12345");
    private static final Set<String> DISALLOWED_ADMIN_PASSWORDS = Set.of(
            "",
            "SuperAdmin123!");

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${encryption.key:}")
    private String encryptionKey;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @PostConstruct
    public void validate() {
        requireConfigured("JWT secret", jwtSecret, DISALLOWED_JWT_SECRETS);
        requireConfigured("encryption key", encryptionKey, DISALLOWED_ENCRYPTION_KEYS);
        requireConfigured("bootstrap admin password", adminPassword, DISALLOWED_ADMIN_PASSWORDS);
    }

    private void requireConfigured(String label, String value, Set<String> disallowedValues) {
        String normalized = value == null ? "" : value.trim();
        if (disallowedValues.contains(normalized)) {
            throw new IllegalStateException("Secure " + label + " must be explicitly configured");
        }
    }
}
