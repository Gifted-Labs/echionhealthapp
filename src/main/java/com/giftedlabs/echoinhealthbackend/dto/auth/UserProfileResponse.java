package com.giftedlabs.echoinhealthbackend.dto.auth;

import com.giftedlabs.echoinhealthbackend.entity.Role;
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
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private String hospitalName;
    private String department;
    private String serviceNumber;
    private Role role;
    private Boolean emailVerified;
    private Boolean profileCompleted;
    private LocalDateTime createdAt;
    private LocalDateTime profileUpdatedAt;
    private LocalDateTime lastLoginAt;
}
