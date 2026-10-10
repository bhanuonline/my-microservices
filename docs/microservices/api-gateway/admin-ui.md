# API Gateway — Admin UI (React CRUD)

Standalone React + Vite + TypeScript app at `api-gateway-admin/`. CRUD for dynamic
routes + audit log viewer + API key management. Zero backend changes — talks to
the existing `/admin/*` endpoints on the gateway and `/oauth2/token` on the
auth-server via a Vite **dev proxy** (no CORS work needed).

---

## 1. Architecture

```
                       ┌──────────────────────────────┐
   Browser ────────▶   │  Vite dev server :5173       │
                       │                              │
                       │  React app (bundled)         │
                       │                              │
                       │  Dev proxy (vite.config.ts): │
                       │   /admin/*  → :8080 gateway  │
                       │   /oauth2/* → :9010 auth-srv │
                       └──────────────────────────────┘
                                    │
                                    │  Proxied requests appear same-origin
                                    │  from the browser — NO CORS needed
                                    │
                       ┌────────────┴────────────────┐
                       ▼                             ▼
              ┌──────────────────┐        ┌──────────────────┐
              │  API Gateway     │        │  Auth Server     │
              │  :8080           │        │  :9010           │
              │                  │        │                  │
              │  /admin/routes   │        │  /oauth2/token   │
              │  /admin/routes/  │        │  (client_creds:  │
              │    audit         │        │   admin:admin123)│
              │  /admin/apikeys  │        │                  │
              └──────────────────┘        └──────────────────┘
```

The Vite proxy is the key trick — browser sees everything as same-origin
`http://localhost:5173`. No CORS setup, no preflight OPTIONS, no cross-origin
cookie machinery. In real prod: serve the built `dist/` from the gateway
same-origin (or an nginx sidecar).

---

## 2. Screens

```
1. LOGIN
   Client ID / Client Secret → POST /oauth2/token (client_credentials)
   Store JWT in memory (not localStorage — XSS defense).
   Default: admin / admin123 (matches demo-admin-subs).

2. ROUTES
   Table columns: id | uri | order | predicates | filters | actions
   Buttons:       [+ New route] [Force refresh] [Reload]
   Row actions:   [Edit] [Delete]
   Create/Edit modal: id, uri, order, predicates JSON, filters JSON.
   Save → POST/PUT /admin/routes.
   Server validation errors surfaced as the modal error banner.

3. AUDIT
   Table: time | route | action | actor | outcome | reason | correlation
   Filters: route id text-input, outcome select (all / success / failures).
   Backing endpoint: /admin/routes/audit or /admin/routes/{id}/audit.
   Failed rows highlighted (row-failure class).

4. API KEYS
   Create key modal: ownerId, name, scopes (CSV), rate-limit tier.
   Response modal: shows keyId + raw key with Copy button. Warning banner.
   Revoke section: type keyId → DELETE /admin/apikeys/{id}.
```

Nav: three tabs in a top bar + user menu (logout).

---

## 3. Design decisions

### 3a. Vite dev proxy (chosen for dev)

- Same-origin from the browser → no CORS to configure
- Zero backend changes
- Downside: prod needs a different serving strategy

Prod options:
1. **Same-origin bundle** — `npm run build` → copy `dist/` to
   `api-gateway/src/main/resources/static/admin/`. Gateway serves the SPA
   alongside the API. Zero CORS.
2. **Nginx sidecar** — separate container serving `dist/`, reverse-proxies
   `/admin/*` + `/oauth2/*` to the backend services.

### 3b. JWT in memory (not localStorage)

- **localStorage** — XSS-vulnerable; any injected script reads it. Rejected.
- **sessionStorage** — same problem, shorter lifetime. Rejected.
- **In-memory** (chosen) — safe against XSS; lost on refresh (user re-logs).
- **HttpOnly cookie** — best for real prod; requires backend cookie support.

Interview soundbite: *"never put a JWT in localStorage — XSS defeats you. Real
prod uses HttpOnly + Secure + SameSite=strict cookies."*

### 3c. State: plain React hooks + one AuthContext

No Redux, no Zustand, no React Query. App is small enough that local state +
one context is clearer than adding a state library. Interview-safe: *"picked
the smallest thing that works."*

### 3d. Forms: JSON textareas for complex fields

RouteDefinition's `predicates` / `filters` have heterogeneous shape (Path uses
`_genkey_0`, RewritePath uses `regexp`/`replacement`). A schema-driven form
builder would be its own project. JSON textarea + server-side validation is the
smallest-viable approach.

### 3e. Styling: one CSS file, no framework

No Tailwind, no MUI, no CSS modules. Single `styles.css` with CSS variables.
Focus is the pattern, not a design system.

---

## 4. Files

```
api-gateway-admin/
├── package.json                 (React 18 + Vite + TS + axios + react-router)
├── vite.config.ts               (dev proxy → gateway + auth-server)
├── tsconfig.json
├── index.html
├── README.md                    (quick-start + prod notes)
└── src/
    ├── main.tsx                 (React root)
    ├── App.tsx                  (router + auth guard)
    ├── styles.css               (all styling — CSS vars, no framework)
    ├── auth/
    │   ├── AuthContext.tsx      (JWT in memory + login/logout hooks)
    │   └── Login.tsx            (client_credentials login form)
    ├── api/
    │   └── client.ts            (axios factory + Bearer injector + 401 handler + typed models)
    ├── pages/
    │   ├── RoutesPage.tsx       (list + create/edit modal + delete + refresh)
    │   ├── AuditPage.tsx        (audit log with route + outcome filters)
    │   └── ApiKeysPage.tsx      (create + revoke; shows raw key ONCE)
    └── components/
        ├── Layout.tsx           (top bar with nav + logout)
        └── Modal.tsx            (reusable dialog with header + body + footer)
```

Roughly 10 TS files, ~800 lines. No backend changes.

---

## 5. Quick start

```bash
# 1. Start the stack (usual)
mvn -pl infra/auth-server  spring-boot:run     # :9010
mvn -pl infra/api-gateway  spring-boot:run     # :8080

# 2. Boot the UI
cd api-gateway-admin
npm install
npm run dev
# → Vite dev server on http://localhost:5173

# 3. Open browser → http://localhost:5173
#    - Login: admin / admin123
#    - See routes list
#    - Create a route → confirm it appears
#    - Delete it → confirm gone
#    - Audit tab → see the CREATE + DELETE events
#    - API Keys tab → create a key, copy the raw key, revoke it
```

---

## 6. Failure modes & mitigations

| Scenario | Behavior | Mitigation |
|---|---|---|
| Gateway not running | Proxy 502 → UI shows API error | Ensure gateway is running before starting UI |
| Auth-server not running | Login shows connection error | Ensure auth-server is running before login |
| JWT expires mid-session | Any API call gets 401 → interceptor calls `onUnauthorized` → context clears token → login screen | Handled by axios interceptor |
| Refresh browser (F5) | JWT lost (in-memory) → back to login | By design; use HttpOnly cookie for persistence |
| CORS error in dev | Should never happen — proxy handles it | If you edit vite.config.ts and break the proxy, restore it |
| Server returns HTML instead of JSON on error | axios parses as text and rejects | Ensure gateway is running the proper build with error handlers |
| Bad JSON in route form | UI validates on submit ("Invalid JSON...") | Present |
| Server-side route validation fails | 400 with message → surfaced in modal error banner | Present |

---

## 7. Interview cheat-sheet

| Question | Answer |
|---|---|
| Why Vite dev proxy? | Bypasses CORS during dev — requests appear same-origin. Prod serves the built bundle from the gateway or an nginx sidecar. |
| Why not localStorage for JWT? | XSS — any injected script reads it. In-memory (lost on refresh) OR HttpOnly cookie for real prod. |
| State management? | Plain React hooks + one AuthContext. Redux/Zustand overkill for CRUD screens. |
| Form validation strategy? | JSON textarea for complex nested config; validation on submit. Server rejects with 400 + message which the UI displays. |
| Client-credentials vs authorization_code grant? | client_creds is machine-to-machine — admin UI operator uses their client credentials. Real prod would use auth_code + PKCE with a real login screen. |
| CORS in real prod? | Enable `CorsWebFilter` on the gateway; allowlist the UI origin; ensure auth-server allows the same. OR serve UI same-origin. |
| Why show raw API key only once? | Stripe / GitHub PAT pattern — server never stores plaintext (only SHA-256 hash). Force safe storage. |
| Refresh route table after mutation? | Explicit reload after POST/PUT/DELETE. Or use React Query for automatic invalidation. |
| Optimistic updates? | Not built — pessimistic (wait for server). Simpler for demo; optimistic UI is a follow-up. |
| Multi-replica route consistency? | Redis pub/sub already broadcasts refresh; UI sees latest state on next reload. Auto-poll every 30s is a small extension. |
| Why single styles.css? | Small app, easy to reason about; no framework tax. Real prod would use CSS modules / Tailwind. |
| Why don't we serve the UI from the gateway right now? | Would need `spring-boot-starter-web` (servlet, not reactive) for static resources OR a WebFlux static resource handler — small extension, deliberately parked. |

---

## 8. Common pitfalls (interview probes)

1. **JWT in localStorage** — very common anti-pattern. XSS steals it. Reject.
2. **CORS wildcards (`*`) with credentials** — browser refuses the response. Must specify exact origin AND `Access-Control-Allow-Credentials: true`.
3. **Dev proxy vs prod deployment mismatch** — code works in dev, breaks in prod because the API calls now go cross-origin. Fix: same-origin build OR real CORS.
4. **Response interceptor loop** — if the interceptor triggers another API call on 401, and that call also gets 401, infinite loop. Ours calls the passed-in `onUnauthorized` synchronously; no loop.
5. **Ambient credentials + CSRF** — if we ever switch to cookie auth, need CSRF protection. Bearer tokens dodge this because they're not sent automatically.
6. **Copy-to-clipboard requires HTTPS or localhost** — `navigator.clipboard.writeText` fails silently on plain HTTP over non-localhost hosts.

---

## 9. Extensions (parked)

- **Production build served from gateway** — `npm run build` → copy `dist/` to `api-gateway/src/main/resources/static/admin/`. Add a `/admin/**` permit for static content in the security config.
- **Real CORS config** — `CorsWebFilter` bean on the gateway; same on auth-server.
- **HttpOnly cookie auth** — swap JWT-in-memory for cookie session. Needs backend cookie support.
- **React Query / TanStack Query** — cache + auto-invalidate on mutations. Cleaner than manual reloads.
- **JSON schema form builder** — auto-generate route form from a schema definition.
- **Diff view** — show what changed on edit before submit.
- **Bulk operations** — checkbox rows + batch delete.
- **Route templates** — pre-filled forms for common patterns (basic proxy, rate-limited proxy, CB-protected proxy).
- **Audit filtering** — date range picker, actor filter, correlation ID search.
- **Dark mode** — swap CSS variables via `prefers-color-scheme: dark`.
- **WebSocket / SSE live audit** — subscribe to audit changes instead of polling.
- **Metrics dashboard tab** — surface `/actuator/prometheus` numbers in Recharts (like the sibling `angle-app`).
- **Role-based UI** — hide destructive actions unless user has `SCOPE_admin`.
- **Route diff viewer** — visualize predicate/filter changes across audit entries.
