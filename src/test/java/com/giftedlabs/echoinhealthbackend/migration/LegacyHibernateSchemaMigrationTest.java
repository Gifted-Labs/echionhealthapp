package com.giftedlabs.echoinhealthbackend.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The upgrade path for an already-deployed database.
 *
 * <p>Flyway had never actually run in this application, so a deployed environment has
 * Hibernate-created tables and <em>no</em> {@code flyway_schema_history}. The first startup
 * after Flyway is wired up therefore baselines at 0 and runs V1 onwards against a schema that
 * already exists — a path that {@link MigrationChainPostgresTest} (which starts from empty)
 * does not cover, and which failed in exactly this situation.
 *
 * <p>This test recreates it: Hibernate builds the schema with {@code ddl-auto: create} and
 * Flyway disabled, then the migration chain is run against that database.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
class LegacyHibernateSchemaMigrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        // Reproduce the legacy state: Hibernate owns the schema, Flyway has never run.
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationChainUpgradesAHibernateCreatedDatabase() {
        // Sanity: this is the legacy shape — tables exist, no Flyway history.
        assertTrue(jdbcTemplate.queryForList(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                        String.class).contains("organizations"),
                "precondition: Hibernate should have created the schema");
        assertTrue(jdbcTemplate.queryForList("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_name = 'flyway_schema_history'
                        """, String.class).isEmpty(),
                "precondition: no Flyway history should exist yet");

        // Hibernate just built the schema from the *current* entity model, but a real deployed
        // database was built from an older one. Drop a representative set of newer columns so
        // this exercises the convergence path rather than a no-op.
        jdbcTemplate.execute("ALTER TABLE organizations DROP COLUMN last_usage_alert_signature");
        jdbcTemplate.execute("ALTER TABLE organizations DROP COLUMN last_usage_alert_at");
        jdbcTemplate.execute("ALTER TABLE reports DROP COLUMN ai_output_edited");
        jdbcTemplate.execute("ALTER TABLE reports DROP COLUMN structured_findings");
        jdbcTemplate.execute("ALTER TABLE report_templates DROP COLUMN blob_deleted");
        jdbcTemplate.execute("ALTER TABLE refresh_tokens DROP COLUMN last_used_at");
        jdbcTemplate.execute("DROP TABLE shared_templates");

        MigrateResult result = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertTrue(result.success,
                "the migration chain must upgrade an existing Hibernate-created database");

        // Columns and tables an older schema would be missing must be restored, otherwise
        // ddl-auto: none turns them into a runtime failure on first query.
        assertColumnExists("organizations", "last_usage_alert_signature");
        assertColumnExists("organizations", "last_usage_alert_at");
        assertColumnExists("reports", "ai_output_edited");
        assertColumnExists("reports", "structured_findings");
        assertColumnExists("report_templates", "blob_deleted");
        assertColumnExists("refresh_tokens", "last_used_at");
        assertTrue(jdbcTemplate.queryForList("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_name = 'shared_templates'
                        """, String.class).size() == 1,
                "a table missing from an older schema must be recreated");

        // The convergence work in V11 must still have landed on the pre-existing tables.
        for (String table : List.of("reports", "folders", "shared_scans", "scan_comments",
                "collaboration_notifications", "signatures", "ai_generation_events")) {
            assertTrue("NO".equals(jdbcTemplate.queryForObject("""
                            SELECT is_nullable FROM information_schema.columns
                            WHERE table_schema = 'public' AND table_name = ? AND column_name = 'organization_id'
                            """, String.class, table)),
                    table + ".organization_id must be NOT NULL after upgrading a legacy database");
        }
    }

    private void assertColumnExists(String table, String column) {
        assertTrue(jdbcTemplate.queryForList("""
                        SELECT column_name FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                        """, String.class, table, column).size() == 1,
                table + "." + column + " must be restored when upgrading an older schema");
    }
}
