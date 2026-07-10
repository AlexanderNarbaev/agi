#!/usr/bin/env bash
set -euo pipefail

# MATRIX Background Model Downloader
# Downloads and converts open-source models to Avro format for MPDT neurons
# Usage: ./scripts/download_models.sh [model_name|all]

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MODELS_DIR="$PROJECT_DIR/models"
PRETRAINED_DIR="$MODELS_DIR/pretrained"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log_ok() { echo -e "${GREEN}[OK]${NC} $*"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_err() { echo -e "${RED}[ERR]${NC} $*"; }

# Model definitions: name|huggingface_id|params
MODELS=(
  "SmolLM2-360M|HuggingFaceTB/SmolLM2-360M|360M"
  "Qwen2.5-1.5B|Qwen/Qwen2.5-1.5B|1.5B"
  "Qwen3-0.6B|Qwen/Qwen3-0.6B|0.6B"
  "Qwen3-1.7B|Qwen/Qwen3-1.7B|1.7B"
  "DeepSeek-R1-Distill-Qwen-1.5B|deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B|1.5B"
  "Gemma-3-1B|google/gemma-3-1b-it|1B"
  "Llama-3.2-3B|meta-llama/Llama-3.2-3B|3B"
  "Phi-4-mini|microsoft/Phi-4-mini-instruct|3.8B"
  "Yi-1.5-Chat|01-ai/Yi-1.5-Chat-6B|6B"
  "Mistral-7B|MistralAI/Mistral-7B-v0.3|7B"
)

download_model() {
  local name="$1"
  local hf_id="$2"
  local params="$3"
  
  echo ""
  echo "=========================================="
  echo "Downloading: $name ($params)"
  echo "HuggingFace: $hf_id"
  echo "=========================================="
  
  local model_dir="$MODELS_DIR/$name"
  local output_dir="$PRETRAINED_DIR/$(echo "$name" | tr '[:upper:]' '[:lower:]')"
  
  # Skip if already converted
  if [ -d "$output_dir" ] && [ "$(ls -1 "$output_dir"/*_layer*_neurons.avro 2>/dev/null | wc -l)" -gt 0 ]; then
    log_ok "$name already converted, skipping"
    return 0
  fi
  
  # Download from HuggingFace
  if [ ! -d "$model_dir" ]; then
    log_warn "Downloading $name from HuggingFace..."
    python3 -c "
from huggingface_hub import snapshot_download
snapshot_download('$hf_id', local_dir='$model_dir', ignore_patterns=['*.gguf'])
print('Download complete')
" 2>&1 | tail -3
    log_ok "Downloaded $name"
  else
    log_ok "$name already downloaded"
  fi
  
  # Find safetensors file
  local safetensors_file
  safetensors_file=$(find "$model_dir" -name "*.safetensors" -type f | head -1)
  
  if [ -z "$safetensors_file" ]; then
    log_err "No safetensors file found for $name"
    return 1
  fi
  
  # Convert to Avro
  log_warn "Converting $name to Avro format..."
  mkdir -p "$output_dir"
  python3 "$PROJECT_DIR/scripts/pretrain_neurons.py" \
    --model-path "$safetensors_file" \
    --source-model "$hf_id" \
    --k 16 \
    --neurons-per-layer 30 \
    --layers 6 \
    --output-dir "$output_dir" 2>&1 | tail -5
  
  local neuron_count
  neuron_count=$(ls -1 "$output_dir"/*_layer*_neurons.avro 2>/dev/null | wc -l)
  log_ok "Converted $name: $neuron_count layer files"
}

merge_all() {
  echo ""
  echo "=========================================="
  echo "Merging all pretrained neurons..."
  echo "=========================================="
  python3 "$PROJECT_DIR/scripts/merge_pretrained_neurons.py" \
    --output-dir "$PRETRAINED_DIR/merged" 2>&1 | tail -5
  log_ok "Merge complete"
}

main() {
  local target="${1:-all}"
  
  echo "MATRIX Model Downloader"
  echo "Project: $PROJECT_DIR"
  echo "Target: $target"
  
  if [ "$target" = "all" ]; then
    for model_def in "${MODELS[@]}"; do
      IFS='|' read -r name hf_id params <<< "$model_def"
      download_model "$name" "$hf_id" "$params" || log_err "Failed: $name"
    done
    merge_all
  else
    # Find specific model
    for model_def in "${MODELS[@]}"; do
      IFS='|' read -r name hf_id params <<< "$model_def"
      if [ "$(echo "$name" | tr '[:upper:]' '[:lower:]')" = "$(echo "$target" | tr '[:upper:]' '[:lower:]')" ]; then
        download_model "$name" "$hf_id" "$params"
        return 0
      fi
    done
    log_err "Unknown model: $target"
    echo "Available models:"
    for model_def in "${MODELS[@]}"; do
      IFS='|' read -r name hf_id params <<< "$model_def"
      echo "  - $name ($params)"
    done
    return 1
  fi
  
  echo ""
  echo "=========================================="
  echo "All models processed!"
  echo "=========================================="
  echo ""
  echo "Converted models:"
  for dir in "$PRETRAINED_DIR"/*/; do
    [ -d "$dir" ] || continue
    local name
    name=$(basename "$dir")
    local count
    count=$(ls -1 "$dir"/*_layer*_neurons.avro 2>/dev/null | wc -l)
    local size
    size=$(du -sh "$dir" 2>/dev/null | cut -f1)
    echo "  $name: $count layers, $size"
  done
}

main "$@"
