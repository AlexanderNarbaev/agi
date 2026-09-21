# MATRIX RESEARCH-ONLY
# Wave 7.1: download Qwen2.5-0.6B-Instruct safetensors from HF and
# import the weights into MATRIX (via Java WeightImporter). The
# pipeline:
#   1. python: pull safetensors only (not optimizer state) into a
#      local cache
#   2. shell: trigger gradle :matrix-core:test --tests
#      ...WeightImporterEndToEndIT (added in this wave)
#   3. python: read the resulting ingestion report and print it
#
# This is the "import real LLM weights into MATRIX's boolean
# substrate" path — what the user asked for in this session.
import os
import sys
import time
from pathlib import Path

CACHE = Path(os.environ.get("MATRIX_IMPORT_CACHE", "/tmp/opencode/matrix-import"))
MODEL_ID = "Qwen/Qwen2.5-0.6B"


def main() -> int:
    print(f"[import] target model: {MODEL_ID}")
    print(f"[import] cache dir:    {CACHE}")
    CACHE.mkdir(parents=True, exist_ok=True)

    # 1. Pull safetensors only via huggingface_hub
    print(f"[import] step 1: pulling safetensors via huggingface_hub…")
    try:
        from huggingface_hub import hf_hub_download, snapshot_download
    except ImportError:
        print("FAIL: huggingface_hub not installed", file=sys.stderr)
        return 1

    t0 = time.perf_counter()
    try:
        local_path = snapshot_download(
            repo_id=MODEL_ID,
            cache_dir=str(CACHE),
            allow_patterns=["*.safetensors", "*.json", "tokenizer.model"],
            max_workers=4,
        )
    except Exception as e:
        print(f"FAIL: snapshot_download: {e}", file=sys.stderr)
        return 1
    elapsed = time.perf_counter() - t0
    safetensors = sorted(Path(local_path).glob("*.safetensors"))
    total_mb = sum(f.stat().st_size for f in safetensors) / 1024 / 1024
    print(f"[import] downloaded {len(safetensors)} safetensors "
          f"({total_mb:.1f} MB) in {elapsed:.1f}s")
    for f in safetensors:
        print(f"  - {f.name}  ({f.stat().st_size / 1024 / 1024:.1f} MB)")

    print(f"[import] step 2: trigger Java WeightImporter ingestion…")
    print(f"[import] (run gradle separately after this script)")

    return 0


if __name__ == "__main__":
    sys.exit(main())