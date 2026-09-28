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
rsync -a --quiet --exclude='.git' --exclude='models' --exclude='build' --exclude='bin' --exclude='.gradle' --exclude='data/mind/benchmarks' --exclude='data/mind/*.ndjson' --exclude='data/mind/mind.sqlite' --exclude='matrix-*/build' --exclude='.codegraph' --exclude='docs-v2/research/cache' --exclude='.opencode' --exclude='.minecraft' --exclude='node_modules' --exclude='*.log' "$SRC/" "$TARGET/"

cd "$TARGET"
echo "  Done. Repo copied."
echo ""
echo "Step 2: Build all required modules..."
./gradlew :matrix-api-gateway:jar :matrix-brain-runtime:jar :matrix-core:jar --no-daemon --console=plain >/tmp/matrix-build.log 2>&1 || {
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
bash scripts/start-mind.sh > /tmp/matrix-start.log 2>&1 || {
    echo "Gateway failed to start:"
    tail -20 /tmp/matrix-start.log
    exit 1
}
echo "  Done. Gateway up."

echo ""
echo "Step 5: Query the mind..."
TOKEN=$(curl -s -X POST http://localhost:8765/v1/auth/login -H 'Content-Type: application/json' -d '{"email":"pro@test.com"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')
RESP=$(curl -s -X POST http://localhost:8765/v1/analyze -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"input":"What is 2+3?"}')
echo "  /v1/analyze → $RESP"

echo ""
echo "=== FRESH-CLONE SMOKE TEST: PASS ==="
echo "Mind at $TARGET is awake and answering."
echo ""
echo "Repro commands (5 total):"
echo "  1. ./gradlew :matrix-api-gateway:jar :matrix-brain-runtime:jar :matrix-core:jar"
echo "  2. ./gradlew :matrix-api-gateway:writeRuntimeClasspath"
echo "  3. (prepend matrix-{api-gateway,brain-runtime}/build/classes/java/main to runtime-classpath.txt)"
echo "  4. bash scripts/start-mind.sh"
echo "  5. TOKEN=\$(curl -s -X POST http://localhost:8765/v1/auth/login ...)"
echo "     curl -X POST http://localhost:8765/v1/analyze -d '{\"input\":\"What is 2+3?\"}'"
