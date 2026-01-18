package com.giftedlabs.echoinhealthbackend.dto.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for search results with highlighted matches
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultResponse {

    private ReportSummaryResponse report;

    private List<String> matchedFields; // Fields that matched the search

    private List<String> highlights; // Highlighted text snippets

    private Double relevanceScore; // Search relevance score
}
