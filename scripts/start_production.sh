#!/bin/bash
# MATRIX production startup script
# Usage: ./scripts/start_production.sh [jvm|native]
# Default: jvm (native build is documented but blocked — see RUNBOOK §Native Build Status)

set -euo pipefail

cd "$(dirname "$0")/.."
ROOT_DIR="$(pwd)"

MODE="${1:-jvm}"
HTTP_PORT="${MATRIX_HTTP_PORT:-9091}"
JAVA_OPTS="${MATRIX_JAVA_OPTS:--Xmx4g -Xms2g --enable-native-access=ALL-UNNAMED}"

case "$MODE" in
    jvm)
        echo "[start_production] Starting MATRIX in JVM mode (port $HTTP_PORT)"
        echo "[start_production] Java opts: $JAVA_OPTS"
        # Build if needed
        if [ ! -d "matrix-core/build/classes/java/main" ]; then
            echo "[start_production] Building matrix-core..."
            ./gradlew :matrix-core:quarkusBuild -Dquarkus.package.jar.type=fast-jar --no-daemon
        fi
        # Run Quarkus
        exec java $JAVA_OPTS \
            -Dquarkus.http.port=$HTTP_PORT \
            -jar matrix-core/build/quarkus-app/quarkus-run.jar
        ;;
    native)
        echo "[start_production] NATIVE build is BLOCKED (see RUNBOOK §Native Build Status)"
        echo "[start_production] Falling back to JVM mode"
        exec "$0" jvm
        ;;
    *)
        echo "Usage: $0 [jvm|native]" >&2
        exit 1
        ;;
esac
