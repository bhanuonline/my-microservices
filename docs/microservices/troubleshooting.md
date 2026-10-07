# Troubleshooting

Living catalog. Append every symptom you hit + the fix. Future-you will thank you.

## Format

```
## Symptom
Short description of what you saw.

**Diagnosis:**
Why it happened.

**Fix:**
Exact command / config change.

**How to prevent recurrence:**
(if applicable)
```

---

## Symptom — `port XXXX already in use`

**Diagnosis:** something else on your Mac has that port.

**Fix:**
```bash
lsof -i :3306   # find the offender
kill <pid>       # or stop the service via brew / launchctl
```

---

## Symptom — auth-server refuses to start: `Schema-validation: missing table [X]`

**Diagnosis:** `ddl-auto=validate` fails when Hibernate finds an entity with
no matching table in the DB. Happens on:
- **First boot** — DB is empty, no tables exist yet
- **Any time a new @Entity is added or renamed** — validate is strict

**Fix (this project — dev only):** `ddl-auto=update` permanently in
`auth-server/src/main/resources/application.properties`. Hibernate auto-creates
missing tables on every boot. **Never use `update` in prod** — silent schema
changes can wreck data.

**Prod fix:** use Flyway or Liquibase migrations. Every schema change is a
version-controlled SQL file. Keep `ddl-auto=validate` so app fails loudly if
migrations weren't run.

**Manual workaround (if you want to keep validate):** flip to `update`, start
once (creates tables), flip back to `validate`.

---

## Symptom — gateway logs `No servers available for service: XXX`

**Diagnosis:** target service not registered with Eureka.

**Fix:**
1. Check Eureka UI (http://localhost:8761) — is `XXX` listed?
2. If not, check that service's logs for registration errors.
3. Check that service's `application.yml` has `eureka.client.service-url.defaultZone` pointing at the right Eureka.

---

## Symptom — Feign call returns 401

**Diagnosis:** JWT not being forwarded, or issuer mismatch.

**Fix:**
1. Check gateway config includes `TokenRelay` in `default-filters`.
2. Verify all services agree on `spring.security.oauth2.resourceserver.jwt.issuer-uri`.
3. Decode the JWT (https://jwt.io) — `iss` field should match issuer-uri exactly.

---

## Symptom — Kafka consumer bean not called even though message is on topic

**Diagnosis:** likely one of:
- `spring.cloud.function.definition` doesn't include the bean name.
- Binding name in yml doesn't match `<beanName>-in-0` convention.
- Consumer group has wrong offset (already consumed).

**Fix:**
1. Check `spring.cloud.function.definition: beanA;beanB` includes yours.
2. Check binding key: bean `foo` → `foo-in-0`.
3. Reset consumer group offset:
   ```bash
   docker exec kafka kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
     --group <groupName> --reset-offsets --to-earliest --topic <topicName> --execute
   ```

---

## Symptom — `Public Key Retrieval is not allowed` on MySQL 8 connect

**Diagnosis:** MySQL 8 defaults to `caching_sha2_password` auth. The driver
needs either SSL (to send the password encrypted) OR permission to fetch
the server's RSA public key over plain TCP first. If both `useSSL=false`
AND no `allowPublicKeyRetrieval=true` → connection fails at handshake.

**Fix:** append `allowPublicKeyRetrieval=true` to the JDBC URL:
```
jdbc:mysql://host:port/db?useSSL=false&allowPublicKeyRetrieval=true
```

Applies to every service connecting to MySQL 8 — user-service,
product-service, auth-server. Fix all their URLs (main + docker profile).

**Prevention:** always include both flags in local/dev JDBC URLs. Never
in prod — use SSL there.

---

## Symptom — auth-server login "incorrect" for admin/password

**Diagnosis:** the `spring.security.user.name` / `spring.security.user.password`
in `application.properties` are IGNORED when a custom `UserDetailsService`
bean is defined. This project defines one in
`auth-server/.../config/SecurityConfig.java` with hardcoded credentials.

**Actual credentials:**
- Login user: `user` / `password` (from `SecurityConfig.userDetailsService()`)
- OAuth2 client: `demo-client` / `secret` (from `SecurityConfig.registeredClientRepository()`)

The `demo-client` is registered for `authorization_code` grant, NOT
`client_credentials` — so the Postman `/oauth2/token` request will fail
with `unauthorized_client` until we register a `client_credentials`-capable
client (or use the full auth-code flow with browser redirect).

**Also broken:** even `user` / `password` browser login fails. Two
`SecurityFilterChain` beans in `SecurityConfig.java` conflict (no `@Order`
on either) — the form-login chain never gets wired to the auth requests.
Deferred fix (see task #29).

**Impact:** neither of these blocks other services. Downstream services
only need the JWKS endpoint, which returns HTTP 200 with real RSA keys.
For Phase 2/3 purposes, treat auth-server as "JWKS provider only".

---

## Symptom — `No spring.config.import property has been defined`

**Diagnosis:** `spring-cloud-starter-config` (the config-client) is on the
classpath, but no config-server import was declared. From Spring Boot 2.4+
the client refuses to start without an explicit `spring.config.import`.

**Fix (config-server not implemented yet):** add these to the service's yml
INSIDE the existing `spring:` block:
```yaml
spring:
  cloud:
    config:
      enabled: false                   # skip the client, don't try to fetch
  config:
    import: "optional:configserver:"   # satisfy the boot-time check anyway
```

Applies to any service with `spring-cloud-starter-config` in its pom.
Currently: user-service, product-service, order-service.

**Watch out for YAML duplicate keys:** if the service already has a
`spring.cloud.*` block (e.g. for stream), you MUST nest `config: {enabled: false}`
UNDER the existing `cloud:` — otherwise the second `spring.cloud:` silently
overwrites the first.

**Long-term fix:** actually implement config-server (see problem #8 in the
original plan).

---

## Symptom — `UnknownHostException: kafka` in Kafka client

**Diagnosis:** Kafka container advertised itself as `PLAINTEXT://kafka:9092`.
That hostname only resolves inside the docker network. When a service runs on
your Mac (via IDE / `mvnw spring-boot:run`), DNS lookup for `kafka` fails.

The Kafka bootstrap sequence:
1. Client connects to `localhost:9092` (correctly mapped to container port)
2. Broker replies: "here's the list of brokers you can talk to: kafka:9092"
3. Client tries to resolve `kafka` → fails

**Fix — dual-listener Kafka config.** Advertise DIFFERENT hostnames to
different audiences:

In `docker-compose.yml`:
```yaml
kafka:
  ports:
    - "9092:9092"       # host access (Mac clients)
    - "29092:29092"     # internal (docker network)
  environment:
    - KAFKA_CFG_LISTENERS=EXTERNAL://:9092,INTERNAL://:29092,CONTROLLER://:9093
    - KAFKA_CFG_ADVERTISED_LISTENERS=EXTERNAL://localhost:9092,INTERNAL://kafka:29092
    - KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP=EXTERNAL:PLAINTEXT,INTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT
    - KAFKA_CFG_INTER_BROKER_LISTENER_NAME=INTERNAL
```

Then:
- **Mac clients:** `bootstrap.servers=localhost:9092` (default in every yml)
- **Docker-network clients:** `bootstrap.servers=kafka:29092` (docker profile
  yml + compose env vars use this)

**MANDATORY:** restart Kafka container after changing listener config:
```
docker compose down
docker compose up -d kafka
```

**Prevention:** always define dual listeners for hybrid dev setups (some
services on host, some in docker).

---

## Alert — order-saga

Target for the `runbook` field on `Saga*` alerts in
`observability/grafana/provisioning/alerting/saga-alerts.yml`.

All three alerts rely on `order-service` SagaMetrics
(`orders_saga_started_total`, `orders_saga_terminal_total`). Start every
investigation on the **Order Saga — business metrics** dashboard:
http://localhost:3000/d/order-saga

### SagaFailureRateHigh — warning

**Symptom:** `(failed + compensated) / total > 5%` sustained for 5 minutes.

**Diagnosis:** a meaningful fraction of order sagas are not completing.
Both `failed` (payment denied at step 1) and `compensated` (notification
step failed → payment refunded) count — the customer loses either way.

**Fix:**
1. Open the dashboard, panel **Top failure reasons**. The dominant
   `(outcome, reason)` row names which step broke.
   - `failed / payment_failed:*` → payment-service degraded.
   - `compensated / notify_failed:*` → notification service degraded.
2. Open the matching service's logs via Loki. The traceId in any saga
   failure log line is clickable → opens the trace in Zipkin.
3. Check `kafka-consumer-lag` dashboard for the group that owns the
   failing step (`order-service-saga` for payment replies,
   `notification-service` for notify replies). A climbing lag before the
   failure spike = backpressure, not a logic bug.

**Prevention:** this rule has a `and total > 0.1/sec` noise floor —
below ~6 terminals in 5m it won't fire. If you ever lower the threshold
below 1%, consider adding a per-outcome split so compensations and
hard failures alert separately.

---

### SagaFailuresBursting — critical

**Symptom:** `rate(failed + compensated) > 0.5/sec` for 2 minutes
(≈ 60 bad outcomes in 2 min).

**Diagnosis:** something is actively broken RIGHT NOW. The ratio alert
can be masked when there's heavy successful traffic drowning the signal;
this one triggers purely on raw failure count.

**Fix:**
1. Dashboard → **Terminal outcomes** panel. The spike color tells you
   whether it's `failed` (payment) or `compensated` (notification).
2. `docker compose ps` — is the implicated service even running?
3. If running: check its logs for stack traces, especially around Kafka
   deserialization or downstream HTTP timeouts.
4. If a payment or notification bug caused good messages to be rejected,
   fix the bug + replay from the matching DLT (see
   [DLT runbook](#alert--dlt-messages) below).

**Prevention:** this alert is the "something is on fire" one. Pair it
with the ratio alert — a fire alarm and a smoke detector.

---

### SagaInFlightStuck — warning

**Symptom:** `sum(started) - sum(terminal) > 50` for 15 minutes.

**Diagnosis:** sagas are STARTING but never terminating in any state
(not failing, not completing, not compensating — just vanishing). The
counters only move when `OrderSagaOrchestrator.start()` and the terminal
hooks run; a message dropped between the two leaves this diff climbing.

Likely causes, in order of frequency:
- Payment-service or notification is down → no reply ever arrives.
- A saga reply handler threw an exception → message routed to its DLT.
- order-service restarted mid-saga and no resume logic re-wired the
  in-flight sagas from the DB rows. (Note: `OrderSagaOrchestrator`
  does not currently resume on boot. See deferred follow-up.)

**Fix:**
1. Dashboard → **In-flight sagas** stat panel. Is the number flat (true
   leak) or still growing (fresh problem)?
2. Check `kafka-consumer-lag` dashboard for groups `order-service-saga`
   and `notification-service`. Lag growing in lockstep with in-flight
   = reply consumer stuck.
3. Query the sagas table directly to see what state they're wedged in:
   ```bash
   docker exec -it mysql-order mysql -uroot -p$MYSQL_ROOT_PASSWORD orderdb \
     -e "SELECT state, COUNT(*) FROM order_sagas GROUP BY state;"
   ```
   Most will likely be `STARTED` (payment never replied) or `PAID`
   (notify never replied).
4. For the stuck sagas, check if the matching reply topic has a backlog
   or if its DLT has messages (see [DLT runbook](#alert--dlt-messages)).

**Prevention:** add a boot-time resume: on startup, scan `order_sagas`
for non-terminal states and re-fire the appropriate command. Deferred —
not fixing in this runbook.

---

## Alert — DLT messages

Target for the `runbook` field on `Dlt*` and `ConsumerLagRunaway` alerts
in `observability/grafana/provisioning/alerting/dlt-alerts.yml`.

Dead-letter topics (`*.DLT`, `error.*`) hold messages the retry logic
gave up on. Every message there is a message a service refused to
process. Investigation path is the same for all three alerts — the only
difference is urgency.

### Where to look first

- **Dashboard:** http://localhost:3000/d/kafka-consumer-lag — panels
  **DLT depth** and **Lag by topic** both at a glance.
- **Mailhog inbox:** http://localhost:8025 — the alert email itself
  names the exact topic.
- **Raw inspection:** peek at the poisoned payload with
  ```bash
  docker exec kafka kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic <topic-name>.DLT \
    --from-beginning \
    --max-messages 5 \
    --property print.headers=true
  ```
  Spring Cloud Stream + the Kafka binder set `x-exception-message`,
  `x-exception-stacktrace`, and `x-original-topic` as record headers.
  Those headers are the fastest path to the root cause.

### Decide: replay, drop, or fix

Every DLT message is one of three things:

| Cause | Decision |
|---|---|
| Transient downstream failure (DB blip, timeout) | **Replay** — consumer code is fine, just missed the window. |
| Bad payload (schema drift, malformed JSON) | **Drop or fix producer** — replaying will fail the same way. |
| Consumer bug | **Fix consumer, then replay** — otherwise the fix doesn't actually recover the message. |

### Replay from DLT back to the live topic

Grafana does not replay for you. For each DLT with messages worth
recovering:

```bash
# 1. Mirror DLT → live topic once, then stop.
docker exec kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic order.created.DLT \
  --from-beginning \
  --timeout-ms 5000 \
  | docker exec -i kafka kafka-console-producer.sh \
      --bootstrap-server localhost:9092 \
      --topic order.created
```

Watch the matching Grafana lag panel — the consumer should drain, DLT
depth should stay flat (replay doesn't delete from DLT, just copies
forward), and no new DLT messages should land.

### Drop (if the message is permanently garbage)

DLTs don't offer per-message delete. Easiest: shift the consumer group's
`<topic>.DLT` offset forward past the junk, or purge the entire topic:

```bash
docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 \
  --delete --topic order.created.DLT
# Topic auto-recreates on next poisoned message.
```

### Why the Consumer-lag runaway alert points here too

The lag alert fires BEFORE messages time out and end up in a DLT. If you
act on `ConsumerLagRunaway` fast enough you avoid the DLT alert
entirely — same root cause, earlier signal.

---

## (Add more as you hit them)
