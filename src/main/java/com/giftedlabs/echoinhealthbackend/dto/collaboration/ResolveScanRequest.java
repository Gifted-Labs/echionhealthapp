package com.giftedlabs.echoinhealthbackend.dto.collaboration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for resolving a shared scan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveScanRequest {
    /**
     * Optional notes explaining the resolution
     */
    private String resolutionNotes;
}
