package com.giftedlabs.echoinhealthbackend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for completing user profile with professional details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteProfileRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Phone number must be valid (E.164 format)")
    private String phone;

    @NotBlank(message = "Hospital name is required")
    @Size(min = 2, max = 255, message = "Hospital name must be between 2 and 255 characters")
    private String hospitalName;

    @NotBlank(message = "Department is required")
    @Size(min = 2, max = 100, message = "Department must be between 2 and 100 characters")
    private String department;

    @NotBlank(message = "Service number is required")
    @Size(min = 2, max = 100, message = "Service number must be between 2 and 100 characters")
    private String serviceNumber;
}
