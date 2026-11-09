#!/usr/bin/env bash
#
# W52 — the cross-process demonstration of BOTH directions of the bridge.
#
# THE QUESTION THIS SCRIPT ANSWERS
# "Do the two chains talk to each other in BOTH directions, in two processes, over a
#  network, with the same governance on both sides?" W51 proved one direction: chain B
# published an observation and chain A recorded it. A system whose halves can only report
# upwards is not one system, so W52 proves the other direction too: chain A offers what
# its epistemic core established, and chain B's brain takes it into its own memory.
#
# WHAT IT DOES
#   1. builds chain A (the Quarkus application) and chain B (the gateway);
#   2. starts BOTH, in separate processes, on ports the operating system chose;
#   3. the FIRST direction: chain B's publisher, in a third process, posts a signed
#      observation to chain A — and chain A accepts it;
#   4. the SECOND direction: chain A's publisher, in a third process, posts a signed
#      established belief to chain B — and chain B's brain takes it into its memory;
#   5. the brain's memory is MEASURED before and after: it grows by exactly what was
#      offered, and the answer says how much it holds;
#   6. a forgery in the SECOND direction is refused, exactly as in the first;
#   7. shows that the owner's epistemic database is byte-identical before and after.
#
# NOTHING OF THE OWNER IS TOUCHED
# Both processes run in the private temporary directory of this script, with their own
# databases and their own mind directory. The owner's database is only READ, and its
# digest is compared before and after.
#
# Usage:
#   scripts/w52-bridge-both-directions-demo.sh
#
# Exit status: 0 when both crossings worked, 1 when they did not.
set -u

export JAVA_HOME="${JAVA_HOME:-/home/alexandr-narbaev/jdk25}"
export PATH="$JAVA_HOME/bin:$PATH"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

readonly SECRET="the secret of the W52 cross-process demonstration"
readonly READY_ATTEMPTS=120
readonly READY_INTERVAL_SECONDS=1

# The exit statuses of a shell that is interrupted or terminated. They are named
# because they are the shell's convention and not this script's choice.
readonly EXIT_INTERRUPTED=130
readonly EXIT_TERMINATED=143

# The loopback address. The two chains talk to each other over the loopback
# interface and to nothing else.
readonly LOOPBACK="127.0.0.1"

FAILURES=0
CHECKS=0
PRIV=""
CHAIN_A_PID=""
CHAIN_B_PID=""

pass() { CHECKS=$((CHECKS + 1)); printf '  OK    %s\n' "$1"; }
fail() { CHECKS=$((CHECKS + 1)); FAILURES=$((FAILURES + 1)); printf '  FAIL  %s\n' "$1"; }
note() { printf '  ..    %s\n' "$1"; }
head1() { printf '\n===== %s =====\n' "$1"; }

# shellcheck disable=SC2317  # the function is invoked by the EXIT trap below
cleanup() {
  for pid in "$CHAIN_A_PID" "$CHAIN_B_PID"; do
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null
      wait "$pid" 2>/dev/null
    fi
  done
  if [ -n "$PRIV" ] && [ -d "$PRIV" ]; then
    rm -rf "$PRIV"
    printf '\nthe private directory is removed: %s\n' "$PRIV"
  fi
}
trap cleanup EXIT
trap 'exit "$EXIT_INTERRUPTED"' INT
trap 'exit "$EXIT_TERMINATED"' TERM

PRIV="$(mktemp -d "${TMPDIR:-/tmp}/w52-bridge-demo-XXXXXX")" || exit 1
note "the private directory: $PRIV"

# ── the two ports ───────────────────────────────────────────────────────────
# Both ports are chosen by the operating system. Nothing of the owner's
# configuration is changed, and neither chain can be reached from outside the
# loopback interface.
free_port() {
  python3 -c 'import socket, sys; s = socket.socket(); s.bind((sys.argv[1], 0)); print(s.getsockname()[1]); s.close()' "$LOOPBACK"
}
CHAIN_A_PORT="$(free_port)"
CHAIN_B_PORT="$(free_port)"
CHAIN_A_URL="http://$LOOPBACK:$CHAIN_A_PORT"
CHAIN_B_URL="http://$LOOPBACK:$CHAIN_B_PORT"
note "chain A will listen on $CHAIN_A_URL"
note "chain B will listen on $CHAIN_B_URL"

# ── the owner's database is only read ───────────────────────────────────────
OWNER_DB="$ROOT/data/knowledge/workbench.sqlite"
owner_digest() {
  if [ -f "$OWNER_DB" ]; then
    sha256sum "$OWNER_DB" | cut -d' ' -f1
  else
    printf 'absent'
  fi
}
OWNER_BEFORE="$(owner_digest)"

# ── step 1: build both chains ───────────────────────────────────────────────
head1 "step 1 — build both chains"

if ! ./gradlew :matrix-core:quarkusBuild :matrix-core:jar :matrix-brain-runtime:classes \
    :matrix-api-gateway:installDist --offline -q >"$PRIV/build.log" 2>&1; then
  fail "the chains could not be built (see $PRIV/build.log)"
  tail -20 "$PRIV/build.log"
  exit 1
fi
pass "both chains are built"

CHAIN_A_JAR="$(find "$ROOT/matrix-core/build/quarkus-app" -maxdepth 1 -name '*.jar' | head -1)"
if [ -z "$CHAIN_A_JAR" ]; then
  fail "the packaged application was not found under matrix-core/build/quarkus-app"
  exit 1
fi

# The production brain loads the core through a jar, and the jar is named by
# the environment. The jar is READ from the repository and nothing is written
# there: the working directory of chain B is the private directory.
CORE_JAR="$(find "$ROOT/matrix-core/build/libs" -maxdepth 1 -name 'matrix-core-*.jar' | head -1)"
if [ -z "$CORE_JAR" ]; then
  fail "the jar of matrix-core was not found under matrix-core/build/libs"
  exit 1
fi
export MATRIX_CORE_JAR="$CORE_JAR"
note "the core jar chain B loads: $CORE_JAR"

CHAIN_B_BIN="$ROOT/matrix-api-gateway/build/install/matrix-api-gateway/bin/matrix-api-gateway"
if [ ! -x "$CHAIN_B_BIN" ]; then
  fail "the packaged gateway was not found under matrix-api-gateway/build/install"
  exit 1
fi
pass "the runnable distribution of chain B is present"

# ── step 2: start chain A ───────────────────────────────────────────────────
head1 "step 2 — start chain A"

(
  cd "$PRIV" || exit 1
  exec "$JAVA_HOME/bin/java" \
    -Dmatrix.bridge.secret="$SECRET" \
    -Dmatrix.bridge.workspace=bridged \
    -Dquarkus.http.port="$CHAIN_A_PORT" \
    -Dquarkus.http.host="$LOOPBACK" \
    -jar "$CHAIN_A_JAR"
) >"$PRIV/chain-a.log" 2>&1 &
CHAIN_A_PID=$!
note "chain A's pid: $CHAIN_A_PID"

# ── step 3: start chain B ───────────────────────────────────────────────────
head1 "step 3 — start chain B"

(
  cd "$PRIV" || exit 1
  MATRIX_MODE=production \
  MATRIX_MIND_DIR="$PRIV/mind" \
  MATRIX_BRIDGE_SECRET="$SECRET" \
  exec "$CHAIN_B_BIN" "$CHAIN_B_PORT"
) >"$PRIV/chain-b.log" 2>&1 &
CHAIN_B_PID=$!
note "chain B's pid: $CHAIN_B_PID"

READY_A=0
for _ in $(seq 1 "$READY_ATTEMPTS"); do
  if curl -fsS "$CHAIN_A_URL/v1/bridge/status" >/dev/null 2>&1; then
    READY_A=1
    break
  fi
  sleep "$READY_INTERVAL_SECONDS"
done
if [ "$READY_A" = "1" ]; then
  pass "chain A answers on $CHAIN_A_URL"
else
  fail "chain A did not answer within the timeout (see $PRIV/chain-a.log)"
  tail -20 "$PRIV/chain-a.log"
  exit 1
fi

READY_B=0
for _ in $(seq 1 "$READY_ATTEMPTS"); do
  if curl -fsS "$CHAIN_B_URL/health/live" >/dev/null 2>&1; then
    READY_B=1
    break
  fi
  sleep "$READY_INTERVAL_SECONDS"
done
if [ "$READY_B" = "1" ]; then
  pass "chain B answers on $CHAIN_B_URL"
else
  fail "chain B did not answer within the timeout (see $PRIV/chain-b.log)"
  tail -20 "$PRIV/chain-b.log"
  exit 1
fi

# The classpath the two publisher CLIs run on.
CP="$ROOT/matrix-core/build/classes/java/main:$ROOT/matrix-brain-runtime/build/classes/java/main"
JACKSON="$(find "$HOME/.gradle/caches/modules-2" -name 'jackson-databind-*.jar' | head -1)"
JACKSON="$JACKSON:$(find "$HOME/.gradle/caches/modules-2" -name 'jackson-core-*.jar' | head -1)"
JACKSON="$JACKSON:$(find "$HOME/.gradle/caches/modules-2" -name 'jackson-annotations-*.jar' | head -1)"
PUBLISHER_CP="$CP:$JACKSON"

# ── step 4: the FIRST direction ─────────────────────────────────────────────
head1 "step 4 — the first direction: chain B offers an observation to chain A"

"$JAVA_HOME/bin/java" -cp "$PUBLISHER_CP" io.matrix.brain.runtime.bridge.BridgePublisherCli \
  "$CHAIN_A_URL" "$SECRET" "the brain runtime reports a settled lamp rule" "req-w52-demo-1" \
  >"$PRIV/publisher-b2a.log" 2>&1
B2A_STATUS=$?
sed 's/^/        /' "$PRIV/publisher-b2a.log"
if [ "$B2A_STATUS" -eq 0 ] && grep -q "ACCEPTED" "$PRIV/publisher-b2a.log"; then
  pass "chain B's publisher was accepted by chain A"
else
  fail "chain B's publisher was refused (exit status $B2A_STATUS)"
fi

# ── step 5: the SECOND direction ────────────────────────────────────────────
head1 "step 5 — the second direction: chain A offers what it established to chain B"

"$JAVA_HOME/bin/java" -cp "$PUBLISHER_CP" io.matrix.bridge.KnowledgePublisherCli \
  "$CHAIN_B_URL" "$SECRET" "the lamp is lit when power and switch are both on" \
  "req-w52-demo-2" SUPPORTED >"$PRIV/publisher-a2b.log" 2>&1
A2B_STATUS=$?
sed 's/^/        /' "$PRIV/publisher-a2b.log"
if [ "$A2B_STATUS" -eq 0 ] && grep -q "status=ACCEPTED" "$PRIV/publisher-a2b.log"; then
  pass "chain A's publisher was accepted by chain B"
else
  fail "chain A's publisher was refused (exit status $A2B_STATUS)"
fi
if grep -q "outcome.status=SUCCEEDED" "$PRIV/publisher-a2b.log"; then
  pass "chain B's brain took the proposition"
else
  fail "chain B did not report that the brain took the proposition"
fi

BRAIN_SIZE_FIRST="$(sed -n 's/.*the brain.s memory holds \([0-9]*\) item.*/\1/p' \
  "$PRIV/publisher-a2b.log" | head -1)"
note "the brain's memory after the first offering: ${BRAIN_SIZE_FIRST:-unknown} item(s)"

# ── step 6: the brain's memory grows by exactly what was offered ────────────
head1 "step 6 — a second proposition, and the memory grows"

"$JAVA_HOME/bin/java" -cp "$PUBLISHER_CP" io.matrix.bridge.KnowledgePublisherCli \
  "$CHAIN_B_URL" "$SECRET" "the lamp is dark when power is off" \
  "req-w52-demo-3" SUPPORTED >"$PRIV/publisher-a2b-2.log" 2>&1
A2B_STATUS2=$?
sed 's/^/        /' "$PRIV/publisher-a2b-2.log"
BRAIN_SIZE_SECOND="$(sed -n 's/.*the brain.s memory holds \([0-9]*\) item.*/\1/p' \
  "$PRIV/publisher-a2b-2.log" | head -1)"
note "the brain's memory after the second offering: ${BRAIN_SIZE_SECOND:-unknown} item(s)"
if [ -n "$BRAIN_SIZE_FIRST" ] && [ -n "$BRAIN_SIZE_SECOND" ] \
    && [ "$BRAIN_SIZE_SECOND" -eq $((BRAIN_SIZE_FIRST + 1)) ]; then
  pass "the brain's memory grew by exactly one: $BRAIN_SIZE_FIRST became $BRAIN_SIZE_SECOND"
else
  fail "the brain's memory did not grow by one: $BRAIN_SIZE_FIRST then $BRAIN_SIZE_SECOND"
fi

# ── step 7: a forgery in the SECOND direction is refused ────────────────────
head1 "step 7 — a forgery in the second direction is refused"

"$JAVA_HOME/bin/java" -cp "$PUBLISHER_CP" io.matrix.bridge.KnowledgePublisherCli \
  "$CHAIN_B_URL" "a wrong secret" "a proposition nobody established" \
  "req-w52-demo-4" SUPPORTED >"$PRIV/publisher-forged.log" 2>&1
FORGED_STATUS=$?
sed 's/^/        /' "$PRIV/publisher-forged.log"
if [ "$FORGED_STATUS" -eq 1 ] && grep -q "status=REFUSED" "$PRIV/publisher-forged.log"; then
  pass "the forged call was refused, and the refusal names itself"
else
  fail "the forged call was not refused (exit status $FORGED_STATUS)"
fi

# ── step 8: the owner's database is untouched ───────────────────────────────
head1 "step 8 — the owner's epistemic database"

OWNER_AFTER="$(owner_digest)"
if [ "$OWNER_BEFORE" = "$OWNER_AFTER" ]; then
  pass "the owner's epistemic database is byte-identical before and after ($OWNER_AFTER)"
else
  fail "the owner's epistemic database changed: $OWNER_BEFORE became $OWNER_AFTER"
fi

# ── the summary ─────────────────────────────────────────────────────────────
head1 "the summary"
printf '%d checks, %d failed\n' "$CHECKS" "$FAILURES"
if [ "$FAILURES" -eq 0 ]; then
  echo "the cross-process demonstration: 0 — the two chains talked in BOTH directions"
  exit 0
fi
echo "the cross-process demonstration: $FAILURES — a crossing did not behave as the contract says"
exit 1
