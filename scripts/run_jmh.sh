#!/bin/bash
# JMH Benchmark runner for MATRIX Core
# Requires: JMH packed in matrix-core build
# Usage: bash scripts/run_jmh.sh

set -e

echo "=== MATRIX JMH Benchmarks ==="
echo ""

cd "$(dirname "$0")/.."

echo "Building benchmarks..."
./gradlew :matrix-core:jmhJar --no-daemon 2>&1 | tail -5

echo ""
echo "Running benchmarks..."
java -jar matrix-core/build/libs/matrix-core-*-jmh.jar \
    -wi 2 -w 1s -i 3 -r 2s -f 1 \
    -rf json -rff build/jmh-result.json \
    2>&1 | tail -20

echo ""
echo "Results saved to matrix-core/build/jmh-result.json"
