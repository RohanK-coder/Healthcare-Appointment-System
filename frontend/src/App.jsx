import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './state/AuthContext.jsx';
import ProtectedRoute from './components/ProtectedRoute.jsx';
import Login from './pages/Login.jsx';
import Register from './pages/Register.jsx';
import Home from './pages/Home.jsx';
import PatientDashboard from './pages/patient/PatientDashboard.jsx';
import PatientDoctors from './pages/patient/PatientDoctors.jsx';
import PatientAppointments from './pages/patient/PatientAppointments.jsx';
import DoctorDashboard from './pages/doctor/DoctorDashboard.jsx';
import DoctorSlots from './pages/doctor/DoctorSlots.jsx';
import DoctorAppointments from './pages/doctor/DoctorAppointments.jsx';
import AdminDashboard from './pages/admin/AdminDashboard.jsx';
import AdminUsers from './pages/admin/AdminUsers.jsx';
import AdminAppointments from './pages/admin/AdminAppointments.jsx';

export default function App() {
  const { loading } = useAuth();
  if (loading) return <main className="center-screen">Loading...</main>;

  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />

      <Route path="/patient" element={<ProtectedRoute role="PATIENT" />}>
        <Route index element={<Navigate to="dashboard" replace />} />
        <Route path="dashboard" element={<PatientDashboard />} />
        <Route path="doctors" element={<PatientDoctors />} />
        <Route path="appointments" element={<PatientAppointments />} />
      </Route>

      <Route path="/doctor" element={<ProtectedRoute role="DOCTOR" />}>
        <Route index element={<Navigate to="dashboard" replace />} />
        <Route path="dashboard" element={<DoctorDashboard />} />
        <Route path="slots" element={<DoctorSlots />} />
        <Route path="appointments" element={<DoctorAppointments />} />
      </Route>

      <Route path="/admin" element={<ProtectedRoute role="ADMIN" />}>
        <Route index element={<Navigate to="dashboard" replace />} />
        <Route path="dashboard" element={<AdminDashboard />} />
        <Route path="users" element={<AdminUsers />} />
        <Route path="appointments" element={<AdminAppointments />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
