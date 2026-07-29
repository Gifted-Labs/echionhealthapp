-- Repair migration: ensure reports.organization_id exists and is backfilled
-- This handles environments where the multitenant column was not applied to reports.

INSERT INTO organizations (id, name, subscription_tier, hospital_name, email, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000001', 'Legacy Default Organization', 'BASIC', 'Legacy Default Hospital',
       'legacy-default@echionhealth.local', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM organizations WHERE id = '00000000-0000-0000-0000-000000000001'
);

ALTER TABLE IF EXISTS reports
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);

UPDATE reports r
SET organization_id = u.organization_id
FROM users u
WHERE r.user_id = u.id
  AND r.organization_id IS NULL
  AND u.organization_id IS NOT NULL;

UPDATE reports
SET organization_id = '00000000-0000-0000-0000-000000000001'
WHERE organization_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_reports_org_user ON reports (organization_id, user_id);
