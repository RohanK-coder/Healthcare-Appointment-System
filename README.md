# CareSlot: Healthcare Appointment System

![CI](https://github.com/RohanK-coder/Healthcare-Appointment-System/actions/workflows/ci.yml/badge.svg)

A full-stack appointment booking system with three role-based portals (patient, doctor, admin), built to
show backend API design, PostgreSQL concurrency control, security controls, and reliable background
processing.

> **Demo system with synthetic data.** CareSlot is a portfolio project. It is **not** a clinical product, is
> **not** HIPAA compliant, and must not be used with real personal or health information. Every name, email
> and visit note in the demo dataset is generated.

**Live demo:** _add link_ · **API docs:** _add link_/swagger-ui.html

## Highlights

- **No double bookings under concurrency.** A pessimistic row lock plus a partial unique index. In a load test,
  2,000 concurrent attempts on 50 slots produced exactly 50 bookings, 1,950 clean `409`s, and no errors.
- **Reliable reminders without a message broker.** PostgreSQL works as the job queue: `FOR UPDATE SKIP LOCKED`
  workers, exponential backoff with jitter, dead-lettering, crash recovery, and an admin retry button.
  Throughput: 2,275 jobs/s with 4 workers.
- **Security.** JWT with role-based access and per-resource ownership checks, revocation on password change,
  BCrypt cost 12, account lockout, per-IP rate limiting, strict input validation, and an append-only audit trail.
- **Tested against real PostgreSQL.** 131 tests (109 integration tests on Testcontainers), including a 20-thread booking
  race, a 72-case role × endpoint permission matrix, and queue failure scenarios. CI runs them on every push.
- **Runs anywhere.** One `docker compose up` starts Postgres, the API, the frontend and a fake mail inbox.
  Deployment guide for Render, Neon and Vercel.

## Tech stack

| Layer | Technology |
|---|---|
| API | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, Bean Validation |
| Database | PostgreSQL 16, Flyway migrations |
| Security | JWT (jjwt), BCrypt, Bucket4j rate limiting |
| Background jobs | Spring scheduling + PostgreSQL queue (`SKIP LOCKED`), SMTP via Spring Mail |
| Frontend | React 18, React Router, Vite, Axios |
| Testing | JUnit 5, Testcontainers, MockMvc, k6 load tests |
| Ops | Docker, Docker Compose, nginx, GitHub Actions, GHCR, Actuator/Micrometer, OpenAPI |

## Quick start (Docker)

```bash
docker compose up --build
```

| URL | What |
|---|---|
| http://localhost:3000 | App |
| http://localhost:3000/swagger-ui.html | Interactive API docs |
| http://localhost:8025 | Mailpit inbox (reminder emails land here; nothing is really sent) |

The `demo` profile seeds a synthetic dataset (12 doctors, 200 patients, ~3,300 slots, ~2,400 appointments).

### Demo accounts

| Role | Email | Password |
|---|---|---|
| Admin | `admin@careslot.test` | `DemoPass123` |
| Doctor | `doctor@careslot.test` | `DemoPass123` |
| Patient | `patient@careslot.test` | `DemoPass123` |

Synthetic users (`doctor01@doctors.careslot.test`, `patient001@patients.careslot.test`, …) use the same password.

## Local development

Requirements: Java 21+, Maven 3.9+, Node 18+, Docker.

```bash
docker compose up -d postgres                     # database only
cd backend && mvn spring-boot:run                 # API on :8080 (add -Dspring-boot.run.profiles=demo for the dataset)
cd frontend && npm install && npm run dev         # UI on :5173
```

## Tests

```bash
cd backend && mvn verify          # needs Docker running (Testcontainers starts PostgreSQL)
```

| Suite | What it proves |
|---|---|
| `BookingConflictTest` | 20 simultaneous bookings for one slot → exactly 1 succeeds; cancel/rebook; repeated cancel can't free someone else's slot; past slots rejected |
| `RolePermissionTest` | Every protected endpoint × {anonymous, patient, doctor, admin} → 401/403/allowed; cross-user (IDOR) checks; tampered and revoked tokens; sign-up can't create admins |
| `AuthApiTest` | Validation errors, generic login failures, lockout after 5 tries, token revocation on password change, no password hashes in responses |
| `AppointmentApiTest` | Slot generation rules (including the midnight regression), malformed input, full visit lifecycle, audit entries without clinical text |
| `RateLimitTest` | 11th auth request per minute from one IP → 429 with `Retry-After`; other clients unaffected |
| `ReminderQueueTest` | Idempotent enqueue, retry then success, dead-lettering and admin requeue, skipping canceled visits, 4 concurrent workers with no duplicate sends, crashed-worker recovery |

## Performance

Measured on an Apple M4 laptop with everything in Docker. Full method and how to reproduce: [docs/PERFORMANCE.md](docs/PERFORMANCE.md).

| Scenario | Result |
|---|---|
| 2,000 concurrent booking attempts on 50 slots (100 users) | 50 booked, 1,950 × 409, **0 errors, 0 double bookings**; p95 144 ms |
| Mixed read traffic, 50 concurrent users, 60 s | 36,035 requests, **0 failures**, p95 **2.8 ms** |
| Reminder queue, 5,000 jobs (excludes SMTP) | **913 jobs/s** with 1 worker, **2,275 jobs/s** with 4, 0 duplicates |

## Documentation

- [Architecture](docs/ARCHITECTURE.md): components, data model, booking and queue design, state machines
- [Security](docs/SECURITY.md): each control, why it was chosen, trade-offs, known gaps
- [API](docs/API.md): endpoints, error format, examples (plus Swagger UI)
- [Performance](docs/PERFORMANCE.md): load test method and results
- [Deployment](docs/DEPLOYMENT.md): Neon + Render + Vercel walkthrough

## Project structure

```
├── backend/            Spring Boot API (config, security, controller, service, notification, repository, seed)
│   └── src/test/       Testcontainers integration tests + queue benchmark
├── frontend/           React SPA + nginx config
├── perf/               k6 load test scripts
├── docs/               Architecture, security, API, performance, deployment
├── docker-compose.yml  Postgres + API + frontend + Mailpit
├── render.yaml         Render blueprint for the API
└── .github/workflows/  CI: tests, build, container images
```
