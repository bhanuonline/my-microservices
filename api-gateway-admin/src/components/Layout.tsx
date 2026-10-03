import { ReactNode } from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export function Layout({ children }: { children: ReactNode }) {
  const { clientId, logout } = useAuth();

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">API Gateway Admin</div>
        <nav className="tabs">
          <NavLink to="/routes"  className={({isActive}) => isActive ? 'tab active' : 'tab'}>Routes</NavLink>
          <NavLink to="/audit"   className={({isActive}) => isActive ? 'tab active' : 'tab'}>Audit</NavLink>
          <NavLink to="/apikeys" className={({isActive}) => isActive ? 'tab active' : 'tab'}>API Keys</NavLink>
        </nav>
        <div className="user">
          <span className="who">Signed in as <strong>{clientId ?? '?'}</strong></span>
          <button onClick={logout} className="linkish">Log out</button>
        </div>
      </header>
      <main className="content">{children}</main>
    </div>
  );
}
