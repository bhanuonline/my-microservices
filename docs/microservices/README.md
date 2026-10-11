# Microservices project — architecture, concepts, and runbooks

Living doc for the study project. Filled in as debugging + verification progress.

## Folder layout

```
docs/microservices/
├── 00-architecture-overview.md   ← start here for the 5-min overview
│
├── setup/        First-time setup, startup, verification, Docker how-to
├── debug/        Troubleshooting, debugging-flows, useful commands
├── design/       Architecture tiers, roadmaps, design decisions
├── features/     Service- and feature-specific guides (payment, UI, etc.)
├── diagrams/     Visual (Mermaid) versions of key diagrams — rendered
│                 as pictures on GitHub/MkDocs; equivalent to ASCII in docs
│
├── concepts/     Reusable patterns (saga, outbox, DLQ, service discovery, …)
├── flows/        End-to-end request walkthroughs
├── postman/      Postman collections for manual + debug testing
├── api-gateway/  Deep-dives into gateway features
├── auth-server-admin/
├── resource-server/
└── build/
```

## How to navigate

| I want to... | Read |
|---|---|
| **I just cloned the repo — how do I get started?** | [setup/00-first-time-setup.md](setup/00-first-time-setup.md) |
| **Is everything running right now?** | Run `./status.sh` from project root |
| Understand the whole system in 5 min | [00-architecture-overview.md](00-architecture-overview.md) |
| Day-to-day Docker commands for this repo | [setup/docker-howto.md](setup/docker-howto.md) |
| Which mode should I run? (nano / minimal / shared-db / individual) | [setup/topologies.md](setup/topologies.md) |
| Bring the stack up right now | [setup/01-startup-runbook.md](setup/01-startup-runbook.md) |
| Know if a service is actually healthy | [setup/02-verification-checklist.md](setup/02-verification-checklist.md) |
| Understand a specific pattern | [concepts/](concepts/) |
| See a specific request end-to-end | [flows/](flows/) |
| Something is broken and I don't know why | [debug/troubleshooting.md](debug/troubleshooting.md) |
| Walk through a saga step-by-step | [debug/debugging-flows.md](debug/debugging-flows.md) |
| Understand the architecture tier-by-tier | [design/](design/) |
| Learn a specific feature | [features/](features/) |

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
