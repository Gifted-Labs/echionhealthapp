package com.giftedlabs.echoinhealthbackend.dto.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSuggestImpressionResponse {
    private String impression;
    private String provider;
    private String model;
    private Boolean fallbackUsed;
    private String promptTemplateVersion;
    private Integer aiCreditsConsumed;
    private Integer processingTimeSeconds;
    private LocalDateTime generatedAt;
}
