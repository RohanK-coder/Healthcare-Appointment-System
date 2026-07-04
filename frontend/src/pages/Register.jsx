import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { getErrorMessage } from '../api/client.js';
import { useAuth } from '../state/AuthContext.jsx';

export default function Register() {
  const [form, setForm] = useState({
    fullName: '', email: '', password: '', role: 'PATIENT', phone: '', specialization: '', department: ''
  });
  const [error, setError] = useState('');
  const { register } = useAuth();
  const navigate = useNavigate();

  function update(field, value) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    try {
      const payload = { ...form };
      if (payload.role !== 'DOCTOR') {
        delete payload.specialization;
        delete payload.department;
      }
      const user = await register(payload);
      navigate(`/${user.role.toLowerCase()}/dashboard`);
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  return (
    <main className="auth-page">
      <form className="auth-card wide" onSubmit={handleSubmit}>
        <h1>Create account</h1>
        {error && <div className="alert">{error}</div>}
        <label>Full name<input value={form.fullName} onChange={(e) => update('fullName', e.target.value)} /></label>
        <label>Email<input value={form.email} onChange={(e) => update('email', e.target.value)} /></label>
        <label>Password<input type="password" value={form.password} onChange={(e) => update('password', e.target.value)} /></label>
        <label>Role
          <select value={form.role} onChange={(e) => update('role', e.target.value)}>
            <option value="PATIENT">Patient</option>
            <option value="DOCTOR">Doctor</option>
            <option value="ADMIN">Admin</option>
          </select>
        </label>
        {form.role === 'PATIENT' && <label>Phone<input value={form.phone} onChange={(e) => update('phone', e.target.value)} /></label>}
        {form.role === 'DOCTOR' && <>
          <label>Specialization<input value={form.specialization} onChange={(e) => update('specialization', e.target.value)} /></label>
          <label>Department<input value={form.department} onChange={(e) => update('department', e.target.value)} /></label>
        </>}
        <button className="full">Register</button>
        <p className="muted">Already have an account? <Link to="/login">Login</Link></p>
      </form>
    </main>
  );
}
