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
    if echo "$output" | grep -q "the contradictions: 0"; then pass "$label"; else fail "$label"; echo "$output" | awk '{ print "        " $0 }'; fi
  else
    if echo "$output" | grep -q "$needle"; then pass "$label"; else fail "$label"; echo "$output" | awk '{ print "        " $0 }'; fi
  fi
}

mkdir -p "$WORK/clean/docs-v2/governance" "$WORK/clean/docs-v2/architecture" "$WORK/clean/scripts"
cp "$CHECKER" "$WORK/clean/scripts/validate-current-state.py"
# the PR number the stale-release fixture uses (the docs claim it is released)
FIXTURE_PR=50
printf "pull request #%s is released into main (#%s)\n" "$FIXTURE_PR" "$FIXTURE_PR" > "$WORK/subjects.txt"
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
printf "\n**The wave\x27s pull request #%s is open** (develop → main).\n" "$FIXTURE_PR" >> "$WORK/stale-release/docs-v2/governance/PROJECT-STATE.md"
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
if run "$WORK/stale-release" --strict --release-subjects-file "$WORK/subjects.txt" >/dev/null 2>&1; then
  fail "strict mode: exits 1 on a contradiction"
else
  pass "strict mode: exits 1 on a contradiction"
fi
if run "$WORK/clean" --strict --release-subjects-file "$WORK/subjects.txt" >/dev/null 2>&1; then
  pass "strict mode: exits 0 on the clean copy"
else
  fail "strict mode: exits 0 on the clean copy"
fi

echo ""
echo "=== 7. the actual repository is consistent"
expect "the repository: no contradiction" "-" "$(run "$ROOT")"

echo ""
echo "=== 8. the quality gate reports the contradiction as a violation (the wiring's own control)"
# The gate calls the checker as its CURRENT-1 rule; a wiring that is never seen to fail
# is a wiring that is not known to work. The fixture is a GIT repository whose main line
# carries the squash of pull request #50 while the current state still says that pull
# request is open - so the checker has a real contradiction to report (RELEASE_CLAIM), not
# a repository it cannot read. The gate must report CURRENT-1 as a VIOLATION, and the
# same gate with the wiring removed must NOT.
FIX="$WORK/gate"
mkdir -p "$FIX/docs-v2/governance" "$FIX/docs-v2/architecture" "$FIX/docs-v2/quality" "$FIX/scripts"
cp "$ROOT/scripts/validate-current-state.py" "$FIX/scripts/validate-current-state.py"
cp "$ROOT/scripts/quality-gate.sh" "$FIX/scripts/quality-gate.sh"
cp "$ROOT/docs-v2/quality/quality-baseline.txt" "$FIX/docs-v2/quality/" 2>/dev/null
cp "$ROOT/docs-v2/quality/frozen-zone-baseline.txt" "$FIX/docs-v2/quality/" 2>/dev/null
printf '# PROJECT STATE\n\n**The wave is released into main.**\n\nPull request #50 is open, the merge is the owner'"'"'s release decision.\n' \
  > "$FIX/docs-v2/governance/PROJECT-STATE.md"
printf '# RUNTIME TOPOLOGY\n\nThe two applications meet at exactly one governed crossing.\n' \
  > "$FIX/docs-v2/architecture/RUNTIME-TOPOLOGY.md"
printf '### R-X\n\n- **evidence**: scripts/validate-current-state.py:10 and nothing else.\n' \
  > "$FIX/docs-v2/governance/RISK-REGISTER.md"
( cd "$FIX" && git init -q . >/dev/null 2>&1 && git config user.email selftest@local \
    && git config user.name selftest && git add -A >/dev/null 2>&1 \
    && git commit -q -m "pull request #50 is released into main (#50)" >/dev/null 2>&1 \
    && git branch -M main >/dev/null 2>&1 )

# (a) the checker really finds the planted contradiction - not an unreadable repository
CHECKER_OUTPUT="$(python3 "$FIX/scripts/validate-current-state.py" --root "$FIX" --strict 2>&1)"
if echo "$CHECKER_OUTPUT" | grep -q "RELEASE_CLAIM"; then
  pass "the fixture's contradiction is detected: RELEASE_CLAIM"
else
  fail "the fixture's contradiction is detected: RELEASE_CLAIM"
  echo "$CHECKER_OUTPUT" | head -5 | awk '{ print "        " $0 }'
fi

# (b) the gate reports CURRENT-1 as a violation
GATE_OUTPUT="$(cd "$FIX" && bash scripts/quality-gate.sh 2>&1)"
if echo "$GATE_OUTPUT" | grep -q "CURRENT-1  the current-state documents contradict each other"; then
  pass "the gate reports CURRENT-1 as a violation"
else
  fail "the gate reports CURRENT-1 as a violation"
  echo "$GATE_OUTPUT" | grep -E "CURRENT-1" | head -3 | awk '{ print "        " $0 }'
fi

# (c) the control is sensitive to the wiring: with the call removed, the violation is gone
sed 's/^check_current_state$/#check_current_state/' "$FIX/scripts/quality-gate.sh" > "$FIX/scripts/quality-gate-nowiring.sh"
BROKEN_OUTPUT="$(cd "$FIX" && bash scripts/quality-gate-nowiring.sh 2>&1)"
if echo "$BROKEN_OUTPUT" | grep -q "CURRENT-1  the current-state documents contradict each other"; then
  fail "without the wiring the violation disappears"
else
  pass "without the wiring the violation disappears"
fi

echo ""
if [ "$failures" -eq 0 ]; then
  echo "the self-test: PASS (all controls behaved)"
  exit 0
else
  echo "the self-test: FAIL ($failures control(s) misbehaved)"
  exit 1
fi
