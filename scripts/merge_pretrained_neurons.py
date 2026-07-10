#!/usr/bin/env python3
"""
Merge all pretrained neurons from multiple models into unified datasets.

Creates merged Avro files that combine neurons from all available pretrained models.
This allows MATRIX to use a larger, more diverse set of pretrained weights.

Usage:
    python3 merge_pretrained_neurons.py [--output-dir DIR] [--layers N]
"""

import argparse
import json
import os
import sys
from pathlib import Path

# Add project root to path
PROJECT_ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

import fastavro


def load_avro_neurons(filepath: Path) -> tuple[list[dict], dict]:
    """Load neurons from an Avro file. Returns (records, schema)."""
    with open(filepath, "rb") as f:
        reader = fastavro.reader(f)
        schema = reader.writer_schema
        records = list(reader)
        return records, schema


def write_avro_neurons(neurons: list[dict], schema: dict, filepath: Path) -> None:
    """Write neurons to an Avro file."""
    if not neurons:
        return
    with open(filepath, "wb") as f:
        fastavro.writer(f, schema, neurons)


def find_model_dirs(pretrained_dir: Path) -> list[tuple[str, Path]]:
    """Find all model directories containing neuron Avro files."""
    models = []
    for d in sorted(pretrained_dir.iterdir()):
        if d.is_dir() and any(d.glob("*_layer*_neurons.avro")):
            # Extract model name from first avro file
            avro_files = list(d.glob("*_layer*_neurons.avro"))
            if avro_files:
                # Get model name from filename pattern: ModelName_layer0_neurons.avro
                model_name = avro_files[0].stem.split("_layer")[0]
                models.append((model_name, d))
    return models


def merge_neurons_for_layer(
    model_dirs: list[tuple[str, Path]],
    layer_idx: int,
    max_neurons_per_model: int = 30,
) -> tuple[list, dict]:
    """Merge neurons from all models for a specific layer."""
    all_neurons = []
    schema = None
    for model_name, model_dir in model_dirs:
        avro_file = model_dir / f"{model_name}_layer{layer_idx}_neurons.avro"
        if avro_file.exists():
            neurons, file_schema = load_avro_neurons(avro_file)
            if schema is None:
                schema = file_schema
            # Limit neurons per model to avoid overwhelming
            selected = neurons[:max_neurons_per_model]
            # Tag each neuron with source model
            for n in selected:
                if isinstance(n, dict):
                    n["_source_model"] = model_name
                all_neurons.extend(selected)
                print(f"  {model_name} layer{layer_idx}: {len(selected)} neurons")
    return all_neurons, schema


def main():
    parser = argparse.ArgumentParser(description="Merge pretrained neurons from all models")
    parser.add_argument(
        "--pretrained-dir",
        type=Path,
        default=PROJECT_ROOT / "models" / "pretrained",
        help="Directory containing pretrained model subdirectories",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=PROJECT_ROOT / "models" / "pretrained" / "merged",
        help="Output directory for merged neurons",
    )
    parser.add_argument(
        "--layers",
        type=int,
        default=6,
        help="Number of layers to merge (default: 6)",
    )
    parser.add_argument(
        "--max-per-model",
        type=int,
        default=30,
        help="Max neurons per model per layer (default: 30)",
    )
    args = parser.parse_args()

    print("=" * 60)
    print("MATRIX Pretrained Neuron Merger")
    print("=" * 60)
    print(f"Source: {args.pretrained_dir}")
    print(f"Output: {args.output_dir}")
    print(f"Layers: {args.layers}")
    print()

    # Find all model directories
    model_dirs = find_model_dirs(args.pretrained_dir)
    if not model_dirs:
        print("ERROR: No model directories found!")
        sys.exit(1)

    print(f"Found {len(model_dirs)} models:")
    for name, path in model_dirs:
        avro_count = len(list(path.glob("*_layer*_neurons.avro")))
        print(f"  - {name}: {avro_count} layer files")
    print()

    # Create output directory
    args.output_dir.mkdir(parents=True, exist_ok=True)

    # Merge each layer
    total_neurons = 0
    for layer_idx in range(args.layers):
        print(f"--- Layer {layer_idx} ---")
        neurons, schema = merge_neurons_for_layer(model_dirs, layer_idx, args.max_per_model)
        if neurons and schema:
            output_file = args.output_dir / f"MATRIX_merged_layer{layer_idx}_neurons.avro"
            write_avro_neurons(neurons, schema, output_file)
            total_neurons += len(neurons)
            print(f"  Written: {output_file.name} ({len(neurons)} neurons)")
        else:
            print(f"  No neurons found for layer {layer_idx}")
        print()

    # Write metadata
    metadata = {
        "merged_from": [name for name, _ in model_dirs],
        "layers": args.layers,
        "total_neurons": total_neurons,
        "max_per_model_per_layer": args.max_per_model,
        "format": "avro",
        "description": "Merged pretrained neurons from multiple transformer models",
    }
    metadata_file = args.output_dir / "MATRIX_merged_metadata.json"
    with open(metadata_file, "w") as f:
        json.dump(metadata, f, indent=2)

    print("=" * 60)
    print(f"SUMMARY: {total_neurons} total neurons merged from {len(model_dirs)} models")
    print(f"Output: {args.output_dir}")
    print("=" * 60)


if __name__ == "__main__":
    main()
