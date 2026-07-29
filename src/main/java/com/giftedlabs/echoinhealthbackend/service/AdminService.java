package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.admin.*;
import com.giftedlabs.echoinhealthbackend.entity.Designation;
import com.giftedlabs.echoinhealthbackend.entity.AuditLog;
import com.giftedlabs.echoinhealthbackend.entity.Role;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException;
import com.giftedlabs.echoinhealthbackend.repository.AuditLogRepository;
import com.giftedlabs.echoinhealthbackend.repository.ReportRepository;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.giftedlabs.echoinhealthbackend.util.CacheNames.DASHBOARD_STATS;

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
    private final PasswordEncoder passwordEncoder;
    private final BillingService billingService;

    // ========== Dashboard Statistics ==========

    /**
     * Get comprehensive dashboard statistics.
     * Results are cached for 5 minutes to reduce database load.
     *
     * @return Dashboard statistics response
     */
    @Transactional(readOnly = true)
    @Cacheable(value = DASHBOARD_STATS, key = "#adminUser.organizationId != null ? #adminUser.organizationId : #adminUser.id")
    public DashboardStatsResponse getDashboardStats(User adminUser) {
        log.debug("Cache miss: fetching dashboard statistics");
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime startOfWeek = LocalDate.now().minusDays(7).atStartOfDay();
        boolean globalAdmin = isGlobalAdmin(adminUser);

        // User statistics
        long totalUsers = globalAdmin ? userRepository.count() : userRepository.countByOrganizationId(adminUser.getOrganizationId());
        long verifiedUsers = globalAdmin
                ? userRepository.countByEmailVerifiedTrue()
                : userRepository.countByOrganizationIdAndEmailVerifiedTrue(adminUser.getOrganizationId());
        long lockedUsers = globalAdmin
                ? userRepository.countByAccountLockedTrue()
                : userRepository.countByOrganizationIdAndAccountLockedTrue(adminUser.getOrganizationId());

        Map<String, Long> usersByRole = new HashMap<>();
        for (Role role : Role.values()) {
            usersByRole.put(role.name(), globalAdmin
                    ? userRepository.countByRole(role)
                    : userRepository.countByOrganizationIdAndRole(adminUser.getOrganizationId(), role));
        }

        long newUsersToday = globalAdmin
                ? userRepository.findRecentUsers(startOfToday).size()
                : userRepository.findRecentUsersByOrganization(adminUser.getOrganizationId(), startOfToday).size();
        long newUsersThisWeek = globalAdmin
                ? userRepository.findRecentUsers(startOfWeek).size()
                : userRepository.findRecentUsersByOrganization(adminUser.getOrganizationId(), startOfWeek).size();

        // Report statistics
        long totalReports = globalAdmin ? reportRepository.count() : reportRepository.countByOrganizationId(adminUser.getOrganizationId());
        // Note: Add date-based report counting if needed

        // Activity statistics
        long totalAuditLogs = globalAdmin ? auditLogRepository.count()
                : auditLogRepository.countByOrganizationId(adminUser.getOrganizationId());
        long activityToday = globalAdmin
                ? auditLogRepository.countByCreatedAtAfter(startOfToday)
                : auditLogRepository.countByOrganizationIdAndCreatedAtAfter(adminUser.getOrganizationId(), startOfToday);
        long failedActionsToday = globalAdmin
                ? auditLogRepository.countFailedActionsSince(startOfToday)
                : auditLogRepository.countFailedActionsSinceByOrganization(adminUser.getOrganizationId(), startOfToday);

        // Get distinct action types
        List<String> actionTypes = auditLogRepository.findDistinctActions();

        // Get recent activity (last 24 hours, limit 10)
        List<AuditLog> recentLogs = globalAdmin
                ? auditLogRepository.findRecentActivity(startOfToday)
                : auditLogRepository.findRecentActivityByOrganization(adminUser.getOrganizationId(), startOfToday);
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
     * Create a tenant-scoped user.
     */
    @Transactional
    public AdminUserResponse createUser(CreateUserRequest request, User adminUser) {
        requireTenantAdmin(adminUser);
        validateAssignableRole(request.getRole(), adminUser);

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }
        if (request.getUsername() != null && !request.getUsername().isBlank()
                && userRepository.existsByUsernameAndOrganizationId(request.getUsername(), adminUser.getOrganizationId())) {
            throw new IllegalArgumentException("Username already exists in this organization");
        }

        billingService.assertUserCanBeAdded(adminUser.getOrganization());

        User user = User.builder()
                .organization(adminUser.getOrganization())
                .email(request.getEmail())
                .username(blankToNull(request.getUsername()))
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .hospitalName(adminUser.getHospitalName())
                .department(request.getDepartment())
                .serviceNumber(request.getServiceNumber())
                .role(request.getRole())
                .designation(request.getDesignation())
                .emailVerified(true)
                .accountLocked(false)
                .active(true)
                .canUploadSignature(Boolean.TRUE.equals(request.getCanUploadSignature()))
                .mfaEnabled(false)
                .build();

        User savedUser = userRepository.save(user);
        auditService.logAction(adminUser, "admin_user_created",
                String.format("Created user %s with role %s", savedUser.getEmail(), savedUser.getRole()));
        return mapToAdminUserResponse(savedUser);
    }

    /**
     * Get paginated list of users with search/filter
     */
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> getAllUsers(AdminUserSearchRequest request, Pageable pageable, User adminUser) {
        Page<User> users = isGlobalAdmin(adminUser)
                ? userRepository.searchUsers(
                        request.getSearch(),
                        request.getRole(),
                        request.getLocked(),
                        request.getVerified(),
                        pageable)
                : userRepository.searchUsersByOrganization(
                        adminUser.getOrganizationId(),
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
    public AdminUserResponse getUserById(String userId, User adminUser) {
        User user = findManagedUser(userId, adminUser)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return mapToAdminUserResponse(user);
    }

    /**
     * Update user role
     */
    @Transactional
    public AdminUserResponse updateUserRole(String userId, Role newRole, User adminUser) {
        validateAssignableRole(newRole, adminUser);
        User user = findManagedUser(userId, adminUser)
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
        User user = findManagedUser(userId, adminUser)
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
        User user = findManagedUser(userId, adminUser)
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
        User user = findManagedUser(userId, adminUser)
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

    @Transactional
    public AdminUserResponse deactivateUser(String userId, User adminUser) {
        User user = findManagedUser(userId, adminUser)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getId().equals(adminUser.getId())) {
            throw new IllegalArgumentException("Cannot deactivate your own account");
        }

        user.setActive(false);
        User savedUser = userRepository.save(user);

        auditService.logAction(adminUser, "admin_user_deactivated",
                String.format("Deactivated user %s", savedUser.getEmail()));
        return mapToAdminUserResponse(savedUser);
    }

    @Transactional
    public AdminUserResponse reactivateUser(String userId, User adminUser) {
        User user = findManagedUser(userId, adminUser)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setActive(true);
        User savedUser = userRepository.save(user);

        auditService.logAction(adminUser, "admin_user_reactivated",
                String.format("Reactivated user %s", savedUser.getEmail()));
        return mapToAdminUserResponse(savedUser);
    }

    @Transactional
    public AdminUserResponse updateSignaturePermission(String userId, boolean canUploadSignature, User adminUser) {
        User user = findManagedUser(userId, adminUser)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setCanUploadSignature(canUploadSignature);
        User savedUser = userRepository.save(user);

        auditService.logAction(adminUser, "admin_signature_permission_updated",
                String.format("Set canUploadSignature=%s for %s", canUploadSignature, savedUser.getEmail()));
        return mapToAdminUserResponse(savedUser);
    }

    // ========== Audit Log Management ==========

    /**
     * Get paginated audit logs with filters
     */
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditLogs(AuditLogSearchRequest request, Pageable pageable, User adminUser) {
        Page<AuditLog> logs = isGlobalAdmin(adminUser)
                ? auditLogRepository.searchAuditLogs(
                        request.getAction(),
                        request.getUserEmail(),
                        request.getSuccess(),
                        request.getStartDate(),
                        request.getEndDate(),
                        pageable)
                : auditLogRepository.searchAuditLogsByOrganization(
                        adminUser.getOrganizationId(),
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
    public Page<AuditLogResponse> getAuditLogsForUser(String userId, Pageable pageable, User adminUser) {
        findManagedUser(userId, adminUser)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Page<AuditLog> logs = isGlobalAdmin(adminUser)
                ? auditLogRepository.findByUserId(userId, pageable)
                : auditLogRepository.findByUserIdAndOrganizationId(userId, adminUser.getOrganizationId(), pageable);
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
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .hospitalName(user.getHospitalName())
                .department(user.getDepartment())
                .serviceNumber(user.getServiceNumber())
                .role(user.getRole())
                .designation(user.getDesignation())
                .emailVerified(user.getEmailVerified())
                .accountLocked(user.getAccountLocked())
                .active(user.getActive())
                .canUploadSignature(user.getCanUploadSignature())
                .mfaEnabled(user.getMfaEnabled())
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

    private java.util.Optional<User> findManagedUser(String userId, User adminUser) {
        return isGlobalAdmin(adminUser)
                ? userRepository.findById(userId)
                : userRepository.findByIdAndOrganizationId(userId, adminUser.getOrganizationId());
    }

    private boolean isGlobalAdmin(User adminUser) {
        return adminUser.getRole() == Role.ADMIN || adminUser.getRole() == Role.SUPER_ADMIN;
    }

    private void requireTenantAdmin(User adminUser) {
        if (!(adminUser.getRole() == Role.HOSPITAL_ADMIN || isGlobalAdmin(adminUser))) {
            throw new IllegalArgumentException("Only admins can manage users");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void validateAssignableRole(Role targetRole, User adminUser) {
        if (isGlobalAdmin(adminUser)) {
            return;
        }
        if (targetRole == Role.ADMIN || targetRole == Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("Hospital admins cannot assign platform admin roles");
        }
    }
}
