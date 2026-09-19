#!/bin/bash
# W400 — Matrix Conversation Launcher
# 
# All-in-one launcher for the MATRIX conversation stack:
#   - cli: Interactive CLI (real Qwen2.5-0.5B model)
#   - server: HTTP REST server (default port 9093)
#   - replay: Replay recorded conversation
#   - list: List recent sessions
#   - train: Convert NDJSON to training pairs
# 
# Usage:
#   ./matrix-conv.sh cli
#   ./matrix-conv.sh server [port]
#   ./matrix-conv.sh replay <session-id>
#   ./matrix-conv.sh list
#   ./matrix-conv.sh train <output.jsonl>

set -e

# Find project root (script is in matrix-core/src/main/scripts)
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

CP="$PROJECT_ROOT/matrix-core/build/classes/java/main:$SLF4J:$JACKSON_DATABIND:$JACKSON_CORE:$JACKSON_ANN:$ONNX:$PROTOBUF"

if [ -z "$1" ]; then
    echo "Matrix Conversation Launcher (W400)"
    echo ""
    echo "Usage: $0 {cli|server|replay|list|train}"
    echo ""
    echo "  cli                        Interactive CLI (real Qwen2.5-0.5B model)"
    echo "  server [port]              HTTP REST server (default port 9093)"
    echo "  replay <session>           Replay recorded conversation"
    echo "  list                       List recent sessions"
    echo "  train <output.jsonl>       Convert NDJSON to training pairs"
    echo "  stats                      Show conversation statistics"
    echo "  stats                      Show conversation statistics"
    echo "  help                       Show this help"
    exit 0
fi

case "$1" in
    cli)
        echo "[matrix-conv] Starting CLI..."
        java -cp "$CP" io.matrix.cli.RealConversationCli "${@:2}"
        ;;
    stats)
        echo "[matrix-conv] Computing statistics..."
        java -cp "$CP" io.matrix.cli.ConversationStats
        ;;
    server)
        PORT="${2:-9093}"
        echo "[matrix-conv] Starting HTTP server on port $PORT..."
        java -cp "$CP" io.matrix.cli.RealConversationServer "$PORT"
        ;;
    stats)
        echo "[matrix-conv] Computing statistics..."
        java -cp "$CP" io.matrix.cli.ConversationStats
        ;;
    replay)
        if [ -z "$2" ]; then
            echo "Usage: $0 replay <session-id>"
            exit 1
        fi
        echo "[matrix-conv] Replaying session $2..."
        java -cp "$CP" io.matrix.cli.RealConversationReplay "$2"
        ;;
    stats)
        echo "[matrix-conv] Computing statistics..."
        java -cp "$CP" io.matrix.cli.ConversationStats
        ;;
    list)
        java -cp "$CP" io.matrix.cli.RealConversationCli --list-sessions 50
        ;;
    stats)
        echo "[matrix-conv] Computing statistics..."
        java -cp "$CP" io.matrix.cli.ConversationStats
        ;;
    train)
        if [ -z "$2" ]; then
            echo "Usage: $0 train <output.jsonl>"
            exit 1
        fi
        echo "[matrix-conv] Converting NDJSON to training pairs..."
        java -cp "$CP" io.matrix.cli.NdjsonToTraining "$2"
        ;;
    stats)
        echo "[matrix-conv] Computing statistics..."
        java -cp "$CP" io.matrix.cli.ConversationStats
        ;;
    help)
        # Same as no-args
        exec "$0"
        ;;
    stats)
        echo "[matrix-conv] Computing statistics..."
        java -cp "$CP" io.matrix.cli.ConversationStats
        ;;
    *)
        echo "Unknown command: $1"
        echo "Run '$0 help' for usage"
        exit 1
        ;;
    stats)
        echo "[matrix-conv] Computing statistics..."
        java -cp "$CP" io.matrix.cli.ConversationStats
        ;;
esac
