#!/usr/bin/env bash
# CONCEPT-TO-ACTION (Workstream C) — collect REAL learning evidence and measure on it.
#
# What this runbook does, in order:
#   1. records the episodic log's BEFORE state (records, class distribution, sha256);
#   2. runs the REAL runtime (io.matrix.brain.runtime.TrueMindCycle — the conductor the
#      gateway serves) against the machine's REAL knowledge store, through the production
#      write path (EpisodicLog.append -> PromotionGate), and writes the resulting episodes
#      into <mindDir>/episodic.ndjson;
#   3. records the AFTER state and proves the log grew by exactly the number of episodes
#      the production promotion gate admitted;
#   4. re-measures the E1 evidence base on the new data, with its artefact redirected so the
#      retained October artefact is NOT overwritten;
#   5. measures the learning subsystem over the collected evidence (the representation
#      comparison) and writes the machine-readable measurements.
#
# NOTHING in the production configuration is changed: not the label rule, not K_MAX (20), not
# the production fold, not the trainer width, not the window selector.
#
# Usage: scripts/c2a-collect-learning-evidence.sh [--dry]
#   --dry   run the collection against a SANDBOX log under
#           matrix-brain-runtime/build/c2a-sandbox/ instead of the machine's real log.
#           The dry run is a rehearsal and is never evidence.
#
# Exit: 0 when every stage ran and the log grew; non-zero otherwise.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

export JAVA_HOME="${JAVA_HOME:-$HOME/jdk25}"
readonly LOCK="/tmp/gradle-matrix.lock"
readonly GRADLE_TIMEOUT_SECONDS=1800
readonly MIND_DIR="${MATRIX_MIND_DIR:-$ROOT/data/mind}"
readonly HDC_STORE="$MIND_DIR/hdc_kb.ndjson"
readonly ARTIFACT="$ROOT/docs-v2/engineering/C2A-LEARNING-EVIDENCE.json"
readonly E1_ARTIFACT="${C2A_E1_ARTIFACT:-/tmp/c2a/E1-LEARNING-EVIDENCE-ON-NEW-DATA.json}"
readonly EVIDENCE_DIR="$ROOT/build/c2a-evidence"
readonly COLLECT_XML="TEST-io.matrix.brain.runtime.C2ARealEpisodeCollectionTest.xml"
readonly E1_XML="TEST-io.matrix.brain.runtime.E1LearningEvidenceTest.xml"
readonly MEASURE_XML="TEST-io.matrix.knowledge.C2ALearningEvidenceTest.xml"

DRY=0
for arg in "$@"; do
  case "$arg" in
    --dry) DRY=1 ;;
    *) echo "usage: $0 [--dry]" >&2; exit 2 ;;
  esac
done

# The build is shared: every Gradle invocation goes through the lock, and the --tests filter
# goes IMMEDIATELY after the task name. Never --rerun / --rerun-tasks (they drag in the
# native-image build).
gradle_locked() {
  flock "$LOCK" timeout "$GRADLE_TIMEOUT_SECONDS" ./gradlew "$@" --offline --console=plain
}

count_records() {
  if [ -f "$1" ]; then grep -c . "$1"; else echo 0; fi
}

sha256_of() {
  if [ -f "$1" ]; then sha256sum "$1" | cut -d' ' -f1; else echo "absent"; fi
}

# The class distribution under the production rule (confidence >= 0.7 AND accepted).
distribution_of() {
  python3 - "$1" << 'PY'
import json, sys
path = sys.argv[1]
pos = neg = 0
try:
    for line in open(path):
        line = line.strip()
        if not line:
            continue
        try:
            row = json.loads(line)
        except ValueError:
            continue
        if float(row.get("confidence", 0.0)) >= 0.7 and bool(row.get("accepted", False)):
            pos += 1
        else:
            neg += 1
except FileNotFoundError:
    pass
print(f"{pos} positive / {neg} negative")
PY
}

# The JUnit XML is the evidence, and another worker may delete a module's
# build/test-results between runs (the documented way to refresh it). Every XML produced by
# this runbook is therefore copied into build/c2a-evidence/ the moment it is produced.
save_xml() {
  local src="$1"
  [ -f "$src" ] || return 1
  mkdir -p "$EVIDENCE_DIR"
  cp "$src" "$EVIDENCE_DIR/$(basename "$src")"
}

report_xml() {
  local name="$1"
  local saved="$EVIDENCE_DIR/$name"
  if [ ! -f "$saved" ]; then
    echo "   MISSING: $name"
    return
  fi
  echo "   $saved"
  python3 - "$saved" << 'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
print(f"      tests={root.get('tests')} failures={root.get('failures')} "
      f"errors={root.get('errors')} skipped={root.get('skipped')}")
for case in root.iter('testcase'):
    print(f"      - {case.get('name')} ({case.get('time')} s)")
PY
}

echo "== C2A learning-evidence collection =="
echo "repo:    $ROOT"
echo "mindDir: $MIND_DIR"
echo "hdc:     $HDC_STORE"

COLLECT_FILTER="io.matrix.brain.runtime.C2ARealEpisodeCollectionTest"
if [ "$DRY" = "1" ]; then
  # the sandbox rehearsal: the same workload against a log under the module's build
  # directory. It never touches the machine's real episodic log.
  mkdir -p "$ROOT/matrix-brain-runtime/build/c2a-sandbox"
  EPISODIC_LOG="$ROOT/matrix-brain-runtime/build/c2a-sandbox/episodic.ndjson"
  COLLECT_FILTER="io.matrix.brain.runtime.C2ARealEpisodeCollectionTest.probeWorkloadAgainstSandbox"
  echo "mode:    DRY RUN (sandbox) — this is a rehearsal, not evidence"
else
  EPISODIC_LOG="$MIND_DIR/episodic.ndjson"
  echo "mode:    MEASURED (the machine's real log)"
fi
echo "log:     $EPISODIC_LOG"

if [ ! -f "$HDC_STORE" ]; then
  echo "FAIL: the knowledge store $HDC_STORE is absent — nothing can be retrieved"
  exit 1
fi

rm -rf "$EVIDENCE_DIR"
mkdir -p "$EVIDENCE_DIR"

BEFORE_COUNT="$(count_records "$EPISODIC_LOG")"
BEFORE_SHA="$(sha256_of "$EPISODIC_LOG")"
BEFORE_DIST="$(distribution_of "$EPISODIC_LOG")"
echo "-- BEFORE: $BEFORE_COUNT records, $BEFORE_DIST, sha256=$BEFORE_SHA"

# ---- 1. the collection -------------------------------------------------------
echo "-- step 1: the real runtime writes real episodes"
rm -rf matrix-brain-runtime/build/test-results
# the flag makes the measured collection run: it is the only step that writes into the
# machine's real episodic log, so it must never happen as a side effect of an ordinary build
if ! gradle_locked :matrix-brain-runtime:test --tests "$COLLECT_FILTER" \
        -Dmatrix.c2a.collect=true; then
  echo "FAIL: the collection run failed"
  exit 1
fi
save_xml "matrix-brain-runtime/build/test-results/test/$COLLECT_XML"

AFTER_COUNT="$(count_records "$EPISODIC_LOG")"
AFTER_SHA="$(sha256_of "$EPISODIC_LOG")"
AFTER_DIST="$(distribution_of "$EPISODIC_LOG")"
echo "-- AFTER:  $AFTER_COUNT records, $AFTER_DIST, sha256=$AFTER_SHA"

if [ "$AFTER_COUNT" -le "$BEFORE_COUNT" ]; then
  echo "FAIL: the episodic log did not grow ($BEFORE_COUNT -> $AFTER_COUNT)"
  echo "      no episode was admitted — the run is NOT evidence"
  exit 1
fi
echo "-- the log grew by $((AFTER_COUNT - BEFORE_COUNT)) episode(s)"

# ---- 2. the E1 evidence base, re-measured on the new data --------------------
echo "-- step 2: E1 re-measured on the new data (the retained artefact is NOT overwritten)"
mkdir -p "$(dirname "$E1_ARTIFACT")"
rm -rf matrix-brain-runtime/build/test-results
if ! gradle_locked :matrix-brain-runtime:test \
    --tests "io.matrix.brain.runtime.E1LearningEvidenceTest" \
    -Dmatrix.e1.result="$E1_ARTIFACT"; then
  echo "FAIL: the E1 re-measurement failed"
  exit 1
fi
echo "-- E1 artefact: $E1_ARTIFACT"
save_xml "matrix-brain-runtime/build/test-results/test/$E1_XML"

# ---- 3. the measurement over the collected evidence --------------------------
echo "-- step 3: the measurement over the collected evidence"
rm -rf matrix-core/build/test-results
if ! gradle_locked :matrix-core:test --tests "io.matrix.knowledge.C2ALearningEvidenceTest"; then
  echo "FAIL: the measurement harness failed"
  echo "      NOTE: Gradle compiles the WHOLE test source set. If a sibling worker's test"
  echo "      file does not compile, this step fails for a reason that has nothing to do"
  echo "      with this harness — fix that file, or exclude it with a temporary init script."
  exit 1
fi
save_xml "matrix-core/build/test-results/test/$MEASURE_XML"

# ---- 4. the evidence ---------------------------------------------------------
echo "-- step 4: the evidence"
report_xml "$COLLECT_XML"
report_xml "$E1_XML"
report_xml "$MEASURE_XML"
echo "   artefact: $ARTIFACT"

echo "== done =="
