# Quick Test Guide - Echoin Health Authentication API

## ✅ Issue Fixed

**Problem:** Security configuration was blocking public endpoints because it was looking for `/api/auth/register` but the actual path is `/auth/register` (since context path `/api` is already configured).

**Solution:** Updated `SecurityConfig.java` to remove `/api` prefix from endpoint matchers.

---

## 🚀 Testing the API

### Base URL
```
http://localhost:8000/api
```

### 1. Register a New User

**Endpoint:** `POST /api/auth/register`

**Request:**
```bash
curl -X POST http://localhost:8000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "password": "SecurePass123!"
  }'
```

**Expected Response (200 OK):**
```json
{
  "success": true,
  "message": "Registration successful! Please check your email to verify your account.",
  "data": null,
  "timestamp": "2026-01-03T19:50:00"
}
```

**What happens:**
- User created in database with `emailVerified = false`
- Verification email sent via Resend
- Audit log created: `user_registered`

---

### 2. Verify Email

**Endpoint:** `POST /api/auth/verify-email?token={TOKEN}`

**Request:**
```bash
curl -X POST "http://localhost:8000/api/auth/verify-email?token=YOUR_TOKEN_FROM_EMAIL"
```

**Expected Response (200 OK):**
```json
{
  "success": true,
  "message": "Email verified successfully! You can now log in.",
  "data": null,
  "timestamp": "2026-01-03T19:51:00"
}
```

**What happens:**
- User's `emailVerified` set to `true`
- Welcome email sent
- Audit log created: `email_verified`

---

### 3. Login

**Endpoint:** `POST /api/auth/login`

**Request:**
```bash
curl -X POST http://localhost:8000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john.doe@example.com",
    "password": "SecurePass123!"
  }'
```

**Expected Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huLmRvZUBleGFtcGxlLmNvbSIsImlzcyI6ImVjaG9pbi1oZWFsdGgiLCJpYXQiOjE3MDQ0MDAwMDAsImV4cCI6MTcwNDQwMzYwMH0...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huLmRvZUBleGFtcGxlLmNvbSIsImlzcyI6ImVjaG9pbi1oZWFsdGgiLCJpYXQiOjE3MDQ0MDAwMDAsImV4cCI6MTcwNDQ4NjQwMH0...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "id": "cm5abc123",
      "email": "john.doe@example.com",
      "firstName": "John",
      "lastName": "Doe",
      "phone": null,
      "hospitalName": null,
      "department": null,
      "serviceNumber": null,
      "role": "SONOGRAPHER",
      "emailVerified": true,
      "profileCompleted": false,
      "createdAt": "2026-01-03T19:50:00",
      "profileUpdatedAt": null,
      "lastLoginAt": "2026-01-03T19:52:00"
    }
  },
  "timestamp": "2026-01-03T19:52:00"
}
```

**What happens:**
- User authenticated
- Access token generated (1 hour expiry)
- Refresh token generated (24 hours expiry)
- `lastLoginAt` updated
- Audit log created: `login_success`

---

### 4. Get Profile (Protected)

**Endpoint:** `GET /api/auth/profile`

**Request:**
```bash
curl -X GET http://localhost:8000/api/auth/profile \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

**Expected Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "id": "cm5abc123",
    "email": "john.doe@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "phone": null,
    "hospitalName": null,
    "department": null,
    "serviceNumber": null,
    "role": "SONOGRAPHER",
    "emailVerified": true,
    "profileCompleted": false,
    "createdAt": "2026-01-03T19:50:00",
    "profileUpdatedAt": null,
    "lastLoginAt": "2026-01-03T19:52:00"
  },
  "timestamp": "2026-01-03T19:53:00"
}
```

---

### 5. Complete Profile (Protected)

**Endpoint:** `POST /api/auth/complete-profile`

**Request:**
```bash
curl -X POST http://localhost:8000/api/auth/complete-profile \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "+1234567890",
    "hospitalName": "City General Hospital",
    "department": "Radiology",
    "serviceNumber": "SN12345"
  }'
```

**Expected Response (200 OK):**
```json
{
  "success": true,
  "message": "Profile completed successfully!",
  "data": {
    "id": "cm5abc123",
    "email": "john.doe@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "phone": "+1234567890",
    "hospitalName": "City General Hospital",
    "department": "Radiology",
    "serviceNumber": "SN12345",
    "role": "SONOGRAPHER",
    "emailVerified": true,
    "profileCompleted": true,
    "createdAt": "2026-01-03T19:50:00",
    "profileUpdatedAt": "2026-01-03T19:54:00",
    "lastLoginAt": "2026-01-03T19:52:00"
  },
  "timestamp": "2026-01-03T19:54:00"
}
```

**What happens:**
- Professional details added
- `profileCompleted` becomes `true`
- `profileUpdatedAt` timestamp set
- Audit log created: `profile_completed`

---

### 6. Refresh Token

**Endpoint:** `POST /api/auth/refresh`

**Request:**
```bash
curl -X POST http://localhost:8000/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "YOUR_REFRESH_TOKEN"
  }'
```

**Expected Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "accessToken": "NEW_ACCESS_TOKEN",
    "refreshToken": "SAME_REFRESH_TOKEN",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": { ... }
  }
}
```

---

### 7. Logout

**Endpoint:** `POST /api/auth/logout`

**Request:**
```bash
curl -X POST http://localhost:8000/api/auth/logout \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "YOUR_REFRESH_TOKEN"
  }'
```

**Expected Response (200 OK):**
```json
{
  "success": true,
  "message": "Logged out successfully",
  "data": null
}
```

**What happens:**
- Refresh token revoked
- Audit log created: `logout`

---

## 🧪 Using Swagger UI

Navigate to: **http://localhost:8000/api/swagger-ui.html**

All endpoints are documented with:
- Request/response schemas
- Try-it-out functionality
- Authentication support

---

## 📊 Database Verification

After testing, check your PostgreSQL database:

```sql
-- View registered users
SELECT id, email, first_name, last_name, email_verified, role, created_at 
FROM users;

-- View audit logs
SELECT user_email, action, details, success, created_at 
FROM audit_logs 
ORDER BY created_at DESC;

-- View verification tokens
SELECT token, expires_at, verified_at, created_at 
FROM email_verification_tokens;

-- View refresh tokens
SELECT token, expires_at, revoked_at, created_at 
FROM refresh_tokens;
```

---

## ⚠️ Common Issues

### 1. Email Not Verified Error
**Error:** "Please verify your email before logging in"
**Solution:** Check your email for verification link or use resend endpoint

### 2. Invalid Token
**Error:** "Invalid verification token" or "Verification token has expired"
**Solution:** Request new verification email via `/auth/resend-verification?email=xxx`

### 3. Invalid Credentials
**Error:** "Invalid email or password"
**Solution:** Verify email is correct and password meets requirements (8+ chars, uppercase, lowercase, number, special char)

### 4. Unauthorized
**Error:** 401 Unauthorized on protected endpoints
**Solution:** Include `Authorization: Bearer {accessToken}` header

---

## ✅ Success Indicators

- ✅ Registration returns 200 OK
- ✅ Verification email received (check Resend dashboard if not in inbox)
- ✅ Email verification returns 200 OK
- ✅ Login returns access + refresh tokens
- ✅ Protected endpoints work with Bearer token
- ✅ Audit logs created in database
- ✅ Profile completion works

---

## 🔧 Troubleshooting

**Check application logs:**
```bash
# Look for these log messages
- "User registered successfully: {email}"
- "Email verified for user: {email}"
- "User logged in successfully: {email}"
- "Profile completed for user: {email}"
```

**Check Resend dashboard:**
- Login to Resend
- View sent emails
- Check delivery status

**Restart application if needed:**
```bash
# Stop current instance (Ctrl+C)
# Rebuild and restart
mvn clean spring-boot:run
```
