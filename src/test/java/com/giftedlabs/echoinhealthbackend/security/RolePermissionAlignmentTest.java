package com.giftedlabs.echoinhealthbackend.security;

import com.giftedlabs.echoinhealthbackend.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the three statements of the role model agreeing with each other: the
 * {@code @PreAuthorize} annotations that enforce access, the {@link Permission} catalogue the
 * permissions endpoint reports, and the matrix in {@code docs/ROLES.md} that the client reads.
 *
 * <p>The client asked for clarification of roles and their operations. The reason that was hard to
 * answer is that the rules lived only as copy-pasted strings across eight controllers, with no
 * single place that stated them. These tests make drift a build failure rather than a support
 * ticket.
 */
class RolePermissionAlignmentTest {

    private static final Path CONTROLLERS =
            Path.of("src/main/java/com/giftedlabs/echoinhealthbackend/controller");
    private static final Path ROLES_DOC = Path.of("docs/ROLES.md");

    @Test
    @DisplayName("no controller spells out a role list inline")
    void annotationsUseTheSharedRoleGroups() throws IOException {
        List<String> offenders;
        try (var files = Files.list(CONTROLLERS)) {
            offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> readSafely(path).contains("@PreAuthorize(\"has"))
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        }

        assertThat(offenders)
                .as("these controllers hardcode a role list instead of referencing RoleGroups, "
                        + "which is how a role gets left out of one endpoint and returns 403")
                .isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("every role holds at least one capability")
    void everyRoleHasCapabilities(Role role) {
        assertThat(Permission.forRole(role))
                .as("%s can do nothing at all, which is almost certainly a mistake", role)
                .isNotEmpty();
    }

    @Test
    @DisplayName("SUPER_ADMIN holds every capability")
    void superAdminHoldsEverything() {
        Set<String> all = Arrays.stream(Permission.values())
                .map(Permission::getKey)
                .collect(Collectors.toSet());

        assertThat(Permission.forRole(Role.SUPER_ADMIN)).containsExactlyInAnyOrderElementsOf(all);
    }

    @Test
    @DisplayName("platform-only capabilities are withheld from hospital roles")
    void platformCapabilitiesAreNotGrantedToTenantRoles() {
        List<Permission> platformOnly = List.of(
                Permission.TENANTS_MANAGE,
                Permission.IMPERSONATE,
                Permission.USERS_DELETE,
                Permission.BILLING_MANAGE,
                Permission.SYSTEM_MAINTAIN);

        for (Permission permission : platformOnly) {
            assertThat(permission.grantedTo(Role.HOSPITAL_ADMIN))
                    .as("%s must not be granted to HOSPITAL_ADMIN", permission.getKey())
                    .isFalse();
            assertThat(permission.grantedTo(Role.SONOGRAPHER)).isFalse();
            assertThat(permission.grantedTo(Role.RADIOLOGIST)).isFalse();
            assertThat(permission.grantedTo(Role.PHYSICIAN)).isFalse();
        }
    }

    @Test
    @DisplayName("clinical roles cannot administer a hospital")
    void clinicalRolesCannotAdminister() {
        for (Role role : List.of(Role.SONOGRAPHER, Role.RADIOLOGIST, Role.PHYSICIAN)) {
            assertThat(Permission.USERS_MANAGE.grantedTo(role)).isFalse();
            assertThat(Permission.BRANDING_MANAGE.grantedTo(role)).isFalse();
            assertThat(Permission.BILLING_VIEW.grantedTo(role)).isFalse();
        }
    }

    @Test
    @DisplayName("the published matrix documents every capability")
    void rolesDocumentationCoversEveryPermission() throws IOException {
        assertThat(ROLES_DOC)
                .as("docs/ROLES.md is the answer the client reads; it must exist")
                .exists();

        String documentation = Files.readString(ROLES_DOC);
        List<String> undocumented = Arrays.stream(Permission.values())
                .map(Permission::getKey)
                .filter(key -> !documentation.contains(key))
                .toList();

        assertThat(undocumented)
                .as("these capabilities exist in code but are missing from docs/ROLES.md")
                .isEmpty();
    }

    private String readSafely(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }
}
