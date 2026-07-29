package com.giftedlabs.echoinhealthbackend.dto.auth;

import com.giftedlabs.echoinhealthbackend.entity.Role;
import com.giftedlabs.echoinhealthbackend.entity.Designation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for user profile response.
 * Excludes sensitive information like password hash.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    private String id;
    private String organizationId;
    private String organizationName;
    private String email;
    private String username;
    private String firstName;
    private String lastName;
    private String phone;
    private String hospitalName;
    private String department;
    private String serviceNumber;
    private Role role;
    private Designation designation;
    private Boolean emailVerified;
    private Boolean active;
    private Boolean canUploadSignature;
    private Boolean mfaEnabled;
    private Boolean profileCompleted;
    private LocalDateTime createdAt;
    private LocalDateTime profileUpdatedAt;
    private LocalDateTime lastLoginAt;
}
