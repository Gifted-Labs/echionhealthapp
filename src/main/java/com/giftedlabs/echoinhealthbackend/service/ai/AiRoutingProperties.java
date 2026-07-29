package com.giftedlabs.echoinhealthbackend.service.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@Data
@ConfigurationProperties(prefix = "ai")
public class AiRoutingProperties {

    private String defaultProvider = "GEMINI";
    private String fallbackProvider = "OPENAI";
    private boolean allowFallback = true;

    /**
     * Log full AI request and response bodies on success. Off by default: request bodies contain
     * the clinical notes being reported on, so enabling this writes PHI into application logs.
     * Failure responses are always logged regardless — a provider error body is an API
     * diagnostic, not clinical content.
     */
    private boolean logPayloads = false;
    private ProviderSettings openai = new ProviderSettings();
    private ProviderSettings gemini = new ProviderSettings();

    /** Single place that converts bound config into the immutable settings the providers use. */
    public AiProviderSettings settingsFor(AiProviderType type) {
        ProviderSettings source = type == AiProviderType.GEMINI ? gemini : openai;
        return AiProviderSettings.builder()
                .apiKey(source.getApiKey())
                .endpoint(source.getEndpoint())
                .reportModel(source.getReportModel())
                .impressionModel(source.getImpressionModel())
                .inputCostPerMillion(source.getInputCostPerMillion())
                .outputCostPerMillion(source.getOutputCostPerMillion())
                .timeoutSeconds(source.getTimeoutSeconds())
                .maxAttempts(source.getMaxAttempts())
                .build();
    }

    @Data
    public static class ProviderSettings {
        private String apiKey;
        private String endpoint;
        private String reportModel;
        private String impressionModel;
        /**
         * Read timeout. UR-075 targets 5-10s end to end; this is the per-attempt ceiling
         * before the router gives up on this provider and tries the fallback.
         */
        private Integer timeoutSeconds = 12;
        /** Attempts per provider for cheap, retryable failures (429 / 5xx / connection reset). */
        private Integer maxAttempts = 2;
        private BigDecimal inputCostPerMillion = BigDecimal.ZERO;
        private BigDecimal outputCostPerMillion = BigDecimal.ZERO;
    }
}
