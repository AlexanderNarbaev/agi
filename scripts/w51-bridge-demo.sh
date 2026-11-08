#!/usr/bin/env bash
#
# W51 — the cross-process demonstration of the bridge.
#
# THE QUESTION THIS SCRIPT ANSWERS
# "Do the two chains actually talk to each other, in two processes, over a network, with
#  separate databases and separate single writers?" The in-process demonstration proves
# that the components work; this script proves that the CROSSING works.
#
# WHAT IT DOES
#   1. starts chain A (the Quarkus application) with a shared secret configured;
#   2. waits until it answers;
#   3. runs chain B's publisher in a SEPARATE PROCESS, which signs an observation and
#      posts it to chain A's bridge endpoint;
#   4. reads chain A's own view of the crossing — the audit count and the status route;
#   5. shows that the owner's epistemic database is byte-identical before and after the
#      publication, because a report is not a fact and nothing was promoted;
#   6. stops chain A and removes everything it created.
#
# NOTHING OF THE OWNER IS TOUCHED
# The application runs in the private temporary directory of this script, with its own
# database. The owner's database is only READ, and its digest is compared before and after.
#
# Usage:
#   scripts/w51-bridge-demo.sh
#
# Exit status: 0 when the crossing worked, 1 when it did not.
set -u

export JAVA_HOME="${JAVA_HOME:-/home/alexandr-narbaev/jdk25}"
export PATH="$JAVA_HOME/bin:$PATH"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

readonly SECRET="the secret of the W51 cross-process demonstration"
readonly PUBLISHER_CLASS="io.matrix.brain.runtime.bridge.BridgePublisherCli"
readonly READY_ATTEMPTS=120
readonly READY_INTERVAL_SECONDS=1

# The exit statuses of a shell that is interrupted or terminated. They are named
# because they are the shell's convention and not this script's choice.
readonly EXIT_INTERRUPTED=130
readonly EXIT_TERMINATED=143

# The loopback address. Chain A and chain B's publisher talk to each other over
# the loopback interface and to nothing else.
readonly LOOPBACK="127.0.0.1"

FAILURES=0
CHECKS=0
PRIV=""
CHAIN_A_PID=""

pass() { CHECKS=$((CHECKS + 1)); printf '  OK    %s\n' "$1"; }
fail() { CHECKS=$((CHECKS + 1)); FAILURES=$((FAILURES + 1)); printf '  FAIL  %s\n' "$1"; }
note() { printf '  ..    %s\n' "$1"; }
head1() { printf '\n===== %s =====\n' "$1"; }

cleanup() {
  if [ -n "$CHAIN_A_PID" ] && kill -0 "$CHAIN_A_PID" 2>/dev/null; then
    kill "$CHAIN_A_PID" 2>/dev/null
    wait "$CHAIN_A_PID" 2>/dev/null
  fi
  if [ -n "$PRIV" ] && [ -d "$PRIV" ]; then
    rm -rf "$PRIV"
    printf '\nthe private directory is removed: %s\n' "$PRIV"
  fi
}
trap cleanup EXIT
trap "exit $EXIT_INTERRUPTED" INT
trap "exit $EXIT_TERMINATED" TERM

PRIV="$(mktemp -d "${TMPDIR:-/tmp}/w51-bridge-demo-XXXXXX")" || exit 1
note "the private directory: $PRIV"

# ── the two ports ───────────────────────────────────────────────────────────
# Chain A's port is chosen by the operating system, and chain B's publisher is told
# where to find it. Nothing of the owner's configuration is changed.
CHAIN_A_PORT="$(python3 -c 'import socket, sys; s = socket.socket(); s.bind((sys.argv[1], 0)); print(s.getsockname()[1]); s.close()' "$LOOPBACK")"
CHAIN_A_URL="http://$LOOPBACK:$CHAIN_A_PORT"
note "chain A will listen on $CHAIN_A_URL"

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

# ── step 1: build chain A ───────────────────────────────────────────────────
head1 "step 1 — build chain A and chain B's publisher"

if ! "$JAVA_HOME/bin/javac" -nowarn \
    -cp "$ROOT/matrix-brain-runtime/build/classes/java/main:$ROOT/matrix-core/build/classes/java/main" \
    -d "$PRIV/publisher" \
    "$ROOT/matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/bridge/BridgePublisherCli.java" \
    2>"$PRIV/javac.log"; then
  fail "chain B's publisher could not be compiled (see $PRIV/javac.log)"
  cat "$PRIV/javac.log"
  exit 1
fi
pass "chain B's publisher is compiled into the private directory"

if ! ./gradlew :matrix-core:quarkusBuild :matrix-brain-runtime:classes --offline -q \
    >"$PRIV/build.log" 2>&1; then
  fail "chain A could not be built (see $PRIV/build.log)"
  tail -20 "$PRIV/build.log"
  exit 1
fi
pass "chain A is built"

# ── step 2: start chain A ───────────────────────────────────────────────────
head1 "step 2 — start chain A"

CHAIN_A_JAR="$(find "$ROOT/matrix-core/build/quarkus-app" -maxdepth 1 -name '*.jar' | head -1)"
if [ -z "$CHAIN_A_JAR" ]; then
  fail "the packaged application was not found under matrix-core/build/quarkus-app"
  exit 1
fi

# The application runs in the PRIVATE directory with its own database: the owner's
# database is not opened by it at all.
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

READY=0
for _ in $(seq 1 "$READY_ATTEMPTS"); do
  if curl -fsS "$CHAIN_A_URL/q/health/ready" >/dev/null 2>&1 \
      || curl -fsS "$CHAIN_A_URL/v1/bridge/status" >/dev/null 2>&1; then
    READY=1
    break
  fi
  sleep "$READY_INTERVAL_SECONDS"
done
if [ "$READY" = "1" ]; then
  pass "chain A answers on $CHAIN_A_URL"
else
  fail "chain A did not answer within the timeout (see $PRIV/chain-a.log)"
  tail -30 "$PRIV/chain-a.log"
  exit 1
fi

note "chain A says about itself:"
curl -fsS "$CHAIN_A_URL/v1/bridge/status" 2>/dev/null | sed 's/^/        /' || true

# ── step 3: chain B publishes, in its own process ───────────────────────────
head1 "step 3 — chain B publishes an observation, in a separate process"

PUBLISHER_CP="$PRIV/publisher:$ROOT/matrix-brain-runtime/build/classes/java/main:$ROOT/matrix-core/build/classes/java/main"
CP="$(find "$HOME/.gradle/caches/modules-2" -name 'jackson-databind-*.jar' | head -1)"
CP="$CP:$(find "$HOME/.gradle/caches/modules-2" -name 'jackson-core-*.jar' | head -1)"
CP="$CP:$(find "$HOME/.gradle/caches/modules-2" -name 'jackson-annotations-*.jar' | head -1)"

"$JAVA_HOME/bin/java" -cp "$PUBLISHER_CP:$CP" "$PUBLISHER_CLASS" \
  "$CHAIN_A_URL" "$SECRET" "the brain runtime reports a settled lamp rule" "req-w51-demo-1" \
  >"$PRIV/publisher.log" 2>&1
PUBLISHER_STATUS=$?
sed 's/^/        /' "$PRIV/publisher.log"
if [ "$PUBLISHER_STATUS" -eq 0 ] && grep -q "ACCEPTED" "$PRIV/publisher.log"; then
  pass "chain B's publisher was accepted by chain A (exit status 0)"
else
  fail "chain B's publisher was refused (exit status $PUBLISHER_STATUS)"
fi

# ── step 4: a true retry of the same request ────────────────────────────────
head1 "step 4 — the same request id is delivered again, with the same payload"

"$JAVA_HOME/bin/java" -cp "$PUBLISHER_CP:$CP" "$PUBLISHER_CLASS" \
  "$CHAIN_A_URL" "$SECRET" "the brain runtime reports a settled lamp rule" "req-w51-demo-1" \
  >"$PRIV/publisher2.log" 2>&1
PUBLISHER_STATUS2=$?
sed 's/^/        /' "$PRIV/publisher2.log"
if [ "$PUBLISHER_STATUS2" -eq 0 ]; then
  pass "the retry was answered with the original outcome (exit status 0)"
else
  fail "the retry was refused (exit status $PUBLISHER_STATUS2)"
fi

# ── step 5: the same request id with a DIFFERENT payload ────────────────────
head1 "step 5 — the same request id is reused for a different message"

"$JAVA_HOME/bin/java" -cp "$PUBLISHER_CP:$CP" "$PUBLISHER_CLASS" \
  "$CHAIN_A_URL" "$SECRET" "a different report under the same request id" "req-w51-demo-1" \
  >"$PRIV/publisher4.log" 2>&1
PUBLISHER_STATUS4=$?
sed 's/^/        /' "$PRIV/publisher4.log"
if [ "$PUBLISHER_STATUS4" -eq 1 ] && grep -q "REQUEST_ID_CONFLICT\|different payload" "$PRIV/publisher4.log"; then
  pass "the conflicting reuse was refused, and the refusal names the conflict"
else
  fail "the conflicting reuse was not refused as a conflict (exit status $PUBLISHER_STATUS4)"
fi

# ── step 6: a wrong secret is refused ───────────────────────────────────────
head1 "step 6 — a call with the wrong secret is refused"

"$JAVA_HOME/bin/java" -cp "$PUBLISHER_CP:$CP" "$PUBLISHER_CLASS" \
  "$CHAIN_A_URL" "a wrong secret" "a forged observation" "req-w51-demo-2" \
  >"$PRIV/publisher3.log" 2>&1
PUBLISHER_STATUS3=$?
sed 's/^/        /' "$PRIV/publisher3.log"
if [ "$PUBLISHER_STATUS3" -eq 1 ] && grep -q "REFUSED" "$PRIV/publisher3.log"; then
  pass "the forged call was refused, and the refusal names itself (exit status 1)"
else
  fail "the forged call was not refused (exit status $PUBLISHER_STATUS3)"
  sed 's/^/        /' "$PRIV/publisher3.log"
fi

# ── step 7: what chain A recorded, and what it did not touch ────────────────
head1 "step 7 — chain A's own record of the crossing, and the effect it had"

STATUS="$(curl -fsS "$CHAIN_A_URL/v1/bridge/status" 2>/dev/null)"
printf '%s\n' "$STATUS" | sed 's/^/        /'
if printf '%s' "$STATUS" | grep -q '"auditRecords"'; then
  pass "chain A holds the audit of the crossing"
else
  fail "chain A did not report the audit of the crossing"
fi

# The effect: the bridged workspace must hold exactly ONE claim, because three
# deliveries of one request id are one effect and one conflict.
EFFECT_COUNT="$(python3 - "$PRIV" <<'PYTHON'
import os, sqlite3, sys
root = sys.argv[1]
path = os.path.join(root, "data", "knowledge", "workbench.sqlite")
if not os.path.exists(path):
    print("absent")
else:
    connection = sqlite3.connect(path)
    try:
        rows = connection.execute(
            "SELECT COUNT(*) FROM claim WHERE workspace_id = 'bridged'").fetchone()
        print(rows[0])
    except sqlite3.Error as error:
        print("unreadable: %s" % error)
    finally:
        connection.close()
PYTHON
)"
note "the claims in the bridged workspace of chain A: $EFFECT_COUNT"
if [ "$EFFECT_COUNT" = "1" ]; then
  pass "the three deliveries of one request id produced exactly ONE effect"
else
  fail "the deliveries produced $EFFECT_COUNT effect(s) and not one"
fi

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
  echo "the cross-process demonstration: 0 — chain B published to chain A and chain A refused a forgery"
  exit 0
fi
echo "the cross-process demonstration: $FAILURES — the crossing did not behave as the contract says"
exit 1
