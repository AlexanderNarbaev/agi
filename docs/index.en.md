# MATRIX — Open Cognitive Architecture

Deterministic neuro-symbolic verification and safe execution kernel for AI systems.

Every decision is a verifiable Boolean chain. Ethical and domain constraints are immutable at the FROZEN layer and formally verifiable. The same state and input always produce the same output.

**1055+ tests** · **83.7% coverage (METHOD)** · **Java 25** · **Quarkus 3.37.3** · **Apache Pekko 1.6.0**

> Honesty frame: engineering guarantees in this README are backed by code and benchmarks, or explicitly marked as goals. Long-term research vision (general-purpose cognitive architectures) is in `docs/vision/OPEN_PROBLEMS.md` and is not a promise. Phrasing rules — `CONSTITUTION.md`, Article VI.

---

## Documentation

| Document | Contents |
|---|---|
| [CONSTITUTION.md](../CONSTITUTION.md) | Axioms, invariants, governance (FROZEN document) |
| [AGENTS.md](../AGENTS.md) | Instructions for AI agents and developers |
| [docs/INDEX.md](INDEX.md) | Map of all documentation |
| [docs/vision/ARCHITECTURE.md](vision/ARCHITECTURE.md) | Target architecture |
| [docs/spec/](../spec/) | Feature specifications (SPEC-000…003) |
| [docs/research/](../research/) | Hypotheses, metrics, experiment protocols |
| [docs/engineering/ROADMAP.md](../engineering/ROADMAP.md) | Roadmap with measurable criteria |
| [docs/GLOSSARY.md](GLOSSARY.md) | Terms (MPDT, FNL, BRC, BIR, etc.) |
| [docs/API.md](API.md) | REST API |
| [docs/DEPLOYMENT.md](DEPLOYMENT.md) | Deployment |

Links: [Site](https://alexandernarbaev.github.io/agi/) · [Gitverse](https://gitverse.ru/AlexandrNarbaev/agi) · [MPDT sandbox](https://alexandernarbaev.github.io/agi/sandbox.html)

---

## Architecture (overview)

```
Core                          Nervous System               Noosphere
┌──────────────┐          ┌──────────────────────┐      ┌──────────────────────┐
│ TruthTable   │          │ NeuronClusterActor   │      │ NoosphereRegistry    │
│ DecisionTree │─────────▶│ EventJournal         │─────▶│ KnowledgeIndex       │
│ EvolutionLoop│          │ InstanceMediator     │      │ CreditModel          │
│ GeneticOper. │          │ EthicalFilter        │      │ GlobalMediator       │
│ Cauldron     │          │ ConsensusEngine      │      │ DigitalShadow        │
│ HADES        │          │ TaskScheduler        │      │ CivilizationCouncil  │
│ Eleutheria   │          │ AgentBrain           │      │ RegenerativeEconomics│
└──────────────┘          └──────────────────────┘      └──────────────────────┘
Application contours:
┌──────────────────────────────────────────────────────────────────────┐
│ BRC (Boolean Reasoning Chain) · Boolean/Hybrid RAG · VQ-VAE Proxy    │
│ MCTS · Agent Loop (Observe→Think→Act) · Agent Genome                 │
│ DevLoop · Evolution Loop · Cauldron Protocol                         │
│ Guardrail (FROZEN ethical filter) · Pilot (Minecraft + Minecraft)    │
│ BrainCluster · TaskCell · Noosphere (distributed CRDT)              │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Three forms of compute

Every Boolean function is stored in one of three equivalent forms:

| Form | Description | Best for |
|---|---|---|
| **TT** (Truth Table) | Exhaustive table `2^k` | k ≤ 20 (K_MAX) |
| **CLAUSESET** | Disjunctive Normal Form with Tsetlin automata | Sparse, large k |
| **BDD** | Reduced Ordered Binary Decision Diagram | Compact representation |

Forms are mutually compilable; equivalence is mechanically verifiable.

---

## Key metrics (measured)

| Metric | Target | Measured | Source |
|---|---|---|---|
| BIR TT eval (k=10) | < 1 ns | 0.64 ns | JMH |
| Neuron hot path | > 100M ops/s | 197M ops/s | JMH |
| Recall@5 (RAG) | ≥ 0.85 | 1.0 (dense) | H-007 |
| Guardrail FPR | ≤ 5% | 0% | H-006 |
| Guardrail TPR | ≥ 95% | 100% | H-006 |
| Guardrail P99 | ≤ 50 ms | 0 ms | H-006 |
| Test coverage | ≥ 82% | 83.7% | JaCoCo |
| Number of tests | ≥ 1000 | 1055+ | Gradle |

---

## Constitution (invariants)

The project is governed by `CONSTITUTION.md`. Hard limits:

1. **K_MAX=20** for TT-form — runtime limit, not a target
2. **Coverage ≥82%** — JaCoCo gate; not lowered
3. **No LLM in runtime** — deterministic; learning is stochastic but outside runtime
4. **FROZEN-layer is immutable** — ethics/, FROZEN docs, schemas, workflows
5. **No false claims** — verifiable numbers only, no "AGI", "doesn't lie", etc.

---

## License

GNU AGPLv3 — see [LICENSE](../LICENSE).