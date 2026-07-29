package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.auth.*;
import com.giftedlabs.echoinhealthbackend.entity.EmailVerificationToken;
import com.giftedlabs.echoinhealthbackend.entity.Organization;
import com.giftedlabs.echoinhealthbackend.entity.RefreshToken;
import com.giftedlabs.echoinhealthbackend.entity.Role;
import com.giftedlabs.echoinhealthbackend.entity.SubscriptionTier;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.exception.*;
import com.giftedlabs.echoinhealthbackend.repository.EmailVerificationTokenRepository;
import com.giftedlabs.echoinhealthbackend.repository.OrganizationRepository;
import com.giftedlabs.echoinhealthbackend.repository.RefreshTokenRepository;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import com.giftedlabs.echoinhealthbackend.util.TokenGenerator;
import com.giftedlabs.echoinhealthbackend.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Core authentication service handling registration, login, email verification,
 * and token management
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

        private final UserRepository userRepository;
        private final OrganizationRepository organizationRepository;

        /** Idle window for a session, in minutes. Must exceed the access-token TTL. */
        @org.springframework.beans.factory.annotation.Value("${jwt.idle-timeout-minutes:15}")
        private long idleTimeoutMinutes;
        private final EmailVerificationTokenRepository verificationTokenRepository;
        private final RefreshTokenRepository refreshTokenRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;
        private final EmailService emailService;
        private final AuditService auditService;
        private final AuthenticationManager authenticationManager;
        private final CustomUserDetailsService userDetailsService;
        private final TotpService totpService;
        private final EncryptionUtil encryptionUtil;
        private final LoginAttemptService loginAttemptService;

        @Value("${app.base-url}")
        private String baseUrl;

        @Value("${app.verification-token.expiration-hours}")
        private int verificationTokenExpirationHours;

        @Value("${app.auth.auto-verify-registration:false}")
        private boolean autoVerifyRegistration;

        /**
         * Register a new user with basic information
         */
        @Transactional
        public void register(RegisterRequest request) {
                if (userRepository.existsByEmail(request.getEmail())) {
                        auditService.logAction(request.getEmail(), "registration_failed",
                                        "Email already exists", false);
                        throw new EmailAlreadyExistsException("Email already registered");
                }

                Organization organization = organizationRepository.save(Organization.builder()
                                .name(request.getOrganizationName())
                                .hospitalName(request.getHospitalName())
                                .address(request.getAddress())
                                .phone(request.getPhone())
                                .email(request.getEmail())
                                .website(request.getWebsite())
                                .subscriptionTier(SubscriptionTier.BASIC)
                                .addonStorageMb(0)
                                .addonAiCredits(0)
                                .liteEmrIntegrationEnabled(false)
                                .aiCreditsUsedThisMonth(0)
                                .aiCreditsLastResetAt(LocalDateTime.now())
                                .build());

                User user = User.builder()
                                .organization(organization)
                                .email(request.getEmail())
                                .passwordHash(passwordEncoder.encode(request.getPassword()))
                                .firstName(request.getFirstName())
                                .lastName(request.getLastName())
                                .phone(request.getPhone())
                                .hospitalName(request.getHospitalName())
                                .role(Role.HOSPITAL_ADMIN)
                                .emailVerified(autoVerifyRegistration)
                                .accountLocked(false)
                                .build();

                User savedUser = userRepository.save(user);
                if (!autoVerifyRegistration) {
                        createAndSendVerificationToken(savedUser);
                }
                auditService.logAction(savedUser, "organization_registered",
                                "Organization registered and first hospital admin provisioned");

                log.info("Organization {} registered with first admin {}", organization.getId(), savedUser.getEmail());
        }

        /**
         * Verify user email with token
         */
        @Transactional
        public void verifyEmail(String token) {
                EmailVerificationToken verificationToken = verificationTokenRepository.findByToken(token)
                                .orElseThrow(() -> new InvalidTokenException("Invalid verification token"));

                if (verificationToken.isVerified()) {
                        throw new InvalidTokenException("Token has already been used");
                }

                if (verificationToken.isExpired()) {
                        throw new InvalidTokenException("Verification token has expired");
                }

                User user = verificationToken.getUser();
                user.setEmailVerified(true);
                userRepository.save(user);

                verificationToken.setVerifiedAt(LocalDateTime.now());
                verificationTokenRepository.save(verificationToken);

                // Send welcome email
                emailService.sendWelcomeEmail(user.getEmail(), user.getFirstName());

                // Audit log
                auditService.logAction(user, "email_verified", "Email verified successfully");

                log.info("Email verified for user: {}", user.getEmail());
        }

        /**
         * Resend verification email
         */
        @Transactional
        public void resendVerificationEmail(String email) {
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new UserNotFoundException("User not found"));

                if (user.getEmailVerified()) {
                        throw new InvalidTokenException("Email is already verified");
                }

                // Delete old tokens
                verificationTokenRepository.deleteByUser(user);

                // Generate new token
                String token = TokenGenerator.generateToken();
                EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                                .token(token)
                                .user(user)
                                .expiresAt(LocalDateTime.now().plusHours(verificationTokenExpirationHours))
                                .build();

                verificationTokenRepository.save(verificationToken);

                // Send verification email
                String verificationLink = baseUrl + "/api/auth/verify-email?token=" + token;
                emailService.sendVerificationEmail(user.getEmail(), user.getFirstName(), verificationLink);

                // Audit log
                auditService.logAction(user, "verification_email_resent", "Verification email resent");

                log.info("Verification email resent to: {}", email);
        }

        /**
         * Authenticate user and generate tokens
         */
        @Transactional
        public AuthResponse login(LoginRequest request) {
                try {
                        loginAttemptService.assertNotBlocked(request.getIdentifier());
                        User user = resolveLoginUser(request);

                        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                                loginAttemptService.recordFailure(request.getIdentifier());
                                auditService.logFailedAction(request.getIdentifier(), "login_failed",
                                                "Invalid credentials");
                                throw new InvalidCredentialsException("Invalid email or password");
                        }

                        // Check if email is verified
                        if (!user.getEmailVerified()) {
                                loginAttemptService.recordFailure(request.getIdentifier());
                                auditService.logFailedAction(request.getIdentifier(), "login_failed",
                                                "Email not verified");
                                throw new EmailNotVerifiedException("Please verify your email before logging in");
                        }

                        // Check if account is locked
                        if (user.getAccountLocked()) {
                                loginAttemptService.recordFailure(request.getIdentifier());
                                auditService.logFailedAction(request.getIdentifier(), "login_failed",
                                                "Account locked");
                                throw new InvalidCredentialsException("Account is locked");
                        }

                        if (!Boolean.TRUE.equals(user.getActive())) {
                                loginAttemptService.recordFailure(request.getIdentifier());
                                auditService.logFailedAction(request.getIdentifier(), "login_failed",
                                                "Account deactivated");
                                throw new InvalidCredentialsException("Account is deactivated");
                        }

                        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
                                if (request.getTotpCode() == null || request.getTotpCode().isBlank()) {
                                        auditService.logAction(user, "login_mfa_required",
                                                        "Password accepted, awaiting TOTP verification");
                                        return AuthResponse.builder()
                                                        .mfaRequired(true)
                                                        .user(mapUserProfile(user))
                                                        .build();
                                }
                                if (!totpService.isValidCode(readMfaSecret(user), request.getTotpCode())) {
                                        loginAttemptService.recordFailure(request.getIdentifier());
                                        auditService.logFailedAction(user.getEmail(), "login_failed",
                                                        "Invalid MFA code");
                                        throw new InvalidCredentialsException("Invalid MFA code");
                                }
                        }

                        // Update last login
                        user.setLastLoginAt(LocalDateTime.now());
                        userRepository.save(user);
                        loginAttemptService.clearFailures(request.getIdentifier());

                        // Generate tokens
                        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
                        String accessToken = jwtService.generateToken(userDetails);
                        String refreshToken = jwtService.generateRefreshToken(userDetails);

                        // Save refresh token
                        RefreshToken refreshTokenEntity = RefreshToken.builder()
                                        .token(refreshToken)
                                        .user(user)
                                        .expiresAt(LocalDateTime.now().plusDays(7))
                                        .build();
                        refreshTokenRepository.save(refreshTokenEntity);

                        // Audit log
                        auditService.logAction(user, "login_success", "User logged in successfully");

                        log.info("User logged in successfully: {}", user.getEmail());

                        // Build response
                        UserProfileResponse userProfile = mapUserProfile(user);

                        return AuthResponse.builder()
                                        .accessToken(accessToken)
                                        .refreshToken(refreshToken)
                                        .tokenType("Bearer")
                                        .expiresIn(jwtService.getJwtExpiration() / 1000) // Convert to seconds
                                        .user(userProfile)
                                        .build();

                } catch (AuthenticationRateLimitException | EmailNotVerifiedException | InvalidCredentialsException e) {
                        throw e;
                } catch (Exception e) {
                        loginAttemptService.recordFailure(request.getIdentifier());
                        auditService.logFailedAction(request.getIdentifier(), "login_failed", e.getMessage());
                        throw new InvalidCredentialsException("Invalid email or password");
                }
        }

        /**
         * Refresh access token using refresh token
         */
        @Transactional
        public AuthResponse refreshToken(String refreshTokenValue) {
                RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

                if (!refreshToken.isValid()) {
                        throw new InvalidTokenException("Refresh token is expired or revoked");
                }

                // UR-089: sliding idle window. An access token only lives `jwt.expiration`, so an
                // active client refreshes well inside this window; a client that has gone quiet
                // stops refreshing and its session becomes unrenewable. Without this the 24-hour
                // refresh token let a silent client stay authenticated all day with no activity,
                // which is an absolute session lifetime, not an idle timeout.
                LocalDateTime lastActivity = refreshToken.getLastUsedAt() != null
                                ? refreshToken.getLastUsedAt()
                                : refreshToken.getCreatedAt();
                if (lastActivity != null
                                && lastActivity.plusMinutes(idleTimeoutMinutes).isBefore(LocalDateTime.now())) {
                        refreshToken.setRevokedAt(LocalDateTime.now());
                        refreshTokenRepository.save(refreshToken);
                        auditService.logAction(refreshToken.getUser(), "session_idle_timeout",
                                        "Session revoked after " + idleTimeoutMinutes
                                                        + " minutes of inactivity");
                        throw new InvalidTokenException(
                                        "Session expired after a period of inactivity. Please sign in again.");
                }

                User user = refreshToken.getUser();
                UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());

                // Generate new access token
                String newAccessToken = jwtService.generateToken(userDetails);

                refreshToken.setLastUsedAt(LocalDateTime.now());
                refreshTokenRepository.save(refreshToken);

                // Audit log
                auditService.logAction(user, "token_refreshed", "Access token refreshed");

                log.info("Token refreshed for user: {}", user.getEmail());

                // Build response
                UserProfileResponse userProfile = mapUserProfile(user);

                return AuthResponse.builder()
                                .accessToken(newAccessToken)
                                .refreshToken(refreshTokenValue) // Same refresh token
                                .tokenType("Bearer")
                                .expiresIn(jwtService.getJwtExpiration() / 1000)
                                .user(userProfile)
                                .build();
        }

        /**
         * Logout user by revoking refresh token
         */
        @Transactional
        public void logout(String email, String refreshTokenValue) {
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new UserNotFoundException("User not found"));

                // Revoke the specific refresh token
                refreshTokenRepository.findByToken(refreshTokenValue).ifPresent(token -> {
                        token.setRevokedAt(LocalDateTime.now());
                        refreshTokenRepository.save(token);
                });

                // Audit log
                auditService.logAction(user, "logout", "User logged out");

                log.info("User logged out: {}", email);
        }

        /**
         * Logout from all devices by revoking all refresh tokens
         */
        @Transactional
        public void logoutAllDevices(String email) {
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new UserNotFoundException("User not found"));

                refreshTokenRepository.revokeAllUserTokens(user, LocalDateTime.now());

                // Audit log
                auditService.logAction(user, "logout_all_devices", "User logged out from all devices");

                log.info("User logged out from all devices: {}", email);
        }

        private User resolveLoginUser(LoginRequest request) {
                if (request.getOrganizationName() != null && !request.getOrganizationName().isBlank()) {
                        Organization organization = organizationRepository.findByName(request.getOrganizationName())
                                        .orElseThrow(() -> new UserNotFoundException("Organization not found"));

                        return userRepository.findByUsernameAndOrganizationId(request.getIdentifier(), organization.getId())
                                        .orElseGet(() -> userRepository.findByEmailAndOrganizationId(
                                                        request.getIdentifier(),
                                                        organization.getId())
                                                        .orElseThrow(() -> new UserNotFoundException("User not found")));
                }

                return userRepository.findByEmail(request.getIdentifier())
                                .orElseThrow(() -> new UserNotFoundException("User not found"));
        }

        @Transactional(readOnly = true)
        public MfaStatusResponse getMfaStatus(String email) {
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new UserNotFoundException("User not found"));
                return MfaStatusResponse.builder()
                                .enabled(Boolean.TRUE.equals(user.getMfaEnabled()))
                                .build();
        }

        @Transactional
        public MfaSetupResponse beginMfaSetup(String email) {
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new UserNotFoundException("User not found"));
                if (Boolean.TRUE.equals(user.getMfaEnabled()) && user.getMfaSecret() != null
                                && !user.getMfaSecret().isBlank()) {
                        return MfaSetupResponse.builder()
                                        .enabled(true)
                                        .build();
                }

                String secret = totpService.generateSecret();
                user.setMfaSecret(writeMfaSecret(secret));
                user.setMfaEnabled(false);
                userRepository.save(user);
                auditService.logAction(user, "mfa_setup_started", "Started MFA setup");

                return MfaSetupResponse.builder()
                                .enabled(false)
                                .secret(secret)
                                .otpauthUrl(totpService.buildOtpAuthUrl("Echion Health", user.getEmail(), secret))
                                .build();
        }

        @Transactional
        public MfaStatusResponse enableMfa(String email, MfaVerificationRequest request) {
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new UserNotFoundException("User not found"));
                if (user.getMfaSecret() == null || user.getMfaSecret().isBlank()) {
                        throw new IllegalArgumentException("MFA setup has not been started");
                }
                if (!totpService.isValidCode(readMfaSecret(user), request.getCode())) {
                        throw new InvalidCredentialsException("Invalid authenticator code");
                }
                user.setMfaEnabled(true);
                userRepository.save(user);
                auditService.logAction(user, "mfa_enabled", "Enabled TOTP MFA");
                return MfaStatusResponse.builder().enabled(true).build();
        }

        @Transactional
        public MfaStatusResponse disableMfa(String email, MfaVerificationRequest request) {
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new UserNotFoundException("User not found"));
                if (user.getMfaSecret() == null || user.getMfaSecret().isBlank()
                                || !totpService.isValidCode(readMfaSecret(user), request.getCode())) {
                        throw new InvalidCredentialsException("Invalid authenticator code");
                }
                user.setMfaEnabled(false);
                user.setMfaSecret(null);
                userRepository.save(user);
                auditService.logAction(user, "mfa_disabled", "Disabled TOTP MFA");
                return MfaStatusResponse.builder().enabled(false).build();
        }

        private UserProfileResponse mapUserProfile(User user) {
                return UserProfileResponse.builder()
                                .id(user.getId())
                                .organizationId(user.getOrganizationId())
                                .organizationName(user.getOrganization() != null ? user.getOrganization().getName()
                                                : null)
                                .email(user.getEmail())
                                .username(user.getUsername())
                                .firstName(user.getFirstName())
                                .lastName(user.getLastName())
                                .phone(user.getPhone())
                                .hospitalName(user.getHospitalName())
                                .department(user.getDepartment())
                                .serviceNumber(user.getServiceNumber())
                                .role(user.getRole())
                                .designation(user.getDesignation())
                                .emailVerified(user.getEmailVerified())
                                .active(user.getActive())
                                .canUploadSignature(user.getCanUploadSignature())
                                .mfaEnabled(user.getMfaEnabled())
                                .profileCompleted(user.hasCompletedProfile())
                                .createdAt(user.getCreatedAt())
                                .profileUpdatedAt(user.getProfileUpdatedAt())
                                .lastLoginAt(user.getLastLoginAt())
                                .build();
        }

        private void createAndSendVerificationToken(User user) {
                verificationTokenRepository.deleteByUser(user);
                String token = TokenGenerator.generateToken();
                EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                                .token(token)
                                .user(user)
                                .expiresAt(LocalDateTime.now().plusHours(verificationTokenExpirationHours))
                                .build();
                verificationTokenRepository.save(verificationToken);

                String verificationLink = baseUrl + "/api/auth/verify-email?token=" + token;
                emailService.sendVerificationEmail(user.getEmail(), user.getFirstName(), verificationLink);
        }

        private String readMfaSecret(User user) {
                String stored = user.getMfaSecret();
                if (stored == null || stored.isBlank()) {
                        return stored;
                }
                return encryptionUtil.isEncrypted(stored) ? encryptionUtil.decrypt(stored) : stored;
        }

        private String writeMfaSecret(String secret) {
                return encryptionUtil.encrypt(secret);
        }
}
