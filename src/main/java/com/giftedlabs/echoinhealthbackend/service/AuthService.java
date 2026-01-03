package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.auth.*;
import com.giftedlabs.echoinhealthbackend.entity.EmailVerificationToken;
import com.giftedlabs.echoinhealthbackend.entity.RefreshToken;
import com.giftedlabs.echoinhealthbackend.entity.Role;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.exception.*;
import com.giftedlabs.echoinhealthbackend.repository.EmailVerificationTokenRepository;
import com.giftedlabs.echoinhealthbackend.repository.RefreshTokenRepository;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import com.giftedlabs.echoinhealthbackend.util.TokenGenerator;
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
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final AuditService auditService;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.verification-token.expiration-hours}")
    private int verificationTokenExpirationHours;

    /**
     * Register a new user with basic information
     */
    @Transactional
    public void register(RegisterRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            auditService.logAction(request.getEmail(), "registration_failed",
                    "Email already exists", false);
            throw new EmailAlreadyExistsException("Email already registered");
        }

        // Create user
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(Role.SONOGRAPHER)
                .emailVerified(false)
                .accountLocked(false)
                .build();

        User savedUser = userRepository.save(user);

        // Generate verification token
        String token = TokenGenerator.generateToken();
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .token(token)
                .user(savedUser)
                .expiresAt(LocalDateTime.now().plusHours(verificationTokenExpirationHours))
                .build();

        verificationTokenRepository.save(verificationToken);

        // Send verification email
        String verificationLink = baseUrl + "/api/auth/verify-email?token=" + token;
        emailService.sendVerificationEmail(
                savedUser.getEmail(),
                savedUser.getFirstName(),
                verificationLink);

        // Audit log
        auditService.logAction(savedUser, "user_registered",
                "User registered successfully, verification email sent");

        log.info("User registered successfully: {}", savedUser.getEmail());
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
            // Authenticate
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new UserNotFoundException("User not found"));

            // Check if email is verified
            if (!user.getEmailVerified()) {
                auditService.logFailedAction(request.getEmail(), "login_failed",
                        "Email not verified");
                throw new EmailNotVerifiedException("Please verify your email before logging in");
            }

            // Check if account is locked
            if (user.getAccountLocked()) {
                auditService.logFailedAction(request.getEmail(), "login_failed",
                        "Account locked");
                throw new InvalidCredentialsException("Account is locked");
            }

            // Update last login
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);

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
            UserProfileResponse userProfile = UserProfileResponse.builder()
                    .id(user.getId())
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .phone(user.getPhone())
                    .hospitalName(user.getHospitalName())
                    .department(user.getDepartment())
                    .serviceNumber(user.getServiceNumber())
                    .role(user.getRole())
                    .emailVerified(user.getEmailVerified())
                    .profileCompleted(user.hasCompletedProfile())
                    .createdAt(user.getCreatedAt())
                    .profileUpdatedAt(user.getProfileUpdatedAt())
                    .lastLoginAt(user.getLastLoginAt())
                    .build();

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(jwtService.getJwtExpiration() / 1000) // Convert to seconds
                    .user(userProfile)
                    .build();

        } catch (EmailNotVerifiedException | InvalidCredentialsException e) {
            throw e;
        } catch (Exception e) {
            auditService.logFailedAction(request.getEmail(), "login_failed", e.getMessage());
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

        User user = refreshToken.getUser();
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());

        // Generate new access token
        String newAccessToken = jwtService.generateToken(userDetails);

        // Audit log
        auditService.logAction(user, "token_refreshed", "Access token refreshed");

        log.info("Token refreshed for user: {}", user.getEmail());

        // Build response
        UserProfileResponse userProfile = UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .hospitalName(user.getHospitalName())
                .department(user.getDepartment())
                .serviceNumber(user.getServiceNumber())
                .role(user.getRole())
                .emailVerified(user.getEmailVerified())
                .profileCompleted(user.hasCompletedProfile())
                .createdAt(user.getCreatedAt())
                .profileUpdatedAt(user.getProfileUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();

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
}
