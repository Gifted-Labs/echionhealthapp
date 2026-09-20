package com.giftedlabs.echoinhealthbackend.dto.platform;

import com.giftedlabs.echoinhealthbackend.entity.OrganizationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Suspends or reactivates a tenant. Suspension blocks sign-in for every member; nothing is deleted.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrganizationStatusRequest {

    @NotNull(message = "Status is required")
    private OrganizationStatus status;

    /** Recorded on the organization and in the audit trail. Required when suspending. */
    @Size(max = 500)
    private String reason;
}
