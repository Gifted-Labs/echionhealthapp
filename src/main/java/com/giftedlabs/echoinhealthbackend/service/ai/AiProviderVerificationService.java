package com.giftedlabs.echoinhealthbackend.service.ai;

import com.giftedlabs.echoinhealthbackend.dto.admin.AiProviderStatusResponse;
import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Live connectivity probe for the configured AI providers.
 *
 * <p>Exists because "the code compiles and the DTOs look right" is not evidence that a
 * provider integration works — the previous Gemini adapter spoke the wrong protocol entirely
 * and nothing in the system could have told you. This performs a real round trip with a
 * throwaway prompt and reports exactly what came back, without touching organization AI
 * credits.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiProviderVerificationService {

    private static final String PROBE_PROMPT = """
            Reply using the required JSON schema only.
            Set "findings" to "connectivity probe", "impression" to "ok",
            "recommendations" to an empty array, and "structuredFindings" to an empty array.
            """;

    private final List<AiReportProvider> providers;
    private final AiRoutingProperties properties;

    public List<AiProviderStatusResponse> verifyAll() {
        AiProviderType primary = AiProviderType.from(properties.getDefaultProvider(), AiProviderType.GEMINI);
        AiProviderType fallback = AiProviderType.from(properties.getFallbackProvider(), AiProviderType.OPENAI);

        List<AiProviderStatusResponse> statuses = new ArrayList<>();
        for (AiReportProvider provider : providers) {
            statuses.add(verify(provider, primary, fallback));
        }
        return statuses;
    }

    private AiProviderStatusResponse verify(AiReportProvider provider,
                                            AiProviderType primary,
                                            AiProviderType fallback) {
        AiProviderType type = provider.providerType();
        AiProviderSettings settings = properties.settingsFor(type);

        AiProviderStatusResponse.AiProviderStatusResponseBuilder status = AiProviderStatusResponse.builder()
                .provider(type.name())
                .configured(provider.isConfigured())
                .reportModel(settings.reportModel())
                .impressionModel(settings.impressionModel())
                .endpoint(settings.endpoint())
                .primary(type == primary)
                .fallback(properties.isAllowFallback() && type == fallback);

        if (!provider.isConfigured()) {
            return status.reachable(false)
                    .failureReason("Provider is not configured (missing API key, endpoint, or model).")
                    .build();
        }

        Instant startedAt = Instant.now();
        try {
            AiModelResponse response = provider.generateStructuredReport(AiProviderRequest.builder()
                    .prompt(PROBE_PROMPT)
                    .promptVersion("probe")
                    .scanType(ScanType.GENERAL)
                    .measurements(Map.of())
                    .build());
            return status.reachable(true)
                    .latencyMs(Duration.between(startedAt, Instant.now()).toMillis())
                    .reportModel(response.model())
                    .build();
        } catch (RuntimeException e) {
            log.warn("AI provider verification failed for {}", type, e);
            return status.reachable(false)
                    .latencyMs(Duration.between(startedAt, Instant.now()).toMillis())
                    .failureReason(e.getMessage())
                    .build();
        }
    }
}
