# MATRIX — Open Cognitive Architecture on BIR

**Deterministic neuro-symbolic cognitive architecture based on BIR (Boolean Intermediate Representation).**

Three equivalent compute forms — **TT** (Truth Table), **CLAUSESET** (DNF with Tsetlin automata), **BDD** (Reduced Ordered BDD) — store the same Boolean functions, mutually compilable and mechanically verifiable.

**1055+ tests** · **83.7% coverage (METHOD)** · **Java 25** · **Quarkus 3.37.3** · **Apache Pekko 1.6.0**

> Honesty frame: engineering guarantees in this README are backed by code and benchmarks, or explicitly marked as goals. Long-term research vision (general-purpose cognitive architectures) is in [`docs/vision/OPEN_PROBLEMS.md`](docs/vision/OPEN_PROBLEMS.md) and is not a promise. Phrasing rules — `CONSTITUTION.md`, Article VI.

---

## 🧬 The BIR Paradigm

**BIR (Boolean Intermediate Representation)** is the central concept of MATRIX. Every Boolean function is stored in one of three equivalent forms:

```
BirUnit = (id, k, m, form, payload, header)   — DESIGN-01
```

| Form | Description | Capacity | Best for |
|------|-------------|----------|----------|
| **TT** — Truth Table | Exhaustive `2^k` table | `2^(2^k)` functions | k ≤ 20 (K_MAX), equivalence check |
| **CLAUSESET** | DNF with Tsetlin automata | ≤ `(3^k+1)^C` DNF | Sparse rules, large k (784), data-driven learning |
| **BDD** — ROBDD | Canonical compact form | Canonical, exponential worst case | Compact storage, formal verification |

Forms compile into each other (BirCompiler, SPEC-002), and **equivalence is verified by enumeration of `2^k` inputs** — a single command.

---

## � Key Principles

- **🔍 Determinism** — every decision is a verifiable Boolean chain. No LLM in runtime.
- **🛡️ FROZEN layer** — ethical/domain constraints are immutable, 6 axioms, formally verifiable.
- **⚡ Energy efficiency** — BIR TT eval: **0.64 ns**; Neuron: **197M ops/s**.
- **🧬 Interpretability** — every decision is a tree of logical rules, no black boxes.
- **🌐 Decentralization** — anyone can run an instance; Noosphere mesh synchronizes knowledge.
- **📚 Ethics by design** — three prohibitions (don't kill, don't torture, don't enslave) in FROZEN layer.

---

## 📊 Measured Metrics

| Metric | Target | Measured |
|--------|--------|----------|
| Tests | ≥ 1000 | **1055+** |
| Coverage (METHOD) | ≥ 82% | **83.7%** |
| BIR TT eval (k=10) | < 10 ns | **0.64 ns** |
| Neuron hot path | > 100M ops/s | **197M ops/s** |
| Guardrail FPR | ≤ 5% | **0%** |
| Guardrail TPR | ≥ 95% | **100%** |
| Guardrail P99 | ≤ 50 ms | **0 ms** |
| Hypotheses | — | **34** (9 running, 25 proposed) |

---

## 🏗️ Architecture (C4 Layers)

```
┌─────────────────────────────────────────────────────────────┐
│  API + CLI + MCP (Quarkus 3.37.3)                            │
└─────────────────────────────────────────────────────────────┘
                                  ↓
┌─────────────────────────────────────────────────────────────┐
│  ETHICS (FROZEN) — 6 AXIOMS — EthicalFilter + GuardrailEngine │
└─────────────────────────────────────────────────────────────┘
                                  ↓
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│  BIR CORE    │    │  RAG         │    │  NEURONS     │
│  TT/CLS/BDD  │───▶│  4 strategies│───▶│  BatchEval   │
│  K_MAX=20    │    │  RRF fusion  │    │  ~197M ops/s │
└──────────────┘    └──────────────┘    └──────────────┘
        ↓                ↓                  ↓
┌─────────────────────────────────────────────────────────────┐
│  LIFECYCLE + FEDERATION: TaskCell + Cauldron + NoosphereCRDT │
└─────────────────────────────────────────────────────────────┘
                                  ↓
┌─────────────────────────────────────────────────────────────┐
│  PILOT + ACTIONS: NaiveMinecraftPilot / LearnedMinecraftPilot│
└─────────────────────────────────────────────────────────────┘
```

**8 architectural layers:**

1. **BIR Compute** — `bir.TtForm` · `bir.ClauseSetForm` · `bir.BddForm` · `bir.BirCompiler` · `bir.JvmSimdBackend` · `bir.FpgaBackend`
2. **Memory & RAG** — `rag.HybridBooleanRag` · `rag.BooleanIndex` · `rag.RrfFusion` · `rag.FloatEmbeddingIndex` (ONNX) · `knowledge.KnowledgeGraphStore`
3. **Neural Layer** — `neuron.TruthTable` · `neuron.BatchEvaluator` · `neuron.BatchMemoryAdapter`
4. **Lifecycle & Federation** — `lifecycle.TaskCell` · `noosphere.MeshFederation` · `noosphere.GrowOnlySet` · `noosphere.QuorumChecker`
5. **FROZEN Ethics** — `ethics.EthicalFilter` · `guardrail.GuardrailEngine`
6. **Pilots & Actions** — `pilot.NaiveMinecraftPilot` · `pilot.LearnedMinecraftPilot`
7. **Evolution & Cauldron** — `evolution.EvolutionLoop` · `cauldron.GuhaCandidateGenerator` · `cauldron.LevinSchedule`
8. **Verification** — `verification.LtlModelChecker` · JMH benchmarks

---

## 🚀 Quick Start

```bash
git clone https://github.com/AlexanderNarbaev/agi.git
cd agi
./gradlew build
./gradlew test                                # all 1055+ tests
./gradlew :matrix-core:jacocoTestReport       # coverage report
./gradlew :matrix-core:quarkusBuild \
    -Dquarkus.package.jar.type=uber-jar
java -jar matrix-core/build/*-runner.jar demo
```

**GridWorld simulation** (BIR-agent evolution):

```bash
java -jar matrix-core/build/*-runner.jar simulate -g 20 -p 10 -k 16 --seed 42
```

**Distill HuggingFace weights → BIR:**

```bash
bash scripts/train_local.sh --model HuggingFaceTB/SmolLM2-135M
python scripts/pretrain_neurons.py --architecture llama --layers 6
```

---

## 🧪 Implemented Algorithms

| Algorithm | Hypothesis | Status |
|-----------|-----------|--------|
| BIR-classifier | H-009 | ✅ running |
| WNN/WiSARD | H-010 | ✅ running |
| SDM/Kanerva | H-011 | ✅ running |
| intESN / Binary Reservoir | H-015 | ✅ running |
| MonotoneDecoder | H-016 | ✅ running |
| LTL Model Checker | H-017 | ✅ running |
| GUHA + Levin scheduler | H-018, H-019 | ✅ running |
| LearnedMinecraftPilot | H-005 | ✅ running |
| Guardrail FPR/TPR | H-006 | ✅ done (0% / 100% / 0ms) |
| MeshFederation (CRDT + Kafka) | M4 | ✅ done |

---

## 📚 Documentation (161 documents)

- **Spec (4):** SPEC-000 (Developmental Loop), SPEC-001 (Weight Conversion), SPEC-002 (Boolean Compute), SPEC-003 (Knowledge Topology)
- **Design (13):** DESIGN-01…13 (Units, Composition, Pipeline, Learning, Memory, Signal Modules, Lifecycle, Federation, Monotone Decoder, Binary Reservoir, Budgeter, FNL TaskCell, Action Registry)
- **Engineering (11):** 6 ADRs + ARC42 risks + C4 model + JAVA_NATIVE + ROADMAP
- **Research (9):** HYPOTHESES.md (34 hypotheses), METRICS.md, ANALYSIS-laptop-feasibility.md
- **Papers (1+):** PAPER-01 (hybrid boolean compute on laptop)

Full map: [`docs/INDEX.md`](docs/INDEX.md)

---

## 📜 License & Ethics

- **Code:** [GNU AGPLv3](LICENSE) with Ethical Restrictions
- **Docs:** CC-BY-SA-4.0
- **FROZEN prohibitions:** don't kill, don't torture, don't enslave
- See [`CONSTITUTION.md`](CONSTITUTION.md)

---

## 🔗 Links

- **Site (RU):** https://alexandernarbaev.github.io/agi/
- **Site (EN):** https://alexandernarbaev.github.io/agi/index.en.html
- **GitHub:** https://github.com/AlexanderNarbaev/agi
- **Gitverse:** https://gitverse.ru/AlexandrNarbaev/agi

---

*Last updated: 2026-08-17 · MATRIX Cognitive Architecture · 422 Java files · 314 tests · 34 hypotheses*