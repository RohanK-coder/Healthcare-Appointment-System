import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { getErrorMessage } from '../api/client.js';
import DemoBanner from '../components/DemoBanner.jsx';
import { DEMO_ACCOUNTS, DEMO_PASSWORD } from '../demo.js';
import { useAuth } from '../state/AuthContext.jsx';

export default function Login() {
  const [email, setEmail] = useState('patient@careslot.test');
  const [password, setPassword] = useState(DEMO_PASSWORD);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      const user = await login(email, password);
      navigate(`/${user.role.toLowerCase()}/dashboard`);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="auth-page">
      <form className="auth-card" onSubmit={handleSubmit}>
        <h1>Login</h1>
        <DemoBanner />
        {error && <div className="alert">{error}</div>}
        <label>Email<input type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} /></label>
        <label>Password<input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} /></label>
        <button className="full" disabled={submitting}>{submitting ? 'Signing in...' : 'Login'}</button>
        <div className="quick-logins">
          {DEMO_ACCOUNTS.map(([role, demoEmail]) => (
            <button type="button" className="secondary" key={demoEmail}
              onClick={() => { setEmail(demoEmail); setPassword(DEMO_PASSWORD); }}>
              {role}
            </button>
          ))}
        </div>
        <p className="muted">No account? <Link to="/register">Register as a patient</Link></p>
      </form>
    </main>
  );
}
