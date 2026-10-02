#!/usr/bin/env bash
# TRUE-W10 — Start the MATRIX mind.
# Builds, starts gateway (MATRIX_MODE=production), and prints URLs.
set -e

echo "============================================="
echo "  MATRIX MIND — start-mind.sh"
echo "============================================="
echo ""

# Pre-flight checks
if ! command -v java >/dev/null 2>&1; then
    echo "ERROR: java not found in PATH"
    exit 1
fi

# Discover JAVA_HOME if unset
if [ -z "$JAVA_HOME" ]; then
    if [ -d "$HOME/.sdkman/candidates/java/25.0.2-graalce" ]; then
        export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-graalce"
    elif [ -d "$HOME/.sdkman/candidates/java/current" ]; then
        export JAVA_HOME="$HOME/.sdkman/candidates/java/current"
    fi
fi
export PATH="$JAVA_HOME/bin:$PATH"
echo "[1/5] JAVA_HOME=$JAVA_HOME"
echo "       java: $(java -version 2>&1 | head -1)"

# Tuning parameters. RECON-W30 moved every JVM/threading/batch constant out of this
# script and into a config file, so that a deployment profile is an env file rather
# than a patch. MATRIX_ENV_FILE lets a profile be selected without editing anything;
# the \${VAR:-default} defaults inside matrix.env keep the file self-documenting and
# mean an operator can still override a single value from the environment.
cd "$(dirname "$0")/.."
MATRIX_ENV_FILE="${MATRIX_ENV_FILE:-scripts/matrix.env}"
if [ ! -f "$MATRIX_ENV_FILE" ]; then
    echo "ERROR: tuning env file not found: $MATRIX_ENV_FILE" >&2
    echo "       set MATRIX_ENV_FILE to a profile, or restore scripts/matrix.env" >&2
    exit 1
fi
# shellcheck disable=SC1090
. "$MATRIX_ENV_FILE"
echo "       tuning: $MATRIX_ENV_FILE (heap=$MATRIX_HEAP gc=$MATRIX_GC threads=$MATRIX_WORKER_THREADS)"

# Disk check
echo "[2/5] Disk check..."
FREE_GB=$(df -BG . | tail -1 | awk '{print $4}' | sed 's/G//')
echo "  free=$FREE_GB GB"
if [ "$FREE_GB" -lt 10 ]; then
    echo "ERROR: less than 10 GB free — refusing to start (CONSTITUTION I)"
    exit 1
fi

# Build only the modules we need
echo "[3/5] Building MATRIX (api-gateway + brain-runtime)..."
# Already cd'd to the repo root above, where MATRIX_ENV_FILE was resolved from.

# RECON-W20 pre-flight: disk hygiene + DiskBudget tier gate. Idempotent.
# Exits non-zero (REFUSE) below 10 GB so heavy operations are blocked honestly.
echo "[pre-flight] disk hygiene..."
bash scripts/disk-hygiene.sh >/dev/null 2>&1 || {
    echo "REFUSED: disk below REFUSE tier (<10GB free). Clean up before starting."
    exit 3
}

./gradlew :matrix-brain-runtime:compileJava :matrix-api-gateway:compileJava --no-daemon --console=plain 2>&1 | tail -5 || {
    echo "WARNING: gradle build returned non-zero; trying to continue anyway"
}

# Build classpath including all runtime dependencies
echo "[4/5] Assembling classpath..."
CP="matrix-brain-runtime/build/classes/java/main"
CP="$CP:matrix-core/build/classes/java/main"
CP="$CP:matrix-api-gateway/build/classes/java/main"
CP="$CP:matrix-audit/build/classes/java/main"
CP="$CP:matrix-billing/build/classes/java/main"
CP="$CP:matrix-observability/build/classes/java/main"
CP="$CP:matrix-quality/build/classes/java/main"
CP="$CP:matrix-tools-distill/build/classes/java/main"

# RECON-W0: use Gradle-generated runtime classpath (reproducible, version-stable).
# The file is produced by './gradlew :matrix-api-gateway:writeRuntimeClasspath'.
CP_FILE="matrix-api-gateway/build/runtime-classpath.txt"
if [ ! -f "$CP_FILE" ]; then
    echo "ERROR: $CP_FILE not found."
    echo "       Run: ./gradlew :matrix-api-gateway:writeRuntimeClasspath"
    exit 1
fi
# RECON-W28 B-1 ROOT CAUSE (verified by reproduction, not inference):
# writeRuntimeClasspath emits a LINE-ORIENTED file - line 1 and 2 are the two
# project classes dirs, line 3 is the colon-separated dependency list. The old
# `CP="$(cat $CP_FILE)"` pasted that straight into -cp, so the embedded newlines
# fused entries together: java received ONE bogus entry of the form
#   matrix-api-gateway/build/classes/java/main\nmatrix-brain-runtime/...\n<first-jar>
# and reported ClassNotFoundException: io.matrix.api.MinimalHttpServer while the
# build printed BUILD SUCCESSFUL and the .class file sat right there. Reproduced
# deterministically: java -cp "$(cat runtime-classpath.txt)" in a clean clone
# fails; the same command with '\n' translated to ':' starts. The project classes
# are also prepended explicitly so freshly compiled code always wins over a jar.
CP="$CP:$(tr '\n' ':' < "$CP_FILE")"
echo "  classpath entries: $(echo $CP | tr ':' '\n' | wc -l) (project classes + $CP_FILE, newlines normalised to ':')"

# Start gateway in production mode
echo "[5/5] Starting gateway (MATRIX_MODE=production)..."
export MATRIX_MODE=production
export MATRIX_MIND_DIR="${MATRIX_MIND_DIR:-$PWD/data/mind}"
# RECON-W25: port and pid file are configurable so two nodes can run side by side
# for a federation transcript. Both default to the single-node behaviour.
export MATRIX_PORT="${MATRIX_PORT:-8765}"
PID_FILE="${MATRIX_PID_FILE:-$PWD/.gateway.pid}"
mkdir -p "$MATRIX_MIND_DIR"

# Kill any previous gateway
if [ -f "$PID_FILE" ]; then
    OLD_PID=$(cat "$PID_FILE" 2>/dev/null || true)
    if kill -0 "$OLD_PID" 2>/dev/null; then
        echo "  killing previous gateway pid=$OLD_PID (from $PID_FILE)"
        kill "$OLD_PID" 2>/dev/null || true
        sleep 1
    fi
    rm -f "$PID_FILE"
fi

java -Xms"$MATRIX_HEAP" -Xmx"$MATRIX_HEAP" "$MATRIX_GC" "$MATRIX_JVM_MODULES" \
      -Dport="${MATRIX_PORT:-8765}" \
      -Dmatrix.workerThreads="${MATRIX_WORKER_THREADS:-16}" \
      -Dmatrix.ioThreads="${MATRIX_IO_THREADS:-16}" \
      -Dmatrix.distillChunk="${MATRIX_DISTILL_CHUNK:-8192}" \
      -cp "$CP" io.matrix.api.MinimalHttpServer > "$MATRIX_MIND_DIR/gateway.log" 2>&1 &
GATEWAY_PID=$!
echo "  gateway pid=$GATEWAY_PID"
echo "$GATEWAY_PID" > "$PID_FILE"
disown $GATEWAY_PID 2>/dev/null || true

# Wait for health check.
#
# RECON-W30 fixed two defects here that had been hiding behind the default port:
#
#   1. The probe polled a hardcoded 8765 while the server honoured MATRIX_PORT. On any
#      non-default port — which is exactly what the smoke script (8799) and the
#      federation script (8774/8775) use — the health check polled a port nothing was
#      listening on.
#   2. The loop had no failure path. When the probe never succeeded it fell out of the
#      loop and printed "MATRIX is awake" anyway. A start script that reports success
#      for a gateway that never came up is worse than one that fails, because the
#      caller stops looking.
#
# So: probe the port actually in use, and exit non-zero if it never answers.
HEALTH_URL="http://localhost:${MATRIX_PORT}/health/live"
HEALTH_OK=0
readonly HEALTH_ATTEMPTS=5
readonly HEALTH_INITIAL_WAIT_SECONDS=5
readonly HEALTH_RETRY_WAIT_SECONDS=2

sleep "$HEALTH_INITIAL_WAIT_SECONDS"
for i in $(seq 1 "$HEALTH_ATTEMPTS"); do
    if curl -s -f "$HEALTH_URL" > /dev/null 2>&1; then
        HEALTH_OK=1
        break
    fi
    if ! kill -0 "$GATEWAY_PID" 2>/dev/null; then
        echo "ERROR: gateway process $GATEWAY_PID died during startup" >&2
        echo "       last log lines:" >&2
        tail -20 "$MATRIX_MIND_DIR/gateway.log" >&2 || true
        exit 1
    fi
    echo "  waiting for gateway on :${MATRIX_PORT}... ($i/$HEALTH_ATTEMPTS)"
    sleep "$HEALTH_RETRY_WAIT_SECONDS"
done

if [ "$HEALTH_OK" -ne 1 ]; then
    echo "ERROR: gateway did not become healthy at $HEALTH_URL after $HEALTH_ATTEMPTS attempts" >&2
    echo "       last log lines:" >&2
    tail -20 "$MATRIX_MIND_DIR/gateway.log" >&2 || true
    echo "       process $GATEWAY_PID left running for inspection; kill it or re-run to retry" >&2
    exit 1
fi
echo "  health: OK ($HEALTH_URL)"

echo ""
echo "============================================="
echo "  MATRIX is awake."
echo "  gateway:     http://localhost:${MATRIX_PORT}"
echo "  health:      http://localhost:${MATRIX_PORT}/health/live"
echo "  analyze:     POST http://localhost:${MATRIX_PORT}/v1/analyze"
echo "  teach:       POST http://localhost:${MATRIX_PORT}/v1/teach"
echo "  sleep:       POST http://localhost:${MATRIX_PORT}/v1/sleep"
echo "  goals:       GET  http://localhost:${MATRIX_PORT}/v1/goals"
echo "  inbox:       POST http://localhost:${MATRIX_PORT}/v1/inbox/scan"
echo "  status:      GET  http://localhost:${MATRIX_PORT}/v1/status"
echo "============================================="
echo ""
echo "Try a query:"
echo "  TOKEN=\$(curl -s -X POST http://localhost:${MATRIX_PORT}/v1/auth/login \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"email\":\"pro@test.com\"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)[\"token\"])')"
echo "  curl -X POST http://localhost:${MATRIX_PORT}/v1/analyze \\"
echo "    -H \"Authorization: Bearer \$TOKEN\" \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"input\":\"What is 2+3?\"}'"
echo ""
echo "Logs: tail -f $MATRIX_MIND_DIR/gateway.log"
echo "Stop with: $0 stop"
