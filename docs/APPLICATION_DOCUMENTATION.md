# Echion Health System - Application Documentation

**Version:** 1.0  
**Date:** January 18, 2026

---

## 1. Overview

Echion Health System is a healthcare application designed for sonographers to:
- **Create and manage ultrasound scan reports** efficiently
- **Build a personal vault** of report templates for reuse
- **Collaborate with colleagues** through SonoShare for peer review

### Core Modules
| Module | Description |
|--------|-------------|
| **SonoVault** | Personal report storage, search, and template management |
| **SonoShare** | Collaboration space for sharing scans and receiving feedback |
| **Admin** | User management and system monitoring |

---

## 2. Technology Stack

### Backend
| Component | Technology | Version |
|-----------|------------|---------|
| Runtime | Java | 21 |
| Framework | Spring Boot | 4.0.1 |
| Database | PostgreSQL | 42.7.8 (driver) |
| Security | Spring Security + JWT | jjwt 0.12.5 |
| Caching | Caffeine | (Spring Boot managed) |
| API Docs | SpringDoc OpenAPI | 3.0.1 |

### External Services
| Service | Provider | Purpose |
|---------|----------|---------|
| Object Storage | Cloudflare R2 (S3-compatible) | File storage |
| Email | Resend API | Email verification & notifications |
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

### 3.1 Authentication (`/auth`)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/auth/register` | Register new user | ❌ |
| POST | `/auth/verify-email?token=` | Verify email | ❌ |
| POST | `/auth/login` | Login (returns JWT) | ❌ |
| POST | `/auth/refresh` | Refresh access token | ❌ |
| POST | `/auth/logout` | Logout | ✅ |
| GET | `/auth/profile` | Get user profile | ✅ |
| POST | `/auth/complete-profile` | Add professional details | ✅ |
| PATCH | `/auth/profile` | Update profile | ✅ |

**Login Request:**
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
    "user": { ... }
  }
}
```

---

### 3.2 SonoVault (`/vault`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/vault/upload` | Upload document (PDF/DOCX) |
| POST | `/vault/reports` | Create report from scratch/template |
| GET | `/vault/reports/{id}` | Get report details |
| PUT | `/vault/reports/{id}` | Update report |
| DELETE | `/vault/reports/{id}` | Delete report |
| POST | `/vault/reports/search` | Search reports |
| GET | `/vault/reports/recent` | Get 10 recent reports |
| GET | `/vault/reports/{id}/download` | Download PDF |
| GET | `/vault/reports/{id}/print` | Print-friendly PDF |

**Create Report Request:**
```json
{
  "patientName": "John Doe",
  "patientAge": 45,
  "gender": "MALE",
  "scanType": "ABDOMINAL",
  "scanDate": "2026-01-18",
  "findings": "Normal liver, gallbladder...",
  "impression": "No abnormalities detected",
  "templateId": "optional-template-id"
}
```

---

### 3.3 Templates (`/templates`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/templates` | Get all user templates |
| POST | `/templates` | Create new template |
| DELETE | `/templates/{id}` | Delete template |

---

### 3.4 Folders (`/folders`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/folders` | Get all folders |
| POST | `/folders` | Create folder |
| PUT | `/folders/{id}` | Update folder |
| DELETE | `/folders/{id}` | Delete folder |

---

### 3.5 SonoShare - Collaboration (`/collaboration`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/collaboration/share` | Share report (JSON) |
| POST | `/collaboration/share-with-image` | Share image (multipart) |
| GET | `/collaboration/shared-with-me` | Scans shared with me |
| GET | `/collaboration/my-shares` | My shared scans |
| GET | `/collaboration/{id}` | Get shared scan details |
| POST | `/collaboration/{id}/comments` | Add comment |
| GET | `/collaboration/{id}/comments` | Get comments |
| PUT | `/collaboration/{id}/resolve` | Mark as resolved |

**Sharing Levels:**
- `SPECIFIC_COLLEAGUES` - Share with selected users
- `EVERYONE` - Share with all system users

**Share with Image (multipart):**
```
POST /api/collaboration/share-with-image
Content-Type: multipart/form-data

image: [file]
sharingLevel: "EVERYONE"
title: "Need feedback"
requestMessage: "Please review this scan"
```

---

### 3.6 Notifications (`/collaboration/notifications`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/notifications/stream` | SSE real-time stream |
| GET | `/notifications` | Get all notifications |
| GET | `/notifications/unread` | Get unread notifications |
| GET | `/notifications/unread-count` | Get unread count |
| PUT | `/notifications/{id}/read` | Mark as read |
| PUT | `/notifications/read-all` | Mark all as read |

---

### 3.7 Admin (`/admin`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/admin/dashboard/stats` | Dashboard statistics |
| GET | `/admin/users` | List all users |
| GET | `/admin/users/{id}` | Get user details |
| PUT | `/admin/users/{id}/lock` | Lock user account |
| PUT | `/admin/users/{id}/unlock` | Unlock user account |
| GET | `/admin/audit-logs` | Get audit logs |

---

## 4. Configuration

### Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `DB_URL` | PostgreSQL connection URL | ✅ |
| `DB_USERNAME` | Database username | ✅ |
| `DB_PASSWORD` | Database password | ✅ |
| `JWT_SECRET` | JWT signing key (min 64 chars) | ✅ |
| `RESEND_API_KEY` | Resend email API key | ✅ |
| `R2_BUCKET_URL` | R2 S3 endpoint URL | ✅ (prod) |
| `R2_BUCKET_NAME` | Bucket name | ✅ (prod) |
| `R2_ACCESS_KEY_ID` | R2 access key | ✅ (prod) |
| `R2_SECRET_ACCESS_KEY` | R2 secret key | ✅ (prod) |
| `STORAGE_TYPE` | `local` or `r2` | ✅ |

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
export STORAGE_TYPE=local

# Run
./mvnw spring-boot:run
```

### Production (Railway)
1. Push code to GitHub
2. Connect Railway to repository
3. Set environment variables in Railway dashboard
4. Deploy automatically

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
| `User` | User accounts with roles |
| `Report` | Scan reports |
| `ReportTemplate` | Saved templates |
| `Folder` | Report organization |
| `SharedScan` | Collaboration shares |
| `ScanComment` | Comments on shared scans |
| `CollaborationNotification` | User notifications |
| `AuditLog` | Activity audit trail |

---

## 8. Security

- **Authentication:** JWT Bearer tokens
- **Authorization:** Role-based (USER, ADMIN)
- **Password:** BCrypt hashing
- **Session:** Stateless JWT (1 hour access, 24 hour refresh)
- **CORS:** Configured for allowed origins
- **Audit:** All actions logged to `AuditLog`
