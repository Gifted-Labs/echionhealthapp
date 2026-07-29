package com.giftedlabs.echoinhealthbackend.dto.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for template bulk upload operations (UR-048).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateBulkUploadResponse {

    private int totalFiles;
    private int successCount;
    private int failureCount;
    private List<TemplateBulkResult> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateBulkResult {
        private String filename;
        private boolean success;
        private String templateId;
        private String errorMessage;
    }
}
