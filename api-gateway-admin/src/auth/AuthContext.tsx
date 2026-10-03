import { createContext, useCallback, useContext, useMemo, useState, ReactNode } from 'react';
import axios from 'axios';

interface AuthState {
  token: string | null;
  clientId: string | null;
}

interface AuthContextValue extends AuthState {
  login: (clientId: string, clientSecret: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

// In-memory JWT storage — safer than localStorage (XSS defense). Downside:
// lost on refresh. Production should use HttpOnly + Secure + SameSite cookies.
export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ token: null, clientId: null });

  const login = useCallback(async (clientId: string, clientSecret: string) => {
    // Client credentials grant — same as `curl -u admin:admin123 ...`
    const body = new URLSearchParams();
    body.append('grant_type', 'client_credentials');
    body.append('scope', 'read');

    const res = await axios.post('/oauth2/token', body, {
      auth: { username: clientId, password: clientSecret },
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    });
    const token = res.data?.access_token;
    if (!token) throw new Error('No access_token in response');
    setState({ token, clientId });
  }, []);

  const logout = useCallback(() => {
    setState({ token: null, clientId: null });
  }, []);

  const value = useMemo(
    () => ({ token: state.token, clientId: state.clientId, login, logout }),
    [state, login, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
