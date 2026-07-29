package com.giftedlabs.echoinhealthbackend.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiProviderStatusResponse {

    private String provider;
    private boolean configured;
    /** True only if a real round trip to the provider returned parseable structured output. */
    private boolean reachable;
    private String reportModel;
    private String impressionModel;
    private String endpoint;
    private Long latencyMs;
    private String failureReason;
    private boolean primary;
    private boolean fallback;
}
