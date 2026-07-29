-- Phase 1 — Multi-tenant foundation.
--
-- This migration is the schema baseline. Historically the core tables were created by
-- Hibernate ddl-auto, which meant a fresh database could never be provisioned from the
-- migration chain alone. Every CREATE below is IF NOT EXISTS so that:
--   * a fresh database gets the complete core schema here, and
--   * an existing (Hibernate-created) database is left untouched and simply proceeds.
-- Column-level convergence for existing databases is handled in V11.

-- ---------------------------------------------------------------------------
-- Tenancy root
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS organizations (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    subscription_tier VARCHAR(20) NOT NULL DEFAULT 'BASIC',
    hospital_name VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    phone VARCHAR(30),
    email VARCHAR(255),
    website VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_organizations_name ON organizations (name);

-- Normalize the billing columns before anything inserts into organizations.
--
-- On a database whose tables were created by Hibernate rather than by this chain, these
-- columns already exist as NOT NULL *without* a DEFAULT (Hibernate does not emit DEFAULT for
-- @Builder.Default). Any INSERT written before those columns existed — such as the legacy-org
-- row below, and the identical one in V10 — then fails with a not-null violation. Adding the
-- columns if missing and pinning their defaults either way makes both inserts safe on a fresh
-- database and on a Hibernate-created one. V6 re-asserts these and harmlessly no-ops.
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS addon_storage_mb INTEGER NOT NULL DEFAULT 0;
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS addon_ai_credits INTEGER NOT NULL DEFAULT 0;
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS lite_emr_integration_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS ai_credits_used_this_month INTEGER NOT NULL DEFAULT 0;
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS ai_credits_last_reset_at TIMESTAMP;

ALTER TABLE organizations ALTER COLUMN addon_storage_mb SET DEFAULT 0;
ALTER TABLE organizations ALTER COLUMN addon_ai_credits SET DEFAULT 0;
ALTER TABLE organizations ALTER COLUMN lite_emr_integration_enabled SET DEFAULT FALSE;
ALTER TABLE organizations ALTER COLUMN ai_credits_used_this_month SET DEFAULT 0;

INSERT INTO organizations (id, name, subscription_tier, hospital_name, email, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000001', 'Legacy Default Organization', 'BASIC', 'Legacy Default Hospital',
       'legacy-default@echionhealth.local', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM organizations WHERE id = '00000000-0000-0000-0000-000000000001'
);

-- ---------------------------------------------------------------------------
-- Identity
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36),
    email VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(100),
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    hospital_name VARCHAR(255),
    department VARCHAR(100),
    service_number VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'SONOGRAPHER',
    designation VARCHAR(50),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    account_locked BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    can_upload_signature BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_secret VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    profile_updated_at TIMESTAMP,
    last_login_at TIMESTAMP
);

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

-- ---------------------------------------------------------------------------
-- Reporting core
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS reports (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36),
    user_id VARCHAR(36) NOT NULL,
    patient_name VARCHAR(255),
    patient_age INTEGER,
    patient_sex VARCHAR(10),
    patient_id VARCHAR(100),
    scan_date DATE NOT NULL,
    scan_type VARCHAR(100),
    report_type VARCHAR(100),
    clinical_history TEXT,
    findings TEXT NOT NULL,
    impression TEXT,
    recommendation TEXT,
    structured_findings TEXT,
    recommendation_options TEXT[],
    original_filename VARCHAR(500),
    file_path VARCHAR(1000),
    file_size BIGINT,
    file_type VARCHAR(50),
    storage_type VARCHAR(20),
    extracted_text TEXT,
    search_vector tsvector,
    tags TEXT[],
    is_favorite BOOLEAN DEFAULT FALSE,
    is_ai_generated BOOLEAN DEFAULT FALSE,
    status VARCHAR(20),
    processing_time_seconds INTEGER,
    last_auto_save_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
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

CREATE TABLE IF NOT EXISTS report_templates (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36),
    user_id VARCHAR(36),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    gender VARCHAR(10),
    report_type VARCHAR(100),
    scan_type VARCHAR(100),
    default_findings TEXT,
    default_impression TEXT,
    is_default BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    phi_free BOOLEAN DEFAULT FALSE,
    tags TEXT[],
    original_filename VARCHAR(500),
    file_path VARCHAR(1000),
    usage_count INTEGER DEFAULT 0,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS folders (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36),
    user_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    parent_folder_id VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_folder_user_name_parent UNIQUE (user_id, name, parent_folder_id)
);

CREATE TABLE IF NOT EXISTS report_folders (
    report_id VARCHAR(36) NOT NULL,
    folder_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (report_id, folder_id)
);

-- ---------------------------------------------------------------------------
-- Collaboration (SonoShare)
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS shared_scans (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36),
    report_id VARCHAR(36),
    image_url VARCHAR(1000),
    image_name VARCHAR(255),
    image_storage_type VARCHAR(20),
    owner_id VARCHAR(36) NOT NULL,
    sharing_level VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW',
    title VARCHAR(255),
    request_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS shared_scan_access (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36),
    shared_scan_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    viewed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS scan_comments (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36),
    shared_scan_id VARCHAR(36) NOT NULL,
    author_id VARCHAR(36) NOT NULL,
    content TEXT NOT NULL,
    annotation_data TEXT,
    parent_id VARCHAR(36),
    edited BOOLEAN NOT NULL DEFAULT FALSE,
    is_suggested_impression BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS collaboration_notifications (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36),
    recipient_id VARCHAR(36) NOT NULL,
    sender_id VARCHAR(36),
    type VARCHAR(30) NOT NULL,
    shared_scan_id VARCHAR(36),
    comment_id VARCHAR(36),
    title VARCHAR(255) NOT NULL,
    message TEXT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- ---------------------------------------------------------------------------
-- Audit
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36),
    user_id VARCHAR(36),
    user_email VARCHAR(255),
    action VARCHAR(100) NOT NULL,
    details VARCHAR(500),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    error_message VARCHAR(500)
);

-- ---------------------------------------------------------------------------
-- Tenant backfill for pre-existing rows
-- ---------------------------------------------------------------------------

ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
ALTER TABLE IF EXISTS reports
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
ALTER TABLE IF EXISTS audit_logs
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
ALTER TABLE IF EXISTS report_templates
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
ALTER TABLE IF EXISTS folders
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
ALTER TABLE IF EXISTS shared_scans
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
ALTER TABLE IF EXISTS shared_scan_access
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
ALTER TABLE IF EXISTS scan_comments
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
ALTER TABLE IF EXISTS collaboration_notifications
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);

UPDATE users SET organization_id = '00000000-0000-0000-0000-000000000001'
WHERE organization_id IS NULL;
UPDATE reports SET organization_id = '00000000-0000-0000-0000-000000000001'
WHERE organization_id IS NULL;
UPDATE audit_logs SET organization_id = '00000000-0000-0000-0000-000000000001'
WHERE organization_id IS NULL;
UPDATE report_templates SET organization_id = '00000000-0000-0000-0000-000000000001'
WHERE organization_id IS NULL AND user_id IS NOT NULL;
UPDATE folders SET organization_id = '00000000-0000-0000-0000-000000000001'
WHERE organization_id IS NULL;
UPDATE shared_scans SET organization_id = '00000000-0000-0000-0000-000000000001'
WHERE organization_id IS NULL;
UPDATE shared_scan_access SET organization_id = '00000000-0000-0000-0000-000000000001'
WHERE organization_id IS NULL;
UPDATE scan_comments SET organization_id = '00000000-0000-0000-0000-000000000001'
WHERE organization_id IS NULL;
UPDATE collaboration_notifications SET organization_id = '00000000-0000-0000-0000-000000000001'
WHERE organization_id IS NULL;

-- ---------------------------------------------------------------------------
-- Tenant-leading composite indexes (UR-097)
-- ---------------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_users_org_role ON users (organization_id, role);
CREATE INDEX IF NOT EXISTS idx_reports_org_user ON reports (organization_id, user_id);
CREATE INDEX IF NOT EXISTS idx_audit_org_created ON audit_logs (organization_id, created_at);
CREATE INDEX IF NOT EXISTS idx_templates_org_user ON report_templates (organization_id, user_id);
CREATE INDEX IF NOT EXISTS idx_folders_org_user ON folders (organization_id, user_id);
CREATE INDEX IF NOT EXISTS idx_shared_scans_org_owner ON shared_scans (organization_id, owner_id);
CREATE INDEX IF NOT EXISTS idx_access_org_scan_user ON shared_scan_access (organization_id, shared_scan_id, user_id);
CREATE INDEX IF NOT EXISTS idx_comments_org_scan ON scan_comments (organization_id, shared_scan_id);
CREATE INDEX IF NOT EXISTS idx_notif_org_recipient ON collaboration_notifications (organization_id, recipient_id);
