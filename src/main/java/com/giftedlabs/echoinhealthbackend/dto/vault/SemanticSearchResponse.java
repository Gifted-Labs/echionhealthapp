package com.giftedlabs.echoinhealthbackend.dto.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticSearchResponse {

    private String query;

    /**
     * The clinical terms the query was expanded into, in the order they were searched.
     *
     * <p>Returned rather than hidden because a search that silently rewrites what you asked for
     * is a search you cannot debug or trust. It also teaches: a sonographer who sees "swollen
     * kidney" become "hydronephrosis, pelvicaliectasis" has learned the reporting term.
     */
    private List<String> interpretedAs;

    private Page<TemplateResponse> results;

    /** Credits the expansion consumed. Zero when the terms came from cache. */
    private int aiCreditsUsed;
    private boolean servedFromCache;
}
