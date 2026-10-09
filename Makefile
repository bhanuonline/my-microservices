# =====================================================================
# Topology-aware wrappers around docker compose.
# See docs/microservices/topologies.md for mode comparison.
#
# Three modes:
#   make up-minimal      → plain `docker compose up` — core services only.
#                          Lightest footprint, ~12 containers.
#   make up [= up-shared]→ shared-db topology with all optional services.
#                          Full-featured, 1 MySQL + 4 schemas, ~26 containers.
#   make up-individual   → shared-db replaced by 4 per-service MySQL
#                          containers. Realistic prod-shaped isolation, ~30 containers.
# =====================================================================

COMPOSE_BASE   := docker-compose.yml
COMPOSE_INDIV  := -f $(COMPOSE_BASE) -f docker-compose.topology-individual.yml

# shared-db (full) and minimal both use the base file; only differ by profile.
PROFILES_FULL  := COMPOSE_PROFILES=full

.DEFAULT_GOAL := help

# ─── Usage / help ──────────────────────────────────────────────────────

help:
	@echo "Topology targets:"
	@echo "  make up-minimal       — core services only, ~12 containers (no profile)"
	@echo "  make up / up-shared   — shared MySQL + all optional services, ~26 containers"
	@echo "  make up-individual    — 4 per-service MySQL + all optional, ~30 containers"
	@echo ""
	@echo "Lifecycle:"
	@echo "  make down             — tear down current stack (any topology)"
	@echo "  make restart          — down + up (default = shared)"
	@echo "  make ps               — show running containers"
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

# ─── Core topology targets ─────────────────────────────────────────────

up: up-shared  ## alias

up-minimal:
	docker compose up -d

up-shared:
	$(PROFILES_FULL) docker compose up -d

up-individual:
	$(PROFILES_FULL) docker compose $(COMPOSE_INDIV) up -d

down:
	docker compose $(COMPOSE_INDIV) down

restart: down up

# ─── Observability ─────────────────────────────────────────────────────

ps:
	docker compose ps

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
	mvn -pl order-service -am test -Dtest=SagaIntegrationTest \
	    -Dsurefire.failIfNoSpecifiedTests=false

build:
	mvn -am install -DskipTests

.PHONY: help up up-shared up-minimal up-individual down restart ps logs \
        migrate-db-to-shared reset-shared-db saga-test build
