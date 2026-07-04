export default function AppointmentTable({ appointments, onCancel, onComplete }) {
  if (!appointments?.length) return <div className="empty">No appointments found.</div>;

  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Time</th>
            <th>Doctor</th>
            <th>Patient</th>
            <th>Status</th>
            <th>Reason</th>
            <th>Summary</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {appointments.map((a) => (
            <tr key={a.id}>
              <td>{formatDate(a.slotStart)}</td>
              <td>{a.doctorName}</td>
              <td>{a.patientName}</td>
              <td><span className={`pill ${a.status.toLowerCase()}`}>{a.status}</span></td>
              <td>{a.reason || '-'}</td>
              <td>{a.summary ? a.summary.diagnosis || 'Added' : '-'}</td>
              <td className="actions">
                {onCancel && a.status === 'BOOKED' && <button className="danger" onClick={() => onCancel(a.id)}>Cancel</button>}
                {onComplete && a.status === 'BOOKED' && <button onClick={() => onComplete(a.id)}>Complete</button>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function formatDate(value) {
  return new Date(value).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
}
