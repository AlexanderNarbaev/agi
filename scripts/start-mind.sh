#!/usr/bin/env bash
# TRUE-W10 — Start the MATRIX mind.
# Builds, starts gateway (MATRIX_MODE=production), web-ui, and prints URLs.
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
if [ -z "$JAVA_HOME" ] && [ -d "$HOME/.sdkman/candidates/java/25.0.2-graalce" ]; then
    export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-graalce"
fi
echo "[1/4] JAVA_HOME=$JAVA_HOME"

# Build everything
echo "[2/4] Building MATRIX..."
./gradlew :matrix-brain-runtime:compileJava :matrix-api-gateway:compileJava :matrix-tools-distill:nativeCompile --no-daemon --console=plain 2>&1 | tail -10

# Disk check
echo "[3/4] Disk check..."
FREE_GB=$(df -BG . | tail -1 | awk '{print $4}' | sed 's/G//')
echo "  free=$FREE_GB GB"
if [ "$FREE_GB" -lt 10 ]; then
    echo "ERROR: less than 10 GB free — refusing to start (CONSTITUTION I)"
    exit 1
fi

# Start gateway in production mode
echo "[4/4] Starting gateway on :8765 (MATRIX_MODE=production)..."
export MATRIX_MODE=production
export MATRIX_MIND_DIR="${MATRIX_MIND_DIR:-$PWD/data/mind}"
mkdir -p "$MATRIX_MIND_DIR"

# Build classpath
CP="matrix-brain-runtime/build/classes/java/main"
CP="$CP:matrix-core/build/classes/java/main"
CP="$CP:matrix-api-gateway/build/classes/java/main"
CP="$CP:matrix-audit/build/classes/java/main"
CP="$CP:matrix-billing/build/classes/java/main"
CP="$CP:matrix-observability/build/classes/java/main"
CP="$CP:matrix-quality/build/classes/java/main"
CP="$CP:$(find ~/.gradle/caches/modules-2/files-2.1 -name 'jackson-*.jar' 2>/dev/null | head -5 | paste -sd:)"

java -cp "$CP" io.matrix.api.MinimalHttpServer 8765 &
GATEWAY_PID=$!
echo "  gateway pid=$GATEWAY_PID"
echo "$GATEWAY_PID" > .gateway.pid

sleep 3

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
echo "  curl -X POST http://localhost:8765/v1/auth/login -H 'Content-Type: application/json' -d '{\"email\":\"pro@test.com\"}'"
echo "  TOKEN=\$(... | python3 -c 'import sys,json; print(json.load(sys.stdin)[\"token\"])')"
echo "  curl -X POST http://localhost:8765/v1/analyze -H \"Authorization: Bearer \$TOKEN\" -H 'Content-Type: application/json' -d '{\"input\":\"What is 2+3?\"}'"
echo ""
echo "Stop with: $0 stop"
