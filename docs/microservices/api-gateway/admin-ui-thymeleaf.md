# API Gateway — Thymeleaf Admin UI (with React toggle)

Server-rendered admin UI as an alternative to the React SPA. Which UI serves is
controlled by `gateway.admin.ui`:

```
gateway.admin.ui: REACT       → /admin/ui redirects to reactUrl (default)
gateway.admin.ui: THYMELEAF   → /admin/ui renders server-side Thymeleaf pages
gateway.admin.ui: NONE        → /admin/ui returns 404 (REST API only)
```

Companion to [admin-ui.md](admin-ui.md) (the React project).

---

## 1. Why both?

```
┌──────────────────────────────────────────────────────────────────────┐
│  React SPA (api-gateway-admin project)                               │
│  ────────────────────────────────────                                │
│  ✓ Rich UX (modals, live filtering, no page reloads)                 │
│  ✓ Separate deploy cycle                                             │
│  ✓ JWT Bearer (matches REST API auth model)                          │
│  ✗ Requires npm build + separate serving strategy in prod            │
│  ✗ Client-side state = XSS surface area                              │
│                                                                      │
│  Thymeleaf SSR (this build)                                          │
│  ─────────────────                                                   │
│  ✓ Zero JS, zero build step — `mvn spring-boot:run` is enough        │
│  ✓ Auth is HTTP Basic (browser-native prompt)                        │
│  ✓ SEO / accessibility "for free" (real HTML)                        │
│  ✓ No client-side JS = no XSS exfiltration path                      │
│  ✗ Full page reload on every action                                  │
│  ✗ Coupled to the gateway JVM (deploy together)                      │
│  ✗ Reactive Thymeleaf less common — fewer StackOverflow answers      │
│                                                                      │
│  When to pick which?                                                 │
│  - Admin used 5 times a week      → Thymeleaf                        │
│  - Admin used constantly by ops    → React                           │
│  - Compliance: no client-side JS   → Thymeleaf                       │
│  - JS-heavy team                   → React                           │
│  - Prod incident, no Node.js       → Thymeleaf as fallback           │
└──────────────────────────────────────────────────────────────────────┘
```

Config picks. Both share the same REST backend.

---

## 2. Toggle behavior

```
┌──────────────────────────────────────────────────────────────────────┐
│  gateway.admin.ui = REACT (default)                                  │
│  ──────────────────────                                              │
│  GET /admin/ui         → 302 http://localhost:5173/                  │
│                          (redirects to reactUrl — React dev server   │
│                           or built bundle host)                      │
│  GET /admin/routes     → REST API (unchanged, JWT-secured)           │
│  GET /admin/apikeys    → REST API                                    │
│                                                                      │
│  Active bean: AdminUiRedirectController                              │
│  Auth: JWT only (React app calls /oauth2/token then Bearer)          │
│                                                                      │
│                                                                      │
│  gateway.admin.ui = THYMELEAF                                        │
│  ──────────────────────                                              │
│  GET /admin/ui               → redirect to /admin/ui/routes          │
│  GET /admin/ui/routes        → Thymeleaf-rendered list + create form │
│  GET /admin/ui/routes/{id}/edit  → edit page                         │
│  POST /admin/ui/routes       → create (form submit) → redirect back  │
│  POST /admin/ui/routes/{id}/update → update                          │
│  POST /admin/ui/routes/{id}/delete → delete                          │
│  POST /admin/ui/routes/refresh → force RefreshRoutesEvent            │
│  GET /admin/ui/audit         → audit log (last 100)                  │
│  GET /admin/ui/apikeys       → API keys page                         │
│  POST /admin/ui/apikeys      → create key (shows raw ONCE)           │
│  POST /admin/ui/apikeys/revoke → revoke by keyId                     │
│                                                                      │
│  Active bean: AdminUiThymeleafController                             │
│  Auth: HTTP Basic (admin:admin123) OR JWT — both accepted            │
│                                                                      │
│                                                                      │
│  gateway.admin.ui = NONE                                             │
│  ────────────────────                                                │
│  GET /admin/ui         → 404 (no UI controller loaded)               │
│  GET /admin/routes     → REST API (unchanged)                        │
└──────────────────────────────────────────────────────────────────────┘
```

Each controller is `@ConditionalOnProperty` — only the matching one loads at boot.
**Switching UI requires a restart** (`@ConditionalOnProperty` evaluates once,
at bean-creation time, not on `/actuator/refresh`).

---

## 3. Thymeleaf-on-WebFlux (the interesting bit)

Spring Cloud Gateway runs on WebFlux/Netty, not servlets. Thymeleaf ships two
integrations:

- `SpringTemplateEngine` + `ThymeleafViewResolver` → **servlet** stack (MVC)
- `SpringWebFluxTemplateEngine` + `ThymeleafReactiveViewResolver` → **reactive**

Adding `spring-boot-starter-thymeleaf` to a WebFlux app auto-configures the
reactive path. Controllers return `Mono<String>` (view name) with a `Model`
populated during pre-render. No new dependency beyond the starter.

Interview point: *"what if the framework you want to use is servlet-only?"*
Thymeleaf isn't — but many are (some Spring Security features, some MVC
filters). Same trick applies: check for reactive variants first.

### Sample controller signature

```java
@GetMapping("/routes")
public Mono<String> routes(Model model) {
    return routeRepo.getRouteDefinitions().collectList()
            .doOnNext(list -> model.addAttribute("routes", list))
            .thenReturn("routes/list");   // view name → templates/routes/list.html
}

@PostMapping("/routes")
public Mono<String> create(@ModelAttribute RouteForm form) {
    // validate + save + redirect
    return Mono.just("redirect:/admin/ui/routes?created=" + form.getId());
}
```

Note the `@ModelAttribute` — same annotation as MVC. Reactive form binding
uses `ServerWebInputException` under the hood, but for the developer it looks
familiar.

---

## 4. Auth story for the Thymeleaf UI

Browsers don't naturally send Bearer headers on form submits. Options:

- **HTTP Basic** (chosen) — browser prompts for creds, sends `Authorization:
  Basic ...` on every request. Simple, no session, works out of the box.
- **Form login + session** — real production shape, needs a login page + session
  storage. Parked.
- **OIDC redirect + cookie** — production-plus. Big lift. Parked.

When `ui=THYMELEAF`, `GatewaySecurityConfig` adds `httpBasic(Customizer.withDefaults())`
AND registers a `MapReactiveUserDetailsService` with `admin:admin123` — same
credentials as the demo-admin-subs escape hatch for consistency.

The user has authority `SCOPE_admin` (mapped from `required-authorities`), so
`/admin/**` (both REST and UI) accepts the Basic-auth admin as well as any
JWT with SCOPE_admin.

**Both auth mechanisms coexist** — the REST API still accepts Bearer JWT
from the React app / API clients, while browsers use Basic.

---

## 5. Files added / changed

```
api-gateway/
├── pom.xml                                                       (+ spring-boot-starter-thymeleaf)
└── src/main/
    ├── java/com/example/apigateway/
    │   ├── config/
    │   │   └── GatewaySecurityConfig.java                        (+ HTTP Basic when ui=THYMELEAF,
    │   │                                                            + MapReactiveUserDetailsService bean)
    │   ├── security/
    │   │   └── AdminAuthProperties.java                          (+ Ui enum + reactUrl field)
    │   └── adminui/                                              (NEW package)
    │       ├── AdminUiRedirectController.java                    (NEW — active when ui=REACT)
    │       └── AdminUiThymeleafController.java                   (NEW — active when ui=THYMELEAF)
    └── resources/
        ├── application.yml                                       (+ gateway.admin.ui + react-url)
        └── templates/                                            (NEW dir)
            ├── layout.html                                       (base fragment: head + topbar)
            ├── routes/
            │   ├── list.html                                     (routes table + create form)
            │   └── edit.html                                     (edit page)
            ├── audit/
            │   └── list.html                                     (audit log + filter)
            └── apikeys/
                └── list.html                                     (create + revoke; raw key shown ONCE)

docs/microservices/api-gateway/
└── admin-ui-thymeleaf.md                                         (this file)
```

No React or REST-API changes.

---

## 6. Quick start

### Use the React UI (default)

```bash
# Terminal 1 — gateway
mvn -pl infra/api-gateway spring-boot:run
# gateway.admin.ui: REACT (yaml default)

# Terminal 2 — React dev server
cd api-gateway-admin
npm install
npm run dev
# → http://localhost:5173

# Browser: http://localhost:8080/admin/ui → 302 → http://localhost:5173
# OR go direct to :5173
```

### Switch to Thymeleaf UI

Edit `api-gateway/src/main/resources/application.yml`:

```yaml
gateway:
  admin:
    ui: THYMELEAF
```

Restart the gateway:

```bash
mvn -pl infra/api-gateway spring-boot:run
```

Open browser to `http://localhost:8080/admin/ui`:
- Browser prompts for HTTP Basic credentials → `admin` / `admin123`
- Redirected to `/admin/ui/routes` → server-rendered routes page

### Switch back to React

Set `gateway.admin.ui: REACT` (or remove — REACT is the default). Restart.

### Disable UI entirely

```yaml
gateway.admin.ui: NONE
```

`/admin/ui` returns 404. REST API at `/admin/routes` etc. still works.

---

## 7. Verification

### 7.1 React mode

```bash
curl -i http://localhost:8080/admin/ui
# HTTP/1.1 302 Found
# Location: http://localhost:5173/
```

### 7.2 Thymeleaf mode

```bash
# Without auth → 401
curl -i http://localhost:8080/admin/ui/routes
# HTTP/1.1 401 Unauthorized
# WWW-Authenticate: Basic realm="Realm"

# With HTTP Basic → 200 + HTML page
curl -i -u admin:admin123 http://localhost:8080/admin/ui/routes
# HTTP/1.1 200 OK
# Content-Type: text/html
# <!DOCTYPE html>
# <html ...>

# Create a route via form submit
curl -i -u admin:admin123 -X POST http://localhost:8080/admin/ui/routes \
  --data-urlencode "id=thymeleaf-demo" \
  --data-urlencode "uri=lb://product-service" \
  --data-urlencode "order=0" \
  --data-urlencode "predicatesJson=[{\"name\":\"Path\",\"args\":{\"_genkey_0\":\"/thymeleaf-demo/**\"}}]" \
  --data-urlencode "filtersJson=[]"
# → 302 Location: /admin/ui/routes?created=thymeleaf-demo

# Confirm the route landed
curl -s -u admin:admin123 http://localhost:8080/admin/routes | jq
# includes {"id":"thymeleaf-demo", ...}

# Audit endpoint shows it
curl -s -u admin:admin123 http://localhost:8080/admin/ui/audit | grep thymeleaf-demo
```

### 7.3 NONE mode

```bash
curl -i http://localhost:8080/admin/ui
# HTTP/1.1 404 Not Found

# REST still works
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/admin/routes
# 200
```

---

## 8. Failure modes & mitigations

| Scenario | Behavior | Mitigation |
|---|---|---|
| Both Redirect + Thymeleaf controllers loaded | Impossible — `@ConditionalOnProperty` on the enum value ensures exactly one loads | Present |
| React dev server not running (ui=REACT) | Browser redirect lands on unreachable URL | Start `npm run dev` OR switch to THYMELEAF |
| Change ui property + /actuator/refresh | Controllers don't hot-swap — need restart | Documented; `@ConditionalOnProperty` is boot-only |
| Bad JSON in form submit | Server-side redirect back with `?error=...` in URL | Handled in controller catch block |
| CSRF token missing | CSRF is repo-wide disabled (existing config) | For prod, enable CSRF + add `<input type="hidden" th:name="_csrf.parameterName" th:value="_csrf.token"/>` |
| HTTP Basic creds sent to REST endpoints | Both work — Basic OR Bearer JWT accepted for /admin/** | Intentional |
| Thymeleaf template not found | Error page rendered by Spring Boot default | Verify view name matches file path under `templates/` |
| Reactive model attribute evaluated too late | Sometimes model.addAttribute("x", monoValue) needs `.thenReturn(viewName)` | Idiom: `.doOnNext(v -> model.addAttribute(...)).thenReturn(view)` |

---

## 9. Interview cheat-sheet

| Question | Answer |
|---|---|
| Why offer both React and Thymeleaf? | Different ops profiles. React for daily use / rich UX; Thymeleaf for zero-JS incident response, compliance-restricted environments, or single-JVM demos. |
| Why not enable both at once? | Same URL prefix (`/admin/ui`) — Spring would fail with duplicate mapping. `@ConditionalOnProperty` on an enum value picks one. |
| Thymeleaf on WebFlux — how? | Spring Boot's `spring-boot-starter-thymeleaf` detects WebFlux and wires `ThymeleafReactiveViewResolver`. Controllers return `Mono<String>`. |
| Why HTTP Basic for the browser? | Simplest browser-native auth. Form login + session is prod-shape but bigger lift. |
| Can you use both auth mechanisms? | Yes. `.oauth2ResourceServer(...jwt(...))` + `.httpBasic(...)` = Spring accepts EITHER. REST clients keep sending Bearer; browsers use Basic. |
| Toggle without restart? | Not possible with `@ConditionalOnProperty` — it evaluates at bean-creation. Would need to build the UI mode as a runtime dispatch inside a single controller. |
| Why full page reload vs SPA? | SSR trade-off. HTMX + Thymeleaf fragments is the middle ground — SPA-like UX without a build step. Parked as extension. |
| CSRF? | Disabled repo-wide (default). For prod, enable it AND add token inputs on every Thymeleaf form. |
| Shared REST backend? | Yes. Both UIs call the same `/admin/routes` etc. REST endpoints. |
| Why put Basic-auth user in code, not properties? | Demo — real prod would use OIDC / LDAP / a real user store. `MapReactiveUserDetailsService` is the smallest viable option. |
| React app can still work when ui=THYMELEAF? | Yes — you can boot both. React still calls REST directly. The `ui` config only decides what `/admin/ui` on the gateway serves. |

---

## 10. Common pitfalls (interview probes)

1. **Two UI beans loading at once** → mapping conflict. `@ConditionalOnProperty` with `havingValue` guards against it.
2. **Thymeleaf ViewResolver mismatch** — accidentally adding `spring-boot-starter-web` (servlet) alongside WebFlux confuses Spring Boot; it picks servlet auto-config and everything else breaks. `spring-boot-starter-thymeleaf` alone is safe.
3. **Model attribute forgot to resolve** — `model.addAttribute("routes", monoValue)` renders as `null` unless Thymeleaf reactive resolves it. Simpler: `.collectList().doOnNext(list -> model.addAttribute("routes", list)).thenReturn("view")`.
4. **CSRF token missing after enable** — Thymeleaf doesn't auto-inject the token in a form; you must add `<input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>`.
5. **Form binding fails with 400** — reactive form binder needs a class with public setters. Records don't work directly for `@ModelAttribute` bindings (they need constructor injection which Spring 6 supports for MVC but is finicky for WebFlux forms). Used a POJO with setters here.
6. **HTTP Basic realm popup annoying** — for real prod, use form login + session so users log in once, not per page. Or use a proper IdP.

---

## 11. Extensions (parked)

- **HTMX + Thymeleaf fragments** — partial-page updates, SPA-like UX without a build. Best-of-both-worlds pattern for internal tools.
- **Form login + session** — real login page instead of Basic. Requires `.formLogin(...)` + session store.
- **CSRF tokens on forms** — enable Spring Security CSRF + add hidden inputs.
- **Cross-site login page** — OIDC redirect + cookie session for prod. Uses `oauth2Login()` DSL.
- **List API keys endpoint** — currently the store doesn't have a paginated `findAll()` method; adding one exposes the list on the Thymeleaf apikeys page.
- **Runtime UI switch** — refactor to a single controller that dispatches based on runtime property (loses `@ConditionalOnProperty` compile-time safety but gains zero-downtime toggle).
- **Prometheus dashboard tab** — embed Recharts / SVG rendered server-side.
- **Bulk operations** — checkbox rows + `<button name="action" value="delete-selected">`.
- **Dark mode** — swap CSS variables via `prefers-color-scheme: dark` in `layout.html`.
