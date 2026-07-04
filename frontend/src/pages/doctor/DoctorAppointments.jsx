import { useEffect, useState } from 'react';
import { api, getErrorMessage } from '../../api/client.js';
import AppointmentTable from '../../components/AppointmentTable.jsx';
import PortalLayout from '../../components/PortalLayout.jsx';

export default function DoctorAppointments() {
  const [appointments, setAppointments] = useState([]);
  const [summary, setSummary] = useState({ diagnosis: 'Routine visit completed', prescription: 'Follow standard care plan', doctorNotes: 'Patient examined.' });
  const [error, setError] = useState('');

  async function load() {
    try {
      const { data } = await api.get('/doctor/appointments');
      setAppointments(data);
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  useEffect(() => { load(); }, []);

  async function complete(id) {
    setError('');
    try {
      await api.post(`/doctor/appointments/${id}/summary`, summary);
      await load();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  return (
    <PortalLayout title="Appointments">
      {error && <div className="alert">{error}</div>}
      <section className="card form-grid">
        <label>Diagnosis<input value={summary.diagnosis} onChange={(e) => setSummary({ ...summary, diagnosis: e.target.value })} /></label>
        <label>Prescription<input value={summary.prescription} onChange={(e) => setSummary({ ...summary, prescription: e.target.value })} /></label>
        <label className="span-all">Doctor notes<textarea value={summary.doctorNotes} onChange={(e) => setSummary({ ...summary, doctorNotes: e.target.value })} /></label>
      </section>
      <AppointmentTable appointments={appointments} onComplete={complete} />
    </PortalLayout>
  );
}
