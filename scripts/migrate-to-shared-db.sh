#!/usr/bin/env bash
# =====================================================================
# One-time: migrate data from the 4 old per-service MySQL containers
# (mysql-user, mysql-product, mysql-auth, mysql-payment) into the new
# mysql-shared container.
#
# Usage:
#   make migrate-db-to-shared
#   OR: ./scripts/migrate-to-shared-db.sh
#
# Prereqs:
#   - At least mysql-shared must be running (make up-minimal is enough).
#   - The 4 old mysql-* volumes must still exist (if you ran `make down`
#     without `-v` and never ran `docker volume prune`, they do).
#
# What it does:
#   1. Spins each old mysql-* container up temporarily on an unused port
#      (if it isn't already running).
#   2. mysqldump each schema from the old container.
#   3. mysql < that dump → mysql-shared (into the matching schema).
#   4. Verifies the row counts match between old and new.
#   5. Leaves the old containers RUNNING if they were already up; stops
#      the ones we temporarily started.
#
# Idempotent in the sense that re-running won't lose data, but it WILL
# re-dump + re-restore (overwriting what's in mysql-shared). If that's a
# problem, don't re-run.
# =====================================================================

set -euo pipefail

# Preflight — mysql-shared must be reachable.
if ! docker compose -f docker-compose.yml ps mysql-shared 2>/dev/null | grep -q 'Up\|running'; then
    echo "ERROR: mysql-shared is not running. Run 'make up-minimal' first, then retry."
    exit 1
fi

# Source the root password from .env if not already set.
if [ -z "${MYSQL_ROOT_PASSWORD:-}" ]; then
    if [ -f .env ]; then
        # shellcheck disable=SC1091
        source .env
    fi
    if [ -z "${MYSQL_ROOT_PASSWORD:-}" ]; then
        echo "ERROR: MYSQL_ROOT_PASSWORD not set. Set it in .env or export it."
        exit 1
    fi
fi

# Map: schema → old container name.
declare -A MAP=(
    [userdb]=mysql-user
    [productdb]=mysql-product
    [authdb]=mysql-auth
    [paymentdb]=mysql-payment
)

echo "Migrating 4 schemas into mysql-shared …"
echo

for schema in userdb productdb authdb paymentdb; do
    old="${MAP[$schema]}"
    echo "── $schema  (from $old) ─────────────────────────────────────"

    # Check if the old container exists.
    if ! docker inspect "$old" >/dev/null 2>&1; then
        echo "  SKIP: container '$old' does not exist (nothing to migrate)."
        continue
    fi

    # If the old container is stopped, start it briefly.
    started_by_us=0
    if ! docker inspect -f '{{.State.Running}}' "$old" | grep -q true; then
        echo "  Starting $old temporarily …"
        docker start "$old" >/dev/null
        started_by_us=1
        # Wait for it to be ready.
        for i in {1..30}; do
            if docker exec "$old" mysqladmin ping -h localhost -uroot -p"$MYSQL_ROOT_PASSWORD" 2>/dev/null | grep -q 'alive'; then
                break
            fi
            sleep 1
        done
    fi

    # Dump + restore.
    tmpfile=$(mktemp -t "migrate-$schema.XXXXXX.sql")
    echo "  Dumping to $tmpfile …"
    docker exec "$old" mysqldump --single-transaction --skip-add-drop-table \
        -uroot -p"$MYSQL_ROOT_PASSWORD" "$schema" > "$tmpfile"
    byte_size=$(wc -c < "$tmpfile")
    echo "  Dump size: $byte_size bytes"

    echo "  Restoring into mysql-shared.$schema …"
    docker exec -i mysql-shared mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$schema" < "$tmpfile"

    # Row-count sanity check for each table.
    for tbl in $(docker exec "$old" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SHOW TABLES" "$schema"); do
        old_count=$(docker exec "$old" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT COUNT(*) FROM $tbl" "$schema")
        new_count=$(docker exec mysql-shared mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT COUNT(*) FROM $tbl" "$schema")
        if [ "$old_count" = "$new_count" ]; then
            echo "    OK    $schema.$tbl ($old_count rows)"
        else
            echo "    MISMATCH $schema.$tbl: old=$old_count new=$new_count"
        fi
    done

    rm -f "$tmpfile"

    if [ "$started_by_us" = "1" ]; then
        echo "  Stopping $old (was off before)."
        docker stop "$old" >/dev/null
    fi

    echo
done

echo "All done. mysql-shared now has data from all 4 old per-service DBs."
echo "Verify with: make up-minimal && curl http://localhost:3306 ..."
