package com.giftedlabs.echoinhealthbackend.service.ai;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AiPromptSanitizer {

    private static final Pattern EMAIL = Pattern.compile("\\b[\\w._%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile("\\b(?:\\+?\\d{1,3}[ -]?)?(?:\\(?\\d{2,4}\\)?[ -]?)?\\d{3,4}[ -]?\\d{4}\\b");
    private static final Pattern DATE = Pattern.compile("\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}\\b");
    private static final Pattern LONG_ID = Pattern.compile("\\b\\d{6,}\\b");

    public String sanitize(String input) {
        if (input == null) {
            return null;
        }
        String sanitized = EMAIL.matcher(input).replaceAll("[REDACTED_EMAIL]");
        sanitized = PHONE.matcher(sanitized).replaceAll("[REDACTED_PHONE]");
        sanitized = DATE.matcher(sanitized).replaceAll("[REDACTED_DATE]");
        sanitized = LONG_ID.matcher(sanitized).replaceAll("[REDACTED_ID]");
        return sanitized.trim();
    }

    public Map<String, Object> sanitizeMeasurements(Map<String, Object> measurements) {
        if (measurements == null) {
            return Map.of();
        }
        return measurements.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> sanitize(entry.getKey()),
                        entry -> entry.getValue() instanceof String value ? sanitize(value) : entry.getValue(),
                        (left, right) -> right,
                        java.util.LinkedHashMap::new));
    }
}
