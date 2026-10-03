# API Gateway — WebSocket Routing

Spring Cloud Gateway handles WebSocket routes natively via HTTP Upgrade. Same
route table, same auth (TokenRelay), same gateway JVM — no separate proxy needed.

Companion: [cors.md](cors.md) (same build session).

---

## 1. How it works

```
Client                Gateway                     Downstream (WS server)
                                                  (user-service :8090)
  │                                                    │
  │──HTTP Upgrade────▶│                                │
  │  GET /ws/echo     │                                │
  │  Upgrade: websocket                                │
  │  Authorization: Bearer …                           │
  │                   │──HTTP Upgrade──────────────────▶│
  │                   │  (TokenRelay forwards Bearer)  │
  │                   │◀─101 Switching Protocols───────│
  │◀──101 Switching──│                                 │
  │      Protocols    │                                │
  │                   │                                │
  │═══bidirectional websocket frames tunneled═════════│
```

Spring Cloud Gateway recognizes `lb:ws://` or `ws://` in a route URI as the
signal to handle HTTP Upgrade. Netty (the underlying transport) supports both
HTTP and WebSocket on the same connection natively.

---

## 2. URI schemes

```
lb:ws://<service-name>   — load-balance via Eureka; upgrade to WebSocket
lb:wss://<service-name>  — same, TLS
ws://host:port           — direct URI, no load balancing
wss://host:port          — direct, TLS
```

Our route:
```yaml
- id: websocket-echo
  uri: lb:ws://user-service
  predicates:
    - Path=/ws/**
```

The gateway:
1. Resolves `user-service` via Eureka
2. Picks a healthy instance
3. Opens a WebSocket to that instance's `/ws/echo`
4. Tunnels frames both ways

---

## 3. Which filters work on WS routes

WebSocket is NOT a request/response protocol — it's a long-lived bidirectional
stream. Most gateway filters don't fit:

```
❌ Retry           — WS is a one-shot upgrade; retrying makes no sense
❌ CircuitBreaker  — hard to define "failure" for a bidirectional stream
❌ BodyLogging     — no request body in the traditional sense
❌ IdempotencyKey  — one connection, not repeatable requests
❌ ResponseCache   — nothing to cache
❌ Bulkhead        — could cap concurrent connections but not integrated

✅ RateLimiter     — can limit connection setup rate
✅ AddTenantHeader — headers pass through the upgrade
✅ TokenRelay      — Authorization passes through
✅ RequestFingerprint — could hash the upgrade request (uncommon)
```

Rule of thumb: **strip most filters on WS routes**. Our config uses just
`TokenRelay` — Authorization Bearer forwarded to downstream so its Spring
Security can authenticate the upgrade.

---

## 4. Auth for WebSockets

```
┌─────────────────────────────────────────────────────────────────────┐
│  Auth mechanism        Works on WS?    How                          │
│  ──────────────       ────────────    ──                            │
│  Authorization header  ✓ CLI clients   Sent on upgrade request      │
│                        ✗ Browsers      WebSocket constructor can't  │
│                                        set arbitrary headers        │
│                                                                     │
│  Cookie session        ✓ Both          Browsers send cookies on WS  │
│                                        upgrade automatically        │
│                                                                     │
│  Query param token     ⚠ Both          Works but token leaks to     │
│                                        access logs — avoid          │
│                                                                     │
│  Subprotocol token     ✓ Both          Custom Sec-WebSocket-        │
│                                        Protocol header — advanced   │
└─────────────────────────────────────────────────────────────────────┘
```

Our setup: **TokenRelay + Authorization header** — works perfectly for
`wscat` / server-side / mobile clients. Browsers need cookie-based auth
if you want same-origin behavior — extension parked.

The gateway's `TokenRelay` filter reads the JWT from Spring Security's
authentication context and adds `Authorization: Bearer <token>` to the
outbound upgrade request.

---

## 5. Architecture

```
                    ┌──────────────────────────────────────────────────┐
                    │  API Gateway :8080                               │
                    │                                                  │
Client              │  Routes:                                         │
(wscat / browser)   │    /api/v1/**   → lb://user-service    (HTTP)    │
─Upgrade──▶         │    /ws/**       → lb:ws://user-service (WS)      │
                    │                     │                            │
                    │  Filters on /ws/**: TokenRelay only              │
                    │                                                  │
                    └────────────────────┼─────────────────────────────┘
                                         │
                                         │ HTTP Upgrade → WS
                                         ▼
                    ┌──────────────────────────────────────────────────┐
                    │  user-service :8090 (Tomcat + Spring MVC)        │
                    │                                                  │
                    │  Servlet WebSocket via spring-boot-starter-      │
                    │  websocket:                                      │
                    │    @EnableWebSocket                              │
                    │    WebSocketConfigurer registers /ws/echo        │
                    │    EchoWebSocketHandler extends TextWebSocketHandler│
                    │                                                  │
                    │  Same Spring Security still applies to /ws/**    │
                    │  → JWT required (TokenRelay provides it)         │
                    └──────────────────────────────────────────────────┘
```

---

## 6. Files added / changed

```
api-gateway/
└── src/main/resources/
    └── application.yml                                (+ /ws/** route with lb:ws://)

user-service/
├── pom.xml                                            (+ spring-boot-starter-websocket)
└── src/main/java/com/example/userservice/websocket/   (NEW package)
    ├── EchoWebSocketHandler.java                      (NEW — TextWebSocketHandler)
    └── WebSocketConfig.java                           (NEW — @EnableWebSocket + register /ws/echo)

docs/microservices/api-gateway/
└── websocket.md                                       (this file)
```

user-service now handles both HTTP REST and WebSocket on the same port
(`:8090`). Tomcat auto-configures for both when `spring-boot-starter-websocket`
is on the classpath.

---

## 7. Verification

### 7.1 wscat (CLI, supports Authorization header)

```bash
npm install -g wscat  # if not installed

TOKEN=$(curl -s -u admin:admin123 \
  -d "grant_type=client_credentials&scope=read" \
  http://localhost:9010/oauth2/token | jq -r .access_token)

wscat -c "ws://localhost:8080/ws/echo" \
      -H "Authorization: Bearer $TOKEN"

# Connected (press CTRL+C to quit)
# > hello
# < echo@2026-09-30T12:00:00.123Z: hello
# > world
# < echo@2026-09-30T12:00:01.456Z: world
```

Successful path:
1. wscat opens HTTP connection to gateway `:8080`
2. Sends `Upgrade: websocket` + `Authorization: Bearer ...`
3. Gateway matches `Path=/ws/**` → route id `websocket-echo`
4. TokenRelay forwards Bearer
5. Gateway opens WS to user-service `:8090/ws/echo`
6. user-service's Spring Security validates JWT (JwtDecoder from auth-server)
7. Handshake succeeds → 101 Switching Protocols
8. Frames tunnel bidirectionally

### 7.2 Without auth → 401 on upgrade

```bash
wscat -c "ws://localhost:8080/ws/echo"
# error: Unexpected server response: 401
```

The gateway allows the upgrade attempt (Path=/ws/** matches), but downstream
user-service's SecurityFilterChain requires JWT. `.anyRequest().authenticated()`.

### 7.3 Direct to downstream (bypassing gateway) — sanity check

```bash
wscat -c "ws://localhost:8090/ws/echo" \
      -H "Authorization: Bearer $TOKEN"
# Same behavior as via gateway — confirms user-service works standalone
```

If direct works but gateway-routed doesn't, problem is in the gateway route.
If neither works, problem is in user-service or auth.

### 7.4 Browser DevTools

```javascript
// Note: browser WebSocket constructor CAN'T set arbitrary headers,
// so this fails against the JWT-authenticated /ws/echo:
const ws = new WebSocket('ws://localhost:8080/ws/echo');
ws.onmessage = e => console.log('recv:', e.data);
ws.onclose = e => console.log('closed:', e.code, e.reason);
ws.send('hello');
// → closes immediately with 1006 or similar
```

For browser use, either:
- Permit `/ws/echo` without auth (bad for prod, fine for demo)
- Use cookie-based auth (send session cookie automatically)
- Use custom subprotocol to smuggle the token

Extension parked.

### 7.5 Gateway logs during a WS session

Enable debug logs to see the routing decision + upgrade:

```yaml
logging:
  level:
    org.springframework.cloud.gateway: DEBUG
    reactor.netty.http.client: DEBUG
```

Look for:
```
Handling WebSocketRequest to ws://localhost:8090/ws/echo
```

user-service logs (from EchoWebSocketHandler):
```
WS connected: id=abc-123 remote=/127.0.0.1:54321 auth=Bearer***
WS closed: id=abc-123 status=NORMAL
```

---

## 8. Failure modes & mitigations

| Scenario | Behavior | Mitigation |
|---|---|---|
| Downstream not registered in Eureka | Gateway can't resolve `lb:ws://user-service` | Verify with `curl http://localhost:8761/eureka/apps/USER-SERVICE` |
| Auth header not forwarded | Downstream returns 401 on upgrade | Ensure `TokenRelay` is in the WS route's filter list |
| CircuitBreaker in WS route | Upgrade may hang / fail unexpectedly | Remove CB from WS routes |
| BodyLogging on WS route | Attempts to buffer the "body" — fails or hangs | Ensure BodyLoggingGlobalFilter excludes /ws/** or Content-Type check catches it (currently handled by non-text Content-Type) |
| Long-lived connection + downstream restart | WS session dies without heartbeat | Application-level ping/pong (JavaScript setInterval sending 'ping') |
| Sticky sessions | Client reconnects hit a different downstream — no state | For stateful WS apps, use session affinity (extra work) OR make WS handlers stateless |
| CORS on WebSocket | Browser applies a DIFFERENT origin check (not standard CORS) | `setAllowedOriginPatterns("*")` in WebSocketConfig for permissive dev; restrict in prod |
| Filter breaks upgrade | 500 or timeout on upgrade | Check filter list — only Token Relay + safe filters on WS routes |
| Load-balancing across replicas | Client's next reconnect may hit a different pod | For distributed pubsub-style WS: put a shared broker (Redis pub/sub, RabbitMQ) between pods |

---

## 9. Interview cheat-sheet

| Question | Answer |
|---|---|
| Does Cloud Gateway handle WebSockets? | Yes, natively via HTTP Upgrade. Route with `lb:ws://` or `ws://` URI. Same JVM handles both HTTP and WS. |
| What's the URI scheme? | `lb:ws://<service>` for load-balanced; `ws://host:port` for direct. `lb:wss://` / `wss://` for TLS. |
| Which filters work on WS routes? | RateLimiter (connection rate), TokenRelay (auth forward), AddTenantHeader. NOT Retry, CircuitBreaker, body-modifying filters. |
| Why don't Retry/CB apply? | WS is not request/response — it's a long-lived bidirectional stream. "Failure ratio in a window" doesn't map to a persistent connection. |
| How does auth work? | Authorization header on the upgrade request (CLI clients); cookie session (browsers). TokenRelay forwards the Bearer. |
| Browser vs CLI auth difference? | Browser `WebSocket` constructor can't set arbitrary headers — no Authorization. Options: cookie session, query param (leaky), subprotocol trick, or same-origin bypass. |
| WebSocket load balancing? | `lb:ws://<service>` integrates with Eureka. Client connects to gateway; gateway picks an instance. Downstream is sticky for the duration of that connection. |
| Long-lived connections + CB? | Doesn't make sense — CB is for request/response failure ratios. Bulkhead COULD limit concurrent WS connections but isn't wired here. |
| WebSocket + CORS? | Browsers apply a DIFFERENT origin check (not standard CORS). Handled via `setAllowedOriginPatterns` in the WebSocketConfig. Server-to-server WS ignores it. |
| Reconnection strategy? | Client-side responsibility. Common: exponential backoff, jitter, cap. Server-side: idempotent connection setup, no session state assumed on server. |
| Backpressure on WS? | Reactor-based reactive WS handlers get automatic backpressure. Our TextWebSocketHandler (servlet) doesn't — Spring's `WebSocketSession.sendMessage()` blocks if the buffer is full. |
| Distributed WS pub/sub? | Need a shared broker (Redis pub/sub, RabbitMQ). Each gateway pod holds N connections; broker fans out messages across all pods. |

---

## 10. Common pitfalls (interview probes)

1. **Using `lb://` (not `lb:ws://`)** for a WS route — gateway treats it as HTTP, upgrade fails.
2. **Adding CircuitBreaker to WS route** — upgrade hangs or fails weirdly. Strip resilience filters from WS routes.
3. **Browser can't send Bearer** — common surprise. WebSocket constructor is very limited on headers. Use cookies OR subprotocol.
4. **Origin check on WebSocket** — `setAllowedOriginPatterns` (WebSocket) is DIFFERENT from CORS (HTTP). Two mechanisms, two config surfaces.
5. **Long-lived connection through cloud LB** — LBs often kill idle TCP after 60s. Application-level ping/pong keeps it alive.
6. **State on the WS handler** — makes horizontal scale hard. Each connection lives on one pod; if client reconnects and lands elsewhere, state is gone. Externalize state (Redis) OR use sticky sessions.
7. **Blocking work in TextWebSocketHandler.handleTextMessage** — blocks the container thread. For heavy work: dispatch to an executor.
8. **Assuming CORS covers WebSocket** — no. Browser applies separate origin check via `Origin` header, subject to server's `setAllowedOriginPatterns`.

---

## 11. Extensions (parked)

- **STOMP over WebSocket** — Spring's `@EnableWebSocketMessageBroker` for pub/sub, prefixes, `@MessageMapping` handlers. Higher-level protocol on top of raw WS.
- **Server-Sent Events (SSE) alternative** — unidirectional server→client. Simpler than WS when you don't need client→server messages.
- **Reactive WebSocket** — swap servlet Tomcat for Reactor Netty WebSocket handler for backpressure semantics.
- **Cookie-based auth for browsers** — session cookie automatically sent on WS upgrade. Requires backend session store.
- **Sticky sessions** — configure load balancer for session affinity so reconnects hit the same pod (only useful with stateful handlers).
- **Distributed pub/sub broker** — Redis pub/sub, RabbitMQ, or Kafka between pods so a message received on pod A is delivered to a client on pod B.
- **Heartbeat / keepalive** — application-level ping every N seconds to prevent LB idle timeout.
- **Rate-limit WS connection setup** — RateLimiter filter on the /ws/** route, keyed by IP or user. Prevents connection floods.
- **WS metrics** — Micrometer gauges for `websocket.connections.active`, counters for `websocket.messages.received`.
- **Chat demo service** — replace echo with a real broadcast handler using Redis pub/sub for multi-pod message fanout.
