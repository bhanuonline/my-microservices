import axios, { AxiosInstance } from 'axios';

/**
 * Factory — one axios instance per authenticated session, bound to the token.
 * On 401 the caller's onUnauthorized callback fires (used by the auth layer
 * to force logout + re-login prompt).
 */
export function createApiClient(token: string | null, onUnauthorized: () => void): AxiosInstance {
  const client = axios.create({ baseURL: '' });

  client.interceptors.request.use((config) => {
    if (token) {
      config.headers = config.headers ?? {};
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  });

  client.interceptors.response.use(
    (res) => res,
    (err) => {
      if (err?.response?.status === 401) {
        onUnauthorized();
      }
      return Promise.reject(err);
    }
  );

  return client;
}

// ─── Typed API models ─────────────────────────────────────────────────

export interface RouteDefinition {
  id: string;
  uri: string;
  order?: number;
  predicates: Array<{ name: string; args: Record<string, string> }>;
  filters?: Array<{ name: string; args?: Record<string, string> }>;
  metadata?: Record<string, unknown>;
}

export interface RouteAuditEntry {
  id: number;
  routeId: string;
  action: 'CREATE' | 'UPDATE' | 'DELETE' | 'REFRESH';
  actor: string | null;
  actorType: 'JWT' | 'API_KEY' | 'ANONYMOUS' | 'OTHER' | null;
  payload: string | null;
  outcome: 'SUCCESS' | 'VALIDATION_FAILED' | 'STORE_ERROR';
  reason: string | null;
  correlationId: string | null;
  createdAt: string;
}

export interface ApiKeyCreateResponse {
  keyId: string;
  rawKey: string;
  warning: string;
}
