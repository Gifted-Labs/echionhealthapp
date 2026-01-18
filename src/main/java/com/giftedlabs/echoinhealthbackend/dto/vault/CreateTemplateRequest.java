package com.giftedlabs.echoinhealthbackend.dto.vault;

import com.giftedlabs.echoinhealthbackend.entity.Gender;
import com.giftedlabs.echoinhealthbackend.entity.ReportType;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating a custom report template
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTemplateRequest {

    @NotBlank(message = "Template name is required")
    private String name;

    private String description;

    private Gender gender; // null means applicable to both

    private ReportType reportType;

    private String defaultFindings;

    private String defaultImpression;

    @Builder.Default
    private Boolean isDefault = false;
}
