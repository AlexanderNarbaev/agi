#!/usr/bin/env bash
# CONCEPT-TO-ACTION — the negative control for the artifact writer's lease.
#
# A guard that no test can fail is not a guard. This script removes the writer's lease — the
# exact state the writer was in before the hardening — and then runs the concurrency
# regression test. The test MUST fail: if it passes, the lease is not load-bearing, or the
# test does not exercise it, and either finding is a real gap.
#
# The mutation is in-place, and the original file is restored under a trap, so the working
# tree is left exactly as it was found. The mutation is verified to have changed the file
# before the tests run; a mutation that did not apply would report a false pass.
#
# Usage: scripts/artifact-writer-negative-control.sh
# Exit:  0 when the mutation was KILLED (the guard is load-bearing), 1 otherwise.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

export JAVA_HOME="${JAVA_HOME:-$HOME/jdk25}"
readonly TARGET="matrix-core/src/main/java/io/matrix/actions/ArtifactWriter.java"
readonly FIXER="/tmp/c2a-lease-mutation.py"
readonly GRADLE_TIMEOUT_SECONDS=1800
readonly TEST_FILTER="io.matrix.actions.ArtifactWriterConcurrencyTest"

ORIG=""
ORIG_SUM=""
restore() {
  if [ -n "$ORIG" ] && [ -f "$ORIG.bak" ]; then
    mv "$ORIG.bak" "$ORIG"
  fi
  if [ -n "$ORIG" ] && [ -n "$ORIG_SUM" ]; then
    local now
    now="$(sha256sum "$ORIG" | cut -d' ' -f1)"
    if [ "$now" != "$ORIG_SUM" ]; then
      echo "ERROR: the original file was not restored (sha256 $now, expected $ORIG_SUM)"
    fi
  fi
}
trap restore EXIT INT TERM

if [ ! -f "$TARGET" ]; then
  echo "SKIP: $TARGET is missing"
  exit 1
fi

cat > "$FIXER" << 'PY'
import sys

path = sys.argv[1]
source = open(path).read()

start = "    private RootLease acquireRootLease() throws WriterConflict {\n"
end = "    private static void closeQuietly(FileChannel channel) {"
assert start in source, "the lease method's start anchor is missing"
assert end in source, "the lease method's end anchor is missing"

mutant = (
    "    private RootLease acquireRootLease() throws WriterConflict {\n"
    "        // MUTATION: the writer takes no lease at all\n"
    "        return () -> { };\n"
    "    }\n\n"
)
open(path, "w").write(source[:source.index(start)] + mutant + source[source.index(end):])
PY

ORIG_SUM="$(sha256sum "$TARGET" | cut -d' ' -f1)"
cp "$TARGET" "$TARGET.bak"
ORIG="$TARGET"
python3 "$FIXER" "$TARGET"
if cmp -s "$TARGET" "$TARGET.bak"; then
  echo "BROKEN: the mutation did not change the file"
  restore
  exit 1
fi
echo "--- the writer's lease has been removed; the concurrency regression test must fail"

LOG="$(mktemp -p "${TMPDIR:-/tmp}" c2a-negctl.XXXXXX)"
flock /tmp/gradle-matrix.lock timeout "$GRADLE_TIMEOUT_SECONDS" \
  ./gradlew :matrix-core:test --tests "$TEST_FILTER" --offline --no-daemon --console=plain \
  > "$LOG" 2>&1
STATUS=$?

# The verdict must be tied to a TEST failure. A non-zero Gradle exit can also mean a
# compile error, a timeout or a dead daemon, and calling that a kill would report a
# confident negative about a guard that was never exercised.
if [ "$STATUS" -eq 0 ]; then
  echo "SURVIVED: the concurrency test is green without the lease — a real gap"
  tail -20 "$LOG"
  rm -f "$LOG"
  exit 1
fi
if grep -qE "error: |FAILURE: Build failed" "$LOG" && ! grep -qE "tests completed.*failed" "$LOG"; then
  echo "BROKEN: the run failed without a test failure — the mutation did not produce a verdict"
  tail -20 "$LOG"
  rm -f "$LOG"
  exit 1
fi
if ! grep -qE "tests completed.*failed|FAILED" "$LOG"; then
  echo "BROKEN: the run failed without a test failure — the mutation did not produce a verdict"
  tail -20 "$LOG"
  rm -f "$LOG"
  exit 1
fi

FAILURES="$(grep -cE "FAILED" "$LOG" || true)"
echo "KILLED: the concurrency test failed without the lease ($FAILURES failing test(s))"
grep -E "FAILED|tests completed" "$LOG" | head -10
rm -f "$LOG"
exit 0
