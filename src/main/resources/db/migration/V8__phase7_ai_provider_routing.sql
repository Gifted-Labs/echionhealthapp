-- Phase 7 — AI provider routing telemetry.
--
-- NOTE: identifiers across this schema are VARCHAR(36) (Hibernate maps a String id with
-- GenerationType.UUID to varchar). An earlier revision of this file declared UUID columns
-- and UUID foreign keys, which PostgreSQL rejects against varchar primary keys
-- ("key columns are of incompatible types"). Types below match the rest of the schema.
-- Foreign keys are added in V11 once every referenced table is guaranteed to exist.

CREATE TABLE IF NOT EXISTS ai_generation_events (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    request_type VARCHAR(40) NOT NULL,
    provider VARCHAR(40),
    model VARCHAR(120),
    prompt_version VARCHAR(40),
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(40) NOT NULL,
    input_tokens INTEGER,
    output_tokens INTEGER,
    estimated_cost_usd NUMERIC(12, 6),
    latency_ms BIGINT,
    failure_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_events_org_created
    ON ai_generation_events(organization_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_events_user_created
    ON ai_generation_events(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_events_status
    ON ai_generation_events(status);
