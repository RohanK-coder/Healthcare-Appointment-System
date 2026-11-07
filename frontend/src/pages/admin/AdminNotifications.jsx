import { useCallback, useEffect, useState } from 'react';
import { api, getErrorMessage } from '../../api/client.js';
import PortalLayout from '../../components/PortalLayout.jsx';
import { Pager } from './AdminAudit.jsx';

const STATUSES = ['', 'PENDING', 'SENDING', 'SENT', 'FAILED', 'DEAD', 'SKIPPED'];

export default function AdminNotifications() {
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    setError('');
    api.get('/admin/notifications', { params: { status: status || undefined, page, size: 50 } })
      .then((res) => setData(res.data))
      .catch((err) => setError(getErrorMessage(err)));
  }, [status, page]);

  useEffect(() => { load(); }, [load]);

  async function retry(id) {
    try {
      await api.post(`/admin/notifications/${id}/retry`);
      load();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  return (
    <PortalLayout title="Reminder Queue">
      {error && <div className="alert">{error}</div>}
      <div className="toolbar">
        <label>Status
          <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
            {STATUSES.map((s) => <option key={s} value={s}>{s || 'All statuses'}</option>)}
          </select>
        </label>
        <Pager data={data} page={page} setPage={setPage} />
      </div>
      <div className="table-wrap">
        <table>
          <thead><tr><th>Created</th><th>Recipient</th><th>Message</th><th>Status</th><th>Attempts</th><th>Next / sent</th><th>Last error</th><th></th></tr></thead>
          <tbody>
            {data?.items.map((n) => (
              <tr key={n.id}>
                <td>{new Date(n.createdAt).toLocaleString()}</td>
                <td>{n.recipientEmail}</td>
                <td>{n.message}</td>
                <td><span className={`pill ${n.status.toLowerCase()}`}>{n.status}</span></td>
                <td>{n.attempts}</td>
                <td>{new Date(n.sentAt || n.nextAttemptAt).toLocaleString()}</td>
                <td>{n.lastError || '-'}</td>
                <td>{['DEAD', 'FAILED'].includes(n.status) && <button onClick={() => retry(n.id)}>Retry</button>}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {data?.items.length === 0 && <div className="empty">No notifications.</div>}
      </div>
    </PortalLayout>
  );
}
