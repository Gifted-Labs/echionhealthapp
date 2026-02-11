package com.giftedlabs.echoinhealthbackend.dto.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response for batch upload operation (UR-002)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchUploadResponse {
    private int totalFiles;
    private int successCount;
    private int failureCount;
    private List<BatchUploadResult> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchUploadResult {
        private String filename;
        private boolean success;
        private String reportId;
        private String errorMessage;
    }
}
