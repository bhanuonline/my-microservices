# =====================================================================
# Topology-aware + profile-aware wrappers around docker compose.
# See docs/microservices/topologies.md for the full comparison.
#
# TOPOLOGIES (pick one; mutually exclusive):
#   make up-nano         → platform only (no business svcs). 7 containers (~1.6 GB)
#   make up-nano-trace   → nano + zipkin for traces. 8 containers (~1.8 GB)
#   make up-minimal      → core incl. business svcs + Prom/Grafana. 12 containers (~2.5 GB)
#   make up / up-shared  → shared-db + all optional (full profile). ~26 containers
#   make up-individual   → shared-db + 4 per-service MySQLs. ~30 containers
#
# SLIM MIXES (just the extras you need; still minimal topology):
#   make up-saga         → minimal + core-plus. Full F1-F3 saga, ~14 containers (~2.8 GB)
#   make up-trace        → minimal + core-plus + tracing + email. ~16 containers (~3.3 GB)
#   make up-kafka-debug  → minimal + core-plus + kafka-ops. ~16 containers (~3.1 GB)
#   make up-with PROFILES="email,tracing"  → minimal + ANY profiles you name
#
# Available profile tags (combine freely):
#   core-plus, email, tracing, logs, kafka-ops, search, cdc, avro,
#   cache, secrets, loadtest, full
# =====================================================================

COMPOSE_BASE   := docker-compose.yml
COMPOSE_INDIV  := -f $(COMPOSE_BASE) -f docker-compose.topology-individual.yml

PROFILES_FULL  := COMPOSE_PROFILES=full

# ─── Service subsets for nano mode ─────────────────────────────────────
# Platform floor — the services you need before ANY business service can
# start (business services depend on these via depends_on). Starting just
# these = ability to run user/product/order/payment on your HOST via
# `mvn spring-boot:run` while this platform is in Docker.
# Redis is required by api-gateway for ApiKeyStore + RouteRefresh +
# IdempotencyStore + RateLimiter + ResponseCache (several are always-on).
NANO_SERVICES := mysql-shared kafka redis eureka-server config-server auth-server api-gateway

.DEFAULT_GOAL := help

# ─── Usage / help ──────────────────────────────────────────────────────

help:
	@echo "Topology (pick one):"
	@echo "  make up-nano          — platform floor, 7 containers (~1.6 GB)"
	@echo "                           (biz svcs OFF — run them from your IDE)"
	@echo "  make up-nano-trace    — nano + zipkin for distributed traces, 8 containers (~1.8 GB)"
	@echo "  make up-minimal       — core + biz svcs + Prom/Grafana, 12 containers (~2.5 GB)"
	@echo "  make up / up-shared   — shared MySQL + ALL optional, ~26 containers (~5.5 GB)"
	@echo "  make up-individual    — shared-db + 4 per-service MySQL, ~30 containers (~6.5 GB)"
	@echo ""
	@echo "Slim mixes (minimal + specific profiles):"
	@echo "  make up-saga          — minimal + core-plus (notification + resource-server)"
	@echo "  make up-trace         — minimal + core-plus + zipkin + mailhog"
	@echo "  make up-kafka-debug   — minimal + core-plus + kafka-ui + kafka-exporter"
	@echo "  make up-with PROFILES='email,tracing'  — minimal + chosen profiles"
	@echo ""
	@echo "Lifecycle:"
	@echo "  make down             — tear down any running stack"
	@echo "  make restart          — down + up (default = shared)"
	@echo "  make ps               — show running containers"
	@echo "  make stats            — live resource usage (CPU/MEM) — ctrl-C to exit"
	@echo "  make verify           — one-shot health check (compose + stats + HTTP + Eureka)"
	@echo "  make logs             — tail all logs"
	@echo "  make logs-SERVICE     — tail logs for one service"
	@echo "  make mysql            — MySQL shell (root prompt)"
	@echo "  make mysql-user       — MySQL shell on userdb (also -product/-auth/-payment)"
	@echo "  make redis            — redis-cli interactive prompt"
	@echo "  make redis-keys       — list all keys (also redis-info / redis-flush)"
	@echo "  make kafka-topics     — list Kafka topics"
	@echo "  make kafka-groups     — list consumer groups"
	@echo "  make kafka-consume TOPIC=foo [FROM_BEGINNING=1]  — tail a topic"
	@echo "  make kafka-describe TOPIC=foo         — topic details"
	@echo "  make kafka-group-describe GROUP=bar   — consumer group + lag"
	@echo "  make kafka-shell      — shell into kafka container (advanced)"
	@echo ""
	@echo "Data lifecycle (destructive — read before running):"
	@echo "  make migrate-db-to-shared  — one-time dump+restore from 4 old DBs"
	@echo "                                into mysql-shared (safe to re-run)"
	@echo "  make reset-shared-db       — remove mysql-shared volume. YOU LOSE DATA."
	@echo ""
	@echo "Testing:"
	@echo "  make saga-test        — Testcontainers saga regression (no stack needed)"
	@echo ""
	@echo "Available profile tags: core-plus, email, tracing, logs, kafka-ops,"
	@echo "                        search, cdc, avro, cache, secrets, loadtest, full"

# ─── Core topology targets ─────────────────────────────────────────────

up: up-shared  ## alias

# nano = platform floor. Starts the 7 containers every business service
# depends on. User/product/order/payment-service are NOT started — run
# them from your IDE (`mvn -pl services/user-service spring-boot:run`)
# while they talk to this platform at the usual localhost:8080 etc.
# Note: redis is gated behind the `cache` profile in docker-compose.yml,
# so we enable it here (required by api-gateway).
up-nano:
	COMPOSE_PROFILES=cache docker compose up -d $(NANO_SERVICES)

# nano + zipkin (tracing enabled). Sets TRACING_ENABLED=true so services
# wire up the zipkin reporter; without that flag, nano mode skips tracing
# autoconfig entirely (preventing crashes when zipkin container isn't up).
up-nano-trace:
	TRACING_ENABLED=true \
	ZIPKIN_ENDPOINT=http://zipkin:9411/api/v2/spans \
	COMPOSE_PROFILES=cache,tracing \
	docker compose up -d $(NANO_SERVICES) zipkin

up-minimal:
	docker compose up -d

up-shared:
	$(PROFILES_FULL) docker compose up -d

up-individual:
	$(PROFILES_FULL) docker compose $(COMPOSE_INDIV) up -d

# ─── Slim mix targets (minimal + specific profiles) ───────────────────

# Full saga F1-F3 works: adds notification + resource-server to minimal.
up-saga:
	COMPOSE_PROFILES=core-plus docker compose up -d

# Debug distributed trace path across services.
up-trace:
	COMPOSE_PROFILES=core-plus,tracing,email docker compose up -d

# Browse topics / check consumer lag.
up-kafka-debug:
	COMPOSE_PROFILES=core-plus,kafka-ops docker compose up -d

# Escape hatch — pass any profile combo via env var.
#   make up-with PROFILES="core-plus,tracing,cache"
up-with:
	@if [ -z "$(PROFILES)" ]; then \
		echo "ERROR: pass PROFILES=... (e.g. make up-with PROFILES='core-plus,tracing')"; \
		exit 1; \
	fi
	COMPOSE_PROFILES=$(PROFILES) docker compose up -d

down:
	docker compose $(COMPOSE_INDIV) down

restart: down up

# ─── Observability ─────────────────────────────────────────────────────

ps:
	docker compose ps

stats:
	docker stats --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}"

# One-shot health check across every running container + Spring Boot
# service — compose ps + docker stats + MySQL/Kafka/Redis probes +
# /actuator/health curls + Eureka registration. Exit 0 if all green,
# 1 if anything failed (CI-friendly).
verify:
	./scripts/verify-stack.sh

# ─── MySQL shell shortcuts ─────────────────────────────────────────────
# `make mysql` → root prompt (no schema selected).
# `make mysql-user` / `-product` / `-auth` / `-payment` → drops you
#  straight into that schema. Pulls password from .env.
mysql:
	./scripts/mysql.sh

mysql-user:
	./scripts/mysql.sh userdb

mysql-product:
	./scripts/mysql.sh productdb

mysql-auth:
	./scripts/mysql.sh authdb

mysql-payment:
	./scripts/mysql.sh paymentdb

# ─── Redis shell shortcuts ─────────────────────────────────────────────
# `make redis` → interactive redis-cli prompt.
# `make redis-info`  → one-shot INFO command (version, memory, clients…)
# `make redis-keys`  → list ALL keys currently stored (prod: never do this)
# `make redis-flush` → wipe all data (dev only!)
redis:
	./scripts/redis.sh

redis-info:
	./scripts/redis.sh INFO

redis-keys:
	./scripts/redis.sh KEYS '*'

redis-flush:
	@echo "This will DELETE ALL REDIS DATA. Continue? [y/N]"
	@read -r confirm && [ "$$confirm" = "y" ] || { echo "aborted"; exit 1; }
	./scripts/redis.sh FLUSHALL

# ─── Kafka shortcuts ───────────────────────────────────────────────────
# All go through ./scripts/kafka.sh — long docker exec prefix hidden.
# For anything exotic:  ./scripts/kafka.sh <subcommand>  (see --help).
kafka-topics:
	./scripts/kafka.sh topics

kafka-groups:
	./scripts/kafka.sh groups

# Usage:  make kafka-consume TOPIC=order.created
kafka-consume:
	@if [ -z "$(TOPIC)" ]; then \
		echo "usage: make kafka-consume TOPIC=<topic> [FROM_BEGINNING=1]"; exit 1; \
	fi
	@if [ "$(FROM_BEGINNING)" = "1" ]; then \
		./scripts/kafka.sh consume $(TOPIC) --from-beginning; \
	else \
		./scripts/kafka.sh consume $(TOPIC); \
	fi

# Usage:  make kafka-describe TOPIC=order.created
kafka-describe:
	@if [ -z "$(TOPIC)" ]; then \
		echo "usage: make kafka-describe TOPIC=<topic>"; exit 1; \
	fi
	./scripts/kafka.sh describe $(TOPIC)

# Usage:  make kafka-group-describe GROUP=order-saga
kafka-group-describe:
	@if [ -z "$(GROUP)" ]; then \
		echo "usage: make kafka-group-describe GROUP=<group>"; exit 1; \
	fi
	./scripts/kafka.sh group-describe $(GROUP)

kafka-shell:
	./scripts/kafka.sh shell

logs:
	docker compose logs -f --tail=100

logs-%:
	docker compose logs -f --tail=200 $*

# ─── Data lifecycle ────────────────────────────────────────────────────

migrate-db-to-shared:
	./scripts/migrate-to-shared-db.sh

reset-shared-db:
	@echo "This will DELETE mysql-shared volume and ALL data in userdb,"
	@echo "productdb, authdb, authdb_jdbc, paymentdb. Continue? [y/N]"
	@read -r confirm && [ "$$confirm" = "y" ] || { echo "aborted"; exit 1; }
	docker compose stop mysql-shared
	docker volume rm my-microservices_mysql-shared-data 2>/dev/null || true
	docker compose up -d mysql-shared
	@echo "mysql-shared reset. Schemas will be recreated on next boot."

# ─── Build / testing ───────────────────────────────────────────────────

saga-test:
	mvn -pl services/order-service -am test -Dtest=SagaIntegrationTest \
	    -Dsurefire.failIfNoSpecifiedTests=false

build:
	mvn -am install -DskipTests

.PHONY: help up up-nano up-nano-trace up-shared up-minimal up-individual up-saga up-trace verify \
        mysql mysql-user mysql-product mysql-auth mysql-payment \
        redis redis-info redis-keys redis-flush \
        kafka-topics kafka-groups kafka-consume kafka-describe kafka-group-describe kafka-shell \
        up-kafka-debug up-with down restart ps stats logs \
        migrate-db-to-shared reset-shared-db saga-test build
