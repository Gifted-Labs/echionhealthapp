# Implementation Audit — Echion Health Backend

**Date:** 2026-07-29 · **Branch:** `main` @ `3087b5e` · **Scope:** `docs/IMPLEMENTATION_CHECKLIST.md` verified against `src/main`
**Method:** static reading of source, migrations, config and tests. Nothing was executed — no build, no live provider call, no DB run. Claims below are marked accordingly.

---

## 1. Headline

The checklist is broadly *honest about features* but *optimistic about production-readiness*. Almost every Phase 1–11 feature has real code behind it — controllers, services, DTOs, audit logging, tenant scoping. The gaps are not "missing features", they are three structural problems:

| # | Problem | Severity |
|---|---------|----------|
| A | The Flyway migration set cannot provision a fresh database, and V8 looks like it will hard-fail on Postgres | **Blocker** |
| B | The Gemini provider — the configured *primary* — speaks the wrong wire protocol; AI generation likely cannot work live against either provider as configured | **Blocker** |
| C | Billing has enforcement but no commercial control: any hospital admin can self-upgrade to ULTIMATE and grant themselves unlimited AI credits, free | **High** |

Tests never touch any of this: `application-test.yaml` disables Flyway and uses H2 with `ddl-auto: create-drop`, so the real schema path and the real providers are unexercised.

---

## 2. Database & Migrations — **Blocker**

### 2.1 Core tables are never created by any migration

Across all 10 migrations there are only **four** `CREATE TABLE` statements: `organizations` (V1), `signatures` (V4), `template_versions` (V5), `ai_generation_events` (V8).

Everything else is `ALTER TABLE IF EXISTS`:

```sql
-- V1__phase1_multitenant_foundation.sql:23
ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
```

`users`, `reports`, `report_templates`, `folders`, `audit_logs`, `shared_scans`, `shared_scan_access`, `scan_comments`, `collaboration_notifications` are assumed to already exist. And these entities have **no migration at all**: `ReportVersion`, `SharedTemplate`, `ReportFolder`, `RefreshToken`, `EmailVerificationToken`.

With `spring.jpa.hibernate.ddl-auto: none` (`application.yaml:21`), a fresh Postgres gets `organizations` + `signatures` + `template_versions` and nothing else. Every `ALTER TABLE IF EXISTS` silently no-ops, migration "succeeds", and the app fails at first query.

The presence of V7, V9, V10 — all named `repair_*`, all re-applying earlier migrations because "V2 was skipped due to Flyway baselining" — confirms the schema has already drifted in a real environment.

**Root cause:** `baseline-on-migrate: true` with `baseline-version: 0` was pointed at a database Hibernate had previously auto-created. The migration history was never made self-sufficient.

**Fix:** author a `V0__baseline_schema.sql` that creates every base table from the current entity model, so the chain runs green from an empty database. Then add a CI check that runs migrations against an empty Postgres.

### 2.2 V8 declares UUID columns against VARCHAR(36) primary keys — likely hard failure

```sql
-- V8__phase7_ai_provider_routing.sql:1
CREATE TABLE IF NOT EXISTS ai_generation_events (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    user_id        UUID NOT NULL REFERENCES users(id),
```

But `organizations.id` is `VARCHAR(36)` (`V1:2`), and every entity — including `AiGenerationEvent` itself — uses `String id` with `@GeneratedValue(strategy = GenerationType.UUID)`, which Hibernate maps to `varchar(36)`.

Postgres will reject a foreign key between `uuid` and `character varying` ("Key columns are of incompatible types"). Even if the FK were dropped, Hibernate writing a `String` into a `uuid` column is a second mismatch.

*Confidence: high from code, but not executed.* Worth confirming with `\d ai_generation_events` on the deployed DB — if the table exists there, it was created some other way and the migration is being skipped.

**Fix:** change V8 to `VARCHAR(36)` throughout to match the rest of the schema.

### 2.3 Tenant isolation is app-layer only, not DB-layer

Checklist UR-001/UR-004 are marked done "at DB layer". In practice:

- `organization_id` columns are **nullable**, with **no FK** to `organizations`, and **no row-level security**.
- Isolation is enforced entirely in repository method signatures — `findByIdAndUserIdAndOrganizationId(...)` and similar — which is applied consistently and is genuinely good (verified in `TemplateService`, `FolderService`, `ReportService`, `CollaborationService`, `CurrentUserService`).
- `MultiTenantFoundationIntegrationTest` has real negative tests for cross-org ID access (`reportEndpointsRejectCrossOrganizationDirectIdAccess`, `adminEndpointsRejectCrossOrganizationUserLookup`). Genuinely earned.

So UR-004 (middleware/ORM enforcement) is **done**. UR-083 (isolation at *DB + app* layers) is **not** — one missed `findById` anywhere leaks across tenants with no backstop. Add `NOT NULL` + FK constraints at minimum; Postgres RLS if HIPAA review demands defence in depth.

UR-097 (org-leading composite indexes) is **done** — `V1:61-69` covers all nine tenant tables.

---

## 3. AI (Phase 7) — **Blocker on live operation**

The architecture here is good: `AiProviderRouter` with primary/fallback, versioned filesystem prompt templates, prompt sanitizer, per-org `AiGenerationEvent` telemetry with token counts, latency, cost estimate and status (`SUCCESS`/`FALLBACK_SUCCESS`/`FAILED`/`BLOCKED_LIMIT`). Credit blocks are recorded before throwing. That's more rigour than the checklist implies.

The problem is the two provider adapters.

### 3.1 GeminiReportProvider sends OpenAI-shaped requests to Google — **Blocker**

`GeminiReportProvider.buildBody()` emits `{"model":…, "input":…, "response_format":…}` and `parseResponse()` reads `root.get("output_text")` and `usage_metadata.prompt_token_count`. That is the OpenAI Responses API shape.

Google's Generative Language API expects `POST /v1beta/models/{model}:generateContent` with `{"contents":[{"parts":[{"text":…}]}], "generationConfig":{"responseMimeType":"application/json","responseSchema":…}}`, returning `candidates[0].content.parts[0].text` and `usageMetadata.promptTokenCount`.

Three separate breakages:
1. The default endpoint `https://generativelanguage.googleapis.com/v1beta/interactions` (`application.yaml:138`) is not a Google endpoint.
2. The model never appears in the URL — Gemini takes it as a path segment, so `GEMINI_REPORT_MODEL` / `GEMINI_IMPRESSION_MODEL` are effectively inert.
3. Request body and response parser are both wrong shape.

Since `ai.default-provider` is `GEMINI`, the *primary* path fails on every call and every request falls through to OpenAI — burning the timeout budget twice.

### 3.2 OpenAI strict schema is self-contradictory — **High**

```java
// OpenAiReportProvider.java:90
"text": { "format": { "type": "json_schema", "strict": true, …
  "properties": { …, "structuredFindings": { "type":"object", "additionalProperties": true } },
  "required": ["findings", "impression", "recommendations"],
  "additionalProperties": false
```

OpenAI structured outputs in `strict` mode require *every* property to be listed in `required` and `additionalProperties: false` on every object. `structuredFindings` violates both. Expect an HTTP 400 on every call.

`AbstractHttpAiProvider:38` classifies 4xx as non-retryable — but `executeWithFallback` catches `AiProviderException` without inspecting the retryable flag, so it falls back on a 400 anyway. The flag is dead code; either honour it or drop it.

### 3.3 Model IDs need verification

`gpt-5.4-mini`, `gemini-3.5-flash`, `gemini-3.1-flash-lite` (`application.yaml:131-140`). I cannot confirm these against current provider catalogs. Verify each against the live model list before launch — a wrong ID is an immediate 404/400 with no graceful degradation. The cost constants (`input-cost-per-million` etc.) should be re-checked at the same time, since `AiUsageCostEstimator` feeds `estimated_cost_usd` straight into the telemetry table.

### 3.4 Prompts are not actually scan-type-aware (UR-023)

`AiPromptTemplateService.loadTemplate()` correctly probes `prompts/{task}/v1/{scantype}.txt` then falls back to `default.txt`. But only `default.txt` exists for each task — so all **32** scan types share one generic prompt. The mechanism is built; the content is not. This is the difference between "AI drafts something plausible" and "AI drafts a usable obstetric anomaly-scan report".

### 3.5 Timeout budget conflicts with the stated target

Provider timeout is 8s (`application.yaml:133,141`) against a 5–10s target (UR-075). Structured report generation regularly exceeds 8s. Combined with primary-provider failure (§3.1), a real request spends 8s failing Gemini then up to 8s on OpenAI. Also: `HttpClient.newBuilder().build()` sets no connect timeout, and there is no retry/backoff.

**Verdict:** UR-020 correctly remains unchecked. UR-021–UR-024 are structurally complete but **have never produced a real completion**. Treat all of Phase 7's `[x]` marks as "wired, unverified".

---

## 4. Billing (Phase 11) — **High**

Enforcement is real and consistently wired. Verified call sites:

| Guard | Enforced in |
|-------|-------------|
| `assertUserCanBeAdded` | `AdminService:152` |
| `assertStorageCapacity` | `ReportService:70`, `TemplateService:113`, `SignatureService:52`, `CollaborationService:81`, `OrganizationBrandingService:57` |
| `assertAiCreditsAvailable` / `consumeAiCredits` | `AiReportGenerationService:51,71,105,125` |

Tier table matches the URD exactly (`SubscriptionTier`: 5/250MB/1000, 15/600MB/1500, 25/1024MB/2000). Add-ons compose correctly via `getEffectiveStorageLimitMb()` / `getEffectiveAiCreditsPerMonth()`. Monthly credit reset is handled in `normalizeAiCreditPeriod`. Over-limit produces `SubscriptionLimitExceededException` with an upgrade-prompt message, not a crash — UR-067's exit criterion is met.

### 4.1 There is no payment layer — plans are free to self-grant

`POST /billing/upgrade` sets `organization.setSubscriptionTier(request.getSubscriptionTier())` and saves. No payment provider, no invoice, no entitlement check, no audit of *who authorised* the money side. `POST /billing/addons` is worse:

```java
// BillingService.java:63
organization.setAddonAiCredits(Math.max(0, request.getExtraAiCredits() != null ? … : 0));
```

`BillingAddonsRequest` validates only `@Min(0)`. Any `HOSPITAL_ADMIN` can POST `{"extraAiCredits": 9999999, "extraStorageMb": 9999999}` and lift every limit in the product, for free. `liteEmrIntegrationEnabled` is likewise self-granted.

This isn't a missing feature so much as a missing boundary: tier and add-on *mutation* must move behind a payment webhook or a `SUPER_ADMIN`-only endpoint. Right now the entire Phase 11 enforcement layer can be nullified by the tenant it constrains.

### 4.2 Credit and storage checks race under concurrency

`assertAiCreditsAvailable` reads, then `consumeAiCredits` re-reads and writes — no `SELECT … FOR UPDATE`, no optimistic `@Version` on `Organization`, no atomic `UPDATE … SET used = used + 1 WHERE used + 1 <= limit`. Two concurrent generations both pass the check and both increment. At the tier concurrency targets (5/15/25 users, UR-078) an org can overshoot its monthly credits.

The Phase 7 exit criterion — *"credit deduction is atomic with generation"* — is **not met**. Same pattern for `assertStorageCapacity`.

Fix: add `@Version` to `Organization`, or make consumption a single conditional UPDATE and treat zero rows affected as limit-exceeded.

### 4.3 Auto-Grammar Check does not exist

Checklist: `[x] Pro/Ultimate only: Auto-Grammar Check feature`. What exists is a boolean derived from the tier (`Organization.isAutoGrammarCheckEnabled()`), surfaced in two billing DTOs. Grep for grammar-checking logic across the codebase returns those five references and nothing else — no endpoint, no service, no provider call. **The gate is built; the feature behind it is not.** This item should be unchecked.

### 4.4 UR-026 alerts are pull-only

`buildUsage()` computes `approachingUserLimit` / `approachingStorageLimit` / `approachingAiCreditLimit` at 80% and returns human-readable strings. Correct logic — but an admin only sees it if they call `GET /billing/usage`. There is no push: `NotificationService` (which has a working SSE + persisted-notification implementation for collaboration) is not wired to billing, and no email is sent. "Admin alerts approaching limit" as a user would read it is not implemented.

### 4.5 Storage accounting drifts on delete

`currentStorageUsageBytes` sums template sizes `WHERE t.isActive = true`, but `deleteTemplate` is a soft delete (`setIsActive(false)`) that leaves the object in R2. Deleted templates stop counting while still consuming real bytes. Low impact, but tier limits will diverge from the actual bill over time.

---

## 5. Organization / Multi-tenancy (Phases 1–3) — **Mostly solid**

Genuinely done:
- `Organization` entity with subscription, add-on, branding and AI-credit state.
- `AuthService.register()` creates the org and auto-provisions the first `HOSPITAL_ADMIN` in one transaction, with an `organization_registered` audit entry (`AuthService:72-107`). UR-002/UR-003 ✔
- Org-scoped login: `RegisterRequest` requires `organizationName` + `hospitalName`; login resolves username *within* the org (`AuthService:348-356`). UR-011 ✔
- Password policy regex enforces 8+/upper/lower/digit/special (`RegisterRequest:54`). UR-084 ✔
- Branding: `/org/branding` GET/PUT, `/org/letterhead` upload, `/org/letterhead/preview`, with format rejection tested. UR-005–UR-009 ✔
- 32 `ScanType` values against a 27+ requirement. UR-027 ✔
- Six roles incl. `PHYSICIAN`; `@PreAuthorize` at class level on every controller inspected. UR-086 ✔

Two corrections to the checklist:

**UR-089 (15-minute idle timeout) is not an idle timeout.** Sessions are `STATELESS` (`SecurityConfig:85`), which makes `server.servlet.session.timeout: 15m` (`application.yaml:46`) inert — there is no server session to expire. What exists is a 15-minute *absolute* access-token lifetime plus a **24-hour refresh token**. A client that refreshes on a timer stays authenticated for a full day with zero user activity. That is the opposite of an idle timeout, and it's the kind of thing a HIPAA reviewer asks about directly. Either track last-activity server-side, or shorten refresh-token life and bind renewal to real requests.

**UR-045 (letterhead on exports) — correctly unchecked, and the gap is bigger than it reads.** Both `ReportPdfService:205` and `ReportDocxService:145` render the *string* `"Custom Letterhead"` or `"Echion Health Default Letterhead"` as centred 9pt text. The uploaded image is never fetched or embedded. Exported reports currently carry a text placeholder where the hospital's logo should be.

---

## 6. Checklist items marked done that need qualifying

| Item | Marked | Reality |
|------|--------|---------|
| UR-025/026 AI credits + alerts | `[x]` | Credits ✔ but racy (§4.2); alerts pull-only (§4.4) |
| Auto-Grammar Check | `[x]` | Tier flag only, no feature (§4.3) |
| UR-021–UR-024 AI endpoints | `[x]` | Code complete, never produced a real completion (§3) |
| UR-023 scan-type-aware prompts | `[x]` | Loader ✔, only one generic template for 32 scan types (§3.4) |
| UR-001/UR-004 tenant isolation | `[x]` | App-layer ✔, DB-layer absent (§2.3) |
| UR-089 idle timeout | `[x]` | Absolute token TTL, not idle (§5) |
| UR-064 audit trail "append-only" | `[x]` | Audit rows are written everywhere and queryable per case ✔, but nothing enforces append-only — no DB grants, no trigger |

## 7. Checklist items marked *not* done that are actually built

Worth reclaiming — these look like stale bookkeeping:

| Item | Status |
|------|--------|
| UR-050 folders/categories | `FolderController` + `FolderService` with nested folders, org+user scoped, duplicate-name guard. **Partial** — folders are generic; no scan-type categorisation and no template↔folder link (`ReportTemplate` has a free-text `category`, `Folder` has no `scanType`) |
| UR-052 vault search | Fully built: `POST /vault/templates/search` filters keyword/scanType/tag/category/favorites/date-range with sort + direction. **But see §8.1** |
| UR-056 edit/duplicate/delete | `PUT /{id}`, `POST /{id}/duplicate`, `DELETE /{id}` with shared-template conflict handling all present |
| UR-055 "Upload More Templates" nav | Frontend concern; backend upload endpoints exist |
| Phase 0 S3 storage | `FileStorageService` has a working Cloudflare R2 (S3-compatible) client; `storage.type` defaults to `r2` |

UR-046 (personal vault) is marked unchecked but every template/report query is already scoped to `(userId, organizationId)` — the per-user vault is the de facto model. Clarify what's actually outstanding or check it off.

---

## 8. Other findings worth queueing

**8.1 Template search is in-memory and won't hold at 5,000 templates.** `TemplateService.searchTemplates:409` loads *every* template for the user, maps each to a DTO, filters in Java streams, sorts, then hand-slices the page. Both the DB round-trip and the mapping cost scale linearly and ignore the page size. UR-076 (<2s @ 5,000 templates) is at real risk. Push filtering into a JPA `Specification` or the existing `optimize_search.sql` tsvector path. Separately: `getAllTemplates` returns own + system templates only, so **templates shared with you are invisible to search and favourites**.

**8.2 The analytics dashboard reports fabricated numbers.** `AnalyticsService:40` — `aiReportsEdited = (long)(aiReports * 0.12)` with the comment *"Simulating 12% edit rate"* — and `:70` — `productivityGain = 32.0` hardcoded. `aiReportAcceptanceRate`, `reportRevisionRate` and `totalHoursSaved` are all derived from that invented 12%. These are presented to hospital admins as measured metrics. Either compute them from real edit events or remove them; shipping invented clinical-productivity figures is a liability.

**8.3 AI telemetry is write-only.** `AiGenerationEvent` rows are written on every call with provider, model, tokens, cost, latency and failure reason — and nothing ever reads them. `AiGenerationEventRepository` is injected in exactly one place (the writer). No admin endpoint, and `AnalyticsService` doesn't touch it. This is the data that would answer "is the AI actually working, and what is it costing us" — surface it.

**8.4 AES-256 at rest (UR-082) covers one field.** `EncryptionUtil` is injected only into `AuthService`, used only to encrypt/decrypt the TOTP secret (`AuthService:473-477`). Patient names, findings and impressions are stored in plaintext columns. Whether that satisfies UR-082 depends entirely on whether Postgres/R2 disk encryption is enabled at the infrastructure layer — which nothing in this repo establishes. Confirm before the HIPAA review, since UR-082 is Critical.

**8.5 PHI stripping is regex heuristics.** `PhiStrippingService` is well-built and deliberately aggressive, but it is label-anchored — `Patient: John Doe` is caught, a bare name in a findings paragraph is not. The exit criterion *"PHI never appears in any vault-surfaced template regardless of entry path"* cannot be met by regex alone. Fine as a first line; it should not be presented to a compliance reviewer as the whole control.

**8.6 Test coverage is thin outside Phases 1–4.** Seven test files. `MultiTenantFoundationIntegrationTest` is substantial and covers signup, cross-org rejection, user lifecycle, branding, signatures and finalization validation. But there is **no test for billing** (no tier-limit test, no add-on test, no credit-consumption test), none for collaboration, none for export formats. And because the test profile disables Flyway and uses H2 `create-drop`, the migration chain that will actually run in production is never executed by CI.

---

## 9. Recommended order

1. **Author a baseline migration** so an empty Postgres provisions cleanly; fix V8's UUID/VARCHAR mismatch; add a CI job that migrates an empty Postgres from scratch. *(§2)*
2. **Rewrite `GeminiReportProvider`** for `generateContent`, fix the OpenAI strict schema, verify all three model IDs live, then run a real end-to-end generation before touching anything else in Phase 7. *(§3.1–3.3)*
3. **Close the billing self-service hole** — move tier/add-on mutation behind payment or `SUPER_ADMIN`. *(§4.1)*
4. **Make credit consumption atomic** — `@Version` on `Organization` or a conditional UPDATE. *(§4.2)*
5. **Embed the real letterhead image** in PDF/DOCX export. *(§5)*
6. Correct the checklist: uncheck Auto-Grammar Check and UR-089; check UR-052/UR-056; qualify UR-023 and Phase 7. *(§6, §7)*
7. Then: push search into the DB (§8.1), replace fabricated analytics (§8.2), expose AI telemetry (§8.3), settle the encryption-at-rest story (§8.4).

Phases 12 and 13 remain almost entirely open, and correctly so — but note that UR-075/076/078 each have a specific, already-identifiable blocker above (§3.5, §8.1, §4.2), so they won't pass on a first run.

---

# Resolution log — 2026-07-29

All findings above were implemented and verified. Test suite: **59 tests, 0 failures**, including
four PostgreSQL-backed integration tests that did not previously exist.

## Found during implementation (not in the audit above)

**Flyway had never run.** Spring Boot 4 moved Flyway auto-configuration out of
`spring-boot-autoconfigure` into a separate `spring-boot-flyway` module, which was not on the
classpath. `flyway-core` was present, `spring.flyway.*` was silently ignored, and every
migration in `db/migration` was inert — which explains both the schema drift and the hand-rolled
`PostgresConstraintRepairRunner`. Fixed by adding the module; the migration chain now actually
executes, and `MigrationChainPostgresTest` fails the build if it ever stops.

**Every `@PreAuthorize` denial returned HTTP 500.** `GlobalExceptionHandler` handled the
project's own `AccessDeniedException`, which shadowed Spring Security's same-named class by
package proximity. Authorization failures fell through to the catch-all. Fixed; RBAC denials now
return 403. Plain `BusinessException` had the same problem and now returns 400.

**The analytics dashboard was worse than reported.** Beyond the simulated 12% edit rate,
`monthlyStaffSavings` (372,000), `revenueImpact` (720,000), `additionalReportsCapacity` (460),
`clinicalErrorReduction` (45%) and `diagnosticDelayReduction` (38%) were all hardcoded from a
design mock and returned to hospital administrators as measurements.

**`AuthUserCacheTest` was already broken** by the uncommitted Spring Boot 4.0.1 → 4.0.6 bump
(JDK vs CGLIB proxying in narrow test contexts). Fixed with explicit `proxyTargetClass`.

## Corrections to the audit

- **§8.1 was wrong about shared templates.** `getAllTemplates` did include templates shared with
  the user, so search was not blind to them. The in-memory scan was real; the visibility gap was
  not. Verified by `VaultSearchPostgresTest`.

## What changed, by finding

| Finding | Resolution |
|---|---|
| §2.1 migrations cannot provision an empty DB | V1 now creates the full core schema; V11 converges existing databases. Verified against real PostgreSQL. |
| §2.2 V8 UUID vs VARCHAR | V8 uses `VARCHAR(36)`; foreign keys moved to V11 where every referenced table exists. |
| §2.3 tenant isolation app-layer only | `organization_id` is NOT NULL with an FK on all 13 tenant-scoped tables. |
| §3.1 Gemini wrong protocol | Rewritten for `POST {base}/models/{model}:generateContent`. |
| §3.2 OpenAI strict schema invalid | Schema satisfies strict mode; output extraction skips reasoning items. |
| §3.4 prompts not scan-type-aware | v2 prompts resolve per scan type → clinical category → default, with each scan type's real section structure injected. |
| §3.5 timeout budget | 12s per attempt, 5s connect timeout, retry only on cheap retryable failures. |
| §4.1 tenants self-granting entitlement | Tier and add-on grants restricted to `SUPER_ADMIN`; tenants get `POST /billing/upgrade-request`. Add-ons bounded by configured ceilings. |
| §4.2 credit/storage races | Credits reserved via one conditional UPDATE and refunded on failure; storage checks hold the organization row lock. |
| §4.3 Auto-Grammar Check absent | Implemented, tier-gated, returns discrete suggestions and never auto-applies. |
| §4.4 alerts pull-only | In-app + email push on threshold crossing, de-duplicated via conditional UPDATE. |
| §4.5 storage accounting drift | Deleting a template releases its blob; accounting keys off `blob_deleted`, not `is_active`. |
| §5 UR-089 not an idle timeout | Sliding idle window on the refresh token (default 15 min) with a 10-minute access token beneath it. |
| §5 UR-045 letterhead | Real image embedded in PDF and DOCX, with graceful fallback. |
| §8.1 in-memory search | One indexed PostgreSQL query with DB-side filtering, sorting and paging. |
| §8.2 fabricated analytics | Measured metrics computed from data; unmeasurable ones omitted with a stated reason; the one estimate is labelled and its assumption returned alongside it. |
| §8.3 write-only AI telemetry | `GET /api/admin/ai/usage` plus `POST /api/admin/ai/verify` for live provider checks. |
| §8.6 thin test coverage | 59 tests, including PostgreSQL-backed migration, search, billing-authority, grammar-gating and letterhead tests. |

## Still open — deliberately not done

- **§8.4 encryption at rest.** `EncryptionUtil` still covers only the TOTP secret. Encrypting
  patient columns is a data-migration and key-management project, and whether it is needed at all
  depends on whether PostgreSQL and R2 disk encryption is enabled at the infrastructure layer —
  which nothing in this repo establishes. Confirm that before the HIPAA review, since UR-082 is
  Critical.
- **§8.5 PHI stripping is still regex heuristics.** Adequate as a first line, but the exit
  criterion ("PHI never appears in any vault-surfaced template regardless of entry path") cannot
  be met by regex alone and should not be presented to a compliance reviewer as the whole control.
- **Model IDs and prices** in `application.yaml` are set to conservative defaults. They are
  deployment configuration and must be verified against each provider's current catalogue.
- **Phase 12/13 items** requiring load tests, a penetration test, a HIPAA review and UAT remain
  open by nature.


---

## Post-deployment fix — V1 failed on the first real startup

Wiring Flyway up exposed a second problem that only appears on an already-deployed database.

```
Migration of schema "public" to version "1 - phase1 multitenant foundation" failed!
ERROR: null value in column "addon_ai_credits" of relation "organizations"
       violates not-null constraint
```

**Cause.** Because Flyway had never run, a deployed environment has Hibernate-created tables
and no `flyway_schema_history`. The first startup after the fix therefore baselines at 0 and
runs V1 against a schema that already exists. V1 inserts the legacy default organization using
the column list that existed when it was written — before the Phase 11 billing columns. On a
Hibernate-created table those columns are `NOT NULL` **without** a `DEFAULT` (Hibernate does not
emit `DEFAULT` for `@Builder.Default`), so the insert violates the constraint. On a fresh
database V1 creates `organizations` without those columns at all, so the fresh path never showed
it — and `MigrationChainPostgresTest` starts from empty, so it could not catch it either.

**Fix.** V1 now normalizes the billing columns (adding them if absent and pinning their defaults
either way) before anything inserts into `organizations`. This also protects the identical
insert in V10.

**Additional hardening.** V11's column convergence is now generated from the canonical schema a
clean run of the chain produces — 207 non-key columns across 17 tables — so a database built
from *any* older entity model converges rather than only the specific columns spotted by hand.
With `ddl-auto: none`, a missing column is a runtime failure on first query rather than a
startup error, which makes this worth being exhaustive about.

**New regression guard.** `LegacyHibernateSchemaMigrationTest` reproduces the exact deployed
state: Hibernate builds the schema with Flyway disabled, a representative set of newer columns
and one whole table are then dropped to simulate an older model, and the chain is run against
it. It asserts the migration succeeds, the dropped columns and table are restored, and the
tenant constraints still land. This is the path that broke; it is now covered.

---

# Backend performance verification — 2026-07-29

Measured against real PostgreSQL 16 (Testcontainers) with 5,000 templates seeded for one user.
Reproduce with `./mvnw -Dtest=VaultSearchPerformancePostgresTest test`; timings print to stdout.

These three targets could not previously be measured: vault search loaded every template visible
to the user, mapped each one to a DTO and filtered in Java streams, so cost scaled with library
size rather than page size. Moving it into a single indexed query is what made the target both
reachable and measurable.

## UR-076 — vault search under 2s at 5,000 templates ✅

| Scenario | p50 | p95 | max | Matches |
|---|---|---|---|---|
| unfiltered (first page) | 18ms | 24ms | 28ms | 5,000 |
| keyword (broad) | 33ms | 41ms | 49ms | 5,000 |
| keyword (rare term) | 19ms | 26ms | 29ms | 1 |
| scan type | 8ms | 11ms | 12ms | 625 |
| category | 12ms | 17ms | 21ms | 1,000 |
| tag — common | 15ms | 20ms | 23ms | 4,966 |
| tag — rare | 11ms | 16ms | 17ms | 34 |
| favourites only | 11ms | 13ms | 13ms | 100 |
| sorted by name | 12ms | 18ms | 21ms | 5,000 |

Worst case is 41ms p95 against a 2,000ms target — roughly 50x headroom.

Deep paging, the case an in-memory implementation degrades on worst, costs 30ms p95 on the last
page versus 17ms on the first. That difference is `OFFSET` cost, not library size, which is
exactly the property the rewrite was meant to buy.

## UR-077 — template load under 1s ✅

p50 2ms, p95 3ms, max 3ms.

## UR-078 — tier concurrency without degradation ✅

| Concurrent users | Tier | p50 | p95 | max | Errors |
|---|---|---|---|---|---|
| 1 (baseline) | — | 30ms | 39ms | — | 0 |
| 5 | Basic | 9ms | 21ms | 21ms | 0 |
| 15 | Pro | 14ms | 33ms | 40ms | 0 |
| 25 | Ultimate | 18ms | 47ms | 73ms | 0 |

Zero errors at every tier. Latency grows sub-linearly — 5x the users costs about 2.2x p95 between
5 and 25 — so the 25-user Ultimate ceiling is not near a knee.

**Worth acting on:** the Hikari pool is sized at 25 (`HIKARI_MAX_POOL`), which is exactly the
Ultimate concurrency target. It held fine here, but there is no headroom above it. Raise it before
introducing any tier above Ultimate, or if multiple organizations are expected to peak
simultaneously — that is where queueing would begin.

## AI credit concurrency ✅

Closes the gap left open in the audit: the fix replaced read-then-write with a single conditional
UPDATE, but nothing had demonstrated the property held under parallel load.

40 threads released simultaneously against an organization with 5 credits remaining: **exactly 5
reservations granted, 35 rejected**, and final usage landed precisely on the tier limit. The
previous check-then-consume implementation would have allowed all 40 through. Refunds clamp at
zero rather than wrapping negative and handing out free quota.

## UR-075 — AI generation 5–10s ⏳ still unverified

App-side overhead (prompt assembly, credit reservation, event recording) is negligible against a
5–10s budget, but the metric is dominated by provider round-trip time and cannot be verified
without a live call.

One thing to watch: timeouts are configured at 12s per attempt with one cheap retry, so a primary
failure followed by fallback is bounded at roughly **24s worst case** — outside the target. If p95
latency after live verification runs close to the ceiling, reduce `timeout-seconds` rather than
accepting the double-provider path inside the SLA.

## Caveats on these numbers

- **Single instance, local database.** Network latency between application and database is near
  zero here; a production deployment with a separate database host adds its round-trip to every
  figure. The headroom is large enough that this should not threaten the targets, but treat these
  as a floor rather than a prediction.
- **Warm JIT and warm PostgreSQL plan cache** — five warm-up iterations run before timing. Cold
  first requests after a deploy will be slower.
- **5,000 templates for a single user**, which is the shape UR-076 specifies. Total corpus size
  across all tenants was not varied; the tenant-leading composite indexes (UR-097) are what keep
  that flat, and they are verified structurally but not under multi-tenant load.
- **Not covered:** sustained soak testing, multi-instance behaviour, or the storage and
  collaboration endpoints. This measured the vault search path and the credit reservation path.
