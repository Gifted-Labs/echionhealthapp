package com.giftedlabs.echoinhealthbackend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaVerificationRequest {
    @NotBlank(message = "Authenticator code is required")
    private String code;
}
