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

## (Add more as you hit them)
