import { useEffect, useState } from 'react';
import { api, getErrorMessage } from '../../api/client.js';
import PortalLayout from '../../components/PortalLayout.jsx';

export default function AdminUsers() {
  const [users, setUsers] = useState([]);
  const emptyDoctor = { fullName: '', email: '', password: '', specialization: '', department: '' };
  const emptyAdmin = { fullName: '', email: '', password: '' };
  const [doctor, setDoctor] = useState(emptyDoctor);
  const [newAdmin, setNewAdmin] = useState(emptyAdmin);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  async function load() {
    const { data } = await api.get('/admin/users');
    setUsers(data);
  }

  useEffect(() => { load().catch((err) => setError(getErrorMessage(err))); }, []);

  async function create(e, path, body, reset, label) {
    e.preventDefault();
    setError('');
    setMessage('');
    try {
      await api.post(path, body);
      reset();
      setMessage(`${label} created.`);
      await load();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  return (
    <PortalLayout title="Users">
      {error && <div className="alert">{error}</div>}
      {message && <div className="success">{message}</div>}
      <form className="card form-grid" onSubmit={(e) => create(e, '/admin/doctors', doctor, () => setDoctor(emptyDoctor), 'Doctor')}>
        <h2 className="span-all">Create doctor</h2>
        <label>Name<input value={doctor.fullName} onChange={(e) => setDoctor({ ...doctor, fullName: e.target.value })} /></label>
        <label>Email<input value={doctor.email} onChange={(e) => setDoctor({ ...doctor, email: e.target.value })} /></label>
        <label>Initial password<input type="password" autoComplete="new-password" value={doctor.password} onChange={(e) => setDoctor({ ...doctor, password: e.target.value })} /></label>
        <label>Specialization<input value={doctor.specialization} onChange={(e) => setDoctor({ ...doctor, specialization: e.target.value })} /></label>
        <label>Department<input value={doctor.department} onChange={(e) => setDoctor({ ...doctor, department: e.target.value })} /></label>
        <button className="span-all">Create doctor</button>
      </form>
      <form className="card form-grid" onSubmit={(e) => create(e, '/admin/admins', newAdmin, () => setNewAdmin(emptyAdmin), 'Admin')}>
        <h2 className="span-all">Create admin</h2>
        <label>Name<input value={newAdmin.fullName} onChange={(e) => setNewAdmin({ ...newAdmin, fullName: e.target.value })} /></label>
        <label>Email<input value={newAdmin.email} onChange={(e) => setNewAdmin({ ...newAdmin, email: e.target.value })} /></label>
        <label>Initial password<input type="password" autoComplete="new-password" value={newAdmin.password} onChange={(e) => setNewAdmin({ ...newAdmin, password: e.target.value })} /></label>
        <button className="span-all">Create admin</button>
      </form>
      <div className="table-wrap">
        <table>
          <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Active</th></tr></thead>
          <tbody>{users.map((u) => <tr key={u.id}><td>{u.fullName}</td><td>{u.email}</td><td>{u.role}</td><td>{String(u.active)}</td></tr>)}</tbody>
        </table>
      </div>
    </PortalLayout>
  );
}
