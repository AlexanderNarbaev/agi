# CRJ-01 — Cognitive Reference Journey: preregistration protocol

**Status:** **PROPOSED / NOT FROZEN / NOT EVIDENCE.** This document is a *draft preregistration*.
It is not a contract, not a result, and not a claim about any capability. It becomes the
CRJ-01 contract **only after** (a) the owner approves it and (b) an **independent reviewer
who is not the author of the test cases freezes the sealed test set** and records its hashes
(`CRJ-01-ACCEPTANCE.md:30`, `CRJ-01-EXECUTION-PLAN.md:41`, `CRJ-01-EXECUTION-PLAN.md:44`).

**Date:** 2026-11-20
**Baseline SHA:** `b7bd1be4` (branch `develop`)
**Drafted by:** the science specialist, sub-task `CRJ.0.6` of `docs-v2/engineering/CRJ-01-EXECUTION-PLAN.md:34`
**Contract:** `docs-v2/engineering/CRJ-01-ACCEPTANCE.md`
**Program:** `docs-v2/governance/COGNITIVE-DEVELOPMENT-PROGRAM.md`
**Commission:** `docs-v2/engineering/AGENT-COMMISSION-COGNITIVE-PROGRAM.md`
**Reconciliation:** `docs-v2/engineering/COGNITIVE-MISSION-RECONCILIATION.md`

**Language:** English throughout.

---

## 0. What this document is, and what it is not

It is a **preregistration**: hypotheses, domains, arms, oracle, data split, freeze procedure,
metrics with denominators, negative controls, stop/pivot rules and a resource budget, written
**before** any CRJ-01 measurement. It creates no capability registry, no roadmap, no competing
document set. The canonical registers remain `docs-v2/architecture/CAPABILITY-REGISTRY.yml`,
`ROADMAP.md`, `PROJECT-STATE.md`, `DECISION-REGISTER.md` and `RISK-REGISTER.md`
(`COGNITIVE-DEVELOPMENT-PROGRAM.md:4`).

Three rules govern every number below.

1. **Every quantitative claim about the existing system is traceable to a file that was read.**
   The citation is `path:line` for source, or the artefact path for a retained measurement.
2. **A number that is a threshold is either justified or marked `PROPOSED` / `NOT_INSTRUMENTED`.**
   A threshold is never chosen after seeing results.
3. **An unmeasured quantity is written `NOT_INSTRUMENTED`, never guessed**
   (`COGNITIVE-DEVELOPMENT-PROGRAM.md:85`; `CRJ-01-ACCEPTANCE.md:65`).

**A note on line references.** Every `path:line` citation is to the **baseline commit
`b7bd1be4`**, the commit this protocol declares. Concurrent work in the same tree (the
`CRJ.0.3` idempotency reproduction) modifies `ArtifactReportResource`, `ReportJourney`,
`ReportJourneyMethod` and `ReportJourneyTrace`, so **their line numbers move as that work
lands**. Re-derive any line reference that matters with
`git show b7bd1be4:<path> | sed -n '<line>p'`. A class-level or method-level citation (the
default in this document) is unaffected by that drift.

The preregistration is *falsifiable by construction*: §8 states in advance what result would
falsify the hypothesis, and a failed run is retained as a result (`CRJ-01-ACCEPTANCE.md:13`).

---

## 1. The scientific question and the null hypothesis

### 1.1 The question

> Can MATRIX's existing, served mechanisms, given unfamiliar but independently checkable
> information, (a) distinguish evidence from hypotheses, (b) maintain durable entity/claim/
> procedure identity, (c) choose a valid method, (d) carry out one authorized bounded action,
> (e) independently observe the effect, and (f) use a **learned, versioned, validated** rule or
> procedure to improve a **different**, held-out task after a restart?

This is the question of `CRJ-01-ACCEPTANCE.md:9`. It is **not** a claim of intelligence or of
generalisation (`CRJ-01-ACCEPTANCE.md:13`).

### 1.2 The null hypothesis

**H0.** *Any observed improvement of arm C over arms A and B is explained by one or more of:
exact request replay, preexisting rules, lexical overlap, selection bias, or unaccounted
computation.*

This is the null of `CRJ-01-ACCEPTANCE.md:11`, restated here as the thing the experiment must
be able to survive.

### 1.3 The alternative explanations, and the exclusion test for each

Each row names the mechanism, the concrete threat in *this* repository, and the **exclusion
test** that must be executed and recorded. A threat that is not excluded is a result.

| # | Alternative explanation | Concrete threat in this repository | Exclusion test (pre-registered) | If the test fails |
|---|---|---|---|---|
| **A1** | **Exact replay** | The report route's action identity is `workspace + (idempotencyKey ?: question)` (`matrix-core/src/main/java/io/matrix/actions/ArtifactActionService.java:198-205`); a reused key under a changed question yields the *same* identity. The program names this as an open, source-based risk, not a proven incident (`docs-v2/governance/COGNITIVE-DEVELOPMENT-PROGRAM.md:60`; `docs-v2/engineering/COGNITIVE-MISSION-RECONCILIATION.md:56`). | Arm **B** exists precisely to expose replay. Separately: every sealed case uses a **fresh** idempotency key, and the case set is checked so that no two cases share a normalised 4-token question sequence (§5.4). One dedicated negative-control case reuses a key under a changed question and must be **refused as a conflict**, never answered from a stale result. | The measured "gain" is replay. Verdict for the transfer claim: **REJECTED**. |
| **A2** | **Preexisting rules** | A promoted `Procedure` can answer without re-deriving (`matrix-core/src/main/java/io/matrix/cognitive/MicroWorldSettledModelMethod.java:86-121`); the store retires a procedure below `MINIMUM_AGREEMENT = 0.95` (`.../cognitive/algorithms/ProcedureStore.java:51`). | Arm C may read **only** procedures that (i) were promoted through `ProcedureStore.promote` (`.../ProcedureStore.java:72-77`), (ii) passed `ProcedureValidator` on **at least `MINIMUM_VALIDATION_CASES = 5`** held-out cases with agreement on **every** one (`.../ProcedureValidator.java:35`, `:53-56`), and (iii) carry a version and provenance. **The validation cases come from the dev split only**; the sealed set may never validate a promotion. Arm A may read none of them. | The gain is a preexisting rule, not learned competence. Verdict: **REJECTED** for transfer. |
| **A3** | **Lexical overlap** | The retrieval path is lexical/substrate based; the measured `recall@1` of the baseline path on the C2A set is `0.5350877192982457` (`docs-v2/engineering/C2A-HELDOUT-EVALUATION.json`, `/metrics/BASELINE-PATH/recallAt1`) — i.e. word overlap alone is already a strong predictor of success. | No question in the CRJ-01 sealed set shares a normalised token sequence of length **≥ 4** with any question, assertion or disputed premise of: the 29-case dev set, the C2A 38-case set, or the EPISTEMIC 16-case set. This reuses the existing, proven convention (`docs-v2/engineering/EPISTEMIC-HELDOUT-SET.json`, `/manifest/verifications`; `docs-v2/engineering/EPISTEMIC-HELDOUT-SET.md`). The generator asserts it and the reviewer re-derives it. | The gain is retrieval by overlap. Verdict: **REJECTED** for transfer. |
| **A4** | **Selection bias** | Every existing evaluation set in the repository has been read during development (§5.2). | The sealed set is authored by a person who does **not** implement any arm, and frozen by a **different** person again, before any arm is run. All three arms receive the **identical** case list in the **identical** order, with identical inputs and identical permissions (§3.4). The freeze record names both roles. | The comparison is not credible. Verdict: **INCONCLUSIVE**, not "no effect". |
| **A5** | **Unaccounted computation** | The served report journey runs with `ComputeBudget.unbounded()` at both the plan and the execution site (`matrix-core/src/main/java/io/matrix/knowledge/ArtifactReportResource.java:320` and `:330`), so the shipped path itself has **no** compute budget. | Per case and per arm, record: wall time, kernel-selected method id, number of world probes (domain B), number of retrieved candidates, number of report writes, and the ledger's measured cost where the ledger exposes it. Arms are compared **at equal budgets** (§3.4), and the per-arm cost table is published next to the per-arm accuracy table (`CRJ-01-ACCEPTANCE.md:65`). | The gain is bought with unbounded work. Verdict: **REJECTED** unless the owner authorises the trade-off in writing. |

### 1.4 Pre-registered predictions (falsifiers, stated in advance)

| id | Prediction | Source of the prediction | If it does not hold |
|---|---|---|---|
| **P1** | In the micro-world **as it exists**, arm C's *correctness* will be **equal** to arm A's on every case, because the reference method "is always correct within the declared space — it is the definition of the answer" (`matrix-core/src/main/java/io/matrix/cognitive/MicroWorldReferenceMethod.java:20-21`). Any measured correctness difference would indicate a scoring defect, not a transfer. | source reading | Investigate the scoring harness before believing the result. |
| **P2** | Arm C's advantage in the micro-world is therefore **cost**, not correctness: fewer probes / less wall time per correct answer, via the settled-model fast path (`.../MicroWorldSettledModelMethod.java:110-121`, declared `O(1)` against the reference's `O(2^v * m)` at `.../MicroWorldReferenceMethod.java:59`). | source reading | The fast path is not being selected; check the `MODEL_SETTLED` precondition and the registry. |
| **P3** | The paired exact McNemar test on **n = 18** paired cases per domain cannot reach `p ≤ 0.05` for a net difference of 2; it requires **6 of 6** discordant pairs in one direction (two-sided exact `p = 2·(1/2)^6 = 0.03125`), and at 5 of 6 it is `p = 2·(6+1)/64 = 0.21875`. | arithmetic on the exact binomial | — (this is why §6.4 fixes the primary inference as a paired-difference interval, and §10 decision **D4** asks the owner to accept that a pilot verdict will usually be INCONCLUSIVE). |

---

## 2. The two domains

### 2.1 Domain A — evidence-rich technical documents

#### 2.1.1 What the repository's corpora actually are today

| corpus | path | content | recorded digest / size |
|---|---|---|---|
| Seed corpus | `matrix-core/src/main/resources/corpus/plate-tectonics/` | 2 documents: USGS *This Dynamic Earth* and Wikipedia *Plate tectonics* rev. 1381934246 | `corpus-manifest.json`: `usgs-this-dynamic-earth.md` sha256 `a82d4ca7143a018710193839fcbeb982998c3ac73db5edd621bf29ecb2467cdc`, 17 144 bytes; `wikipedia-plate-tectonics.md` sha256 `db794272ec30fb62ce4d9076e43db0fadc2b1037fc9d437a51a690da8fc1c853`, 58 972 bytes |
| Research pool | `matrix-core/src/main/resources/research-pool/plate-tectonics/` | 7 versioned Wikipedia snapshots (continental drift, mantle convection, mid-ocean ridge, paleomagnetism, seafloor spreading, slab pull, subduction) | `corpus-manifest.json`, manifest digest `fa7ba16e9baddea9d58109100c27d19a2404ee34391514a1ff4e2d785c0413b5` (`docs-v2/engineering/C2A-HELDOUT-SET.json`, `/manifest/corpusManifestSha256`) |
| Foundation corpus | `matrix-core/src/main/resources/corpus/corpus-manifest.json` | 3 documents: `photosynthesis-notes.md`, `water-boiling.txt`, `photosynthesis-page.html` — all `AGPL-3.0`, "written for the MATRIX corpus foundation, 2026-11-02" | sha256 `2c712e2b9f8cc8ca6cf825fde8185ae61033f16437369f7fdf335eebba56132b`, `15ef0a09935b20566df5afce4c0a6f1f4c2c03447156884d43fa05f4f6348fa7`, `a04c0484562451d736b1344f4738c2a159e2387689bd493b1a73dc0affb3e0ae` |
| Knowledge packs | `matrix-core/src/main/resources/knowledge/photosynthesis.pack.json` | the bundled photosynthesis package: 4 sources, 8 concepts, 17 claims, 13 representations, 5 prerequisites, 1 revision | 44 949 bytes (counts read from the file itself) |
| | `matrix-core/src/main/resources/knowledge/logic.pack.json` | the second pack (Boolean logic / propositional reasoning): 39 417 bytes | |
| | `photosynthesis.assessment.json`, `photosynthesis.adversarial.json` | learning/teach-back instruments: the assessment pairs a training item with a held-out variant; the adversarial set has 13 items over the phases `pre`(3) / `post`(4) / `delayed`(3) / `transfer`(3), 10 of them flagged `heldOut` | |

Both corpora are imported through the **production** acquisition path —
`io.matrix.knowledge.acquisition.AcquisitionCycle.SEED_CORPUS` →
`ClasspathCorpus.extract` → `KnowledgeWorkbench.importDocument` — and the cycle is bounded to
`MAXIMUM_DOCUMENTS = 3` documents per question
(`matrix-core/src/main/java/io/matrix/knowledge/acquisition/AcquisitionCycle.java:36-42`).

The knowledge packs are **not** corpora and **not** held-out sets. They are curated packages
with recorded provenance, and the assessment/adversarial files are *learning* instruments with
their own training items. Re-labelling either as a CRJ-01 held-out set would be a category
error.

#### 2.1.2 The evaluation sets that exist, and their honest status

| set | path | size | digest | status for CRJ-01 |
|---|---|---|---|---|
| Development set | `matrix-core/src/main/resources/epistemic-eval/cases.json` | 29 cases, 25 texts compared | sha256 `214e0b9a7e59a227e2cf3c688623b05d8e039a53de9c5cffb8b2d82b4b575a1f` | **developer-inspected.** Reusable as a *regression* set only. |
| C2A held-out set | `docs-v2/engineering/C2A-HELDOUT-SET.json` | 38 cases (POSITIVE 19, NEGATIVE 6, UNSUPPORTED 6, AMBIGUOUS 4, CONTRADICTORY 3) | sha256 `bbb3d2ad630ec610df3dbaae3ec10978d6cbbe740e13604dfd827b56b6872d77` | **developer-inspected.** REGRESSION set only. |
| EPISTEMIC held-out set | `docs-v2/engineering/EPISTEMIC-HELDOUT-SET.json` | 16 cases, 9 classes (SUPPORTED_PREMISE 2, REFUTED_PREMISE 2, UNSUPPORTED_PREMISE 2, AMBIGUOUS_READINGS 2, LEXICAL_NEGATION 2, PARAPHRASE 2, NO_PREMISE 2, IRRELEVANT_REFUTATION 1, CONFLICTING_EVIDENCE 1) | freeze sha256 `2f4347da61b67df5f039f4dc72c985a8187f5c211e91faf7cd4a7ae2e791acbe` | **developer-inspected.** REGRESSION set only. |

The honest statement, which this protocol adopts verbatim as a binding constraint:

> The EPISTEMIC 16-case set and the C2A 38-case set **have been read during development** and
> **cannot be presented as untouched held-out data**. The reconciliation says so
> (`docs-v2/engineering/COGNITIVE-MISSION-RECONCILIATION.md:26`), the program says so
> (`docs-v2/governance/COGNITIVE-DEVELOPMENT-PROGRAM.md:58`), the execution plan says so
> (`docs-v2/engineering/CRJ-01-EXECUTION-PLAN.md:88-90`), and the acceptance contract forbids it
> (`docs-v2/engineering/CRJ-01-ACCEPTANCE.md:30`).

**Both existing plate-tectonics corpora are exhausted.** The EPISTEMIC set's own manifest
verifies that "every corpus document carries a case" over the seed corpus, and the C2A
manifest verifies "every pool document has a case" over the research pool
(`docs-v2/engineering/EPISTEMIC-HELDOUT-SET.json`, `/manifest/verifications`;
`docs-v2/engineering/C2A-HELDOUT-SET.json`, `/manifest/verifications`). Re-using either corpus
for a new CRJ-01 set would mean the new questions are drawn from material whose gold has
already been inspected. **A novel CRJ-01 set therefore requires a third, new corpus.**

#### 2.1.3 What a novel held-out set must contain

A new CRJ-01 domain-A set is admissible only if **all** of the following hold. These are
requirements on the *dataset*, not on any candidate.

1. **A new corpus directory** with its own `corpus-manifest.json` carrying, per entry: file,
   format, language, licence + licence URL, attribution, canonical URL, exact snapshot
   revision, access date and the **sha256 of the imported bytes**. The importer verifies the
   hash before it reads the file — a file whose bytes differ is reported, not silently imported
   (`matrix-core/src/main/resources/corpus/corpus-manifest.json`, `/theLicensing`).
2. **The documents must be imported through the production acquisition path**, not through a
   test-only loader, so the claims, sources and ids in the gold are the production ones
   (the convention of `docs-v2/engineering/EPISTEMIC-HELDOUT-SET.md` §2).
3. **The gold is the source's own text**: every gold proposition must occur **verbatim** in its
   source document, and the builder must prove it before the case is written out
   (`docs-v2/engineering/C2A-HELDOUT-SET.json`, `/manifest/method`).
4. **No hand-written identifiers**: every claim id in the dataset is the production claim id
   introduced by that document's source (`.../C2A-HELDOUT-SET.json`, `/manifest/verifications`).
5. **Class coverage**: the set must contain at least one case of each class in §7
   (unsupported premise, false claim, ambiguous name, contradictory evidence), plus positive
   answerable cases.
6. **Lexical disjointness** from all three existing sets (§1.3 / A3) and **gold disjointness**
   from them.
7. **Non-trivial difficulty**: the C2A measurement shows the production path fails 7 of 25
   comparable cases — `H-P05, H-P06, H-P08, H-P10, H-P11, H-P13, H-P17`
   (`docs-v2/engineering/C2A-HELDOUT-EVALUATION.json`, `/metrics/BASELINE-PATH/failedCases`).
   A set of only easy cases would measure nothing; the set must contain cases the baseline
   path is known to get wrong **by type**, not by copying those questions.
8. **A freeze digest over the gold-bearing content**, computed with the same scope convention
   the repository already uses: "the UTF-8 bytes of the compact JSON document that is the
   `cases` array of this dataset, followed by a single line feed"
   (`docs-v2/engineering/EPISTEMIC-HELDOUT-SET.json`, `/freeze/scope`).

#### 2.1.4 The candidate corpus for CRJ-01 (recommendation, §10 decision D1)

**Recommendation:** a **new, third corpus** on a *different* technical subject from plate
tectonics (so that the existing three sets cannot overlap by document), authored and licensed
for the repository, with 4–7 documents. It must not be the photosynthesis or logic pack
content, because those packs carry their own teach-back instruments (§2.1.1).

**Alternative:** reuse the photosynthesis pack as the *material* for a **teach-back** domain-A
variant. This is cheaper but has two defects: the pack is not a corpus (no manifest, no
import path), and its adversarial file already contains a `transfer` phase
(`photosynthesis.adversarial.json`, items `adv-transfer-1..3`) that has been read during
development. It is therefore a **regression** instrument, not a CRJ-01 sealed set.

### 2.2 Domain B — deterministic microworld

#### 2.2.1 The machinery exists — this is not an invented domain

A deterministic micro-world is **already implemented** in the repository. It is not a proposal.

| element | path | fact |
|---|---|---|
| The world | `matrix-core/src/main/java/io/matrix/cognitive/microworld/LampEnvironment.java` | two inputs `power` and `switch` (`:47-53`), one outcome `lamp` (`:53`). The rule is a **private** method `applyHiddenRule` (`:126-128`) — "PRIVATE, ON PURPOSE. Nothing outside this class may read the rule" (`:120-125`). |
| The world's interface | `.../microworld/MicroWorldEnvironment.java:18-35` | the interface exposes only `probe`, `variables`, `outcomeVariable` — the rule is not part of it. |
| The fault injector | `.../LampEnvironment.java:102-107` | `adversarialProbe` returns the **negation** of the world, flagged adversarial, so a scenario can prove the learner *detects* a contradiction instead of silently rewriting its model. |
| The refusal | `.../LampEnvironment.java:136-141` | an assignment naming a variable the world does not have is refused, not answered falsely. |
| The hypothesis space | `.../microworld/HypothesisSpace.java:80-82`, `:157-173` | `unbiased()` = **all sixteen** Boolean functions of the two variables; `h-0001` is the conjunction the world obeys (`:154-155`). Exact, no sampling (`:33-36`). |
| The learner | `.../microworld/MicroWorldSession.java` | `observe` (`:176-223`) narrows the space; a contradiction of a **settled** prediction is recorded as a `PredictionError` and the observation is **quarantined**, not applied (`:212-220`); `predict` (`:363-420`) reports disagreement rather than hiding it; `proposeExperiment` (`:436-464`) maximises expected information gain with a canonical tie-break. |
| The episode | `matrix-core/src/main/java/io/matrix/learning/env/MicroWorldEpisodeRunner.java` | `run()` (`:183-241`) with the order **predict before probe** (`:218-219`); `utilityOf(s) = 1 − s/16` (`:255-257`); `UNBIASED_HYPOTHESIS_COUNT = 16` (`:67-68`); `EXHAUSTED_INPUT_DOMAIN_SIZE = 4` (`:78-79`); `SETTLED_SURVIVOR_COUNT = 1` (`:88`); `DEFAULT_STEP_LIMIT = 4` (`:97`); seeded probe order via `ExploratoryActionSampler` (`:286-303`). |
| The comparison | `.../learning/env/EpisodeComparison.java` | `stepsSaved()` (`:74-76`), `utilityGain()` (`:119-121`) — a comparison holds *reports*, not copies of numbers (`:16-23`). |
| The kernel methods | `.../cognitive/MicroWorldReferenceMethod.java`, `.../cognitive/MicroWorldSettledModelMethod.java` | ids `micro-world-hypothesis-enumeration` (v1) and `micro-world-settled-model` (v1); the fast path's fallback is the reference (`:48`), and it refuses when the model has not settled (`:110-121`). |
| The wiring | `matrix-core/src/main/java/io/matrix/cognitive/CognitiveService.java:291-314` | the canonical build constructs `new LampEnvironment()`, a `MicroWorldSession.DefaultFactory`, and registers **both** micro-world methods into **one** `AlgorithmRegistry` and **one** `CostLedger`. |

#### 2.2.2 The limit that also exists, and it must be stated, not smoothed over

**The lamp world has no generalisation gap.** Its input domain is 4 assignments
(`EXHAUSTED_INPUT_DOMAIN_SIZE`, `MicroWorldEpisodeRunner.java:78-79`), and the class that
implements the fast path says so in its own accuracy profile:

> "agreement with the reference method over the SAME model-eligible observations
> (**IN-SAMPLE: the lamp world's input domain is exhausted once the model settles, so no
> independent validation data exists**)" — `.../cognitive/MicroWorldSettledModelMethod.java:64-69`

Consequences that the protocol must honour:

* The reference method is **exact** within the declared space
  (`.../MicroWorldReferenceMethod.java:20-21`), so **arm C cannot be more correct than arm A in
  the lamp world** — it can only be *cheaper* (prediction **P1**, **P2**).
* Therefore the micro-world domain's primary metric is **cost per correct answer** and
  **competence retention**, and **correctness is a guard** (must be equal), not the target.
* A *semantic* generalisation test in domain B (a learned rule applied to a **different rule**)
  is **not possible with the machinery as it exists**: `WorldInput.KNOWN_VARIABLES` is fixed to
  `{power, switch}` (`.../microworld/WorldInput.java:36`) and `HypothesisSpace` is fixed to the
  sixteen functions of those two variables (`.../HypothesisSpace.java:41`, `:157-173`).

This is stated plainly because it would be easy to present a micro-world experiment as a
"generalisation" result when the source says the validation data does not exist.

#### 2.2.3 The smallest domain that CAN be run with the machinery that exists

**Domain B is the lamp world, run as-is, as a *cross-episode competence-retention and
efficiency-transfer* domain.** Concretely:

* **Task instance** = one episode = one (world, episode id, seed, probe policy) tuple
  (`MicroWorldEpisodeRunner` constructor, `:129-181`).
* **The learned artefact** = a promoted `Procedure` over the micro-world family
  (`.../cognitive/algorithms/ProcedureStore.java:72-77`), validated by
  `ProcedureValidator` against the reference method on dev-set episodes only
  (`.../ProcedureValidator.java:35`, `:53-56`).
* **The transfer test** = the promoted procedure answers **a different episode** (new episode
  id, new seed, new probe order) of the **same world family**, on assignments that episode has
  not observed, without re-deriving the hypothesis set.
* **What "different task" means here, exactly**: a different *episode*, not a different *rule*.
  The protocol states this in every table that reports a domain-B result.

**If the owner wants a semantic-generalisation test in domain B** (a learned rule reused on a
*different* rule), the smallest sufficient change is a new `MicroWorldEnvironment`
implementation plus a widened `HypothesisSpace` over *n* variables — a **CRJ.2.x build item**,
an owner-authorized boundary (`CRJ-01-EXECUTION-PLAN.md:51`), **not** a CRJ-01 protocol item.
Its cost, its hash and its design are `NOT_INSTRUMENTED` at the baseline `b7bd1be4`.

#### 2.2.4 Reachability caveat

This protocol asserts that the micro-world machinery exists and how it is wired
(`CognitiveService.java:291-314`). It does **not** assert that the micro-world is reachable
through a *packaged HTTP or CLI* entry point — that is the explicit deliverable of the
reachability maps `CRJ.0.1`/`CRJ.0.2`
(`docs-v2/engineering/CRJ-01-EXECUTION-PLAN.md:29-30`). Until those maps land, **domain B's
served-path status is `UNKNOWN`**, and any domain-B claim must be labelled accordingly.

---

## 3. The three arms

The arm table is the contract's (`CRJ-01-ACCEPTANCE.md:17-23`). This section makes each arm
concrete: what it may read, what it may not read, and **which existing classes implement it**.

### 3.1 Arm A — stateless reference

| | |
|---|---|
| **May read** | the current workspace and its documents/claims/sources; the approved reference methods; the current question and level. |
| **May NOT read** | any stored episode, any prior journey trace, any promoted `Procedure`, any retained answer, the learning store, or any record of a previous run of the same case. |
| **Domain A implementation (existing classes)** | `POST /v1/workbench/workspaces/{id}/reports` (`matrix-core/src/main/java/io/matrix/knowledge/ArtifactReportResource.java:59`, `:208-211`) with the method forced to `report.journey.reference.v1` (`.../cognitive/report/ReportJourneyMethod.java:50`). The reference method asks the workspace, assembles the report and hands it to the bounded writer (`ReportJourneyMethod.java:22-44`). Retrieval and premise semantics come from `KnowledgeWorkbench` and `PremiseSemantics` (`.../knowledge/PremiseSemantics.java:62-75`). |
| **Domain B implementation (existing classes)** | `MicroWorldReferenceMethod` (`.../cognitive/MicroWorldReferenceMethod.java:36-45`), registered at `CognitiveService.java:304`. It re-derives the surviving hypotheses from the **model-eligible** observations only (`:96`) and **refuses** when the survivors disagree (`:108-112`). |
| **Rationale for the implementation choice** | the reference method is the definition of the answer inside the declared space (`MicroWorldReferenceMethod.java:20-21`); it is the correct baseline for both the accuracy metric and the fallback of the fast path (`MicroWorldSettledModelMethod.java:48`). |

### 3.2 Arm B — exact replay / cache

| | |
|---|---|
| **May read** | everything arm A may read, **plus** the exact-key retained journey and the artifact already committed under the same action identity. |
| **May NOT read** | anything generalised, any promoted `Procedure`, any learned rule, any store of prior episodes. |
| **Domain A implementation (existing classes)** | `report.journey.replay.v1` (`.../cognitive/report/ReportJourneyMethod.java:53`). Its applicability domain is the precondition: the identity was already committed, the workspace has not changed, and the requested file name is the committed one (`ReportJourneyMethod.java:64-97`). Inside the domain it serves the retained answer **and re-reads the artifact from disk and re-measures its digest** rather than trusting the trace (`:41-44`). The retained payload is stored and read through `ReportJourneyTrace.retained(...)` (`.../cognitive/report/ReportJourneyTrace.java:141-147`). |
| **Domain B implementation (existing classes)** | the same episode re-executed under the same seed and compared with `EpisodeComparison.between(candidate, baseline)` (`.../learning/env/EpisodeComparison.java:43-46`). |
| **Scoring rule (binding)** | arm B's replay advantage is **reported separately** from the genuinely-new-task subset, exactly as the contract requires: "Cached identical queries must be scored separately from genuinely new task instances" (`CRJ-01-ACCEPTANCE.md:23`). The §6 metric table therefore carries two denominators for arm B: the *repeat* subset and the *novel* subset. |
| **A caution about arm B's declared accuracy** | the replay method's declared accuracy profile is `1.0` over `REPLAY_VALIDATION_CASES = 3` cases of "the closed-loop replay validation corpus", measured `2026-11-16` (`.../cognitive/report/ReportJourneyMethod.java:157-164`) — **fewer** cases than the repository's own promotion convention for a validated procedure (`MINIMUM_VALIDATION_CASES = 5`, `.../cognitive/algorithms/ProcedureValidator.java:35`). CRJ-01 therefore does **not** treat that declared `1.0` as an accuracy claim: arm B's accuracy is measured on the sealed split like every other arm. |

### 3.3 Arm C — governed learned competence

| | |
|---|---|
| **May read** | everything arm A may read, **plus** the safety controls of arm B (the gate, the root confinement, the writer's lease, the idempotency), **plus only** independently validated, versioned learned claims/rules/procedures **with their provenance** (`CRJ-01-ACCEPTANCE.md:21`). |
| **May NOT read** | unvalidated candidate rules; any record produced by evaluation traffic (the quarantine exists and is enforced — `docs-v2/engineering/EPISTEMIC-LEARNING-ISOLATION.md`); anything whose validation used the sealed set (§1.3 / A2). |
| **Domain A implementation (existing classes)** | the same served route as arm A (`ArtifactReportResource`), with a promoted `Procedure` consulted through the kernel's selector (`.../cognitive/CognitiveKernel.java:1145` writes a procedure in the micro-world family; the same mechanism is the portfolio's). The gate, consent and root checks are unchanged (`ArtifactReportResource.java:264-276`) — the plan cannot widen them (`:295-303`). |
| **Domain B implementation (existing classes)** | `MicroWorldSettledModelMethod` (`.../cognitive/MicroWorldSettledModelMethod.java:36-121`): it answers only when the model has settled **and** any supplied procedure is active and applicable (`:86-107`), and it falls back to the reference otherwise. Promotion and retirement: `ProcedureStore.promote` / `recordUse` (`.../ProcedureStore.java:72-77`, `:44-49`). |
| **The governing rule** | a procedure enters the store only after it agreed with its reference on **every** held-out validation case, of which there must be at least `MINIMUM_VALIDATION_CASES = 5` (`.../ProcedureValidator.java:35`); one disagreement refuses the promotion and the reason names the case (`ProcedureValidator.java:17-20`). A procedure whose recent agreement falls below `MINIMUM_AGREEMENT = 0.95` is retired and the reference takes over again (`.../ProcedureStore.java:44-51`). |

### 3.4 Keeping the arms comparable (binding)

| dimension | rule |
|---|---|
| **Inputs** | identical. The same case id, the same question text, the same level, the same workspace snapshot, the same document set, in the same order, for all three arms. |
| **Permissions** | identical. Every case carries `consent: true` (`ArtifactReportResource.java:264-270`); the gate must be open for all arms (`.../actions/ArtifactActionService.java:126-153`); the authorized output root comes from configuration and never from the request (`:227-252`). |
| **Budget** | identical and **explicit**. The shipped route uses `ComputeBudget.unbounded()` (`ArtifactReportResource.java:320`, `:330`); CRJ-01 must replace it with a fixed per-arm budget. The budget value is **`PROPOSED`** (§9) and, if the owner does not set one, the run is reported as `NOT_INSTRUMENTED` on budget and the comparison is marked as cost-uncontrolled. |
| **Wall time** | measured per case and per arm; published next to the accuracy table (`CRJ-01-ACCEPTANCE.md:65`). |
| **Isolation** | each arm runs against its **own** workspace id and its **own** output root, so that arm B's retained traces cannot be seen by arm A and arm C's procedures cannot be seen by arms A or B. The workspace id and the root are recorded per arm per case. |
| **Ordering** | the arms are run in a **pre-registered order** (A, B, C) on the sealed set, and the order is recorded. If an arm's run can influence a later arm's state (it cannot, given isolation, but the possibility must be excluded by evidence), that is a protocol violation. |
| **Inapplicability** | if an arm cannot run a case (refusal, inapplicable precondition), the case is recorded as an **inapplicable** row with the reason, and the arm's denominator is stated accordingly ("record whether any arm is inapplicable", `CRJ-01-ACCEPTANCE.md:23`). |

---

## 4. The oracle

> "A separately implemented oracle is required; an oracle that reuses the solver's own code is
> rejected." (`CRJ-01-ACCEPTANCE.md:30`)

The oracle's truth must be computable **without running, importing, or consulting the solver's
answer path**. Concretely, the oracle may not call the retriever, the workbench, the kernel
selector, the session, the hypothesis space, or the environment's `probe`.

### 4.1 Domain A oracle — source-span oracle

**Question of truth:** *does the served response cite the evidence the source document actually
contains, for this question?*

**Mechanism.** The oracle is a **literal text check against the corpus files on disk**. For each
sealed case the dataset carries a gold record: the verbatim gold proposition, the source file
name, and the expected disposition. The oracle:

1. re-reads the named corpus file's bytes from the classpath and re-computes its sha256,
   comparing it with the manifest's recorded digest;
2. asserts that the gold proposition occurs **verbatim** in those bytes;
3. reads the served response and extracts the cited propositions and claim ids;
4. scores a case as **grounded** only if at least one cited proposition is byte-identical to a
   gold proposition **and** the cited claim id is the production claim introduced by that
   document's source;
5. scores a negative case (UNSUPPORTED / REFUTED / AMBIGUOUS / CONTRADICTORY / unavailable
   action) by comparing the response's **disposition field** with the expected disposition and
   the expected provenance.

It never calls the retriever, so it cannot be fooled by a retrieval bug and it cannot inherit
the solver's notion of relevance. The oracle is a *second implementation of the truth*, not a
re-run of the first.

**Where the logic lives (proposed, does not exist at `b7bd1be4`):**
`matrix-core/src/test/java/io/matrix/crj/evidence/SourceSpanOracle.java`
(class `SourceSpanOracle`, package `io.matrix.crj.evidence`), with its unit test
`matrix-core/src/test/java/io/matrix/crj/evidence/SourceSpanOracleTest.java`.
The class does **not** exist at the baseline; its sha256 is a mandatory freeze field (§5.5).

> **STATUS: BUILT.** The class and its test exist as of **2026-11-20**, on the branch
> `feature/crj-01-arm-c`, with **7 tests, 0 failures**. The instrument's own control (§7.6) is
> among them: a gold proposition corrupted by a single character is reported as
> `GOLD_NOT_IN_CORPUS` — **loudly**, not as an ungrounded answer — and a corrupted case can
> never score as grounded. The oracle reads the corpus file's **bytes** and re-computes their
> sha256; it never calls the retriever, the workbench, the kernel selector or the session, and
> it takes the served answer as **data** (`ServedAnswer`), so the harness that reads the
> response and the oracle that scores it are two pieces of code that meet at one record.
> Its sha256 at the moment of writing is
> `2f1f5579717272ee10cccbf32bcd82aab7d73d62adbd23087bdee0a980c65d9d` — **the freeze records
> the digest of the file the reviewer re-derives, not this one**: the file may still change
> before the freeze, and the freeze's digest is the one that counts.

**Why a test-tree class and not production code:** the oracle is an *instrument of the
experiment*, not a product feature. Putting it in `src/test` keeps it out of the serving path
and out of the packaged application, which is exactly what the contract wants. The negative
control of the instrument itself is in §7.6.

### 4.2 Domain B oracle — independent truth table, cross-checked

**Question of truth:** *what is the lamp world's outcome for a given assignment?*

**The constraint.** The world's rule is `input.get(POWER_VARIABLE) && input.get(SWITCH_VARIABLE)`
and it is **private** (`.../LampEnvironment.java:126-128`). The oracle may not read it, and may
not ask the environment (`probe` performs the rule and returns the answer —
`.../microworld/MicroWorldEnvironment.java:20-29`); an oracle that asked the environment would
be asking the solver's own instrument.

**Mechanism.** The oracle is written from the **specification**, not from the code: the world is
documented as "two inputs, one output, one hidden rule" (`LampEnvironment.java:8`) and the
hypothesis space documents `h-0001` as "the function that is true only at
`power=1,switch=1` — the conjunction the lamp world obeys"
(`.../microworld/HypothesisSpace.java:151-153`). The oracle is an explicit four-row truth
table, implemented as a literal table of literals — not an expression that could be copied
from the private method.

**Where the logic lives (proposed, does not exist at `b7bd1be4`):**
`matrix-core/src/test/java/io/matrix/crj/microworld/LampTruthOracle.java`
(class `LampTruthOracle`, package `io.matrix.crj.microworld`).

> **STATUS: BUILT.** The class and its test exist as of **2026-11-20**, on the branch
> `feature/crj-01-arm-c`, with **4 tests, 0 failures**. The truth table is **four literal
> rows**, written out one by one from the specification — not an expression that could have
> been copied from the world's private method — and the class does **not import the lamp
> environment at all**: it does not name its type and cannot ask it anything. The two meet in
> exactly one place, the cross-check, and the world is read there **as data**. The cross-check
> has been **executed**: on all four assignments the oracle and the world agree, and the record
> is `instrument_crosscheck: {assignments: 4, agreements: 4}`. A disagreement — and an
> assignment the world did not answer — makes the run **VOID** with the reason
> `INSTRUMENT_MISMATCH`, and both cases are tested. The oracle's sha256 at the moment of
> writing is
> `c669dfb70825796bec3cc4e58d39cb8f997871175593d736906d3ae5565cff52` — **the freeze records
> the digest of the file the reviewer re-derives, not this one**.

**The instrument cross-check (binding, before any measurement).** Before the first arm runs, the
oracle and the world are compared on **all four** assignments of the domain. The result is
recorded as `instrument_crosscheck: {assignments: 4, agreements: 4}`. If they disagree on any
assignment, the run is **VOID** with reason `INSTRUMENT_MISMATCH` — it is not scored, not
re-run to a nicer number, and the mismatch is a finding in its own right. This makes the
oracle's independence testable rather than asserted: a second implementation that silently
agreed with a broken world would still be caught by the negative controls of §7.

### 4.3 The oracle's own falsifier

The oracle is not trusted because it was written carefully; it is trusted because it can
disagree with the solver. §7.6 states the fixture that must make it disagree.

---

## 5. Data split and freeze

### 5.1 The split

| split | purpose | author | status at `b7bd1be4` |
|---|---|---|---|
| **dev split** | arm development, procedure validation (A2), threshold-free debugging | the solver's author | `PROPOSED` — must exist before any promotion; **must not** overlap the sealed split |
| **sealed test split** | the single measurement of arm C vs A vs B | an author who implements no arm | `PROPOSED` — does not exist; must be frozen by an independent reviewer |
| **regression sets** | protection against regressions; never a CRJ-01 measurement | — | the 29-case dev set, the C2A 38-case set, the EPISTEMIC 16-case set (§2.1.2) |

### 5.2 Honest statement about the existing sets (binding)

**The EPISTEMIC 16-case set and the C2A 38-case set have been inspected during development.
They CANNOT be presented as untouched held-out data.** They are **regression sets** for CRJ-01
and nothing else. The same is true of the 29-case development set
(`matrix-core/src/main/resources/epistemic-eval/cases.json`). A CRJ-01 result that quotes any
of them as "held-out evidence" is invalid.

The consequences are concrete and are already visible in the source: the C2A builder is
explicit that the two older sets "are REGRESSION sets from here on: they keep their value, and
they are no longer fresh proof"
(`matrix-core/src/test/java/io/matrix/knowledge/epistemic/EpistemicHeldOutSetBuilderTest.java`,
class javadoc).

### 5.3 Sizes

**All sizes below are `PROPOSED`.** The only anchor is the contract's own band: "ideally 30–50
novel tasks across both domains if the data and budget permit"
(`CRJ-01-ACCEPTANCE.md:30`). The arithmetic of the proposal:

| split | domain A | domain B | total |
|---|---|---|---|
| dev | 9 | 9 | 18 |
| sealed test | 18 | 18 | 36 |

36 novel tasks is inside the contract's 30–50 band; the split across the two domains is even;
the dev:sealed ratio is 1:2, which lets a procedure be promoted on dev episodes and then
measured on sealed ones. **The proposal is justified by the contract's band and by nothing
else** — it was fixed before any CRJ-01 measurement and must not be changed after one.

Domain B's "cases" are **episodes**: 18 sealed episodes, each with a distinct seed drawn from
a pre-committed seed list, over the same world family.

### 5.4 Generation, and the rules the generator must enforce

**The generator** is a builder in the shape the repository already uses: a test-tree builder
that writes a byte-reproducible JSON artefact and reads it back and compares it byte-for-byte
before the test ends (`.../knowledge/epistemic/EpistemicHeldOutSetBuilderTest.java`, class
javadoc). The CRJ-01 generator is a **new** builder; it does not exist at `b7bd1be4`.

**Generator identity (all recorded in the freeze record):**

* `generator.id` — a stable identifier, e.g. `crj-01-case-builder`
* `generator.version` — a semantic version
* `generator.sourceSha256` — the sha256 of the builder source file
* `generator.seed` — the single integer seed from which every case id, ordering and (for
  domain B) episode seed is derived. The repository's own convention for a derived seed is a
  pure function of a state hash (`.../cognitive/ExploratoryActionSampler.java:35-50`), and the
  episode identity hash is `episodeId.hashCode() * 31 + seed`
  (`.../learning/env/MicroWorldEpisodeRunner.java:268-271`).

**Rules the generator must enforce and assert (each is a test that must fail if violated):**

1. every gold proposition occurs **verbatim** in its corpus document (§2.1.3.3);
2. every gold claim id is the **production** claim id of that document's source;
3. every case has a source span;
4. no question shares a normalised token sequence of length **≥ 4** with any question,
   assertion or disputed premise of the 29-case dev set, the C2A 38-case set or the EPISTEMIC
   16-case set, **or with any dev-split case of CRJ-01**;
5. no gold proposition of the sealed split equals a gold proposition of the dev split or of any
   of the three regression sets;
6. the required class set is present and no case repeats a question or an id;
7. the sealed split contains no case whose answer the production path is known to produce from
   the *training* side of any existing instrument (this is the "no exercise of the
   photosynthesis `transfer` phase" rule — the `transfer` items of
   `photosynthesis.adversarial.json` are read, so they may not be reused);
8. every case carries a **fresh** idempotency key, distinct from every other case's key and
   from every key used in the dev split (§1.3 / A1);
9. for domain B: the seed list is committed, the episodes are enumerated in seed order, and the
   per-episode probe order is derived from the episode identity hash exactly as
   `MicroWorldEpisodeRunner` does (§2.2.1).

### 5.5 The hashes that are recorded at freeze

| field | what it covers |
|---|---|
| `dataset.sha256` | the gold-bearing content: the UTF-8 bytes of the compact JSON `cases` array + one line feed — the exact scope the repository already uses (`docs-v2/engineering/EPISTEMIC-HELDOUT-SET.json`, `/freeze/scope`) |
| `dataset.fullFileSha256` | the whole artefact file, for convenience |
| `generator.sourceSha256` | the builder source |
| `oracle.sourceSha256` | `SourceSpanOracle.java` **and** `LampTruthOracle.java`, separately |
| `seeds` | the generator seed and the domain-B episode seed list |
| `corpusManifestSha256` | each new corpus manifest's digest (the C2A set records the analogous field as `fa7ba16e…`, `docs-v2/engineering/C2A-HELDOUT-SET.json`, `/manifest/corpusManifestSha256`) |
| `branchSha` | the exact commit the freeze was taken on. **The baseline of this protocol is `b7bd1be4`**; the freeze records its own SHA, which is normally **not** `b7bd1be4` because the corpus, the builder and the oracle are new files. |
| `instrumentCrosscheck` | the 4-assignment agreement record of §4.2 |
| `roles` | the name of the case author, the name of the freezing reviewer, and the statement that they are different people and that neither wrote a solver |

### 5.6 The freeze procedure (binding)

1. The **case author** (not a solver author) produces the corpus, the cases and the builder.
2. The **independent reviewer** (not the case author, not a solver author) runs the builder,
   re-derives every hash in §5.5 independently, and confirms the builder's own assertions
   (§5.4.1–9) hold.
3. The reviewer writes the freeze record: the hashes, the date, the branch SHA, the roles, and
   the statement that the freeze precedes any measurement of any arm.
4. **After the freeze, the sealed split is read-only.** Any change — a new case, a reworded
   question, a changed gold — **voids the freeze**, requires a new dataset version, and
   requires the whole measurement to be re-run against the new version. It is never "patched".
5. The reviewer publishes the freeze record and the manifest hash **before** the first arm run.
   A freeze that is published after the first result is not a freeze, and the result is
   reported as `UNCORRECTED` whatever it says.
6. The reviewer is given the **class list** and the **acceptance criteria**, and is **not** given
   the arms' implementations, so that the set cannot be written to favour one of them
   (`CRJ-01-ACCEPTANCE.md:30`).
7. The reviewer may not be the author of the negative controls of §7 either, because those are
   also inputs to the arms' behaviour.

---

## 6. Metrics and denominators

Every metric names its **numerator**, its **denominator**, and the **exact population** the
denominator counts. Denominators are drawn from the sealed split's own manifest, never from a
number quoted from a prior report.

### 6.1 Domain A (evidence-rich technical documents)

| metric | numerator | denominator | notes |
|---|---|---|---|
| **M-A1 grounded answer rate** | sealed cases whose response cites ≥ 1 gold claim whose proposition is byte-identical to a gold proposition **and** whose claim id is the production id of that document's source | the sealed split's **answerable** cases (the POSITIVE class) | the analogue of the C2A measurement, whose denominator was `answerableCases = 19` out of `comparableCases = 25` (`docs-v2/engineering/C2A-HELDOUT-EVALUATION.json`, `/denominators`) |
| **M-A2 abstention correctness** | sealed cases whose response's disposition is `ABSTAINED`/`REFUTED` as expected | the sealed split's **unanswerable + premise-bearing negative** classes (NEGATIVE, UNSUPPORTED, REFUTED, AMBIGUOUS, CONTRADICTORY) | the C2A analogue: `abstentionAccuracy = 1.0` over `unanswerableCases = 6` (same artefact) |
| **M-A3 unsupported-answer rate** | sealed cases answered with **zero** gold cited | **all** sealed cases that carried a question (the C2A analogue: `askCases = 25`, `unsupportedOverAllAskCases = 0.28`, `unsupportedAnswerRate = 0.3684`) | this is the **guard**: coverage may not rise while this rises (`CRJ-01-ACCEPTANCE.md:59`) |
| **M-A4 premise-disposition accuracy** | cases whose `PremiseSemantics.Status` equals the expected status | the sealed cases that carry a premise | the five statuses are `NO_PREMISE, SUPPORTED, REFUTED, UNSUPPORTED, CONTRADICTORY, AMBIGUOUS` (`.../knowledge/PremiseSemantics.java:62-75`) |
| **M-A5 conflict-surfacing rate** | CONTRADICTORY cases whose response carries **both** sides with their provenance, and resolves neither | the sealed CONTRADICTORY cases | the disposition is defined as "both sides reported with their provenance; not refused, not resolved" (`PremiseSemantics.java:35-38`) |
| **M-A6 effect-verification rate** | cases whose written artifact was independently verified (bytes + digest + index) by a reader that did not write it | the sealed cases whose arm performed a write | the observer is `ArtifactObserver.observe(...)` (`.../cognitive/report/ArtifactObserver.java:82`), invoked from `ArtifactReportResource.java:352-359` |

### 6.2 Domain B (deterministic microworld)

| metric | numerator | denominator | notes |
|---|---|---|---|
| **M-B1 episode utility** | `1 − finalSurvivors / 16` | per episode; the scale's denominator is `UNBIASED_HYPOTHESIS_COUNT = 16` (`.../learning/env/MicroWorldEpisodeRunner.java:67-68`, `:255-257`) | the microworld's own scale, not a new one |
| **M-B2 model settled** | episodes whose `finalSurvivors == 1` | the sealed episodes | `SETTLED_SURVIVOR_COUNT = 1` (`MicroWorldEpisodeRunner.java:88`); `EpisodeReport.learned()` (`.../learning/env/EpisodeReport.java:137-139`) |
| **M-B3 probes to settle** | `steps` | the sealed episodes | bounded by `DEFAULT_STEP_LIMIT = 4` (= `EXHAUSTED_INPUT_DOMAIN_SIZE`, `MicroWorldEpisodeRunner.java:97`, `:78-79`) |
| **M-B4 transfer answer accuracy** | sealed-episode queries whose answer equals the oracle's truth | the sealed episodes' **novel query assignments** (assignments that episode had not observed) | the domain-B correctness metric. By **P1** it is expected to be **equal** across arms |
| **M-B5 cost per correct answer** | total probes (or total wall time) of the arm | the number of correct answers that arm produced | the domain-B **primary** metric, because M-B4 is predicted to be flat |
| **M-B6 probes saved vs A** | `EpisodeComparison.stepsSaved()` | the sealed episodes | `.../learning/env/EpisodeComparison.java:74-76` |
| **M-B7 prediction-error handling** | adversarial observations that produced a recorded `PredictionError` **and** were quarantined rather than applied | the adversarial probes injected | `.../microworld/MicroWorldSession.java:212-220`; `.../LampEnvironment.java:102-107` |
| **M-B8 termination honesty** | episodes whose `TerminationReason` is reported truthfully (`MODEL_SETTLED`, `DOMAIN_EXHAUSTED`, `STEP_LIMIT_REACHED`, `NO_SURVIVING_HYPOTHESIS`) | the sealed episodes | a cut-short episode must not be readable as a finished one (`.../learning/env/EpisodeReport.java:56-91`) |

### 6.3 The paired case-level outcome format (binding)

One row per `(case_id, arm)`, in the sealed split's order. This is the row schema the evidence
artefact must carry (`CRJ-01-ACCEPTANCE.md:71`):

```
case_id | arm | domain | outcome | oracle_verdict | gold_claim_ids_cited | gold_propositions_matched
        | disposition | citations | duration_ms | probes | method_id | permission_decision
        | artifact_sha256 | trace_ref | inapplicable_reason
```

* `outcome` ∈ {CORRECT, INCORRECT, REFUSED, INAPPLICABLE, ERROR}
* `oracle_verdict` ∈ {GROUNDED, REFUSED_CORRECTLY, DISPOSITION_MISMATCH, UNGROUNDED, N/A}
* A row is **never deleted**. A crash is a row with `outcome = ERROR` and the exception text
  (§8.4).
* `duration_ms` is wall time for that row; `probes` is the domain-B probe count; `method_id` is
  the kernel-selected method, so an arm that silently fell back is visible.

### 6.4 Uncertainty treatment for small samples (binding)

The sample is small (`PROPOSED` n = 18 paired per domain). The treatment is therefore
**paired**, never a comparison of two independent proportions.

1. **Primary inference — the paired difference with an interval.** For each pair
   `(case, A)` and `(case, C)` on the identical case list, let
   `d_i = correct_C(i) − correct_A(i) ∈ {−1, 0, +1}`. The point estimate is
   `Δ = (1/n)·Σ d_i`. The interval is a **seeded bootstrap percentile interval** over cases:
   `B = 10000` resamples with replacement, seed recorded in the freeze record (§5.5),
   percentile interval `[2.5, 97.5]`. The same is computed for `Δ(C − B)` on the **novel**
   subset (the repeat subset is excluded from this difference and reported separately, §3.2).
   `B = 10000` is a convention fixed here, before any run, not a tuned value.
2. **Secondary inference — the exact McNemar test.** The two-sided exact binomial test on the
   discordant pairs (`b` = C wins, `c` = A wins), `p = 2·Σ_{k≥b} C(b+c,k)/2^(b+c)`. **Its power
   limitation must be printed next to the p-value**: at n = 18 the test reaches `p ≤ 0.05` only
   when at least 6 discordant pairs exist and **all** of them favour one arm
   (P3: 6/6 → p = 0.03125; 5/6 → p = 0.21875).
3. **No metric is reported without its denominator**, and no accuracy is reported without the
   refusal, cost and calibration figures beside it
   (`docs-v2/engineering/AGENT-COMMISSION-COGNITIVE-PROGRAM.md:61`).
4. **Thresholds.** `α = 0.05` (two-sided) is the conventional level, fixed here **before** any
   run; it is `PROPOSED` in the sense that the owner may change it, and it may not be changed
   after a run. The **minimum meaningful improvement** `δ_MI` is **not** fixed by this protocol
   — it is owner decision **D4** (§10.4). Every threshold in this document is either `PROPOSED`
   or `NOT_INSTRUMENTED`; none is derived from a prior report's numbers.

---

## 7. Negative controls

One concrete instance per required class (`CRJ-01-ACCEPTANCE.md:32`;
`docs-v2/engineering/CRJ-01-EXECUTION-PLAN.md:45`). Each names the **fixture**, the **expected
verdict**, and the **class that must produce it**.

> **Status of the fixtures below.** They are *specifications* for the control set, written here
> so that the class list is fixed before the set is authored. The strings are `PROPOSED` and
> must be validated against the new corpus by the independent reviewer at freeze time (the
> corpus-containment check of §5.4). The **verdicts and the producing classes are not
> negotiable** — they are read from the source.

### 7.1 Unsupported premise

| | |
|---|---|
| **Fixture (domain A)** | Question: *"Given that the speed of light in vacuum is exactly 299,792,458 metres per second, what does that imply about the rate of seafloor spreading?"* — a premise whose content terms occur in **no** corpus document. |
| **Expected verdict** | premise status **UNSUPPORTED** — *not* REFUTED; the question is **still answered**; the premise is named as unsupported. "The premise is reported as unsupported and the question still proceeds: an absence of evidence is not a refutation" (`.../knowledge/PremiseSemantics.java:32-34`). |
| **Class that must produce it** | `io.matrix.knowledge.PremiseSemantics` (status `UNSUPPORTED`) + `io.matrix.knowledge.PremiseChecker`, served through `ArtifactReportResource.report` (`.../knowledge/ArtifactReportResource.java:208-211`). |
| **A control fails if** | the response refuses the question, or answers it as if the premise held. |

### 7.2 False claim (refuted premise)

| | |
|---|---|
| **Fixture (domain A)** | Question: *"Given that new crust is NOT generated where the plates pull away from each other, what happens at the Mid-Atlantic Ridge?"* — with the premise typed **BACKGROUND** ("the question asks under the premise; a refutation stops it", `PremiseSemantics.java:91-98`). |
| **Expected verdict** | premise status **REFUTED**; the question is **refused**; the refusal **names the claim id and the source id** that refute it (`PremiseSemantics.java:30-31`). The corpus supports the premise: divergent boundaries are "where new crust is generated as the plates pull away from each other" (the proposition is the gold of `E-S01` in the EPISTEMIC set; the reviewer re-derives the exact span from the new corpus). |
| **Class that must produce it** | `io.matrix.knowledge.PremiseSemantics` (status `REFUTED`) + `PremiseChecker`, served through `ArtifactReportResource.report`. |
| **A control fails if** | the refusal does not name the claim and the source, or the question is answered anyway (that would be a **false abstention** — the defect class the repo has already paid for once, `docs-v2/engineering/EPISTEMIC-EXECUTION-PLAN.md:88`). |

### 7.3 Ambiguous name

| | |
|---|---|
| **Fixture (domain A)** | Question: *"What drives plate motion — what is the strongest force acting on the plates?"* — the corpus holds accounts on more than one side: the subducting oceanic crust forms "the downward convecting limb of a mantle cell, which is **the strongest driver of plate motion**"
 (`matrix-core/src/main/resources/corpus/plate-tectonics/wikipedia-plate-tectonics.md:7`); "Slab pull is therefore most widely thought to be **the greatest force acting on the plates**" (`:43`); and "This view however **has been contradicted** by a recent study which found that the actual motions of the Pacific plate … do not correlate mainly with either slab pull or slab push" (`:63`). |
| **Expected verdict** | disposition **AMBIGUOUS** (or **CONTRADICTORY**, if the reviewer judges the two accounts unable to coexist): the readings are reported **separately, each with its claim and its source**, and **none of them is chosen** (`PremiseSemantics.java:39-41`, `:25-42`). |
| **Class that must produce it** | `io.matrix.knowledge.PremiseSemantics` (status `AMBIGUOUS` / `CONTRADICTORY`) + `io.matrix.knowledge.ClaimConflictDetector`, surfaced in `ArtifactReportResource`'s `ConflictReport` / `PremiseStatus` fields (`.../knowledge/ArtifactReportResource.java:140-156`). |
| **A control fails if** | one reading is chosen and presented as the answer, or the readings are reported without their provenance. |

### 7.4 Contradictory evidence

Two instances, one per domain, because the mechanism exists in both.

| | domain A | domain B |
|---|---|---|
| **Fixture** | the same corpus tension as §7.3, presented as a direct question: *"Is slab pull the force that drives plate motion?"* with two accounts on opposite sides | an **adversarial probe**: `LampEnvironment.adversarialProbe(input)` returns the **negation** of the world, flagged adversarial (`.../LampEnvironment.java:102-107`) |
| **Expected verdict** | disposition **CONTRADICTORY**: both sides with their provenance; **not refused, not resolved** (`PremiseSemantics.java:35-38`, `:25-42`) | a recorded `PredictionError`, the hypotheses flagged for review, and the observation **quarantined** — the model is **not** rewritten (`.../microworld/MicroWorldSession.java:212-220`) |
| **Class** | `PremiseSemantics` + `ClaimConflictDetector` | `io.matrix.cognitive.microworld.MicroWorldSession.observe` and `.../microworld/PredictionError` |
| **A control fails if** | the conflict is resolved silently, or the model is rewritten on the contradicting datum | the model is rewritten, or the contradiction is not recorded |

### 7.5 Unavailable action

| | |
|---|---|
| **Fixture (domain A)** | `POST /v1/workbench/workspaces/{id}/reports` **without `consent: true`**, and a second request with a report name that is not a plain `.md` file name (a name carrying a parent-directory part, e.g. two dots followed by a separator and a file name). |
| **Expected verdict** | HTTP **403** with reason `consent_required` for the first (`ArtifactReportResource.java:264-270`); HTTP **400** with reason `invalid_report_name` for the second (`ArtifactReportResource.java:285-291`). **No file is written in either case.** |
| **Class that must produce it** | `io.matrix.actions.ArtifactActionService.gate()` (`.../actions/ArtifactActionService.java:126-153`), `io.matrix.actions.ArtifactWriter` (`.../actions/ArtifactWriter.java:96`), `io.matrix.cognitive.report.ArtifactObserver` (`.../cognitive/report/ArtifactObserver.java:31`). |
| **Fixture (domain B)** | an assignment naming a variable the world does not have, e.g. `{power, flux}`. |
| **Expected verdict (domain B)** | `IllegalArgumentException` — the world refuses the assignment rather than answering falsely (`.../LampEnvironment.java:136-141`). |
| **A control fails if** | a file is written without consent, a path escapes the output root, or the world answers an assignment it does not understand. |

### 7.6 The instrument's own control

The oracle must be able to **disagree** with the solver, or it is not an instrument.

| | |
|---|---|
| **Fixture** | a deliberately corrupted gold record: one sealed case's gold proposition is altered by a single character. |
| **Expected verdict** | the oracle reports `UNGROUNDED` for that case and the corpus-containment assertion (§5.4.1) **fails loudly**. |
| **Class** | `io.matrix.crj.evidence.SourceSpanOracle` (proposed, §4.1). |
| **A control fails if** | the corrupted case still scores as grounded. |

### 7.7 The replay control (the direct test of the null's first term)

| | |
|---|---|
| **Fixture** | the same idempotency key reused with a **different question** (changed wording, changed level, changed report name, changed workspace state — the four variants the execution plan asks for, `CRJ-01-EXECUTION-PLAN.md:31`). |
| **Expected verdict** | the second request is **refused as a conflict** — it must not return the first request's success. The current identity function binds `workspace + key` and ignores the question when a key is present (`ArtifactActionService.java:198-205`), so this is a **source-based risk**, not yet a proven incident (`COGNITIVE-DEVELOPMENT-PROGRAM.md:60`). |
| **Class** | `io.matrix.actions.ArtifactActionService.identity(...)` + `io.matrix.actions.ArtifactWriter`'s one-identity-one-artifact rule. |
| **A control fails if** | the changed request returns the first request's artifact as a success. If it does, the finding is recorded as **`PROVEN RISK`** and is not suppressed by this protocol. |

---

## 8. Stop / pivot rules

### 8.1 What counts as a falsified hypothesis

**H (the transfer hypothesis): *arm C improves on a different held-out task, beyond replay,
preexisting rules, lexical overlap, selection bias and unaccounted computation.***

**FALSIFIED / REJECTED** if any of the following holds on the sealed split:

* **F1 — no better than A.** The upper bound of the bootstrap interval for `Δ(C − A)` is
  **≤ 0**. Arm C is not better than the stateless reference.
* **F2 — no better than B on novel cases.** On the **novel** subset (replay excluded),
  the upper bound of `Δ(C − B)` is **≤ 0**. Arm C is not better than the replay arm.
* **F3 — the advantage is replay.** On the **repeat** subset `Δ(C − B) > 0` while on the
  **novel** subset `Δ(C − B) ≤ 0`. The gain is caching, not learning. This is the explicit
  prohibition of `CRJ-01-ACCEPTANCE.md:23`: "If arm C wins only exact repeats, do not call it
  learning transfer."
* **F4 — deterioration.** The point estimate of `Δ(C − A)` is **below** `−δ_MI`, and the
  interval's lower bound is also below `−δ_MI`: arm C is actively worse.
* **F5 — the excluded alternatives reappear.** Any of A1–A5 (§1.3) fails its exclusion test:
  the advantage disappears when replay is removed, when preexisting procedures are removed, when
  lexical overlap is controlled, when the freeze is honoured, or when the budgets are equal.
* **F6 — the instrument disagrees.** The oracle/world cross-check (§4.2) fails. The run is VOID,
  not scored — and the mismatch is a result.

**A falsified hypothesis is not a reason to optimise the candidate further.** It goes to the
next research decision (`docs-v2/engineering/AGENT-COMMISSION-COGNITIVE-PROGRAM.md:82`).

### 8.2 What counts as INCONCLUSIVE

**INCONCLUSIVE** if the interval is compatible with no benefit *and* the design cannot exclude
it — which is the **expected** verdict of a pilot of this size (P3, §1.4):

* **I1 — the interval straddles zero.** The bootstrap interval for `Δ(C − A)` contains 0, and
  its lower bound is not below `−δ_MI`.
* **I2 — the interval straddles `δ_MI`.** The interval contains 0 **and** contains `δ_MI`:
  neither "no benefit" nor "meaningful benefit" can be excluded.
* **I3 — insufficient discordance.** Fewer than 6 discordant pairs, so the exact McNemar test
  could not have reached `p ≤ 0.05` in either direction (P3). This is reported as a **power
  statement**, not as a null result.
* **I4 — a pre-registered condition of the run was not met** (freeze invalid, a control
  failed, the instrument disagreed, an arm was inapplicable for a non-benchmark reason).

**INCONCLUSIVE is a legitimate scientific outcome and must be reported as such.** It is not a
soft REJECTED and not a soft CONFIRMED.

### 8.3 What triggers a pivot

* **PIVOT-1 — two substantive unsuccessful attempts with no new information.** Independent
  critique and a different hypothesis are then required
  (`docs-v2/engineering/AGENT-COMMISSION-COGNITIVE-PROGRAM.md:82`;
  `docs-v2/governance/COGNITIVE-DEVELOPMENT-PROGRAM.md:85`).
* **PIVOT-2 — the domain cannot carry the claim.** If the domain-B result is exactly as
  predicted by **P1** (correctness flat across arms) **and** no cost advantage is measurable,
  then domain B cannot test transfer, and the protocol pivots to the owner decision **D1**
  option B (§10.1) — a widened hypothesis space — rather than to "try harder" on the lamp
  world.
* **PIVOT-3 — the exclusion test fails.** If A1–A3 fail (§1.3), the hypothesis is not "the
  effect is small"; it is "the effect is not what it was claimed to be", and the next experiment
  must be designed to remove that explanation.
* **PIVOT-4 — the budget cannot buy a detectable effect.** If `δ_MI` cannot be reached at the
  affordable sample size, the owner is asked to choose between a larger sealed set and a
  smaller `δ_MI` — **before** the data are seen (§10.4).

### 8.4 The rule that a failed run is retained

* Every run produces `CRJ-01-EVIDENCE.json` with **one row per (case, arm)**, including the
  rows that crashed, were refused, were inapplicable, or timed out
  (`CRJ-01-ACCEPTANCE.md:71`).
* A run that crashes, times out, or is aborted is **retained as a result** with its error text
  and the state at the crash. It is not re-run to obtain a nicer number.
* Re-running is permitted **only** with a recorded reason, and only against a **new dataset
  version** or a **fixed, documented defect in the harness** — never against a changed
  threshold. Every re-run is a new row set, not an overwrite.
* A threshold, a denominator, a case list, or a metric definition is **never** changed after a
  result is seen. A change requires a new protocol version, a new freeze, and a re-run.
* The result report names the negative outcomes, the observed deficiencies and the decision
  (`CRJ-01-ACCEPTANCE.md:72`).

---

## 9. The resource budget

### 9.1 The standing constraints (from the normative documents)

| constraint | value | source |
|---|---|---|
| heavy Gradle JVMs | **one at a time** | `docs-v2/engineering/CRJ-01-EXECUTION-PLAN.md:80`; `docs-v2/engineering/AGENT-COMMISSION-COGNITIVE-PROGRAM.md:47` |
| concurrent deep-research workers | **≤ 4** | `docs-v2/engineering/AGENT-COMMISSION-COGNITIVE-PROGRAM.md:47`; `AGENTS.md` META-R4 |
| Gradle daemon heap | `-Xmx4g` | `gradle.properties:2` |
| CI, documentation-only change | **0 minutes** — the FAST lane's required check is satisfied by an empty module matrix (OD-066) | `docs-v2/engineering/CRJ-01-EXECUTION-PLAN.md:81`; `docs-v2/governance/DECISION-REGISTER.md` (OD-066) |
| CI, release lane | not required for CRJ-01; it is a separate owner decision | `docs-v2/engineering/COGNITIVE-MISSION-RECONCILIATION.md:28` |

### 9.2 The proposed CRJ-01 budget (all figures `PROPOSED`)

| item | budget | note |
|---|---|---|
| **Drafting this protocol** | 1 read-only agent round, no CI, no Gradle | already spent; `CRJ-01-EXECUTION-PLAN.md:34` |
| **C1 freeze** | 1 case-author round + 1 independent-reviewer round + 1 coordinator round; 0 CI; **0** heavy Gradle JVMs (the builder is a JUnit test → 1 targeted JVM) | the builder runs as a test, so it costs one targeted JVM, not a full suite |
| **C2 execution (A/B/C × 2 domains × sealed split)** | **≤ 90 min** wall time; **≤ 2** heavy `matrix-core` JVMs, one at a time; peak RAM ≤ the daemon's `-Xmx4g` (`gradle.properties:2`) | `PROPOSED`. The CI-minute cost is **0** (the run is local); the FAST lane would additionally run the new targeted tests — `PROPOSED ≤ 10 min` |
| **Domain-A wall time** | `PROPOSED`: the measured baseline retrieval cost is 65 ms/case on the C2A set (`docs-v2/engineering/C2A-HELDOUT-EVALUATION.json`, `/metrics/BASELINE-PATH/meanRetrievalMillis = 65`) and 9 ms/case for the retriever alone (`/metrics/BASELINE-RETRIEVER/meanRetrievalMillis = 9`); the corpus import of 7 documents cost 50 ms (`/extraction/extractionMillis = 50`) | these are **measurements of the prior system on the prior set**, cited as the *scale* of the budget, not as a prediction of CRJ-01 |
| **Domain-B wall time** | `PROPOSED`: 36 episodes × ≤ 4 probes; the episode runner is deterministic and in-process | the step bound is `DEFAULT_STEP_LIMIT = 4` (`.../learning/env/MicroWorldEpisodeRunner.java:97`) |
| **CI** | 0 minutes for the protocol; `PROPOSED ≤ 10 min` FAST-lane minutes if the builder/oracle tests are added | the FAST lane's scope job alone has `timeout-minutes: 5` (`.github/workflows/fast-pr.yml:64`); the weekly research lane is `timeout-minutes: 60` (`.github/workflows/research.yml:19`) and is **not** triggered by this work |
| **Native image / GPU / FPGA** | **out of scope.** The native-image workflow is a separate, inherited red lane; CRJ-01 does not make it a prerequisite | `docs-v2/governance/COGNITIVE-DEVELOPMENT-PROGRAM.md:62`; `docs-v2/engineering/COGNITIVE-MISSION-RECONCILIATION.md:28` |
| **Network** | none. The corpora are pre-cached and versioned; no request leaves the machine | `.../knowledge/acquisition/AcquisitionCycle.java:45-47` |

### 9.3 Metrics that G5 requires and that are not instrumented at the baseline

Per `CRJ-01-ACCEPTANCE.md:65` (G5) and `AGENT-COMMISSION-COGNITIVE-PROGRAM.md:58`, the
following are **`NOT_INSTRUMENTED`** at baseline `b7bd1be4` and are recorded as such rather
than estimated:

| metric | status at `b7bd1be4` | note |
|---|---|---|
| per-task CPU time per arm | `NOT_INSTRUMENTED` | no per-arm CPU accounting exists on the report route |
| peak RSS per arm | `NOT_INSTRUMENTED` | — |
| per-task end-to-end latency of the *packaged* app on the report route | `NOT_INSTRUMENTED` | the measured 65 ms is retrieval only, on a prior set |
| number of LLM/model calls per case | `NOT_INSTRUMENTED` | no LLM call enters the current asking path; the rule is `no LLM imports` (CI check, `.github/workflows/ci.yml:49`) |
| learner-quality metric at the task level in a packaged run | `NOT_INSTRUMENTED` | the microworld's measured metrics exist at the episode level (`EpisodeReport`), not at the packaged-request level |
| CI minutes actually consumed by CRJ-01 | `NOT_INSTRUMENTED` until a run happens | the *plan* is 0 (documentation-only, OD-066) |

---

## 10. The owner decisions this protocol depends on

**THE DECISIONS ARE TAKEN (2026-11-20).** The owner answered D1–D4 and the CRJ.1.2 build item;
the answers are recorded in `docs-v2/governance/DECISION-REGISTER.md` as **OD-081** and
**OD-082**, and they are fixed in writing **before** the first arm runs, as
`CRJ-01-ACCEPTANCE.md:61` requires. The options and the grounded recommendation are kept below
because the register routes to them — a reader must be able to see what was chosen **and what
was not**.

| the decision | the owner's answer | the protocol's recommendation | the consequence that is now binding |
|---|---|---|---|
| **D1 — the domains** | **A** — a new third corpus (domain A) + the lamp world as it exists (domain B) | A | domain B tests **cost and retention**, not semantic generalisation — its input domain is 4 |
| **D2 — the arms** | **B** — arm C reads promoted procedures **and** learned claims | A | the learned-claim path has **no** promotion-and-validation contract with a held-out-case rule ⇒ ⇒ ⇒ that contract is a **build item** (OD-082), not an implementation detail |
| **D3 — the oracle** | **A** — a source-span oracle + an independently written truth table | A | the independence is **testable**: the truth table is cross-checked on all four assignments before the run, and a mismatch voids the run |
| **D4 — δ_MI** | **A** — **2** additional sealed cases over both A and B | A | at n=18 the verdict of the pilot is expected to be **INCONCLUSIVE**; the interval is the primary outcome |
| **CRJ.1.2 — what arm C builds** | **B** — a new `Procedure` family for domain A, beside the existing mechanism | A (reuse) | a larger surface, built **before** any measurement says it is needed — the held-out-case rule must be frozen with the sealed split, and the evaluation traffic stays quarantined |
| **D5 — the freeze** | the contract decides it: a **third-party reviewer** freezes the sealed split; the hash record and the roles are published **before** the first arm run | A | **still OPEN as a procedure**: it needs a reviewer who is neither the case author nor a solver author — the freeze cannot be performed by the coordinator (the contract says so) |

Each decision lists the options and a **grounded recommendation**. All five are owner-level
(`docs-v2/engineering/CRJ-01-EXECUTION-PLAN.md:70`).

### 10.1 D1 — The domains

| option | description | grounded assessment |
|---|---|---|
| **A** (recommended) | Domain A = a **new third corpus** (new licensed technical documents, new manifest, imported through the production acquisition path) with a novel sealed set; Domain B = the **lamp world as it exists**, run as a cross-episode **competence-retention and efficiency-transfer** domain | Both existing plate-tectonics corpora are exhausted by dev-inspected sets (§2.1.2), so a novel domain-A set *requires* new material. The micro-world exists and is fully specified (§2.2.1), so it is usable without inventing anything — but the protocol must state, as it does, that it tests **cost and retention, not semantic generalisation** (§2.2.2) |
| **B** | as A, **plus** a widened micro-world (new `MicroWorldEnvironment` + a hypothesis space over *n* variables) so that domain B can test transfer to a *different rule* | The only way to get a semantic-generalisation test in domain B. It is a **CRJ.2.x build item**, an owner-authorized boundary, and it is **not** required for C1. It should be sequenced **after** the lamp-world result, because P1/P2 already predict what the lamp world will show |
| **C** | domain A from the **photosynthesis knowledge pack** + its adversarial teach-back set | **Rejected.** The pack is not a corpus (no manifest, no production import path) and its `transfer` phase (`adv-transfer-1..3`) has been read during development (§2.1.1, §2.1.2) — it is a regression instrument, not a held-out set |

### 10.2 D2 — The arms

| option | description | grounded assessment |
|---|---|---|
| **A** (recommended) | A = `report.journey.reference.v1` / `MicroWorldReferenceMethod`; B = `report.journey.replay.v1` + the same-episode re-run; C = A + `ProcedureStore`-promoted procedures + `MicroWorldSettledModelMethod`, with the sealed split excluded from all validation | This is the literal implementation of the contract's arm table (`CRJ-01-ACCEPTANCE.md:17-23`) using classes that exist today (§3). B's replay subset is scored separately, as the contract demands |
| **B** | as A, but arm C additionally reads learned **claims** (not only procedures) | Defensible, but the learned-claim path has no existing promotion-and-validation contract with a held-out-case rule; `ProcedureValidator` has one (`.../ProcedureValidator.java:35`). Choosing B defers a clean design to an implementation detail |
| **C** | as A, with the arms run as three separate applications | Rejected: it breaks the "identical inputs, permission and budget" comparability rule (§3.4) at the root |

### 10.3 D3 — The oracle

| option | description | grounded assessment |
|---|---|---|
| **A** (recommended) | Domain A: a **source-span oracle** that re-reads the corpus bytes and checks byte-identity with the gold proposition, never touching the retriever. Domain B: an **independent truth table**, cross-checked against the world on all 4 assignments before the run, with a mismatch voiding the run | Both satisfy "a separately implemented oracle is required" (`CRJ-01-ACCEPTANCE.md:30`). The cross-check makes the independence *testable* rather than asserted, and §7.6 makes the oracle falsifiable |
| **B** | domain A: reuse the dataset builder's own verification (it already proves the verbatim property) | **Rejected.** The builder's verification is a *dataset* property; a solver's answer still has to be scored, and reusing the builder's code path would put the oracle inside the tool that produced the ground truth |
| **C** | domain B: ask the environment (`LampEnvironment.probe`) | **Rejected.** `probe` "performs the hidden rule once and reports the result"
(`.../microworld/MicroWorldEnvironment.java:20-29`); an oracle built on it is the solver's own instrument |

### 10.4 D4 — The minimum meaningful improvement `δ_MI`

This is the decision the contract reserves for the owner
(`CRJ-01-ACCEPTANCE.md:61`; `docs-v2/engineering/CRJ-01-EXECUTION-PLAN.md:43`).

| option | value | grounded assessment |
|---|---|---|
| **A** | **≥ 2 additional sealed cases solved by arm C over both A and B** (i.e. `δ_MI = 2` of 18 comparable cases per domain ≈ 11 pp) | **Recommended.** Grounded in a measured artefact: the largest single improvement measured anywhere on the comparable 25-case measure was **one case** — the `QuestionDemand` future-relative fix moved the sealed measure from 17/25 to 18/25 (`docs-v2/engineering/EPISTEMIC-EXECUTION-PLAN.md:88`). A one-case difference is therefore **within the range of a single known defect fix**, so it cannot be called a competence gain. Two cases is the smallest increment strictly larger than any single historical fix observed |
| **B** | ≥ 1 additional case | Rejected: a one-case delta is exactly what a single bug fix produces in this system, so it does not distinguish competence from repair |
| **C** | ≥ 3 additional cases | More conservative; costs power (see below) without new grounding. Available if the owner prefers a stricter bar |
| **D** | a relative/effect-size threshold (e.g. Cohen's *d* on the paired differences) | Not usable here: with `n = 18` and a binary paired metric, the paired differences are almost all zero, and a standardised effect size over a degenerate variance is not informative. Rejected on statistical grounds |

**The honest consequence of every option, which the owner must accept explicitly:** at
`n = 18` paired cases per domain, the exact McNemar test reaches `p ≤ 0.05` only with **6 of 6**
discordant pairs in one direction (P3, §1.4). A `δ_MI = 2` improvement therefore **will not
produce a significant p-value**; it will produce an interval. The verdict rule of §8 is written
for exactly that situation, and the expected honest verdict of a pilot is **INCONCLUSIVE**.
The owner chooses between:

* accepting a design whose primary outcome is the **interval** and whose typical verdict is
  INCONCLUSIVE (option A at `n = 18`), or
* enlarging the sealed split so that the discordance can be large enough — which raises the
  cost and may exceed the contract's 30–50 band
  (`CRJ-01-ACCEPTANCE.md:30`) and would need an explicit contract amendment.

**The value of `δ_MI` must be fixed in this decision, in writing, before the first arm runs.**
A threshold chosen after seeing the results is prohibited by the contract
(`CRJ-01-ACCEPTANCE.md:61`) and by `CRJ-01-EXECUTION-PLAN.md:43`.

### 10.5 D5 — The freeze

| option | description | grounded assessment |
|---|---|---|
| **A** (recommended) | A **third-party reviewer** (neither the case author nor a solver author) freezes the sealed split; the hash record and the roles are published **before** the first arm run; any change voids the freeze and forces a new dataset version and a full re-run | This is the contract's own requirement (`CRJ-01-ACCEPTANCE.md:30`) and the execution plan's acceptance test (`CRJ-01-EXECUTION-PLAN.md:44`). It is the only option that actually excludes **A4 (selection bias)** |
| **B** | the coordinator freezes the split | Rejected: the coordinator owns the arms' execution and the final report, so this does not exclude selection bias — it only relocates it |
| **C** | the case author freezes the split | Rejected: the contract says "use an independent reviewer to freeze the test cases"
  (`CRJ-01-ACCEPTANCE.md:30`) |

**Additional owner item folded into D5 (Level-2/3 boundary, `CRJ.1.2`):** the owner names
**which existing classes arm C reuses** and **the smallest thing to build** — concretely,
whether arm C needs a new `Procedure` family for domain A or can reuse the portfolio's
existing procedure mechanism (`.../cognitive/algorithms/ProcedureStore.java:72-77`).

---

## Appendix A — Artefact index

Every artefact this protocol cites, with what it is cited for. Nothing else is used as
evidence.

**Normative and programme documents**
`docs-v2/engineering/CRJ-01-ACCEPTANCE.md` · `docs-v2/governance/COGNITIVE-DEVELOPMENT-PROGRAM.md`
· `docs-v2/engineering/AGENT-COMMISSION-COGNITIVE-PROGRAM.md`
· `docs-v2/engineering/COGNITIVE-MISSION-RECONCILIATION.md`
· `docs-v2/engineering/CRJ-01-EXECUTION-PLAN.md` · `AGENTS.md` · `gradle.properties`

**Retained measurements (prior, dev-inspected — regression only)**
`docs-v2/engineering/C2A-HELDOUT-EVALUATION.json` (18/25 comparable; the seven failed cases;
the denominators; the retrieval latencies) ·
`docs-v2/engineering/EPISTEMIC-HELDOUT-SET.json` (16 cases, 9 classes, freeze scope and digest) ·
`docs-v2/engineering/EPISTEMIC-HELDOUT-SET.md` ·
`docs-v2/engineering/EPISTEMIC-EXECUTION-PLAN.md` (the 17/25 → 18/25 single-fix measurement) ·
`docs-v2/engineering/EPISTEMIC-FINAL-REPORT.md` · `docs-v2/engineering/EPISTEMIC-LEARNING-ISOLATION.md`

**Datasets**
`matrix-core/src/main/resources/epistemic-eval/cases.json` (29 cases / 25 texts compared) ·
`docs-v2/engineering/C2A-HELDOUT-SET.json` (38 cases) · `docs-v2/engineering/EPISTEMIC-HELDOUT-SET.json` (16 cases)

**Corpora and packages**
`matrix-core/src/main/resources/corpus/corpus-manifest.json` ·
`matrix-core/src/main/resources/corpus/plate-tectonics/` ·
`matrix-core/src/main/resources/research-pool/plate-tectonics/` ·
`matrix-core/src/main/resources/knowledge/photosynthesis.pack.json` ·
`matrix-core/src/main/resources/knowledge/photosynthesis.assessment.json` ·
`matrix-core/src/main/resources/knowledge/photosynthesis.adversarial.json` ·
`matrix-core/src/main/resources/knowledge/logic.pack.json`

**Source classes (domain B)** `io/matrix/cognitive/microworld/{LampEnvironment, MicroWorldEnvironment, MicroWorldSession, HypothesisSpace, WorldInput, Observation, PredictionError}`
· `io/matrix/learning/env/{MicroWorldEpisodeRunner, EpisodeReport, EpisodeComparison, ProbePolicy}`
· `io/matrix/cognitive/{MicroWorldReferenceMethod, MicroWorldSettledModelMethod, MicroWorldComputation, ExploratoryActionSampler, CognitiveService, CognitiveKernel, KernelSelector}`
· `io/matrix/cognitive/algorithms/{Procedure, ProcedureStore, ProcedureValidator, ProcedureMethod, ProcedureBacked}`

**Source classes (domain A and the action)** `io/matrix/knowledge/{ArtifactReportResource, LearningWorkbenchResource, PremiseSemantics, PremiseChecker, KnowledgeWorkbench, ClaimConflictDetector}`
· `io/matrix/knowledge/acquisition/{AcquisitionCycle, ClasspathCorpus, DocumentImporter}`
· `io/matrix/actions/{ArtifactActionService, ArtifactWriter}` ·
`io/matrix/cognitive/report/{ReportJourney, ReportJourneyMethod, ReportJourneyTrace, ArtifactObserver, ArtifactBuilder→ReportBuilder, WorkspaceFingerprint}`

**Proposed classes that do not exist at `b7bd1be4`**
`matrix-core/src/test/java/io/matrix/crj/evidence/SourceSpanOracle.java` ·
`matrix-core/src/test/java/io/matrix/crj/microworld/LampTruthOracle.java` ·
the CRJ-01 case builder and the new corpus manifest.

## Appendix B — The `NOT_INSTRUMENTED` register

Everything in this protocol that is *not* measured at baseline `b7bd1be4`:

1. per-arm CPU time, per-arm peak RSS, per-arm packaged end-to-end latency;
2. the LLM/model-call count per case (no LLM is on the current asking path, so the count is
   zero by construction rather than measured — it is recorded as **not applicable**, not as 0);
3. the number of retrieved candidates per case;
4. the cost of a **widened** micro-world hypothesis space (option B of D1) — design, size and
   build cost;
5. the achievable precision of any domain-A metric on a **new** corpus — the numbers in §6
   come from the prior sets and the prior corpora;
6. the CI minutes CRJ-01 will actually consume;
7. the served-path reachability of the micro-world (assigned to `CRJ.0.1`/`CRJ.0.2`);
8. the oracle classes' behaviour (they do not exist at the baseline);
9. the sealed split's case count, class distribution and difficulty profile (it does not exist);
10. the budget value that replaces `ComputeBudget.unbounded()`.

---

**End of the draft.** This document is `PROPOSED / NOT FROZEN`. It becomes the CRJ-01 contract
only after the owner's approval of §10's five decisions **and** an independent reviewer's
freeze of the sealed test set with the hashes of §5.5 recorded. Until then it is neither a
plan nor a promise, and it evidences nothing.
