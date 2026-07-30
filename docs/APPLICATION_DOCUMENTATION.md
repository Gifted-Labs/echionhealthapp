# Echion Health System - Application Documentation

**Version:** 2.0
**Date:** July 30, 2026

---

## 1. Overview

Echion Health System is a multi-tenant healthcare application designed for sonographers, radiologists and physicians to:
- **Create and manage ultrasound scan reports** efficiently (manually, from templates, or AI-assisted)
- **Build an organization vault** of reports and reusable templates
- **Collaborate with colleagues** through SonoShare for peer review
- **Track adoption** through an org-scoped analytics dashboard

### Core Modules
| Module | Description |
|--------|-------------|
| **SonoVault** | Report storage, search, versioning, export, and template management |
| **SonoScribe** | AI report generation, impression suggestion, and grammar check |
| **SonoShare** | Collaboration space for sharing scans, peer feedback, and case audit trails |
| **Branding** | Hospital profile and letterhead applied to finalized reports |
| **Billing** | Plan tiers, quota enforcement, add-ons, and usage reporting |
| **Admin** | Tenant user management, audit logs, AI operations, and system monitoring |
| **Analytics** | Org-scoped adoption and AI-usage metrics |

### Tenancy model

Every authenticated request carries an `organizationId` resolved from the JWT principal. All
reads and writes are scoped to that organization — a user can only see reports, shares,
notifications, templates and audit logs belonging to their own tenant. `ADMIN` and
`SUPER_ADMIN` are platform-level roles and see across organizations where the endpoint
explicitly allows it (currently: analytics dashboard, admin user management, billing tier
changes).

### Roles

`HOSPITAL_ADMIN`, `SONOGRAPHER`, `RADIOLOGIST`, `PHYSICIAN`, `ADMIN`, `SUPER_ADMIN`

---

## 2. Technology Stack

### Backend
| Component | Technology | Version |
|-----------|------------|---------|
| Runtime | Java | 21 |
| Framework | Spring Boot | 4.0.6 |
| Database | PostgreSQL | 42.7.8 (driver) |
| Security | Spring Security + JWT | jjwt 0.12.5 |
| Caching | Caffeine | (Spring Boot managed) |
| API Docs | SpringDoc OpenAPI | 3.0.1 |
| Testing | JUnit 5 + Testcontainers | 1.20.4 |

### External Services
| Service | Provider | Purpose |
|---------|----------|---------|
| Object Storage | Cloudflare R2 (S3-compatible) | File storage |
| Email | Resend API | Email verification & notifications |
| AI | Gemini (default), OpenAI (fallback) | Report generation, impressions, grammar |
| Database Hosting | Railway PostgreSQL | Production database |

### Document Processing
| Library | Purpose |
|---------|---------|
| Apache POI 5.2.5 | Word document processing |
| Apache PDFBox 3.0.1 | PDF generation |
| Apache Tika 2.9.1 | Content type detection |

---

## 3. API Reference

**Base URL:** `http://localhost:8080/api` (dev) or `https://your-domain.com/api` (prod)
The `/api` prefix comes from `server.servlet.context-path` and is **not** repeated in the
controller paths below.

**Auth:** all endpoints except `/auth/register`, `/auth/verify-email`,
`/auth/resend-verification`, `/auth/login` and `/auth/refresh` require an
`Authorization: Bearer <accessToken>` header.

**Envelope:** every JSON endpoint returns the shared `ApiResponse` wrapper:

```json
{
  "success": true,
  "message": "optional human-readable message",
  "data": { }
}
```

Paginated endpoints put a Spring `Page` object in `data` and accept the standard
`?page=&size=&sort=` query parameters.

### 3.1 Authentication (`/auth`)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/auth/register` | Register an organization + its first `HOSPITAL_ADMIN` | ❌ |
| POST | `/auth/verify-email?token=` | Verify email | ❌ |
| POST | `/auth/resend-verification?email=` | Resend verification email | ❌ |
| POST | `/auth/login` | Login (returns JWT pair) | ❌ |
| POST | `/auth/refresh` | Refresh access token | ❌ |
| POST | `/auth/logout` | Logout and revoke refresh token | ✅ |
| GET | `/auth/profile` | Get current user profile | ✅ |
| POST | `/auth/complete-profile` | Add professional details | ✅ |
| PATCH | `/auth/profile` | Partial profile update | ✅ |

**Login Request** — either `email`, or `username` plus `organizationName`:
```json
{
  "email": "user@example.com",
  "password": "yourpassword"
}
```

**Login Response:**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "abc123...",
    "user": { }
  }
}
```

#### Multi-factor authentication (TOTP)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/auth/mfa` | Get MFA status for the current user |
| POST | `/auth/mfa/setup` | Generate a TOTP secret + `otpauth://` URL |
| POST | `/auth/mfa/enable` | Verify a TOTP code and enable MFA |
| POST | `/auth/mfa/disable` | Verify a TOTP code and disable MFA |

---

### 3.2 SonoVault — Reports (`/vault`)

All roles except platform-only operators may call these; every report is org-scoped.

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/vault/upload` | Upload a PDF/DOCX document (multipart) |
| POST | `/vault/upload-batch` | Upload multiple documents (multipart) |
| POST | `/vault/reports` | Create report manually or from a template |
| GET | `/vault/reports/{id}` | Get a report |
| PUT | `/vault/reports/{id}` | Update a report (creates a version) |
| POST | `/vault/reports/{id}/autosave` | Persist draft content without versioning |
| DELETE | `/vault/reports/{id}` | Delete a report |
| POST | `/vault/reports/search` | Search with filters |
| GET | `/vault/reports/recent` | 10 most recent reports |
| POST | `/vault/reports/helpers` | Impression suggestion, differentials, wording, references |
| GET | `/vault/reports/{id}/versions` | Version history |
| GET | `/vault/reports/{id}/versions/{versionNumber}` | Get a specific version |
| POST | `/vault/reports/{id}/versions/{versionNumber}/restore` | Restore a version |
| POST | `/vault/reports/{id}/finalize` | Apply a signature and finalize |
| POST | `/vault/reports/{id}/preview` | Preview the finalized output without committing |
| GET | `/vault/reports/{id}/download` | Download the original uploaded file |
| GET | `/vault/reports/{id}/download-pdf` | Export as PDF |
| GET | `/vault/reports/{id}/download-docx` | Export as Word |
| GET | `/vault/reports/{id}/download-hl7` | Export as HL7 v2.x `ORU^R01` |
| GET | `/vault/reports/{id}/print` | Print-friendly PDF |

**Create Report Request:**
```json
{
  "patientName": "John Doe",
  "patientAge": 45,
  "gender": "MALE",
  "scanType": "ABDOMINAL",
  "scanDate": "2026-07-30",
  "findings": "Normal liver, gallbladder...",
  "impression": "No abnormalities detected",
  "templateId": "optional-template-id"
}
```

Finalized exports carry the organization's letterhead and the signature selected at
finalize time — see §3.6 (Branding) and §3.5 (Signatures).

---

### 3.3 SonoVault — Templates (`/vault/templates`)

> **Path change:** templates moved from `/templates` to `/vault/templates`.

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/vault/templates` | List available templates |
| POST | `/vault/templates` | Create a custom template |
| PUT | `/vault/templates/{id}` | Update a template |
| DELETE | `/vault/templates/{id}` | Delete a template (`?cascadeSharedAccess=true` to force-delete a shared one) |
| POST | `/vault/templates/upload` | Upload a template from PDF/DOCX/TXT (multipart) |
| POST | `/vault/templates/bulk-upload` | Upload multiple template files (multipart) |
| POST | `/vault/templates/import/json` | Import templates from JSON (multipart) |
| POST | `/vault/templates/import/csv` | Import templates from CSV (multipart) |
| POST | `/vault/templates/search` | Search by keyword, scan type, tag, category, date, favorites, sort |
| GET | `/vault/templates/favorites` | Favorite templates |
| GET | `/vault/templates/recent` | 10 most recently used |
| PUT | `/vault/templates/{id}/favorite` | Mark/unmark as favorite |
| POST | `/vault/templates/{id}/duplicate` | Duplicate a template |
| POST | `/vault/templates/{id}/share` | Share with colleagues |
| GET | `/vault/templates/analytics` | Template usage statistics |
| GET | `/vault/templates/{id}/versions` | Version history |
| POST | `/vault/templates/{id}/versions/{versionNumber}/restore` | Restore a version |

Deleting a template that other users hold access to is rejected unless
`cascadeSharedAccess=true` is passed, so a shared template is never silently removed from
someone else's library.

---

### 3.4 SonoVault — Folders & Scan Types

> **Path change:** folders moved from `/folders` to `/vault/folders`.

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/vault/folders` | Top-level folders |
| POST | `/vault/folders` | Create a folder |
| GET | `/vault/folders/{id}/subfolders` | Folders inside a parent folder |
| GET | `/vault/scan-types` | All supported scan types and their field definitions |
| GET | `/vault/scan-types/{scanType}` | Field definitions for one scan type |

---

### 3.5 Signatures (`/signatures`)

Uploading requires the user's `canUploadSignature` permission, granted by an admin via
`PUT /admin/users/{id}/signature-permission`.

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/signatures` | List the current user's signatures |
| POST | `/signatures` | Upload a signature image (multipart) |
| PATCH | `/signatures/{id}` | Update signature metadata |
| DELETE | `/signatures/{id}` | Delete a signature |

---

### 3.6 Organization Branding (`/org`)

Roles: `HOSPITAL_ADMIN`, `ADMIN`, `SUPER_ADMIN`.

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/org/branding` | Current hospital profile + letterhead configuration |
| PATCH | `/org/branding` | Update branding fields used on reports |
| POST | `/org/letterhead` | Upload a letterhead/logo image (multipart) |
| GET | `/org/letterhead/preview` | Preview the branding applied to generated reports |

---

### 3.7 SonoScribe — AI (`/generate-report`)

Clinical roles only.

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/generate-report` | Generate findings, impression and recommendations from raw notes |
| POST | `/generate-report/suggest-impression` | Generate an impression from findings |
| POST | `/generate-report/grammar-check` | Auto-grammar check (Pro/Ultimate plans) |

**Credits.** Each generation consumes AI credits from the organization's monthly quota and
is rejected with `SubscriptionLimitExceededException` once the plan limit is reached.
A generation that produces nothing is refunded, so failed calls are not billed. The
response reports `aiCreditsConsumed`, `provider`, `model`, `fallbackUsed`,
`promptTemplateVersion` and `processingTimeSeconds`.

**Provider.** Defaults come from configuration — `AI_DEFAULT_PROVIDER` (Gemini) with
automatic fallback to `AI_FALLBACK_PROVIDER` when `AI_ALLOW_FALLBACK=true`. A request may
override the default with an optional `provider` field.

---

### 3.8 SonoShare — Collaboration (`/collaboration`)

Roles: `HOSPITAL_ADMIN`, `SONOGRAPHER`, `RADIOLOGIST`, `PHYSICIAN`, `ADMIN`, `SUPER_ADMIN`
(enforced at the controller level). All shares, comments and notifications are org-scoped.

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/collaboration/share` | Share a report for peer review (JSON) |
| POST | `/collaboration/share-with-image` | Share an image, optionally with a report (multipart) |
| GET | `/collaboration/shared-with-me` | Scans shared with the current user |
| GET | `/collaboration/my-shares` | Scans the current user has shared |
| GET | `/collaboration/{id}` | Shared scan details |
| GET | `/collaboration/{id}/audit-trail` | **New** — append-only audit events for a shared case (paginated) |
| POST | `/collaboration/{id}/comments` | Add a comment or suggested impression |
| GET | `/collaboration/{id}/comments` | Get comments |
| PUT | `/collaboration/{id}/resolve` | Mark the case as resolved |

**Sharing Levels:**
| Value | Meaning |
|-------|---------|
| `SPECIFIC_COLLEAGUES` | Named users; `colleagueIds` is required |
| `DEPARTMENT` | **New** — all users in the given `department` within the organization |
| `EVERYONE` | Everyone in the organization |
| `ORGANIZATION_WIDE` | **New** — explicit alias of `EVERYONE`, matching the v2.0 requirements language |

`EVERYONE` no longer means "all users on the system" — it is bounded by the organization.

**Urgency Levels (new):** `LOW`, `MEDIUM`, `HIGH`, `URGENT`. Defaults to `MEDIUM` when omitted.

**Share Request (JSON):**
```json
{
  "reportId": "report-uuid",
  "sharingLevel": "DEPARTMENT",
  "department": "Radiology",
  "urgency": "HIGH",
  "colleagueIds": [],
  "title": "Need a second read",
  "requestMessage": "Please review the hepatic findings"
}
```
`department` is only stored when `sharingLevel` is `DEPARTMENT`; `colleagueIds` is required
when `sharingLevel` is `SPECIFIC_COLLEAGUES`.

**Share with Image (multipart)** — now accepts `urgency` and `department` parts:
```
POST /api/collaboration/share-with-image
Content-Type: multipart/form-data

image:          [file]
reportId:       optional-report-uuid
sharingLevel:   DEPARTMENT
department:     Radiology
urgency:        URGENT
colleagueIds:   []
title:          Need feedback
requestMessage: Please review this scan
```

**Add Comment Request** — supports flagging a comment as a suggested impression:
```json
{
  "content": "Consider hepatic steatosis grade II",
  "annotationData": null,
  "parentId": null,
  "isSuggestedImpression": true
}
```
`ScanCommentResponse` echoes `isSuggestedImpression` so the UI can render suggestions
distinctly from ordinary comments.

**Audit trail response** — a page of audit log entries filtered to the shared case,
newest first, each with `userId`, `userEmail`, `action`, `details`, `ipAddress`,
`userAgent`, `success`, `errorMessage`, `createdAt`. Callers who cannot access the shared
scan get `403`.

---

### 3.9 Notifications (`/collaboration/notifications`)

Notification queries are scoped by **both** user and organization, so a user moved between
tenants never sees carried-over notifications.

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/collaboration/notifications/stream` | SSE real-time stream (`text/event-stream`) |
| GET | `/collaboration/notifications` | Paginated notifications |
| GET | `/collaboration/notifications/unread` | All unread notifications |
| GET | `/collaboration/notifications/unread-count` | Unread count |
| PUT | `/collaboration/notifications/{id}/read` | Mark one as read |
| PUT | `/collaboration/notifications/read-all` | Mark all as read |

---

### 3.10 Billing (`/billing`)

Roles: `HOSPITAL_ADMIN`, `ADMIN`, `SUPER_ADMIN` — with tier and add-on changes restricted
to `SUPER_ADMIN` (platform operators).

| Method | Endpoint | Description | Role |
|--------|----------|-------------|------|
| GET | `/billing/plan` | Current plan and limits | Admin |
| GET | `/billing/usage` | Users, storage and AI credits vs. limits | Admin |
| POST | `/billing/upgrade-request` | Request a plan change | Admin |
| POST | `/billing/upgrade` | Change an organization's tier | `SUPER_ADMIN` |
| POST | `/billing/addons` | Apply add-ons | `SUPER_ADMIN` |

Add-ons are capped by `BILLING_MAX_ADDON_STORAGE_MB` and `BILLING_MAX_ADDON_AI_CREDITS`.

---

### 3.11 Admin (`/admin`)

Base roles: `HOSPITAL_ADMIN`, `ADMIN`, `SUPER_ADMIN`. `HOSPITAL_ADMIN` operates only
within its own organization.

| Method | Endpoint | Description | Role |
|--------|----------|-------------|------|
| GET | `/admin/dashboard` | System statistics | Admin |
| POST | `/admin/users` | Create a tenant-scoped user | `HOSPITAL_ADMIN`, `SUPER_ADMIN` |
| GET | `/admin/users` | Paginated user list with filters | Admin |
| GET | `/admin/users/{id}` | User details | Admin |
| PUT | `/admin/users/{id}/role` | Change a user's role | `HOSPITAL_ADMIN`, `SUPER_ADMIN` |
| PUT | `/admin/users/{id}/lock` | Lock an account | Admin |
| PUT | `/admin/users/{id}/unlock` | Unlock an account | Admin |
| PUT | `/admin/users/{id}/deactivate` | Soft-deactivate, preserving history | `HOSPITAL_ADMIN`, `SUPER_ADMIN` |
| PUT | `/admin/users/{id}/reactivate` | Restore a deactivated account | `HOSPITAL_ADMIN`, `SUPER_ADMIN` |
| PUT | `/admin/users/{id}/signature-permission` | Grant/revoke signature upload | `HOSPITAL_ADMIN`, `SUPER_ADMIN` |
| DELETE | `/admin/users/{id}` | Permanently delete a user | `SUPER_ADMIN` |
| GET | `/admin/audit-logs` | Paginated audit logs with filters | Admin |
| GET | `/admin/audit-logs/user/{userId}` | Audit logs for one user | Admin |
| GET | `/admin/audit-logs/actions` | Available action types for filtering | Admin |
| POST | `/admin/rebuild-search-vectors` | Rebuild search vectors | `SUPER_ADMIN` |

> **Path change:** dashboard stats moved from `/admin/dashboard/stats` to `/admin/dashboard`.

#### AI operations (`/admin/ai`)

| Method | Endpoint | Description | Role |
|--------|----------|-------------|------|
| GET | `/admin/ai/usage` | AI usage and cost report | Admin |
| POST | `/admin/ai/verify` | Health-check configured AI providers | `ADMIN`, `SUPER_ADMIN` |

---

### 3.12 Analytics (`/analytics`)

| Method | Endpoint | Description | Role |
|--------|----------|-------------|------|
| GET | `/analytics/dashboard` | Adoption and AI-usage metrics | `HOSPITAL_ADMIN`, `ADMIN`, `SUPER_ADMIN` |

`HOSPITAL_ADMIN` was added to this endpoint, and the response is now **scoped to the
caller's organization**. `ADMIN` and `SUPER_ADMIN` still receive platform-wide totals.

**⚠️ Breaking response change.** The payload previously included invented clinical and
financial outcomes — a hardcoded 12% AI edit rate that drove the acceptance and revision
rates, plus fixed `productivityGain`, `clinicalErrorReduction`, `diagnosticDelayReduction`,
`monthlyStaffSavings`, `additionalReportsCapacity` and `revenueImpact` figures carried over
from a design mock. Those fields have been **removed** rather than re-derived, because the
platform does not collect the pre-adoption baseline or cost data needed to compute them.
Clients reading any of those fields must stop.

Also removed: `totalHoursSaved` (replaced by `estimatedHoursSaved`),
`aiReportsRejected`, `avgReportsPerUserTrend`, `totalReportsPerDay`, `minorEdits`,
`majorEdits`, `averageTurnaroundTimeMinutes`, `slaCompliant`.

**Response:**
```json
{
  "success": true,
  "data": {
    "aiReportAcceptanceRate": 87.5,
    "reportRevisionRate": 12.5,
    "aiUsageRate": 64.2,
    "totalReports": 1280,
    "totalAiReports": 822,
    "totalManualReports": 458,
    "aiReportsAccepted": 719,
    "aiReportsEdited": 103,
    "dailyActiveUsers": 34,
    "avgReportsPerUser": 18.3,
    "averageAiGenerationSeconds": 6.4,
    "estimatedHoursSaved": 342.5,
    "estimatedMinutesSavedPerAiReport": 25.0,
    "allMetricsMeasured": false,
    "unavailableMetricsNote": "Clinical error reduction, diagnostic delay reduction, staff savings and revenue impact are not reported: computing them requires a pre-adoption baseline and cost data that this platform does not collect."
  }
}
```

| Field | Basis |
|-------|-------|
| `aiReportAcceptanceRate` | Measured — AI reports not edited before finalize, over all AI reports |
| `reportRevisionRate` | Measured — AI reports whose findings/impression/recommendation were edited |
| `aiUsageRate` | Measured — AI reports over total reports |
| `totalReports` / `totalAiReports` / `totalManualReports` | Measured counts |
| `aiReportsAccepted` / `aiReportsEdited` | Measured counts |
| `dailyActiveUsers` | Measured — distinct users in audit logs over the last 24h |
| `avgReportsPerUser` | Measured — total reports over total users in scope |
| `averageAiGenerationSeconds` | Measured — may be `null` when no AI reports exist yet |
| `estimatedHoursSaved` | **Estimate** — `totalAiReports × estimatedMinutesSavedPerAiReport` |
| `estimatedMinutesSavedPerAiReport` | Configuration (`ANALYTICS_MINUTES_SAVED_PER_AI_REPORT`, default 25) |
| `allMetricsMeasured` | `false` whenever any returned value is an estimate — the UI must label it |
| `unavailableMetricsNote` | Why outcome/financial metrics are absent |

Render `estimatedHoursSaved` with a visible "estimated" label and surface
`estimatedMinutesSavedPerAiReport` alongside it, so the assumption is judgeable rather than
presented to hospital administrators as a measurement.

---

## 4. Configuration

### Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `DB_URL` | PostgreSQL connection URL | ✅ |
| `DB_USERNAME` | Database username | ✅ |
| `DB_PASSWORD` | Database password | ✅ |
| `JWT_SECRET` | JWT signing key (min 64 chars) | ✅ |
| `ENCRYPTION_KEY` | Field-level encryption key | ✅ |
| `RESEND_API_KEY` | Resend email API key | ✅ |
| `ADMIN_PASSWORD` | Bootstrap super-admin password | ✅ |
| `STORAGE_TYPE` | `local` or `r2` | ✅ |
| `R2_BUCKET_URL` | R2 S3 endpoint URL | ✅ (prod) |
| `R2_BUCKET_NAME` | Bucket name | ✅ (prod) |
| `R2_ACCESS_KEY_ID` | R2 access key | ✅ (prod) |
| `R2_SECRET_ACCESS_KEY` | R2 secret key | ✅ (prod) |
| `AI_DEFAULT_PROVIDER` | `GEMINI` (default) or `OPENAI` | ➖ |
| `AI_FALLBACK_PROVIDER` | Provider used when the default fails | ➖ |
| `AI_ALLOW_FALLBACK` | Enable provider fallback (default `true`) | ➖ |
| `GEMINI_API_KEY` | Gemini API key | ✅ (if Gemini enabled) |
| `OPENAI_API_KEY` | OpenAI API key | ✅ (if OpenAI enabled) |
| `ANALYTICS_MINUTES_SAVED_PER_AI_REPORT` | Assumption behind `estimatedHoursSaved` (default `25`) | ➖ |
| `BILLING_MAX_ADDON_STORAGE_MB` | Add-on storage cap (default `102400`) | ➖ |
| `BILLING_MAX_ADDON_AI_CREDITS` | Add-on AI credit cap (default `100000`) | ➖ |

---

## 5. Running the Application

### Development
```bash
# Set environment variables (or use .env file)
export DB_URL=jdbc:postgresql://localhost:5432/echoinhealth
export DB_USERNAME=postgres
export DB_PASSWORD=yourpassword
export JWT_SECRET=your-64-character-secret-key-here
export RESEND_API_KEY=re_xxxxx
export GEMINI_API_KEY=xxxxx
export STORAGE_TYPE=local

# Run
./mvnw spring-boot:run
```

### Production (Railway)
1. Push code to GitHub
2. CI runs build, tests, Qodana and Trivy quality gates (see `docs/CI_CD_PIPELINE.md`)
3. Set environment variables in the Railway dashboard
4. Deploy on a green pipeline

---

## 6. API Documentation (Swagger)

Access interactive API documentation at:
- **Local:** `http://localhost:8080/api/swagger-ui.html`
- **Production:** `https://your-domain.com/api/swagger-ui.html`

---

## 7. Database Schema

### Core Entities
| Entity | Description |
|--------|-------------|
| `Organization` | Tenant root; owns branding, plan and quotas |
| `User` | User accounts with roles, MFA, and signature permission |
| `Report` | Scan reports (AI generation status, AI-output-edited flag, processing time) |
| `ReportTemplate` | Saved templates with version history |
| `Folder` | Report organization |
| `Signature` | Uploaded clinician signatures applied at finalize |
| `SharedScan` | Collaboration shares (sharing level, urgency, target department) |
| `SharedScanAccess` | Per-user access grants to a shared scan |
| `ScanComment` | Comments on shared scans (threaded, suggested-impression flag) |
| `CollaborationNotification` | User notifications (org-scoped) |
| `AuditLog` | Activity audit trail, also backing the SonoShare case audit trail |

---

## 8. Security

- **Authentication:** JWT Bearer tokens, optional TOTP MFA
- **Authorization:** Role-based — `HOSPITAL_ADMIN`, `SONOGRAPHER`, `RADIOLOGIST`, `PHYSICIAN`, `ADMIN`, `SUPER_ADMIN`
- **Tenancy:** every query is scoped by `organizationId`; cross-tenant reads are only possible for platform roles on endpoints that explicitly allow it
- **Password:** BCrypt hashing
- **Session:** Stateless JWT (1 hour access, 24 hour refresh)
- **CORS:** Configured for allowed origins
- **Audit:** All actions logged to `AuditLog`; SonoShare cases expose an append-only trail via `/collaboration/{id}/audit-trail`
