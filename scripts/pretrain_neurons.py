#!/usr/bin/env python3
"""Pre-train MPDT neurons from open-source transformer model weights.

Converts feed-forward network (FFN) weights from transformer layers into
MPDT truth tables by sampling k input positions and thresholding the
weighted sum.

Modes:
  --demo          Generate synthetic weights (default, no dependencies)
  --model-path    Load weights from a safetensors file (requires safetensors)

Output: Avro container files compatible with TruthTable.fromAvroBytes().
"""

import argparse
import json
import os
import sys
import time
import uuid

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

# ── Avro schema (mirrors matrix-core/src/main/resources/avro/mpdt_neuron.avsc) ──
MPDT_NEURON_SCHEMA = {
    "type": "record",
    "name": "MPDTNeuron",
    "namespace": "io.matrix.neuron",
    "doc": "MPDT neuron schema. Ref: L1_MPDT_neuron.md §7",
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


def weights_to_truth_table(w: np.ndarray, k: int, threshold: float = 0.0,
                           rng: np.random.Generator | None = None) -> tuple[bytes, int, list[int]]:
    """Convert k sampled weights to a 2^k bit truth table.

    For each of the 2^k possible input combinations, computes
    dot(w_selected, input_bits) and thresholds to get the output.

    Args:
        w:         Weight vector (size >= k)
        k:         Number of inputs to sample (1 ≤ k ≤ 20)
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


def generate_synthetic_weights(d_model: int, d_ff: int, seed: int = 42
                               ) -> np.ndarray:
    """Generate synthetic FFN W1 weights mimicking a decoder-only transformer.

    Uses Xavier/Glorot-style normal initialisation scaled by 1/sqrt(d_model),
    consistent with typical open-source model initialisation (SmolLM2, GPT-2, etc.).
    """
    rng = np.random.default_rng(seed)
    std = 1.0 / np.sqrt(d_model)
    return rng.normal(0.0, std, (d_ff, d_model)).astype(np.float32)


def load_safetensors_weights(model_path: str) -> dict[str, np.ndarray]:
    """Load all tensors from a safetensors file, converting to float32 numpy."""
    if not SAFETENSORS_AVAILABLE:
        raise ImportError(
            "safetensors is required for --model-path mode. "
            "Install: pip install safetensors"
        )
    tensors = {}
    try:
        import torch
        from safetensors.torch import load_file
        torch_tensors = load_file(model_path)
        for key, t in torch_tensors.items():
            tensors[key] = t.float().numpy()
        return tensors
    except ImportError:
        pass

    with safe_open(model_path, framework="np") as f:
        for key in f.keys():
            try:
                tensors[key] = f.get_tensor(key)
            except TypeError:
                t = f.get_tensor(key)
                tensors[key] = t.astype(np.float32) if hasattr(t, 'astype') else np.array(t, dtype=np.float32)
    return tensors


def extract_ffn_weights(tensors: dict[str, np.ndarray], layer_idx: int
                        ) -> np.ndarray | None:
    """Extract W1 (up-projection) weights from a transformer layer.

    Tries common weight name patterns for different model architectures:
      - LLaMA-style:  model.layers.{i}.mlp.up_proj.weight
      - Qwen2-style:  model.layers.{i}.mlp.up_proj.weight
      - GPT-2 style:  transformer.h.{i}.mlp.c_fc.weight
      - SmolLM2:      model.layers.{i}.mlp.up_proj.weight
    """
    patterns = [
        f"model.layers.{layer_idx}.mlp.up_proj.weight",
        f"model.layers.{layer_idx}.mlp.gate_proj.weight",
        f"model.layers.{layer_idx}.mlp.c_fc.weight",
        f"transformer.h.{layer_idx}.mlp.c_fc.weight",
        f"transformer.h.{layer_idx}.mlp.up_proj.weight",
    ]
    for pattern in patterns:
        if pattern in tensors:
            return tensors[pattern]
    return None


def make_avro_record(k: int, truth_table_bytes: bytes,
                     weight_indices: list[int]) -> dict:
    """Build a single Avro record matching the MPDTNeuron schema."""
    now_ms = int(time.time() * 1000)
    return {
        "id": {
            "uuid": str(uuid.uuid4()),
            "generation": 1,
        },
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


def run_demo(args: argparse.Namespace) -> None:
    """Generate synthetic weights and extract truth tables."""
    print(f"Generating synthetic weights: d_model={args.d_model}, d_ff={args.d_ff}")
    rng = np.random.default_rng(args.seed)

    # SmolLM2-135M has 12 layers, d_model=576, d_ff=1536
    # Generate weights for all requested layers
    for layer in range(args.layers):
        print(f"  Layer {layer}: generating {args.neurons_per_layer} neurons (k={args.k})")
        w1 = generate_synthetic_weights(args.d_model, args.d_ff,
                                        seed=args.seed + layer)

        records = []
        for n in range(args.neurons_per_layer):
            neuron_weights = w1[n % args.d_ff]  # cycle through d_ff neurons
            packed, actual_k, indices = weights_to_truth_table(
                neuron_weights, args.k, threshold=args.threshold, rng=rng
            )
            rec = make_avro_record(actual_k, packed, indices)
            records.append(rec)

        out_path = os.path.join(
            args.output_dir,
            f"{args.model_name}_layer{layer}_neurons.avro",
        )
        write_avro_file(records, out_path)
        print(f"    Wrote {len(records)} neurons → {out_path}")


def run_from_model(args: argparse.Namespace) -> None:
    """Load real model weights from safetensors and extract truth tables."""
    if not os.path.exists(args.model_path):
        print(f"Error: model path not found: {args.model_path}", file=sys.stderr)
        sys.exit(1)

    print(f"Loading safetensors from: {args.model_path}")
    tensors = load_safetensors_weights(args.model_path)
    print(f"  Loaded {len(tensors)} tensors")

    rng = np.random.default_rng(args.seed)
    total_neurons = 0

    for layer in range(args.layers):
        w1 = extract_ffn_weights(tensors, layer)
        if w1 is None:
            print(f"  Layer {layer}: W1 not found, skipping")
            continue

        d_model = w1.shape[1]
        d_ff = w1.shape[0]
        print(f"  Layer {layer}: W1 shape=({d_ff}, {d_model}), "
              f"extracting {args.neurons_per_layer} neurons (k={args.k})")

        records = []
        for n in range(min(args.neurons_per_layer, d_ff)):
            neuron_weights = w1[n]
            packed, actual_k, indices = weights_to_truth_table(
                neuron_weights, args.k, threshold=args.threshold, rng=rng
            )
            rec = make_avro_record(actual_k, packed, indices)
            records.append(rec)

        out_path = os.path.join(
            args.output_dir,
            f"{args.model_name}_layer{layer}_neurons.avro",
        )
        write_avro_file(records, out_path)
        print(f"    Wrote {len(records)} neurons → {out_path}")
        total_neurons += len(records)

    print(f"Total: {total_neurons} neurons extracted")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Pre-train MPDT neurons from transformer model weights",
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
        help="Model name for output filenames (default: SmolLM2-135M-synth)",
    )
    parser.add_argument(
        "--d-model", type=int, default=576,
        help="Model hidden dimension (default: 576, SmolLM2-135M)",
    )
    parser.add_argument(
        "--d-ff", type=int, default=1536,
        help="FFN intermediate dimension (default: 1536, SmolLM2-135M)",
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
        help="Number of transformer layers to process (default: 4)",
    )
    parser.add_argument(
        "--threshold", type=float, default=0.0,
        help="Activation threshold (default: 0.0, >0 → true)",
    )
    parser.add_argument(
        "--seed", type=int, default=42,
        help="Random seed for reproducibility (default: 42)",
    )

    # Output
    parser.add_argument(
        "--output-dir", type=str, default="models/pretrained",
        help="Output directory for Avro files (default: models/pretrained)",
    )

    args = parser.parse_args()

    # Validate k
    if args.k < 1 or args.k > 20:
        parser.error(f"--k must be in [1, 20], got {args.k}")

    # Validate output dependencies
    if not FASTAVRO_AVAILABLE:
        print(
            "Warning: fastavro not installed. Install with: pip install fastavro",
            file=sys.stderr,
        )
        print(
            "Will write raw truth table bytes instead of Avro files.",
            file=sys.stderr,
        )
        # Fallback to raw binary output
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
        "mode": "real" if args.model_path else "synthetic",
        "k": args.k,
        "neurons_per_layer": args.neurons_per_layer,
        "layers": args.layers,
        "threshold": args.threshold,
        "seed": args.seed,
        "d_model": args.d_model,
        "d_ff": args.d_ff if not args.model_path else "from_model",
        "created_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    }
    meta_path = os.path.join(args.output_dir, f"{args.model_name}_metadata.json")
    os.makedirs(args.output_dir, exist_ok=True)
    with open(meta_path, "w") as f:
        json.dump(metadata, f, indent=2)
    print(f"Metadata → {meta_path}")


def _run_fallback(args: argparse.Namespace) -> None:
    """Fallback: write raw truth table bytes + sidecar JSON."""
    os.makedirs(args.output_dir, exist_ok=True)
    rng = np.random.default_rng(args.seed)

    for layer in range(args.layers):
        w1 = generate_synthetic_weights(args.d_model, args.d_ff,
                                        seed=args.seed + layer)
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
            args.output_dir,
            f"{args.model_name}_layer{layer}_neurons.json",
        )
        with open(out_path, "w") as f:
            json.dump(records, f, indent=2)
        print(f"  Wrote {len(records)} neurons → {out_path}")


if __name__ == "__main__":
    main()
