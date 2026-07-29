-- Repair migration: ensure all user columns from V2 exist
-- This handles cases where V2 was skipped due to Flyway baselining
ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS username VARCHAR(100);
ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS designation VARCHAR(50);
ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS can_upload_signature BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS mfa_secret VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_users_org_username ON users (organization_id, username);
