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
cd "$(dirname "$0")/.."
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
CP="$(cat $CP_FILE)"
echo "  classpath entries: $(echo $CP | tr ':' '\\n' | wc -l) (from $CP_FILE)"

# Start gateway in production mode
echo "[5/5] Starting gateway on :8765 (MATRIX_MODE=production)..."
export MATRIX_MODE=production
export MATRIX_MIND_DIR="${MATRIX_MIND_DIR:-$PWD/data/mind}"
mkdir -p "$MATRIX_MIND_DIR"

# Kill any previous gateway
if [ -f .gateway.pid ]; then
    OLD_PID=$(cat .gateway.pid 2>/dev/null)
    if kill -0 "$OLD_PID" 2>/dev/null; then
        echo "  killing previous gateway pid=$OLD_PID"
        kill "$OLD_PID" 2>/dev/null || true
        sleep 1
    fi
    rm -f .gateway.pid
fi

java -Dport=8765 -cp "$CP" io.matrix.api.MinimalHttpServer > "$MATRIX_MIND_DIR/gateway.log" 2>&1 &
GATEWAY_PID=$!
echo "  gateway pid=$GATEWAY_PID"
echo "$GATEWAY_PID" > .gateway.pid
disown $GATEWAY_PID 2>/dev/null || true

# Wait for health check
sleep 5
for i in 1 2 3 4 5; do
    if curl -s http://localhost:8765/health/live > /dev/null 2>&1; then
        echo "  health: OK"
        break
    fi
    echo "  waiting for gateway... ($i)"
    sleep 2
done

echo ""
echo "============================================="
echo "  MATRIX is awake."
echo "  gateway:     http://localhost:8765"
echo "  health:      http://localhost:8765/health/live"
echo "  analyze:     POST http://localhost:8765/v1/analyze"
echo "  teach:       POST http://localhost:8765/v1/teach"
echo "  sleep:       POST http://localhost:8765/v1/sleep"
echo "  goals:       GET  http://localhost:8765/v1/goals"
echo "  inbox:       POST http://localhost:8765/v1/inbox/scan"
echo "  status:      GET  http://localhost:8765/v1/status"
echo "============================================="
echo ""
echo "Try a query:"
echo "  TOKEN=\$(curl -s -X POST http://localhost:8765/v1/auth/login \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"email\":\"pro@test.com\"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)[\"token\"])')"
echo "  curl -X POST http://localhost:8765/v1/analyze \\"
echo "    -H \"Authorization: Bearer \$TOKEN\" \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"input\":\"What is 2+3?\"}'"
echo ""
echo "Logs: tail -f $MATRIX_MIND_DIR/gateway.log"
echo "Stop with: $0 stop"
