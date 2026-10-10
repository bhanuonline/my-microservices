# Load Testing — k6 against POST /api/v1/orders

Companion to the Tier 1 & Tier 2 docs. k6 drives real traffic through the
gateway → order-service → outbox → saga pipeline and streams results to
the same Prometheus/Grafana stack you already use for app metrics.

- [1. What this exercises](#1-what-this-exercises)
- [2. Topology](#2-topology)
- [3. The three scenarios](#3-the-three-scenarios)
- [4. SLOs enforced as thresholds](#4-slos-enforced-as-thresholds)
- [5. Running it](#5-running-it)
- [6. Where to see results](#6-where-to-see-results)
- [7. Interpreting the output](#7-interpreting-the-output)
- [8. Interview talking points](#8-interview-talking-points)

---

## 1. What this exercises

Every request goes through:

```
k6 VUs ─▶ api-gateway (RateLimiter, CB, auth) ─▶ order-service
                                                   │
                                                   ├─ IdempotencyFilter      ← Tier 1
                                                   │   (unique key per iter)
                                                   ├─ OrderService.create    ← @NewSpan
                                                   │   INSERT orders
                                                   │   INSERT outbox_event
                                                   │   saga.start(…)
                                                   │
                                                   └─ (async) OutboxRelay    ← Tier 2
                                                       → Kafka → payment / notify
```

So one `POST /api/v1/orders` touches every single Tier 1/2 feature. If a
piece is broken, k6 will out it.

---

## 2. Topology

```
   ┌──────────┐    POST /api/v1/orders        ┌────────────┐
   │  k6      │ ───────────────────────────▶ │ api-gateway │
   │ (docker  │    Idempotency-Key: <uuid>    │   :8080     │
   │  compose │    X-Correlation-Id           └──────┬──────┘
   │  service)│                                      │
   └────┬─────┘                                      ▼
        │ remote-write                       ┌──────────────┐
        │ (push every 5s)                    │ order-service│
        ▼                                    │   :8083      │
   ┌─────────────┐                           └──────────────┘
   │ Prometheus  │ ◀───── scrape /actuator/prometheus (app metrics)
   │   :9090     │
   │             │
   │  k6 series: │
   │   k6_http_* │
   │   k6_vus    │
   │   k6_checks │
   │   orders_*  │   ← custom counters
   └──────┬──────┘
          ▼
   ┌─────────────┐
   │  Grafana    │   dashboard: "k6 — load test overview"
   │   :3000     │   plus the existing "Microservices — RED + Resilience"
   └─────────────┘       (both panels side-by-side tell you if load broke prod paths)
```

---

## 3. The three scenarios

Pick one via `SCENARIO=…`. Only one runs per invocation.

| name | shape | when | expected cost |
|---|---|---|---|
| `smoke` (default) | 1 VU · 10 iterations | after any config change — sanity check | 2–3 s |
| `load` | ramp 0→50 VUs · soak 2m · ramp down | regular SLO check | ~3 min |
| `stress` | ramping arrival rate 1→200 rps | find breaking point before shipping | ~4 min |

Why the three shapes:

- **Smoke** proves the pipeline works end-to-end. Fails loud and fast if auth is broken, the gateway is misrouted, or the DB is down.
- **Load** holds steady at expected peak so the breaker/retry/metrics stories play out at scale.
- **Stress** climbs past the breaking point so you *know* where the knee is — a service you've never stressed has an unknown ceiling.

---

## 4. SLOs enforced as thresholds

The script fails (exit 99) if any of these is breached:

| threshold | why |
|---|---|
| `http_req_failed < 1%` | basic availability — more than 1% errors is a release-blocker |
| `http_req_duration p95 < 800 ms` | user-perceived latency ceiling |
| `http_req_duration p99 < 1500 ms` | tail-latency guard; usually what tips over first under load |
| `checks > 99%` | response assertions (2xx status, id in body) hold |
| `orders_created > 0` | sanity — if no order ever landed, the test is lying |

Tune these to your real production SLO. k6 treats a breach as a build-failing
non-zero exit, which is what you want in CI.

---

## 5. Running it

### Local, against a running stack

```bash
# One-off smoke test (needs k6 installed locally — brew install k6)
k6 run load-tests/k6/orders.js

# Full load scenario, metrics stay local (summary printed at end)
k6 run -e SCENARIO=load load-tests/k6/orders.js

# Push metrics to Prometheus for dashboard visibility
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM=true \
k6 run --out experimental-prometheus-rw -e SCENARIO=load load-tests/k6/orders.js
```

### Via docker-compose (zero local install)

```bash
# Bring up the stack if not already
docker compose up -d api-gateway order-service product-service payment-service \
                     notification eureka-server kafka prometheus grafana

# Run the smoke scenario (k6 service lives behind the 'loadtest' profile so
# it never starts with a plain `docker compose up`)
docker compose --profile loadtest run --rm k6 \
  run -e SCENARIO=smoke /scripts/orders.js

# Full load — results land in Prometheus automatically via remote-write
docker compose --profile loadtest run --rm k6 \
  run -e SCENARIO=load --out experimental-prometheus-rw /scripts/orders.js
```

### Overriding auth

```bash
# JWT token from the auth-server
TOKEN=$(curl -s -X POST http://localhost:8095/oauth2/token \
          -u my-client:secret -d 'grant_type=client_credentials' | jq -r .access_token)
k6 run -e AUTH="$TOKEN" load-tests/k6/orders.js
```

---

## 6. Where to see results

**Live, during the run:**

- **k6 stdout** — classic rolling checks + VU gauge + latency summary. Also shows every threshold's running state so you can watch an SLO about to breach.
- **Grafana → "k6 — load test overview"** — req rate, failure rate, p50/p95/p99, VU count, custom counters, check pass rate. Refreshes every 10 s.
- **Grafana → "Microservices — RED + Resilience"** — the existing app dashboard. Open side-by-side with k6: your load should correlate with the app's own `http_server_requests_*` histograms. If k6 says 50 rps and the app sees 10, you have a routing problem.
- **Zipkin** — pick any slow trace; see which span inside order-service ate the budget.

**Post-run:**

- **k6 summary table** (end of stdout) — totals, mins, maxes, means, every custom metric.
- **Prometheus** — all series persist for 7d (retention set in compose).
- **Grafana** — zoom back in time to compare runs. Add an annotation per run (manual) to call them out.

---

## 7. Interpreting the output

The useful questions, with the queries that answer them:

```
How fast did the stack keep up with the load?
    sum(rate(k6_http_reqs_total[30s]))

What % failed?
    sum(rate(k6_http_req_failed_total[1m])) / sum(rate(k6_http_reqs_total[1m]))

What was the p95 tail?
    histogram_quantile(0.95, sum by (le) (rate(k6_http_req_duration_seconds_bucket[1m])))

Did any VU wait for the server?
    k6_http_req_waiting_seconds (long-tail → connection pool exhausted / threadpool full)

Did the app see what k6 sent?
    sum(rate(http_server_requests_seconds_count{application="order-service", uri="/api/v1/orders"}[30s]))
    If this diverges from k6_http_reqs_total → gateway rate-limited OR health-check-in-the-way
```

### Common findings (what each one means)

| symptom | likely cause |
|---|---|
| p95 climbs linearly with VUs | thread pool saturation — tune `server.tomcat.threads.max` |
| failure rate spikes at 429 | gateway rate limiter kicking in — expected if `load` > configured bucket |
| p99 much larger than p95 | GC pauses OR a single slow downstream (payment-service, product-service) — pivot to Zipkin |
| orders_created lags http 2xx count | idempotency replays happening — check `idempotency_replays` metric |
| Grafana `resilience4j_circuitbreaker_state` goes to OPEN mid-run | downstream broke; cover more ground in the stress scenario before shipping |

---

## 8. Interview talking points

**Q: How do you know your system meets its SLO?**
> We run k6 in CI with thresholds encoded as pass/fail gates: p95 < 800ms,
> p99 < 1.5s, <1% errors. k6 exits non-zero if any threshold is breached,
> which fails the build. Results are published to Prometheus via
> remote-write so we can trend performance across releases and spot
> regressions before shipping.

**Q: Why k6 over JMeter / Gatling?**
> Scripts are JS not XML/Scala, so devs actually read them. Native output
> plugins (Prometheus remote-write, InfluxDB, JSON). Single Go binary —
> easy to drop into a Docker Compose profile or a GitHub Action.

**Q: Smoke vs load vs stress — why three?**
> Smoke proves wiring. Load proves SLO at expected peak. Stress finds the
> knee of the curve so we know where graceful degradation needs to kick in.
> Each tests a different question.

**Q: How do you avoid distortion from the load generator?**
> k6 runs in a separate container on the same Docker network as the gateway
> so there's no Mac-host networking tax. In CI we'd move k6 to a sibling
> machine. Native histograms are enabled on Prometheus so quantiles are
> exact rather than bucket approximations.

**Q: What's the first thing you look at when p99 blows up?**
> Grafana → RED dashboard filtered to the same time window. If application
> p99 matches k6's, the service is actually slow — pivot to Zipkin and find
> which span owns the latency. If app p99 is fine but k6 sees high tail,
> the problem is between client and gateway (DNS, TLS handshake, LB).

---

## Related docs

- [tier1-architecture.md](../design/tier1-architecture.md) — Prometheus/Grafana wiring this reuses
- [tier2-architecture.md](../design/tier2-architecture.md) — the saga + outbox path that handles the load
- [concepts/circuit-breaker.md](concepts/circuit-breaker.md) — what should kick in during `stress`
