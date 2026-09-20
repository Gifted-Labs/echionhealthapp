package com.giftedlabs.echoinhealthbackend.dto.auth;

import com.giftedlabs.echoinhealthbackend.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * What the signed-in user is allowed to do.
 *
 * <p>Exists so the front-end can hide controls a user cannot use, instead of discovering the
 * answer by calling an endpoint and getting a 403. The client asked for clarification of roles and
 * their operations; this is that answer in machine-readable form, and {@code docs/ROLES.md} is the
 * same answer for people.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionsResponse {

    private Role role;
    private String organizationId;
    private String organizationName;

    /** Capability keys the user holds. Stable identifiers, safe to branch on in the UI. */
    private Set<String> permissions;

    /** True when this session was opened by a super admin acting as this user. */
    private boolean impersonating;
}
