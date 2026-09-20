package com.giftedlabs.echoinhealthbackend.dto.platform;

import com.giftedlabs.echoinhealthbackend.entity.SubscriptionTier;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Super-admin-driven onboarding: provisions a hospital and its first administrator in one step.
 *
 * <p>Previously the only way a tenant came into existence was self-service registration, which
 * gave the operator no way to onboard a hospital on their behalf.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrganizationRequest {

    @NotBlank(message = "Organization name is required")
    @Size(max = 255)
    private String name;

    @NotBlank(message = "Hospital name is required")
    @Size(max = 255)
    private String hospitalName;

    @Size(max = 500)
    private String address;

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Phone number must be valid (E.164 format)")
    private String phone;

    @Email(message = "Organization email must be valid")
    @Size(max = 255)
    private String email;

    @Size(max = 255)
    private String website;

    /** Defaults to BASIC when omitted. */
    private SubscriptionTier subscriptionTier;

    // ----- first administrator -----

    @NotBlank(message = "Administrator first name is required")
    @Size(min = 2, max = 100)
    private String adminFirstName;

    @NotBlank(message = "Administrator last name is required")
    @Size(min = 2, max = 100)
    private String adminLastName;

    @NotBlank(message = "Administrator email is required")
    @Email(message = "Administrator email must be valid")
    @Size(max = 255)
    private String adminEmail;

    @NotBlank(message = "Administrator password is required")
    @Size(min = 8, max = 100)
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
            message = "Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character")
    private String adminPassword;
}
