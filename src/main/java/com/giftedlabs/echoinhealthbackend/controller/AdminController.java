package com.giftedlabs.echoinhealthbackend.controller;

import com.giftedlabs.echoinhealthbackend.dto.admin.*;
import com.giftedlabs.echoinhealthbackend.dto.common.ApiResponse;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import com.giftedlabs.echoinhealthbackend.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin controller for super admin operations
 * All endpoints require ADMIN or SUPER_ADMIN role
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Super Admin management APIs")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final UserRepository userRepository;

    // ========== Dashboard ==========

    @GetMapping("/dashboard")
    @Operation(summary = "Dashboard stats", description = "Get comprehensive system statistics")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getDashboard() {
        DashboardStatsResponse stats = adminService.getDashboardStats();
        return ResponseEntity.ok(ApiResponse.<DashboardStatsResponse>builder()
                .success(true)
                .data(stats)
                .build());
    }

    // ========== User Management ==========

    @GetMapping("/users")
    @Operation(summary = "List users", description = "Get paginated list of all users with optional filters")
    public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> getAllUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean locked,
            @RequestParam(required = false) Boolean verified,
            Pageable pageable) {

        AdminUserSearchRequest request = AdminUserSearchRequest.builder()
                .search(search)
                .role(role != null ? com.giftedlabs.echoinhealthbackend.entity.Role.valueOf(role) : null)
                .locked(locked)
                .verified(verified)
                .build();

        Page<AdminUserResponse> users = adminService.getAllUsers(request, pageable);
        return ResponseEntity.ok(ApiResponse.<Page<AdminUserResponse>>builder()
                .success(true)
                .data(users)
                .build());
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get user", description = "Get detailed information about a single user")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getUser(@PathVariable String id) {
        AdminUserResponse user = adminService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.<AdminUserResponse>builder()
                .success(true)
                .data(user)
                .build());
    }

    @PutMapping("/users/{id}/role")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Update role", description = "Change a user's role (SUPER_ADMIN only)")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUserRole(
            @PathVariable String id,
            @Valid @RequestBody UpdateUserRoleRequest request,
            Authentication authentication) {

        User adminUser = getAdminUser(authentication);
        AdminUserResponse user = adminService.updateUserRole(id, request.getRole(), adminUser);
        return ResponseEntity.ok(ApiResponse.<AdminUserResponse>builder()
                .success(true)
                .message("User role updated successfully")
                .data(user)
                .build());
    }

    @PutMapping("/users/{id}/lock")
    @Operation(summary = "Lock user", description = "Lock a user's account")
    public ResponseEntity<ApiResponse<AdminUserResponse>> lockUser(
            @PathVariable String id,
            Authentication authentication) {

        User adminUser = getAdminUser(authentication);
        AdminUserResponse user = adminService.lockUser(id, adminUser);
        return ResponseEntity.ok(ApiResponse.<AdminUserResponse>builder()
                .success(true)
                .message("User account locked successfully")
                .data(user)
                .build());
    }

    @PutMapping("/users/{id}/unlock")
    @Operation(summary = "Unlock user", description = "Unlock a user's account")
    public ResponseEntity<ApiResponse<AdminUserResponse>> unlockUser(
            @PathVariable String id,
            Authentication authentication) {

        User adminUser = getAdminUser(authentication);
        AdminUserResponse user = adminService.unlockUser(id, adminUser);
        return ResponseEntity.ok(ApiResponse.<AdminUserResponse>builder()
                .success(true)
                .message("User account unlocked successfully")
                .data(user)
                .build());
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Delete user", description = "Permanently delete a user (SUPER_ADMIN only)")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable String id,
            Authentication authentication) {

        User adminUser = getAdminUser(authentication);
        adminService.deleteUser(id, adminUser);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("User deleted successfully")
                .build());
    }

    // ========== Audit Logs ==========

    @GetMapping("/audit-logs")
    @Operation(summary = "View audit logs", description = "Get paginated audit logs with optional filters")
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> getAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) java.time.LocalDateTime startDate,
            @RequestParam(required = false) java.time.LocalDateTime endDate,
            Pageable pageable) {

        AuditLogSearchRequest request = AuditLogSearchRequest.builder()
                .action(action)
                .userEmail(userEmail)
                .success(success)
                .startDate(startDate)
                .endDate(endDate)
                .build();

        Page<AuditLogResponse> logs = adminService.getAuditLogs(request, pageable);
        return ResponseEntity.ok(ApiResponse.<Page<AuditLogResponse>>builder()
                .success(true)
                .data(logs)
                .build());
    }

    @GetMapping("/audit-logs/user/{userId}")
    @Operation(summary = "User audit logs", description = "Get audit logs for a specific user")
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> getAuditLogsForUser(
            @PathVariable String userId,
            Pageable pageable) {

        Page<AuditLogResponse> logs = adminService.getAuditLogsForUser(userId, pageable);
        return ResponseEntity.ok(ApiResponse.<Page<AuditLogResponse>>builder()
                .success(true)
                .data(logs)
                .build());
    }

    @GetMapping("/audit-logs/actions")
    @Operation(summary = "Action types", description = "Get list of available action types for filtering")
    public ResponseEntity<ApiResponse<List<String>>> getActionTypes() {
        List<String> actions = adminService.getActionTypes();
        return ResponseEntity.ok(ApiResponse.<List<String>>builder()
                .success(true)
                .data(actions)
                .build());
    }

    // ========== Helper Methods ==========

    private User getAdminUser(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String email = userDetails.getUsername();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found"));
    }
}
