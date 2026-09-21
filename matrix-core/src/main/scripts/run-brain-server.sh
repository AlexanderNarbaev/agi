#!/bin/bash
# Runs the brain server using gradle's classpath (no classpath issues)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
cd "$PROJECT_ROOT"

export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/25.0.2-graalce}"
export PATH="$JAVA_HOME/bin:$PATH"

PORT="${1:-9200}"

echo "Starting MATRIX Brain Server on port $PORT..."
./gradlew :matrix-core:runBrainServer -PbrainServerPort="$PORT" --quiet
