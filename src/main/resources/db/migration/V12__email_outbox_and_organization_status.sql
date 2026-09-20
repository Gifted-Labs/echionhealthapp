-- V12 — Email outbox + organization lifecycle status.
--
-- Two additions backing the client-reported defects:
--
--   1. email_outbox — every outbound message and what the provider said about it. Sending was
--      previously fire-and-forget inside a swallowed try/catch, so a rejected message was
--      indistinguishable from a delivered one. Onboarding mail that never arrived left no trace
--      anywhere in the system.
--
--   2. organizations.status — tenants can be suspended and reactivated from the platform
--      console. Previously a hospital, once created, could never be turned off.
--
-- Everything here is idempotent so re-running against a partially migrated database is safe.

-- ---------------------------------------------------------------------------
-- 1. Email outbox
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS email_outbox (
    id VARCHAR(36) PRIMARY KEY,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    template_name VARCHAR(100) NOT NULL,
    organization_id VARCHAR(36),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    provider_status_code INTEGER,
    provider_message_id VARCHAR(255),
    last_error TEXT,
    last_attempt_at TIMESTAMP,
    sent_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_email_outbox_status ON email_outbox (status);
CREATE INDEX IF NOT EXISTS idx_email_outbox_created_at ON email_outbox (created_at);
CREATE INDEX IF NOT EXISTS idx_email_outbox_recipient ON email_outbox (recipient);

-- Partial index for the retry sweep, which only ever scans unfinished work.
CREATE INDEX IF NOT EXISTS idx_email_outbox_retryable
    ON email_outbox (last_attempt_at)
    WHERE status IN ('PENDING', 'RETRYING');

-- ---------------------------------------------------------------------------
-- 2. Organization lifecycle status
-- ---------------------------------------------------------------------------

ALTER TABLE organizations ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMP;
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS suspension_reason TEXT;

CREATE INDEX IF NOT EXISTS idx_organizations_status ON organizations (status);

-- The platform console lists tenants newest-first by default; without this the sort degrades
-- into a full scan as tenant count grows.
CREATE INDEX IF NOT EXISTS idx_organizations_created_at ON organizations (created_at);

-- ---------------------------------------------------------------------------
-- 3. Impersonation audit columns
-- ---------------------------------------------------------------------------
--
-- An action taken while a super admin is impersonating a clinician must never be attributed to
-- the clinician alone. Both identities are recorded so the trail stays truthful.

ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS impersonated_by_user_id VARCHAR(36);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS impersonated_by_email VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_audit_logs_impersonated_by
    ON audit_logs (impersonated_by_user_id)
    WHERE impersonated_by_user_id IS NOT NULL;
