import { Link } from 'react-router-dom';
import DemoBanner from '../components/DemoBanner.jsx';
import { DEMO_ACCOUNTS, DEMO_PASSWORD } from '../demo.js';
import { useAuth } from '../state/AuthContext.jsx';

const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';
const swaggerUrl = apiBase.replace(/\/api\/?$/, '') + '/swagger-ui.html';

export default function Home() {
  const { user } = useAuth();
  const dashboard = user ? `/${user.role.toLowerCase()}/dashboard` : '/login';

  return (
    <main className="landing">
      <section className="hero-card">
        <span className="eyebrow">Spring Boot + PostgreSQL + React</span>
        <h1>Healthcare Appointment System</h1>
        <p>
          Three portals for patients, doctors, and admins with JWT role-based access, conflict-safe slot booking,
          a retrying reminder queue, rate limiting, and an audit trail.
        </p>
        <DemoBanner />
        <div className="hero-actions">
          <Link className="button" to={dashboard}>Open portal</Link>
          <Link className="button secondary" to="/register">Create patient account</Link>
          <a className="button secondary" href={swaggerUrl} target="_blank" rel="noreferrer">API docs</a>
        </div>
        <div className="demo-logins">
          <strong>Demo logins (password {DEMO_PASSWORD}):</strong>
          {DEMO_ACCOUNTS.map(([role, email]) => <span key={email}>{role}: {email}</span>)}
        </div>
      </section>
    </main>
  );
}
