# Product Requirements Document (PRD)
## Echion Health v2.0 — Multi-Tenant AI Ultrasound Reporting Platform

**Source:** Updated User Requirements Document (URD) v2.0, May 5 2026
**Status:** Ready for implementation
**Audience:** Engineering agent / dev team implementing this upgrade

---

## 1. Overview

Echion Health is evolving from a single-tenant ultrasound reporting tool into a **multi-tenant SaaS platform** serving multiple hospitals/clinics independently. Each organization gets its own isolated environment, custom branding, user base, template vault, and subscription plan.

### 1.1 Business goals
- Cut report generation time 50–60% via AI assistance and templates
- Improve diagnostic report quality, consistency, and standardization
- Support multi-hospital deployment with centralized management
- Offer tiered pricing for practices of all sizes
- Enable peer knowledge-sharing and consultation
- Guarantee HIPAA compliance and data security
- Support professional hospital branding on every report
- Scale to 500+ users across multiple facilities

### 1.2 Success metrics (tie to acceptance criteria in §9)
- AI report generation < 10s
- SonoVault search < 2s at 5,000 templates
- 99.9% uptime during clinical hours
- 85%+ satisfaction in sonographer UAT (20+ participants)
- 90% of new users complete their first report within 30 minutes

---

## 2. User roles

| Role | Summary | Key permissions |
|---|---|---|
| **Hospital Administrator** | Manages the org's environment | User CRUD, branding config, signature authorization, subscription/billing, SonoVault/SonoShare access control |
| **Sonographer** | Primary daily user, creates reports | Create/edit reports, use SonoScribe, manage own SonoVault, participate in SonoShare |
| **Radiologist/Consultant** | Reviews and finalizes reports | Review, finalize, sign reports, SonoShare consultation |
| **System Administrator** | IT/infra role | Security, backups, performance, troubleshooting (not org-scoped) |

Role-based access control (RBAC) must be enforced server-side on every endpoint, not just hidden in the UI (UR-086).

---

## 3. Epic breakdown

Each epic maps to a section of the URD. Implement roughly in this order due to dependencies (see `IMPLEMENTATION_CHECKLIST.md` for the sequenced task list).

### Epic A — Multi-Tenant Organization Management (UR-001–UR-004)

**Goal:** Every hospital operates in a fully isolated tenant.

- Org signup flow: org name, admin credentials, contact info → creates `Organization` + first `HOSPITAL_ADMIN` user.
- Every data-bearing table scoped by `organizationId`; every query middleware-enforced to filter by the authenticated user's org.
- No UI or API path should allow cross-org reads/writes, including via IDs guessed/enumerated (test with negative cases, not just happy path).

**Acceptance criteria:**
- Creating two orgs and attempting to fetch org B's data while authenticated as org A fails with 403/404, not 200 with empty/leaked data.
- Admin account is auto-provisioned during signup with full permissions.

---

### Epic B — Hospital Customization & Branding (UR-005–UR-009)

**Goal:** Every report reflects the issuing hospital's identity.

- Letterhead/logo upload: PNG/JPG/SVG, max 5MB, stored per-org.
- System-provided default Echion Health letterhead used when none uploaded.
- Hospital profile: name, address, phone, email, website (optional).
- Letterhead + profile auto-injected into every generated report for that org.
- Live preview of report-with-letterhead before saving config.

**Acceptance criteria:**
- Uploading an oversized (>5MB) or wrong-format file is rejected with a clear error, not a silent failure.
- Reports generated before vs. after a branding change reflect the correct letterhead as of generation time (don't retroactively alter finalized reports).

---

### Epic C — User Management & Authorization (UR-010–UR-014)

**Goal:** Admins fully control who has access and at what level.

- Admin creates users: full name, username, email, role, designation (Releasing Radiologist / Consultant Radiologist / Sonographer / Physician Specialist / Cardiologist).
- Org-scoped username/password auth.
- Optional TOTP-based MFA.
- Deactivate/reactivate accounts without deleting history (soft delete pattern — retain FK integrity for past reports/audit logs).
- Admin explicitly grants `canUploadSignature` per user.

**Acceptance criteria:**
- Deactivated user cannot log in but their historical reports/signatures remain intact and attributable.
- A user without `canUploadSignature` cannot access the signature upload endpoint (403), not just a hidden UI button.

---

### Epic D — Digital Signature Management (UR-015–UR-019)

**Goal:** Reports are authenticated with legally traceable signatures.

- Authorized users upload signature images (PNG/JPG, transparent background preferred, max 2MB).
- Multiple labeled signatures per user (e.g. "Full Signature", "Initials", "Official Seal"), one marked default.
- Signature selection happens at report finalization time.
- Rendered signature includes name + designation beneath it.
- Every signature application is audit-logged (who, which signature, when, on which report).

**Acceptance criteria:**
- Audit log entry exists for every finalized report showing signature ID + timestamp.
- A user can have zero, one, or many signatures; UI must not assume exactly one.

---

### Epic E — AI-Powered Report Generation (SonoScribe) (UR-020–UR-026)

**Goal:** AI drafts structured, editable reports from raw clinical input.

- Integration with OpenAI (`gpt-4.0-mini`) and/or Gemini API — configurable per deployment, with a fallback provider if the primary fails.
- `POST /api/generate-report` endpoint accepting: raw clinical notes/transcript, scan type (from the 27+ types), optional measurements/findings data.
- Output structured into: **Findings** (per organ/structure), **Impression** (summary), **Recommendations** (optional).
- Prompt engineering must enforce clinical terminology and formatting standards — treat prompts as versioned artifacts, not inline strings scattered through the codebase.
- All AI output is editable before finalization — never allow direct-to-final without a human review step.
- AI credits consumed per generation, tracked per org against tier limits (Basic 1,000 / Pro 1,500 / Ultimate 2,000 per month).
- Admins alerted as usage approaches the monthly limit (e.g. at 80% and 100%).

**Acceptance criteria:**
- Generation completes in 5–10s for typical input (NFR, see §8).
- Exceeding the monthly AI credit limit blocks further AI generation for that org (manual report creation still works) and surfaces an upgrade prompt.
- A report can be marked `aiAssisted: true/false` and stores `aiCreditsUsed`.

---

### Epic F — Scan Types & Templates (UR-027–UR-030)

**Goal:** Comprehensive, structured coverage of ultrasound exam types.

Support the full scan type catalogue (see `TECHNICAL_ARCHITECTURE.md` §2 for the enum):
- Abdominal & Pelvic (5 types)
- Obstetric (5 types)
- Gynecological (2 types)
- Cardiac (2 types)
- Specialized (8 types: MSK, Neck, Thyroid, Breast, Chest, Scrotal, Penile, Neonatal Head)
- Vascular/Doppler (arterial + venous, multiple limb combinations)
- General (free-form, for anything not covered)

Each template defines organ/structure-specific sections. Doppler templates include a velocity measurement table (columns for measurement + finding). General Report type provides an open free-form section.

**Acceptance criteria:**
- Selecting any of the 27+ scan types loads the correct structured field set.
- Doppler scan types render a measurement table, not just free text.

---

### Epic G — Report Creation (SonoScribe Interface) (UR-031–UR-039)

**Goal:** Fast, structured, flexible report authoring.

- New report entry points: from scratch, from a SonoVault template, or via SonoScribe AI.
- Mandatory fields: Patient Name, Patient Age, Scan Date (auto-populated, editable), Scan Type.
- Per-organ/structure fields: name, findings (text), measurements (structured numeric where applicable).
- "Impression (Summary)" field.
- "Recommendations" section: predefined options per scan type + free-text.
- "Suggest Impression" AI button generates impression text from entered findings.
- Helper tools: Clinical Differential, Impression Generator, Wording Assistant, Verified References.
- **No voice dictation** — explicitly excluded, do not build.
- Auto-save every 2 minutes.

**Acceptance criteria:**
- Killing the browser/tab mid-report loses no more than 2 minutes of work on reload.
- "Suggest Impression" only activates once findings have content (don't call the AI on an empty form).

---

### Epic H — Report Finalization & Output (UR-040–UR-045)

**Goal:** Produce a complete, validated, professionally branded, exportable report.

- Pre-finalization preview: letterhead, patient info, findings/measurements, impression, recommendations, signature block.
- Designation selection at finalize time (5 options, see Epic C).
- Signature selection from the user's uploaded signatures.
- Mandatory-field validation blocks finalization until complete.
- Export formats: **PDF**, **DOCX**, **HL7** (see `TECHNICAL_ARCHITECTURE.md` §5 for HL7 mapping notes).
- Hospital letterhead rendered on every exported format where applicable (PDF/DOCX; HL7 is data-only).

**Acceptance criteria:**
- Attempting to finalize with a missing mandatory field returns a clear, field-level error, not a generic failure.
- All three export formats are byte-for-byte reproducible from the same finalized report record (i.e., export is a rendering step, not a separate data path).

---

### Epic I — SonoVault (Template Storage) (UR-046–UR-058)

**Goal:** Personal + shared template library that's fast to search and safe from PHI leakage.

- Personal vault per user.
- Upload existing reports as templates: PDF, DOCX, TXT.
- Bulk upload: CSV, JSON, or multi-file.
- **Auto PHI stripping** on save-as-template: strip patient name/age/dates, flag `phiFree: true`. This must be enforced server-side, not just a UI checkbox.
- Custom folders/categories by scan type; custom tags (complexity, clinical indication, normal/abnormal).
- Search by keyword/scan type/tag/date range; filter by category/favorite/usage frequency; sort by date/alphabetical/most-used; grid or list view with thumbnails.
- "Favorite" marking; "Recently Used" (last 10).
- Edit/duplicate/delete with confirmation prompts.
- Version history with restore.
- Share templates with org colleagues while retaining ownership (shared ≠ transferred).

**Acceptance criteria:**
- A template saved from a report containing a patient name never surfaces that name anywhere in the vault UI or API response.
- Search returns results in < 2s at 5,000 templates (NFR, see §8).
- Deleting a shared template from the owner's vault either blocks (with a warning) or cascades predictably — decide and document the behavior; don't leave it undefined.

---

### Epic J — SonoShare (Collaboration) (UR-059–UR-064)

**Goal:** Structured peer consultation on complex cases.

- Upload in-progress/completed scans to SonoShare.
- Share scope: specific colleagues, department-wide, org-wide.
- Case description + question/concern + optional urgency level.
- Colleagues can: view scan + clinical context, comment, suggest impressions, annotate images (if image support exists).
- Notification on new feedback.
- Mark case "Resolved."
- Full audit trail: who accessed what, what feedback was given.

**Acceptance criteria:**
- A user outside the specified share scope cannot access the shared case (enforce server-side, same as Epic A's tenant isolation pattern).
- Audit trail entries are immutable (append-only) and queryable by case.

---

### Epic K — Subscription Tiers & Billing (UR-065–UR-068)

**Goal:** Enforceable tiered plans with predictable upgrade paths.

| Tier | Price | Users | Storage | AI credits/mo | Notable extras |
|---|---|---|---|---|---|
| Basic | GHC 1,000/mo | 5 | 250 MB | 1,000 | Core features |
| Pro | GHC 1,500/mo | 15 | 600 MB | 1,500 | + Auto-Grammar Check |
| Ultimate | GHC 2,500/mo | 25 | 1 GB | 2,000 | + Auto-Grammar Check + admin features |

Add-ons: extra storage (from GHC 300/mo), extra AI credits (from GHC 200/mo), Lite EMR integration (from GHC 1,000/mo).

- Enforce user-count limits at invite/creation time — block over-limit user creation with an upgrade prompt.
- Enforce storage and AI credit limits with proactive alerts before hard blocking.

**Acceptance criteria:**
- Attempting to add a 6th user on Basic is blocked with a clear upgrade CTA, not a silent failure or crash.
- Storage/credit alerts fire at a sensible threshold (e.g. 80%) before the hard limit.

---

## 4. Non-functional requirements (NFRs)

See `TECHNICAL_ARCHITECTURE.md` for implementation detail. Summary:

| Category | Requirement |
|---|---|
| Usability | Responsive (desktop/tablet/mobile); new report in 3–5 min; first report within 30 min of training; keyboard shortcuts; medical term autocomplete |
| Performance | AI generation 5–10s; vault search <2s @5k templates; template load <1s; tier-based concurrency (5/15/25 users) |
| Security | HIPAA compliance; TLS 1.3+ in transit; AES-256 at rest; strict tenant isolation; strong passwords; optional MFA; RBAC; auto PHI detection; full audit logging; 15-min session timeout; GDPR-ready |
| Reliability | 99.9% uptime (clinical hours); incremental backups every 6h + daily full; ≤2 min work loss on failure; 4h RTO |
| Scalability | 500+ users across orgs; tenant-scoped DB indexes; storage caps per tier |

---

## 5. Explicitly out of scope (v2.0)

- Voice dictation (UR-038 — explicitly removed)
- DICOM image integration
- Advanced AI analytics
- EMR/EHR integrations beyond HL7
- Native iOS/Android apps

These are deferred to a future roadmap (UR-098) — do not implement now, but avoid architectural decisions that would block them later.

---

## 6. Data model reference

Full JPA entity definitions in `TECHNICAL_ARCHITECTURE.md` §2, covering: `Organization`, `User`, `Signature`, `Report`, plus enums for `SubscriptionTier`, `UserRole`, `Designation`, `ScanType`. Additional entities needed but not fully specified in the source URD (agent must design consistent with existing patterns): `VaultTemplate`, `SharedScan`, `Collaboration`, `AuditLog` — see architecture doc for proposed shapes.

---

## 7. Key risks / open questions to resolve before/during build

1. **AI provider fallback behavior** — what happens if OpenAI and Gemini are both unavailable mid-generation? (Recommend: save draft as manual entry, surface a retry option.)
2. **HL7 export scope** — which HL7 message type (e.g. ORU^R01) and version (2.x) is targeted? Confirm with whoever owns EMR integration expectations.
3. **Shared template ownership on deletion** — resolve the SonoVault sharing edge case (Epic I) before building the delete flow.
4. **MFA method** — TOTP authenticator app assumed (UR-085); confirm SMS is not required.
5. **GDPR scope** — UR-090 is Medium priority; confirm whether this ships in v2.0 or is tracked separately.

Flag these to the product owner rather than guessing silently if they materially change scope.

---

## 8. Priority legend (from URD)

- **Critical** — blocking for v2.0 release
- **High** — required for a credible v2.0 release, but not launch-blocking day one
- **Medium** — should ship in v2.0 if time allows
- **Low** — backlog / nice-to-have

Full UR-by-UR priority mapping is in `IMPLEMENTATION_CHECKLIST.md`.

---

## 9. Acceptance criteria (release-level)

Copied/organized from URD §5 — treat as the Definition of Done for the v2.0 release as a whole, on top of the epic-level acceptance criteria above.

**Functional completeness**
- UR-001–UR-098 implemented and validated
- Multi-tenant signup, config, user management functional
- Letterhead customization and branding working
- SonoScribe produces clinically structured, accurate reports
- All 27+ scan templates available with correct sections
- Signature upload/management/application functional
- SonoVault storage/search/bulk upload working
- SonoShare enables case sharing and feedback
- Subscription tier enforcement operational

**Regulatory & security**
- HIPAA compliance certified by independent auditor
- Tenant isolation verified via penetration testing
- TLS 1.3 / AES-256 encryption validated
- PHI detection prevents patient data leaking into templates
- Audit logging covers all required activity categories
- No high-severity vulnerabilities outstanding

**Performance & reliability**
- AI generation <10s
- Vault search <2s @ 5,000 templates
- Concurrency targets met per tier without degradation
- 99.9% uptime over a 30-day pilot
- Auto-save prevents data loss in simulated failure tests

**Usability & UAT**
- Admins configure letterhead/users successfully
- Sonographers create AI-assisted reports in <5 min
- 85%+ satisfaction across 20+ sonographer UAT participants
- 90% of users complete a first report within 30 min of training

**Integration & interoperability**
- PDF/DOCX/HL7 exports correctly formatted
- Letterheads render correctly across all export formats
- Signatures render professionally on finalized reports

**Clinical validation**
- AI-generated reports reviewed by radiologists for accuracy
- Structure/terminology meet clinical standards
- No adverse events attributed to system use during pilot
