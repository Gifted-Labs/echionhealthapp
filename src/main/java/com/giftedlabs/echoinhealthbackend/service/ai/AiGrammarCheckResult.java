package com.giftedlabs.echoinhealthbackend.service.ai;

import java.util.List;

/**
 * Result of an Auto-Grammar Check pass over report prose.
 */
public record AiGrammarCheckResult(
        String originalText,
        String correctedText,
        List<Correction> corrections,
        String provider,
        String model,
        Integer inputTokens,
        Integer outputTokens) {

    public record Correction(String original, String corrected, String reason) {
    }
}
