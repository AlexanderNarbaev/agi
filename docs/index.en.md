# MATRIX — A deterministic Boolean compute platform for safe AI

**Boolean Intermediate Representation (BIR). Three equivalent forms — TT, CLAUSESET, BDD. FROZEN ethics. 1055+ tests. 83.7% coverage. Energy 4-5 orders better than LLMs.**

---

## Quick start

```bash
git clone https://github.com/AlexanderNarbaev/agi.git
cd agi
./gradlew test
./gradlew :matrix-core:quarkusBuild -Dquarkus.package.jar.type=uber-jar
java -jar matrix-core/build/*-runner.jar demo
```

Run a GridWorld simulation (BIR-agent evolution):

```bash
java -jar matrix-core/build/*-runner.jar simulate -g 20 -p 10 -k 16 --seed 42
```

Distill a HuggingFace model into BIR weights:

```bash
bash scripts/train_local.sh --model HuggingFaceTB/SmolLM2-135M
python scripts/pretrain_neurons.py --architecture llama --layers 6
```

## The BIR paradigm

```
BirUnit = (id, k, m, form, payload, header)
  id      — unique identifier
  k       — input bits (k ≤ K_MAX = 20)
  m       — output bits
  form    — TT | CLAUSESET | BDD
  payload — form-specific data
  header  — metadata, FROZEN flag, signature
```

| Form | Storage | Evaluation | Best for |
|------|---------|------------|----------|
| **TT** | `2^k` bits | byte lookup | k ≤ 20, hot-path |
| **CLAUSESET** | C clauses × k bits | threshold test | sparse, large k |
| **BDD** | O(f) nodes | graph traversal | formal verification |

Forms compile into each other through `BirCompiler`. Equivalence is verified by enumeration of `2^k` inputs.

## Three equivalent forms

**TT — Truth Table.** Exhaustive table of all `2^k` inputs. At k=20: 128 KB per unit, 2¹⁰⁴⁸⁵⁷⁶ functions.

**CLAUSESET — Tsetlin DNF.** Disjunction of clauses, trained by Tsetlin automata (interact & reward). Scales to k=784 (MNIST).

**BDD — ROBDD.** Canonical compact graph. Parity-k functions need exactly `2^(k-1)` nodes — the theoretical ceiling that motivated the multi-form architecture.

## FROZEN ethics layer

Three prohibitions, enforced at compile time:

1. **Don't kill** — any action causing death is denied
2. **Don't torture** — any action inflicting suffering is denied
3. **Don't enslave** — any action removing choice is denied

The FROZEN layer is structural, not advisory. The compiler refuses bytecode that mutates FROZEN units. The runtime refuses requests that violate them. There is no override path.

## Architecture (8 layers)

```
Layer 8 · API + CLI + MCP              (Quarkus REST + WebSocket)
Layer 7 · FROZEN Ethics                 (EthicalFilter, GuardrailEngine)
Layer 6 · Pilots + Actions              (NaiveMinecraftPilot, LearnedMinecraftPilot)
Layer 5 · Lifecycle + Federation        (TaskCell, MeshFederation, GrowOnlySet)
Layer 4 · Evolution + Cauldron          (EvolutionLoop, GuhaCandidateGenerator)
Layer 3 · Neural Compute                (TruthTable, BatchEvaluator, BatchMemoryAdapter)
Layer 2 · Memory + RAG                  (HybridBooleanRag, RrfFusion, ONNX embeddings)
Layer 1 · Boolean Compute (BIR)         (TtForm, ClauseSetForm, BddForm, BirCompiler)
```

## Implemented algorithms

| Algorithm | Hypothesis | Status |
|-----------|-----------|--------|
| BIR-classifier | H-009 | running |
| WNN/WiSARD | H-010 | running |
| SDM/Kanerva | H-011 | running |
| intESN Reservoir | H-015 | running |
| MonotoneDecoder | H-016 | running |
| LTL Model Checker | H-017 | running |
| GUHA Candidate Generator | H-018 | running |
| Levin Schedule | H-019 | running |
| LearnedMinecraftPilot | H-005 | done |
| Guardrail FPR/TPR | H-006 | done (0% / 100% / 0ms) |

## Benchmarks

| Metric | Value |
|--------|-------|
| Unit tests | 1055+ |
| Coverage (METHOD) | 83.7% |
| TT eval, k=10 | 0.64 ns |
| Neuron hot path | 197M ops/s |
| Guardrail TPR | 100% |
| Guardrail FPR | 0% |
| Guardrail P99 | 0 ms |
| Tracked hypotheses | 34 |

Performance vs local LLM 3B token: **~10⁶× less energy**, **~10�× faster**.

## How it compares

| Property | MATRIX | Local LLM 3B |
|----------|--------|--------------|
| Determinism | bit-exact | stochastic |
| Verifiability | formal proof | statistical eval |
| Energy per decision | ~10⁻¹² J | ~3 J (10¹²× more) |
| Latency | ns to µs | ~50 ms |
| Open-ended language | not supported | supported |
| Hardware | laptop CPU, FPGA | GPU or quantized CPU |
| Ethics | FROZEN struct layer | RLHF, advisory |

## Roadmap

| Phase | Goal | Status |
|-------|------|--------|
| Phase 0 | BIR core (TT/CLAUSESET/BDD) | done |
| Phase 1 | RAG 4-strategy + Recall@5 | done (100% dense) |
| Phase 2 | Guardrail FPR/TPR | done (0% / 100%) |
| Phase 3 | Minecraft Pilot (Naive + Learned) | done |
| Phase 4 | FPGA Backend bitstream | no hardware |
| Phase 5 | Noosphere Mesh Federation | done |
| H-020 | AQ/LAD candidate generation | proposed |
| H-021 | Backward value iteration planner | proposed |
| PAPER-02..04 | Research publications | planned |

## Documentation

161 documents in [`docs/`](https://github.com/AlexanderNarbaev/agi/blob/main/docs/INDEX.md):

- **Spec (4):** SPEC-000..003
- **Design (13):** DESIGN-01..13
- **ADRs (6):** ADR-001..006
- **Research (9):** HYPOTHESES.md, METRICS.md, ANALYSIS-laptop-feasibility.md
- **Papers (1+):** PAPER-01..N

## Next steps

- [Read the full documentation](https://github.com/AlexanderNarbaev/agi/blob/main/docs/INDEX.md) — 161 documents
- [Read the CONSTITUTION](https://github.com/AlexanderNarbaev/agi/blob/main/CONSTITUTION.md) — axioms, invariants, governance
- [Browse hypotheses](https://github.com/AlexanderNarbaev/agi/blob/main/docs/research/HYPOTHESES.md) — 34 tracked hypotheses
- [Read PAPER-01](https://github.com/AlexanderNarbaev/agi/blob/main/docs/research/papers/PAPER-01-hybrid-boolean-compute-laptop.md) — formal analysis

## License & Ethics

- Code: GNU AGPLv3 with Ethical Restrictions
- Docs: CC-BY-SA-4.0
- FROZEN prohibitions: don't kill, don't torture, don't enslave
- See `CONSTITUTION.md`

## Links

- **Site (EN):** https://alexandernarbaev.github.io/agi/index.en.html
- **Site (RU):** https://alexandernarbaev.github.io/agi/
- **GitHub:** https://github.com/AlexanderNarbaev/agi
- **Gitverse:** https://gitverse.ru/AlexandrNarbaev/agi

---

*Last updated: 2026-08-17 · MATRIX Cognitive Architecture · 422 Java files · 314 tests · 34 hypotheses*