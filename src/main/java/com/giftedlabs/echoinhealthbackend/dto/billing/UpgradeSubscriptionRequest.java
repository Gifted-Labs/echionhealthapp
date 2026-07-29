package com.giftedlabs.echoinhealthbackend.dto.billing;

import com.giftedlabs.echoinhealthbackend.entity.SubscriptionTier;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpgradeSubscriptionRequest {
    @NotNull(message = "Target subscription tier is required")
    private SubscriptionTier subscriptionTier;
}
