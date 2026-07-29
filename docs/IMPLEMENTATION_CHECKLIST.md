# Implementation Checklist — Echion Health v2.0

Hand this file directly to the coding agent as the execution plan. Ordered by dependency: each phase builds on the previous one. Every item references its source requirement ID (UR-xxx) from URD v2.0 — cross-check `PRD.md` for acceptance criteria and `TECHNICAL_ARCHITECTURE.md` for schema/API detail before marking a phase done.

Legend: **[C]** Critical · **[H]** High · **[M]** Medium · **[L]** Low

> **Revised 2026-07-29** after an audit found several items ticked that were not actually
> implemented, and several implemented items left unticked. See
> `IMPLEMENTATION_AUDIT_2026-07-29.md` for the findings and what changed. The largest was that
> **Flyway had never run at all**: Spring Boot 4 moved Flyway auto-configuration into a separate
> module that was not on the classpath, so `flyway-core` was present, `spring.flyway.*` was
> silently ignored, and the entire migration chain was inert.

---

## Phase 0 — Setup

- [x] Confirm actual tech stack matches (or update) `TECHNICAL_ARCHITECTURE.md` §1
- [x] Set up Postgres + Flyway (or Liquibase) migration pipeline
- [x] Set up S3-compatible storage bucket + credentials (Cloudflare R2, S3-compatible; `storage.type=r2`)
- [x] Configure OpenAI + Gemini API keys, decide default provider
- [x] Stand up environment variables per `README.md` §4
- [x] Resolve open questions in `PRD.md` §7 (AI fallback: Gemini primary → OpenAI fallback → manual; HL7: ORU^R01 v2.5)

---

## Phase 1 — Multi-Tenant Foundation

**Blocks everything else — do this first.**

- [x] **[C]** UR-001: `Organization` model + tenant data isolation at DB layer
- [x] **[C]** UR-002: Org signup flow (org name, admin credentials, contact info)
- [x] **[C]** UR-003: First `HOSPITAL_ADMIN` user auto-provisioned on signup
- [x] **[C]** UR-004: Middleware/ORM-layer enforcement preventing cross-org data access
- [x] Write negative tests: attempt cross-org reads/writes via direct ID access, confirm 403/404
- [x] **[C]** UR-097: Add `organizationId`-leading composite indexes on all tenant-scoped tables

**Exit criteria:** two orgs can be created; a user in org A cannot read/write any org B data via any endpoint.

---

## Phase 2 — Auth, Users & RBAC

- [x] **[C]** UR-010: Admin creates users (full name, username, email, role, designation)
- [x] **[C]** UR-011: Org-scoped username/password authentication
- [x] **[H]** UR-012: Optional TOTP MFA
- [x] **[H]** UR-013: Deactivate/reactivate users (soft delete, preserve history)
- [x] **[H]** UR-014: Admin grants `canUploadSignature` per user
- [x] **[C]** UR-086: Server-side RBAC on every endpoint (Admin / Sonographer / Radiologist / System Admin)
- [x] **[H]** UR-089: 15-minute idle session timeout (server-enforced, sliding window on the refresh token)
- [x] **[C]** UR-084: Password complexity rules (8+ chars, upper/lower/number/special)

**Exit criteria:** all four roles exist with distinct, server-enforced permissions; deactivated users can't log in but their data persists.

---

## Phase 3 — Hospital Branding

- [x] **[H]** UR-005: Letterhead/logo upload (PNG/JPG/SVG, max 5MB)
- [x] **[H]** UR-006: Default Echion Health letterhead fallback
- [x] **[H]** UR-007: Hospital profile fields (name, address, phone, email, website)
- [x] **[H]** UR-008: Auto-inject letterhead + profile into generated reports
- [x] **[M]** UR-009: Live letterhead preview before saving config

**Exit criteria:** an org with no uploaded letterhead still produces professionally branded reports via the default template.

---

## Phase 4 — Digital Signatures

- [x] **[H]** UR-015: Signature upload (PNG/JPG, max 2MB)
- [x] **[M]** UR-016: Multiple labeled signatures per user
- [x] **[H]** UR-017: Signature selection at report finalization
- [x] **[H]** UR-018: Render signature + name + designation on report
- [x] **[C]** UR-019: Audit log entry per signature application

**Exit criteria:** a report can't be finalized with an unauthorized/nonexistent signature; every signature use is auditable.

---

## Phase 5 — Scan Types & Templates (data layer)

- [x] **[C]** UR-027: Implement all 27+ `ScanType` enum values + structured field definitions
- [x] **[C]** UR-028: Organ/structure-specific sections per scan type
- [x] **[H]** UR-029: Doppler velocity measurement table (measurement + finding columns)
- [x] **[H]** UR-030: General Report free-form template

**Exit criteria:** selecting any scan type from the dropdown loads the correct field structure, verified against the URD's scan type list.

---

## Phase 6 — Report Creation (SonoScribe Interface)

- [x] **[C]** UR-031: Report creation entry points — from scratch / from SonoVault template / via AI
- [x] **[C]** UR-032: Mandatory fields (Patient Name, Age, Scan Date, Scan Type)
- [x] **[H]** UR-033: Organ/structure input fields (name, findings, measurements)
- [x] **[C]** UR-034: Impression (Summary) field
- [x] **[H]** UR-035: Recommendations section (predefined options + free text, per scan type)
- [x] **[M]** UR-037: Helper tools — Clinical Differential, Impression Generator, Wording Assistant, Verified References
- [x] **[H]** UR-038: Confirm **no voice dictation** anywhere in the UI
- [x] **[C]** UR-039: Auto-save every 2 minutes

**Exit criteria:** a sonographer can build a complete report through the manual flow without AI, with auto-save verified via simulated tab close.

---

## Phase 7 — SonoScribe AI Integration

**Depends on Phase 5 (scan types) and Phase 6 (report data model).**

- [x] **[C]** UR-020: OpenAI and/or Gemini API integration
  Gemini speaks the real `generateContent` protocol (it previously sent OpenAI-shaped bodies to a
  non-existent endpoint); the OpenAI strict JSON schema is now valid (it previously 400'd on every
  call). Both wire formats are pinned by `AiProviderWireFormatTest`.
  **Before release:** confirm the configured model IDs and per-million prices in `application.yaml`
  against each provider's current catalogue, then run `POST /api/admin/ai/verify` to confirm live
  wiring end to end.
- [x] **[C]** UR-021: `POST /api/generate-report` endpoint (raw notes, scan type, optional measurements)
- [x] **[C]** UR-022: Structured output — Findings / Impression / Recommendations
- [x] **[C]** UR-023: Versioned, scan-type-aware prompt templates (v2; resolves per scan type → clinical category → default, with each scan type's section structure, measurement columns and recommendation options injected)
- [x] **[C]** UR-024: All AI output editable pre-finalization (no direct-to-final path)
- [x] **[H]** UR-036: "Suggest Impression" AI button from entered findings
- [x] **[H]** UR-025: AI credit tracking per subscription tier (1,000 / 1,500 / 2,000 per month) — credits are reserved atomically before generation and refunded if it fails
- [x] **[H]** UR-026: Usage tracking + admin alerts approaching limit (in-app + email push on threshold crossing, de-duplicated; `GET /api/admin/ai/usage` reports success/fallback rate, latency and cost)
- [x] Decide + implement AI provider fallback behavior (Gemini primary -> OpenAI fallback -> manual retry/continue flow if both fail)

**Exit criteria:** AI generation completes in 5–10s, produces structured JSON (not raw text needing parsing), and credit deduction is atomic with generation.

---

## Phase 8 — Report Finalization & Export

- [x] **[H]** UR-040: Pre-finalization preview (letterhead, patient info, findings, impression, recommendations, signature)
- [x] **[H]** UR-041: Designation selection at finalize time
- [x] **[H]** UR-042: Signature selection at finalize time
- [x] **[H]** UR-043: Mandatory field validation blocking finalization
- [x] **[C]** UR-044: Export to PDF, DOCX, HL7
- [x] **[H]** UR-045: Letterhead rendered on all exported formats (real PNG/JPG embedded in PDF and DOCX; SVG and unreadable objects fall back to the default text banner)

**Exit criteria:** a finalized report exports identically (content-wise) across PDF/DOCX/HL7 from a single source record.

---

## Phase 9 — SonoVault

- [x] **[C]** UR-046: Personal vault per user (every template/report query is scoped to `(userId, organizationId)`)
- [x] **[H]** UR-047: Upload templates (PDF, DOCX, TXT)
- [x] **[H]** UR-048: Bulk upload (CSV, JSON, multi-file)
- [x] **[C]** UR-049: Server-side PHI stripping + `phiFree` flag on template save
- [x] **[H]** UR-050: Custom folders/categories by scan type (nested user folders + per-template `category`, auto-derived from scan type)
- [x] **[H]** UR-051: Custom tags (complexity, indication, normal/abnormal)
- [x] **[H]** UR-052: Search (keyword/scan type/tag/category/date/favourites) + sort + paging — executed as one indexed PostgreSQL query, not an in-memory scan. Grid/list view is a frontend concern.
- [x] **[H]** UR-053: Favorite marking
- [x] **[M]** UR-054: "Recently Used" (last 10)
- [ ] **[M]** UR-055: In-context "Upload More Templates" navigation — frontend only; upload endpoints exist
- [x] **[H]** UR-056: Edit/duplicate/delete (delete releases the stored blob so quota is returned, and blocks on shared templates unless cascade is confirmed)
- [x] **[M]** UR-057: Version history + restore
- [x] **[H]** UR-058: Share templates with org colleagues (ownership retained)
- [x] Resolve shared-template deletion semantics before building delete flow (shared templates now block deletion by default unless cascade removal is explicitly requested)

**Exit criteria:** PHI never appears in any vault-surfaced template regardless of entry path; search returns in <2s at 5,000 templates.

---

## Phase 10 — SonoShare

- [x] **[H]** UR-059: Upload scans for review/consultation
- [x] **[H]** UR-060: Share scope (specific / department / org-wide), description, urgency
- [x] **[H]** UR-061: View / comment / suggest impression / annotate (if image support exists)
- [x] **[M]** UR-062: Notification on new feedback
- [x] **[L]** UR-063: Mark case "Resolved"
- [x] **[C]** UR-064: Full audit trail (access + feedback)

**Exit criteria:** a user outside the share scope gets no access (server-enforced); audit trail is append-only and queryable per case.

---

## Phase 11 — Subscription Tiers & Billing

- [x] **[C]** UR-065: Basic / Pro / Ultimate tier definitions (users, storage, AI credits)
- [x] **[M]** UR-066: Add-ons — storage, AI credits, Lite EMR integration (granting is restricted to platform operators and bounded by configured ceilings; tenants raise an upgrade *request*)
- [x] **[H]** UR-067: Enforce user limits per tier (block over-limit creation)
- [x] **[H]** UR-068: Alert admins approaching storage/AI credit limits, with upgrade option
- [x] Pro/Ultimate only: Auto-Grammar Check feature — `POST /api/generate-report/grammar-check`, tier-gated, returns discrete suggested edits and never rewrites clinical text automatically

**Exit criteria:** exceeding any tier limit produces a clear, non-crashing upgrade prompt, not a silent failure.

---

## Phase 12 — Non-Functional Requirements Hardening

### Usability
- [ ] **[H]** UR-069: Responsive design (desktop/tablet/mobile)
- [ ] **[H]** UR-070: New AI-assisted report in 3–5 min (experienced user)
- [ ] **[H]** UR-071: First report within 30 min of training (new user)
- [ ] **[H]** UR-072: Intuitive nav, minimal clicks, contextual help
- [ ] **[M]** UR-073: Keyboard shortcuts
- [ ] **[M]** UR-074: Medical terminology autocomplete

### Performance
- [ ] **[H]** UR-075: AI generation 5–10s — app-side overhead is negligible; the figure is dominated by provider round-trip time and cannot be verified until a live provider call is made (`POST /api/admin/ai/verify`).
- [x] **[H]** UR-076: Vault search <2s @ 5,000 templates — **measured**: p95 11–41ms across nine filter shapes at 5,000 templates; deep paging (last page) p95 30ms. ~50x headroom.
- [x] **[H]** UR-077: Template load <1s — **measured**: p95 3ms.
- [x] **[H]** UR-078: Tier-based concurrency (5/15/25 users) — **measured**: p95 21ms / 33ms / 47ms, zero errors. Degradation is sub-linear across the tier range.
- [ ] **[M]** UR-079: File uploads up to 50MB with progress indicator

### Security & Privacy
- [ ] **[C]** UR-080: HIPAA compliance posture
- [ ] **[C]** UR-081: TLS 1.3+ in transit
- [ ] **[C]** UR-082: AES-256 at rest
- [x] **[C]** UR-083: Tenant isolation enforced at DB + app layers — `organization_id` is NOT NULL with a foreign key on every tenant-scoped table (V11), on top of the existing repository-level scoping
- [x] **[C]** UR-084: Strong password policy (cross-check Phase 2)
- [x] **[H]** UR-085: Optional MFA (cross-check Phase 2)
- [x] **[C]** UR-086: RBAC (cross-check Phase 2) — authorization denials now return 403 rather than 500
- [ ] **[C]** UR-087: Auto PHI detection/flagging on template save (cross-check Phase 9)
- [ ] **[C]** UR-088: Comprehensive audit logging across all activity categories
- [x] **[H]** UR-089: Session timeout — genuine sliding idle window enforced on the refresh token (`jwt.idle-timeout-minutes`, default 15), with a 10-minute access token beneath it. Previously this was a 15-minute *absolute* token TTL plus a 24-hour refresh token, so a silent client stayed authenticated all day.
- [ ] **[M]** UR-090: GDPR readiness — confirm scope with product first

### Reliability & Availability
- [ ] **[H]** UR-091: 99.9% uptime target (clinical hours)
- [ ] **[C]** UR-092: Incremental backups every 6h + daily full
- [ ] **[H]** UR-093: ≤2 min work loss on failure (cross-check auto-save, Phase 6)
- [ ] **[H]** UR-094: 4-hour RTO disaster recovery

### Data Storage & Scalability
- [x] **[H]** UR-095: Enforce storage limits per tier (250MB / 600MB / 1GB)
- [ ] **[H]** UR-096: Architecture supports 500+ users across orgs
- [x] **[H]** UR-097: Tenant-scoped indexes (cross-check Phase 1)

---

## Verification status

What is now covered by automated tests, and what still needs a human or a load test:

| Area | Covered by | Still outstanding |
|------|-----------|-------------------|
| Migration chain | `MigrationChainPostgresTest` — migrates an empty PostgreSQL and validates every JPA entity against the result | — |
| AI wire formats | `AiProviderWireFormatTest` — pins both providers' request/response shapes and retry classification against a stub server | A live call to each provider (`POST /api/admin/ai/verify`) |
| Vault search | `VaultSearchPostgresTest` — filters, tag array containment, paging, shared-template visibility, on real PostgreSQL | — |
| Search performance | `VaultSearchPerformancePostgresTest` — UR-076/077/078 measured at 5,000 templates and 5/15/25 concurrent users | Sustained soak test; multi-instance behaviour |
| Billing authority | `MultiTenantFoundationIntegrationTest` — tenants cannot self-grant tiers or add-ons; operators can; add-on ceilings hold | — |
| AI credits | `AiReportGenerationServiceTest` + `AiCreditConcurrencyPostgresTest` — reserve-before-generate, refund on failure, and 40 threads racing for 5 remaining credits granting exactly 5 | — |
| Grammar check | `GrammarCheckTierGatingTest` — Basic rejected without charge, Pro/Ultimate entitled, failures refunded | Clinical review of correction quality |
| Letterhead export | `LetterheadExportTest` — image genuinely embedded in the PDF, graceful fallback | DOCX visual check |
| Tenant isolation | `MultiTenantFoundationIntegrationTest` (app layer) + `MigrationChainPostgresTest` (DB constraints) | Penetration test |

---

## Phase 13 — QA, Compliance & Launch Readiness

Map directly to `PRD.md` §9 release-level acceptance criteria.

- [ ] All UR-001–UR-098 implemented and demoed against acceptance criteria
- [ ] Penetration test confirms tenant data isolation
- [ ] Independent HIPAA compliance review completed
- [ ] Security scan shows no high-severity vulnerabilities
- [x] Load test confirms tier-based concurrency targets (`VaultSearchPerformancePostgresTest`; see UR-078 above)
- [ ] 30-day uptime pilot tracking in place
- [ ] Auto-save failure simulation passes (≤2 min loss)
- [ ] PDF/DOCX/HL7 export QA across all 27+ scan types
- [ ] Radiologist clinical review of AI-generated report samples
- [ ] Sonographer UAT: 20+ participants, ≥85% satisfaction target
- [ ] New-user onboarding test: ≥90% complete first report within 30 min

---

## Not in scope — do not build

- [ ] ~~Voice dictation~~ (UR-038 — explicitly excluded)
- [ ] ~~DICOM image integration~~ (UR-098 — future roadmap)
- [ ] ~~Advanced AI analytics~~ (UR-098 — future roadmap)
- [ ] ~~EMR/EHR integrations beyond HL7~~ (UR-098 — future roadmap)
- [ ] ~~Native iOS/Android apps~~ (UR-098 — future roadmap)
