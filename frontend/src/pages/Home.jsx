import { Link } from 'react-router-dom';
import { useAuth } from '../state/AuthContext.jsx';

export default function Home() {
  const { user } = useAuth();
  const dashboard = user ? `/${user.role.toLowerCase()}/dashboard` : '/login';

  return (
    <main className="landing">
      <section className="hero-card">
        <span className="eyebrow">Spring Boot + React</span>
        <h1>Healthcare Appointment System</h1>
        <p>
          Three secure portals for patients, doctors, and admins with RBAC, slot-based booking,
          appointment summaries, and automated reminder processing.
        </p>
        <div className="hero-actions">
          <Link className="button" to={dashboard}>Open portal</Link>
          <Link className="button secondary" to="/register">Create account</Link>
        </div>
        <div className="demo-logins">
          <strong>Demo logins:</strong>
          <span>admin@healthcare.local / password</span>
          <span>doctor@healthcare.local / password</span>
          <span>patient@healthcare.local / password</span>
        </div>
      </section>
    </main>
  );
}
