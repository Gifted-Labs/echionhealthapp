package com.giftedlabs.echoinhealthbackend.controller;

import com.giftedlabs.echoinhealthbackend.dto.auth.*;
import com.giftedlabs.echoinhealthbackend.dto.common.ApiResponse;
import com.giftedlabs.echoinhealthbackend.service.AuthService;
import com.giftedlabs.echoinhealthbackend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication and user profile management
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Authentication and user management endpoints")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    /**
     * Register a new user
     */
    @PostMapping("/register")
    @Operation(summary = "Register a new organization", description = "Create an organization and auto-provision its first hospital administrator.")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Organization registered successfully. Check your email if verification is required before login.",
                        null));
    }

    /**
     * Verify email with token
     */
    @PostMapping("/verify-email")
    @Operation(summary = "Verify email address", description = "Verify email using the token sent to the user's email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success(
                "Email verified successfully! You can now log in.",
                null));
    }

    /**
     * Resend verification email
     */
    @PostMapping("/resend-verification")
    @Operation(summary = "Resend verification email", description = "Request a new verification email")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@RequestParam String email) {
        authService.resendVerificationEmail(email);
        return ResponseEntity.ok(ApiResponse.success(
                "Verification email sent! Please check your inbox.",
                null));
    }

    /**
     * Login user
     */
    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate using email, or using username plus organization name, and receive JWT tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    /**
     * Refresh access token
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Get a new access token using refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse authResponse = authService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    /**
     * Logout user
     */
    @PostMapping("/logout")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Logout", description = "Logout user and revoke refresh token")
    public ResponseEntity<ApiResponse<Void>> logout(
            Authentication authentication,
            @Valid @RequestBody RefreshTokenRequest request) {
        String email = authentication.getName();
        authService.logout(email, request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    /**
     * Get current user profile
     */
    @GetMapping("/profile")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SONOGRAPHER', 'RADIOLOGIST', 'PHYSICIAN', 'ADMIN', 'SUPER_ADMIN')")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Get user profile", description = "Get current authenticated user's profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(Authentication authentication) {
        String email = authentication.getName();
        UserProfileResponse profile = userService.getUserProfile(email);
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    /**
     * Complete user profile with professional details
     */
    @PostMapping("/complete-profile")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SONOGRAPHER', 'RADIOLOGIST', 'PHYSICIAN', 'ADMIN', 'SUPER_ADMIN')")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Complete profile", description = "Add professional details (phone, hospital, department, serviceNumber)")
    public ResponseEntity<ApiResponse<UserProfileResponse>> completeProfile(
            Authentication authentication,
            @Valid @RequestBody CompleteProfileRequest request) {
        String email = authentication.getName();
        UserProfileResponse profile = userService.completeProfile(email, request);
        return ResponseEntity.ok(ApiResponse.success(
                "Profile completed successfully!",
                profile));
    }

    /**
     * Update user profile
     */
    @PatchMapping("/profile")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SONOGRAPHER', 'RADIOLOGIST', 'PHYSICIAN', 'ADMIN', 'SUPER_ADMIN')")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Update profile", description = "Update any profile fields (partial update)")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {
        String email = authentication.getName();
        UserProfileResponse profile = userService.updateProfile(email, request);
        return ResponseEntity.ok(ApiResponse.success(
                "Profile updated successfully!",
                profile));
    }

    @GetMapping("/mfa")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SONOGRAPHER', 'RADIOLOGIST', 'PHYSICIAN', 'ADMIN', 'SUPER_ADMIN')")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Get MFA status", description = "Check whether TOTP MFA is enabled for the current user")
    public ResponseEntity<ApiResponse<MfaStatusResponse>> getMfaStatus(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(authService.getMfaStatus(authentication.getName())));
    }

    @PostMapping("/mfa/setup")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SONOGRAPHER', 'RADIOLOGIST', 'PHYSICIAN', 'ADMIN', 'SUPER_ADMIN')")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Begin MFA setup", description = "Generate a TOTP secret and otpauth URL for an authenticator app")
    public ResponseEntity<ApiResponse<MfaSetupResponse>> beginMfaSetup(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "MFA setup initialized",
                authService.beginMfaSetup(authentication.getName())));
    }

    @PostMapping("/mfa/enable")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SONOGRAPHER', 'RADIOLOGIST', 'PHYSICIAN', 'ADMIN', 'SUPER_ADMIN')")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Enable MFA", description = "Verify a TOTP code and enable MFA for the current user")
    public ResponseEntity<ApiResponse<MfaStatusResponse>> enableMfa(
            Authentication authentication,
            @Valid @RequestBody MfaVerificationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "MFA enabled successfully",
                authService.enableMfa(authentication.getName(), request)));
    }

    @PostMapping("/mfa/disable")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SONOGRAPHER', 'RADIOLOGIST', 'PHYSICIAN', 'ADMIN', 'SUPER_ADMIN')")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Disable MFA", description = "Verify a TOTP code and disable MFA for the current user")
    public ResponseEntity<ApiResponse<MfaStatusResponse>> disableMfa(
            Authentication authentication,
            @Valid @RequestBody MfaVerificationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "MFA disabled successfully",
                authService.disableMfa(authentication.getName(), request)));
    }
}
