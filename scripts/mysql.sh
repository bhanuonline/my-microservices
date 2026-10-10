#!/usr/bin/env bash
# =====================================================================
# mysql.sh — drop into the MySQL shell inside the mysql-shared container
#
# Pulls MYSQL_ROOT_PASSWORD from .env so you don't have to type it.
# Pre-selects a schema if you pass one as the first argument.
#
# Usage:
#   ./scripts/mysql.sh                 # root prompt, no schema selected
#   ./scripts/mysql.sh userdb          # connect + USE userdb
#   ./scripts/mysql.sh productdb
#   ./scripts/mysql.sh authdb
#   ./scripts/mysql.sh paymentdb
#
# Or run a one-shot query:
#   ./scripts/mysql.sh userdb -e "SHOW TABLES;"
# =====================================================================

set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# ─── Preflight ────────────────────────────────────────────────────────
if ! docker inspect -f '{{.State.Running}}' mysql-shared 2>/dev/null | grep -q true; then
  echo "ERROR: mysql-shared container isn't running."
  echo "       Start it with:  make up-nano  (or any larger mode)"
  exit 1
fi

MYSQL_PW="$(grep -E '^MYSQL_ROOT_PASSWORD=' .env 2>/dev/null | cut -d= -f2)"
if [ -z "$MYSQL_PW" ]; then
  echo "ERROR: MYSQL_ROOT_PASSWORD not set in .env"
  exit 1
fi

# ─── Build the mysql command ──────────────────────────────────────────
SCHEMA="${1:-}"
shift 2>/dev/null || true   # if no args, shift is a no-op in bash

if [ -n "$SCHEMA" ]; then
  # schema named: use it + pass remaining args (for -e queries etc)
  exec docker exec -it mysql-shared mysql -uroot -p"$MYSQL_PW" "$SCHEMA" "$@"
else
  # no schema: interactive root prompt, no USE
  exec docker exec -it mysql-shared mysql -uroot -p"$MYSQL_PW"
fi
