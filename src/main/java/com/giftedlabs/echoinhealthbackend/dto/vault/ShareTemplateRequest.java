package com.giftedlabs.echoinhealthbackend.dto.vault;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for sharing a template with colleagues (UR-058).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareTemplateRequest {

    @NotEmpty(message = "At least one recipient user ID is required")
    private List<String> recipientUserIds;
}
