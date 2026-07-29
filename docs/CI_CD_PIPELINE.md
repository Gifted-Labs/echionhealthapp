# CI/CD pipeline — setup and operation

**Repository:** `Gifted-Labs/echionhealthapp` · **Deploy target:** Railway (Dockerfile-based)

---

## ⚠️ Do this before anything else: rotate leaked credentials

`.env` is correctly ignored today, but **it was committed in earlier history and the secrets are
still in the repository**:

| Commit | Contents |
|---|---|
| `e17b31e` — *initial commit: Authentication system* | 3 populated secrets |
| `6a4af08` — *feat: Collaboration and Sono Share* | 4 populated secrets |
| `96e172a` — *Migrate storage to Cloudflare R2* | `.env` removed here |

Anyone who has ever cloned this repository has those values, and removing a file in a later
commit does not remove it from history. Rotate all of these:

- `DB_PASSWORD`
- `JWT_SECRET` — rotating this invalidates all existing tokens, which is the point
- `ADMIN_PASSWORD`
- `RESEND_API_KEY`
- `RAILWAY_ACCESS_KEY_ID` / `RAILWAY_SECRET_ACCESS_KEY`

Rotate first, then decide whether to purge history (`git filter-repo` or BFG). Purging rewrites
every commit hash and forces everyone to re-clone; rotation is what actually removes the risk.

The `secret-scan` job added below exists so this cannot recur silently.

---

## What runs, and when

### `ci.yml` — the merge gate

Runs on every pull request and every push to `main`.

| Job | What it does | Blocks merge |
|---|---|---|
| **Build & test** | `mvnw verify` — 65 tests including Testcontainers PostgreSQL suites | ✅ |
| **Secret scan** | Gitleaks over the full history | ✅ |
| **Dependency vulnerabilities** | Trivy, HIGH/CRITICAL, fixed CVEs only | ✅ |
| **CodeQL (Java)** | GitHub SAST, `security-extended` queries | ✅ |
| **Container image build & scan** | Builds the Dockerfile, Trivy scans the image | ✅ |
| **CI gate** | Aggregates the five above into one status check | ✅ |
| **Deploy to Railway** | Only on `main`, only after the gate is green | — |

`ignore-unfixed: true` on both Trivy scans means a CVE with no available fix reports but does not
block. Without it the pipeline fails on things you cannot action, and people learn to ignore it.

### `performance.yml` — not a merge gate

Nightly at 03:00 UTC, on demand, and on pull requests that touch the query or caching layer.

Performance tests assert absolute latency, which is only meaningful on an unloaded machine.
Running them with the rest of the suite makes them measure contention: an assertion that passes
at **13ms** standalone was observed at **2126ms** during a full `verify`. They are tagged
`performance`, excluded from the default build by the pom, and run alone here.

Run locally:

```bash
./mvnw verify -DexcludedGroups= -Dgroups=performance
```

---

## One-time GitHub setup

### 1. Repository secrets

**Settings → Secrets and variables → Actions → Secrets**

| Secret | Where to get it |
|---|---|
| `RAILWAY_TOKEN` | Railway dashboard → Account Settings → Tokens |

`GITHUB_TOKEN` is provided automatically; nothing to add.

### 2. Repository variables

**Settings → Secrets and variables → Actions → Variables**

| Variable | Value |
|---|---|
| `RAILWAY_SERVICE` | Your Railway service name (defaults to `echionhealthapp` if unset) |

### 3. Branch protection

**Settings → Branches → Add rule** for `main`:

- ☑ Require a pull request before merging
- ☑ Require status checks to pass before merging
  - Add **`CI gate`** as the required check — just that one. It aggregates the others, so adding
    a new gate later needs no change here.
- ☑ Require branches to be up to date before merging
- ☑ Do not allow bypassing the above settings

### 4. Environment (optional but recommended)

**Settings → Environments → New environment → `production`**

Add yourself as a required reviewer to put a manual approval between a green build and a
production deploy. The `deploy` job already targets this environment, so protection applies as
soon as you create it.

### 5. 🔴 Disable Railway's own GitHub auto-deploy

**This is the step that makes the gate real.** If Railway is connected to the GitHub repo with
automatic deploys enabled, it deploys on push — before, and regardless of, any of these checks.
Every gate above would be decorative.

In the Railway dashboard → your service → **Settings → Source**: disconnect the GitHub repo, or
turn off automatic deployments. The `deploy` job in `ci.yml` becomes the only path to production.

Confirm afterwards: push a commit that deliberately fails a test and check nothing deploys.

---

## Required environment variables on Railway

CI does not manage runtime configuration. These must be set on the Railway service itself
(**Settings → Variables**), and they are what the application actually runs with — values in
`application.yaml` are only defaults:

| Variable | Notes |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection |
| `JWT_SECRET` | Rotate — see the warning at the top |
| `ENCRYPTION_KEY` | AES-256; rotating this breaks stored MFA secrets |
| `RESEND_API_KEY` | Rotate |
| `R2_*` | Object storage credentials |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Bootstrap super admin; rotate |
| `GEMINI_API_KEY`, `GEMINI_API_ENDPOINT` | Endpoint must be the API **base** — see below |
| `OPENAI_API_KEY` | Currently unset, so there is no AI fallback provider |
| `AI_LOG_PAYLOADS` | Leave unset/false — enabling writes clinical notes to logs |

**`GEMINI_API_ENDPOINT` must be `https://generativelanguage.googleapis.com/v1beta`** — the base,
not a full endpoint. The provider appends `/models/{model}:generateContent` itself. A value with
anything after the version produces a 404, which is the failure this deployment already hit.
`AiConfigurationReporter` warns about this at startup.

---

## After a deploy

1. **Watch the startup logs for the Flyway result.** The migration chain runs on boot. Both the
   empty-database and legacy-Hibernate-schema paths are covered by tests, but the first
   production run after this change is the one that matters.
2. **`POST /api/admin/ai/verify`** — performs a real round trip to each configured provider and
   reports whether it returned parseable structured output. This is the only way to confirm AI
   wiring; a green build proves nothing about provider configuration.
3. **`GET /api/billing/usage`** — confirms tenant quota accounting reads correctly.

---

## Extending the pipeline

- **A new gate:** add the job to `ci.yml` and list it in `ci-gate`'s `needs`. Branch protection
  needs no change.
- **Integration tests against a real environment:** add a job after `deploy` gated on the
  `production` environment, rather than widening the merge gate.
- **Dependabot:** add `.github/dependabot.yml` for Maven and GitHub Actions. It complements the
  Trivy gate — Trivy tells you a vulnerable dependency exists, Dependabot opens the PR that
  fixes it.

## Known gaps

- **No staging environment.** `main` deploys straight to production. If you add one, promote
  through it rather than adding another trigger on `main`.
- **No database backup verification.** UR-092 (incremental backups every 6h, daily full) and
  UR-094 (4-hour RTO) are unaddressed and are not something CI can prove.
- **No rollback automation.** A bad deploy needs a manual Railway rollback. Given migrations run
  on boot and are forward-only, a rollback of the application does not roll back the schema —
  worth designing before you need it.
