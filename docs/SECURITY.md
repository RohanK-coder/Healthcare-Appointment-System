# Security design

> Demo system with synthetic data. It demonstrates common controls; it has **not** been audited and is
> **not** suitable for real protected health information (PHI).

## Threat model (scope)

Internet-facing API used by three roles. Main concerns: privilege escalation, account takeover via
credential guessing, cross-user data access (IDOR), race conditions on shared resources, and leaking
internals through errors or logs.

## Controls and decisions

| Area | Decision | Why / trade-off |
|---|---|---|
| **Authentication** | Stateless JWT (HS384), 8h expiry, sent as `Authorization: Bearer` | Simple horizontal scaling. Trade-off: no server-side session list, so revocation uses a version counter (below). Token is kept in `localStorage`, which is exposed to XSS; an httpOnly cookie + CSRF token would be stronger. |
| **Token revocation** | Each user has `token_version`; JWTs carry it as `tv`. Password change bumps it. | Invalidates every older token immediately without a token denylist. |
| **Account creation** | Public `/api/auth/register` always creates a **PATIENT**; there is no role field. Doctors and admins are created only by admins. | Fixes a privilege-escalation bug where anyone could register as ADMIN. |
| **Authorization** | URL-level role rules (`/api/admin/**` → ADMIN, etc.) **plus** ownership checks in services (a patient can cancel only their appointments; a doctor can update only theirs). | Role checks alone would allow IDOR. Covered by a 72-case endpoint × role test matrix plus ownership tests. |
| **Password storage** | BCrypt, cost 12 (configurable). Hashes from lower costs are upgraded on next login. | ~250 ms per hash on a laptop: slows offline cracking; acceptable latency for login. |
| **Password policy** | 8–72 chars, at least one letter and one digit. | Length matters most; 72 is BCrypt's input limit. No forced rotation (per NIST 800-63B). |
| **Brute force** | Per-account lockout: 5 consecutive failures → 15 min lock (HTTP 423). Per-IP rate limit on `/login`, `/register`, `/password`: 10/min. | Lockout stops targeted guessing; IP limiting slows spraying. Trade-off: lockout lets an attacker lock a known account (demo accounts are reset on restart). |
| **User enumeration** | Same 401 message for unknown email and wrong password; unknown emails still run a BCrypt comparison to equalize timing. Duplicate-email registration does reveal existence (409), a deliberate UX trade-off. | |
| **Rate limiting** | Bucket4j token buckets in memory, keyed by client IP: auth 10/min, API 300/min; returns 429 + `Retry-After`. | In-memory means per instance; multiple instances would need Redis-backed buckets. |
| **Client IP** | `server.forward-headers-strategy=native`: Tomcat honors `X-Forwarded-For` only from private-network proxies. | Prevents spoofing the rate-limit key from the internet while working behind nginx / PaaS proxies. |
| **Input validation** | Bean Validation on every request DTO (sizes, formats, enums), plus business rules (no past slots, ≤180 days ahead, valid state transitions). Malformed JSON → 400. | Fixed a denial-of-service bug: slot generation near midnight looped forever. |
| **Concurrency** | Row locks + partial unique index on active bookings (see ARCHITECTURE.md). | Verified by a 20-thread test and a 2,000-request load test with zero double bookings. |
| **Errors** | Uniform JSON errors; 500s return a generic message and log the stack trace server-side. 401/403 come from Spring Security handlers, also JSON. | No stack traces or SQL in responses. |
| **Audit trail** | Append-only `audit_logs`: who, what, target, outcome, IP, time. Covers logins (success/failure/lock), registration, password change, user creation, slot creation, booking, cancel, status change, summary save, dead notifications, admin retries. Failures are written in their own transaction so they survive rollbacks. | Clinical text (diagnosis, notes, prescriptions) is **never** written to the audit log or app logs. |
| **Secrets** | `JWT_SECRET`, DB and SMTP credentials come from environment variables. The `prod` profile refuses to start with the dev secret. `.env` is git-ignored. | |
| **CORS** | Explicit origin allow-list from `FRONTEND_URL`; only `Authorization` and `Content-Type` headers; no credentials mode. | |
| **Transport / headers** | nginx adds `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy`; Spring Security adds its defaults. TLS terminates at the hosting provider. | |
| **Containers** | Backend runs as a non-root user on a JRE-only Alpine image with a health check. | |
| **Actuator** | Only `health` and `info` are public; `metrics` requires ADMIN. Mail health is excluded so an SMTP outage doesn't fail health checks (the queue retries). | |

## Known gaps (would be needed before handling real data)

- No MFA; no email verification or password-reset flow.
- JWT in `localStorage` (XSS exposure); no refresh-token rotation.
- Rate limits and lockout are per instance and in memory.
- No encryption at rest beyond what the database host provides; no field-level encryption of clinical notes.
- Audit log is append-only by convention, not cryptographically tamper-evident, and has no retention policy.
- No dependency/container vulnerability scanning in CI yet (e.g. Dependabot, Trivy, OWASP dependency-check).
- No formal threat model, penetration test, BAA, or HIPAA risk assessment.
