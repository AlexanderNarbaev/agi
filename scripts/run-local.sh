#!/usr/bin/env bash
# W50 — the canonical local start command of the MATRIX demonstrator.
#
# One command, no Docker, no native image, no model weights and no network
# access. The script builds the runnable application when the jar is missing
# and then starts it; after the first build it starts the application
# straight away.
#
# The application seeds its own store on the first start, which takes about
# two and a half minutes on this machine. The Workbench is at
# http://127.0.0.1:9091/workbench/.
#
# The script writes nothing outside the repository and stops with Ctrl-C.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$ROOT/matrix-core/build/quarkus-app/quarkus-run.jar"
PORT="${QUARKUS_HTTP_PORT:-9091}"

if [[ -n "${JAVA_HOME:-}" ]]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
else
    JAVA_BIN="java"
fi

if [[ ! -x "$JAVA_BIN" ]]; then
    echo "run-local: no java at $JAVA_BIN; set JAVA_HOME to a JDK 25" >&2
    exit 1
fi

if [[ "${1:-}" == "--build" || ! -f "$JAR" ]]; then
    echo "run-local: building the application" >&2
    (cd "$ROOT" && ./gradlew :matrix-core:quarkusBuild -x test)
fi

echo "run-local: starting MATRIX on http://127.0.0.1:$PORT" >&2
echo "run-local: the first start seeds the store and takes about two minutes" >&2
exec "$JAVA_BIN" -jar "$JAR"
