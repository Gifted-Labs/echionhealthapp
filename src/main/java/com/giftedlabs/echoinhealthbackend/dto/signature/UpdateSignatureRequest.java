package com.giftedlabs.echoinhealthbackend.dto.signature;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSignatureRequest {
    @Size(min = 2, max = 100, message = "Label must be between 2 and 100 characters")
    private String label;

    private Boolean isDefault;
}
