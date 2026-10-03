import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { createApiClient, RouteDefinition } from '../api/client';
import { Modal } from '../components/Modal';

const EMPTY_ROUTE: RouteDefinition = {
  id: '',
  uri: 'lb://',
  order: 0,
  predicates: [{ name: 'Path', args: { _genkey_0: '/api/v1/example/**' } }],
  filters: [],
};

export function RoutesPage() {
  const { token, logout } = useAuth();
  const api = useMemo(() => createApiClient(token, logout), [token, logout]);

  const [routes, setRoutes] = useState<RouteDefinition[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<RouteDefinition | null>(null);
  const [creating, setCreating] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await api.get<RouteDefinition[]>('/admin/routes');
      setRoutes(res.data);
    } catch (err: unknown) {
      setError(msg(err));
    } finally {
      setLoading(false);
    }
  }, [api]);

  useEffect(() => { load(); }, [load]);

  const handleDelete = async (id: string) => {
    if (!confirm(`Delete route "${id}"?`)) return;
    try {
      await api.delete(`/admin/routes/${encodeURIComponent(id)}`);
      await load();
    } catch (err: unknown) {
      alert(msg(err));
    }
  };

  const handleRefresh = async () => {
    try {
      await api.post('/admin/routes/refresh');
      await load();
    } catch (err: unknown) {
      alert(msg(err));
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <h1>Routes</h1>
        <div className="actions">
          <button onClick={() => setCreating(true)}>+ New route</button>
          <button onClick={handleRefresh} className="secondary">Force refresh</button>
          <button onClick={load} className="secondary">Reload</button>
        </div>
      </div>

      {loading && <div className="hint">Loading…</div>}
      {error && <div className="error">{error}</div>}

      {!loading && routes.length === 0 && (
        <div className="empty">No dynamic routes yet. Create one to get started.</div>
      )}

      {routes.length > 0 && (
        <table className="grid">
          <thead>
            <tr>
              <th>ID</th>
              <th>URI</th>
              <th>Order</th>
              <th>Predicates</th>
              <th>Filters</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {routes.map((r) => (
              <tr key={r.id}>
                <td><code>{r.id}</code></td>
                <td><code>{r.uri}</code></td>
                <td>{r.order ?? 0}</td>
                <td>{(r.predicates ?? []).map(p => p.name).join(', ')}</td>
                <td>{(r.filters ?? []).map(f => f.name).join(', ') || <span className="muted">—</span>}</td>
                <td className="row-actions">
                  <button onClick={() => setEditing(r)} className="linkish">Edit</button>
                  <button onClick={() => handleDelete(r.id)} className="linkish danger">Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {(creating || editing) && (
        <RouteEditor
          initial={editing ?? EMPTY_ROUTE}
          isNew={creating}
          onClose={() => { setEditing(null); setCreating(false); }}
          onSaved={async () => {
            setEditing(null);
            setCreating(false);
            await load();
          }}
          api={api}
        />
      )}
    </div>
  );
}

interface EditorProps {
  initial: RouteDefinition;
  isNew: boolean;
  onClose: () => void;
  onSaved: () => Promise<void> | void;
  api: ReturnType<typeof createApiClient>;
}

function RouteEditor({ initial, isNew, onClose, onSaved, api }: EditorProps) {
  const [id, setId] = useState(initial.id);
  const [uri, setUri] = useState(initial.uri);
  const [order, setOrder] = useState(String(initial.order ?? 0));
  const [predicatesJson, setPredicatesJson] = useState(
    JSON.stringify(initial.predicates ?? [], null, 2)
  );
  const [filtersJson, setFiltersJson] = useState(
    JSON.stringify(initial.filters ?? [], null, 2)
  );
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const save = async () => {
    setError(null);
    let predicates, filters;
    try {
      predicates = JSON.parse(predicatesJson);
      filters = filtersJson.trim() ? JSON.parse(filtersJson) : [];
    } catch (err: unknown) {
      setError('Invalid JSON in predicates or filters');
      return;
    }
    const body: RouteDefinition = {
      id, uri,
      order: Number(order) || 0,
      predicates,
      filters,
    };
    setBusy(true);
    try {
      if (isNew) {
        await api.post('/admin/routes', body);
      } else {
        await api.put(`/admin/routes/${encodeURIComponent(id)}`, body);
      }
      await onSaved();
    } catch (err: unknown) {
      setError(msg(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      wide
      title={isNew ? 'Create route' : `Edit route: ${initial.id}`}
      onClose={onClose}
      actions={
        <>
          <button onClick={onClose} className="secondary" disabled={busy}>Cancel</button>
          <button onClick={save} disabled={busy || !id || !uri}>{busy ? 'Saving…' : 'Save'}</button>
        </>
      }
    >
      <label>
        Route ID
        <input
          type="text"
          value={id}
          onChange={(e) => setId(e.target.value)}
          disabled={!isNew}
          placeholder="products-preview"
        />
      </label>

      <label>
        URI
        <input
          type="text"
          value={uri}
          onChange={(e) => setUri(e.target.value)}
          placeholder="lb://product-service"
        />
      </label>

      <label>
        Order
        <input
          type="number"
          value={order}
          onChange={(e) => setOrder(e.target.value)}
        />
      </label>

      <label>
        Predicates (JSON array)
        <textarea
          value={predicatesJson}
          onChange={(e) => setPredicatesJson(e.target.value)}
          rows={6}
          spellCheck={false}
        />
      </label>

      <label>
        Filters (JSON array)
        <textarea
          value={filtersJson}
          onChange={(e) => setFiltersJson(e.target.value)}
          rows={6}
          spellCheck={false}
        />
      </label>

      {error && <div className="error">{error}</div>}

      <p className="hint small">
        Server validates on save: bad predicate/filter names return 400.
      </p>
    </Modal>
  );
}

function msg(err: unknown): string {
  if (typeof err === 'object' && err !== null && 'response' in err) {
    const r = (err as { response?: { data?: { message?: string } } }).response;
    if (r?.data?.message) return r.data.message;
  }
  return err instanceof Error ? err.message : 'Request failed';
}
