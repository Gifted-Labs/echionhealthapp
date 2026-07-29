ALTER TABLE IF EXISTS organizations
    ADD COLUMN IF NOT EXISTS addon_storage_mb INTEGER NOT NULL DEFAULT 0;
ALTER TABLE IF EXISTS organizations
    ADD COLUMN IF NOT EXISTS addon_ai_credits INTEGER NOT NULL DEFAULT 0;
ALTER TABLE IF EXISTS organizations
    ADD COLUMN IF NOT EXISTS lite_emr_integration_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE IF EXISTS organizations
    ADD COLUMN IF NOT EXISTS ai_credits_used_this_month INTEGER NOT NULL DEFAULT 0;
ALTER TABLE IF EXISTS organizations
    ADD COLUMN IF NOT EXISTS ai_credits_last_reset_at TIMESTAMP;

UPDATE organizations
SET ai_credits_last_reset_at = CURRENT_TIMESTAMP
WHERE ai_credits_last_reset_at IS NULL;

ALTER TABLE IF EXISTS report_templates
    ADD COLUMN IF NOT EXISTS file_size BIGINT;

ALTER TABLE IF EXISTS shared_scans
    ADD COLUMN IF NOT EXISTS image_size BIGINT;
