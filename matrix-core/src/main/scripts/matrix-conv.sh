#!/bin/bash
# W398 — Matrix Conversation Launcher
# 
# Builds and launches the real conversation CLI/HTTP server.
# Usage:
#   ./matrix-conv.sh cli    # Interactive CLI
#   ./matrix-conv.sh server # HTTP server (port 9093)
#   ./matrix-conv.sh replay <session-id>  # Replay session
#   ./matrix-conv.sh list  # List sessions

set -e

# Find project root (the matrix-conv.sh is in matrix-core/src/main/scripts)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
cd "$PROJECT_ROOT"

# Find necessary JARs from gradle cache
SLF4J=$(find ~/.gradle/caches -name "slf4j-api-2.0.13.jar" | head -1)
JACKSON_DATABIND=$(find ~/.gradle/caches -name "jackson-databind-2.21.3.jar" | head -1)
JACKSON_CORE=$(find ~/.gradle/caches -name "jackson-core-2*.jar" -not -name "*sources*" | head -1)
JACKSON_ANN=$(find ~/.gradle/caches -name "jackson-annotations-2*.jar" -not -name "*sources*" | head -1)
ONNX=$(find ~/.gradle/caches -name "onnxruntime-1.29.0.jar" | head -1)
PROTOBUF=$(find ~/.gradle/caches -name "protobuf-java-3.25.5.jar" | head -1)

CP="/home/alexandr-narbaev/Projects/agi/matrix-core/build/classes/java/main:$SLF4J:$JACKSON_DATABIND:$JACKSON_CORE:$JACKSON_ANN:$ONNX:$PROTOBUF"

if [ -z "$1" ]; then
    echo "Usage: $0 {cli|server|replay|list}"
    echo ""
    echo "  cli               Interactive CLI (real Qwen2.5-0.5B model)"
    echo "  server [port]     HTTP REST server (default port 9093)"
    echo "  replay <session>  Replay recorded conversation"
    echo "  list              List recent sessions"
    exit 1
fi

case "$1" in
    cli)
        echo "[matrix-conv] Starting CLI..."
        java -cp "$CP" io.matrix.cli.RealConversationCli "${@:2}"
        ;;
    server)
        PORT="${2:-9093}"
        echo "[matrix-conv] Starting HTTP server on port $PORT..."
        java -cp "$CP" io.matrix.cli.RealConversationServer "$PORT"
        ;;
    replay)
        echo "[matrix-conv] Replaying session $2..."
        java -cp "$CP" io.matrix.cli.RealConversationReplay "$2"
        ;;
    list)
        java -cp "$CP" io.matrix.cli.RealConversationCli --list-sessions 50
        ;;
    *)
        echo "Unknown command: $1"
        exit 1
        ;;
esac
