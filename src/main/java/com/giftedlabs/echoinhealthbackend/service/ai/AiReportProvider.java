package com.giftedlabs.echoinhealthbackend.service.ai;

public interface AiReportProvider {

    AiProviderType providerType();

    boolean isConfigured();

    AiModelResponse generateStructuredReport(AiProviderRequest request);

    AiModelResponse suggestImpression(AiProviderRequest request);

    /** Auto-Grammar Check (Pro/Ultimate tiers). Returns discrete edits, never applied silently. */
    AiGrammarCheckResult checkGrammar(AiProviderRequest request, String originalText);
}
