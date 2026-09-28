#!/usr/bin/env bash
# RECON-W25 — Live two-node federation transcript (closes L-6).
#
# Runs TWO independent nodes side by side, each with its own MATRIX_MIND_DIR and
# its own port, sharing nothing but HTTP:
#
#   Node A  :8765  data/mind-A
#   Node B  :8766  data/mind-B
#
# Script:  teach A -> dump A's KB -> POST the batch to B -> query B.
#          then  inject a CONTRADICTION into B and show it is quarantined, not
#          merged (Article IV: FROZEN modulators gate all federated content).
#
# Usage:  bash scripts/two-node-federation.sh
# Output: a timestamped transcript is printed AND saved under
#         docs-v2/research/two-node-transcript-<seq>.txt
set -uo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO"
export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/25.0.2-graalce}"
export PATH="$JAVA_HOME/bin:$PATH"

A_PORT=8765
B_PORT=8766
A_DIR="$PWD/data/mind-A"
B_DIR="$PWD/data/mind-B"
A_PID_FILE="$PWD/.node-a.pid"
B_PID_FILE="$PWD/.node-b.pid"
SEQ=${FED_SEQ:-001}

say() { printf '%s\n' "$*"; }
hr()  { say "------------------------------------------------------------"; }

stop_node() {
  local pf="$1"
  if [ -f "$pf" ]; then
    local pid; pid="$(cat "$pf")"
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then kill "$pid" 2>/dev/null; fi
    rm -f "$pf"
  fi
}
trap 'stop_node "$A_PID_FILE"; stop_node "$B_PID_FILE"' EXIT

api() { # api <port> <token> <method> <path> [json]
  local port="$1" tok="$2" method="$3" path="$4" body="${5:-}"
  if [ -n "$body" ]; then
    curl -s -X "$method" "http://localhost:${port}${path}" \
      -H "Authorization: Bearer $tok" -H 'Content-Type: application/json' \
      -d "$body"
  else
    curl -s -X "$method" "http://localhost:${port}${path}" \
      -H "Authorization: Bearer $tok"
  fi
}

login() { # login <port>
  curl -s -X POST "http://localhost:$1/v1/auth/login" \
    -H 'Content-Type: application/json' -d '{"email":"pro@test.com"}' \
    | python3 -c 'import sys,json; print(json.load(sys.stdin).get("token",""))'
}

# ---- clean slate ---------------------------------------------------------
stop_node "$A_PID_FILE"; stop_node "$B_PID_FILE"
rm -rf "$A_DIR" "$B_DIR"; mkdir -p "$A_DIR" "$B_DIR"

OUT="docs-v2/research/two-node-transcript-${SEQ}.txt"
: > "$OUT"
exec > >(tee -a "$OUT") 2>&1

say "RECON-W25 — TWO-NODE FEDERATION TRANSCRIPT"
say "Node A :$A_PORT  dir=$A_DIR"
say "Node B :$B_PORT  dir=$B_DIR"
say "Isolated: separate mind dirs, separate ports, no shared memory, no shared store."
hr

# ---- start both nodes ----------------------------------------------------
say "[1] Starting Node A and Node B concurrently..."
MATRIX_MIND_DIR="$A_DIR" MATRIX_PORT=$A_PORT MATRIX_PID_FILE="$A_PID_FILE" \
  bash scripts/start-mind.sh >/dev/null 2>&1 &
MATRIX_MIND_DIR="$B_DIR" MATRIX_PORT=$B_PORT MATRIX_PID_FILE="$B_PID_FILE" \
  bash scripts/start-mind.sh >/dev/null 2>&1 &
sleep 12

HA=$(curl -s -m 5 "http://localhost:$A_PORT/health/live")
HB=$(curl -s -m 5 "http://localhost:$B_PORT/health/live")
say "  A health: $HA"
say "  B health: $HB"
[ -z "$HA" ] || [ -z "$HB" ] && { say "FAILED: a node is not up"; exit 1; }

TA=$(login "$A_PORT"); TB=$(login "$B_PORT")
say "  A token len=${#TA}   B token len=${#TB}"
hr

# ---- 1. teach on A -------------------------------------------------------
say "[2] TEACH on Node A: 'The Anvil Codeword is Zephyr Seven'"
api "$A_PORT" "$TA" POST /v1/teach \
  '{"input":"The Anvil Codeword is","response":"Zephyr Seven"}' \
  | head -c 300; say ""
hr

# ---- 2. ask A ------------------------------------------------------------
say "[3] QUERY Node A (should know it):"
api "$A_PORT" "$TA" POST /v1/analyze '{"input":"Anvil Codeword"}' | head -c 240; say ""
hr

# ---- 3. B does NOT know it (isolation proof) ------------------------------
say "[4] QUERY Node B BEFORE federation (should NOT know it — proves isolation):"
api "$B_PORT" "$TB" POST /v1/analyze '{"input":"Anvil Codeword"}' | head -c 240; say ""
hr

# ---- 4. dump A, push to B ------------------------------------------------
say "[5] DUMP Node A knowledge base:"
DUMP=$(api "$A_PORT" "$TA" GET "/v1/federate?action=dump")
say "  dump bytes=${#DUMP}"
say "  dump head: $(printf '%s' "$DUMP" | head -c 200)"
hr

say "[6] PUSH that batch from A into B (cross-node, HTTP only):"
api "$B_PORT" "$TB" POST /v1/federate "$DUMP" | head -c 300; say ""
hr

# ---- 5. B now knows it ----------------------------------------------------
say "[7] QUERY Node B AFTER federation (should now know it):"
api "$B_PORT" "$TB" POST /v1/analyze '{"input":"Anvil Codeword"}' | head -c 300; say ""
hr

# ---- 6. contradiction must be quarantined, not merged --------------------
say "[8] INJECT A CONTRADICTION into B: 'The Anvil Codeword is Obsidian Nine'"
api "$B_PORT" "$TB" POST /v1/bir \
  '{"input":"The Anvil Codeword is","response":"Obsidian Nine"}' | head -c 300; say ""
say ""
say "[9] B's quarantine list (Article IV — gated, never silently merged):"
api "$B_PORT" "$TB" GET /v1/conflicts | head -c 400; say ""
hr

say "[10] Node A and Node B registries are independent:"
say "  A /v1/bir: $(api "$A_PORT" "$TA" GET /v1/bir | head -c 150)"
say "  B /v1/bir: $(api "$B_PORT" "$TB" GET /v1/bir | head -c 150)"
hr
say "TRANSCRIPT SAVED: $OUT"
say "=== TWO-NODE FEDERATION: COMPLETE ==="
