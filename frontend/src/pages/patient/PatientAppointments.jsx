import { useEffect, useState } from 'react';
import { api, getErrorMessage } from '../../api/client.js';
import AppointmentTable from '../../components/AppointmentTable.jsx';
import PortalLayout from '../../components/PortalLayout.jsx';

export default function PatientAppointments() {
  const [appointments, setAppointments] = useState([]);
  const [error, setError] = useState('');

  async function load() {
    try {
      const { data } = await api.get('/patient/appointments');
      setAppointments(data);
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  useEffect(() => { load(); }, []);

  async function cancel(id) {
    setError('');
    try {
      await api.put(`/patient/appointments/${id}/cancel`);
      await load();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  return (
    <PortalLayout title="My Appointments">
      {error && <div className="alert">{error}</div>}
      <AppointmentTable appointments={appointments} onCancel={cancel} />
    </PortalLayout>
  );
}
