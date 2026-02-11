package com.giftedlabs.echoinhealthbackend.dto.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response for report version history (UR-031)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportVersionResponse {
    private String id;
    private String reportId;
    private Integer versionNumber;
    private String changeDescription;
    private String changedByName;
    private String changedByEmail;
    private LocalDateTime createdAt;
}
