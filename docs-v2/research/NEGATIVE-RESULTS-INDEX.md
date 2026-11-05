# NEGATIVE-RESULTS-INDEX

**Status:** `CANONICAL` · **Date:** 2026-11-04 · **Wave:** W46.2 (task T3)
**Scope:** an **index**, not a summary. Every entry points at the document that holds the
finding. Nothing here restates a result in place of its evidence, and nothing here replaces
the evidence.

---

## 0. What this document is, and the rule it exists to enforce

The negative evidence in this repository is real, dated, and in places decisive. It is also
almost entirely invisible from the current navigation: **178** research documents exist under
`docs-v2/research/`, and **83** of them are named by no other document outside
`docs-v2/_raw/` (measured: see §5 of `RESEARCH-INDEX.md`). The four highest-risk findings
named by the W46.0 audit — the `MinimalHttpServer` label, the 24-failure attribution, the
erased observation evidence, the legacy in-process load — each live in one document that a
reader has to know to open.

This index makes them findable. **It does not rewrite them.** The documents indexed here are
the evidence; a correction that contradicts one of them is a correction *to that document*,
not to this index.

> **The rule: never delete an experiment because its conclusion was negative.**
>
> A refuted hypothesis, a failed algorithm, an incorrect measurement, a benchmark regression,
> an abandoned proposal, an invalid assumption, a defect a test exposed, a result that cannot
> be reproduced on this host, and an experiment that could not be completed are **all
> evidence**. They are kept, indexed, and labelled with what repeating them would cost.
> The index removes nothing.

The directive's words, from the W46 governance request: *«Classify them so that future
agents can discover and avoid repeating them.»*

---

## 1. The vocabulary

### 1.1 The lifecycle status of a claim

These are the statuses the indexed documents themselves use. **A status in this index is
copied from its source document, never inferred from a title.** Where a document does not
say, the entry is marked `not tested` or `inconclusive` and says why.

| status | the meaning |
|---|---|
| `accepted` | the claim's preregistered gate was met on the corpus the document names |
| `supported` | the evidence is consistent with the claim, but the gate was not the preregistered one |
| `refuted` | the preregistered gate was measured and the claim failed it |
| `refuted-toy` | the claim failed its gate **on a synthetic/toy corpus** — the refutation is scoped to that corpus and does not transfer to a domain corpus |
| `inconclusive` | the experiment ran but its result settles nothing (no denominator, no comparison, a missing control) |
| `not tested` | no experiment bears on the claim |
| `superseded` | the claim was replaced by a later, better-evidenced statement; the original stays readable |

> The `refuted` / `refuted-toy` distinction is **load-bearing**. Every refuted entry in the
> current registers is `refuted-toy`: the claim failed on a synthetic corpus, and the
> preregistered protocol fixes exactly which gates must be re-measured before the claim may be
> re-opened on a domain corpus. Reading `refuted` there would overstate the finding.

### 1.2 The class of a finding

| class | what it means |
|---|---|
| **a refuted hypothesis** | a registered `H-*` claim whose preregistered gate was measured and failed |
| **a failed algorithm** | a mechanism that does not work as described, measured or read in the source |
| **an incorrect measurement** | a number that was published and is wrong, or was never measured at all |
| **a benchmark regression** | a measured result that got worse against its own baseline |
| **an abandoned proposal** | a direction that was decided against |
| **an invalid assumption** | an assumption a design rests on, which the evidence does not support |
| **a defect a test exposed** | a real defect, found by the wave's own gates |
| **a non-reproducible result** | a result that was measured once and cannot be reproduced on this host or with the tooling present |
| **an experiment that could not be completed** | the experiment was blocked by the environment, not by a negative result |

---

## 2. The index

`id | the finding | class | status | the document that holds it | repeating it would cost | the date | the successor / note`

### 2.1 Refuted hypotheses (the `H-*` register)

| id | the finding | class | status | the document that holds it | what repeating it would cost | date | the successor / note |
|---|---|---|---|---|---|---|---|
| **NR-001** | **H-002** — "CLAUSESET (Tsetlin) beats MPDT-GA on quality×bytes at ≥10× speed" | a refuted hypothesis | `refuted-toy` | `docs-v2/research/HYPOTHESES.md:10`; the protocol `docs-v2/research/protocols/H-002-clauseset-vs-ga.md`; the measurement `docs-v2/research/reports/EXP-002-report.md` | 3 synthetic datasets, seed 42; the preregistered gates are speedup ≥10× (measured: GA ×5.5), accuracy advantage ≥0 pp (measured: GA up to +8.75 pp), artifact ratio ≤100 (measured: GA ×7500 smaller) | undated register; the protocol is the RECON-W30-era card | the gates to re-measure before re-opening: `docs-v2/research/protocols/H-002-clauseset-vs-ga.md` §"Метрики и gates" |
| **NR-002** | **H-003** — "no 'living' learner (GA / three-factor) is justified against Tsetlin" | a refuted hypothesis | `refuted-toy` | `docs-v2/research/HYPOTHESES.md:11`; the protocol `docs-v2/research/protocols/H-003-living-learner.md`; the measurement `docs-v2/research/reports/EXP-003-report.md` | 3 datasets, seed 42; the convergence protocol runs `examplesTo99TrainAcc` (measured: GA 346.3 vs Tsetlin 672.7), `reached99` (GA 2/3 vs Tsetlin 1/3), mean test accuracy (GA .8625 vs Tsetlin .7833) | undated register | the protocol fixes the three gates for a re-run |
| **NR-003** | **H-035** — "EBL speeds convergence ≥2×" | a refuted hypothesis | `refuted-toy` | `docs-v2/research/HYPOTHESES.md:38`; the failure is also recorded as **F-004** in `docs-v2/archive/failures.md:61` | the EBL learner is **×17 slower** than the baseline; refuted on XOR | undated register | the corpus was XOR — a synthetic corpus, hence `refuted-toy` |

### 2.2 Rejected capabilities and abandoned proposals

| id | the finding | class | status | the document that holds it | what repeating it would cost | date | the successor / note |
|---|---|---|---|---|---|---|---|
| **NR-004** | **The connectome as an integration substrate** — `Connectome`, `ReplayPhase`, `SleepConsolidator`, `ClauseExplorer` form a closed island: **159** tests, **0** production callers | an abandoned proposal | rejected | `docs-v2/engineering/W44-HYPOTHESIS-AND-FAILURE-MAP.md` §6 item 1; the deferral `docs-v2/governance/ROADMAP.md` §7; the class evidence `docs-v2/engineering/W44-CLASS-INVENTORY.csv` | 159 tests to maintain for a component no production path reaches; the island also carries a documented guard defect (NR-031) | 2026-11-02 (W44 audit) | revived only by "a use case"; the W37 release already labels it tested-only |
| **NR-005** | **The biochemical modulator as a real mechanism** — the coefficients are hand-picked with no cited source, and the bridge always reads 0.5 | an invalid assumption | rejected | `docs-v2/engineering/W44-HYPOTHESIS-AND-FAILURE-MAP.md` §5 (medium risk) and §6 item 2; `docs-v2/engineering/ADR-W44-01-cognitive-kernel.md` §1 | a source for every coefficient, or the mechanism is a constant with a name | 2026-11-02 | revived only by "a source for the coefficients" (`docs-v2/governance/ROADMAP.md` §7) |
| **NR-006** | **The measurement cluster** — `io.matrix.neuron.ConsciousBrain` has zero production callers against its test references | an abandoned proposal | deferred | `docs-v2/governance/ROADMAP.md` §7 (the deferral table) | the class and its reference tests are dead weight until a research question needs them | 2026-11-04 | the ROADMAP states the revive condition: "a research question that needs it" |
| **NR-007** | **The `io.matrix.consciousness` measurement package** — its callers measure the package, not a serving path | an abandoned proposal | deferred | `docs-v2/governance/ROADMAP.md` §7; the census `docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md` §3 | a decision to serve or retire it; until then the package keeps a measurement surface that looks like a runtime | 2026-11-04 | the ROADMAP states the revive condition: "a decision to serve or to retire it" |
| **NR-008** | **A second serving conductor** — `BrainLoopService` has callers and does not delegate to `TrueMindCycle`; five (now six) cycles coexist and none delegates | an abandoned proposal | decided against | `docs-v2/engineering/W44-HYPOTHESIS-AND-FAILURE-MAP.md` §6 item 3; the decision `docs-v2/engineering/ADR-W45.2-01-canonical-entry-point.md`; the correction `docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md` §3 | the W44 cycle map listed five cycles; the tree has six — the sixth was named by the declaration and omitted from the map | 2026-11-04 | the owner chose **one named entry point per application**, keeping the cycles |
| **NR-009** | **The gateway's JAX-RS facade** — deliberately unmounted; mounting it would make `StubBrainCycle` the served implementation of `/v1/analyze` and `/v1/explain` | an abandoned proposal | rejected | `docs-v2/engineering/W44-HYPOTHESIS-AND-FAILURE-MAP.md` §6 item 4; `docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md` §6 T-03; `docs-v2/governance/ROADMAP.md` §7 | the facade is a stub-shaped decoy: mounting it silently replaces the real `ProductionBrainClient` path | 2026-11-04 | the ROADMAP's revive condition is explicit: **"nothing — it should stay unmounted"** |
| **NR-010** | **A global cognitive conductor** (`TrueMindCycle` for the whole system) | an abandoned proposal | decided against | `docs-v2/governance/ROADMAP.md` §8; `docs-v2/engineering/ADR-W45.2-01-canonical-entry-point.md` | the bytecode says the epistemic application runs a different loop | 2026-11-04 | the owner chose to **name** an entry point per application rather than to merge the cycles |
| **NR-011** | **An authenticated write bridge between the two serving chains** | an abandoned proposal | decided against | `docs-v2/governance/ROADMAP.md` §8; the boundary `docs-v2/engineering/W45.2-CHAIN-BOUNDARY.md` | the owner re-answered and chose **isolation**; any future bridge needs a separate, versioned, provenance-carrying contract (BRIDGE-1…10) | 2026-11-04 | the isolation gate is the standing evidence that the boundary holds |
| **NR-012** | **A non-destructive reload by versioning** | an abandoned proposal | decided against | `docs-v2/governance/ROADMAP.md` §8; `docs-v2/engineering/W45.2-OWNER-DECISIONS.md` | the owner chose **fail-closed with a recoverable snapshot** | 2026-11-04 | the residuals of the chosen design are NR-025 (the erased observation evidence), NR-026 (the legacy in-process door) and the two open questions `Q-W46-01` (the flag's shape) and `Q-W46-02` (no preview) |
| **NR-013** | **Distributed leases for the database writer** | an abandoned proposal | decided against | `docs-v2/governance/ROADMAP.md` §8; `docs-v2/engineering/W46-OWNER-DECISION-PACKET.md` | the owner chose **one writer process per database file**; a lease framework needs a real multi-host use case | 2026-11-04 | — |
| **NR-014** | **A single memory taxonomy** — five exist; and a single goal model — four exist | an invalid assumption | rejected | `docs-v2/engineering/W44-HYPOTHESIS-AND-FAILURE-MAP.md` §6 items 6 and 7 | a consolidation of the taxonomies is a design task, not a discovery | 2026-11-02 | — |

### 2.3 Corrected claims — the numbers the later waves disproved

| id | the claim as published | class | status | the document that holds the correction | what repeating it would cost | date | the successor / note |
|---|---|---|---|---|---|---|---|
| **NR-015** | "**33,000× faster than LLMs**" / "Energy Efficiency 33,000× vs GPU-based LLMs" | an incorrect measurement | `refuted` | `docs-v2/architecture/CAPABILITY-REGISTRY.md` row **C-02**; the rejection `docs-v2/governance/ROADMAP.md` §8 | the published 33,000× is **derived** in `docs-v2/story/overview.md` by dividing 10–100 *tokens/sec* by a *<10 ms inference* — two different quantities. The benchmark report `docs-v2/research/BENCHMARK-REPORT-W620.md` §4 records a **100,000×** speedup against a *synthetic* LLM ops/sec model with an energy ratio of 0.00x | 2026-10-26 (registry) / 2026-11-04 (rejection) | the registry is `SUPERSEDED` → `docs-v2/architecture/CAPABILITY-REGISTRY.yml`; the card is also labelled "Energy Efficiency" while carrying a speed-up |
| **NR-016** | "**90% accuracy retention from LLM distillation**" | an incorrect measurement | `refuted` (no measurement exists) | `docs-v2/architecture/CAPABILITY-REGISTRY.md` row **C-08** (`UNSOURCED — removed`); the rejection `docs-v2/governance/ROADMAP.md` §8; the ledger `docs-v2/research/DISTILLATION-LEDGER.md` | **no measurement of distillation accuracy retention exists in this repository.** The ledger records the pipeline, not a retention figure | 2026-10-26 / 2026-11-04 | the registry row is the record of the removal |
| **NR-017** | "**724 Tests Passing**" | an incorrect measurement | `refuted` | `docs-v2/architecture/CAPABILITY-REGISTRY.md` row **C-01** | the executed count is **9 432** tests across 14 modules and 1 282 suites, 26 skipped, 0 failures, 0 errors — a different number, from `./gradlew test` (the executed count) against `scripts/count-tests.sh` (the source count) | 2026-10-26 | the registry is superseded; the per-scope denominators live in `docs-v2/engineering/TEST-CATALOGUE.yml` |
| **NR-018** | "**9 TLA+ specs, model-checked in CI**" | an incorrect measurement | `refuted` | `docs-v2/architecture/CAPABILITY-REGISTRY.md` row **C-01c`; the correction `docs-v2/engineering/W46.0-FINAL-REPORT.md` §B.4; the per-specification detail `docs-v2/engineering/TLA-STATUS.md` | the registry says 9; the repository holds **15** `.tla` files, of which **14** sit in `formal/` and are gated by `formal/spec-implementation-map.yml` (14 entries, one per spec); the 15th, `docs-v2/tla/FederationConsensus.tla`, is outside the map and therefore **not gated** | 2026-11-04 | the gate itself is `scripts/formal-gate.sh` (a `*.cfg` loop over `formal/`), wired into `.github/workflows/tla.yml` |
| **NR-019** | "**75% accuracy on logic tasks, vs a 25% random baseline**" | an incorrect measurement | `refuted` (the accuracy is right; the baseline is not) | `docs-v2/architecture/CAPABILITY-REGISTRY.md` row **C-04` | BIR **75%** on 100 puzzles is correct; the random baseline is **30%**, not 25% | 2026-10-26 | the registry row carries both numbers |
| **NR-020** | "**the GPU is unavailable to this machine**" | an incorrect measurement | `refuted` | `docs-v2/engineering/W46-HARDWARE-AND-RESOURCE-REPORT.md` §6; the rejection `docs-v2/governance/ROADMAP.md` §8; the mechanism `docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md` §6 T-07 | it was a **loader-path fact, not a hardware fact**: the CUDA provider is compiled in, but the library path is set as the JVM system property `java.library.path` while a native `dlopen` consults the `LD_LIBRARY_PATH` **environment variable**; the loader therefore finds the distro's `libcudart.so.12` (12.4), which lacks `cudaLibraryGetKernel`. With the variable exported, the same model on the same machine runs **12.6× faster: 191.367 ms → 15.190 ms per forward** | 2026-11-04 | the fix is one environment variable in the launcher; the open question is `docs-v2/engineering/W46-OPEN-QUESTIONS.csv` `Q-W46-07` |
| **NR-021** | "`io.matrix.api.MinimalHttpServer` is **inactive-but-tested**" | an incorrect measurement (a mislabel) | `refuted` | `docs-v2/engineering/W44-CAPABILITY-REGISTRY.md` (the annotation at its head); the label itself `docs-v2/engineering/W44-CLASS-INVENTORY.csv` (the `io.matrix.api.MinimalHttpServer` row: `reachability=uncalled`, `cli_main=no`, `lifecycle=inactive-but-tested`) | the class **has a `main`** at `matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:1674`, and it is the serving HTTP server of chain B. The inventory's `cli_main` column is wrong | 2026-11-04 | the registry itself now carries the correction; the inventory is kept as the audit's evidence and is not rewritten |
| **NR-022** | "the W44 failure map attributes **all 24** test failures to the native image" | an incorrect measurement (an attribution) | `refuted` | `docs-v2/engineering/W44-HYPOTHESIS-AND-FAILURE-MAP.md` §4 | 8 of the 9 failing classes assert the presence and behaviour of a native binary, but the ninth — `io.matrix.consciousness.Exp228ComplianceTest`, **1 failure** — is an **ordinary JVM** failure with no native dependency. The truth is **23 native + 1 ordinary JVM** | 2026-11-02 (the map) / 2026-11-04 (the correction) | the map is historical evidence and is not rewritten; the corrected split is the one to read |
| **NR-023** | "the W44 figure of **1131** Markdown documents" | an incorrect measurement | `refuted` | `docs-v2/engineering/W46.0-FINAL-REPORT.md` §B.4 | the audit counted **1162** Markdown documents, **880** outside the `pre-v2` archive; the W44 figure **does not reproduce** from any W44 artefact | 2026-11-04 | the census is `docs-v2/engineering/W44-FILE-CENSUS.json`; the document census `docs-v2/engineering/W46-DOCUMENT-AUDIT.csv` |
| **NR-024** | "the **`io.matrix.consciousness` `BrainLoopService`** is the served nine-stage loop" | an incorrect measurement (a misattribution) | `refuted` | `docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md` §3 | the two `BrainLoopService` classes are **different things**: the `consciousness` one is a framework-free **measurement** loop; the `reasoning` one is the **served** nine-stage loop with exactly one caller | 2026-11-04 | — |

### 2.4 The defects the wave's own gates exposed

| id | the finding | class | status | the document that holds it | what repeating it would cost | date | the successor / note |
|---|---|---|---|---|---|---|---|
| **NR-025** | **The evidence of an observation does not survive a workspace replacement** — the erasure set predates the observation tables: it erases `evidence` but not `epistemic_case`, `observation_disposition`, `observation_occurrence` or `observation_adjudication`. A load therefore leaves a case and a disposition whose evidence rows are gone — the evidence is the part of the record the load takes, and the decision is what it keeps | a defect a test exposed | open, with the owner | `docs-v2/engineering/W45.2-CAPABILITY-DELTA.md` §3 (first row); the risk `docs-v2/engineering/W44-HYPOTHESIS-AND-FAILURE-MAP.md` §5 (HIGH: `KnowledgeStore.deleteWorkspace`, `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeStore.java:700-726`) | the fix is **not made**: it changes what a workspace replacement means, which is the owner's call. The open question is `docs-v2/engineering/W46-OPEN-QUESTIONS.csv` `Q-W46-03` | 2026-11-04 | the two candidate fixes are stated in the delta §3: take the observation tables out of the erasure set, or erase them together with the evidence |
| **NR-026** | **The legacy in-process door** `KnowledgeWorkbench.loadBundledPackage(String)` **replaces without the flag** — the HTTP surface fails closed, but the in-process door does not | a defect a test exposed | open | `docs-v2/engineering/W45.2-CAPABILITY-DELTA.md` §3 (second row) | it still replaces, because an existing test loads the package three times into one workspace and the decision requires a refusal on the second. It is **no longer a silent erase** — it leaves a recoverable snapshot. Deprecating the one-argument method and moving its callers is the next step | 2026-11-04 | the flag itself is the open question `Q-W46-01` |
| **NR-027** | **The writer lock's path alias** — a symlinked path to the database is not resolved, so the lock can be taken twice under two names | a defect a test exposed | open, blocked | `docs-v2/engineering/W46-OWNER-DECISION-PACKET.md` (D4-a); the status `docs-v2/engineering/W46.1-STATUS.md` | it touches the database-ownership enforcement, which the directive protects until the owner approves; it is task `TASK-W46-4` in `docs-v2/governance/ROADMAP.md` §5 | 2026-11-04 | the fix is a defect fix inside an already-approved contract, but the directive protects that contract |
| **NR-028** | **The `activeProviders()` diagnostic returns a hardcoded string** — it reports `{"CPUExecutionProvider"}` unconditionally and therefore cannot report the truth, whether the GPU is in use or not | an incorrect measurement (a diagnostic that measures nothing) | open | `matrix-core/src/main/java/io/matrix/api/OnnxRuntimeAdapter.java:118-123`; the finding `docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md` §6 T-07; the question `Q-W46-07` | the honest sources are the CUDA-registration exception and `OrtEnvironment.getEpDevices()`; ORT 1.29.0 exposes no `OrtSession.getProviders()` | 2026-11-04 | the related task is `TASK-W46-9` in `docs-v2/governance/ROADMAP.md` §5 |
| **NR-029** | **The gateway serves a stub by default** — `MATRIX_MODE` defaults to `stub`, so `/v1/analyze` is answered by an 87-line `StubBrainCycle` and `/health/live` reports `"brain_available": false` | an invalid assumption (a deployment assumption) | open, needs a decision | `docs-v2/engineering/W46-REPOSITORY-TOPOLOGY.md` **F-01**; `docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md` §6 T-01; the evidence `matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:127-128` | a deployed gateway on 8765 serves a stub, while the W45.2 canonical declaration says chain B runs `TrueMindCycle` | 2026-11-04 | the task is `TASK-W46-4` (`F-01`) in `docs-v2/governance/ROADMAP.md` §5 |
| **NR-030** | **Chain A binds three ports, not one** — the Quarkus HTTP server on 9091, plus the P2P TCP socket on 9094 and the P2P multicast socket on 9093, started by `P2PNetwork`'s `@PostConstruct` | an invalid assumption (a declared-boundary assumption) | open, needs a decision | `docs-v2/engineering/W46-REPOSITORY-TOPOLOGY.md` **F-02**; `docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md` §6 T-02; the evidence `matrix-core/src/main/java/io/matrix/noosphere/p2p/P2PNetwork.java:28-29,37,108,121` | an undeclared network surface of the application that the isolation decision declared to have "no new network exposure" | 2026-11-04 | the task is `TASK-W46-4` (`F-02`) |
| **NR-031** | **The `SleepConsolidator` anneal guard is sound only for edges that entered `CONNECTED`** — `markPruned` matches the whole `Synapse` record (state included) while `restore` matches endpoints only, so an edge that entered `POTENTIATED`/`DRESSED_DOWN` is not re-pruned, and a rejected move rewrites such an edge to `CONNECTED` | a failed algorithm (a guard that does not hold) | open | `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/graph/SleepConsolidator.java:251-268` (the class's own javadoc); the risk `docs-v2/engineering/W44-HYPOTHESIS-AND-FAILURE-MAP.md` §5 (medium) | the defect is in the guard of the component that has no production caller (NR-004); it is a latent defect, not an active one | 2026-11-02 | the class's own text calls the state "the production input domain" — which is only true once a sleep cycle reaches production |
| **NR-032** | **The `Impulse` producer does not exist** — the router merges nothing | a failed algorithm | open | `docs-v2/engineering/W44-HYPOTHESIS-AND-FAILURE-MAP.md` §5 (medium): `io.matrix.consciousness.AttentionRouter.merge` | the router's merge is a no-op; any reasoning that assumes impulses arrive is unfounded | 2026-11-02 | — |

### 2.5 Non-reproducible results and experiments that could not be completed

| id | the finding | class | status | the document that holds it | what repeating it would cost | date | the successor / note |
|---|---|---|---|---|---|---|---|
| **NR-033** | **The native-image build cannot be completed on this host** — GraalVM and Mandrel are absent and the mandrel builder image is a multi-GB pull; the **eight** native-image test classes cannot run | an experiment that could not be completed | deferred | `docs-v2/governance/ROADMAP.md` §7; the attempt log `docs-v2/research/reports/EXP-MATRIX.36-native-attempt.md` (*"None succeeded. JVM mode (Quarkus uber-jar, 155 MB) remains the production target."*); the status reports `docs-v2/research/reports/EXP-MATRIX.11-native-build-status.md` and `docs-v2/research/reports/EXP-MATRIX.34-native-status.md` | a multi-GB image pull, or the tooling installed; the eight test classes stay red until then | 2026-11-04 | revived by "the tooling installed, or a decision to drop the native target" (`ROADMAP.md` §7) |
| **NR-034** | **The `/tmp` quota incident** — the ONNX runtime extracts a native library of about **583 MB** into a fresh directory under `java.io.tmpdir` for every session load and never removes it. A wave's worth of starts left **52 directories, 19.5 GB** and filled the per-user quota of the `/tmp` tmpfs; every spawn-based tool — the build, the gates, the shell — began to fail with `EDQUOT` | a defect a test exposed | fixed, but the mechanism remains | `docs-v2/engineering/RECON-W43-STATUS.md` §1.4 (recorded as **R48**) | the launcher now sets `java.io.tmpdir` and `io.matrix.Scratch` removes what earlier runs left behind; **measured**: two consecutive starts leave 0 directories in `/tmp` and 583 MB in `data/tmp`. The mechanism is not gone: the extraction is 583 MB **per JVM** | 2026-11-02 | the `/tmp` quota is **30 GiB** (`docs-v2/engineering/W46-HARDWARE-AND-RESOURCE-REPORT.md`) |
| **NR-035** | **The `matrix-fpga`, `matrix-micro`, `matrix-ros2` modules** are commented out of `settings.gradle` and dormant | an experiment that could not be completed | deferred | `docs-v2/governance/ROADMAP.md` §7 | the toolchains and a concrete use case | 2026-11-04 | revived by "a concrete use case and the toolchains" |
| **NR-036** | **The GPU experiments of EXP-MATRIX.37 / .38** — the ONNX GPU path as MATRIX runs it | a non-reproducible result | superseded by NR-020 | `docs-v2/research/reports/EXP-MATRIX.37-gpu-onnx.md` and `docs-v2/research/reports/EXP-MATRIX.38-gpu-vs-cpu.md`; the correction `docs-v2/engineering/W46-HARDWARE-AND-RESOURCE-REPORT.md` §6 | the historical GPU numbers were measured on a path that could not load the CUDA provider; **the numbers must not be generalised to the current runtime** | 2026-11-04 | the torch-path experiment EXP-009C **does** reproduce (see `RESEARCH-INDEX.md` §3) |
| **NR-037** | **The "hall of shame"** — 11 recorded failures (F-001…F-011) with what was tried, what went wrong and the lesson | a failed algorithm (a family) | kept | `docs-v2/archive/failures.md` | the entries are the cheapest negative evidence in the repository and are the first place to look before re-attempting an old idea | 2026-09-20 (last updated) | F-001 (LLM in the runtime path), F-005 and F-007 (native-image builds), F-009 (test execution timeout) and F-010 (memory leak in long-running tests) are the ones that recur |

---

## 3. How to read an entry

- **The status is the source's, not the index's.** If a document says `refuted-toy`, the entry says `refuted-toy`.
- **The cost column is the point.** An entry that names only a conclusion is a summary; an entry that names what repeating it costs is an index that saves work.
- **The successor column names what replaced it.** A superseded claim is not deleted — it is superseded, and the superseding document is named.
- **A `not tested` or `inconclusive` entry is not a weak entry.** It says the experiment settled nothing, which is exactly what a future agent needs to know before spending the time.

---

## 4. How to add an entry

1. **The evidence first.** An entry cites the document that holds the finding. If no document holds it, the finding is not indexed — **write the finding down somewhere else first** (a report, a wave document), then index it. This index never becomes the primary record.
2. **Copy the status from the source.** Do not infer `refuted` from a title, a filename, or the absence of a result. If the source does not say, use `not tested` or `inconclusive` and say why in the note.
3. **State the cost.** What would a future agent have to do to repeat this, and what would it cost? A number of runs, a corpus, a toolchain, a licence, a decision from the owner.
4. **Name the successor.** If a later finding replaces this one, name the document that replaced it. If there is none, say so.
5. **Do not rewrite the source.** Corrections belong in the source or in a new document that supersedes it, with a date. This index is updated to point at the correction.
6. **Never delete an entry because the conclusion was negative.** If a finding is later overturned, the entry stays and its status moves to `superseded`, with the successor named. A refuted hypothesis that was deleted is a hypothesis that will be re-entered by the next agent who does not know it was tried.
7. **Verify the path.** Every path in this index must exist. A path that does not resolve is a finding to report (`docs-v2/engineering/W46-DOCUMENT-AUDIT.csv` is where the audit records them), not a value to write.

---

## 5. The navigation problem this index does not solve

The index makes the negative evidence discoverable **from this file**. It does not make it
discoverable from `docs-v2/INDEX.md`: that file names research documents in backticks rather
than links, and **6** of its literal path tokens do not resolve
(`SPEC-005-action.md`, `SPEC-006-consciousness-deliberation.md`, `SPEC-007-subconscious.md`,
`WAL.md`, `research/summaries/W60-W64-DEEP-RESEARCH-SYNTHESIS.md`, `scripts/native-launch.sh`).
The repair of the navigation is task `TASK-W46-3` in `docs-v2/governance/ROADMAP.md` §4; it is
**not** done by this index, and this index does not claim to have done it.
