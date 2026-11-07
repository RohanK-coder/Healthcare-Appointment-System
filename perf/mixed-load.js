// Read-heavy traffic from logged-in patients and doctors, ramping to 50 concurrent users.
//   k6 run -e BASE_URL=http://localhost:8080/api perf/mixed-load.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE, authed, loginMany, patientEmails } from './lib.js';

export const options = {
  scenarios: {
    mixed: {
      executor: 'ramping-vus',
      stages: [
        { duration: '15s', target: Number(__ENV.VUS || 50) },
        { duration: __ENV.HOLD || '60s', target: Number(__ENV.VUS || 50) },
        { duration: '5s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{expected_response:true}': ['p(95)<300'],
  },
};

export function setup() {
  const doctors = Array.from({ length: 12 }, (_, i) => `doctor${String(i + 1).padStart(2, '0')}@doctors.careslot.test`);
  return { patients: loginMany(patientEmails(50)), doctors: loginMany(doctors) };
}

export default function ({ patients, doctors }) {
  if (Math.random() < 0.8) {
    const token = patients[Math.floor(Math.random() * patients.length)];
    const list = http.get(`${BASE}/patient/doctors`, authed(token, 'list-doctors'));
    check(list, { 'doctors 200': (r) => r.status === 200 });
    const doctorId = list.json()[Math.floor(Math.random() * list.json().length)].id;
    check(http.get(`${BASE}/patient/doctors/${doctorId}/slots?availableOnly=true`, authed(token, 'available-slots')),
      { 'slots 200': (r) => r.status === 200 });
    check(http.get(`${BASE}/patient/appointments`, authed(token, 'my-appointments')),
      { 'appointments 200': (r) => r.status === 200 });
  } else {
    const token = doctors[Math.floor(Math.random() * doctors.length)];
    check(http.get(`${BASE}/doctor/appointments`, authed(token, 'doctor-appointments')),
      { 'doctor appointments 200': (r) => r.status === 200 });
  }
  sleep(Math.random() * 0.5);
}
