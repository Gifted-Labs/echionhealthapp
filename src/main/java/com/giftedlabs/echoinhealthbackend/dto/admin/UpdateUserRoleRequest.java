package com.giftedlabs.echoinhealthbackend.dto.admin;

import com.giftedlabs.echoinhealthbackend.entity.Role;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating user role
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRoleRequest {
    @NotNull(message = "Role is required")
    private Role role;
}
