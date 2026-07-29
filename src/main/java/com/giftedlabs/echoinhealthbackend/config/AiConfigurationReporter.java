package com.giftedlabs.echoinhealthbackend.config;

import com.giftedlabs.echoinhealthbackend.service.ai.AiProviderSettings;
import com.giftedlabs.echoinhealthbackend.service.ai.AiProviderType;
import com.giftedlabs.echoinhealthbackend.service.ai.AiRoutingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Prints the resolved AI provider configuration at startup and flags settings that are known to
 * fail at call time.
 *
 * <p>The values that matter here come from environment variables and a {@code .env} file, which
 * override {@code application.yaml}. That makes it entirely possible for the defaults in the
 * repository to be correct while the running process is misconfigured — and the symptom is a
 * generic 503 on the first generation attempt, long after startup. Reporting the effective
 * values, and validating the shapes that are provably wrong, turns that into a startup warning.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiConfigurationReporter {

    private static final String GEMINI_EXPECTED_BASE =
            "https://generativelanguage.googleapis.com/v1beta";

    private final AiRoutingProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void reportConfiguration() {
        AiProviderType primary = AiProviderType.from(properties.getDefaultProvider(), AiProviderType.GEMINI);
        AiProviderType fallback = AiProviderType.from(properties.getFallbackProvider(), AiProviderType.OPENAI);

        log.info("AI routing: primary={}, fallback={} (fallback {})",
                primary, fallback, properties.isAllowFallback() ? "enabled" : "DISABLED");

        List<String> problems = new ArrayList<>();
        for (AiProviderType type : AiProviderType.values()) {
            problems.addAll(report(type, properties.settingsFor(type)));
        }

        if (!problems.isEmpty()) {
            log.warn("AI configuration problems detected -- generation will fail until these are fixed:\n  - {}",
                    String.join("\n  - ", problems));
            log.warn("These values come from environment variables or .env, which override "
                    + "application.yaml. Fix them there, not in application.yaml. "
                    + "Then POST /api/admin/ai/verify to confirm.");
        }
    }

    private List<String> report(AiProviderType type, AiProviderSettings settings) {
        List<String> problems = new ArrayList<>();
        boolean hasKey = settings.apiKey() != null && !settings.apiKey().isBlank();

        log.info("AI provider {}: endpoint={} reportModel={} impressionModel={} apiKey={} timeout={}s attempts={}",
                type,
                settings.endpoint() != null ? settings.endpoint() : "(unset)",
                settings.reportModel() != null ? settings.reportModel() : "(unset)",
                settings.impressionModel() != null ? settings.impressionModel() : "(unset)",
                hasKey ? "set (" + settings.apiKey().length() + " chars)" : "MISSING",
                settings.timeoutSeconds(), settings.maxAttempts());

        if (!hasKey) {
            problems.add(type + ": API key is not set.");
        }
        if (settings.endpoint() == null || settings.endpoint().isBlank()) {
            problems.add(type + ": endpoint is not set.");
            return problems;
        }

        String endpoint = settings.endpoint().toLowerCase(Locale.ROOT);
        if (type == AiProviderType.GEMINI) {
            // The provider appends /models/{model}:generateContent, so anything beyond the API
            // version in the configured value produces a URL that does not exist.
            String trimmed = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
            String lastSegment = trimmed.substring(trimmed.lastIndexOf('/') + 1);
            if (!"v1beta".equals(lastSegment) && !"v1".equals(lastSegment)) {
                problems.add(type + ": endpoint '" + settings.endpoint() + "' is not an API base. "
                        + "The provider appends /models/{model}:generateContent, so this resolves to a "
                        + "404. Set GEMINI_API_ENDPOINT=" + GEMINI_EXPECTED_BASE);
            }
            if (endpoint.contains("/interactions")) {
                problems.add(type + ": '/interactions' is not a Google endpoint. "
                        + "Set GEMINI_API_ENDPOINT=" + GEMINI_EXPECTED_BASE);
            }
        } else if (type == AiProviderType.OPENAI && !endpoint.contains("/responses")) {
            problems.add(type + ": endpoint '" + settings.endpoint() + "' does not look like the "
                    + "Responses API. This provider sends Responses-shaped requests; expected "
                    + "https://api.openai.com/v1/responses");
        }

        return problems;
    }
}
