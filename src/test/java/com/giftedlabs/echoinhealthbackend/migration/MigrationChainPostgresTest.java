package com.giftedlabs.echoinhealthbackend.migration;

import com.giftedlabs.echoinhealthbackend.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the two failure modes that made this schema unshippable:
 *
 * <ol>
 *   <li>The migration chain could not provision an empty database, because the core tables
 *       were only ever created by Hibernate {@code ddl-auto} and every migration used
 *       {@code ALTER TABLE IF EXISTS}, which silently no-ops on a fresh database.</li>
 *   <li>V8 declared UUID columns and UUID foreign keys against VARCHAR(36) primary keys,
 *       which PostgreSQL rejects outright.</li>
 * </ol>
 *
 * <p>The rest of the suite runs on H2 with {@code ddl-auto: create-drop} and Flyway
 * disabled, so it could never have caught either. This test boots the real application
 * against real PostgreSQL with Flyway on and {@code ddl-auto: validate}, which fails the
 * build if any entity disagrees with the migrated schema.
 */
class MigrationChainPostgresTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * If the context started at all, Flyway provisioned an empty database from scratch and
     * Hibernate validated every entity against it. This asserts the table inventory too, so
     * a future entity added without a migration fails loudly rather than at runtime.
     */
    @Test
    void migrationChainProvisionsEveryEntityTableFromEmpty() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);

        for (String required : List.of(
                "organizations", "users", "refresh_tokens", "email_verification_tokens",
                "reports", "report_versions", "report_templates", "template_versions",
                "shared_templates", "folders", "report_folders", "shared_scans",
                "shared_scan_access", "scan_comments", "collaboration_notifications",
                "audit_logs", "signatures", "ai_generation_events")) {
            assertTrue(tables.contains(required),
                    "table '" + required + "' missing after migrating an empty database");
        }
    }

    @Test
    void aiGenerationEventsUsesVarcharIdentifiersNotUuid() {
        for (String column : List.of("id", "organization_id", "user_id")) {
            assertEquals("character varying", columnType("ai_generation_events", column),
                    "ai_generation_events." + column
                            + " must be VARCHAR to match organizations(id)/users(id)");
        }
    }

    @Test
    void tenantColumnsAreConstrainedAtTheDatabaseLayer() {
        for (String table : List.of("reports", "folders", "shared_scans", "scan_comments",
                "collaboration_notifications", "signatures", "ai_generation_events")) {
            assertEquals("NO", isNullable(table, "organization_id"),
                    table + ".organization_id must be NOT NULL");
        }

        List<String> foreignKeys = jdbcTemplate.queryForList("""
                SELECT conname FROM pg_constraint
                WHERE contype = 'f' AND conname LIKE 'fk\\_%\\_organization'
                """, String.class);

        assertTrue(foreignKeys.size() >= 13,
                "every tenant-scoped table needs an organizations foreign key, found "
                        + foreignKeys);
    }

    @Test
    void reportSearchVectorIsMaintainedByTheDatabase() {
        jdbcTemplate.update("""
                INSERT INTO organizations (id, name, subscription_tier, hospital_name)
                VALUES ('search-org', 'Test Org', 'BASIC', 'Test Hospital')
                """);
        jdbcTemplate.update("""
                INSERT INTO users (id, organization_id, email, password_hash, first_name, last_name)
                VALUES ('search-user', 'search-org', 'search@test.local', 'x', 'Ada', 'Lovelace')
                """);
        jdbcTemplate.update("""
                INSERT INTO reports (id, organization_id, user_id, scan_date, findings)
                VALUES ('search-report', 'search-org', 'search-user', CURRENT_DATE,
                        'Hepatomegaly with coarse echotexture')
                """);

        Integer matches = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM reports
                WHERE search_vector @@ to_tsquery('english', 'hepatomegaly')
                """, Integer.class);

        assertEquals(1, matches, "search_vector must be populated by trigger on insert");
    }

    private String columnType(String table, String column) {
        return jdbcTemplate.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """, String.class, table, column);
    }

    private String isNullable(String table, String column) {
        return jdbcTemplate.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """, String.class, table, column);
    }
}
