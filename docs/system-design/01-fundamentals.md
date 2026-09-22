# System Design — Fundamentals

A running notebook of the "must-know cold" fundamentals. Each topic starts with intuition, then the mental model, then interview-ready facts.

---

## 1. Client–Server Model & Request Lifecycle

### 1.1 What is the Client–Server model?

At its core: **two roles talking over a network**.

- **Client** — initiates a request. Wants something (data, action).
- **Server** — listens for requests, does work, returns a response.

That's it. Everything else in system design is variations on this theme:
- Browser ↔ Web server
- Mobile app ↔ REST API
- Microservice A ↔ Microservice B
- App ↔ Database (yes, the DB is a server too)

**Why it matters:** every distributed system is a graph of clients and servers. A single service can be *both* — a server to the mobile app, and a client to the database.

### 1.2 Mental model

```
[Client]  --- request --->  [Server]
[Client]  <-- response ---  [Server]
```

Key properties:
- **Request/response is usually synchronous** from the client's POV — it waits.
- **Stateless by default** (esp. HTTP): each request stands alone; the server doesn't remember you between requests unless we add sessions/tokens.
- **Asymmetric**: client knows the server's address (URL/IP). Server does not know clients in advance.

### 1.3 The full request lifecycle (browser → server → back)

Take a simple case: user types `https://api.example.com/users/42` in a browser and hits Enter. What actually happens?

**The whole chain at a glance:**

```
URL parse → DNS → TCP handshake → TLS handshake → HTTP request
                                                       ↓
                                            LB → Proxy → Gateway → App
                                                       ↓
                                            Controller → Service → DB / Cache
                                                       ↓
HTTP response ← serialize ← business logic result
       ↓
Client renders / consumes
       ↓
Connection reuse (keep-alive)  OR  Teardown (FIN/ACK + TIME_WAIT)
```

Each arrow is a place latency is added and a place things can fail. The 9 steps below walk through each.

**Step 1 — URL parsing**
Browser breaks the URL into: scheme (`https`), host (`api.example.com`), port (implicit 443 for HTTPS), path (`/users/42`).

**Step 2 — DNS resolution**
Browser needs an IP for `api.example.com`. The lookup walks a hierarchy of caches, then queries authoritative servers if none has the answer.

```
Browser
   │  "api.example.com?"
   ▼
┌─────────────────┐   HIT → return IP
│ Browser cache   │───────────────────►
└────────┬────────┘
         │ MISS
         ▼
┌─────────────────┐   HIT → return IP
│ OS cache        │───────────────────►
└────────┬────────┘
         │ MISS
         ▼
┌──────────────────────────┐
│ Recursive resolver       │   (e.g. 8.8.8.8, ISP)
│                          │
│   ① Root NS  ── ".com?" ─┐
│                          │
│   ② .com TLD ── "example.com?" ─┐
│                                 │
│   ③ example.com NS ── "api?" ───┘
│                                 │
│   ← 203.0.113.10                │
└──────────┬───────────────────────┘
           ▼
     203.0.113.10  (cached for TTL seconds at every level)
```

**Key idea:** every level caches. That's why DNS is fast — but also why changes take TTL time to propagate.

**Step 3 — TCP connection (3-way handshake)**
Browser opens a TCP socket to `203.0.113.10:443`:
```
Client -> SYN         -> Server
Client <- SYN+ACK     <- Server
Client -> ACK         -> Server
```
Now there's a reliable byte pipe.

**Step 4 — TLS handshake (for HTTPS)**
Before any HTTP data flows, both sides agree on encryption keys.

```
Client                                           Server
  │                                                │
  │  ─── ClientHello ────────────────────────────► │  ciphers I support,
  │                                                │  TLS version, random
  │                                                │
  │  ◄─── ServerHello ──────────────────────────── │  chosen cipher,
  │                                                │  server random
  │                                                │
  │  ◄─── Certificate ───────────────────────────  │  server's public key
  │                                                │  (signed by CA)
  │                                                │
  │  ◄─── ServerKeyExchange (ECDHE params) ─────── │
  │                                                │
  │  ◄─── ServerHelloDone ───────────────────────  │
  │                                                │
  │  [ Client verifies cert chain against          │
  │    trusted CA store — else abort ]             │
  │                                                │
  │  ─── ClientKeyExchange ─────────────────────► │
  │  ─── ChangeCipherSpec ──────────────────────► │
  │  ─── Finished (encrypted) ──────────────────► │
  │                                                │
  │  ◄─── ChangeCipherSpec ─────────────────────  │
  │  ◄─── Finished (encrypted) ─────────────────  │
  │                                                │
  │  ═══ Symmetric session key established ═══════│
  │      All further data encrypted with it        │
```

**Cost:** TLS 1.2 = 2 RTTs. TLS 1.3 cuts it to 1 RTT (or 0-RTT on resumption).

**Step 5 — HTTP request**
Browser sends bytes like:
```
GET /users/42 HTTP/1.1
Host: api.example.com
Accept: application/json
Authorization: Bearer eyJhbGciOi...
User-Agent: Mozilla/5.0
```

**Step 6 — Server-side processing**
The request passes through several infrastructure layers before your business code sees it.

```
                    Incoming request (203.0.113.10:443)
                                │
                                ▼
                    ┌───────────────────────┐
                    │  Load Balancer        │  picks a backend
                    │  (L4 or L7)           │  (round-robin, least-conn)
                    └───────────┬───────────┘
                                │
                                ▼
                    ┌───────────────────────┐
                    │  Reverse Proxy        │  TLS termination,
                    │  (nginx / Envoy)      │  static routing, rate limit
                    └───────────┬───────────┘
                                │
                                ▼
                    ┌───────────────────────┐
                    │  API Gateway          │  auth, quota,
                    │                       │  request shaping
                    └───────────┬───────────┘
                                │
                                ▼
                    ┌───────────────────────┐
                    │  App Server           │  Spring Boot,
                    │  (embedded Tomcat)    │  worker thread pool
                    └───────────┬───────────┘
                                │
                                ▼
              ┌──────────────────────────────────┐
              │  Controller / Handler            │
              │    ① Parse request               │
              │    ② Auth check (JWT / session)  │
              │    ③ Call service layer          │
              │       ├─► Database  (client hop) │
              │       ├─► Cache     (client hop) │
              │       └─► Other svc (client hop) │
              │    ④ Build response              │
              └──────────────────────────────────┘
```

**Every downstream call is itself a client-server request** — it goes through DNS, TCP, TLS again (or reuses a pool). That's why service-to-service latency stacks up.

**Step 7 — HTTP response**
```
HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 47

{"id":42,"name":"Alice","email":"a@x.com"}
```

**Step 8 — Client renders / consumes**
Browser parses JSON, updates the UI. Mobile app deserializes into an object.

**Step 9 — Connection reuse or teardown**

This is the "what happens *after* the response" phase. Two possible paths:

**Path A — Reuse (keep-alive, the common case)**
- In HTTP/1.1, connections are keep-alive **by default** (`Connection: keep-alive`).
- The same TCP + TLS session is reused for the next request to the same host.
- Why it matters: skips the TCP 3-way handshake (~1 RTT) and TLS handshake (~1–2 RTT) on every subsequent request. Huge latency win.
- HTTP/2 goes further: **one TCP connection multiplexes many streams** in parallel over the same socket.
- HTTP/3 (QUIC) uses UDP, so there's no TCP handshake at all — connection setup is 0-RTT on resumption.
- Servers set an **idle timeout** (e.g., nginx default 75s). If no new request arrives, the connection is closed.

**Path B — Teardown (TCP 4-way FIN handshake)**
When either side decides to close (idle timeout, client done, server shutdown, `Connection: close` header):

```
Client -> FIN         -> Server    (I'm done sending)
Client <- ACK         <- Server    (got it)
Client <- FIN         <- Server    (I'm done too)
Client -> ACK         -> Server    (got it, bye)
```

Four packets instead of three because TCP is **full-duplex** — each direction must be closed independently. Either side can initiate.

**Post-close state: TIME_WAIT**
- The side that sent the first FIN enters `TIME_WAIT` (usually 60–120s on Linux, `2 × MSL`).
- Purpose: absorb any straggler packets so they don't confuse a *new* connection reusing the same port pair.
- **Interview gotcha:** high-throughput servers can run out of ephemeral ports if they open lots of short-lived outbound connections → `TIME_WAIT` exhaustion. Fixes: connection pooling, `SO_REUSEADDR`, or reuse via keep-alive.

**Java/Spring angle — connection pools ARE this optimization**
Every pool you use (HikariCP for JDBC, Apache HttpClient pool, OkHttp pool, Feign, WebClient) exists so your service doesn't pay TCP + TLS handshake cost per request. The pool holds keep-alive connections warm and hands them out. Tuning pool size = tuning how many keep-alive connections you hold to each downstream.

**Summary of what "teardown" means in practice**
- On the happy path (keep-alive), teardown is **deferred** — connections stay warm for many requests.
- Real teardown = FIN/ACK exchange + a TIME_WAIT cool-down window.
- Bad tuning here shows up as: high P99 latency (handshake per request), or "cannot assign requested address" errors (port exhaustion).

### 1.4 Java-flavored view (Spring Boot)

Since you're a Java dev, here's the same lifecycle mapped to a Spring Boot service:

```
TCP socket (accepted by embedded Tomcat)
   ↓
Tomcat connector — parses HTTP, hands off to a worker thread
   ↓
Servlet filter chain (Spring Security, CORS, logging)
   ↓
DispatcherServlet — routes by URL to a @Controller
   ↓
Argument resolvers (parse @PathVariable, @RequestBody)
   ↓
@Service — business logic
   ↓
@Repository / JPA — SQL to DB (another client-server hop!)
   ↓
Response built → serialized to JSON (Jackson) → written to socket
```

Each `@Autowired` dependency is often another client-server call in disguise.

### 1.5 Key facts / gotchas for interviews

- **HTTP is stateless.** State (login, cart) must be carried in cookies, tokens, or a server-side store keyed by a session ID.
- **DNS is cached everywhere.** Changes take TTL time to propagate. Never assume "new DNS = instant traffic shift."
- **TCP handshake = 1 RTT. TLS handshake = 1–2 extra RTTs.** HTTP/3 (QUIC) combines them to reduce this.
- **Connection reuse matters.** Opening a new TCP+TLS connection per request is expensive. That's why keep-alive and connection pools exist (e.g., HikariCP for JDBC, HTTP client pools).
- **Every hop can fail.** DNS, TCP, TLS, LB, app, DB — all separate failure domains. Design for it (timeouts, retries, circuit breakers).
- **Latency budget.** A "simple" GET is often 10+ network hops under the hood. Watch P99, not average.

### 1.6 The one-slide mental model

> A request is a **chain of client-server hops** with **stateful infrastructure** (DNS, TCP, TLS) wrapped around **stateless HTTP**. Every hop adds latency and a failure mode. Your job as a designer is to minimize hops on the hot path, cache what you can, and make each hop robust.

### 1.7 Common interview questions

1. "Walk me through what happens when I type a URL in the browser." → the 9 steps above.
2. "Why is HTTP stateless, and how do you keep users logged in?" → cookies / tokens / server-side sessions.
3. "What's the difference between a load balancer and a reverse proxy?" → LB picks a backend; reverse proxy also does TLS, caching, routing. Often the same box (nginx).
4. "How does DNS caching bite you during a failover?" → TTL delays; use short TTLs, health-checked DNS, or Anycast/GSLB.
5. "What's the cost of TLS?" → CPU for handshake + 1–2 RTT; mitigated by session resumption, HTTP/2 multiplexing, HTTP/3.

### 1.8 Communication patterns beyond request/response

Plain request/response is the default, but the client-server model has several variations depending on **who initiates** and **how the connection lives**.

**Pattern A — Request/Response (classic)**
```
Client ──req──► Server
Client ◄─res── Server
```
- ✅ Simple, cacheable, stateless. ❌ Server can't push.
- Use for: reads, writes, CRUD APIs, most REST endpoints.

**Pattern B — Short polling**
```
Client ──"any updates?"──► Server
Client ◄─── "no" ────────  Server
   (wait 5s)
Client ──"any updates?"──► Server
Client ◄─── "no" ────────  Server
   (wait 5s)
Client ──"any updates?"──► Server
Client ◄── "yes: X" ────  Server
```
- ✅ Trivial to implement. ❌ Wasteful, high latency (up to N seconds).
- Use for: cheap, low-frequency updates.

**Pattern C — Long polling**
```
Client ──"any updates?"────────────────────► Server
                                              │
                                     (server waits...
                                      holds connection open)
                                              │
Client ◄──────── "yes: X" ───────────────────  Server
Client ──"any updates?"────────────────────► Server (reconnects immediately)
```
- ✅ Near real-time over plain HTTP. ❌ Ties up server thread per client.

**Pattern D — Server-Sent Events (SSE)**
```
Client ──GET /events──► Server   (opens once)
Client ◄── data: {...} ── Server
Client ◄── data: {...} ── Server
Client ◄── data: {...} ── Server
        (single long-lived HTTP response)
```
- ✅ Server → client push, plain HTTP, auto-reconnect. ❌ One-way only.
- Use for: live scores, stock tickers, log tails, LLM token streaming.

**Pattern E — WebSockets**
```
Client ──GET /ws  Upgrade: websocket──► Server
Client ◄── 101 Switching Protocols ─── Server

  ═══════ full-duplex channel open ═══════
Client ──► frame ──► Server
Client ◄── frame ◄── Server
Client ──► frame ──► Server
Client ◄── frame ◄── Server   (both sides send anytime)
```
- ✅ True bidirectional, low overhead. ❌ Stateful → harder to LB / scale.
- Use for: chat, multiplayer games, collab editing, trading.

**Pattern F — Pub/Sub (async messaging)**
```
                        ┌──────────────┐
   Producer ──publish──►│              │──► Subscriber A
                        │  Broker      │──► Subscriber B
   Producer ──publish──►│  (topic:X)   │──► Subscriber C
                        └──────────────┘
```
- ✅ Producer/consumer decoupled in time and space. ❌ No direct reply, broker infra.
- Use for: event-driven microservices, order pipelines, analytics.

**Pattern G — gRPC streaming**
```
Unary:              Client ──req──► Server ──res──► Client
Server-stream:      Client ──req──► Server ──res──► ──res──► ──res──►
Client-stream:      Client ──req──► ──req──► ──req──► Server ──res──►
Bidirectional:      Client ◄──►◄──►◄──►◄──► Server  (multiplexed HTTP/2 streams)
```
Cleaner than raw WebSockets when both sides are your own services.

**Decision cheat sheet**

| Need | Use |
|---|---|
| Simple CRUD | Request/response (REST) |
| Client asks now and then | Short polling |
| "Real-time-ish" on legacy stack | Long polling |
| Server → client stream, one-way | SSE |
| Full-duplex, low-latency, bidirectional | WebSockets |
| Decouple producer/consumer, fan-out | Pub/Sub |
| Service-to-service streaming | gRPC streams |

**Java angle**
- Spring MVC: request/response + `SseEmitter` for SSE.
- Spring WebFlux: reactive streams, easy SSE + WebSocket.
- Spring `@KafkaListener` / RabbitMQ for pub/sub.
- gRPC via `grpc-spring-boot-starter`.

### 1.9 N-tier architecture — how we got here

The client-server model has **evolved through tiers**. Understanding the progression explains why modern systems look the way they do.

**1-tier (monolithic desktop)**
Everything on one machine: UI + logic + data (e.g., old MS Access, single-user apps).
- ✅ Simple.
- ❌ No sharing, no scale.

**2-tier (fat client + DB)**
Desktop app talks directly to a shared database over the network.
```
[Fat Client]  ←→  [Database]
```
- ✅ Multiple users share data.
- ❌ Business logic duplicated in every client. DB connections don't scale. Upgrades = re-install everywhere.

**3-tier (the classic web architecture)**
Introduce a middle tier.
```
[Client / Browser]  ←→  [App Server]  ←→  [Database]
   Presentation        Business logic      Data
```
- ✅ Thin client, logic centralized, DB isolated.
- ✅ Each tier scales independently.
- This is basically **every Spring Boot app** you've ever built.

**N-tier (modern microservices)**
Split further into many specialized tiers.
```
[Client]
  ↓
[CDN]
  ↓
[Load Balancer]
  ↓
[API Gateway]
  ↓
[Microservice A]  [Microservice B]  [Microservice C]
        ↓                ↓                ↓
     [Cache]         [Queue]         [Database]
                        ↓
                 [Async Worker]
                        ↓
                  [Data Warehouse]
```
- ✅ Each concern isolated: presentation, edge, routing, business, cache, data, analytics.
- ✅ Independent scaling, deployment, teams (Conway's Law).
- ❌ Operational complexity explodes. Latency budget spread across many hops. Debugging is distributed tracing territory.

**Why this matters in interviews**
When asked to "design X," you're really deciding **which tiers you need** and how they connect. Start 3-tier; add tiers (cache, queue, search, CDN) only when a specific requirement demands it. Don't hand-wave 15 boxes on the whiteboard without justifying each.

### 1.10 HTTP methods, status codes, idempotency

The client-server contract on HTTP is defined by **method + URL + status code**. Get this right and half your API design is done.

**Methods and their semantics**

| Method | Purpose | Safe? | Idempotent? | Body? |
|---|---|---|---|---|
| GET | Read | ✅ | ✅ | ❌ |
| HEAD | Read headers only | ✅ | ✅ | ❌ |
| OPTIONS | Discover capabilities (CORS) | ✅ | ✅ | ❌ |
| POST | Create / non-idempotent action | ❌ | ❌ | ✅ |
| PUT | Replace resource at URL | ❌ | ✅ | ✅ |
| PATCH | Partial update | ❌ | ⚠️ usually not | ✅ |
| DELETE | Remove | ❌ | ✅ | rare |

Definitions:
- **Safe** = read-only, no side effects. Servers may cache freely.
- **Idempotent** = calling it N times has the same effect as calling it once.

**Why idempotency matters (huge for retries)**
The network is unreliable. If your client times out on a POST, did the server process it or not? If POST is not idempotent, retrying might double-charge a customer.

Two standard fixes:
1. **Use PUT with a client-generated ID** (`PUT /orders/{uuid}`) — retry is safe.
2. **Idempotency key header** on POST (`Idempotency-Key: abc123`). Server dedupes by that key for a window (Stripe's pattern).

**Status code families**

| Family | Meaning | Examples |
|---|---|---|
| 1xx | Informational | 100 Continue, 101 Switching Protocols (WebSocket upgrade) |
| 2xx | Success | 200 OK, 201 Created, 202 Accepted (async), 204 No Content |
| 3xx | Redirection | 301 Moved Permanently, 302 Found, 304 Not Modified (cache) |
| 4xx | Client error | 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 409 Conflict, 429 Too Many Requests |
| 5xx | Server error | 500 Internal, 502 Bad Gateway, 503 Unavailable, 504 Gateway Timeout |

**Interview-critical distinctions**
- **401 vs 403** — 401 = "who are you?" (not authenticated), 403 = "I know you, no." (not authorized).
- **502 vs 503 vs 504** — 502 = upstream returned garbage, 503 = server itself down/overloaded, 504 = upstream timeout.
- **200 with `{"error": ...}` body is an anti-pattern** — use proper 4xx/5xx so infra (LBs, monitoring, retries) can react.
- **Retry logic:** only retry idempotent methods on 5xx / network errors, ideally with exponential backoff + jitter. Never blindly retry POST.

**Java angle**
- Spring: `@GetMapping`, `@PostMapping`, `ResponseEntity.status(HttpStatus.CREATED)`.
- Global handlers via `@ControllerAdvice` map exceptions to correct status codes.
- Feign / RestTemplate / WebClient — configure retry only for safe methods.

### 1.11 Statefulness workarounds

HTTP is stateless, but real apps need to know "who is this user, and what were they doing?" Three main strategies:

**A) Server-side sessions (traditional)**
Server stores state; client holds only a **session ID** in a cookie.

```
① Login
   Client ──POST /login──► Server
                             │ create session { userId:42, cart:[] }
                             │ store in memory / Redis under "abc123"
   Client ◄─Set-Cookie:      Server
          JSESSIONID=abc123

② Subsequent requests
   Client ──GET /cart────► Server
          Cookie:            │
          JSESSIONID=abc123  │ lookup("abc123") → {userId:42, cart:[...]}
   Client ◄── {cart:[...]}   Server
```

- ✅ Easy to invalidate (logout = delete session). ❌ Server has state → scale problem.

**Multi-instance session problem**

```
                       ┌─► App-1  (session abc123 here)
   Client ──► LB ──────┼─► App-2  ✗ doesn't know abc123
                       └─► App-3  ✗ doesn't know abc123
```

Two fixes:

```
Fix 1: Sticky sessions              Fix 2: Shared session store
                                    
         ┌─► App-1 ◄── you           ┌─► App-1 ─┐
LB ──────┼─► App-2                LB ┼─► App-2 ─┼─► [Redis]
         └─► App-3                    └─► App-3 ─┘
                                    
LB pins user to same node.          Any node serves any request.
Dead node = lost session.           Redis is now critical infra.
```

**B) Token-based (JWT & friends)**
Server issues a signed token. Client sends it on every request. Server just verifies the signature — no lookup.

```
① Login
   Client ──POST /login──► Server
                             │ sign JWT with server's secret key
                             │ payload = {userId:42, roles:[...], exp:...}
   Client ◄── JWT ─────────  Server

② Subsequent requests
   Client ──GET /cart────► Server
          Authorization:     │
          Bearer <jwt>       │ verify signature (no DB lookup!)
                             │ read claims from payload
   Client ◄── {cart:[...]}   Server


JWT structure:
   ┌──────────┐.┌──────────┐.┌───────────┐
   │  header  │ │ payload  │ │ signature │
   │ (algo)   │ │ (claims) │ │ (HMAC)    │
   └──────────┘ └──────────┘ └───────────┘
      base64       base64       base64
   
   Anyone can READ payload. Only server can FORGE signature.
```

- ✅ **Stateless server** — no lookup, just verify signature. Scales trivially.
- ✅ Works across services (microservices love this).
- ❌ Can't invalidate a token before its `exp` without extra infra (blocklist, short TTL + refresh tokens).
- ❌ Payload is visible (base64, not encrypted). Don't put secrets in it.

**Opaque tokens** — random string; server looks it up. Same benefits as sessions with token ergonomics.

**JWT vs opaque token vs session cheat sheet**

| | Sessions | JWT | Opaque token |
|---|---|---|---|
| Server state | Yes (or Redis) | No | Yes (lookup) |
| Revocation | Easy | Hard | Easy |
| Cross-service | Hard | Easy | Medium |
| Payload visible? | No | Yes (base64) | No |
| Best for | Monolith web apps | Microservices, mobile | OAuth-style APIs |

**C) Cookies deep-dive (browser world)**
- `HttpOnly` — JS can't read (blocks XSS token theft).
- `Secure` — HTTPS only.
- `SameSite=Lax/Strict/None` — CSRF defense.
- `Domain` / `Path` — scope.
- Missing these = classic security bugs.

**Java angle**
- `HttpSession` — old-school in-memory; combine with Spring Session + Redis for multi-instance.
- Spring Security supports both session and JWT flows (`OAuth2ResourceServer`, `jwtDecoder`).
- Refresh-token pattern: short-lived JWT (15 min) + long-lived refresh token stored server-side.

### 1.12 Server concurrency model — how one server handles many clients

The client-server model breaks down if the server can't handle concurrent load. Four generations of solutions:

**Gen 1 — Thread per connection**
```
   Conn 1 ──► [Thread 1]  ← alive whole connection
   Conn 2 ──► [Thread 2]
   Conn 3 ──► [Thread 3]
   ...
   Conn N ──► [Thread N]  ← ~1 MB stack each
```
- Simple. Blocking I/O is fine.
- ❌ ~10K conns = OOM. The **C10K problem**.

**Gen 2 — Thread pool + queue (classic Tomcat, Spring MVC default)**
```
                           ┌────────────────────┐
   Conn 1 ─┐               │  Worker pool       │
   Conn 2 ─┤   [Queue]     │   [T1] [T2] [T3]   │
   Conn 3 ─┼──►  ▓▓▓▓ ────►│   [T4] [T5] [T6]   │──► handle → return
   Conn 4 ─┤               │   ...  ~200 threads│
   Conn N ─┘               └────────────────────┘
```
- ✅ Bounded memory, predictable.
- ❌ Slow I/O → pool starves → queue grows → cascade failures.

**Gen 3 — Event loop / non-blocking I/O (Node, Netty, nginx)**
```
         ┌──────────────────────────────────────┐
         │        Event Loop (1 thread)         │
         │                                      │
   OS epoll/kqueue ──► "socket 42 ready"        │
         │                │                     │
         │                ▼                     │
         │     run handler ── register cb ──┐   │
         │                                  │   │
         │  ◄───── I/O completes ───────────┘   │
         └──────────────────────────────────────┘
   
   One thread juggles 100K+ connections.
```
- ✅ Massive concurrency. ❌ Callback hell. Any blocking call stalls the loop.

**Gen 4a — Reactive streams (WebFlux / Reactor)**
```
   request ──► Mono/Flux pipeline
                │
                ├─► map ──► filter ──► flatMap (async DB call)
                │                             │
                │                             ▼
                │                          [R2DBC]
                │                             │
                └────── response ◄────────────┘
   
   Same event-loop under the hood, functional API on top.
```
- ✅ Composable, scalable. ❌ Whole stack must be non-blocking (no JDBC → use R2DBC).

**Gen 4b — Virtual threads (Loom, Java 21+)**
```
   1 million virtual threads
      │  │  │  │  │  │  │  │  ...
      ▼  ▼  ▼  ▼  ▼  ▼  ▼  ▼
   ┌────────────────────────┐
   │  JVM scheduler         │  parks a VT when it blocks,
   │  (mount/unmount)       │  runs another on the same OS thread
   └──────┬─────────────────┘
          ▼
   Small OS thread pool (~cores)
```
- ✅ You write blocking code, get event-loop scalability. Retrofits Spring MVC.
- ❌ Synchronized pinning, thread-local pitfalls. Ecosystem catching up.

**Client-side matching concept**
- **Blocking client** (RestTemplate, JDBC): thread waits.
- **Async client** (CompletableFuture, WebClient, R2DBC): thread returns immediately, callback fires on completion.
- **Fire-and-forget** (publish to Kafka): no wait at all.

Choice of client model must match server model — mixing blocking JDBC into a WebFlux app kills its scalability.

**Cheat sheet — which model when?**

| Scenario | Best fit |
|---|---|
| CRUD API, moderate load, team knows Spring MVC | Thread pool (Tomcat) — good enough |
| Very high concurrent connections (chat, streaming, 100K+ sockets) | Event loop / WebFlux / Netty |
| High-throughput microservice with blocking dependencies | Virtual threads (Loom) |
| Long-lived push connections | WebSocket on event loop |

**Interview one-liner**
> "The server-side concurrency model determines how many concurrent clients you can support with fixed hardware. Thread-per-request is simple but hits the C10K wall; event loops and virtual threads move that ceiling to 100K+."

---

## 2. Networking — OSI, TCP/UDP, HTTP versions, WebSockets, gRPC

Section 1 treated the network as one big pipe. Now we open the pipe.

### 2.1 The layered model — OSI vs TCP/IP

Networking is built as **layers**, each doing one job and handing off to the next. Two models describe them:

- **OSI** — 7 layers, theoretical, useful for teaching / interviews.
- **TCP/IP** — 4 layers, what the internet actually implements.

**Side-by-side:**

```
   OSI (7 layers)              TCP/IP (4 layers)          Example
─────────────────────         ──────────────────         ─────────────────
7. Application       ┐                                    HTTP, DNS, SMTP,
6. Presentation      ├──►    Application                  gRPC, WebSocket,
5. Session           ┘                                    TLS (arguable)
─────────────────────         ──────────────────         ─────────────────
4. Transport                 Transport                    TCP, UDP, QUIC
─────────────────────         ──────────────────         ─────────────────
3. Network                   Internet                     IP, ICMP, routing
─────────────────────         ──────────────────         ─────────────────
2. Data Link         ┐                                    Ethernet, Wi-Fi,
1. Physical          ┘──►    Link                         MAC addresses,
                                                          cables/radio
```

**How to remember the OSI 7 (top→bottom):**
> **A**ll **P**eople **S**eem **T**o **N**eed **D**ata **P**rocessing
> (Application, Presentation, Session, Transport, Network, Data-link, Physical)

**What flows down the stack — encapsulation**

```
   Your JSON: {"id":42}
         │
         ▼
   ┌────────────────────────────────────────────┐  Application
   │ HTTP: GET /users/42\r\n...  {"id":42}       │
   └────────────────────────────────────────────┘
         │
         ▼
   ┌────────────────────────────────────────────┐  Transport
   │ TCP hdr (ports, seq, ack) │ HTTP payload   │
   └────────────────────────────────────────────┘
         │
         ▼
   ┌────────────────────────────────────────────┐  Network
   │ IP hdr (src IP, dst IP) │ TCP segment      │
   └────────────────────────────────────────────┘
         │
         ▼
   ┌────────────────────────────────────────────┐  Link
   │ Ethernet hdr (MACs) │ IP packet │ Ether tail│
   └────────────────────────────────────────────┘
         │
         ▼
       bits on the wire / radio
```

Each layer **wraps** the layer above with its own header. Receiver unwraps in reverse.

**Interview-usable one-liner per layer**

| Layer | Deals in | Question it answers |
|---|---|---|
| Application | Meaningful bytes (HTTP, JSON) | "What does the app want?" |
| Transport | Ports, reliability | "Which process on that host? Reliable or not?" |
| Network | IP addresses, routing | "Which host, via what path?" |
| Link | MAC addresses, frames | "Which cable/wifi hop next?" |

**Why layering matters for design**
- You can swap layers independently. TCP → QUIC didn't force you to change HTTP or your JSON.
- Failures at each layer look different (DNS ≠ TCP reset ≠ HTTP 500). Diagnose by layer.
- Security (TLS) slots between Transport and Application — it wraps HTTP without HTTP knowing.

---

### 2.2 TCP — reliable, ordered, stream-oriented

TCP guarantees: **every byte arrives, in order, exactly once**. On top of an unreliable IP layer that gives none of that. How?

**Big-picture mental model**

```
      Application writes:    "HELLO WORLD"
                                 │
                                 ▼
   ┌───────────────────────────────────────────────┐
   │  TCP:                                          │
   │   ① Break into segments                        │
   │   ② Number each byte (sequence numbers)        │
   │   ③ Send                                       │
   │   ④ Receiver ACKs what it got                  │
   │   ⑤ Retransmit if no ACK in time              │
   │   ⑥ Reorder at receiver by sequence number     │
   │   ⑦ Deliver to app as a byte stream            │
   └───────────────────────────────────────────────┘
```

**Connection lifecycle — you've seen this before**

```
   Setup (3-way handshake)      Teardown (4-way FIN)
   ────────────────────────    ─────────────────────
   Client ──SYN─────► Server   Client ──FIN────► Server
   Client ◄─SYN+ACK── Server   Client ◄─ACK───── Server
   Client ──ACK─────► Server   Client ◄─FIN───── Server
   ═══ connection open ═══      Client ──ACK────► Server
                                ═══ TIME_WAIT ═══
```

**Reliability mechanism — seq numbers + ACKs**

```
   Sender                                    Receiver
    │                                           │
    │──── seq=1, data="HELL" ──────────────►   │
    │                                           │
    │──── seq=5, data="O WO" ──────────────►   │   (arrives)
    │                                           │
    │──── seq=9, data="RLD" ───── LOST ✗       │
    │                                           │
    │  ◄──── ACK=9  (got everything up to 8) ──│
    │                                           │
    │   (timer expires, no ACK for seq=9)       │
    │──── seq=9, data="RLD" ──── RESEND ────►  │
    │                                           │
    │  ◄──── ACK=12 (got everything) ──────────│
```

**Flow control — sliding window (don't overwhelm slow receiver)**

Receiver advertises how much buffer space it has (`window size`). Sender never sends more unacked bytes than the window.

```
   Receiver's buffer (window = 8 KB)
   ┌──────────────────────────────────┐
   │████████████████░░░░░░░░░░░░░░░░│
   └──────────────────────────────────┘
     already read       free space
   
   Sender sees window=8KB → sends max 8KB unacked.
   Receiver empties buffer → window grows → sender sends more.
```

**Congestion control — don't overwhelm the network**

Separate from flow control. TCP probes the network:
- Start slow (`cwnd = 1 MSS`), double each RTT ("slow start").
- On packet loss, back off (halve `cwnd`).
- Algorithms: Reno, CUBIC (Linux default), BBR (Google, throughput-optimized).

```
   Congestion window over time
   
   cwnd ▲
        │              ╱╲          ╱╲
        │            ╱    ╲      ╱    ╲
        │          ╱   loss╲   ╱ loss  ╲
        │        ╱          ╲╱          ╲
        │      ╱                          ╲
        │    ╱  slow start                 ...
        │  ╱
        └─────────────────────────────────────► time
```

**What you actually get from TCP:**
- ✅ Reliable delivery, in-order, no duplicates
- ✅ Flow + congestion control
- ✅ Full-duplex (both sides send simultaneously)
- ❌ Higher latency (handshake, ACKs, retransmit waits)
- ❌ **Head-of-line blocking**: if one segment is lost, everything behind it waits

**Java angle**
- `java.net.Socket` = TCP client. `ServerSocket` = TCP server.
- Netty, JDBC drivers, JMS brokers — all TCP underneath.
- `SO_KEEPALIVE`, `TCP_NODELAY` (disable Nagle's algorithm for low-latency small messages) — knobs you tune.

---

### 2.3 UDP — fast, unreliable, message-oriented

UDP gives you: **send a datagram, hope it arrives.** No connection, no ordering, no retries.

**Mental model**

```
   Sender                            Receiver
    │                                   │
    │──── datagram A ──────────►       │   (arrives)
    │──── datagram B ──── LOST ✗       │
    │──── datagram C ──────────►       │   (arrives BEFORE A? maybe!)
    │──── datagram D ──────────►       │   (arrives, or maybe not)
    │                                   │
   No ACK. No retransmit. No order guarantee.
   Just fire packets and move on.
```

**TCP vs UDP head-to-head**

```
   Property           TCP                  UDP
   ─────────────────  ───────────────────  ───────────────────
   Connection         Yes (3-way HS)       No
   Reliability        Guaranteed           Best-effort
   Order              Guaranteed           No
   Duplicates         Removed              Possible
   Flow control       Yes                  No
   Congestion ctrl    Yes                  No (app must handle)
   Header size        20 bytes             8 bytes
   Latency            Higher               Lower
   Multicast/bcast    No                   Yes
   Use case           HTTP, SSH, DB        DNS, VoIP, games, video
```

**When UDP is the right choice**
- **DNS lookups** — tiny, want low latency, retry at app layer if needed.
- **Voice / video calls** — a dropped audio frame is better than a delayed one. Skip it, keep going.
- **Online games** — latest position matters more than an old missed one.
- **DHCP, NTP, SNMP** — small, one-shot, best-effort is fine.
- **Custom reliability** — QUIC builds its *own* reliability on UDP for better control than TCP allows.

**Interview one-liner**
> "TCP is a phone call; UDP is a postcard. Use TCP when you need every byte; UDP when latest-fast beats complete-slow."

---

### 2.4 HTTP evolution — 1.0 → 1.1 → 2 → 3

HTTP is the application-layer protocol on top of TCP (or QUIC for /3). Each version fixed the previous version's biggest pain.

**HTTP/1.0 (1996) — one request per connection**

```
   Request 1:  TCP handshake → send → recv → close
   Request 2:  TCP handshake → send → recv → close
   Request 3:  TCP handshake → send → recv → close
```
Terrible: every request paid full handshake cost.

**HTTP/1.1 (1997) — keep-alive + pipelining**

```
   Connection: keep-alive
   
   TCP handshake ──► req1 ──► res1 ──► req2 ──► res2 ──► req3 ──► res3
                    └─── same TCP connection, sequential ───┘
```

Big win: reuse connections. But **head-of-line blocking**: request 2 can't start replying until request 1 finishes. Browsers worked around this by opening 6 parallel TCP connections per host — wasteful.

**HTTP/2 (2015) — multiplexing over one connection**

```
   Single TCP connection, many parallel STREAMS:
   
       Stream 1: ──req──►                ◄──res──
       Stream 3:      ──req──►      ◄─res─
       Stream 5:            ──req──►             ◄─res─
       Stream 7:  ──req──►    ◄─res──
       
   Frames from different streams interleave on the wire.
```

Also: **header compression (HPACK)**, **server push** (mostly deprecated), binary framing (not text).

But: one lost TCP packet still stalls **all** streams (TCP-level head-of-line blocking).

**HTTP/3 (2022) — QUIC over UDP**

```
   TCP + TLS + HTTP/2 stack:          HTTP/3 stack:
   ┌────────────┐                     ┌────────────┐
   │  HTTP/2    │                     │  HTTP/3    │
   ├────────────┤                     ├────────────┤
   │  TLS 1.2/3 │                     │  QUIC      │   (TLS 1.3 baked in)
   ├────────────┤                     ├────────────┤
   │  TCP       │                     │  UDP       │
   └────────────┘                     └────────────┘
   
   Handshake: 2-3 RTTs                 Handshake: 1 RTT (0-RTT on resume)
   Loss stalls all streams             Loss stalls only that stream
```

QUIC = "TCP + TLS 1.3 reimagined on UDP." Independent streams, faster handshake, connection migration (survives IP change on mobile).

**Version comparison table**

| Feature | HTTP/1.1 | HTTP/2 | HTTP/3 |
|---|---|---|---|
| Transport | TCP | TCP | UDP (QUIC) |
| Multiplexing | ❌ (workaround: parallel TCP conns) | ✅ streams | ✅ streams |
| Header format | Text | Binary + HPACK | Binary + QPACK |
| HoL blocking | Yes (per connection) | Yes (TCP layer) | No |
| Handshake RTT | 1 (TCP) + 2 (TLS) | Same | 1 (0 on resume) |
| Server push | ❌ | ✅ (deprecated) | ✅ (rare) |
| Connection migration | ❌ | ❌ | ✅ (mobile-friendly) |

**Java angle**
- Java 11+ `HttpClient` supports HTTP/2 out of the box.
- Netty, Jetty, Undertow — HTTP/2 servers.
- HTTP/3 support still maturing (Netty incubator, Jetty modules).

---

### 2.5 WebSockets — full-duplex over one TCP connection

Already introduced in Section 1.8. Now the deep dive.

**Why they exist:** HTTP is request-driven. Server can't push to client without polling. WebSockets fix that.

**The upgrade dance**

```
   Client                                       Server
     │                                             │
     │─── GET /chat HTTP/1.1 ────────────────►    │
     │     Host: example.com                       │
     │     Upgrade: websocket                      │
     │     Connection: Upgrade                     │
     │     Sec-WebSocket-Key: dGhlIHNhbXBs...     │
     │     Sec-WebSocket-Version: 13               │
     │                                             │
     │◄── HTTP/1.1 101 Switching Protocols ────   │
     │     Upgrade: websocket                      │
     │     Connection: Upgrade                     │
     │     Sec-WebSocket-Accept: s3pPLMBiTx...    │
     │                                             │
     ═══════ Same TCP connection, now WS ═══════
     │                                             │
     │──► frame ("hi")                            │
     │                            frame ("hello") ◄│
     │──► frame (binary data)                     │
     │                             frame (ping)   ◄│
     │──► frame (pong)                            │
```

Steps:
1. Client sends an HTTP request with `Upgrade: websocket`.
2. Server accepts with `101 Switching Protocols`.
3. From now on, the same TCP socket carries WebSocket **frames** (small binary/text messages), both directions, anytime.

**Frame types**
- Text (UTF-8)
- Binary
- Ping / Pong (keep-alive)
- Close

**When to use vs alternatives**

```
   Need                                  Best fit
   ────────────────────────              ─────────────
   Server → client push only              SSE
   Occasional updates                     Long polling
   Bidirectional, low-latency, real-time  WebSocket
   Service-to-service streaming (backend) gRPC bidi stream
```

**Operational gotchas**
- **Stateful connection = load-balancer complexity.** LB must support WS (many L7 LBs do; L4 LBs pass-through fine).
- **Scaling horizontally:** which server holds Alice's connection? Need pub/sub between nodes (Redis, Kafka) to route messages.
- **Idle disconnects:** proxies and mobile carriers kill idle TCP. Send periodic pings.
- **Auth is one-shot:** you auth on the initial HTTP handshake. Token expiry mid-connection is your problem to handle.

**Java angle**
- Spring: `@ServerEndpoint` (JSR-356) or Spring's `WebSocketHandler` / STOMP over WS.
- Netty for raw WS at scale.
- Spring WebFlux has reactive WS support.

---

### 2.6 gRPC — RPC on HTTP/2 with Protobuf

gRPC = **G**oogle **R**emote **P**rocedure **C**all. Instead of "send an HTTP request to a URL," you **call a method** on a remote service like it's local.

**The stack**

```
   ┌──────────────────────────────────┐
   │  Your service code               │   generated from .proto
   ├──────────────────────────────────┤
   │  gRPC library                    │
   ├──────────────────────────────────┤
   │  Protobuf (binary serialization) │
   ├──────────────────────────────────┤
   │  HTTP/2 (multiplexed streams)    │
   ├──────────────────────────────────┤
   │  TCP + TLS                       │
   └──────────────────────────────────┘
```

**The workflow**

```
   ① Write .proto (contract)
   
      syntax = "proto3";
      service UserService {
        rpc GetUser (GetUserRequest) returns (User);
      }
      message GetUserRequest { int32 id = 1; }
      message User { int32 id = 1; string name = 2; }
   
   ② Compile → generates client + server stubs
   
      .proto ──[protoc]──► UserServiceGrpc.java  (server base)
                          UserServiceStub.java   (client)
                          User.java, GetUserRequest.java
   
   ③ Server implements the method
   ④ Client calls it like a local method
   
      User user = userStub.getUser(GetUserRequest.newBuilder().setId(42).build());
```

**4 call modes**

```
   Unary               Client ──req──► Server ──res──► Client
   
   Server streaming    Client ──req──► Server ══res══► Client (many)
   
   Client streaming    Client ══req══► Server ──res──► Client
                            (many)
   
   Bidirectional       Client ◄══════════════════════► Server
                       (independent read/write streams over HTTP/2)
```

**gRPC vs REST — when to pick which**

| Aspect | REST/JSON | gRPC |
|---|---|---|
| Payload | Text (JSON) | Binary (Protobuf) |
| Size / speed | Larger, slower parse | Smaller, faster |
| Contract | Loose (OpenAPI optional) | Strict (`.proto`) |
| Browser support | Native | Needs grpc-web proxy |
| Streaming | SSE / WebSocket bolt-on | First-class, 4 modes |
| Debuggability | curl-friendly | needs tools (grpcurl) |
| Versioning | URL / header | Protobuf field numbers |
| Best for | Public APIs, browsers | Internal service-to-service |

**Rule of thumb:**
- **External / public API** → REST + JSON.
- **Internal microservices at scale** → gRPC (smaller payload, strict contract, cheap streaming).

**Java angle**
- `grpc-java` official library.
- `grpc-spring-boot-starter` (community) auto-wires services.
- Proto files in `src/main/proto`, generated code from Maven/Gradle plugin.

---

### 2.7 Putting it all together — protocol decision tree

```
                    Need to move bytes between two processes
                                    │
                    ┌───────────────┴───────────────┐
                    │                               │
              Real-time                       Request/response
              streaming?                             │
                    │                    ┌──────────┴──────────┐
          ┌─────────┴────────┐           │                     │
          │                  │        Internal              Public /
       One-way            Bidirectional  service-to-svc     browser
       server→client         │              │                     │
          │                  │           gRPC                   REST
          │             WebSocket        (HTTP/2)              (HTTP/1.1 or 2)
          │             (browser)                             + JSON
          │             or gRPC bidi
          │             (backend)
          ▼
        SSE
```

**Interview one-liner for the whole section**
> "The stack is layered so each concern is swappable. TCP gives reliability at a latency cost; UDP trades it for speed. HTTP evolves to remove head-of-line blocking (1.1 → 2 → 3). WebSockets and gRPC bidi streams let servers push and enable true full-duplex over a single connection."

---

## 3. DNS, CDN, Anycast, TLS deep-dive

Section 2 was about *how* bytes move between two endpoints. Section 3 is about the **edge** — how clients find the right endpoint in the first place, how content is served close to them, and how the whole thing stays encrypted.

### 3.1 DNS — the phonebook of the internet

You saw a quick DNS diagram in Section 1.3. Now the full picture.

**Why DNS exists**
Humans want `netflix.com`. Machines want `52.85.132.10`. DNS translates.

**The hierarchy**

```
                         ┌──────────────┐
                         │   Root  "."  │   13 logical root servers (A–M)
                         └──────┬───────┘
                                │
              ┌─────────────────┼──────────────────┐
              ▼                 ▼                  ▼
         ┌────────┐        ┌────────┐         ┌────────┐
         │  .com  │        │  .org  │         │  .io   │    TLD servers
         └────┬───┘        └────────┘         └────────┘
              │
      ┌───────┴────────┐
      ▼                ▼
  ┌──────────┐    ┌──────────┐
  │netflix.  │    │example.  │   Authoritative NS
  │  com     │    │  com     │   (owned by the domain owner)
  └──────────┘    └──────────┘
```

**Full lookup with all the caches**

```
   Browser wants:  api.example.com
        │
        ▼
   ┌─────────────────┐
   │ Browser cache   │──HIT──► return IP  (fastest)
   └───────┬─────────┘
           │ MISS
           ▼
   ┌─────────────────┐
   │ OS resolver     │──HIT──► return IP
   │ /etc/hosts +    │
   │ system cache    │
   └───────┬─────────┘
           │ MISS
           ▼
   ┌───────────────────────────────────────────────┐
   │ Recursive resolver (ISP / 8.8.8.8 / 1.1.1.1)  │
   │                                                │
   │   Q: "api.example.com A?"                     │
   │                                                │
   │   ① Ask root  →  "ask .com NS at 192.5.6.30"  │
   │   ② Ask .com  →  "ask example.com NS at ..."  │
   │   ③ Ask example.com NS → "A 203.0.113.10"     │
   │                                                │
   │   Cache result for TTL seconds                 │
   └───────┬────────────────────────────────────────┘
           │
           ▼
      203.0.113.10  → returned & cached at every level
```

**DNS record types you must know**

| Type | Purpose | Example |
|---|---|---|
| **A** | Domain → IPv4 | `api.example.com → 203.0.113.10` |
| **AAAA** | Domain → IPv6 | `api.example.com → 2001:db8::1` |
| **CNAME** | Alias to another domain | `www.example.com → example.com` |
| **MX** | Mail server | `example.com → mail.example.com priority=10` |
| **TXT** | Arbitrary text (SPF, DKIM, verification) | `v=spf1 include:_spf...` |
| **NS** | Which servers are authoritative | `example.com → ns1.example.com` |
| **SOA** | Zone metadata (serial, refresh) | one per zone |
| **PTR** | Reverse: IP → domain | used for email reputation |
| **SRV** | Service location + port | Consul / SIP discovery |

**TTL — the cache lifetime knob**

```
   Response includes TTL (e.g., 300s)
   
   ┌────────────────────────────────────────────┐
   │  IP is cached at:                          │
   │    • Browser        (respects TTL, mostly) │
   │    • OS resolver    (respects TTL)         │
   │    • Recursive res  (respects TTL)         │
   └────────────────────────────────────────────┘
   
   Change DNS to point elsewhere?
   → Have to wait TTL seconds at *every* cache before all traffic shifts.
```

- **Low TTL (30–60s)** — fast failover, higher DNS query volume.
- **High TTL (1h+)** — cheap DNS, but slow failover.
- Typical for load-balanced services: **60s**. Prep for failover: drop to 30s hours before.

**DNS-level load balancing**
Return **multiple A records** and shuffle order. Clients typically use the first.

```
   dig api.example.com  →  203.0.113.10
                           203.0.113.11
                           203.0.113.12
```

Called **DNS round-robin**. Cheap, but no health checks — a dead IP keeps getting handed out until you update DNS.

**Smarter: GeoDNS / GSLB**
DNS server returns different IPs based on the resolver's location.

```
   Resolver in Mumbai   →  mumbai-lb.example.com  → 10.1.0.5
   Resolver in Frankfurt → eu-lb.example.com     → 10.2.0.5
   Resolver in Virginia  → us-lb.example.com     → 10.3.0.5
```

Route53, Cloudflare, NS1 all do this. Combined with health checks = automatic regional failover.

**Interview gotchas**
- **DNS caching bites you during migrations.** Assume TTL delays.
- **CNAME at apex is illegal** (`example.com` → alias). Use ALIAS/ANAME (provider extension) or flatten.
- **Split-horizon DNS** — same name resolves differently inside vs outside corp network.
- **DNS uses UDP** (port 53) for queries, **TCP** for large responses (>512B) or zone transfers.

**Java angle**
- JVM caches DNS results too — `networkaddress.cache.ttl` (default varies, historically forever with SecurityManager). Tune it for short-TTL failover.
- `InetAddress.getByName()` triggers the lookup.

---

### 3.2 CDN — content delivery network

**The problem**
User in Sydney fetches an image from a server in Virginia:
- ~200 ms one-way latency (speed of light + hops).
- Every user in Sydney does the same thing → wasteful, slow.

**The idea**
Cache static (and sometimes dynamic) content at **edge locations** near users.

```
   Without CDN:
   
      [User: Sydney] ────── 200ms ──────► [Origin: Virginia]
                       every request
   
   With CDN:
   
      [User: Sydney] ── 10ms ──► [Edge: Sydney PoP] ── 200ms ──► [Origin]
                                        │                     (only cache misses)
                              cached image served locally
                              to next Sydney user in 10ms
```

**How a request flows through a CDN**

```
   Client
     │  GET https://cdn.example.com/logo.png
     ▼
   ┌───────────────────────────────────────┐
   │  DNS returns nearest edge PoP IP       │  (Anycast or GeoDNS)
   └────────────┬──────────────────────────┘
                ▼
   ┌───────────────────────────────────────┐
   │  Edge server                          │
   │                                       │
   │   Cache HIT ──► serve to client       │  (< 20ms typical)
   │      │                                │
   │      └── log for analytics            │
   │                                       │
   │   Cache MISS ──► fetch from origin    │
   │      │           store, then serve    │
   │      ▼                                │
   │   Origin (your server / S3 bucket)    │
   └───────────────────────────────────────┘
```

**What CDNs cache**
- **Static assets** — images, JS, CSS, fonts, videos. Easy.
- **Dynamic HTML** — with short TTL + cache keys.
- **API responses** — with careful cache-control headers.

**Cache-control headers — the contract**

```
   Response headers:
     Cache-Control: public, max-age=86400, s-maxage=604800
                    │       │              │
                    │       │              └─ CDN caches 7 days
                    │       └───────────────── browser caches 1 day
                    └─────────────────────── anyone may cache
     
     ETag: "abc123"                    ← version stamp for revalidation
     Last-Modified: Wed, 15 Sep 2025... ← older revalidation
```

Client with a cached copy can revalidate:
```
   Client ──GET  If-None-Match: "abc123"──► CDN
   CDN    ◄── 304 Not Modified ──          (no body! just "still good")
```

**Cache invalidation — the hard part**
Two computer science hard problems: naming things, cache invalidation, and off-by-one errors.

Strategies:
1. **TTL expiry** — wait it out. Simple.
2. **Purge API** — CDN provider lets you invalidate a URL. Slow (~seconds to minutes globally).
3. **Cache-busting URLs** — `/logo.v42.png` or `/logo.png?v=42`. On deploy, filename changes → new cache entry, old one just ages out. Preferred pattern.

**CDN benefits recap**

| Benefit | Why |
|---|---|
| Lower latency | Edge is close to user |
| Reduced origin load | Most requests never reach origin |
| DDoS absorption | CDN has massive capacity |
| TLS termination at edge | Faster handshake near user |
| Global reach cheaply | You don't run servers in 200 cities |

**Big providers:** Cloudflare, AWS CloudFront, Fastly, Akamai, Google Cloud CDN.

**Java angle**
Not much on the app side — CDN is infra. But your Spring app must set proper `Cache-Control` / `ETag` headers on responses you want cached. Spring's `ResourceHttpRequestHandler` and `@GetMapping` support this natively.

---

### 3.3 Anycast — one IP, many locations

**The problem**
"Nearest edge" is nice — but how does the packet actually *reach* the nearest one?

**The idea**
Advertise the **same IP address** from multiple physical locations. The internet's routing protocol (BGP) naturally sends each user to the "closest" one.

```
   Anycast IP:  198.51.100.1  is announced from:
   
                    ┌──── PoP: Mumbai ────┐
                    │                     │
                    ├──── PoP: London ────┤
      Same IP  ─────┤                     │
     everywhere     ├──── PoP: Virginia ──┤
                    │                     │
                    └──── PoP: Sydney ────┘
   
   User in Delhi           ──packet──►  Mumbai PoP (shortest BGP path)
   User in Paris           ──packet──►  London PoP
   User in NYC             ──packet──►  Virginia PoP
   User in Melbourne       ──packet──►  Sydney PoP
```

**Unicast vs Anycast vs Multicast vs Broadcast**

```
   Unicast:   one sender ──► one receiver           (normal traffic)
   Anycast:   one sender ──► one of many receivers  (nearest wins)
   Multicast: one sender ──► group of receivers     (video groups, LAN)
   Broadcast: one sender ──► everyone on network    (LAN discovery)
```

**Where Anycast shines**
- **DNS root servers** — 13 logical, hundreds of physical, all Anycast.
- **CDN edge routing** — Cloudflare, Google DNS (`8.8.8.8`) — all Anycast.
- **DDoS mitigation** — attack traffic spreads across many PoPs instead of hammering one.

**Why "TCP over Anycast" used to be scary**
BGP re-routes could send subsequent packets to a *different* PoP mid-connection, breaking TCP state. Modern anycast networks stabilize routes long enough for TCP sessions; QUIC (HTTP/3) additionally supports connection migration.

**Interview one-liner**
> "Anycast is one IP announced from many locations; the internet's routing picks the nearest. It's how a single address like `1.1.1.1` serves the whole planet."

---

### 3.4 TLS / SSL deep-dive

You saw a TLS handshake diagram in Section 1.3. Now the concepts behind it.

**What TLS gives you (3 guarantees)**

```
   ┌─────────────────────────────────────────┐
   │ 1. Confidentiality — nobody can read it │
   │ 2. Integrity       — nobody can tamper  │
   │ 3. Authentication  — you're really      │
   │                       talking to X      │
   └─────────────────────────────────────────┘
```

**The two kinds of cryptography TLS uses**

```
   Asymmetric (slow, used for handshake)
   ─────────────────────────────────────
      Public key      Private key
         🔓              🔐
         │               │
      anyone           only server
      can encrypt      can decrypt
   
   Symmetric (fast, used for the session)
   ─────────────────────────────────────
      Shared session key 🔑
      Both sides encrypt AND decrypt with the same key.
```

**Why both?**
- Asymmetric is 100–1000× slower than symmetric.
- So: use asymmetric to **agree on a symmetric session key**, then use symmetric for all real data.

**The chain of trust — how you know the server is legit**

```
   ┌─────────────────────────────────────┐
   │  Root CA  (baked into your OS / JVM │
   │           / browser trust store)    │
   │  self-signed, trusted by fiat        │
   └───────────────┬─────────────────────┘
                   │ signs
                   ▼
   ┌─────────────────────────────────────┐
   │  Intermediate CA                    │
   └───────────────┬─────────────────────┘
                   │ signs
                   ▼
   ┌─────────────────────────────────────┐
   │  Server's certificate               │
   │   subject:  api.example.com         │
   │   pub key:  ...                     │
   │   valid:    2026-01 → 2027-01       │
   │   signature by intermediate         │
   └─────────────────────────────────────┘
   
   Verification walk:
   Client validates server cert → is it signed by an intermediate we can chain?
     → is that intermediate signed by a root in our trust store? → ✅
```

If the chain breaks, connection fails. That's the padlock icon in your browser.

**TLS 1.2 vs TLS 1.3 (interview-relevant)**

```
   TLS 1.2 handshake:  2 RTTs before app data
   ────────────────────────────────────────────
   Client ──ClientHello────────►
          ◄─ServerHello, Cert──
          ◄─KeyExchange, Done─
   Client ──KeyExchange────────►
          ──ChangeCipher, Fin──►
          ◄─ChangeCipher, Fin──
   Client ══app data══════════►     (2 RTTs consumed)
   
   TLS 1.3 handshake:  1 RTT (or 0-RTT resume)
   ────────────────────────────────────────────
   Client ──ClientHello + key share──►
          ◄─ServerHello + key share + Cert + Fin──
   Client ──Finished ══app data══════════►    (1 RTT)
```

TLS 1.3 also removes weak ciphers, forces forward secrecy, and adds **0-RTT resumption** (first byte on the wire for repeat visitors — with replay-attack caveats).

**Forward secrecy**
If a private key leaks *tomorrow*, past recorded sessions must remain unreadable. Achieved by using **ephemeral** Diffie-Hellman (ECDHE) to derive per-session keys — the long-lived server key never encrypts the data directly.

**Where TLS is terminated — architectural choices**

```
   Option A: TLS at the CDN / LB (most common)
   ────────────────────────────────────────
   Client ══TLS══► CDN/LB ──plain HTTP──► App
   
   ✅ Cheaper (fewer CPU-heavy handshakes on app tier)
   ✅ CDN can cache & inspect
   ❌ Internal traffic in cleartext — risky in shared VPCs
   
   Option B: End-to-end TLS
   ────────────────────────────────────────
   Client ══TLS══► CDN/LB ══TLS══► App
   
   ✅ Encrypted the whole way
   ❌ App handles handshakes, cert management
   
   Option C: mTLS (mutual TLS) — service mesh
   ────────────────────────────────────────
   Service A ═══TLS both sides authenticate═══► Service B
   
   Both sides present certs. Authenticates *services*, not just servers.
   Basis of zero-trust networking (Istio, Linkerd).
```

**Certificate lifecycle — the operational reality**
- Certs expire (90 days for Let's Encrypt, 1 year for paid).
- Auto-renewal (cert-manager, ACME clients) is essential — an expired cert = full outage.
- **Certificate transparency logs** — all issued certs are public. Monitor for rogue issuance for your domain.
- **OCSP / CRL** — how clients check if a cert was revoked before expiry.

**Interview-critical distinctions**
- **SSL vs TLS** — SSL is the old name (SSL 3.0 → TLS 1.0 → 1.1 → 1.2 → 1.3). "SSL cert" is a misnomer; it's a TLS cert.
- **Symmetric vs asymmetric** — used together, for different reasons (see above).
- **HTTPS = HTTP over TLS.** Same HTTP, wrapped in TLS.

**Java angle**
- JVM keeps a trust store: `$JAVA_HOME/lib/security/cacerts`. Add custom CAs here or via `javax.net.ssl.trustStore`.
- Key store holds *your* certs (server-side): `javax.net.ssl.keyStore`.
- `SSLContext`, `SSLSocketFactory` — low-level API. Higher up: `HttpsURLConnection`, Spring's `RestTemplate` / `WebClient` handle it transparently.
- Common bug: `PKIX path building failed` → missing/expired CA in trust store.

---

### 3.5 How DNS + CDN + Anycast + TLS combine — one request end-to-end

Let's stitch this section together with the full path of a real request.

```
   User in Tokyo types https://cdn.example.com/logo.png
     │
     ▼
   ① DNS lookup
       Browser → local cache (miss) → OS (miss) → resolver
       Resolver walks: root → .com → example.com NS
       Returns anycast IP  198.51.100.1
     │
     ▼
   ② Anycast routing
       Packet to 198.51.100.1 is BGP-routed to
       the nearest CDN PoP: Tokyo PoP
     │
     ▼
   ③ TLS 1.3 handshake with the edge (1 RTT, sub-10ms)
       Edge presents cert for cdn.example.com
       Client verifies chain against JVM/browser trust store
       Session key negotiated
     │
     ▼
   ④ HTTPS request over TLS
       GET /logo.png Host: cdn.example.com
     │
     ▼
   ⑤ Edge cache
       HIT  → return 200 OK image bytes  (~5ms total from Tokyo user)
       MISS → edge fetches from origin in Virginia (~200ms)
              caches it, then returns to user
              next Tokyo user gets HIT
     │
     ▼
   User sees the image blazingly fast.
```

**Key insight**
Each piece of this section (**DNS to find the front door, Anycast to route to the nearest one, TLS to secure the pipe, CDN to serve from cache**) shaves latency and shields the origin. Together they're why modern web apps feel fast globally with only a handful of origin servers.

---

## 4. Latency, Throughput, Percentiles & Back-of-Envelope Numbers

Every system design interview eventually asks: "how fast?" and "how much?" This section is the vocabulary and math to answer both.

### 4.1 Latency vs Throughput — two different questions

They sound similar. They're not.

```
   Latency    = time for ONE request to complete       (seconds / ms)
   Throughput = requests completed PER UNIT TIME       (req/s, MB/s)
```

**The highway analogy**

```
   Latency        = how long it takes YOU to drive from A to B
   Throughput     = how many CARS per hour pass a given point
   
   ─────────────────────────────────────────────────────────
   
   Wide highway, slow cars:
       ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
       Many cars pass/hour (high throughput)
       Each car takes forever (high latency)
   
   Narrow highway, fast cars:
       ─────────────► ─────────────►
       Few cars pass/hour (low throughput)
       Each car flies (low latency)
```

**They're not the same knob.** You can trade one for the other:
- Batching increases throughput but adds latency (wait to accumulate a batch).
- Caching reduces latency but doesn't help throughput on a cache miss.
- Async / queues let throughput scale but add queueing latency.

**Little's Law — the one formula to memorize**

```
   L = λ × W
   
   L = average # of requests in the system (concurrency)
   λ = arrival rate (throughput, req/s)
   W = average time in the system (latency, seconds)
```

Example: your API sees **1000 req/s** with **200ms average latency**.
→ At any instant, **200 concurrent requests** are in flight.
→ You need enough threads / connections / capacity to hold 200 in parallel.

This tiny equation drives thread pool sizing, queue sizing, capacity planning. Interview gold.

**Interview one-liner**
> "Latency is the time per request; throughput is requests per second. They're linked by Little's Law: concurrency = throughput × latency. Optimizing one doesn't automatically improve the other."

---

### 4.2 Percentiles — why averages lie

**The problem with averages**

```
   100 requests, latencies (ms):
   
   99 requests @ 10 ms
    1 request  @ 5000 ms  (something stalled — GC, cold cache, retry)
   
   Average = (99×10 + 5000) / 100 ≈ 59.9 ms   ← "looks fine!"
   
   Median  = 10 ms                            ← "great!"
   
   P99     = 5000 ms                          ← reality: 1% of users hate you
```

Averages hide the tail. Users don't experience averages — each user experiences **their own request**.

**What percentiles mean**

```
   Sort all latencies from fastest to slowest.
   
   P50 (median) = value where 50% of requests are ≤ this
   P95          = 95% of requests are ≤ this ; 5% are worse
   P99          = 99% of requests are ≤ this ; 1% are worse
   P99.9        = "three nines" — 0.1% worse. Amazon obsesses over these.
   P99.99       = "four nines"
```

**Visualized as a distribution**

```
   Request count
      ▲
      │      ▓▓▓
      │    ▓▓▓▓▓▓▓
      │   ▓▓▓▓▓▓▓▓▓
      │  ▓▓▓▓▓▓▓▓▓▓▓
      │  ▓▓▓▓▓▓▓▓▓▓▓▓       ← "the tail" — a long right side
      │  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
      │  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░
      └──┬────┬────┬─────────────────────┬────► latency
         P50  P90  P95                   P99
         10   30   50                    5000ms
```

**Why the tail matters even more in distributed systems**
Every service call goes through **multiple services**. If each has 1% chance of a slow tail, a request hitting 10 services has:
```
   P(all fast) = 0.99^10 ≈ 0.90
   P(at least one slow tail) ≈ 10%   
   
   → Your service's P99 becomes downstream services' P90!
```

This is why *reducing tail latency* matters far more than averages at scale. Google calls this "the tyranny of the tail."

**Common SLOs (Service Level Objectives) in the wild**

| System | Typical target |
|---|---|
| Interactive web app | P95 < 300ms, P99 < 1s |
| Public API (Stripe, Twilio) | P99 < 500ms |
| Real-time trading | P99 < 10ms, P99.9 < 50ms |
| Batch job | throughput matters, not latency |

**How to measure percentiles right**
- **Don't average percentiles!** P99 of your two datacenters is NOT (P99_a + P99_b)/2.
- Use a **histogram** (HDR Histogram, Prometheus `histogram_quantile`) that records buckets, not raw averages.
- Aggregate over meaningful windows (1 min, 5 min) — not 24h averages that smear over incidents.

**Java angle**
- Micrometer + Prometheus exports `http_server_requests_seconds_bucket` — use `histogram_quantile(0.99, ...)` in PromQL.
- `HdrHistogram` library for in-process P99/P99.9 tracking.
- Micrometer `@Timed` on Spring endpoints gives you percentiles for free.

**Interview one-liner**
> "Averages hide the tail. Track P95/P99/P99.9 because that's what real users experience during GC pauses, cold caches, and retries — and in a fan-out call graph, downstream tail latency dominates your own."

---

### 4.3 Numbers every engineer should know

Jeff Dean's famous list. Interviewers love checking that you know order-of-magnitude latencies — it drives every capacity estimate.

**The canonical table (rounded, ~2020s hardware)**

```
   Operation                                   Time         Rough scale
   ─────────────────────────────────────────  ──────────   ────────────
   L1 cache reference                          0.5 ns
   Branch mispredict                             5 ns
   L2 cache reference                            7 ns       14× L1
   Mutex lock/unlock                            25 ns
   Main memory reference                       100 ns       20× L2
   Compress 1KB with Zippy/Snappy             3,000 ns  = 3 µs
   Send 1 KB over 1 Gbps network             10,000 ns  = 10 µs
   Read 4 KB randomly from SSD              150,000 ns  = 150 µs
   Read 1 MB sequentially from memory       250,000 ns  = 250 µs
   Round trip within same datacenter        500,000 ns  = 500 µs = 0.5 ms
   Read 1 MB sequentially from SSD        1,000,000 ns  = 1 ms
   Disk seek (HDD)                       10,000,000 ns  = 10 ms
   Read 1 MB sequentially from disk       20,000,000 ns  = 20 ms
   Send packet CA → Netherlands → CA    150,000,000 ns  = 150 ms
```

**Same table, human-scaled (multiply everything by 1 billion)**

```
   If 1 ns = 1 second, then:
   
   L1 cache             0.5 s      ← "look at watch"
   L2 cache             7   s
   Main memory          100 s      ← ~2 minutes
   SSD read             ~2 days
   Datacenter RTT       ~6 days
   HDD seek             ~4 months
   California → NL      ~5 years   ← speed of light in glass
```

Makes it viscerally obvious why:
- Cache misses hurt.
- Cross-DC calls are the *last* thing you want on a hot path.
- CDN edges exist.

**Grouped by "what's it good for"**

```
   CPU / memory tier         ns scale       "instant"
   ─────────────────────────────────────────────────
   L1/L2 cache               < 10 ns
   Main memory                100 ns
   Local mutex                 25 ns
   
   Local I/O                 µs–ms          "quick"
   ─────────────────────────────────────────────────
   SSD read                  100 µs
   Same-DC RTT               0.5 ms
   
   Cross-machine / network   ms             "noticeable"
   ─────────────────────────────────────────────────
   HDD seek                    10 ms
   Cross-region RTT       50–150 ms
   
   Human-perceptible         100+ ms        "user notices"
   ─────────────────────────────────────────────────
   Frame at 60fps             16 ms
   "Feels instant" limit     100 ms
   "Feels responsive" limit  300 ms
   Give-up threshold        1000 ms
```

**Storage capacity + throughput (also useful)**

```
   Component            Sequential read       Random read
   ─────────────────    ─────────────────    ─────────────────
   L1 cache             ~1 TB/s               same
   Main memory (DDR5)   ~50 GB/s              ~10 GB/s
   NVMe SSD             ~7 GB/s               ~1 GB/s @ 4KB
   SATA SSD             ~500 MB/s             ~200 MB/s
   HDD                  ~150 MB/s             ~1 MB/s (seek-bound!)
   1 Gbps network       ~125 MB/s             —
   10 Gbps network      ~1.25 GB/s            —
```

**Bytes-per-time in one line:**
> "A gigabit-ethernet link moves ~125 MB/s. So 1 GB of data over gigabit = ~8 seconds. 1 TB over gigabit = ~2 hours."

---

### 4.4 Back-of-envelope estimation — how interviews use these

The Q that separates good candidates: "Design Twitter. Estimate storage." Here's the recipe.

**The recipe**

```
   ① State assumptions out loud (users, activity level, retention)
   ② Convert to per-second numbers (throughput)
   ③ Multiply by size to get bandwidth / storage
   ④ Apply write:read ratio & fan-out
   ⑤ Add safety margin (2–3×) — you don't want to plan for exactly p50
   ⑥ Cross-check with a known number ("that's ~1% of AWS S3, plausible")
```

**Handy powers of 10**

```
   1 KB  = 10³ bytes
   1 MB  = 10⁶
   1 GB  = 10⁹
   1 TB  = 10¹²
   1 PB  = 10¹⁵
   
   Seconds in a day    ≈ 86,400  ≈ 10⁵
   Seconds in a year   ≈ 3.15×10⁷ ≈ 3×10⁷
   Seconds in a month  ≈ 2.5×10⁶
```

**Worked example — "Design Twitter, estimate write load"**

```
   Assumptions:
     300 million MAU (monthly active users)
     50% post per day  → 150M tweets/day
     Peak = 2× average
   
   Step 1: tweets/sec average
     150M / 86,400s ≈ 1,700 tweets/sec
   
   Step 2: peak
     1,700 × 2 = 3,400 tweets/sec
   
   Step 3: storage per tweet
     ~300 bytes text + metadata
     150M/day × 300B = 45 GB/day
   
   Step 4: annualize
     45 GB × 365 ≈ 16 TB/year of tweet text
   
   Step 5: sanity check
     16 TB fits on one modern SSD.
     Twitter's real problem is fan-out (reads), not storage.
```

**Worked example — "How big a cache do I need for hot data?"**

```
   Given:
     10M users, each has a profile object ~2 KB
     80/20 rule: 20% of users produce 80% of traffic
   
   Hot set:
     2M users × 2 KB = 4 GB
   
   → A single Redis instance easily holds this in RAM.
```

**Worked example — read fan-out for a news feed**

```
   Given:
     User has 500 followers on average
     User posts 1 tweet/day → 1 write
     Each follower reads their feed 5×/day
     
   Write path (fan-out on write):
     1 tweet × 500 followers = 500 writes to feeds
   
   Read path (fan-out on read):
     Each of 500 followers × 5 reads/day = 2,500 reads
     Each read must merge posts from N people they follow
   
   → tells you whether to precompute feeds (write-heavy) or merge on read (read-heavy).
```

**Rules of thumb for interviews**

| Rule | Value |
|---|---|
| Read:Write ratio (typical social) | 100:1 to 1000:1 |
| DAU / MAU ratio | ~20% (rough) |
| Peak traffic vs average | 2–4× |
| Storage safety margin | 2–3× estimated |
| One machine handles | ~10K QPS (typical, varies wildly) |
| SSD IOPS (random 4K) | ~100K reads/s |
| Datacenter RTT | 0.5–1 ms |
| Cross-region RTT | 50–150 ms |
| Memory per commodity server | 64–512 GB |

**Interview one-liner**
> "Back-of-envelope isn't about being right to two decimal places. It's about landing within an order of magnitude and knowing which dimension dominates — storage, QPS, bandwidth, or fan-out."

---

### 4.5 Java angle — measuring latency in a Spring app

Bringing it home to your stack:

```
   ┌──────────────────────────────────────────┐
   │  Spring Boot app                         │
   │                                          │
   │  @RestController                          │
   │  @Timed(percentiles = {0.5, 0.95, 0.99}) │
   │  GET /users/{id}                          │
   │       │                                   │
   │       ▼                                   │
   │  Micrometer meter registry                │
   │       │                                   │
   │       ▼                                   │
   │  Prometheus scrape endpoint               │
   │       │                                   │
   └───────┼──────────────────────────────────┘
           ▼
   ┌──────────────────────────────────────────┐
   │  Prometheus                              │
   │                                          │
   │  histogram_quantile(0.99,                 │
   │    sum(rate(http_server_requests_        │
   │    seconds_bucket[5m])) by (le))         │
   └───────┬──────────────────────────────────┘
           ▼
   ┌──────────────────────────────────────────┐
   │  Grafana dashboards + alerts             │
   │  Alert: P99 > 500ms for 5 min → page     │
   └──────────────────────────────────────────┘
```

**Common Java latency killers**
- **GC pauses** — a stop-the-world pause spikes P99 while P50 stays fine. Use G1 / ZGC / Shenandoah for low-pause GC.
- **Thread pool exhaustion** — requests queue → wait time added to latency. Watch queue depth.
- **Cold connections** — first HTTPS call after idle repays handshake cost. Warm up connection pools.
- **N+1 queries** — JPA/Hibernate lazy loading. Use `@EntityGraph` or fetch joins.

---

## 5. CAP, PACELC, ACID vs BASE, Consistency Models

Once you replicate data across machines, you inherit a fundamental tradeoff: **you can't have perfect consistency, perfect availability, and perfect network reliability at the same time**. This section maps the tradeoffs.

### 5.1 CAP theorem — the three-way choice

**Definition (Eric Brewer, 2000)**
In a distributed system, you can only guarantee **two of three**:

```
                  ┌──────────────────┐
                  │  Consistency (C) │  Every read sees the latest write
                  │                  │  (or an error)
                  └────────┬─────────┘
                           │
                           │
     ┌─────────────────────┼─────────────────────┐
     │                     │                     │
     ▼                     ▼                     ▼
┌────────────┐    ┌─────────────────┐   ┌────────────────┐
│Availability│    │ Pick TWO — you  │   │  Partition     │
│  (A)       │    │ CANNOT have all │   │  Tolerance (P) │
│Every req   │    │      three.     │   │ System keeps   │
│gets a      │    │                 │   │ working when   │
│response    │    │                 │   │ network splits │
└────────────┘    └─────────────────┘   └────────────────┘
```

**The three definitions, precisely**
- **C** (Linearizability) — All nodes see the same value at the same time. A read after a successful write returns that write (or later).
- **A** (Availability) — Every request to a non-failed node gets a (non-error) response.
- **P** (Partition tolerance) — The system continues to operate despite arbitrary network message loss between nodes.

**The catch nobody tells you**

Partitions **will happen** in real distributed systems (cables cut, switches fail, VPCs glitch). So P isn't really optional. Real choice becomes:

> **When the network partitions, do you sacrifice C or A?**

```
   Network is fine (99% of the time)
      → You get all three. CAP doesn't force anything.
   
   Network partitions (1% of the time)
      → You must pick:
   
      ┌─────────────────────┐      ┌─────────────────────┐
      │  CP: Consistency    │      │  AP: Availability   │
      │      wins           │      │      wins           │
      │                     │      │                     │
      │  Refuse writes/reads│      │  Serve stale data,  │
      │  during partition   │      │  accept writes,     │
      │  Return errors      │      │  reconcile later    │
      │                     │      │                     │
      │  MongoDB (default), │      │  Cassandra, DynamoDB│
      │  HBase, ZooKeeper,  │      │  CouchDB, Riak      │
      │  Etcd, Consul       │      │                     │
      └─────────────────────┘      └─────────────────────┘
```

**The classic CP vs AP scenario**

```
   Two data centers, network cable cut between them.
   
   ┌─────── DC-1 ───────┐     ✗     ┌─────── DC-2 ───────┐
   │  Node A            │◄─────────►│  Node B            │
   │  (has value = 5)   │           │  (has value = 5)   │
   └────────────────────┘           └────────────────────┘
   
   Client writes to A: value = 10   Client reads from B: what to return?
   
   CP system:                       AP system:
     B refuses to answer               B returns the stale 5
     (or returns error)                (accepts new writes locally too)
     
     ✅ Never wrong                    ✅ Always available
     ❌ Unavailable                    ❌ Can be inconsistent, needs
                                          reconciliation later
```

**Rule of thumb**
- **Money, inventory, config, locks** → CP (better to error than be wrong).
- **Social feeds, shopping cart, DNS, product catalog** → AP (stale is fine, being down is not).

**Interview one-liner**
> "CAP forces a choice only during partitions. Partitions are unavoidable, so real systems decide upfront: do we refuse to serve (CP) or serve possibly-stale data (AP)?"

---

### 5.2 PACELC — the extension CAP missed

**The problem with CAP**
It only says what happens during partitions. But 99% of the time, there **is no partition**, and you still have a tradeoff — between **latency and consistency**.

**PACELC (Daniel Abadi, 2010)**

```
   IF Partition (P):
       choose Availability (A) OR Consistency (C)
   ELSE (normal operation):
       choose Latency (L) OR Consistency (C)
```

So every system is really "PA/EL" or "PC/EC" or a mix.

**Why the else-branch matters**
To be strongly consistent, replicas must coordinate on every write (2PC, quorum, consensus). That adds latency. Some systems trade consistency for low-latency reads even when the network is fine.

**Classifying real systems**

```
   System           P → A/C        E → L/C       Verdict
   ──────────────────────────────────────────────────────
   DynamoDB         PA             EL            Fast + always up, eventually consistent
   Cassandra        PA             EL            Same profile
   MongoDB          PC             EC            Consistent but slower reads
   HBase            PC             EC            Strict consistency
   PostgreSQL       PC             EC            (single-node, but replicated setups)
   Google Spanner   PC             EC            Global consistency via TrueTime
```

**Mental model**

```
   During partition:     During normal ops:
   
   ┌────────────┐        ┌────────────┐
   │            │        │            │
   │  A  or  C  │        │  L  or  C  │
   │            │        │            │
   └────────────┘        └────────────┘
   
   "What happens when things break"    "What happens when things are fine"
```

**Interview one-liner**
> "CAP tells you what happens during a partition. PACELC adds: even without a partition, you're still trading latency against consistency. Systems live on both axes."

---

### 5.3 ACID vs BASE — the two philosophies

Two design philosophies for how data systems handle correctness.

**ACID — the SQL tradition**

```
   ┌───────────────────────────────────────────────────┐
   │  A  Atomicity     — all-or-nothing transactions  │
   │  C  Consistency   — DB invariants preserved      │
   │  I  Isolation     — concurrent tx don't interfere│
   │  D  Durability    — committed data survives crash│
   └───────────────────────────────────────────────────┘
   
   Examples: PostgreSQL, MySQL, Oracle, SQL Server
```

**BASE — the NoSQL response**

```
   ┌───────────────────────────────────────────────────┐
   │  BA  Basically Available — always answers        │
   │  S   Soft state         — state may change       │
   │                            without input          │
   │  E   Eventual consistency — replicas converge     │
   │                             over time             │
   └───────────────────────────────────────────────────┘
   
   Examples: DynamoDB, Cassandra, Riak, CouchDB
```

**Head-to-head**

```
   ACID                             BASE
   ──────────────────────           ──────────────────────
   Strong consistency               Eventual consistency
   Transactions                     No cross-row txns (usually)
   Complex joins                    Denormalize, lookup by key
   Vertical scaling first           Horizontal scaling native
   Predictable but limited scale    Massive scale, weaker guarantees
   
   Analogy:                         Analogy:
   Bank ledger — must be correct    Facebook likes — approximate is fine
```

**The C in ACID vs the C in CAP — same word, different meanings**
- **ACID's C** — the DB won't violate declared invariants (e.g., no negative balance).
- **CAP's C** — all nodes agree on the current value at the same instant (linearizability).

Interviewers love this trap.

**Isolation levels (ACID's I, expanded)**

```
   Level                  Prevents                                    Ex.
   ─────────────────────  ───────────────────────────────────────    ─────
   READ UNCOMMITTED       nothing                                    rare
   READ COMMITTED         dirty reads                                Postgres default
   REPEATABLE READ        dirty reads + non-repeatable reads         MySQL InnoDB default
   SERIALIZABLE           all anomalies (fully sequential effect)    strictest

   Anomalies climbing the ladder:
     Dirty read:          see uncommitted data
     Non-repeatable read: same query returns different rows
     Phantom read:        same query returns new rows appearing
     Lost update:         two writes overlap; one lost
     Write skew:          each read sees consistent snapshot, but
                          combined writes violate an invariant
```

**Interview one-liner**
> "ACID promises transactional correctness at the cost of scale; BASE relaxes it for horizontal scale and availability. Modern systems often mix: strong consistency for the hot core (payments), eventual for the periphery (feeds)."

---

### 5.4 Consistency models — the full spectrum

CAP paints it as "consistent vs not." Reality is a **spectrum**, from strictest to loosest:

```
   ▲ STRICTER (more guarantees, more coordination cost)
   │
   │  Linearizability          "atomic — instantaneous global order"
   │  Sequential consistency   "one global order, respect program order"
   │  Causal consistency       "causally-related writes seen in order"
   │  Read-your-writes         "you see your own writes"
   │  Monotonic reads          "reads don't go backward in time"
   │  Monotonic writes         "your writes are applied in order"
   │  Eventual consistency     "all replicas converge...eventually"
   │
   ▼ LOOSER (less coordination, cheaper, more surprising)
```

Let's walk each with a diagram.

**Linearizability (strongest)**
Every operation appears to take effect **instantaneously** at some point between its start and end. Global real-time order.

```
   Real-time timeline:
                                              
    Client A ─── write(x=1) ────────┤              
                                    │              
    Client B                        ├── read(x) ──► must return 1
                                                    (write "happened" first
                                                     in real time)
```

Cost: needs consensus (Raft, Paxos) for every write. High latency. Examples: **Etcd, ZooKeeper**.

**Sequential consistency (Lamport, 1979)**
There's *some* single global order, and each client's operations appear in program order — but not necessarily real-time order.

```
    Client A: write(x=1) → write(x=2)
    Client B: read(x) → read(x)
    
    Valid history: A1 → A2 → B1 → B2  (both reads see 2)
    Valid history: A1 → B1 → A2 → B2  (B sees 1, then 2)
    
    Not required: real-time ordering across clients.
```

Examples: Java's `volatile` + happens-before model.

**Causal consistency**
If write X **causes** write Y (X happens-before Y), everyone sees X before Y. Independent writes may be seen in any order.

```
    Alice posts:  "I'm engaged!"           (event X)
    Bob replies:  "Congrats!"              (event Y, causally after X)
    Carol posts:  "I baked a cake"         (event Z, independent)
    
    Rule: nobody sees Y ("Congrats!") without X ("I'm engaged!")
          Z can appear before or after either — it's unrelated.
    
    Wrong ordering:                Right ordering:
    "Congrats!" → "I'm engaged"    "I'm engaged" → "Congrats!"
    (nonsensical to reader)        (makes sense)
```

Enough for social feeds and comments. Cheaper than linearizability. Examples: **Riak, MongoDB (with causal sessions)**.

**Read-your-writes**
After you write, *you* see it. Others may not yet.

```
    Client A → write(profile = "new bio")
    Client A → read(profile)                     ← must return "new bio"
    Client B → read(profile)                     ← may return old value
```

Common pattern: after Facebook posts, YOU see it immediately; your friends see it eventually.

Implementation trick: after writing, route your subsequent reads to the same replica (session stickiness) or the primary.

**Monotonic reads**
If you've seen a value, later reads won't return an older value.

```
    Client A → read(counter) = 5
    Client A → read(counter) = 4          ← VIOLATION (time went backward)
    Client A → read(counter) = 5 or 6+    ← OK
```

Prevents the "F5 flip-flop" where refreshing shows new data then old data then new again — usually because reads hit different replicas.

**Monotonic writes**
Your own writes are applied in the order you issued them.

```
    Client A → write(x=1)
    Client A → write(x=2)
    
    Final state: x=2   ✅
    Final state: x=1   ❌ (out of order — violation)
```

**Eventual consistency (weakest)**
If writes stop, all replicas eventually converge to the same state. No timing guarantee.

```
    t=0   A writes: x = "hello"
    t=1   Replicas: [hello, old, old, old]
    t=2   Replicas: [hello, hello, old, old]
    t=3   Replicas: [hello, hello, hello, old]
    t=4   Replicas: [hello, hello, hello, hello]   ← converged
    
    During t=1..3, reads may return "old" or "hello" depending on replica.
```

Examples: **DNS, Cassandra, DynamoDB, S3 (until 2020)**.

**The stack, visualized**

```
                              Coordination cost
                                     ▲
    Linearizability ────────────────►│ very high
    Sequential      ────────────►    │
    Causal          ─────────►       │
    Read-your-writes────►            │
    Monotonic reads ──►              │
    Monotonic writes─►               │
    Eventual        →                │ minimal
                                     │
                                     ▼
                                Latency cost
```

**Choosing a model — quick rules**

```
   Data                          Pick this
   ─────────────────────────    ─────────────────────────
   Bank balance, locks           Linearizability
   Distributed config            Linearizability (Etcd)
   Social timelines              Causal + read-your-writes
   Your own profile edits        Read-your-writes
   Product catalog               Eventual (with cache invalidation)
   DNS                           Eventual (TTL-based)
```

**Interview one-liner**
> "Consistency isn't binary. There's a spectrum from linearizability (expensive, safest) down to eventual (cheap, surprises possible). Match the model to the data: money = strong, feeds = causal, catalog = eventual."

---

### 5.5 Putting it together — a design lens

When designing any system that stores replicated data, walk through these four questions:

```
   ① What happens during a network partition?    → CAP: CP or AP?
   ② What happens when the network is fine?      → PACELC: EL or EC?
   ③ Do we need transactions?                    → ACID or BASE?
   ④ What consistency guarantee per data type?   → Model per bucket
```

**A hybrid real-world example — an e-commerce app**

```
   ┌─────────────────────────────────────────────────────────────┐
   │  Data bucket        Consistency model     Storage           │
   │  ──────────────────────────────────────────────────────     │
   │  User accounts      Strong (linearizable) PostgreSQL        │
   │  Order + payment    ACID transactions     PostgreSQL        │
   │  Inventory count    Strong (per-item)     PostgreSQL / Redis│
   │  Product catalog    Eventual              DynamoDB + CDN    │
   │  Shopping cart      Read-your-writes      Redis (sticky)    │
   │  Recommendations    Eventual              Cassandra         │
   │  Search index       Eventual              Elasticsearch     │
   │  Session state      Read-your-writes      Redis             │
   └─────────────────────────────────────────────────────────────┘
   
   One app, many consistency models — matched to the data.
```

**Java angle**
- **JPA / Hibernate** → give you ACID via the underlying DB.
- **Spring Data JPA** `@Transactional` → wraps ACID transactions.
- **Spring Data Redis** → typically eventual / read-your-writes.
- **Cassandra driver** — you pick per-query consistency: `ONE`, `QUORUM`, `ALL`, `LOCAL_QUORUM`.
- **Distributed transactions** across services (2PC/XA) → avoid unless needed; prefer Saga pattern (covered later in Section 6+).

---

## 6. Scaling Building Blocks

Section 5 was about *correctness* under scale. Section 6 is about the *mechanics* — the infrastructure pieces you compose to actually handle more traffic.

### 6.1 Vertical vs Horizontal scaling

Two fundamentally different ways to handle more load.

```
   Vertical scaling ("scale up")           Horizontal scaling ("scale out")
   ────────────────────────────────       ─────────────────────────────────
   
   ┌─────────────────┐                     ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐
   │                 │                     │      │ │      │ │      │ │      │
   │   BIGGER BOX    │                     │ box  │ │ box  │ │ box  │ │ box  │
   │                 │                     │      │ │      │ │      │ │      │
   │  more CPU/RAM   │                     └──────┘ └──────┘ └──────┘ └──────┘
   │  more disk      │                     
   │                 │                     Many small boxes behind
   │                 │                     a load balancer
   └─────────────────┘                     
```

**Head-to-head**

| Property | Vertical | Horizontal |
|---|---|---|
| How | Buy bigger server | Add more servers |
| Ceiling | Hardware limits (~2 TB RAM, ~200 cores) | Effectively unlimited |
| Complexity | Simple (same code) | High (distributed problems) |
| Cost curve | Exponential (top-tier HW is pricey) | Linear-ish |
| Downtime for scale | Often (replace machine) | Zero (add / remove nodes) |
| Fault tolerance | Single point of failure | Redundancy built-in |
| Best for | DBs, stateful services, quick wins | Stateless web/app tier |

**Rule of thumb**
- **Start vertical.** Simpler. Moore's law used to make this free; less so now, but a bigger box still buys you time.
- **Scale horizontally when:** state is small or externalized, load is elastic, and hitting HW ceilings.
- **Databases:** vertical first, then read replicas, then shard (horizontal).

**Interview one-liner**
> "Scale up until you can't. Then scale out. Vertical is simpler; horizontal is unbounded but forces you to solve distributed-systems problems."

---

### 6.2 Load Balancers — L4 vs L7

A **load balancer (LB)** distributes incoming requests across a pool of backend servers.

```
                    ┌────────────────┐
                    │  Load Balancer │
                    └────────┬───────┘
                             │
             ┌───────────────┼───────────────┐
             ▼               ▼               ▼
        ┌────────┐      ┌────────┐      ┌────────┐
        │Backend │      │Backend │      │Backend │
        │   1    │      │   2    │      │   3    │
        └────────┘      └────────┘      └────────┘
```

Two flavors depending on which OSI layer they operate at.

**L4 (Transport layer) — dumb but fast**

```
   Client ──TCP/UDP──► L4 LB ──TCP/UDP──► Backend
                        │
                        └─ Forwards packets by IP + port
                           Doesn't parse the payload
```

- Sees: source/dest IP, ports, TCP/UDP flags.
- Doesn't see: URL, headers, cookies, body.
- Very fast (kernel-level, sometimes hardware).
- Common: AWS NLB, HAProxy (TCP mode), IPVS.

**L7 (Application layer) — smart, fully parses HTTP**

```
   Client ──HTTP──► L7 LB ──HTTP──► Backend
                     │
                     ├─ Parses URL: route by path
                     ├─ Reads headers: route by host / auth
                     ├─ Handles TLS termination
                     ├─ Rewrites, retries, rate-limits
                     └─ Caches responses
```

- Sees: full HTTP request — URL, method, headers, cookies, sometimes body.
- Slower per request (CPU-bound TLS + HTTP parsing) but way more flexible.
- Common: NGINX, HAProxy (HTTP mode), Envoy, AWS ALB, Traefik.

**L4 vs L7 comparison**

| Aspect | L4 | L7 |
|---|---|---|
| OSI layer | Transport (TCP/UDP) | Application (HTTP/gRPC) |
| Routes by | IP + port | URL, host, headers |
| TLS termination | Passthrough | Terminates |
| Sticky sessions | By source IP | By cookie |
| Latency | Lower | Higher |
| Content-based routing | ❌ | ✅ |
| Path/host routing | ❌ | ✅ |
| WebSocket support | ✅ (opaque bytes) | ✅ (upgrade-aware) |
| Use for | High-throughput TCP, DBs, custom protocols | Web / API traffic |

**Balancing algorithms**

```
   Round Robin
   ──────────────────
   Request 1 → Backend A
   Request 2 → Backend B
   Request 3 → Backend C
   Request 4 → Backend A          Simple. Ignores backend load.
   
   Weighted Round Robin
   ──────────────────
   A weight=3, B weight=1
   → A, A, A, B, A, A, A, B, ...   Bigger boxes get more.
   
   Least Connections
   ──────────────────
   Track active connections per backend, pick lowest.
   Great when request duration varies widely.
   
   Least Response Time
   ──────────────────
   Track recent P99 per backend, pick fastest.
   Auto-avoids sick nodes.
   
   IP Hash / Source Hash
   ──────────────────
   hash(client_IP) % N → same client → same backend.
   Poor-man's sticky session. Rebalances badly on N change.
   
   Consistent Hashing
   ──────────────────
   Nodes and keys on a hash ring. Adding/removing a node
   moves only ~1/N of keys. Used by Cassandra, DynamoDB,
   Envoy for cache-affinity routing.
```

**Consistent hashing — visualized**

```
                   0
               ┌───┴───┐
             ▓ │       │ ▓        Nodes hashed onto ring
       (Node A)│       │(Node B)
               │       │          Each key hashes to a point.
               │       │          Walk clockwise → first node = owner.
               │       │
               │       │          Add Node D between A and B?
               │       │          Only keys A→D move; rest untouched.
               │       │
       (Node C)│       │
             ▓ │       │
               └───────┘
                  180
```

**Sticky sessions (session affinity)**

```
   Problem: users need to hit the SAME backend
            (in-memory session, WS connection, etc.)
   
   L4:  Route by source IP hash — fragile behind NAT/mobile
   L7:  Route by cookie — LB injects a "backend id" cookie
   
   Downside: uneven load; dead backend = lost sessions.
   Better: externalize state (Redis) → any backend serves any user.
```

**Health checks — quiet-but-critical**

```
   LB probes each backend periodically:
      HTTP GET /health → 200 OK?  keep in pool
      No response / non-200?      remove from pool
   
   Two-level check:
     Liveness  → "am I alive?" (restart if fail)
     Readiness → "am I ready for traffic?" (remove from LB if fail)
```

**Java angle**
- Spring Boot Actuator exposes `/actuator/health` — feed this to your LB.
- Kubernetes uses liveness / readiness probes → integrated into service networking.
- Spring Cloud LoadBalancer for client-side LB in service-to-service calls.

---

### 6.3 Reverse Proxy — the workhorse in front of your app

**Forward proxy vs reverse proxy**

```
   Forward proxy               Reverse proxy
   ─────────────               ─────────────
   
   Client ──► Proxy ──► Any    Client ──► Proxy ──► Your servers
   
   Client says "get X for me". Client thinks it's talking to
   Proxy sits on client side.  the real server.
                                Proxy sits on server side.
   
   Example: corporate outbound Example: nginx, Envoy in front
   filter, Squid                of your app cluster.
```

**What a reverse proxy does (in one picture)**

```
   ┌──────────────────────────────────────────┐
   │  Reverse Proxy (nginx / Envoy / HAProxy) │
   │                                          │
   │  ① TLS termination                       │
   │  ② HTTP parsing                          │
   │  ③ Routing (path, host, header)          │
   │  ④ Load balancing (L7)                   │
   │  ⑤ Response caching                      │
   │  ⑥ Compression (gzip, brotli)            │
   │  ⑦ Rate limiting                         │
   │  ⑧ Request rewriting                     │
   │  ⑨ Auth (basic, JWT check)               │
   │  ⑩ Logging & access control              │
   └────────────────┬─────────────────────────┘
                    │
        plain HTTP  ▼   (internal, unencrypted or mTLS)
              ┌──────────┐
              │  Backend │
              │  cluster │
              └──────────┘
```

**Reverse proxy vs L7 Load Balancer**
Honestly? They overlap. NGINX is both. Envoy is both. The distinction:

- **LB** emphasizes distribution across backends.
- **Reverse proxy** emphasizes the front-door role: TLS, caching, security.

In practice, one box does both jobs.

**Why put it in front of your app?**
- Your app doesn't need to speak TLS.
- Your app doesn't need to handle 100K idle connections (proxy holds them).
- Your app doesn't need to handle slowloris DoS (proxy buffers).
- Your app can scale/upgrade behind a stable public IP.

**Java angle**
- Spring Boot in production: **almost always** behind nginx / Envoy / ALB.
- Configure Spring to trust `X-Forwarded-For` / `X-Forwarded-Proto` (`server.forward-headers-strategy=native`) so it sees the real client IP.

---

### 6.4 API Gateway — the smart edge for microservices

An **API gateway** is a reverse proxy specialized for API traffic in microservice architectures.

```
                          Public internet
                                 │
                                 ▼
                    ┌────────────────────────┐
                    │      API Gateway       │
                    │                        │
                    │  ▸ AuthN / AuthZ       │
                    │  ▸ Rate limiting       │
                    │  ▸ Request throttling  │
                    │  ▸ Path → service      │
                    │  ▸ API versioning       │
                    │  ▸ Response aggregation│
                    │  ▸ Protocol translation│
                    │    (REST ↔ gRPC)       │
                    │  ▸ Metrics / logging   │
                    │  ▸ Circuit breaker     │
                    │  ▸ Caching             │
                    └───────┬────────────────┘
                            │
        ┌───────────────────┼────────────────────┐
        ▼                   ▼                    ▼
   ┌──────────┐        ┌──────────┐        ┌──────────┐
   │ Users    │        │ Orders   │        │ Catalog  │
   │ service  │        │ service  │        │ service  │
   └──────────┘        └──────────┘        └──────────┘
```

**Why gateway ≠ plain reverse proxy**
Beyond L7 features, a gateway is *API-aware*:

- **Aggregation:** one client call fans out to multiple services and merges responses. Kills chatty mobile clients.
- **Protocol translation:** browser speaks REST/JSON; internal services speak gRPC/protobuf.
- **API contracts:** OpenAPI-driven routing, request validation, mock responses.
- **Consumer-scoped quotas:** free tier vs paid tier vs internal.
- **Developer portal:** self-service API keys, docs, usage dashboards.

**BFF pattern — Backend For Frontend**

```
                Mobile client
                     │
                     ▼
         ┌────────────────────┐
         │  Mobile BFF        │  ← tailored for mobile:
         │  (own API gateway) │    fewer fields, aggregated
         └─────────┬──────────┘
                   │
                   ▼
       ┌──────┬─────────┬──────┐
       │ Svc1 │  Svc2   │ Svc3 │
       └──────┴─────────┴──────┘
                   ▲
                   │
         ┌─────────┴──────────┐
         │  Web BFF           │  ← tailored for web:
         │  (own API gateway) │    richer fields, different auth
         └────────────────────┘
                     ▲
                     │
                 Web client
```

One gateway per client type → each client gets an API that's exactly what it needs.

**Common gateway products**
- Cloud: AWS API Gateway, Azure APIM, GCP Apigee.
- Self-hosted: Kong, Tyk, KrakenD, Spring Cloud Gateway, Zuul.
- Envoy-based: Ambassador, Contour.

**When you DO NOT need one**
Small monolith with one public API. A plain nginx is enough. Adopt a gateway when you have 5+ services or external API consumers with quotas.

**Java angle**
- **Spring Cloud Gateway** (reactive, Netty-based) — modern, actively developed.
- **Zuul** (Netflix) — older, servlet-based, largely deprecated.

---

### 6.5 Service Mesh — the sidecar era

**The problem gateways don't solve**
API gateways handle north-south traffic (external → internal). But microservices also talk **east-west** (service → service). Every service duplicates the same cross-cutting logic: retries, timeouts, TLS, circuit breakers, tracing.

**The service mesh idea**
Move all that logic out of the app and into a **sidecar proxy** that runs beside every service.

```
   Without mesh:
   
       ┌──────────────────┐
       │  Service A       │
       │  (retry, TLS,    │──────► Service B
       │   metrics, LB,   │
       │   breaker code   │
       │   in every app)  │
       └──────────────────┘
   
   With mesh:
   
       ┌────────────┐   ┌──────────┐          ┌──────────┐   ┌────────────┐
       │            │   │ Sidecar  │          │ Sidecar  │   │            │
       │  Service A │──►│  proxy   │─────────►│  proxy   │──►│  Service B │
       │  (plain    │   │ (Envoy)  │          │ (Envoy)  │   │  (plain    │
       │   code)    │   │          │          │          │   │   code)    │
       └────────────┘   └──────────┘          └──────────┘   └────────────┘
                             ▲                      ▲
                             │                      │
                             └────── Control ───────┘
                                     Plane (Istio,
                                     Linkerd)
```

**Two planes**

```
   Control plane     ← operator writes policy (routing, mTLS, retries)
        │
        │  push config
        ▼
   Data plane        ← sidecar proxies enforce it on every request
```

**What the mesh gives you (for free, per service)**
- **mTLS everywhere** — automatic cert issue & rotation between services.
- **Traffic splitting** — canary, blue/green, weighted (90/10 → 50/50 → 100/0).
- **Retries / timeouts / circuit breakers** — declarative config, not code.
- **Observability** — distributed tracing, metrics, access logs uniform across languages.
- **Policy** — who can call whom (authorization by service identity).
- **Fault injection** — chaos-test retries by injecting 500s.

**Trade-offs**

```
   ✅ Language-agnostic: Java + Go + Python services all get same features
   ✅ Consistent security & observability
   ✅ Ops team owns the mesh; app teams stay focused
   
   ❌ Every request goes through one extra hop (latency +1-2ms)
   ❌ 2× the pods (sidecar per service) = more RAM/CPU
   ❌ Complex to operate; steep learning curve
   ❌ Overkill until you have ~20+ services
```

**Popular meshes**
- **Istio** — most features, most complex.
- **Linkerd** — simpler, Rust-based data plane, lightweight.
- **Consul Connect** — HashiCorp, integrates with Nomad/Vault.
- **AWS App Mesh** — managed.

**Java angle**
- Big win: **Resilience4j / Hystrix in every service becomes optional.** The mesh handles retries, breakers, timeouts uniformly.
- Micrometer + OpenTelemetry integrations work alongside the mesh, not against.
- Trade: your Spring apps stay lean; ops complexity moves to the platform team.

---

### 6.6 Putting it all together — the front-end stack

Modern architectures compose all these pieces. Here's how they layer:

```
                       Internet
                          │
                          ▼
                    ┌───────────┐
                    │    CDN    │           ← static assets, TLS at edge
                    │(CloudFront│              cache, DDoS absorption
                    │ /Cloudflr)│
                    └─────┬─────┘
                          │
                          ▼
                    ┌───────────┐
                    │  DNS +    │           ← GeoDNS / Anycast
                    │  Anycast  │              route to nearest region
                    └─────┬─────┘
                          │
                          ▼
                    ┌───────────┐
                    │  L4 LB    │           ← AWS NLB / GCP TCP LB
                    │ (regional)│              handles massive TCP volume
                    └─────┬─────┘
                          │
                          ▼
                    ┌───────────┐
                    │  L7 LB /  │           ← nginx / ALB / Envoy
                    │  Reverse  │              TLS termination, host/path
                    │  Proxy    │              routing
                    └─────┬─────┘
                          │
                          ▼
                    ┌───────────┐
                    │   API     │           ← Spring Cloud Gateway
                    │  Gateway  │              auth, quotas, aggregation
                    └─────┬─────┘
                          │
     ┌────────────────────┼────────────────────┐
     ▼                    ▼                    ▼
  ┌──────┐    Service   ┌──────┐    Mesh    ┌──────┐
  │ Svc  │◄─────────────│ Svc  │◄───────────│ Svc  │
  │  A   │    Mesh      │  B   │            │  C   │
  │+sidcr│              │+sidcr│            │+sidcr│
  └──────┘              └──────┘            └──────┘
     │                    │                    │
     ▼                    ▼                    ▼
   Data                 Data                 Data
   store                store                store
```

Each layer solves one problem. Small apps skip layers (a monolith might go CDN → LB → app). Big architectures use them all.

**Interview one-liner for the whole section**
> "Scale up until it hurts, then scale out. Load balancers (L4 for speed, L7 for smarts) distribute traffic across the pool. Reverse proxies front the app for TLS, caching, and safety. API gateways add API-level concerns. Service meshes push cross-cutting infra into sidecars so app code stays clean."

---

## 7. Caching — Layers, Strategies, Eviction, Pitfalls

Caching is the single biggest performance lever in system design. It's also the source of the hardest bugs. This section covers **where** to cache, **how** to keep it in sync with the source of truth, **what** to throw out when it fills up, and **which** ways it goes wrong.

### 7.1 Why cache at all — the intuition

**The core idea**
Data that's expensive to fetch or compute is **kept close to where it's needed**, so most reads hit fast storage instead of the slow source.

```
   Without cache:               With cache:
   ───────────────              ─────────────
                                
   Client ──► DB (slow)          Client ──► Cache ──► HIT ──► return
                                                │
                                                └── MISS ──► DB ──► store in cache
   
   Every read = DB roundtrip     Most reads = memory-speed
   DB gets hammered              DB load drops 10–100×
```

**Why it works: locality**
Two patterns show up in almost every real workload:
- **Temporal locality** — recently used data is used again soon (feed refresh, session token).
- **Spatial locality** — near items get used together (paginated results, related products).

**Where cache buys you the most**
Recall from Section 4 the latency numbers:

```
   Main memory    100 ns
   SSD read       150,000 ns    ← 1500× slower
   Same-DC RTT    500,000 ns    ← 5000× slower
   Cross-region   150,000,000 ns ← 1,500,000× slower
```

Moving a read up one tier is often a 100–10,000× win. That's why caching wins.

**The three costs of caching**
- **Complexity** — invalidation, coherence, edge cases.
- **Staleness** — cached copy diverges from source of truth.
- **Memory** — RAM isn't free.

**Interview one-liner**
> "Caching trades staleness for speed. The trick is deciding where to cache, how to invalidate it, and what to evict when full."

---

### 7.2 The cache hierarchy — where can you cache?

Caches live at every layer of the stack. The best systems use multiple layers.

```
                         User's device
                              │
                              ▼
     ┌────────────────────────────────────────┐
     │ ① Browser cache                        │  images, JS, CSS
     │    (private, per-user)                 │  Cache-Control headers
     └────────────────┬───────────────────────┘
                      │
                      ▼
     ┌────────────────────────────────────────┐
     │ ② CDN edge cache                       │  static assets, some API
     │    (shared, global)                    │  Cloudflare, CloudFront
     └────────────────┬───────────────────────┘
                      │
                      ▼
     ┌────────────────────────────────────────┐
     │ ③ Reverse-proxy cache                  │  nginx, Varnish
     │    (shared, in-DC)                     │  HTTP response caching
     └────────────────┬───────────────────────┘
                      │
                      ▼
     ┌────────────────────────────────────────┐
     │ ④ Application-level in-process cache   │  Caffeine, Guava,
     │    (per-JVM, fastest)                  │  Ehcache local mode
     └────────────────┬───────────────────────┘
                      │
                      ▼
     ┌────────────────────────────────────────┐
     │ ⑤ Distributed/shared cache             │  Redis, Memcached,
     │    (network hop, shared across pods)   │  Hazelcast
     └────────────────┬───────────────────────┘
                      │
                      ▼
     ┌────────────────────────────────────────┐
     │ ⑥ Database cache                       │  buffer pool, query cache,
     │    (built-in)                          │  materialized views
     └────────────────┬───────────────────────┘
                      │
                      ▼
     ┌────────────────────────────────────────┐
     │ ⑦ CPU / OS caches                      │  L1/L2/L3, page cache
     └────────────────────────────────────────┘
```

**Which cache does what**

| Layer | Best for | Downsides |
|---|---|---|
| Browser | Static assets, per-user data | Only helps that one user |
| CDN | Static + cacheable dynamic | Invalidation is slow globally |
| Reverse-proxy | Anonymous HTTP responses | Not per-user friendly |
| In-process (Caffeine) | Hottest keys, ns-scale reads | Not shared → each JVM warms up |
| Distributed (Redis) | Shared hot data across pods | Adds a network hop |
| DB buffer pool | Recent pages / rows | You get it for free |

**In-process vs distributed cache**

```
   In-process (Caffeine)              Distributed (Redis)
   ───────────────────────            ─────────────────────
   
   ┌──────────┐  ┌──────────┐         ┌──────────┐  ┌──────────┐
   │ JVM 1    │  │ JVM 2    │         │ JVM 1    │  │ JVM 2    │
   │ [Cache]  │  │ [Cache]  │         │          │  │          │
   └──────────┘  └──────────┘         └────┬─────┘  └────┬─────┘
                                            │             │
   ✅ ns-level reads (in-heap)               └─────┬───────┘
   ✅ No network hop                              ▼
   ❌ Each JVM warms up separately          ┌──────────┐
   ❌ Data may differ between JVMs          │  Redis   │
                                            │ [Cache]  │
                                            └──────────┘
                                            
                                            ✅ Shared, coherent
                                            ✅ Big capacity
                                            ❌ Network hop (~0.5ms)
                                            ❌ Extra infra to run
```

**Best pattern: multi-tier**

```
   Read:  Caffeine (local) ──MISS──► Redis ──MISS──► DB
   Write: DB ──► invalidate Redis ──► invalidate Caffeine (or TTL)
   
   Hit rates typically: L1 (Caffeine) 80% → L2 (Redis) 15% → DB 5%
```

---

### 7.3 Caching strategies — who reads, who writes, in what order?

Six patterns cover 99% of real designs.

**Pattern A — Cache-Aside (lazy loading, "look-aside")**
App orchestrates. Cache is a helper, not in the write path.

```
   READ                                 WRITE
   ─────────────────────                ─────────────────────
   
   App ──get(key)──► Cache               App ──write(k, v)──► DB
                       │                               │
                   HIT │──► return                     ▼
                       │                          invalidate
                   MISS│                          (or update)
                       ▼                              key in
              App ──read──► DB                       Cache
                       │
                       ▼
                App ──set(k, v)──► Cache
                       │
                       ▼
                    return
```

- ✅ Simple. Resilient to cache down (fall through to DB).
- ✅ Only requested data cached (works for sparse access).
- ❌ First read after miss is slow.
- ❌ Race conditions on concurrent write + read miss.
- **Most common pattern with Redis.**

**Pattern B — Read-Through**
Cache sits in front. App only talks to cache; cache fetches from DB on miss.

```
   App ──get(k)──► Cache
                     │
                 HIT │──► return
                     │
                 MISS│──► Cache fetches from DB ──► stores ──► returns
```

- ✅ App code is simpler (one dep).
- ❌ Needs cache library integrated with DB loader (e.g. Caffeine's `LoadingCache`).
- ❌ Cache outage = read outage.

**Pattern C — Write-Through**
Writes go to cache; cache synchronously writes to DB.

```
   App ──write(k,v)──► Cache ──► DB
                          │       │
                          │◄──────┘ (ack)
                          │
                   set in cache, return success
```

- ✅ Cache is always up-to-date (no invalidation logic).
- ❌ Every write is slower (two writes).
- ❌ Cache holds even rarely-read keys (memory waste).

**Pattern D — Write-Behind (Write-Back)**
Write to cache; cache asynchronously flushes to DB later (batched).

```
   App ──write(k,v)──► Cache ──► (buffer)
                          │
                     immediate ack
                          │
                          ▼
                   (later, async)
                   Cache ──► DB
```

- ✅ Very fast writes, high throughput.
- ✅ Batching → DB gets fewer, larger writes.
- ❌ Data loss risk if cache dies before flush.
- ❌ Complexity: retries, ordering, partial failures.
- Used in: Kafka + DB sinks, some materialized-view systems.

**Pattern E — Refresh-Ahead**
Cache proactively refreshes hot keys before their TTL expires.

```
   Time ─────────────────────────────────────►
   
   TTL: [───────────── 60s ─────────────]
                                  │
                   at 80% of TTL: │  cache refreshes
                                  ▼  in background
                            [─── new 60s ────]
   
   Read at any time = fresh, no miss.
```

- ✅ No first-read miss for hot keys.
- ❌ Wasteful for cold keys — needs "hotness" detection.

**Pattern F — Write-Around**
Writes bypass cache, go straight to DB. Cache populated lazily on reads.

```
   Write:  App ──► DB                        (cache untouched)
   Read:   App ──► Cache ──MISS──► DB ──► populate Cache
```

- ✅ Good for write-heavy workloads where written data is rarely read soon.
- ❌ First read after write is slow.

**Decision cheat sheet**

```
   Workload                           Best fit
   ──────────────────────────────    ────────────────────
   Read-heavy, sparse hot keys        Cache-aside
   Read-heavy, want simple app code   Read-through
   Consistency critical               Write-through
   Write-heavy, tolerate small loss   Write-behind
   Predictable hot keys, no miss OK   Refresh-ahead
   Writes rarely read back            Write-around
```

**Java angle**
- Spring `@Cacheable` / `@CachePut` / `@CacheEvict` → cache-aside declaratively.
- Caffeine `LoadingCache` → read-through in-process.
- Redisson / Spring Data Redis + `@Cacheable` → distributed cache-aside.

---

### 7.4 Eviction policies — what to throw out when full

Cache is finite. When it's full and a new item comes in, something must go.

**LRU — Least Recently Used**

```
   Cache holds 4 slots. Access order left→right.
   
   State:  [A][B][C][D]     (D is newest)
   Access E → evict A       (A was oldest untouched)
   State:  [B][C][D][E]
   Access B → move to front
   State:  [C][D][E][B]
   Access F → evict C
   State:  [D][E][B][F]
```

- ✅ Great intuition, works for most workloads.
- ❌ A single "scan" (someone reads all rows once) evicts your hot set.
- Data structure: doubly-linked list + hashmap. O(1) per op.
- **The default for most caches.**

**LFU — Least Frequently Used**

```
   Cache tracks HIT COUNT per key.
   Evict the least frequently accessed.
   
   State:  A(hits=100) B(hits=50) C(hits=5) D(hits=2)
   Insert E → evict D (fewest hits)
```

- ✅ Better for stable hot sets that rarely change.
- ❌ Sticky: once a key has high count, it survives even if usage drops.
- Fix: **W-LFU / TinyLFU** — decay old counts (Caffeine uses this).

**LRU vs LFU visualized**

```
   Scenario: workload = one-time scan of 1000 items,
             then repeated reads of the top 10.
   
   Pure LRU:                          Pure LFU:
   Scan wipes out top-10 hot set.     Top-10 keep their counts,
   Cold performance until re-warm.    survive the scan. Good.
   
   Scenario: workload = last week's hot posts are today's cold posts.
   
   Pure LRU: adapts quickly.          Pure LFU: old hot posts linger,
                                       hard to evict due to count history.
```

**Modern default: TinyLFU / W-TinyLFU (Caffeine)**
Combines LRU-style recency with frequency counting + count decay. Beats both LRU and LFU on most real workloads.

**Other policies**

```
   FIFO       Evict oldest inserted (ignores access). Simple, poor.
   Random     Evict a random key. Cheap; surprisingly OK in some cases.
   ARC        Adaptive Replacement Cache. Balances recency + frequency
              adaptively. Used in ZFS. Patent-encumbered until 2020.
   2Q         Two queues (hot / probationary). Simpler ARC-alike.
   Clock      Approximate LRU with reference bits, O(1). OS page caches.
   TTL-only   No eviction on capacity — just let entries expire.
              Assumes TTL fits the workload.
```

**Eviction policy comparison**

| Policy | Recency | Frequency | Scan-resistant | Complexity |
|---|---|---|---|---|
| FIFO | ❌ | ❌ | ❌ | trivial |
| LRU | ✅ | ❌ | ❌ | simple |
| LFU | ❌ | ✅ | ✅ | medium |
| W-TinyLFU (Caffeine) | ✅ | ✅ (decayed) | ✅ | medium |
| ARC | ✅ | ✅ | ✅ | complex |
| Random | ❌ | ❌ | partial | trivial |

**TTL — the other knob**
Every cached item has an expiry. Independent from capacity eviction.

```
   Set with TTL=60s:
   
   t=0    set(k, v)                 → v cached
   t=45   get(k)   → HIT              (still fresh)
   t=61   get(k)   → MISS             (expired)
   
   TTL choices:
     Short (seconds)   → fresh, but low hit rate
     Long (hours)      → high hit rate, stale risk
     None (until evict)→ purely capacity-driven
```

Tune per data type: session token = 30 min, product name = hours, price = seconds.

**Interview one-liner**
> "Eviction policy shapes hit rate under memory pressure. LRU is a fine default; W-TinyLFU (Caffeine) is state of the art. TTL controls staleness, independent of capacity."

---

### 7.5 Cache invalidation — the second-hardest problem in CS

Phil Karlton's line: "There are only two hard problems in computer science: cache invalidation and naming things." Here's why the first one is hard.

**The core question**
Source of truth changed → when does the cache know?

**Strategy 1 — TTL only**
Just wait. Cache holds stale data for up to TTL seconds, then re-fetches.

```
   DB updated: name = "New"
                     │
                     │  TTL still valid
                     ▼
   Cache still returns "Old" until TTL expires (up to N seconds).
```

- ✅ Simplest possible. No coordination.
- ❌ Users see stale data for up to TTL.

**Strategy 2 — Write-through / write-around invalidation**
On write, update or delete the cache entry.

```
   App writes:  DB.update(k, v)
                │
                └─► cache.delete(k)   OR    cache.set(k, v)
```

- **Delete** is safer than **update** — no risk of cache getting a wrong value from a partial write.
- ❌ Only works within one app. External DB changes (batch jobs, other services) aren't caught.

**Strategy 3 — CDC-based invalidation**
Listen to DB changelog (WAL, binlog). Invalidate cache when rows change.

```
   ┌──────────┐   binlog   ┌─────────────┐   invalidate   ┌────────┐
   │ Database │──stream──►│Debezium/Kafka│──────────────►│ Cache  │
   └──────────┘            └─────────────┘                └────────┘
```

- ✅ Catches ALL writes, no matter the source.
- ❌ Big infra (Debezium, Kafka).

**Strategy 4 — Versioning / cache-busting keys**
Include a version in the key. Update = new version = new key. Old key ages out.

```
   Key: user:42:v3          (initial)
   Update user 42 → bump version → user:42:v4
   
   Reads with v3 miss; refetch, cache under v4.
   Old v3 entry ages out of memory naturally.
```

Common for static assets (`logo.v42.png`) and public API responses.

**The double-write race**

```
   Time
    ▼
   T1 Client A: read miss for k → fetch v1 from DB
   T2 Client B: write v2 to DB
   T3 Client B: delete cache[k]
   T4 Client A: cache[k] = v1        ← STALE WRITE
   
   Cache now holds v1 forever (until TTL). Nasty bug.
```

**Fixes:**
- **Delete-after-write** timing + short TTL as safety net.
- **Read-through with lock** (single-flight): only one thread fetches; others wait.
- **CDC** so a DB update always trumps a stale in-flight fetch.

**Interview one-liner**
> "Invalidation strategies range from 'let TTL fix it' to 'stream binlog into a bus.' Most systems use TTL + delete-on-write and accept short windows of staleness. When you can't accept staleness, use CDC or single-flight reads."

---

### 7.6 Cache pathologies — the classic failure modes

Real caches fail in a few well-known ways. Recognize them; the fix is usually simple.

**Cache stampede (thundering herd)**

```
   Popular key expires. 10,000 clients see MISS at the same time.
   All 10,000 hit the DB simultaneously.
   
                   TTL expires
                        │
                        ▼
   ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓  ← spike of DB reads
   
   DB melts.
```

**Fixes:**
- **Single-flight / dogpile lock** — first thread fetches, others wait for the result.
- **Probabilistic early expiration** — refresh a bit before TTL for some fraction of requests.
- **Locked recompute** with a background refresh.

**Cache penetration**

```
   Attacker (or bug) requests keys that DON'T exist:
      user:0, user:-1, user:999999999...
   
   Cache MISS → DB query → still nothing → DB hit wasted, repeat forever.
```

**Fixes:**
- **Cache the negative** — store `null` sentinel with short TTL.
- **Bloom filter** in front: "does this key even plausibly exist?"

**Cache avalanche**

```
   Many keys share the same TTL and expire at the same instant.
   → mass simultaneous DB fetch, similar to stampede but broader.
```

**Fixes:**
- **Randomized TTL** — add jitter (`TTL + rand(0, TTL*0.1)`).
- **Staggered warm-up** at startup.

**Hot key problem**

```
   One key (a viral post, top page) gets 99% of the traffic.
   
   In a distributed cache, this key lives on ONE shard.
   That shard's CPU/network melts. Other shards idle.
   
             ┌────────┐
   99% ─────►│ shard1 │  🔥
             └────────┘
             ┌────────┐
   0.5% ────►│ shard2 │
             └────────┘
             ┌────────┐
   0.5% ────►│ shard3 │
             └────────┘
```

**Fixes:**
- **Local L1 cache** (Caffeine) on each app pod → hot key served from memory before it hits Redis.
- **Read replicas** of the hot key on multiple shards.
- **Randomized key suffixes** for very hot keys (`item:42:0..9`).

**Big key / hot key on Redis**
A single 500 MB value or hash. One `GET` blocks the event loop.
**Fix:** split into chunks; store metadata separately.

**Cache coherence in multi-tier**

```
   Update flow:
     DB updated → Redis invalidated → but Caffeine (local L1) still hot!
   
   Each JVM has its own L1 that DB write can't reach directly.
```

**Fix:** publish invalidation via Redis pub/sub or Kafka → all JVMs drop their L1 entry.

**Interview one-liner**
> "Every cache eventually meets stampede, penetration, avalanche, or hot key. Know the pattern and the fix — mutex-single-flight, negative caching, jittered TTL, and local L1 in front of shared L2."

---

### 7.7 HTTP caching — the standard headers

Since a lot of caching is at the HTTP layer (browser, CDN, reverse proxy), know the vocabulary.

**Cache-Control (the primary knob)**

```
   Cache-Control: public, max-age=3600, s-maxage=86400
                   │         │              │
                   │         │              └─ CDN / shared caches: 24h
                   │         └─────────────── browser: 1h
                   └───────────────────────── anyone may cache
   
   Other directives:
     private            only browser, not shared
     no-store           don't cache at all (sensitive data)
     no-cache           cache, but revalidate every time
     immutable          never revalidate; safe forever
     stale-while-revalidate=N   serve stale for N sec while refetching
```

**Validators — cheap "is it still fresh?" checks**

```
   Server responds with:
     ETag: "v42-abc"           unique fingerprint
     Last-Modified: <date>     coarser fallback
   
   Client re-requests:
     If-None-Match: "v42-abc"        ─┐
     If-Modified-Since: <date>       ─┤
                                      │
   Server ──── 304 Not Modified ─────►│   (no body, tiny response)
              (or 200 with new body)
```

**Vary — cache key modifiers**
```
   Vary: Accept-Encoding, Accept-Language
   → cache stores a separate copy per (URL, encoding, language) combo
```

Miss this and users get each others' language variants.

**Interview trap**
- `no-cache` ≠ "don't cache." It means "cache, but must revalidate before use." `no-store` is the "don't cache at all."

---

### 7.8 Practical — Redis vs Memcached (the everyday choice)

**Redis**
- Data structures: strings, lists, sets, sorted sets, hashes, streams, bitmaps, HLL.
- Persistence (RDB snapshots + AOF logs) → survives restart.
- Pub/sub, Lua scripting, transactions.
- Cluster mode: hash-slot sharding, replicas per master.
- **Use when:** you want more than just KV — leaderboards, queues, rate limiters, sessions.

**Memcached**
- Pure KV. Volatile, no persistence.
- Multi-threaded (Redis is single-threaded per shard).
- Slightly simpler / cheaper for pure caching.
- **Use when:** simple string cache at massive scale, no data structures needed.

**In practice: 90% of Java shops pick Redis.**

**Java angle**
- **Spring Data Redis** + **Lettuce** (default) or **Jedis** (older).
- **Redisson** for higher-level primitives (distributed locks, RMap, RQueue).
- **Spring Cache abstraction** — swap Caffeine ↔ Redis by config.

---

### 7.9 Putting it together — a layered cache design

Real systems combine layers. Here's a typical Java/Spring microservice cache stack:

```
   Client
     │
     ▼
   CDN cache (static, some public API)                ← 100ms saved for global users
     │
     ▼
   Reverse-proxy (nginx) cache for anonymous GETs
     │
     ▼
   API Gateway cache for public read APIs
     │
     ▼
   ┌─────────────────────────────────────────┐
   │   Service pod                           │
   │                                         │
   │   ┌────────────────────┐                │
   │   │  Caffeine (L1)     │  ← ns reads,  │
   │   │  in-heap           │    per-pod    │
   │   └─────────┬──────────┘                │
   │             │ MISS                       │
   │             ▼                            │
   │   ┌────────────────────┐                │
   │   │  Redis (L2)        │  ← 0.5ms,     │
   │   │  shared, cluster   │    shared     │
   │   └─────────┬──────────┘                │
   │             │ MISS                       │
   │             ▼                            │
   │   ┌────────────────────┐                │
   │   │  DB buffer pool    │  ← next best  │
   │   │  (Postgres)        │                │
   │   └─────────┬──────────┘                │
   │             │ MISS                       │
   │             ▼                            │
   │       Disk read                          │
   └─────────────────────────────────────────┘
   
   Invalidation:
     Write path: DB write ──► Redis invalidate
                             ──► pub/sub "k evicted" ──► all pods drop L1
```

**Hit rate math**
```
   Miss rate at each layer:
     Caffeine    20%
     Redis       5%
     DB read     100% of what's left
   
   Effective end-to-end miss rate = 20% × 5% = 1% of requests hit DB.
   → DB load drops 100×.
```

**Interview one-liner for the whole section**
> "Cache close to the reader with an appropriate strategy, evict smartly under memory pressure, invalidate on writes, and know your pathologies. A well-designed multi-tier cache slashes DB load by 100× and end-to-end latency by 10×."

---

## 8. Rate Limiting & Resilience Patterns

Sections 6–7 gave your system speed. Section 8 makes it **survive** — under bursts, abuse, and downstream failure. Both topics answer the same underlying question: **how do we protect a system from being overwhelmed?**

### 8.1 Why rate limit — the intuition

**The problem**
Without limits, one caller can:
- **Overload** your system (accidental bug, viral spike).
- **Abuse** your service (scraping, brute-force login, credential stuffing).
- **Consume the free tier** and starve paying customers.
- **DDoS you** (whether malicious or not).

Rate limiting = "reject requests beyond a policy so the rest keep working."

```
   Without rate limit:
   
   Client X (buggy) ──10,000 req/s──► Server ──► ⚠️ overload, everyone slow
   Client Y (normal) ──10 req/s──►    Server ──► ⚠️ starved
   
   With rate limit:
   
   Client X ──10,000 req/s──► [Limiter] ─100/s─► Server ──► ✅ healthy
   Client Y ──10 req/s──────► [Limiter] ─10/s──► Server ──► ✅ served
   
   X gets 429 Too Many Requests for the rest.
```

**Where to enforce**

```
   Client
      │
      ▼
   ┌─────────────┐    ← per-IP DDoS-scale (Cloudflare, AWS Shield)
   │  CDN / WAF  │
   └──────┬──────┘
          │
          ▼
   ┌─────────────┐    ← per-API-key, per-endpoint quota
   │ API Gateway │       (Kong, Spring Cloud Gateway)
   └──────┬──────┘
          │
          ▼
   ┌─────────────┐    ← per-tenant, per-user, per-feature
   │  App code   │       (Resilience4j, Bucket4j, Redis)
   └─────────────┘
```

**Key dimensions to limit by**
- **Global** — protect the system as a whole.
- **Per-IP** — cheap default, evadable via NAT / proxies.
- **Per-user / API-key** — the fair one. Requires auth first.
- **Per-endpoint** — expensive endpoints get tighter limits.
- **Per-tenant** — free vs paid tier.

**Interview one-liner**
> "Rate limiting is quota enforcement. The four dimensions — global, per-IP, per-user, per-endpoint — combine to protect the system without punishing normal users."

---

### 8.2 Rate limiting algorithms — the classic four

**Algorithm 1 — Fixed Window Counter**

Divide time into fixed buckets (e.g., 1-minute windows). Count requests per bucket. Reject when count exceeds limit.

```
   Limit: 100 req / minute
   
   00:00 ──► 00:01 ──► 00:02 ──► 00:03
   [  85 req ]  [  100 req ] [  22 req ] [  ...
                      │
              at 100 → reject rest until 00:02

   The window boundary problem:
   
   00:00:00 ─────────── 00:01:00 ─────────── 00:02:00
        [ 100 req at 00:00:59 ]  [ 100 req at 00:01:00 ]
             = 200 req in 1 second across the boundary!
```

- ✅ Trivial to implement (`INCR key EX 60` in Redis).
- ❌ **Boundary spike** allows 2× the limit at window edges.
- Use for: coarse quotas where spikes are OK.

**Algorithm 2 — Sliding Window Log**

Store timestamp of every request in a sorted set. Count entries within the last N seconds. Evict older ones.

```
   Limit: 100 req / 60s
   
   Sorted set: [t1, t2, t3, ..., tN]  (timestamps)
   
   On each request:
     1. Remove entries older than (now - 60s)
     2. Count remaining
     3. If < 100, add current timestamp, allow
     4. Else, reject
```

- ✅ Perfectly accurate — no boundary spikes.
- ❌ Memory grows with request rate (1 timestamp per request per user).
- Use for: strict limits at low-to-moderate rates.

**Algorithm 3 — Sliding Window Counter (hybrid)**

Combine fixed windows with a weighted count from the previous window.

```
   Windows: prev (00:00-00:01), curr (00:01-00:02)
   
   Current time: 00:01:42  (42s into curr window, 18s remain)
   
   Effective count = curr_count + prev_count × (18/60)
                                             ↑
                                    fraction of prev window
                                    still in the "sliding" range
   
   Reject if effective_count > limit.
```

```
   Timeline:
   
   00:00 ─────────── 00:01 ─────────── 00:02
      prev bucket        curr bucket
                         ▲
                    now: 00:01:42
   
   Weight of prev in the last-60s "virtual" window:
       remaining 18s of prev / 60s = 0.3
       So contribute 30% of prev_count.
```

- ✅ Smooths boundary spikes.
- ✅ O(1) memory (2 counters per user).
- ✅ Good approximation of true sliding window.
- Use for: **most production rate limiters.** Cloudflare uses this.

**Algorithm 4 — Token Bucket**

Imagine a bucket that **refills at a constant rate** and holds up to N tokens. Each request consumes 1 token. No tokens → reject.

```
   Bucket capacity: 10 tokens.
   Refill rate:     2 tokens/sec.
   
   ┌─────────────┐   ← 2 tokens/sec drip in
   │ ▓▓▓▓▓▓▓▓▓▓ │    (never above capacity)
   │ 10 tokens   │
   └──────┬──────┘
          │
          │ each request consumes 1
          ▼
        Server
   
   Request comes: bucket has 5 → consume → 4 left → allow
   Request comes: bucket has 0 → reject (or wait)
```

**Behavior**
- Sustains ~2 req/s average.
- **Allows bursts** up to capacity (10 tokens = 10 req in a row).
- After a burst, must wait for tokens to refill.

- ✅ Simple, flexible, supports bursts.
- ✅ O(1) state (last-refill timestamp + token count).
- ✅ **The most common choice.** AWS, GCP, most APIs use this.

**Algorithm 5 — Leaky Bucket (queue-based)**

Requests enter a **queue** (the bucket). A **fixed-rate drain** processes them. Overflow → reject.

```
   Incoming ──►  ┌─────────┐
                 │▓▓▓▓▓▓▓▓▓│  ← queue fills
                 │▓▓▓▓▓▓▓▓▓│    (capacity = 10)
                 │▓▓▓▓▓▓▓▓▓│
                 │         │
                 │         │
                 └────┬────┘
                      │
                      ▼  drain 2/sec (constant)
                    Server
```

**Behavior**
- Output rate is **strictly smooth**. No bursts pass through.
- Full bucket = reject.

- ✅ Smooths spikes for downstream systems that hate bursts.
- ❌ Higher latency (queueing).
- Use for: leveling load into a queue-sensitive backend.

**Token bucket vs Leaky bucket — the key difference**

```
   Token bucket:  Allows bursts up to capacity, then rate-limits.
                  Good for user-facing APIs (a burst feels responsive).
   
   Leaky bucket:  Constant output rate. Smooths bursts by queueing.
                  Good for feeding a downstream that must be paced.
   
   ─────────── burst behavior visualized ───────────
   
   Input:         ██████░░░░░░██████░░░░░░
   
   Token bucket:  ██████░░░░░░██████░░░░░░   (passes bursts)
   Leaky bucket:  ██░██░██░██░██░██░██░██░   (paced output)
```

**Algorithm comparison table**

| Algorithm | Bursts | Boundary spikes | Memory | Complexity |
|---|---|---|---|---|
| Fixed window | ✅ (at boundary) | ❌ 2× | O(1) | trivial |
| Sliding window log | ❌ | ✅ accurate | O(N) | moderate |
| Sliding window counter | ✅ (smoothed) | ✅ approx | O(1) | moderate |
| Token bucket | ✅ | ✅ | O(1) | simple |
| Leaky bucket | ❌ (smoothed) | ✅ | O(N queue) | simple |

**Interview one-liner**
> "Fixed window is easy but bursty at boundaries. Sliding window counter fixes it with O(1) memory. Token bucket allows friendly bursts and is the industry default. Leaky bucket is when you need constant output."

---

### 8.3 Distributed rate limiting

Fine for one node. But you probably have 10 pods. **Whose count is authoritative?**

**Approach 1 — Per-node local**
Each pod tracks its own count. Simple but wrong: with N=10 pods, effective global limit is 10× local.

**Approach 2 — Centralized store (Redis)**
Every pod increments a shared Redis counter.

```
   Pod 1 ─┐
   Pod 2 ─┼──► Redis:  INCR user:42:count EX 60
   Pod 3 ─┘         if > 100 → reject
```

- ✅ Accurate global limit.
- ❌ Every request costs a Redis roundtrip (~0.5ms).
- ❌ Redis is a critical dependency.

**Approach 3 — Local buckets + periodic sync**
Each pod has a local budget (100/N tokens). Periodically reconcile with Redis. Less accurate but fewer roundtrips.

**Approach 4 — Envoy / API gateway does it**
Push rate limiting to the edge (one place, purpose-built). App doesn't care. Common in service-mesh setups.

**Java angle**
- **Bucket4j** — token bucket library, backends: in-memory, Redis, Hazelcast.
- **Resilience4j RateLimiter** — annotation-based per-method limiting.
- **Spring Cloud Gateway** `RequestRateLimiter` filter with Redis backend.
- **Redis** Lua scripts for atomic increment-and-check.

**HTTP semantics for rate limits**

```
   Response when limited:
     HTTP/1.1 429 Too Many Requests
     Retry-After: 30                    ← tell the client to back off
     X-RateLimit-Limit: 100             ← total allowed / window
     X-RateLimit-Remaining: 0           ← left in current window
     X-RateLimit-Reset: 1734567890      ← unix time when quota resets
```

Play nicely: clients can back off gracefully.

---

### 8.4 Resilience patterns — surviving downstream failure

Rate limiting protects **you** from callers. Resilience patterns protect **you** from your dependencies.

**The failure modes to defend against**

```
   ① Downstream slow      → your threads pile up waiting
   ② Downstream down      → every call errors, cascading up
   ③ Transient blips      → 1% error rate becomes 100% if you don't retry
   ④ Retry storm          → everyone retries at once, hammers the recovering svc
   ⑤ Backpressure lost    → you accept more than you can process
```

Each pattern below targets one of these.

---

### 8.5 Timeout — the most basic resilience

Never wait forever. Set a deadline per call.

```
   Without timeout:
   
   Your thread ──► Downstream (frozen) ──► ⏳ forever
                                            
   Your thread pool fills up. Cascading death.
   
   With timeout:
   
   Your thread ──► Downstream ──500ms──► ⏱ timeout
   Thread returns to pool. Move on. Fail this one request.
```

**Two levels**

```
   Connect timeout: max time to establish connection    (e.g., 1s)
   Read timeout:    max time to receive response body   (e.g., 3s)
```

**Budgets**
For a request that fans out, each downstream call must fit in the remaining budget.

```
   Client's SLA:  P99 < 500ms
                              
   ┌──── total 500ms ─────────────────────────────┐
   │ svc A (100ms) │ svc B (200ms) │ svc C (150ms)│  ← reserve 50ms buffer
   └──────────────────────────────────────────────┘
   
   If svc A took 350ms, svc B and C must be squeezed / skipped.
```

Propagate the deadline: gRPC has this natively (`grpc.Deadline`); HTTP does it via `X-Deadline` headers.

**Interview one-liner**
> "Every network call needs a timeout. No timeout = your service inherits the failure of every downstream, forever."

---

### 8.6 Retry — turn transient failures into success

Downstream had a blip. Retry saves the request.

```
   Attempt 1 → 503 ───► wait ───► Attempt 2 → 200 ✅
```

**Only retry when it's safe**
- Idempotent methods only (GET, PUT, DELETE). POST unsafe unless idempotency-key.
- Errors that suggest transience: 5xx, timeouts, connection resets.
- **Never retry 4xx.** The client is wrong; retrying won't help.

**The retry storm — what happens without care**

```
   Downstream flaps at t=0. 10,000 clients all retry immediately.
   → 10,000 retries at t=1
   → still overloaded
   → 10,000 retries at t=2
   → downstream can't recover
```

**Fix: exponential backoff + jitter**

```
   Attempt 1: fail at t=0
   Attempt 2: wait 1s ± random   → at t=1..2
   Attempt 3: wait 2s ± random   → at t=3..5
   Attempt 4: wait 4s ± random   → at t=7..11
   Attempt 5: wait 8s ± random   → at t=15..23
   
   Backoff spreads load; jitter prevents synchronized storms.
```

**Retry budget** — cap total retries as a fraction of traffic. Prevents runaway retry amplification.

**Retry policy tradeoffs**

| Retries | Effect |
|---|---|
| 0 | Every blip fails |
| 3 with backoff+jitter | Sweet spot for most calls |
| Unbounded | Retry storm, resource exhaustion |

**Java angle**
- Spring Retry (`@Retryable`).
- Resilience4j Retry (`@Retry(name="foo")`) with `IntervalFunction.ofExponentialRandomBackoff(...)`.

---

### 8.7 Circuit Breaker — stop hitting the corpse

If downstream keeps failing, **stop calling it** for a while. Give it time to recover instead of amplifying the outage.

**The three states**

```
   ┌──────────┐   failures exceed threshold   ┌────────┐
   │  CLOSED  │──────────────────────────────►│  OPEN  │
   │  (normal)│                                │(reject) │
   └────▲─────┘                                └───┬────┘
        │                                          │  after timeout
        │  all/most probes succeed                 ▼
        │                                    ┌────────────┐
        │                                    │ HALF-OPEN  │
        └────────────────────────────────────│  (probe)   │
             probes fail again               └────────────┘
             → back to OPEN
```

**Behavior in each state**

```
   CLOSED     Calls pass through. Failure counter increments on error.
              Threshold hit (e.g., >50% errors over 20 calls)
              → transition to OPEN.
   
   OPEN       All calls FAIL FAST (no downstream call made).
              Return cached / default / error immediately.
              After timeout (e.g., 30s) → HALF-OPEN.
   
   HALF-OPEN  Let a few calls through as probes.
              All succeed → CLOSED.
              Any fail → back to OPEN.
```

**Why this saves the system**

```
   Without breaker:
   
   Downstream sick ──► 10,000 concurrent slow calls
                    ──► your thread pool full
                    ──► YOU are now sick too ──► cascade
   
   With breaker:
   
   Downstream sick ──► breaker opens after N failures
                    ──► subsequent calls fail fast (< 1ms)
                    ──► your threads stay free ──► YOU stay healthy
                    ──► fall back to cached / degraded response
```

**Fallback strategies**
When the breaker is open, what do you return?

```
   Option                        Example
   ─────────────────────────    ─────────────────────
   Cached last-known-good        Recommendations service down → return cached list
   Default / empty response      Ads service down → serve no ads
   Downgrade to simpler path     Personalized feed down → serve popular feed
   Explicit error to client      Payment down → tell user to retry
```

**Java angle**
- **Resilience4j CircuitBreaker** — modern, functional.
- **Hystrix** — Netflix's original, now deprecated but still around.
- Fallback: `@CircuitBreaker(name="foo", fallbackMethod="fallbackFoo")`.

**Interview one-liner**
> "A circuit breaker fails fast during downstream outages, sparing your resources and giving the sick service room to recover. Combined with a fallback, it turns a hard failure into a graceful degradation."

---

### 8.8 Bulkhead — isolate failures like a ship's compartments

Named after the watertight compartments in ships: if one floods, the rest stay dry.

**The problem**
Your app has one shared thread pool (say, 200 Tomcat threads). Downstream **X** goes slow. Every request that hits X blocks a thread. Soon ALL 200 threads are stuck on X — even requests that don't need X can't get served.

```
   Without bulkhead:
   
   ┌────────────────────────────────┐
   │ Shared pool (200 threads)       │
   │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓  all      │
   │ blocked waiting on slow svc X   │
   └────────────────────────────────┘
   
   Requests to svc Y, Z, DB all starve.
   
   With bulkhead:
   
   ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
   │ Pool for X  │ │ Pool for Y  │ │ Pool for Z  │
   │  max 50     │ │  max 50     │ │  max 50     │
   │ ▓▓▓▓▓▓▓▓▓▓▓ │ │ ░░░░░░░░░░ │ │ ░░░░░░░░░░ │
   │ (all busy)  │ │ (fine)      │ │ (fine)      │
   └─────────────┘ └─────────────┘ └─────────────┘
   
   Only calls to X are impacted. Y and Z serve normally.
```

**Two implementations**

```
   Thread-pool bulkhead:  separate thread pool per dependency
                          (Hystrix classic).
   Semaphore bulkhead:    a counter capping concurrent calls
                          per dependency (Resilience4j, lighter).
```

**Rejection when bulkhead is full**
Fail fast with an appropriate error (429 or 503). Same principle as circuit breaker: better to reject some than starve all.

**Interview one-liner**
> "Bulkheads segment your capacity by dependency so one sick downstream can't sink the whole ship."

---

### 8.9 Backpressure — telling upstream to slow down

Bulkhead and rate limit **reject** overload. **Backpressure signals** overload upstream so it produces less in the first place.

**The problem without backpressure**

```
   Producer ──1000 msgs/s──► Queue ──► Consumer (processes 100/s)
                                      │
                              queue grows forever ──► OOM
```

**With backpressure**

```
   Producer ◄──"slow down"── Queue ──► Consumer
   
   Producer sees the signal and either:
     ▸ produces slower
     ▸ drops messages (based on policy)
     ▸ sheds load (returns 429/503 to ITS callers)
```

**Mechanisms**
- **TCP** — receiver's window size shrinks; sender must wait. Native, transparent.
- **HTTP/2** — flow control per stream (`SETTINGS_INITIAL_WINDOW_SIZE`).
- **Reactive streams (Reactor / RxJava)** — subscriber calls `request(n)`; publisher sends at most `n`.
- **Bounded queues** — producer blocks when full (or drops).
- **Kafka** — consumer lag → alerts + auto-scale consumers.

**Load shedding — when backpressure isn't enough**

```
   If you can't process, and can't slow the producer,
   drop the LOW-priority requests to keep serving the HIGH.
```

**Priority queues** at the edge: paying users > free tier > internal batch.

**Java angle**
- **Project Reactor** `Flux` has built-in `request(n)` backpressure.
- **Servlet stack** doesn't have first-class backpressure — bounded queues + timeouts approximate it.
- **Kafka consumers** — commit slower to signal lag; auto-scaling reacts.

**Interview one-liner**
> "Backpressure is upstream throttling: instead of buffering forever, the receiver signals the sender to slow. When you can't slow the sender, shed load — drop low-priority work to keep high-priority alive."

---

### 8.10 Combining patterns — the full outbound-call recipe

For every remote call from your service, layer the patterns.

```
   Your code calls svc X:
   
   ┌───────────────────────────────────────────────────┐
   │ ① Rate limiter (per-caller / per-endpoint)        │
   │        │                                          │
   │        ▼                                          │
   │ ② Bulkhead  (cap concurrent calls to X)           │
   │        │                                          │
   │        ▼                                          │
   │ ③ Circuit breaker  (fail fast if X is sick)       │
   │        │                                          │
   │        ▼                                          │
   │ ④ Retry with backoff + jitter                     │
   │        │                                          │
   │        ▼                                          │
   │ ⑤ Timeout on each attempt                         │
   │        │                                          │
   │        ▼                                          │
   │      svc X                                        │
   │        │                                          │
   │        ▼                                          │
   │ ⑥ Fallback on failure (cache, default, degrade)  │
   └───────────────────────────────────────────────────┘
```

**Order matters:**
- Rate limiter first — reject before touching resources.
- Bulkhead + breaker before the call — protect the pool.
- Retry inside the breaker — a retry attempt is still a call the breaker counts.
- Timeout wraps each attempt — never wait forever.
- Fallback catches everything.

**Java angle — a stacked Resilience4j example**

```
    Supplier<Response> decorated =
        Decorators.ofSupplier(() -> svcX.call(req))
            .withRateLimiter(rateLimiter)
            .withBulkhead(bulkhead)
            .withCircuitBreaker(circuitBreaker)
            .withRetry(retry)
            .withFallback(ex -> fallback(req))
            .decorate();
    
    Response r = decorated.get();
```

---

### 8.11 What NOT to do — common mistakes

- **No timeouts** — silent killer #1.
- **Blind retries on POST** — duplicate charges, doubled orders.
- **No jitter on retry** — synchronized retry storms hit downstream even harder.
- **Breaker without fallback** — you fail fast but still fail; user gets 500.
- **One shared thread pool for everything** — one sick dep sinks all.
- **Rate limit without headers** — clients can't back off intelligently.
- **Retries + breaker with mismatched budgets** — retry keeps opening the breaker.

---

### 8.12 Putting it together — protecting a Spring Boot service

```
   External clients
        │
        ▼
   ┌──────────────────────────────┐
   │ API Gateway                  │
   │  - Per-API-key rate limit    │  ← 8.2 + 8.3
   │  - 429 with Retry-After      │
   └──────────────┬───────────────┘
                  │
                  ▼
   ┌──────────────────────────────┐
   │ Your Spring Boot service     │
   │                              │
   │  For each downstream call:   │  ← 8.10 stack
   │   RateLimiter                │
   │   Bulkhead                   │
   │   CircuitBreaker             │
   │   Retry + jitter             │
   │   Timeout                    │
   │   Fallback                   │
   │                              │
   │  Bounded queues + backpressure│ ← 8.9
   │  Bulkheaded thread pools     │ ← 8.8
   │  Load shedding on overload   │
   └──────────────┬───────────────┘
                  │
                  ▼
           Downstream services
```

**Interview one-liner for the whole section**
> "Rate limits enforce quotas at the edge. Timeouts, retries with jitter, circuit breakers, bulkheads, and backpressure defend against downstream failure. Together they turn every outage from a cascade into a graceful degradation."

---

*Next up: **See [02-databases.md](./02-databases.md)** — SQL vs NoSQL, indexing, transactions & isolation, replication, sharding.*
