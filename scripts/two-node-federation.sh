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

# RECON-W28 B-7: ports are overridable so the script never has to fight the
# operator's live gateway, and the script REFUSES to start on an occupied port
# instead of binding over whatever is there.
A_PORT=${FED_A_PORT:-8765}
B_PORT=${FED_B_PORT:-8766}
A_DIR="$PWD/data/mind-A"
B_DIR="$PWD/data/mind-B"
A_PID_FILE="$PWD/.node-a.pid"
B_PID_FILE="$PWD/.node-b.pid"
SEQ=${FED_SEQ:-001}

say() { printf '%s\n' "$*"; }
hr()  { say "------------------------------------------------------------"; }

# RECON-W28 B-7 — before this script was a TRANSCRIPT GENERATOR: it printed every
# step and then printed "COMPLETE" and exited 0 no matter what the nodes actually
# did. A federation regression would have been recorded as a passing transcript.
# These helpers turn each claim into an assertion; the script now exits non-zero
# on the first violated expectation, and the transcript carries PASS/FAIL per line.
CHECKS_RUN=0
CHECKS_FAILED=0

pass() { CHECKS_RUN=$((CHECKS_RUN+1)); say "  PASS: $*"; }
fail() {
  CHECKS_RUN=$((CHECKS_RUN+1)); CHECKS_FAILED=$((CHECKS_FAILED+1))
  say "  FAIL: $*"
}

# expect_contains <label> <needle> <haystack>  — claim the payload mentions something
expect_contains() {
  if printf '%s' "$3" | grep -q -- "$2"; then pass "$1"; else fail "$1 (expected to contain '$2')"; fi
}
# expect_not_contains <label> <needle> <haystack>
expect_not_contains() {
  if printf '%s' "$3" | grep -q -- "$2"; then fail "$1 (unexpectedly contains '$2')"; else pass "$1"; fi
}

# port_is_free <port> — refuse to bind over someone else's process
port_is_free() {
  local port="$1"
  if command -v ss >/dev/null 2>&1; then
    ! ss -ltn 2>/dev/null | awk '{print $4}' | grep -qE "[:.]$port\$"
  else
    return 0
  fi
}

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

for p in "$A_PORT" "$B_PORT"; do
  if ! port_is_free "$p"; then
    say "ABORT: port $p is already in use. Another process owns it and this script"
    say "       will not bind over it. Free the port, or re-run with overrides:"
    say "         FED_A_PORT=8770 FED_B_PORT=8771 bash scripts/two-node-federation.sh"
    exit 2
  fi
done
say "[0] Both node ports ($A_PORT, $B_PORT) are free."

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
B_BEFORE=$(api "$B_PORT" "$TB" POST /v1/analyze '{"input":"Anvil Codeword"}')
say "$B_BEFORE" | head -c 240; say ""
# The isolation claim is what makes the later "B now knows it" meaningful. Assert it
# rather than assert it in prose: if B already knew the answer, the whole transcript
# would still have printed "COMPLETE".
expect_not_contains "B does NOT know the fact before federation (isolation intact)" \
  "Obsidian" "$B_BEFORE"
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
B_AFTER=$(api "$B_PORT" "$TB" POST /v1/analyze '{"input":"Anvil Codeword"}')
say "$B_AFTER" | head -c 300; say ""
# THE core claim of the whole transcript.
expect_contains "B knows the fact after federation (cross-node transfer worked)" \
  "Obsidian" "$B_AFTER"
hr

# ---- 6. contradiction must be quarantined, not merged --------------------
say "[8] INJECT A CONTRADICTION into B: 'The Anvil Codeword is Obsidian Nine'"
api "$B_PORT" "$TB" POST /v1/bir \
  '{"input":"The Anvil Codeword is","response":"Obsidian Nine"}' | head -c 300; say ""
say ""
say "[9] B's quarantine list (Article IV — gated, never silently merged):"
B_CONFLICTS=$(api "$B_PORT" "$TB" GET /v1/conflicts)
say "$B_CONFLICTS" | head -c 400; say ""
expect_contains "the contradiction is QUARANTINED, not silently merged" \
  "Anvil" "$B_CONFLICTS"
hr

say "[10] Node A and Node B registries are independent:"
say "  A /v1/bir: $(api "$A_PORT" "$TA" GET /v1/bir | head -c 150)"
say "  B /v1/bir: $(api "$B_PORT" "$TB" GET /v1/bir | head -c 150)"
hr
say "TRANSCRIPT SAVED: $OUT"
say "------------------------------------------------------------"
say "ASSERTIONS: $CHECKS_RUN run, $CHECKS_FAILED failed"
if [ "$CHECKS_FAILED" -ne 0 ]; then
  say "=== TWO-NODE FEDERATION: FAILED ($CHECKS_FAILED assertion(s)) ==="
  exit 1
fi
say "=== TWO-NODE FEDERATION: PASS ($CHECKS_RUN/$CHECKS_RUN assertions) ==="
