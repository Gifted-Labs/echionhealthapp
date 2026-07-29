package com.giftedlabs.echoinhealthbackend.dto.vault;

import com.giftedlabs.echoinhealthbackend.entity.Designation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for generating a pre-finalization report preview (UR-040).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviewReportRequest {

    @NotBlank(message = "Signature ID is required for preview")
    private String signatureId;

    @NotNull(message = "Designation is required for preview")
    private Designation designation;
}
