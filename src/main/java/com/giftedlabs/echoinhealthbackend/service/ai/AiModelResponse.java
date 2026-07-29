package com.giftedlabs.echoinhealthbackend.service.ai;

import lombok.Builder;

import java.util.Map;

@Builder
public record AiModelResponse(
        String findings,
        String impression,
        java.util.List<String> recommendations,
        Map<String, Object> structuredFindings,
        String provider,
        String model,
        Integer inputTokens,
        Integer outputTokens) {
}
