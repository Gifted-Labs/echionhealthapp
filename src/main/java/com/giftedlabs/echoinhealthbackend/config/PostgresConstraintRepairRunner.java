package com.giftedlabs.echoinhealthbackend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
@RequiredArgsConstructor
@Order(0)
@Slf4j
public class PostgresConstraintRepairRunner implements CommandLineRunner {

    private static final String REPAIR_USERS_ROLE_CONSTRAINT = """
            ALTER TABLE IF EXISTS users
                DROP CONSTRAINT IF EXISTS users_role_check;

            ALTER TABLE IF EXISTS users
                ADD CONSTRAINT users_role_check
                CHECK (role IN ('HOSPITAL_ADMIN', 'SONOGRAPHER', 'RADIOLOGIST', 'PHYSICIAN', 'ADMIN', 'SUPER_ADMIN'));
            """;

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String databaseProductName = connection.getMetaData().getDatabaseProductName();
            if (!"PostgreSQL".equalsIgnoreCase(databaseProductName)) {
                return;
            }
        }

        jdbcTemplate.execute(REPAIR_USERS_ROLE_CONSTRAINT);
        log.info("Repaired PostgreSQL users_role_check constraint to match application roles.");
    }
}
