#!/usr/bin/env bash
# RECON-W18 — Fresh-clone smoke test.
# Simulates first-time user experience: build, launch, query.
# Usage: scripts/fresh-clone-smoke.sh [target-dir]
set -euo pipefail

TARGET="${1:-$(pwd)/data/smoke/$(date +%s)}"
echo "Smoke target: $TARGET"

# RECON-W25 — retention policy.
#
# Root cause of an 8.8 GB disk leak: this script left a full repository copy
# behind on EVERY run, and `data/smoke-old/` accumulated several of them. The
# copies are provably regenerable (they contain only build output and tracked
# source), so they are disposable — but only by an explicit, opt-in mechanism.
#
#  - default: keep at most KEEP=2 most recent smoke directories, prune the rest
#  - --keep-all : disable pruning (for debugging)
#  - --prune-only: prune and exit, no smoke run
#
# A trap removes the working copy when the run FAILS, so a broken smoke does not
# leave a 2 GB directory behind either.
KEEP="${MATRIX_SMOKE_KEEP:-2}"
PRUNE_ONLY=0
KEEP_ALL=0
for arg in "$@"; do
  case "$arg" in
    --keep-all)  KEEP_ALL=1 ;;
    --prune-only) PRUNE_ONLY=1 ;;
  esac
done

SMOKE_ROOT="$(pwd)/data/smoke"

prune_old_smokes() {
  [ -d "$SMOKE_ROOT" ] || return 0
  # Newest-first by mtime; keep the first $KEEP, remove the rest.
  local keep=0 victim
  for victim in $(ls -1dt "$SMOKE_ROOT"/*/ 2>/dev/null); do
    keep=$((keep + 1))
    if [ "$keep" -gt "$KEEP" ]; then
      echo "  pruning old smoke dir: $victim"
      # The copy is a build artefact, never cognitive data: it excludes
      # data/mind/*.ndjson, data/mind/benchmarks and .git at rsync time.
      rm -rf "$victim"
    fi
  done
}

if [ "$KEEP_ALL" -eq 0 ]; then
  echo "Step 0: pruning smoke dirs beyond KEEP=$KEEP in $SMOKE_ROOT"
  prune_old_smokes
fi
[ "$PRUNE_ONLY" -eq 1 ] && { echo "prune-only done"; exit 0; }

# On failure, drop the partially built copy so it cannot accumulate.
cleanup_on_failure() {
  local rc=$?
  if [ $rc -ne 0 ] && [ -d "$TARGET" ] && [ "$KEEP_ALL" -eq 0 ]; then
    echo "smoke failed (rc=$rc); removing partial copy $TARGET"
    rm -rf "$TARGET"
  fi
  return $rc
}
trap cleanup_on_failure EXIT

# Step 1: Find repo root
cd "$(dirname "$0")/.."
SRC="$(pwd)"
echo ""
echo "Step 1: Copy repo to $TARGET..."
mkdir -p "$TARGET"
# Use rsync for speed excluding .git build dirs etc.
# RECON-W27 — exclusions whose absence caused a real failure, verified by running
# this script (which the previous wave had not done).
#  * data/smoke*  : without it the clone recursively copies PREVIOUS smoke dirs,
#    nesting a smoke run inside itself; the failure log showed data/smoke/...
#    repeating eight levels deep.
#  * .venv / venv : data/smoke-old holds an 8.6 GB Python virtualenv containing
#    NVIDIA CUDA shared libraries (libcublasLt.so.13). Copying it filled the disk
#    and aborted the clone with ENOSPC. That — not build output — was the true
#    source of the 8.8 GB previously blamed on build artifacts.
EXCLUDE_ARGS="--exclude=.git --exclude=models --exclude=build --exclude=bin --exclude=.gradle --exclude=data/mind/benchmarks --exclude=data/mind/*.ndjson --exclude=data/mind/mind.sqlite --exclude=matrix-*/build --exclude=.codegraph --exclude=docs-v2/research/cache --exclude=.opencode --exclude=.minecraft --exclude=node_modules --exclude=*.log --exclude=data/smoke* --exclude=.venv --exclude=venv --exclude=__pycache__"
# shellcheck disable=SC2086  # deliberate word-splitting of the exclude list
rsync -a --quiet $EXCLUDE_ARGS "$SRC/" "$TARGET/"
cd "$TARGET"
echo "  Done. Repo copied."
echo ""
echo "Step 2: Build all required modules..."
# RECON-W27 FIX: build `classes`, not just `jar`. start-mind.sh assembles its
# runtime classpath from */build/classes/java/main, and the `jar` task alone
# does not guarantee those directories exist in a clean clone — the gateway
# died with ClassNotFoundException: io.matrix.api.MinimalHttpServer.
./gradlew :matrix-core:classes :matrix-brain-runtime:classes :matrix-api-gateway:classes --no-daemon --console=plain >/tmp/matrix-build.log 2>&1 || {
    echo "Build failed; tail of /tmp/matrix-build.log:"
    tail -20 /tmp/matrix-build.log
    exit 1
}
echo "  Done. Jars built."

echo ""
echo "Step 3: Generate runtime classpath..."
./gradlew :matrix-api-gateway:writeRuntimeClasspath --no-daemon --console=plain >/tmp/matrix-cp.log 2>&1 || {
    echo "WriteRuntimeClasspath failed; tail of /tmp/matrix-cp.log:"
    tail -20 /tmp/matrix-cp.log
    exit 1
}
# Prepend classes dirs (Gradle task doesn't include them by default)
{
  echo "matrix-api-gateway/build/classes/java/main"
  echo "matrix-brain-runtime/build/classes/java/main"
  cat matrix-api-gateway/build/runtime-classpath.txt | grep -v '^$' | tr '\n' ':' | sed 's/:$//'
  echo ""
} > /tmp/matrix-cp-merged.txt
mv /tmp/matrix-cp-merged.txt matrix-api-gateway/build/runtime-classpath.txt
echo "  Done. Classpath ready."

echo ""
echo "Step 4: Launch the gateway..."
# RECON-W27: honour MATRIX_PORT and MATRIX_PID_FILE so a clean-room smoke can run
# on its own port and its own pid file WITHOUT displacing the live gateway the
# operator is using. Previously this script always bound 8765 and always wrote
# .gateway.pid, so running it would take the live system down.
SMOKE_PORT="${MATRIX_PORT:-8765}"
MATRIX_PORT="$SMOKE_PORT" \
MATRIX_PID_FILE="${MATRIX_PID_FILE:-$PWD/.smoke-gateway.pid}" \
  bash scripts/start-mind.sh > /tmp/matrix-start.log 2>&1 || {
    echo "Gateway failed to start:"
    tail -20 /tmp/matrix-start.log
    exit 1
}
echo "  Done. Gateway up."

echo ""
echo "Step 5: Query the mind..."
# RECON-W27 FIX — start-mind.sh backgrounds the JVM and returns immediately, so
# step 5 raced the listener. Two of the three attempts failed here with an
# empty body from /v1/auth/login, which the JSON parse then reported as
# "Expecting value: line 1 column 1". Poll /health/live until it answers.
echo "  Waiting for $SMOKE_PORT to accept requests..."
READY=0
for _ in $(seq 1 60); do
  if curl -s -m 2 "http://localhost:$SMOKE_PORT/health/live" | grep -q '"status":"UP"'; then
    READY=1; break
  fi
  sleep 1
done
if [ "$READY" -ne 1 ]; then
  echo "  gateway did not become ready on port $SMOKE_PORT; last 20 log lines:"
  tail -20 "$TARGET/data/mind/gateway.log" 2>/dev/null || true
  exit 1
fi
echo "  Gateway ready."

TOKEN=$(curl -s -X POST "http://localhost:$SMOKE_PORT/v1/auth/login" -H 'Content-Type: application/json' -d '{"email":"pro@test.com"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')
RESP=$(curl -s -X POST "http://localhost:$SMOKE_PORT/v1/analyze" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"input":"What is 2+3?"}')
echo "  /v1/analyze → $RESP"

echo ""
echo "=== FRESH-CLONE SMOKE TEST: PASS ==="
echo "Mind at $TARGET is awake and answering."
echo ""
echo "Repro commands (5 total):"
echo "  1. ./gradlew :matrix-api-gateway:jar :matrix-brain-runtime:jar :matrix-core:jar"
echo "  2. ./gradlew :matrix-api-gateway:writeRuntimeClasspath"
echo "  3. (no manual classpath surgery: start-mind.sh normalises the newline-separated
       runtime-classpath.txt itself - see the B-1 root-cause comment there)"
echo "  4. MATRIX_PORT=$SMOKE_PORT bash scripts/start-mind.sh"
echo "  5. TOKEN=\$(curl -s -X POST http://localhost:8765/v1/auth/login ...)"
echo "     curl -X POST http://localhost:8765/v1/analyze -d '{\"input\":\"What is 2+3?\"}'"
