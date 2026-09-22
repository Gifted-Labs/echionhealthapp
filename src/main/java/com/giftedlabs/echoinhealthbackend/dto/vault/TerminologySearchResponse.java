package com.giftedlabs.echoinhealthbackend.dto.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TerminologySearchResponse {

    private String query;
    private List<TerminologyMatch> matches;

    /** Which provider answered, and whether the primary had to fall back. */
    private String provider;
    private String model;
    private boolean fallbackUsed;

    /** Credits this lookup consumed. Zero when the answer came from cache. */
    private int aiCreditsUsed;
    private boolean servedFromCache;

    /**
     * Why this is not a clinical reference.
     *
     * <p>Returned on every response rather than documented once, so a client cannot render the
     * result without also having the caveat in hand. The model can produce a fluent and wrong
     * definition, and this output is one step away from a sonographer's clipboard.
     */
    @Builder.Default
    private String disclaimer =
            "AI-generated drafting assistance, not a validated clinical reference. "
                    + "Verify any term against an authoritative source before using it in a report.";

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TerminologyMatch {
        private String term;
        private String definition;
        private String category;
        private List<String> synonyms;
        private List<String> confusedWith;
        private String exampleUsage;
        private Double relevance;
    }
}
