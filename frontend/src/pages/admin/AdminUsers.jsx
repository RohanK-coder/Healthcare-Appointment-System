import { useEffect, useState } from 'react';
import { api, getErrorMessage } from '../../api/client.js';
import PortalLayout from '../../components/PortalLayout.jsx';

export default function AdminUsers() {
  const [users, setUsers] = useState([]);
  const [doctor, setDoctor] = useState({ fullName: '', email: '', password: 'password', specialization: '', department: '' });
  const [error, setError] = useState('');

  async function load() {
    const { data } = await api.get('/admin/users');
    setUsers(data);
  }

  useEffect(() => { load().catch((err) => setError(getErrorMessage(err))); }, []);

  async function createDoctor(e) {
    e.preventDefault();
    setError('');
    try {
      await api.post('/admin/doctors', doctor);
      setDoctor({ fullName: '', email: '', password: 'password', specialization: '', department: '' });
      await load();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  return (
    <PortalLayout title="Users">
      {error && <div className="alert">{error}</div>}
      <form className="card form-grid" onSubmit={createDoctor}>
        <h2 className="span-all">Create doctor</h2>
        <label>Name<input value={doctor.fullName} onChange={(e) => setDoctor({ ...doctor, fullName: e.target.value })} /></label>
        <label>Email<input value={doctor.email} onChange={(e) => setDoctor({ ...doctor, email: e.target.value })} /></label>
        <label>Password<input value={doctor.password} onChange={(e) => setDoctor({ ...doctor, password: e.target.value })} /></label>
        <label>Specialization<input value={doctor.specialization} onChange={(e) => setDoctor({ ...doctor, specialization: e.target.value })} /></label>
        <label>Department<input value={doctor.department} onChange={(e) => setDoctor({ ...doctor, department: e.target.value })} /></label>
        <button className="span-all">Create doctor</button>
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
