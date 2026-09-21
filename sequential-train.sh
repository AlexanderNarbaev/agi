#!/usr/bin/env bash
# sequential-train.sh — Wave 16: Sequentially download HF models, convert weights to MPDT neurons,
# save neurons, then DELETE the downloaded safetensors files.
#
# The output is a single growing set of neurons in /data/models/pretrained/<model>/
# (Avro format), with all original HF weight files deleted.
#
# Usage: ./sequential-train.sh [start_idx]
#
# Configuration:
#   MODELS — ordered list of model IDs (TINY first, then MEDIUM/LARGE if disk allows).
#   HF_TOKEN — optional, for private models.
#   MAX_DISK_GB — stop downloading if free disk < this.
#   NEURONS_PER_LAYER / K — neuron extraction parameters.
set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MINIKUBE="minikube"
PRETRAINED_DIR="/data/models/pretrained"
CACHE_DIR="/data/models/cache"
START_IDX="${1:-0}"

# Model pipeline — TINY first (less disk), then MEDIUM.
MODELS=(
    "HuggingFaceTB/SmolLM2-135M"          # 257M, base SmolLM2
    "Qwen/Qwen2.5-0.5B"                   # 954M, already downloaded
    "Qwen/Qwen3-0.6B"                     # 600M
    "Qwen/Qwen2.5-1.5B"                   # 1.5B
    "Qwen/Qwen3-1.7B"                     # 1.7B
    "deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B"   # 1.5B distilled
)

MAX_DISK_GB="${MAX_DISK_GB:-10}"
NEURONS_PER_LAYER="${NEURONS_PER_LAYER:-30}"
K="${K:-16}"

log() { printf '%s [seq-train] %s\n' "$(date +%H:%M:%S)" "$*"; }
err() { printf '%s [seq-train] ERROR: %s\n' "$(date +%H:%M:%S)" "$*" >&2; }

check_disk() {
    local free_mb
    free_mb=$(docker exec "$MINIKUBE" sh -c "df -m /data | tail -1 | awk '{print \$4}'" 2>/dev/null)
    if [ -z "$free_mb" ]; then
        err "Cannot check disk on minikube"
        return 1
    fi
    local free_gb=$((free_mb / 1024))
    if [ "$free_gb" -lt "$MAX_DISK_GB" ]; then
        err "Only ${free_gb}GB free, need >= ${MAX_DISK_GB}GB"
        return 1
    fi
    log "Disk free: ${free_gb}GB"
    return 0
}

download_one() {
    local model_id="$1"
    local owner="${model_id%/*}"
    local name="${model_id#*/}"
    local hf_dir="models--${owner//\//_}--${name//\//_}"
    local local_snap="/home/alexandr-narbaev/.cache/huggingface/hub/${hf_dir}/snapshots"
    local cache="${CACHE_DIR}/${model_id//\//_}"
    log "Loading ${model_id} from local cache -> ${cache}"

    if [ ! -d "$local_snap" ]; then
        err "Local cache missing: $local_snap"
        return 1
    fi
    local src=$(ls -1d "$local_snap"/*/ 2>/dev/null | head -1)
    if [ -z "$src" ]; then
        err "No snapshot in $local_snap"
        return 1
    fi
    log "Source: $src"
    # Use tar pipe (docker cp needs parent dirs which docker exec mkdir makes buggy)
    docker exec "$MINIKUBE" mkdir -p "$cache"
    tar -C "$src" -cf - . | docker exec -i "$MINIKUBE" tar -C "$cache" -xf -
    docker exec "$MINIKUBE" ls -la "$cache"
}

extract_neurons() {
    local model_id="$1"
    local cache="${CACHE_DIR}/${model_id//\//_}"
    local model_name=$(echo "$model_id" | tr '/' '_' | tr '[:upper:]' '[:lower:]')
    local out_dir="${PRETRAINED_DIR}/${model_name}"
    log "Extracting neurons from ${cache} -> ${out_dir}"

    # Strategy: copy safetensors into the standard /app/models/pretrained/<name>/
    # path in the K8s pod via hostPath, then trigger Quarkus reload via rolling restart.
    # PretrainedLoader on startup will convert weights → Avro neurons automatically.
    local pod_name=$(kubectl -n matrix get pods -l app=matrix-core -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
    if [ -z "$pod_name" ]; then
        err "No matrix-core pod found"
        return 1
    fi

    # Use kubectl cp to put the file into the pod
    log "Copying ${cache}/model.safetensors into pod ${pod_name} at /tmp/ingest/"
    kubectl -n matrix exec "$pod_name" -- mkdir -p /tmp/ingest
    # tar pipe through the pod
    tar -C "$cache" -cf - . | kubectl -n matrix exec -i "$pod_name" -- tar -C /tmp/ingest -xf -

    # Trigger Quarkus train-all subcommand (will pick up safetensors, save Avro, then we delete safetensors)
    log "Triggering train-all in pod..."
    kubectl -n matrix exec "$pod_name" -- java -jar /app/matrix-core.jar train-all --model-dir /tmp/ingest --weights-only 2>&1 | tail -10

    # Pull generated neurons from pod
    log "Pulling generated neurons from pod..."
    kubectl -n matrix cp "${pod_name}:/data/models/pretrained/${model_name}" "/tmp/neurons_${model_name}" 2>&1 | tail -3
    # Push to minikube
    docker exec "$MINIKUBE" mkdir -p "$out_dir"
    tar -C "/tmp/neurons_${model_name}" -cf - . | docker exec -i "$MINIKUBE" tar -C "$out_dir" -xf -
}

verify_neurons() {
    local model_id="$1"
    local model_name=$(echo "$model_id" | tr '/' '_' | tr '[:upper:]' '[:lower:]')
    local out_dir="${PRETRAINED_DIR}/${model_name}"
    if docker exec "$MINIKUBE" test -d "$out_dir"; then
        local count=$(docker exec "$MINIKUBE" sh -c "ls -1 $out_dir/*.avro 2>/dev/null | wc -l")
        log "Verified $out_dir: $count avro files"
        return 0
    fi
    err "Missing neuron dir: $out_dir"
    return 1
}

delete_safetensors() {
    local model_id="$1"
    local cache="${CACHE_DIR}/${model_id//\//_}"
    log "DELETING original safetensors: ${cache}"
    docker exec "$MINIKUBE" sh -c "
        rm -f '${cache}'/*.safetensors
        rm -f '${cache}'/config.json '${cache}'/tokenizer.json
        rm -rf '${cache}'
        echo 'remaining:' \$(ls /data/models/cache 2>/dev/null | wc -l)
    " 2>&1 | tail -3
}

# Main loop
log "Sequential HF trainer — starting at index $START_IDX"
log "Plan: $(printf '%s ' "${MODELS[@]:$START_IDX}")"
log "Constraints: max disk ${MAX_DISK_GB}GB, neurons/layer=$NEURONS_PER_LAYER, K=$K"

# First: ensure base dir exists
docker exec "$MINIKUBE" mkdir -p "$PRETRAINED_DIR" "$CACHE_DIR" 2>&1

processed=0
for ((i=START_IDX; i<${#MODELS[@]}; i++)); do
    model_id="${MODELS[$i]}"
    log "=== [$((i+1))/${#MODELS[@]}] $model_id ==="

    if ! check_disk; then
        log "Skipping — disk too low"
        break
    fi

    if ! download_one "$model_id"; then
        err "Download failed for $model_id"
        continue
    fi

    if ! extract_neurons "$model_id"; then
        err "Neuron extraction failed for $model_id"
        continue
    fi

    if ! verify_neurons "$model_id"; then
        err "Neuron verification failed"
        continue
    fi

    # CRITICAL: delete the original HF files to enforce "no pre-loaded models"
    delete_safetensors "$model_id"
    processed=$((processed + 1))
    log "  -> processed: $processed"
done

log "Done. Processed $processed models. Remaining:"
docker exec "$MINIKUBE" sh -c "ls -la $PRETRAINED_DIR/ && du -sh $PRETRAINED_DIR/" 2>&1