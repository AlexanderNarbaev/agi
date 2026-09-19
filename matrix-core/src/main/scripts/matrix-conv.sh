#!/bin/bash
# W408 — Matrix Conversation Launcher
# 
# All-in-one launcher for the MATRIX conversation stack.
# 
# Usage:
#   ./matrix-conv.sh cli
#   ./matrix-conv.sh server [port]
#   ./matrix-conv.sh replay <session-id>
#   ./matrix-conv.sh list
#   ./matrix-conv.sh train <output.jsonl>
#   ./matrix-conv.sh stats
#   ./matrix-conv.sh search <query>
#   ./matrix-conv.sh delete <session-id> [--force]
#   ./matrix-conv.sh export <session-id|all> <format> [output-file]
#   ./matrix-conv.sh help

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
    echo "Matrix Conversation Launcher (W408)"
    echo ""
    echo "Usage: $0 {cli|server|replay|list|train|stats|search|delete|export}"
    echo ""
    echo "  cli                        Interactive CLI (real Qwen2.5-0.5B model)"
    echo "  server [port]              HTTP REST server (default port 9093)"
    echo "  replay <session>           Replay recorded conversation"
    echo "  list                       List recent sessions"
    echo "  train <output.jsonl>       Convert NDJSON to training pairs"
    echo "  stats                      Show conversation statistics"
    echo "  search <query>             Search conversation content"
    echo "  delete <session> [--force] Delete a session"
    echo "  export <id|all> <format>   Export to json/csv/txt"
    echo "  help                       Show this help"
    exit 0
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
        if [ -z "$2" ]; then
            echo "Usage: $0 replay <session-id>"
            exit 1
        fi
        echo "[matrix-conv] Replaying session $2..."
        java -cp "$CP" io.matrix.cli.RealConversationReplay "$2"
        ;;
    list)
        java -cp "$CP" io.matrix.cli.RealConversationCli --list-sessions 50
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
    search)
        if [ -z "$2" ]; then
            echo "Usage: $0 search <query>"
            exit 1
        fi
        echo "[matrix-conv] Searching for '$2'..."
        java -cp "$CP" io.matrix.cli.ConversationSearch "$2"
        ;;
    delete)
        if [ -z "$2" ]; then
            echo "Usage: $0 delete <session-id> [--force]"
            exit 1
        fi
        echo "[matrix-conv] Deleting session $2..."
        shift
        java -cp "$CP" io.matrix.cli.ConversationDelete "$@"
        ;;
    export)
        if [ -z "$2" ]; then
            echo "Usage: $0 export <session-id|all> <format> [output-file]"
            echo "Formats: json, csv, txt"
            exit 1
        fi
        echo "[matrix-conv] Exporting $1 to $2..."
        java -cp "$CP" io.matrix.cli.ConversationExport "${@:2}"
        ;;
    help)
        exec "$0"
        ;;
    *)
        echo "Unknown command: $1"
        echo "Run '$0 help' for usage"
        exit 1
        ;;
esac
