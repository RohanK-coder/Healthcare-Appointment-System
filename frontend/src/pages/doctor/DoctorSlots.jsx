import { useState } from 'react';
import { api, getErrorMessage } from '../../api/client.js';
import PortalLayout from '../../components/PortalLayout.jsx';

export default function DoctorSlots() {
  const tomorrow = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
  const [form, setForm] = useState({ date: tomorrow, startTime: '09:00', endTime: '13:00', durationMinutes: 30 });
  const [slots, setSlots] = useState([]);
  const [error, setError] = useState('');

  function update(field, value) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function createSlots(e) {
    e.preventDefault();
    setError('');
    try {
      const { data } = await api.post('/doctor/slots', { ...form, durationMinutes: Number(form.durationMinutes) });
      setSlots(data);
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  return (
    <PortalLayout title="Manage Slots">
      <form className="card form-grid" onSubmit={createSlots}>
        {error && <div className="alert span-all">{error}</div>}
        <label>Date<input type="date" value={form.date} onChange={(e) => update('date', e.target.value)} /></label>
        <label>Start time<input type="time" value={form.startTime} onChange={(e) => update('startTime', e.target.value)} /></label>
        <label>End time<input type="time" value={form.endTime} onChange={(e) => update('endTime', e.target.value)} /></label>
        <label>Duration minutes<input type="number" min="5" value={form.durationMinutes} onChange={(e) => update('durationMinutes', e.target.value)} /></label>
        <button className="span-all">Generate slots</button>
      </form>
      <section className="card">
        <h2>Created slots</h2>
        <div className="slot-list horizontal">
          {slots.map((slot) => <span className="slot static" key={slot.id}>{new Date(slot.slotStart).toLocaleTimeString([], { timeStyle: 'short' })}</span>)}
          {!slots.length && <p className="muted">No new slots generated yet.</p>}
        </div>
      </section>
    </PortalLayout>
  );
}
