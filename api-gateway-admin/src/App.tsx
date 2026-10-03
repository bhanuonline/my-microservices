import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth/AuthContext';
import { Login } from './auth/Login';
import { Layout } from './components/Layout';
import { RoutesPage } from './pages/RoutesPage';
import { AuditPage } from './pages/AuditPage';
import { ApiKeysPage } from './pages/ApiKeysPage';

export function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <RootRoutes />
      </BrowserRouter>
    </AuthProvider>
  );
}

function RootRoutes() {
  const { token } = useAuth();

  if (!token) {
    return (
      <Routes>
        <Route path="*" element={<Login />} />
      </Routes>
    );
  }

  return (
    <Layout>
      <Routes>
        <Route path="/routes"  element={<RoutesPage />} />
        <Route path="/audit"   element={<AuditPage />} />
        <Route path="/apikeys" element={<ApiKeysPage />} />
        <Route path="*"        element={<Navigate to="/routes" replace />} />
      </Routes>
    </Layout>
  );
}
