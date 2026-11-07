import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { getErrorMessage } from '../api/client.js';
import DemoBanner from '../components/DemoBanner.jsx';
import { useAuth } from '../state/AuthContext.jsx';

// Mirrors the server rule in Validation.PASSWORD_REGEX; the server remains the authority.
const PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/;

export default function Register() {
  const [form, setForm] = useState({ fullName: '', email: '', password: '', phone: '', dateOfBirth: '' });
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const { register } = useAuth();
  const navigate = useNavigate();

  function update(field, value) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    setFieldErrors({});
    if (!PASSWORD_RULE.test(form.password)) {
      setFieldErrors({ password: 'must be 8-72 characters and contain a letter and a digit' });
      return;
    }
    try {
      const payload = { ...form, dateOfBirth: form.dateOfBirth || null };
      const user = await register(payload);
      navigate(`/${user.role.toLowerCase()}/dashboard`);
    } catch (err) {
      setFieldErrors(err?.response?.data?.fields || {});
      setError(getErrorMessage(err));
    }
  }

  const hint = (field) => fieldErrors[field] && <small className="field-error">{fieldErrors[field]}</small>;

  return (
    <main className="auth-page">
      <form className="auth-card wide" onSubmit={handleSubmit}>
        <h1>Create patient account</h1>
        <DemoBanner />
        <p className="muted small">Doctor and admin accounts are created by an administrator.</p>
        {error && <div className="alert">{error}</div>}
        <label>Full name<input value={form.fullName} onChange={(e) => update('fullName', e.target.value)} />{hint('fullName')}</label>
        <label>Email<input type="email" autoComplete="username" value={form.email} onChange={(e) => update('email', e.target.value)} />{hint('email')}</label>
        <label>Password
          <input type="password" autoComplete="new-password" value={form.password} onChange={(e) => update('password', e.target.value)} />
          <small className="muted">At least 8 characters with a letter and a digit.</small>
          {hint('password')}
        </label>
        <label>Phone (optional)<input value={form.phone} onChange={(e) => update('phone', e.target.value)} />{hint('phone')}</label>
        <label>Date of birth (optional)<input type="date" value={form.dateOfBirth} onChange={(e) => update('dateOfBirth', e.target.value)} />{hint('dateOfBirth')}</label>
        <button className="full">Register</button>
        <p className="muted">Already have an account? <Link to="/login">Login</Link></p>
      </form>
    </main>
  );
}
