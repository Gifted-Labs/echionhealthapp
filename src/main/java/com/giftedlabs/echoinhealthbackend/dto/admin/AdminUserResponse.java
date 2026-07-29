package com.giftedlabs.echoinhealthbackend.dto.admin;

import com.giftedlabs.echoinhealthbackend.entity.Role;
import com.giftedlabs.echoinhealthbackend.entity.Designation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * User response DTO for admin view with full details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {
    private String id;
    private String email;
    private String username;
    private String firstName;
    private String lastName;
    private String fullName;
    private String phone;
    private String hospitalName;
    private String department;
    private String serviceNumber;
    private Role role;
    private Designation designation;
    private Boolean emailVerified;
    private Boolean accountLocked;
    private Boolean active;
    private Boolean canUploadSignature;
    private Boolean mfaEnabled;
    private Boolean profileComplete;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private LocalDateTime profileUpdatedAt;
}
