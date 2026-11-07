# API reference

Interactive docs (OpenAPI 3, generated from the code): **`/swagger-ui.html`** on the backend
(`http://localhost:8080/swagger-ui.html` locally, or `http://localhost:3000/swagger-ui.html` via compose).
Raw spec: `/v3/api-docs`. Click **Authorize** and paste a token from `POST /api/auth/login`.

Base path: `/api`. All bodies are JSON. Timestamps are ISO-8601 local date-times in the clinic timezone.

## Endpoints

| Method | Path | Role | Description |
|---|---|---|---|
| POST | `/auth/register` | public | Create a **patient** account → `{token, user}` |
| POST | `/auth/login` | public | Log in → `{token, user}` (401 bad credentials, 423 locked) |
| GET | `/auth/me` | any | Current user |
| PUT | `/auth/password` | any | Change password; revokes older tokens → new `{token, user}` |
| GET | `/patient/doctors` | PATIENT | Active doctors |
| GET | `/patient/doctors/{id}/slots?availableOnly=true` | PATIENT | Future available slots (or `?date=YYYY-MM-DD` for one day) |
| POST | `/patient/appointments` | PATIENT | Book `{slotId, reason?}` (409 if taken, 400 if in the past) |
| GET | `/patient/appointments` | PATIENT | My appointments with summaries |
| PUT | `/patient/appointments/{id}/cancel` | PATIENT | Cancel my booked appointment |
| POST | `/doctor/slots` | DOCTOR | Generate slots `{date, startTime, endTime, durationMinutes}` |
| GET | `/doctor/appointments` | DOCTOR | My appointments |
| PUT | `/doctor/appointments/{id}/status` | DOCTOR | `{status: COMPLETED \| NO_SHOW \| CANCELED}` from BOOKED |
| POST | `/doctor/appointments/{id}/summary` | DOCTOR | Write/update visit summary; marks COMPLETED |
| GET | `/admin/users` | ADMIN | All users |
| POST | `/admin/doctors` | ADMIN | Create doctor account (201) |
| POST | `/admin/admins` | ADMIN | Create admin account (201) |
| GET | `/admin/appointments` | ADMIN | All appointments |
| GET | `/admin/reports` | ADMIN | Counts by role, appointment status, notification status |
| GET | `/admin/audit-logs?action=&page=&size=` | ADMIN | Paged audit trail (newest first, size ≤ 200) |
| GET | `/admin/notifications?status=&page=&size=` | ADMIN | Paged reminder queue |
| POST | `/admin/notifications/{id}/retry` | ADMIN | Requeue a DEAD/FAILED reminder |
| GET | `/actuator/health` | public | Liveness/readiness (`/actuator/health/readiness`) |
| GET | `/actuator/metrics` | ADMIN | Micrometer metrics, e.g. `notifications.processed` |

## Errors

Every error is JSON:

```json
{ "timestamp": "2026-11-06T19:18:41", "status": 409, "error": "Conflict", "message": "This slot is no longer available." }
```

Validation errors add per-field messages:

```json
{ "status": 400, "error": "Validation failed",
  "message": "password: must be 8-72 characters and contain a letter and a digit",
  "fields": { "password": "must be 8-72 characters and contain a letter and a digit" } }
```

| Status | Meaning |
|---|---|
| 400 | Validation failed, malformed JSON, or invalid state transition |
| 401 | Missing/invalid/expired/revoked token, or bad credentials |
| 403 | Authenticated but wrong role, or not your resource |
| 404 | Resource not found |
| 409 | Conflict: slot already booked, email already registered |
| 423 | Account temporarily locked after repeated failed logins |
| 429 | Rate limited; see the `Retry-After` header |

## Example session

```bash
API=http://localhost:8080/api
TOKEN=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"patient@careslot.test","password":"DemoPass123"}' | jq -r .token)

curl -s $API/patient/doctors -H "Authorization: Bearer $TOKEN" | jq '.[0]'
curl -s "$API/patient/doctors/1/slots?availableOnly=true" -H "Authorization: Bearer $TOKEN" | jq '.[0]'
curl -s -X POST $API/patient/appointments -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"slotId": 1, "reason": "Checkup"}'
```
