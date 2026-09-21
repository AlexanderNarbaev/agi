# MATRIX REBUILD PLAN
# Полная пересборка проекта: от старой реализации к L0-L22 соответствию

**Ветка:** docs/matrix-rebuild
**Дата:** 2026-08-08
**Статус:** Phase 0-3 COMPLETE (BIR, Tsetlin, DevLoop, KTopo, Signals, Lifecycle, Federation, Actions, Monotone, Reservoir, Budgeter, Distill)

---

## 1. Что мы имеем сегодня (v3.58.9 / v3.59.3 + rebuild modules)

### 1.0. Rebuild modules (NEW — docs/matrix-rebuild branch)

| Module | Package | SPEC/DESIGN | Status |
|--------|---------|-------------|--------|
| **BIR** | `io.matrix.bir` | SPEC-002 keystone | ✅ 7 tests pass |
| **TsetlinTrainer** | `io.matrix.tsetlin` | SPEC-002 Stage B | ✅ 7 tests pass |
| **Developmental Loop** | `io.matrix.devloop` | SPEC-000 | ✅ 6 tests pass |
| **Knowledge Topology** | `io.matrix.ktopo` | SPEC-003 | ✅ 6 tests pass |
| **Signal Modules** | `io.matrix.signals` | DESIGN-06 | ✅ 8 tests pass |
| **Lifecycle (Cauldron+TaskCell)** | `io.matrix.lifecycle` | DESIGN-07/12 | ✅ 7 tests pass |
| **Federation** | `io.matrix.federation` | DESIGN-08 | ✅ 4 tests pass |
| **Action Registry** | `io.matrix.actions` | DESIGN-13 | ✅ 6 tests pass |
| **MonotoneDecoder** | `io.matrix.monotone` | DESIGN-09 | ✅ 4 tests pass |
| **Binary Reservoir** | `io.matrix.reservoir` | DESIGN-10 | ✅ 4 tests pass |
| **Budgeter-Homeostat** | `io.matrix.budgeter` | DESIGN-11 | ✅ 5 tests pass |
| **Weight Distiller** | `io.matrix.distill` | SPEC-001 | ✅ 4 tests pass |

**Total: 12 modules, 68 tests, all passing.**

### 1.1. Работающие компоненты (проверено end-to-end)

| Компонент | Проверено | Где в коде |
|-----------|-----------|------------|
| Quarkus на 3 репликах (K8s) | ✅ `GET /api/v1/health` UP | `io.matrix.*` |
| OpenAI-compatible chat | ✅ `/v1/chat/completions` returns corpus | `OpenAIChatResource` |
| 3-block BrainPipeline | ✅ `/v1/brain/think` 4μs latency | `BrainPipeline`, `DefaultBrainPipeline` |
| Long-horizon planning | ✅ `/v1/brain/plan` 4 steps | `LongHorizonPlanner` |
| Sub-agent tool use | ✅ `/v1/brain/subagent` pi²=9.869 | `SubAgent` + `ToolsResource` |
| 8 tools (calculator, datetime, 6 stubs) | ✅ `/api/v1/tools/{list,invoke,stats}` | `ToolsResource` |
| Generative chat primary | ✅ textGenerator.forwardPass | `NeuralTextGenerator` |
| World model + memory write-back | ✅ HierarchicalMemory | `HierarchicalMemory` + `OpenAIChatResource` |
| Self-improvement loop | ✅ auto_generated.jsonl grows 14→101 | `ChatDrivenTrainer` |
| Training endpoint | ✅ `/api/v1/agent/train` bestFitness 1050 | `MatrixResource` |
| 6 pretrained models loaded | ✅ MultiBrainEnsemble 150 neurons | `MultiBrainEnsemble` |
| 6653 corpus entries | ✅ NeuralMemoryResponse loaded | `NeuralMemoryResponse` |
| Grafana dashboards | ✅ 3 MATRIX dashboards | K8s + Grafana |
| Prometheus + Jaeger + Loki | ✅ Live | K8s |
| Postgres + Redis + Kafka | ✅ Live | K8s |
| MinIO (S3) | ✅ Live | K8s |

### 1.2. Частично реализованные / заявленные но не рабочие

| Компонент | Статус | Что делает |
|-----------|--------|------------|
| Coverage 82% floor | ❌ ENV BLOCKED | jacocoTestReport cannot run because Quarkus native-image plugin filters the jacoco agent. chown via docker --privileged fixed the build dir, but coverage measurement still not feasible in this env. |
| Sequential HF training | ⚠️ SCRIPT READY | `sequential-train.sh` orchestrates HF load→convert→delete but requires minikube DNS and disk access. |
| Multi-modal (vision/audio) | ⚠️ STUBS | FeatureExtractors exist but only return `[image:512feats]` — no real safetensors → feature pipeline. |
| Sub-agent tool use (web_search, web_fetch, code_execute) | ⚠️ STUBS | Only `calculator` and `datetime` are implemented. The rest return placeholder strings. |
| Long-term memory persistence | ⚠️ IN-MEMORY | HierarchicalMemory has no disk backend (RocksDB/SQLite). |
| FROZEN neuron audit | ⚠️ PARTIAL | Ethical filter works (logs show REJECTED), but no automated check that the 6 frozen neurons match L5_DNA exactly. |
| Sub-agent sandboxing | ⚠️ PARTIAL | SubAgent exists, whitelist enforced, but no process isolation. |
| Cluster mediator hierarchy | ⚠️ PARTIAL | InstanceMediator exists but LobeMediator/ClusterMediator wiring incomplete. |
| Multi-instance mesh | ⚠️ PARTIAL | Kafka event journal exists but no live multi-instance communication. |
| 3-block brain input processor | ⚠️ PARTIAL | TextFeatureExtractor exists; image/audio return length-only summaries. |

### 1.3. Нереализованные компоненты (по документации)

| Компонент | Документ | Что ожидается |
|-----------|----------|---------------|
| Event Sourcing (fully) | L6 | EventJournal → Kafka, SnapshotStore → MinIO, `.ldn` snapshots |
| Noosphere global registry | L6 | Distributed Neuron Identity Ledger via compacted Kafka topic |
| Cauldron Protocol | L5 | Controlled creation of new FNL (Functionally Novel Lobe) |
| HADES restart | L5 | Controlled restart after damage |
| Eleutheria (right of refusal) | L4 | Instance rejects mutations violating Ethics |
| Minecraft pilot | L13 | Pilot #1: agent in Minecraft world |
| Proactive chat-bot pilot | L13 | Pilot #2: proactive dialogue initiation |
| FPGA synthesis | L16 | Pilot #4: generate FPGA bitstream |
| ROS2 integration | L16 | Pilot #7: sensor fusion via ROS2 |
| University course | L15 | Pilot #6: 7-module course |
| Multi-tenant isolation | L2 | Full tenant-aware request routing |

---

## 2. Генезис архитектуры (L0 → L22)

### 2.1. Ядро (L1-L5) — реализовано в коде

| Слой | Документ | В коде | Состояние |
|------|----------|--------|-----------|
| L1 MPDT-neuron | TruthTable, DecisionTree | `neuron/` | ✅ Complete |
| L2 Protocols | Signal, ConsensusEngine | `consensus/`, `hades/` | ✅ Complete |
| L3 Clusters | NeuronClusterActor, FNL | `cluster/`, `fnl/` | ✅ Complete |
| L4 Mediators | InstanceMediator, drivers | `mediator/` | ✅ Complete |
| L5 DNA/Genetics | EvolutionLoop, Population | `evolution/` | ✅ Complete |

### 2.2. Инфраструктура (L6-L10) — реализовано в коде

| Слой | Документ | В коде | Состояние |
|------|----------|--------|-----------|
| L6 Memory | EventJournal, SnapshotStore | `events/`, `snapshot/` | ✅ Complete (in-memory + Kafka) |
| L7 Ethics | EthicalFilter, FrozenNeurons | `ethics/` | ✅ Complete |
| L9 Deployment | Docker, K8s, Operator | `deploy/`, `infra/` | ✅ Complete |
| L10 Monitoring | Prometheus, Jaeger, Loki | `observability/` | ✅ Complete |

### 2.3. Пилоты и бизнес (L11-L23) — частично

| Слой | Документ | В коде | Состояние |
|------|----------|--------|-----------|
| L11 Management | — | — | ⚠️ Partial |
| L12 Legal | — | — | ⚠️ Partial |
| L13 Pilots | Sub-agent use | `agent/SubAgent.java` | ✅ Newly added (v3.59.3) |
| L14 Business | — | — | ⚠️ Spec only |
| L15 Education | — | — | ⚠️ Spec only |
| L16 Physical | FPGA, ROS2 | `fpga/`, `ros2/` | ⚠️ Spec only |
| L17+ | — | — | 📋 Spec only |

---

## 3. Rebuild plan (what to implement)

### 3.1. Phase 1: Close operational gaps (2-3 days)

**Goal:** Make existing code measurable and safe.

1. **Fix jacoco coverage measurement**
   - Root cause: Quarkus native-image plugin filters jacoco agent (`org.jacoco:org.jacoco.agent:0.8.14 is filtered`).
   - Fix: override the exclusion in build.gradle, or run tests with `-Dquarkus.native.enabled=false`.
   - Alternative: use JaCoCo's `OfflineInstrumentTask` (instrument classes at build time, then run tests without agent).

2. **Fix docker env blocker for root-owned files**
   - `docker run --privileged -v ... chown -R` worked for build dir.
   - Add a `fix-perms.sh` script to the repo.

3. **Implement web_search and web_fetch tools**
   - Current: stubs returning "No JS engine" or placeholder.
   - Implement: real HTTP fetch (with rate limiting), DuckDuckGo or Wikipedia API search.
   - Sandboxed: timeout 5s, response size cap 64KB.

4. **Add multi-modal input parsing**
   - Current: FeatureExtractors return `[image:512feats]` placeholders.
   - Implement: real decoding (BMP/PNG/WAV parsers) to extract real feature vectors.
   - Then: use Text2VecService to encode features into 64-bit signal vector.

5. **Wire MultiBrainEnsemble as the "conscious" layer**
   - Current: textGenerator uses 3-layer hierarchy with random neurons.
   - Implement: MultiBrainEnsemble.forwardPass() that uses loaded pretrained neurons for generation.

### 3.2. Phase 2: Sequential HF training (1 week)

**Goal:** No pre-loaded models. Load, convert, delete, consolidate.

1. **Run sequential-train.sh end-to-end**
   - Fix minikube DNS (use external DNS or local cache).
   - Preload from local HF cache (models already downloaded to `~/.cache/huggingface/hub/`).
   - For each model: download safetensors → run Quarkus train-all subcommand → convert weights → Avro neurons → delete safetensors.
   - Consolidate all neurons into a single `/data/models/pretrained/` tree.

2. **Verify neuron consolidation**
   - After all 6 models processed, `MultiBrainEnsemble` should load 150 neurons from a unified pool (not 6 separate files).
   - Add a `consolidate` step that merges per-model Avro files into one `merged.avro`.

3. **Delete downloaded HF files from disk**
   - Script should be idempotent: `delete_safetensors` runs even if extraction fails (to prevent disk accumulation).

### 3.3. Phase 3: Real generative reasoning (2 weeks)

**Goal:** Chat output reflects actual neural state, not corpus retrieval.

1. **Train the 3-layer neural hierarchy on corpus**
   - Use combined_training.json (6653 entries) + auto_generated.jsonl (100+ entries) as training data.
   - Run `MultiBrainEnsemble.train(corpus)` or a dedicated training loop that:
     - Encodes each input as 64-bit sensor vector (Text2VecService)
     - Feeds through encoder/compression/output layers
     - Compares output to expected response (the corpus answer)
     - Mutates neurons via EvolutionLoop to reduce error
   - Result: textGenerator.forwardPass() produces text that is grounded in corpus, not just matched to it.

2. **Implement real autoregressive generation**
   - Current: textGenerator.generate() does forward pass + autoregressive update.
   - Fix: make the output layer actually decode to meaningful characters (use a real tokenizer like SentencePiece or BPE for vocabulary mapping).

3. **Remove corpus retrieval as primary path**
   - After training, the conscious layer should generate without corpus memory.
   - Corpus becomes fallback only for first-turn context or when generation fails.

### 3.4. Phase 4: World model + memory (1 week)

**Goal:** HierarchicalMemory becomes persistent and queryable.

1. **Add SQLite or RocksDB backend to HierarchicalMemory**
   - Current: in-memory HashMap (lost on restart).
   - Implement: on-disk storage with level-aware indexing (L0-L4).
   - Add persistence between restarts (K8s PVC or hostPath).

2. **Add world model queries**
   - Not just `search(text, k)` but semantic queries:
     - "What do we know about topic X?"
     - "What's the most recent event involving entity Y?"
   - Use HierarchicalMemory.DriftSignal for world-model evolution.

3. **Wire multi-instance mesh (L6 Noosphere)**
   - Publish memory updates to Kafka.
   - Subscribe to other instances' updates.
   - Merge memory entries by importance.

### 3.5. Phase 5: Sub-agent tool use (1 week)

**Goal:** Sub-agents can use tools in a sandbox, with result flowing back.

1. **Fix SubAgent tool invocation**
   - Current: SubAgent calls `tools.invoke(tool, args)` — direct method call, not via REST.
   - Fix: use REST endpoint internally or proper dependency injection.

2. **Add web_search and web_fetch as real tools**
   - web_search: DuckDuckGo HTML or Wikipedia API.
   - web_fetch: URL fetch with content extraction (jsoup or similar).
   - Sandboxed: timeout 5s, response cap 64KB.

3. **Add code_execute tool**
   - Sandboxed Java executor (JShell or Janino).
   - Whitelisted classes only (no System.exit, no file I/O outside sandbox).

4. **Sub-agent lifecycle**
   - Each SubAgent is a short-lived instance (single task).
   - After result returned, sub-agent is destroyed.
   - Main agent decides if result should be stored in HierarchicalMemory.

### 3.6. Phase 6: Multi-modal perception (1 week)

**Goal:** Real vision and audio processing, not placeholders.

1. **Image processing**
   - Add `org.apache.commons.imaging.Imaging` for PNG/JPEG/BMP parsing.
   - Convert pixels to 64-dim float features (downsampled grayscale).
   - Wire into BrainPipeline Block 1.

2. **Audio processing**
   - Add basic WAV parser (RIFF header → PCM samples).
   - Convert samples to 64-dim float features (FFT or mel spectrogram).
   - Wire into BrainPipeline Block 1.

3. **Cross-modal alignment**
   - Use CrossModalAligner to fuse image + audio + text features.
   - Produce unified 64-bit signal vector for the conscious layer.

### 3.7. Phase 7: Long-horizon planning with real execution (1 week)

**Goal:** LongHorizonPlanner executes actual tool calls, not just text generation.

1. **Wire tools into LongHorizonPlanner steps**
   - Each step may invoke a tool (calculator, datetime, web_search, etc.).
   - Result of step N becomes input to step N+1.

2. **Add step verification**
   - After each step, verify the output (e.g., check that a URL was fetched successfully).
   - If verification fails, retry with different approach.

3. **Add deployment / monitoring steps**
   - If goal mentions "deploy", add steps to build, push, rollout, verify.
   - If goal mentions "monitor", add steps to query Grafana/Prometheus.

### 3.8. Phase 8: Full 82% coverage (1 week)

**Goal:** JaCoCo measurement works, coverage hits 82% METHOD.

1. **Fix jacoco agent**
   - Override Quarkus's native-image plugin exclusion.
   - Or use OfflineInstrumentTask.

2. **Write tests for remaining uncovered classes**
   - Top 20 uncovered classes (from earlier analysis):
     - AgentBrainService, AgentLoop, GeneticOperators, ReActAgentLoop, Population, ...
   - Each class needs at least constructor, public methods, edge cases.

3. **Run full test suite and measure**
   - `gradle test` (full, no --tests filter)
   - `gradle jacocoTestReport`
   - Verify METHOD ≥ 82%.

---

## 4. Critical gaps that BLOCK real AGI

These are the items that prevent M.A.T.R.I.X. from being "real AGI" (vs. a sophisticated chatbot):

| Gap | Impact | Effort |
|-----|--------|--------|
| **No real generative reasoning** | Chat returns corpus retrieval, not generated content. | 2 weeks (train textGenerator on corpus) |
| **No real multi-modal input** | FeatureExtractors are stubs. | 1 week (real image/audio parsers) |
| **No persistent world model** | HierarchicalMemory is in-memory, lost on restart. | 3 days (SQLite backend) |
| **No sub-agent sandboxing** | SubAgent can crash the whole system if tool fails. | 3 days (process isolation) |
| **No long-horizon planning execution** | LongHorizonPlanner generates text, doesn't actually execute tools. | 1 week (wire tools into steps) |
| **No coverage measurement** | Can't verify 82% floor. | 1 day (fix Quarkus plugin exclusion) |
| **Sequential HF training not automated** | sequential-train.sh exists but not wired into CI. | 1 day (add to CI) |
| **Sub-agent memory write-back** | Sub-agent results not stored in HierarchicalMemory. | 2 days (hook into SubAgent) |
| **Multi-instance mesh** | No live communication between M.A.T.R.I.X. instances. | 1 week (Kafka topic + listener) |
| **FROZEN neuron verification** | No automated check that 6 frozen neurons match L5_DNA spec. | 2 days (test) |

---

## 5. What makes this "real AGI" vs "just a chatbot"

| Property | Current | Target |
|----------|---------|--------|
| Generates novel text | ❌ (corpus retrieval) | ✅ (neural generation) |
| Persistent long-term memory | ❌ (in-memory) | ✅ (disk backend) |
| Learns from interactions | ✅ (self-improvement loop) | ✅ (with real training) |
| Uses tools to solve problems | ⚠️ (only calculator/datetime) | ✅ (8+ real tools) |
| Sub-agents for focused tasks | ⚠️ (no sandboxing) | ✅ (process isolation) |
| Multi-modal perception | ❌ (stubs) | ✅ (real image/audio) |
| World model updates | ⚠️ (in-memory only) | ✅ (persistent + shared) |
| Long-horizon planning | ⚠️ (text-only steps) | ✅ (real tool execution) |
| Interpretable decisions | ✅ (3-block pipeline) | ✅ (discrete log per step) |
| Ethical enforcement | ✅ (filter rejects) | ✅ (frozen neurons + audit) |

---

## 6. Immediate next actions (today)

1. Fix jacoco agent exclusion in build.gradle (unblock coverage).
2. Run `sequential-train.sh` end-to-end on one model to verify the pipeline works.
3. Implement web_search and web_fetch tools (real HTTP fetch).
4. Wire tools into LongHorizonPlanner steps.
5. Add SQLite backend to HierarchicalMemory.
6. Train textGenerator on combined corpus (generate, not retrieve).

Each of these is measurable and completes in < 1 day.

---

**End of REBUILD_PLAN.md**
