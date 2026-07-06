#!/usr/bin/env bash
set -euo pipefail

# ─── MATRIX Full-Stack Launcher ─────────────────────────────────────────────
# Usage: ./matrix-full.sh [start|stop|status]
#   start  – (default) launch the entire MATRIX stack
#   stop   – graceful shutdown of all components
#   status – check which services are running
# ─────────────────────────────────────────────────────────────────────────────

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
readonly PID_FILE="$PROJECT_DIR/.matrix-full.pids"
readonly COMPOSE_FILE="$PROJECT_DIR/infra/docker-compose.yml"
readonly JAR_GLOB="$PROJECT_DIR/matrix-core/build/matrix-core-*-runner.jar"
readonly NATIVE_BINARY="$PROJECT_DIR/matrix-core/build/matrix-core-1.0.0-runner"
readonly MC_SERVER_DIR="$PROJECT_DIR/minecraft-server"
readonly AUTH_MOCK="$HOME/.local/share/matrix-auth/yggdrasil-mock.py"
readonly MC_CLIENT="$HOME/.local/bin/mc-direct"

# ─── Colors ─────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m' # No Color

log_ok()    { echo -e "  ${GREEN}[OK]${NC} $*" >&2; }
log_warn()  { echo -e "  ${YELLOW}[WARN]${NC} $*" >&2; }
log_err()   { echo -e "  ${RED}[ERR]${NC} $*" >&2; }
stage_hdr() { echo -e "\n${BOLD}=== Stage $* ===${NC}" >&2; }
info()      { echo -e "  $*" >&2; }

# ─── Port management ─────────────────────────────────────────────────────────
# Each service: default port, env var name for docker-compose, description
declare -A PORT_MAP=(
  ["PG"]="5432"   ["REDIS"]="6379"   ["KAFKA"]="9092"  ["MINIO"]="9000"
  ["PROM"]="9090" ["JAEGER"]="16686" ["OTLP"]="4317"   ["GRAFANA"]="3000"
)
MC_PORT=25565  AUTH_PORT=25567  CORE_PORT=9091

check_port() { ss -tln 2>/dev/null | grep -q ":$1 " && return 0 || return 1; }

assign_port() {
  local default=$1
  if check_port "$default"; then
    echo $((default + 10000))
  else
    echo "$default"
  fi
}

allocate_ports() {
  set +e  # disable exit-on-error for port checks
  local changed=0
  for var in "${!PORT_MAP[@]}"; do
    local default="${PORT_MAP[$var]}"
    local assigned
    assigned=$(assign_port "$default" 2>/dev/null || echo "$default")
    PORT_MAP[$var]="$assigned"
    if [ "$assigned" != "$default" ]; then
      log_warn "$default→$assigned ($var)"
      ((changed++)) || true
    fi
  done
  MC_PORT=$(assign_port "$MC_PORT" 2>/dev/null || echo "$MC_PORT")
  AUTH_PORT=$(assign_port "$AUTH_PORT" 2>/dev/null || echo "$AUTH_PORT")
  CORE_PORT=$(assign_port "$CORE_PORT" 2>/dev/null || echo "$CORE_PORT")
  [ "$changed" -gt 0 ] && info "Using alternative ports (+10000 offset)"
  set -e
}

write_env() {
  cat > "$PROJECT_DIR/infra/.env" <<EOF || true
PG_PORT=${PORT_MAP[PG]}
REDIS_PORT=${PORT_MAP[REDIS]}
KAFKA_PORT=${PORT_MAP[KAFKA]}
MINIO_PORT=${PORT_MAP[MINIO]}
PROM_PORT=${PORT_MAP[PROM]}
JAEGER_PORT=${PORT_MAP[JAEGER]}
OTLP_PORT=${PORT_MAP[OTLP]}
GRAFANA_PORT=${PORT_MAP[GRAFANA]}
EOF
}

cleanup_previous() {
  echo -e "  ${YELLOW}Cleaning up previous MATRIX containers and volumes...${NC}"
  # Always run down — handles running AND stopped containers
  docker compose -f "$COMPOSE_FILE" down -v --remove-orphans 2>/dev/null || true
  sleep 2
  # Force-remove any leftover named volumes (belt and suspenders)
  for vol in infra_postgres_data infra_redis_data infra_minio_data infra_prometheus_data infra_grafana_data; do
    docker volume rm -f "$vol" 2>/dev/null || true
  done
  sleep 1
}

# ─── Help ───────────────────────────────────────────────────────────────────
usage() {
  echo "MATRIX Full-Stack Launcher"
  echo ""
  echo "Usage: $0 <command>"
  echo ""
  echo "Commands:"
  echo "  start    Launch all components (default)"
  echo "  stop     Graceful shutdown of all components"
  echo "  status   Show which services are running"
}

# ─── Prerequisites ──────────────────────────────────────────────────────────
check_prereqs() {
  stage_hdr "Prerequisites"

  # Java 21+
  if ! command -v java &>/dev/null; then
    log_err "java: not found"
    return 1
  fi
  local java_ver
  java_ver=$(java -version 2>&1 | head -1 | grep -oP '\d+' | head -1 || true)
  if [ -z "$java_ver" ] || [ "$java_ver" -lt 21 ]; then
    log_err "java: version 21+ required (found: ${java_ver:-unknown})"
    return 1
  fi
  log_ok "java: $(java -version 2>&1 | head -1)"

  # Docker
  if ! command -v docker &>/dev/null; then
    log_err "docker: not found"
    return 1
  fi
  if ! docker info &>/dev/null; then
    log_err "docker: daemon not running or no permissions"
    return 1
  fi
  log_ok "docker: $(docker --version)"

  # Python3
  if ! command -v python3 &>/dev/null; then
    log_err "python3: not found"
    return 1
  fi
  log_ok "python3: $(python3 --version)"
}

# ─── Wait for TCP port ──────────────────────────────────────────────────────
wait_for_port() {
  local host="$1" port="$2" label="$3" max_wait="${4:-60}"
  info "Waiting for $label ($host:$port)..."
  local elapsed=0
  while [ "$elapsed" -lt "$max_wait" ]; do
    if timeout 2 bash -c "echo >/dev/tcp/$host/$port" 2>/dev/null; then
      log_ok "$label is ready ($host:$port)"
      return 0
    fi
    sleep 1
    ((elapsed++))
  done
  log_err "$label did not become ready within ${max_wait}s"
  return 1
}

# ─── Infrastructure ─────────────────────────────────────────────────────────
start_infra() {
  stage_hdr "1/6: Infrastructure"
  info "Starting Docker services..."
  docker compose -f "$COMPOSE_FILE" up -d
}

wait_infra() {
  stage_hdr "2/6: Service Readiness"

  # PostgreSQL
  if command -v pg_isready &>/dev/null; then
    info "Waiting for PostgreSQL (pg_isready)..."
    local elapsed=0
    while [ "$elapsed" -lt 60 ]; do
      if pg_isready -h localhost -p "${PORT_MAP[PG]}" -U matrix -d matrix -q 2>/dev/null; then
        log_ok "PostgreSQL is ready"
        break
      fi
      sleep 1
      ((elapsed++))
    done
    [ "$elapsed" -ge 60 ] && log_err "PostgreSQL did not become ready within 60s" && return 1
  else
    log_warn "pg_isready not found, falling back to TCP check"
    wait_for_port localhost "${PORT_MAP[PG]}" "PostgreSQL" 60 || return 1
  fi

  # Kafka / Redpanda
  wait_for_port localhost "${PORT_MAP[KAFKA]}" "Kafka/Redpanda" 60 || return 1

  # MinIO
  wait_for_port localhost "${PORT_MAP[MINIO]}" "MinIO" 60 || return 1

  # Redis
  info "Waiting for Redis..."
  local elapsed=0
  while [ "$elapsed" -lt 60 ]; do
    if command -v redis-cli &>/dev/null; then
      if redis-cli ping 2>/dev/null | grep -q PONG; then
        log_ok "Redis is ready"
        return 0
      fi
    else
      if timeout 2 bash -c "echo 'ping' | nc -w1 localhost ${PORT_MAP[REDIS]}" 2>/dev/null | grep -qi '+PONG'; then
        log_ok "Redis is ready"
        return 0
      fi
      log_warn "redis-cli not found — using netcat fallback"
    fi
    sleep 1
    ((elapsed++))
  done
  log_err "Redis did not become ready within 60s"
  return 1
}

# ─── Build matrix-core ──────────────────────────────────────────────────────
build_matrix_core() {
  stage_hdr "3/6: Build matrix-core"

  # Check native binary
  if [ -f "$NATIVE_BINARY" ] && [ -x "$NATIVE_BINARY" ]; then
    log_ok "Native binary: $NATIVE_BINARY ($(du -h "$NATIVE_BINARY" | cut -f1))"
    echo "$NATIVE_BINARY"
    return 0
  fi

  local jar
  jar=$(ls $JAR_GLOB 2>/dev/null | head -1 || true)
  if [ -n "$jar" ]; then
    log_ok "Uber-jar: $(basename "$jar")"
    echo "$jar"
    return 0
  fi

  # Try native build if GraalVM available
  if command -v native-image &>/dev/null; then
    info "GraalVM detected — building native binary..."
    (cd "$PROJECT_DIR" && ./gradlew build -Dquarkus.native.enabled=true --no-daemon)
    if [ -f "$NATIVE_BINARY" ] && [ -x "$NATIVE_BINARY" ]; then
      log_ok "Native binary built"
      echo "$NATIVE_BINARY"
      return 0
    fi
    log_warn "Native build failed, falling back to JVM"
  fi

  info "Building uber-jar (force clean)..."
  (cd "$PROJECT_DIR" && ./gradlew :matrix-core:clean :matrix-core:quarkusBuild -Dquarkus.package.jar.type=uber-jar --no-build-cache) >/dev/null
  jar=$(ls $JAR_GLOB 2>/dev/null | tail -1 || true)
  [ -z "$jar" ] && log_err "Build failed: no binary or jar" && return 1
  log_ok "Built: $(basename "$jar")"
  echo "$jar"
}

# ─── Start matrix-core ──────────────────────────────────────────────────────
start_matrix_core() {
  local binary="$1"
  stage_hdr "4/6: Start matrix-core"

  if [[ "$binary" == *"-runner" ]] && [ -x "$binary" ]; then
    info "Launching native binary (port $CORE_PORT)..."
    DB_HOST=localhost DB_PORT="${PORT_MAP[PG]}" DB_PASS=matrix \
    QUARKUS_HTTP_PORT="$CORE_PORT" \
    KAFKA_BOOTSTRAP_SERVERS="localhost:${PORT_MAP[KAFKA]}" \
    QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT="http://localhost:${PORT_MAP[OTLP]}" \
    "$binary" &
  else
    info "Launching JVM (port $CORE_PORT)..."
    DB_HOST=localhost DB_PORT="${PORT_MAP[PG]}" DB_PASS=matrix \
    QUARKUS_HTTP_PORT="$CORE_PORT" \
    KAFKA_BOOTSTRAP_SERVERS="localhost:${PORT_MAP[KAFKA]}" \
    QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT="http://localhost:${PORT_MAP[OTLP]}" \
    java -jar "$binary" &
  fi
  local pid=$!
  echo "matrix-core:$pid" >> "$PID_FILE"
  log_ok "matrix-core PID: $pid"

  info "Waiting for health check (port $CORE_PORT)..."
  local elapsed=0
  while [ "$elapsed" -lt 120 ]; do
    if curl -sf http://localhost:$CORE_PORT/q/health/ready &>/dev/null; then
      log_ok "matrix-core is healthy (ready)"
      return 0
    fi
    sleep 2
    ((elapsed+=2))
  done
  log_err "matrix-core health check failed after 120s"
  return 1
}

# ─── Start Paper Minecraft Server ────────────────────────────────────────────
start_minecraft() {
  stage_hdr "5/6: Minecraft Server"
  if ! lsof -i:$MC_PORT &>/dev/null; then
    info "Starting Paper server..."
    cd "$MC_SERVER_DIR"
    java -Xmx2G -Xms1G -jar paper.jar --nogui > /dev/null 2>&1 &
    local mc_pid=$!
    echo "minecraft:$mc_pid" >> "$PID_FILE"
    log_ok "Paper server PID: $mc_pid"

    info "Waiting for server (port $MC_PORT)..."
    local elapsed=0
    while [ "$elapsed" -lt 60 ]; do
      lsof -i:$MC_PORT &>/dev/null && break
      sleep 2
      ((elapsed+=2))
    done
    if [ "$elapsed" -ge 60 ]; then
      log_warn "Minecraft server did not start within 60s (may still be loading)"
    else
      log_ok "Minecraft server: localhost:$MC_PORT"
    fi
    cd "$PROJECT_DIR"
  else
    log_ok "Minecraft server already running on :$MC_PORT"
  fi
}

# ─── Start Yggdrasil Auth ───────────────────────────────────────────────────
start_auth() {
  stage_hdr "6/6: Auth Mock + Client"
  # Kill any previous auth mock on port $AUTH_PORT
  kill "$(lsof -ti:$AUTH_PORT)" 2>/dev/null || true
  info "Starting Yggdrasil auth mock (port $AUTH_PORT)..."
  python3 "$AUTH_MOCK" $AUTH_PORT &
  local auth_pid=$!
  echo "auth-mock:$auth_pid" >> "$PID_FILE"
  sleep 1
  log_ok "Auth mock PID: $auth_pid"
}

# ─── Start Minecraft Client ─────────────────────────────────────────────────
start_client() {
  # Check if client jar exists
  if [ ! -f "$HOME/.minecraft/versions/1.20.4/1.20.4.jar" ]; then
    log_warn "Minecraft 1.20.4 not found. Opening HMCL to download..."
    log_warn "  Install 1.20.4, then re-run this script."
    java -jar "$HOME/.local/bin/hmcl.jar"
    return 1
  fi
  info "Launching Minecraft client (mc-direct)..."
  "$MC_CLIENT" &
  local client_pid=$!
  echo "client:$client_pid" >> "$PID_FILE"
  log_ok "Minecraft client PID: $client_pid"
}

# ─── Graceful Shutdown ──────────────────────────────────────────────────────
cleanup() {
  echo -e "\n${YELLOW}>>> Shutting down MATRIX...${NC}"

  # 1. Minecraft client
  if grep -q "^client:" "$PID_FILE" 2>/dev/null; then
    local pid
    pid=$(grep "^client:" "$PID_FILE" | cut -d: -f2)
    kill "$pid" 2>/dev/null && info "Stopped Minecraft client" || true
  fi

  # 2. Auth mock
  if grep -q "^auth-mock:" "$PID_FILE" 2>/dev/null; then
    local pid
    pid=$(grep "^auth-mock:" "$PID_FILE" | cut -d: -f2)
    kill "$pid" 2>/dev/null && info "Stopped auth mock" || true
    kill "$(lsof -ti:$AUTH_PORT)" 2>/dev/null || true
  fi

  # 3. Minecraft server
  if grep -q "^minecraft:" "$PID_FILE" 2>/dev/null; then
    local pid
    pid=$(grep "^minecraft:" "$PID_FILE" | cut -d: -f2)
    kill "$pid" 2>/dev/null && info "Stopped Minecraft server" || true
  fi

  # 4. matrix-core
  if grep -q "^matrix-core:" "$PID_FILE" 2>/dev/null; then
    local pid
    pid=$(grep "^matrix-core:" "$PID_FILE" | cut -d: -f2)
    kill "$pid" 2>/dev/null && info "Stopped matrix-core (PID $pid)" || true
  fi

  # 5. Docker infrastructure
  info "Stopping Docker services..."
  docker compose -f "$COMPOSE_FILE" down 2>/dev/null || true

  rm -f "$PID_FILE"
  echo -e "${GREEN}>>> MATRIX stopped.${NC}"
}

# ─── Stop ───────────────────────────────────────────────────────────────────
cmd_stop() {
  if [ ! -f "$PID_FILE" ]; then
    echo -e "${YELLOW}No PID file found. Stopping Docker services...${NC}"
    docker compose -f "$COMPOSE_FILE" down 2>/dev/null || true
    echo -e "${GREEN}Done.${NC}"
    exit 0
  fi
  cleanup
}

# ─── Status ─────────────────────────────────────────────────────────────────
cmd_status() {
  echo -e "${BOLD}MATRIX Stack Status${NC}"
  echo ""

  # Docker containers
  echo "Docker containers:"
  docker compose -f "$COMPOSE_FILE" ps 2>/dev/null || echo "  (none)"
  echo ""

  # Managed processes
  if [ -f "$PID_FILE" ]; then
    echo "Managed processes:"
    while IFS=: read -r name pid; do
      if kill -0 "$pid" 2>/dev/null; then
        echo -e "  ${GREEN}[RUNNING]${NC} $name (PID $pid)"
      else
        echo -e "  ${RED}[STALE]${NC}   $name (PID $pid — dead)"
      fi
    done < "$PID_FILE"
  else
    echo "No managed processes (no PID file)"
  fi

  # Port checks
  echo ""
  echo "Port listeners:"
  for svc in "$MC_PORT:Minecraft" "$AUTH_PORT:Auth Mock" "$CORE_PORT:matrix-core" \
             "${PORT_MAP[PG]}:PostgreSQL" "${PORT_MAP[KAFKA]}:Kafka" "${PORT_MAP[MINIO]}:MinIO" "${PORT_MAP[REDIS]}:Redis" \
             "9090:Prometheus" "3000:Grafana" "16686:Jaeger"; do
    local port="${svc%%:*}"
    local name="${svc#*:}"
    if lsof -i:"$port" &>/dev/null 2>&1; then
      echo -e "  ${GREEN}[:$port]${NC} $name"
    else
      echo -e "  ${YELLOW}[:$port]${NC} $name (off)"
    fi
  done
}

# ─── Start (default) ────────────────────────────────────────────────────────
cmd_start() {
  echo -e "${BOLD}${GREEN}MATRIX Full-Stack Launcher${NC}"
  echo "Project: $PROJECT_DIR"
  echo ""

  # Reset PID file
  : > "$PID_FILE"

  # Trap for graceful shutdown
  trap cleanup SIGINT SIGTERM

  check_prereqs || exit 1

  allocate_ports
  write_env

  cleanup_previous

  start_infra || exit 1

  wait_infra || exit 1

  local jar
  jar=$(build_matrix_core) || exit 1

  start_matrix_core "$jar" || exit 1

  start_minecraft || exit 1

  start_auth || exit 1

  start_client

  echo ""
  echo -e "${BOLD}${GREEN}=== MATRIX Stack is running ===${NC}"
  echo "  matrix-core:    http://localhost:$CORE_PORT"
  echo "  Minecraft srv:  localhost:$MC_PORT"
  echo "  Auth mock:      localhost:$AUTH_PORT"
  echo "  Prometheus:     http://localhost:${PORT_MAP[PROM]}"
  echo "  Jaeger:         http://localhost:${PORT_MAP[JAEGER]}"
  echo "  Grafana:        http://localhost:${PORT_MAP[GRAFANA]}"
  echo ""
  echo "Press Ctrl+C to stop all services."

  # Wait for any background process to exit or signal
  wait
}

# ─── Main ───────────────────────────────────────────────────────────────────
case "${1:-start}" in
  start)   cmd_start ;;
  stop)    cmd_stop ;;
  status)  cmd_status ;;
  -h|--help|help) usage ;;
  *)
    echo -e "${RED}Unknown command: $1${NC}"
    usage
    exit 1
    ;;
esac
