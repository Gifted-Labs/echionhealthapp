package com.giftedlabs.echoinhealthbackend.dto.vault;

import com.giftedlabs.echoinhealthbackend.entity.Gender;
import com.giftedlabs.echoinhealthbackend.entity.ReportType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for report template response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateResponse {

    private String id;
    private String userId; // null for system templates

    private String name;
    private String description;

    private Gender gender;
    private ReportType reportType;

    private String defaultFindings;
    private String defaultImpression;

    private Boolean isDefault;
    private Boolean isActive;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
