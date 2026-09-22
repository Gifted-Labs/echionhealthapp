package com.giftedlabs.echoinhealthbackend.service.ai;

import lombok.Builder;

import java.util.List;

/**
 * What the model returned for a terminology lookup.
 *
 * <p>Carries provider, model and token counts alongside the matches for the same reason
 * {@link AiGrammarCheckResult} does: every AI call is recorded in {@code ai_generation_events}
 * with its cost, and a task that cannot report its own usage is a task that silently escapes
 * the cost telemetry.
 */
@Builder
public record AiTerminologyResult(
        List<Match> matches,
        String provider,
        String model,
        Integer inputTokens,
        Integer outputTokens) {

    /**
     * One term the model matched.
     *
     * @param term            the canonical spelling
     * @param definition      a short clinical definition
     * @param category        the anatomical or clinical grouping it belongs to
     * @param synonyms        other accepted names for the same thing
     * @param confusedWith    terms it is commonly mistaken for, which is where reporting errors start
     * @param exampleUsage    the term used in a sentence of the kind that appears in a report
     * @param relevance       the model's own 0–1 confidence that this answers the query
     */
    @Builder
    public record Match(
            String term,
            String definition,
            String category,
            List<String> synonyms,
            List<String> confusedWith,
            String exampleUsage,
            Double relevance) {
    }
}
