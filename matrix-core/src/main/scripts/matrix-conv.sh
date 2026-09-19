#!/bin/bash
# W413 — Matrix Conversation Launcher
# 
# All-in-one launcher for the MATRIX conversation stack.
# 
# Usage:
#   ./matrix-conv.sh cli
#   ./matrix-conv.sh server [port]
#   ./matrix-conv.sh replay <session-id>     # full transcript
#   ./matrix-conv.sh head <session-id> [n]    # first N turns
#   ./matrix-conv.sh tail <session-id> [n]    # last N turns
#   ./matrix-conv.sh list
#   ./matrix-conv.sh train <output.jsonl>
#   ./matrix-conv.sh stats
#   ./matrix-conv.sh search <query>
#   ./matrix-conv.sh delete <session-id> [--force]
#   ./matrix-conv.sh export <session-id|all> <format> [output-file]
#   ./matrix-conv.sh merge <out> <in1> <in2>...
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
    echo "Matrix Conversation Launcher (W413)"
    echo ""
    echo "Usage: $0 {cli|server|replay|head|tail|list|train|stats|search|delete|export|merge}"
    echo ""
    echo "  cli                        Interactive CLI (real Qwen2.5-0.5B model)"
    echo "  server [port]              HTTP REST server (default port 9093)"
    echo "  replay <session>           Full conversation transcript"
    echo "  head <session> [n=5]       First N turns"
    echo "  tail <session> [n=5]       Last N turns"
    echo "  list                       List recent sessions"
    echo "  train <output.jsonl>       Convert NDJSON to training pairs"
    echo "  stats                      Show conversation statistics"
    echo "  search <query>             Search conversation content"
    echo "  delete <session> [--force] Delete a session"
    echo "  export <id|all> <format>   Export to json/csv/txt"
    echo "  merge <out> <in1> <in2>...  Merge sessions"
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
        if [ -z "$2" ]; then echo "Usage: $0 replay <session-id>"; exit 1; fi
        java -cp "$CP" io.matrix.cli.RealConversationReplay "$2"
        ;;
    head)
        if [ -z "$2" ]; then echo "Usage: $0 head <session-id> [n]"; exit 1; fi
        java -cp "$CP" io.matrix.cli.ConversationHead "${@:2}"
        ;;
    tail)
        if [ -z "$2" ]; then echo "Usage: $0 tail <session-id> [n]"; exit 1; fi
        java -cp "$CP" io.matrix.cli.ConversationTail "${@:2}"
        ;;
    list)
        java -cp "$CP" io.matrix.cli.RealConversationCli --list-sessions 50
        ;;
    train)
        if [ -z "$2" ]; then echo "Usage: $0 train <output.jsonl>"; exit 1; fi
        java -cp "$CP" io.matrix.cli.NdjsonToTraining "$2"
        ;;
    stats)
        java -cp "$CP" io.matrix.cli.ConversationStats
        ;;
    search)
        if [ -z "$2" ]; then echo "Usage: $0 search <query>"; exit 1; fi
        java -cp "$CP" io.matrix.cli.ConversationSearch "$2"
        ;;
    delete)
        if [ -z "$2" ]; then echo "Usage: $0 delete <session-id> [--force]"; exit 1; fi
        shift
        java -cp "$CP" io.matrix.cli.ConversationDelete "$@"
        ;;
    export)
        if [ -z "$2" ]; then echo "Usage: $0 export <session-id|all> <format>"; exit 1; fi
        java -cp "$CP" io.matrix.cli.ConversationExport "${@:2}"
        ;;
    merge)
        if [ -z "$2" ]; then echo "Usage: $0 merge <output> <input1> <input2>"; exit 1; fi
        java -cp "$CP" io.matrix.cli.ConversationMerge "${@:2}"
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
