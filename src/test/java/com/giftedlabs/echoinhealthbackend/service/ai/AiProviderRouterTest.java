package com.giftedlabs.echoinhealthbackend.service.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AiProviderRouterTest {

    @Test
    void primaryProviderFallsBackToConfiguredSecondary() {
        AiRoutingProperties properties = new AiRoutingProperties();
        properties.setDefaultProvider("GEMINI");
        properties.setFallbackProvider("OPENAI");
        properties.setAllowFallback(true);

        AiProviderRouter router = new AiProviderRouter(List.of(
                new StubProvider(AiProviderType.GEMINI, false),
                new StubProvider(AiProviderType.OPENAI, true)), properties);

        assertEquals(AiProviderType.OPENAI, router.primaryProvider(null).providerType());
    }

    @Test
    void fallbackProviderReturnsNullWhenDisabled() {
        AiRoutingProperties properties = new AiRoutingProperties();
        properties.setAllowFallback(false);

        AiProviderRouter router = new AiProviderRouter(List.of(
                new StubProvider(AiProviderType.GEMINI, true),
                new StubProvider(AiProviderType.OPENAI, true)), properties);

        assertNull(router.fallbackProvider(AiProviderType.GEMINI));
    }

    private record StubProvider(AiProviderType providerType, boolean configured) implements AiReportProvider {

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public AiModelResponse generateStructuredReport(AiProviderRequest request) {
            return AiModelResponse.builder()
                    .provider(providerType.name())
                    .model("test-model")
                    .findings("f")
                    .impression("i")
                    .recommendations(List.of())
                    .structuredFindings(Map.of())
                    .build();
        }

        @Override
        public AiGrammarCheckResult checkGrammar(AiProviderRequest request, String originalText) {
            return new AiGrammarCheckResult(originalText, originalText, java.util.List.of(),
                    providerType.name(), "stub-model", null, null);
        }

        @Override
        public AiModelResponse suggestImpression(AiProviderRequest request) {
            return generateStructuredReport(request);
        }
    }
}
