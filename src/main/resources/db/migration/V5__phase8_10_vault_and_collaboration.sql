ALTER TABLE IF EXISTS report_templates
    ADD COLUMN IF NOT EXISTS category VARCHAR(255);
ALTER TABLE IF EXISTS report_templates
    ADD COLUMN IF NOT EXISTS is_favorite BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE IF EXISTS report_templates
    ADD COLUMN IF NOT EXISTS source_format VARCHAR(20);

CREATE TABLE IF NOT EXISTS template_versions (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    template_id VARCHAR(36) NOT NULL,
    changed_by_user_id VARCHAR(36),
    version_number INTEGER NOT NULL,
    snapshot_json TEXT NOT NULL,
    change_description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_template_versions_template ON template_versions (template_id);
CREATE INDEX IF NOT EXISTS idx_template_versions_org_created ON template_versions (organization_id, created_at);

ALTER TABLE IF EXISTS shared_scans
    ADD COLUMN IF NOT EXISTS urgency VARCHAR(20) DEFAULT 'MEDIUM';
ALTER TABLE IF EXISTS shared_scans
    ADD COLUMN IF NOT EXISTS target_department VARCHAR(100);
ALTER TABLE IF EXISTS shared_scans
    ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMP;
ALTER TABLE IF EXISTS shared_scans
    ADD COLUMN IF NOT EXISTS resolution_notes TEXT;
