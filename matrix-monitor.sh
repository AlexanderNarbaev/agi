#!/usr/bin/env bash
# matrix-monitor.sh — Live monitor for M.A.T.R.I.X. training & health.
#
# Usage:
#   ./matrix-monitor.sh                  # default: matrix-core:30091, refresh 5s
#   MATRIX_URL=http://host:port ./matrix-monitor.sh
#   REFRESH=2 ./matrix-monitor.sh
#
# Endpoints used:
#   GET  /api/v1/health                  — health
#   POST /api/v1/agent/train             — quick train probe
#   GET  /api/v1/agent/neurons/system    — neuron list (count)
# Background evolution log is read from K8s via kubectl.
#
# Stop with Ctrl+C.

set -u
MATRIX_URL="${MATRIX_URL:-http://192.168.49.2:30091}"
REFRESH="${REFRESH:-5}"
KUBE_NS="${KUBE_NS:-matrix}"
POD_SELECTOR="${POD_SELECTOR:-app=matrix-core}"

if ! command -v curl >/dev/null 2>&1; then
  echo "curl not found" >&2; exit 1
fi

# ANSI
BOLD=$'\033[1m'; DIM=$'\033[2m'; RST=$'\033[0m'
GRN=$'\033[32m'; RED=$'\033[31m'; YLW=$'\033[33m'; CYN=$'\033[36m'

# Ctrl+C cleanup
cleanup() {
  printf "\n%s[monitor stopped]%s\n" "$DIM" "$RST"
  exit 0
}
trap cleanup INT TERM

# Header
printf "%s%s M.A.T.R.I.X. live monitor %s\n" "$BOLD" "$CYN" "$RST"
printf "%surl=%s refresh=%ss pod=%s/%s%s\n\n" "$DIM" "$MATRIX_URL" "$REFRESH" "$KUBE_NS" "$POD_SELECTOR" "$RST"

iteration=0
while true; do
  iteration=$((iteration + 1))
  ts=$(date '+%Y-%m-%d %H:%M:%S')

  # 1. Health
  health_json=$(curl -s -m 3 "$MATRIX_URL/api/v1/health" 2>/dev/null)
  if [ -n "$health_json" ]; then
    status=$(printf '%s' "$health_json" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('status','?'))" 2>/dev/null)
    version=$(printf '%s' "$health_json" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('version','?'))" 2>/dev/null)
    loops=$(printf '%s' "$health_json" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('activeLoops','?'))" 2>/dev/null)
    color="$GRN"; [ "$status" = "UP" ] || color="$RED"
    printf "%s[%s]%s Health: %s%s%s v%s activeLoops=%s\n" "$DIM" "$ts" "$RST" "$color" "$status" "$RST" "$version" "$loops"
  else
    printf "%s[%s]%s Health: %sUNREACHABLE%s\n" "$DIM" "$ts" "$RST" "$RED" "$RST"
  fi

  # 2. Neurons
  neurons_json=$(curl -s -m 3 "$MATRIX_URL/api/v1/agent/neurons/system" 2>/dev/null)
  if [ -n "$neurons_json" ]; then
    count=$(printf '%s' "$neurons_json" | python3 -c "import json,sys; d=json.load(sys.stdin); print(len(d) if isinstance(d, list) else 'n/a')" 2>/dev/null)
    printf "  %sneurons (system role):%s %s\n" "$DIM" "$RST" "$count"
  fi

  # 3. Quick train probe (only every 3rd iteration to avoid load)
  if [ $((iteration % 3)) -eq 0 ]; then
    train_json=$(curl -s -m 15 -X POST -H "Content-Type: application/json" \
      -d '{"generations":1,"population":4,"k":3}' \
      "$MATRIX_URL/api/v1/agent/train" 2>/dev/null)
    if [ -n "$train_json" ]; then
      bf=$(printf '%s' "$train_json" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('bestFitness','?'))" 2>/dev/null)
      gen=$(printf '%s' "$train_json" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('generations','?'))" 2>/dev/null)
      st=$(printf '%s' "$train_json" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('status','?'))" 2>/dev/null)
      printf "  %slast train probe:%s bestFitness=%s generations=%s status=%s\n" "$YLW" "$RST" "$bf" "$gen" "$st"
    fi
  fi

  # 4. Background evolution step (from K8s logs)
  if command -v kubectl >/dev/null 2>&1; then
    pod=$(kubectl -n "$KUBE_NS" get pod -l "$POD_SELECTOR" -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
    if [ -n "$pod" ]; then
      last_evo=$(kubectl -n "$KUBE_NS" logs "$pod" 2>/dev/null | grep "Evolution step" | tail -1)
      if [ -n "$last_evo" ]; then
        printf "  %sevolution:%s %s\n" "$CYN" "$RST" "$last_evo"
      fi
    fi
  fi

  printf "\n"
  sleep "$REFRESH"
done
