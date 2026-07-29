package com.giftedlabs.echoinhealthbackend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for user login request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Login identifier is required")
    private String identifier;

    private String organizationName;

    @NotBlank(message = "Password is required")
    private String password;

    private String totpCode;
}
