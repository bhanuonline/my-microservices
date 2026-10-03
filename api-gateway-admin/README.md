# API Gateway Admin UI

Standalone React + Vite + TypeScript app for administering the api-gateway.
CRUD for dynamic routes, view audit log, manage API keys.

## Quick start

Prereqs — the gateway stack running on their usual ports:

```bash
mvn -pl auth-server  spring-boot:run     # :9010
mvn -pl api-gateway  spring-boot:run     # :8080
```

Then:

```bash
cd api-gateway-admin
npm install
npm run dev
```

Open http://localhost:5173. Log in with `admin` / `admin123` (the same
credentials that grant `SCOPE_admin` via the `demo-admin-subs` escape hatch
in the gateway).

## What it does

- **Routes tab** — list / create / edit / delete dynamic routes; force refresh
- **Audit tab** — last 100 admin mutations across all routes
- **API Keys tab** — create keys (raw key shown ONCE), list, revoke

## Dev proxy

`vite.config.ts` proxies `/admin/*` → gateway `:8080` and `/oauth2/*` →
auth-server `:9010`. Browser sees everything as same-origin, so no CORS setup
is needed during development.

## Production notes

Two options:

1. **Same-origin build** — `npm run build` → copy `dist/` to
   `api-gateway/src/main/resources/static/admin/`. Gateway serves the SPA
   alongside the API. No CORS.
2. **Nginx sidecar** — separate container serving `dist/`, with `/admin/*` +
   `/oauth2/*` reverse-proxied to the gateway + auth-server.

## Security posture

- JWT stored in memory (lost on refresh) — safer than localStorage vs XSS
- Real prod should use an HttpOnly + Secure + SameSite=strict cookie
- Never expose the admin UI to the public internet; put it behind VPN / IdP SSO

## Files

```
src/
├── main.tsx                — entry
├── App.tsx                 — router + auth guard
├── styles.css              — all styling (single file)
├── auth/
│   ├── AuthContext.tsx     — JWT in memory, login/logout hooks
│   └── Login.tsx           — login form
├── api/
│   └── client.ts           — axios + Bearer + 401 handler
├── pages/
│   ├── RoutesPage.tsx      — CRUD + refresh
│   ├── AuditPage.tsx       — audit log
│   └── ApiKeysPage.tsx     — create/revoke keys
└── components/
    ├── Layout.tsx          — nav + logout
    └── Modal.tsx           — reusable dialog
```
