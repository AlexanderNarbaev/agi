#!/bin/bash
# Live audit log stream — polls /v1/audit/logs every 3 seconds

BASE_URL="${BASE_URL:-http://localhost:8765}"
TOKEN=$(curl -s -X POST "$BASE_URL/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"ent@enterprise.com"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

LAST_ID=0
echo "🔍 MATRIX Audit Log Stream (Ctrl+C to stop)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

while true; do
  response=$(curl -s -X GET "$BASE_URL/v1/audit/logs?limit=20" \
    -H "Authorization: Bearer $TOKEN" 2>/dev/null)
  if [ -z "$response" ]; then
    sleep 3
    continue
  fi

  events=$(echo "$response" | python3 -c "
import sys,json
d = json.load(sys.stdin)
events = d.get('events', [])
for e in events:
    if int(e['id']) > $LAST_ID:
        print(f\"{e['timestamp']} | {e['event_type']:20s} | User: {e['user_id']:30s} | Action: {e['action'][:50]}\")
" 2>/dev/null)
  if [ -n "$events" ]; then
    echo "$events"
    LAST_ID=$(echo "$response" | python3 -c "
import sys,json
d = json.load(sys.stdin)
events = d.get('events', [])
if events:
    print(max(int(e['id']) for e in events))
else:
    print($LAST_ID)
" 2>/dev/null)
  fi

  sleep 3
done
