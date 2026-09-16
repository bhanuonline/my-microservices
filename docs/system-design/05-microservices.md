# System Design — Microservices Architecture

Section 12 of the study series. Continues from [04-distributed-systems-theory.md](./04-distributed-systems-theory.md).

Sections 1–11 gave you the primitives. Section 12 is about the **organizing pattern** — how to split, connect, secure, observe, and deploy services. This is your day-to-day as a Java/Spring dev.

---

## 12. Microservices Architecture

### 12.1 The evolution — monolith → SOA → microservices → serverless

Architectures evolved to match team size, deployment cadence, and scale needs.

**Monolith**

```
   ┌──────────────────────────────────┐
   │  ONE deployable app              │
   │                                  │
   │   Auth  Orders  Catalog  Search  │
   │   Cart  Payment Email    Reports │
   │                                  │
   │       ONE shared database        │
   └──────────────────────────────────┘
```

- ✅ Simple deploy, simple debug, one language, easy transactions.
- ✅ Great for early-stage products.
- ❌ Team coupling: everyone edits the same code.
- ❌ Any change → redeploy everything.
- ❌ Scale = scale the whole thing.

**SOA (Service-Oriented Architecture, ~2000s)**

```
   ┌───────────┐ ┌───────────┐ ┌───────────┐
   │  Service  │ │  Service  │ │  Service  │
   │     A     │ │     B     │ │     C     │
   └─────┬─────┘ └─────┬─────┘ └─────┬─────┘
         │             │             │
         └─────────────┴─────────────┘
                       │
                       ▼
              ┌──────────────────┐
              │  ESB / SOAP bus  │  ← centralized, XML-heavy, complex
              └──────────────────┘
```

- ✅ Separate services.
- ❌ Heavy central bus (ESB) became the bottleneck.
- ❌ Governance-heavy: contracts, schemas, standards committees.
- ❌ Usually shared DB anyway — not truly independent.

**Microservices (2010s onward)**

```
   ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
   │ Users    │  │ Orders   │  │ Catalog  │  │ Payment  │
   │ svc      │  │ svc      │  │ svc      │  │ svc      │
   │  ─────   │  │  ─────   │  │  ─────   │  │  ─────   │
   │  own DB  │  │  own DB  │  │  own DB  │  │  own DB  │
   └──────────┘  └──────────┘  └──────────┘  └──────────┘
        │             │              │             │
        └─────────────┴──────────────┴─────────────┘
                     lightweight comms
                     (REST, gRPC, Kafka)
```

- ✅ Independent deploy, scale, tech stack per service.
- ✅ Teams own end-to-end (Conway's Law works FOR you).
- ✅ Fault isolation.
- ❌ Distributed-systems tax: everything gets harder (Section 11's problems become yours).
- ❌ Ops complexity, cross-cutting concerns everywhere.

**Serverless / FaaS**

```
   ┌──────────────────────────────────────────┐
   │  Cloud runtime (Lambda / Cloud Functions)│
   │                                          │
   │   fn:handleOrder(event) → run on demand  │
   │   fn:sendEmail(event)   → scale to zero  │
   │   fn:resizeImage(event) → pay per call    │
   └──────────────────────────────────────────┘
```

- ✅ Zero ops. Auto-scaling. Pay per invocation.
- ✅ Perfect for spiky, event-driven, glue code.
- ❌ Cold starts.
- ❌ Vendor lock-in.
- ❌ Long/stateful workloads awkward.
- ❌ Hard to reason about cost at scale.

**Evolution comparison table**

| Property | Monolith | SOA | Microservices | Serverless |
|---|---|---|---|---|
| Deployment unit | One app | Few services | Many services | Function |
| Scale unit | Whole app | Service | Service | Function |
| Data model | Shared DB | Often shared | DB per service | Storage per fn |
| Communication | In-process | SOAP/ESB | REST/gRPC/events | Events/APIs |
| Team model | One team | Aligned teams | Team per svc | Small squads |
| Ops complexity | Low | Medium | High | Low (managed) |

**The right answer isn't always microservices.** Start monolith. Split when team coordination or scaling forces it. Don't over-microservice.

**Interview one-liner**
> "Monoliths are the right starting point. SOA over-engineered communication with a heavy ESB. Microservices decentralized — every service owns its stack, DB, and deploy — at the cost of distributed-systems complexity. Serverless takes it further for event-driven glue but doesn't fit every workload."

---

### 12.2 Splitting the monolith — bounded contexts and Conway's Law

**How do you decide what's a service?**

The wrong answers:
- By technical layer (auth-service, DB-service, cache-service). This creates entangled dependencies.
- By CRUD entity (user-service, order-service, product-service). Too fine-grained; joins everywhere.

The right answer: **bounded contexts** from Domain-Driven Design.

**Bounded context**
A boundary within which a model is consistent and terminology is unambiguous.

```
   In "Sales" context:      In "Support" context:
     "Customer" = someone     "Customer" = someone
     who bought               with a ticket
   
   Different model → different service. Talk via well-defined events.
```

**Conway's Law**
> "Organizations design systems that mirror their communication structure."

So: **align services to teams.** One team = one (or a few) service. Cross-team services are pain.

**Signs you should split**
- Two teams stepping on each other in the same repo.
- Deploy cadence needs differ (payment: careful; catalog: rapid).
- Scale profiles differ (search: heavy; billing: light).
- Failure isolation matters (payment must survive catalog outage).

**Signs you should NOT split**
- You only have 3 devs.
- The domain isn't well understood yet.
- Every "service" would need to talk to every other one.
- Latency budgets can't afford the network hops.

**The strangler fig pattern — migrating a monolith**

```
   Step 1:  Monolith serving everything.
   
   Step 2:  Introduce a router in front.
              Router ──► Monolith
   
   Step 3:  Peel one feature into a new service.
              Router ──► Monolith (most)
                     └─► New svc  (one route)
   
   Step 4:  Repeat until the monolith is small (or gone).
```

Migrate incrementally. Nobody wants a "big bang rewrite."

**Interview one-liner**
> "Split by bounded context, aligned to teams. Wrong splits create distributed monoliths — worse than a real monolith. Migrate incrementally with the strangler fig."

---

### 12.3 Service discovery — how services find each other

In a monolith, module A calls module B via method call. In microservices, A must find B's network address. Addresses change (autoscaling, restarts). **Service discovery** solves this.

**Two patterns**

**A. Client-side discovery**

```
   ┌──────────┐   ① query      ┌────────────────┐
   │ Client   │───────────────►│ Registry        │
   │ (svc A)  │◄─── address ── │ (Consul/Eureka) │
   │          │                └────────────────┘
   │          │
   │          │   ② direct call  ┌──────────┐
   │          │──────────────────►│  Svc B   │
   └──────────┘                   └──────────┘
```

- Client picks a specific instance and calls it.
- Client-side load balancing (Spring Cloud LoadBalancer, Netflix Ribbon).
- ✅ No extra hop.
- ❌ Every language/client re-implements LB logic.

**B. Server-side discovery**

```
   ┌──────────┐        ┌─────────────┐     ┌──────────┐
   │ Client   │───────►│  Router /   │────►│  Svc B   │
   │ (svc A)  │        │  LB         │     └──────────┘
   └──────────┘        │             │
                       │  queries    │
                       │  registry   │
                       └─────────────┘
```

- Router picks the instance, client just calls a stable name.
- ✅ Client is dumb (just an HTTP client).
- ❌ Extra hop; router is critical infra.

**Kubernetes model — server-side, hidden**

```
   Client calls http://user-svc/api/...
   
   ▸ DNS: user-svc → ClusterIP
   ▸ kube-proxy iptables/IPVS load-balances across Pods
   ▸ Endpoints controller keeps the pool of Pod IPs current
   ▸ Health probes remove sick Pods automatically
```

Discovery is invisible to your app code. **The dominant pattern today.**

**Common tools**
- **Consul** (HashiCorp) — DNS + HTTP API, health checks.
- **Eureka** (Netflix) — client-side, Spring-Cloud favorite.
- **Etcd + custom** — Kubernetes uses this.
- **Zookeeper** — older, used by Kafka, HBase historically.

**Interview one-liner**
> "Client-side discovery gives fewer hops but couples the client to the registry protocol. Server-side (Kubernetes' default) hides it behind a stable name. In modern stacks, Kubernetes + DNS is discovery, quietly."

---

### 12.4 Configuration management — externalized config

**The rule**
Code is the same across environments. **Config is what changes.**

**12-factor app principle:** store config in the environment, not in the code.

```
   ┌──────────────────────┐
   │  Code (same image)   │
   │                      │
   │   reads env vars     │
   │   reads config svc   │
   └──────────┬───────────┘
              │
              ▼
   Dev:    DB_URL=localhost   FEATURE_X=false
   Stage:  DB_URL=stage-db    FEATURE_X=true
   Prod:   DB_URL=prod-db     FEATURE_X=true
```

**Levels of config**
- **Static** — baked into env vars / files on deploy.
- **Dynamic** — polled or pushed at runtime (feature flags).
- **Secrets** — encrypted, rotated, audit-logged.

**Config server patterns**

```
   ┌───────────┐   ① fetch config on startup
   │  Svc A    │────────────────────────────┐
   └───────────┘                            │
                                            ▼
   ┌───────────┐   ② watch for changes  ┌────────────────┐
   │  Svc B    │──────────────────────►│ Config Server  │
   └───────────┘                        │ (Spring Cloud │
                                        │  Config, Consul│
   ┌───────────┐   ③ push updated       │  KV, Etcd)     │
   │  Svc C    │◄──────────────────────│                 │
   └───────────┘                        └────────────────┘
```

**Feature flags**
Runtime switches. Allow deploying code without exposing it.

```
   if (featureFlag.isEnabled("new-checkout", user)) {
     return newCheckoutFlow(user);
   } else {
     return legacyCheckoutFlow(user);
   }
```

Tools: LaunchDarkly, Unleash, Flagsmith, or homegrown.

Use for: gradual rollouts, A/B tests, kill-switches for risky features.

**Secrets management**

```
   ❌ Secrets in Git.
   ❌ Secrets in env vars in your Kubernetes YAML files.
   ✅ Secrets in HashiCorp Vault / AWS Secrets Manager / GCP Secret Manager,
      injected at runtime with short TTLs.
```

**Java angle**
- Spring Boot: `application.yml` + `--spring.profiles.active=prod`.
- Spring Cloud Config Server for centralized config.
- Spring Cloud Vault / AWS SDK for secrets.
- Kubernetes ConfigMaps + Secrets → mounted as env or files.

**Interview one-liner**
> "Externalize all config. The same container image runs everywhere; environment supplies DB URLs, flags, and secrets. Feature flags decouple deploy from release."

---

### 12.5 API design — REST, GraphQL, gRPC (revisited for design tradeoffs)

You've seen these individually. Now the design lens.

**REST**

```
   URL is the noun.       Method is the verb.
   
   GET    /users/42
   POST   /users
   PUT    /users/42
   DELETE /users/42
   
   Nested:
   GET    /users/42/orders?status=open&page=3
```

- ✅ Universal, cacheable, browser-friendly, debuggable with curl.
- ❌ Over-fetching (get whole user when you need just name).
- ❌ Under-fetching (N+1 API calls for related data).
- ❌ Versioning is manual (`/v1/`, headers).

**GraphQL**

```
   Client asks for exactly what it wants:
   
   query {
     user(id: 42) {
       name
       orders(status: OPEN) {
         id
         total
       }
     }
   }
   
   Server resolves in one round trip.
```

- ✅ No over/under-fetching. One endpoint.
- ✅ Strong schema, tooling.
- ❌ Complex server (resolvers, N+1 mitigation with DataLoader).
- ❌ Caching harder (POST-based, custom keys).
- ❌ File uploads / binary awkward.

**gRPC**

You saw this in Section 2.6. For design purposes:

- ✅ Compact binary (Protobuf).
- ✅ Strict contracts (.proto).
- ✅ 4 streaming modes.
- ❌ Not browser-friendly (needs grpc-web).
- ❌ Harder to debug (need grpcurl).

**Picking one — the decision tree**

```
                        Client type?
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
     Browser         Internal service         Mobile / desktop
        │                    │                    │
        ▼                    ▼                    ▼
   ┌────────┐          ┌────────┐           ┌────────────┐
   │ REST   │          │ gRPC   │           │  Client    │
   │ or     │          │ (fast, │           │  builds    │
   │GraphQL │          │strong  │           │  dictate   │
   │        │          │contract│           │  REST or   │
   │        │          │)       │           │  GraphQL   │
   └────────┘          └────────┘           └────────────┘
```

**Versioning strategies**

```
   URL versioning:   /api/v1/users, /api/v2/users
   Header versioning: Accept: application/vnd.myapp.v2+json
   Content negotiation: response fields evolve, clients tolerate extras
   
   Rule: never break clients. Add fields, don't rename. Deprecate slowly.
```

**Pagination**

```
   Offset-based:   ?page=3&size=20   ← easy, but slow on large tables
   Cursor-based:   ?cursor=xyz&limit=20  ← consistent under inserts, faster
```

Cursor-based is the modern default (Twitter, Instagram, Stripe).

**Interview one-liner**
> "REST is the default for public / browser APIs. gRPC wins for internal service-to-service (contract + streaming). GraphQL fits when clients need flexible queries over a rich graph. Never break clients; version deliberately."

---

### 12.6 Authentication and authorization

**AuthN vs AuthZ**
- **Authentication (AuthN)** — who are you? Login.
- **Authorization (AuthZ)** — what are you allowed to do? Permissions.

Different concerns, both required.

**Session vs Token — the big split**

You saw this in Section 1.11. In microservices, **tokens win** because they're stateless and cross-service.

**OAuth 2.0 — the delegation framework**

OAuth 2.0 lets a user grant a **third-party app** access to their resources on **your service**, without sharing their password.

**The classic Authorization Code flow**

```
   ① User clicks "Login with Google" on YourApp
   
   ② YourApp redirects browser to Google:
        GET https://accounts.google.com/auth
            ?client_id=YourApp
            &redirect_uri=YourApp/callback
            &scope=email
            &response_type=code
   
   ③ User logs into Google, consents.
   
   ④ Google redirects browser back:
        YourApp/callback?code=abc123
   
   ⑤ YourApp server exchanges code for tokens:
        POST https://oauth2.google.com/token
            code=abc123
            client_id=... client_secret=...
        ← returns access_token, refresh_token, id_token
   
   ⑥ YourApp uses access_token to call Google APIs:
        GET https://www.googleapis.com/user
            Authorization: Bearer <access_token>
```

**Key concepts**
- **Client ID / secret** — identifies YourApp to the auth server.
- **Scope** — what YourApp is asking for.
- **Access token** — short-lived (minutes to hours). Used to call APIs.
- **Refresh token** — long-lived. Exchange for new access tokens.
- **Authorization code** — one-time, used to prevent token leakage in browser URL.

**OIDC — OpenID Connect (auth on top of OAuth)**

OAuth 2.0 is for **authorization** ("this app can access your calendar"). OIDC adds **identity** ("this is the user") on top.

```
   OIDC adds:
   
   id_token (JWT):  {
     iss: "https://accounts.google.com",
     sub: "user123",
     email: "a@x.com",
     iat: ..., exp: ...
   }
   
   → verifies WHO the user is, cryptographically signed by Google.
```

Use OIDC when you're implementing "Login with X" — it gives you identity, not just API access.

**JWT — the token format**

Recall Section 1.11:

```
   header.payload.signature   ← all base64
   
   Payload example:
     {
       "sub": "user_42",
       "roles": ["admin", "user"],
       "iat": 1734567890,
       "exp": 1734571490,
       "iss": "auth.myco.com"
     }
   
   Signature (HMAC or RSA/ECDSA) proves the token wasn't forged.
```

**JWT verification in microservices**

```
   Client ──JWT──► Service A
                    │
                    ├─ verify signature (public key from auth server / JWKS)
                    ├─ check exp / iss / aud
                    └─ read claims → know user, roles
   
   No DB lookup, no auth server round-trip. Stateless.
```

**JWT downsides (recap)**
- Can't revoke before expiry without extra state.
- Payload visible (base64). Never put secrets in it.
- Larger than opaque session IDs.

**Fixes**
- Short access tokens (5-15 min) + refresh tokens.
- Token blocklist for emergency revocation.
- Rotate signing keys via JWKS.

**mTLS — mutual TLS**

TLS normally: server proves who it is. **Mutual TLS**: client proves who it is TOO.

```
   Service A ──cert-A──► Service B (verifies A's cert)
   Service A ◄─cert-B── Service B (verifies B's cert)
   
   Both sides authenticated.
```

- ✅ Strong service-to-service auth (identity is the cert).
- ✅ Encrypted transport.
- Used by: service meshes (Istio, Linkerd), zero-trust networks.
- Cert issuance/rotation via CA (SPIFFE/SPIRE, cert-manager).

**AuthZ patterns**

**Role-based access control (RBAC)**
```
   User has roles: [admin, editor].
   Endpoint requires role: editor.
   → allow.
```

**Attribute-based access control (ABAC)**
```
   Rule: user can edit doc if doc.owner_id = user.id AND doc.status != "locked".
   → evaluated per request against attributes.
```

**Policy engines**
- **Open Policy Agent (OPA)** — decouple policy from code.
- **Casbin** — library-based policy engine.
- **AWS IAM** — cloud-scale ABAC.

**Interview one-liner**
> "OAuth 2.0 is delegated authorization; OIDC adds identity on top. JWT carries claims stateless across services. mTLS authenticates services to each other. Use short access tokens + refresh; use policies (not code) for complex authorization."

---

### 12.7 Observability — logs, metrics, traces

You can't operate what you can't see. Modern observability has three pillars.

```
   ┌────────────────────────────────────────────────┐
   │  Logs      "what happened, in text"            │
   │  Metrics   "how much, how fast, aggregated"    │
   │  Traces    "the path of ONE request across svcs"│
   └────────────────────────────────────────────────┘
```

**Logs**

```
   Structured (JSON) → machine parseable:
   
   { "ts": "...", "level": "INFO", "svc": "orders", "user_id": 42,
     "trace_id": "abc", "msg": "order created", "order_id": 99 }
   
   ❌ Plain text is hard to filter, correlate.
```

**Metrics**

```
   Prometheus-style:
   
   http_requests_total{method="GET", path="/users", status="200"}  = 12345
   http_request_duration_seconds_bucket{le="0.1"} = 10000
   http_request_duration_seconds_bucket{le="0.5"} = 12000
   
   Aggregated over time → dashboards, alerts.
```

Metric types:
- **Counter** — monotonically increasing (total requests).
- **Gauge** — instantaneous value (memory in use).
- **Histogram** — bucketed distribution (latency).
- **Summary** — client-side percentiles.

**Traces**

```
   One request touches many services. A trace stitches them together.
   
   Trace ID: abc123
     Span A: gateway   (start=0, end=200ms)
       Span B: user-svc  (start=10, end=50ms)
       Span C: order-svc (start=60, end=180ms)
         Span D: DB     (start=70, end=120ms)
         Span E: cache  (start=130, end=140ms)
```

Visualized:

```
   gateway     [────────── 200ms ──────────]
   user-svc      [── 40ms ──]
   order-svc              [────── 120ms ──────]
   db                       [── 50ms ──]
   cache                                [10ms]
```

Instantly shows where time went.

**OpenTelemetry — the standard**
- SDK for logs + metrics + traces in every language.
- Vendor-neutral protocol (OTLP).
- Exports to Jaeger, Zipkin, Prometheus, Grafana, Datadog, etc.

**The observability stack (typical)**

```
   Your services  ──logs──►  Loki / ELK / CloudWatch
                  ──metrics─► Prometheus  → Grafana
                  ──traces──► Jaeger / Tempo / Zipkin
                  
   All three correlated by trace_id.
```

**SLIs, SLOs, SLAs**

```
   SLI (indicator)  — the measurement.
                       e.g., "99.5% of requests < 300ms"
   
   SLO (objective) — the target you commit to internally.
                       e.g., "P99 latency < 300ms over 30 days"
   
   SLA (agreement) — the contract with customers (usually looser).
                       e.g., "99.9% availability or refund"
   
   Error budget  = 1 - SLO
                  Consume it with risky changes; freeze if depleted.
```

**Java angle**
- **Micrometer** — metric facade, exports to Prometheus.
- **OpenTelemetry Java agent** — auto-instruments Spring, JDBC, Kafka.
- **Logback/Log4j** JSON encoder for structured logs.
- **Sleuth / Micrometer Tracing** for trace context propagation.

**Interview one-liner**
> "Logs tell you what happened; metrics tell you how often and how fast; traces tell you where a single request went. OpenTelemetry unifies collection. SLIs/SLOs turn observability into an accountability framework."

---

### 12.8 Deployment strategies

Modern shops release many times per day. **How** you release matters as much as **what**.

**A. Recreate (naive)**

```
   [v1][v1][v1]  → [ ][ ][ ]  → [v2][v2][v2]
   
   Stop old, start new. Downtime during cutover.
```

**B. Rolling update (default in Kubernetes)**

```
   Step 1: [v1][v1][v1]
   Step 2: [v2][v1][v1]   ← spin up one v2, kill one v1
   Step 3: [v2][v2][v1]
   Step 4: [v2][v2][v2]
   
   ✅ No downtime.
   ❌ Old and new run simultaneously → schema and API changes must be compatible.
   ❌ Hard to rollback quickly if bugs appear mid-roll.
```

**C. Blue-Green**

```
   Blue (live):  [v1][v1][v1]   ← current traffic
   Green (idle): [v2][v2][v2]   ← deployed but not receiving
   
   Router flips: Blue → Green in one shot.
   
   ✅ Instant rollback (flip back).
   ✅ Simple to reason about.
   ❌ Doubles infra during deploy.
   ❌ DB migrations across the flip are tricky.
```

**D. Canary**

```
   Live: 95% v1  +  5% v2 (canary)
     │
     │ monitor error rates, latency
     ▼
   Live: 50% v1  +  50% v2   (if healthy)
     ▼
   Live: 100% v2
   
   Rollback = re-shift traffic.
```

- ✅ Real-user validation, low blast radius.
- ✅ Great with metrics/SLO guards.
- ❌ More complex traffic shaping (Istio, Envoy, LB rules).

**E. Feature flags**

```
   Deploy v2 with the new feature gated behind a flag.
   
   Turn flag ON for internal users → 1% users → 10% → 100%.
   
   Rollback = flip the flag OFF (no re-deploy).
```

Decouples **deploy** from **release**. The safest way to ship risky features.

**F. Shadow / Mirror traffic**

```
   Real traffic → v1 (returns response)
              └→ v2 (executes silently, response discarded, metrics collected)
   
   Great for perf-testing new code with real load, no user risk.
```

**Deployment strategies comparison**

| Strategy | Downtime | Rollback speed | Infra cost | Best for |
|---|---|---|---|---|
| Recreate | Yes | Redeploy old | 1× | Dev, small apps |
| Rolling | No | Slowish (undo roll) | 1× | Default |
| Blue-Green | No | Instant | 2× | Big-bang releases |
| Canary | No | Traffic shift | 1× + a bit | User-facing services |
| Feature flag | No | Flip flag | 1× | Risky features |
| Shadow | No | N/A (no user impact) | 2× | Perf/parity testing |

**Database migrations — the underrated risk**

Deploys are easy. Schema migrations are hard because old + new code run together.

```
   Rule: always make schema changes BACKWARDS-COMPATIBLE.
   
   ❌ Rename column         → old code breaks.
   ✅ Add new column, dual-write, migrate reads, drop old column later.
   
   ✅ Deployment order:
      ① Schema change (backwards-compatible).
      ② Code that USES the new column.
      ③ Migrate data (backfill).
      ④ Remove old column (later deploy).
```

**Interview one-liner**
> "Rolling is the default; blue-green is safest for stateful cutovers; canary is safest for user-facing services; feature flags decouple deploy from release. Never break the DB — do additive migrations across multiple deploys."

---

### 12.9 Cross-cutting concerns — the microservices checklist

For every new service, you must have:

```
   ┌─────────────────────────────────────────┐
   │ ☐ Health checks (/health, /ready)       │
   │ ☐ Structured logging with trace IDs     │
   │ ☐ Metrics (RED / USE)                   │
   │ ☐ Distributed tracing                   │
   │ ☐ AuthN (JWT / mTLS)                    │
   │ ☐ AuthZ (RBAC / policy)                 │
   │ ☐ Rate limiting                         │
   │ ☐ Circuit breaker + retries + timeouts  │
   │ ☐ Config from environment               │
   │ ☐ Secrets from vault                    │
   │ ☐ Graceful shutdown                     │
   │ ☐ Idempotent operations                 │
   │ ☐ Backpressure handling                 │
   │ ☐ API versioning strategy               │
   │ ☐ CI/CD pipeline                        │
   │ ☐ Runbook + on-call docs                │
   └─────────────────────────────────────────┘
```

**RED metrics** — for user-facing services:
- **R**ate — requests per second
- **E**rrors — % failing
- **D**uration — latency distribution

**USE metrics** — for infrastructure:
- **U**tilization — how busy
- **S**aturation — how backed up
- **E**rrors — hardware/software

Every service should export both.

---

### 12.10 The platform play — golden path

Big orgs don't leave the checklist to each team. They build a **platform**.

```
   ┌────────────────────────────────────────────┐
   │ Internal Developer Platform                │
   │                                            │
   │  ▸ Service template (Spring Boot boilerplate)│
   │  ▸ CI/CD pipeline templates                │
   │  ▸ Preconfigured observability             │
   │  ▸ Auth patterns wired in                  │
   │  ▸ Secret injection                        │
   │  ▸ Deploy to K8s in one command            │
   │  ▸ Runbook + dashboards auto-provisioned   │
   └────────────────────────────────────────────┘
   
   Team creates a new service → the platform gives it 80% for free.
```

Tools: Backstage (Spotify), custom internal CLIs, Terraform modules, Helm charts. The point: **make the right thing easy**.

---

### 12.11 Java angle — the Spring microservices stack

```
   ┌─────────────────────────────────────────────────────────┐
   │ Layer                     Typical choice                 │
   ├─────────────────────────────────────────────────────────┤
   │ Framework                 Spring Boot 3.x                │
   │ HTTP + REST               Spring Web (MVC or WebFlux)    │
   │ gRPC                      grpc-spring-boot-starter       │
   │ Persistence               Spring Data JPA + HikariCP     │
   │ Messaging                 spring-kafka / spring-amqp     │
   │ Cache                     Spring Cache + Caffeine/Redis  │
   │ Config                    Spring Cloud Config + Vault    │
   │ Discovery                 K8s DNS (or Eureka/Consul)      │
   │ API Gateway               Spring Cloud Gateway            │
   │ Auth                      Spring Security + OAuth2 Resource│
   │ Resilience                Resilience4j                    │
   │ Observability             Micrometer + OpenTelemetry Agent│
   │ Migrations                Flyway / Liquibase              │
   │ Deploy                    Kubernetes + Helm               │
   │ CI/CD                     GitHub Actions / GitLab / Jenkins│
   └─────────────────────────────────────────────────────────┘
```

**The Spring Boot service template you should always have**
```
   ▸ Actuator endpoints (/health, /info, /metrics)
   ▸ Structured JSON logging
   ▸ Micrometer + OpenTelemetry auto-config
   ▸ JWT security via spring-boot-starter-oauth2-resource-server
   ▸ Flyway migrations enabled
   ▸ Graceful shutdown enabled
   ▸ Dockerfile + Helm chart
   ▸ Baseline resilience4j configs
```

Every new service starts from this. Nothing built from scratch.

---

### 12.12 Common anti-patterns

- **Distributed monolith** — services technically split, but one release ships all → worst of both worlds. Fix: contract testing, backward-compat rules.
- **Chatty services** — 10 sync calls per request. Fix: aggregate at edge, async events, denormalize.
- **Shared database** — services owning the same tables. Fix: give each service its own DB, communicate via events.
- **Ignoring failures** — no timeouts, no breakers. Fix: apply Section 8.
- **No versioning strategy** — services break each other on deploy. Fix: contract-first, additive changes.
- **Micro-microservices** — 200 services, 15 devs. Fix: consolidate.
- **Debugging without tracing** — impossible after 5+ services. Fix: OpenTelemetry from day one.
- **Config in code** — different image per env. Fix: 12-factor externalized config.

---

### 12.13 Summary — one-liners you can defend

- **Split by bounded context, aligned to teams.**
- **Discovery is a solved problem — Kubernetes hides it.**
- **Config lives in the environment, not the code.**
- **REST for browsers, gRPC for internal, GraphQL for flexible clients.**
- **OAuth 2.0 delegates access; OIDC adds identity; JWT carries claims; mTLS authenticates services.**
- **Observability = logs + metrics + traces, all correlated by trace_id.**
- **Deploy != release. Feature flags separate them.**
- **DB migrations are additive; never rename in place.**
- **Every service starts from a golden-path template with resilience, obs, auth, and CI baked in.**

**Top-level insight**
> "Microservices don't make hard problems go away — they push them into the network, where the tools of Sections 5–11 (CAP, consistency, consensus, retries, circuit breakers, observability) become your daily grind. The winners standardize the cross-cutting concerns so teams can focus on domain."

---

*Next up: Section 13 — Reliability, security, ops: SLA/SLO/SLI, failure modes (cascading, thundering herd, retry storms), chaos engineering, multi-region, DR/RTO/RPO, encryption, KMS, threat modeling, OWASP Top 10.*
