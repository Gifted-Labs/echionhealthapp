-- V11 — Schema convergence + DB-layer tenant isolation.
--
-- Two jobs:
--   1. Bring databases that were originally created by Hibernate ddl-auto up to the full
--      entity model. Everything here is idempotent, so a fresh database (which already got
--      the schema from V1) passes straight through.
--   2. Promote tenant isolation from an application-layer convention to a database
--      constraint: organization_id becomes NOT NULL with a real foreign key wherever the
--      entity model declares it mandatory (UR-083).

-- ---------------------------------------------------------------------------
-- 1. Tables that predate the migration chain or were added later
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id VARCHAR(36) PRIMARY KEY,
    token VARCHAR(500) NOT NULL UNIQUE,
    user_id VARCHAR(36) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id VARCHAR(36) PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id VARCHAR(36) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    verified_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS report_versions (
    id VARCHAR(36) PRIMARY KEY,
    report_id VARCHAR(36) NOT NULL,
    version_number INTEGER NOT NULL,
    report_data TEXT NOT NULL,
    changed_by_id VARCHAR(36),
    change_description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_report_version UNIQUE (report_id, version_number)
);

CREATE TABLE IF NOT EXISTS report_folders (
    report_id VARCHAR(36) NOT NULL,
    folder_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (report_id, folder_id)
);

CREATE TABLE IF NOT EXISTS shared_templates (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    template_id VARCHAR(36) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    recipient_id VARCHAR(36) NOT NULL,
    shared_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_shared_template_recipient UNIQUE (template_id, recipient_id)
);

CREATE INDEX IF NOT EXISTS idx_shared_templates_org_template
    ON shared_templates (organization_id, template_id);
CREATE INDEX IF NOT EXISTS idx_shared_templates_recipient
    ON shared_templates (recipient_id);
CREATE INDEX IF NOT EXISTS idx_shared_templates_owner
    ON shared_templates (owner_id);

-- ---------------------------------------------------------------------------
-- 2. Column convergence
-- ---------------------------------------------------------------------------
--
-- Every non-key column the entity model requires, generated from the schema a clean run of
-- this chain produces — the same schema MigrationChainPostgresTest validates Hibernate
-- against. A database created by an older Hibernate model can be missing any of these, and
-- with ddl-auto: none a missing column is a runtime failure on first query rather than a
-- startup error.
--
-- Every statement is idempotent, so an already-current database passes straight through.
-- NOT NULL is asserted only where a DEFAULT exists to backfill rows that already exist;
-- tightening the rest is left to the targeted tenant constraints in section 4.

ALTER TABLE IF EXISTS ai_generation_events
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS user_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS request_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS provider VARCHAR(40),
    ADD COLUMN IF NOT EXISTS model VARCHAR(120),
    ADD COLUMN IF NOT EXISTS prompt_version VARCHAR(40),
    ADD COLUMN IF NOT EXISTS fallback_used BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS status VARCHAR(40),
    ADD COLUMN IF NOT EXISTS input_tokens INTEGER,
    ADD COLUMN IF NOT EXISTS output_tokens INTEGER,
    ADD COLUMN IF NOT EXISTS estimated_cost_usd NUMERIC(12,6),
    ADD COLUMN IF NOT EXISTS latency_ms BIGINT,
    ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE IF EXISTS audit_logs
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS user_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS user_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS action VARCHAR(100),
    ADD COLUMN IF NOT EXISTS details VARCHAR(500),
    ADD COLUMN IF NOT EXISTS ip_address VARCHAR(45),
    ADD COLUMN IF NOT EXISTS user_agent VARCHAR(500),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS success BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS error_message VARCHAR(500);

ALTER TABLE IF EXISTS collaboration_notifications
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS recipient_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS sender_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS type VARCHAR(30),
    ADD COLUMN IF NOT EXISTS shared_scan_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS comment_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS title VARCHAR(255),
    ADD COLUMN IF NOT EXISTS message TEXT,
    ADD COLUMN IF NOT EXISTS is_read BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS read_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE IF EXISTS email_verification_tokens
    ADD COLUMN IF NOT EXISTS token VARCHAR(255),
    ADD COLUMN IF NOT EXISTS user_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS verified_at TIMESTAMP;

ALTER TABLE IF EXISTS folders
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS user_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS parent_folder_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE IF EXISTS organizations
    ADD COLUMN IF NOT EXISTS name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS subscription_tier VARCHAR(20) NOT NULL DEFAULT 'BASIC',
    ADD COLUMN IF NOT EXISTS hospital_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS address VARCHAR(500),
    ADD COLUMN IF NOT EXISTS phone VARCHAR(30),
    ADD COLUMN IF NOT EXISTS email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS website VARCHAR(255),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS addon_storage_mb INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS addon_ai_credits INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS lite_emr_integration_enabled BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS ai_credits_used_this_month INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS ai_credits_last_reset_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS letterhead_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS last_usage_alert_signature VARCHAR(120),
    ADD COLUMN IF NOT EXISTS last_usage_alert_at TIMESTAMP;

ALTER TABLE IF EXISTS refresh_tokens
    ADD COLUMN IF NOT EXISTS token VARCHAR(500),
    ADD COLUMN IF NOT EXISTS user_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_used_at TIMESTAMP;

ALTER TABLE IF EXISTS report_templates
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS user_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS gender VARCHAR(10),
    ADD COLUMN IF NOT EXISTS report_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS scan_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS default_findings TEXT,
    ADD COLUMN IF NOT EXISTS default_impression TEXT,
    ADD COLUMN IF NOT EXISTS is_default BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT true,
    ADD COLUMN IF NOT EXISTS phi_free BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS tags TEXT[],
    ADD COLUMN IF NOT EXISTS original_filename VARCHAR(500),
    ADD COLUMN IF NOT EXISTS file_path VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS usage_count INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_used_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS category VARCHAR(255),
    ADD COLUMN IF NOT EXISTS is_favorite BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS source_format VARCHAR(20),
    ADD COLUMN IF NOT EXISTS file_size BIGINT,
    ADD COLUMN IF NOT EXISTS blob_deleted BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE IF EXISTS report_versions
    ADD COLUMN IF NOT EXISTS report_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS version_number INTEGER,
    ADD COLUMN IF NOT EXISTS report_data TEXT,
    ADD COLUMN IF NOT EXISTS changed_by_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS change_description VARCHAR(500),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE IF EXISTS reports
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS user_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS patient_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS patient_age INTEGER,
    ADD COLUMN IF NOT EXISTS patient_sex VARCHAR(10),
    ADD COLUMN IF NOT EXISTS patient_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS scan_date DATE,
    ADD COLUMN IF NOT EXISTS scan_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS report_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS clinical_history TEXT,
    ADD COLUMN IF NOT EXISTS findings TEXT,
    ADD COLUMN IF NOT EXISTS impression TEXT,
    ADD COLUMN IF NOT EXISTS recommendation TEXT,
    ADD COLUMN IF NOT EXISTS structured_findings TEXT,
    ADD COLUMN IF NOT EXISTS recommendation_options TEXT[],
    ADD COLUMN IF NOT EXISTS original_filename VARCHAR(500),
    ADD COLUMN IF NOT EXISTS file_path VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS file_size BIGINT,
    ADD COLUMN IF NOT EXISTS file_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS storage_type VARCHAR(20),
    ADD COLUMN IF NOT EXISTS extracted_text TEXT,
    ADD COLUMN IF NOT EXISTS search_vector TSVECTOR,
    ADD COLUMN IF NOT EXISTS tags TEXT[],
    ADD COLUMN IF NOT EXISTS is_favorite BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS is_ai_generated BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS processing_time_seconds INTEGER,
    ADD COLUMN IF NOT EXISTS last_auto_save_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS applied_signature_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS signatory_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS signatory_designation VARCHAR(50),
    ADD COLUMN IF NOT EXISTS finalized_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS ai_output_edited BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE IF EXISTS scan_comments
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS shared_scan_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS author_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS content TEXT,
    ADD COLUMN IF NOT EXISTS annotation_data TEXT,
    ADD COLUMN IF NOT EXISTS parent_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS edited BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS is_suggested_impression BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE IF EXISTS shared_scan_access
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS shared_scan_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS user_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS viewed_at TIMESTAMP;

ALTER TABLE IF EXISTS shared_scans
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS report_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS image_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS image_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS image_storage_type VARCHAR(20),
    ADD COLUMN IF NOT EXISTS owner_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS sharing_level VARCHAR(30),
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW',
    ADD COLUMN IF NOT EXISTS title VARCHAR(255),
    ADD COLUMN IF NOT EXISTS request_message TEXT,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS urgency VARCHAR(20) DEFAULT 'MEDIUM',
    ADD COLUMN IF NOT EXISTS target_department VARCHAR(100),
    ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS resolution_notes TEXT,
    ADD COLUMN IF NOT EXISTS image_size BIGINT;

ALTER TABLE IF EXISTS shared_templates
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS template_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS owner_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS recipient_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS shared_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE IF EXISTS signatures
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS user_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS label VARCHAR(100),
    ADD COLUMN IF NOT EXISTS image_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS is_default BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE IF EXISTS template_versions
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS template_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS changed_by_user_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS version_number INTEGER,
    ADD COLUMN IF NOT EXISTS snapshot_json TEXT,
    ADD COLUMN IF NOT EXISTS change_description VARCHAR(255),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS username VARCHAR(100),
    ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255),
    ADD COLUMN IF NOT EXISTS first_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS last_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS phone VARCHAR(20),
    ADD COLUMN IF NOT EXISTS hospital_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS department VARCHAR(100),
    ADD COLUMN IF NOT EXISTS service_number VARCHAR(100),
    ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'SONOGRAPHER',
    ADD COLUMN IF NOT EXISTS designation VARCHAR(50),
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS account_locked BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS can_upload_signature BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS mfa_secret VARCHAR(255),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS profile_updated_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;

-- Backfill idle-session tracking for sessions that predate it.
UPDATE refresh_tokens SET last_used_at = created_at WHERE last_used_at IS NULL;

-- ---------------------------------------------------------------------------
-- 3. Repair dangling tenant references before constraining
-- ---------------------------------------------------------------------------

DO $$
DECLARE
    legacy_org CONSTANT VARCHAR(36) := '00000000-0000-0000-0000-000000000001';
    tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'users', 'reports', 'audit_logs', 'report_templates', 'folders',
        'shared_scans', 'shared_scan_access', 'scan_comments',
        'collaboration_notifications', 'signatures', 'template_versions',
        'shared_templates', 'ai_generation_events'
    ] LOOP
        IF to_regclass(tbl) IS NOT NULL THEN
            EXECUTE format(
                'UPDATE %I SET organization_id = %L
                 WHERE organization_id IS NOT NULL
                   AND organization_id NOT IN (SELECT id FROM organizations)',
                tbl, legacy_org);
        END IF;
    END LOOP;
END $$;

-- Backfill any remaining NULLs on tables where the entity model requires a tenant.
DO $$
DECLARE
    legacy_org CONSTANT VARCHAR(36) := '00000000-0000-0000-0000-000000000001';
    tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'reports', 'folders', 'shared_scans', 'shared_scan_access', 'scan_comments',
        'collaboration_notifications', 'signatures', 'template_versions',
        'shared_templates', 'ai_generation_events'
    ] LOOP
        IF to_regclass(tbl) IS NOT NULL THEN
            EXECUTE format(
                'UPDATE %I SET organization_id = %L WHERE organization_id IS NULL',
                tbl, legacy_org);
        END IF;
    END LOOP;
END $$;

-- ---------------------------------------------------------------------------
-- 4. Tenant isolation at the database layer (UR-083)
-- ---------------------------------------------------------------------------

-- NOT NULL wherever the entity declares organization_id mandatory.
DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'reports', 'folders', 'shared_scans', 'shared_scan_access', 'scan_comments',
        'collaboration_notifications', 'signatures', 'template_versions',
        'shared_templates', 'ai_generation_events'
    ] LOOP
        IF to_regclass(tbl) IS NOT NULL THEN
            EXECUTE format('ALTER TABLE %I ALTER COLUMN organization_id SET NOT NULL', tbl);
        END IF;
    END LOOP;
END $$;

-- Foreign keys to organizations. users / audit_logs / report_templates keep a nullable
-- organization_id (system templates and pre-tenant audit rows), but still get the FK.
DO $$
DECLARE
    tbl TEXT;
    fk_name TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'users', 'reports', 'audit_logs', 'report_templates', 'folders',
        'shared_scans', 'shared_scan_access', 'scan_comments',
        'collaboration_notifications', 'signatures', 'template_versions',
        'shared_templates', 'ai_generation_events'
    ] LOOP
        IF to_regclass(tbl) IS NOT NULL THEN
            fk_name := 'fk_' || tbl || '_organization';
            -- Skip when the column is already covered. A Hibernate-created database carries
            -- its own auto-named foreign keys from the @ManyToOne mappings; adding a second
            -- one would just duplicate the constraint.
            IF NOT EXISTS (
                SELECT 1
                FROM pg_constraint c
                JOIN pg_attribute a
                  ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
                WHERE c.conrelid = to_regclass(tbl)
                  AND c.contype = 'f'
                  AND a.attname = 'organization_id'
            ) THEN
                EXECUTE format(
                    'ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (organization_id)
                     REFERENCES organizations(id)',
                    tbl, fk_name);
            END IF;
        END IF;
    END LOOP;
END $$;

-- ---------------------------------------------------------------------------
-- 5. Full-text search maintenance
-- ---------------------------------------------------------------------------
-- search_vector is mapped read-only in the Report entity, so the database owns it.
-- Previously it was only populated by a manual script plus an admin endpoint, which
-- meant freshly written reports were invisible to full-text search until someone
-- remembered to rebuild.

CREATE OR REPLACE FUNCTION reports_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', coalesce(NEW.patient_name, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.patient_id, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.findings, '')), 'B') ||
        setweight(to_tsvector('english', coalesce(NEW.impression, '')), 'B') ||
        setweight(to_tsvector('english', coalesce(NEW.clinical_history, '')), 'C') ||
        setweight(to_tsvector('english', coalesce(NEW.recommendation, '')), 'C') ||
        setweight(to_tsvector('english', coalesce(NEW.extracted_text, '')), 'D');
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_reports_search_vector ON reports;
CREATE TRIGGER trg_reports_search_vector
    BEFORE INSERT OR UPDATE ON reports
    FOR EACH ROW EXECUTE FUNCTION reports_search_vector_update();

-- Touching the row fires the BEFORE UPDATE trigger, which populates search_vector.
UPDATE reports SET search_vector = NULL WHERE search_vector IS NULL;

CREATE INDEX IF NOT EXISTS idx_reports_search_vector ON reports USING GIN(search_vector);
CREATE INDEX IF NOT EXISTS idx_reports_scan_date ON reports (scan_date DESC NULLS LAST);
CREATE INDEX IF NOT EXISTS idx_reports_created_at ON reports (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reports_patient_name ON reports (patient_name);

-- ---------------------------------------------------------------------------
-- 6. Vault search support (UR-052 / UR-076)
-- ---------------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_templates_org_active
    ON report_templates (organization_id, is_active);
CREATE INDEX IF NOT EXISTS idx_templates_scan_type
    ON report_templates (scan_type);
CREATE INDEX IF NOT EXISTS idx_templates_last_used
    ON report_templates (last_used_at DESC NULLS LAST);
CREATE INDEX IF NOT EXISTS idx_templates_tags
    ON report_templates USING GIN(tags);
