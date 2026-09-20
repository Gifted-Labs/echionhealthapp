package com.giftedlabs.echoinhealthbackend.dto.platform;

import com.giftedlabs.echoinhealthbackend.dto.admin.AdminUserResponse;
import com.giftedlabs.echoinhealthbackend.dto.admin.AuditLogResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Everything a super admin needs about one tenant on a single screen: who is in it, what plan it
 * is on, how much of that plan it is consuming, and what has been happening in it lately.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationDetailResponse {

    private OrganizationSummaryResponse organization;

    private int aiCreditsUsedThisMonth;
    private int aiCreditsLimitThisMonth;
    private boolean liteEmrIntegrationEnabled;

    private long sharedScanCount;
    private long templateCount;

    /** Members of the tenant, capped for display. */
    private List<AdminUserResponse> users;

    /** Most recent activity within the tenant. */
    private List<AuditLogResponse> recentActivity;
}
