package com.giftedlabs.echoinhealthbackend.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Runs {@code repair()} before {@code migrate()}.
 *
 * <p>This deployment carries historical schema drift: the core tables were originally
 * created by Hibernate {@code ddl-auto} rather than by the migration chain, and several
 * early migrations had to be corrected in place (V1 gained the missing CREATE TABLE
 * statements, V8's UUID columns were wrong for a VARCHAR(36) schema). Correcting an
 * already-applied migration changes its checksum, which Flyway rejects on the next
 * startup unless the history table is realigned first.
 *
 * <p>{@code repair()} realigns recorded checksums and clears failed migration entries.
 * It never re-executes an applied migration, so corrections to historical files only
 * take effect on databases that have not run them yet — which is exactly why V11 exists
 * to converge already-migrated databases.
 */
@Configuration
@Slf4j
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy repairBeforeMigrate() {
        return flyway -> {
            repairQuietly(flyway);
            flyway.migrate();
        };
    }

    private void repairQuietly(Flyway flyway) {
        try {
            flyway.repair();
        } catch (RuntimeException e) {
            // A repair failure on a pristine database (no history table yet) is expected
            // and harmless; migrate() below is the operation that matters.
            log.debug("Flyway repair skipped: {}", e.getMessage());
        }
    }
}
