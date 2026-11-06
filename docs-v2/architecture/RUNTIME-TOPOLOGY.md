# MATRIX — RUNTIME TOPOLOGY

**Status:** `CANONICAL` · **Date:** 2026-11-04
**What it owns:** what actually runs, what it binds, what it serves, and what it does not
reach.

**The evidence behind it:** `docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md` §1–§3 (the
bytecode census of 2026-11-04: the constant pool over the mounted route classes, the cycle
closures, and the chain-isolation gate).

**The rule:** *a compiled class does not prove that its capability is reachable, and a
registered route does not prove that a process reaches it.* Everything below was measured, not
assumed.

**What this document used to be:** the cluster pipeline that was designed and never built. Its
text is preserved in §7, because a design that was not built is evidence of an intent — but it
is **not** the topology of the running system, and the two must not be confused.

---

## 1. The two serving chains

**They do not meet.** The isolation is not a convention: chain B reaches **zero** of
`io.matrix.cognitive.**, zero of the stores, zero of the database-naming classes. What they
share is **179 substrate classes** — the library, not a path to the data.

### Chain A — one Quarkus process, three serving paths

```
io.matrix.MatrixApplication  (@QuarkusMain, matrix-core/src/main/java/io/matrix/MatrixApplication.java:30)
   binds  9091 HTTP (application.properties:31)  +  9094 TCP  +  9093 multicast
   │
   ├── LearningWorkbenchResource   @Path /v1/workbench    57 routes  → KnowledgeWorkbench → KnowledgeStore
   ├── CognitiveResource           @Path /v1/cognitive    11 routes  → CognitiveService    → CognitiveStore
   ├── OpenAIChatResource          @Path /v1               6 routes  → io.matrix.reasoning.BrainLoopService
   └── 36 more resources           /api/v1/**, /v1/**     136 routes
                                    ─────────────────────────────────────────
                                    chain A total: 210 routes
```

**Two of chain A's three ports are undeclared.** `9094` (TCP) and `9093` (multicast) are
bound by `P2PNetwork` in its `@PostConstruct` (`P2PNetwork.java:28-29, 37, 108, 121`) and
appear in no configuration property. **This is the owner's open question F-02** — recorded,
not fixed: a port that binds without being declared is a port that surprises a firewall.

### Chain B — one JDK `HttpServer` process

```
io.matrix.api.MinimalHttpServer  (matrix-api-gateway/.../MinimalHttpServer.java:1674)
   binds 8765 (the -Dport property, then args[0]); 127.0.0.1 unless matrix.security.mode says otherwise
   │
   ├── MATRIX_MODE=production ──► ProductionBrainClient ──► TrueMindCycle ──► BirBrainCycle
   │        (the real brain)                                  ├── PersistentMind ──► data/mind/mind.sqlite
   │                                                          ├── NeuroModulatorHub
   │                                                          └── GoalTracker, HierarchicalMemory
   │
   └── THE DEFAULT ──► StubBrainCycle  (87 lines)   ← WHAT ACTUALLY SERVES BY DEFAULT
```

**28 served routes:** `/v1/analyze` `/v1/explain` `/v1/audit*` `/v1/distill` `/v1/bir`
`/v1/conflicts` `/v1/generalize` `/v1/gpu` `/v1/federate` `/v1/auth/login` `/v1/learn`
`/v1/teach` `/v1/sleep` `/v1/status` `/v1/goals` `/v1/inbox/scan` `/v1/transcode/*`
`/health/live` `/health/ready` `/q/openapi` `/metrics` `/openapi.yaml`.

**And 9 declared but unmounted JAX-RS routes:** `BrainQuarkusResource` (4), `AnalyzeResource`,
`AuditResource`, `ExplainResource` (2), `FederateResource` (2).

> **The default mode is `stub`.** `MinimalHttpServer.java:127-128` and `:300-304`. Unless
> `MATRIX_MODE=production` is set — which `scripts/start-mind.sh` does — `/v1/analyze` answers
> from an **87-line** `StubBrainCycle` and **not** from the brain. The current mode is visible
> in `GET /health/live`, in the field `"mode"`. **This is the owner's open question F-01**: a
> gateway that serves a stub by default is a gateway whose health check lies.

---

## 2. The naming trap

`/v1/brain/think`, `/v1/brain/plan` and `/v1/brain/subagent` are **chain A** routes (inside
`OpenAIChatResource`), while the gateway's own `/v1/brain/**` is **unmounted**. A reader who
knows the gateway and not the workbench will look in the wrong place.

---

## 3. The cycles — six, not five

| the cycle | the class | the status | the production callers |
|---|---|---|---|
| the served nine-stage loop | `io.matrix.reasoning.BrainLoopService` | **SERVED** (chain A) | 1 — `OpenAIChatResource.java:260` |
| the consciousness loop | `io.matrix.reasoning.ConsciousnessLoop` | **SERVED** (chain A) | via the chat path |
| the cognitive kernel | `io.matrix.cognitive.CognitiveKernel` | **SERVED** (chain A) | via `CognitiveResource` |
| the true mind | `io.matrix.brain.runtime.TrueMindCycle` | **SERVED** (chain B, production mode) | 3 |
| the BIR brain | `io.matrix.brain.BirBrainCycle` | **SERVED** (chain B, production mode) | via `TrueMindCycle` |
| the CLI mind | `io.matrix.brain.runtime.MindCycle` | **CLI only** | the command line |

**The two `BrainLoopService` classes are not the same thing.**
`io.matrix.consciousness.BrainLoopService` has **25** callers — **none** of them in a serving
path. `io.matrix.reasoning.BrainLoopService` has **one**, and that one *is* the serving path.
The W44 map named the wrong one; the W46.0 audit corrected it, and the correction is in the
code (`CanonicalEntryPoint`).

**The owner's decision D1** — name the canonical entry point per application, keep the
specialist cycles, merge nothing — is implemented in
`docs-v2/engineering/ADR-W45.2-01-canonical-entry-point.md` and in
`io/matrix/api/CanonicalEntryPoint.java`.

---

## 4. What is reachable, and what is only compiled

| the class | the tests | the production callers | the verdict |
|---|---|---|---|
| `Connectome`, `ReplayPhase`, `SleepConsolidator` | 159 | **0** | an island: tested, unserved |
| `io.matrix.neuron.ConsciousBrain` | 19 references | **0** | an island |
| `io.matrix.consciousness.BrainLoopService` | many | 25, all inside the package | an island |
| the biochemical coefficients | — | — | an island: the bridge always reads `0.5`, and the coefficients have no source |
| `MinimalHttpServer` | — | a `main` at `:1674` | **served** — the W44 label "inactive" is wrong |

**The machine-readable form of this table** is `docs-v2/architecture/CAPABILITY-REGISTRY.yml`:
34 capabilities on **four independent axes** — implementation, integration, verification and
operational status. A capability is `SERVED` only when it names a serving path; it is
`MEASURED` only when it names a benchmark.

---

## 5. The gates that hold this topology in place

| the gate | the command | the result |
|---|---|---|
| the chain isolation | `bash scripts/w45-chain-isolation-check.sh` | 16 checks, 0 failed |
| the isolation self-test (the negative control) | `bash scripts/w45-chain-isolation-selftest.sh` | 6 checks, 0 failed |
| the SIGKILL recovery journey | `bash scripts/w45-cognitive-recovery-journey.sh` | 37 checks, 0 failed |
| the security boundary | `bash scripts/security-boundary-check.sh` | 0 violations |
| the quality gate | `bash scripts/quality-gate.sh` | 271 literal candidates at the baseline of 271 |

**The isolation is proved, not asserted.** The proof is a gate that can go red — and the
self-test is the negative control that shows it does.

---

## 6. Where the evidence lives

| the claim | the evidence |
|---|---|
| the route census (210, 57, 11, 6, 136, 28, 9) | the constant-pool census in `docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md` §1 |
| the chain-isolation numbers | `scripts/w45-chain-isolation-check.sh` and its self-test |
| the cycles and their callers | `docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md` §3 |
| the stub default | `MinimalHttpServer.java:127-128`, `:300-304` |
| the undeclared ports | `P2PNetwork.java:28-29, 37, 108, 121` |
| the capability statuses | `docs-v2/architecture/CAPABILITY-REGISTRY.yml` |
| the persistence | `docs-v2/architecture/DATA-AND-MEMORY-ARCHITECTURE.md` |

---

## 7. Appendix — the cluster pipeline that was designed and never built

**This is the previous content of this document, preserved verbatim.** It describes a
**multi-service cluster**: PostgreSQL, Redis, Kafka and Loki, with a Pekko actor cell and a
per-cell snapshot store.

**None of it is in the running topology.** The measurement of 2026-11-04 found **one**
Quarkus process and **one** JDK `HttpServer` process, one SQLite file in chain A and one in
chain B, and no Kafka, Redis, PostgreSQL or Loki in any serving path. The cluster pipeline is
a **design**, not a deployment — and the SLO table below is the one set of numbers in this
document that was measured on the *prototype* path rather than the production one.

> **Keep it as evidence of an intent. Do not read it as the topology.**

### The services as they were designed

| Сервис | Артефакт | Порт | Конфиг |
|---|---|---|---|
| Quarkus | matrix-core | 9091 (HTTP), 9090 (OTel), 8443 (TLS) | application.properties |
| PostgreSQL | R2DBC | 5433 (dev) | `quarkus.datasource.reactive.url` |
| Redis | Jedis | 6379 | `redis.uri` |
| Kafka | kafka-clients 4.3 | 9092 (dev) | `KAFKA_BOOTSTRAP_SERVERS` env |
| Loki (logging) | JSON-логирование | 4317 (OTel) | `quarkus.log.console.json=true` |

### The cluster pipeline (cell)

```
[ ingress /api/v1/* ] ← OpenAI compat + MCP
 |
 v
[ ai/AgentLoop ] ──► [ brain/Viewpoint ] ──► [ neuron/NeuronLayer.eval → bir/BooleanRuntime ]
 | |
 | v
 ├────────► [ events/KafkaEventJournal (append) ] [ bir/TtForm (cache: actor/federation/FORM_CACHE) ]
 | |
 v v
[ dialog/TelegramBotService ] [ audit/HashChain (x-matrix-trace) ]
```

### The cluster cell-actor (Pekko 1.6)

`cluster/NeuronClusterActor` — actor-узел на одну ячейку сигмоид/Tsetlin-нейронов. Маршрутизация по `TopologyCache`, бюджет памяти через `ConjugateBudgeter`, snapshot через `snapshot/SnapshotStore` каждые N операций.

### Operational SLO (минимальные)

| Метрика | Цель | Реальное (synthetic) |
|---|---|---|
| BIR eval латентность (per-call, CPU) | ≤ 1 µs | ~62 нс (EXP-009C, FFN16 distilled) |
| GPU latency per-call (RTX 5070 Ti, fp32 FFN16) | reference | 17.25 µs (reference) |
| GPU batch throughput N=2000 | reference | 0.020 ms |
| ORT-CPU batch N=2000 | reference | 18.68 ms |

### Безопасность

- `api/TenantFilter` — изоляция тенантов (X-Matrix-Tenant header).
- `security/` — Spring-Security-подобный адаптер; FROZEN-этика доминирует.
- `audit/HashChain` — append-only цепочка; `events/KafkaEventJournal` реплицируется в `ClusterSnapshot` для воспроизводимости.

### Integration tests (Testcontainers 1.21.3)

- KafkaIntegrationTest — Testcontainers Kafka (1.21.3); чувствителен к таймаутам метаданных брокера (зафиксирован флейк на медленном хосте).
- PostgreSQLIntegrationTest, EndToEndIntegrationTest, AgentLoopIntegrationTest, BrcIntegrationTest, BooleanRagIntegrationTest, CompressionIntegrationTest и др. — см. `matrix-core/src/test/java/io/matrix/integration/`.
