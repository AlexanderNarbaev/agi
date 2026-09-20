#!/bin/bash
# MATRIX Brain Launcher
# Starts the real brain with all features: RAG, confidence filter, learning, HTTP server

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
    echo "Usage: $0 {runner|server|telemetry|learn|eval|test|<message>}"
    echo ""
    echo "  runner     Interactive brain (talk + learn loop)"
    echo "  server     HTTP server on port 9200"
    echo "  telemetry  Prometheus metrics server on port 9201"
    echo "  learn      Learn from recorded conversations"
    echo "  eval       Evaluate model (quick test)"
    echo "  test       Run all brain tests"
    echo "  <message>  Talk to brain once"
    exit 0
fi

case "$1" in
    runner)
        echo "Starting interactive brain..."
        java -cp "$CP" io.matrix.brain.BrainRunner models/onnx/qwen05b
        ;;
    server)
        PORT=${2:-9200}
        echo "Starting brain server on port $PORT..."
        java -cp "$CP" io.matrix.brain.BrainHttpServer $PORT models/onnx/qwen05b
        ;;
    telemetry)
        echo "Starting telemetry server on port 9201..."
        java -cp "$CP" io.matrix.brain.BrainTelemetry
        ;;
    learn)
        echo "Learning from recorded conversations..."
        java -cp "$CP" io.matrix.brain.BrainImprover data/conversations
        ;;
    eval)
        echo "Evaluating model..."
        java -cp "$CP" io.matrix.cli.ConversationModelEval
        ;;
    test)
        echo "Running brain tests..."
        ./gradlew :matrix-core:test --tests "io.matrix.brain.*" --no-daemon
        ;;
    *)
        echo "Talking to brain..."
        echo "$1" | java -cp "$CP" io.matrix.brain.BrainRunner models/onnx/qwen05b
        ;;
esac
