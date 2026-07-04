import { useEffect, useState } from 'react';
import { api, getErrorMessage } from '../../api/client.js';
import PortalLayout from '../../components/PortalLayout.jsx';

export default function AdminDashboard() {
  const [report, setReport] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    api.get('/admin/reports').then((res) => setReport(res.data)).catch((err) => setError(getErrorMessage(err)));
  }, []);

  return (
    <PortalLayout title="Admin Dashboard">
      {error && <div className="alert">{error}</div>}
      <section className="stats-grid">
        {report && Object.entries({
          Users: report.users,
          Doctors: report.doctors,
          Patients: report.patients,
          Appointments: report.appointments,
          Booked: report.booked,
          Completed: report.completed,
          Canceled: report.canceled
        }).map(([label, value]) => (
          <div className="stat-card" key={label}><span>{label}</span><strong>{value}</strong></div>
        ))}
      </section>
    </PortalLayout>
  );
}
