package com.giftedlabs.echoinhealthbackend.dto.vault;

import com.giftedlabs.echoinhealthbackend.entity.Gender;
import com.giftedlabs.echoinhealthbackend.entity.ReportType;
import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating an existing template (UR-051).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTemplateRequest {

    private String name;
    private String description;
    private Gender gender;
    private ReportType reportType;
    private ScanType scanType;
    private String category;
    private String defaultFindings;
    private String defaultImpression;
    private Boolean isDefault;
    private String[] tags;
}
