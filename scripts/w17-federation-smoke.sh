#!/usr/bin/env bash
# RECON-W17 — Two-node federation smoke test.
# Launches the gateway twice sequentially to simulate two nodes.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/25.0.2-graalce}"
export PATH="$JAVA_HOME/bin:$PATH"
if [ -f .gateway.pid ]; then PID=$(cat .gateway.pid); kill "$PID" 2>/dev/null || true; rm -f .gateway.pid; fi
NODE_A_MIND="$PWD/data/mind-A"
rm -rf "$NODE_A_MIND"; mkdir -p "$NODE_A_MIND"
MATRIX_MIND_DIR="$NODE_A_MIND" bash scripts/start-mind.sh > /tmp/node-A.log 2>&1 &
NODE_A_PID=$!
echo "Node A PID=$NODE_A_PID"; sleep 5
TOKEN_A=$(curl -s -X POST http://localhost:8765/v1/auth/login -H "Content-Type: application/json" -d "{\"email\":\"pro@test.com\"}" | python3 -c "import sys,json; print(json.load(sys.stdin)[\"token\"])")
TEACH=$(curl -s -X POST http://localhost:8765/v1/teach -H "Authorization: Bearer $TOKEN_A" -H "Content-Type: application/json" -d "{\"input\":\"Alice lives in Paris\",\"answer\":\"Yes — Alice lives in Paris.\"}")
echo "TEACH-A: $TEACH" | head -c 300; echo
DUMP=$(curl -s "http://localhost:8765/v1/federate?action=dump" -H "Authorization: Bearer $TOKEN_A")
echo "DUMP-A: $(echo $DUMP | head -c 300)" | head -c 300
echo
kill "$NODE_A_PID" 2>/dev/null; sleep 2
NODE_B_MIND="$PWD/data/mind-B"
rm -rf "$NODE_B_MIND"; mkdir -p "$NODE_B_MIND"
MATRIX_MIND_DIR="$NODE_B_MIND" bash scripts/start-mind.sh > /tmp/node-B.log 2>&1 &
NODE_B_PID=$!
echo "Node B PID=$NODE_B_PID"; sleep 5
TOKEN_B=$(curl -s -X POST http://localhost:8765/v1/auth/login -H "Content-Type: application/json" -d "{\"email\":\"pro@test.com\"}" | python3 -c "import sys,json; print(json.load(sys.stdin)[\"token\"])")
FED=$(curl -s -X POST http://localhost:8765/v1/federate -H "Authorization: Bearer $TOKEN_B" -H "Content-Type: application/json" --data-raw "$DUMP")
echo "FED-RESP: $(echo $FED | head -c 300)" | head -c 300
echo
QUERY=$(curl -s -X POST http://localhost:8765/v1/analyze -H "Authorization: Bearer $TOKEN_B" -H "Content-Type: application/json" -d "{\"input\":\"Where does Alice live?\"}")
echo "QUERY-B: $QUERY" | head -c 400
echo
kill "$NODE_B_PID" 2>/dev/null; sleep 1
echo "W17 SMOKE: COMPLETE"
