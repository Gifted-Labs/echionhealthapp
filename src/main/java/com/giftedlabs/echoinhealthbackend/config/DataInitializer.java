package com.giftedlabs.echoinhealthbackend.config;

import com.giftedlabs.echoinhealthbackend.entity.Organization;
import com.giftedlabs.echoinhealthbackend.entity.Role;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.repository.OrganizationRepository;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(10)
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.admin.bootstrap.organization-name:Echion Platform}")
    private String bootstrapOrganizationName;

    @Value("${app.admin.bootstrap.hospital-name:Echion Health HQ}")
    private String bootstrapHospitalName;

    @Value("${app.admin.bootstrap.reset-mfa-on-startup:true}")
    private boolean resetAdminMfaOnStartup;

    @Override
    public void run(String... args) throws Exception {
        bootstrapSuperAdmin();
    }

    private void bootstrapSuperAdmin() {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("Skipping Super Admin bootstrap because ADMIN_PASSWORD is not configured.");
            return;
        }

        User superAdmin = userRepository.findByEmail(adminEmail)
                .orElseGet(() -> User.builder()
                        .email(adminEmail)
                        .firstName("Super")
                        .lastName("Admin")
                        .build());

        boolean creating = superAdmin.getId() == null;

        if (superAdmin.getFirstName() == null || superAdmin.getFirstName().isBlank()) {
            superAdmin.setFirstName("Super");
        }
        if (superAdmin.getLastName() == null || superAdmin.getLastName().isBlank()) {
            superAdmin.setLastName("Admin");
        }
        if (superAdmin.getOrganization() == null) {
            superAdmin.setOrganization(resolveBootstrapOrganization());
        }
        if (superAdmin.getHospitalName() == null || superAdmin.getHospitalName().isBlank()) {
            superAdmin.setHospitalName(bootstrapHospitalName);
        }

        superAdmin.setPasswordHash(passwordEncoder.encode(adminPassword));
        superAdmin.setRole(Role.SUPER_ADMIN);
        superAdmin.setEmailVerified(true);
        superAdmin.setAccountLocked(false);
        superAdmin.setActive(true);

        if (resetAdminMfaOnStartup) {
            superAdmin.setMfaEnabled(false);
            superAdmin.setMfaSecret(null);
        }

        userRepository.save(superAdmin);
        log.info("Super Admin user {} and ready for API login testing.", creating ? "bootstrapped" : "repaired");
    }

    private Organization resolveBootstrapOrganization() {
        return organizationRepository.findByName(bootstrapOrganizationName)
                .orElseGet(() -> organizationRepository.save(Organization.builder()
                        .name(bootstrapOrganizationName)
                        .hospitalName(bootstrapHospitalName)
                        .email(adminEmail)
                        .build()));
    }
}
