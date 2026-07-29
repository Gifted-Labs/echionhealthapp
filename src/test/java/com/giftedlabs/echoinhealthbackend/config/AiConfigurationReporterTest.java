package com.giftedlabs.echoinhealthbackend.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.giftedlabs.echoinhealthbackend.service.ai.AiRoutingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The startup reporter has to catch the misconfiguration that actually shipped: a {@code .env}
 * pointing Gemini at {@code /v1beta/interactions}. That value overrides application.yaml, so the
 * repository defaults being correct proves nothing about the running process — the only symptom
 * was a generic 503 on the first generation attempt.
 */
class AiConfigurationReporterTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void captureLogs() {
        logger = (Logger) LoggerFactory.getLogger(AiConfigurationReporter.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    private AiRoutingProperties properties(String geminiEndpoint, String openAiEndpoint) {
        AiRoutingProperties properties = new AiRoutingProperties();

        AiRoutingProperties.ProviderSettings gemini = new AiRoutingProperties.ProviderSettings();
        gemini.setApiKey("test-gemini-key");
        gemini.setEndpoint(geminiEndpoint);
        gemini.setReportModel("gemini-2.5-flash");
        gemini.setImpressionModel("gemini-2.5-flash-lite");
        properties.setGemini(gemini);

        AiRoutingProperties.ProviderSettings openai = new AiRoutingProperties.ProviderSettings();
        openai.setApiKey("test-openai-key");
        openai.setEndpoint(openAiEndpoint);
        openai.setReportModel("gpt-4.1-mini");
        openai.setImpressionModel("gpt-4.1-mini");
        properties.setOpenai(openai);

        return properties;
    }

    private String warnings() {
        return appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }

    /** The exact value from the deployed .env that caused the 503. */
    @Test
    void flagsTheInteractionsEndpointThatShippedInDotEnv() {
        new AiConfigurationReporter(properties(
                "https://generativelanguage.googleapis.com/v1beta/interactions",
                "https://api.openai.com/v1/responses")).reportConfiguration();

        String warnings = warnings();
        assertTrue(warnings.contains("/interactions"),
                "the reporter must name the offending path segment");
        assertTrue(warnings.contains("GEMINI_API_ENDPOINT=https://generativelanguage.googleapis.com/v1beta"),
                "the reporter must state the exact value to set");
        assertTrue(warnings.contains("404"),
                "the reporter must explain what the misconfiguration causes");
    }

    @Test
    void flagsAnyGeminiEndpointThatIsNotAnApiBase() {
        new AiConfigurationReporter(properties(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
                "https://api.openai.com/v1/responses")).reportConfiguration();

        assertTrue(warnings().contains("is not an API base"),
                "a full endpoint rather than a base must be rejected too");
    }

    @Test
    void acceptsCorrectConfigurationSilently() {
        for (String base : List.of(
                "https://generativelanguage.googleapis.com/v1beta",
                "https://generativelanguage.googleapis.com/v1beta/",
                "https://generativelanguage.googleapis.com/v1")) {
            appender.list.clear();
            new AiConfigurationReporter(properties(base, "https://api.openai.com/v1/responses"))
                    .reportConfiguration();
            assertTrue(warnings().isEmpty(), "'" + base + "' is valid but was flagged: " + warnings());
        }
    }

    @Test
    void flagsMissingApiKeys() {
        AiRoutingProperties properties = properties(
                "https://generativelanguage.googleapis.com/v1beta",
                "https://api.openai.com/v1/responses");
        properties.getGemini().setApiKey("");

        new AiConfigurationReporter(properties).reportConfiguration();

        assertTrue(warnings().contains("GEMINI: API key is not set"));
    }

    /** The effective values must be visible even when nothing is wrong. */
    @Test
    void alwaysReportsEffectiveConfigurationAtInfo() {
        new AiConfigurationReporter(properties(
                "https://generativelanguage.googleapis.com/v1beta",
                "https://api.openai.com/v1/responses")).reportConfiguration();

        String info = appender.list.stream()
                .filter(event -> event.getLevel() == Level.INFO)
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));

        assertTrue(info.contains("gemini-2.5-flash"), "the resolved model must be logged");
        assertTrue(info.contains("generativelanguage.googleapis.com"), "the resolved endpoint must be logged");
        assertFalse(info.contains("test-gemini-key"), "the API key itself must never be logged");
    }
}
