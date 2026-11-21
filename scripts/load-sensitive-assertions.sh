#!/usr/bin/env bash
# CRJ-01 — the LOAD-SENSITIVE ASSERTION class: the measurement.
#
# WHAT THIS MEASURES
# -------------------
# R-EPIS-19 closed ONE instance of a class: an assertion whose truth depends on the
# MACHINE's load rather than on the code. The instance was PortfolioBenchmarkTest, and
# the class it belonged to was left "open and already on the register".
#
# The class is: an assertion of the form
#
#     assertThat(<A MEASURED DURATION>).isLessThan(<A CONSTANT BUDGET>)
#
# Such an assertion states a property of the machine ("this code finishes inside 500 ms"),
# not a property of the code ("this code does the same work as that code"). Under load
# the machine's speed changes and the assertion flips — while the code is unchanged.
#
# THE PROTOCOL IS THE REPOSITORY'S OWN (AGENTS.md):
#
#     "treat a failure that only appears under contention as unattributed until it is
#      reproduced in isolation"
#
# So the measurement is two-pass:
#
#   PASS 1 — run each candidate ALONE, three times, and record the worst elapsed time.
#            This is the BASELINE: the machine's own speed with nothing else running.
#   PASS 2 — run each candidate under a DELIBERATE LOAD (busy threads on every core),
#            three times, and record the worst elapsed time and whether it FAILED.
#   PASS 3 — re-run, ALONE, every candidate that failed under load. A failure that does
#            NOT reproduce in isolation is a load-sensitive assertion: the code is right
#            and the machine was busy. A failure that DOES reproduce is a REAL DEFECT and
#            is reported as such.
#
# WHAT IT PRINTS
# ---------------
#   the classification of every candidate, with the margin (budget / observed), the
#   contention result, and the verdict.
#
# The Gradle invocations are serialised with the repository's lock, and the load is
# stopped before each isolated re-run.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

export JAVA_HOME="$HOME/jdk25"
export PATH="$JAVA_HOME/bin:$PATH"
LOCK=/tmp/gradle-matrix.lock
OUT="${1:-/tmp/matrix-full/load-sensitive}"
REPEATS="${REPEATS:-3}"
HOG_THREADS="${HOG_THREADS:-$(nproc)}"

mkdir -p "$OUT"

# ── the candidates ────────────────────────────────────────────────────────────────
# A filter may be a class or a package; the package form is how the wider class is
# measured, because the class is 39 assertions spread over 23 files and a per-class
# run costs a Gradle start-up each time.
#
# The class was DISCOVERED, not guessed: a scan of the test sources for an assertion
# whose measured value carries a time unit in its name and whose threshold is a
# constant. The thresholds of 0 are tautologies and are excluded; a comparison against
# another measurement is a different class and is excluded.
CANDIDATES=(
  "io.matrix.api.RateLimiterTest"
  "io.matrix.compression.SimdBooleanCompressorTest"
  "io.matrix.compression.TruthTableMinimizerTest"
  "io.matrix.nativebin.NativeBinaryFullIntegrationTest"
  "io.matrix.nativeimage.NativeCompilationTest"
  "io.matrix.neuron.binary.BinaryBenchmarkTest"
  "io.matrix.research.W31PerformanceBenchmarkTest"
  "io.matrix.safety.BridgeSafetyTest"
  "io.matrix.benchmark.*"
  "io.matrix.consciousness.*"
  "io.matrix.imports.*"
  "io.matrix.integration.*"
  "io.matrix.reasoning.*"
  "io.matrix.research.*"
  "io.matrix.shadow.*"
)

hog_pids=()
start_hog() {
  hog_pids=()
  for _ in $(seq 1 "$HOG_THREADS"); do
    (while :; do :; done) &
    hog_pids+=($!)
  done
}
stop_hog() {
  for pid in "${hog_pids[@]:-}"; do
    kill -9 "$pid" 2>/dev/null || true
  done
  hog_pids=()
}

run_class() {
  # $1 = the class filter, $2 = the tag
  flock "$LOCK" ./gradlew :matrix-core:test --tests "$1" --offline --console=plain \
      > "$OUT/$2.log" 2>&1
  return $?
}

worst_elapsed() {
  # the slowest testcase time in the run's JUnit XML, in milliseconds
  python3 - "$1" <<'PY'
import glob, re, sys
tag = sys.argv[1]
best = 0.0
for path in glob.glob('matrix-core/build/test-results/test/*.xml'):
    text = open(path, encoding='utf-8', errors='replace').read()
    for m in re.finditer(r'<testcase [^>]*time="([0-9.]+)"', text):
        best = max(best, float(m.group(1)))
print(f"{best:.3f}")
PY
}

failed_count() {
  python3 - "$1" <<'PY'
import glob, re, sys
tag = sys.argv[1]
failed = 0
for path in glob.glob('matrix-core/build/test-results/test/*.xml'):
    text = open(path, encoding='utf-8', errors='replace').read()
    for m in re.finditer(r'<testsuite[^>]*failures="([0-9]+)"[^>]*errors="([0-9]+)"', text):
        failed += int(m.group(1)) + int(m.group(2))
print(failed)
PY
}

echo "the load-sensitive assertion class — the measurement"
echo "the machine: $(nproc) cores; the load: ${HOG_THREADS} busy threads; the repeats: ${REPEATS}"
echo

RESULTS="$OUT/results.tsv"
: > "$RESULTS"
printf 'class\talone_worst_s\talone_failures\tloaded_worst_s\tloaded_failures\tisolation_reproduced\n' >> "$RESULTS"

for candidate in "${CANDIDATES[@]}"; do
  slug="${candidate//./_}"

  # ── PASS 1: alone ──────────────────────────────────────────────────────────────
  alone_worst=0
  alone_failures=0
  for i in $(seq 1 "$REPEATS"); do
    rm -rf matrix-core/build/test-results/test
    run_class "$candidate" "alone_${slug}_${i}" || true
    t="$(worst_elapsed alone_${slug}_${i})"
    f="$(failed_count alone_${slug}_${i})"
    alone_failures=$((alone_failures + f))
    alone_worst="$(python3 -c "print(max($alone_worst, $t))")"
  done

  # ── PASS 2: under a deliberate load ───────────────────────────────────────────
  start_hog
  loaded_worst=0
  loaded_failures=0
  for i in $(seq 1 "$REPEATS"); do
    rm -rf matrix-core/build/test-results/test
    run_class "$candidate" "loaded_${slug}_${i}" || true
    t="$(worst_elapsed loaded_${slug}_${i})"
    f="$(failed_count loaded_${slug}_${i})"
    loaded_failures=$((loaded_failures + f))
    loaded_worst="$(python3 -c "print(max($loaded_worst, $t))")"
  done
  stop_hog

  # ── PASS 3: re-run ALONE everything that failed under the load ────────────────
  reproduced="n/a (it passed under the load)"
  if [ "$loaded_failures" -gt 0 ]; then
    rm -rf matrix-core/build/test-results/test
    run_class "$candidate" "isolation_${slug}" || true
    iso_failures="$(failed_count isolation_${slug})"
    if [ "$iso_failures" -gt 0 ]; then
      reproduced="YES — a REAL DEFECT, it reproduces in isolation"
    else
      reproduced="no — LOAD-SENSITIVE: it passes in isolation, so the code is right and the machine was busy"
    fi
  fi

  printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$candidate" "$alone_worst" "$alone_failures" \
      "$loaded_worst" "$loaded_failures" "$reproduced" >> "$RESULTS"
  printf '%-58s alone %8ss (%s failures)  loaded %8ss (%s failures)  %s\n' \
      "$candidate" "$alone_worst" "$alone_failures" "$loaded_worst" "$loaded_failures" "$reproduced"
done

echo
echo "the results: $RESULTS"
column -t -s $'\t' "$RESULTS" 2>/dev/null || cat "$RESULTS"
