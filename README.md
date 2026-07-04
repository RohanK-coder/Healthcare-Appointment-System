# Healthcare Appointment System

Full-stack three-portal healthcare appointment system built with Spring Boot, Spring Security JWT RBAC, PostgreSQL, Flyway, and React.

## Features

- Patient portal: browse doctors, view available slots, book/cancel appointments, view summaries.
- Doctor portal: generate calendar-based availability slots, view appointments, complete visits, write summaries.
- Admin portal: manage users/doctors, view all appointments, view dashboard reports.
- Backend security: Spring Security, JWT authentication, role-based access control.
- Database reliability: PostgreSQL schema managed by Flyway migrations.
- Scheduling: background reminder processor marks upcoming booked appointments and creates notification records.
- Double-booking protection: database unique constraint plus pessimistic row lock during appointment booking.

## Project structure

```txt
healthcare-appointment-system/
├── backend/                 # Spring Boot API
├── frontend/                # React + Vite UI
├── docker-compose.yml       # PostgreSQL + backend container
└── README.md
```

## Requirements

Install these before running locally:

- Java 17+
- Maven 3.9+
- Node.js 18+
- npm 9+
- Docker Desktop, recommended for PostgreSQL

## Demo accounts

The backend seeds these accounts automatically on startup:

| Role | Email | Password |
|---|---|---|
| Admin | `admin@healthcare.local` | `password` |
| Doctor | `doctor@healthcare.local` | `password` |
| Patient | `patient@healthcare.local` | `password` |

## Option 1: Run with Docker for database + local dev servers

From the project root:

```bash
docker compose up -d postgres
```

Run the backend:

```bash
cd backend
mvn spring-boot:run
```

Run the frontend in a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open:

```txt
http://localhost:5173
```

Backend API base URL:

```txt
http://localhost:8080/api
```

## Option 2: Run backend with Docker Compose

From the project root:

```bash
docker compose up --build
```

Then run the frontend separately:

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`.

## Common workflow to test the app

1. Login as `doctor@healthcare.local` / `password`.
2. Go to **Manage Slots** and generate slots for tomorrow.
3. Logout.
4. Login as `patient@healthcare.local` / `password`.
5. Go to **Find Doctors**, view available slots, and book one.
6. Go to **My Appointments** to confirm it appears.
7. Logout.
8. Login as `doctor@healthcare.local` again.
9. Go to **Appointments**, add a summary, and complete the appointment.
10. Login as admin to view reports and all appointments.

## Useful API examples

Login:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"patient@healthcare.local","password":"password"}'
```

Create doctor slots after logging in as a doctor:

```bash
curl -X POST http://localhost:8080/api/doctor/slots \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{"date":"2026-07-02","startTime":"09:00","endTime":"12:00","durationMinutes":30}'
```

Book an appointment as a patient:

```bash
curl -X POST http://localhost:8080/api/patient/appointments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{"slotId":1,"reason":"Routine checkup"}'
```

## Backend commands

```bash
cd backend
mvn spring-boot:run
mvn test
mvn clean package
```

## Frontend commands

```bash
cd frontend
npm install
npm run dev
npm run build
npm run preview
```

## Environment variables

Backend:

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/healthcare_appointments` |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | `postgres` |
| `JWT_SECRET` | Dev-only secret in `application.yml` |
| `FRONTEND_URL` | `http://localhost:5173` |

Frontend:

| Variable | Default |
|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080/api` |

## Notes for resume/project demo

The system includes the foundations for the resume points:

- Three portals: patient, doctor, admin.
- Spring Security RBAC: route access is enforced by user role.
- Calendar slot management: doctors generate slots by date/time/duration.
- Automated reminders: scheduled service processes upcoming booked appointments and creates notification rows.
- Flyway migrations: schema is versioned in `backend/src/main/resources/db/migration`.
- Integration testing foundation: Spring Boot context test with H2 PostgreSQL mode.

To demonstrate processing speed for 500+ records, seed or create 500 upcoming booked appointments and inspect the `Processed X appointment reminders in Y ms` backend log emitted by `NotificationService`.
# Healthcare-Appointment-System
