package com.giftedlabs.echoinhealthbackend.dto.platform;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Landing view of the whole platform for a super admin.
 *
 * <p>The only admin surface that previously existed reported counts for a single tenant. Operating
 * the platform means seeing across all of them at once, and seeing which ones need attention.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformOverviewResponse {

    // ----- tenants -----
    private long totalOrganizations;
    private long activeOrganizations;
    private long suspendedOrganizations;
    private long organizationsOnboardedLast7Days;
    private long organizationsOnboardedLast30Days;
    private Map<String, Long> organizationsByTier;

    // ----- people -----
    private long totalUsers;
    private long activeUsers;
    private long verifiedUsers;
    private long lockedUsers;
    private Map<String, Long> usersByRole;

    // ----- clinical volume -----
    private long totalReports;
    private long reportsToday;
    private long reportsLast7Days;
    private long totalSharedScans;

    // ----- capacity sold vs consumed -----
    private long storageUsedMb;
    private long storageAllocatedMb;
    private long aiCreditsUsedThisMonth;
    private long aiCreditsAllocatedThisMonth;

    // ----- operational signals -----
    private long failedLoginsToday;
    private long failedActionsToday;
    private long emailsFailed;
    private long emailsPending;
    private long emailsSentLast24Hours;

    /** Tenants above the attention threshold on seats, storage or AI credits. */
    private List<TenantQuotaAlert> quotaAlerts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantQuotaAlert {
        private String organizationId;
        private String organizationName;
        private String hospitalName;
        private String subscriptionTier;
        /** Which quota is under pressure: SEATS, STORAGE or AI_CREDITS. */
        private String quota;
        private int percentUsed;
        private long used;
        private long limit;
    }
}
