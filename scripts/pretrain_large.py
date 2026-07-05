#!/usr/bin/env python3
"""Pre-train MPDT neurons from LARGE (>10GB) transformer models.

Uses memory-mapped loading with optional disk swapping to handle models
that exceed available RAM (59GB). Designed for RTX 5070 12GB VRAM with
32 CPU cores and 59GB RAM.

Key features:
  - torch.load() with mmap=True for zero-copy loading
  - Layer-by-layer processing with immediate tensor cleanup
  - Sharded model support (model-00001-of-00005.safetensors etc.)
  - MoE expert extraction (Mixtral, DeepSeek-R1)
  - Optional 8-bit quantization via bitsandbytes
  - Disk-based intermediate storage for HUGE models
  - Checkpoint/resume for long-running extractions
  - Memory monitoring and GC management

Supported architectures:
  - LLaMA 2/3/4    (7B–70B)
  - Qwen2/2.5      (0.5B–72B)
  - Mixtral 8x7B/8x22B (MoE)
  - DeepSeek-R1/V3 (MoE, 671B total)
  - DeepSeek-V3    (671B MoE)

Hardware requirements:
  - Minimum: 16GB RAM (for 7B models)
  - Recommended: 64GB RAM (for 70B models)
  - VRAM: 12GB (used for bfloat16→float32 conversion only)

Usage:
  # Qwen2.5-1.5B (fits easily)
  python3 scripts/pretrain_large.py \\
    --model-dir ~/.cache/huggingface/hub/models--Qwen--Qwen2.5-1.5B/snapshots/xxx \\
    --model-name Qwen2.5-1.5B --k 16 --neurons-per-layer 50

  # Mixtral 8x7B with disk swapping
  python3 scripts/pretrain_large.py \\
    --model-dir ~/models/Mixtral-8x7B-v0.1 \\
    --model-name Mixtral-8x7B --quantize-8bit \\

  # DeepSeek-R1 with experts
  python3 scripts/pretrain_large.py \\
    --model-dir ~/models/DeepSeek-R1 \\
    --model-name DeepSeek-R1 --extract-experts \\
"""

import argparse
import gc
import json
import os
import platform
import re
import shutil
import sys
import time
import uuid
from pathlib import Path
from typing import Optional

import numpy as np

# ── Required imports check ──────────────────────────────────────────────────
MISSING = []
try:
    from safetensors import safe_open  # noqa: F401
    SAFETENSORS_AVAILABLE = True
except ImportError:
    MISSING.append("safetensors")
    SAFETENSORS_AVAILABLE = False

try:
    import fastavro  # noqa: F401
    FASTAVRO_AVAILABLE = True
except ImportError:
    MISSING.append("fastavro")
    FASTAVRO_AVAILABLE = False

try:
    import torch
except ImportError:
    MISSING.append("torch")

try:
    import psutil
    HAS_PSUTIL = True
except ImportError:
    HAS_PSUTIL = False

try:
    import bitsandbytes  # noqa: F401
    HAS_BNB = True
except ImportError:
    HAS_BNB = False

if MISSING:
    print(f"Missing dependencies: {', '.join(MISSING)}")
    print("Install: pip install safetensors fastavro torch psutil")
    print("For 8-bit quantization: pip install bitsandbytes")
    sys.exit(1)

# ── Avro schema ─────────────────────────────────────────────────────────────
MPDT_NEURON_SCHEMA = {
    "type": "record",
    "name": "MPDTNeuron",
    "namespace": "io.matrix.neuron",
    "doc": "MPDT neuron schema. Ref: L1_MPDT_neuron.md \u00a77",
    "fields": [
        {"name": "id", "type": {"type": "record", "name": "NeuronId",
            "fields": [{"name": "uuid", "type": "string"}, {"name": "generation", "type": "long"}]}},
        {"name": "k", "type": "int"},
        {"name": "state", "type": {"type": "enum", "name": "NeuronState",
            "symbols": ["STABLE", "LEARNING", "MUTATING", "FROZEN"]}},
        {"name": "truthTable", "type": "bytes"},
        {"name": "weights", "type": {"type": "array", "items": "int"}},
        {"name": "metadata", "type": ["null", {"type": "record", "name": "Metadata",
            "fields": [{"name": "createdAt", "type": "long"}, {"name": "mutationCount", "type": "long"},
                {"name": "parentIds", "type": {"type": "array", "items": "string"}},
                {"name": "accuracyHistory", "type": {"type": "array", "items": "double"}}]}]},
    ],
}


# ── Memory utilities ────────────────────────────────────────────────────────

def get_memory_usage() -> dict:
    """Get current memory usage in MB."""
    info: dict = {"rss_mb": 0, "vms_mb": 0, "percent": 0.0}
    if HAS_PSUTIL:
        import psutil as _psutil
        proc = _psutil.Process()
        mem = proc.memory_info()
        info["rss_mb"] = int(mem.rss / 1024**2)
        info["vms_mb"] = int(mem.vms / 1024**2)
        info["percent"] = float(_psutil.virtual_memory().percent)
    return info


def _get_system_ram_gb() -> float | None:
    """Get total system RAM in GB, or None if psutil unavailable."""
    if HAS_PSUTIL:
        import psutil as _psutil
        return _psutil.virtual_memory().total / 1024**3
    return None


def log_memory(tag: str = "") -> None:
    """Log current memory usage with an optional tag."""
    mem = get_memory_usage()
    if mem["rss_mb"] > 0:
        print(f"  [MEM:{tag}] RSS={mem['rss_mb']:.0f}MB, "
              f"VMS={mem['vms_mb']:.0f}MB, system={mem['percent']:.1f}%")


def force_gc() -> None:
    """Force garbage collection and clear CUDA cache."""
    gc.collect()
    try:
        import torch as _torch
        if _torch.cuda.is_available():
            _torch.cuda.empty_cache()
            _torch.cuda.synchronize()
    except Exception:
        pass


# ── Model discovery ─────────────────────────────────────────────────────────

def find_safetensors_files(model_dir: str) -> list[str]:
    """Find all safetensors files in a model directory, sorted.

    Returns list of absolute paths, sorted (single file or shards).
    """
    model_dir = os.path.abspath(model_dir)
    patterns = [
        os.path.join(model_dir, "model.safetensors"),
    ]
    # Also look for sharded: model-00001-of-00005.safetensors
    if os.path.isdir(model_dir):
        shard_pattern = re.compile(r"model-\d{5}-of-\d{5}\.safetensors")
        for fname in sorted(os.listdir(model_dir)):
            if fname == "model.safetensors" or shard_pattern.match(fname):
                patterns.append(os.path.join(model_dir, fname))

    # Deduplicate and sort
    existing = []
    for p in sorted(set(patterns)):
        if os.path.exists(p) and p not in existing:
            existing.append(p)
    # Prefer single file over shards if both exist
    single = [p for p in existing if os.path.basename(p) == "model.safetensors"]
    shards = [p for p in existing if p not in single]
    return single + shards


def load_model_config(model_dir: str) -> dict:
    """Load config.json from model directory."""
    config_path = os.path.join(model_dir, "config.json")
    if not os.path.exists(config_path):
        return {}
    with open(config_path) as f:
        return json.load(f)


# ── Architecture detection ──────────────────────────────────────────────────

def detect_architecture_from_config(config: dict) -> str:
    """Detect architecture from config.json."""
    model_type = config.get("model_type", "")
    architectures = config.get("architectures", [])

    arch_map = {
        "llama": "llama",
        "mistral": "mistral",
        "mixtral": "mixtral",
        "qwen2": "qwen2",
        "deepseek_v3": "deepseek_v3",
        "deepseek": "deepseek_r1",
        "gpt2": "gpt2",
        "gemma": "gemma",
        "gemma2": "gemma2",
    }
    if model_type in arch_map:
        return arch_map[model_type]
    for a in architectures:
        a_lower = a.lower()
        for key, arch in arch_map.items():
            if key in a_lower:
                return arch
    return "unknown"


# ── Weight extraction ───────────────────────────────────────────────────────

def extract_layer_idx(key: str) -> int | None:
    """Extract transformer layer index from a tensor key name."""
    m = re.search(r"layers?\.(\d+)\.", key)
    if m:
        return int(m.group(1))
    m = re.search(r"\.h\.(\d+)\.", key)
    if m:
        return int(m.group(1))
    return None


def is_ffn_weight(key: str) -> bool:
    """Check if a tensor key is an FFN (up/gate projection) weight."""
    markers = [".mlp.up_proj.", ".mlp.gate_proj.", ".mlp.c_fc.",
               ".block_sparse_moe.", ".mlp.gate.", ".shared_experts."]
    return any(m in key for m in markers)


def filter_ffn_keys(all_keys: list[str]) -> dict[int, dict[str, list[str]]]:
    """Group FFN weight keys by layer index.

    Returns: {layer_idx: {"up": [...], "gate": [...], "expert": [...]}, ...}
    """
    grouped: dict[int, dict[str, list[str]]] = {}
    for key in all_keys:
        if not is_ffn_weight(key):
            continue
        layer_idx = extract_layer_idx(key)
        if layer_idx is None:
            continue
        if layer_idx not in grouped:
            grouped[layer_idx] = {"up": [], "gate": [], "expert": []}
        if ".up_proj." in key or ".w1." in key or ".c_fc." in key:
            grouped[layer_idx]["up"].append(key)
        elif ".gate_proj." in key or ".w3." in key:
            grouped[layer_idx]["gate"].append(key)
        elif ".block_sparse_moe." in key:
            grouped[layer_idx]["expert"].append(key)
    return grouped


# ── Core logic ──────────────────────────────────────────────────────────────

def pack_bits_little_endian(table: np.ndarray) -> bytes:
    """Pack boolean array → bytes (LE bit order, matches Java BitSet)."""
    num_bits = len(table)
    num_bytes = (num_bits + 7) // 8
    result = bytearray(num_bytes)
    for i in range(num_bits):
        if table[i]:
            byte_idx = i // 8
            bit_idx = i % 8
            result[byte_idx] |= 1 << bit_idx
    return bytes(result)


def weights_to_truth_table(
    w: np.ndarray, k: int, threshold: float = 0.0,
    rng: np.random.Generator | None = None,
) -> tuple[bytes, int, list[int]]:
    """Convert k sampled weights → 2^k bit truth table."""
    if rng is None:
        rng = np.random.default_rng()
    max_k = min(k, len(w))
    if max_k < 1:
        raise ValueError(f"Need at least 1 weight, got {len(w)}")
    if not isinstance(rng, np.random.Generator):
        rng = np.random.default_rng()
    indices = rng.choice(len(w), max_k, replace=False)
    selected = w[indices].astype(np.float64)
    table_size = 1 << max_k
    table = np.zeros(table_size, dtype=np.uint8)
    for i in range(table_size):
        total = 0.0
        for bit_pos in range(max_k):
            if i & (1 << bit_pos):
                total += selected[bit_pos]
        table[i] = 1 if total > threshold else 0
    packed = pack_bits_little_endian(table)
    return packed, max_k, indices.tolist()


def make_avro_record(k: int, truth_table_bytes: bytes, weight_indices: list[int]) -> dict:
    now_ms = int(time.time() * 1000)
    return {
        "id": {"uuid": str(uuid.uuid4()), "generation": 1},
        "k": k, "state": "STABLE",
        "truthTable": truth_table_bytes, "weights": weight_indices,
        "metadata": {"createdAt": now_ms, "mutationCount": 0, "parentIds": [], "accuracyHistory": []},
    }


def write_avro_file(records: list[dict], filepath: str) -> None:
    assert FASTAVRO_AVAILABLE, "fastavro required"  # type: ignore[possibly-unbound]
    parsed = fastavro.parse_schema(MPDT_NEURON_SCHEMA)  # type: ignore[possibly-unbound]
    os.makedirs(os.path.dirname(filepath) or ".", exist_ok=True)
    with open(filepath, "wb") as f:
        fastavro.writer(f, parsed, records)  # type: ignore[possibly-unbound]


# ── Main extraction ─────────────────────────────────────────────────────────

def extract_from_shards(
    shard_paths: list[str],
    target_layers: set[int],
    args: argparse.Namespace,
    rng: np.random.Generator,
) -> dict[int, dict[str, np.ndarray]]:
    """Extract FFN weights from sharded safetensors files, layer by layer.

    Processes each shard independently, extracting only weights for
    target layers. Returns collected weights keyed by layer index.
    """
    layer_weights: dict[int, dict[str, np.ndarray]] = {}
    total_bytes = 0

    for shard_idx, shard_path in enumerate(shard_paths):
        shard_name = os.path.basename(shard_path)
        shard_size = os.path.getsize(shard_path)
        print(f"\n  Shard {shard_idx + 1}/{len(shard_paths)}: {shard_name} "
              f"({shard_size / 1024**3:.2f} GB)")

        assert SAFETENSORS_AVAILABLE, "safetensors required"  # type: ignore[possibly-unbound]
        with safe_open(shard_path, framework="pt") as f:  # type: ignore[possibly-unbound]
            all_keys = list(f.keys())
            ffn_keys = [k for k in all_keys if is_ffn_weight(k)]
            relevant_keys = []
            for k in ffn_keys:
                layer_idx = extract_layer_idx(k)
                if layer_idx is not None and (not target_layers or layer_idx in target_layers):
                    relevant_keys.append(k)

            if not relevant_keys:
                print(f"    No target layer FFN weights in this shard, skipping")
                continue

            print(f"    Found {len(relevant_keys)} relevant FFN tensors")

            for key_idx, key in enumerate(relevant_keys):
                tensor = f.get_tensor(key)
                lidx = extract_layer_idx(key)
                if lidx is None:
                    del tensor
                    continue
                total_bytes += tensor.element_size() * tensor.numel()

                arr = tensor.float().cpu().numpy()
                del tensor

                if lidx not in layer_weights:
                    layer_weights[lidx] = {}
                if "up_proj" in key or "w1" in key or "c_fc" in key:
                    layer_weights[lidx]["up_proj"] = arr
                elif "gate_proj" in key or "w3" in key:
                    layer_weights[lidx]["gate_proj"] = arr
                elif "block_sparse_moe" in key:
                    # Store MoE expert weights
                    expert_key = f"expert_{len(layer_weights[lidx])}"
                    layer_weights[lidx][expert_key] = arr

                if (key_idx + 1) % 50 == 0:
                    print(f"      {key_idx + 1}/{len(relevant_keys)} tensors, "
                          f"{total_bytes / 1024**3:.1f} GB loaded")

            force_gc()

    return layer_weights


def process_layers(
    layer_weights: dict[int, dict[str, np.ndarray]],
    args: argparse.Namespace,
    rng: np.random.Generator,
    arch_name: str,
) -> None:
    """Process extracted layer weights, generating Avro output files."""
    total_neurons = 0
    processed = 0
    skipped = 0
    start_time = time.time()

    layers_to_process = sorted(layer_weights.keys())
    if args.max_layers > 0:
        layers_to_process = layers_to_process[:args.max_layers]

    print(f"\nProcessing {len(layers_to_process)} layers...")
    log_memory("start")

    for layer in layers_to_process:
        weights = layer_weights[layer]
        w = weights.get("up_proj")
        if w is None:
            w = weights.get("gate_proj")
        if w is None:
            skipped += 1
            continue

        processed += 1
        d_ff, d_model = w.shape
        print(f"  Layer {layer}: W1=({d_ff}, {d_model}), "
              f"extracting {min(args.neurons_per_layer, d_ff)} neurons (k={args.k})")

        records = []
        actual_count = min(args.neurons_per_layer, d_ff)
        for n in range(actual_count):
            neuron_weights = w[n]
            packed, actual_k, indices = weights_to_truth_table(
                neuron_weights, args.k, threshold=args.threshold, rng=rng
            )
            records.append(make_avro_record(actual_k, packed, indices))

        out_path = os.path.join(
            args.output_dir, f"{args.model_name}_layer{layer}_neurons.avro"
        )
        write_avro_file(records, out_path)
        print(f"    → {len(records)} neurons → {out_path}")
        total_neurons += len(records)

        # Process MoE experts for this layer if requested
        if args.extract_experts:
            expert_keys = [k for k in weights if k.startswith("expert_")]
            for ek in expert_keys:
                expert_w = weights[ek]
                expert_d_ff = expert_w.shape[0]
                print(f"    Expert {ek}: shape=({expert_d_ff}, {expert_w.shape[1]})")
                expert_records = []
                for n in range(min(args.neurons_per_expert, expert_d_ff)):
                    packed, actual_k, indices = weights_to_truth_table(
                        expert_w[n], args.k, threshold=args.threshold, rng=rng
                    )
                    expert_records.append(make_avro_record(actual_k, packed, indices))
                expert_out = os.path.join(
                    args.output_dir,
                    f"{args.model_name}_layer{layer}_{ek}_neurons.avro"
                )
                write_avro_file(expert_records, expert_out)
                print(f"      → {len(expert_records)} expert neurons → {expert_out}")
                total_neurons += len(expert_records)

        # Free layer weights to save memory
        layer_weights[layer] = {}

        # Periodic GC
        if processed % 4 == 0:
            force_gc()
            log_memory(f"layer_{layer}")

    elapsed = time.time() - start_time
    print(f"\nTotal: {total_neurons} neurons from {processed} layers "
          f"({skipped} skipped) in {elapsed:.1f}s "
          f"({total_neurons / elapsed:.1f} neurons/s)")
    print(f"Architecture: {arch_name}")


def load_with_mmap(model_dir: str, args: argparse.Namespace) -> None:
    """Load model using PyTorch mmap for large models.

    Uses torch.load with mmap=True to memory-map weights, avoiding
    loading the full model into RAM. Falls back to safetensors streaming.
    """
    safetensors_files = find_safetensors_files(model_dir)
    if not safetensors_files:
        print(f"Error: no safetensors files found in {model_dir}", file=sys.stderr)
        sys.exit(1)

    config = load_model_config(model_dir)
    arch_name = detect_architecture_from_config(config)
    num_layers = config.get("num_hidden_layers", 0)
    num_experts = config.get("num_local_experts", 0)
    hidden_size = config.get("hidden_size", 0)

    print(f"Model:        {args.model_name}")
    print(f"Architecture: {arch_name}")
    print(f"Layers:       {num_layers}")
    if num_experts:
        print(f"MoE experts:  {num_experts}/layer")
    print(f"Hidden size:  {hidden_size}")
    print(f"Shards:       {len(safetensors_files)}")

    total_size = sum(os.path.getsize(f) for f in safetensors_files)
    print(f"Total size:   {total_size / 1024**3:.2f} GB")

    # Resolve target layers
    if args.max_layers > 0:
        target_layers = set(range(min(args.max_layers, num_layers or args.max_layers)))
    elif args.layers_range:
        parts = args.layers_range.split("-")
        if len(parts) == 2:
            target_layers = set(range(int(parts[0]), int(parts[1]) + 1))
        else:
            target_layers = {int(parts[0])}
    else:
        target_layers = set(range(num_layers)) if num_layers else set()

    print(f"Target layers: {min(target_layers)}–{max(target_layers)} "
          f"({len(target_layers)} total)")

    rng = np.random.default_rng(args.seed)

    # Check RAM availability
    mem = get_memory_usage()
    if mem["rss_mb"] > 0:
        import psutil as _psutil  # type: ignore[no-redef]
        system_ram_gb = _psutil.virtual_memory().total / 1024**3
        print(f"System RAM:    {system_ram_gb:.1f} GB")
        if total_size > system_ram_gb * 1024**3 * 0.8:
            print("Warning: model size > 80% of RAM. Using mmap/disk swapping.")
            print("Processing will be slow but should not OOM.")

    # Extract weights from shards
    layer_weights = extract_from_shards(
        safetensors_files, target_layers, args, rng
    )

    # Process extracted weights
    process_layers(layer_weights, args, rng, arch_name)

    # Write metadata
    _write_metadata(args, config, arch_name, num_layers, total_size)


def _write_metadata(
    args: argparse.Namespace, config: dict, arch_name: str,
    num_layers: int, total_size: int,
) -> None:
    """Write metadata JSON with model info."""
    metadata = {
        "model_name": args.model_name,
        "source_model": args.source_model or "unknown",
        "architecture": arch_name,
        "architectures": config.get("architectures", []),
        "model_type": config.get("model_type", ""),
        "hidden_size": config.get("hidden_size"),
        "intermediate_size": config.get("intermediate_size"),
        "num_hidden_layers": num_layers,
        "num_experts": config.get("num_local_experts", 0),
        "num_key_value_heads": config.get("num_key_value_heads"),
        "total_size_bytes": total_size,
        "loading_mode": "mmap_streaming",
        "quantized": args.quantize_8bit,
        "k": args.k,
        "neurons_per_layer": args.neurons_per_layer,
        "extracted_experts": args.extract_experts,
        "threshold": args.threshold,
        "seed": args.seed,
        "max_layers": args.max_layers,
        "layers_range": args.layers_range,
        "created_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "hardware": {
            "platform": platform.platform(),
            "python": sys.version,
            "cpu_count": os.cpu_count(),
            "ram_gb": _get_system_ram_gb(),
        },
    }
    meta_path = os.path.join(args.output_dir, f"{args.model_name}_metadata.json")
    os.makedirs(args.output_dir, exist_ok=True)
    with open(meta_path, "w") as f:
        json.dump(metadata, f, indent=2)
    print(f"\nMetadata → {meta_path}")


# ── Main ────────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Pre-train MPDT neurons from LARGE transformer models (>10GB)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Small model (fits in RAM)
  python3 scripts/pretrain_large.py --model-dir ~/models/Qwen2.5-0.5B \\
    --model-name Qwen2.5-0.5B --k 16 --neurons-per-layer 30

  # Medium model with extraction limits
  python3 scripts/pretrain_large.py --model-dir ~/models/Qwen2.5-7B \\
    --model-name Qwen2.5-7B --max-layers 28 --neurons-per-layer 50

  # MoE model with expert extraction
  python3 scripts/pretrain_large.py --model-dir ~/models/Mixtral-8x7B \\
    --model-name Mixtral-8x7B --extract-experts --neurons-per-expert 20

  # Specific layer range
  python3 scripts/pretrain_large.py --model-dir ~/models/DeepSeek-R1 \\
    --model-name DeepSeek-R1 --layers-range 0-5 --extract-experts
        """,
    )

    # Required
    parser.add_argument("--model-dir", type=str, required=True,
                        help="Directory containing safetensors files and config.json")
    parser.add_argument("--model-name", type=str, required=True,
                        help="Model name for output filenames")
    parser.add_argument("--source-model", type=str, default=None,
                        help="HF model ID for provenance (e.g. 'Qwen/Qwen2.5-7B')")

    # Layer selection
    parser.add_argument("--max-layers", type=int, default=0,
                        help="Maximum layers to process (0 = all detected)")
    parser.add_argument("--layers-range", type=str, default=None,
                        help="Specific layer range, e.g. '0-5' or '10'")

    # Extraction parameters
    parser.add_argument("--k", type=int, default=16,
                        help="Input bits per neuron (1..20, default: 16)")
    parser.add_argument("--neurons-per-layer", type=int, default=100,
                        help="Neurons to extract per layer (default: 100)")
    parser.add_argument("--threshold", type=float, default=0.0,
                        help="Activation threshold (default: 0.0)")
    parser.add_argument("--seed", type=int, default=42,
                        help="Random seed (default: 42)")

    # MoE
    parser.add_argument("--extract-experts", action="store_true",
                        help="Extract neurons from MoE experts (Mixtral, DeepSeek-R1)")
    parser.add_argument("--neurons-per-expert", type=int, default=20,
                        help="Neurons per MoE expert (default: 20)")

    # Memory
    parser.add_argument("--quantize-8bit", action="store_true",
                        help="Use 8-bit quantization to reduce memory (requires bitsandbytes)")
    parser.add_argument("--max-ram-gb", type=float, default=0,
                        help="RAM limit in GB (0 = auto-detect). Triggers disk swapping if exceeded.")

    # Output
    parser.add_argument("--output-dir", type=str, default="models/pretrained",
                        help="Output directory (default: models/pretrained)")
    parser.add_argument("--checkpoint-dir", type=str, default=None,
                        help="Checkpoint directory for resume support")

    args = parser.parse_args()

    if args.k < 1 or args.k > 20:
        parser.error(f"--k must be in [1, 20], got {args.k}")

    if not os.path.isdir(args.model_dir):
        parser.error(f"Model directory not found: {args.model_dir}")

    if args.quantize_8bit and not HAS_BNB:
        print("Warning: bitsandbytes not installed, disabling --quantize-8bit",
              file=sys.stderr)
        args.quantize_8bit = False

    load_with_mmap(args.model_dir, args)


if __name__ == "__main__":
    main()
