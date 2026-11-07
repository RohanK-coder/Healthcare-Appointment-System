import { useState } from 'react';
import { getErrorMessage } from '../api/client.js';
import PortalLayout from '../components/PortalLayout.jsx';
import { useAuth } from '../state/AuthContext.jsx';

export default function Account() {
  const { user, changePassword } = useAuth();
  const [form, setForm] = useState({ currentPassword: '', newPassword: '' });
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  async function submit(e) {
    e.preventDefault();
    setError('');
    setMessage('');
    try {
      await changePassword(form.currentPassword, form.newPassword);
      setForm({ currentPassword: '', newPassword: '' });
      setMessage('Password changed. Sessions on other devices have been signed out.');
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  return (
    <PortalLayout title="Account">
      <section className="card">
        <p><strong>{user?.fullName}</strong> &middot; {user?.email} &middot; {user?.role}</p>
      </section>
      <form className="card form-grid" onSubmit={submit}>
        <h2 className="span-all">Change password</h2>
        {error && <div className="alert span-all">{error}</div>}
        {message && <div className="success span-all">{message}</div>}
        <label>Current password<input type="password" autoComplete="current-password" value={form.currentPassword}
          onChange={(e) => setForm({ ...form, currentPassword: e.target.value })} /></label>
        <label>New password<input type="password" autoComplete="new-password" value={form.newPassword}
          onChange={(e) => setForm({ ...form, newPassword: e.target.value })} /></label>
        <button className="span-all">Update password</button>
      </form>
    </PortalLayout>
  );
}
