package com.giftedlabs.echoinhealthbackend.service.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPromptSanitizerTest {

    private final AiPromptSanitizer sanitizer = new AiPromptSanitizer();

    @Test
    void sanitizeRedactsCommonIdentifiers() {
        String sanitized = sanitizer.sanitize("Patient Jane Doe, email jane@example.com, phone +233 24 123 4567, date 01/06/2026, id 12345678");

        assertFalse(sanitized.contains("jane@example.com"));
        assertFalse(sanitized.contains("12345678"));
        assertTrue(sanitized.contains("[REDACTED_EMAIL]"));
        assertTrue(sanitized.contains("[REDACTED_PHONE]"));
        assertTrue(sanitized.contains("[REDACTED_DATE]"));
    }
}
