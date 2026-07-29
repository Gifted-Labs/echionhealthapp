package com.giftedlabs.echoinhealthbackend.dto.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HelperToolsResponse {
    private String impressionSuggestion;
    private List<String> clinicalDifferentials;
    private List<String> wordingSuggestions;
    private List<String> verifiedReferences;
}
