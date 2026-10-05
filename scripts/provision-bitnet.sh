#!/usr/bin/env bash
# provision-bitnet.sh — RECON-W33.1 / BitNet-Download
#
# Downloads the REAL microsoft/bitnet-b1.58-2B-4T checkpoint to a PERSISTENT
# in-repo location, so the BitNet test suite can EXECUTE instead of skipping.
#
# Why this exists (a measured failure, not a hypothetical):
#   W32.34 D1 pointed the BitNet fixture at /tmp/hf_cache/... and guarded the 11
#   test classes with an ENV-BLOCKED skip. /tmp is NOT persistent. By W33.1 that
#   path no longer existed at all, so the guard had silently become permanent and
#   "28 failures -> 32 skips" was a skip that could never become a pass. The
#   operator directive calls this out directly: persistent folder, NOT /tmp.
#
# Verified at authoring time:
#   microsoft/bitnet-b1.58-2B-4T  gated=False  (no license click-through needed)
#   model.safetensors            1178.6 MB
#
# Idempotent: re-running skips files already present at the right size.
set -euo pipefail

REPO="microsoft/bitnet-b1.58-2B-4T"
DEST="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/data/models/bitnet-checkpoint"
# sha256 is recorded by the downloader into a sidecar; see verify step below.
FILES=(config.json generation_config.json model.safetensors special_tokens_map.json tokenizer.json tokenizer_config.json)

echo "==> provisioning $REPO into $DEST"

# Token resolution, in order of preference. The HF CLI reads HF_TOKEN itself, but
# huggingface_hub wants it in the environment for direct API calls, so export it.
# 1. an explicitly exported HF_TOKEN / HUGGING_FACE_HUB_TOKEN
# 2. the file the HF CLI writes on `hf auth login`
# 3. nothing — the model is ungated, so anonymous download also works
if [[ -z "${HF_TOKEN:-}" && -z "${HUGGING_FACE_HUB_TOKEN:-}" ]]; then
  if [[ -f "${HF_HOME:-$HOME/.cache/huggingface}/token" ]]; then
    HF_TOKEN="$(tr -d '\n' < "${HF_HOME:-$HOME/.cache/huggingface}/token")"
    export HF_TOKEN
    echo "==> using token from ~/.cache/huggingface/token"
  else
    echo "==> no token found; attempting anonymous download (repo is ungated)"
  fi
fi

mkdir -p "$DEST"

# `hf download` is the current CLI; huggingface-cli is deprecated and prints a
# warning. Prefer `hf`, fall back to the python API if neither is on PATH.
if command -v hf >/dev/null 2>&1; then
  hf download "$REPO" --local-dir "$DEST"
elif command -v huggingface-cli >/dev/null 2>&1; then
  huggingface-cli download "$REPO" --local-dir "$DEST"
else
  python3 - "$REPO" "$DEST" <<'PY'
import os, sys
from huggingface_hub import snapshot_download
repo, dest = sys.argv[1], sys.argv[2]
tok = os.environ.get("HF_TOKEN") or os.environ.get("HUGGING_FACE_HUB_TOKEN")
if not tok:
    p = os.path.join(os.environ.get("HF_HOME", os.path.expanduser("~/.cache/huggingface")), "token")
    tok = open(p).read().strip() if os.path.exists(p) else None
print(snapshot_download(repo_id=repo, local_dir=dest, token=tok,
                         allow_patterns=["*.json", "*.safetensors"]))
PY
fi

echo "==> verifying"
missing=0
for f in "${FILES[@]}"; do
  if [[ ! -s "$DEST/$f" ]]; then
    echo "    MISSING or empty: $f" >&2
    missing=1
  else
    printf '    ok  %-28s %s\n' "$f" "$(du -h "$DEST/$f" | cut -f1)"
  fi
done
[[ $missing -eq 0 ]] || { echo "==> FAILED: incomplete checkpoint" >&2; exit 1; }

# The fixture asserts this file is a real, non-trivial safetensors payload; a
# truncated download would satisfy -s but not the test, so fail loudly here.
sz=$(stat -c%s "$DEST/model.safetensors")
echo "    model.safetensors = $sz bytes"
if (( sz < 1000000000 )); then
  echo "==> FAILED: model.safetensors is $sz bytes, expected >1e9. Truncated download." >&2
  exit 1
fi

# Record the digest so a corrupted-but-plausible download is detectable later.
sha256sum "$DEST/model.safetensors" | tee "$DEST/model.safetensors.sha256"

echo "==> DONE. $DEST now holds the real checkpoint."
echo "    Run the suite with BitNet tests enabled:"
echo "      ./gradlew :matrix-core:test --tests 'io.matrix.research.BitNet*'"
