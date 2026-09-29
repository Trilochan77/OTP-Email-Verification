# Email OTP Verification System

Real email OTP verification with Spring Boot backend + modern web UI. Free SMTP delivery, 6-digit code, 60-second expiry, 3 attempts.

## Features
- Email-only OTP (no paid SMS needed)
- 6-digit secure OTP, 60s expiry, max 3 attempts
- Real email via Gmail SMTP (HTML + text), test-mode fallback
- Wrong OTP auto-clears from input box
- Modern responsive UI with timer progress bar + status colors
- `delivery-status` endpoint for quick SMTP diagnosis

## Tech Stack
- Backend: Java 25, Spring Boot 4.1.1, Spring Mail, Spring Web MVC
- Frontend: HTML / CSS / JS (no framework)
- Build: Maven

## Project Structure
```
D:/OTP
├── otp-backend/          # Spring Boot API (port 8080)
│   └── src/main/...
├── OTP-Web-UI/           # index.html, style.css, script.js
├── OTP-Verification-System/  # old console demo (optional)
└── README.md
```

## Quick Start

### 1. Get Gmail App Password (one-time, free)
1. Google Account → Security → enable 2-Step Verification
2. Open https://myaccount.google.com/apppasswords
3. Create app `OTP` → copy 16-letter password (remove spaces when pasting)

### 2. Run backend
PowerShell:
```powershell
$env:MAIL_USERNAME="you@gmail.com"
$env:MAIL_PASSWORD="abcdefghijklmnop"
Set-Location D:\OTP\otp-backend
mvn spring-boot:run
```
Wait for `Started OtpBackendApplication`.

Check: http://localhost:8080/api/otp/delivery-status → want `"mailConfigured":true`

### 3. Open UI
Open `OTP-Web-UI/index.html` in Chrome → enter email → Send OTP → check inbox/spam → Verify OTP.

## API
| Method | URL | Description |
|--------|-----|-------------|
| GET | `/api/otp/test` | health check |
| GET | `/api/otp/delivery-status` | SMTP status (never exposes secrets) |
| GET | `/api/otp/generate?email=user@example.com` | generate + send OTP |
| GET | `/api/otp/verify?otp=123456` | verify OTP |
| GET | `/api/otp/resend?email=user@example.com` | invalidate old + send new |

Legacy `?destination=` param still works for backward compatibility.

## Configuration
`otp-backend/src/main/resources/application.properties`:
```properties
app.otp.delivery-mode=email
app.otp.email-from=${MAIL_FROM:${MAIL_USERNAME:no-reply@example.com}}
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME:}
spring.mail.password=${MAIL_PASSWORD:}
app.otp.expose-for-testing=true
```
- Gmail limit ~500 emails/day free — enough for OTP.
- Gmail requires `From == MAIL_USERNAME`, already handled.
- Set `app.otp.expose-for-testing=false` in production to hide `testOtp` from API.

## Security Notes
- Real Gmail/App Password lives only in PowerShell `$env:` memory, never in code — safe to push to GitHub.
- Never hardcode `spring.mail.username/password` with real values.
- Do not commit `target/` or `.idea/` (already in `.gitignore`).

## Troubleshooting
| Error | Fix |
|-------|-----|
| `No such host is known (repo.maven.apache.org)` | No internet — turn ON WiFi, retry |
| `Port 8080 was already in use` | `netstat -ano \| findstr :8080` → `taskkill /PID <pid> /F` |
| `mailConfigured:false` | Env vars not set in this window — set + restart backend |
| `[OTP][EMAIL-FAIL] Authentication failed` | Wrong App Password — use 16-letter App Password, no spaces, not login password |
| College mail gets nothing, Gmail works | College server blocking/quarantine — check Spam/Junk, ask IT to allow Gmail SMTP |
| `Not in multipart mode` | Fixed — use `new MimeMessageHelper(mime, true, "UTF-8")` |

## License
College project — free to use.
