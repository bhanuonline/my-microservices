import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { createApiClient, RouteAuditEntry } from '../api/client';

type OutcomeFilter = 'ALL' | 'SUCCESS' | 'FAILURES';

export function AuditPage() {
  const { token, logout } = useAuth();
  const api = useMemo(() => createApiClient(token, logout), [token, logout]);

  const [entries, setEntries] = useState<RouteAuditEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [outcomeFilter, setOutcomeFilter] = useState<OutcomeFilter>('ALL');
  const [routeFilter, setRouteFilter] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const url = routeFilter
        ? `/admin/routes/${encodeURIComponent(routeFilter)}/audit`
        : '/admin/routes/audit';
      const res = await api.get<RouteAuditEntry[]>(url);
      setEntries(res.data);
    } catch (err: unknown) {
      setError(msg(err));
    } finally {
      setLoading(false);
    }
  }, [api, routeFilter]);

  useEffect(() => { load(); }, [load]);

  const filtered = entries.filter((e) => {
    if (outcomeFilter === 'ALL') return true;
    if (outcomeFilter === 'SUCCESS') return e.outcome === 'SUCCESS';
    return e.outcome !== 'SUCCESS';
  });

  return (
    <div className="page">
      <div className="page-header">
        <h1>Audit log</h1>
        <div className="actions">
          <input
            type="text"
            value={routeFilter}
            onChange={(e) => setRouteFilter(e.target.value)}
            placeholder="Filter by route id (blank = all)"
            className="filter-input"
          />
          <select
            value={outcomeFilter}
            onChange={(e) => setOutcomeFilter(e.target.value as OutcomeFilter)}
          >
            <option value="ALL">All outcomes</option>
            <option value="SUCCESS">Success only</option>
            <option value="FAILURES">Failures only</option>
          </select>
          <button onClick={load} className="secondary">Reload</button>
        </div>
      </div>

      {loading && <div className="hint">Loading…</div>}
      {error && <div className="error">{error}</div>}
      {!loading && filtered.length === 0 && <div className="empty">No audit entries.</div>}

      {filtered.length > 0 && (
        <table className="grid">
          <thead>
            <tr>
              <th>Time</th>
              <th>Route</th>
              <th>Action</th>
              <th>Actor</th>
              <th>Outcome</th>
              <th>Reason</th>
              <th>Correlation</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((e) => (
              <tr key={e.id} className={e.outcome === 'SUCCESS' ? '' : 'row-failure'}>
                <td className="mono">{formatTs(e.createdAt)}</td>
                <td><code>{e.routeId}</code></td>
                <td><span className={`badge action-${e.action.toLowerCase()}`}>{e.action}</span></td>
                <td>{e.actor ?? '-'} <span className="muted small">({e.actorType ?? '-'})</span></td>
                <td>
                  <span className={`badge outcome-${e.outcome.toLowerCase()}`}>
                    {e.outcome}
                  </span>
                </td>
                <td className="reason">{e.reason ?? ''}</td>
                <td className="mono small">{e.correlationId ?? ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

function formatTs(iso: string): string {
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

function msg(err: unknown): string {
  if (typeof err === 'object' && err !== null && 'response' in err) {
    const r = (err as { response?: { data?: { message?: string } } }).response;
    if (r?.data?.message) return r.data.message;
  }
  return err instanceof Error ? err.message : 'Request failed';
}
