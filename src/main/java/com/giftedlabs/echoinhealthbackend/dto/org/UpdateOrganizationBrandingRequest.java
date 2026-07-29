package com.giftedlabs.echoinhealthbackend.dto.org;

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
public class UpdateOrganizationBrandingRequest {
    @NotBlank(message = "Hospital name is required")
    @Size(min = 2, max = 255, message = "Hospital name must be between 2 and 255 characters")
    private String hospitalName;

    @Size(max = 500, message = "Address must not exceed 500 characters")
    private String address;

    @Size(max = 30, message = "Phone must not exceed 30 characters")
    private String phone;

    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @Size(max = 255, message = "Website must not exceed 255 characters")
    private String website;
}
