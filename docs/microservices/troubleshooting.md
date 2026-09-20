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

## Symptom — auth-server refuses to start: `Schema-validation: missing table [oauth2_authorization]`

**Diagnosis:** `ddl-auto=validate` runs on empty DB.

**Fix:** first-boot flip `auth-server/application.properties`:
```
spring.jpa.hibernate.ddl-auto=update
```
Start once, stop, flip back to `validate`.

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

## (Add more as you hit them)
