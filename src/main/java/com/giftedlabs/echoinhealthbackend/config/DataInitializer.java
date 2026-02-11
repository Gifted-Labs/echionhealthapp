package com.giftedlabs.echoinhealthbackend.config;

import com.giftedlabs.echoinhealthbackend.entity.Role;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Override
    public void run(String... args) throws Exception {
        createSuperAdminIfNotFound();
    }

    private void createSuperAdminIfNotFound() {
        if (userRepository.findByEmail(adminEmail).isPresent()) {
            log.info("Super Admin user already exists.");
            return;
        }

        log.info("Creating Super Admin user...");

        User superAdmin = User.builder()
                .firstName("Super")
                .lastName("Admin")
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(Role.SUPER_ADMIN)
                .emailVerified(true)
                .accountLocked(false)
                .build();

        userRepository.save(superAdmin);
        log.info("Super Admin user created successfully.");
    }
}
