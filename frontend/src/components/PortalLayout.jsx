import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../state/AuthContext.jsx';

const links = {
  PATIENT: [
    ['Dashboard', '/patient/dashboard'],
    ['Find Doctors', '/patient/doctors'],
    ['My Appointments', '/patient/appointments']
  ],
  DOCTOR: [
    ['Dashboard', '/doctor/dashboard'],
    ['Manage Slots', '/doctor/slots'],
    ['Appointments', '/doctor/appointments']
  ],
  ADMIN: [
    ['Dashboard', '/admin/dashboard'],
    ['Users', '/admin/users'],
    ['Appointments', '/admin/appointments']
  ]
};

export default function PortalLayout({ children, title }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate('/login');
  }

  return (
    <div className="portal-shell">
      <aside className="sidebar">
        <Link className="brand" to="/">CareSlot</Link>
        <p className="muted small">{user?.role} portal</p>
        <nav>
          {(links[user?.role] || []).map(([label, href]) => (
            <NavLink key={href} to={href}>{label}</NavLink>
          ))}
        </nav>
        <button className="secondary full" onClick={handleLogout}>Logout</button>
      </aside>
      <main className="portal-main">
        <header className="page-header">
          <div>
            <p className="muted">Welcome, {user?.fullName}</p>
            <h1>{title}</h1>
          </div>
        </header>
        {children}
      </main>
    </div>
  );
}
