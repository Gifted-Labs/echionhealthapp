package com.giftedlabs.echoinhealthbackend.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.giftedlabs.echoinhealthbackend.exception.AiProviderException;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Google Generative Language API (Gemini) provider.
 *
 * <p>A previous revision of this class sent OpenAI Responses-shaped bodies
 * ({@code model}/{@code input}/{@code response_format}) to a non-existent
 * {@code /v1beta/interactions} endpoint and read {@code output_text} back, so no call against
 * Google could ever have succeeded. This implementation speaks the real protocol:
 * {@code POST {base}/models/{model}:generateContent} with {@code contents[].parts[].text},
 * a {@code generationConfig.responseSchema} for structured output, and
 * {@code candidates[0].content.parts[0].text} on the way back.
 */
@Service
public class GeminiReportProvider extends AbstractHttpAiProvider {

    private final AiProviderSettings settings;

    public GeminiReportProvider(ObjectMapper objectMapper, AiRoutingProperties properties) {
        super(objectMapper, properties.isLogPayloads());
        this.settings = properties.settingsFor(AiProviderType.GEMINI);
    }

    @Override
    public AiProviderType providerType() {
        return AiProviderType.GEMINI;
    }

    @Override
    public boolean isConfigured() {
        return settings.apiKey() != null && !settings.apiKey().isBlank()
                && settings.endpoint() != null && !settings.endpoint().isBlank()
                && settings.reportModel() != null && !settings.reportModel().isBlank();
    }

    @Override
    public AiModelResponse generateStructuredReport(AiProviderRequest request) {
        return parseStructuredPayload(
                call(settings.reportModel(), request.prompt(), REPORT_SCHEMA));
    }

    @Override
    public AiModelResponse suggestImpression(AiProviderRequest request) {
        return parseStructuredPayload(
                call(settings.impressionModel(), request.prompt(), REPORT_SCHEMA));
    }

    @Override
    public AiGrammarCheckResult checkGrammar(AiProviderRequest request, String originalText) {
        return parseGrammarPayload(
                call(settings.impressionModel(), request.prompt(), GRAMMAR_SCHEMA), originalText);
    }

    private ProviderPayload call(String model, String prompt, String schema) {
        return postJson(settings, generateContentUrl(model), model, buildBody(prompt, schema),
                Map.of("x-goog-api-key", settings.apiKey()));
    }

    /**
     * Gemini takes the model as a path segment, not a body field — which is why the previous
     * implementation's configured model names had no effect whatsoever.
     */
    private String generateContentUrl(String model) {
        String base = settings.endpoint();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/models/" + model + ":generateContent";
    }

    @Override
    protected ProviderPayload extractPayload(JsonNode root, String model) {
        JsonNode candidate = root.path("candidates").path(0);

        String finishReason = candidate.path("finishReason").asText("");
        if ("SAFETY".equals(finishReason) || "RECITATION".equals(finishReason)) {
            throw new AiProviderException(
                    "Gemini blocked the response (finishReason=" + finishReason + ")", false);
        }
        if ("MAX_TOKENS".equals(finishReason)) {
            throw new AiProviderException("Gemini response was truncated before valid JSON", true);
        }

        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            text.append(part.path("text").asText(""));
        }
        if (text.isEmpty()) {
            throw new AiProviderException("Gemini returned an empty response", true);
        }

        JsonNode usage = root.path("usageMetadata");
        return new ProviderPayload(text.toString(), model,
                intOrNull(usage.get("promptTokenCount")),
                intOrNull(usage.get("candidatesTokenCount")));
    }

    /**
     * Gemini validates {@code responseSchema} against the OpenAPI 3.0 subset: no
     * {@code additionalProperties}, no free-form objects. structuredFindings is therefore an
     * array of section/details pairs, matching what the OpenAI provider asks for.
     */
    private String buildBody(String prompt, String schema) {
        return """
                {
                  "contents": [
                    { "role": "user", "parts": [ { "text": %s } ] }
                  ],
                  "generationConfig": {
                    "temperature": 0.2,
                    "responseMimeType": "application/json",
                    "responseSchema": %s
                  }
                }
                """.formatted(asJsonString(prompt), schema);
    }

    private static final String REPORT_SCHEMA = """
            {
              "type": "OBJECT",
              "properties": {
                "findings": { "type": "STRING" },
                "impression": { "type": "STRING" },
                "recommendations": { "type": "ARRAY", "items": { "type": "STRING" } },
                "structuredFindings": {
                  "type": "ARRAY",
                  "items": {
                    "type": "OBJECT",
                    "properties": {
                      "section": { "type": "STRING" },
                      "details": { "type": "STRING" }
                    },
                    "required": ["section", "details"]
                  }
                }
              },
              "required": ["findings", "impression", "recommendations", "structuredFindings"]
            }
            """;

    private static final String GRAMMAR_SCHEMA = """
            {
              "type": "OBJECT",
              "properties": {
                "correctedText": { "type": "STRING" },
                "corrections": {
                  "type": "ARRAY",
                  "items": {
                    "type": "OBJECT",
                    "properties": {
                      "original": { "type": "STRING" },
                      "corrected": { "type": "STRING" },
                      "reason": { "type": "STRING" }
                    },
                    "required": ["original", "corrected", "reason"]
                  }
                }
              },
              "required": ["correctedText", "corrections"]
            }
            """;
}
