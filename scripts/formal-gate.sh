#!/usr/bin/env bash
# RECON-W42.9 / X.2 — the formal specifications, run and asserted.
#
# WHY THIS IS A SCRIPT IN THE REPOSITORY:
#
#   - the twelve TLA+ specifications under formal/ were only ever checked by
#     the CI job (.github/workflows/tla.yml), so on a developer machine there
#     was no way to run them;
#   - and "the CI is green" is a claim about a moment, not about the tree in
#     front of you. The same words the Local Alpha checklist uses apply: a
#     gate that cannot go red is not a gate, so this one EXITS 1 when any
#     specification fails or errors.
#
# The invocation is the one the CI uses and the one each .cfg documents:
#   java -jar tla2tools.jar -config <spec>.cfg <spec>.tla
# `-continue` is deliberately NOT passed: with it, TLC's exit code is 0 even
# on a violated property, so the check would be unsound.
#
# Usage:
#   scripts/formal-gate.sh            # every specification under formal/
#
# Exit status: 0 when every specification holds, 1 when any does not.
set -u

readonly ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SPEC_DIR="$ROOT/formal"
readonly JAR="$ROOT/tools/tla2tools.jar"
readonly TIMEOUT_SECONDS=300

if [ ! -f "$JAR" ]; then
  printf 'the TLC jar is missing: %s\n' "$JAR"
  printf 'it is tracked in the repository (tools/tla2tools.jar); see tools/README.md\n'
  exit 1
fi

# W48-04 — the tool is verified, not assumed.
#
# The jar is tracked so that the local gate and the CI model-check with the same
# bytes, and the upstream release it came from was replaced in place (the
# provenance is in tools/README.md). A jar of a different size would mean that
# the tool changed underneath the specifications, and every property that was
# model-checked would have to be model-checked again.
readonly JAR_SHA256="fc6878c603f0fdc18b9fe3e5bf51860cb90ab5b1154e3c371660c036d6bdd63e"
if command -v sha256sum >/dev/null 2>&1; then
    ACTUAL_SHA256="$(sha256sum "$JAR" | awk '{print $1}')"
    if [ "$ACTUAL_SHA256" != "$JAR_SHA256" ]; then
        printf 'the formal tool is not the pinned one:\n'
        printf '  expected %s\n' "$JAR_SHA256"
        printf '  actual   %s\n' "$ACTUAL_SHA256"
        printf 'the specifications were model-checked with the pinned bytes; a different\n'
        printf 'tool must be a deliberate upgrade, not an accident\n'
        exit 1
    fi
fi

if ! command -v java >/dev/null 2>&1; then
  printf 'java is not on the PATH\n'
  exit 1
fi

PASSED=0
FAILED=0
FAILED_SPECS=""

cd "$SPEC_DIR" || exit 1

for cfg in *.cfg; do
  spec="${cfg%.cfg}"
  out=$(timeout "$TIMEOUT_SECONDS" java -jar "$JAR" -config "$cfg" "$spec.tla" 2>&1)
  code=$?
  if [ "$code" -eq 0 ] && printf '%s' "$out" | grep -q "No error has been found."; then
    printf '  OK    %s\n' "$spec"
    PASSED=$((PASSED + 1))
  else
    printf '  FAIL  %s (exit %s)\n' "$spec" "$code"
    printf '%s' "$out" | grep -iE "error|violated|exception" | head -3
    FAILED=$((FAILED + 1))
    FAILED_SPECS="$FAILED_SPECS $spec"
  fi
done

printf '=== the formal gate: %s passed, %s failed\n' "$PASSED" "$FAILED"
if [ "$FAILED" -ne 0 ]; then
  printf 'the failed specifications:%s\n' "$FAILED_SPECS"
  exit 1
fi
exit 0
