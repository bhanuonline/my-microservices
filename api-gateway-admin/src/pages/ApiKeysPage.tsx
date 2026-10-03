import { useMemo, useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { ApiKeyCreateResponse, createApiClient } from '../api/client';
import { Modal } from '../components/Modal';

export function ApiKeysPage() {
  const { token, logout } = useAuth();
  const api = useMemo(() => createApiClient(token, logout), [token, logout]);

  const [showCreate, setShowCreate] = useState(false);
  const [ownerId, setOwnerId] = useState('partner-corp');
  const [name, setName] = useState('Prod API key');
  const [scopes, setScopes] = useState('read,write');
  const [rateLimitTier, setRateLimitTier] = useState('standard');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<ApiKeyCreateResponse | null>(null);

  const [revokeId, setRevokeId] = useState('');
  const [revokeBusy, setRevokeBusy] = useState(false);
  const [revokeMsg, setRevokeMsg] = useState<string | null>(null);

  const handleCreate = async () => {
    setError(null);
    setBusy(true);
    try {
      const res = await api.post<ApiKeyCreateResponse>('/admin/apikeys', {
        ownerId,
        name,
        scopes: scopes.split(',').map((s) => s.trim()).filter(Boolean),
        rateLimitTier,
      });
      setCreated(res.data);
      setShowCreate(false);
    } catch (err: unknown) {
      setError(msg(err));
    } finally {
      setBusy(false);
    }
  };

  const handleRevoke = async () => {
    setRevokeMsg(null);
    if (!revokeId.trim()) return;
    setRevokeBusy(true);
    try {
      const res = await api.delete(`/admin/apikeys/${encodeURIComponent(revokeId.trim())}`);
      setRevokeMsg(`Revoked (HTTP ${res.status})`);
      setRevokeId('');
    } catch (err: unknown) {
      setRevokeMsg(msg(err));
    } finally {
      setRevokeBusy(false);
    }
  };

  const copyKey = async () => {
    if (!created?.rawKey) return;
    await navigator.clipboard.writeText(created.rawKey);
  };

  return (
    <div className="page">
      <div className="page-header">
        <h1>API Keys</h1>
        <div className="actions">
          <button onClick={() => setShowCreate(true)}>+ Create key</button>
        </div>
      </div>

      <p className="hint">
        API keys authenticate machine-to-machine callers with header{' '}
        <code>X-Api-Key: sk_live_...</code>. Raw keys are shown ONCE at creation
        and stored as SHA-256 hashes in Redis — they cannot be retrieved later.
      </p>

      <div className="card">
        <h3>Revoke a key</h3>
        <p className="hint small">
          Enter the key ID (like <code>key_9f2a...</code>) returned at creation time.
        </p>
        <div className="row">
          <input
            type="text"
            value={revokeId}
            onChange={(e) => setRevokeId(e.target.value)}
            placeholder="key_..."
          />
          <button onClick={handleRevoke} disabled={revokeBusy || !revokeId.trim()} className="danger">
            {revokeBusy ? 'Revoking…' : 'Revoke'}
          </button>
        </div>
        {revokeMsg && <div className="hint small">{revokeMsg}</div>}
      </div>

      {showCreate && (
        <Modal
          title="Create API key"
          onClose={() => setShowCreate(false)}
          actions={
            <>
              <button onClick={() => setShowCreate(false)} className="secondary" disabled={busy}>Cancel</button>
              <button onClick={handleCreate} disabled={busy || !ownerId}>{busy ? 'Creating…' : 'Create'}</button>
            </>
          }
        >
          <label>
            Owner ID
            <input value={ownerId} onChange={(e) => setOwnerId(e.target.value)} />
          </label>
          <label>
            Name
            <input value={name} onChange={(e) => setName(e.target.value)} />
          </label>
          <label>
            Scopes (comma-separated)
            <input value={scopes} onChange={(e) => setScopes(e.target.value)} />
          </label>
          <label>
            Rate-limit tier
            <input value={rateLimitTier} onChange={(e) => setRateLimitTier(e.target.value)} />
          </label>
          {error && <div className="error">{error}</div>}
        </Modal>
      )}

      {created && (
        <Modal
          title="API key created — copy it NOW"
          onClose={() => setCreated(null)}
          wide
          actions={
            <button onClick={() => setCreated(null)}>I have copied it</button>
          }
        >
          <div className="warn">
            {created.warning ?? 'This is the ONLY time the raw key will be shown.'}
          </div>
          <label>
            Key ID (safe to store)
            <input value={created.keyId} readOnly />
          </label>
          <label>
            Raw key (SENSITIVE)
            <div className="row">
              <input value={created.rawKey} readOnly className="mono" />
              <button onClick={copyKey} className="secondary">Copy</button>
            </div>
          </label>
        </Modal>
      )}
    </div>
  );
}

function msg(err: unknown): string {
  if (typeof err === 'object' && err !== null && 'response' in err) {
    const r = (err as { response?: { data?: { message?: string } } }).response;
    if (r?.data?.message) return r.data.message;
  }
  return err instanceof Error ? err.message : 'Request failed';
}
