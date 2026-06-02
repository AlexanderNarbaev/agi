#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

case "${1:-help}" in
  up)
    echo "==> Starting MATRIX local stack..."
    docker compose -f "$PROJECT_DIR/infra/docker-compose.yml" up -d
    echo "==> Prometheus: http://localhost:9090"
    echo "==> Jaeger:     http://localhost:16686"
    echo "==> Grafana:    http://localhost:3000"
    ;;
  down)
    echo "==> Stopping MATRIX local stack..."
    docker compose -f "$PROJECT_DIR/infra/docker-compose.yml" down
    ;;
  status)
    docker compose -f "$PROJECT_DIR/infra/docker-compose.yml" ps
    ;;
  build)
    echo "==> Building matrix-core..."
    (cd "$PROJECT_DIR" && ./gradlew :matrix-core:quarkusBuild -Dquarkus.package.jar.type=uber-jar)
    ;;
  test)
    echo "==> Running all tests..."
    (cd "$PROJECT_DIR" && ./gradlew clean test)
    ;;
  demo)
    echo "==> Building and running system demo..."
    (cd "$PROJECT_DIR" && ./gradlew :matrix-core:quarkusBuild -Dquarkus.package.jar.type=uber-jar -q)
    java -jar "$PROJECT_DIR/matrix-core/build/matrix-core-"*-runner.jar demo
    ;;
  simulate)
    echo "==> Running GridWorld simulation..."
    (cd "$PROJECT_DIR" && ./gradlew :matrix-core:quarkusBuild -Dquarkus.package.jar.type=uber-jar -q)
    java -jar "$PROJECT_DIR/matrix-core/build/matrix-core-"*-runner.jar simulate "${@:2}"
    ;;
  docker)
    echo "==> Building Docker image..."
    docker build -t ghcr.io/matrix-ai/matrix-core:latest "$PROJECT_DIR"
    ;;
  *)
    echo "MATRIX Development Script"
    echo ""
    echo "Usage: $0 <command>"
    echo ""
    echo "Commands:"
    echo "  up          Start local stack (Prometheus, Jaeger, Grafana)"
    echo "  down        Stop local stack"
    echo "  status      Show container status"
    echo "  build       Build matrix-core uber-jar"
    echo "  test        Run all tests"
    echo "  demo        Run full system demo"
    echo "  simulate    Run GridWorld simulation (pass args after)"
    echo "  docker      Build Docker image"
    ;;
esac
