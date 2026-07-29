package com.giftedlabs.echoinhealthbackend.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.giftedlabs.echoinhealthbackend.exception.AiProviderException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
abstract class AbstractHttpAiProvider implements AiReportProvider {

    /** Both providers are asked for this shape, so one parser serves both. */
    protected static final String FIELD_FINDINGS = "findings";
    protected static final String FIELD_IMPRESSION = "impression";
    protected static final String FIELD_RECOMMENDATIONS = "recommendations";
    protected static final String FIELD_STRUCTURED = "structuredFindings";

    protected final ObjectMapper objectMapper;

    /**
     * Whether to log full request and response bodies on success. Off by default: the request
     * body carries the clinical notes under report, so enabling this writes PHI to the logs.
     */
    private final boolean logPayloads;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    protected AbstractHttpAiProvider(ObjectMapper objectMapper, boolean logPayloads) {
        this.objectMapper = objectMapper;
        this.logPayloads = logPayloads;
    }

    /**
     * POSTs a JSON body and parses the provider's reply.
     *
     * <p>Retries only failures that are both retryable and cheap to retry. A read timeout is
     * deliberately <em>not</em> retried: it has already consumed the whole latency budget, and
     * the router's fallback provider is the better next move.
     */
    protected ProviderPayload postJson(AiProviderSettings settings,
                                       String url,
                                       String model,
                                       String body,
                                       Map<String, String> headers) {
        int maxAttempts = Math.max(1, settings.maxAttempts() != null ? settings.maxAttempts() : 2);
        AiProviderException lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return executeOnce(settings, url, model, body, headers);
            } catch (AiProviderException e) {
                lastFailure = e;
                if (!e.isRetryable() || attempt == maxAttempts) {
                    throw e;
                }
                log.warn("{} attempt {}/{} failed ({}), retrying",
                        providerType(), attempt, maxAttempts, e.getMessage());
                backoff(attempt);
            }
        }
        throw lastFailure;
    }

    private ProviderPayload executeOnce(AiProviderSettings settings,
                                        String url,
                                        String model,
                                        String body,
                                        Map<String, String> headers) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(settings.timeoutSeconds() != null
                            ? settings.timeoutSeconds() : 12))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(requestBuilder::header);

            HttpResponse<String> response =
                    httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                logRawFailure(url, model, response);
            } else if (logPayloads) {
                logRawSuccess(url, model, body, response);
            }

            if (response.statusCode() == 429) {
                throw new AiProviderException(
                        providerType() + " rate limited (HTTP 429) calling " + url, true);
            }
            if (response.statusCode() >= 500) {
                throw new AiProviderException(providerType() + " upstream failure: HTTP "
                        + response.statusCode() + " calling " + url, true);
            }
            if (response.statusCode() >= 400) {
                // 4xx is a contract problem — wrong endpoint, unknown model id, bad key, or an
                // invalid schema. Retrying is pointless, so the URL, the model and the provider's
                // own error body are the whole diagnosis and all three belong in the message.
                throw new AiProviderException(String.format(
                        "%s rejected request: HTTP %d. url=%s model=%s response=%s%s",
                        providerType(), response.statusCode(), url, model,
                        snippet(response.body()), hintFor(response.statusCode(), url)), false);
            }

            return extractPayload(objectMapper.readTree(response.body()), model);
        } catch (HttpTimeoutException e) {
            throw new AiProviderException(String.format(
                    "%s timed out after %ds. url=%s model=%s",
                    providerType(), settings.timeoutSeconds(), url, model), e, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException(providerType() + " call interrupted", e, false);
        } catch (IOException e) {
            throw new AiProviderException(String.format(
                    "%s call failed: %s. url=%s", providerType(), e.getMessage(), url), e, true);
        }
    }

    /**
     * Logs the provider's raw reply verbatim whenever a call fails.
     *
     * <p>Always on, and safe to be: a provider error body is an API diagnostic, not clinical
     * content. This is the single most useful artefact when a call fails and it was previously
     * truncated to 300 characters inside an exception message.
     *
     * <p>An empty body alongside a 404 is itself diagnostic — it means the request never reached
     * a handler, so the URL is wrong rather than the model.
     */
    private void logRawFailure(String url, String model, HttpResponse<String> response) {
        String rawBody = response.body();
        log.error("""
                        {} API call FAILED -- raw response follows
                          POST     {}
                          model    {}
                          status   HTTP {}
                          headers  content-type={}
                          body     {}{}""",
                providerType(), url, model, response.statusCode(),
                response.headers().firstValue("content-type").orElse("(none)"),
                rawBody == null || rawBody.isBlank()
                        ? "(EMPTY -- the server returned no body, which usually means the URL "
                          + "path does not exist rather than the request being rejected)"
                        : truncate(rawBody, 4000),
                hintFor(response.statusCode(), url));
    }

    /**
     * Full request and response bodies, for deliberate debugging only.
     *
     * <p>Gated behind {@code ai.log-payloads} and off by default because the request body
     * contains the clinical notes being reported on. Turning this on writes patient findings
     * into application logs, which is a decision about PHI handling, not a logging preference.
     */
    private void logRawSuccess(String url, String model, String requestBody,
                               HttpResponse<String> response) {
        log.debug("""
                        {} API call OK -- payload logging is ENABLED (ai.log-payloads=true)
                          POST     {}
                          model    {}
                          status   HTTP {}
                          request  {}
                          response {}""",
                providerType(), url, model, response.statusCode(),
                truncate(requestBody, 4000), truncate(response.body(), 4000));
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "(null)";
        }
        return value.length() > max
                ? value.substring(0, max) + "... [" + (value.length() - max) + " more chars]"
                : value;
    }

    /**
     * Turns the two failure codes that are almost always configuration mistakes into a direct
     * instruction, because the provider's own error body rarely names the setting at fault.
     */
    private String hintFor(int status, String url) {
        return switch (status) {
            case 404 -> " -- HINT: 404 means this URL does not exist. Check the endpoint and the "
                    + "model id. For Gemini the configured endpoint must be the API *base* "
                    + "(https://generativelanguage.googleapis.com/v1beta); the provider appends "
                    + "/models/{model}:generateContent itself.";
            case 401, 403 -> " -- HINT: the endpoint exists but rejected the credential. Check the "
                    + "API key for this provider, and that the key is enabled for this model.";
            case 400 -> " -- HINT: the request was malformed for this endpoint. Most often an "
                    + "unknown model id, or a model that does not support structured output.";
            default -> "";
        };
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(Math.min(1000L, 200L * attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException(providerType() + " retry interrupted", e, false);
        }
    }

    /**
     * The JSON document the model emitted, plus token usage. Separating transport from
     * interpretation lets one HTTP path serve several task schemas (report, impression,
     * grammar) without each provider re-implementing retries and error classification.
     */
    protected record ProviderPayload(String text, String model,
                                     Integer inputTokens, Integer outputTokens) {
    }

    /** Pulls the model's JSON text and usage counters out of the provider's envelope. */
    protected abstract ProviderPayload extractPayload(JsonNode root, String model);

    /**
     * Parses the structured-report document. Shared by both providers because both are
     * constrained to the same response schema.
     */
    protected AiModelResponse parseStructuredPayload(ProviderPayload payload) {
        JsonNode parsed = readJson(payload.text());
        String model = payload.model();
        Integer inputTokens = payload.inputTokens();
        Integer outputTokens = payload.outputTokens();

        String findings = parsed.path(FIELD_FINDINGS).asText("");
        if (findings.isBlank()) {
            throw new AiProviderException(providerType() + " returned no findings", true);
        }

        return AiModelResponse.builder()
                .findings(findings)
                .impression(parsed.path(FIELD_IMPRESSION).asText(""))
                .recommendations(recommendations(parsed.get(FIELD_RECOMMENDATIONS)))
                .structuredFindings(structuredFindings(findings, parsed.get(FIELD_STRUCTURED)))
                .provider(providerType().name())
                .model(model)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .build();
    }

    /**
     * Parses the grammar-check document. Corrections are returned as discrete edits rather
     * than only as rewritten prose, so a clinician can see exactly what would change to a
     * clinical document before accepting anything.
     */
    protected AiGrammarCheckResult parseGrammarPayload(ProviderPayload payload, String originalText) {
        JsonNode parsed = readJson(payload.text());

        String correctedText = parsed.path("correctedText").asText("");
        if (correctedText.isBlank()) {
            throw new AiProviderException(providerType() + " returned no corrected text", true);
        }

        List<AiGrammarCheckResult.Correction> corrections = new java.util.ArrayList<>();
        for (JsonNode node : parsed.path("corrections")) {
            String original = node.path("original").asText("");
            String corrected = node.path("corrected").asText("");
            if (!original.isBlank() && !corrected.isBlank() && !original.equals(corrected)) {
                corrections.add(new AiGrammarCheckResult.Correction(
                        original, corrected, node.path("reason").asText("")));
            }
        }

        return new AiGrammarCheckResult(originalText, correctedText, corrections,
                providerType().name(), payload.model(),
                payload.inputTokens(), payload.outputTokens());
    }

    private JsonNode readJson(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new AiProviderException(
                    providerType() + " returned malformed JSON: " + snippet(payload), e, true);
        }
    }

    /**
     * Both providers express structuredFindings as an array of {@code {section, details}} pairs.
     * A free-form object is not representable in OpenAI strict mode (which forbids
     * {@code additionalProperties: true}) nor in Gemini's OpenAPI schema subset, so the array
     * form is the only shape both can actually enforce. It is flattened back to a map here.
     */
    protected Map<String, Object> structuredFindings(String findings, JsonNode node) {
        Map<String, Object> sections = new LinkedHashMap<>();
        if (node != null && node.isArray()) {
            for (JsonNode entry : node) {
                String section = entry.path("section").asText("");
                String details = entry.path("details").asText("");
                if (!section.isBlank() && !details.isBlank()) {
                    sections.put(section, details);
                }
            }
        } else if (node != null && node.isObject()) {
            // Tolerate the older free-form object shape rather than failing the generation.
            node.properties().forEach(field -> sections.put(field.getKey(), field.getValue().asText()));
        }
        if (sections.isEmpty()) {
            sections.put("narrative", findings);
        }
        return sections;
    }

    protected List<String> recommendations(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .map(JsonNode::asText)
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    protected Integer intOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asInt();
    }

    protected String asJsonString(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize prompt for " + providerType(), e);
        }
    }

    private String snippet(String body) {
        if (body == null || body.isBlank()) {
            return "(empty body)";
        }
        String flat = body.replaceAll("\\s+", " ").trim();
        return flat.length() > 300 ? flat.substring(0, 300) + "..." : flat;
    }
}
