#!/usr/bin/env bash
# MATRIX AGI — Quick Start Deployment Script
# Starts minikube, deploys everything, sets up port-forward and OpenWebUI.
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log()  { echo -e "${GREEN}==>${NC} $*"; }
warn() { echo -e "${YELLOW}==>${NC} $*"; }
err()  { echo -e "${RED}==>${NC} $*"; }

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODELS_DIR="${PROJECT_DIR}/models"
DATA_DIR="${PROJECT_DIR}/data/conversations"

log "MATRIX AGI Quick Start"
log "======================"
echo ""

# —— 1. Prerequisites ——
log "Step 1: Checking prerequisites..."
for cmd in minikube kubectl docker gradle java; do
    command -v $cmd >/dev/null 2>&1 || { err "Missing $cmd. Install it first."; exit 1; }
done
log "  All tools found."

# —— 2. Minikube ——
log "Step 2: Starting minikube..."
minikube status 2>/dev/null | grep -q "Running" || {
    log "  Starting minikube (Docker driver)..."
    minikube start --driver=docker --memory=4096 --cpus=2
}
log "  minikube is running."

# —— 3. Build ——
log "Step 3: Building uber-jar..."
cd "$PROJECT_DIR"
if [ ! -f "matrix-core/build/matrix-core-1.0.0-runner.jar" ]; then
    ./gradlew :matrix-core:quarkusBuild -Dquarkus.package.type=uber-jar --no-daemon -x test -x spotbugsMain
else
    log "  uber-jar already exists, skipping build."
fi

# —— 4. Docker image ——
log "Step 4: Building Docker image..."
eval $(minikube docker-env)
docker build -f Dockerfile.minikube -t matrix-core:latest . 2>&1 | tail -2
log "  Image built."

# —— 5. Mount models ——
log "Step 5: Mounting models directory..."
mkdir -p "$DATA_DIR"
# Kill any existing mounts
pkill -f "minikube mount" 2>/dev/null || true
sleep 2
nohup minikube mount "${MODELS_DIR}:/data/models" > /tmp/minikube-mount-models.log 2>&1 &
sleep 3
nohup minikube mount "${DATA_DIR}:/data/conversations" > /tmp/minikube-mount-conv.log 2>&1 &
sleep 3
log "  Models mounted: /data/models"
log "  Conversations mounted: /data/conversations"

# —— 6. Deploy infrastructure ——
log "Step 6: Deploying infrastructure..."
kubectl apply -f infra/k8s/minikube/namespace.yaml --wait=false
kubectl apply -f infra/k8s/minikube/postgres.yaml
kubectl apply -f infra/k8s/minikube/redis.yaml
log "  Infrastructure applied."

# —— 7. Deploy matrix-core ——
log "Step 7: Deploying matrix-core..."
kubectl delete deployment matrix-core -n matrix --ignore-not-found 2>/dev/null || true
sleep 3
kubectl apply -f infra/k8s/minikube/matrix-core.yaml
log "  Deployment applied."

# —— 8. Wait for readiness ——
log "Step 8: Waiting for pods to be ready..."
kubectl wait --for=condition=ready pod -l app=postgres -n matrix --timeout=120s 2>/dev/null
kubectl wait --for=condition=ready pod -l app=redis -n matrix --timeout=120s 2>/dev/null
kubectl wait --for=condition=ready pod -l app=matrix-core -n matrix --timeout=120s 2>/dev/null
log "  All pods ready."

# —— 9. Port-forward ——
log "Step 9: Setting up port-forward..."
pkill -f "kubectl port-forward.*matrix-core" 2>/dev/null || true
sleep 1
nohup kubectl port-forward -n matrix svc/matrix-core 9091:9091 > /tmp/pf-matrix.log 2>&1 &
sleep 3
log "  Port-forward: localhost:9091 → matrix-core:9091"

# —— 10. Smoke test ——
log "Step 10: Smoke test..."
sleep 5
HEALTH=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:9091/q/health 2>/dev/null || echo "000")
if [ "$HEALTH" = "200" ]; then
    log "  Health check: PASS"
    RESPONSE=$(curl -s -X POST http://localhost:9091/v1/chat/completions \
        -H "Content-Type: application/json" \
        -d '{"model":"M.A.T.R.I.X.","messages":[{"role":"user","content":"Hi"}]}' 2>/dev/null)
    if echo "$RESPONSE" | grep -q "choices"; then
        CONTENT=$(echo "$RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['choices'][0]['message']['content'][:60])" 2>/dev/null)
        log "  Chat API: PASS ($CONTENT)"
    else
        warn "  Chat API: UNEXPECTED RESPONSE"
    fi
else
    err "  Health check: FAIL (HTTP $HEALTH)"
fi
echo ""

# —— Summary ——
echo "========================================="
echo -e "${GREEN}  MATRIX AGI deployed!${NC}"
echo "========================================="
echo ""
echo "  Chat API:    http://localhost:9091/v1/chat/completions"
echo "  Status:      http://localhost:9091/v1/chat/status"
echo "  Health:      http://localhost:9091/q/health"
echo "  Metrics:     http://localhost:9091/metrics"
echo ""
echo "  Quick test:"
echo "  curl -X POST http://localhost:9091/v1/chat/completions \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"model\":\"M.A.T.R.I.X.\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}'"
echo ""
echo "  To stop:"
echo "    pkill -f 'kubectl port-forward|minikube mount'"
echo "    minikube stop"
