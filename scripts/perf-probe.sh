#!/usr/bin/env bash
# RECON-W30 — performance probe.
#
# Runs MATRIX hot-kernel microbenchmarks and stamps the environment into the output.
#
# Why the environment block exists: a nanosecond number without knowing which CPU, which
# JVM, which GC and which heap produced it is not evidence, it is a rumour. Every run of
# this script emits a header recording the machine, the JVM, the flags and the CPU count
# that were live at measurement time, so a number in TUNING-PARAMETERS.md can always be
# traced to the conditions that produced it.
#
# Results land in docs-v2/operations/bench/ as JMH JSON plus a human-readable log.
#
# Usage:
#   scripts/perf-probe.sh                     # all kernels
#   scripts/perf-probe.sh --include cosine    # substring filter, e.g. one benchmark
#   scripts/perf-probe.sh --quick             # fewer iterations, for a smoke check
#
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT" || exit 1

BENCH_DIR="docs-v2/operations/bench"
INCLUDE=""
QUICK="no"

# JMH iteration budgets. Named, with units, because "a few iterations" is how a
# benchmark ends up reporting a number nobody can reproduce.
readonly WARMUP_ITERATIONS_STANDARD=3
readonly MEASURE_ITERATIONS_STANDARD=5
readonly WARMUP_ITERATIONS_QUICK=1
readonly MEASURE_ITERATIONS_QUICK=2
readonly ITERATION_SECONDS=1
readonly FORK_COUNT=1

while [ $# -gt 0 ]; do
    case "$1" in
        --include) INCLUDE="${2:-}"; shift 2 ;;
        --quick)   QUICK="yes"; shift ;;
        -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done

if [ "$QUICK" = "yes" ]; then
    WARMUP="$WARMUP_ITERATIONS_QUICK"
    MEASURE="$MEASURE_ITERATIONS_QUICK"
else
    WARMUP="$WARMUP_ITERATIONS_STANDARD"
    MEASURE="$MEASURE_ITERATIONS_STANDARD"
fi

# The Vector API is an incubating module; the jmhJar build already passes it via
# build.gradle, and the runner must too or the vectorised kernel cannot be loaded.
readonly VECTOR_MODULE_FLAG="--add-modules=jdk.incubator.vector"

# DiskBudget preflight: a benchmark that fills the disk produces numbers nobody can keep.
readonly DISK_REFUSE_BYTES=$((10 * 1024 * 1024 * 1024))
avail_bytes="$(df --output=avail -B1 . 2>/dev/null | tail -1 | tr -dc '0-9')"
if [ -n "$avail_bytes" ] && [ "$avail_bytes" -lt "$DISK_REFUSE_BYTES" ]; then
    echo "REFUSING to benchmark: only ${avail_bytes} bytes free on $(pwd), below the 10 GiB floor." >&2
    exit 1
fi

mkdir -p "$BENCH_DIR"

STAMP="$(date +%Y%m%d-%H%M%S)"
# .txt, not .log: .gitignore excludes *.log, and this file IS the provenance for
# every number in TUNING-PARAMETERS.md. The repo keeps transcripts as .txt
# (see docs-v2/research/fresh-clone-smoke-transcript.txt) for the same reason.
LOG="${BENCH_DIR}/w30-perf-${STAMP}.txt"

{
    echo "# MATRIX RECON-W30 performance probe"
    echo "# run at        : $(date +%Y-%m-%dT%H:%M:%S%z)"
    echo "# host          : $(cat /sys/class/dmi/id/product_name 2>/dev/null || echo unknown)"
    echo "# cpu           : $(lscpu 2>/dev/null | awk -F': *' '/Model name/{print $2}')"
    echo "# physical cores: $(lscpu 2>/dev/null | awk -F': *' '/Core\(s\) per socket/{print $2}') x $(lscpu 2>/dev/null | awk -F': *' '/^Socket\(s\)/{print $2}')"
    echo "# logical cpus  : $(nproc)"
    echo "# isa           : avx2=$(lscpu 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="avx2") print "yes"}') avx512f=$(lscpu 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="avx512f") print "yes"}')"
    echo "# jvm           : $(java -version 2>&1 | head -1)"
    echo "# gc            : $(java -XX:+PrintFlagsFinal -version 2>/dev/null | awk '/UseG1GC/ { print $4 }')"
    echo "# warmup        : ${WARMUP} x ${ITERATION_SECONDS}s"
    echo "# measurement   : ${MEASURE} x ${ITERATION_SECONDS}s, ${FORK_COUNT} fork"
    echo "# include       : ${INCLUDE:-<all>}"
    echo "# disk free     : ${avail_bytes} bytes"
    echo
} > "$LOG"

echo "environment block -> $LOG"
echo "building jmh jar (this dominates the wall clock; the jar is fat by design)..."
./gradlew :matrix-core:jmhJar --no-daemon --console=plain > /dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "jmhJar build FAILED" | tee -a "$LOG"
    exit 1
fi

JAR="$(ls matrix-core/build/libs/matrix-core-*-jmh.jar 2>/dev/null | head -1)"
if [ -z "$JAR" ]; then
    echo "no jmh jar produced" | tee -a "$LOG"
    exit 1
fi
echo "jar: $JAR"

JSON="${BENCH_DIR}/w30-perf-${STAMP}.json"

# Benchmark selection is the POSITIONAL regex argument, not "-p include=". On JMH 1.37
# the "-p include=..." form sets a property but still runs the whole jar: measured, that
# invocation produced 230 result entries across 6 benchmark classes while the log header
# claimed the filter had been applied. The positional form is what actually filters.
FILTER_ARGS=()
if [ -n "$INCLUDE" ]; then
    FILTER_ARGS=("$INCLUDE")
fi

java -jar "$JAR" \
    "${FILTER_ARGS[@]}" \
    -wi "$WARMUP" -w "${ITERATION_SECONDS}s" \
    -i "$MEASURE" -r "${ITERATION_SECONDS}s" \
    -f "$FORK_COUNT" \
    -rf json -rff "$JSON" \
    -jvmArgsAppend "$VECTOR_MODULE_FLAG" \
    >> "$LOG" 2>&1
STATUS=$?

{
    echo
    echo "# exit status: $STATUS"
    echo "# json: $JSON"
} >> "$LOG"

if [ $STATUS -ne 0 ]; then
    echo "benchmark run FAILED (exit $STATUS); see $LOG" >&2
    exit $STATUS
fi

# Post-condition: when a filter was requested, the JSON must contain only matching
# benchmarks. Without this check a filter that silently does nothing looks exactly like
# a successful filtered run, which is how the "-p include=" bug survived my first
# verification.
if [ -n "$INCLUDE" ]; then
    RAN="$(python3 -c "
import json,sys
d=json.load(open('$JSON'))
print(len([e for e in d if '$INCLUDE' in e['benchmark']]), len(d))
" 2>/dev/null || echo "0 0")"
    MATCHED="${RAN%% *}"
    TOTAL_ENTRIES="${RAN##* }"
    if [ "$MATCHED" -eq 0 ] || [ "$MATCHED" -ne "$TOTAL_ENTRIES" ]; then
        echo "ERROR: --include '$INCLUDE' did not filter: $MATCHED of $TOTAL_ENTRIES results match." >&2
        echo "       A filter that runs everything is worse than no filter, because the" >&2
        echo "       log header then claims a scoping that never happened." >&2
        exit 1
    fi
    echo "  filter verified: $MATCHED/$TOTAL_ENTRIES results match '$INCLUDE'"
fi

echo
echo "results:"
grep -E '^[A-Za-z].*\.(ns/op|ops/s)' "$LOG" | sed 's/^/  /'
echo
echo "  environment + full log : $LOG"
echo "  machine-readable       : $JSON"
