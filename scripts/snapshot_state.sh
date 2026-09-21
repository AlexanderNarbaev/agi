#!/bin/bash
# RUN 320 — State archive snapshot (Wave H.2)
#
# Bundles the running-system state into a single tarball:
#   - chain_state.json (chain metadata)
#   - lm_head_weights.bin (LM head weights, 176 MB)
#   - hierarchical_memory.jsonl (LTM persistence)
#   - conversations/*.ndjson (chat history)
# Plus a manifest.json describing the snapshot and a SHA-256.
#
# Usage: ./scripts/snapshot_state.sh [output-path]
# Default output: data/state-snapshot-YYYY-MM-DD.tar.gz

set -euo pipefail

# Resolve absolute output path BEFORE changing into WORKDIR
DATE=$(date -u +%Y-%m-%d)
DEFAULT_OUTPUT="data/state-snapshot-${DATE}.tar.gz"
OUTPUT="${1:-$DEFAULT_OUTPUT}"

# Make absolute
mkdir -p "$(dirname "$OUTPUT")"
OUTPUT="$(cd "$(dirname "$OUTPUT")" && pwd)/$(basename "$OUTPUT")"

# Stage the snapshot contents in a temp dir
WORKDIR=$(mktemp -d)
trap "rm -rf $WORKDIR" EXIT
STAGE="$WORKDIR/snapshot"
mkdir -p "$STAGE/data"

# 1) chain metadata
if [[ -f data/chain_state.json ]]; then
  cp data/chain_state.json "$STAGE/data/"
fi

# 2) LM head weights
if [[ -f data/lm_head_weights.bin ]]; then
  cp data/lm_head_weights.bin "$STAGE/data/"
fi

# 3) LTM persistence
if [[ -f data/hierarchical_memory.jsonl ]]; then
  cp data/hierarchical_memory.jsonl "$STAGE/data/"
fi

# 4) conversation history
if [[ -d data/conversations ]]; then
  cp -r data/conversations "$STAGE/data/"
fi

# 5) Build manifest
cat > "$STAGE/manifest.json" <<EOF
{
  "snapshotAt": "$(date -u --iso-8601=seconds)",
  "matrixRoot": "$(git rev-parse HEAD 2>/dev/null || echo unknown)",
  "matrixBranch": "$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)",
  "gitStatus": "$(git status --short | head -5 | tr '\n' ';')",
  "contents": {
    "chain_state": "data/chain_state.json",
    "lm_head_weights": "data/lm_head_weights.bin",
    "hierarchical_memory": "data/hierarchical_memory.jsonl",
    "conversations": "data/conversations/"
  },
  "excludes": [
    "models/onnx/* (re-derivable from safetensors)",
    "models/training_data/* (corpora, regenerable)",
    "data/chain_feature_cache.bin (cached only, regenerable)",
    "build artifacts"
  ]
}
EOF

# 6) Build tarball (cd into WORKDIR so archive paths are clean)
( cd "$WORKDIR" && tar czf "$OUTPUT" snapshot/ )

# 7) SHA-256 + size
SHA=$(sha256sum "$OUTPUT" | awk '{print $1}')
SIZE=$(stat -c %s "$OUTPUT")
echo ""
echo "=========================================="
echo "  STATE ARCHIVE CREATED"
echo "=========================================="
echo "  Path:  $OUTPUT"
echo "  Size:  $SIZE bytes ($(du -h "$OUTPUT" | awk '{print $1}'))"
echo "  SHA:   $SHA"
echo "  Manifest:"
cat "$STAGE/manifest.json"
echo ""
echo "=========================================="
echo "  Archive contents:"
tar tzf "$OUTPUT" | head -20
echo "  ..."
echo "=========================================="
