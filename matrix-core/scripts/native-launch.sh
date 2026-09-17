#!/bin/bash
# Launch the native binary as a long-lived service.
# Usage: ./native-launch.sh <command> [args...]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BINARY="${SCRIPT_DIR}/../build/native/nativeCompile/matrix-core"

if [ ! -x "$BINARY" ]; then
    echo "Error: Native binary not found at $BINARY"
    echo "Build it first: ./gradlew :matrix-core:nativeCompile"
    exit 1
fi

case "${1:-version}" in
    version)
        "$BINARY" --version
        ;;
    status)
        "$BINARY" --status
        ;;
    bench)
        "$BINARY" --bench
        ;;
    help)
        "$BINARY" --help
        ;;
    *)
        "$BINARY" "$@"
        ;;
esac
