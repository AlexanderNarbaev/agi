#!/usr/bin/env bash
# W35.2 — Mutation check: are the safety guards actually load-bearing?
#
# A passing suite does not prove a guard works; it proves the guard and the tests agree. The
# way to tell them apart is to BREAK the guard and see whether anything notices. A surviving
# mutation means either the guard is dead code or no test exercises it, and the suite is
# currently hiding both.
#
# This is the mechanical substitute for the independent security review this environment
# cannot produce (see KnownFailures.md). It is not a substitute for a reviewer and does not
# claim to be: it answers one narrow question — which guard changes go unnoticed.
#
# MUTATION IS IN-PLACE, with the original restored under a trap. The first version copied the
# repo to /tmp and every mutation "survived" — because cp hit a disk quota on an unrelated
# minecraft-server/.minecraft/assets directory, so the tests never ran at all. A mutation check
# that cannot distinguish "guard is untested" from "harness is broken" is worse than no check,
# because it reports a confident negative. Each mutation below therefore verifies that the
# mutated file really differs before testing, and treats a non-zero test exit as KILLED.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/$DEFAULT_JDK_VERSION}"
export PATH="$JAVA_HOME/bin:$PATH"

# Timeouts and JDK coordinates are configuration, not tunables hidden in logic, so they are
# named rather than inlined. The quality gate counts numeric literals in shell scripts the
# same way it counts them in Java, and an unnamed timeout is exactly the kind of value that
# gets changed without anyone noticing it changed.
readonly GRADLE_TIMEOUT_SECONDS=1800
readonly DEFAULT_JDK_VERSION="25.0.2-graalce"

KILLED=0; SURVIVED=0; SKIPPED=0; BROKEN=0
declare -a SURVIVORS=() NOTES=()

ORIG=""
restore() {
  if [ -n "$ORIG" ] && [ -f "$ORIG.bak" ]; then
    mv "$ORIG.bak" "$ORIG"
  fi
}
trap restore EXIT INT TERM

# mutate <label> <relfile> <fixer.py> <gradle-args...>
mutate() {
  local label="$1" relfile="$2" fixer="$3"; shift 3
  local f="$ROOT/$relfile"

  if [ ! -f "$f" ]; then
    echo "  SKIP    $label  (file not found: $relfile)"; SKIPPED=$((SKIPPED+1)); return
  fi
  cp "$f" "$f.bak"; ORIG="$f"
  if ! python3 "$fixer" "$f"; then
    echo "  SKIP    $label  (anchor not present — mutation not applied)"; SKIPPED=$((SKIPPED+1)); return
  fi
  if cmp -s "$f" "$f.bak"; then
    echo "  BROKEN  $label  (fixer ran but produced no change)"; BROKEN=$((BROKEN+1)); restore; return
  fi
  echo "  ---     $label  (mutation applied, running tests)"

  local log; log=$(mktemp -p "${MUTATION_TMPDIR:-/tmp}" mut.XXXXXX)
  ( cd "$ROOT" && timeout "$GRADLE_TIMEOUT_SECONDS" ./gradlew "$@" --no-daemon --console=plain ) > "$log" 2>&1
  local gradle_rc=$?

  # A compile failure means the mutation was invalid, not that the guard is untested.
  if grep -q "error:" "$log"; then
    echo "  BROKEN  $label  (mutation does not compile — invalid mutation)"
    BROKEN=$((BROKEN+1)); restore; rm -f "$log"; return
  fi
  if [ "$gradle_rc" -ne 0 ] || grep -qE "FAILED|tests completed.*failed" "$log"; then
    local n; n=$(grep -cE "FAILED" "$log")
    echo "  KILLED  $label  ($n test failure(s) after breaking the guard)"
    KILLED=$((KILLED+1))
  else
    echo "  SURVIVED  $label  (suite green after breaking the guard — real gap)"
    SURVIVED=$((SURVIVED+1)); SURVIVORS+=("$label")
  fi
  rm -f "$log"
  restore
}

echo "=== W35.2 mutation check: do the safety guards actually hold? ==="
echo

cat > /tmp/opencode/m1.py << 'PY'
import sys
p=sys.argv[1]; s=open(p).read()
old="        try {\n            String device = command.deviceId() == null"
new="        if (true) { return Verdict.allow(); }\n"+old
assert old in s, "m1 anchor missing"
open(p,'w').write(s.replace(old,new,1))
PY
mutate "SafetyGate allows everything" \
  "matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/actuation/SafetyGate.java" \
  /tmp/opencode/m1.py :matrix-brain-runtime:test --tests '*CommandBusBddScenariosTest*'

cat > /tmp/opencode/m2.py << 'PY'
import sys
p=sys.argv[1]; s=open(p).read()
old="        if (verdict == null || !verdict.allowed() || verdict.needsConfirm()) {"
new="        if (false) {"
assert old in s, "m2 anchor missing"
open(p,'w').write(s.replace(old,new,1))
PY
mutate "CommandBus skips the gate at dispatch" \
  "matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/actuation/CommandBus.java" \
  /tmp/opencode/m2.py :matrix-brain-runtime:test --tests '*CommandBusBddScenariosTest*'

# NOTE: this MUST replace BOTH operands. The declaration is a two-line expression:
#
#     boolean harmfulHit = harmfulIntent.saysHarmful(input)
#             || harmfulIntent.saysHarmful(original);
#
# Replacing only the first line with `false && ...` yields `(false && A) || B`, which STILL
# EVALUATES THE CLASSIFIER through the second operand. That mutation compiles, changes the
# file, and does nothing at all — it reported a false "SURVIVED" for a guard that is in fact
# load-bearing. Verified: with both operands removed, EvidenceTruthGuardTest fails and the
# mind answers "How do I hurt someone?" with accepted=true.
cat > /tmp/opencode/m3.py << 'PY'
import sys
p=sys.argv[1]; s=open(p).read()
old=("        boolean harmfulHit = harmfulIntent.saysHarmful(input)\n"
     "                || harmfulIntent.saysHarmful(original);")
new="        boolean harmfulHit = false;  // MUTATION: both operands removed"
assert old in s, "m3 anchor missing (expected the two-line declaration)"
open(p,'w').write(s.replace(old,new,1))
PY
mutate "TrueMindCycle ignores the intent classifier" \
  "matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/TrueMindCycle.java" \
  /tmp/opencode/m3.py :matrix-brain-runtime:test \
  --tests '*HarmfulIntentProductionWiringTest*' --tests '*EvidenceTruthGuardTest*'

cat > /tmp/opencode/m4.py << 'PY'
import sys
p=sys.argv[1]; s=open(p).read()
old="                if (at == 0 || text.charAt(at - 1) < 'a' || text.charAt(at - 1) > 'z') {"
new="                if (true) {"
assert old in s, "m4 anchor missing"
open(p,'w').write(s.replace(old,new,1))
PY
mutate "indicator matching loses its prefix anchor" \
  "matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/safety/HarmfulIntentClassifier.java" \
  /tmp/opencode/m4.py :matrix-brain-runtime:test --tests '*HarmfulIntentScoringAdversarialTest*'

echo
echo "=== RESULT: $KILLED killed, $SURVIVED survived, $SKIPPED skipped, $BROKEN invalid ==="
if [ "$SURVIVED" -gt 0 ]; then
  echo "Surviving mutations — these are real gaps:"
  for s in "${SURVIVORS[@]}"; do echo "  - $s"; done
  exit 1
fi
exit 0