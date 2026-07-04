import { Link } from 'react-router-dom';
import PortalLayout from '../../components/PortalLayout.jsx';

export default function DoctorDashboard() {
  return (
    <PortalLayout title="Doctor Dashboard">
      <section className="grid two">
        <div className="card">
          <h2>Create calendar slots</h2>
          <p className="muted">Generate availability in fixed durations for patients to book.</p>
          <Link className="button" to="/doctor/slots">Manage slots</Link>
        </div>
        <div className="card">
          <h2>Patient appointments</h2>
          <p className="muted">Review booked appointments and write summaries.</p>
          <Link className="button secondary" to="/doctor/appointments">View appointments</Link>
        </div>
      </section>
    </PortalLayout>
  );
}
