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
        # OD-028. ONNX Runtime loads its CUDA provider with a native dlopen, and
        # the OS dynamic linker resolves that through LD_LIBRARY_PATH -- never
        # through the JVM's -Djava.library.path, which only governs System.load.
        # Without this, the loader finds the distro's libcudart, which does not
        # export the symbol the provider needs: addCUDA() throws inside
        # OnnxRuntimeAdapter, the exception is swallowed, and MATRIX silently
        # runs on the CPU. The libraries live in .onnx_libs, which is gitignored
        # and created per machine by scripts/diagnose-cuda.sh --repair, so on a
        # fresh clone the directory is absent and this block changes nothing.
        # A path the caller set is kept and this one is prepended to it.
        if [ -d "$ROOT_DIR/.onnx_libs" ]; then
            export LD_LIBRARY_PATH="$ROOT_DIR/.onnx_libs${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
            echo "[start_production] CUDA libraries: $LD_LIBRARY_PATH"
        else
            echo "[start_production] No .onnx_libs directory; the CUDA provider will not load"
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
