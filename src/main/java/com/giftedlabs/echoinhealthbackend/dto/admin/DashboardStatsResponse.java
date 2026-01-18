package com.giftedlabs.echoinhealthbackend.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Dashboard statistics response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {
    // User statistics
    private long totalUsers;
    private long verifiedUsers;
    private long lockedUsers;
    private Map<String, Long> usersByRole;
    private long newUsersToday;
    private long newUsersThisWeek;

    // Report statistics
    private long totalReports;
    private long reportsToday;
    private long reportsThisWeek;

    // Activity statistics
    private long totalAuditLogs;
    private long activityToday;
    private long failedActionsToday;

    // Available action types for filtering
    private List<String> actionTypes;

    // Recent activity summary
    private List<AuditLogResponse> recentActivity;
}
