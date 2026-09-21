#!/bin/bash
# Run comprehensive native binary tests.
# Usage: ./native-full-test.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}/../.."

export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-graalce"
export PATH="$JAVA_HOME/bin:$PATH"

BINARY="matrix-core/build/native/nativeCompile/matrix-core"
if [ ! -x "$BINARY" ]; then
    echo "Error: Native binary not built yet"
    echo "Run: ./matrix-core/scripts/build-native.sh"
    exit 1
fi

echo "=== MATRIX Native Binary Full Test Suite ==="
echo ""

echo "[1/6] --version"
"$BINARY" --version

echo ""
echo "[2/6] --help"
"$BINARY" --help

echo ""
echo "[3/6] --status (memory, GC, etc.)"
"$BINARY" --status

echo ""
echo "[4/6] --bench (1M sqrt*sin)"
"$BINARY" --bench

echo ""
echo "[5/6] --info (detailed binary info)"
"$BINARY" --info

echo ""
echo "[6/6] --cognitive (full cognitive pipeline)"
"$BINARY" --cognitive

echo ""
echo "=== All Tests Complete ==="
