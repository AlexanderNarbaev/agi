#!/bin/bash
# MATRIX demo chat — interact with the live MATRIX instance

API_KEY="${MATRIX_API_KEY:-pro@test.com}"
BASE_URL="${MATRIX_BASE_URL:-http://localhost:8765}"

echo "🤖 MATRIX Chat Interface (type 'quit' to exit)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

TOKEN=$(curl -s -X POST "$BASE_URL/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$API_KEY\"}" | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

while true; do
  echo -n "👤 You: "
  read user_input
  if [[ "$user_input" == "quit" ]]; then
    echo "👋 Goodbye!"
    break
  fi
  if [[ -z "$user_input" ]]; then continue; fi

  response=$(curl -s -X POST "$BASE_URL/v1/analyze" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"input\":\"$user_input\",\"modality\":\"text\"}")
  if [ $? -ne 0 ]; then
    echo "MATRIX: (no connection)"
    continue
  fi

  answer=$(echo "$response" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('answer', d.get('error', '?')))" 2>/dev/null)
  confidence=$(echo "$response" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('confidence', '?'))" 2>/dev/null)
  exp_id=$(echo "$response" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('explain_id', ''))" 2>/dev/null)

  echo "🧠 MATRIX: $answer"
  echo "   (confidence: ${confidence}, explain_id: ${exp_id})"
  echo ""
done
