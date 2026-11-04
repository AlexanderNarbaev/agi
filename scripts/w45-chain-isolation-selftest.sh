#!/usr/bin/env bash
#
# W45.2 — the NEGATIVE CONTROL of the chain isolation gate.
#
# THE QUESTION THIS SCRIPT ANSWERS
# "Can the gate go red?" A gate that cannot fail is not a gate, and a gate
# that has never been seen failing is a claim. This script breaks the
# isolation ON PURPOSE, in a private copy of the compiled classes, and runs the
# real gate against the broken tree.
#
# THE TWO CONTROLS
#   C1  a class of chain B that CALLS the epistemic store. The gate must
#       report that the store is reachable, and it must exit non-zero.
#   C2  a class of chain B that DECLARES a route under /v1/cognitive. The
#       gate must report that a route of chain A belongs to chain B, and it
#       must exit non-zero.
#
# NOTHING IS TOUCHED IN THE REPOSITORY
# The broken classes are compiled into a COPY of the compiled classes, in the
# private temporary directory of this script. The repository's own class files
# are read, never written.
#
# Usage:
#   scripts/w45-chain-isolation-selftest.sh
#
# Exit status: 0 when the gate detected both controls, 1 when it did not.
set -u

export JAVA_HOME="${JAVA_HOME:-/home/alexandr-narbaev/jdk25}"
export PATH="$JAVA_HOME/bin:$PATH"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

readonly GATE="$ROOT/scripts/w45-chain-isolation-check.sh"
readonly JAVAC="$JAVA_HOME/bin/javac"
readonly MODULES="matrix-core matrix-brain-runtime matrix-api-gateway matrix-audit"

# The library directory of the chain B distribution: it holds the JAX-RS
# annotations the route control is compiled against.
readonly DISTRIBUTION_LIB="$ROOT/matrix-api-gateway/build/install/matrix-api-gateway/lib"

# The exit statuses of a shell that is interrupted or terminated.
readonly EXIT_INTERRUPTED=130
readonly EXIT_TERMINATED=143

FAILURES=0
CHECKS=0
PRIV=""

pass() { CHECKS=$((CHECKS + 1)); printf '  OK    %s\n' "$1"; }
fail() { CHECKS=$((CHECKS + 1)); FAILURES=$((FAILURES + 1)); printf '  FAIL  %s\n' "$1"; }
note() { printf '  ..    %s\n' "$1"; }
head1() { printf '\n===== %s =====\n' "$1"; }

cleanup() {
  if [ -n "$PRIV" ] && [ -d "$PRIV" ]; then
    rm -rf "$PRIV"
    printf '\nthe private directory is removed: %s\n' "$PRIV"
  fi
}
trap cleanup EXIT
trap "exit $EXIT_INTERRUPTED" INT
trap "exit $EXIT_TERMINATED" TERM

if [ ! -x "$JAVAC" ]; then
  echo "the compiler is absent: $JAVAC — the negative control cannot run"
  exit 1
fi

PRIV="$(mktemp -d "${TMPDIR:-/tmp}/w45-chain-selftest-XXXXXX")" || exit 1
note "the private directory: $PRIV"

# The tree the control breaks: a copy of the compiled classes.
BROKEN="$PRIV/broken"
mkdir -p "$BROKEN"
module=""
for module in $MODULES; do
  mkdir -p "$BROKEN/$module/build/classes/java/main"
  cp -r "$ROOT/$module/build/classes/java/main/." \
    "$BROKEN/$module/build/classes/java/main/" 2>/dev/null
done
note "the copy of the compiled classes: $BROKEN"

# ── control 1 — a chain B class that calls the epistemic store ───────────────
head1 "control 1 — chain B calls the epistemic store"

mkdir -p "$PRIV/probe1/io/matrix/api"
cat >"$PRIV/probe1/io/matrix/api/MinimalHttpServer.java" <<'JAVA'
package io.matrix.api;

import java.nio.file.Path;

/**
 * The NEGATIVE CONTROL of the chain isolation gate.
 *
 * <p>This class stands where the real entry point of chain B stands, and it
 * calls the epistemic store. A gate that cannot see this is not a gate.</p>
 */
public final class MinimalHttpServer {

    public MinimalHttpServer(int port) {
    }

    public void start() {
        io.matrix.knowledge.KnowledgeStore store =
                new io.matrix.knowledge.KnowledgeStore(Path.of("probe.sqlite"));
        store.claims("probe");
    }
}
JAVA

CP="$ROOT/matrix-core/build/classes/java/main"
if ! "$JAVAC" -nowarn -cp "$CP" -d \
    "$BROKEN/matrix-api-gateway/build/classes/java/main" \
    "$PRIV/probe1/io/matrix/api/MinimalHttpServer.java" 2>"$PRIV/probe1/javac.log"; then
  fail "the control class could not be compiled (see $PRIV/probe1/javac.log)"
  cat "$PRIV/probe1/javac.log"
  exit 1
fi
pass "the control class is compiled into the COPY of the classes"

head1 "the gate, pointed at the broken tree"
W45_CLASSES_SOURCE="$BROKEN" W45_SKIP_BUILD=1 W45_SKIP_RUNTIME=1 \
  bash "$GATE" >"$PRIV/gate1.log" 2>&1
GATE_STATUS=$?
if [ "$GATE_STATUS" -ne 0 ]; then
  pass "the gate went red on the broken tree (exit status $GATE_STATUS)"
else
  fail "the gate stayed green on a tree where chain B calls the epistemic store"
fi
grep -E "^  (OK|FAIL)" "$PRIV/gate1.log" | sed 's/^/        /'
if grep -q "FAIL.*epistemic store" "$PRIV/gate1.log"; then
  pass "the gate named the violation: the epistemic store became reachable"
else
  fail "the gate did not name the violation of the epistemic store"
  grep -E "^  FAIL" "$PRIV/gate1.log" | sed 's/^/        /'
fi

# ── control 2 — a chain B class that declares a chain A route ────────────────
head1 "control 2 — chain B declares a route of chain A"

rm -f "$BROKEN/matrix-api-gateway/build/classes/java/main/io/matrix/api/MinimalHttpServer.class"
mkdir -p "$PRIV/probe2/io/matrix/api"
cat >"$PRIV/probe2/io/matrix/api/BridgeResource.java" <<'JAVA'
package io.matrix.api;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

/**
 * The NEGATIVE CONTROL of the route census of the chain isolation gate: a
 * class of chain B that declares a route of the chain A surface.
 */
@Path("/v1/cognitive")
public final class BridgeResource {

    @POST
    @Path("/observe")
    public String observe() {
        return "{}";
    }
}
JAVA

if ! "$JAVAC" -nowarn -cp "$DISTRIBUTION_LIB/*" -d \
    "$BROKEN/matrix-api-gateway/build/classes/java/main" \
    "$PRIV/probe2/io/matrix/api/BridgeResource.java" 2>"$PRIV/probe2/javac.log"; then
  fail "the route control could not be compiled (see $PRIV/probe2/javac.log)"
  cat "$PRIV/probe2/javac.log"
  exit 1
fi
pass "the route control is compiled into the COPY of the classes"

W45_CLASSES_SOURCE="$BROKEN" W45_SKIP_BUILD=1 W45_SKIP_RUNTIME=1 \
  bash "$GATE" >"$PRIV/gate2.log" 2>&1
GATE_STATUS=$?
if [ "$GATE_STATUS" -ne 0 ]; then
  pass "the gate went red on the tree with the chain A route (exit status $GATE_STATUS)"
else
  fail "the gate stayed green on a tree where chain B declares a chain A route"
fi
if grep -q "FAIL.*route(s) of chain B belong to the chain A surface" "$PRIV/gate2.log"; then
  pass "the gate named the violation: a route of chain A belongs to chain B"
else
  fail "the gate did not name the violation of the route census"
  grep -E "^  FAIL" "$PRIV/gate2.log" | sed 's/^/        /'
fi

# ── the summary ─────────────────────────────────────────────────────────────
head1 "the summary"
printf '%d checks, %d failed\n' "$CHECKS" "$FAILURES"
if [ "$FAILURES" -eq 0 ]; then
  echo "the negative control: 0 — the gate goes red when the isolation is broken"
  exit 0
fi
echo "the negative control: $FAILURES — the gate did not detect the breakage"
exit 1
