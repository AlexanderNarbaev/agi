# MATRIX — PROJECT STATE

**Status:** `CANONICAL` · **This document is a SNAPSHOT, not a history.** It describes the
project at one commit and is replaced, not appended to, when the state moves.
**Snapshot of:** `b7bd1be4` · **Date:** 2026-11-20
**Validation:** `scripts/validate-project-state.py --strict` — it checks that the commit below
is the repository's HEAD, that the wave and the baseline below are the current ones, and that
the canonical documents it names exist. It reports a lag of one commit as an OBSERVATION, not
as a defect: the snapshot names the commit it was written at, and the commit that follows it
is the one that carries the snapshot itself.
**The wave that produced this snapshot is the EPISTEMIC-INTEGRITY initiative, and it is
RELEASED.** The commit named above was observed at 2026-11-20
(`git rev-parse origin/develop`): the **EPISTEMIC-INTEGRITY** wave — the owner's directive of
2026-11-15, *does the system answer what it was asked, on the evidence it claims, and can that
answer be measured on data the development cases never saw?* — is complete on `develop`, and its
**release pull request #63** (`develop` → `main`) was **merged on 2026-11-20 at 09:32:44 UTC** as
the squash **`188d756e`**, with the ruleset's own logged admin bypass (**OD-074**) — the bypass
covered **only** the requirement that cannot be satisfied (one approving review from a code owner,
in a repository whose sole collaborator is the author of the work). The head that was merged,
`5c7e2e2c`, was green on the fast lane (run `44706302593`) and on the **full** release lane (run
`44706309984`), and `main` was synchronised back into `develop` (the merge `72c09e2f`) — the two
trees are **identical** (`03e4829b`). The wave's report is
`docs-v2/engineering/EPISTEMIC-FINAL-REPORT.md`, its task graph and append-only evidence ledger
are `docs-v2/engineering/EPISTEMIC-EXECUTION-PLAN.md`, and its **retained evidence** — **52** JUnit
XML files, **237** tests, **0** failures — is tracked in `docs-v2/engineering/evidence/`.
**What it delivered, measured**: the asking path **states** the premise's disposition instead of
conflating "unsupported" with "false", and a refusal names the claim and the source that deny the
premise; the corpus's conflicts and ambiguous readings reach the caller; the concept identity layer
was repaired and measured (**20** referent concepts carrying **29** claims and **23** claim-scoped
records in the owner's migrated live store, the migration rehearsed with a proven rollback); the
evaluation records can no longer enter the durable organic learning (**212** of **226**
quarantined, the log byte-identical, the gate fails closed); the packaged application opens its
SQLite store with no manual property; the CI became a fast gate on `develop` plus a
credential-gated release and publish lane; and the sealed held-out set's quality moved
**17/25 → 18/25** on the one metric that moved, with the abstention and the unsupported rate
unmoved. **What it did NOT deliver, and what stays the owner's**: the fold replacement (ADR §4.4,
options A/B/C — **OPEN**), the peer's durable payload contract (**OPEN**), the Exp324 threshold
(**2 ms**, unchanged), the Docker Hub publication credentials (R-W48-04), and the merge of the
release pull request itself.

**The wave before it was CONCEPT-TO-ACTION** — from demonstrated components to a verifiable
cognitive product — committed on its own branch (`feature/concept-to-action`, pull request #60,
squash `dfd24b44`) and **released into `main`** by pull request #61 (squash `26c549c4`), with
`main` merged back into `develop` (`1194f918`); **the trees were identical at that
synchronisation** (`1e70749981fcc9b3b00a93e4ac6a5eca770efd07`). **They are not identical now**:
the difference is the **initiative's own work** — **563** commits and **198** files, +43 942/−2 522,
of which the wave's **111** commits (`1194f918..develop`) are the EPISTEMIC-INTEGRITY work and the
rest are the develop-side re-materialisations of already-released waves (their trees are equal, so
they contribute **no** changed file). `main` is a strict subset of `develop`, and `develop` leads it
by **unreleased work**, not by release bookkeeping. What CONCEPT-TO-ACTION
delivered is measured in `docs-v2/engineering/C2A-FINAL-REPORT.md`: **one real route**
(`POST /v1/workbench/workspaces/{id}/reports`) over the project's existing bounded action,
verified over real HTTP against the packaged server; the writer's six residual risks closed
(with a negative control that kills the guard); the retrieval's three frozen candidates
measured on the development cases **and** on an independent held-out set, with **no candidate
promoted** and the eleven failures attributed **per gold**; and the learning subsystem measured
on 59 newly executed real observations, with the active window carrying both classes for the
first time. **What it did NOT deliver**, and what stays the owner's: the fold replacement
(ADR §4.4, options A/B/C — **OPEN**, and now with a measured reason), the peer's durable payload
contract (**OPEN**), the Exp324 threshold (**2 ms**, unchanged), and a promotion of any
retrieval candidate (the recommendation is **NO**).
The wave before it was the EVID-BASE wave — the evidence base and the first real bounded
action — which was committed on its own branch
(`feature/evid-base-evidence-to-action`, pull request #53, squash `01d4cdc0`) and released
into `main` by pull request #56 (squash `32666218`); the wave before it, the W53
distributed-cognition wave, was committed on its own branch
(`feature/w53-distributed-cognition` @ `8cfcf854`), merged into `develop` by `48bfe37a`,
and **released into `main` by pull request #50** (squash `bcdd1a0a`, 2026-11-14); the
release record followed (pull request #51, squash `c4cee8ab`) and the registers naming the
release's own commits followed it (pull request #52, squash `1dc01537`). **EVID-BASE** — the
evidence-base and evidence-to-action wave — came after it: pull request #53
(`feature/evid-base-evidence-to-action` → `develop`, squash `01d4cdc0`), the state
correction (#55, `5ff4964a`), the negative-control script's fix (#57, `2b531dd0`) and the
**release** (pull request #56, `develop` → `main`, squash **`32666218`**, 2026-11-14), with
`main` merged back into `develop` (`4257d6df`). **`main` is at `32666218` and it is contained
in `develop`'s history**: the integration branch carries the released squash back through the
post-release synchronisation, which is why `develop` leads `main` by release bookkeeping
rather than by unreleased work.
A reader must expect the snapshot to lag the branch by exactly the commit that carries it
(the convention above); **the release itself IS pending — pull request #63 is open and the merge
is the owner's**.
**This snapshot replaces the one of 2026-11-15** (which named `42919371` and CONCEPT-TO-ACTION as the released wave). That one named W50 as the active wave; W50
is merged (pull request #47, squash `67b3410d`, 2026-11-07) and W51 is merged (pull request
#48, squash `3ef8a5c8`, 2026-11-09). The rows that were re-measured say so; the rows that
were carried say that too, and the rows whose number was measured by a different method
carry the method with them, because a number without its method is a number nobody can
reproduce.

---

## 1. The identity, in one paragraph

MATRIX is a **deterministic neuro-symbolic core** in which knowledge is represented as
truth tables and their compilable forms (TT / CLAUSESET / BDD), and in which the learning
path is separated from the runtime: the runtime executes only compiled artefacts, without
LLM calls and without randomness. The repository holds **two serving applications that do
not meet**, a large body of implemented but unserved research subsystems, and a decade of
wave records.

**What it is not:** it is not a general LLM agent, it is not a single cognitive cycle, and it
is not a system in which every implemented class is served. The distance between "implemented"
and "served" is the single most important fact about this repository, and it is measured in
`docs-v2/architecture/CAPABILITY-REGISTRY.yml`.

**What changed in W51:** the two serving applications do not meet, but the parts of MATRIX
now interact through **one governed crossing** — the bridge, chain B → chain A — and the
cognitive cycle is a real runnable loop with all six memory roles, a reflection that
produces decisions, and a plugin architecture with a creator. **The distance did not close:
it moved.** The bridge, the canonical contracts, the world-state memory and the reflection
are **served**; the shared runtime, the plugin system, the meta-memory and the native
boundary are **not** — their only caller today is the wave's demonstration. The registry says
so on each of those rows, and §2 of this snapshot repeats it.

**What changed in W53:** the system's **procedural memory is no longer empty**. When the
cognitive runtime's model settles on exactly one rule, the system writes the procedure down
— with the standing it actually has, the episode it was learned in, and a note that says
what the agreement rests on — and a later cycle **consults that record and names it**, which
is the mandate's acceptance point 11 read literally. The reason the earlier wave refused to
write anything (the lamp world's input domain is exhausted once the model settles) is now
**proved in a test** rather than asserted: with V inputs the space holds 2^(2^V) candidates,
each distinct assignment eliminates half of them, and the model settles only after every
assignment the domain has. One epistemic rule changed — a supersession now requires a scoped
denial rather than a better-supported claim with lexically similar words — and the whole
29-case evaluation was re-run with it in place: **18 of 29, unchanged, the same eleven
failures**. The change is therefore not a score-motivated one, and the report names what it
does not fix: the denial's own scoping is still lexical, and the eight citation failures need
a match that does not require a shared rare term.

**What changed in W52:** the two serving applications **now talk in both directions** — chain
A's epistemic core offers what it has established and chain B's brain takes it into its own
memory, under the same signature, the same capability check, the same budget, the same
idempotency key and the same audit as the first direction. The canonical contracts are no
longer a vocabulary the runtime never speaks: the cognitive cycle produces the canonical form
of itself, and a test asserts that every contract the mandate names has a producer. The
system's profile of its own methods is durable, so a process that starts knows what its
methods cost before it has run any of them. **The epistemic evaluation still reads 18 of 29**
— the wave measured three more configurations of the relevance floor and **left the
threshold where it was**, because every configuration that made the answers better made the
abstentions worse.

---

## 2. The state

| | |
|---|---|
| the branch | `develop` — the working branch; the release pull request #63 (`develop` → `main`) was **merged** on 2026-11-20 (`188d756e`), and `main` is synchronised back into `develop` (`72c09e2f`). The CONCEPT-TO-ACTION branch `feature/concept-to-action` is merged into `develop` by `dfd24b44` (pull request #60); the W53 wave's own branch `feature/w53-distributed-cognition` is at `8cfcf854` and is merged into `develop` by `48bfe37a`; the EVID-BASE branch `feature/evid-base-evidence-to-action` is merged by `01d4cdc0`; `main` is at `26c549c4` — the **squash merge of pull request #61** (the CONCEPT-TO-ACTION release), merged on 2026-11-15 |
| the commit | `98fe35e5` — the head of `develop` at the time of this snapshot, observed at 2026-11-20 (`git rev-parse origin/develop`); the release head is the commit that carries this snapshot itself, and the pull request #63 names it. It carries the EPISTEMIC-INTEGRITY wave (the premise semantics, the sealed held-out set, the concept identity repair and the live store's migration, the learning isolation, the packaging fix, the CI lanes and the retained evidence) |
| the remote | `origin/develop` carries the synchronisation (`72c09e2f`) and this snapshot's own commit; `origin/main` is at **`188d756e`** — the **squash merge of pull request #63** (the EPISTEMIC-INTEGRITY release), merged on 2026-11-20; before it `26c549c4` — the **squash merge of pull request #61** (the CONCEPT-TO-ACTION release), merged on 2026-11-15; before it `724ea537` the EVID-BASE release record (pull request #59) and `32666218` the EVID-BASE release itself (pull request #56). `main` is contained in `develop`'s history, but the two trees **differ by 198 files** — the initiative's own work (see the header); the release pull request **#63** is open |
| the release version | **none.** The tags `v1.0.0` and `v16.0.0-mind` exist, but no release process is defined and the `v16.0.0-mind` tag predates three waves of work |
| the latest completed wave | **EPISTEMIC-INTEGRITY — from demonstrated components to verified capability.** It is complete on `develop` and **released into `main`** (pull request #63, squash `188d756e`, 2026-11-20). Its report is `docs-v2/engineering/EPISTEMIC-FINAL-REPORT.md`, its task graph and append-only evidence ledger `docs-v2/engineering/EPISTEMIC-EXECUTION-PLAN.md`, its retained evidence `docs-v2/engineering/evidence/` (**51** XML, **234** tests, **0** failures), and its decisions **OD-053 … OD-078**. Before it, **CONCEPT-TO-ACTION — from demonstrated components to a verifiable cognitive product.** It is complete on `develop` (pull request #60, squash `dfd24b44`) and **released into `main`** (pull request #61, squash `26c549c4`, 2026-11-15). Its report is `docs-v2/engineering/C2A-FINAL-REPORT.md`, its product evidence is `docs-v2/engineering/C2A-HTTP-DEMONSTRATION.json` (and the artefact `docs-v2/demos/concept-to-action/evidence-report.md`), its retrieval evidence is `docs-v2/engineering/C2A-CONCEPT-ENRICHMENT-EXPERIMENT.md` and `C2A-HELDOUT-EVALUATION.md` (with two sha256-pinned freezes in `C2A-CANDIDATE-FREEZE.md`), its learning evidence is `docs-v2/engineering/C2A-LEARNING-EVIDENCE.md`, and the window diagnosis it corrected is `docs-v2/engineering/C1-LEARNING-WINDOW-DIAGNOSIS.md`. Before it, **EVID-BASE — the evidence base and the first real bounded action.** It is complete on `develop` (pull request #53, squash `01d4cdc0`) and **released into `main`** (pull request #56, squash `32666218`, 2026-11-14). Its report is `docs-v2/engineering/EVID-BASE-FINAL-REPORT.md` (with the release addendum in §K), its retrieval experiment is `docs-v2/engineering/E2-RETRIEVAL-EXPERIMENT.md`, its learning evidence is `docs-v2/engineering/E1-LEARNING-EVIDENCE.md`, its action is `docs-v2/engineering/B3-EVIDENCE-TO-ACTION.md`, and its findings are in `docs-v2/engineering/E1-LEARNING-EVIDENCE.md` and its report is `docs-v2/engineering/EVID-BASE-FINAL-REPORT.md` (the fold-replacement decision it inherits is the **REP-SUBSTRATE** wave's ADR, `docs-v2/engineering/ADR-REP-SUBSTRATE-01-representation-substrate.md`, which stays OPEN). Before it, **W53 — Distributed Cognition** (pull request #50, squash `bcdd1a0a`), and before that W52 (pull request #49, squash `7709dc9e`) |
| the previous waves | **W53 — Distributed Cognition** (pull request #50, squash `bcdd1a0a`), **W52 — the canonical path** (pull request #49, squash `7709dc9e`) and **W51 — the unified cognitive runtime** (pull request #48). Before them, **W50** — the local evidence-grounded demonstrator: a bounded, licensed corpus in one domain (plate tectonics) — 2 seed documents and a 7-document pre-cached research pool, every file carrying its licence, its revision and its sha256 in a manifest the import verifies; the seed import produced **348 claims** and the bounded acquisition cycle **3 pool documents and 324 claims**; the four-turn demonstration ran on the packaged jar: Q1 answered from **153 claims of 2 sources**, Q2 from **25 claims of 4 sources**, Q3 a **CONTRADICTED** verdict that names the disputed premise and the contradicting claim with `promoted=false`, Q4 applied **exactly one supersession**; the teacher stays off (OD-049) with **0 teacher invocations** measured. Its report is `docs-v2/engineering/W50-STATUS.md`. Before it, W48 — the CI reconciliation and the native budget; W47 — the owner's four architecture decisions (`a6ded233`); W45.1 (`509d23ba`) — the crash consistency; W45.0 (`eae6003f`) — the cognitive kernel integrity; RECON-W44.0 (`ae6c017b`) — the bytecode archaeology |
| the active work | **the cognitive development programme's arm C — the governed learned competence — is BUILT, GATED and MEASURED** (`docs-v2/engineering/CRJ-01-ARM-C.md`): a journey's observed effect becomes a candidate, the candidate is checked on **held-out** questions it was never proposed from, and only a candidate that reproduces the reference on every one of them is **promoted** with a version and a provenance; after a **restart** the promoted procedure answers a **different** question on the served route (the packaged demonstration is **20/20**). The honest measurement: the **saving is 2.7 %** on a 17-claim workspace and the first selection rested on a **declared** cost — the mechanism is right, the benefit is not yet claimed, and the learned-claims half and the sealed-set comparison are not done. **The FULL suite was run and is green** — 1359 suites / 9808 tests / 0 failures — and it found a **pre-existing defect of the served path**: the journey could refuse a request with HTTP 503 `output_root_unavailable` because the **database was busy** (a wrong name for the cause). The defect is **not** arm C's — the control tree fails the same probe at the same rate — and it is **fixed and proven** (RED without the fix, GREEN with it; `R-EPIS-13`). **The INSTRUMENT the comparison needs now exists**: CRJ-01's two oracles are built as test-tree instruments — the **source-span oracle** (domain A) and the **lamp truth oracle** (domain B) — with **11 tests, 0 failures**, each carrying the control that makes it an instrument: the corrupted-gold control fails **loudly**, and the binding cross-check against the world has been **executed** (`instrument_crosscheck: {assignments: 4, agreements: 4}`) and voids the run on any disagreement (`docs-v2/engineering/CRJ-01-INSTRUMENTS.md`). **The corpus, the cases, the builder and the freeze are NOT built here**, and the reason is the protocol's own role rule: this agent implemented arm C, which is a solver, and §5.6 requires a case author who implements no arm and an independent reviewer. **A second defect of the served path is closed**: the report journey wrote its trace into an episode the store **never opened** — the steps were in the event table and the episode existed nowhere else, so a learned competence's provenance named an episode a reader could not find, and a workspace's journeys could not be enumerated from the store. The journey now **opens** its episode before it writes a step into it, and a later journey of the same identity **continues** it without rewriting its account (`ensureEpisode`). **Proven RED → GREEN** (2 tests / 2 failures before the fix, 2 / 0 after; both XMLs retained). **What the owner is asked for:** the merge of **PR #65** (the round-1 reliability milestone) and **PR #66** (arm C and the concurrency fix) — both are green, both are stacked, and the merge is the owner's; the **freeze's independent reviewer** for COG-01; and a decision on whether the research suite's **load-sensitive latency assertions** should keep asserting on a wall-clock bound. **The cognitive development programme's first milestone — CRJ-01 — is DONE and measured** (`docs-v2/engineering/CRJ-01-VALUE-CHECKPOINT.md`): the changed-payload idempotency case was **reproduced** (a reused key with a different question answered HTTP 200 with the earlier journey's report) and **fixed** under **OD-079** — the replay's applicability domain is now the request, and the same request is refused with **HTTP 409 `REFUSED_IDENTITY`** instead; the Article I instrument was narrowed to its true reach (**OD-080**, R-EPIS-10 closed, the accepted baseline **unchanged**); and the CRJ-01 local journey was demonstrated over the **real packaged application** (**16/16** checks, including a restart; `docs-v2/engineering/CRJ-01-JOURNEY-DEMONSTRATION.json`). The programme's **developmental** milestone is NOT done: the preregistration is a **draft** (`docs-v2/engineering/CRJ-01-PROTOCOL.md`, five owner decisions open), the reachability map is **PARTIAL** with five named break points (`docs-v2/engineering/CRJ-01-REACHABILITY.md`), and no transfer to a different task has been shown — the reuse that exists is the exact replay (arm B). **EPISTEMIC-INTEGRITY is complete and released** (pull request #63 → `main` as `188d756e`); the next work is the **seven remaining POSITIVE failures** of the sealed set, each measured per gold — four have their gold **retrieved but not cited** (the citation selection: ranks 8, 17, 26, 11) and three have their gold **not retrieved** (the retrieval) — plus the two open gaps the release names (**R-EPIS-10**, the Article I exemption's file granularity; **R-EPIS-11**, the wave's print-only probe family). Before it, **CONCEPT-TO-ACTION is complete and released** (pull request #60 → `develop` as `dfd24b44`; the release pull request #61 → `main` as `26c549c4`; the post-release synchronisation `1194f918`). What it delivered is measured and recorded: **one real route** (`POST /v1/workbench/workspaces/{id}/reports`) now serves the bounded action, verified over real HTTP against the packaged server; the writer's six residual risks are closed and the guard is killed on demand by a negative control; the retrieval's three frozen candidates were measured on the development cases **and** on an independent held-out set and **none is promoted**, with the eleven failures attributed per gold (only **two** are pure floor failures, five carry a gold that no floor can reach); and the learning subsystem's active window now carries both classes (10 positive / 10 negative) for the first time, with the production induction's fidelity measured at **0.50** — chance — against a synthetic control's 0.714. **What it did NOT deliver**, and what stays the owner's: the fold replacement (ADR §4.4, options A/B/C — **OPEN**, and now with a measured reason: the representation is not the observed bottleneck), the peer's durable payload contract (**OPEN**), the Exp324 threshold (**2 ms**, unchanged), and a promotion of any candidate (the recommendation is **NO**). The tasks its measurements created are in `docs-v2/governance/ROADMAP.md` §4.16 |
| the modules | **14** included in `settings.gradle`, 3 commented out, **3** outside Gradle (`matrix-web-ui`, `matrix-sdk-python`, `matrix-sdk-js` — each with its own toolchain, `settings.gradle` lines 23-34), 1 stray orphan file |
| the serving entry points | **2** applications — the Quarkus app on **9091** and the brain runtime's JDK server on **8765**. **Measured 2026-11-09:** chain A carries **205** HTTP method endpoints (the count of the `@Path` annotations that carry an HTTP method) and chain B's gateway registers **34** route contexts (the count of the distinct `createContext` paths — 33 of them literal and one, the second direction of the bridge, named by the constant `io.matrix.api.bridge.KnowledgeBridge.KNOWLEDGE_PATH`). The earlier figures in this snapshot — 210 and 28 — were measured by a different method and are not comparable; the method is written with each number here so that the next reader can reproduce it.  **Re-measured 2026-11-15:** chain A carries **226** HTTP method endpoints (the new route `POST /v1/workbench/workspaces/{id}/reports` adds one) and chain B's gateway registers **35** route contexts. **Re-measured 2026-11-14:** chain A carries **225** HTTP method endpoints (`git grep -oE "@(GET\|POST\|PUT\|DELETE\|PATCH)" -- matrix-core/src/main/java matrix-brain-runtime/src/main/java \| wc -l`) and chain B's gateway registers **35** route contexts (`grep -rhoE 'createContext\("[^"]+"\|createContext\([A-Za-z.]+_PATH' matrix-api-gateway/src/main/java \| sort -u \| wc -l`) — 33 literal, plus `KnowledgeBridge.KNOWLEDGE_PATH` and, since W53, `TaskBridge.TASK_PATH`|
| the CLI entry points | **13** `@Command` classes in chain A's two modules, measured 2026-11-09 by the count of the classes that carry the `@Command` annotation (`git grep -lE "@Command" -- matrix-core/src/main/java matrix-brain-runtime/src/main/java`). The previous snapshot said **12** and the method it named — the count of the annotations — gives **15** occurrences across the same 13 classes; the number here is the one the stated method reproduces, and the earlier figures of 79 and 12 were replaced rather than restated. |
| the databases | **one canonical** SQLite file (`matrix-core/data/knowledge/workbench.sqlite`, the knowledge schema **v21** — `KnowledgeStore.SCHEMA_VERSION = 21`; the file was migrated v15 → v21 by the wave's launcher, with the backup, the audit and the rollback rehearsed) plus chain B's own `data/mind/mind.sqlite`. The canonical file is **not tracked** (it is a local artefact) — the W50 demonstration had left **687 claims** in each of its two workspaces; the claims carry the workspace id, but the `workspace` table itself holds no row — which is why the status route's workspace list comes back empty (Q-W50-03) |

**What this snapshot re-verified, and what it carried.** Re-verified on 2026-11-14 (this
snapshot): the branch, the head and the remote (`git rev-parse HEAD` → `4257d6df` on
`develop`, `git rev-parse origin/develop` → the same commit, `git rev-parse origin/main` →
`32666218`), the EVID-BASE release (pull request #56, squash `32666218`) and the divergence
between the branches (`git rev-list --count origin/main..develop` → 448; `develop..main` →
0 — the two branches hold the same tree `7a703df9`).

**What the earlier snapshots re-verified, and what they carried.** Re-verified on
2026-11-13: the branch, the head and the remote (`git rev-parse HEAD` → `9f9948e7` on
`develop`, `git rev-parse origin/develop` → the same commit, `git rev-parse origin/main` →
`7709dc9e`), the wave's own merge into `develop` (`48bfe37a`) and its branch head
(`8cfcf854`), pull request #50 (open on that day; merged on 2026-11-14 as `bcdd1a0a`) and
the divergence between the branches
(`git rev-list --count origin/main..develop` → 433; the earlier figure of 436 was measured
against the LOCAL `main`, which lagged the released `main` by three commits — the released
branch is the reference). Re-verified on 2026-11-11: the merge of W52
into `main` (the squash `7709dc9e`). Re-verified on 2026-11-07: the merge of W49 into
`main` (the squash `79045c57`), the wave that was active then, and the schema version
(`matrix-core/src/main/java/io/matrix/knowledge/KnowledgeStore.java:92` → `SCHEMA_VERSION = 19`).
**Carried unchanged from the W46/W47 measurement**, because this wave did not re-measure them:
the module count, the serving entry points and their route counts, the CLI entry point count
and the release tags. A row that was not re-measured is not a new claim; it is the previous
measurement, and it is marked here rather than silently restated.

---

## 3. The test baseline — commit-specific, and it is not one number

**The full `matrix-core` suite is not run in this repository's practice.** It needs the
owner's approval (the OOM risk; `AGENTS.md` §Тестирование). The baseline is therefore a set
of **scopes**, each verified on a named commit.

| the scope | the command | the result | the commit | the environment |
|---|---|---|---|---|
| the cognitive kernel and the epistemic store | `./gradlew :matrix-core:test --tests "io.matrix.cognitive.*" --tests "io.matrix.knowledge.*" --rerun-tasks` | **128 classes, 910 tests, 0 failures, 0 skipped** | `a6ded233` | JVM, OpenJDK 25.0.4.1 |
| the W43 regression scope | `./gradlew :matrix-core:test --tests "io.matrix.memory.*" --tests "io.matrix.brain.*" --tests "io.matrix.api.*" --tests "io.matrix.rag.*" --rerun-tasks` | **98 classes, 1036 tests, 0 failures** | `a6ded233` | JVM |
| the brain runtime | `./gradlew :matrix-brain-runtime:test --rerun-tasks` | **119 classes, 914 tests, 0 failures, 2 skipped** | `a6ded233` | JVM |
| the API gateway | `./gradlew :matrix-api-gateway:test --rerun-tasks` | **43 classes, 240 tests, 0 failures** | `a6ded233` | JVM |
| the chain isolation gate | `bash scripts/w45-chain-isolation-check.sh` | **16 checks, 0 failed** | `a6ded233` | integration — a real chain B server |
| its negative control | `bash scripts/w45-chain-isolation-selftest.sh` | **6 checks, 0 failed** | `a6ded233` | integration — the isolation is broken on purpose |
| the process-kill journey | `bash scripts/w45-cognitive-recovery-journey.sh` | **37 checks, 0 failed** | `a6ded233` | integration — 3 real SIGKILLs |
| the quality gate | `bash scripts/quality-gate.sh` | **271 literal candidates at the baseline of 271** | `a6ded233` | gate |
| the security boundary | `bash scripts/security-boundary-check.sh` | **0 violations** | `a6ded233` | gate |
| the numerical-tolerance scope (the fixed flaky property test) | `./gradlew :matrix-core:test --tests "io.matrix.consciousness.CausalEmergencePropertyTest"` | **7 tests, 0 failures, 0 errors, 0 skipped** | `241af1e1` | JVM |
| the wall-clock compliance scope (the 21 adjudicated sites) | `./gradlew :matrix-core:test --tests "io.matrix.consciousness.Exp228ComplianceTest"` | **1 test, 0 failures, 0 errors, 0 skipped** | `4adbc664` | JVM |
| the disk-budget scope (the unit defect) | `./gradlew :matrix-brain-runtime:test --tests "io.matrix.brain.runtime.DiskBudgetTest"` | **10 tests, 0 failures, 0 errors, 0 skipped** | `af9ba688` | JVM |
| the GPU benchmark harness's own arithmetic | `./gradlew :matrix-core:test --tests "io.matrix.benchmark.GpuBenchmarkHarnessTest"` | **8 executions, 0 failures, 0 errors** | `75937934` | JVM |
| the W50 acquisition and premise scope | `./gradlew :matrix-core:test --tests "io.matrix.knowledge.acquisition.*" --tests "io.matrix.knowledge.PremiseAssessmentTest" --rerun-tasks` | **11 classes, 60 tests, 0 failures, 0 errors, 0 skipped** | `71b5b5c6` | JVM |
| the W51 wave's whole scope | `./gradlew :matrix-core:test --tests "io.matrix.contract.*" --tests "io.matrix.plugin.*" --tests "io.matrix.runtime.*" --tests "io.matrix.bridge.*" --tests "io.matrix.learning.env.*" --tests "io.matrix.nativekernel.*" --tests "io.matrix.w51.*" --tests "io.matrix.safety.*" --tests "io.matrix.knowledge.*" --tests "io.matrix.benchmark.w51.*" --tests "io.matrix.cognitive.*" --rerun --offline` | **1323 tests, 0 failures, 0 errors, 0 skipped** | `83fa911c` | JVM, OpenJDK 25 |
| the chain isolation gate, after the bridge | `bash scripts/w45-chain-isolation-check.sh` | **22 checks, 0 failed** | `83fa911c` | integration — a real chain B server |
| its negative control, after the bridge | `bash scripts/w45-chain-isolation-selftest.sh` | **12 checks, 0 failed** | `83fa911c` | integration — the isolation is broken on purpose |
| the cross-process bridge demonstration | `bash scripts/w51-bridge-demo.sh` | **10 checks, 0 failed** | `83fa911c` | integration — two processes, two databases |
| the end-to-end demonstration | `./gradlew :matrix-core:w51Demonstration` | **15 of 15 acceptance points**; the trace is `docs-v2/engineering/W51-DEMONSTRATION.json` | `83fa911c` | JVM |
| the W50 epistemic evaluation, before and after the W51 fixes | `python3 scripts/w51-epistemic-eval.py` | **12/29 → 18/29**, the baseline reproduced first on the same runner in a clean worktree at `a098f283` | `83fa911c` | evaluation — 29 cases, per-case report |
| the quality gate, after the wave | `bash scripts/quality-gate.sh` | **271 literal candidates at the baseline of 271** | `83fa911c` | gate |
| the EPISTEMIC-INTEGRITY wave's retained evidence | `flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test --tests "io.matrix.knowledge.*" --tests "io.matrix.auditor.*" --tests "io.matrix.cognitive.report.*" --tests "io.matrix.packaging.*" --offline` | **52 XML files, 237 tests, 0 failures, 0 errors, 0 skipped** — the copies are tracked in `docs-v2/engineering/evidence/`, because a Gradle run wipes the build tree | `7d39fe81` | JVM, OpenJDK 25.0.4.1 |
| the release lane's own scope, on the release head | the CI job "Build & Test (Java)" of `.github/workflows/ci.yml` | **9 789 tests, 1 failed, 165 skipped** — the one failure was the wave's own non-portable probe, now fixed | `7d39fe81` | the GitHub runner |

**The four W48 rows are quoted from the wave's own committed records**, not re-run here:
`docs-v2/engineering/W48-TEST-RECONCILIATION.md` §3 (the two test scopes),
`docs-v2/engineering/W48-CI-RECONCILIATION.md` §4.1 (the disk-budget scope) and
`docs-v2/engineering/W48-GPU-BENCHMARK.md` §9 (the harness's arithmetic).

**The W50 row is the wave's own scope**, counted from its retained JUnit XML under
`matrix-core/build/test-results/test/`: the eleven classes of `io.matrix.knowledge.acquisition.*`
plus `io.matrix.knowledge.PremiseAssessmentTest`. It is a **scope**, not the project's total
test population — the full suite is still not run here (R-W46-10).

**The W51 rows are the wave's own scope**, measured on 2026-11-09 and counted from the
retained JUnit XML: **1323 tests across eleven scopes**, 0 failures, 0 errors, 0 skipped. They
are a **scope** too, and a wide one — the contract, the bridge, the plugin, the runtime, the
learning environment, the native boundary, the safety matrix, the knowledge store, the
benchmark and the cognitive kernel. **It is still not the project's total population**, and
the full suite is still not run here.

**Three things this baseline does NOT say:**

- **The total test population is unknown.** The W44 archaeology measured 9816 executions with
  24 failures during the W44 run; that is a **floor**, not a total, and it is a year old.
- **The native-image classes are neither passing nor failing.** Eight classes (23 failures)
  depend on GraalVM, which is **absent on this host** — `matrix-core/build/native` <!-- no-link: a build artefact, not a repository file --> has never
  existed here. **They are never counted as passing.** The true W44 split is 8 native-image
  classes / 23 failures **plus 1 ordinary JVM failure**
  (`io.matrix.consciousness.Exp228ComplianceTest`). **In W48 they became a scope of their
  own** (`31d8f832`): the default JVM `test` task excludes them, and
  `./gradlew :matrix-core:nativeBinaryTest` **refuses to run** with an explicit message while
  the executable is absent. Their state is **BLOCKED**, and it is not a pass
  (`docs-v2/engineering/W48-TEST-RECONCILIATION.md` §5).
- **A stale count is not a baseline.** The per-scope registry is
  `docs-v2/engineering/TEST-CATALOGUE.yml`, and each row names its commit.

---

## 4. The known blockers

| the blocker | the kind | what it blocks | the owner |
|---|---|---|---|
| the **workspace reload** erases the evidence of an observation while keeping its case, its disposition and its adjudication history | an architecture decision (D3-c) | the meaning of "replace a workspace" | **the owner** |
| the **writer lock** does not resolve a symlinked path alias, so two writers can open the same file through different paths | a defect inside an approved contract (D4-a) | the guarantee the lock exists for | the coordinator (Level 2 — it touches the database-ownership boundary) |
| the **gateway serves a stub by default** (`MATRIX_MODE` defaults to `stub`), while the canonical declaration says chain B runs `TrueMindCycle` | an owner decision (F-01) | the truth of the serving declaration | **the owner** |
| chain A opens **three ports**, two of them (9093 multicast, 9094 TCP) undeclared in any serving record | an owner decision (F-02) | the truth of the serving declaration; the D2 "no new network exposure" claim | **the owner** |
| **no whole-database backup exists** — the SQLite backup API appears nowhere, and the runbook has no backup procedure | a missing implementation | the recoverability of a corruption or a bad migration | the coordinator (Level 1) |
| the **GPU is an OPTIONAL ACCELERATOR, and the speed-up is measured but the digit is bounded** — the ONNX CUDA registration fails because the library path is a JVM system property and a native `dlopen` consults `LD_LIBRARY_PATH`; the diagnostic that reports the active provider is a **hardcoded string** | a performance defect + a lying diagnostic | the truth of the GPU claim | **decided by the owner on 2026-11-06 — OD-047**: the GPU is an **optional** accelerator, not part of the default serving path; the measurement is kept, the silent CPU fallback stays visible in the registry, and no deployment is obliged to provide the CUDA libraries. The measurement: `docs-v2/engineering/W48-GPU-BENCHMARK.md` measures **12.06× / 11.68× / 11.47×** over three runs of MATRIX's own forward call (`75937934`), the end-to-end path at **12.69×**, and the point call **4× slower** on the GPU. The historical **12.6×** does **not** reproduce and stays **HISTORICAL**, bounded by that measurement. **The serving path does not change; the hardcoded diagnostic still stands.**
| the **quality gates are not blocking** — they live in a CI job named *«Quality gates (can fail)»*, and `STANDARDS-MATRIX.md` does not say so | a documentation and process defect | the trust a gate carries | the owner |
| ~~the packaged application could not open the SQLite store on this host~~ — **CLOSED 2026-11-15**, and the earlier diagnosis was **wrong**: the driver's resource lookup succeeds; the failure was its **write into the extraction target** (the application's scratch directory under a tmpfs whose per-user quota the project had already measured exhausting, R48/NR-034). The need for `-Dorg.sqlite.lib.path` is **not reproducible** in the JVM packaging — the packaged application opens its store with no property, from a directory outside the repository. The packaging now verifies that the driver carries the native library, the demonstration's workaround is deleted, and a mutation proof shows the guard failing when the library is removed | a packaging defect — **fixed and measured 2026-11-15** | nothing that is still open; the native-image path remains unverified because that build is red on this host (OOM) | closed (`docs-v2/engineering/EPISTEMIC-SQLITE-PACKAGING.md`) |
| the **Docker Hub publication has no credentials** — the repository holds **zero** secrets (`gh api repos/AlexanderNarbaev/agi/actions/secrets` → `total_count: 0`), so the publication step cannot authenticate and the aggregate `ci` job stays red on that step alone | an operational blocker | the container images | **the owner** (the values must be added as repository secrets — never pasted into a chat) |
| the **readiness probe is permanently 503** — the reactive PostgreSQL datasource gates it and **no production code reads it** | an operational blocker | the CI smoke test; every deployment manifest | the coordinator |
| the **static analysis produces no evidence** — the pinned SpotBugs plugin 6.1.7 resolves the engine 4.9.3, whose ASM 9.7.1 reads class-file major 68 and the code is major 69; the run yields **6 645** `Unsupported class file major version` lines and **no** findings | an inherited infrastructure limitation, **measured 2026-11-09** | nothing in the cognitive architecture — the gate is not a prerequisite of the deliverable | the owner (Q-W49-06) |
| the **secret scan is green on the released branches** — TruffleHog finishes with **0 verified secrets**, and the job **passed** on the release commit `bb46fc41` (CI run `43573097294`) and on `main`'s squash `bcdd1a0a` (CI run `43574788457`). **Measured 2026-11-13** on the wave's own push (CI run `43366273512`, the `develop` push at `9f9948e7`): the action scans the commits the push carried, and **exactly one** of its findings lay in that range — the line of `docs-v2/engineering/W53-STATUS.md` that quoted chain B's inbox URL verbatim (the driver prefix `jdbc:sqlite:` followed by the relative path `data/mind/tasks.sqlite`): a **relative local path with no credentials**, so a false positive. A scan of the whole history gives **44 findings — 30 JDBC and 14 Postgres — and 0 verified**; the exit code 183 is the action's own convention for "unverified findings remain", so the job is red on that code and not on a secret. **The finding itself is answered by the narrowest change**: the documentation lines that quoted the URL verbatim now name the driver prefix and the path separately, so the files carry the same fact without the literal — no scanner disabled, no wide exception. **What the scanner still reports over a long range, measured 2026-11-14**: the pull-request event scans the whole branch and finds **9 unverified results and 0 verified**, all of them attached to **historical** commits — the W51 infrastructure plugin's placeholder URLs, the placeholder URLs in the owner-protected `.veai/skills` files, and the wave's own historical commit — so the current tree is clean and the job is green on a push that carries only the new commits | the history of the literals, not a secret in the tree | the job's green state on a push, not the absence of literals in the old commits | the coordinator |
| the **ShellCheck job is red on the repository's other scripts** — **182** findings at the wave's head, where the base commit `a098f283` had **210**; the wave's own four scripts have **zero**. The job is red on `main` at `67b3410d`, `79045c57` and `7a0ed0cc` as well | an inherited infrastructure limitation, **measured 2026-11-09** | the job's green state, not the wave's scripts | the coordinator |
| the **workspace has no identity for a subject** beyond the words two sentences share — its concepts are scoped to the sentence they came from, and its scope and context are the sentence's own beginning | a missing capability | the eight citation failures (the answering claims rank immediately below the relevance floor, 10th of 161, because the question's rare terms carry the IDF weight and the claims do not contain them) and the two revision false positives (the denial's scoping is lexical) | the coordinator (Level 1 — a retriever that matches without a shared rare term, or a subject identity) |
| the **native image runs out of memory** — `native.yml:28-34` sets no memory limit, `-J-Xmx8g` with `--gc=epsilon` is a hard wall, and the build dies with `OutOfMemoryError: Java heap space` | an inherited infrastructure limitation, **measured 2026-11-09** | nothing in the cognitive architecture — the mandate says the native image is not a prerequisite | the owner (Q-W49-05) |

---

## 5. The pending owner decisions

The full register, with the competing explanations and the experiment that would resolve each,
is `docs-v2/governance/OPEN-QUESTIONS.md`. The owner questions that block the next work — the
three this wave raised are at the bottom:

| the ID | the question | what a decision unblocks |
|---|---|---|
| **Q-W46-01** | the destructive reload's consent parameter: `?replace=true` (as implemented) or the `replaceExisting: true` body field of the target contract? | the API shape of a destructive operation |
| **Q-W46-02** | a dry-run / preview mode for the destructive reload? | a new HTTP surface on a destructive path |
| **Q-W46-03** | what may a workspace replacement erase? | the erasure set of the workspace replacement |
| **Q-W46-13** | the retention policy for the append-only observation, adjudication and request-event histories | the unbounded growth of the audit trail |
| **Q-W50-01** | does the repository's own AGPL-3.0 licence interact with the share-alike of the CC BY-SA 4.0 Wikipedia extracts the corpus embeds? | whether the corpus may ship inside the repository as it does |
| **Q-W50-02** | the import stores the source text as the source excerpt, and the audit trace of an imported claim does not carry the byte offset of the quoted span | the auditability of every imported claim |
| **Q-W50-03** | the Workbench status route reports the database path and its size but leaves the workspace list and the counts empty | the observability of the demonstrator's own workspace |
| **Q-EPI-01** | confirm the **provisional** decision **OD-065** — the alias and resolution policy the identity migration writes with — and the two questions the earlier waves left open (the fold replacement, the peer's durable payload contract) | the identity migration's policy moves from provisional to recorded, and the fold's decision stops being a carried debt |

**The first three rows are CLOSED in the register** (OD-025, OD-026 and OD-027, answered on
2026-11-05) and are kept here for the record; only **the three W50 questions** are pending —
Q-W46-13 was closed on 2026-11-08 (OD-029, `docs-v2/governance/OPEN-QUESTIONS.md`).
The EVID-BASE wave's own open questions are the fold replacement (ADR §4.4, options A/B/C —
**OPEN**) and the peer's durable payload contract (**OPEN**), both recorded in
`docs-v2/engineering/EVID-BASE-FINAL-REPORT.md` §H.
The **CONCEPT-TO-ACTION** wave leaves the same two open and adds what its measurements say
about them, plus its own: the fold replacement now has a **measured reason** to stay open — on
the two-class test partition **no** representation beats the majority baseline, and a 1-NN over
the **unfolded 256-bit** feature reaches only 0.544 balanced accuracy (chance), while the same
fold reaches 0.714 on a controlled synthetic fixture, so the label is not recoverable from a
token hash and a better projection cannot fix it; and the recommendation on the wave's own
question — **promote a retrieval candidate? — is NO**, because no candidate recovers a failing
case on the development set or on the independent held-out set, and H3 loses one. The decisions
and the tasks are in `docs-v2/engineering/C2A-FINAL-REPORT.md` §H and §J, and in
`docs-v2/governance/ROADMAP.md` §4.16.

**One of the five this section used to list is now CLOSED and is no longer pending:**
**Q-W46-07** (the GPU path) was answered on 2026-11-06 — **OD-047**: the GPU is an **optional
accelerator**, not part of the default serving path. The register records it as `CLOSED`
(`docs-v2/governance/OPEN-QUESTIONS.md`). **It is not re-opened here.**

---

## 6. The next recommended work

The canonical roadmap is `docs-v2/governance/ROADMAP.md`. The tasks this section used to
list (TASK-W46-2…TASK-W46-6, TASK-W48-01…TASK-W48-03 and TASK-W49-01…TASK-W49-10) are
**done** and live in §4.1, §4.7 and §4.8 of the roadmap; the W51 tasks are in §4.10 and are
done as well, with one exception — TASK-W51-11, the closeout, was open at the moment this
snapshot was taken and is closed by the merge.

**The CONCEPT-TO-ACTION wave's tasks are in §4.16 of the roadmap**, and they are ordered by
what the measurements point at: the premise path must refuse a disputed premise (it refuses
**0 of 6**); a conflict in the evidence must be reported (it never is); the concept identity
layer must be repaired (**19 %** of the concept names end in a dangling function word — the
enrichment could not work because the thing to enrich was not a concept yet); the learning
evidence needs a positive control (the measured 0.50 is chance); the window's tail must be
measured as an estimator on an organically mixed log; the packaged store must open without a
manual property; and the Docker Hub publication needs the owner's credentials.

**What is open now, in order:**

1. **The W53 tasks** — §4.13 of the roadmap: the baseline is reproduced and recorded; the
   distributed boundary is decided in an ADR; the typed remote task contracts exist and are
   tested; the bridge carries the two new rights; chain A has one coordinator; chain B
   executes its real path; the execution is durable on both sides; chain A adjudicates the
   report; the isolation gate grows; the cross-process demonstration runs; the method
   reliability is estimated honestly; the distributed path is measured; the research is
   persisted; and the closeout writes the report, the audit and the architecture map.
   Three things were added in the wave's last rounds, each with its own evidence: the
   delegation reaches the **reflection** when the task runs inside a cycle (a report that
   did not settle the proposition becomes an uncertainty, and the reflection turns it into
   a `LEAVE_UNRESOLVED` decision that names the request); both processes **report
   themselves** — chain A answers its health endpoint, chain B names the capability it can
   really execute — and the demonstration checks that the capability chain A used is one
   the peer reports as active; and the peer's duplicate guard was found to stand in
   **three** places, so the test now counts the brain's calls instead of trusting the
   answer alone. And the **last crash boundary** was found to be a hole rather than a
   covered case: a dispatch whose answer was on record but whose verdict was not — the
   process died between receiving the answer and judging it — was in no queue at all, so
   the task stayed unjudged for ever. The rule is now stated in the store where the truth
   is: a dispatch is unfinished while it owes chain A a decision (CD-036), and the
   cancelled and the timed-out dispatch, whose answer is deliberately kept unjudged, are
   finished and are left alone.
   **The previous wave's tasks** — §4.12 of the roadmap, all closed with their evidence:
   the procedure
   of the settled model is written down with the standing it actually has; a later cycle
   consults it and names it; the reason an independent validation is unreachable in the
   micro-world is proved in a test instead of asserted; the canonical `Procedure` and `Skill`
   are filled by the runtime's own record; and one epistemic rule was changed and measured —
   the score did not move, and the report says so.
2. **The release pull request** — the lead opens `develop` → `main` at the end of the wave,
   and **an agent does not merge it**. The pull request is a prepared path, not a release;
   the merge is the owner's decision (Q-W49-01). **The EPISTEMIC-INTEGRITY release is pull request
   #63, and it is MERGED** (the squash `188d756e`, 2026-11-20, with the ruleset's logged admin
   bypass — **OD-074** — scoped to the impossible self-review requirement alone); its precondition,
   a **green full release lane on the head being merged**, was met on `5c7e2e2c`. The lane caught
   **four** defects of this wave that the fast lane could not see — **R-EPIS-09** stays open as the
   lane's gap. **Every release pull request since W50 has
   been merged: W50's (#47) and W51's (#48) on 2026-11-07 and 2026-11-09, W52's (#49) on
   2026-11-09, W53's (#50) on 2026-11-14, and EVID-BASE's (#56) on 2026-11-14. The
   repository's ruleset requires an approval that a sole author cannot self-supply, so each
   merge used the ruleset's own administrative bypass — recorded in the wave report and in
   `docs-v2/engineering/EVID-BASE-FINAL-REPORT.md` §F.**
3. **The three W50 owner questions** — the licence interaction of the corpus inside the
   repository (Q-W50-01), the audit trace of an imported claim (Q-W50-02) and the status
   route's empty workspace list and counts (Q-W50-03). They are in §5 above and in the
   register, and W51 did not close them.
4. **The honest limits of the demonstrator** — the false-premise judgement is lexical, not
   semantic (R-W50-01); the extractor's corpus vocabulary is a heuristic verb list
   (R-W50-02); the answering path proves zero LLM calls by instrumentation, and the
   assertion check is itself a deterministic lexical pass (R-W50-03). They are named, not
   hidden.
5. **The W49 owner questions, unchanged** — the release path's missing credentials
   (R-W48-04), the static-analysis policy for the 1 414 findings of the Java 25-capable
   engine (Q-W49-06), and the native build's larger runner and one missing dependency
   (Q-W49-05).
6. **The wave's own report** — `docs-v2/engineering/W51-STATUS.md` for the wave that is
   merged, and the W52 report when the wave closes. This snapshot names them and does not
   restate them.

---

## 7. The authoritative records — where each subject is written down

**One subject, one place.** If a subject is listed here and the document it names disagrees
with this snapshot, the document wins for its subject and this snapshot is wrong — and the
validator says so.

| the subject | the document |
|---|---|
| the principles | `CONSTITUTION.md` (normative) — and the unfilled template duplicate `constitution.md` <!-- no-link: removed on 2026-11-05 by the owner's decision; the file is gone --> was **removed on 2026-11-05** — the normative file is untouched |
| the session rules | `AGENTS.md` (normative, protected) |
| the decisions | `docs-v2/governance/DECISION-REGISTER.md` |
| the capabilities | `docs-v2/architecture/CAPABILITY-REGISTRY.yml` |
| the serving architecture | `docs-v2/architecture/RUNTIME-TOPOLOGY.md` — the canonical runtime topology (W46.2); the evidence is `docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md` |
| the persistence | `docs-v2/architecture/DATA-AND-MEMORY-ARCHITECTURE.md` — the canonical persistence map (W46.2); the evidence is the same truth map §4 |
| the module graph | `docs-v2/engineering/W46-REPOSITORY-TOPOLOGY.md` |
| the roadmap | `docs-v2/governance/ROADMAP.md` |
| the risks | `docs-v2/governance/RISK-REGISTER.md` |
| the open questions | `docs-v2/governance/OPEN-QUESTIONS.md` |
| the agent protocol | `docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` |
| the document statuses | `docs-v2/governance/DOCUMENT-STATUS-POLICY.md` |
| the machine | `docs-v2/operations/HARDWARE-PROFILE.md` (stale — the fresh profile is `docs-v2/engineering/W46-HARDWARE-AND-RESOURCE-REPORT.md`; the regeneration is TASK-W46-5) and `docs-v2/operations/RESOURCE-BUDGETS.md` |
| the procedures | `docs-v2/operations/RUNBOOK.md` and `docs-v2/operations/RECOVERY-RUNBOOK.md` (the backup and the restore — W46.2) |
| the test scopes | `docs-v2/engineering/TEST-CATALOGUE.yml` |
| the waves | `docs-v2/waves/INDEX.md` — the chronological work log (W46.2) — and `docs-v2/engineering/W4*.md`, the record of what was done, **immutable** |
| the W50 demonstrator | `docs-v2/engineering/W50-STATUS.md` (the wave's own report) and `docs-v2/engineering/W50-LOCAL-MODEL.md` (the measured local-model evaluation); the demonstration itself is `docs-v2/demos/local-learning-loop/README.md`, its four prompts are `docs-v2/demos/local-learning-loop/OWNER-SEQUENCE.md`, and the expected answers are `docs-v2/demos/local-learning-loop/ANSWER-KEY.md` — deliberately kept outside the application |
| the W51 wave | `docs-v2/engineering/W51-STATUS.md` (the wave's own report) · `docs-v2/engineering/W51-AUDIT.md` (the audit and the classification of nineteen subsystems) · `docs-v2/engineering/W51-ARCHITECTURE-MAP.md` (the actual runtime interactions, with the file:line references) · `docs-v2/engineering/ADR-W51-01-cognitive-runtime-and-bridge.md` (the inter-chain model and the preserved boundaries) · `docs-v2/engineering/W51-PERFORMANCE.md` (the measured hot paths) · `docs-v2/engineering/W51-EPISTEMIC-EVALUATION.md` and `docs-v2/engineering/W51-JAVA-C-BOUNDARY.md` (the two measurements the mandate names) · `docs-v2/engineering/W51-DEMONSTRATION.json` (the machine-readable trace) |
| the W53 distributed-cognition wave | `docs-v2/engineering/W53-STATUS.md` (the wave's report) · `docs-v2/engineering/W53-AUDIT.md` · `docs-v2/engineering/W53-ARCHITECTURE-MAP.md` · `docs-v2/engineering/ADR-W53-01-cognitive-task-bridge.md` · `docs-v2/designs/DESIGN-84-distributed-cognitive-bridge.md` (the design the research forced) · `docs-v2/engineering/W53-DISTRIBUTED-DEMONSTRATION.json` (the two-process demonstration) · `docs-v2/engineering/W53-PERFORMANCE.md` and `W53-PERFORMANCE.json` (the measured path) · `docs-v2/engineering/W53-EPISTEMIC-COMPARISON.md` (the 29 cases) · the decisions **CD-032, CD-033, CD-034** |
| the EPISTEMIC-INTEGRITY initiative | `docs-v2/engineering/EPISTEMIC-FINAL-REPORT.md` (the report of the initiative, sections A–J) · `docs-v2/engineering/EPISTEMIC-EXECUTION-PLAN.md` (the task graph and the append-only evidence ledger) · `docs-v2/engineering/EPISTEMIC-CLOSED-LOOP-DESIGN.md` (P2) · `docs-v2/engineering/EPISTEMIC-CONCEPT-IDENTITY.md` (P1: the measured root cause of the concept-identity defect) · `docs-v2/engineering/EPISTEMIC-HELDOUT-SET.md` (P0.3: the sealed set, freeze sha256 `2f4347da…`) · `docs-v2/engineering/EPISTEMIC-LEARNING-ISOLATION.md` (P0.4) · `docs-v2/engineering/EPISTEMIC-SQLITE-PACKAGING.md` (P0.5) · `docs-v2/engineering/CI-SIMPLIFICATION-RFC.md` and `docs-v2/engineering/BRANCH-INVENTORY.md` (Gate 1) · the decisions **OD-053 … OD-078** · the retained evidence `docs-v2/engineering/evidence/` (**52** JUnit XML, **237** tests, **0** failures) and its rule |
| the W53 wave | `docs-v2/engineering/W53-PROCEDURE-MEMORY-STATUS.md` (the wave's own report, with the five categories) · `docs-v2/engineering/W53-EPISTEMIC-EVALUATION.json` (the epistemic evaluation, machine-readable, with the supersession rule in place: 18 of 29, the same eleven failures) · the decisions **CD-030 and CD-031** in `docs-v2/governance/DECISION-REGISTER.md` · the proof of the exhaustion: `matrix-core/src/test/java/io/matrix/cognitive/microworld/DomainExhaustionTest.java` |
| the W52 wave | `docs-v2/engineering/W52-STATUS.md` (the wave's own report, with the five categories) · `docs-v2/engineering/W52-EPISTEMIC-EVALUATION.json` (the epistemic evaluation, machine-readable, and the three measured configurations of the relevance floor in §5.4 of the report) · the decisions **CD-026 to CD-029** in `docs-v2/governance/DECISION-REGISTER.md` · the cross-process demonstration of both directions: `scripts/w52-bridge-both-directions-demo.sh` |
| the canonical cognitive contracts | `docs-v2/engineering/ADR-W51-01-cognitive-runtime-and-bridge.md` (the model) and the package `io.matrix.contract` (the records themselves); the compatibility and migration strategy is §5 of `docs-v2/engineering/W51-STATUS.md`; **the producers and the boundary between the runtime's own records and the canonical ones are §5.2 of `docs-v2/engineering/W52-STATUS.md`**, and the acceptance is `CanonicalContractCoverageTest` |
| the inter-chain contract | `docs-v2/engineering/ADR-W51-01-cognitive-runtime-and-bridge.md` and the decisions CD-020 to CD-025 in `docs-v2/governance/DECISION-REGISTER.md`; the evidence that the crossing is the only one is the gate `scripts/w45-chain-isolation-check.sh` and its negative control `scripts/w45-chain-isolation-selftest.sh` |
| the research and the negative results | `docs-v2/research/RESEARCH-INDEX.md` and `docs-v2/research/NEGATIVE-RESULTS-INDEX.md` (W46.2) |
| the documentation navigation | `README.md` and `docs-v2/INDEX.md` — both reconciled in W46.2 |
| the agent's skills | `docs-v2/governance/SKILL-INVENTORY.md` (what exists) and `docs-v2/governance/SKILLS.md` (what is recommended) |
| the end of a session | `docs-v2/governance/SESSION-HANDOFF-TEMPLATE.md` |
| the agent's amendment | `docs-v2/governance/AGENTS-AMENDMENT-PROPOSAL.md` — **the proposal; `AGENTS.md` itself is untouched** |

---

## 8. The mission's thirteen questions, and the document that answers each

**This is the acceptance test of the governance structure: a new agent must be able to answer
all thirteen from these documents alone.**

| # | the question | the document that answers it |
|---|---|---|
| 1 | What is MATRIX today? | §1 and §2 of this document; the identity in `README.md` |
| 2 | What is actually implemented? | `docs-v2/architecture/CAPABILITY-REGISTRY.yml` — the `implementation` axis |
| 3 | What is reachable through a real serving path? | the same registry — the `serving_path` and `serving_chain` fields; the topology in `docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md` |
| 4 | What is tested, measured and independently verified? | the same registry — the `verification` axis and the `test_evidence`; the denominators in `docs-v2/engineering/TEST-CATALOGUE.yml` |
| 5 | What is experimental, inactive, obsolete, rejected or merely proposed? | the same registry — the `operational` axis; the rejected directions in `docs-v2/governance/ROADMAP.md` §8 |
| 6 | Which architectural decisions are authoritative? | `docs-v2/governance/DECISION-REGISTER.md` |
| 7 | Which research findings and failed hypotheses must be preserved? | `docs-v2/research/HYPOTHESES.md` and `docs-v2/research/HYPOTHESES-NEW.md`; the negative-results index is TASK-W46-7 |
| 8 | What are the current goals, blockers and next engineering tasks? | §6 of this document; `docs-v2/governance/ROADMAP.md`; the blockers in §4 |
| 9 | What may an autonomous agent change without asking the owner? | `docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` §3 — the autonomy levels |
| 10 | What requires explicit owner approval? | the same document §3.2 — the Level 3 list |
| 11 | What resources are actually available on the owner's machine? | `docs-v2/operations/RESOURCE-BUDGETS.md`; the evidence in `docs-v2/engineering/W46-HARDWARE-AND-RESOURCE-REPORT.md` |
| 12 | How should an agent resume work after context loss? | `docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` §1 — the twelve steps; and the bootstrap block in `docs-v2/engineering/W46.0-FINAL-REPORT.md` §H |
| 13 | How should every session leave enough evidence for the next one? | the same document §6 — the handoff template |

**The five files an ordinary session reads:** this document · `governance/ROADMAP.md` ·
`governance/DECISION-REGISTER.md` · `governance/OPEN-QUESTIONS.md` · the subsystem's own
source and tests. **The history is not read unless the task changes an architectural
assumption.**

---

## 9. The owner's working tree — the set has changed, and the change is a fact

**This section was rewritten on 2026-11-09, against the observed tree.** The W47 snapshot
named **eleven** owner entries that were *uncommitted* at that time. **At the commit this
snapshot names, eight of them are tracked in git** — they were committed by the waves that
followed, and they are still the owner's:

`.gitignore` · `.opencode/todo.md` · `.opencode/w37-plan.md` · `AGENTS.md` ·
`opencode.json` · `.codex/` · `.opencode/opencode-swarm.json` · `.veai/` (30 tracked files)

The two that are still outside git are the two `.gitignore` excludes:
`/.opencode/goals/` (`.gitignore:116`) and `/.opencode/tmp/` (`.gitignore:117`). The deletion
of `opencode.json.bak` is **gone** — the file is no longer there to delete, and it is not in
the index (`git ls-files opencode.json.bak` → empty). The local journal
`wal/SESSION_WAL.md` is **tracked**, as it has been since the early waves; only
`wal/state.yaml` is excluded and does not exist <!-- no-link: the file is excluded by .gitignore and does not exist -->.

**The working tree carries the wave's own working files and nothing of the owner's.** At
2026-11-09 `git ls-files -m -o --exclude-standard` returned **one** entry:
`.dsh-project-memory/admission-shadow.jsonl`, which is **neither the wave's nor the owner's** —
it is the harness's journal, and the harness writes it on every call. **The set changes as
the wave runs**; what does not change is that **no owner entry is in it**.

**A session that finds a different set of owner entries is not a failure — it is a fact that
the owner worked in between.** Record the set; do not "restore" it.
