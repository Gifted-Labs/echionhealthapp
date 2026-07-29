package com.giftedlabs.echoinhealthbackend.dto.admin;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSignaturePermissionRequest {

    @NotNull(message = "canUploadSignature is required")
    private Boolean canUploadSignature;
}
