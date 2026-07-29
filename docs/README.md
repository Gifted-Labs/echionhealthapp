# Echion Health — v2.0 Upgrade

Multi-tenant AI-assisted ultrasound reporting platform. This repo/branch implements the **v2.0** feature set defined in the Updated User Requirements Document (URD v2.0, May 2026), moving the product from a single-tenant report tool to a full multi-hospital SaaS platform.

> This README is written for the engineering agent implementing the upgrade. Pair it with `PRD.md` (what to build and why), `IMPLEMENTATION_CHECKLIST.md` (the ordered task list), and `TECHNICAL_ARCHITECTURE.md` (schema, API contracts, integration details).

---

## 1. What's new in v2.0

| Area | v1.0 | v2.0 |
|---|---|---|
| Tenancy | Single org | Multi-tenant, org-isolated |
| Branding | None | Custom letterhead/logo per hospital |
| Roles | Flat | Hospital Admin, Sonographer, Radiologist, System Admin |
| Signatures | None | Per-user, multi-signature, admin-authorized |
| Report generation | Manual | AI-assisted (SonoScribe) via OpenAI 4.0 Mini / Gemini |
| Scan types | ~5 | 27+ specialized templates |
| Templates | None | SonoVault personal/shared vault |
| Collaboration | None | SonoShare peer review/consultation |
| Billing | Flat | Basic / Pro / Ultimate tiers + add-ons |
| Voice dictation | N/A | Explicitly **excluded** (UR-038) |

Full requirement-by-requirement detail lives in `PRD.md`.

---

## 2. Core modules

- **SonoScribe** — AI-assisted structured report drafting (findings / impression / recommendations)
- **SonoVault** — personal + shared template library, PHI-stripped on save
- **SonoShare** — case sharing, comments, consult workflow
- **Hospital Customization** — letterhead, logo, hospital profile, report branding
- **Signature Management** — multi-signature upload, admin authorization, audit trail
- **Subscription & Billing** — tiered plans (Basic/Pro/Ultimate) + metered add-ons

---

## 3. Tech stack

- **Backend:** Java 17+, Spring Boot 3.x (Spring MVC, Spring Data JPA, Spring Security 6)
- **Persistence:** PostgreSQL, Hibernate/JPA, Flyway (or Liquibase — confirm existing choice) for migrations
- **AI providers:** OpenAI (`gpt-4.0-mini`) and/or Google Gemini API, called via a provider-abstraction service (configurable per org)
- **File storage:** S3-compatible bucket via AWS SDK v2 (letterheads, signatures, uploaded templates)
- **Auth:** Spring Security — JWT or server-side session (confirm which is already in use), org-scoped, optional TOTP MFA
- **Export:** PDF (OpenPDF/iText or PDFBox), DOCX (Apache POI), HL7 v2 (HAPI HL7)
- **API docs:** springdoc-openapi
- **Build:** Maven or Gradle (confirm existing choice)
- **Frontend:** Responsive web app (desktop/tablet/mobile) — separate from this backend scope unless your agent owns both

See `TECHNICAL_ARCHITECTURE.md` for full entity definitions, repository patterns, and REST controller surface. If any stack detail above doesn't match the real codebase, update that file first — everything else references it.

---

## 4. Environment variables / application properties (expected)

```bash
SPRING_DATASOURCE_URL=
SPRING_DATASOURCE_USERNAME=
SPRING_DATASOURCE_PASSWORD=
JWT_SECRET=
S3_BUCKET_NAME=
S3_ACCESS_KEY_ID=
S3_SECRET_ACCESS_KEY=
OPENAI_API_KEY=
GEMINI_API_KEY=
AI_PROVIDER_DEFAULT=openai            # openai | gemini
MFA_ISSUER_NAME=EchionHealth
SERVER_SERVLET_SESSION_TIMEOUT=15m
AUTO_SAVE_INTERVAL_SECONDS=120
```

Map these to `application.yml`/`application.properties` (or `application-{profile}.yml` per environment) following whatever convention the existing project already uses.

---

## 5. How to use these documents

1. **`PRD.md`** — read first. Defines every epic, user story, and acceptance criteria mapped to UR-001–UR-098.
2. **`TECHNICAL_ARCHITECTURE.md`** — data model, API endpoints, AI prompt contracts, export formats.
3. **`IMPLEMENTATION_CHECKLIST.md`** — hand this directly to your coding agent as the execution plan. It's ordered by dependency (tenancy → auth → branding → signatures → SonoScribe → scan types → vault → share → billing → NFRs) with checkboxes per UR.

Suggested agent workflow per phase:
1. Implement schema changes for the phase.
2. Implement API endpoints.
3. Implement UI.
4. Write tests against the acceptance criteria in `PRD.md`.
5. Check off items in `IMPLEMENTATION_CHECKLIST.md`.

---

## 6. Non-negotiable constraints

- **Data isolation:** no query may cross `organizationId` boundaries (UR-004, UR-083).
- **PHI handling:** patient name/age encrypted at rest; stripped automatically when saved as a SonoVault template (UR-049, UR-087).
- **No voice dictation** (UR-038) — do not implement even as a stretch feature.
- **Audit logging** is mandatory on: auth events, report lifecycle, template access, signature use, collaboration activity, admin actions (UR-088).
- **Session timeout:** 15 minutes idle (UR-089).
- **Encryption:** TLS 1.3+ in transit, AES-256 at rest (UR-081, UR-082).

---

## 7. Out of scope for v2.0

Explicitly deferred (UR-098), do not build now:
- DICOM image integration
- Advanced AI analytics
- EMR/EHR integrations beyond HL7
- Native iOS/Android apps
