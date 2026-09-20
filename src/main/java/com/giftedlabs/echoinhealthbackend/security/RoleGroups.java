package com.giftedlabs.echoinhealthbackend.security;

import com.giftedlabs.echoinhealthbackend.entity.Role;

import java.util.Set;

/**
 * The role sets the API authorizes against, in one place.
 *
 * <p>These lists were previously spelled out as literal strings inside eight separate
 * {@code @PreAuthorize} annotations. Adding a role, or admitting an existing one to a surface it
 * had been left out of, meant finding and editing every copy — and a copy that was missed showed
 * up as an unexplained HTTP 403 for whoever hit that endpoint. Annotations reference these
 * constants by name instead, so the set is defined once.
 *
 * <p>The string constants exist because {@code @PreAuthorize} takes a SpEL expression that must be
 * a compile-time constant; the {@code Set<Role>} forms are for code that needs to reason about
 * membership, such as the permissions endpoint.
 */
public final class RoleGroups {

    private RoleGroups() {
    }

    // ---------------------------------------------------------------- SpEL expressions

    /** Anyone who works on reports: the clinical staff of a hospital, plus platform staff. */
    public static final String CLINICAL = "hasAnyRole('HOSPITAL_ADMIN', 'SONOGRAPHER', 'RADIOLOGIST', "
            + "'PHYSICIAN', 'ADMIN', 'SUPER_ADMIN')";

    /** Administers a hospital: its own admin, plus platform staff acting on its behalf. */
    public static final String TENANT_ADMIN = "hasAnyRole('HOSPITAL_ADMIN', 'ADMIN', 'SUPER_ADMIN')";

    /** Operates the platform itself, across every tenant. */
    public static final String PLATFORM_ADMIN = "hasAnyRole('ADMIN', 'SUPER_ADMIN')";

    /** The highest privilege: tenant lifecycle, impersonation, destructive operations. */
    public static final String SUPER_ADMIN = "hasRole('SUPER_ADMIN')";

    /** Can create and manage users within some organization. */
    public static final String USER_MANAGER = "hasAnyRole('HOSPITAL_ADMIN', 'ADMIN', 'SUPER_ADMIN')";

    // ------------------------------------------------------------------- role sets

    public static final Set<Role> CLINICAL_ROLES = Set.of(
            Role.HOSPITAL_ADMIN, Role.SONOGRAPHER, Role.RADIOLOGIST,
            Role.PHYSICIAN, Role.ADMIN, Role.SUPER_ADMIN);

    public static final Set<Role> TENANT_ADMIN_ROLES = Set.of(
            Role.HOSPITAL_ADMIN, Role.ADMIN, Role.SUPER_ADMIN);

    public static final Set<Role> PLATFORM_ADMIN_ROLES = Set.of(Role.ADMIN, Role.SUPER_ADMIN);

    public static final Set<Role> SUPER_ADMIN_ROLES = Set.of(Role.SUPER_ADMIN);
}
