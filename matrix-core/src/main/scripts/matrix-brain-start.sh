#!/bin/bash
# MATRIX Brain — Production Startup Script
# Runs the brain HTTP server with all features

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

BRAIN_PORT=${1:-9200}

echo "MATRIX Brain — Production Startup"
echo "  Model: models/onnx/qwen05b"
echo "  Port: $BRAIN_PORT"
echo "  Endpoints:"
echo "    /health - Health check"
echo "    /chat - Send message (POST JSON)"
echo "    /learn - Learn from conversations (POST)"
echo "    /stats - Brain statistics"
echo "    /knowledge?q=query - Search KB"
echo "    /metrics - Prometheus metrics"
echo "    / - Web UI"
echo ""

java -cp "$CP" io.matrix.brain.BrainHttpServer $BRAIN_PORT models/onnx/qwen05b
