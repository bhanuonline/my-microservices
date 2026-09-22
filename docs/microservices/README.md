# Microservices project — architecture, concepts, and runbooks

Living doc for the study project. Filled in as debugging + verification progress.

## How to navigate

| I want to... | Read |
|---|---|
| **Is everything running right now?** | Run `./status.sh` from project root |
| Understand the whole system in 5 min | [00-architecture-overview.md](00-architecture-overview.md) |
| Bring the stack up right now | [01-startup-runbook.md](01-startup-runbook.md) |
| Know if a service is actually healthy | [02-verification-checklist.md](02-verification-checklist.md) |
| Understand a specific pattern | [concepts/](concepts/) |
| See a specific request end-to-end | [flows/](flows/) |
| Something is broken and I don't know why | [troubleshooting.md](troubleshooting.md) |

## Concepts index

- [Service discovery (Eureka + lb://)](concepts/service-discovery.md)
- [API gateway (reactive routing, JWT relay)](concepts/api-gateway.md)
- [JWT auth flow (auth-server + resource-server)](concepts/jwt-auth-flow.md)
- [OAuth2 + JWT — grant types, JWKS, testing with client_credentials](concepts/oauth2-auth.md)
- [Distributed tracing (Zipkin)](concepts/distributed-tracing.md)
- [Correlation IDs (MDC)](concepts/correlation-ids.md)
- [Circuit breaker](concepts/circuit-breaker.md)
- [Retry + Bulkhead](concepts/retry-and-bulkhead.md)
- [Event-driven basics (StreamBridge + Consumer)](concepts/event-driven-basics.md)
- [Transactional outbox](concepts/outbox-pattern.md)
- [Saga orchestration](concepts/saga-orchestration.md)
- [Dead Letter Queue (DLQ)](concepts/dlq.md)

## Flow walkthroughs

- [User registration](flows/user-registration.md)
- [Order → payment happy path](flows/order-payment-happy.md)
- [Order → payment failure path](flows/order-payment-failure.md)
- [Saga compensation (refund)](flows/saga-compensation.md)
- [DLQ (poison message quarantine)](flows/dlq-quarantine.md)

## Doc conventions

Each **concept** doc follows this template:

```
## The problem it solves
## How it works (ASCII diagram)
## How it's wired in THIS project
## How to observe it running
## Common failure modes
## Interview talking points
```

Each **flow** doc follows this template:

```
## The request
## Expected outcome
## Step-by-step trace (with log lines)
## Zipkin trace screenshot (or description)
## What could go wrong
```
