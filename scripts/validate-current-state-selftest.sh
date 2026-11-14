#!/bin/bash
# scripts/validate-current-state-selftest.sh - the negative control of the
# current-state consistency check.
#
# It builds small temporary repositories, plants the exact contradictions the
# checker exists to catch, and requires the checker to (a) notice the planted
# contradiction, (b) stay silent on the clean copy, and (c) notice a citation
# that no longer resolves. A checker that cannot be made to fail is not a
# checker.

set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CHECKER="$ROOT/scripts/validate-current-state.py"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

failures=0
pass() { echo "  PASS  $1"; }
fail() { echo "  FAIL  $1"; failures=$((failures + 1)); }

run() { # run <root> <extra args...>
  local root="$1"; shift
  python3 "$CHECKER" --root "$root" "$@" 2>&1
}

expect() { # expect <label> <needle|-> <output>
  local label="$1" needle="$2" output="$3"
  if [ "$needle" = "-" ]; then
    if echo "$output" | grep -q "the contradictions: 0"; then pass "$label"; else fail "$label"; echo "$output" | sed 's/^/        /'; fi
  else
    if echo "$output" | grep -q "$needle"; then pass "$label"; else fail "$label"; echo "$output" | sed 's/^/        /'; fi
  fi
}

mkdir -p "$WORK/clean/docs-v2/governance" "$WORK/clean/docs-v2/architecture" "$WORK/clean/scripts"
cp "$CHECKER" "$WORK/clean/scripts/validate-current-state.py"
# the PR number the stale-release fixture uses (the docs claim it is released)
FIXTURE_PR=50
printf "pull request #${FIXTURE_PR} is released into main (#${FIXTURE_PR})\n" > "$WORK/subjects.txt"
printf '# PROJECT STATE\n\nThe wave is released into main.\n' > "$WORK/clean/docs-v2/governance/PROJECT-STATE.md"
printf '# RUNTIME TOPOLOGY\n\nThe two applications meet at exactly one governed crossing.\n' > "$WORK/clean/docs-v2/architecture/RUNTIME-TOPOLOGY.md"
printf '### R-X\n\n- **evidence**: scripts/validate-current-state.py:10 and nothing else.\n' > "$WORK/clean/docs-v2/governance/RISK-REGISTER.md"

echo "=== 1. the clean copy stays silent (negative control)"
expect "clean copy: no contradiction" "-" "$(run "$WORK/clean" --release-subjects-file "$WORK/subjects.txt")"

echo ""
echo "=== 2. a stale release claim is caught (positive control)"
cp -r "$WORK/clean" "$WORK/stale-release"
sed -i "s/released into main\./open, the merge is the owner's release decision./; s/pull request #${FIXTURE_PR} is released into main (#${FIXTURE_PR})//" \
  "$WORK/stale-release/docs-v2/governance/PROJECT-STATE.md"
printf "\n**The wave\x27s pull request #${FIXTURE_PR} is open** (develop → main).\n" >> "$WORK/stale-release/docs-v2/governance/PROJECT-STATE.md"
expect "stale release claim: RELEASE_CLAIM" "RELEASE_CLAIM" \
  "$(run "$WORK/stale-release" --release-subjects-file "$WORK/subjects.txt")"

echo ""
echo "=== 3. a topology that denies the crossing is caught (positive control)"
cp -r "$WORK/clean" "$WORK/denial"
mkdir -p "$WORK/denial/matrix-core/src/main/java/io/matrix/contract/bridge"
printf 'class CognitiveTaskRequest {}\n' > "$WORK/denial/matrix-core/src/main/java/io/matrix/contract/bridge/CognitiveTaskRequest.java"
printf '\n## 1. The two serving chains\n\n**They do not meet.** The isolation is not a convention.\n' \
  >> "$WORK/denial/docs-v2/architecture/RUNTIME-TOPOLOGY.md"
expect "topology denial: TOPOLOGY_CROSSING" "TOPOLOGY_CROSSING" \
  "$(run "$WORK/denial" --release-subjects-file "$WORK/subjects.txt")"

echo ""
echo "=== 4. a citation that does not resolve is caught (positive control)"
cp -r "$WORK/clean" "$WORK/stale-evidence"
printf '### R-Y\n\n- **evidence**: ThereIsNoSuchFile.java:5 is cited.\n' >> "$WORK/stale-evidence/docs-v2/governance/RISK-REGISTER.md"
expect "unresolved citation: STALE_EVIDENCE" "STALE_EVIDENCE" \
  "$(run "$WORK/stale-evidence" --release-subjects-file "$WORK/subjects.txt")"

echo ""
echo "=== 5. a citation beyond the end of its file is caught (positive control)"
cp -r "$WORK/clean" "$WORK/stale-line"
printf '### R-Z\n\n- **evidence**: scripts/validate-current-state.py:99999 is cited.\n' >> "$WORK/stale-line/docs-v2/governance/RISK-REGISTER.md"
expect "line beyond EOF: STALE_EVIDENCE" "STALE_EVIDENCE" \
  "$(run "$WORK/stale-line" --release-subjects-file "$WORK/subjects.txt")"

echo ""
echo "=== 6. the strict mode's exit code follows the contradiction"
run "$WORK/stale-release" --strict --release-subjects-file "$WORK/subjects.txt" >/dev/null 2>&1
if [ "$?" -ne 0 ]; then pass "strict mode: exits 1 on a contradiction"; else fail "strict mode: exits 1 on a contradiction"; fi
run "$WORK/clean" --strict --release-subjects-file "$WORK/subjects.txt" >/dev/null 2>&1
if [ "$?" -eq 0 ]; then pass "strict mode: exits 0 on the clean copy"; else fail "strict mode: exits 0 on the clean copy"; fi

echo ""
echo "=== 7. the actual repository is consistent"
expect "the repository: no contradiction" "-" "$(run "$ROOT")"

echo ""
if [ "$failures" -eq 0 ]; then
  echo "the self-test: PASS (all controls behaved)"
  exit 0
else
  echo "the self-test: FAIL ($failures control(s) misbehaved)"
  exit 1
fi
