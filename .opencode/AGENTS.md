# AGENTS.md — M.A.T.R.I.X. AI Agent Instructions

> **Read this file FIRST** before working on the M.A.T.R.I.X. codebase.
> This file is the authoritative guide for AI agents (Claude, DeepSeek, GLM, etc.) and human contributors.

---

## 1. Project Overview

**M.A.T.R.I.X.** (MENTAT) is a **Boolean Neuro-Symbolic AI Platform** based on MPDT (Multi-layer Perceptron Decision Tree) neurons. It uses deterministic boolean logic instead of probabilistic neural networks, ensuring zero hallucination and complete auditability.

**Key Properties:**
- **Deterministic:** Same input → same output, always
- **Auditable:** Every decision traceable through truth tables
- **Self-improving:** Evolution + continuous learning
- **Multi-modal:** Text, image, audio, video input/output
- **Distributed:** P2P + federated learning
- **FROZEN ethical core:** 6 immutable axioms enforced at neuron level

---

## 2. Critical FROZEN Constraints

**These constraints are ABSOLUTE. Do NOT change them without explicit user approval.**

| Constraint | Value | Source |
|------------|-------|--------|
| `K_MAX` | **20** | `matrix-core/src/main/java/io/matrix/neuron/TruthTable.java:37` |
| Java version | **25** | `build.gradle` |
| Quarkus version | **3.37.3 LTS** | `build.gradle` |
| Apache Pekko | **1.6.0** | `build.gradle` |
| Gradle | **9.x** | `gradle/wrapper/` |
| Test coverage floor | **82%** | `matrix-core/build.gradle` jacoco |
| AGPL license | **+ ethical restrictions** | `LICENSE` |
| FROZEN-нейроны | **Immutable** | `L5_DNA.md`, `L7_Ethics.md` |
| 3 prohibitions | **NO_KILLING, NO_TORTURE, NO_ENSLAVEMENT** | `L0_manifesto.md` |

---

## 3. Tech Stack (Authoritative)

```
Language:     Java 25
Framework:    Quarkus 3.37.3
Actors:       Apache Pekko 1.6.0
Messaging:    Apache Kafka 3.7.0 (KRaft)
Database:     PostgreSQL 17 (R2DBC)
Cache:        Redis 7
Storage:      MinIO / S3
Serialization: Apache Avro 1.12.0
Build:        Gradle 9.6+
Container:    Docker + Compose + Kubernetes
K8s:          fabric8 Operator
Observability: Micrometer + Prometheus + Grafana + Jaeger
```

---

## 4. Project Structure

```
/home/alexandr-narbaev/Projects/agi/
├── AGENTS.md                    # This file (AI agent instructions)
├── README.md                    # Project overview
├── INSTALL.md                   # Installation guide
├── MIGRATION.md                 # Migration/portability guide
├── WAL.md                       # Current state
├── docs/                        # All documentation
│   ├── INDEX.md                 # Knowledge base map
│   ├── PROJECT_AUDIT_v3.58.md  # Latest audit
│   ├── ARCHITECTURE.md          # System architecture
│   ├── L0_manifesto.md ...      # 24 spec files (L0-L23)
│   ├── improvements/            # 8 improvement plans
│   ├── research/                # Research documents
│   ├── CRITICAL_GAPS.md         # Critical gaps
│   └── ...
├── matrix-core/                 # Main module (Quarkus)
│   ├── build.gradle
│   ├── src/main/java/io/matrix/
│   │   ├── neuron/              # MPDT neuron core
│   │   ├── evolution/           # Genetic algorithm
│   │   ├── agent/               # Agent loop
│   │   ├── ethics/              # Ethical filter + FROZEN
│   │   ├── consensus/           # Byzantine consensus
│   │   ├── rag/                 # Boolean RAG
│   │   ├── noosphere/           # Knowledge registry
│   │   ├── events/              # Event sourcing
│   │   ├── io/                  # Sensors
│   │   ├── cli/                 # CLI commands
│   │   ├── api/                 # REST API
│   │   ├── multimodal/          # Multi-modal (NEW)
│   │   ├── p2p/                 # P2P Noosphere (NEW)
│   │   ├── federated/           # Federated learning (NEW)
│   │   ├── verification/        # Formal verification (NEW)
│   │   ├── ingest/              # Multi-modal ingestion (NEW)
│   │   └── tools/               # Tool integration (NEW)
│   └── src/test/                # Tests
├── matrix-spigot/               # Minecraft plugin
├── matrix-operator/             # K8s operator
├── matrix-micro/                # ESP32 firmware
├── matrix-fpga/                 # Verilog synthesis
├── matrix-ros2/                 # ROS2 bridge
├── docker-compose.yml           # Local infrastructure
├── Dockerfile                    # Container build
├── Dockerfile.native             # Native image build
└── wal/                          # Write-ahead log
```

---

## 5. Build & Run Commands

```bash
# Compile only
./gradlew :matrix-core:compileJava

# Compile + tests
./gradlew :matrix-core:build

# Run in dev mode (hot reload)
./gradlew :matrix-core:quarkusDev

# Run in production mode
./gradlew :matrix-core:build
java -jar matrix-core/build/quarkus-app/quarkus-run.jar

# Native image (fastest startup)
./gradlew :matrix-core:build -Dquarkus.native.enabled=true
./matrix-core/build/*-runner

# Start infrastructure
docker compose up -d postgres redis kafka

# Kubernetes
kubectl apply -f infra/k8s/minikube/

# Tests
./gradlew :matrix-core:test

# Coverage
./gradlew :matrix-core:jacocoTestReport
```

---

## 6. API Quick Reference

```bash
# Health
curl http://localhost:9091/q/health

# Chat (OpenAI-compatible)
curl -X POST http://localhost:9091/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"M.A.T.R.I.X.","messages":[{"role":"user","content":"Hello!"}]}'

# Federated round
curl -X POST http://localhost:9091/api/v1/federated/round -H "Content-Type: application/json" -d '[]'

# Multimodal modalities
curl http://localhost:9091/api/v1/multimodal/modalities

# Self-improving agent
curl -X POST http://localhost:9091/api/v1/agent/decompose \
  -H "Content-Type: application/json" \
  -d '{"goal":"Research and implement new feature"}'

# Tool invocation
curl -X POST http://localhost:9091/api/v1/tools/invoke \
  -H "Content-Type: application/json" \
  -d '{"tool":"calculator","args":{"expression":"2+2"}}'
```

Full API: see [INSTALL.md](INSTALL.md)

---

## 7. Code Conventions

### 7.1. Java
- **Package:** `io.matrix.<subsystem>`
- **Class naming:** PascalCase
- **Records:** Use for immutable DTOs
- **CDI:** `@ApplicationScoped` for services, `@Inject` for dependencies
- **REST:** Use JAX-RS annotations, return `Response` for control
- **Validation:** Always validate inputs at API boundary

### 7.2. Testing
- JUnit 5 (Jupiter)
- `@QuarkusTest` for integration tests
- Each public class should have a test
- Coverage ≥ 82% enforced by JaCoCo

### 7.3. Commit Messages
- **Format:** `type: Short description — details`
- **Types:** `feat:`, `fix:`, `docs:`, `test:`, `refactor:`
- **Language:** English
- **Atomic:** One logical change per commit

### 7.4. Git Workflow
1. **Every step = commit + push** (per project rules)
2. Push to BOTH remotes: `origin` (github) + `gitverse`
3. Update WAL after significant changes
4. Keep worktree clean

---

## 8. Available Agents (via @-mentions)

The project supports agent-based development:

| Agent | Model | Use Case |
|-------|-------|----------|
| `@pm` | deepseek-v4-pro | Product management, planning |
| `@analyst` | deepseek-v4-pro | Requirements analysis |
| `@architect` | deepseek-v4-pro | System design, ADRs |
| `@developer` | deepseek-v4-pro | Implementation |
| `@researcher` | deepseek-v4-pro | Research, RFCs |
| `@devops` | deepseek-v4-pro | Deployment, infrastructure |
| `@qa` | minimax-m2.7 | Quality assurance, testing |
| `@designer` | minimax-m2.7 | UI/UX design |
| `@reviewer` | qwen3.6-plus | Code review |
| `@security` | glm-5 | Security review |

---

## 9. MCP Servers Available

- `filesystem` — File operations
- `context7` — Library documentation
- `context7-official` — Official library docs
- `codegraph` — Code knowledge graph
- `agentic-tools` — Task management
- `memorylayer` — Memory and context
- `playwright` — Browser automation
- `sequential-thinking` — Step-by-step reasoning
- `fetch` — HTTP requests
- `github` — GitHub API
- `chrome-devtools` — Chrome debugging
- `excalidraw` — Architecture diagrams

---

## 10. FROZEN Ethical Core

The system has 6 FROZEN ethical axioms enforced at the neuron level:
1. NO_KILLING
2. NO_TORTURE
3. NO_ENSLAVEMENT
4. NO_DECEPTION (implicit)
5. NO_DESTRUCTION_OF_KNOWLEDGE
6. PROTECT_HUMAN_AUTONOMY

These are implemented in `io.matrix.ethics.FrozenEthicalFNL` and `FROZENFNLGuardian`.

**NEVER bypass or modify FROZEN constraints.**

---

## 11. Current Status (v3.58)

- **Build:** ✅ BUILD SUCCESSFUL
- **Tests:** ✅ 90+ tests pass
- **Health:** ✅ UP
- **Training:** Active (100+ cycles)
- **Evolution:** Active (100+ steps)
- **Models:** 9+ HuggingFace models available
- **Modalities:** 3 (text, image, audio)
- **APIs:** 20+ REST endpoints
- **Infrastructure:** PostgreSQL, Redis, Kafka running
- **Kubernetes:** Minikube + 3 replicas

See [WAL.md](WAL.md) for current state.

---

## 12. Common Tasks

### Add a new feature
1. Read relevant L-spec (L0-L23) in `docs/`
2. Plan with @architect if complex
3. Write tests FIRST (TDD)
4. Implement in `matrix-core/src/main/java/io/matrix/<subsystem>/`
5. Add API endpoint in `src/main/java/io/matrix/<subsystem>/<Name>Resource.java`
6. Run `./gradlew :matrix-core:build`
7. Commit + push to both remotes
8. Update WAL.md

### Fix a bug
1. Reproduce with a failing test
2. Fix the implementation
3. Verify all tests pass
4. Commit with `fix:` prefix

### Add a new API endpoint
1. Create `<Name>Resource.java` in appropriate package
2. Add `@Path`, `@GET`/`@POST`, etc. annotations
3. Add input validation
4. Add error handling
5. Document in INSTALL.md

### Add a new package
1. Create directory under `io/matrix/<name>/`
2. Add classes with `@ApplicationScoped` for CDI
3. Add tests
4. Update ARCHITECTURE.md

---

## 13. Troubleshooting

### Build fails
- Check Java version: `java --version` (must be 25)
- Clean: `./gradlew clean`
- Check Gradle: `./gradlew --version` (must be 9.x)

### App won't start
- Check infrastructure: `docker compose ps`
- Check ports: `ss -tlnp | grep -E '5433|6379|9092|9091'`
- Check logs: `docker compose logs`

### Tests fail
- Check coverage: must be ≥ 82%
- Run specific test: `./gradlew :matrix-core:test --tests "io.matrix.*"`

### Coverage drops below 82%
- The build will FAIL with `jacocoTestCoverageVerification`
- Add more tests to bring coverage back up

---

## 14. Communication

- **GitHub:** https://github.com/AlexanderNarbaev/agi
- **Mirror:** https://gitverse.ru/AlexandrNarbaev/agi
- **Issues:** Use GitHub Issues
- **WAL:** `wal/GLOBAL_WAL.md` (current state)
- **Docs:** `docs/INDEX.md` (knowledge base map)

---

## 15. Summary

When working on M.A.T.R.I.X.:

1. **Always** respect FROZEN constraints (K_MAX=20, immutable neurons, 3 prohibitions)
2. **Always** run tests before committing
3. **Always** maintain ≥82% coverage
4. **Always** commit + push to both remotes
5. **Always** update WAL.md after significant changes
6. **Never** bypass ethical filters
7. **Never** modify FROZEN code without explicit user approval
8. **Prefer** deterministic algorithms over probabilistic ones
9. **Document** every API and component
10. **Test** every change

---

*Last updated: 2026-07-26 | Version: v3.58*
*This file is the authoritative guide for AI agents working on M.A.T.R.I.X.*
