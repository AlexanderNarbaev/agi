#!/bin/bash
# W431 — Matrix Conversation Launcher
# 
# All-in-one launcher for the MATRIX conversation stack.
# 23 commands total: cli, server, replay, head, tail, list, train, stats, search, 
# delete, export, merge, name, names, validate, find, summary, extract, 
# count, compact, diff, report, test

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
cd "$PROJECT_ROOT"

SLF4J=$(find ~/.gradle/caches -name "slf4j-api-2.0.13.jar" | head -1)
JACKSON_DATABIND=$(find ~/.gradle/caches -name "jackson-databind-2.21.3.jar" | head -1)
JACKSON_CORE=$(find ~/.gradle/caches -name "jackson-core-2*.jar" -not -name "*sources*" | head -1)
JACKSON_ANN=$(find ~/.gradle/caches -name "jackson-annotations-2*.jar" -not -name "*sources*" | head -1)
ONNX=$(find ~/.gradle/caches -name "onnxruntime-1.29.0.jar" | head -1)
PROTOBUF=$(find ~/.gradle/caches -name "protobuf-java-3.25.5.jar" | head -1)

CP="$PROJECT_ROOT/matrix-core/build/classes/java/main:$PROJECT_ROOT/matrix-core/build/resources/main:$SLF4J:$JACKSON_DATABIND:$JACKSON_CORE:$JACKSON_ANN:$ONNX:$PROTOBUF"

if [ -z "$1" ]; then
    echo "Matrix Conversation Launcher (W431)"
    echo ""
    echo "Usage: $0 {cli|server|replay|head|tail|list|train|stats|search|delete|export|merge|name|names|validate|find|summary|extract|count|compact|diff|report|test}"
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
    echo "  name <session> <name>      Tag a session"
    echo "  names                      List sessions with names"
    echo "  validate <id|all>          Check NDJSON validity"
    echo "  find <query> [top-n]       Find similar sessions"
    echo "  summary <session>          Show session summary"
    echo "  extract <s> <role> [out]   Extract user/assistant turns"
    echo "  count <session>            Count lines/turns/bytes"
    echo "  compact <session> [n]      One-line-per-turn view"
    echo "  diff <s1> <s2>             Compare two sessions"
    echo "  report                     Generate aggregate report"
    echo "  test                       Run model sanity test"
    echo "  help                       Show this help"
    exit 0
fi

case "$1" in
    cli) java -cp "$CP" io.matrix.cli.RealConversationCli "${@:2}" ;;
    server) PORT="${2:-9093}"; java -cp "$CP" io.matrix.cli.RealConversationServer "$PORT" ;;
    replay) [ -z "$2" ] && { echo "Usage: $0 replay <session-id>"; exit 1; }; java -cp "$CP" io.matrix.cli.RealConversationReplay "$2" ;;
    head) [ -z "$2" ] && { echo "Usage: $0 head <session-id> [n]"; exit 1; }; java -cp "$CP" io.matrix.cli.ConversationHead "${@:2}" ;;
    tail) [ -z "$2" ] && { echo "Usage: $0 tail <session-id> [n]"; exit 1; }; java -cp "$CP" io.matrix.cli.ConversationTail "${@:2}" ;;
    list) java -cp "$CP" io.matrix.cli.RealConversationCli --list-sessions 50 ;;
    train) [ -z "$2" ] && { echo "Usage: $0 train <output.jsonl>"; exit 1; }; java -cp "$CP" io.matrix.cli.NdjsonToTraining "$2" ;;
    stats) java -cp "$CP" io.matrix.cli.ConversationStats ;;
    search) [ -z "$2" ] && { echo "Usage: $0 search <query>"; exit 1; }; java -cp "$CP" io.matrix.cli.ConversationSearch "$2" ;;
    delete) [ -z "$2" ] && { echo "Usage: $0 delete <session-id> [--force]"; exit 1; }; shift; java -cp "$CP" io.matrix.cli.ConversationDelete "$@" ;;
    export) [ -z "$2" ] && { echo "Usage: $0 export <session-id|all> <format>"; exit 1; }; java -cp "$CP" io.matrix.cli.ConversationExport "${@:2}" ;;
    merge) [ -z "$2" ] && { echo "Usage: $0 merge <output> <input1> <input2>"; exit 1; }; java -cp "$CP" io.matrix.cli.ConversationMerge "${@:2}" ;;
    name) [ -z "$2" ] && { echo "Usage: $0 name <session> <new-name>|--read|--remove"; exit 1; }; java -cp "$CP" io.matrix.cli.ConversationName "${@:2}" ;;
    names) java -cp "$CP" io.matrix.cli.ConversationListNamed ;;
    validate) [ -z "$2" ] && { echo "Usage: $0 validate <session|all>"; exit 1; }; java -cp "$CP" io.matrix.cli.ConversationValidate "${@:2}" ;;
    find) [ -z "$2" ] && { echo "Usage: $0 find <query> [top-n=5]"; exit 1; }; java -cp "$CP" io.matrix.cli.ConversationFind "${@:2}" ;;
    summary) [ -z "$2" ] && { echo "Usage: $0 summary <session>"; exit 1; }; java -cp "$CP" io.matrix.cli.ConversationSummary "${@:2}" ;;
    extract) [ -z "$2" ] || [ -z "$3" ] && { echo "Usage: $0 extract <session> <--user|--assistant|--turns> [output]"; exit 1; }; java -cp "$CP" io.matrix.cli.ConversationExtract "${@:2}" ;;
    count) [ -z "$2" ] && { echo "Usage: $0 count <session>"; exit 1; }; java -cp "$CP" io.matrix.cli.ConversationCount "${@:2}" ;;
    compact) [ -z "$2" ] && { echo "Usage: $0 compact <session> [max-chars]"; exit 1; }; java -cp "$CP" io.matrix.cli.ConversationCompact "${@:2}" ;;
    diff) [ -z "$2" ] || [ -z "$3" ] && { echo "Usage: $0 diff <session1> <session2>"; exit 1; }; java -cp "$CP" io.matrix.cli.ConversationDiff "${@:2}" ;;
    report) java -cp "$CP" io.matrix.cli.ConversationReport "${@:2}" ;;
    test) java -cp "$CP" io.matrix.cli.ConversationModelTest ;;
    help) exec "$0" ;;
    *) echo "Unknown command: $1"; echo "Run '$0 help' for usage"; exit 1 ;;
esac
