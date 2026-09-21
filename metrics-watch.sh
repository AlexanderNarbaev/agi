#!/bin/bash
# Real-time metrics viewer for MATRIX Production Ecosystem
# Polls Prometheus every 5 seconds, prints metrics dashboard

PROM_URL="${PROM_URL:-http://localhost:9094}"
HEALTH_URL="${HEALTH_URL:-http://localhost:8765/health/live}"

echo "📊 MATRIX Live Metrics (Ctrl+C to stop)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Generate a few requests for visibility
TOKEN=$(curl -s -X POST http://localhost:8765/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"pro@test.com"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

COUNT=0
while true; do
  clear
  echo "⏰ $(date '+%Y-%m-%d %H:%M:%S')"
  echo ""
  COUNT=$((COUNT+1))

  # API requests total
  rpm=$(curl -sg "$PROM_URL/api/v1/query?query=matrix_requests_total" 2>/dev/null | \
    python3 -c "import sys,json; d=json.load(sys.stdin); r=d['data']['result']; print(sum(float(x['value'][1]) for x in r))" 2>/dev/null)

  # Active explanations cached
  exp=$(curl -sg "$PROM_URL/api/v1/query?query=matrix_explanations_cached" 2>/dev/null | \
    python3 -c "import sys,json; d=json.load(sys.stdin); r=d['data']['result']; print(r[0]['value'][1] if r else 0)" 2>/dev/null)

  # Audit events
  aud=$(curl -sg "$PROM_URL/api/v1/query?query=matrix_audit_events" 2>/dev/null | \
    python3 -c "import sys,json; d=json.load(sys.stdin); r=d['data']['result']; print(r[0]['value'][1] if r else 0)" 2>/dev/null)

  # Health
  health=$(curl -s "$HEALTH_URL" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status', '?'))" 2>/dev/null)

  echo "📡 API Requests (total): ${rpm:-0}"
  echo "🧠 Active Explainments: ${exp:-0}"
  echo "🔍 Audit Events: ${aud:-0}"
  echo "❤  Health: ${health:-UNKNOWN}"
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

  # Generate a demo request every 5 cycles
  if [ $((COUNT % 3)) -eq 0 ]; then
    curl -s -X POST http://localhost:8765/v1/analyze \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -d "{\"input\":\"metrics viewer tick #$COUNT\"}" > /dev/null 2>&1
  fi
  sleep 5
done
