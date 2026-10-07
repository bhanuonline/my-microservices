# config-repo/

Served by `config-server` (Spring Cloud Config, native-filesystem backend).
Each service pulls its `<service-name>.yml` from here at boot.

## Why in-repo (not a separate git repo)

Study project. Keeping the config *in* the same repo as the code means
one commit changes both when the two must move together. For prod,
split this out so ops can change config without redeploying code.

## Layout

```
config-repo/
├── README.md
├── application.yml           ← shared defaults applied to every service
├── <service-name>.yml        ← service-specific overrides
└── <service-name>-<profile>.yml  ← profile-specific overrides for a service
```

Spring Cloud Config layers them in order:
1. `application.yml`  (shared)
2. `<service-name>.yml`  (service)
3. `<service-name>-<active-profile>.yml`  (service + profile)

Later layers win.

## How a service pulls its config

Add to the service's own `application.yml`:

```yaml
spring:
  config:
    import: "optional:configserver:http://localhost:8888"
```

`optional:` means "boot anyway if config-server is unreachable." Keep
that unless you want services to hard-fail on config-server outage.

## How to see what config-server would serve

```bash
# Pretty-prints the fully-merged config for user-service in default profile.
curl -s http://localhost:8888/user-service/default | jq
```

## Status (2026-10)

**Group 1 complete** — config-server runs, mounts this dir, serves
whatever yml files land here. **No service is wired to it yet**; the
three services carrying `spring-cloud-starter-config` do not import
anything from here. Opt-in migration happens one service at a time.
