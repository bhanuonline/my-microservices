# Effort vs. capability ladder

How many business services you run depends on what you want to do.
This is the progression — each level unlocks more capability but
costs more RAM.

```mermaid
graph LR
    L0[0 services<br/>Prove nano boots] --> L1[1 service<br/>One API endpoint]
    L1 --> L3[3 services<br/>product + order + payment<br/>Place an order]
    L3 --> L4[4 services<br/>+ notification<br/>Full saga]
    L4 --> L5[5+ services<br/>+ UI<br/>Browser testing]

    classDef lvl0 fill:#E8F5E9,stroke:#4CAF50
    classDef lvl1 fill:#C8E6C9,stroke:#43A047
    classDef lvl3 fill:#FFF9C4,stroke:#FBC02D
    classDef lvl4 fill:#FFE0B2,stroke:#FB8C00
    classDef lvl5 fill:#FFCCBC,stroke:#E64A19
    class L0 lvl0
    class L1 lvl1
    class L3 lvl3
    class L4 lvl4
    class L5 lvl5
```

| Level | Services | Example command |
|---|---|---|
| 0 | — | `make up-nano` + `curl /actuator/health` |
| 1 | user **or** product | `make up-nano` + `make debug-user` |
| 3 | product + order + payment | `make up-nano` + `make debug-saga` |
| 4 | + notification | `make up-nano` + `make debug-all` |
| 5+ | + shop-ui / backoffice-ui | manual (UIs run on separate ports) |

Rule: run only what you need. Each extra Spring Boot service ≈ 400 MB RAM.

See the ASCII version in [`setup/00-first-time-setup.md`](../setup/00-first-time-setup.md#6-·-how-many-services-do-i-actually-need-to-run).
