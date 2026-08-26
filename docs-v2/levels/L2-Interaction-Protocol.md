# L2 — Interaction Protocol (signal, routing, consensus)

**Status:** normative · **Layer:** 2 (transport) · **Date:** 2026-08-26
**Changelog:** 2026-08-26 — brain wave v3 levels; rewrite in measured
/scientific tone; archive reference added.

## 1. Scope

Layer 2 specifies the communication substrate of the matrix-core
service: the signal unit, the Neuron Batch Protocol (NBP),
hierarchical routing, weighted consensus, and the HADES recovery
protocol. Layer 2 does not define truth-table semantics (L1) or
cluster layout (L3); it specifies how messages move.

## 2. Signal Model

A **signal** is a single-bit message between two neurons.

```
Signal {
  senderId:   NeuronId   // UUID + generation
  receiverId: NeuronId
  value:      boolean    // 0 or 1
  timestamp:  long       // sender-local monotonic clock
  priority:   int        // 0 normal, 1 critical
}
```

Signals are aggregated by the cluster actor into a `NeuronBatch`
every T_batch (default 5 ms). Compression is adaptive across RLE,
bitmask, and LZ4, selected by traffic profile. Transport uses
Apache Kafka; topic `neuron-batch.{targetClusterId}`.

## 3. Routing

- Local routing (intra-cluster): direct delivery, no network, no
  signature. Target latency < 1 µs.
- Inter-cluster: NBP via Kafka. Target latency < 10 ms.
- Global resolution: Kademlia DHT on Pekko Cluster. Key = SHA-1 of
  NeuronId (160 bits). Cold lookup budget < 100 ms; hot path uses
  cached routes on dedicated Kafka topics.
- A neuron may have at most G_MAX (default 4) global inputs; the
  rest must be local. This caps the diameter of remote dependency.

## 4. Proof-of-Accuracy Consensus

Decision weight per node is a deterministic function of accuracy,
uptime, and contribution; the function is verifiable by all
participants. Decisions are classified:

- L0 — single FNL: LobeMediator.
- L1 — single cluster: ClusterMediator + InstanceMediator notice.
- L2 — single instance: InstanceMediator, user-revisable.
- L3 — cross-instance: weighted council, threshold 2/3 of
  participating weight.

Voting window T_consensus; signed Vote messages on
`consensus.proposals`. Partitioned branches reconcile on reconnect
by selecting the chain with greater accumulated weight.

## 5. Cryptographic Layer

Every message is signed with Ed25519. Public keys are recorded in
the Neuron Identity Ledger (compacted Kafka topic). Keys rotate
every T_key_rotation (default 7 days), signed by the previous key
and admitted by consensus. HADES (L5) revokes compromised
identifiers.

## 6. Failure Handling

- Circuit Breaker: per-peer error rate > ERROR_THRESHOLD opens the
  circuit for COOLDOWN_PERIOD; buffered or rerouted traffic.
- HADES triggers: accuracy < ACC_CRITICAL for T_critical cycles,
  unresolvable log contradiction, persistent Derangement. Procedure
  isolates, snapshots, rolls back, restores gradually, logs cause.
- AP / PACELC-Latency: the system prioritises availability under
  partition and latency in steady state. Eventual consistency on
  reconnect; no synchronous waits on writes in the inference path.

## 7. Wire Formats

Avro schemas in the matrix-core Avro resource bundle (frozen per
CONSTITUTION VII): Signal, NeuronBatch, Proposal. Schema migration
follows the Avro-resource rule. Compression enum: NONE, LZ4, RLE,
BITMASK. ProposalType enum: MUTATION, TOPOLOGY_CHANGE,
KEY_ROTATION, PROTOCOL_UPGRADE.

## 8. Audit

All consensus decisions, key rotations, and HADES invocations
emit x-matrix-trace entries with deterministic identifiers
(DESIGN-03).

> Cited legacy phrasing (traceability only): the prior document
> framed the protocol as the "nervous system of MATRIX". The v3
> text replaces that framing with measurable latency budgets and
> verifiable consensus rules. Archive copy:
> archive/2026-08-pre-v2/docs-root-flat/L2_Iteraction_protocol.md
> (note: original filename contains a typo, "Iteraction").

Next: L3 Cluster & FNL Architecture — local execution semantics.