# Technical Architecture — Echion Health v2.0

Companion to `PRD.md` and `IMPLEMENTATION_CHECKLIST.md`. This is the implementation reference for entities, API contracts, and integration details, targeting the existing **Java Spring Boot** codebase.

---

## 1. Stack

- **Runtime/Framework:** Java 21, Spring Boot 4.0.x in the current repo
- **Persistence:** Spring Data JPA + Hibernate, PostgreSQL
- **Migrations:** Flyway has been introduced for the v2 upgrade path, but the repo still carries Hibernate `ddl-auto: update` during the transition from an unmigrated schema
- **Security:** Spring Security with stateless JWT already in use in the current repo; tenant context is carried from the authenticated principal/JWT, with `@PreAuthorize` on privileged endpoints
- **Storage:** S3-compatible object storage via AWS SDK v2 (letterheads, signatures, uploaded templates, scan attachments)
- **AI:** OpenAI (`gpt-4.0-mini`) primary, Google Gemini API as configurable alternative/fallback — call via `RestClient`/`WebClient`, wrapped in a provider-abstraction service
- **Export:** PDF via OpenPDF/iText or Apache PDFBox, DOCX via Apache POI, HL7 v2.x via HAPI HL7 (`ca.uhn.hapi`)
- **Validation:** Bean Validation (`jakarta.validation`) on request DTOs
- **Docs:** springdoc-openapi for the REST API
- **Build:** Maven (`pom.xml` + `mvnw`)

Important repo mismatch carried forward into implementation:
- Current login remains email-based in this codebase. The PRD's org-scoped username model is a later auth refactor and should not be assumed by backend code until Phase 2 completes.

---

## 2. Data model (JPA entities)

### 2.1 Core entities (from URD, authoritative)

```java
@Entity
@Table(name = "organizations")
public class Organization {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionTier subscriptionTier;

    private Integer storageLimitMb;
    private Integer aiCreditsLimit;

    @Column(nullable = false)
    private Integer aiCreditsUsed = 0;

    // Branding
    private String letterheadUrl;
    @Column(nullable = false)
    private String hospitalName;
    private String address;
    private String phone;
    private String email;
    private String website;

    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;

    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL)
    private List<User> users = new ArrayList<>();

    @OneToMany(mappedBy = "organization")
    private List<Report> reports = new ArrayList<>();

    // getters/setters/equals/hashCode
}

@Entity
@Table(name = "users",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = "username"),
           @UniqueConstraint(columnNames = "email")
       })
public class User {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    private Designation designation;

    @Column(nullable = false)
    private boolean active = true;          // soft-deactivate, not hard delete

    @Column(nullable = false)
    private boolean canUploadSignature = false;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Signature> signatures = new ArrayList<>();

    private boolean mfaEnabled = false;
    private String mfaSecret;

    private Instant lastLoginAt;

    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;

    // getters/setters/equals/hashCode
}

@Entity
@Table(name = "signatures")
public class Signature {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String label;              // "Full Signature", "Initials", "Official Seal"

    @Column(nullable = false)
    private String imageUrl;

    private boolean isDefault = false;

    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;
}

@Entity
@Table(name = "reports")
public class Report {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private UUID templateId;

    // PHI — encrypted at rest via AttributeConverter (see §6)
    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false)
    private String patientName;

    @Column(nullable = false)
    private Integer patientAge;

    @Column(nullable = false)
    private Instant scanDate = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScanType scanType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> findings;      // organ/structure -> observation

    @Column(columnDefinition = "TEXT")
    private String impression;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] recommendations;

    private UUID appliedSignatureId;
    private String signatoryName;

    @Enumerated(EnumType.STRING)
    private Designation designation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status = ReportStatus.DRAFT;

    private boolean aiAssisted = false;
    private Integer aiCreditsUsed = 0;

    private Instant lastAutoSaveAt = Instant.now();
    private Instant completedAt;
    private Instant exportedAt;

    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;
}
```

```java
public enum SubscriptionTier { BASIC, PRO, ULTIMATE }
public enum UserRole { HOSPITAL_ADMIN, SONOGRAPHER, RADIOLOGIST, PHYSICIAN }
public enum Designation {
    RELEASING_RADIOLOGIST, CONSULTANT_RADIOLOGIST, SONOGRAPHER,
    PHYSICIAN_SPECIALIST, CARDIOLOGIST
}
public enum ReportStatus { DRAFT, FINALIZED, EXPORTED }

public enum ScanType {
    ABDOMINAL, ABDOMEN_PELVIS_MALE, ABDOMEN_PELVIS_FEMALE, PELVIC_MALE, PELVIC_FEMALE,
    OBSTETRIC_EARLY, OBSTETRIC_LATE, OBSTETRIC_TWINS, ANOMALY, BIOPHYSICAL_PROFILE,
    TRANSABDOMINAL_PELVIC, TRANSVAGINAL,
    ECHO_ADULT, ECHO_PEDIATRIC,
    MUSCULOSKELETAL, NECK, THYROID, BREAST, CHEST, SCROTAL, PENILE, NEONATAL_HEAD,
    ARTERIAL_DOPPLER_BOTH_LOWER, ARTERIAL_DOPPLER_LEFT_LOWER, ARTERIAL_DOPPLER_RIGHT_LOWER,
    ARTERIAL_DOPPLER_LEFT_UPPER, ARTERIAL_DOPPLER_RIGHT_UPPER,
    VENOUS_DOPPLER_BOTH_LOWER, VENOUS_DOPPLER_LEFT_LOWER, VENOUS_DOPPLER_RIGHT_LOWER,
    VENOUS_DOPPLER_LEFT_UPPER, VENOUS_DOPPLER_RIGHT_UPPER,
    GENERAL
}
```

### 2.2 Additional entities (implied by URD, not fully specified — proposed shapes)

Required by UR-046–UR-064 but not specified in the source URD. Adjust naming to match existing codebase conventions.

```java
@Entity
@Table(name = "vault_templates")
public class VaultTemplate {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScanType scanType;

    private String category;              // e.g. "Obstetric - First Trimester"

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] tags;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> content;   // structured, PHI-free report body

    @Column(nullable = false)
    private boolean phiFree = true;        // enforced server-side on save, never trust client

    private boolean favorite = false;
    private Integer usageCount = 0;
    private Instant lastUsedAt;
    private String sourceFormat;           // "PDF" | "DOCX" | "TXT" | "GENERATED"

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "uuid[]")
    private UUID[] sharedWithUserIds;

    private UUID versionOf;                // self-reference for version history

    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;
}

@Entity
@Table(name = "shared_scans")
public class SharedScan {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    private UUID reportId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShareScope shareScope;         // SPECIFIC | DEPARTMENT | ORGANIZATION

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "uuid[]")
    private UUID[] sharedWithUserIds;      // populated when scope = SPECIFIC

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    private Urgency urgency;               // ROUTINE | URGENT | STAT

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShareStatus status = ShareStatus.OPEN;   // OPEN | RESOLVED

    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;

    @OneToMany(mappedBy = "sharedScan", cascade = CascadeType.ALL)
    private List<Collaboration> collaborations = new ArrayList<>();
}

@Entity
@Table(name = "collaborations")
public class Collaboration {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_scan_id", nullable = false)
    private SharedScan sharedScan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String comment;

    private String suggestedImpression;

    @CreationTimestamp
    private Instant createdAt;
}

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID organizationId;

    private UUID userId;

    @Column(nullable = false)
    private String action;         // "LOGIN", "REPORT_FINALIZED", "SIGNATURE_APPLIED", "TEMPLATE_ACCESSED", ...

    private String entityType;     // "Report" | "VaultTemplate" | "Signature" | "User" | ...
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    private Instant createdAt;
}
```

```java
public enum ShareScope { SPECIFIC, DEPARTMENT, ORGANIZATION }
public enum Urgency { ROUTINE, URGENT, STAT }
public enum ShareStatus { OPEN, RESOLVED }
```

**Indexing note (UR-097):** add a Flyway migration with composite indexes leading with `organization_id` on every tenant-scoped table, e.g.:
```sql
CREATE INDEX idx_reports_org_scantype ON reports (organization_id, scan_type);
CREATE INDEX idx_vault_templates_org_owner ON vault_templates (organization_id, owner_id);
CREATE INDEX idx_vault_templates_org_favorite ON vault_templates (organization_id, favorite);
CREATE INDEX idx_audit_logs_org_created ON audit_logs (organization_id, created_at);
```

---

## 3. Repository layer (Spring Data JPA)

Every tenant-scoped repository must expose org-scoped finder methods — never a bare `findById` on tenant data without also checking `organizationId` in the service layer.

```java
public interface ReportRepository extends JpaRepository<Report, UUID> {
    Optional<Report> findByIdAndOrganizationId(UUID id, UUID organizationId);
    Page<Report> findAllByOrganizationId(UUID organizationId, Pageable pageable);
}

public interface VaultTemplateRepository extends JpaRepository<VaultTemplate, UUID> {
    Page<VaultTemplate> findAllByOrganizationIdAndScanType(
        UUID organizationId, ScanType scanType, Pageable pageable);

    @Query("""
        SELECT t FROM VaultTemplate t
        WHERE t.organization.id = :orgId
          AND (:keyword IS NULL OR LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (:scanType IS NULL OR t.scanType = :scanType)
        """)
    Page<VaultTemplate> search(
        @Param("orgId") UUID orgId,
        @Param("keyword") String keyword,
        @Param("scanType") ScanType scanType,
        Pageable pageable);
}
```

**Enforcement pattern:** prefer a base `@MappedSuperclass`/service-level helper (or a Hibernate `@Filter` on `organizationId`) so tenant scoping is applied consistently rather than re-implemented per repository method. Whatever pattern the existing codebase already uses for this, extend it — don't introduce a second pattern.

---

## 4. REST API surface (Spring MVC controllers)

Group by resource. All endpoints except signup/login require an authenticated, org-scoped principal and `@PreAuthorize` role checks. Use DTOs (records) for request/response — never expose JPA entities directly.

> **This section is the original design sketch, not the shipped contract.** Several paths below were renamed during implementation (templates and folders live under `/vault/...`, users under `/admin/users`, AI generation under `/generate-report`, sharing under `/collaboration`). For the endpoints as actually implemented — with roles, payloads and response shapes — see [`APPLICATION_DOCUMENTATION.md` §3](./APPLICATION_DOCUMENTATION.md#3-api-reference), which is authoritative.

### Auth & org
```
POST   /api/auth/signup                # creates Organization + first HOSPITAL_ADMIN
POST   /api/auth/login
POST   /api/auth/logout
POST   /api/auth/mfa/enable
POST   /api/auth/mfa/verify
GET    /api/org/profile
PATCH  /api/org/profile                # name, address, phone, email, website
POST   /api/org/letterhead              # multipart upload, max 5MB, PNG/JPG/SVG
GET    /api/org/letterhead/preview
```

### Users
```
GET    /api/users
POST   /api/users                       # @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
PATCH  /api/users/{id}
POST   /api/users/{id}/deactivate
POST   /api/users/{id}/reactivate
POST   /api/users/{id}/authorize-signature
```

### Signatures
```
GET    /api/signatures
POST   /api/signatures                  # requires canUploadSignature = true
PATCH  /api/signatures/{id}
DELETE /api/signatures/{id}
```

### SonoScribe / AI
```
POST   /api/reports/generate            # { rawNotes, scanType, measurements? } -> structured draft
POST   /api/reports/{id}/suggest-impression   # { findings } -> impression text
GET    /api/org/ai-credits              # usage vs. limit
```

### Reports
```
POST   /api/reports
GET    /api/reports/{id}
PATCH  /api/reports/{id}                # edit draft, triggers auto-save
POST   /api/reports/{id}/finalize       # validates required fields, applies signature
GET    /api/reports/{id}/preview
GET    /api/reports/{id}/export?format=pdf|docx|hl7
```

### SonoVault
```
GET    /api/vault/templates             # search/filter/sort query params, paginated
POST   /api/vault/templates             # save report as template (server-side PHI strip)
POST   /api/vault/templates/bulk        # CSV/JSON/multi-file multipart
PATCH  /api/vault/templates/{id}
DELETE /api/vault/templates/{id}
POST   /api/vault/templates/{id}/favorite
POST   /api/vault/templates/{id}/share
GET    /api/vault/templates/recent      # last 10 used
GET    /api/vault/templates/{id}/versions
POST   /api/vault/templates/{id}/restore/{versionId}
```

### SonoShare — implemented as `/api/collaboration`
```
POST   /api/collaboration/share                 # JSON; sharingLevel, urgency, department
POST   /api/collaboration/share-with-image      # multipart
GET    /api/collaboration/shared-with-me
GET    /api/collaboration/my-shares
GET    /api/collaboration/{id}
POST   /api/collaboration/{id}/comments         # supports isSuggestedImpression
GET    /api/collaboration/{id}/comments
PUT    /api/collaboration/{id}/resolve
GET    /api/collaboration/{id}/audit-trail      # paginated, append-only
```
Sharing levels: `SPECIFIC_COLLEAGUES`, `DEPARTMENT`, `EVERYONE`, `ORGANIZATION_WIDE` — all bounded by the caller's organization. Urgency: `LOW`, `MEDIUM`, `HIGH`, `URGENT`.

### Billing
```
GET    /api/billing/plan
POST   /api/billing/upgrade
POST   /api/billing/addons
GET    /api/billing/usage               # users, storage, AI credits vs. limits
```

Document all of the above with springdoc-openapi annotations (`@Operation`, `@ApiResponse`) so the OpenAPI spec stays current.

---

## 5. AI integration (SonoScribe)

Implement as a `SonoScribeService` with a provider-abstraction interface so OpenAI and Gemini are swappable via config, not hardcoded:

```java
public interface AiReportProvider {
    StructuredReportDraft generate(ReportGenerationRequest request);
}

@Service
@ConditionalOnProperty(name = "ai.provider.default", havingValue = "openai")
public class OpenAiReportProvider implements AiReportProvider { /* ... */ }

@Service
@ConditionalOnProperty(name = "ai.provider.default", havingValue = "gemini")
public class GeminiReportProvider implements AiReportProvider { /* ... */ }
```

**Request DTO (`POST /api/reports/generate`):**
```json
{
  "rawNotes": "string — clinical notes or transcript",
  "scanType": "ABDOMINAL | OBSTETRIC_EARLY | ... (enum)",
  "measurements": { "optional": "structured numeric data" }
}
```

**Expected structured response (parsed server-side into a Java record, not left as free text):**
```json
{
  "findings": { "Liver": "...", "Gallbladder": "...", "...": "..." },
  "impression": "string",
  "recommendations": ["string", "..."]
}
```

Guidelines:
- Store prompt templates per scan type as versioned resources (e.g. classpath templates under `src/main/resources/prompts/`), not inline strings in service classes.
- Request structured JSON output from the model (function calling / JSON mode) and deserialize with Jackson — don't regex-parse free text.
- The generation endpoint always returns a draft; never write directly to a `FINALIZED` `Report` from this call.
- Deduct AI credits atomically with the generation call (`@Transactional`, pessimistic or optimistic lock on `Organization.aiCreditsUsed`); reject before calling the provider if the org is already at its limit.
- Wrap provider calls with a timeout + circuit breaker (e.g. Resilience4j) and define the fallback behavior explicitly (see `PRD.md` §7 open question).

---

## 6. Export formats

| Format | Library | Notes |
|---|---|---|
| **PDF** | OpenPDF/iText or Apache PDFBox | Primary rendering target. Letterhead, patient info, findings/measurements, impression, recommendations, signature block. |
| **DOCX** | Apache POI (`XWPFDocument`) | Same content model as PDF, different renderer. Feed both from one `ReportRenderModel` built from the `Report` entity — don't fork the data mapping per format. |
| **HL7** | HAPI HL7 (`ca.uhn.hapi`) | Confirm target message type with EMR stakeholder — ORU^R01 is the conventional choice for observation/result reporting. Data-only (no letterhead/signature image); map findings/impression/recommendations into OBX segments. |

Implement a single `ReportExportService` with format-specific renderers behind a common interface (`ReportRenderer.render(Report, ExportFormat)`), so PDF/DOCX/HL7 stay in sync as the report model evolves.

---

## 7. Security implementation notes (Spring Security)

- **Tenant isolation:** resolve `organizationId` from the authenticated principal (JWT claim or session attribute) in a filter/interceptor, and require every service method touching tenant data to pass it explicitly to repository calls — don't rely on the client-supplied ID anywhere.
- **PHI encryption:** implement `EncryptedStringConverter implements AttributeConverter<String, String>` for `patientName` (and other PHI fields), backed by AES-256 (e.g. via `javax.crypto` or a library like Jasypt), key sourced from a secrets manager, not application.yml.
- **PHI stripping for templates:** enforce in the `VaultTemplateService` when constructing a template from a `Report` — strip name/age/dates server-side before persistence; never trust a client-supplied "already stripped" flag.
- **Audit logging:** either write `AuditLog` rows synchronously in an `@Transactional` service method, or publish a Spring event (`ApplicationEventPublisher`) consumed by an async `@EventListener` that persists the log — pick one pattern consistently. Cover: login/logout, report create/edit/finalize, template upload/access, signature application, collaboration activity, admin actions.
- **Session/JWT timeout:** 15 min idle — enforce server-side (JWT short expiry + refresh, or `HttpSession` timeout in `application.yml`: `server.servlet.session.timeout=15m`), not just a client-side inactivity timer.
- **RBAC:** `@PreAuthorize("hasRole('HOSPITAL_ADMIN')")` (or a custom `@PreAuthorize` SpEL expression checking org membership + role) on every controller method touching tenant data.
- **MFA:** TOTP via a library such as `dev.samstevens.totp`, optional per user, checked after password auth succeeds and before issuing the session/JWT.
- **Password policy:** enforce via Bean Validation custom annotation (`@StrongPassword`) on the signup/user-creation DTO — 8+ chars, upper/lower/number/special.
- **Transport:** TLS 1.3+ terminated at the load balancer/ingress or embedded Tomcat SSL config — confirm which layer owns this in the existing deployment.

---

## 8. Performance targets to design for

| Operation | Target |
|---|---|
| AI report generation | 5–10s |
| SonoVault search (5,000 templates) | <2s |
| Report template load | <1s |
| Concurrent users | 5 (Basic) / 15 (Pro) / 25 (Ultimate) without degradation |
| File upload | up to 50MB with progress indicator |

Design implications:
- Index `vault_templates` on `(organization_id, scan_type, favorite)` and consider Postgres full-text search (`tsvector`/`tsquery`) for the keyword search if `ILIKE` doesn't hit the 2s target at 5,000+ templates.
- Use `Pageable`/pagination on all list endpoints — never return an unbounded `List<Report>` or `List<VaultTemplate>`.
- Configure the connection pool (HikariCP) sized for peak tier concurrency (25 concurrent Ultimate-tier users per org, multiplied by number of active orgs).
- Multipart upload size limits: set `spring.servlet.multipart.max-file-size=50MB` and `max-request-size=50MB`, with client-side chunked progress reporting.

---

## 9. Open items to confirm with product before/while building

See `PRD.md` §7 for the consolidated list (AI fallback behavior, HL7 message type, shared-template deletion semantics, MFA method, GDPR scope). Additionally, for the Spring Boot context specifically:

- Confirm JWT vs. server-side session as the auth mechanism (affects §7 timeout implementation).
- Confirm Flyway vs. Liquibase for migrations if not already decided.
- Confirm Maven vs. Gradle and existing module/package structure so new code follows established conventions.
