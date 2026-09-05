#!/bin/bash
# Usage: ./analyze-day.sh 2026-09-04 ensemble 99926000 FIVE_MINUTE [EXCHANGE] [CPS]
#
# Examples:
#   ./analyze-day.sh                                       # today, ensemble, Nifty, 5-min
#   ./analyze-day.sh 2026-09-04 ob-retest 99926009 FIFTEEN_MINUTE
#   ./analyze-day.sh 2026-09-04 ensemble 436250 FIVE_MINUTE MCX

DATE=${1:-$(date +%Y-%m-%d)}
STRATEGY=${2:-ensemble}
TOKEN=${3:-99926000}
INTERVAL=${4:-FIVE_MINUTE}
EXCHANGE=${5:-NSE}
CPS=${6:-30}
BASE_URL=${BASE_URL:-http://localhost:9010}

echo "=== Analyzing $DATE with $STRATEGY on $EXCHANGE:$TOKEN @ $INTERVAL ==="

SESSION=$(curl -s -X POST "$BASE_URL/api/paper/sessions" \
  -H "Content-Type: application/json" \
  -d "{
    \"strategyName\": \"$STRATEGY\",
    \"sourceType\": \"angel-historical-replay\",
    \"symbolToken\": \"$TOKEN\",
    \"exchange\": \"$EXCHANGE\",
    \"interval\": \"$INTERVAL\",
    \"from\": \"$DATE\",
    \"to\": \"$DATE\",
    \"candlesPerSecond\": $CPS
  }" | jq -r '.sessionId')

echo "Session started: $SESSION"
echo "Waiting for replay to complete..."
sleep 10

echo ""
echo "=== Results ==="
curl -s "$BASE_URL/api/paper/sessions/$SESSION" \
  | jq '{status, candleCount, totalTrades, winners, losers, netPnl}'

echo ""
echo "=== Trades ==="
curl -s "$BASE_URL/api/paper/sessions/$SESSION" \
  | jq '.trades[] | {entry: .entryPrice, exit: .exitPrice, reason: .exitReason, pnl}'