package com.giftedlabs.echoinhealthbackend.dto.platform;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Asks the platform to send a probe message and report what the provider said. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailTestRequest {

    @NotBlank(message = "Recipient is required")
    @Email(message = "Recipient must be a valid email address")
    private String recipient;
}
