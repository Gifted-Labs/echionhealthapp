package com.giftedlabs.echoinhealthbackend.security;

import com.giftedlabs.echoinhealthbackend.entity.Role;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The capabilities the API grants, and which roles hold each one.
 *
 * <p>This is the single source of truth behind both {@code GET /auth/permissions} and the role
 * matrix in {@code docs/ROLES.md}. Keeping the mapping here rather than only in annotations means
 * the documentation and the endpoint cannot drift apart from each other — though the annotations
 * remain what actually enforces access, and {@code RolePermissionAlignmentTest} checks the two
 * agree.
 */
public enum Permission {

    // ----- clinical work -----
    REPORTS_WRITE("reports:write", RoleGroups.CLINICAL_ROLES),
    REPORTS_UPLOAD("reports:upload", RoleGroups.CLINICAL_ROLES),
    REPORTS_EXPORT("reports:export", RoleGroups.CLINICAL_ROLES),
    AI_GENERATE("ai:generate", RoleGroups.CLINICAL_ROLES),
    TEMPLATES_MANAGE("templates:manage", RoleGroups.CLINICAL_ROLES),
    FOLDERS_MANAGE("folders:manage", RoleGroups.CLINICAL_ROLES),
    COLLABORATION_SHARE("collaboration:share", RoleGroups.CLINICAL_ROLES),

    /**
     * Finalising a report attaches a signature. Sonographers hold this only when a hospital admin
     * has granted {@code canUploadSignature} on their account, which is why the endpoint resolves
     * it per user rather than from the role alone.
     */
    REPORTS_FINALIZE("reports:finalize", Set.of(
            Role.HOSPITAL_ADMIN, Role.RADIOLOGIST, Role.PHYSICIAN, Role.SUPER_ADMIN)),

    // ----- hospital administration -----
    BRANDING_MANAGE("branding:manage", RoleGroups.TENANT_ADMIN_ROLES),
    USERS_MANAGE("users:manage", Set.of(Role.HOSPITAL_ADMIN, Role.ADMIN, Role.SUPER_ADMIN)),
    AUDIT_VIEW("audit:view", RoleGroups.TENANT_ADMIN_ROLES),
    ANALYTICS_VIEW("analytics:view", RoleGroups.TENANT_ADMIN_ROLES),
    BILLING_VIEW("billing:view", RoleGroups.TENANT_ADMIN_ROLES),

    // ----- platform operations -----
    USERS_ASSIGN_PLATFORM_ROLES("users:assignPlatformRoles", RoleGroups.PLATFORM_ADMIN_ROLES),
    TENANTS_VIEW_ALL("tenants:viewAll", RoleGroups.PLATFORM_ADMIN_ROLES),

    USERS_DELETE("users:delete", RoleGroups.SUPER_ADMIN_ROLES),
    BILLING_MANAGE("billing:manage", RoleGroups.SUPER_ADMIN_ROLES),
    TENANTS_MANAGE("tenants:manage", RoleGroups.SUPER_ADMIN_ROLES),
    IMPERSONATE("platform:impersonate", RoleGroups.SUPER_ADMIN_ROLES),
    SYSTEM_MAINTAIN("platform:maintain", RoleGroups.SUPER_ADMIN_ROLES);

    private final String key;
    private final Set<Role> roles;

    Permission(String key, Set<Role> roles) {
        this.key = key;
        this.roles = roles;
    }

    public String getKey() {
        return key;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public boolean grantedTo(Role role) {
        return roles.contains(role);
    }

    /** Capability keys held by a role, before per-user grants are applied. */
    public static Set<String> forRole(Role role) {
        return Arrays.stream(values())
                .filter(permission -> permission.grantedTo(role))
                .map(Permission::getKey)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
