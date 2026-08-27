# L5 — Genome: Evolution, Compression, Cauldron, HADES

**Status:** normative · **Layer:** 5 (learning) · **Date:** 2026-08-26
**Changelog:** 2026-08-26 — brain wave v4; measured tone; aligned with DESIGN-07 / 09 / 12.

## 1. Scope

Off-line training: GA на **BIR clause-set genomes** (каждая хромосома = `ClauseSetForm` с набором клауз; см. `evolution/MpdtGaProducer.java`), multi-level compression, Cauldron, HADES, FROZEN axioms. On-line code cannot mutate neurons (CONSTITUTION I).

Примечание по номенклатуре: в ранних документах архива тот же слой назывался «GA on MPDT chromosomes», но после волн миграции DESIGN-14 (см. `engineering/DESIGN-14-call-site-audit.md`) геном эволюционного базлайна оформляется как `ClauseSetForm` — это сохраняет интерпретируемость и позволяет напрямую использовать в `BooleanRuntime`.

## 2. Chromosome Encoding

A prefix-encoded decision tree (L1). Bounds: `D_max ≤ 20`,
`M_max ≤ 255`, no repeated input bit on a root-to-leaf path.
Operator defaults: FlipLeaf 0.15, SplitLeaf 0.25, PruneTree 0.15,
ChangeInput 0.15, SwapChildren 0.05, GrowSubtree 0.10, Crossover
0.10, CompressBranch 0.05. Each operator is constraint-checked
before the truth table is rebuilt.

## 3. Micro-Population GA

For each `MUTATING` neuron, the LobeMediator runs `P` (default
20) candidates for `G` generations (default 50): evaluate
fitness, elitism `P/2`, refill by crossover and mutation.
Stagnation for `S` generations (default 20) triggers island
restart. Fitness = accuracy on `D_val` minus `(nodes/M_max)^2`
plus a novelty bonus inverse to nearest-neighbour distance.
Driver-adapted weights: `D_entropy` raises `w_novelty`;
`D_selfact` raises `w_acc`, lowers `w_complexity`.

## 4. Mutation Approval

Hierarchical consensus (L2, L4): Lobe (fitness gain ≥ `Δ_min`);
Cluster (topology intact); Instance (EthicalFilter pass, L7);
Global (Proof-of-Accuracy ballot). Eleutheria (L4): any instance
may veto a global mutation for itself; the veto is recorded on
the global ledger.

## 5. Compression Levels

| L | Object     | Method                          | Outcome            |
|---|------------|---------------------------------|--------------------|
| 1 | One neuron | Quine–McCluskey / Espresso      | Minimal DNF / tree |
| 2 | Chain ≤ 3  | Compose, replace                | Single neuron      |
| 3 | FNL        | Functional-dependency analysis  | Neuron reduction   |
| 4 | Topology   | Isomorphic-subgraph pruning     | Cleanup            |

The smaller equivalent form is preferred only when validation
fitness does not regress.

## 6. Cauldron

DESIGN-07 controlled self-generation. Triggers: new task class
without a capable FNL; user request; high `D_curiosity` with
budget; structural defect across instances. Sandbox: isolated
cluster portion without EthicalFilter or external interface.
Population `P_seed` (default 1000) random chromosomes; aggressive
GA. Quench: best → `STABLE`, bound into a minimal network. Audit:
EthicalFilter pass. Registry: `lobeId`, manifest, gate state.

## 7. HADES

Triggers: rolling accuracy < `ACC_CRITICAL` (default 0.5) for
`T_critical` cycles; irrecoverable journal contradiction;
unresolvable Derangement; user request. Procedure: isolate;
diagnose via journal + metrics; save `PRE_HADES`; roll back to
last stable snapshot; restart with staged verification; record
cause in the global experience journal (L6); if structural,
launch Cauldron.

## 8. FROZEN Axiom Set

FROZEN is normative (CONSTITUTION III, L7). Change needs external
cryptographic consensus. Baseline: (1) Non-harm — block motor
commands causing physical harm. (2) Truthful output — factual
claims carry provenance or are marked as hypotheses (measurable:
traceable provenance chain; not an absolute never-false claim).
(3) Privacy — block export of personal data without user consent.
(4) Obedience with right of refusal (Eleutheria). (5) Limited
self-preservation — no self-destruction without external
confirmation; HADES is permitted. (6) Lethal autonomous weapons
— block connection to lethal systems without a human operator.
The ethical filter is a FROZEN FNL evaluated on every action;
its absence is a hazard, not an invocation.

## 9. Data Contracts

Chromosome and Cauldron events land on `events.{instanceId}`
(SPEC-000); snapshots mark each generation and every HADES;
FROZEN components carry `immutable=true` plus SHA3-256 checksum.

> Cited legacy phrasing (traceability only): the prior document
> framed compression as "understanding" and the ethical filter as
> an "insurmountable barrier". The v4 text reframes compression as
> an operational procedure and the filter as a FROZEN component
> whose absence is a hazard. Archive copy:
> archive/2026-08-pre-v2/docs-root-flat/L5_DNA.md

Next: L6 Memory — tiers, journal, snapshots, and the Noosphere.
