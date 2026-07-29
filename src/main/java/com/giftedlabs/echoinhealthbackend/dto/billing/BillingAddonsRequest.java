package com.giftedlabs.echoinhealthbackend.dto.billing;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingAddonsRequest {
    @Min(value = 0, message = "Extra storage must be zero or greater")
    private Integer extraStorageMb;

    @Min(value = 0, message = "Extra AI credits must be zero or greater")
    private Integer extraAiCredits;

    private Boolean liteEmrIntegrationEnabled;
}
