# MATRIX RESEARCH-ONLY
# Pull DistilBERT SST-2 from HuggingFace and export to ONNX for
# M-A.T.R.I.X.2 (real-LLM distillation). Output: a small ONNX file
# (~22 MB) at models/external/distilbert-sst2/model.onnx.
#
# Run: python3 scripts/download_distilbert_onnx.py
import os
import sys
from pathlib import Path

# canonical small model — ~22 MB on disk; M-A.T.R.I.X.2 used the tiny
# variant; M-A.T.R.I.X.3 uses the real (non-tiny) DistilBERT for better
# fidelity. Pass the model ID as $MODEL_ID env var to override.
import os
MODEL_ID = os.environ.get(
    "MATRIX_MODEL_ID",
    "distilbert-base-uncased-finetuned-sst-2-english",
)
OUT_DIR_NAME = os.environ.get("MATRIX_OUT_DIR", "distilbert-base-sst2")
OUT_DIR = Path("models/external") / OUT_DIR_NAME
OUT_DIR.mkdir(parents=True, exist_ok=True)


def main() -> int:
    print(f"[M-A.T.R.I.X.2] pulling {MODEL_ID} → {OUT_DIR}")
    try:
        from transformers import AutoTokenizer, AutoModelForSequenceClassification
    except ImportError:
        print("FAIL: transformers not installed")
        return 1

    tok = AutoTokenizer.from_pretrained(MODEL_ID, cache_dir=str(OUT_DIR / ".cache"))
    model = AutoModelForSequenceClassification.from_pretrained(
        MODEL_ID, cache_dir=str(OUT_DIR / ".cache"),
        ignore_mismatched_sizes=True)
    tok.save_pretrained(str(OUT_DIR))
    model.save_pretrained(str(OUT_DIR))
    print(f"[M-A.T.R.I.X.2] HF model + tokenizer saved")

    # export to ONNX via torch.onnx
    print("[M-A.T.R.I.X.2] exporting to ONNX…")
    import torch
    dummy = tok("This movie was excellent", return_tensors="pt", padding=True, truncation=True, max_length=128)
    onnx_path = OUT_DIR / "model.onnx"
    torch.onnx.export(
        model,
        (dummy["input_ids"], dummy["attention_mask"]),
        str(onnx_path),
        input_names=["input_ids", "attention_mask"],
        output_names=["logits"],
        dynamic_axes={
            "input_ids": {0: "batch", 1: "seq"},
            "attention_mask": {0: "batch", 1: "seq"},
            "logits": {0: "batch"},
        },
        opset_version=14,
        do_constant_folding=True,
    )
    size_mb = onnx_path.stat().st_size / (1024 * 1024)
    print(f"[M-A.T.R.I.X.2] ONNX saved: {onnx_path} ({size_mb:.1f} MB)")

    # validate via onnx
    try:
        import onnx
        onnx.checker.check_model(onnx_path, full_check=True)
        print("[M-A.T.R.I.X.2] ONNX model structurally valid")
    except ImportError:
        print("[M-A.T.R.I.X.2] (onnx checker not available — skipping validation)")

    return 0


if __name__ == "__main__":
    sys.exit(main())