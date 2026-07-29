package com.giftedlabs.echoinhealthbackend.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import com.giftedlabs.echoinhealthbackend.exception.AiProviderException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the request and response wire formats of both providers against a local stub.
 *
 * <p>This is the test that was missing. The previous Gemini adapter posted an OpenAI
 * Responses body to a non-existent Google endpoint and read {@code output_text} back — a
 * defect invisible to unit tests that mock the provider interface, because the bug lived
 * entirely in the bytes on the wire.
 */
class AiProviderWireFormatTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private final AtomicReference<String> capturedPath = new AtomicReference<>();
    private final AtomicReference<String> capturedBody = new AtomicReference<>();
    private final AtomicReference<Map<String, String>> capturedHeaders = new AtomicReference<>();
    private final AtomicInteger requestCount = new AtomicInteger();
    private volatile int responseStatus = 200;
    private volatile String responseBody = "{}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        capturedPath.set(exchange.getRequestURI().getPath());
        capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        capturedHeaders.set(Map.of(
                "x-goog-api-key", header(exchange, "x-goog-api-key"),
                "authorization", header(exchange, "Authorization")));

        byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(responseStatus, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private String header(HttpExchange exchange, String name) {
        String value = exchange.getRequestHeaders().getFirst(name);
        return value == null ? "" : value;
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private AiRoutingProperties properties(String geminiEndpoint, String openAiEndpoint) {
        AiRoutingProperties properties = new AiRoutingProperties();

        AiRoutingProperties.ProviderSettings gemini = new AiRoutingProperties.ProviderSettings();
        gemini.setApiKey("gemini-key");
        gemini.setEndpoint(geminiEndpoint);
        gemini.setReportModel("gemini-test-model");
        gemini.setImpressionModel("gemini-test-impression");
        gemini.setTimeoutSeconds(5);
        gemini.setMaxAttempts(1);
        gemini.setInputCostPerMillion(BigDecimal.ONE);
        gemini.setOutputCostPerMillion(BigDecimal.ONE);
        properties.setGemini(gemini);

        AiRoutingProperties.ProviderSettings openai = new AiRoutingProperties.ProviderSettings();
        openai.setApiKey("openai-key");
        openai.setEndpoint(openAiEndpoint);
        openai.setReportModel("openai-test-model");
        openai.setImpressionModel("openai-test-impression");
        openai.setTimeoutSeconds(5);
        openai.setMaxAttempts(1);
        openai.setInputCostPerMillion(BigDecimal.ONE);
        openai.setOutputCostPerMillion(BigDecimal.ONE);
        properties.setOpenai(openai);

        return properties;
    }

    private AiProviderRequest request() {
        return AiProviderRequest.builder()
                .prompt("Liver appears enlarged.")
                .promptVersion("v2")
                .scanType(ScanType.ABDOMINAL)
                .measurements(Map.of())
                .build();
    }

    // ------------------------------------------------------------------ Gemini

    @Test
    void geminiCallsGenerateContentWithTheModelInThePath() throws Exception {
        responseBody = """
                {
                  "candidates": [{
                    "finishReason": "STOP",
                    "content": { "parts": [{ "text": "{\\"findings\\":\\"Hepatomegaly.\\",\\"impression\\":\\"Enlarged liver.\\",\\"recommendations\\":[\\"Clinical correlation advised\\"],\\"structuredFindings\\":[{\\"section\\":\\"Liver\\",\\"details\\":\\"Enlarged, 17cm.\\"}]}" }] }
                  }],
                  "usageMetadata": { "promptTokenCount": 120, "candidatesTokenCount": 64 }
                }
                """;

        GeminiReportProvider provider = new GeminiReportProvider(
                MAPPER, properties(baseUrl() + "/v1beta", baseUrl()));

        AiModelResponse response = provider.generateStructuredReport(request());

        // The model is a PATH segment for Gemini. The old adapter put it in the body, which
        // meant the configured model name had no effect at all.
        assertEquals("/v1beta/models/gemini-test-model:generateContent", capturedPath.get());
        assertEquals("gemini-key", capturedHeaders.get().get("x-goog-api-key"));

        JsonNode body = MAPPER.readTree(capturedBody.get());
        assertEquals("Liver appears enlarged.",
                body.path("contents").path(0).path("parts").path(0).path("text").asText());
        assertEquals("application/json",
                body.path("generationConfig").path("responseMimeType").asText());
        assertFalse(body.has("response_format"), "response_format is OpenAI vocabulary, not Gemini");
        assertFalse(body.has("input"), "input is OpenAI vocabulary, not Gemini");

        assertEquals("Hepatomegaly.", response.findings());
        assertEquals("Enlarged liver.", response.impression());
        assertEquals(List.of("Clinical correlation advised"), response.recommendations());
        assertEquals("Enlarged, 17cm.", response.structuredFindings().get("Liver"));
        assertEquals(120, response.inputTokens());
        assertEquals(64, response.outputTokens());
    }

    @Test
    void geminiTrimsTrailingSlashesFromTheConfiguredBaseUrl() {
        responseBody = geminiSuccessBody();
        GeminiReportProvider provider = new GeminiReportProvider(
                MAPPER, properties(baseUrl() + "/v1beta/", baseUrl()));

        provider.generateStructuredReport(request());

        assertEquals("/v1beta/models/gemini-test-model:generateContent", capturedPath.get());
    }

    @Test
    void geminiSafetyBlockIsReportedAsNonRetryable() {
        responseBody = """
                { "candidates": [{ "finishReason": "SAFETY", "content": { "parts": [] } }] }
                """;
        GeminiReportProvider provider = new GeminiReportProvider(
                MAPPER, properties(baseUrl() + "/v1beta", baseUrl()));

        AiProviderException error = assertThrows(AiProviderException.class,
                () -> provider.generateStructuredReport(request()));
        assertFalse(error.isRetryable());
        assertTrue(error.getMessage().contains("SAFETY"));
    }

    // ------------------------------------------------------------------ OpenAI

    @Test
    void openAiSendsAStrictSchemaThatSatisfiesStrictModeRules() throws Exception {
        responseBody = openAiSuccessBody();
        OpenAiReportProvider provider = new OpenAiReportProvider(
                MAPPER, properties(baseUrl() + "/v1beta", baseUrl() + "/v1/responses"));

        provider.generateStructuredReport(request());

        JsonNode schema = MAPPER.readTree(capturedBody.get())
                .path("text").path("format").path("schema");

        assertTrue(schema.path("additionalProperties").isBoolean());
        assertFalse(schema.path("additionalProperties").asBoolean(),
                "strict mode requires additionalProperties:false");

        // Strict mode requires EVERY property to appear in `required`. The old schema omitted
        // structuredFindings and set additionalProperties:true on it, so OpenAI 400'd.
        List<String> properties = schema.path("properties").propertyStream()
                .map(Map.Entry::getKey).toList();
        List<String> required = schema.path("required").valueStream()
                .map(JsonNode::asText).toList();
        assertEquals(properties.size(), required.size(),
                "every property must be required under strict mode: " + properties + " vs " + required);
        assertTrue(required.containsAll(properties));

        JsonNode structuredItems = schema.path("properties").path("structuredFindings").path("items");
        assertFalse(structuredItems.path("additionalProperties").asBoolean(true),
                "nested objects also need additionalProperties:false");
    }

    @Test
    void openAiSkipsReasoningItemsWhenExtractingOutputText() {
        // Responses API output is heterogeneous; indexing output[0].content[0] is not safe.
        responseBody = """
                {
                  "output": [
                    { "type": "reasoning", "summary": [] },
                    { "type": "message", "content": [
                        { "type": "output_text", "text": "{\\"findings\\":\\"Normal study.\\",\\"impression\\":\\"No abnormality.\\",\\"recommendations\\":[],\\"structuredFindings\\":[]}" }
                    ]}
                  ],
                  "usage": { "input_tokens": 30, "output_tokens": 12 }
                }
                """;
        OpenAiReportProvider provider = new OpenAiReportProvider(
                MAPPER, properties(baseUrl() + "/v1beta", baseUrl() + "/v1/responses"));

        AiModelResponse response = provider.generateStructuredReport(request());

        assertEquals("Normal study.", response.findings());
        assertEquals("No abnormality.", response.impression());
        assertEquals(30, response.inputTokens());
    }

    @Test
    void httpErrorsAreClassifiedForRetryCorrectly() {
        OpenAiReportProvider provider = new OpenAiReportProvider(
                MAPPER, properties(baseUrl() + "/v1beta", baseUrl() + "/v1/responses"));

        responseStatus = 400;
        responseBody = "{\"error\":{\"message\":\"Invalid schema\"}}";
        AiProviderException badRequest = assertThrows(AiProviderException.class,
                () -> provider.generateStructuredReport(request()));
        assertFalse(badRequest.isRetryable(), "a 4xx contract error must not be retried");

        responseStatus = 503;
        responseBody = "{}";
        AiProviderException unavailable = assertThrows(AiProviderException.class,
                () -> provider.generateStructuredReport(request()));
        assertTrue(unavailable.isRetryable(), "a 5xx must be retryable");
    }

    @Test
    void retryableFailuresAreRetriedUpToMaxAttempts() {
        AiRoutingProperties properties = properties(baseUrl() + "/v1beta", baseUrl() + "/v1/responses");
        properties.getOpenai().setMaxAttempts(3);
        OpenAiReportProvider provider = new OpenAiReportProvider(MAPPER, properties);

        responseStatus = 503;
        responseBody = "{}";
        requestCount.set(0);

        assertThrows(AiProviderException.class, () -> provider.generateStructuredReport(request()));
        assertEquals(3, requestCount.get(), "retryable failures should exhaust maxAttempts");
    }

    @Test
    void nonRetryableFailuresAreNotRetried() {
        AiRoutingProperties properties = properties(baseUrl() + "/v1beta", baseUrl() + "/v1/responses");
        properties.getOpenai().setMaxAttempts(3);
        OpenAiReportProvider provider = new OpenAiReportProvider(MAPPER, properties);

        responseStatus = 401;
        responseBody = "{}";
        requestCount.set(0);

        assertThrows(AiProviderException.class, () -> provider.generateStructuredReport(request()));
        assertEquals(1, requestCount.get(), "a bad API key must not be retried");
    }

    private String geminiSuccessBody() {
        return """
                {
                  "candidates": [{
                    "finishReason": "STOP",
                    "content": { "parts": [{ "text": "{\\"findings\\":\\"F\\",\\"impression\\":\\"I\\",\\"recommendations\\":[],\\"structuredFindings\\":[]}" }] }
                  }],
                  "usageMetadata": { "promptTokenCount": 1, "candidatesTokenCount": 1 }
                }
                """;
    }

    private String openAiSuccessBody() {
        return """
                {
                  "output": [{ "type": "message", "content": [
                    { "type": "output_text", "text": "{\\"findings\\":\\"F\\",\\"impression\\":\\"I\\",\\"recommendations\\":[],\\"structuredFindings\\":[]}" }
                  ]}],
                  "usage": { "input_tokens": 1, "output_tokens": 1 }
                }
                """;
    }
}
