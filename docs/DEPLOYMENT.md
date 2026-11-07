# Deploying the demo

Recommended free-tier setup: **Neon** (PostgreSQL) + **Render** (API container) + **Vercel** (frontend).
Any host that runs a Docker image and a static site works the same way.

## 1. Database (Neon)

1. Create a project and database.
2. Neon shows a URL like `postgresql://USER:PASSWORD@HOST/DB?sslmode=require`. Spring needs it split up:
   - `DB_URL=jdbc:postgresql://HOST/DB?sslmode=require`
   - `DB_USERNAME=USER`
   - `DB_PASSWORD=PASSWORD`

Flyway creates the schema on first start.

## 2. API (Render)

1. New → **Blueprint** → select this repo (uses `render.yaml`), or New → Web Service → Docker, root `backend`.
2. Set environment variables:

| Variable | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod,demo` (`demo` seeds the synthetic dataset once) |
| `JWT_SECRET` | random, at least 32 chars (`openssl rand -base64 48`; the blueprint generates one) |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | from Neon |
| `FRONTEND_URL` | your Vercel URL, e.g. `https://careslot.vercel.app` (comma-separate several) |
| `DB_POOL_SIZE` | `5` (free Postgres tiers have small connection limits) |
| `NOTIFICATION_CHANNEL` | `log`, or `email` with `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` |
| `CLINIC_ZONE` | optional, e.g. `America/New_York` |

3. Health check path: `/actuator/health/readiness`.
4. Check `https://<service>.onrender.com/swagger-ui.html`.

Free Render services sleep when idle; the first request after a nap takes ~30–60 s.

## 3. Frontend (Vercel)

1. Import the repo; set **Root Directory** to `frontend` (framework: Vite).
2. Environment variable: `VITE_API_BASE_URL=https://<service>.onrender.com/api`.
3. Deploy. `frontend/vercel.json` rewrites all paths to `index.html` for client-side routing.

## 4. Smoke test

- Log in with each demo account (`admin@careslot.test`, `doctor@careslot.test`, `patient@careslot.test`, password `DemoPass123`).
- Book a slot as the patient; confirm it appears for the doctor and in the admin audit log.
- `GET /actuator/health` returns `UP`.

## Container images

On every push to `main`, CI publishes `ghcr.io/<owner>/<repo>-backend` and `-frontend` (tags `latest` and the
commit SHA). The frontend image proxies `/api` to a host named `backend`, so it's meant for Docker Compose;
on Vercel the frontend is built from source instead.

## Operating the demo

- Demo account passwords and lockouts reset on every backend restart, and those accounts can't change their passwords.
- To wipe data, drop and recreate the database; the next start re-runs migrations and seeding.
