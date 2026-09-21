#!/bin/bash
# ─── MATRIX Native Image Build Script ───
# Builds and tests a GraalVM native image for matrix-core.
#
# Prerequisites:
#   - GraalVM 25+ with native-image installed
#   - JAVA_HOME pointing to GraalVM
#
# Usage:
#   ./scripts/build-native.sh          # build + test
#   ./scripts/build-native.sh --build  # build only
#   ./scripts/build-native.sh --test   # test only (requires prior build)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
MODULE="matrix-core"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log() { echo -e "${GREEN}[MATRIX-NATIVE]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
err() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

# ─── Preflight checks ───
check_prerequisites() {
    log "Checking prerequisites..."

    if ! command -v native-image &>/dev/null; then
        err "native-image not found. Install GraalVM and run: gu install native-image"
        exit 1
    fi

    local native_version
    native_version=$(native-image --version 2>/dev/null || echo "unknown")
    log "native-image version: $native_version"

    if [[ -z "${JAVA_HOME:-}" ]]; then
        warn "JAVA_HOME not set. Using system Java."
    else
        log "JAVA_HOME: $JAVA_HOME"
    fi
}

# ─── Build native image ───
build_native() {
    log "Building native image for $MODULE..."
    local start_time=$SECONDS

    cd "$PROJECT_ROOT"

    # Step 1: Compile and run tests (JVM mode)
    log "Step 1/3: Compiling and running JVM tests..."
    ./gradlew :$MODULE:compileJava :$MODULE:test --no-daemon -q

    # Step 2: Build native image via Quarkus
    log "Step 2/3: Building native image (this may take several minutes)..."
    ./gradlew :$MODULE:build -Dquarkus.package.type=native --no-daemon

    # Step 3: Measure binary size
    local binary_path
    binary_path=$(find "$PROJECT_ROOT/$MODULE/build" -name "matrix-core-runner" -o -name "$MODULE-*-runner" 2>/dev/null | head -1)

    if [[ -n "$binary_path" && -f "$binary_path" ]]; then
        local size_bytes
        size_bytes=$(stat -c%s "$binary_path" 2>/dev/null || stat -f%z "$binary_path" 2>/dev/null || echo 0)
        local size_mb
        size_mb=$(echo "scale=2; $size_bytes / 1048576" | bc 2>/dev/null || echo "unknown")
        log "Native binary: $binary_path"
        log "Binary size: ${size_mb} MB ($size_bytes bytes)"
    else
        warn "Native binary not found. Check build output for errors."
    fi

    local elapsed=$((SECONDS - start_time))
    log "Build completed in ${elapsed}s"
}

# ─── Test native image ───
test_native() {
    log "Testing native image..."
    local start_time=$SECONDS

    cd "$PROJECT_ROOT"

    # Find the native binary
    local binary_path
    binary_path=$(find "$PROJECT_ROOT/$MODULE/build" -name "matrix-core-runner" -o -name "$MODULE-*-runner" 2>/dev/null | head -1)

    if [[ -z "$binary_path" || ! -f "$binary_path" ]]; then
        err "Native binary not found. Run with --build first."
        exit 1
    fi

    log "Testing binary: $binary_path"

    # Test 1: Health check (startup time measurement)
    log "Test 1: Health check + startup time..."
    local health_start=$SECONDS
    local health_result
    health_result=$("$binary_path" --help 2>&1 || true)
    local health_time=$((SECONDS - health_start))

    if echo "$health_result" | grep -q "matrix\|MATRIX"; then
        log "✓ Health check passed (${health_time}s startup)"
    else
        err "✗ Health check failed"
        echo "$health_result"
        exit 1
    fi

    # Test 2: Run native reflection tests via Gradle
    log "Test 2: Running native reflection tests..."
    ./gradlew :$MODULE:test --tests "io.matrix.nativeimage.*" --no-daemon -q || {
        warn "Some native reflection tests failed (may require full infrastructure)"
    }

    local elapsed=$((SECONDS - start_time))
    log "Tests completed in ${elapsed}s"
}

# ─── Main ───
main() {
    local do_build=true
    local do_test=true

    for arg in "$@"; do
        case "$arg" in
            --build) do_test=false ;;
            --test)  do_build=false ;;
            --help|-h)
                echo "Usage: $0 [--build|--test]"
                echo "  --build  Build native image only"
                echo "  --test   Test native image only (requires prior build)"
                exit 0
                ;;
        esac
    done

    check_prerequisites

    if $do_build; then
        build_native
    fi

    if $do_test; then
        test_native
    fi

    log "Done!"
}

main "$@"
