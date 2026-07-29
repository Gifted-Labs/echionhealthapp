package com.giftedlabs.echoinhealthbackend.service.ai;

public enum AiProviderType {
    GEMINI,
    OPENAI;

    public static AiProviderType from(String value, AiProviderType fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return AiProviderType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
