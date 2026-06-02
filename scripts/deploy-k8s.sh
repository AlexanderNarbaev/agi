#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
K8S_DIR="$PROJECT_DIR/infra/k8s"

ENV="${1:-staging}"
COMMAND="${2:-apply}"

case "$COMMAND" in
  apply)
    echo "==> Deploying MATRIX to $ENV environment..."
    kubectl apply -k "$K8S_DIR/overlays/$ENV"
    echo "==> Waiting for deployment rollout..."
    kubectl rollout status deployment/matrix-core -n matrix --timeout=120s
    echo "==> Deployment complete."
    ;;
  delete)
    echo "==> Removing MATRIX from $ENV environment..."
    kubectl delete -k "$K8S_DIR/overlays/$ENV" --ignore-not-found
    echo "==> Cleanup complete."
    ;;
  status)
    echo "==> MATRIX status in $ENV:"
    kubectl get all -n matrix
    echo ""
    kubectl get hpa -n matrix
    ;;
  logs)
    kubectl logs -n matrix -l app=matrix-core --tail=100 -f
    ;;
  *)
    echo "Usage: $0 <env> <command>"
    echo "  env: staging (default)"
    echo "  commands: apply, delete, status, logs"
    exit 1
    ;;
esac
