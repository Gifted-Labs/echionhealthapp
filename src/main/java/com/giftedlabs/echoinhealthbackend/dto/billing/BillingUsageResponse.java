package com.giftedlabs.echoinhealthbackend.dto.billing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingUsageResponse {
    private long activeUsers;
    private int userLimit;
    private long storageUsedBytes;
    private int storageLimitMb;
    private int aiCreditsUsedThisMonth;
    private int aiCreditsLimitThisMonth;
    private boolean approachingUserLimit;
    private boolean approachingStorageLimit;
    private boolean approachingAiCreditLimit;
    private boolean overUserLimit;
    private boolean overStorageLimit;
    private boolean overAiCreditLimit;
    private List<String> alerts;
    private boolean autoGrammarCheckEnabled;
    private boolean liteEmrIntegrationEnabled;
}
