# =====================================================================
# Topology-aware + profile-aware wrappers around docker compose.
# See docs/microservices/topologies.md for the full comparison.
#
# TOPOLOGIES (pick one; mutually exclusive):
#   make up-minimal      → core only. ~11 containers. Lightest footprint.
#   make up / up-shared  → shared-db + all optional (full profile). ~25 containers.
#   make up-individual   → shared-db + 4 per-service MySQLs. ~29 containers.
#
# SLIM MIXES (just the extras you need; still minimal topology):
#   make up-saga         → minimal + core-plus. Full F1-F3 saga, ~13 containers (~2.8 GB)
#   make up-trace        → minimal + core-plus + tracing + email. ~16 containers (~3.3 GB)
#   make up-kafka-debug  → minimal + core-plus + kafka-ops. ~15 containers (~3.1 GB)
#   make up-with PROFILES="email,tracing"  → minimal + ANY profiles you name
#
# Available profile tags (combine freely):
#   core-plus, email, tracing, logs, kafka-ops, search, cdc, avro,
#   cache, secrets, loadtest, full
# =====================================================================

COMPOSE_BASE   := docker-compose.yml
COMPOSE_INDIV  := -f $(COMPOSE_BASE) -f docker-compose.topology-individual.yml

PROFILES_FULL  := COMPOSE_PROFILES=full

.DEFAULT_GOAL := help

# ─── Usage / help ──────────────────────────────────────────────────────

help:
	@echo "Topology (pick one):"
	@echo "  make up-minimal       — core only, ~11 containers (~2.5 GB)"
	@echo "  make up / up-shared   — shared MySQL + ALL optional, ~25 containers (~5.5 GB)"
	@echo "  make up-individual    — shared-db + 4 per-service MySQL, ~29 containers (~6.5 GB)"
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
	@echo "  make logs             — tail all logs"
	@echo "  make logs-SERVICE     — tail logs for one service"
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

.PHONY: help up up-shared up-minimal up-individual up-saga up-trace \
        up-kafka-debug up-with down restart ps stats logs \
        migrate-db-to-shared reset-shared-db saga-test build
