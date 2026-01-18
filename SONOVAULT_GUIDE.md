# SonoVault - Feature Guide

## 🚀 Overview

SonoVault is a complete document management system for Echion Health, allowing sonographers to upload, organize, and search ultrasound reports.

### Key Features
- **Document Upload**: Support for Word (.docx) and PDF files with automatic text extraction.
- **Full-Text Search**: Powerful search capability finding keywords within document content.
- **Structured Reporting**: Create consistent reports with patient demographics and findings.
- **Templates**: customizable templates for different scan types.
- **Folders**: Organize reports hierarchically.
- **Cloud Storage**: Seamless integration with Railway object storage (S3 compatible).

---

## 🛠️ Configuration

### 1. Storage Settings
Configure your storage backend in `src/main/resources/application.yaml`.

**For Development (Local)**
```yaml
storage:
  type: local
  local:
    base-path: ./vault-storage
```

**For Production (Railway)**
Set the following environment variables in Railway:
- `STORAGE_TYPE`: railway
- `RAILWAY_S3_ENDPOINT`: (Your S3 endpoint)
- `RAILWAY_S3_BUCKET`: (Your bucket name)
- `RAILWAY_S3_ACCESS_KEY`: (Your access key)
- `RAILWAY_S3_SECRET_KEY`: (Your secret key)
- `RAILWAY_S3_REGION`: (e.g., us-east-1)

### 2. Database
Ensure your PostgreSQL database is running. The system will automatically create the necessary tables (`reports`, `report_templates`, `folders`) and Full-Text Search indexes.

---

## 🧪 How to Test

### 1. Upload a Document
Upload an existing Word or PDF report to auto-extract text.

**Endpoint:** `POST /api/vault/upload`
```bash
curl -X POST http://localhost:8000/api/vault/upload \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "file=@/path/to/report.docx"
```

### 2. Create a Structured Report
Create a new report manually.

**Endpoint:** `POST /api/vault/reports`
```json
{
  "patientName": "John Doe",
  "patientAge": 45,
  "patientSex": "MALE",
  "scanDate": "2026-01-04",
  "scanType": "ABDOMEN",
  "findings": "Liver size is normal. No focal lesions.",
  "impression": "Normal abdominal ultrasound."
}
```

### 3. Full-Text Search
Search for reports containing specific keywords (e.g., "lesion").

**Endpoint:** `POST /api/vault/search`
```json
{
  "query": "lesion",
  "scanType": "ABDOMEN"
}
```
**Response:** Returns matching reports ranked by relevance!

### 4. Manage Templates
Create a reusable template.

**Endpoint:** `POST /api/vault/templates`
```json
{
  "name": "Standard Abdomen Male",
  "gender": "MALE",
  "reportType": "ROUTINE",
  "defaultFindings": "Liver, gallbladder, pancreas, spleen, and kidneys are unremarkable."
}
```

---

## 🔍 API Documentation
Access the full Swagger UI documentation to explore all 15+ new endpoints:
👉 **http://localhost:8000/api/swagger-ui.html**

## 📂 Project Structure
- `com.giftedlabs.echoinhealthbackend.controller.VaultController` - Main entry point
- `com.giftedlabs.echoinhealthbackend.service.FileStorageService` - Validates and stores files
- `com.giftedlabs.echoinhealthbackend.service.ReportSearchService` - Handles full-text search logic
- `com.giftedlabs.echoinhealthbackend.repository.ReportRepository` - Native SQL for efficient search
