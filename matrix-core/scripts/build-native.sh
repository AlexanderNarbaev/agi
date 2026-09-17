#!/bin/bash
# Build the native binary with proper GraalVM setup.
# Usage: ./build-native.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}/../.."

echo "=== MATRIX Native Image Build ==="

# Set GraalVM as JAVA_HOME
GRAALVM_HOME="$HOME/.sdkman/candidates/java/25.0.2-graalce"
if [ ! -d "$GRAALVM_HOME" ]; then
    echo "Error: GraalVM 25.0.2 CE not found at $GRAALVM_HOME"
    echo "Install with: sdk install java 25.0.2-graalce"
    exit 1
fi

export JAVA_HOME="$GRAALVM_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

echo "Using JAVA_HOME=$JAVA_HOME"
echo "Java version: $(java -version 2>&1 | head -1)"
echo ""

# Check native-image
if ! command -v native-image >/dev/null 2>&1; then
    echo "Error: native-image not found"
    exit 1
fi

echo "native-image version: $(native-image --version 2>&1 | head -1)"
echo ""

echo "Building native image..."
./gradlew :matrix-core:nativeCompile

echo ""
echo "=== Build Complete ==="
ls -lah matrix-core/build/native/nativeCompile/matrix-core 2>&1
