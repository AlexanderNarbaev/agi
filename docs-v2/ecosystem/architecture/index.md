---
layout: default
title: Architecture
nav_order: 6
parent: Ecosystem
permalink: /ecosystem/architecture/
---

**Status:** ACTIVE · **Updated:** 2026-11-06

# Architecture

MATRIX is a **monorepo** whose module graph was measured from the Gradle build files and
the compiled classes, not from a design document
([`docs-v2/engineering/W46-REPOSITORY-TOPOLOGY.md`](../../engineering/W46-REPOSITORY-TOPOLOGY.md);
the runtime side is in
[`docs-v2/architecture/RUNTIME-TOPOLOGY.md`](../../architecture/RUNTIME-TOPOLOGY.md)).

**Fourteen modules are included in `settings.gradle`**, three are commented out, the web UI
and the Python and JavaScript SDKs are outside Gradle entirely, and one stray orphan file
remains in the tree.

## The modules, and what each one actually is

| the module | what it is | the status |
|---|---|---|
| `matrix-core` | 1 248 production files, 1 180 test classes; the epistemic store, the cognitive store, the knowledge workbench, the reasoner, the CLI | **the only Quarkus application** — chain A |
| `matrix-brain-runtime` | 97 files; `TrueMindCycle` and its stages, the persistent HDC store, the goal tracker | a **CLI** — a REPL over `TrueMindCycle`. No `@Path` anywhere |
| `matrix-api-gateway` | 33 files; `MinimalHttpServer` | **a serving entry point** — chain B. A plain JDK `HttpServer`, not a Quarkus application |
| `matrix-quality` | 19 files; the 14 goal-guard gates | a CLI (`QualityCli`) |
| `matrix-sdk-java` | 12 files; `io.matrix.sdk.MatrixClient` and its DTOs | publishes `io.matrix:matrix-sdk-java`, version `0.1.0-T08-SNAPSHOT` — a snapshot, not a released artifact |
| `matrix-tools-distill` | 1 file; the offline distillation CLI | **deliberately isolated** — no compile-time dependency on `matrix-core`, its own toolchain |
| `matrix-audit` | 6 files; `HashChainedLog` | **in-memory only** — the class holds no file path |
| `matrix-spigot` | 3 files; a Bukkit plugin | an **independent toolchain: Java 21**, against 25 everywhere else. A `JavaPlugin`, not a `main` |
| `matrix-operator` | 8 files; the Kubernetes CRD types | types only — `TaskCellResource` is a `CustomResource`, **not** a JAX-RS resource |
| `matrix-billing` | 10 files | **dormant in practice** — the Stripe and Postgres dependencies are deferred; 0 mains, 0 `@Path` |
| `matrix-observability` | 5 files | **an orphan** — no inbound `project(':matrix-observability')` edge anywhere, and the ten users of that package import `MatrixMetrics`, which lives in `matrix-core` |
| `pilots/smart-home-agent`, `pilots/edu-assessor`, `pilots/compliance-bot` | 1 file each | thin pilots that depend only on `matrix-sdk-java` |
| `matrix-fpga`, `matrix-micro`, `matrix-ros2` | Amaranth, C and Python sources | **commented out of Gradle and dormant** |
| `matrix-web-ui` | 397 files, a Next.js project | **intentionally outside Gradle** — its own toolchain and its own CI job |
| `matrix-sdk-python`, `matrix-sdk-js` | 9 and 6 files, with `src/` and their own tests | outside Gradle; the registry calls them planned, which the tree contradicts |
| `matrix-core/matrix-core/build.gradle` | 13 lines | **a stray orphan file** — a duplicated `jmhJar` block with a syntax error. A dead artefact |

## The two serving chains

```
                    chain A — the epistemic application
   io.matrix.MatrixApplication (@QuarkusMain, matrix-core)
      binds  9091 HTTP  +  9094 TCP  +  9093 multicast
      │
      ├── /v1/workbench    57 routes  → KnowledgeWorkbench → KnowledgeStore
      ├── /v1/cognitive    11 routes  → CognitiveService    → CognitiveStore
      ├── /v1  (chat)       6 routes  → io.matrix.reasoning.BrainLoopService
      └── 36 more resources           136 routes
                                  ────────────────────
                                  chain A total: 210 routes


                    chain B — the brain runtime
   io.matrix.api.MinimalHttpServer (matrix-api-gateway)
      binds 8765
      │
      ├── MATRIX_MODE=production ──► ProductionBrainClient ──► TrueMindCycle ──► BirBrainCycle
      │         (the real brain)                                  ├── PersistentMind → data/mind/mind.sqlite
      │                                                          ├── NeuroModulatorHub
      │                                                          └── GoalTracker, HierarchicalMemory
      │
      └── THE DEFAULT ──► StubBrainCycle (87 lines)   ← what actually serves by default
                                  ────────────────────
                                  chain B total: 28 routes
```

**The chains do not meet.** Chain B reaches zero classes of the epistemic and cognitive
stores. What they share is substrate — the library, not a path to the data. The isolation is
proved by `scripts/w45-chain-isolation-check.sh` (16 checks) and shown to be able to fail by
`scripts/w45-chain-isolation-selftest.sh` (6 checks).

**Two ports are undeclared.** 9094 (TCP) and 9093 (multicast) are bound by `P2PNetwork` and
appear in no configuration property — the owner's open question F-02.

## The dependency direction

```
matrix-api-gateway  →  matrix-core          (the gateway's own classes; the isolation gate
                                              is what keeps the *data* apart)
matrix-sdk-java     →  (nothing in the core)
pilots/*            →  matrix-sdk-java
matrix-tools-distill→  (deliberately isolated — no compile-time dependency on matrix-core)
matrix-web-ui       →  the HTTP surfaces of chain A

matrix-core         ←  no incoming edge from the ecosystem layer
```

The rule the ecosystem layer follows: it is transport and packaging. It does not modify the
core, and the core does not depend on it.

## Where the data lives

| the store | the file | the owner |
|---|---|---|
| the epistemic store — schema **v19** | `data/knowledge/workbench.sqlite` | `io.matrix.knowledge.KnowledgeStore` (`matrix-core`) |
| the cognitive store — schema v4, the `cognitive_*` tables | **the same file**, with its own connection | `io.matrix.cognitive.CognitiveStore` (`matrix-core`) |
| the brain runtime's persistent mind | `data/mind/mind.sqlite` | `io.matrix.brain.runtime.PersistentMind` (`matrix-brain-runtime`) |
| the HDC knowledge base | `data/mind/hdc_kb.ndjson` — NDJSON, written by an atomic temp-and-rename | `io.matrix.brain.runtime.PersistentHdcStore` |
| the audit log | **in memory only** | `io.matrix.audit.HashChainedLog` |

The persistence map is canonical in
[`docs-v2/architecture/DATA-AND-MEMORY-ARCHITECTURE.md`](../../architecture/DATA-AND-MEMORY-ARCHITECTURE.md).

## What is deliberately not here

- **No cluster.** An earlier version of the topology described PostgreSQL, Redis, Kafka and
  Loki with an actor cell per service. None of it is in the running system: the measurement
  found one Quarkus process and one JDK server, one SQLite file in chain A and one in chain
  B, and no Kafka, Redis, PostgreSQL or Loki in any serving path. The old text is preserved
  as an appendix in the runtime-topology document, marked as a design and not as the
  topology.
- **No native executable here.** The native-image build is `BLOCKED` on this host: the
  GraalVM toolchain is absent and the native test scope refuses to run rather than report a
  pass.
- **No release version.** The tags that exist predate several waves of work.

## The documents of record

- [`docs-v2/architecture/RUNTIME-TOPOLOGY.md`](../../architecture/RUNTIME-TOPOLOGY.md) — what runs, what binds, what it does not reach.
- [`docs-v2/architecture/CAPABILITY-REGISTRY.yml`](../../architecture/CAPABILITY-REGISTRY.yml) — every capability on four axes.
- [`docs-v2/architecture/DATA-AND-MEMORY-ARCHITECTURE.md`](../../architecture/DATA-AND-MEMORY-ARCHITECTURE.md) — the persistence map.
- [`docs-v2/engineering/W46-REPOSITORY-TOPOLOGY.md`](../../engineering/W46-REPOSITORY-TOPOLOGY.md) — the module graph and the entry points.
- [`docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md`](../../engineering/W46-ARCHITECTURE-TRUTH-MAP.md) — the constant-pool census the route counts come from.

## Next

- [Getting started](../guide/getting-started.html) — run it.
- [The runtime topology](../../architecture/RUNTIME-TOPOLOGY.html) — the measured serving graph.
- [Constitution](/ecosystem/constitution/) — the ethical constraints and the frozen zones.
- [XAI deep dive](/ecosystem/xai/) — where the explanation trace comes from.
