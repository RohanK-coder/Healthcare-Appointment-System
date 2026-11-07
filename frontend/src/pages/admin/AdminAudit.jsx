import { useEffect, useState } from 'react';
import { api, getErrorMessage } from '../../api/client.js';
import PortalLayout from '../../components/PortalLayout.jsx';

const ACTIONS = ['', 'LOGIN_SUCCESS', 'LOGIN_FAILED', 'LOGIN_LOCKED', 'USER_REGISTERED', 'PASSWORD_CHANGED',
  'DOCTOR_CREATED', 'ADMIN_CREATED', 'SLOTS_CREATED', 'APPOINTMENT_BOOKED', 'APPOINTMENT_CANCELED',
  'APPOINTMENT_STATUS_CHANGED', 'SUMMARY_SAVED', 'NOTIFICATION_DEAD', 'NOTIFICATION_RETRIED'];

export default function AdminAudit() {
  const [action, setAction] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    setError('');
    api.get('/admin/audit-logs', { params: { action: action || undefined, page, size: 50 } })
      .then((res) => setData(res.data))
      .catch((err) => setError(getErrorMessage(err)));
  }, [action, page]);

  return (
    <PortalLayout title="Audit Log">
      {error && <div className="alert">{error}</div>}
      <div className="toolbar">
        <label>Action
          <select value={action} onChange={(e) => { setAction(e.target.value); setPage(0); }}>
            {ACTIONS.map((a) => <option key={a} value={a}>{a || 'All actions'}</option>)}
          </select>
        </label>
        <Pager data={data} page={page} setPage={setPage} />
      </div>
      <div className="table-wrap">
        <table>
          <thead><tr><th>Time</th><th>Actor</th><th>Action</th><th>Target</th><th>Outcome</th><th>IP</th><th>Details</th></tr></thead>
          <tbody>
            {data?.items.map((e) => (
              <tr key={e.id}>
                <td>{new Date(e.occurredAt).toLocaleString()}</td>
                <td>{e.actorEmail || 'system'}{e.actorRole && <span className="muted small"> ({e.actorRole})</span>}</td>
                <td>{e.action}</td>
                <td>{e.entityType ? `${e.entityType} #${e.entityId}` : '-'}</td>
                <td><span className={`pill ${e.outcome === 'SUCCESS' ? 'completed' : 'canceled'}`}>{e.outcome}</span></td>
                <td>{e.ipAddress || '-'}</td>
                <td>{e.details || '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {data?.items.length === 0 && <div className="empty">No entries.</div>}
      </div>
    </PortalLayout>
  );
}

export function Pager({ data, page, setPage }) {
  if (!data) return null;
  return (
    <div className="pager">
      <button className="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
      <span className="muted">Page {data.totalPages === 0 ? 0 : page + 1} of {data.totalPages} ({data.totalItems})</span>
      <button className="secondary" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Next</button>
    </div>
  );
}
