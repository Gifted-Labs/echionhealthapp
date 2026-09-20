package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.auth.CompleteProfileRequest;
import com.giftedlabs.echoinhealthbackend.dto.auth.UpdateProfileRequest;
import com.giftedlabs.echoinhealthbackend.dto.auth.UserProfileResponse;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.exception.UserNotFoundException;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import com.giftedlabs.echoinhealthbackend.dto.auth.PermissionsResponse;
import com.giftedlabs.echoinhealthbackend.entity.Role;
import com.giftedlabs.echoinhealthbackend.security.ImpersonationContext;
import com.giftedlabs.echoinhealthbackend.security.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.giftedlabs.echoinhealthbackend.util.CacheNames.USERS;

/**
 * Service for user profile management.
 * Implements caching for frequently accessed user profiles.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    /**
     * This bean's own proxy, so cached methods can be reached from inside the class. Obtained
     * lazily through a provider because injecting the bean into itself eagerly is a circular
     * dependency.
     */
    private final ObjectProvider<UserService> self;
    private final AuditService auditService;

    // ========== Read Operations (Cached) ==========

    /**
     * Get user profile by email.
     * Result is cached to reduce database lookups.
     *
     * @param email User's email address
     * @return User profile response
     * @throws UserNotFoundException if user not found
     */
    public UserProfileResponse getUserProfile(String email) {
        // Through the proxy, not this.getCachedProfile(...): Spring's caching is proxy-based, and
        // a direct self-call would silently skip the cache entirely.
        UserProfileResponse cached = self.getObject().getCachedProfile(email);

        // Impersonation is per-request state and must never be written into the shared cache:
        // one impersonated request would otherwise leave the flag set for every later caller.
        // The cached instance is copied rather than mutated for the same reason.
        return cached.toBuilder()
                .impersonating(ImpersonationContext.isImpersonating())
                .impersonatedBy(ImpersonationContext.get() != null
                        ? ImpersonationContext.get().impersonatorEmail() : null)
                .build();
    }

    @Cacheable(value = USERS, key = "#email")
    public UserProfileResponse getCachedProfile(String email) {
        log.debug("Cache miss: fetching user profile for email: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        return mapToProfileResponse(user);
    }

    /**
     * Capability keys the user holds, for front-end gating.
     *
     * <p>Sonographers are a special case: they may finalise a report only when a hospital admin has
     * granted them signature permission, so that one capability is resolved per user rather than
     * from the role alone.
     */
    @Transactional(readOnly = true)
    public PermissionsResponse getPermissions(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        Set<String> permissions = new LinkedHashSet<>(Permission.forRole(user.getRole()));
        if (user.getRole() == Role.SONOGRAPHER && Boolean.TRUE.equals(user.getCanUploadSignature())) {
            permissions.add(Permission.REPORTS_FINALIZE.getKey());
        }

        return PermissionsResponse.builder()
                .role(user.getRole())
                .organizationId(user.getOrganizationId())
                .organizationName(user.getOrganization() != null ? user.getOrganization().getName() : null)
                .permissions(permissions)
                .impersonating(ImpersonationContext.isImpersonating())
                .build();
    }

    // ========== Write Operations (Cache Evicting) ==========

    /**
     * Complete user profile with professional details.
     * Evicts user from cache after update.
     *
     * @param email   User's email address
     * @param request Profile completion details
     * @return Updated user profile response
     */
    @Transactional
    @CacheEvict(value = USERS, key = "#email")
    public UserProfileResponse completeProfile(String email, CompleteProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        user.setPhone(request.getPhone());
        user.setHospitalName(request.getHospitalName());
        user.setDepartment(request.getDepartment());
        user.setServiceNumber(request.getServiceNumber());
        user.setProfileUpdatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);

        auditService.logAction(
                savedUser,
                "profile_completed",
                String.format("Hospital: %s, Department: %s",
                        request.getHospitalName(), request.getDepartment()));

        log.info("Profile completed for user: {}", email);

        return mapToProfileResponse(savedUser);
    }

    /**
     * Update user profile (partial update).
     * Evicts user from cache after update.
     *
     * @param email   User's email address
     * @param request Profile update details
     * @return Updated user profile response
     */
    @Transactional
    @CacheEvict(value = USERS, key = "#email")
    public UserProfileResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        StringBuilder changedFields = new StringBuilder();

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
            changedFields.append("firstName,");
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
            changedFields.append("lastName,");
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
            changedFields.append("phone,");
        }
        if (request.getHospitalName() != null) {
            user.setHospitalName(request.getHospitalName());
            changedFields.append("hospitalName,");
        }
        if (request.getDepartment() != null) {
            user.setDepartment(request.getDepartment());
            changedFields.append("department,");
        }
        if (request.getServiceNumber() != null) {
            user.setServiceNumber(request.getServiceNumber());
            changedFields.append("serviceNumber,");
        }

        user.setProfileUpdatedAt(LocalDateTime.now());
        User savedUser = userRepository.save(user);

        auditService.logAction(
                savedUser,
                "profile_updated",
                "Changed fields: " + changedFields.toString());

        log.info("Profile updated for user: {}", email);

        return mapToProfileResponse(savedUser);
    }

    // ========== Private Helper Methods ==========

    /**
     * Map User entity to UserProfileResponse DTO
     */
    private UserProfileResponse mapToProfileResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .organizationId(user.getOrganizationId())
                .organizationName(user.getOrganization() != null ? user.getOrganization().getName() : null)
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
}
