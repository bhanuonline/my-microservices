import { FormEvent, useState } from 'react';
import { useAuth } from './AuthContext';

export function Login() {
  const { login } = useAuth();
  const [clientId, setClientId] = useState('admin');
  const [clientSecret, setClientSecret] = useState('admin123');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await login(clientId, clientSecret);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Login failed';
      setError(msg);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="login-container">
      <form className="login-form" onSubmit={handleSubmit}>
        <h1>API Gateway Admin</h1>
        <p className="hint">Log in with an OAuth2 client_credentials client.</p>

        <label>
          Client ID
          <input
            type="text"
            value={clientId}
            onChange={(e) => setClientId(e.target.value)}
            required
            autoFocus
          />
        </label>

        <label>
          Client Secret
          <input
            type="password"
            value={clientSecret}
            onChange={(e) => setClientSecret(e.target.value)}
            required
          />
        </label>

        {error && <div className="error">{error}</div>}

        <button type="submit" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>

        <p className="hint small">
          Default demo credentials: <code>admin</code> / <code>admin123</code>
        </p>
      </form>
    </div>
  );
}
