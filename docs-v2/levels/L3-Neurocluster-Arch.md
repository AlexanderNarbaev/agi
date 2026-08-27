# L3 — Neurocluster & Functional Neural Lobe (FNL)

**Status:** normative · **Layer:** 3 (composition) · **Date:** 
scientific tone; archive reference added; aligned with SPEC-002 BIR.

## 1. Scope

Layer 3 определяет, как **BirUnit** (атомарная вычислительная единица BirRuntime; см. `specifications/SPEC-002-boolean-compute-layer.md`) компонутся в масштабируемую ячейку исполнения (`neuron/NeuronClusterActor`) и в функциональный нейронный кластер (FNL, DESIGN-12).

Историческая номенклатура «BirUnit (L1)» вытеснена BirUnit (см. `levels/L1-BirUnit-Legacy.md` для архивной полноты).
The cluster is a Pekko actor owning a neuron pool; the FNL is a
logical group of clusters with a manifest. L1 owns truth-table
semantics; L2 routing; L4 scheduling; L7 the ethics gate.

## 2. Cluster Identity

A `NeuronClusterActor` carries `clusterId` (UUID), `role` ∈
{sensor, wm, ltm, motor, mediator}, `generation` (monotonic on
topology or composition change), and `capabilityVector`. Invariants:
K_MAX = 20 inputs per neuron (CONSTITUTION II); `FROZEN` neurons
are immutable and excluded from mutation lists.

## 3. Internal Structure

`activeNeurons: Map<NeuronId, Bir>` (`STABLE` / `FROZEN`; hot
path); `pendingMutations` (`LEARNING` / `MUTATING`); `topologyCache`
(precomputed inputs); `inputQueue` (ring buffer); `outputBuffer`
(Outbox to Kafka); `eventLog` (local Kafka topic); `metrics`
(rolling windows). `FROZEN` entries never appear in mutation lists.

## 4. Lifecycle

`LEARNING` and `MUTATING` neurons sit in `pendingMutations`. A
transition to `STABLE` runs Quine–McClusky / Espresso (DESIGN-09
monotone decoder) and emits `Stabilised` on `events.{instanceId}`
(SPEC-000). `FROZEN` neurons encode ethical or safety axioms and
are immutable.

## 5. Batched Inference

Cycle `T_cycle` (default 1 ms) on a Java virtual thread:
(1) drain `inputQueue` for the cycle window; (2) group signals by
target neuron and update `currentInputs`; (3) call
`BooleanRuntime.evaluateBatch(Bir[], long[][])` (SPEC-002
keystone; DESIGN-14 migration); (4) emit changed outputs into
`outputBuffer` by destination cluster; (5) flush `outputBuffer`
atomically with state. Latency budget: < 10 ns per neuron lookup;
throughput target > 1e9 signals/s per node.

## 6. Functional Neural Lobe (FNL)

An FNL groups one or more clusters (or parts) with a
`LobeMediator`. Each lobe carries `lobeId`, `LobeManifest`
(capabilities, deps, resources, gateway neurons, optional parent).
Gateway neurons are the only neurons with global I/O. The
`LobeMediator` runs a limited driver set (Curiosity, Entropy);
mutations spanning more than 10 neurons require `ClusterMediator`
approval.

## 7. Just-In-Time Loading

`ClusterMediator` resolves `lobeId` → snapshot (S3/MinIO or peer),
verifies Ed25519 signature, and pulls. The lobe binds its gateway
neurons, integrates into the inference cycle, and unloads on task
completion or memory pressure after state sync.

## 8. Sharding, Scaling, Recovery

`Pekko Cluster Sharding` partitions neurons by `hash(NeuronId)`;
the shard coordinator rebalances on node join/leave. Migration is
non-blocking (snapshot → restore → re-register). Backpressure
propagates when `inputQueue.depth` exceeds `highWaterMark`; the
Mediator may load additional FNLs. `Pekko Persistence` restores
state from journal + snapshot; in-flight signals replay from the
last processed Kafka offset. Trials in `pendingMutations` may be
lost without affecting stable inference.

## 9. Derangement & HADES

A cluster with rolling entropy above threshold or outputs diverging
from invariants is marked `QUARANTINED`. Signals undergo double
verification; routing bypasses it; HADES (DESIGN-07, L5) is
initiated by the upstream Mediator.

> framed the cluster as a "minimal agent" and FNL as a "cognitive
> lobe in the brain". The v4 text reframes it as a Pekko actor with
> a BIR pool and a manifest-driven lobe. Archive copy:

Next: L4 Mediator — the hierarchy that schedules and gates the
cluster's inference.