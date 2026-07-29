package com.giftedlabs.echoinhealthbackend.service.ai;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record AiProviderSettings(
        String apiKey,
        String endpoint,
        String reportModel,
        String impressionModel,
        BigDecimal inputCostPerMillion,
        BigDecimal outputCostPerMillion,
        Integer timeoutSeconds,
        Integer maxAttempts) {
}
