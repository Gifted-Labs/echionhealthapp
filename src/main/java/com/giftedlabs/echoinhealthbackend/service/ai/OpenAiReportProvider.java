package com.giftedlabs.echoinhealthbackend.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.giftedlabs.echoinhealthbackend.exception.AiProviderException;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * OpenAI Responses API provider.
 *
 * <p>The previous schema declared {@code strict: true} while giving structuredFindings
 * {@code additionalProperties: true} and omitting it from {@code required}. Strict mode
 * requires every property to be listed in {@code required} and {@code additionalProperties:
 * false} on every object, so the API rejected every request with HTTP 400.
 */
@Service
public class OpenAiReportProvider extends AbstractHttpAiProvider {

    private final AiProviderSettings settings;

    public OpenAiReportProvider(ObjectMapper objectMapper, AiRoutingProperties properties) {
        super(objectMapper, properties.isLogPayloads());
        this.settings = properties.settingsFor(AiProviderType.OPENAI);
    }

    @Override
    public AiProviderType providerType() {
        return AiProviderType.OPENAI;
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
                call(settings.reportModel(), request.prompt(), "structured_report", REPORT_SCHEMA));
    }

    @Override
    public AiModelResponse suggestImpression(AiProviderRequest request) {
        return parseStructuredPayload(
                call(settings.impressionModel(), request.prompt(), "structured_report", REPORT_SCHEMA));
    }

    @Override
    public AiGrammarCheckResult checkGrammar(AiProviderRequest request, String originalText) {
        return parseGrammarPayload(
                call(settings.impressionModel(), request.prompt(), "grammar_check", GRAMMAR_SCHEMA),
                originalText);
    }

    private ProviderPayload call(String model, String prompt, String schemaName, String schema) {
        return postJson(settings, settings.endpoint(), model,
                buildBody(model, prompt, schemaName, schema),
                Map.of("Authorization", "Bearer " + settings.apiKey()));
    }

    @Override
    protected ProviderPayload extractPayload(JsonNode root, String model) {
        if (root.hasNonNull("error")) {
            throw new AiProviderException(
                    "OpenAI error: " + root.path("error").path("message").asText("unknown"), false);
        }

        String text = extractOutputText(root);
        if (text.isBlank()) {
            String status = root.path("status").asText("");
            if ("incomplete".equals(status)) {
                throw new AiProviderException("OpenAI response was truncated ("
                        + root.path("incomplete_details").path("reason").asText("unknown") + ")", true);
            }
            throw new AiProviderException("OpenAI returned an empty structured response", true);
        }

        JsonNode usage = root.path("usage");
        return new ProviderPayload(text, model,
                intOrNull(usage.get("input_tokens")),
                intOrNull(usage.get("output_tokens")));
    }

    /**
     * The Responses API returns a heterogeneous {@code output} array — reasoning items can
     * precede the message — so indexing {@code output[0].content[0]} is not safe. Collect the
     * text from every {@code output_text} content block instead.
     */
    private String extractOutputText(JsonNode root) {
        JsonNode shortcut = root.get("output_text");
        if (shortcut != null && shortcut.isTextual() && !shortcut.asText().isBlank()) {
            return shortcut.asText();
        }

        StringBuilder text = new StringBuilder();
        for (JsonNode item : root.path("output")) {
            if (!"message".equals(item.path("type").asText("message"))) {
                continue;
            }
            for (JsonNode content : item.path("content")) {
                if ("output_text".equals(content.path("type").asText())) {
                    text.append(content.path("text").asText(""));
                }
            }
        }
        return text.toString();
    }

    private String buildBody(String model, String prompt, String schemaName, String schema) {
        return """
                {
                  "model": "%s",
                  "input": [
                    { "role": "user", "content": %s }
                  ],
                  "text": {
                    "format": {
                      "type": "json_schema",
                      "name": "%s",
                      "strict": true,
                      "schema": %s
                    }
                  }
                }
                """.formatted(model, asJsonString(prompt), schemaName, schema);
    }

    /**
     * Strict mode requires every property to appear in {@code required} and
     * {@code additionalProperties: false} on every object, including nested ones.
     */
    private static final String REPORT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "findings": { "type": "string" },
                "impression": { "type": "string" },
                "recommendations": { "type": "array", "items": { "type": "string" } },
                "structuredFindings": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "section": { "type": "string" },
                      "details": { "type": "string" }
                    },
                    "required": ["section", "details"],
                    "additionalProperties": false
                  }
                }
              },
              "required": ["findings", "impression", "recommendations", "structuredFindings"],
              "additionalProperties": false
            }
            """;

    private static final String GRAMMAR_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "correctedText": { "type": "string" },
                "corrections": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "original": { "type": "string" },
                      "corrected": { "type": "string" },
                      "reason": { "type": "string" }
                    },
                    "required": ["original", "corrected", "reason"],
                    "additionalProperties": false
                  }
                }
              },
              "required": ["correctedText", "corrections"],
              "additionalProperties": false
            }
            """;
}
