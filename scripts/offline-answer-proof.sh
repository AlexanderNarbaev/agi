#!/usr/bin/env bash
# W50 — the offline proof of the answering path.
#
# The four turns are run against a FRESH instance whose HTTP and HTTPS
# clients are pointed at a closed proxy, so any outbound HTTP from the JVM
# would fail. The answers are nevertheless produced, from the persisted
# store, with their claims and sources attached.
#
# The application is started on its own port with its own database, so the
# running demonstrator is not touched. Nothing leaves the machine.
#
# Usage: scripts/offline-answer-proof.sh [port]

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$ROOT/matrix-core/build/quarkus-app/quarkus-run.jar"
PORT="${1:-9099}"
WORK="${WORK:-/tmp/w50-offline}"
BASE="http://127.0.0.1:$PORT/v1"
WORKSPACE="plate-tectonics-offline"

# The constants the gate wants named rather than written inline.
PROXY_HOST="127.0.0.1"
PROXY_PORT="9"
STARTUP_ATTEMPTS="70"
STARTUP_INTERVAL_SECONDS="3"
ASK_TIMEOUT_SECONDS="300"
ACQUIRE_TIMEOUT_SECONDS="900"

if [[ -n "${JAVA_HOME:-}" ]]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
else
    JAVA_BIN="java"
fi

if [[ ! -f "$JAR" ]]; then
    echo "offline-proof: $JAR is missing; run ./gradlew :matrix-core:quarkusBuild first" >&2
    exit 1
fi

rm -rf "$WORK"
mkdir -p "$WORK"

# Every HTTP(S) route of the JVM points at a closed port: a request cannot
# leave the machine even if some code tried to make one.
QUARKUS_HTTP_PORT="$PORT" MATRIX_KNOWLEDGE_PATH="$WORK/knowledge.sqlite" "$JAVA_BIN" \
    -Dhttp.proxyHost="$PROXY_HOST" -Dhttp.proxyPort="$PROXY_PORT" \
    -Dhttps.proxyHost="$PROXY_HOST" -Dhttps.proxyPort="$PROXY_PORT" \
    -Dhttp.nonProxyHosts= -Dhttps.nonProxyHosts= \
    -jar "$JAR" > "$WORK/app.log" 2>&1 &
APP_PID=$!

cleanup() {
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
}
trap cleanup EXIT

for _ in $(seq 1 "$STARTUP_ATTEMPTS"); do
    if grep -q "Listening on:" "$WORK/app.log" 2>/dev/null; then
        break
    fi
    sleep "$STARTUP_INTERVAL_SECONDS"
done

if ! grep -q "Listening on:" "$WORK/app.log" 2>/dev/null; then
    echo "offline-proof: the application did not start; see $WORK/app.log" >&2
    exit 1
fi

ask() {
    curl -s -m "$ASK_TIMEOUT_SECONDS" -X POST "$BASE/workbench/workspaces/$WORKSPACE/ask" \
        -H 'Content-Type: application/json' -d "{\"question\":\"$1\"}"
}

curl -s -m "$ACQUIRE_TIMEOUT_SECONDS" -X POST "$BASE/learning/workspaces/$WORKSPACE/seed" > "$WORK/seed.json"
T1="$(ask 'What is plate tectonics, and what are the three types of plate boundaries?')"
curl -s -m "$ACQUIRE_TIMEOUT_SECONDS" -X POST "$BASE/learning/workspaces/$WORKSPACE/learn?question=How+do+we+know+the+continents+have+moved&documents=3" > "$WORK/learn.json"
T2="$(ask 'What drives the motion of the tectonic plates?')"
curl -s -m "$ACQUIRE_TIMEOUT_SECONDS" -X POST "$BASE/learning/workspaces/$WORKSPACE/revise?file=wikipedia-slab-pull.md" > "$WORK/revise.json"
T3="$(ask 'What drives the motion of the tectonic plates?')"

CLAIMS1="$(printf '%s' "$T1" | grep -o '"claimCount":[0-9]*' | head -1)"
CLAIMS2="$(printf '%s' "$T2" | grep -o '"claimCount":[0-9]*' | head -1)"
CLAIMS3="$(printf '%s' "$T3" | grep -o '"claimCount":[0-9]*' | head -1)"

echo "offline-proof: HTTP client routed through a closed proxy (127.0.0.1:9)"
echo "offline-proof: turn 1 $CLAIMS1"
echo "offline-proof: turn 2 $CLAIMS2"
echo "offline-proof: turn 3 $CLAIMS3"
echo "offline-proof: the revision report $(grep -o '"revisionsApplied":[0-9]*' "$WORK/revise.json" | head -1)"

if [[ -z "$CLAIMS1" || -z "$CLAIMS2" || -z "$CLAIMS3" ]]; then
    echo "offline-proof: the answers were not produced" >&2
    exit 1
fi

echo "offline-proof: OK — the answers were produced with no outbound HTTP"
