#!/usr/bin/env bash
# train_local.sh — Local HuggingFace model training (no minikube required)
#
# Downloads models from HuggingFace cache, extracts MPDT neurons, saves Avro files.
# Works with locally cached models (~/.cache/huggingface/hub/).
#
# Usage: ./scripts/train_local.sh [--all | --model MODEL_NAME]
#
# Examples:
#   ./scripts/train_local.sh --all           # Train all cached models
#   ./scripts/train_local.sh --model SmolLM2-135M  # Train specific model
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
HF_CACHE="$HOME/.cache/huggingface/hub"
OUTPUT_DIR="$PROJECT_ROOT/models/pretrained"
K="${K:-16}"
NEURONS_PER_LAYER="${NEURONS_PER_LAYER:-30}"
LAYERS="${LAYERS:-0}"  # 0 = auto-detect all

log() { printf '%s [train-local] %s\n' "$(date +%H:%M:%S)" "$*"; }
err() { printf '%s [train-local] ERROR: %s\n' "$(date +%H:%M:%S)" "$*" >&2; }

# Find all cached models with safetensors
find_cached_models() {
    local models=()
    for dir in "$HF_CACHE"/models--*; do
        [ -d "$dir" ] || continue
        local name=$(basename "$dir" | sed 's/models--//;s/--/\//')
        local safetensors=$(find "$dir" -name "model.safetensors" 2>/dev/null | head -1)
        if [ -n "$safetensors" ]; then
            models+=("$name|$safetensors")
        fi
    done
    printf '%s\n' "${models[@]}"
}

# Train a single model
train_model() {
    local model_id="$1"
    local model_path="$2"
    local model_name=$(echo "$model_id" | tr '/' '_' | tr '[:upper:]' '[:lower:]')

    log "=== Training: $model_id ==="
    log "  Path: $model_path"
    log "  Output: $OUTPUT_DIR/$model_name/"

    python3 "$PROJECT_ROOT/scripts/pretrain_neurons.py" \
        --model-path "$model_path" \
        --model-name "$model_name" \
        --source-model "$model_id" \
        --k "$K" \
        --neurons-per-layer "$NEURONS_PER_LAYER" \
        --layers "$LAYERS" \
        --output-dir "$OUTPUT_DIR/$model_name" \
        2>&1 | while IFS= read -r line; do
            log "  $line"
        done

    if [ $? -eq 0 ]; then
        log "✅ $model_id → $OUTPUT_DIR/$model_name/"
    else
        err "❌ Failed: $model_id"
    fi
}

# Main
main() {
    local mode="${1:---all}"

    log "Local HuggingFace trainer"
    log "HF cache: $HF_CACHE"
    log "Output: $OUTPUT_DIR"
    log "K=$K, neurons/layer=$NEURONS_PER_LAYER, layers=$LAYERS"

    mkdir -p "$OUTPUT_DIR"

    case "$mode" in
        --all)
            log "Training all cached models..."
            local count=0
            while IFS='|' read -r model_id model_path; do
                [ -z "$model_id" ] && continue
                train_model "$model_id" "$model_path"
                count=$((count + 1))
            done < <(find_cached_models)
            log "Done. Trained $count models."
            ;;
        --model)
            local target="$2"
            log "Looking for model: $target"
            local found=0
            while IFS='|' read -r model_id model_path; do
                if [[ "$model_id" == *"$target"* ]]; then
                    train_model "$model_id" "$model_path"
                    found=1
                fi
            done < <(find_cached_models)
            if [ "$found" -eq 0 ]; then
                err "Model not found in cache: $target"
                log "Available models:"
                find_cached_models | cut -d'|' -f1
                exit 1
            fi
            ;;
        *)
            echo "Usage: $0 [--all | --model MODEL_NAME]"
            exit 1
            ;;
    esac

    log "Summary:"
    ls -la "$OUTPUT_DIR"/*/  2>/dev/null | head -20
}

main "$@"
