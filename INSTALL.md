# M.A.T.R.I.X. — Installation & Startup Guide

**Version:** v3.58
**Last updated:** 2026-07-26

---

## Prerequisites

### Hardware
- **CPU:** x86_64 or ARM64, 4+ cores recommended
- **RAM:** 8GB minimum, 16GB recommended
- **Disk:** 20GB minimum (50GB+ with all models)
- **Network:** Internet access for HuggingFace model downloads

### Software
- **OS:** Linux (Ubuntu 24.04+), macOS 13+, or Windows 11 with WSL2
- **Java:** OpenJDK 25 (GraalVM 25 recommended for native builds)
- **Docker:** 24+ with Docker Compose v2
- **Gradle:** 9.x (or use `./gradlew`)
- **Git:** 2.40+

### Optional
- **Kubernetes:** Minikube 1.35+ or any K8s cluster
- **CUDA:** For GPU acceleration (not required)

---

## Quick Start (5 minutes)

```bash
# 1. Clone the repository
git clone https://github.com/AlexanderNarbaev/agi.git
cd agi

# 2. Start infrastructure (PostgreSQL, Redis, Kafka)
docker compose up -d postgres redis kafka

# 3. Build and run the application
./gradlew :matrix-core:quarkusDev

# 4. Verify it's running
curl http://localhost:9091/q/health
```

The application will be available at:
- **REST API:** http://localhost:9091
- **Health check:** http://localhost:9091/q/health
- **Metrics:** http://localhost:9091/q/metrics

---

## Detailed Installation

### Step 1: Java Installation

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install -y openjdk-25-jdk

# macOS
brew install openjdk@25

# Verify
java --version
# openjdk 25.x.x
```

For GraalVM (native builds):
```bash
sdk install java 25.0.1-graal
sdk use java 25.0.1-graal
```

### Step 2: Docker Installation

```bash
# Ubuntu
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
# Log out and back in for group changes to take effect

# Verify
docker --version
docker compose version
```

### Step 3: Clone and Build

```bash
# Clone
git clone https://github.com/AlexanderNarbaev/agi.git
cd agi

# Build (first time, ~5-10 minutes)
./gradlew :matrix-core:build -x test

# Or run directly in dev mode
./gradlew :matrix-core:quarkusDev
```

### Step 4: Start Infrastructure

The project includes a `docker-compose.yml` for local development:

```bash
# Start PostgreSQL, Redis, Kafka
docker compose up -d postgres redis kafka

# Verify
docker compose ps
```

**Note on port conflicts:** The Docker Compose maps PostgreSQL to port **5433** (not 5432) to avoid conflicts with existing PostgreSQL installations. Redis uses 6379, Kafka uses 9092.

### Step 5: Run the Application

#### Option A: Dev Mode (with hot reload)
```bash
./gradlew :matrix-core:quarkusDev
```

#### Option B: Production Mode
```bash
# Build the runner JAR
./gradlew :matrix-core:build

# Run
java -jar matrix-core/build/quarkus-app/quarkus-run.jar
```

#### Option C: Native Build (fastest startup, smallest memory)
```bash
# Requires GraalVM 25
./gradlew :matrix-core:build -Dquarkus.native.enabled=true

# Run the native binary
./matrix-core/build/*-runner
```

### Step 6: Verify

```bash
# Health check
curl http://localhost:9091/q/health

# Models API (OpenAI-compatible)
curl http://localhost:9091/v1/models

# Chat API
curl -X POST http://localhost:9091/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"M.A.T.R.I.X.","messages":[{"role":"user","content":"Hello!"}]}'
```

---

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | localhost | PostgreSQL host |
| `DB_PORT` | 5433 | PostgreSQL port |
| `DB_USER` | matrix | PostgreSQL user |
| `DB_PASS` | matrix | PostgreSQL password |
| `QUARKUS_HTTP_PORT` | 9091 | HTTP port |
| `MATRIX_MODELS_DIR` | models | Pretrained models directory |

### Application Properties

Main config: `matrix-core/src/main/resources/application.properties`

Key settings:
- Database URL: `postgresql://${DB_HOST}:${DB_PORT}/matrix`
- Kafka: `localhost:9092`
- Redis: `localhost:6379`
- Model directory: `${MATRIX_MODELS_DIR:models}`

---

## Kubernetes Deployment

### Prerequisites
- Minikube 1.35+ or K8s cluster
- kubectl configured

### Deploy

```bash
# Start Minikube
minikube start --memory=4096 --cpus=4

# Deploy all resources
kubectl apply -f infra/k8s/minikube/

# Check status
kubectl get pods -n matrix

# Scale
kubectl scale deployment matrix-core -n matrix --replicas=3
```

### Access

```bash
# Get the URL
minikube service matrix-core -n matrix --url

# Or use port forwarding
kubectl port-forward -n matrix svc/matrix-core 9091:9091
```

---

## Pretrained Models

The system can download and use HuggingFace models:

```bash
# Via API
curl -X POST http://localhost:9091/api/v1/import/all

# Or use CLI
./gradlew :matrix-core:quarkusRun -- --matrix train-all --model-dir models --budget-mb 4096
```

Available models are in `models/pretrained/` and downloaded on-demand.

---

## Available CLI Commands

```bash
# Show help
./gradlew :matrix-core:quarkusRun -- --help

# Run a simulation
./gradlew :matrix-core:quarkusRun -- --matrix simulate

# Run evolution
./gradlew :matrix-core:quarkusRun -- --matrix evolution

# Run a demo
./gradlew :matrix-core:quarkusRun -- --matrix demo

# Grid world pilot
./gradlew :matrix-core:quarkusRun -- --matrix grid-world

# Robot arm demo
./gradlew :matrix-core:quarkusRun -- --matrix robot-arm

# Cauldron demo
./gradlew :matrix-core:quarkusRun -- --matrix cauldron-demo

# HADES demo
./gradlew :matrix-core:quarkusRun -- --matrix hades-demo

# Noosphere demo
./gradlew :matrix-core:quarkusRun -- --matrix noosphere-demo

# Full pipeline
./gradlew :matrix-core:quarkusRun -- --matrix pipeline
```

---

## Available API Endpoints

### Health & Status
- `GET /q/health` — Health check
- `GET /q/metrics` — Prometheus metrics
- `GET /v1/models` — List available models

### Chat (OpenAI-compatible)
- `POST /v1/chat/completions` — Chat completion
- `GET /v1/chat/status` — Training status
- `POST /v1/chat/status/train` — Trigger training
- `POST /v1/chat/status/flush` — Flush conversation log

### Federated Learning
- `POST /api/v1/federated/round` — Run a round
- `GET /api/v1/federated/status` — Status
- `GET /api/v1/federated/model` — Global model
- `POST /api/v1/federated/config` — Update config

### P2P Noosphere
- `GET /api/v1/noosphere/p2p/peers` — List peers
- `POST /api/v1/noosphere/p2p/publish` — Publish knowledge
- `GET /api/v1/noosphere/p2p/query` — Query knowledge
- `GET /api/v1/noosphere/p2p/trust` — Trust scores

### Multimodal
- `GET /api/v1/multimodal/modalities` — List modalities
- `POST /api/v1/multimodal/text/extract` — Extract text features
- `POST /api/v1/multimodal/unify` — Unify multi-modal input

### Generation
- `POST /api/v1/generation/image` — Generate image
- `POST /api/v1/generation/video` — Generate video
- `POST /api/v1/generation/audio` — Generate audio

### Ingestion
- `POST /api/v1/ingest/text` — Ingest text
- `POST /api/v1/ingest/binary/{type}` — Ingest audio/video/photo/PDF
- `POST /api/v1/ingest/url` — Ingest from URL
- `POST /api/v1/ingest/stats` — Statistics

### Agent (Self-improving)
- `POST /api/v1/agent/decompose` — Decompose complex task
- `POST /api/v1/agent/improve` — Run improvement cycle
- `GET /api/v1/agent/stats` — Statistics

### Tools
- `GET /api/v1/tools/list` — List available tools
- `POST /api/v1/tools/invoke` — Invoke a tool
- `GET /api/v1/tools/stats` — Usage statistics

### Verification
- `GET /api/v1/verification/properties` — List properties
- `GET /api/v1/verification/stats` — Statistics
- `GET /api/v1/verification/violations` — Violations

### Import
- `GET /api/v1/import/models` — List available models
- `POST /api/v1/import/all` — Import all models
- `POST /api/v1/import/model/{modelId}` — Import specific model

---

## Troubleshooting

### Application won't start
1. Check Docker is running: `docker ps`
2. Check infrastructure is up: `docker compose ps`
3. Check ports: `ss -tlnp | grep -E '5433|6379|9092|9091'`
4. Check logs: `docker compose logs`

### OutOfMemoryError
Increase JVM heap:
```bash
export JAVA_OPTS="-Xmx4g -Xms2g"
./gradlew :matrix-core:quarkusDev
```

### Port already in use
Check and free the port:
```bash
# Find process using port
lsof -i :9091

# Kill it
kill -9 <PID>
```

### Database connection failed
```bash
# Test connection
docker exec -it agi-postgres-1 psql -U matrix -d matrix

# Reset if needed
docker compose down
docker volume rm agi_pgdata
docker compose up -d postgres
```

---

## Development

### Build
```bash
# Full build with tests
./gradlew build

# Just compile
./gradlew :matrix-core:compileJava

# Run tests
./gradlew :matrix-core:test
```

### Coverage
```bash
./gradlew :matrix-core:jacocoTestReport
# Report: matrix-core/build/reports/jacoco/test/html/index.html
```

### JMH Benchmarks
```bash
./gradlew :matrix-core:jmh
```

---

## Next Steps

After installation:
1. Read [AGENTS.md](AGENTS.md) for AI agent instructions
2. Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for system design
3. Read [docs/improvements/](docs/improvements/) for optimization plans
4. Read [MIGRATION.md](MIGRATION.md) for portability

---

*Last updated: 2026-07-26 | Version: v3.58*
