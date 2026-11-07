import http from 'k6/http';

export const BASE = __ENV.BASE_URL || 'http://localhost:8080/api';
export const PASSWORD = __ENV.DEMO_PASSWORD || 'DemoPass123';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export function login(email) {
  const res = http.post(`${BASE}/auth/login`, JSON.stringify({ email, password: PASSWORD }),
    { headers: JSON_HEADERS, tags: { name: 'login' } });
  if (res.status !== 200) throw new Error(`login failed for ${email}: ${res.status} ${res.body}`);
  return res.json('token');
}

/** Logs in many accounts in parallel batches (BCrypt makes each login deliberately slow). */
export function loginMany(emails) {
  const tokens = [];
  for (let i = 0; i < emails.length; i += 20) {
    const batch = emails.slice(i, i + 20).map((email) => ['POST', `${BASE}/auth/login`,
      JSON.stringify({ email, password: PASSWORD }), { headers: JSON_HEADERS, tags: { name: 'login' } }]);
    for (const res of http.batch(batch)) {
      if (res.status !== 200) throw new Error(`login failed: ${res.status} ${res.body}`);
      tokens.push(res.json('token'));
    }
  }
  return tokens;
}

export function authed(token, name) {
  return { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` }, tags: { name } };
}

export function patientEmails(n) {
  return Array.from({ length: n }, (_, i) => `patient${String(i + 1).padStart(3, '0')}@patients.careslot.test`);
}
