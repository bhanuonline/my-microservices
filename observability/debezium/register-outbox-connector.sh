#!/usr/bin/env bash
# Registers the Debezium Postgres Outbox connector against kafka-connect.
# Run after `docker compose up -d postgres-order kafka-connect` and after
# order-service (running on the docker-cdc profile) has created the
# outbox_events table.
#
# After registration, every INSERT into outbox_events in Postgres becomes a
# message on the Kafka topic named by the `destination` column — exactly like
# OutboxRelay does today, but with sub-50ms latency and no polling.

set -euo pipefail

CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"
NAME="order-outbox-connector"

cat > /tmp/${NAME}.json <<'JSON'
{
  "name": "order-outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "postgres-order",
    "database.port": "5432",
    "database.user": "order",
    "database.password": "order",
    "database.dbname": "orderdb",
    "topic.prefix": "orderdb",
    "schema.include.list": "public",
    "table.include.list": "public.outbox_events",
    "plugin.name": "pgoutput",
    "tombstones.on.delete": "false",

    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.id": "id",
    "transforms.outbox.table.field.event.key": "aggregate_type",
    "transforms.outbox.route.by.field": "destination",
    "transforms.outbox.route.topic.replacement": "${routedByValue}",
    "transforms.outbox.table.field.event.payload": "payload",
    "transforms.outbox.table.fields.additional.placement": "destination:header"
  }
}
JSON

echo "Registering connector at ${CONNECT_URL}/connectors ..."
curl -sS -X POST -H "Content-Type: application/json" \
     --data @/tmp/${NAME}.json \
     "${CONNECT_URL}/connectors" | jq .

echo
echo "Status:"
curl -sS "${CONNECT_URL}/connectors/${NAME}/status" | jq .
