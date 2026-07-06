#!/usr/bin/env bash
set -euo pipefail

# MATRIX Minikube Launcher
# Запускает всю инфраструктуру в minikube K8s кластере
# Сервисы доступны через NodePort + DNS записи

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
K8S_DIR="$PROJECT_DIR/infra/k8s/minikube"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m'

log_ok()   { echo -e "  ${GREEN}[OK]${NC} $*"; }
log_warn() { echo -e "  ${YELLOW}[WARN]${NC} $*"; }
log_err()  { echo -e "  ${RED}[ERR]${NC} $*"; }
info()     { echo -e "  $*"; }

cmd_start() {
  echo -e "${BOLD}MATRIX Minikube Launcher${NC}"
  echo "Project: $PROJECT_DIR"
  echo ""

  # 1. Start minikube
  echo -e "${BOLD}=== Stage 1/7: Start minikube ===${NC}"
  if minikube status 2>/dev/null | grep -q "Running"; then
    log_ok "minikube already running"
  else
    info "Starting minikube (4 CPUs, 8GB RAM)..."
    minikube start --cpus 4 --memory 8192 --driver=docker
    log_ok "minikube started"
  fi

  # 2. Build matrix-core image into minikube
  echo -e "\n${BOLD}=== Stage 2/7: Build matrix-core image ===${NC}"
  info "Building uber-jar..."
  (cd "$PROJECT_DIR" && ./gradlew :matrix-core:clean :matrix-core:quarkusBuild -Dquarkus.package.jar.type=uber-jar --no-build-cache) >/dev/null 2>&1
  log_ok "Uber-jar built"

  info "Building Docker image (in minikube)..."
  eval "$(minikube docker-env)"
  docker build -t matrix-core:latest -f "$PROJECT_DIR/Dockerfile" "$PROJECT_DIR"
  log_ok "Docker image matrix-core:latest built"

  # 3. Build Paper server image into minikube
  echo -e "\n${BOLD}=== Stage 3/7: Build Paper server image ===${NC}"
  info "Building Spigot plugin..."
  (cd "$PROJECT_DIR" && ./gradlew :matrix-spigot:clean :matrix-spigot:build) >/dev/null 2>&1
  log_ok "Spigot plugin built"

  info "Building Paper Docker image (in minikube)..."
  eval "$(minikube docker-env)"
  docker build -t matrix-paper:latest -f "$PROJECT_DIR/matrix-spigot/Dockerfile" "$PROJECT_DIR"
  log_ok "Docker image matrix-paper:latest built"

  # 4. Deploy K8s manifests
  echo -e "\n${BOLD}=== Stage 4/7: Deploy to K8s ===${NC}"
  kubectl apply -k "$K8S_DIR"
  log_ok "Manifests applied"

  # 5. Wait for pods
  echo -e "\n${BOLD}=== Stage 5/7: Wait for pods ===${NC}"
  info "Waiting for matrix-core pod..."
  kubectl wait --for=condition=ready pod -l app=matrix-core -n matrix --timeout=120s 2>/dev/null || {
    log_warn "matrix-core not ready yet, checking status..."
    kubectl get pods -n matrix
  }
  info "Waiting for paper-server pod..."
  kubectl wait --for=condition=ready pod -l app=paper-server -n matrix --timeout=120s 2>/dev/null || {
    log_warn "paper-server not ready yet, checking status..."
    kubectl get pods -n matrix
  }
  log_ok "Pods running"
  kubectl get pods -n matrix

  # 6. Setup DNS entries
  echo -e "\n${BOLD}=== Stage 6/7: DNS setup ===${NC}"
  MINIKUBE_IP=$(minikube ip)
  info "minikube IP: $MINIKUBE_IP"

  # Add DNS entries to /etc/hosts (requires sudo)
  for host in matrix.local grafana.local prometheus.local minecraft.local; do
    if grep -q "$host" /etc/hosts 2>/dev/null; then
      log_ok "$host already in /etc/hosts"
    else
      info "Adding $host → $MINIKUBE_IP to /etc/hosts"
      echo "$MINIKUBE_IP $host" | sudo tee -a /etc/hosts >/dev/null 2>&1 || {
        log_warn "Cannot add to /etc/hosts (no sudo). Add manually:"
        log_warn "  echo '$MINIKUBE_IP $host' | sudo tee -a /etc/hosts"
      }
    fi
  done

  # 7. Access info
  echo -e "\n${BOLD}=== Stage 7/7: Access info ===${NC}"
  echo ""
  echo -e "${BOLD}${GREEN}=== MATRIX Stack (minikube) ===${NC}"
  echo "  matrix-core:  http://matrix.local:30091"
  echo "  Grafana:      http://grafana.local:30300"
  echo "  Prometheus:   http://prometheus.local:30090"
  echo "  Paper MC:     minecraft.local:32565"
  echo ""
  echo "  Or use minikube service:"
  echo "    minikube service matrix-core -n matrix"
  echo "    minikube service grafana -n matrix"
  echo "    minikube service prometheus -n matrix"
  echo "    minikube service paper-server -n matrix"
  echo ""
  echo "  OpenAI Chat API:"
  echo "    curl -X POST http://matrix.local:30091/v1/chat/completions \\"
  echo "      -H 'Content-Type: application/json' \\"
  echo "      -d '{\"model\":\"mpdt-qwen\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}'"
  echo ""
  echo "  kubectl get pods -n matrix  # check status"
  echo "  minikube stop               # stop cluster"
}

cmd_stop() {
  echo -e "${BOLD}Stopping minikube...${NC}"
  minikube stop
  log_ok "minikube stopped"
}

cmd_status() {
  echo -e "${BOLD}MATRIX Minikube Status${NC}"
  minikube status
  echo ""
  kubectl get pods -n matrix
  echo ""
  kubectl get svc -n matrix
}

cmd_tunnel() {
  echo -e "${BOLD}Port forwarding to matrix-core...${NC}"
  echo "  matrix-core:  http://localhost:9091"
  echo "  Press Ctrl+C to stop"
  kubectl port-forward svc/matrix-core 9091:9091 -n matrix
}

case "${1:-start}" in
  start)  cmd_start ;;
  stop)   cmd_stop ;;
  status) cmd_status ;;
  tunnel) cmd_tunnel ;;
  *)
    echo "Usage: $0 {start|stop|status|tunnel}"
    exit 1
    ;;
esac
