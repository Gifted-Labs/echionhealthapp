package com.giftedlabs.echoinhealthbackend.dto.vault;

import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TerminologySearchRequest {

    /**
     * What to look up — a term, a partial term, a misspelling, or a short description of the
     * thing whose name is wanted.
     *
     * <p>Capped at 200 characters deliberately. This is a terminology lookup, not a place to
     * paste a findings paragraph; a low cap bounds both the token cost and the amount of
     * clinical text that can reach a third-party model in the first place.
     */
    @NotBlank(message = "Search query is required")
    @Size(min = 2, max = 200, message = "Search query must be between 2 and 200 characters")
    private String query;

    /** Optional. Biases which sense of an ambiguous term is returned. */
    private ScanType scanType;

    @Min(value = 1, message = "limit must be at least 1")
    @Max(value = 10, message = "limit may not exceed 10")
    @Builder.Default
    private Integer limit = 5;
}
