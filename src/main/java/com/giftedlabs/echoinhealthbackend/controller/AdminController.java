package com.giftedlabs.echoinhealthbackend.controller;

import com.giftedlabs.echoinhealthbackend.dto.admin.*;
import com.giftedlabs.echoinhealthbackend.dto.common.ApiResponse;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.security.CurrentUserService;
import com.giftedlabs.echoinhealthbackend.repository.ReportRepository;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin controller for super admin operations
 * All endpoints require tenant-aware admin access
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Super Admin management APIs")
@PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
public class AdminController {

        private final AdminService adminService;
        private final ReportRepository reportRepository;
        private final CurrentUserService currentUserService;

        // ========== Dashboard ==========

        @GetMapping("/dashboard")
        @Operation(summary = "Dashboard stats", description = "Get comprehensive system statistics")
        public ResponseEntity<ApiResponse<DashboardStatsResponse>> getDashboard(Authentication authentication) {
                DashboardStatsResponse stats = adminService.getDashboardStats(getAdminUser(authentication));
                return ResponseEntity.ok(ApiResponse.<DashboardStatsResponse>builder()
                                .success(true)
                                .data(stats)
                                .build());
        }

        // ========== System Maintenance ==========

        @PostMapping("/rebuild-search-vectors")
        @PreAuthorize("hasRole('SUPER_ADMIN')")
        @Operation(summary = "Rebuild search vectors", description = "Rebuild all search vectors for optimized search (SUPER_ADMIN only)")
        public ResponseEntity<ApiResponse<String>> rebuildSearchVectors(Authentication authentication) {
                User adminUser = getAdminUser(authentication);
                reportRepository.rebuildAllSearchVectors();
                return ResponseEntity.ok(ApiResponse.<String>builder()
                                .success(true)
                                .message("Search vectors rebuilt successfully")
                                .data("All report search vectors have been rebuilt")
                                .build());
        }

        // ========== User Management ==========

        @PostMapping("/users")
        @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SUPER_ADMIN')")
        @Operation(summary = "Create user", description = "Create a tenant-scoped user account")
        public ResponseEntity<ApiResponse<AdminUserResponse>> createUser(
                        @Valid @RequestBody CreateUserRequest request,
                        Authentication authentication) {

                AdminUserResponse user = adminService.createUser(request, getAdminUser(authentication));
                return ResponseEntity.ok(ApiResponse.<AdminUserResponse>builder()
                                .success(true)
                                .message("User created successfully")
                                .data(user)
                                .build());
        }

        @GetMapping("/users")
        @Operation(summary = "List users", description = "Get paginated list of all users with optional filters")
        public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> getAllUsers(
                        @RequestParam(required = false) String search,
                        @RequestParam(required = false) String role,
                        @RequestParam(required = false) Boolean locked,
                        @RequestParam(required = false) Boolean verified,
                        Pageable pageable,
                        Authentication authentication) {

                AdminUserSearchRequest request = AdminUserSearchRequest.builder()
                                .search(search)
                                .role(role != null ? com.giftedlabs.echoinhealthbackend.entity.Role.valueOf(role)
                                                : null)
                                .locked(locked)
                                .verified(verified)
                                .build();

                Page<AdminUserResponse> users = adminService.getAllUsers(request, pageable, getAdminUser(authentication));
                return ResponseEntity.ok(ApiResponse.<Page<AdminUserResponse>>builder()
                                .success(true)
                                .data(users)
                                .build());
        }

        @GetMapping("/users/{id}")
        @Operation(summary = "Get user", description = "Get detailed information about a single user")
        public ResponseEntity<ApiResponse<AdminUserResponse>> getUser(@PathVariable String id, Authentication authentication) {
                AdminUserResponse user = adminService.getUserById(id, getAdminUser(authentication));
                return ResponseEntity.ok(ApiResponse.<AdminUserResponse>builder()
                                .success(true)
                                .data(user)
                                .build());
        }

        @PutMapping("/users/{id}/role")
        @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SUPER_ADMIN')")
        @Operation(summary = "Update role", description = "Change a user's role within allowed admin scope")
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

        @PutMapping("/users/{id}/deactivate")
        @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SUPER_ADMIN')")
        @Operation(summary = "Deactivate user", description = "Soft deactivate a user's account while preserving history")
        public ResponseEntity<ApiResponse<AdminUserResponse>> deactivateUser(
                        @PathVariable String id,
                        Authentication authentication) {

                AdminUserResponse user = adminService.deactivateUser(id, getAdminUser(authentication));
                return ResponseEntity.ok(ApiResponse.<AdminUserResponse>builder()
                                .success(true)
                                .message("User deactivated successfully")
                                .data(user)
                                .build());
        }

        @PutMapping("/users/{id}/reactivate")
        @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SUPER_ADMIN')")
        @Operation(summary = "Reactivate user", description = "Restore a previously deactivated user account")
        public ResponseEntity<ApiResponse<AdminUserResponse>> reactivateUser(
                        @PathVariable String id,
                        Authentication authentication) {

                AdminUserResponse user = adminService.reactivateUser(id, getAdminUser(authentication));
                return ResponseEntity.ok(ApiResponse.<AdminUserResponse>builder()
                                .success(true)
                                .message("User reactivated successfully")
                                .data(user)
                                .build());
        }

        @PutMapping("/users/{id}/signature-permission")
        @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SUPER_ADMIN')")
        @Operation(summary = "Update signature permission", description = "Grant or revoke signature-upload permission for a user")
        public ResponseEntity<ApiResponse<AdminUserResponse>> updateSignaturePermission(
                        @PathVariable String id,
                        @Valid @RequestBody UpdateSignaturePermissionRequest request,
                        Authentication authentication) {

                AdminUserResponse user = adminService.updateSignaturePermission(
                                id,
                                request.getCanUploadSignature(),
                                getAdminUser(authentication));
                return ResponseEntity.ok(ApiResponse.<AdminUserResponse>builder()
                                .success(true)
                                .message("Signature permission updated successfully")
                                .data(user)
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
                        Pageable pageable,
                        Authentication authentication) {

                AuditLogSearchRequest request = AuditLogSearchRequest.builder()
                                .action(action)
                                .userEmail(userEmail)
                                .success(success)
                                .startDate(startDate)
                                .endDate(endDate)
                                .build();

                Page<AuditLogResponse> logs = adminService.getAuditLogs(request, pageable, getAdminUser(authentication));
                return ResponseEntity.ok(ApiResponse.<Page<AuditLogResponse>>builder()
                                .success(true)
                                .data(logs)
                                .build());
        }

        @GetMapping("/audit-logs/user/{userId}")
        @Operation(summary = "User audit logs", description = "Get audit logs for a specific user")
        public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> getAuditLogsForUser(
                        @PathVariable String userId,
                        Pageable pageable,
                        Authentication authentication) {

                Page<AuditLogResponse> logs = adminService.getAuditLogsForUser(
                                userId,
                                pageable,
                                getAdminUser(authentication));
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
                return currentUserService.requireUser(authentication);
        }
}
