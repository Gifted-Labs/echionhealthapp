package com.giftedlabs.echoinhealthbackend.dto.billing;

import com.giftedlabs.echoinhealthbackend.entity.SubscriptionTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingPlanResponse {
    private String organizationId;
    private String organizationName;
    private SubscriptionTier subscriptionTier;
    private Integer maxUsers;
    private Integer storageLimitMb;
    private Integer aiCreditsPerMonth;
    private Integer addonStorageMb;
    private Integer addonAiCredits;
    private Boolean liteEmrIntegrationEnabled;
    private Boolean autoGrammarCheckEnabled;
}
