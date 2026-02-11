package com.giftedlabs.echoinhealthbackend.dto.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response for template analytics (UR-034)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateAnalyticsResponse {
    private String templateId;
    private String templateName;
    private Integer usageCount;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
}
