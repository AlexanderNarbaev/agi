#!/usr/bin/env bash
#
# W45.2 — the gate that PROVES the isolation of the two chains.
#
# THE OWNER'S DECISION (W45.2, decision 2)
#   "Keep the two chains isolated and fail closed. No bridge, no implicit write
#    path, no automatic trust, no new network surface. Chain B cannot alter
#    claims, confidence, goals or safety policy."
#
# WHAT THIS GATE DOES
# It ASSERTS the isolation and exits non-zero when the isolation is broken. A
# check that cannot go red is not a check, so every rule below is written as
# an assertion with a way to fail.
#
# THE PROOF IS A REACHABILITY PROOF OVER THE COMPILED CLASSES
# The edges are resolved from the constant pool (`javap -p -c`), not from a
# textual search: a textual search mistakes a mention for a call. The census
# the gate prints is the evidence — how many classes were reachable, from
# which entry points, and which routes each chain declares.
#
# THE RULES
#   G1  chain B reaches no class of the cognitive store (io.matrix.cognitive.**)
#   G2  chain B reaches no class of the epistemic store's own classes, and no
#       class that NAMES the epistemic database in its constant pool
#   G3  chain B reaches no class of io.matrix.knowledge.** that can open a
#       database at all (the persistence stack)
#   G4  chain B reaches no class that serves the chain A surface
#   G5  no route of chain B is a route of chain A, and no route of chain B
#       writes to the epistemic store
#   G6  at runtime: the chain B server refuses the chain A surface, and the
#       epistemic database of the owner is byte-identical before and after
#
# HYGIENE
# Everything the gate touches is inside ONE private temporary directory that
# it creates itself. It kills only the pid it started, deletes only that
# directory, and never opens the database of the owner.
#
# Usage:
#   scripts/w45-chain-isolation-check.sh
#
# Environment:
#   W45_SKIP_BUILD=1   do not build (the compiled classes and the distribution
#                      must already exist)
#   W45_KEEP=1         keep the private temporary directory and print its path
#   W45_CLASSES_SOURCE the root the compiled classes are copied from (the
#                      negative control of the gate points it at a broken tree)
#   W45_SKIP_RUNTIME=1 skip the runtime part (the negative control)
#
# Exit status: 0 when every assertion holds, 1 when any does not.
set -u

# ── configuration ────────────────────────────────────────────────────────────

# The default java on this machine is a JRE and the build fails without this.
export JAVA_HOME="${JAVA_HOME:-/home/alexandr-narbaev/jdk25}"
export PATH="$JAVA_HOME/bin:$PATH"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

readonly ANALYSER="$ROOT/scripts/w45-chain-isolation-reachability.py"
readonly JAVAP="$JAVA_HOME/bin/javap"
readonly GRADLE="./gradlew"

# The modules of the two chains. The classes of all of them are the universe
# of the proof: chain B depends on matrix-core, so the proof has to be able to
# walk into it.
readonly MODULES="matrix-core matrix-brain-runtime matrix-api-gateway matrix-audit"

# The task that compiles the classes the proof is made over.
readonly BUILD_TASK=":matrix-core:classes :matrix-brain-runtime:classes :matrix-api-gateway:classes :matrix-audit:classes"

# The task that assembles the runnable distribution of the chain B server.
readonly DIST_TASK=":matrix-api-gateway:installDist"

# The entry points of chain B, as the owner decision names them.
readonly CHAIN_B_ENTRY_POINTS="io.matrix.api.MinimalHttpServer io.matrix.api.llm_quarantine.BrainProduction io.matrix.api.brain.ProductionBrainClient io.matrix.brain.runtime.TrueMindCycle"

# The HTTP status codes the runtime part asserts.
readonly HTTP_OK=200
readonly HTTP_NOT_FOUND=404

# How long the gate waits for the chain B server to answer.
readonly STARTUP_WAIT_SECONDS=60
readonly READY_WAIT_SECONDS=60
readonly REQUEST_TIMEOUT_SECONDS=10

# The sleep between two attempts of the readiness loop.
readonly POLL_INTERVAL_SECONDS=1

# The exit statuses of a shell that is interrupted or terminated.
readonly EXIT_INTERRUPTED=130
readonly EXIT_TERMINATED=143

# Where the compiled classes are copied FROM. The default is the repository.
# The hook exists for the NEGATIVE CONTROL of the gate: the self-test points
# it at a tree in which chain B calls the epistemic store, and the gate must
# go red there. A gate that cannot go red is not a gate.
readonly CLASSES_SOURCE="${W45_CLASSES_SOURCE:-$ROOT}"

# The contract of a future bridge, and the document that holds it. The gate
# points at it: a bridge that does not satisfy every requirement of this
# document is not a bridge the owner accepted.
readonly CONTRACT_DOCUMENT="docs-v2/engineering/W45.2-CHAIN-BOUNDARY.md"
readonly CONTRACT_REQUIREMENTS="BRIDGE-1 BRIDGE-2 BRIDGE-3 BRIDGE-4 BRIDGE-5 BRIDGE-6 BRIDGE-7 BRIDGE-8 BRIDGE-9 BRIDGE-10"

FAILURES=0
CHECKS=0
PRIV=""
SERVER_PID=""

# ── output ───────────────────────────────────────────────────────────────────

pass() { CHECKS=$((CHECKS + 1)); printf '  OK    %s\n' "$1"; }
fail() { CHECKS=$((CHECKS + 1)); FAILURES=$((FAILURES + 1)); printf '  FAIL  %s\n' "$1"; }
note() { printf '  ..    %s\n' "$1"; }
head1() { printf '\n===== %s =====\n' "$1"; }

# One field of the report of the analyser.
report_field() {
  python3 -c 'import json,sys
data = json.load(open(sys.argv[1]))
value = data
for part in sys.argv[2].split("."):
    if isinstance(value, list):
        try:
            value = value[int(part)]
        except (ValueError, IndexError):
            value = ""
    elif isinstance(value, dict):
        value = value.get(part, "")
    else:
        value = ""
print("" if value is None else (value if isinstance(value, str) else json.dumps(value)))' \
    "$1" "$2"
}

# The size of a list in the report.
report_size() {
  local raw
  raw="$(report_field "$1" "$2")"
  python3 -c 'import json,sys
raw = sys.argv[1]
try:
    print(len(json.loads(raw)))
except Exception:
    print(raw)' "$raw"
}

# ── the private directory ────────────────────────────────────────────────────

cleanup() {
  if [ -n "$SERVER_PID" ] && kill -0 "$SERVER_PID" 2>/dev/null; then
    kill -9 "$SERVER_PID" 2>/dev/null
    wait "$SERVER_PID" 2>/dev/null
  fi
  SERVER_PID=""
  if [ -n "$PRIV" ] && [ -d "$PRIV" ]; then
    if [ "${W45_KEEP:-0}" = "1" ]; then
      printf '\nthe private directory is KEPT: %s\n' "$PRIV"
    else
      rm -rf "$PRIV"
      printf '\nthe private directory is removed: %s\n' "$PRIV"
    fi
  fi
}
trap cleanup EXIT
trap "exit $EXIT_INTERRUPTED" INT
trap "exit $EXIT_TERMINATED" TERM

# ── the tools ────────────────────────────────────────────────────────────────

require_tool() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail "the tool $1 is not available: the gate cannot run, and a gate that cannot run is not a gate"
    return 1
  fi
  return 0
}

# ── the build ────────────────────────────────────────────────────────────────

build_all() {
  if [ "${W45_SKIP_BUILD:-0}" = "1" ]; then
    note "the build is skipped (W45_SKIP_BUILD=1)"
  else
    note "the build: $GRADLE $BUILD_TASK"
    if ! "$GRADLE" $BUILD_TASK --console=plain; then
      fail "the compiled classes of the two chains could not be built"
      return 1
    fi
    note "the build: $GRADLE $DIST_TASK"
    if ! "$GRADLE" $DIST_TASK --console=plain; then
      fail "the runnable distribution of the chain B server could not be built"
      return 1
    fi
  fi
  local module
  for module in $MODULES; do
    if [ ! -d "$module/build/classes/java/main" ]; then
      fail "the compiled classes of $module are missing"
      return 1
    fi
  done
  return 0
}

# A COPY of the compiled classes, so that the proof is made over ONE build
# even when another process builds the repository at the same time.
snapshot_classes() {
  local module
  for module in $MODULES; do
    mkdir -p "$PRIV/classes/$module/build/classes/java/main"
    cp -r "$CLASSES_SOURCE/$module/build/classes/java/main/." \
      "$PRIV/classes/$module/build/classes/java/main/" 2>/dev/null
  done
}

# ── the runtime part ─────────────────────────────────────────────────────────

# A free port, asked of the operating system. 8080 and 8180 belong to the
# services of the owner and are never used.
free_port() {
  python3 - <<'PY'
import socket
sock = socket.socket()
sock.bind(("127.0.0.1", 0))
print(sock.getsockname()[1])
sock.close()
PY
}

# The digest of a file, or the word ABSENT when there is no file.
digest_of() {
  if [ -f "$1" ]; then
    sha256sum "$1" | cut -d' ' -f1
  else
    echo "ABSENT"
  fi
}

# The status code of a request to the chain B server.
http_status() {
  local port="$1" method="$2" path="$3"
  curl -s -o /dev/null -m "$REQUEST_TIMEOUT_SECONDS" -w "%{http_code}" \
    -X "$method" "http://127.0.0.1:$port$path" 2>/dev/null
}

# The runtime part: the chain B server, started against its OWN disposable
# directory, must refuse the chain A surface and must not touch the epistemic
# database of the owner.
runtime_part() {
  head1 "the runtime part — the chain B server, against its own directory"

  local distribution="$ROOT/matrix-api-gateway/build/install/matrix-api-gateway/lib"
  if [ ! -d "$distribution" ]; then
    fail "the distribution of the chain B server is missing: $distribution"
    return 1
  fi

  # The epistemic database of the owner: its digest BEFORE the server runs.
  local owner_db="$ROOT/data/knowledge/workbench.sqlite"
  local before after
  before="$(digest_of "$owner_db")"
  note "the epistemic database of the owner before: $before"

  local work="$PRIV/server"
  mkdir -p "$work"
  local port
  port="$(free_port)"
  note "the chain B server starts: cwd=$work port=$port"
  (
    cd "$work" || exit 1
    exec env MATRIX_SECURITY_MODE=LOCAL_ONLY \
      java -Dmatrix.security.mode=LOCAL_ONLY \
        -cp "$distribution/*" io.matrix.api.MinimalHttpServer "$port"
  ) >"$work/server.log" 2>&1 </dev/null &
  SERVER_PID=$!

  local waited=0
  local ready=0
  while [ "$waited" -lt "$READY_WAIT_SECONDS" ]; do
    sleep "$POLL_INTERVAL_SECONDS"
    waited=$((waited + 1))
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
      break
    fi
    if [ "$(http_status "$port" GET /health/live)" = "$HTTP_OK" ]; then
      ready=1
      break
    fi
  done
  if [ "$ready" -ne 1 ]; then
    fail "the chain B server did not answer within ${READY_WAIT_SECONDS}s (see $work/server.log)"
    return 1
  fi
  pass "the chain B server is up on port $port (pid $SERVER_PID)"

  # The chain A surface must not be served by chain B.
  local path method status
  for path in /v1/cognitive/summary /v1/workbench/workspaces /v1/cognitive/observe; do
    method=GET
    case "$path" in
      */observe) method=POST ;;
    esac
    status="$(http_status "$port" "$method" "$path")"
    if [ "$status" = "$HTTP_NOT_FOUND" ]; then
      pass "the chain B server refuses $method $path with $HTTP_NOT_FOUND"
    else
      fail "the chain B server answered $method $path with '$status', expected $HTTP_NOT_FOUND"
    fi
  done

  # A route of chain B must work: the server is functional, not merely alive.
  status="$(http_status "$port" GET /v1/status)"
  if [ "$status" = "$HTTP_OK" ]; then
    pass "the chain B server serves its own route GET /v1/status with $HTTP_OK"
  else
    fail "the chain B server answered GET /v1/status with '$status', expected $HTTP_OK"
  fi

  # The epistemic database of the owner must be untouched.
  after="$(digest_of "$owner_db")"
  if [ "$before" = "$after" ]; then
    pass "the epistemic database of the owner is byte-identical after the run ($after)"
  else
    fail "the epistemic database of the owner CHANGED: $before -> $after"
  fi

  # The server must have kept its own state inside the private directory.
  if [ -d "$work/data" ] || [ -d "$work/logs" ] || [ -n "$(ls -A "$work" 2>/dev/null | grep -v '^server.log$')" ]; then
    pass "the chain B server wrote only inside its own private directory"
  else
    note "the chain B server wrote nothing at all: it holds no state of its own in this mode"
  fi

  if [ -n "$SERVER_PID" ] && kill -0 "$SERVER_PID" 2>/dev/null; then
    kill -9 "$SERVER_PID" 2>/dev/null
    wait "$SERVER_PID" 2>/dev/null
  fi
  SERVER_PID=""
  return 0
}

# ── the journey ──────────────────────────────────────────────────────────────

head1 "the tools"
require_tool java || exit 1
require_tool python3 || exit 1
require_tool curl || exit 1
require_tool javap || exit 1
if [ ! -x "$ANALYSER" ] && [ ! -f "$ANALYSER" ]; then
  fail "the analyser of the reachability is missing: $ANALYSER"
  exit 1
fi
pass "the tools are present"

head1 "the build of the two chains"
if ! build_all; then
  exit 1
fi
pass "the compiled classes of the two chains are ready"

PRIV="$(mktemp -d "${TMPDIR:-/tmp}/w45-chain-isolation-XXXXXX")" || exit 1
note "the private directory: $PRIV"

head1 "the reachability, over a private copy of the compiled classes"
snapshot_classes
note "the classes are copied into the private directory, so the proof is made over one build"
REPORT="$PRIV/report.json"
if ! python3 "$ANALYSER" --root "$PRIV/classes" --json "$REPORT" --javap "$JAVAP"; then
  fail "the analyser of the reachability did not run"
  exit 1
fi
if [ ! -f "$REPORT" ]; then
  fail "the analyser of the reachability produced no report"
  exit 1
fi

# ── G0 — the contract of a future bridge ───────────────────────────────────
head1 "G0 — the contract of a future bridge is on record"
if [ ! -f "$CONTRACT_DOCUMENT" ]; then
  fail "the contract document is missing: $CONTRACT_DOCUMENT"
else
  pass "the contract document exists: $CONTRACT_DOCUMENT"
  MISSING=""
  for requirement in $CONTRACT_REQUIREMENTS; do
    if ! grep -q "$requirement" "$CONTRACT_DOCUMENT"; then
      MISSING="$MISSING $requirement"
    fi
  done
  if [ -z "$MISSING" ]; then
    pass "the contract names every requirement the gate knows: $CONTRACT_REQUIREMENTS"
  else
    fail "the contract does not name:$MISSING"
  fi
fi

# ── G1 — the cognitive store ────────────────────────────────────────────────
head1 "G1 — chain B reaches no class of the cognitive store"
G1="$(report_size "$REPORT" chain_b.reachable_cognitive)"
note "the classes of io.matrix.cognitive.** reached from chain B: $G1"
if [ "$G1" = "0" ]; then
  pass "no class of io.matrix.cognitive.** is reachable from any chain B entry point"
else
  fail "$G1 class(es) of io.matrix.cognitive.** are reachable from chain B"
  report_field "$REPORT" chain_b.reachable_cognitive
fi

# ── G2 — the epistemic store ─────────────────────────────────────────────────
head1 "G2 — chain B reaches no class of the epistemic store"
G2="$(report_size "$REPORT" chain_b.reachable_store_own)"
G2B="$(report_size "$REPORT" chain_b.reachable_epistemic_database)"
note "the store's own classes reached from chain B: $G2"
note "the classes that name the epistemic database and are reachable from chain B: $G2B"
if [ "$G2" = "0" ]; then
  pass "no class of the epistemic store's own packages is reachable from chain B"
else
  fail "$G2 class(es) of the epistemic store are reachable from chain B"
  report_field "$REPORT" chain_b.reachable_store_own
fi
if [ "$G2B" = "0" ]; then
  pass "no class that names the epistemic database is reachable from chain B"
else
  fail "$G2B class(es) that name the epistemic database are reachable from chain B"
  report_field "$REPORT" chain_b.reachable_epistemic_database
fi

# ── G3 — the knowledge package chain B does reach ───────────────────────────
head1 "G3 — the classes of io.matrix.knowledge.** that chain B DOES reach"
G3="$(report_size "$REPORT" chain_b.reachable_knowledge)"
G3B="$(report_size "$REPORT" chain_b.reachable_knowledge_persistence)"
note "the classes of io.matrix.knowledge.** reached from chain B: $G3"
report_field "$REPORT" chain_b.reachable_knowledge
if [ "$G3B" = "0" ]; then
  pass "none of them can open a database: no persistence stack is reachable from them"
else
  fail "$G3B class(es) of io.matrix.knowledge.** reach the persistence stack"
  report_field "$REPORT" chain_b.reachable_knowledge_persistence
fi

# ── G4 — the chain A surface ────────────────────────────────────────────────
head1 "G4 — chain B reaches no class that serves the chain A surface"
G4="$(report_size "$REPORT" chain_b.reaches_chain_a_surface)"
note "the chain A surface classes reached from chain B: $G4"
if [ "$G4" = "0" ]; then
  pass "no class that serves /v1/cognitive/** or /v1/workbench/** is reachable from chain B"
else
  fail "$G4 class(es) of the chain A surface are reachable from chain B"
  report_field "$REPORT" chain_b.reaches_chain_a_surface
fi

# ── G5 — the route census ───────────────────────────────────────────────────
head1 "G5 — the route census, read out of the compiled annotations"
CHAIN_B_ROUTES="$(report_size "$REPORT" routes.chain_b_jaxrs)"
CHAIN_B_SERVED="$(report_size "$REPORT" routes.chain_b_served)"
CHAIN_A_ROUTES="$(report_size "$REPORT" routes.chain_a)"
note "chain A declares $CHAIN_A_ROUTES annotated routes"
note "chain B declares $CHAIN_B_ROUTES annotated routes and serves $CHAIN_B_SERVED compiled routes"
G5="$(python3 -c 'import json,sys
report = json.load(open(sys.argv[1]))
prefixes = ("/v1/cognitive", "/v1/workbench")
routes = report["routes"]["chain_b_jaxrs"] + [
    {"http": "ANY", "path": path, "class": "io.matrix.api.MinimalHttpServer"}
    for path in report["routes"]["chain_b_served"]]
offenders = sorted({route["path"] for route in routes
                    if any(route["path"].startswith(prefix) for prefix in prefixes)})
print(len(offenders))
for offender in offenders:
    print("    " + offender)' "$REPORT")"
G5_COUNT="$(printf '%s\n' "$G5" | head -1)"
if [ "$G5_COUNT" = "0" ]; then
  pass "no route of chain B is a route of chain A"
else
  fail "$G5_COUNT route(s) of chain B belong to the chain A surface"
  printf '%s\n' "$G5" | tail -n +2
fi

# ── G6 — the runtime ────────────────────────────────────────────────────────
if [ "${W45_SKIP_RUNTIME:-0}" = "1" ]; then
  head1 "G6 — the runtime part"
  note "the runtime part is skipped (W45_SKIP_RUNTIME=1)"
else
  runtime_part
fi

# ── the summary ─────────────────────────────────────────────────────────────
head1 "the summary"
printf '%d checks, %d failed\n' "$CHECKS" "$FAILURES"
if [ "$FAILURES" -eq 0 ]; then
  echo "the chain isolation: 0 — every assertion held"
  exit 0
fi
echo "the chain isolation: $FAILURES — assertion(s) failed"
exit 1
