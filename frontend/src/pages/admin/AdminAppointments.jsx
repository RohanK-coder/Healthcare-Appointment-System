import { useEffect, useState } from 'react';
import { api, getErrorMessage } from '../../api/client.js';
import AppointmentTable from '../../components/AppointmentTable.jsx';
import PortalLayout from '../../components/PortalLayout.jsx';

export default function AdminAppointments() {
  const [appointments, setAppointments] = useState([]);
  const [error, setError] = useState('');

  useEffect(() => {
    api.get('/admin/appointments').then((res) => setAppointments(res.data)).catch((err) => setError(getErrorMessage(err)));
  }, []);

  return (
    <PortalLayout title="All Appointments">
      {error && <div className="alert">{error}</div>}
      <AppointmentTable appointments={appointments} />
    </PortalLayout>
  );
}
