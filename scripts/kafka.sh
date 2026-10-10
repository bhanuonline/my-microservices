#!/usr/bin/env bash
# =====================================================================
# kafka.sh — convenience wrapper around Kafka CLI tools inside the
# `kafka` container. Saves typing the long `docker exec kafka
# kafka-X.sh --bootstrap-server localhost:9092 ...` prefix every time.
#
# Usage:
#   ./scripts/kafka.sh topics                      # list all topics
#   ./scripts/kafka.sh topics --create --topic foo --partitions 1 --replication-factor 1
#   ./scripts/kafka.sh describe <topic>            # describe one topic
#   ./scripts/kafka.sh consume <topic>             # tail messages (Ctrl+C to stop)
#   ./scripts/kafka.sh consume <topic> --from-beginning --max-messages 5
#   ./scripts/kafka.sh produce <topic>             # type message, Enter, Ctrl+D
#   ./scripts/kafka.sh groups                      # list consumer groups
#   ./scripts/kafka.sh group-describe <group>      # describe + lag
#   ./scripts/kafka.sh group-reset <group> <topic> # reset offset to earliest
#   ./scripts/kafka.sh shell                       # drop into container shell
#
# Any other first-arg is passed through to kafka-<arg>.sh — advanced
# usage: `./scripts/kafka.sh console-producer --topic foo`.
# =====================================================================

set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# ─── Preflight ────────────────────────────────────────────────────────
if ! docker inspect -f '{{.State.Running}}' kafka 2>/dev/null | grep -q true; then
  echo "ERROR: kafka container isn't running."
  echo "       Start it with:  make up-nano  (or any larger mode)"
  exit 1
fi

BS="--bootstrap-server localhost:9092"
CMD="${1:-}"
shift 2>/dev/null || true

case "$CMD" in
  "" | help | -h | --help)
    sed -n '2,24p' "$0" | sed 's/^# \{0,1\}//'
    exit 0
    ;;

  topics)
    # No sub-args? list topics. Otherwise pass through (e.g. --create).
    if [ $# -eq 0 ]; then
      exec docker exec kafka kafka-topics.sh $BS --list
    else
      exec docker exec kafka kafka-topics.sh $BS "$@"
    fi
    ;;

  describe)
    [ $# -eq 0 ] && { echo "usage: kafka.sh describe <topic>"; exit 1; }
    exec docker exec kafka kafka-topics.sh $BS --describe --topic "$@"
    ;;

  consume)
    [ $# -eq 0 ] && { echo "usage: kafka.sh consume <topic> [--from-beginning] [--max-messages N]"; exit 1; }
    topic="$1"; shift
    exec docker exec -it kafka kafka-console-consumer.sh $BS --topic "$topic" "$@"
    ;;

  produce)
    [ $# -eq 0 ] && { echo "usage: kafka.sh produce <topic>  (type msg, Enter, Ctrl+D to end)"; exit 1; }
    topic="$1"
    exec docker exec -it kafka kafka-console-producer.sh $BS --topic "$topic"
    ;;

  groups)
    exec docker exec kafka kafka-consumer-groups.sh $BS --list
    ;;

  group-describe)
    [ $# -eq 0 ] && { echo "usage: kafka.sh group-describe <group>"; exit 1; }
    exec docker exec kafka kafka-consumer-groups.sh $BS --group "$1" --describe
    ;;

  group-reset)
    if [ $# -lt 2 ]; then
      echo "usage: kafka.sh group-reset <group> <topic>"
      echo "       resets offset to earliest; adds --execute"
      exit 1
    fi
    exec docker exec kafka kafka-consumer-groups.sh $BS \
      --group "$1" --reset-offsets --to-earliest --topic "$2" --execute
    ;;

  shell)
    exec docker exec -it kafka bash
    ;;

  *)
    # Pass-through: `kafka.sh console-consumer --topic foo` → kafka-console-consumer.sh …
    exec docker exec -it kafka "kafka-${CMD}.sh" $BS "$@"
    ;;
esac
