#!/usr/bin/env python3
"""Pre-train MPDT neurons from open-source transformer model weights.

Converts feed-forward network (FFN) weights from transformer layers into
MPDT truth tables by sampling k input positions and thresholding the
weighted sum.

Modes:
  --demo              Generate synthetic weights (default, no dependencies)
  --model-path        Load weights from a safetensors file (requires safetensors)
  --quantize          Memory-efficient layer-by-layer loading for large models
  --source-model      Track provenance (e.g. "Qwen/Qwen2.5-0.5B")

Supported architectures (auto-detected):
  - LLaMA 2/3, Qwen2/2.5   model.layers.{i}.mlp.{up,gate}_proj.weight
  - GPT-2                   transformer.h.{i}.mlp.c_fc.weight
  - SmolLM2                 model.layers.{i}.mlp.{up,gate}_proj.weight
  - Mixtral (MoE)           model.layers.{i}.block_sparse_moe.experts.{j}.w1
  - DeepSeek-R1 (MoE)       model.layers.{i}.mlp.experts.{j}.up_proj.weight

Output: Avro container files compatible with TruthTable.fromAvroBytes().
"""

import argparse
import gc
import json
import os
import re
import sys
import time
import uuid
from pathlib import Path
from typing import Optional

import numpy as np

# ── optional imports ──────────────────────────────────────────────────────
try:
    from safetensors import safe_open
    SAFETENSORS_AVAILABLE = True
except ImportError:
    SAFETENSORS_AVAILABLE = False

try:
    import fastavro
    FASTAVRO_AVAILABLE = True
except ImportError:
    FASTAVRO_AVAILABLE = False

try:
    import torch
    TORCH_AVAILABLE = True
except ImportError:
    TORCH_AVAILABLE = False

# ── Avro schema (mirrors matrix-core/src/main/resources/avro/mpdt_neuron.avsc) ──
MPDT_NEURON_SCHEMA = {
    "type": "record",
    "name": "MPDTNeuron",
    "namespace": "io.matrix.neuron",
    "doc": "MPDT neuron schema. Ref: L1_MPDT_neuron.md \u00a77",
    "fields": [
        {
            "name": "id",
            "type": {
                "type": "record",
                "name": "NeuronId",
                "fields": [
                    {"name": "uuid", "type": "string"},
                    {"name": "generation", "type": "long"},
                ],
            },
        },
        {"name": "k", "type": "int"},
        {
            "name": "state",
            "type": {
                "type": "enum",
                "name": "NeuronState",
                "symbols": ["STABLE", "LEARNING", "MUTATING", "FROZEN"],
            },
        },
        {"name": "truthTable", "type": "bytes"},
        {"name": "weights", "type": {"type": "array", "items": "int"}},
        {
            "name": "metadata",
            "type": [
                "null",
                {
                    "type": "record",
                    "name": "Metadata",
                    "fields": [
                        {"name": "createdAt", "type": "long"},
                        {"name": "mutationCount", "type": "long"},
                        {
                            "name": "parentIds",
                            "type": {"type": "array", "items": "string"},
                        },
                        {
                            "name": "accuracyHistory",
                            "type": {"type": "array", "items": "double"},
                        },
                    ],
                },
            ],
        },
    ],
}


# ── Architecture detection ──────────────────────────────────────────────────

# FFN weight name patterns per architecture
ARCH_PATTERNS = {
    "llama": [
        re.compile(r"model\.layers\.(\d+)\.mlp\.(up_proj|gate_proj)\.weight"),
    ],
    "qwen2": [
        re.compile(r"model\.layers\.(\d+)\.mlp\.(up_proj|gate_proj)\.weight"),
    ],
    "smollm2": [
        re.compile(r"model\.layers\.(\d+)\.mlp\.(up_proj|gate_proj)\.weight"),
    ],
    "gpt2": [
        re.compile(r"transformer\.h\.(\d+)\.mlp\.c_fc\.weight"),
    ],
    "mixtral": [
        re.compile(r"model\.layers\.(\d+)\.block_sparse_moe\.experts\.(\d+)\.(w1|w3)"),
    ],
    "deepseek_r1": [
        re.compile(r"model\.layers\.(\d+)\.mlp\.experts\.(\d+)\.(up_proj|gate_proj)\.weight"),
    ],
    "deepseek_v3": [
        re.compile(r"model\.layers\.(\d+)\.mlp\.gate\.up_proj\.weight"),
        re.compile(r"model\.layers\.(\d+)\.mlp\.shared_experts\.up_proj\.weight"),
    ],
}

# Priority order for architecture detection
ARCH_PRIORITY = ["llama", "qwen2", "smollm2", "gpt2", "mixtral", "deepseek_r1", "deepseek_v3"]


def detect_architecture(tensor_keys: list[str]) -> tuple[str, int]:
    """Detect model architecture and layer count from tensor key names.

    Returns:
        (arch_name, num_layers) or ("unknown", 0)
    """
    scores = {}
    for arch in ARCH_PRIORITY:
        patterns = ARCH_PATTERNS[arch]
        matched_layers = set()
        total_matches = 0
        for key in tensor_keys:
            for pat in patterns:
                m = pat.match(key)
                if m:
                    total_matches += 1
                    matched_layers.add(int(m.group(1)))
        if total_matches > 0:
            scores[arch] = (total_matches, max(matched_layers) + 1)
    if not scores:
        return ("unknown", 0)
    best_arch = max(scores, key=lambda a: scores[a][0])
    return (best_arch, scores[best_arch][1])


def extract_layer_idx(key: str) -> int | None:
    """Extract the transformer layer index from a tensor key name."""
    for arch, patterns in ARCH_PATTERNS.items():
        for pat in patterns:
            m = pat.match(key)
            if m:
                return int(m.group(1))
    # Fallback: generic digit extraction after "layers."
    m = re.search(r"layers?\.(\d+)\.", key)
    if m:
        return int(m.group(1))
    m = re.search(r"\.h\.(\d+)\.", key)
    if m:
        return int(m.group(1))
    return None


def is_ffn_weight(key: str) -> bool:
    """Check if a tensor key is an FFN up-projection or gate weight."""
    ffn_markers = [
        ".up_proj.", ".gate_proj.", ".c_fc.",
        ".block_sparse_moe.", ".mlp.gate.", ".shared_experts.",
    ]
    return any(m in key for m in ffn_markers)


def filter_ffn_keys(tensor_keys: list[str]) -> list[str]:
    """Filter tensor keys to only FFN weight keys."""
    return [k for k in tensor_keys if is_ffn_weight(k)]


# ── Core logic ──────────────────────────────────────────────────────────────

def pack_bits_little_endian(table: np.ndarray) -> bytes:
    """Pack a boolean array into bytes in little-endian bit order.

    Matches Java BitSet.toByteArray() / BitSet.valueOf(byte[]) convention:
    bit 0 (index 0) is LSB of byte 0, bit 8 is LSB of byte 1, etc.
    """
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
    w: np.ndarray,
    k: int,
    threshold: float = 0.0,
    rng: np.random.Generator | None = None,
) -> tuple[bytes, int, list[int]]:
    """Convert k sampled weights to a 2^k bit truth table.

    For each of the 2^k possible input combinations, computes
    dot(w_selected, input_bits) and thresholds to get the output.

    Args:
        w:         Weight vector (size >= k)
        k:         Number of inputs to sample (1 \u2264 k \u2264 20)
        threshold: Decision threshold (default: 0.0)
        rng:       Random generator for sampling positions

    Returns:
        (packed_bytes, k, selected_indices) — packed truth table bytes,
        the k value, and the sampled weight indices for reproducibility.
    """
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


def generate_synthetic_weights(
    d_model: int, d_ff: int, seed: int = 42
) -> np.ndarray:
    """Generate synthetic FFN W1 weights mimicking a decoder-only transformer."""
    rng = np.random.default_rng(seed)
    std = 1.0 / np.sqrt(d_model)
    return rng.normal(0.0, std, (d_ff, d_model)).astype(np.float32)


def make_avro_record(
    k: int, truth_table_bytes: bytes, weight_indices: list[int]
) -> dict:
    """Build a single Avro record matching the MPDTNeuron schema."""
    now_ms = int(time.time() * 1000)
    return {
        "id": {"uuid": str(uuid.uuid4()), "generation": 1},
        "k": k,
        "state": "STABLE",
        "truthTable": truth_table_bytes,
        "weights": weight_indices,
        "metadata": {
            "createdAt": now_ms,
            "mutationCount": 0,
            "parentIds": [],
            "accuracyHistory": [],
        },
    }


def write_avro_file(records: list[dict], filepath: str) -> None:
    """Write records to an Avro container file."""
    if not FASTAVRO_AVAILABLE:
        raise ImportError(
            "fastavro is required for Avro output. Install: pip install fastavro"
        )
    parsed = fastavro.parse_schema(MPDT_NEURON_SCHEMA)
    os.makedirs(os.path.dirname(filepath) or ".", exist_ok=True)
    with open(filepath, "wb") as f:
        fastavro.writer(f, parsed, records)


# ── Model loading ───────────────────────────────────────────────────────────

def load_safetensors_bulk(model_path: str) -> dict[str, np.ndarray]:
    """Load all tensors from a safetensors file into memory (small models only).

    Falls back to numpy loader if torch is unavailable.
    """
    if not SAFETENSORS_AVAILABLE:
        raise ImportError(
            "safetensors is required for --model-path mode. "
            "Install: pip install safetensors"
        )

    tensors = {}
    # Try torch-based loader first (handles bfloat16)
    if TORCH_AVAILABLE:
        try:
            from safetensors.torch import load_file
            torch_tensors = load_file(model_path)
            for key, t in torch_tensors.items():
                tensors[key] = t.float().numpy()
            return tensors
        except Exception:
            pass

    # Fallback: numpy-based loading
    with safe_open(model_path, framework="np") as f:
        for key in f.keys():
            try:
                t = f.get_tensor(key)
                tensors[key] = t.astype(np.float32) if hasattr(t, "astype") else np.array(t, dtype=np.float32)
            except TypeError:
                t = f.get_tensor(key)
                tensors[key] = t.astype(np.float32) if hasattr(t, "astype") else np.array(t, dtype=np.float32)
    return tensors


def load_safetensors_streaming(
    model_path: str,
    target_layers: set[int],
    dtype: str = "float32",
) -> dict[int, dict[str, np.ndarray]]:
    """Memory-efficient layer-by-layer loading for large models.

    Opens the safetensors file once and processes each tensor individually,
    freeing memory immediately after extraction. Only loads FFN weights
    for requested layers.

    Args:
        model_path:     Path to model.safetensors file
        target_layers:  Set of layer indices to load
        dtype:          Output dtype: "float32", "float16", or "bfloat16"

    Returns:
        {layer_idx: {"up_proj": np.ndarray, "gate_proj": np.ndarray | None}, ...}
    """
    if not SAFETENSORS_AVAILABLE:
        raise ImportError("safetensors is required. Install: pip install safetensors")

    np_dtype = {"float32": np.float32, "float16": np.float16, "bfloat16": np.float16}[dtype]

    layer_weights: dict[int, dict[str, np.ndarray]] = {}

    print(f"  Streaming from: {model_path}")
    print(f"  Target layers: {sorted(target_layers) if target_layers else 'all'}")
    print(f"  Output dtype: {dtype}")

    tensor_count = 0
    loaded_bytes = 0
    skipped_count = 0

    if TORCH_AVAILABLE:
        # Use torch-based safe_open for bfloat16 support
        with safe_open(model_path, framework="pt") as f:
            all_keys = list(f.keys())
            print(f"  Total tensors in file: {len(all_keys)}")

            for key in all_keys:
                if not is_ffn_weight(key):
                    skipped_count += 1
                    continue

                layer_idx = extract_layer_idx(key)
                if layer_idx is None:
                    skipped_count += 1
                    continue

                if target_layers and layer_idx not in target_layers:
                    skipped_count += 1
                    continue

                # Load tensor
                tensor = f.get_tensor(key)  # bfloat16/float16 torch tensor
                tensor_count += 1
                loaded_bytes += tensor.element_size() * tensor.numel()

                # Convert to numpy
                if dtype == "float32":
                    arr = tensor.float().cpu().numpy()
                elif dtype == "float16":
                    arr = tensor.half().cpu().numpy()
                else:
                    # bfloat16 → float32 for numpy compatibility
                    arr = tensor.float().cpu().numpy()

                del tensor

                # Determine weight type
                if layer_idx not in layer_weights:
                    layer_weights[layer_idx] = {}
                if "up_proj" in key or "w1" in key or "c_fc" in key:
                    layer_weights[layer_idx]["up_proj"] = arr
                elif "gate_proj" in key or "w3" in key:
                    layer_weights[layer_idx]["gate_proj"] = arr

                # Progress indicator
                if tensor_count % 10 == 0:
                    print(f"    Loaded {tensor_count} tensors, "
                          f"{loaded_bytes / 1024**2:.0f} MB so far...")
    else:
        # Pure numpy fallback (no bfloat16 support)
        with safe_open(model_path, framework="np") as f:
            all_keys = list(f.keys())
            print(f"  Total tensors in file: {len(all_keys)}")

            for key in all_keys:
                if not is_ffn_weight(key):
                    skipped_count += 1
                    continue

                layer_idx = extract_layer_idx(key)
                if layer_idx is None:
                    skipped_count += 1
                    continue

                if target_layers and layer_idx not in target_layers:
                    skipped_count += 1
                    continue

                t = f.get_tensor(key)
                tensor_count += 1

                if hasattr(t, "astype"):
                    arr = t.astype(np_dtype)
                else:
                    arr = np.array(t, dtype=np_dtype)

                if layer_idx not in layer_weights:
                    layer_weights[layer_idx] = {}
                if "up_proj" in key or "w1" in key or "c_fc" in key:
                    layer_weights[layer_idx]["up_proj"] = arr
                elif "gate_proj" in key or "w3" in key:
                    layer_weights[layer_idx]["gate_proj"] = arr

    print(f"  Loaded {tensor_count} FFN tensors ({loaded_bytes / 1024**2:.0f} MB), "
          f"skipped {skipped_count} non-FFN keys")

    return layer_weights


def load_model_info(model_path: str) -> dict:
    """Load metadata from model directory (config.json, model_index.json)."""
    model_dir = os.path.dirname(model_path)
    info = {}

    config_path = os.path.join(model_dir, "config.json")
    if os.path.exists(config_path):
        try:
            with open(config_path) as f:
                config = json.load(f)
            info["architectures"] = config.get("architectures", [])
            info["hidden_size"] = config.get("hidden_size")
            info["intermediate_size"] = config.get("intermediate_size")
            info["num_hidden_layers"] = config.get("num_hidden_layers")
            info["model_type"] = config.get("model_type")
            info["num_experts"] = config.get("num_local_experts")
        except Exception:
            pass

    # Check for sharded model
    index_path = os.path.join(model_dir, "model.safetensors.index.json")
    if os.path.exists(index_path):
        try:
            with open(index_path) as f:
                index = json.load(f)
            info["sharded"] = True
            info["weight_map_size"] = len(index.get("weight_map", {}))
            info["total_size"] = index.get("metadata", {}).get("total_size", 0)
        except Exception:
            pass
    else:
        info["sharded"] = False
        if os.path.exists(model_path):
            info["file_size"] = os.path.getsize(model_path)

    return info


# ── Runners ─────────────────────────────────────────────────────────────────

def run_demo(args: argparse.Namespace) -> None:
    """Generate synthetic weights and extract truth tables."""
    print(f"Generating synthetic weights: d_model={args.d_model}, d_ff={args.d_ff}")
    rng = np.random.default_rng(args.seed)

    for layer in range(args.layers):
        print(f"  Layer {layer}: generating {args.neurons_per_layer} neurons (k={args.k})")
        w1 = generate_synthetic_weights(
            args.d_model, args.d_ff, seed=args.seed + layer
        )

        records = []
        for n in range(args.neurons_per_layer):
            neuron_weights = w1[n % args.d_ff]
            packed, actual_k, indices = weights_to_truth_table(
                neuron_weights, args.k, threshold=args.threshold, rng=rng
            )
            records.append(make_avro_record(actual_k, packed, indices))

        out_path = os.path.join(
            args.output_dir, f"{args.model_name}_layer{layer}_neurons.avro"
        )
        write_avro_file(records, out_path)
        print(f"    Wrote {len(records)} neurons \u2192 {out_path}")


def run_from_model(args: argparse.Namespace) -> None:
    """Load real model weights and extract truth tables.

    Uses either bulk loading (small models) or streaming (--quantize for
    large models).
    """
    if not os.path.exists(args.model_path):
        print(f"Error: model path not found: {args.model_path}", file=sys.stderr)
        sys.exit(1)

    # Detect model info from config
    model_info = load_model_info(args.model_path)
    detected_layers = model_info.get("num_hidden_layers", 0)

    # Resolve number of layers to process
    if args.layers > 0:
        num_layers = min(args.layers, detected_layers) if detected_layers else args.layers
    else:
        num_layers = detected_layers if detected_layers else 24  # default

    target_layers = set(range(num_layers))

    # Determine architecture from tensor keys
    arch_name = "unknown"
    arch_layers = 0
    if SAFETENSORS_AVAILABLE:
        try:
            with safe_open(args.model_path, framework="pt") as f:
                all_keys = list(f.keys())
            arch_name, arch_layers = detect_architecture(all_keys)
            if arch_layers > 0 and detected_layers == 0:
                detected_layers = arch_layers
                num_layers = min(num_layers, arch_layers)
                target_layers = set(range(num_layers))
        except Exception:
            pass

    print(f"Model path:    {args.model_path}")
    print(f"Architecture:  {arch_name} ({detected_layers or '?'} layers)")
    print(f"Processing:    {num_layers} layers")
    file_size = model_info.get("file_size", 0) or model_info.get("total_size", 0)
    if file_size:
        print(f"File size:     {file_size / 1024**3:.2f} GB")

    rng = np.random.default_rng(args.seed)

    if args.quantize:
        # Memory-efficient: load layer by layer
        print("\nUsing QUANTIZED (streaming) loading mode.")
        layer_weights = load_safetensors_streaming(
            args.model_path, target_layers=target_layers, dtype=args.dtype
        )
        _process_streamed_layers(args, layer_weights, rng, arch_name, num_layers)
    else:
        # Bulk load (small models only)
        print("\nLoading all tensors into memory (use --quantize for large models)...")
        tensors = load_safetensors_bulk(args.model_path)
        print(f"  Loaded {len(tensors)} tensors")
        _process_bulk_layers(args, tensors, rng, arch_name, num_layers)


def _process_streamed_layers(
    args: argparse.Namespace,
    layer_weights: dict[int, dict[str, np.ndarray]],
    rng: np.random.Generator,
    arch_name: str,
    num_layers: int,
) -> None:
    """Process layers loaded via streaming mode."""
    total_neurons = 0
    processed = 0

    for layer in sorted(layer_weights.keys()):
        if layer >= num_layers:
            break
        weights = layer_weights[layer]
        w = weights.get("up_proj")
        if w is None:
            w = weights.get("gate_proj")
        if w is None:
            print(f"  Layer {layer}: no FFN weights, skipping")
            continue

        processed += 1
        d_ff = w.shape[0]
        d_model = w.shape[1]
        print(f"  Layer {layer}: W1 shape=({d_ff}, {d_model}), "
              f"extracting {args.neurons_per_layer} neurons (k={args.k})")

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
        print(f"    Wrote {len(records)} neurons \u2192 {out_path}")
        total_neurons += len(records)

        # Free layer weights to reduce memory
        layer_weights[layer] = {}

    print(f"Total: {total_neurons} neurons from {processed} layers")
    print(f"Architecture: {arch_name}")


def _process_bulk_layers(
    args: argparse.Namespace,
    tensors: dict[str, np.ndarray],
    rng: np.random.Generator,
    arch_name: str,
    num_layers: int,
) -> None:
    """Process layers from bulk-loaded tensors (backward compat)."""
    total_neurons = 0

    for layer in range(num_layers):
        w1 = None
        # Try different naming patterns
        patterns = [
            f"model.layers.{layer}.mlp.up_proj.weight",
            f"model.layers.{layer}.mlp.gate_proj.weight",
            f"model.layers.{layer}.mlp.c_fc.weight",
            f"transformer.h.{layer}.mlp.c_fc.weight",
            f"transformer.h.{layer}.mlp.up_proj.weight",
        ]
        for pattern in patterns:
            if pattern in tensors:
                w1 = tensors[pattern]
                break

        if w1 is None:
            print(f"  Layer {layer}: W1 not found, skipping")
            continue

        d_ff = w1.shape[0]
        d_model = w1.shape[1]
        print(f"  Layer {layer}: W1 shape=({d_ff}, {d_model}), "
              f"extracting {args.neurons_per_layer} neurons (k={args.k})")

        records = []
        for n in range(min(args.neurons_per_layer, d_ff)):
            neuron_weights = w1[n]
            packed, actual_k, indices = weights_to_truth_table(
                neuron_weights, args.k, threshold=args.threshold, rng=rng
            )
            records.append(make_avro_record(actual_k, packed, indices))

        out_path = os.path.join(
            args.output_dir, f"{args.model_name}_layer{layer}_neurons.avro"
        )
        write_avro_file(records, out_path)
        print(f"    Wrote {len(records)} neurons \u2192 {out_path}")
        total_neurons += len(records)

    print(f"Total: {total_neurons} neurons extracted")


# ── Main ────────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Pre-train MPDT neurons from transformer model weights",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Synthetic demo
  python3 scripts/pretrain_neurons.py --demo --k 16 --layers 6

  # Real model (bulk load, <2GB models)
  python3 scripts/pretrain_neurons.py --model-path cache/model.safetensors \\
    --model-name Qwen2.5-0.5B --k 16 --neurons-per-layer 30 --layers 24

  # Quantized (streaming) for large models
  python3 scripts/pretrain_neurons.py --model-path cache/model.safetensors \\
    --model-name Qwen2.5-3B --quantize --dtype float16 --layers 36

  # Auto-detect all layers
  python3 scripts/pretrain_neurons.py --model-path cache/model.safetensors \\
    --model-name Qwen2.5-0.5B --layers 0 --neurons-per-layer 30
        """,
    )

    # Mode selection
    parser.add_argument(
        "--demo", action="store_true", default=True,
        help="Generate synthetic weights (default mode)",
    )
    parser.add_argument(
        "--model-path", type=str, default=None,
        help="Path to safetensors model file (enables real-model mode)",
    )

    # Model parameters
    parser.add_argument(
        "--model-name", type=str, default="SmolLM2-135M-synth",
        help="Model name for output filenames",
    )
    parser.add_argument(
        "--source-model", type=str, default=None,
        help="Source model HF ID for provenance tracking (e.g. 'Qwen/Qwen2.5-0.5B')",
    )
    parser.add_argument(
        "--d-model", type=int, default=576,
        help="Model hidden dimension (demo only, default: 576)",
    )
    parser.add_argument(
        "--d-ff", type=int, default=1536,
        help="FFN intermediate dimension (demo only, default: 1536)",
    )

    # Extraction parameters
    parser.add_argument(
        "--k", type=int, default=16,
        help="Number of input bits per neuron, 1..20 (default: 16)",
    )
    parser.add_argument(
        "--neurons-per-layer", type=int, default=100,
        help="Neurons to extract per layer (default: 100)",
    )
    parser.add_argument(
        "--layers", type=int, default=4,
        help="Number of transformer layers to process. 0 = auto-detect all (default: 4)",
    )
    parser.add_argument(
        "--threshold", type=float, default=0.0,
        help="Activation threshold (default: 0.0, >0 = true)",
    )
    parser.add_argument(
        "--seed", type=int, default=42,
        help="Random seed for reproducibility (default: 42)",
    )

    # Memory management
    parser.add_argument(
        "--quantize", action="store_true",
        help="Enable memory-efficient streaming: load weights layer-by-layer, "
             "convert bfloat16→float32 on-the-fly. Required for models >2GB.",
    )
    parser.add_argument(
        "--dtype", type=str, default="float32",
        choices=["float32", "float16", "bfloat16"],
        help="Output dtype for loaded weights (default: float32). "
             "Use float16 to halve memory at precision cost.",
    )

    # Output
    parser.add_argument(
        "--output-dir", type=str, default="models/pretrained",
        help="Output directory for Avro files",
    )

    args = parser.parse_args()

    # Validate k
    if args.k < 1 or args.k > 20:
        parser.error(f"--k must be in [1, 20], got {args.k}")

    # Auto-enable quantize for large files
    if args.model_path and os.path.exists(args.model_path):
        file_size = os.path.getsize(args.model_path)
        if file_size > 2 * 1024**3 and not args.quantize:
            print(
                f"Warning: model file is {file_size / 1024**3:.1f} GB. "
                f"Auto-enabling --quantize to avoid OOM.",
                file=sys.stderr,
            )
            args.quantize = True

    # Validate output dependencies
    if not FASTAVRO_AVAILABLE:
        print(
            "Warning: fastavro not installed. Install with: pip install fastavro",
            file=sys.stderr,
        )
        print("Will write raw truth table bytes instead of Avro files.", file=sys.stderr)
        _run_fallback(args)
        return

    # Select mode
    if args.model_path:
        run_from_model(args)
    else:
        run_demo(args)

    # Write metadata
    metadata = {
        "model_name": args.model_name,
        "source_model": args.source_model or "synthetic",
        "mode": "real" if args.model_path else "synthetic",
        "loading_mode": "quantized" if args.quantize else "bulk",
        "k": args.k,
        "neurons_per_layer": args.neurons_per_layer,
        "layers": args.layers,
        "threshold": args.threshold,
        "seed": args.seed,
        "d_model": args.d_model if not args.model_path else "from_model",
        "d_ff": args.d_ff if not args.model_path else "from_model",
        "dtype": args.dtype,
        "created_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    }

    # Add model-specific info if available
    if args.model_path and os.path.exists(args.model_path):
        model_info = load_model_info(args.model_path)
        metadata.update({
            "architectures": model_info.get("architectures", []),
            "hidden_size": model_info.get("hidden_size"),
            "intermediate_size": model_info.get("intermediate_size"),
            "num_hidden_layers": model_info.get("num_hidden_layers"),
            "model_type": model_info.get("model_type"),
            "sharded": model_info.get("sharded", False),
        })
        if model_info.get("file_size"):
            metadata["file_size_bytes"] = model_info["file_size"]

    meta_path = os.path.join(args.output_dir, f"{args.model_name}_metadata.json")
    os.makedirs(args.output_dir, exist_ok=True)
    with open(meta_path, "w") as f:
        json.dump(metadata, f, indent=2)
    print(f"Metadata \u2192 {meta_path}")


def _run_fallback(args: argparse.Namespace) -> None:
    """Fallback: write raw truth table bytes + sidecar JSON (no fastavro)."""
    os.makedirs(args.output_dir, exist_ok=True)
    rng = np.random.default_rng(args.seed)

    for layer in range(args.layers):
        w1 = generate_synthetic_weights(
            args.d_model, args.d_ff, seed=args.seed + layer
        )
        records = []
        for n in range(args.neurons_per_layer):
            neuron_weights = w1[n % args.d_ff]
            packed, actual_k, indices = weights_to_truth_table(
                neuron_weights, args.k, threshold=args.threshold, rng=rng
            )
            records.append({
                "k": actual_k,
                "truth_table_hex": packed.hex(),
                "weights": indices,
            })

        out_path = os.path.join(
            args.output_dir, f"{args.model_name}_layer{layer}_neurons.json"
        )
        with open(out_path, "w") as f:
            json.dump(records, f, indent=2)
        print(f"  Wrote {len(records)} neurons \u2192 {out_path}")


if __name__ == "__main__":
    main()
