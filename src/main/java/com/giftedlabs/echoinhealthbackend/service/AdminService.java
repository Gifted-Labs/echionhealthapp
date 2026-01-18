package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.admin.*;
import com.giftedlabs.echoinhealthbackend.entity.AuditLog;
import com.giftedlabs.echoinhealthbackend.entity.Role;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException;
import com.giftedlabs.echoinhealthbackend.repository.AuditLogRepository;
import com.giftedlabs.echoinhealthbackend.repository.ReportRepository;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for admin operations - user management, audit logs, and system
 * statistics
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final ReportRepository reportRepository;
    private final AuditService auditService;

    // ========== Dashboard Statistics ==========

    /**
     * Get comprehensive dashboard statistics
     */
    @Transactional(readOnly = true)
    public DashboardStatsResponse getDashboardStats() {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime startOfWeek = LocalDate.now().minusDays(7).atStartOfDay();

        // User statistics
        long totalUsers = userRepository.count();
        long verifiedUsers = userRepository.countByEmailVerifiedTrue();
        long lockedUsers = userRepository.countByAccountLockedTrue();

        Map<String, Long> usersByRole = new HashMap<>();
        for (Role role : Role.values()) {
            usersByRole.put(role.name(), userRepository.countByRole(role));
        }

        long newUsersToday = userRepository.findRecentUsers(startOfToday).size();
        long newUsersThisWeek = userRepository.findRecentUsers(startOfWeek).size();

        // Report statistics
        long totalReports = reportRepository.count();
        // Note: Add date-based report counting if needed

        // Activity statistics
        long totalAuditLogs = auditLogRepository.count();
        long activityToday = auditLogRepository.countByCreatedAtAfter(startOfToday);
        long failedActionsToday = auditLogRepository.countFailedActionsSince(startOfToday);

        // Get distinct action types
        List<String> actionTypes = auditLogRepository.findDistinctActions();

        // Get recent activity (last 24 hours, limit 10)
        List<AuditLog> recentLogs = auditLogRepository.findRecentActivity(startOfToday);
        List<AuditLogResponse> recentActivity = recentLogs.stream()
                .limit(10)
                .map(this::mapToAuditLogResponse)
                .collect(Collectors.toList());

        return DashboardStatsResponse.builder()
                .totalUsers(totalUsers)
                .verifiedUsers(verifiedUsers)
                .lockedUsers(lockedUsers)
                .usersByRole(usersByRole)
                .newUsersToday(newUsersToday)
                .newUsersThisWeek(newUsersThisWeek)
                .totalReports(totalReports)
                .reportsToday(0) // Can be implemented with date filtering
                .reportsThisWeek(0)
                .totalAuditLogs(totalAuditLogs)
                .activityToday(activityToday)
                .failedActionsToday(failedActionsToday)
                .actionTypes(actionTypes)
                .recentActivity(recentActivity)
                .build();
    }

    // ========== User Management ==========

    /**
     * Get paginated list of users with search/filter
     */
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> getAllUsers(AdminUserSearchRequest request, Pageable pageable) {
        Page<User> users = userRepository.searchUsers(
                request.getSearch(),
                request.getRole(),
                request.getLocked(),
                request.getVerified(),
                pageable);

        return users.map(this::mapToAdminUserResponse);
    }

    /**
     * Get single user by ID
     */
    @Transactional(readOnly = true)
    public AdminUserResponse getUserById(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return mapToAdminUserResponse(user);
    }

    /**
     * Update user role
     */
    @Transactional
    public AdminUserResponse updateUserRole(String userId, Role newRole, User adminUser) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Role oldRole = user.getRole();
        user.setRole(newRole);
        User updatedUser = userRepository.save(user);

        // Log the action
        auditService.logAction(adminUser, "admin_role_change",
                String.format("Changed role for %s from %s to %s", user.getEmail(), oldRole, newRole));

        log.info("Admin {} changed role for user {} from {} to {}",
                adminUser.getEmail(), user.getEmail(), oldRole, newRole);

        return mapToAdminUserResponse(updatedUser);
    }

    /**
     * Lock user account
     */
    @Transactional
    public AdminUserResponse lockUser(String userId, User adminUser) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getId().equals(adminUser.getId())) {
            throw new IllegalArgumentException("Cannot lock your own account");
        }

        user.setAccountLocked(true);
        User updatedUser = userRepository.save(user);

        auditService.logAction(adminUser, "admin_lock_user",
                String.format("Locked account for user %s", user.getEmail()));

        log.info("Admin {} locked account for user {}", adminUser.getEmail(), user.getEmail());

        return mapToAdminUserResponse(updatedUser);
    }

    /**
     * Unlock user account
     */
    @Transactional
    public AdminUserResponse unlockUser(String userId, User adminUser) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setAccountLocked(false);
        User updatedUser = userRepository.save(user);

        auditService.logAction(adminUser, "admin_unlock_user",
                String.format("Unlocked account for user %s", user.getEmail()));

        log.info("Admin {} unlocked account for user {}", adminUser.getEmail(), user.getEmail());

        return mapToAdminUserResponse(updatedUser);
    }

    /**
     * Delete user (soft delete or handle with care)
     */
    @Transactional
    public void deleteUser(String userId, User adminUser) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getId().equals(adminUser.getId())) {
            throw new IllegalArgumentException("Cannot delete your own account");
        }

        // For safety, we could implement soft delete, but for now we'll do hard delete
        String userEmail = user.getEmail();
        userRepository.delete(user);

        auditService.logAction(adminUser, "admin_delete_user",
                String.format("Deleted user account: %s", userEmail));

        log.warn("Admin {} deleted user account: {}", adminUser.getEmail(), userEmail);
    }

    // ========== Audit Log Management ==========

    /**
     * Get paginated audit logs with filters
     */
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditLogs(AuditLogSearchRequest request, Pageable pageable) {
        Page<AuditLog> logs = auditLogRepository.searchAuditLogs(
                request.getAction(),
                request.getUserEmail(),
                request.getSuccess(),
                request.getStartDate(),
                request.getEndDate(),
                pageable);

        return logs.map(this::mapToAuditLogResponse);
    }

    /**
     * Get audit logs for a specific user
     */
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditLogsForUser(String userId, Pageable pageable) {
        Page<AuditLog> logs = auditLogRepository.findByUserId(userId, pageable);
        return logs.map(this::mapToAuditLogResponse);
    }

    /**
     * Get available action types for filtering
     */
    @Transactional(readOnly = true)
    public List<String> getActionTypes() {
        return auditLogRepository.findDistinctActions();
    }

    // ========== Mapping Methods ==========

    private AdminUserResponse mapToAdminUserResponse(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .hospitalName(user.getHospitalName())
                .department(user.getDepartment())
                .serviceNumber(user.getServiceNumber())
                .role(user.getRole())
                .emailVerified(user.getEmailVerified())
                .accountLocked(user.getAccountLocked())
                .profileComplete(user.hasCompletedProfile())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .profileUpdatedAt(user.getProfileUpdatedAt())
                .build();
    }

    private AuditLogResponse mapToAuditLogResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .userId(log.getUser() != null ? log.getUser().getId() : null)
                .userEmail(log.getUserEmail())
                .action(log.getAction())
                .details(log.getDetails())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .success(log.getSuccess())
                .errorMessage(log.getErrorMessage())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
