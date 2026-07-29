package com.giftedlabs.echoinhealthbackend.dto.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Auto-Grammar Check output.
 *
 * <p>Corrections are returned for review and are never applied to a report automatically.
 * Silently rewriting clinical prose is not a safe default, so accepting a change stays an
 * explicit act by the reporting clinician.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarCheckResponse {

    private String originalText;
    private String correctedText;
    private List<Correction> corrections;
    private boolean changesSuggested;
    private String provider;
    private String model;
    private Boolean fallbackUsed;
    private String promptTemplateVersion;
    private int aiCreditsConsumed;
    private LocalDateTime generatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Correction {
        private String original;
        private String corrected;
        private String reason;
    }
}
