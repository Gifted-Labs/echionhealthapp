package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.admin.AdminUserResponse;
import com.giftedlabs.echoinhealthbackend.dto.admin.CreateUserRequest;
import com.giftedlabs.echoinhealthbackend.dto.platform.*;
import com.giftedlabs.echoinhealthbackend.entity.*;
import com.giftedlabs.echoinhealthbackend.exception.AccessDeniedException;
import com.giftedlabs.echoinhealthbackend.exception.BusinessException;
import com.giftedlabs.echoinhealthbackend.exception.SubscriptionLimitExceededException;
import com.giftedlabs.echoinhealthbackend.repository.OrganizationRepository;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import com.giftedlabs.echoinhealthbackend.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The super admin control plane: tenant visibility, cross-tenant user creation, and impersonation.
 *
 * <p>Covers three client-reported defects at once. A super admin could not create a user at all —
 * the new user inherited the acting admin's own organization, which is the bootstrap platform
 * tenant, so a hospital could never be the destination. A newly onboarded hospital appeared in no
 * list, because no endpoint returned organizations. And there was no way to act as a user in order
 * to test a feature.
 */
class PlatformControlPlanePostgresTest extends PostgresIntegrationTest {

    @Autowired
    private PlatformService platformService;
    @Autowired
    private AdminService adminService;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ImpersonationSessionRegistry impersonationSessionRegistry;

    private Organization platformOrganization;
    private Organization hospital;
    private User superAdmin;
    private User hospitalAdmin;
    private User sonographer;
    private String suffix;

    @BeforeEach
    void seed() {
        suffix = UUID.randomUUID().toString().substring(0, 8);

        platformOrganization = organizationRepository.save(Organization.builder()
                .name("Echion Platform " + suffix)
                .hospitalName("Echion HQ")
                .subscriptionTier(SubscriptionTier.BASIC)
                .build());

        hospital = organizationRepository.save(Organization.builder()
                .name("St Mary " + suffix)
                .hospitalName("St Mary Hospital")
                .subscriptionTier(SubscriptionTier.PRO)
                .build());

        superAdmin = userRepository.save(
                user(platformOrganization, "super-" + suffix, Role.SUPER_ADMIN));
        hospitalAdmin = userRepository.save(user(hospital, "hadmin-" + suffix, Role.HOSPITAL_ADMIN));
        sonographer = userRepository.save(user(hospital, "sono-" + suffix, Role.SONOGRAPHER));
    }

    // ------------------------------------------------------------ cross-tenant user creation

    @Test
    @DisplayName("super admin creates a user inside a named hospital, not the platform tenant")
    void superAdminCreatesUserIntoNamedHospital() {
        AdminUserResponse created = adminService.createUser(CreateUserRequest.builder()
                .organizationId(hospital.getId())
                .firstName("Grace")
                .lastName("Adjei")
                .email("grace-" + suffix + "@stmary.test")
                .password("Str0ng!Pass1")
                .role(Role.RADIOLOGIST)
                .build(), superAdmin);

        User persisted = userRepository.findById(created.getId()).orElseThrow();

        assertThat(persisted.getOrganizationId()).isEqualTo(hospital.getId());
        assertThat(persisted.getOrganizationId()).isNotEqualTo(platformOrganization.getId());
        assertThat(persisted.getHospitalName()).isEqualTo("St Mary Hospital");
    }

    @Test
    @DisplayName("super admin omitting the organization gets a clear 400, not a silent misplacement")
    void superAdminMustNameTheOrganization() {
        assertThatThrownBy(() -> adminService.createUser(CreateUserRequest.builder()
                .firstName("Nameless")
                .lastName("User")
                .email("nameless-" + suffix + "@test.local")
                .password("Str0ng!Pass1")
                .role(Role.SONOGRAPHER)
                .build(), superAdmin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("organizationId is required");
    }

    @Test
    @DisplayName("hospital admin cannot place a user in another hospital")
    void hospitalAdminCannotCrossTenantBoundary() {
        assertThatThrownBy(() -> adminService.createUser(CreateUserRequest.builder()
                .organizationId(platformOrganization.getId())
                .firstName("Sneaky")
                .lastName("User")
                .email("sneaky-" + suffix + "@test.local")
                .password("Str0ng!Pass1")
                .role(Role.SONOGRAPHER)
                .build(), hospitalAdmin))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("your own organization");
    }

    @Test
    @DisplayName("platform roles do not consume a tenant's seats")
    void platformRolesBypassTheSeatCap() {
        // Fill the BASIC platform tenant to its five-seat cap.
        for (int i = userRepository.findByOrganizationIdAndRole(platformOrganization.getId(),
                Role.SUPER_ADMIN).size(); i < 5; i++) {
            userRepository.save(user(platformOrganization, "filler-" + suffix + "-" + i, Role.ADMIN));
        }

        AdminUserResponse created = adminService.createUser(CreateUserRequest.builder()
                .firstName("Platform")
                .lastName("Staff")
                .email("staff-" + suffix + "@echion.test")
                .password("Str0ng!Pass1")
                .role(Role.ADMIN)
                .build(), superAdmin);

        assertThat(created.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("a tenant's seat cap still applies to clinical roles")
    void seatCapStillAppliesToTenantRoles() {
        Organization small = organizationRepository.save(Organization.builder()
                .name("Small Clinic " + suffix)
                .hospitalName("Small Clinic")
                .subscriptionTier(SubscriptionTier.BASIC)
                .build());

        for (int i = 0; i < SubscriptionTier.BASIC.getMaxUsers(); i++) {
            userRepository.save(user(small, "seat-" + suffix + "-" + i, Role.SONOGRAPHER));
        }

        assertThatThrownBy(() -> adminService.createUser(CreateUserRequest.builder()
                .organizationId(small.getId())
                .firstName("One")
                .lastName("TooMany")
                .email("toomany-" + suffix + "@test.local")
                .password("Str0ng!Pass1")
                .role(Role.SONOGRAPHER)
                .build(), superAdmin))
                .isInstanceOf(SubscriptionLimitExceededException.class);
    }

    // ------------------------------------------------------------------ organization list

    @Test
    @DisplayName("a newly provisioned hospital appears in the organization list")
    void newlyProvisionedHospitalIsListed() {
        OrganizationSummaryResponse created = platformService.createOrganization(
                CreateOrganizationRequest.builder()
                        .name("Freshly Onboarded " + suffix)
                        .hospitalName("Freshly Onboarded Hospital")
                        .adminFirstName("Ama")
                        .adminLastName("Mensah")
                        .adminEmail("ama-" + suffix + "@fresh.test")
                        .adminPassword("Str0ng!Pass1")
                        .build(),
                superAdmin);

        assertThat(created.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        assertThat(created.getUserCount()).isEqualTo(1);

        var found = platformService.listOrganizations("Freshly Onboarded", null, null,
                PageRequest.of(0, 20));

        assertThat(found.getContent())
                .extracting(OrganizationSummaryResponse::getId)
                .contains(created.getId());
    }

    @Test
    @DisplayName("organization search filters by name and by status")
    void organizationSearchFilters() {
        assertThat(platformService.listOrganizations("St Mary", null, null, PageRequest.of(0, 20))
                .getContent())
                .extracting(OrganizationSummaryResponse::getId)
                .contains(hospital.getId());

        assertThat(platformService.listOrganizations(null, null, OrganizationStatus.SUSPENDED,
                PageRequest.of(0, 20)).getContent())
                .extracting(OrganizationSummaryResponse::getId)
                .doesNotContain(hospital.getId());
    }

    @Test
    @DisplayName("suspension is recorded and reversible")
    void suspendAndReactivate() {
        OrganizationSummaryResponse suspended = platformService.updateStatus(hospital.getId(),
                UpdateOrganizationStatusRequest.builder()
                        .status(OrganizationStatus.SUSPENDED)
                        .reason("Non-payment")
                        .build(),
                superAdmin);

        assertThat(suspended.getStatus()).isEqualTo(OrganizationStatus.SUSPENDED);
        assertThat(suspended.getSuspensionReason()).isEqualTo("Non-payment");
        assertThat(suspended.getSuspendedAt()).isNotNull();

        OrganizationSummaryResponse restored = platformService.updateStatus(hospital.getId(),
                UpdateOrganizationStatusRequest.builder()
                        .status(OrganizationStatus.ACTIVE)
                        .build(),
                superAdmin);

        assertThat(restored.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        assertThat(restored.getSuspendedAt()).isNull();
        assertThat(restored.getSuspensionReason()).isNull();
    }

    @Test
    @DisplayName("suspending requires a reason")
    void suspensionRequiresAReason() {
        assertThatThrownBy(() -> platformService.updateStatus(hospital.getId(),
                UpdateOrganizationStatusRequest.builder()
                        .status(OrganizationStatus.SUSPENDED)
                        .build(),
                superAdmin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason is required");
    }

    // ---------------------------------------------------------------------- impersonation

    @Test
    @DisplayName("impersonation issues a live, time-limited session naming both identities")
    void impersonationIssuesALiveSession() {
        ImpersonationResponse session = platformService.startImpersonation(
                sonographer.getId(),
                ImpersonationRequest.builder().reason("Reproducing a reported bug").build(),
                superAdmin);

        assertThat(session.getAccessToken()).isNotBlank();
        assertThat(session.getTargetUserEmail()).isEqualTo(sonographer.getEmail());
        assertThat(session.getTargetOrganizationId()).isEqualTo(hospital.getId());
        assertThat(session.getImpersonatorEmail()).isEqualTo(superAdmin.getEmail());
        assertThat(session.getExpiresInSeconds()).isPositive();
        assertThat(impersonationSessionRegistry.isActive(session.getImpersonationId())).isTrue();
    }

    @Test
    @DisplayName("stopping a session invalidates it immediately")
    void stoppingASessionInvalidatesIt() {
        ImpersonationResponse session = platformService.startImpersonation(
                sonographer.getId(),
                ImpersonationRequest.builder().reason("Checking the export flow").build(),
                superAdmin);

        platformService.stopImpersonation(session.getImpersonationId(), superAdmin.getEmail());

        assertThat(impersonationSessionRegistry.isActive(session.getImpersonationId())).isFalse();
    }

    @Test
    @DisplayName("a super admin cannot be impersonated")
    void superAdminCannotBeImpersonated() {
        User anotherSuperAdmin = userRepository.save(
                user(platformOrganization, "super2-" + suffix, Role.SUPER_ADMIN));

        assertThatThrownBy(() -> platformService.startImpersonation(
                anotherSuperAdmin.getId(),
                ImpersonationRequest.builder().reason("Should not be permitted").build(),
                superAdmin))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("cannot be impersonated");
    }

    @Test
    @DisplayName("locked and deactivated accounts cannot be impersonated")
    void lockedOrInactiveAccountsCannotBeImpersonated() {
        User locked = userRepository.save(user(hospital, "locked-" + suffix, Role.SONOGRAPHER));
        locked.setAccountLocked(true);
        userRepository.save(locked);

        assertThatThrownBy(() -> platformService.startImpersonation(locked.getId(),
                ImpersonationRequest.builder().reason("Investigating lockout").build(), superAdmin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("locked");

        User inactive = userRepository.save(user(hospital, "inactive-" + suffix, Role.SONOGRAPHER));
        inactive.setActive(false);
        userRepository.save(inactive);

        assertThatThrownBy(() -> platformService.startImpersonation(inactive.getId(),
                ImpersonationRequest.builder().reason("Investigating deactivation").build(), superAdmin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("deactivated");
    }

    // --------------------------------------------------------------------------- overview

    @Test
    @DisplayName("the overview reports tenants, users and capacity across the platform")
    void overviewAggregatesAcrossTenants() {
        PlatformOverviewResponse overview = platformService.getOverview();

        assertThat(overview.getTotalOrganizations()).isGreaterThanOrEqualTo(2);
        assertThat(overview.getTotalUsers()).isGreaterThanOrEqualTo(3);
        assertThat(overview.getUsersByRole()).containsKey(Role.SONOGRAPHER.name());
        assertThat(overview.getOrganizationsByTier()).containsKey(SubscriptionTier.PRO.name());
        assertThat(overview.getStorageAllocatedMb()).isPositive();
        assertThat(overview.getQuotaAlerts()).isNotNull();
    }

    // --------------------------------------------------------------------------- helpers

    private User user(Organization organization, String handle, Role role) {
        return User.builder()
                .organization(organization)
                .email(handle + "@test.local")
                .passwordHash("hash")
                .firstName("Test")
                .lastName(handle)
                .hospitalName(organization.getHospitalName())
                .role(role)
                .emailVerified(true)
                .accountLocked(false)
                .active(true)
                .build();
    }
}
