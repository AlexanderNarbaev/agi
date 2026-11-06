---
layout: default
title: "What MATRIX is"
---

**Status:** ACTIVE · **Updated:** 2026-11-06

# What MATRIX is

MATRIX is an open research system for **deterministic neuro-symbolic inference**. Knowledge
is represented as truth tables and their compilable forms, the learning path is separated
from the runtime, and a runtime decision is a boolean chain that can be read back.

This page is the plain introduction. It uses no metaphor, and every number on it is
attached to the artefact that produced it — where a claim of this project was once published
and did not survive its own audit, the audit is what appears here.

## The idea

A reasoning system has two very different jobs, and most systems mix them:

1. **deciding** — given the state, what is the answer? and
2. **learning** — given experience, what changes?

MATRIX separates them. The **numerical substrate** — the metrics, the weights, the
deterministic kernels — is implemented as pure functions, without randomness and without a
wall-clock read. The **cognitive layer** — error-driven learning, exploration sampling,
hypothesis generation — may use seeded randomness, but only where the seed is itself a pure
function of the state. Learning is stochastic; the runtime is not.

That is the project's constitution, and it is checked, not merely asserted
([`CONSTITUTION.md`](../../CONSTITUTION.md), Article I).

### What follows from it

| | |
|---|---|
| **No LLM on a decision path.** | The gate scans the runtime modules of both chains for legacy LLM imports and fails the build on a hit; the reproduced value is **0**. It runs as a blocking CI job ([`CAPABILITY-REGISTRY.yml`](../architecture/CAPABILITY-REGISTRY.yml), row `C-01b`) |
| **Every runtime decision is a boolean chain**, auditable through the `x-matrix-trace` header and a hash chain (Article VIII) |
| **The boolean artefacts are bounded**: K_MAX = 20 inputs, and raising it is an amendment to every compiler and backend (Article II) |
| **Four prohibitions hold under any input content**: the system does not kill, does not torture, does not enslave, and does not reproduce without the operator's explicit consent. They are implemented in `EthicalFilter`, `StructuralSafetyGuard`, `LieDetector` and the frozen FROZEN-FNL guardian (Article IV) |
| **The coverage gate** is ≥ 82 % METHOD on `matrix-core`, and lowering it takes an RFC (Article V) |

**And what the constitution forbids in writing.** "AGI", "general artificial intelligence",
"superintelligence"; claims of absolute safety such as "does not lie" or "cannot be used for
harm"; and **numerical characteristics without a measurement**. The last one is why the
numbers below carry their source, and why several once-published numbers are absent
(Article VI).

## The three families of reasoning that are implemented

| | what it does | where it lives |
|---|---|---|
| **BIR** — boolean inference | rules and facts compiled into truth tables, clause sets and BDDs, evaluated deterministically | `io.matrix.bir` |
| **HDC** — hyperdimensional computing | patterns as high-dimensional vectors, compared by similarity | `io.matrix.neuron` |
| **MCTS** — Monte Carlo tree search | planning over a search tree | `io.matrix.mcts` |

Alongside them, an **algorithm portfolio** admits a method only when every precondition the
method declares is present in the request, and ranks the admitted methods by measured data
— eight methods over four families, `SERVED` with a named path
([`CAPABILITY-REGISTRY.yml`](../architecture/CAPABILITY-REGISTRY.yml), row
`W44-ALGO-PORTFOLIO`).

## The architecture, in two lines

The system runs as **two serving applications that deliberately do not call each other**.

```
chain A   io.matrix.MatrixApplication      Quarkus        127.0.0.1:9091   210 routes
chain B   io.matrix.api.MinimalHttpServer  JDK HttpServer 127.0.0.1:8765    28 routes
```

Chain A owns the epistemic store (claims, cases, evidence, revisions) and the cognitive
store (episodes, predictions, errors). Chain B owns the brain cycle and its persistent
mind. The isolation is proved over the compiled classes by a gate that fails when the chains
meet, and by a self-test that breaks the isolation on purpose to show the gate can go red.
The canonical description, with the census behind it, is
[`docs-v2/architecture/RUNTIME-TOPOLOGY.md`](../architecture/RUNTIME-TOPOLOGY.md).

**One flag decides what chain B serves.** Its default mode is `stub` — an 87-line
`StubBrainCycle` — and the real brain runs only under `MATRIX_MODE=production`. The active
mode is reported by `GET /health/live`. This is the owner's open question F-01, and it is
the first thing to check when a gateway answer looks too simple.

## What has actually been measured

These are the audited claims, in the registry's own words, with the corpus each was measured
on. **A claim is a measurement or it is nothing** — so each row names the report and the
scope.

| the claim | the measurement | the scope |
|---|---|---|
| BIR on logic tasks | **75.0 %** over 100 puzzles, against a random baseline of **30.0 %** — and against a heuristic baseline of the same 75.0 % | one named corpus of 100 puzzles ([`BENCHMARK-REPORT-W620.md`](../research/BENCHMARK-REPORT-W620.md) §1) |
| HDC sample efficiency | **AUC 0.899**, reaching 90 % after **50 samples**, against a random AUC of 0.253 | one corpus ([`BENCHMARK-REPORT-W620.md`](../research/BENCHMARK-REPORT-W620.md) §2) |
| MCTS in a headless simulator | **92 %** success with 0 deaths for a 5-node federation, against 78 % for a single agent | a **headless Minecraft simulator**, not a public server |
| federation consensus | **100 %** consensus with 10 %, 30 % and 50 % of the nodes killed, at 100, 500 and 1000 nodes | **a model** ([`FEDERATION-SCALE-REPORT-W635.md`](../research/FEDERATION-SCALE-REPORT-W635.md)) |
| the GPU as an optional accelerator | **12.06×** on the serving path over three runs (12.06 / 11.68 / 11.47); the end-to-end path 12.69×; a single-point ONNX call **0.24×** — about 4× slower | one machine, one model ([`W48-GPU-BENCHMARK.md`](../engineering/W48-GPU-BENCHMARK.md)) |

**And the claims that did not survive.** The registry holds them as audited rows, and the
negative-results index holds them with the cost of repeating them
([`docs-v2/research/NEGATIVE-RESULTS-INDEX.md`](../research/NEGATIVE-RESULTS-INDEX.md)):

- **"33,000× faster than LLMs"** — never measured; the published figure divided a
  *generation rate* by a *single-call latency*, two different quantities. The benchmark
  report records a different number against a **synthetic** LLM ops/sec model. The registry
  marks the claim `REJECTED` and `REMOVED` (row `C-02`).
- **"90 % accuracy retention from LLM distillation"** — the pipeline is documented; the
  retention figure was never measured (`REJECTED`, `REMOVED`; row `C-08`).
- **"100 % Constitution compliant"** — the compliance *mechanisms* exist and run in CI;
  **the percentage is not a measurable quantity** (row `C-03`).
- **"production-grade hybrid AI framework"** — a statement about readiness rather than a
  measurement; the registry records it as unknown on every axis (row `C-09`).
- **the published test counts** — "724 tests", later "9,432 tests / 1,282 suites" — are
  stale numbers of a stale whole. The baseline is a **set of scopes**, each verified on a
  named commit (row `C-01`).

## What is not working

| | |
|---|---|
| the **native-image build** | **BLOCKED** — the GraalVM toolchain is absent on the development host; the native scope refuses to run rather than report a pass |
| the **gateway's default mode** | serves a stub; F-01 |
| chain A's **undeclared ports** | 9093 (multicast) and 9094 (TCP) bind without appearing in any serving record — F-02 |
| the **readiness probe** | permanently 503, and no production code reads it |
| **two formal-spec mutants survive** | `Consensus-evaluate-without-quorum` and `KnowledgeAxes-conflict-forgets-what-it-recorded` |
| the **corpus properties** | skipped on a clean runner, because the corpus is not in git. A skip is not a pass |
| **no whole-database backup** | the SQLite backup API appears nowhere in the repository |

The full list with the owner each item waits on is in
[`docs-v2/governance/PROJECT-STATE.md`](../governance/PROJECT-STATE.md) §4.

## Where to go next

- **Run it** — [Getting started](../guide/getting-started.html).
- **Check a claim** — [the capability registry](../architecture/CAPABILITY-REGISTRY.yml), one row per capability, on four axes.
- **Read the method** — [the research index](../research/RESEARCH-INDEX.md) and [the negative results](../research/NEGATIVE-RESULTS-INDEX.md).
- **Read the rules** — [`CONSTITUTION.md`](../../CONSTITUTION.md) and [`AGENTS.md`](../../AGENTS.md).
- **Contribute** — [`CONTRIBUTING.md`](../../CONTRIBUTING.md).

---

*The earlier, metaphorical telling of this material — the city, the hive, the brain analogy —
is kept in [the brain analogy](brain-analogy.md) and [the federation](federation.md) as the
record of how the project was once described. It is not the source of any claim here, and
where it disagrees with the registry, the registry is right.*
