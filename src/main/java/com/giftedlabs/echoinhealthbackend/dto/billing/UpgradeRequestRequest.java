package com.giftedlabs.echoinhealthbackend.dto.billing;

import com.giftedlabs.echoinhealthbackend.entity.SubscriptionTier;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A tenant admin asking for a plan change. Recording intent only — it grants nothing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpgradeRequestRequest {

    @NotNull(message = "Requested subscription tier is required")
    private SubscriptionTier requestedTier;

    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
