package com.giftedlabs.echoinhealthbackend.dto.platform;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Starts an impersonation session.
 *
 * <p>The reason is mandatory. Borrowing a clinician's session is a privileged act against a real
 * person's account, and the audit row is far more useful with the operator's own words in it than
 * with a bare "impersonation started".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImpersonationRequest {

    @NotBlank(message = "A reason is required to impersonate a user")
    @Size(min = 5, max = 500, message = "Reason must be between 5 and 500 characters")
    private String reason;
}
