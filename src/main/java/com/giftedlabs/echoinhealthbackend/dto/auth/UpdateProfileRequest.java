package com.giftedlabs.echoinhealthbackend.dto.auth;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for updating user profile.
 * All fields are optional - only provided fields will be updated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @Size(min = 2, max = 100, message = "First name must be between 2 and 100 characters")
    private String firstName;

    @Size(min = 2, max = 100, message = "Last name must be between 2 and 100 characters")
    private String lastName;

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Phone number must be valid (E.164 format)")
    private String phone;

    @Size(min = 2, max = 255, message = "Hospital name must be between 2 and 255 characters")
    private String hospitalName;

    @Size(min = 2, max = 100, message = "Department must be between 2 and 100 characters")
    private String department;

    @Size(min = 2, max = 100, message = "Service number must be between 2 and 100 characters")
    private String serviceNumber;
}
