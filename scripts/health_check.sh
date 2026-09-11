#!/bin/bash
# MATRIX production health check
# Usage: ./scripts/health_check.sh [host] [port]
# Default: localhost:9091

set -euo pipefail

HOST="${1:-localhost}"
PORT="${2:-9091}"
BASE_URL="http://$HOST:$PORT"

echo "[health_check] Testing MATRIX at $BASE_URL"

# 1. /q/health (Quarkus health)
echo -n "  /q/health... "
HEALTH=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$BASE_URL/q/health" || echo "000")
if [ "$HEALTH" = "200" ]; then
    echo "OK ($HEALTH)"
else
    echo "FAIL ($HEALTH)"
    exit 1
fi

# 2. /v1/sandbox/inspect (chain state)
echo -n "  /v1/sandbox/inspect... "
INSPECT=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$BASE_URL/v1/sandbox/inspect" || echo "000")
if [ "$INSPECT" = "200" ]; then
    echo "OK ($INSPECT)"
else
    echo "FAIL ($INSPECT)"
    exit 1
fi

# 3. /q/metrics (Prometheus metrics)
echo -n "  /q/metrics... "
METRICS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$BASE_URL/q/metrics" || echo "000")
if [ "$METRICS" = "200" ]; then
    echo "OK ($METRICS)"
else
    echo "FAIL ($METRICS)"
    exit 1
fi

echo "[health_check] All checks passed"
