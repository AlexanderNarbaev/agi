# W46.0 — REPOSITORY TOPOLOGY

**The module graph, the entry points and the storage ownership, as they are on 2026-11-04 at
`a6ded233`.**
**Method:** the Gradle build files and the current compiled classes. The compiled tree was
verified fresh against the sources (`find src/main/java -newer build/classes/java/main` → 0
for every module). The W44 maps were used to orient, not as the answer.

---

## 1. The modules

`settings.gradle` is 37 lines. **Fourteen modules are included, three are commented out, two
are outside Gradle entirely, and one is a stray orphan file.**

| the module | included | the production sources | an application? | Quarkus? | what it is |
|---|---|---|---|---|---|
| `matrix-core` | **yes** (`settings.gradle:6`) | 1248 files / 180 856 LOC; 1180 test classes | no `application` plugin; the entry point is the Quarkus `@QuarkusMain` + the native `mainClass` (`build.gradle:371-375`) | **yes** (`build.gradle:5`) | the only Quarkus application: chains A, the epistemic and cognitive core |
| `matrix-brain-runtime` | **yes** (`:37`) | 97 files / 17 526 LOC; 112 test classes | **yes** — `io.matrix.brain.runtime.MindRuntimeCli` (`build.gradle:51-53`) | no | the brain runtime: `TrueMindCycle`, the stages, the graph cluster. **No `@Path` anywhere** |
| `matrix-api-gateway` | **yes** (`:12`) | 33 files / 5 525 LOC; 37 test classes | **yes** — `io.matrix.api.MinimalHttpServer` (`build.gradle:25-31`) | **no** — the module applies only `java`/`jacoco`/`application` | chain B's HTTP server; it carries the Quarkus BOM as plain libraries (`build.gradle:55-67`) |
| `matrix-audit` | **yes** (`:14`) | 6 files / 797 LOC | no | no | `HashChainedLog` — **in-memory only**, no file path in the class |
| `matrix-quality` | **yes** (`:36`) | 19 files / 1 225 LOC | **yes** — `QualityCli` (`build.gradle:37-39`) | no | the 14 goal-guard gates; also holds `ModuleLifecycleRegistryTest` and `SpecImplementationMapTest` |
| `matrix-sdk-java` | **yes** (`:13`) | 12 files / 448 LOC | no (`maven-publish`) | no | publishes `io.matrix:matrix-sdk-java` |
| `matrix-tools-distill` | **yes** (`:7`) | **1 file** / 66 LOC | **yes** — `DistillCli` (`build.gradle:74-77`) | no | **deliberately isolated**: *«No compile-time dependency on matrix-core»* (`build.gradle:5-8`); its own toolchain |
| `matrix-spigot` | **yes** (`:8`) | 3 files / 1 419 LOC | no — a `fatJar` with `Main-Class: io.matrix.spigot.MatrixPlugin` | no | **an independent toolchain: Java 21** (`build.gradle:15-19`) against 25 everywhere else. A Bukkit `JavaPlugin`, **not** a `main` |
| `matrix-operator` | **yes** (`:9`) | 8 files / 468 LOC | no (`java-library`) | no | Kubernetes CRD types only — `TaskCellResource` is a `CustomResource`, **not** a JAX-RS resource |
| `matrix-billing` | **yes** (`:15`) | 10 files / 951 LOC | no | no | **dormant in practice**: the Stripe and Postgres dependencies are deferred (`build.gradle:3`), 0 mains, 0 `@Path` |
| `matrix-observability` | **yes** (`:16`) | 5 files / 363 LOC | no | no | **an orphan**: no inbound `project(':matrix-observability')` edge anywhere, and nothing imports `io.matrix.observability` from it — the 10 users of that package all import `MatrixMetrics`, which lives in **matrix-core** |
| `pilots/smart-home-agent` | **yes** (`:29`) | 1 file / 83 LOC | no | no | depends only on `matrix-sdk-java` |
| `pilots/edu-assessor` | **yes** (`:30`) | 1 file / 103 LOC | no | no | the same |
| `pilots/compliance-bot` | **yes** (`:31`) | 1 file / 127 LOC | no | no | the same |
| `matrix-fpga` | **no — commented out** (`:19`) | 12 files (Amaranth) | — | — | dormant: *«SKIPPED — the amaranth package is not installed»* |
| `matrix-micro` | **no — commented out** (`:20`) | 12 files (C) | — | — | dormant: `gcc -std=c11` builds it, on demand |
| `matrix-ros2` | **no — commented out** (`:21`) | 8 files (Python) | — | — | dormant |
| `matrix-web-ui` | **no — not in Gradle** (`:23-26`) | 397 files (Next.js) | — | — | **active but external**: its own toolchain, its own CI job (`ci.yml:129-150`) |
| `matrix-sdk-python` | **no** (`:34`) | 9 files **with `src/` and `tests/` <!-- no-link: a generic directory name, not a path -->** | — | — | the registry calls it PLANNED with *«no implementation present»* — **contradicted by the tree** |
| `matrix-sdk-js` | **no** (`:34`) | 6 files **with `src/` and `__tests__/`** | — | — | the same contradiction |
| `matrix-core/matrix-core` | **not a module** | — | — | — | **a stray orphan file** `matrix-core/matrix-core/build.gradle` (13 lines): a duplicated `jmhJar` block with a **syntax error** (a stray `}` at line 9). A dead artefact |

**The dormancy summary:** four modules have ≤ 10 production files; `matrix-billing` and
`matrix-observability` have no production caller, no entry point and no HTTP surface;
`matrix-operator`'s `*Resource` classes are Kubernetes CRDs, not JAX-RS resources.

---

## 2. The dependency graph

**There are exactly eight `project(...)` edges in the whole repository, in five files:**

```
matrix-api-gateway  → matrix-brain-runtime   (build.gradle:47)
matrix-api-gateway  → matrix-core            (build.gradle:50)
matrix-api-gateway  → matrix-audit           (build.gradle:52)
matrix-brain-runtime → matrix-core            (build.gradle:27)
matrix-brain-runtime → matrix-audit           (build.gradle:30)
pilots/smart-home-agent → matrix-sdk-java     (build.gradle:7)
pilots/edu-assessor     → matrix-sdk-java     (build.gradle:7)
pilots/compliance-bot   → matrix-sdk-java     (build.gradle:7)
```

`matrix-core` has **no** `project(...)` edge — it is the root of the graph. There is no
`buildSrc/`, no init script (the `gradle/` directory holds only the wrapper) and one
`settings.gradle`.

### **There are NO module-level cycles.** — verified by Tarjan over the transitive closure

```
matrix-api-gateway  → [matrix-audit, matrix-brain-runtime, matrix-core]
matrix-brain-runtime → [matrix-audit, matrix-core]
pilots/*             → [matrix-sdk-java]
for every edge A→B: is A ∈ closure(B)?  →  False for all eight
CYCLES FOUND: False
```

### But three real cycles exist at a finer granularity — **and this is the valuable finding**

**(a) Two genuine class-level mutual-reference cycles inside `matrix-core`** — verified in the
source *and* in the compiled constant pool:

| # | the cycle | the evidence | the exposure |
|---|---|---|---|
| 1 | `io.matrix.ethics.EthicalFilter` ↔ `io.matrix.ethics.frozen.FrozenEthicalFNL` | `ethics/EthicalFilter.java:3` imports `ethics.frozen.FrozenEthicalFNL`; `ethics/frozen/FrozenEthicalFNL.java:3` imports `ethics.EthicalFilter`; both directions in the constant pool | **inside the FROZEN zone** — `AGENTS.md` forbids editing it without an explicit RFC |
| 2 | `io.matrix.agent.AgentBrainService` ↔ `io.matrix.neuron.NeuralMemoryResponse` | `agent/AgentBrainService.java:10` imports `neuron.NeuralMemoryResponse`; `neuron/NeuralMemoryResponse.java:3` imports `agent.AgentBrainService` | not frozen; mechanically fixable (extract an interface) |

These are the **only two** class-level strongly-connected components in the entire production
tree (Tarjan over all 14 modules, 1400+ own classes).

**(b) A 42-package strongly-connected component inside `matrix-core/src/main`.** At *package*
level, `io.matrix.knowledge`, `io.matrix.cognitive`, `io.matrix.neuron`, `io.matrix.api`,
`io.matrix.brain`, `io.matrix.reasoning`, `io.matrix.ethics`, `io.matrix.cluster`,
`io.matrix.events`, `io.matrix.memory`, `io.matrix.simulation`, `io.matrix.snapshot`,
`io.matrix.hades`, `io.matrix.consensus`, `io.matrix.noosphere`, `io.matrix.lifecycle`,
`io.matrix.learning` and others are **all mutually reachable**. Three smaller components
also exist: `io.matrix.mediator ↔ io.matrix.observability`,
`io.matrix.privacy ↔ io.matrix.privacy.storage`, `io.matrix ↔ io.matrix.cli`.

**The epistemically relevant edges inside that component:**
- `knowledge → cognitive`: `KnowledgeStore.java:6`, `KnowledgeWorkbench.java:3`,
  `ObservationAdjudication.java:3`, `ObservationOccurrence.java:3`
  (`import io.matrix.cognitive.microworld.ObservationDisposition`);
  `HebbianBindings.java:3-4` (`import io.matrix.cognitive.CognitiveError`);
- `cognitive → knowledge`: `CognitiveKernel.java:17-20` (`Claim`, `Evidence`, `KnowledgeStore`,
  `KnowledgeWorkbench`), `algorithms/EntailmentMethod.java:3-5` (`Derivation`,
  `DerivationStatus`, `EntailmentChecker`), `CognitiveService.java:10`,
  `CognitiveResource.java:5-7`.

**This is a package cycle without a class cycle** — and it is a cycle that **re-formed**.
`ADR-W44-01 §2.4` deliberately avoided the *class* version by putting the cognitive tables in
a separate store; `CognitiveStore.java:26-32` states the intent explicitly: *«the alternative
was to put the cognitive tables inside KnowledgeStore, which would have forced
io.matrix.knowledge to import io.matrix.cognitive records and created a package cycle»*. The
package cycle came back anyway through `ObservationDisposition` and `CognitiveError`.

**(c) A module cycle avoided by convention rather than by structure.** The W45.2 declaration
lives in `matrix-core` and names chain B's classes **as strings**
(`CanonicalEntryPoint.java:113,115,132`) precisely so that `matrix-core` would not depend on
the gateway — `grep -rn "io.matrix.brain.runtime" matrix-core/src/main` returns only those
three string literals. **The convention is held by a test, not by the compiler.**

**(d) Three split packages across modules** — which breaks JPMS and makes the classpath order
decide the resolution:

| the package | in matrix-core | in another module |
|---|---|---|
| `io.matrix.api` | **92 files** | matrix-api-gateway (`MinimalHttpServer`, `resource/*`, `llm_quarantine/*`) |
| `io.matrix.audit` | `HashChain`, `HashLink`, `FrozenFNLHashChain` | matrix-audit (`HashChainedLog`, `AuditEvent`) |
| `io.matrix.observability` | `MatrixMetrics` | matrix-observability (`MetricsRegistry`) |

**The consequence for the W45.2 declaration:** it names `"io.matrix.api.MinimalHttpServer"`,
a class that exists **only** in matrix-api-gateway, from a class that lives in matrix-core.
The name resolves only on the gateway's classpath — which is exactly why the resolution test
had to live in `matrix-api-gateway/src/test/java/io/matrix/api/CanonicalEntryPointChainBTest.java`.

---

## 3. The entry points

### The `application {}` blocks — there are exactly four

| the module | the main class | the kind |
|---|---|---|
| `matrix-api-gateway` | `io.matrix.api.MinimalHttpServer` | **a serving entry point** (chain B) |
| `matrix-brain-runtime` | `io.matrix.brain.runtime.MindRuntimeCli` | a CLI (a REPL over `TrueMindCycle`) |
| `matrix-quality` | `io.matrix.quality.cli.QualityCli` | a CLI (the 14 gates) |
| `matrix-tools-distill` | `io.matrix.distill.cli.DistillCli` (`matrix-distill`) | a CLI (offline distillation) |

### The serving entry points

| the class | the module | the kind | the file:line |
|---|---|---|---|
| `io.matrix.MatrixApplication` | matrix-core | **the server** — the Quarkus `@QuarkusMain`, HTTP **9091** | `matrix-core/src/main/java/io/matrix/MatrixApplication.java:30` |
| `io.matrix.api.MinimalHttpServer` | matrix-api-gateway | **the server** — the JDK `com.sun.net.httpserver.HttpServer`, **8765** | `matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:1674` |
| `io.matrix.api.llm_quarantine.BrainProduction` | matrix-api-gateway | a server — a second chain B entry point | `…/llm_quarantine/BrainProduction.java:184` |
| `io.matrix.api.llm_quarantine.BrainHttpServer` | matrix-api-gateway | a server | `…/llm_quarantine/BrainHttpServer.java:226` |
| `io.matrix.api.llm_quarantine.BrainServer` | matrix-api-gateway | a server | `…/llm_quarantine/BrainServer.java:31` |
| `io.matrix.api.llm_quarantine.BrainIntegrated` | matrix-api-gateway | a server | `…/llm_quarantine/BrainIntegrated.java:35` |
| `io.matrix.cli.ConversationStreamServer` | matrix-core | a server (a CLI with a `main`) | `…/cli/ConversationStreamServer.java:40` |
| `io.matrix.cli.RealConversationServer` | matrix-core | a server (a CLI with a `main`) | `…/cli/RealConversationServer.java:50` |
| `io.matrix.brain.BrainTelemetry` | matrix-core | a server (a CLI with a `main`) | `…/brain/BrainTelemetry.java:116` |
| `io.matrix.federation.liquid.WebSocketTelemetryServer` | matrix-core | a server — **no `main`**, 0 production references | `…/federation/liquid/WebSocketTelemetryServer.java:39` |

### The CLI entry points — **79, each verified twice**

The method: a source scan for `public static void main` across every `src/main/java` of all
14 modules, **and** an independent `javap -p` scan of every compiled class file, excluding the
inner classes. **Both give 79.** The strict `main(String[])` signature matches 78 —
`MatrixApplication` uses `main(java.lang.String...)`, exactly the kind of entry that a
signature-strict scanner misses. No file contains two `main`s and no inner class declares one.

| the module | the count |
|---|---|
| `matrix-core` | **61** — `MatrixApplication`, `MatrixSimulation`, `MinecraftExperiment`, `SystemDemo`, `ConversationLearner`, `SimpleKnowledgeBase`, `BirBrainCycle`, `BrainTelemetry`, `LearningMemory`, `BrainLoopDemo`, the two liquid benchmarks, and **46** in `io.matrix.cli` (`Conversation*` × 46 plus `ModelDownloader`, `NdjsonToTraining`, `RealConversationCli`, `RealConversationReplay`, `RealConversationServer`) |
| `matrix-api-gateway` | **13** — `MinimalHttpServer` plus 12 in `io.matrix.api.llm_quarantine` |
| `matrix-brain-runtime` | **3** — `BenchmarkRunner`, `KnowledgeForgeIngest`, `MindRuntimeCli` |
| `matrix-tools-distill` | **1** — `DistillCli` |
| `matrix-quality` | **1** — `QualityCli` |
| the other seven modules | **0** |

**This confirms the W44 correction:** the archaeology's scanner missed 11 of the 79 because it
attributed a `main` to the last type declared in a file. Here every file has exactly one
`main` and the file name always equals the top-level type, so the ambiguity does not arise.

---

## 4. The external and optional services

| what | where it is configured | hard or optional |
|---|---|---|
| **PostgreSQL** | `matrix-core/src/main/resources/application.properties:47-50` — `quarkus.datasource.db-kind=postgresql`, `quarkus.datasource.reactive.url=postgresql://${DB_HOST:localhost}:${DB_PORT:5433}/matrix` | **hard for the readiness probe, dead for the business logic.** The reactive PG client is declared (`matrix-core/build.gradle:59-60`), so Quarkus opens the datasource and contributes a datasource health check — **`/q/health/ready` returns 503 when PG is unreachable**. But there is **zero** `Panache`, `@DataSource` or `@Transactional` in `matrix-core/src/main` and **no production code queries it**. The default port **5433** is not the port of the PG on this machine (**5432** is `opencode-postgres`). |
| **Redis** | `application.properties:14` — `redis.uri=${REDIS_URI:redis://localhost:6379}` | **optional / soft-fail.** Read through **Lettuce directly**, not the Quarkus extension (matrix-core does not declare `quarkus-redis-client`): `io.matrix.redis.RedisConfig:24-30` produces a `RedisClient` bean; the consumers are `MatrixResource`, `AgentWebSocket`, `AgentBrainService` and `NeuronIdentityLedger`. `RedisClient.create(uri)` does not connect eagerly. |
| **Kafka** | `matrix-core/build.gradle:76`, `MatrixApplication.java:66-71` produces a `KafkaEventJournal` with the default `localhost:9092` | **optional / fail-soft.** `KafkaTopics.ensureTopics` catches everything and only logs (`events/KafkaTopics.java:59-61`); the producer is lazy. **But the bean is created at start-up**, so every boot without a broker pays a 10 s `AdminClient` timeout. |
| **The OTLP collector** | `application.properties:17-18` — `quarkus.otel.exporter.otlp.endpoint=${OTEL_EXPORTER_ENDPOINT:http://localhost:4317}` | optional |
| **Postgres/Redis containers** | `docker-compose.yml:3,20,33`; the CI smoke test starts `postgres:17-alpine` + `redis:7-alpine` and waits for `/q/health/ready` (`ci.yml:226-248`) | optional for the app, **de facto hard for the CI smoke job** |
| **ONNX Runtime** | `matrix-core/build.gradle:66-67`; `matrix-tools-distill/build.gradle:55-56` | hard for the modules that import `ai.onnxruntime`; quarantined from the decision-path packages. **Extracts ~583 MB into `java.io.tmpdir` per session** (`MatrixApplication.java:33-41`) |
| **Paper/Minecraft** | `matrix-spigot/build.gradle:10`; `docker-compose.yml:163` | optional |
| **The Kubernetes API** | `matrix-operator/build.gradle:18-20` (fabric8 7.2.0) | optional |

---

## 5. The storage ownership

| the store | the path | the owner | the module |
|---|---|---|---|
| **the epistemic SQLite** — the claims, the evidence, the sources, the revisions, the derivations, the contradictions, the dialogue turns, the goals, the learning state | `data/knowledge/workbench.sqlite` — the key `matrix.knowledge.path` (`application.properties:104`), the env `MATRIX_KNOWLEDGE_PATH`, the default also in `KnowledgeWorkbench.java:71` | `io.matrix.knowledge.KnowledgeStore` (`KnowledgeStore.java:170`), opened by `KnowledgeWorkbench` (`KnowledgeWorkbench.java:103-108`) | matrix-core |
| **the cognitive SQLite** — the `cognitive_*` tables, `SCHEMA_VERSION = 4` | **the same file**, with its own connection | `io.matrix.cognitive.CognitiveStore` (`CognitiveStore.java:146`); the class states the design explicitly at `:26-32` | matrix-core |
| **the writer lock** | `<database>.writer.lock` next to the database (`LOCK_SUFFIX`, `WriterLock.java:71`) | `io.matrix.cognitive.WriterLock` — taken by **both** stores; a second **process** is refused, a second store **in the same process** joins | matrix-core |
| **the HDC knowledge base** | `data/mind/hdc_kb.ndjson` — the directory from `matrix.mind.dir` / `MATRIX_MIND_DIR`, the default `data/mind` (`MinimalHttpServer.java:137-140`) | `io.matrix.brain.runtime.PersistentHdcStore` (NDJSON, an atomic tmp+rename) | matrix-brain-runtime (the path is assembled in matrix-api-gateway) |
| **the episodic log** | `data/mind/episodic.ndjson` (`MinimalHttpServer.java:171-172`) | `io.matrix.brain.runtime.EpisodicLog` | matrix-brain-runtime |
| **the BIR rules** | `data/mind/bir.ndjson` (`MinimalHttpServer.java:229`) | `BirKnowledgeBase` + `io.matrix.bir.BirRegistryPersistence` | matrix-brain-runtime / matrix-core |
| **the persistent mind memory** | `data/mind/mind.sqlite` (`MinimalHttpServer.java:190`) | `io.matrix.memory.SqliteMemoryBackend` (`:40`) via `io.matrix.brain.runtime.PersistentMind:57` | matrix-core (the backend) / matrix-brain-runtime (the owner) |
| **the conversations** | `data/conversations/<sessionId>.ndjson` <!-- no-link: a runtime artefact with a placeholder name --> | `io.matrix.learning.ConversationLearner` + the `io.matrix.cli.Conversation*` CLIs | matrix-core / matrix-api-gateway |
| **the chain feature cache** | `data/chain_feature_cache.bin` <!-- no-link: a runtime artefact, not a repository file --> (`ChainFeatureCache.java:57`) | `io.matrix.api.ChainFeatureCache` | matrix-core |
| **the LM head weights** | `data/lm_head_weights.bin` (`LmHeadTrainer.java:77`) | `io.matrix.api.LmHeadTrainer` | matrix-core |
| **the chain state** | `data/chain_state.json` | the chain resources | matrix-core |
| **the inbox** | `data/mind/inbox/` (`MinimalHttpServer.java:288`) | `io.matrix.brain.runtime.RealInboxWatcher` | matrix-brain-runtime |
| **the audit log** | **none** — `io.matrix.audit.HashChainedLog` is **in-memory only**; `data/audit/` is empty | — | matrix-audit |
| **the knowledge packs** | `matrix-core/src/main/resources/knowledge/*.json` — read-only resources, not stores | `io.matrix.knowledge.KnowledgeImporter` | matrix-core |

### **Does a second store duplicate the knowledge store's responsibility? No.**

`SqliteMemoryBackend` is the only other SQLite writer, and it is **chain B's** mind store
(`data/mind/mind.sqlite`), constructed only by `PersistentMind` — a different application, a
different file, a different domain (the brain runtime's episodic and hierarchical memory, not
the epistemic claims). **The two chains do not share a memory store, and no second store
duplicates the canonical knowledge store.** The W46 directive's question is answered: no, and
no store is introduced.

---

## 6. The findings that need a decision

| # | the finding | the evidence | the severity |
|---|---|---|---|
| **F-01** | **the gateway's default mode is `stub`** — `System.getenv().getOrDefault("MATRIX_MODE", System.getProperty("matrix.mode", "stub"))` (`MinimalHttpServer.java:127-128`), so unless `MATRIX_MODE=production` is set, `/v1/analyze` is answered by an **87-line** `StubBrainCycle` and `/health/live` reports `"brain_available": false` | verified by the coordinator in the source | **high** — a deployed gateway on 8765 serves a stub, and the W45.2 canonical declaration says chain B runs `TrueMindCycle` |
| **F-02** | **chain A binds three ports, not one** — the Quarkus HTTP server on 9091, plus the P2P TCP socket on 9094 and the P2P multicast socket on 9093, started by `P2PNetwork`'s `@PostConstruct` (`P2PNetwork.java:28-29,37,108,121`). The mounted route `/api/v1/noosphere/p2p` reaches them | verified by the coordinator in the source | **high** — an undeclared network surface of the application that D2 declared to have "no new network exposure" |
| **F-03** | **the W45.2 canonical declaration mis-attributes `BirBrainCycle` to the epistemic application** | the constant pool places it in chain B (`TrueMindCycle`, `PersistentMind`) and in the unmounted `BrainHttpServer`; **no** chain A route reaches it | **medium** — a defect in the declaration the coordinator wrote in W45.2 |
| **F-04** | **`matrix-core`'s class graph contains a 42-package SCC and two class-level cycles**, one of them inside the **FROZEN** `ethics/frozen/` zone | the Tarjan census over the current tree | **medium** — not a vulnerability; a maintainability fact that a "no new cycles" gate must be calibrated against |
| **F-05** | **three split packages** (`io.matrix.api`, `io.matrix.audit`, `io.matrix.observability`) — which break JPMS and make the classpath order decide the resolution | the package census | **medium** — relevant to the native-image work |
| **F-06** | **stray compiled classes poison any `-cp .` invocation** — `./io/` (3 `.class` files created on 2026-11-04 at 18:28) and `matrix-core/bin/` (**3435** `.class` files from 2026-10-12). Both are gitignored, so Gradle ignores them — and a `javac -cp .` in the repository root silently resolves the stale ones | reported by T2, which caught it in its own first pass | **medium** — a live hazard for the next investigator |
| **F-07** | `matrix-observability` and `matrix-billing` are registered **ACTIVE** in `MODULE-LIFECYCLE.md` but have no production caller and no entry point | the module census | low |
| **F-08** | `MODULE-LIFECYCLE.md` calls `matrix-sdk-python` and `matrix-sdk-js` **PLANNED with no implementation**, yet both have `src/` and tests | the tree | low |
| **F-09** | `matrix-core/matrix-core/build.gradle` is a stray, syntactically broken orphan file | the tree | low |
| **F-10** | the PostgreSQL datasource gates the readiness probe while **no production code reads it** | the datasource grep | low — but it is why `/q/health/ready` is permanently 503 here |
