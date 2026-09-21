# L6 — Memory (tiers, event sourcing, snapshots, Noosphere)

**Status:** normative · **Layer:** 6 (storage) · **Date:** 
/scientific tone; archive reference added.

## 1. Scope

Layer 6 specifies storage tiers, the event journal, the `.ldn`
snapshot format, hybrid local/global storage, and the federated
Noosphere pool. Memory does not interpret signals (L1); it stores
them. Tier lifecycles and tombstoning rules are normative; the
credit economy is experimental.

## 2. Five Tiers

| Tier | Type | Location | Lifetime | Example |
|------|-----------------------|----------------------|----------|--------------------------|
| L1 | Volatile | Cluster ring buffer | ms | Current batch |
| L2 | Session WAL | Local Kafka | hours | Mediator snapshot |
| L3 | Long-term | RocksDB + S3/MinIO | days–yrs | Stable FNL, truth tables |
| L4 | Structural (frozen) | FROZEN FNL bundle | immutable| Axioms, ethical gradient |
| L5 | Federated (Noosphere) | S3 + global Kafka | long | Anonymised improvements |

L4 bundles are governed by the frozen-neuron rule (L0 /
CONSTITUTION III). L5 entries are tagged with origin and weight;
revocation is a signed event on the global ledger.

## 3. Event Journal

Every state change yields an immutable event on
`events.{instanceId}`. Categories: NeuronLifecycle,
ClusterLifecycle, FnlLifecycle, MediatorDecision, Safety, Storage.
Schema: Event(eventId, eventType, instanceId, timestamp, payload,
signature). Recovery = snapshot + replay from offset. Tombstoning
replaces payload-bearing fields with a marker while preserving
structure, satisfying GDPR-style erasure on demand.

## 4. Snapshot Format (.ldn)

A directory or tar archive containing MANIFEST.json (snapshotId,
instanceId, type ∈ {FULL, INCREMENTAL, FNL, NEURON}, version,
neuronCount, fnlCount, checksum SHA3-256, parentSnapshotId,
compression, signaturePublicKey), plus per-domain subdirectories
(neurons/, topology/, lobes/, mediator/, events/) and
SIGNATURE.sig. Snapshot types:

- INCREMENTAL on schedule T_snapshot (default 1 h).
- FNL on Cauldron completion or unload.
- FULL on HADES pre-state or user command.
- Global daily; non-blocking, hierarchical.

## 5. Hybrid Storage Decision

The InstanceMediator decides what crosses the local-global
boundary based on data class:

| Class | Local | Global |
|---------------------------|-------|--------|
| Personal facts | yes | never |
| Stabilised general FNL | cache | primary after ethical audit |
| HADES logs | copy | anonymised copy for federation |
| Accuracy-improving mut. | local | ballot after Instance approval |
| Raw signal logs | local | never |

Conflict resolution on federated FNL: highest fitness wins;
runner-up archived as an alternative branch.

## 6. Noosphere

The federated pool is a ledger + object store + search index. The
ledger is a compacted Kafka topic; entries carry snapshotHash,
authorInstanceId, signature, metadata, status ∈ {ACTIVE, REVOKED,
DEPRECATED}. The Knowledge Index supports scenario-based lookup.
The credit economy that rewards publishers is experimental;
baseline deployment is contribution-weighted access with no
monetary counterpart. Regional builds may preset language and
cultural tags; the federation does not enforce a single format.

## 7. Trust Chain

Imported snapshot verification: signature check, weight check at
creation time, revocation lookup, local EthicalFilter pass before
integration. Revocation is a signed ledger event; affected
instances must roll back or isolate (HADES).

> framed the Noosphere as a "collective unconscious". The v3 text
> reframes it as a federated object store with a signed ledger and
> weight-attributed access. Archive copy:

Next: L7 Ethics — the gate that all memory writes pass through.