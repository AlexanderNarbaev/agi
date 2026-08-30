# MATRIX — How to use the system

This is the entry point for anyone wanting to actually run, explore,
and test MATRIX. The system is **launchable now** via `java -jar` on
the Quarkus uber-jar. This README documents the launch path, the API
endpoints, the existing benchmarks, and the limitations.

---

## Quick start (60 seconds)

```bash
# 1. Start the server (port 9091)
java -jar matrix-core/build/matrix-core-1.0.0-runner.jar

# 2. Verify it's alive
curl http://localhost:9091/v1/models-registry | jq .

# 3. Chat with MATRIX through the boolean chain
curl -X POST http://localhost:9091/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"model":"M.A.T.R.I.X.","messages":[{"role":"user","content":"hi"}]}'

# 4. Inspect chain state
curl http://localhost:9091/v1/chain-status | jq .

# 5. Use the sandbox UI
curl -X POST http://localhost:9091/v1/sandbox/chat \
  -H 'Content-Type: application/json' \
  -d '{"input":"Hello MATRIX"}'
```

The server also exposes:
- `GET /v1/sandbox/inspect` — full chain + recent conversations
- `GET /v1/sandbox/explain` — last decision explanation
- `GET /v1/sandbox/topology` — chain layer visualization
- `GET /q/health` — Quarkus health check
- `GET /metrics` — Prometheus metrics

---

## What is MATRIX

MATRIX is a **brain-like boolean compute architecture** with:

- **BIR substrate** (Boolean Intermediate Representation) — TT/CLAUSESET/BDD forms
- **24-layer transformer chain** from Qwen2.5-0.5B-Instruct — every chat hit runs through this chain (24 layers × ~21,960 boolean neurons)
- **9-stage cognition loop** (ConsciousnessLoop) — perception → attention → deliberation → gate → action → consolidation → subconscious → prediction-error → attention
- **HierarchicalMemory** with search-before-store-after for deliberation context
- **4 autonomy impulses** — curiosity, consolidation, integrity-check, share-digest
- **FROZEN ethical gate** — TLA+ verified (BotEthicsPipeline)
- **4 TLA+ specs** for ConjugateBudgeterDP, MemoryM4Causal, BrcStep, MctsLatsVisit (plus 4 older)
- **98,357 boolean neurons** imported from real open-weight LLMs (Qwen, SmolLM, TinyLlama)

---

## Models imported into MATRIX (substrate)

| Model | Params | Source | Neurons |
|---|---|---|---|
| Qwen2.5-0.5B-Instruct | 500M | `models/external/qwen2.5-0.5b/` (HF public) | 21,960 |
| SmolLM2-360M-Instruct | 360M | HF public | 19,201 |
| TinyLlama-1.1B-Chat-v1.0 | 1.1B | HF public | 57,195 |
| **Total** | | | **98,357** |

Run `python3 scripts/import_qwen_weights.py` to re-pull if missing.
**Note**: gated HF models (Llama, Mistral, Gemma, Phi-4, DeepSeek-distill, Qwen3-1.7B)
require HF authentication that's currently not configured in this env.

---

## Benchmarks (Wave K, EXP-MATRIX.13)

| Task | Sample size | Boolean-chain accuracy | Random |
|---|---|---|---|
| HellaSwag validation | 500 | 0.292 | 0.250 |
| HellaSwag validation | 200 | 0.270 | 0.250 |
| ARC-Easy test | 30 | 0.233 | 0.250 |
| MMLU-mini test | 30 | 0.200 | 0.250 |

The boolean chain beats random by ~4 pp on HellaSwag (commonsense)
but **under-performs on scientific reasoning tasks**. The score
function (`chain_score`) currently counts active bits; absmean and
BPE tokenization would lift this. Run benchmarks yourself:
```bash
python3 scripts/exp_matrix13_full_bench.py --task hellaswag --limit 1000
```

---

## Architecture (1-page)

```
           ┌─ Birr (boolean substrate) ─── 24-layer Qwen boolean chain ─┐
           │                                                             │
input text →│ Text2Vec → sensorBits → BooleanChainRunner → decision bits │
           │                                                             │
           │   ↑                                  ↑                       │
           │   │                                  │                       │
           │   ConsciousnessLoop                  │                       │
           │   (9-stage cognition)                │                       │
           │   ↑                                  │                       │
           │   HierarchicalMemory (search)        │                       │
           │   SleepCycle (drain/promote/digest)  │                       │
           │   Anonymizer (k-anon + DP noise)     │                       │
           │   EthicalFilter (FROZEN-FNL TLA+)    │                       │
           │   ConjugateBudgeter (per-period)     │                       │
           └─────────────────────────────────────┘
```

---

## What's in the codebase

### Production classes (`matrix-core/src/main/java/io/matrix/`)
- `api/` — Quarkus HTTP resources (`OpenAIChatResource`, `SandboxResource`, `ChainStatusResource`, `ModelRegistryResource`)
- `bir/` — Boolean Intermediate Representation (TT/CLAUSET/BDD)
- `budgeter/` — ConjugateBudgeter with per-period state machine
- `chat/` — ConversationRecorder, ChatDrivenTrainer
- `conscience/` — ethical filters (FROZEN-FNL)
- `curriculum/` — MaturityGateKeeper
- `ethics/` — EthicalFilter (TLA+ verified)
- `events/` — KafkaEventJournal (peer event sync)
- `evolution/` — TsetlinTrainer, MpdtGaProducer, Chromosome
- `federation/` — Anonymizer (k-anon + DP), KnowledgeShare, DecentralizedDigestPipeline, ELSP channels
- `hades/` — DerangementDetector (stuck-neuron reset)
- `imports/` — `WeightImporter`, `TensorProjector`, `BitLinearProjector`, `BitLinearTrainer`, `BooleanChainRunner`, `BooleanChainProducer`, `FullChainLoader`, `TruthTableLayer`
- `lifecycle/` — `ConsolidationCycle`, `SubconsciousConsolidator`, `SleepCycle`, `AutonomyImpulse`, `ImpulseScheduler`, `TaskCell`, `CauldronProtocol`, `FnlGate`
- `memory/` — `HierarchicalMemory` (L0-L5 levels)
- `model/` — `ModelRegistry`, `ChatPipelineEnricher`
- `neuron/` — `TruthTable`, `DecisionTree`, `NeuronLayer`, `HierarchicalBrain`, `MultiBrainEnsemble`
- `noosphere/` — `Crdt`, `GrowOnlySet`, `KnowledgeIndex`, `NoosphereRegistry`, `MeshFederation`
- `reasoning/` — `BrcChain`, `BrcStep`, `ConsciousnessLoop`, `FeedbackPerception`
- `signals/` — `SensorPacket`, `FederatedEncoder`, `Text2VecService`, `TextSignalModule`, etc.
- `sleep/` — `SleepCycle` (Wave F)
- `topo/` — Ricci curvature, drift fingerprint

### TLA+ formal contracts (`formal/`)
- `BotEthicsPipeline.tla` — FROZEN ethical axioms
- `ConjugateBudgeterDP.tla` — per-period state invariants
- `MemoryM4Causal.tla` — 4 invariants (Monotonicity, TombstoneIrreversible, EventualConsistency, FrozenImmutability)
- `BrcStep.tla` — α-cushion composition
- `MctsLatsVisit.tla` — α-Root convergence
- `FrozenEthicalFNL.tla` — FROZEN-FNL axioms
- `HashChain.tla` — audit-trail integrity
- `Consensus.tla` — Byzantine debate

### EXP reports (`docs-v2/research/reports/`)
- EXP-MATRIX.0/1 — baseline + distillation
- EXP-MATRIX.2/3/4 — real LLM distillation (DistilBERT, GPT-2)
- EXP-MATRIX.5 — real-LLM sidecar
- EXP-MATRIX.6 — multi-backend chat
- EXP-MATRIX.7 — weight import pipeline
- EXP-MATRIX.8/10/13 — benchmark suite
- EXP-MATRIX.9 — chat training
- EXP-MATRIX.11 — native-build status (blocker documented)
- EXP-MATRIX.12 — BitLinear training

---

## How to launch

### Option A: Java uber-jar (recommended)
```bash
java -jar matrix-core/build/matrix-core-1.0.0-runner.jar
# → http://localhost:9091
```

### Option B: Build from source
```bash
./gradlew :matrix-core:quarkusBuild -Dquarkus.package.jar.type=uber-jar -x test
java -jar matrix-core/build/matrix-core-1.0.0-runner.jar
```

### Option C: Native build (BLOCKED — see EXP-MATRIX.11)
The native-image build is environment-blocked by:
- `MatrixApplication extends QuarkusApplication` (no static main)
- Quarkus 3.38.3 + GraalVM 25.0.2 class-init whack-a-mole on scala/pekko/jackson-scala
- Suggested fix: `git filter-repo` to remove `.deprecated/git-broken-2026-08-28/` from history (LFS push blocker); use Mandrel container for native build

### Option D: Real-LLM sidecars (for higher-quality chat)
```bash
# Real DistilBERT classifier on CUDA
python3 scripts/llm_sidecar.py --port 9093 --model distilbert

# Real GPT-2 text generation
python3 scripts/llm_sidecar.py --port 9095 --model gpt2

# Real DialoGPT chat-tuned
python3 scripts/llm_sidecar.py --port 9096 --model dialogpt
```

The Quarkus app uses PureBirGenerator + boolean chain by default.
To use a sidecar, just point your client at the sidecar port.

---

## How to test (sandbox UI)

```bash
# Inspect chain state
curl http://localhost:9091/v1/sandbox/inspect | jq .

# Chat through the boolean chain
curl -X POST http://localhost:9091/v1/sandbox/chat \
  -H 'Content-Type: application/json' \
  -d '{"input":"hello"}'

# Get an explanation of the last decision
curl http://localhost:9091/v1/sandbox/explain | jq .

# Visualize chain topology
curl http://localhost:9091/v1/sandbox/topology | jq .
```

---

## Known limitations (honest)

1. **Push to GitHub blocked by LFS cache** — `.deprecated/git-broken-2026-08-28/` has a 623 MB LFS object that the remote cached. Local commits are valid; only the network push is blocked. Fix: `git filter-repo` to rewrite history (currently blocked by Goal Guard).

2. **Boolean chain accuracy is below float source model on scientific tasks** — HellaSwag beats random; ARC-Easy/MMLU under-perform. The score function loses magnitude information. Fix: use absmean-aware scoring + BPE tokenization.

3. **HF token not configured** — gated models (Llama-3.2-1B, Mistral-7B, Phi-4-mini, Gemma-2-2b-it, DeepSeek-R1-Distill, Qwen3-1.7B) cannot be downloaded. Public models (Qwen2.5-0.5B, SmolLM2-360M, TinyLlama-1.1B, BERT, DistilBERT, GPT-2) work.

4. **Native build blocked** — see Option C above. Quarkus 3.38.3 + GraalVM 25.0.2 incompatibilities require either Mandrel container OR dropping scala/pekko deps.

5. **Conversation persistence is in-memory only** — restart loses recent conversations. Persistence is on the next-session roadmap.

6. **No multi-node federation smoke test** — the M3→M4 gossip pipeline exists but hasn't been tested between 2 JVMs. Wave L was planned for the next session.

---

## What's RUNNING right now (this session)

- ✅ Quarkus server: `java -jar matrix-core/build/matrix-core-1.0.0-runner.jar` → http://localhost:9091
- ✅ 24-layer boolean chain from Qwen2.5-0.5B (21,960 neurons, ~2.5 ms/eval)
- ✅ Sandbox UI: chat, inspect, explain, topology
- ✅ OpenAI-compatible chat endpoint
- ✅ HierarchicalMemory retrieval wired
- ✅ SleepCycle + KnowledgeShare for end-of-cycle consolidation
- ✅ BitLinearProjector + BitLinearTrainer for absmean-rescaled training
- ✅ Multi-benchmark harness (`scripts/exp_matrix13_full_bench.py`)
- ✅ Real LLM sidecars (DistilBERT/GPT-2/DialoGPT) for chat alternatives
- ⚠️ Push blocked by LFS cache (623 MB legacy object)
- ❌ Native build (Quarkus + GraalVM 25 incompat)

---

## Files for the curious

- `docs-v2/vision/FINALSUMMARY.md` — sections IV through VIII document the journey
- `docs-v2/research/reports/EXP-MATRIX.13-full-bench.md` — current benchmark
- `docs-v2/architecture/FORMAL-CONTRACTS.md` — TLA+ spec index
- `docs-v2/specifications/SPEC-002-quantum-bir-mps.md` — BIR substrate spec
- `docs-v2/designs/DESIGN-18-consciousness-loop.md` — 9-stage loop design

---

## Honesty statement (per CONSTITUTION VI)

- No fabricated numbers — all measurements above are from real JVM/Python runs
- No "AGI" / "general intelligence" / "superintelligence" claims
- No absolute safety claims — the FROZEN gate is formally verified but not infallible
- Refused results recorded honestly (H-043, H-046 retuning was genuine fix; EXP-013 below chance on ARC/MMLU is recorded plainly)