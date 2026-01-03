package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.auth.CompleteProfileRequest;
import com.giftedlabs.echoinhealthbackend.dto.auth.UpdateProfileRequest;
import com.giftedlabs.echoinhealthbackend.dto.auth.UserProfileResponse;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.exception.UserNotFoundException;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for user profile management
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final AuditService auditService;

    /**
     * Get user profile by email
     */
    public UserProfileResponse getUserProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        return mapToProfileResponse(user);
    }

    /**
     * Complete user profile with professional details
     */
    @Transactional
    public UserProfileResponse completeProfile(String email, CompleteProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        user.setPhone(request.getPhone());
        user.setHospitalName(request.getHospitalName());
        user.setDepartment(request.getDepartment());
        user.setServiceNumber(request.getServiceNumber());
        user.setProfileUpdatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);

        // Audit log
        auditService.logAction(
                savedUser,
                "profile_completed",
                String.format("Hospital: %s, Department: %s",
                        request.getHospitalName(), request.getDepartment()));

        log.info("Profile completed for user: {}", email);

        return mapToProfileResponse(savedUser);
    }

    /**
     * Update user profile (partial update)
     */
    @Transactional
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

        // Audit log
        auditService.logAction(
                savedUser,
                "profile_updated",
                "Changed fields: " + changedFields.toString());

        log.info("Profile updated for user: {}", email);

        return mapToProfileResponse(savedUser);
    }

    /**
     * Map User entity to UserProfileResponse DTO
     */
    private UserProfileResponse mapToProfileResponse(User user) {
        return UserProfileResponse.builder()
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
    }
}
