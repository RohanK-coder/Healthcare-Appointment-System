// Many patients race for a small set of slots. Correct behavior: every slot is booked exactly once
// and every other attempt gets 409 Conflict, with no 5xx errors.
//   k6 run -e BASE_URL=http://localhost:8080/api perf/booking-contention.js
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE, authed, login, loginMany, patientEmails } from './lib.js';

const SLOT_COUNT = Number(__ENV.SLOTS || 50);
const PATIENTS = Number(__ENV.PATIENTS || 100);
const booked = new Counter('bookings_succeeded');
const conflicts = new Counter('bookings_conflicted');
const unexpected = new Counter('bookings_unexpected_status');

export const options = {
  scenarios: {
    contention: {
      executor: 'shared-iterations',
      vus: Number(__ENV.VUS || 100),
      iterations: Number(__ENV.ATTEMPTS || 2000),
      maxDuration: '2m',
    },
  },
  thresholds: {
    'http_req_duration{name:book}': ['p(95)<500'],
    bookings_unexpected_status: ['count==0'],
    bookings_succeeded: [`count==${SLOT_COUNT}`],
  },
};

export function setup() {
  const doctorToken = login('doctor@careslot.test');
  // A far-future day with no other slots, so each run starts from a clean set.
  const day = new Date(Date.now() + (60 + Math.floor(Math.random() * 100)) * 86400000).toISOString().slice(0, 10);
  const minutes = 10;
  const end = new Date(Date.UTC(2000, 0, 1, 8, 0) + SLOT_COUNT * minutes * 60000).toISOString().slice(11, 16);
  const res = http.post(`${BASE}/doctor/slots`,
    JSON.stringify({ date: day, startTime: '08:00', endTime: end, durationMinutes: minutes }),
    authed(doctorToken, 'create-slots'));
  const slotIds = res.json().map((s) => s.id);
  if (slotIds.length !== SLOT_COUNT) throw new Error(`expected ${SLOT_COUNT} new slots on ${day}, got ${slotIds.length}`);
  return { slotIds, tokens: loginMany(patientEmails(PATIENTS)) };
}

export default function ({ slotIds, tokens }) {
  const token = tokens[Math.floor(Math.random() * tokens.length)];
  const slotId = slotIds[Math.floor(Math.random() * slotIds.length)];
  const res = http.post(`${BASE}/patient/appointments`, JSON.stringify({ slotId, reason: 'load test' }), authed(token, 'book'));
  if (res.status === 200) booked.add(1);
  else if (res.status === 409) conflicts.add(1);
  else unexpected.add(1);
  check(res, { 'booked or conflict': (r) => r.status === 200 || r.status === 409 });
}
