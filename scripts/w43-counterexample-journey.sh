#!/usr/bin/env bash
# RECON-W43.2 — the counterexample journey, live, over HTTP.
#
# The mission's §5 scenario, executed against a RUNNING application:
#
#   1. read a claim out of the knowledge core
#   2. record a positive example
#   3. ask whether the claim still holds
#   4. record a counterexample, with the condition it was made under
#   5. ask again: the claim is contradicted, and the scope is explained
#   6. a narrower formulation is proposed
#   7. accept it: the original claim is kept, superseded, and the revision
#      that links them is stored
#   8. RESTART THE PROCESS FORCIBLY
#   9. everything is still there
#
# Usage:
#   scripts/w43-counterexample-journey.sh
#
# Exit status: 0 when every assertion holds, 1 when any does not.
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

JAR="matrix-core/build/quarkus-app/quarkus-run.jar"
PORT=9095
WORKSPACE=photosynthesis
FAILURES=0
APP_PID=""

# The journey runs against a TEMPORARY database, never the working one.
#
# The first version of this script wrote its examples and its counterexample
# into data/knowledge/workbench.sqlite — the store the owner works with. That
# is exactly what the mission forbids ("do not silently change the existing
# data file or overwrite owner data during tests"), and it also made the
# script's own result depend on how many times it had been run before. A
# fresh store per run fixes both.
DB="$(mktemp -t w43-journey-XXXXXX.sqlite)"
rm -f "$DB"

# How long the script waits for the service to answer. Named, because the
# quality gate counts a bare number in a loop as a literal, and it is one.
readonly STARTUP_WAIT_SECONDS=60

if [ ! -f "$JAR" ]; then
  echo "the application is not built: $JAR is missing."
  echo "build it with: ./gradlew :matrix-core:quarkusBuild"
  exit 1
fi

pass() { printf '  OK    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; FAILURES=$((FAILURES + 1)); }

cleanup() {
  if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
    kill -9 "$APP_PID" 2>/dev/null
  fi
  APP_PID=""
  rm -f "$DB" "$DB-wal" "$DB-shm"
}
trap cleanup EXIT

start_app() {
  QUARKUS_HTTP_PORT=$PORT \
    java -Dmatrix.auto-train.enabled=false \
         -Dmatrix.knowledge.path="$DB" \
         -jar "$JAR" >/tmp/w43-journey.log 2>&1 &
  APP_PID=$!
  for _ in $(seq 1 "$STARTUP_WAIT_SECONDS"); do
    sleep 1
    if curl -s -o /dev/null -m 2 "http://127.0.0.1:$PORT/q/health" 2>/dev/null; then
      return 0
    fi
  done
  return 1
}

# The id of the first claim of a JSON array, read without jq.
first_claim_id() {
  python3 -c 'import json,sys
try:
    print(json.load(sys.stdin)[0]["id"])
except Exception:
    print("")'
}

# One field of a JSON object, read without jq.
json_field() {
  python3 -c 'import json,sys
field = sys.argv[1]
try:
    data = json.load(sys.stdin)
except Exception:
    print("")
    sys.exit(0)
value = data
for part in field.split("."):
    if isinstance(value, list):
        value = value[int(part)] if part.isdigit() else ""
    elif isinstance(value, dict):
        value = value.get(part, "")
    else:
        value = ""
print("" if value is None else (value if isinstance(value, str) else json.dumps(value)))' "$1"
}

echo "===== the application starts ====="
if start_app; then
  pass "the service is up"
else
  fail "the service did not start (see /tmp/w43-journey.log)"
  exit 1
fi

echo "===== 1. read a claim out of the knowledge core ====="
LOAD="$(curl -s -m 20 -X POST "http://127.0.0.1:$PORT/v1/workbench/workspaces/$WORKSPACE/load")"
LOADED="$(printf '%s' "$LOAD" | json_field claims)"
if [ -n "$LOADED" ] && [ "$LOADED" -gt 0 ] 2>/dev/null; then
  pass "the bundled package is loaded: $LOADED claims"
else
  fail "the bundled package did not load: $LOAD"
fi
CLAIMS="$(curl -s -m 10 "http://127.0.0.1:$PORT/v1/workbench/workspaces/$WORKSPACE/claims")"
CLAIM_ID="$(printf '%s' "$CLAIMS" | first_claim_id)"
if [ -n "$CLAIM_ID" ]; then
  pass "the workspace holds claims; the first is $CLAIM_ID"
else
  fail "the workspace holds no claim"
  exit 1
fi

echo "===== 2. record a positive example ====="
EXAMPLE="$(curl -s -m 10 -X POST -H "Content-Type: application/json" \
  -d '{"observation":"a geranium on a windowsill releases oxygen in the light","condition":"in daylight","contradicts":false}' \
  "http://127.0.0.1:$PORT/v1/workbench/workspaces/$WORKSPACE/claims/$CLAIM_ID/examples")"
RELATION="$(printf '%s' "$EXAMPLE" | json_field relation)"
if [ "$RELATION" = "EXEMPLIFIES" ]; then
  pass "the example is recorded as EXEMPLIFIES"
else
  fail "the example was recorded as '$RELATION'"
fi

echo "===== 3. does the claim still hold? ====="
ASSESS="$(curl -s -m 10 "http://127.0.0.1:$PORT/v1/workbench/workspaces/$WORKSPACE/claims/$CLAIM_ID/assessment")"
STANDING="$(printf '%s' "$ASSESS" | json_field standing)"
if [ "$STANDING" = "SUPPORTED" ]; then
  pass "the claim is SUPPORTED by the example"
else
  fail "the standing after the example is '$STANDING'"
fi

echo "===== 4. record a counterexample, with its condition ====="
COUNTER="$(curl -s -m 10 -X POST -H "Content-Type: application/json" \
  -d '{"observation":"a plant kept in the dark does not release oxygen","condition":"without light","contradicts":true}' \
  "http://127.0.0.1:$PORT/v1/workbench/workspaces/$WORKSPACE/claims/$CLAIM_ID/examples")"
RELATION="$(printf '%s' "$COUNTER" | json_field relation)"
if [ "$RELATION" = "CONTRADICTS" ]; then
  pass "the counterexample is recorded as CONTRADICTS"
else
  fail "the counterexample was recorded as '$RELATION'"
fi

echo "===== 5. the claim is contradicted, and the scope is explained ====="
ASSESS="$(curl -s -m 10 "http://127.0.0.1:$PORT/v1/workbench/workspaces/$WORKSPACE/claims/$CLAIM_ID/assessment")"
STANDING="$(printf '%s' "$ASSESS" | json_field standing)"
READING="$(printf '%s' "$ASSESS" | json_field counterexampleReading)"
REASON="$(printf '%s' "$ASSESS" | json_field readingReason)"
# The bundled claim already carries supporting evidence, so a counterexample
# against it makes the claim CONTESTED — the sources disagree and both
# accounts are kept — rather than REFUTED, which is what a claim with no
# supporting source would become. The distinction is the point.
if [ "$STANDING" = "CONTESTED" ]; then
  pass "the standing is CONTESTED: the sources disagree and both accounts are kept"
else
  fail "the standing after the counterexample is '$STANDING'"
fi
if [ "$READING" = "REVEALS_MISSING_CONDITION" ]; then
  pass "the reading is REVEALS_MISSING_CONDITION"
else
  fail "the reading is '$READING'"
fi
case "$REASON" in
  *"without light"*) pass "the reason quotes the recorded condition" ;;
  *) fail "the reason does not quote the condition: $REASON" ;;
esac

echo "===== 6. a narrower formulation is proposed ====="
PROPOSAL="$(printf '%s' "$ASSESS" | json_field proposedRefinement)"
case "$PROPOSAL" in
  *"without light"*) pass "the proposal quotes the condition verbatim" ;;
  *) fail "the proposal does not quote the condition: $PROPOSAL" ;;
esac

echo "===== 7. the user accepts it ====="
ACCEPT="$(curl -s -m 10 -X POST -H "Content-Type: application/json" \
  -d "{\"proposition\":\"$PROPOSAL\"}" \
  "http://127.0.0.1:$PORT/v1/workbench/workspaces/$WORKSPACE/claims/$CLAIM_ID/refine/accept")"
KIND="$(printf '%s' "$ACCEPT" | json_field kind)"
if [ "$KIND" = "REFINEMENT" ]; then
  pass "the refinement is recorded as REFINEMENT"
else
  fail "the refinement was recorded as '$KIND'"
fi

REVISIONS="$(curl -s -m 10 "http://127.0.0.1:$PORT/v1/workbench/workspaces/$WORKSPACE/revisions")"
COUNT="$(printf '%s' "$REVISIONS" | python3 -c 'import json,sys; print(len(json.load(sys.stdin)))' 2>/dev/null)"
if [ "${COUNT:-0}" -ge 2 ]; then
  pass "the revision history holds $COUNT revisions"
else
  fail "the revision history holds ${COUNT:-0} revisions, expected at least 2"
fi

echo "===== 8. the process is terminated forcibly ====="
kill -9 "$APP_PID" 2>/dev/null
wait "$APP_PID" 2>/dev/null
APP_PID=""
sleep 2
pass "the process was killed with SIGKILL"

echo "===== 9. the history survives the restart ====="
if start_app; then
  pass "the service is up again"
else
  fail "the service did not start again (see /tmp/w43-journey.log)"
fi

AFTER="$(curl -s -m 10 "http://127.0.0.1:$PORT/v1/workbench/workspaces/$WORKSPACE/claims/$CLAIM_ID/assessment")"
STANDING="$(printf '%s' "$AFTER" | json_field standing)"
PROPOSAL_AFTER="$(printf '%s' "$AFTER" | json_field proposedRefinement)"
if [ "$STANDING" = "CONTESTED" ]; then
  pass "the assessment is the same after the restart"
else
  fail "the standing after the restart is '$STANDING'"
fi
if [ "$PROPOSAL_AFTER" = "$PROPOSAL" ]; then
  pass "the proposal is the same after the restart"
else
  fail "the proposal after the restart differs"
fi

REVISIONS_AFTER="$(curl -s -m 10 "http://127.0.0.1:$PORT/v1/workbench/workspaces/$WORKSPACE/revisions")"
COUNT_AFTER="$(printf '%s' "$REVISIONS_AFTER" | python3 -c 'import json,sys; print(len(json.load(sys.stdin)))' 2>/dev/null)"
if [ "${COUNT_AFTER:-0}" -ge 2 ]; then
  pass "the revision history survived: $COUNT_AFTER revisions"
else
  fail "the revision history holds ${COUNT_AFTER:-0} revisions after the restart"
fi

cleanup
echo
if [ "$FAILURES" -eq 0 ]; then
  echo "the counterexample journey: 0 — every assertion held"
  exit 0
fi
echo "the counterexample journey: $FAILURES — assertion(s) failed"
exit 1
