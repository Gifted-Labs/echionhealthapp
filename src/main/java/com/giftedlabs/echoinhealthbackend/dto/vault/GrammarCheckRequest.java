package com.giftedlabs.echoinhealthbackend.dto.vault;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarCheckRequest {

    @NotBlank(message = "Text to check is required")
    @Size(max = 20000, message = "Text must not exceed 20000 characters")
    private String text;

    /** Optional provider override, mirroring the other AI endpoints. */
    private String provider;
}
