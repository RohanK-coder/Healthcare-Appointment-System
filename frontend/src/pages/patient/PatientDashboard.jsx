import { Link } from 'react-router-dom';
import PortalLayout from '../../components/PortalLayout.jsx';

export default function PatientDashboard() {
  return (
    <PortalLayout title="Patient Dashboard">
      <section className="grid two">
        <div className="card">
          <h2>Book an appointment</h2>
          <p className="muted">Browse doctors and reserve an available calendar slot.</p>
          <Link className="button" to="/patient/doctors">Find doctors</Link>
        </div>
        <div className="card">
          <h2>Appointment history</h2>
          <p className="muted">View booked visits, cancellations, and appointment summaries.</p>
          <Link className="button secondary" to="/patient/appointments">View appointments</Link>
        </div>
      </section>
    </PortalLayout>
  );
}
