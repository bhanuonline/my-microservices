#!/bin/bash

# ============================================================
# Combined READ Load Test Runner
# Runs k6 read load test + captures Docker container CPU/Memory
# stats in the background, then APPENDS a combined, timestamped
# report to a persistent log file (never overwritten).
# ============================================================

STATS_FILE="docker_stats_snapshot_temp.log"     # temporary, overwritten each run
REPORT_FILE="readtest_history.log"              # permanent, appended every run
K6_SCRIPT="loadtest-read.js"

RUN_TIMESTAMP=$(date "+%Y-%m-%d %H:%M:%S")

echo "Starting Docker stats monitoring in background..."
> "$STATS_FILE"
(
  while true; do
    echo "---- $(date +%H:%M:%S) ----" >> "$STATS_FILE"
    docker stats --no-stream --format "{{.Name}}\t{{.CPUPerc}}\t{{.MemPerc}}" >> "$STATS_FILE"
    sleep 2
  done
) &
STATS_PID=$!

echo "Running k6 READ load test..."
K6_OUTPUT=$(k6 run "$K6_SCRIPT" 2>&1)
echo "$K6_OUTPUT"

echo "Stopping Docker stats monitoring..."
kill $STATS_PID 2>/dev/null
sleep 1

RESOURCE_REPORT=$(awk '
/^[a-zA-Z0-9_-]+\t/ {
    split($0, fields, "\t")
    name = fields[1]
    cpu = fields[2]
    mem = fields[3]
    gsub("%", "", cpu)
    gsub("%", "", mem)
    cpu_sum[name] += cpu
    mem_sum[name] += mem
    count[name]++
}
END {
    printf "%-20s %-15s %-15s\n", "CONTAINER", "AVG CPU %", "AVG MEM %"
    for (name in cpu_sum) {
        printf "%-20s %-15.2f %-15.2f\n", name, cpu_sum[name]/count[name], mem_sum[name]/count[name]
    }
}
' "$STATS_FILE")

{
  echo ""
  echo "################################################################"
  echo "# READ TEST RUN AT: $RUN_TIMESTAMP"
  echo "################################################################"
  echo "$K6_OUTPUT"
  echo ""
  echo "---------------- RESOURCE USAGE DURING TEST ----------------"
  echo "$RESOURCE_REPORT"
  echo "################################################################"
  echo ""
} >> "$REPORT_FILE"

echo ""
echo "Results appended to: $REPORT_FILE"
echo "(Open that file to see this run alongside all previous read test runs.)"
