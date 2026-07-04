import { useEffect, useState } from 'react';
import { api, getErrorMessage } from '../../api/client.js';
import PortalLayout from '../../components/PortalLayout.jsx';

export default function PatientDoctors() {
  const [doctors, setDoctors] = useState([]);
  const [slots, setSlots] = useState({});
  const [reason, setReason] = useState('Routine consultation');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    api.get('/patient/doctors')
      .then((res) => setDoctors(res.data))
      .catch((err) => setError(getErrorMessage(err)));
  }, []);

  async function loadSlots(doctorId) {
    setError('');
    const { data } = await api.get(`/patient/doctors/${doctorId}/slots?availableOnly=true`);
    setSlots((prev) => ({ ...prev, [doctorId]: data }));
  }

  async function book(slotId) {
    setError('');
    setMessage('');
    try {
      await api.post('/patient/appointments', { slotId, reason });
      setMessage('Appointment booked successfully.');
      setSlots({});
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  return (
    <PortalLayout title="Find Doctors">
      {error && <div className="alert">{error}</div>}
      {message && <div className="success">{message}</div>}
      <label className="reason-box">Reason for visit<input value={reason} onChange={(e) => setReason(e.target.value)} /></label>
      <div className="grid two">
        {doctors.map((doctor) => (
          <article className="card" key={doctor.id}>
            <h2>Dr. {doctor.fullName}</h2>
            <p className="muted">{doctor.specialization} - {doctor.department}</p>
            <p>{doctor.bio || 'No bio available.'}</p>
            <button onClick={() => loadSlots(doctor.id)}>View available slots</button>
            <div className="slot-list">
              {(slots[doctor.id] || []).map((slot) => (
                <button className="slot" key={slot.id} onClick={() => book(slot.id)}>
                  {new Date(slot.slotStart).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })}
                </button>
              ))}
              {slots[doctor.id]?.length === 0 && <p className="muted">No available slots.</p>}
            </div>
          </article>
        ))}
      </div>
    </PortalLayout>
  );
}
