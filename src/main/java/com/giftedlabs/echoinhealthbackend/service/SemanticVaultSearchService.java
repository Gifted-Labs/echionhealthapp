package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.vault.SemanticSearchResponse;
import com.giftedlabs.echoinhealthbackend.dto.vault.TemplateResponse;
import com.giftedlabs.echoinhealthbackend.dto.vault.TerminologySearchRequest;
import com.giftedlabs.echoinhealthbackend.dto.vault.TerminologySearchResponse;
import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import com.giftedlabs.echoinhealthbackend.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Natural-language search over the user's SonoVault.
 *
 * <p>"Cases where the liver looked bright" finds templates filed under hepatic steatosis and
 * increased echogenicity, without those words appearing in the query.
 *
 * <p><b>Why this is not a vector search.</b> The obvious implementation is embeddings in pgvector.
 * It was rejected for three reasons, in order of weight. It would require sending template
 * <em>content</em> to an embedding provider, which is a materially different privacy posture from
 * sending a 200-character query, on a corpus whose PHI protection is admittedly regex heuristics
 * (UR-049). It would add a Postgres extension whose availability on the deployment target is
 * unverified, on a project whose last two outages were both a migration meeting a database that
 * was not what the migration assumed. And it would need an embedding pipeline, a backfill, and a
 * re-embed-on-write path before returning a single result.
 *
 * <p>What it does instead is expand the query into clinical terminology and run that against the
 * existing indexed search. In this domain that is close to as good: medical retrieval is
 * overwhelmingly vocabulary-driven, and the gap between "swollen kidney" and a template about
 * hydronephrosis is a <em>terminology</em> gap, which is exactly what expansion closes. Only the
 * query leaves the system; the vault is never sent anywhere.
 *
 * <p>The expansion reuses {@link TerminologySearchService} rather than adding a second prompt and
 * a fifth provider method. One consequence is worth having: the terminology cache is shared, so
 * searching for a term someone has already looked up anywhere on the platform costs nothing.
 *
 * <p>Where this is genuinely weaker than embeddings is conceptual similarity with no shared
 * vocabulary at all. If that turns out to matter in practice, the seam to replace is
 * {@link #expandQuery} — the search path below does not care where its terms came from.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SemanticVaultSearchService {

    /** Terms whose expansion would match half the vault and rank nothing. */
    private static final Set<String> TOO_BROAD = Set.of(
            "scan", "ultrasound", "report", "study", "patient", "normal", "abnormal", "finding",
            "findings", "imaging", "image", "examination", "exam");

    private static final int MIN_TERM_LENGTH = 4;

    private final TerminologySearchService terminologySearchService;
    private final TemplateService templateService;

    @Transactional(readOnly = true)
    public SemanticSearchResponse search(String query, ScanType scanType, User user, Pageable pageable) {
        TerminologySearchResponse expansion = expandQuery(query, scanType, user);

        List<String> terms = termsFrom(query, expansion);
        if (terms.isEmpty()) {
            return SemanticSearchResponse.builder()
                    .query(query)
                    .interpretedAs(List.of())
                    .results(Page.empty(pageable))
                    .aiCreditsUsed(expansion.getAiCreditsUsed())
                    .servedFromCache(expansion.isServedFromCache())
                    .build();
        }

        Page<TemplateResponse> results =
                templateService.semanticSearch(terms, scanType, user.getId(), pageable);

        return SemanticSearchResponse.builder()
                .query(query)
                .interpretedAs(terms)
                .results(results)
                .aiCreditsUsed(expansion.getAiCreditsUsed())
                .servedFromCache(expansion.isServedFromCache())
                .build();
    }

    /**
     * Turns a natural-language query into clinical terms.
     *
     * <p>The seam an embedding implementation would replace. Everything downstream consumes a
     * list of strings and is indifferent to how they were produced.
     */
    private TerminologySearchResponse expandQuery(String query, ScanType scanType, User user) {
        return terminologySearchService.search(
                TerminologySearchRequest.builder()
                        .query(query)
                        .scanType(scanType)
                        .limit(5)
                        .build(),
                user);
    }

    /**
     * Builds the search vocabulary: the canonical terms, their synonyms, and the meaningful words
     * of the original query.
     *
     * <p>The original words are kept because the expansion can be wrong, and a user who typed an
     * exact term should still find it when the model decides to explain something adjacent.
     * {@code confusedWith} is deliberately excluded — those are the terms this <em>is not</em>,
     * and searching for them is how a search returns confidently irrelevant results.
     */
    private List<String> termsFrom(String query, TerminologySearchResponse expansion) {
        Set<String> terms = new LinkedHashSet<>();

        if (expansion.getMatches() != null) {
            for (TerminologySearchResponse.TerminologyMatch match : expansion.getMatches()) {
                addIfUseful(terms, match.getTerm());
                if (match.getSynonyms() != null) {
                    match.getSynonyms().forEach(synonym -> addIfUseful(terms, synonym));
                }
            }
        }

        for (String word : query.split("\\W+")) {
            addIfUseful(terms, word);
        }

        return List.copyOf(terms);
    }

    private void addIfUseful(Set<String> terms, String candidate) {
        if (candidate == null) {
            return;
        }
        String normalized = candidate.toLowerCase(Locale.ROOT).trim();
        if (normalized.length() < MIN_TERM_LENGTH || TOO_BROAD.contains(normalized)) {
            return;
        }
        terms.add(normalized);
    }
}
