# Request flow — browser to service

Sequence diagram showing a single API call hopping through every
component. Example: `GET /api/v1/users` landing in `user-service`.

```mermaid
sequenceDiagram
    autonumber
    participant C as Browser / curl
    participant G as api-gateway<br/>:8080
    participant A as auth-server<br/>:8095
    participant E as eureka-server<br/>:8761
    participant U as user-service<br/>:8081
    participant M as mysql-shared<br/>:3306

    C->>G: GET /api/v1/users<br/>(Basic admin:admin123)
    G->>A: Validate JWT
    A-->>G: ✓ OK
    G->>E: Lookup USER-SERVICE
    E-->>G: localhost:8081
    G->>U: GET /users
    U->>M: SELECT * FROM user
    M-->>U: rows
    U-->>G: JSON array
    G-->>C: 200 OK
```

**Steps 1-3 — auth boundary:** gateway validates the Basic token
against auth-server; if the JWT is bad, request stops here with 401.

**Steps 4-5 — discovery:** gateway doesn't know *where* user-service
lives; it asks Eureka for an instance URL.

**Steps 6-10 — downstream:** gateway proxies the request. User-service
hits MySQL, returns JSON up the chain.

See the ASCII version in [`setup/00-first-time-setup.md`](../setup/00-first-time-setup.md#5-·-verify-the-service-registered).
