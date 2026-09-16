# System Design — Reliability, Security & Ops

Section 13 of the study series. Continues from [05-microservices.md](./05-microservices.md).

Sections 1–12 built the system. Section 13 keeps it **alive and safe** — measuring reliability, surviving the failure modes distributed systems inevitably produce, recovering from disasters, and defending against attack.

---

## 13. Reliability, Security & Ops

### 13.1 SLA / SLO / SLI — the reliability contract

You saw the definitions briefly in Section 12.7. Here's the full mental model.

**The three terms**

```
   SLI  — Service Level Indicator
          "the number we actually measure"
          e.g. % of requests returning 2xx in < 300ms

   SLO  — Service Level Objective
          "the internal target we commit to"
          e.g. 99.9% of requests meet the SLI over 30 days

   SLA  — Service Level Agreement
          "the customer contract, usually looser than the SLO"
          e.g. 99.5% or refund
```

Relationship: **SLA < SLO < reality**. You set SLOs stricter than the SLA so you have room to detect and fix issues before customers notice.

**Common SLI categories**

```
   Availability:    good_requests / total_requests
   Latency:         % requests < N ms
   Throughput:      requests handled / time
   Correctness:     % results matching expected
   Freshness:       data staleness under X seconds
   Durability:      probability data survives (11 nines for S3)
```

**Error budget — the killer concept**

```
   SLO = 99.9% success over 30 days
   
   Error budget = 100% - 99.9% = 0.1%
   Over 30 days ≈ 43 minutes of "allowed" downtime.
   
   Spend it on:
     ▸ Risky deploys
     ▸ Chaos experiments
     ▸ Migrations
   
   Depleted → freeze risky work, focus on reliability.
```

Google popularized this. It aligns dev velocity with reliability without endless arguments.

**"Nines" reference table**

```
   Availability   Downtime/year    Downtime/month
   ────────────   ─────────────    ──────────────
   99%            3.65 days        7.2 hours
   99.9%          8.76 hours       43.2 min
   99.95%         4.38 hours       21.6 min
   99.99%         52.6 min         4.32 min
   99.999%        5.26 min         26 sec
   99.9999%       31.5 sec         2.6 sec
```

Each extra nine costs 10× more infra + operational discipline. Match the number to what your users need.

**Interview one-liner**
> "SLIs are what you measure, SLOs are what you commit to, SLAs are what you contract. Error budgets turn reliability into a currency you spend on velocity — deploy fast until you run out, then slow down."

---

### 13.2 Failure modes — the classic pathologies

Distributed systems fail in predictable patterns. Naming them is half the cure.

**Cascading failure**

```
   Downstream Y gets slow.
     │
     ▼
   Caller X's threads pile up waiting on Y.
     │
     ▼
   X can't handle new requests → X gets slow.
     │
     ▼
   X's callers pile up → THEY get slow.
     │
     ▼
   Whole system melts. Root cause is buried under symptoms.
```

**Fixes** (Section 8 resilience): timeouts, circuit breakers, bulkheads, load shedding.

**Thundering herd**

```
   Cache expires → 10,000 concurrent clients see MISS →
   all hit DB simultaneously → DB melts.
   
   Or:  service restarts → all clients reconnect at once → auth server melts.
```

**Fixes:** single-flight locks, jittered TTLs, jittered retries, exponential backoff.

**Retry storm**

```
   Downstream flaps for 2 seconds.
   Every client retries immediately.
   Downstream can't recover — it's still under retry load.
   Retries retry the retries.
```

**Fixes:** exponential backoff + jitter, retry budgets, circuit breakers, adaptive concurrency.

**Metastable failure (Google research term)**

```
   System is healthy → hits a load spike → falls into a degraded state →
   stays degraded even after the spike is gone.
   
   Example: latency rises → clients retry more → more load → higher latency → ...
   
   No self-healing without operator intervention (drain queues, reset caches).
```

**Fixes:** load shedding, circuit breakers, adaptive concurrency, priority queues.

**Grey failure**

```
   Node is neither fully up nor fully down.
   It responds to health checks (200 OK on /health)
   but real requests fail or time out.
   
   Health checks say "fine". Users say "broken".
```

**Fixes:** deep health checks (actually query DB, dependencies), synthetic monitoring from user's perspective, active probing.

**Poison messages**

```
   Message that consistently crashes the consumer.
   Consumer retries → crashes → retries → forever.
   Queue backs up, other messages starved.
```

**Fixes:** dead-letter queue after N failures, timeout per message, error isolation.

**Head-of-line blocking (HoL)**

```
   Queue with strict order.
   One slow item at the front blocks everyone behind it.
   
   Fair-queue A B C D  → A is slow → B, C, D wait forever.
```

**Fixes:** parallel consumers, out-of-order processing where allowed, timeouts per item, priority queues.

**Split brain**

Already covered in Section 11.9 — two leaders both accepting writes during a partition. Fix: quorum + fencing tokens.

**Correlated failures**

```
   You have 3 DB replicas. Feels safe (99.9%^3 ≈ 5 nines).
   But all 3 are in the same rack, same power grid, same AZ.
   → 1 fault takes them all out.
   
   You thought failures were independent. They weren't.
```

**Fixes:** spread across racks, AZs, regions. Chaos test to verify.

**Interview one-liner**
> "Failures in distributed systems come in shapes: cascading, thundering herd, retry storm, metastable, grey, poison, HoL, split brain, correlated. Each has a canonical fix — timeouts, jitter, DLQs, quorum. Know the shapes and you'll find the fix fast."

---

### 13.3 Chaos engineering — breaking things on purpose

**The idea**
Prod will break. Better to break it under controlled conditions with observers watching, than in the middle of the night with pagers going off.

**The chaos loop**

```
   ① Form a hypothesis:
        "Killing 30% of order-svc pods won't affect P99 latency."
   
   ② Define blast radius:
        Start in staging, one region, a subset of users.
   
   ③ Inject the fault:
        Kill pods, drop packets, throttle DB, inject 500s.
   
   ④ Measure:
        Did SLOs hold? What broke?
   
   ⑤ Learn, fix, repeat.
```

**Categories of faults**

```
   ▸ Compute:    kill process, exhaust CPU / memory, kill container / VM
   ▸ Network:    drop packets, delay, partition, DNS fail
   ▸ Storage:    slow disk, disk full, corrupt read
   ▸ Dependency: return 500, timeout, garbage payload, refuse connections
   ▸ Time:       clock skew, NTP jump
```

**Tools**
- **Netflix Chaos Monkey** — random pod kills.
- **Litmus / Chaos Mesh** — Kubernetes-native, rich fault library.
- **Gremlin** — commercial, big fault menu.
- **Toxiproxy** — TCP-level chaos for tests.

**Game days**
Structured team exercise. "Simulate a payment-svc outage — respond to the incident as if it were real." Builds runbooks and muscle memory.

**Prerequisite: observability**
No point breaking things if you can't see what happened. Chaos without observability is just an outage.

**Interview one-liner**
> "Chaos engineering makes failure practice cheap. Small hypothesis, small blast radius, measured outcome. It exposes hidden coupling and grows organizational readiness."

---

### 13.4 Multi-region & disaster recovery

Cross-region is expensive. But single-region means one bad AZ event = full outage.

**Active-Passive (warm standby)**

```
   Region A (primary)     Region B (standby)
   ┌────────────┐         ┌────────────┐
   │  Serving   │         │  Idle      │
   │  100%      │         │  scaled-down│
   │  traffic   │◄──async─│  data replica│
   └────────────┘  repl.  └────────────┘
                                │
   Failover:  DNS / global LB flips A → B.
   Startup:   scale up B, promote replica to primary.
```

- ✅ Simpler, cheaper (B mostly idle).
- ❌ Failover takes minutes → tens of minutes.
- ❌ Replication lag → some data loss possible.

**Active-Active**

```
   Region A                 Region B
   ┌────────────┐           ┌────────────┐
   │  Serving   │◄────────► │  Serving    │
   │  50% users │  bi-repl  │  50% users  │
   │            │           │             │
   └────────────┘           └────────────┘
                    │             │
   Global LB / GeoDNS routes users to nearest healthy region.
   
   Failover: LB drains the sick region; the other absorbs 100%.
```

- ✅ Near-zero failover time.
- ✅ Lower latency (users hit nearest).
- ❌ Multi-master data model needed (or partition by user).
- ❌ Conflict resolution, harder to reason about.

**RTO and RPO — the two DR numbers**

```
   RTO — Recovery Time Objective:
        "How long can we be down?"
        e.g. 15 minutes
   
   RPO — Recovery Point Objective:
        "How much data can we afford to lose?"
        e.g. 5 minutes' worth
```

Visualized on a disaster timeline:

```
   ────────── normal ──────────┤ DISASTER ├────── recovery ───────►
                                │
                     ← RPO ─────┤              ← RTO ────►
                                │                        │
                    Data loss window            Downtime window
                    (from last replicated       (until service is back)
                     write)
```

Setting them:
- Payment / order data:   **RPO ≈ 0, RTO minutes.** Requires sync replication + hot standby.
- Analytics / logs:        **RPO hours, RTO hours.** Async replication, cold standby fine.
- Content catalog:         **RPO minutes, RTO minutes.** Async replication, warm standby.

**DR strategy tiers**

```
   Backup only        Restore from cold backup. RTO hours-days.
                      Cheapest.
   
   Pilot light        Core infra always running, scaled down.
                      Scale up on failover. RTO ~30 min.
   
   Warm standby       Full stack running at reduced capacity.
                      Scale up on failover. RTO ~5-15 min.
   
   Active-active      Both regions live. RTO seconds.
                      Most expensive.
```

**Data replication modes recap**

```
   Sync replication:   write returns after remote ack.
                       RPO = 0, but write latency = network RTT.
                       Feasible within a region, painful across regions.
   
   Async replication:  write returns immediately, remote catches up.
                       RPO = replication lag (~seconds).
                       Standard for cross-region.
   
   Semi-sync:          write returns after ONE remote confirms.
                       Balance between the two.
```

**Testing DR**
Untested DR = no DR. Practice failovers regularly. Netflix runs region evacuations as normal drills.

**Interview one-liner**
> "Multi-region means picking your poison: active-active is fast but complex; active-passive is simple but slow to fail over. RTO and RPO translate the business's downtime and data-loss tolerance into concrete architecture — replication mode, hot/warm/cold standby, DNS strategy."

---

### 13.5 Data durability and backups

**Availability != Durability**

```
   Availability — is my data reachable RIGHT NOW?
   Durability   — will my data still exist next year, after crashes,
                  fires, ransomware, human error?
```

S3's famous "11 nines of durability" (99.999999999%) is about data *surviving*, not *being reachable at every moment*.

**Backup strategy — the 3-2-1 rule**

```
   3 copies of data
   2 different storage types
   1 copy off-site (or off-cloud)
   
   Protects against: hardware failure, region outage, ransomware, bad human.
```

**Common backup mistakes**
- Backups on the same account/region as primary. Ransomware / IAM error takes both.
- Never testing restores. "Backup" that can't restore is a false sense of security.
- Retaining backups forever. Costs money; violates data-retention rules.
- Backups without encryption. Leaks are backups' main risk.

**Recovery from human error**
```
   Prod DB deleted at 03:12:47 UTC.
   Point-in-time restore to 03:12:46.
   Replay WAL / binlog up to that point.
   
   Requires: PITR-capable DB or continuous WAL archival (Postgres wal-g,
             MySQL binlog to S3, DynamoDB PITR).
```

**Interview one-liner**
> "Durability is separate from availability. 3-2-1 keeps data safe. Backups you never restore aren't backups; PITR turns 'deleted at 3AM' from career-ending into a Slack apology."

---

### 13.6 Security — the mental model

The security field is huge. For a system-design interview, know these axes:

```
   ┌─────────────────────────────────────────────────────────┐
   │ Confidentiality  — only allowed parties see the data    │
   │ Integrity        — data isn't altered                   │
   │ Availability     — service is reachable (DDoS defense)  │
   │ Authentication   — who is this                          │
   │ Authorization    — what can they do                     │
   │ Auditability     — what happened, and by whom          │
   │ Non-repudiation  — they can't deny doing it            │
   └─────────────────────────────────────────────────────────┘
```

**Defense in depth**

```
   Assume any single layer will fail. Stack them.
   
                    ┌─── User ───┐
                    │            │
                    ▼            │
             ┌────────────┐      │
             │ CDN / WAF  │  ← filter bad traffic, DDoS
             └──────┬─────┘      │
                    ▼            │
             ┌────────────┐      │
             │ TLS / mTLS │  ← in-transit encryption
             └──────┬─────┘      │
                    ▼            │
             ┌────────────┐      │
             │ Auth / z   │  ← identity + policy
             └──────┬─────┘      │
                    ▼            │
             ┌────────────┐      │
             │ App code   │  ← input validation, safe defaults
             └──────┬─────┘      │
                    ▼            │
             ┌────────────┐      │
             │ Secrets vlt│  ← no plaintext creds
             └──────┬─────┘      │
                    ▼            │
             ┌────────────┐      │
             │ DB + KMS   │  ← at-rest encryption
             └────────────┘      │
                                 │
   Monitoring, audit logs, IAM, network segmentation wrap ALL layers.
```

Attacker breaks one layer → the others still hold.

---

### 13.7 Encryption — at rest and in transit

**In transit — the wire**
```
   TLS 1.2+ between all clients and services.
   mTLS between internal services (service mesh).
   
   Rule: no plaintext outside your process memory.
```

**At rest — the disk**
```
   Layers where encryption happens:
   
   Application    → field-level encryption for sensitive columns
                    (SSN, tokens). Key from KMS.
   
   Database       → transparent data encryption (TDE)
                    Postgres pgcrypto, MySQL InnoDB TDE
   
   Disk / Volume  → EBS / GCP PD encryption (automatic).
   
   Hardware       → self-encrypting drives.
```

**Key hierarchy — envelope encryption**

```
   ┌────────────────────────────┐
   │ KMS master key (KEK)       │  ← never leaves KMS
   │  (root of trust)           │
   └─────────────┬──────────────┘
                 │ encrypts
                 ▼
   ┌────────────────────────────┐
   │ Data encryption key (DEK)  │  ← generated per-record / per-file
   │  (stored encrypted alongside data) │
   └─────────────┬──────────────┘
                 │ encrypts
                 ▼
   ┌────────────────────────────┐
   │ Actual data                │
   └────────────────────────────┘
   
   Rotate KEK → re-encrypt DEKs (fast) → data never re-encrypted.
```

Used by AWS KMS, GCP KMS, HashiCorp Vault.

**Key rotation**
- KEK rotated periodically (yearly).
- DEK rotated per-object or on schedule.
- Compromised KEK ≠ data compromise (yet), if DEKs are rotated.

**Hashing vs encryption vs signing**

```
   Hashing:     one-way. Used for passwords, integrity checks.
                bcrypt, argon2, SHA-256.
   Encryption:  reversible with the key.
                AES-GCM, ChaCha20-Poly1305.
   Signing:     asymmetric authentication of a message.
                RSA-PSS, ECDSA, Ed25519.
```

**Passwords, specifically**
- ❌ Plain text.
- ❌ MD5 / SHA-1 / SHA-256 (too fast; brute-forceable).
- ✅ **bcrypt, scrypt, argon2** — deliberately slow, salted. Modern default: argon2id.

**Interview one-liner**
> "Encrypt in transit with TLS, at rest with KMS-backed envelope encryption. Rotate keys, never keys-in-Git, never SHA-256 for passwords."

---

### 13.8 Secrets management

**The problem**
Services need DB passwords, API keys, cert private keys. Where do they live?

**Bad → better ladder**

```
   ❌ Hardcoded in code            (in Git = permanent leak)
   ❌ In .env files in the repo    (accidentally committed)
   ⚠️  In Kubernetes Secret YAMLs  (base64, not encrypted; ok if RBAC-scoped)
   ✅ In a secrets manager         (Vault, AWS Secrets Manager, GCP Secret Manager)
   ✅✅ Short-lived, auto-rotated  (AWS IAM roles, Vault dynamic secrets)
```

**Dynamic secrets — the pinnacle**
```
   App requests DB credentials from Vault.
   Vault creates a new DB user with 1h TTL.
   Returns credentials to the app.
   
   After 1h, credentials auto-expire. App requests new ones.
   
   Compromise → attacker's window is minutes, not months.
```

**Machine identity — SPIFFE / SPIRE**
```
   Every workload gets a signed identity (X.509 SVID or JWT-SVID).
   No shared secrets between services.
   Basis of zero-trust between services.
```

**Interview one-liner**
> "Secrets in Git are game over. Secrets in env vars are ok until they leak. Secrets manager + short TTLs + machine identity is the modern floor."

---

### 13.9 Threat modeling — thinking like an attacker

**STRIDE — the classic framework**

```
   Spoofing              — impersonating identity
   Tampering             — modifying data in transit / rest
   Repudiation           — denying you did it (fix: audit logs)
   Information disclosure — leaking data
   Denial of service     — making service unavailable
   Elevation of privilege — becoming admin from user
```

Walk your data flow diagram and ask STRIDE for each arrow. Cheap and revealing.

**Trust boundaries**
```
   Internet
   ────────────────
   Where untrusted crosses into trusted (DMZ, load balancer)
   ────────────────
   Between microservices (assume compromised)
   ────────────────
   App to DB (least-privilege user)
   ────────────────
   Between accounts/tenants (multi-tenant isolation)
```

At every boundary, do:
- Authentication (who's on the other side?)
- Authorization (what may they do?)
- Input validation (what did they send?)

**Least privilege**
Every process, service, and user has ONLY the permissions they need. No shared admin credentials. No wildcard IAM roles.

**Zero-trust**
Assume every network is hostile, including the internal one. Every hop authenticates. mTLS + policy engines make this real.

---

### 13.10 OWASP Top 10 — the classic web attack list

Every web dev must know these. Latest OWASP Top 10 (paraphrased):

```
   A01  Broken access control
        e.g. /users/42 works if you change 42 to any ID (IDOR).
        Fix: enforce authZ on every request, never trust client-supplied IDs.
   
   A02  Cryptographic failures
        e.g. TLS 1.0, MD5 password hashes, secrets logged.
        Fix: modern TLS, argon2, never log secrets.
   
   A03  Injection (SQLi, command, LDAP, template)
        e.g. "SELECT * FROM users WHERE name='" + input + "'"
        Fix: parameterized queries, escape output, safe templating.
   
   A04  Insecure design
        e.g. no rate limit on password reset, no MFA on admin.
        Fix: threat model before building.
   
   A05  Security misconfiguration
        e.g. debug endpoints exposed, S3 buckets public.
        Fix: hardened baselines, config audits, IaC scanning.
   
   A06  Vulnerable/outdated components
        e.g. old Log4j (log4shell).
        Fix: Dependabot / Snyk / OWASP Dependency Check.
   
   A07  Identification / authentication failures
        e.g. no rate-limit on login, weak passwords accepted.
        Fix: MFA, account lockout with care, secure session management.
   
   A08  Software and data integrity failures
        e.g. unsigned updates, untrusted deserialization.
        Fix: signed artifacts, safe deserialization allowlists.
   
   A09  Security logging & monitoring failures
        e.g. no logs of auth failures, no alerting.
        Fix: audit logs, SIEM, alerts.
   
   A10  Server-Side Request Forgery (SSRF)
        e.g. user gives URL, server fetches it — attacker points to internal.
        Fix: URL allowlist, block private CIDRs, metadata endpoints.
```

**SQL injection example (still #1 in interviews)**

```
   ❌ String query = "SELECT * FROM users WHERE name = '" + input + "'";
   
       Input:  ' OR '1'='1
       → SELECT * FROM users WHERE name = '' OR '1'='1'
       → returns EVERY user.
   
   ✅ PreparedStatement ps = conn.prepareStatement(
          "SELECT * FROM users WHERE name = ?");
       ps.setString(1, input);
   
       DB engine treats input as pure DATA, not code.
```

**JPA / Hibernate**

```
   ❌ em.createQuery("FROM User u WHERE u.name = '" + input + "'")
   ✅ em.createQuery("FROM User u WHERE u.name = :name")
         .setParameter("name", input)
```

**XSS (Cross-Site Scripting)**

```
   User posts:  <script>steal(document.cookie)</script>
   Rendered:    <div>{{unsafe user content}}</div>
   
   Every user viewing the page runs the attacker's JS.
   
   Fix: HTML-escape output. Use frameworks that escape by default
        (React, Thymeleaf, Vue).
```

**CSRF (Cross-Site Request Forgery)**

```
   User is logged into bank.com.
   Visits attacker.com, which submits a form to bank.com/transfer.
   Browser sends bank.com cookies → transfer happens.
   
   Fix: SameSite=Lax cookies, CSRF tokens, or reject cross-site POSTs.
```

**Interview one-liner**
> "Broken access control and injection remain #1 and #3 for a reason. Parameterize every query, escape every output, enforce authZ on every request, never trust client input, keep dependencies patched, and audit-log everything security-relevant."

---

### 13.11 DDoS defense

**Layers of protection**

```
   L3/L4 (volumetric)   ← Cloudflare / AWS Shield absorb tens of Gbps
                          Anycast + scrubbing centers
   
   L7 (application)     ← WAF filters bad URL patterns
                          rate limiting per IP / cookie
                          bot detection (JS challenges, CAPTCHA)
   
   Origin protection    ← Origin hidden behind CDN; direct IP blocked
                          Only CDN's IPs allowed to hit origin
```

**Common attack shapes**
- **SYN flood** — half-open TCP connections. Fix: SYN cookies at kernel level.
- **Amplification** (DNS, NTP, memcached) — attacker spoofs source IP; reflector floods victim. Fix: ingress filtering, CDN absorption.
- **HTTP flood** — real-looking GETs at scale. Fix: WAF + rate limit + captcha.
- **Slowloris** — many slow connections tying up threads. Fix: reverse proxy with per-connection timeouts.
- **Layer-7 targeted** — hit expensive endpoints (search, checkout). Fix: per-endpoint rate limits + cache.

---

### 13.12 Incident response — before, during, after

**Before**
- Runbooks per service.
- On-call rotation + escalation paths.
- Alerts based on SLOs, not raw metrics.
- Chaos-tested playbooks.

**During**

```
   ① Detect      — alerts fire, someone acknowledges.
   ② Triage      — assess scope, severity.
   ③ Communicate — update status page, chat channel.
   ④ Mitigate    — rollback, feature flag off, scale up, drain region.
   ⑤ Resolve     — issue cleared, monitoring back to green.
```

**Roles**
- **Incident commander (IC)** — makes decisions, doesn't do work.
- **Comms lead** — updates stakeholders.
- **Ops lead** — hands on the keyboard.

Small incidents: one person does all three. Big incidents: split.

**After — blameless postmortem**

```
   ▸ Timeline of events
   ▸ Root cause (or contributing causes; often multiple)
   ▸ What went well
   ▸ What went badly
   ▸ Action items with owners + due dates
   
   Rule: no naming, no shaming. Focus on systems, not humans.
```

Google's philosophy: "If humans caused the outage, the system permitted it. Fix the system."

**Interview one-liner**
> "Prepare runbooks, alert on SLO burn, separate the commander from the doer, communicate constantly, and write blameless postmortems. Reliability grows from learning, not from blame."

---

### 13.13 The Java / Spring angle

**Reliability**
- `spring-boot-starter-actuator` — health, metrics, info endpoints.
- `Resilience4j` — timeouts, retries, breakers, bulkheads, rate limits.
- Micrometer histograms → SLO burn alerts in Prometheus.

**Security**
- `spring-boot-starter-security` — auth pipeline, filter chain.
- `spring-boot-starter-oauth2-resource-server` — JWT / opaque token validation.
- `spring-boot-starter-oauth2-client` — OIDC login.
- `bcrypt` / `argon2` via Spring Security's `PasswordEncoder`.
- Parameterized queries via JPA / JDBC (`?` or named params).
- Thymeleaf auto-escapes; be careful with `th:utext` (raw text).
- HTTPS enforced via HSTS, secure cookies.

**Secrets & config**
- Spring Cloud Vault for dynamic secrets.
- Kubernetes secret projection into env vars / files.

**Observability**
- OpenTelemetry Java agent → auto-instruments Spring MVC, WebFlux, JDBC, Kafka.
- JSON logs via Logback encoder.

---

### 13.14 Common anti-patterns

- **Reliability by hope** — no SLOs, no error budgets, no alerts.
- **Health checks that lie** — /health returns 200 even when DB is down.
- **Untested backups** — restore never rehearsed.
- **Retry without jitter** — synchronized storms.
- **Same-region "multi-AZ"** treated as multi-region — a whole-region outage takes both.
- **Secrets in Git** — permanent, once pushed, even after `--force-push` (mirrors, forks).
- **"We'll add security later"** — bolted-on security is always incomplete.
- **Naming humans in postmortems** — kills learning.

---

### 13.15 Summary — one-liners you can defend

- **SLI = measure, SLO = target, SLA = contract. Error budgets align dev velocity with reliability.**
- **Failure modes are shapes: cascade, thundering herd, retry storm, metastable, grey. Name them, apply the canonical fix.**
- **Chaos engineering makes failure cheap and expected.**
- **RTO = how fast we recover; RPO = how much data we lose. Translate business tolerance into architecture.**
- **Durability ≠ availability. 3-2-1 backups + PITR.**
- **Defense in depth: any layer will fail; the next one must hold.**
- **Encryption in transit (TLS/mTLS), at rest (KMS envelope), passwords via argon2.**
- **Secrets: managed + short-lived + machine identity.**
- **STRIDE + OWASP Top 10 + parameterize/escape/authZ every request.**
- **Postmortems are blameless. The system permitted it.**

**Top-level insight**
> "Reliability and security are not features — they are properties of the whole system. You get them by measuring what matters, defending in depth, practicing failure, and treating operations as engineering. Sections 1–12 build systems that work; Section 13 keeps them working when the world is hostile."

---

*Next up: Section 14 — Classic Interview Design Problems. Structured walk-throughs (Requirements → Estimation → API → Data model → HLD → Deep dives → Bottlenecks) for URL shortener, rate limiter, chat, news feed, Uber, YouTube, Dropbox, autocomplete, payment ledger, notification system, and more.*
