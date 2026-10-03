# Postman collections — auth-server

Two collections in this folder:

| File | When to use |
|---|---|
| `auth-server-full.postman_collection.json` | **Consolidated — all 13 features.** Import this if you want everything. |
| `auth-server-admin.postman_collection.json` | Original — Phase 6 admin UI only (before features). Kept as reference. |

The full collection is organized by feature folder. Each folder has:
- pre-request scripts that auto-fetch tokens where needed
- test scripts that assert expected behaviour + cache useful IDs
- console.log lines to explain what to look for

## What's covered by folder in the FULL collection

```
0. Setup                      Discovery + JWKS + health
1. OAuth2 core                token / introspect / revoke  (Feature 7 subject_type test)
3. Multi-key signing          list keys + rotate           (Feature 3)
5. Account lockout unlock     REST unlock endpoint         (Feature 5)
6. Rate limiting              probe + shell one-liner      (Feature 6)
9. Zipkin tracing             query traces via Zipkin API  (Feature 9)
10. Prometheus metrics        scrape + filter auth_*       (Feature 10)
11. KMS-backed keys           JWKS kid check + Vault status (Feature 11)
12. DCR                       registrar bootstrap + register + read (Feature 12)
14. REST API mirror           CRUD + negative tests        (Feature 14)
BROWSER-ONLY                  Feature 8 consent + Feature 5 lockout trigger
```

## Import

1. Postman → Import → File → select `auth-server-full.postman_collection.json`.
2. It appears as **"auth-server FULL (features 1-14)"** in your Collections.
3. Optional: import the original admin-only collection if you want the pure Phase 6 UI flow.

## Run order (recommended for first pass)

```
0. Setup → Discovery + JWKS + health          (see the server is up)
1. OAuth2 core → POST /oauth2/token m2m       (populates m2m_token variable +
                                                 asserts Feature 7 claim)
14. REST API → Step 1 admin token             (populates admin_token)
Then any feature folder in any order.
```

Tokens are cached in collection variables (`m2m_token`, `admin_token`, `registrar_token`) by pre-request/test scripts. Requests that need auth read from these variables automatically.

## Legacy — the admin-only collection

## Import

1. Open Postman → Import → File → select `auth-server-admin.postman_collection.json`.
2. It will appear as **"auth-server admin (jdbc profile)"** in your Collections sidebar.

## One-time setup (per Postman workspace)

The admin CRUD requests need cookies + a CSRF token to work. Postman handles this if you:

1. **Turn on the cookie jar for `localhost:8095`** — enabled by default; no action.
2. **Turn OFF automatic redirect following** for the `POST /login` request only.
   - Click that request → **Settings** tab → set `Automatically follow redirects` to **OFF**.
   - Reason: on 302, Postman would follow to `/` → to `/admin` and eat the CSRF token before you can capture the post-login one.
3. **Turn on the "Send cookies automatically"** toggle in the Cookies dialog — default.

That's it.

## What's in the collection

```
┌─────────────────────────────────────────────────────────────────┐
│  1. Discovery                                                   │
│      GET /.well-known/openid-configuration                      │
│      GET /oauth2/jwks                                           │
│                                                                 │
│  2. OAuth2 tokens                                               │
│      POST /oauth2/token         (client_credentials, saves      │
│                                   access_token to variable)     │
│      POST /oauth2/introspect                                    │
│      POST /oauth2/revoke                                        │
│      GET  /oauth2/authorize     (paste in browser, not Postman) │
│                                                                 │
│  3. Admin login (session bootstrap)                             │
│      Step 1  GET  /login        (scrapes CSRF into var)         │
│      Step 2  POST /login        (submits creds; 302 → /)        │
│      Step 3  GET  /admin        (refresh CSRF post-login)       │
│      Step 4  POST /logout                                       │
│                                                                 │
│  4. Admin CRUD — clients                                        │
│      GET  /admin/clients                                        │
│      POST /admin/clients                (create)                │
│      GET  /admin/clients/{id}/edit                              │
│      POST /admin/clients/{id}           (update)                │
│      POST /admin/clients/{id}/delete                            │
│                                                                 │
│  5. Admin CRUD — users                                          │
│      GET  /admin/users                                          │
│      POST /admin/users                  (create)                │
│      POST /admin/users/{id}             (update)                │
│      POST /admin/users/{id}/delete                              │
│                                                                 │
│  6. Actuator                                                    │
│      GET /actuator/health                                       │
└─────────────────────────────────────────────────────────────────┘
```

## Run order

### Path A — just want a JWT for testing a resource-server

Run **only** `2 → POST /oauth2/token` (Basic `m2m-client:m2m-secret`). Done. The access token is saved to `{{access_token}}`.

### Path B — full admin flow

```
1. Boot the auth-server:
     mvn -pl auth-server spring-boot:run -Dspring-boot.run.profiles=jdbc

2. In Postman, run in ORDER:
     3.1  GET /login             ← captures csrf_token
     3.2  POST /login            ← 302 (session cookie set)
     3.3  GET /admin             ← refreshes csrf_token

3. Now the session + CSRF are ready. Run any request under
   groups 4 and 5.

4. When done:
     3.4  POST /logout           (optional)
```

## Collection variables (edit if your setup differs)

| Variable | Default | Meaning |
|---|---|---|
| `base_url` | `http://localhost:8095` | Auth-server root |
| `admin_user` | `admin` | Bootstrap admin username (from V6 seed) |
| `admin_pass` | `password` | Bootstrap admin password |
| `m2m_client_id` | `m2m-client` | Seeded client credentials client |
| `m2m_client_sec` | `m2m-secret` | ...and its secret |
| `demo_client_id` | `demo-client` | Seeded authorization_code client |
| `demo_client_sec` | `secret` | ...and its secret |
| `csrf_token` | (empty) | Auto-set by Step 1 / Step 3 test scripts |
| `access_token` | (empty) | Auto-set by the token-request test script |

## Replacing `REPLACE_WITH_CLIENT_ID` / `REPLACE_WITH_USER_ID`

Update, edit-form, and delete requests need the row id. Two ways to get it:

**Option A — inspect the list HTML**
Run `GET /admin/clients` in Postman, look at the response body, find the Edit button. Its href looks like `/admin/clients/<UUID>/edit` — copy the UUID.

**Option B — go to the DB directly**
```bash
docker exec mysql-auth mysql -uroot -ppass1234 -N \
  -e "SELECT id, client_id FROM authdb_jdbc.oauth2_registered_client;"
```

For users:
```bash
docker exec mysql-auth mysql -uroot -ppass1234 -N \
  -e "SELECT id, username FROM authdb_jdbc.app_user;"
```

Paste the id into the request URL.

## Why the requests aren't 100% self-service

The admin controllers return **HTML**, not JSON — this is a browser-driven admin UI, not a REST API. Postman still works fine because we exercise the same endpoints the browser does (form-urlencoded body + session cookie + CSRF token), but the responses are HTML strings you have to eyeball.

If you want proper JSON APIs, the natural extension is to add `/api/admin/**` controllers returning `@RestController` responses, gated on `hasRole('ADMIN')` with basic auth or an API key. That's a Phase 7 conversation.

## Common issues

| Symptom | Cause | Fix |
|---|---|---|
| `403 Forbidden` on POST admin action | Stale CSRF token | Re-run **Step 1 GET /login** and **Step 3 GET /admin** to refresh |
| `302 → /login` on admin GETs | Session cookie missing / expired | Re-run login steps 1 + 2 + 3 |
| POST /login returns 200 with the login page again | Bad creds or admin lacks ADMIN role | Check `app_user_role` in DB — should have `(1, 'ADMIN')` |
| `Whitelabel Error 500` on POST /login | Password missing `{bcrypt}` prefix in DB | See main docs — run repair migration, or `UPDATE app_user SET password = CONCAT('{bcrypt}', password) WHERE password NOT LIKE '{bcrypt}%'` |
| `access_token` missing after Step 2 (tokens) | Wrong client secret | Verify creds match `V6__seed.sql` |
| `Communications link failure` on any request | Server not running / MySQL down | `docker ps \| grep mysql-auth` and check `lsof -i :8095` |
