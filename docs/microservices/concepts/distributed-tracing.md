# Distributed tracing — Micrometer Tracing + Zipkin

## The problem it solves

- One user request touches 4+ services. Cannot correlate logs across them without a shared ID.
- Cannot visualize where time was spent.

## How it works

```
<!-- ASCII: traceId + parent spanId, propagated via W3C headers -->
```

## How it's wired in THIS project

- **Backend:** Zipkin container, port 9411
- **Instrumentation:** `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` in every service pom
- **Feign propagation:** `feign-micrometer` in order-service (so downstream calls carry the traceparent header)
- **Kafka propagation:** automatic via Micrometer Cloud Stream instrumentation
- **Config:**
  ```
  management.tracing.sampling.probability: 1.0     # 100% in dev
  management.zipkin.tracing.endpoint: http://zipkin:9411/api/v2/spans
  ```
- **Log pattern:** `"%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-},%X{correlationId:-}]"`

## How to observe it running

```bash
# 1. Trigger a request
curl -X POST http://localhost:8081/api/v1/users -H "Content-Type: application/json" \
  -d '{"name":"Alice","email":"a@a.com","password":"secret123"}'

# 2. Note the traceId in the log line

# 3. Open Zipkin: http://localhost:9411 → Run Query → find your trace
# 4. Grep across service logs
grep <traceId> */*.log
```

## Common failure modes

- Traces not appearing in Zipkin — check `management.zipkin.tracing.endpoint`.
- Sampling too low in prod — 100% in dev, ~1–10% in prod.
- Trace ID missing in Kafka consumer — instrumentation didn't wire; check Cloud Stream version.

## Interview talking points

- W3C Trace Context standard (`traceparent` header) vs older B3 propagation.
- Sampling: head-based vs tail-based.
- Traces vs metrics vs logs (the "3 pillars of observability").
- OpenTelemetry (the vendor-neutral standard replacing OpenTracing/OpenCensus).
