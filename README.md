# Echion Health System

Echion Health System is a multi-tenant Spring Boot backend for ultrasound reporting workflows. It supports manual and AI-assisted report generation, reusable report templates, SonoShare peer collaboration, hospital branding, billing quotas, audit logs, and organization-scoped analytics.

The application is designed for sonographers, radiologists, physicians, hospital administrators, and platform operators. Every authenticated clinical workflow is scoped to an `organizationId` so reports, templates, notifications, shares, signatures, and audit records remain tenant-bounded by default.

## Contents

- [Core Capabilities](#core-capabilities)
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Repository Layout](#repository-layout)
- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
- [Local Development](#local-development)
- [Database Migrations](#database-migrations)
- [API Overview](#api-overview)
- [Testing](#testing)
- [Docker](#docker)
- [CI/CD](#cicd)
- [Security Notes](#security-notes)
- [Project Documentation](#project-documentation)

## Core Capabilities

### SonoVault

- Create, update, autosave, search, version, restore, finalize, and delete ultrasound reports.
- Upload PDF/DOCX source documents individually or in batches.
- Export finalized reports as PDF, DOCX, print-friendly PDF, or HL7 v2.x `ORU^R01`.
- Organize reports with folders and predefined scan-type field definitions.
- Manage reusable templates with import, upload, search, favorites, sharing, analytics, and version restore.

### SonoScribe

- Generate structured ultrasound report content from raw notes.
- Suggest impressions from findings.
- Run grammar checks for eligible subscription tiers.
- Route AI calls through Gemini by default, with optional OpenAI fallback.
- Track provider, model, prompt template version, processing time, and AI credit usage.

### SonoShare

- Share reports or image-backed cases with specific colleagues, departments, or the organization.
- Add comments, threaded replies, and suggested impressions.
- Resolve shared scans and expose an append-only case audit trail.
- Stream and manage collaboration notifications.

### Admin, Billing, and Analytics

- Manage tenant users, roles, locks, deactivation/reactivation, and signature permissions.
- Inspect audit logs and run search-vector rebuilds.
- View current subscription plan, quota usage, upgrade requests, and operator-managed add-ons.
- Track organization-scoped adoption metrics and AI usage.
- Verify live AI provider wiring from the admin operations API.

## Architecture

This repository contains the backend API only.

- Runtime: stateless Spring Boot REST API under the `/api` context path.
- Persistence: PostgreSQL with Flyway-managed schema migrations.
- Security: Spring Security, JWT access tokens, refresh tokens with idle timeout, BCrypt passwords, optional TOTP MFA, and method-level role checks.
- Tenancy: authenticated requests resolve the current user and organization through the JWT principal; repositories and services enforce organization-bounded reads/writes.
- Storage: local filesystem in development or S3-compatible Cloudflare R2 in production.
- AI: provider router supporting Gemini and OpenAI with prompt templates stored under `src/main/resources/prompts`.
- Observability: Spring Boot Actuator, structured audit logging, CI security scanning, and provider verification endpoints.

## Technology Stack

| Area | Technology |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 4.0.6 |
| API | Spring Web MVC, SpringDoc OpenAPI 3.0.1 |
| Security | Spring Security, JJWT 0.12.5, BCrypt, TOTP |
| Database | PostgreSQL, Spring Data JPA, Flyway |
| Caching | Spring Cache with Caffeine |
| File processing | Apache POI, PDFBox, Apache Tika |
| Object storage | Local filesystem or Cloudflare R2/S3-compatible storage |
| Email | Resend API |
| AI providers | Gemini and OpenAI |
| Tests | JUnit 5, Spring Boot Test, Testcontainers PostgreSQL, H2 for focused unit tests |
| Deployment | Dockerfile-based Railway deployment |

## Repository Layout

```text
.
+-- src/main/java/com/giftedlabs/echoinhealthbackend
|   +-- config/          # Spring, security, cache, Flyway, storage, AI configuration
|   +-- controller/      # REST controllers
|   +-- dto/             # Request and response DTOs
|   +-- entity/          # JPA entities and enums
|   +-- exception/       # Domain exceptions and global error handling
|   +-- repository/      # Spring Data repositories
|   +-- security/        # JWT filter and current-user helpers
|   +-- service/         # Domain services
|   +-- util/            # Parsing, tokens, encryption, email templates
+-- src/main/resources
|   +-- application.yaml
|   +-- application-dev.yaml
|   +-- db/migration/    # Flyway migrations
|   +-- prompts/         # AI prompt templates
+-- src/test             # Unit and integration tests
+-- docs/                # Product, architecture, CI, audit, and implementation docs
+-- Dockerfile
+-- pom.xml
+-- README.md
```

## Prerequisites

- Java 21.
- Docker, if running Testcontainers-backed tests or building the container image.
- PostgreSQL 14+ for local development.
- Maven is optional because the repository includes the Maven wrapper: `./mvnw`.
- Resend API key for real email delivery.
- Gemini and/or OpenAI API key for AI workflows.
- Cloudflare R2 credentials if `STORAGE_TYPE=r2`.

## Configuration

The application reads environment variables directly and also imports a root `.env` file through:

```yaml
spring.config.import: optional:file:.env[.properties]
```

Create a local `.env` file for development. Do not commit it.

```properties
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=8080

DB_URL=jdbc:postgresql://localhost:5432/echion_health
DB_USERNAME=postgres
DB_PASSWORD=postgres

JWT_SECRET=replace-with-at-least-64-characters-of-random-secret-material
ENCRYPTION_KEY=replace-with-a-32-byte-encryption-key

ADMIN_EMAIL=superadmin@echoinhealth.com
ADMIN_PASSWORD=replace-with-a-strong-bootstrap-password
ADMIN_ORGANIZATION_NAME=Echion Platform
ADMIN_HOSPITAL_NAME=Echion Health HQ

RESEND_API_KEY=re_xxxxx
EMAIL_FROM=noreply@echoinhealth.com
BASE_URL=http://localhost:8080

STORAGE_TYPE=local
LOCAL_STORAGE_PATH=./vault-storage

AI_DEFAULT_PROVIDER=GEMINI
AI_FALLBACK_PROVIDER=OPENAI
AI_ALLOW_FALLBACK=true
GEMINI_API_KEY=your-gemini-key
OPENAI_API_KEY=your-openai-key
```

### Important Environment Variables

| Variable | Required | Purpose |
| --- | --- | --- |
| `DB_URL` | Yes | JDBC URL for PostgreSQL |
| `DB_USERNAME` | Yes | Database user |
| `DB_PASSWORD` | Yes | Database password |
| `JWT_SECRET` | Yes | JWT signing secret; use at least 64 random characters |
| `ENCRYPTION_KEY` | Yes | Field-level encryption key for sensitive stored data |
| `ADMIN_PASSWORD` | Yes | Bootstrap platform admin password |
| `RESEND_API_KEY` | Yes | Email verification and notification delivery |
| `STORAGE_TYPE` | Yes | `local` or `r2` |
| `LOCAL_STORAGE_PATH` | Local only | Local vault storage directory |
| `R2_BUCKET_URL` | R2 only | S3-compatible R2 endpoint |
| `R2_BUCKET_NAME` | R2 only | Object storage bucket |
| `R2_ACCESS_KEY_ID` | R2 only | R2 access key |
| `R2_SECRET_ACCESS_KEY` | R2 only | R2 secret key |
| `GEMINI_API_KEY` | If Gemini enabled | Gemini provider credentials |
| `OPENAI_API_KEY` | If OpenAI enabled | OpenAI provider credentials |
| `AI_LOG_PAYLOADS` | No | Leave `false`; enabling can write clinical notes to logs |
| `ANALYTICS_MINUTES_SAVED_PER_AI_REPORT` | No | Assumption used for estimated time-saved analytics |
| `BILLING_MAX_ADDON_STORAGE_MB` | No | Safety cap for storage add-ons |
| `BILLING_MAX_ADDON_AI_CREDITS` | No | Safety cap for AI credit add-ons |

## Local Development

1. Create a PostgreSQL database:

   ```bash
   createdb echion_health
   ```

2. Add a local `.env` file using the example above.

3. Start the application:

   ```bash
   ./mvnw spring-boot:run
   ```

4. Open the API documentation:

   ```text
   http://localhost:8080/api/swagger-ui.html
   ```

5. The raw OpenAPI document is available at:

   ```text
   http://localhost:8080/api/api-docs
   ```

### First Login Flow

The standard flow is:

1. `POST /api/auth/register` to create an organization and first `HOSPITAL_ADMIN`.
2. `POST /api/auth/verify-email?token=...` after receiving the verification email.
3. `POST /api/auth/login` to receive an access token and refresh token.
4. Send protected requests with `Authorization: Bearer <accessToken>`.

For local development, `AUTH_AUTO_VERIFY_REGISTRATION=true` can be used when you do not want to depend on email delivery.

## Database Migrations

Flyway runs automatically on application startup.

Migration files live in:

```text
src/main/resources/db/migration
```

Current migrations cover:

- Multi-tenant foundation.
- Authentication, RBAC, MFA, and refresh-token persistence.
- Hospital branding and letterhead support.
- Signatures.
- Vault reports, templates, search vectors, and collaboration.
- Subscription tiers and billing quota tables.
- AI provider routing and usage events.
- Schema repair/convergence migrations for existing databases.

JPA is configured with `ddl-auto: none`, so schema changes should be made through Flyway migrations, not Hibernate auto-DDL.

## API Overview

All endpoints are served under:

```text
http://localhost:8080/api
```

The `/api` prefix is configured through `server.servlet.context-path`; controller mappings do not include it.

Most JSON responses use the shared envelope:

```json
{
  "success": true,
  "message": "optional message",
  "data": {}
}
```

### Public Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/auth/register` | Register an organization and first hospital admin |
| `POST` | `/auth/verify-email?token=` | Verify email |
| `POST` | `/auth/resend-verification?email=` | Resend verification email |
| `POST` | `/auth/login` | Login and receive JWT tokens |
| `POST` | `/auth/refresh` | Refresh an access token |

### Protected Endpoint Groups

| Group | Base path | Purpose |
| --- | --- | --- |
| Authentication and MFA | `/auth` | Logout, profile, profile completion, TOTP setup/enable/disable |
| Reports | `/vault` | Upload, CRUD, search, versions, preview, finalize, export |
| Templates | `/vault/templates` | Template CRUD, import/upload, sharing, analytics, versions |
| Folders | `/vault/folders` | Report folder creation and listing |
| Scan types | `/vault/scan-types` | Supported scan types and field definitions |
| Signatures | `/signatures` | Signature upload and management |
| Branding | `/org` | Hospital branding and letterhead |
| AI generation | `/generate-report` | Report generation, impression suggestion, grammar check |
| Collaboration | `/collaboration` | Shares, comments, audit trail, notifications |
| Billing | `/billing` | Plan, usage, upgrades, add-ons |
| Admin | `/admin` | Dashboard, user administration, audit logs, search maintenance |
| AI operations | `/admin/ai` | Provider verification and AI usage reporting |
| Analytics | `/analytics` | Adoption and AI-usage dashboard |

For the full endpoint contract, request/response examples, role notes, and path changes, see [docs/APPLICATION_DOCUMENTATION.md](docs/APPLICATION_DOCUMENTATION.md).

## Testing

Run the default verification suite:

```bash
./mvnw verify
```

The default suite excludes tests tagged `performance`. It includes focused unit tests, Spring tests, and Testcontainers-backed PostgreSQL integration tests for migration and database-specific behavior.

Run performance tests separately on an unloaded machine:

```bash
./mvnw verify -DexcludedGroups= -Dgroups=performance
```

Run a single test class:

```bash
./mvnw test -Dtest=TemplateServiceTest
```

Useful test references:

- [TESTING_GUIDE.md](TESTING_GUIDE.md)
- `src/test/resources/application-test.yaml`
- `src/test/java/com/giftedlabs/echoinhealthbackend/support/PostgresIntegrationTest.java`

## Docker

Build the image:

```bash
docker build -t echion-health .
```

Run the container with local storage:

```bash
docker run --rm -p 8080:8080 \
  --env-file .env \
  -e STORAGE_TYPE=local \
  -e LOCAL_STORAGE_PATH=/app/data \
  -v "$(pwd)/vault-storage:/app/data" \
  echion-health
```

The Dockerfile builds the application on Ubuntu 22.04 with Java 21 and Maven, then runs the packaged JAR on `eclipse-temurin:21-jre-jammy`.

## CI/CD

GitHub Actions contains three workflows:

| Workflow | Purpose |
| --- | --- |
| `.github/workflows/ci.yml` | Main merge/deploy gate: compile, tests, secret scan, dependency scan, CodeQL, container scan, and Railway deploy from `main` |
| `.github/workflows/performance.yml` | Isolated performance test workflow |
| `.github/workflows/qodana_code_quality.yml` | Qodana static analysis |

Production deployment is Dockerfile-based and targets Railway. The CI workflow expects:

- `RAILWAY_TOKEN` as a GitHub Actions secret.
- `RAILWAY_SERVICE` as an optional GitHub Actions variable.
- Runtime secrets configured on Railway itself.

Read [docs/CI_CD_PIPELINE.md](docs/CI_CD_PIPELINE.md) before changing deployment behavior. It includes the current gate design, Railway setup, and credential rotation warning.

## Security Notes

- Do not commit `.env`, API keys, database credentials, JWT secrets, or encryption keys.
- Rotate any credentials that have ever appeared in repository history.
- Keep `AI_LOG_PAYLOADS=false` outside deliberate debugging; AI request payloads can include clinical notes.
- Rotate `JWT_SECRET` to invalidate all active tokens.
- Rotate `ENCRYPTION_KEY` only with a migration plan; changing it can make encrypted stored values unreadable.
- Signature uploads require explicit admin-granted permission.
- Organization scoping is part of the security model; new queries and repository methods must preserve tenant boundaries.
- Prefer DTOs for API boundaries and do not expose JPA entities directly.

## Project Documentation

| Document | Purpose |
| --- | --- |
| [docs/APPLICATION_DOCUMENTATION.md](docs/APPLICATION_DOCUMENTATION.md) | Authoritative v2 application and API documentation |
| [docs/TECHNICAL_ARCHITECTURE.md](docs/TECHNICAL_ARCHITECTURE.md) | Architecture notes, design sketch, and implementation guidance |
| [docs/CI_CD_PIPELINE.md](docs/CI_CD_PIPELINE.md) | CI/CD operation and Railway deployment notes |
| [docs/IMPLEMENTATION_AUDIT_2026-07-29.md](docs/IMPLEMENTATION_AUDIT_2026-07-29.md) | Implementation audit findings |
| [docs/IMPLEMENTATION_CHECKLIST.md](docs/IMPLEMENTATION_CHECKLIST.md) | Build progress checklist |
| [docs/FEATURE_CHECKLIST.md](docs/FEATURE_CHECKLIST.md) | Feature completion checklist |
| [docs/PRD.md](docs/PRD.md) | Product requirements |
| [docs/REDIS_CACHING_ANALYSIS.md](docs/REDIS_CACHING_ANALYSIS.md) | Cache strategy analysis |
| [SONOVAULT_GUIDE.md](SONOVAULT_GUIDE.md) | SonoVault-specific user/developer guide |
| [AUTHENTICATION_FIX.md](AUTHENTICATION_FIX.md) | Historical authentication path fix |

## License

This project currently includes the [MIT License](LICENSE).
