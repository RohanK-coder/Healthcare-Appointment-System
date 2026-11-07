# Performance results

Measured on **2026-11-06** on a laptop, so treat these as relative numbers, not production capacity.

**Environment:** Apple M4 (10 cores), Docker Desktop with 10 CPUs and 8 GB RAM. PostgreSQL 16, the backend
(Java 21, default Hikari pool of 10) and k6 0.54 all ran as containers on one Docker network. Synthetic
`demo` dataset: 12 doctors, 200 patients, ~3,300 slots, ~2,400 appointments. Rate limiting was **disabled**
for the load tests (`RATE_LIMIT_ENABLED=false`); otherwise the limiter, not the app, is what gets measured.

## 1. Booking under contention

`perf/booking-contention.js`: 100 virtual users fire **2,000 booking attempts** at **50 slots**, picking a
random slot each time, so most requests race for an already-taken slot.

| Metric | Result |
|---|---|
| Successful bookings | **50** (exactly one per slot) |
| 409 Conflict responses | **1,950** |
| Other statuses (5xx etc.) | **0** |
| Double-booked slots in the database afterwards | **0** (checked with SQL) |
| Booking latency p50 / p95 / p99 / max | **57 ms / 144 ms / 200 ms / 311 ms** |

Latency under contention is dominated by requests queuing on the slot's row lock, which is the intended
behavior: correctness first, then speed.

## 2. Mixed read traffic

`perf/mixed-load.js`: ramp to **50 concurrent logged-in users**, hold 60 s. 80% act as patients (list doctors →
available slots → my appointments), 20% as doctors (my appointments), with 0–0.5 s think time.

| Metric | Result |
|---|---|
| Requests | **36,035** |
| Failed requests | **0 (0.00%)** |
| Latency p50 / p95 / p99 | **0.8 ms / 2.8 ms / 3.9 ms** |
| Throughput | ~431 requests/s (limited by the think time, not by the server) |

The max latency (289 ms) comes from the login calls in setup, which are slowed on purpose by BCrypt cost 12.
These list endpoints run at most 2–3 queries each because of fetch joins and batched summary loading;
before that change, listing N appointments ran several extra queries per row (patient, doctor, slot, summary).

## 3. Reminder queue throughput

`ReminderThroughputBenchmark` (JUnit, Testcontainers PostgreSQL): 5,000 booked appointments in the next
24 h. Uses an in-memory sender, so these numbers are **queue overhead only** (enqueue, claim, status
updates) and leave out SMTP latency.

| Workers | Enqueue 5,000 jobs | Process 5,000 jobs | Throughput | Duplicates |
|---|---|---|---|---|
| 1 | 122 ms | 5.5 s | **913 jobs/s** | 0 |
| 4 | 103 ms | 2.2 s | **2,275 jobs/s** | 0 |

Workers scale because `FOR UPDATE SKIP LOCKED` hands each one a disjoint batch. With real SMTP, sending
time dominates, and adding workers or instances scales throughput linearly until the mail provider's
rate limit.

## Reproduce

```bash
# 1. Start the stack with the synthetic dataset and rate limiting off
RATE_LIMIT_ENABLED=false docker compose up -d --build

# 2. Load tests (k6 in Docker on the compose network)
docker run --rm --network healthcare-appointment-system_default -v "$PWD/perf:/perf" \
  -e BASE_URL=http://backend:8080/api grafana/k6:0.54.0 run /perf/booking-contention.js
docker run --rm --network healthcare-appointment-system_default -v "$PWD/perf:/perf" \
  -e BASE_URL=http://backend:8080/api grafana/k6:0.54.0 run /perf/mixed-load.js

# 3. Verify no double bookings
docker exec healthcare-postgres psql -U postgres -d healthcare_appointments -c \
  "SELECT slot_id, count(*) FROM appointments WHERE status <> 'CANCELED' GROUP BY slot_id HAVING count(*) > 1;"

# 4. Queue benchmark
cd backend && mvn test -Dgroups=benchmark -Dsurefire.excludedGroups= -Dtest=ReminderThroughputBenchmark
```

The compose network name is `<folder-name>_default`; check it with `docker network ls`.
