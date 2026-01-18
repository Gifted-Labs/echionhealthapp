package com.giftedlabs.echoinhealthbackend.dto.admin;

import com.giftedlabs.echoinhealthbackend.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for searching/filtering users
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserSearchRequest {
    private String search; // Search by name or email
    private Role role; // Filter by role
    private Boolean locked; // Filter by locked status
    private Boolean verified; // Filter by email verified status
}
