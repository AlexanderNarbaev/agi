#!/bin/bash
# MATRIX Brain Launcher — runs the real brain
#
# Usage:
#   ./matrix-brain.sh brain          # Start brain HTTP server
#   ./matrix-brain.sh autonomy       # Start self-improving engine
#   ./matrix-brain.sh learn          # Learn from conversations
#   ./matrix-brain.sh eval           # Evaluate model
#   ./matrix-brain.sh stats          # Show brain stats
#   ./matrix-brain.sh <message>      # Talk to brain once

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
cd "$PROJECT_ROOT"

# Build classpath
SLF4J_API=$(find ~/.gradle/caches -name "slf4j-api-2.0.13.jar" 2>/dev/null | head -1)
SLF4J_SIMPLE=$(find ~/.gradle/caches -name "slf4j-simple-2.0.13.jar" 2>/dev/null | head -1)
JACKSON_DATABIND=$(find ~/.gradle/caches -name "jackson-databind-2.21.3.jar" 2>/dev/null | head -1)
JACKSON_CORE=$(find ~/.gradle/caches -name "jackson-core-2*.jar" -not -name "*sources*" 2>/dev/null | head -1)
JACKSON_ANN=$(find ~/.gradle/caches -name "jackson-annotations-2*.jar" -not -name "*sources*" 2>/dev/null | head -1)
ONNX_JAR=$(find ~/.gradle/caches -name "onnxruntime-1.29.0.jar" 2>/dev/null | head -1)
PROTOBUF=$(find ~/.gradle/caches -name "protobuf-java-3.25.5.jar" 2>/dev/null | head -1)
AVRO=$(find ~/.gradle/caches -name "avro-*.jar" -not -name "*sources*" 2>/dev/null | head -1)

CP="$PROJECT_ROOT/matrix-core/build/classes/java/main:$PROJECT_ROOT/matrix-core/build/resources/main"
CP="$CP:$SLF4J_API:$SLF4J_SIMPLE:$JACKSON_DATABIND:$JACKSON_CORE:$JACKSON_ANN:$ONNX_JAR:$PROTOBUF:$AVRO"

if [ -z "$1" ]; then
    echo "MATRIX Brain Launcher"
    echo ""
    echo "Usage: $0 {brain|autonomy|learn|eval|stats|<message>}"
    echo ""
    echo "  brain       Start brain HTTP server (port 9200)"
    echo "  autonomy    Start self-improving engine"
    echo "  learn       Learn from recorded conversations"
    echo "  eval        Evaluate model on test prompts"
    echo "  stats       Show brain statistics"
    echo "  <message>   Talk to brain once (echo mode)"
    exit 0
fi

case "$1" in
    brain)
        echo "[brain] Starting brain server..."
        java -cp "$CP" io.matrix.brain.BrainHttpServer 9200 models/onnx/qwen05b
        ;;
    autonomy)
        echo "[brain] Starting self-improving engine..."
        java -cp "$CP" io.matrix.autonomy.SelfImprovingEngine models/onnx/qwen05b
        ;;
    learn)
        echo "[brain] Learning from conversations..."
        java -cp "$CP" io.matrix.brain.BrainLearningLoop models/onnx/qwen05b
        ;;
    eval)
        echo "[brain] Evaluating model..."
        java -cp "$CP" io.matrix.cli.ConversationModelEval
        ;;
    stats)
        echo "[brain] Statistics:"
        java -cp "$CP" io.matrix.brain.BrainHttpServer 9200 models/onnx/qwen05b &
        sleep 30
        curl -s http://localhost:9200/stats
        echo ""
        kill %1
        ;;
    *)
        echo "[brain] Talking to brain..."
        echo "$*" | java -cp "$CP" io.matrix.brain.LlmBrainLoopRag models/onnx/qwen05b
        ;;
esac
