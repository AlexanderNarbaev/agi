# CONCEPT-TO-ACTION — the final report

**Status:** `MEASURED` · **Date:** 2026-11-15 · **Branch:** `feature/concept-to-action`
**Base:** `origin/develop` @ `429eba27` · **Implementation PR:** #60 (squash `dfd24b44`)
**Release PR:** #61 (squash `26c549c4`)

**What this wave asked.** Does the sequence the system claims to perform — *a user request →
evidence → a justified decision → an authorized real action → a verified effect* — actually work
on the serving path? Three obligations came with the question: make the demonstrated bounded
action reachable through **one real product API**; improve evidence retrieval through
**measured, provenance-preserving concept identity and relations** rather than by relaxing the
epistemic thresholds; and establish **stronger independent evaluation** and **meaningful
real-world evidence** for the learning subsystem.

**What the wave delivered.** One real route, verified over real HTTP against the packaged
server. Two negative results, reproducible and kept: **no retrieval candidate is promoted**, and
**the representation is not the learning subsystem's observed bottleneck**. One new measurement
that changes what the learning subsystem's numbers mean: the active production window now
carries both classes for the first time, and the production induction's own fidelity falls from
a meaningless 1.00 to a measured 0.50.

**What this wave did not deliver.** A promoted retrieval candidate; a decision on the fold
(the owner's, and now with a measured reason); a production-ready packaging of the SQLite store
(an inherited defect, §G); a positive control on the learning side (§J, task C2A.4).

---

## A. The Git state and the integration

| | |
|---|---|
| the starting state | `origin/main` = `724ea537`, `origin/develop` = `429eba27`, `main` contained in `develop`'s history, both trees equal (`6051348d`) |
| the implementation branch | `feature/concept-to-action`, cut from `origin/develop` @ `429eba27` |
| the implementation PR | **#60** (`feature/concept-to-action` → `develop`), squash **`dfd24b44`** |
| the release PR | **#61** (`develop` → `main`), squash **`26c549c4`**, merged 2026-11-15 |
| the synchronisation | `main` merged back into `develop` — **`1194f918`** |
| the trees after the synchronisation | `origin/main` and `origin/develop` held the **same tree** `1e70749981fcc9b3b00a93e4ac6a5eca770efd07` (verified: `git diff --stat origin/main origin/develop` → empty) |
| the final state | `origin/develop` = **`42919371`** — the commit that carries the state and the registers; this report's own restructuring follows it, and the snapshot convention keeps the two one commit apart. `origin/main` = **`26c549c4`**. The lag the state validator measures is exactly one commit, which is the convention it allows (`scripts/validate-project-state.py --strict` → 0 defects) |
| the commits | 16 commits on the branch, each one significant: the execution plan, the writer's hardening, the window diagnosis, the coverage baseline, the action and the route, the demonstration, the frozen candidates, the held-out set, the learning evidence, the review fixes, the corrected metrics, the refreshed artefacts, the concurrency fix, the road map, the report |

**The merge method, and its disclosure.** Both merges were squash merges made with the
ruleset's **administrator bypass** (`gh pr merge --squash --admin`). The reason is structural
and is not a circumvention of the review: the ruleset *"Protected main"* requires **one
approving review**, and the repository's sole collaborator is the author of this work, so an
approval cannot come from anywhere. The ruleset has **no required status checks**, so **no
failed required check was bypassed**. What was verified before each merge:

- the independent reviews (§G.4) are documented, and every material finding is fixed;
- the substantive checks are green: **Build & Test (Java)** (two runs on the release PR, 21m38s
  and 21m22s), **Quality gates**, **Web UI Tests**, **CONSTITUTION compliance**, **Compile all
  T-01 modules**, **Model-check TLA+ specs**;
- **no frozen file was modified** (`CONSTITUTION.md`, `AGENTS.md`, `matrix-core/.../ethics/frozen/`,
  `matrix-core/src/main/resources/avro/**`, `.github/workflows/**` — verified against the diff);
- **no check was disabled or falsified** — the negative control's verdict was tightened so that
  only a real test failure counts as a kill;
- the inherited failures are named in §G.5 and are **not** represented as success.

**The actual CI status.** `Build & Test (Java)`, `Quality gates`, `Web UI Tests`,
`CONSTITUTION compliance check`, `Compile all T-01 modules`, `Model-check TLA+ specs` and
`Validate ecosystem docs` **passed**. `Docker Build & Push (web-ui)` **fails** — the repository
holds **zero** secrets, so the publication step cannot authenticate (§G.5). The aggregate `ci`
job is therefore **red**, and this report does not call it green.

---

## B. The product capability

### B.1 The route and its contract

`POST /v1/workbench/workspaces/{id}/reports` — on the existing Quarkus serving application
(chain A, `io.matrix.MatrixApplication`), in the existing workbench resource family.

**The request** (`ArtifactReportResource.ReportRequest`): the question, the report name (a plain
`.md` file name, ≤128 characters), an explicit consent flag, an idempotency key (≤128
characters), and the requested level. **It does not accept** a filesystem path, a command, a
tool name or raw file content.

**The response** (`ArtifactReportResource.ReportResponse`): the task identifier, the action name
and version, the execution disposition, the artifact name (a relative locator, never an
absolute path), the SHA-256 digest, the evidence references, the epistemic disposition, the trace
reference, the limitations and the refusal reasons.

**The call chain**: `ArtifactReportResource.report` → `KnowledgeWorkbench.ask` (the workspace's
own retrieval) → the workspace's own claims and sources → `ArtifactActionService` → the
project's existing bounded action `report.artifact.write` → `ArtifactWriter` (the root lease,
the staged file, the digest, the append-only index). **No new action engine, no new conductor,
no new memory architecture** was introduced.

### B.2 The activation and the security boundary

| the property | how it is enforced |
|---|---|
| **opt-in** | the feature is off unless `matrix.actions.artifact.enabled=true`; the output root has **no default** — an absent configuration is a closed gate (`application.properties`; the root's line is commented out) |
| **local-only** | a network-exposed process refuses the action **even when** the general mutation filter accepted the caller's credential; **and** the configured bind address must be the loopback interface, because the mode is a declaration and the bind is the fact (`ArtifactActionService.gate()`, `StartupGuard.configuredHost()`) |
| **consent** | a missing or false consent is a 403 and writes nothing |
| **the caller chooses almost nothing** | the path, the action identity, the trace and the limits are the server's derivation: the identity is `"c2a.report.v" + VERSION + "." + sha256(workspaceId.length + ":" + workspaceId + subject)[0:24]` |

### B.3 The epistemic separation

The response keeps five statements apart: the action executed successfully (`disposition`); the
artifact exists and matches its digest (`sha256`); the answer is supported by evidence
(`epistemicDisposition`, the cited claims and their statuses, the sources); a proposition is
unresolved (the unmatched terms, the limitations); and the report came from another process
(`traceReference`). **A written report is a record of what the workspace said — not evidence for
the propositions it repeats**, and the report says so in its own text.

### B.4 The real external HTTP demonstration

`scripts/c2a-http-demonstration.py` builds the packaged application, starts the real server on
a port, seeds the corpus through the production route (`POST /v1/learning/workspaces/c2a-demo/seed`),
makes the requests and verifies. **Every check passed**
(`docs-v2/engineering/C2A-HTTP-DEMONSTRATION.json`):

| the check | the result |
|---|---|
| the request | **201**; the artifact is **52 971 bytes**; its SHA-256 `eba139f74ad250acc5b8511b32786d207ab7dd73e119043572d12d915c6909ec` equals the digest the response reported **and** the digest of the file on disk |
| the repeat | **200 `REPLAYED`**, one artifact on disk, the same digest |
| the request without consent | **403**, **no artifact written** |
| four concurrent requests, one identity | one **WRITTEN**, three **REPLAYED**; exactly one `STAGED` and one `COMMITTED` record for the identity |
| after a **restart of the server** | **200 `REPLAYED`**, the same digest, **no new artifact** |

The artefact is committed: `docs-v2/demos/concept-to-action/evidence-report.md`. The
demonstration was re-run after the independent review's fixes and passes again.

### B.5 The operational classification

`docs-v2/architecture/CAPABILITY-REGISTRY.yml` carries the row `C2A-EVIDENCE-TO-ACTION`:
**implementation IMPLEMENTED** (the source exists), **integration INTEGRATED** (it is wired
into the serving application's route table), **verification MEASURED** (the real HTTP
demonstration and the executed tests exist), **operational EXPERIMENTAL** — **not** OPERATIONAL:
it is disabled by default, it has never been observed in a deployment, and its operational axis
can only become ACTIVE when an operator turns it on in a local-only process. The registry's
validator reports **57 capabilities, 0 defects**.

---

## C. Crash and concurrency

### C.1 The guarantee

One writer per root, in this process and on this host. The writer takes a **lease** — a
JVM-wide lock keyed by the canonical root path **plus** an operating-system lock on
`<root>/.artifact-lock` — and it takes it **before** it reads the index, so the identity check
and the index append are one indivisible step with respect to every other writer. A second
process is **refused** (`REFUSED_WRITER_CONFLICT`), never queued. The lease is released on
every path, including every refusal.

### C.2 The six residual risks, and how each is closed

| the risk (from the B3 report) | the closure |
|---|---|
| no synchronization across concurrent writers | the lease (§C.1) |
| the identity check and the index append are not atomic | they are one step under the lease; the writer refuses with `REFUSED_WRITER_CONFLICT` rather than racing |
| the index can grow without limit | the index is bounded in records and bytes; at the bound the writer **fails closed** with an actionable reason ("this root is full — use another root"); the trace is never truncated |
| a forged committed record can deny future writes | a record binds a file name **only when the artifact on disk carries the record's digest**; a forged record reserves nothing and does not exhaust the budget |
| failure windows and I/O paths are not fully exercised | the torn-index, symlink, hard-link, root-replacement, partial-record and index-corruption cases are all tested (13 writer-concurrency tests, 29 writer tests) |
| power-loss durability is not established | the artifact is written to a staged file, `fsync`ed, moved into place, the index is `fsync`ed and the **root directory** is `fsync`ed afterwards |

### C.3 The negative control

`scripts/artifact-writer-negative-control.sh` removes the lease **in place**, runs the
concurrency regression test, and reports the verdict. **The mutation is KILLED** — the
second-process refusal test goes red. The verdict is tied to a **real test failure** (a build
failure prints `BROKEN` and exits non-zero), and the script restores the source file and
verifies its sha256 — no `.bak` is left in the tree.

### C.4 The crash residue

A crash in the middle of an append leaves a fragment that was never a record. Before the next
append the writer resolves the index's tail: a **complete** record that lost its newline is
terminated and **kept** (it is evidence); a **fragment** is removed, and nothing else in the
file is touched. Leaving the fragment in place would put a broken line in the middle of the
log, and every later read would see a structure it cannot trust — the root would refuse every
further write over a fragment that was never evidence. Two regression tests pin it.

### C.5 The residual risks

1. The lease is a **process-level and host-level** exclusion, as the approved ownership model
   says. A multi-host or cluster-wide writer is **not** supported and was not attempted.
2. The index's bound is per root; a full root refuses every further write until the operator
   changes the root.
3. The writer's refusals carry the disposition and a reason, and **no path**: every message
   that leaves the process is redacted (`<output-root>`, `<path>`), which the route's own test
   asserts for a refusal and not only for a success.
4. The concurrent append in the **cognitive** store was a separate check-then-act race, found
   by the CI (a second writer took the position between the check and the insert). It is fixed
   — the append now resolves the position against what the database holds — and the regression
   test is deterministic: without the fix it fails in **5 of 5** runs, with the fix it passes.

---

## D. The concept coverage

Measured **before** any enrichment was proposed
(`docs-v2/engineering/C2A-CONCEPT-COVERAGE-BASELINE.md`), from the production importer and store:

| the measure | the value |
|---|---|
| the distinct concepts | **326** |
| the claims | **348** |
| the concepts with a **usable** definition | **0** — the importer writes `definition = null` for every document-derived concept (`DocumentImporter:227-228`) |
| the concepts without a definition | **164** (the seed scope's 162 + the pool's) |
| the persisted concept→concept relations | **0** — `Representation.prerequisiteConceptIds` is the narrowest existing persisted channel and it is always empty; `EvidenceRelation` is a typed vocabulary of 7 values that nothing writes; `KnowledgeGraphStore.Relation(fromId,toId,predicate,weight)` exists and is discarded |
| the gold entries with no lexical match at the floor 0.0 | 11 of 27 (9 propositions) — A-05, A-06, M-01, M-03, P-01, P-02 and others |
| the ambiguous or broken concept names | **61 of 326 (19 %)** end in a dangling function word ("Convergence can", "Magmas that") — **a band, not a number**: the word list behind the figure was never published, and the 2026-11-15 reconstruction gives 61 (18.7 %) on a 120-word list and 75 (23.0 %) on a wider one (`EPISTEMIC-CONCEPT-IDENTITY.md`); the root cause is measured: the name is the claim's subject phrase and the id is its digest. **9** are a bare function word (It, There, This, To); the three largest concepts of the seed corpus are the pronouns *This* (10 claims), *It* (6) and *There* (4) |

**The provenance model.** Every candidate definition and relation carries its originating source
id, the source file and its sha256, the exact span offsets, the unit and precision, the
extraction method, the evidentiary status, and the supporting claim id. The extraction reads the
corpus bytes and verifies the spans char-for-byte. **SUPPORTED is a read** of the claim's own
status (`EpistemicPolicy.CITABLE_STATUSES`), never an assertion. **No relation is built from
co-occurrence alone** — a trigger phrase plus a name on each side are required, with a passive
guard, a longest-match tie-break and a function-word filter. The extraction is deterministic:
the same input gives the same 162/7/116 artefacts across two runs.

**The implementation scope.** Research-only, under `matrix-core/src/test/java/io/matrix/knowledge/concept/`
— `SourceGroundedEnrichment`, `EnrichmentCandidates`, `RetrievalExperiment`,
`ConceptEnrichmentExperimentTest`, `HeldOutEvaluationTest`. **No production file was changed by
the experiment**, and no candidate was promoted (§E).

---

## E. The retrieval experiment

### E.1 The baseline

The official evaluation is reproduced: **18 of 29**, with the identical eleven failures — the
same numbers the EVID-BASE wave measured, re-derived independently by the wave's own harness
(`docs-v2/engineering/C2A-CONCEPT-ENRICHMENT-EXPERIMENT.md`).

### E.2 The hypotheses and the candidates

| the candidate | the hypothesis | the scale it actually touched |
|---|---|---|
| **H1** | source-grounded definitions improve the retrieval | 120 concepts / 141 claims (the seed scope yields 120 definitions under the tightened rule, against 162 under the loose one) |
| **H2** | typed concept relations enable retrieval without lexical overlap | **13 of 326 concepts (4.0 %)**, 7 relations — a near no-op |
| **H3** | a constrained query-aware expansion is sufficient | no corpus change; one extra question term |
| **H4** | retrieval is **not** the only bottleneck | the eleven failures are attributed per gold (§E.5) |

### E.3 The metrics, with their denominators

The development measurement, under the second freeze's definitions (freeze **v1.1**, sha256
`36565590…f7c`; the first freeze **v1.0**, sha256 `14251fbc…08cf4`, is preserved with its
numbers in the report's §0):

| row | passed | R@1 | R@10 | R@20 | MRR | micro R@10 | supported | unsupported | abstention | unsupported / 20 ask | top-1 ≠ gold |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `BASELINE-PATH` | 18/29 | .2024 | .3690 | .5952 | **.5078** | .3000 | **6/14 = .4286** | **8/14 = .5714** | 1.0000 | .4000 | **8/14** |
| `BASELINE-RETRIEVER` | 8/20 | .2024 | .3690 | .5952 | .5078 | .3000 | 6/14 | 8/14 | .3333 | .6000 | 8/14 |
| `H1-definition-enrichment` | 8/20 | .2381 | .3690 | .5952 | .5422 | .3000 | 6/14 | 8/14 | .3333 | .6000 | 7/14 |
| `H2-relation-expansion` | 8/20 | .2024 | .3690 | .5952 | .5075 | .3000 | 6/14 | 8/14 | .3333 | .6000 | 8/14 |
| `H3-query-lexical-expansion` | 5/20 | .1429 | **.4048** | .5476 | .4395 | **.3250** | **3/14 = .2143** | **11/14 = .7857** | .3333 | **.7500** | 10/14 |
| `H1+H2` | 8/20 | .2024 | .3690 | .5595 | .4938 | .3000 | 6/14 | 8/14 | .3333 | .6000 | 8/14 |

The denominators are: the recall and the MRR are the **mean over the 14 answerable cases**; the
micro recall is the share over **every gold proposition of those 14**; the supported, unsupported
and answered-with-no-gold are over the **14**; the abstention accuracy is over the **6
unanswerable**; the unsupported rate over all ask cases is over the **20**; the passed column is
over the **29** for the production path and over the **20** for the retriever-level rows. The
MRR is the **topmost** gold (the minimum rank over the present golds) with the misses in the
denominator. The supported answer honours **the case's own gold condition** ("all of
`requiredPropositions`, or at least one of `requiredPropositionsAny`").

### E.4 The held-out set and its result

A **38-case** set, built independently and source-verified before any candidate was measured:
**19 POSITIVE / 6 NEGATIVE / 6 UNSUPPORTED / 4 AMBIGUOUS / 3 CONTRADICTORY**, 59 distinct gold
claims, 61 references, 7 pool documents (all of them covered). Every gold proposition is
**verbatim** in its own source document; every negative case's content terms are absent from all
seven documents; no held-out question shares a normalised 4-token sequence with any of the 25
development texts.

The held-out measurement (denominator 25 = 19 answerable + 6 unanswerable): the baseline
retriever 18/25, MRR .8600, supported 12/19 = .6316, unsupported 7/19 = .3684, abstention
1.0000; **H1** 18/25, **H2** 18/25, **H1+H2** 18/25, **H3** 17/25 (MRR .7818, unsupported
.4211, top-1 ≠ gold 6/19). **No candidate newly passes on the held-out set.** H3 newly fails
H-P19. The absolute levels are not comparable with the development set (the held-out baseline is
stronger: R@10 .8158 against .3690), so the headroom is smaller, not larger — the set can
**reject** a candidate, which is what it was built for.

**False positives and abstention.** No candidate loses an unanswerable case: the abstention
accuracy is **1.0000** in every held-out row. H3 raises the unsupported-answer rate (.4211
against .3684) and loses the cited gold of H-P06 and H-P19 — the added query terms dilute the
coverage. The corpus-side candidates lose nothing.

**Contradictory and ambiguous behaviour.** In **every** row the system answers and does not
report the conflict: the ambiguous cases represent every reading in **0 of 4**, and the
contradictory cases carry both conflicting claims in **2 of 3** and never say they conflict.

### E.5 The adoption decision

**Adopted:** nothing. **Rejected:** H1, H2, H3 and H1+H2 — none recovers a single failing
supported-answer case on either set, H3 loses one, and the promotion criteria (≥2 recovered,
6/6 abstention preserved, no new unsupported answers, a meaningful change in recall or ranking)
are not met by any of them. **Deferred:** the production retrieval is unchanged, and the
production's own default (floor 0.25) is unchanged — **no epistemic threshold was relaxed**.

**The failures are now attributed per gold**, which is the wave's most useful scientific result:
**pure floor failures ×2** (A-02, A-04), **mixed ×5** (A-05, A-06, P-01, M-01, M-03 — each
carries a gold that no floor can reach, because the proposition is not in the candidate list at
all), **lexical miss ×1** (P-02), **premise verdict ×1** (F-02), **revision logic ×2** (R-04,
R-05). The floor band is **not** where the residual lives.

---

## F. The real learning evidence

### F.1 The dataset and its provenance

**59 newly executed real observations** were produced by the current build's own conductor
(`TrueMindCycle`) and written into the machine's **real** episodic log by the production write
path (`EpisodicLog.append` → `PromotionGate`). The log grew from **108** to **167** records
(64+/44- → 108+/59-). Of the 59 new episodes, **44 were promoted** (positive) and **15 were
refused** by the production gate: `REFUSAL` 12, `EVAL_PROBE` 3, `DEFAULTED_CONFIDENCE` 3,
`FICTIONAL` 1, `NOT_PROMOTED` 1.

**The provenance is separated and never mixed:** the historical records are `HISTORICAL`, the
new ones `NEWLY_EXECUTED_REAL_OBSERVATION`, and the synthetic fixture `CONTROLLED_SYNTHETIC` —
the last one is **never** relabelled as real. The harness cross-checks every feature against
the production encoder, byte for byte.

### F.2 The classes, the distinctness and the window

| | before | after |
|---|---|---|
| the active production window (the last 20 entries, `RealSleepScheduler:237-240`) | **20 positive / 0 negative**, 3 distinct inputs | **10 positive / 10 negative**, 20 distinct features |
| the production induction's own fidelity | **1.00** — a one-class window, which measured nothing | **0.50** — chance, on a window that does carry both classes |

**The collection is OPT-IN** (`-Dmatrix.c2a.collect=true`): it writes into the machine's real
runtime log, so it must never happen as a side effect of an ordinary build. This was not a
cosmetic choice — the CI proved it: a test that creates the log silently changes what another
measurement sees, and the second measurement then fails.

### F.3 The representation comparison, on a two-class test partition

Chronological 60/20/20, the test partition 34 = 19 positive / 15 negative, three seeds:

| representation | distinct | cross-label collisions | test accuracy | balanced | train ms | inference |
|---|---|---|---|---|---|---|
| the production fold 256→16 | 74 | 5 | .441 / .588 / .559 | .402 / .540 / .500 | 7.4 / 7.7 / 7.1 | 9386 / 2501 / 2011 µs |
| the informative projection | 17 | 4 | .500 / .500 / .471 | .447 / .447 / .421 | 7.6 / 7.5 / 6.5 | 2204 / 1889 / 1679 µs |
| the discriminative projection | 30 | 2 | .529 / .441 / .529 | .474 / .479 / .474 | 5.1 / 5.5 / 4.9 | 1312 / 1110 / 1029 µs |
| the random projection (control) | 11 | 2 | .441 | .500 | 6.3 / 6.2 / 6.2 | 2053 / 982 / 802 µs |
| the shuffled labels (control) | 74 | 5 | .382 / .441 / .412 | .349 / .423 / .453 | 5.5 / 5.7 / 5.7 | — |
| **1-NN on the unfolded 256-bit (the ceiling)** | 78 | **1** | .529 | **.544** | — | 32 B/episode |

**The representation loss is real but small, and it is NOT the observed bottleneck.** The 256-bit
feature keeps one cross-label collision in 78; the fold adds four. But **even a 1-NN over the
unfolded feature sits at chance** (.544 balanced), while the **same fold reaches .714 on the
controlled synthetic fixture** — the pipeline works when the feature carries the signal. The
label (`confidence >= 0.7`, a function of the retrieval similarity) is **not recoverable from a
token hash**.

### F.4 The leakage analysis

Three partitions are evaluated: the chronological 60/20/20 (E1's rule), the same over the new
observations only, and a grouped chronological partition that keeps the repeated inputs together.
The input-level overlap of the chronological splits is measured and reported. The synthetic
fixture's numbers (144/99/45) are the mechanism's, never the system's.

### F.5 The readiness for the A/B/C decision

**The decision stays OPEN, and the wave's contribution is the reason.** The packet for the owner
(`docs-v2/engineering/C2A-LEARNING-EVIDENCE.md` §4 and
`docs-v2/engineering/C1-LEARNING-WINDOW-DIAGNOSIS.md`) says: **the representation is not where
the residual is**; the missing ingredient is a feature that carries the label, and no
projection of a feature that does not carry it can supply one. The owner's three options stay
**unmentioned and unimplied** — no fold is proposed, `K_MAX` is unchanged, the trainer width is
unchanged, and the production fold is unchanged.

---

## G. The verification

### G.1 The commands and the environment

```bash
export JAVA_HOME=$HOME/jdk25

# the route's tests, the writer's tests, the asking path's concurrency
flock /tmp/gradle-matrix.lock bash -c 'rm -rf matrix-core/build/test-results && \
  ./gradlew :matrix-core:test --tests "io.matrix.knowledge.*" --tests "io.matrix.actions.*" \
  --offline --console=plain'

# the writer's negative control (it mutates a tracked file in place and restores it)
./scripts/artifact-writer-negative-control.sh

# the real HTTP demonstration (it builds the packaged application and starts it)
python3 scripts/c2a-http-demonstration.py

# the retrieval measurement (the frozen candidates, then the held-out set)
flock /tmp/gradle-matrix.lock bash -c 'rm -rf matrix-core/build/test-results && \
  ./gradlew :matrix-core:test --tests "io.matrix.knowledge.concept.*" --offline --console=plain'

# the learning evidence (OPT-IN: it writes into the machine's real episodic log)
./scripts/c2a-collect-learning-evidence.sh

# the learning window's measurement, without a build
python3 scripts/c1-measure-learning-window.py

# the official evaluation (the driver's native library is no longer a manual step, see §G.5)
python3 scripts/w51-epistemic-eval.py

# the gates
./scripts/quality-gate.sh
python3 scripts/validate-project-registry.py
python3 scripts/validate-project-state.py --strict
python3 scripts/docs-link-check.py
```

### G.2 The measured test denominators

| the scope | the result |
|---|---|
| `io.matrix.knowledge.*` + `io.matrix.actions.*` + `io.matrix.cognitive.*` | **178 test classes, 1 242 tests, 0 failures, 0 errors, 0 skipped** (the JUnit XML under `matrix-core/build/test-results/test/`) |
| `io.matrix.knowledge.concept.*` | **17** (the concept experiment) + **5** (the held-out evaluation) tests, 0 failures |
| `:matrix-brain-runtime:test` (the whole module) | **931 tests, 0 failures, 0 errors** |
| the full `matrix-core` suite (local) | **9 606 tests, 1 failure, 19 skipped** — the failure is `Exp324EndToEndForwardPassTest > endToEndForwardPassLatency()`, the owner's **2 ms** threshold measured at a 1.82 ms median **under the load of a full local suite**; the same test **passes in isolation** and **passed in the CI** |
| the CI's `Build & Test (Java)` | **passed** on the implementation PR (16m48s) and on both runs of the release PR (21m38s, 21m22s) |

**The evidence is the retained JUnit XML**, not the console summary.

### G.3 The security and the gates

| the check | the result |
|---|---|
| the quality gate | **PASS: 271 candidates, at or below the accepted baseline of 271; 0 other violations** |
| the registry validator | **57 capabilities, 24 fields per record, 0 defects** |
| the state validator (`--strict`) | **11 canonical documents checked, 0 defects** — the state agrees with the repository |
| the current-state checker | **0 contradictions** |
| the docs link check | **0 strict failures** (193 external links unverified by design) |
| the writer's negative control | **KILLED** — the guard is load-bearing |
| the concurrency fix's negative control | the regression test fails in **5 of 5** runs without the fix |
| the path confinement | the index's symlink, the index's hard link, the lock's symlink and the root replaced by a symlink are all **refused**, and nothing is written outside the root |

### G.4 The two independent reviews

**The product and security review — 2 blockers, 3 major, 6 minor, 2 nit.** All are fixed:
the **ABBA deadlock** in the asking path (the `context()` method is now synchronized on the
workbench, so there is one lock order, with a regression test that drives a question and a
dialogue turn through one shared workbench); the **trace-poisoning** path after a crash
mid-append (§C.4); the **absolute paths in the refusal bodies** (every message is redacted,
with a test for a refusal and not only for a success); the **negative control's verdict** (it
must be a test failure; a build failure prints `BROKEN`) and its leftover `.bak` (the restored
file is sha256-verified); the **concurrency test's topology** (one workbench, as production has
one); and the minors — the root check moved before the lease, `recover()` takes the lease, the
identity conflict is named as a name conflict, the gate requires a loopback bind, the success
body explains a trace-incomplete write, the stale javadoc is gone, the budget refusal says what
the operator can do, and the index's own checks run inside the lease.

**The scientific review — 1 blocker, 8 major, 8 minor, 1 nit.** All are fixed: the **failure
classification** is now per gold (§E.5); the **MRR** is the topmost gold (the baseline moves
.4615 → .5078); the **supported-answer metric** honours the case's own condition (.3571 →
6/14 = .4286, and the unsupported rate .6429 → .5714); **"top-1 not gold"** checks the
top-ranked claim (9 → 8/14); **H1's "definitions"** were mostly not definitions (only 109 of
162 had the name immediately followed by a definitional copula) — a **second freeze** (v1.1)
requires the adjacency and a length bound, and **the negative result is unchanged under both
rules**; **H2's scale** is stated (13 of 326 concepts, 4.0 %) and **H3's coverage shift** is
measured; the **causal explanation** of the two baseline rows is computed from the data and the
gate is read from the production path's own abstention reason (U-03 → `ABSTAINED_TIME_DEMAND`);
the **held-out set's independence claim is corrected** (it is *question* independence, not
corpus independence — 26 of the 38 cases draw their gold from a document the development
evaluation also imports); the **C1 diagnosis's staleness** is stated and its measurement script
is now in the repository; and the minor metrics (the global micro recall, the unsupported rate
over all ask cases, the latency overhead, the aggregate gold availability) are delivered.

**What the review found no defect in**, and the wave states as fact: the extraction's
provenance (the spans verified against the corpus bytes), its determinism, the absence of any
held-out leakage into the development measurement, the comparability of the candidates (all of
a case's candidates run on one prepared snapshot), and the honesty of the negative result.

### G.5 What was NOT verified, and the inherited defects

1. **The packaged application could not open the SQLite store on this host** — and the
   diagnosis in this report was **wrong**: it blamed the driver's resource extraction inside the
   application's class loader, but the lookup **succeeds**; the failure is the driver's **write
   into its extraction target**, which was the application's scratch directory under a tmpfs
   whose per-user quota the project had already measured exhausting. The need for
   `-Dorg.sqlite.lib.path` is **not reproducible** in the JVM packaging on this host. **Corrected
   and closed on 2026-11-15** (`docs-v2/engineering/EPISTEMIC-SQLITE-PACKAGING.md`): the packaging
   verifies that the driver carries the native library, the demonstration's workaround is
   deleted, and a mutation proof shows the guard failing when the library is removed.
2. **The Docker Hub publication has no credentials.** The repository holds **zero** secrets
   (`gh api repos/AlexanderNarbaev/agi/actions/secrets` → `total_count: 0`), so the publication
   step cannot authenticate and the aggregate `ci` job stays red on that step alone. The values
   must be added as repository secrets — never pasted into a chat.
3. **ShellCheck (43 files) and the GraalVM native build (out of memory, red since 2026-10-31)**
   are red and were red before this wave. The PR-event TruffleHog scan reports nine unverified
   historical findings.
4. **Two frozen evaluation probes entered the learning feed** ('столица франции', 'Москва
   столица России?', in transliterated form) — a real defect, outside this wave's scope,
   recorded as such and not fixed here.
5. **Not verified:** generalisation beyond one corpus, one topic and one author; the held-out
   set's negative cases are **easier** than a real "topic present, figure absent" negative (the
   mechanical rule forces off-topic negatives); the held-out set has **no revision case**, so
   the revision safeguard could only be checked as unreachable; the collection's window is
   **designed**, not organic, so it cannot answer whether a tail-20 window is a biased
   estimator; the latency figures are one machine and one run; and the `.jqwik-database`
   artefact of the property tests is left untracked in the working tree.

---

## H. The architectural compliance

| the invariant | how it was held |
|---|---|
| the frozen zones | `CONSTITUTION.md`, `AGENTS.md`, `matrix-core/.../ethics/frozen/`, `matrix-core/src/main/resources/avro/**` and `.github/workflows/**` were **not modified** — verified against the diff of the whole wave |
| the chain boundaries | the new route is on **chain A** (the Quarkus application) only; chain B (the gateway) is untouched; the cognitive store's single-writer ownership model is unchanged, and the report route uses the **knowledge** store, not the cognitive one |
| the reused components | the workbench's own retrieval and answering APIs, the project's existing `ArtifactAction` / `ActionRegistry`, the existing `NetworkExposure` and `AuthorizationFilter` contracts, the existing `KnowledgeStore` and `KnowledgeWorkbench`, the existing `EpisodicLog` / `PromotionGate` / `TrueMindCycle` for the learning evidence, and the existing `TsetlinTrainer` / `RuleInductionEngine` for the representation comparison — **no new action engine, no new conductor, no new memory architecture, no second database** |
| the new serving contract | one route, one request shape, one response shape, opt-in and local-only. The output root and the action identity are the server's derivation. The **no-absolute-path** rule holds for successes and for refusals |
| the new storage contract | **none** — the artifact root is the project's existing local-root convention, the trace is the project's existing append-only NDJSON index, and the index is bounded rather than replaced |
| the deterministic decision paths | no `Random` and no wall-clock in any runtime decision path that the wave added; the research harnesses' seeds are fixed and named |
| `K_MAX = 20` | unchanged |
| the capability registry | one row added (`C2A-EVIDENCE-TO-ACTION`), the file went from 56 to 57 rows, **no existing row was renumbered, rewritten or deleted**, and the validator reports 0 defects |
| the canonical documents | `PROJECT-STATE.md` (the snapshot), `ROADMAP.md` (§3's new section and §4.16's seven tasks) and `INDEX.md` (the wave's row) are updated in the snapshot commit; the wave's report is `docs-v2/engineering/C2A-FINAL-REPORT.md` |

---

## I. The owner's decisions

| the decision | the state | what the wave adds |
|---|---|---|
| the fold replacement (ADR-REP-SUBSTRATE-01 §4.4, options A/B/C) | **OPEN** | the measured reason (§F.3, §F.5): on a two-class partition no representation beats the majority baseline, and a 1-NN over the unfolded 256-bit feature is at chance — **the representation is not the observed bottleneck**. The consequence of the choice: choosing A, B or C now would optimise a component that the measurement does not indict; the honest next step is a feature that carries the label |
| the peer's durable payload contract | **OPEN** | untouched |
| the Exp324 threshold | **2 ms, unchanged** | the measurement: the chain's forward pass has a 1.82 ms median in isolation and exceeds it under a loaded full suite — the threshold is the owner's, and it is a *latency* question, not a learning question |
| **promote a retrieval candidate?** | the recommendation is **NO** | no candidate recovers a failing case on the development set or on the held-out set; H3 loses a case and raises the unsupported rate. The production retrieval is unchanged and the floor is unchanged |
| **the concept identity layer** | **new (task C2A.3)** | 19 % of the concept names end in a dangling function word and the three largest concepts are pronouns; the enrichment could not work because the thing to enrich was not a concept yet |
| **the premise path** | **new (task C2A.1)** | the premise path refuses the disputed premise in **0 of 6** unsupported cases — the largest single epistemic weakness the wave measured, and it sits above the retrieval |
| the Docker Hub secrets | **the owner's action** | the repository holds zero secrets |

---

## J. The next measurable development step

The tasks are recorded in `docs-v2/governance/ROADMAP.md` §4.16, each with the measurement that
created it and the criterion that would close it.

### J.1 The premise path must refuse a disputed premise (C2A.1)

**The user problem.** A user asks a question whose premise the workspace's own evidence
contradicts, and the system answers it as if the premise held — the answer is fluent,
confident and wrong at the root.
**The design.** The asking path must run the premise assessment, and its verdict must be
distinct from the retrieval's: a contradicted premise is **refused and named**, with the
contradicting claim cited.
**The baseline.** `PremiseAssessment.assess` returns `CONTRADICTED` for **1 of 6** unsupported
cases; the asking path refuses **0 of 6**.
**The acceptance criterion.** On the six unsupported cases: the premise is assessed, the
disputed premise is named, the request is refused, and the refusal does not look like an
ordinary ignorance. The 6/6 abstention accuracy must not fall.
**The cost and the risk.** A production change in the asking path — the risk is a false
refusal, which the six unanswerable cases and the 18/29 baseline measure.

### J.2 A conflict in the evidence must be reported (C2A.2)

**The user problem.** The corpus holds two claims that cannot both hold; the answer cites both
and says nothing.
**The design.** The answer construction must detect a contradiction among the cited claims and
report it as such, without manufacturing a resolution.
**The baseline.** The ambiguous cases represent every reading in **0 of 4**; the contradictory
cases carry both claims in **2 of 3** and never name the conflict.
**The acceptance criterion.** On the three contradictory cases the answer names the conflict and
both claims; on the ambiguous cases it states that the readings differ.
**The cost and the risk.** An answer-shape change — the risk is a noisier answer; the
epistemic separation of the response must survive it.

### J.3 The concept identity layer must be repaired (C2A.3)

**The user problem.** The concepts a workspace holds are fragments of sentences, not concepts;
every retrieval experiment inherits the defect.
**The design.** The acquisition layer must produce concepts whose names are the concepts the
documents state, and definitions that are definitions.
**The baseline.** 326 concepts, **0** usable definitions, 61 of 326 names ending in a dangling
function word, 9 bare function words, the three largest concepts are pronouns; 109 of 162
extracted "definitions" fail the adjacency test.
**The acceptance criterion.** The dangling-function-word rate is 0, the extracted definitions
pass the adjacency test, and the concept coverage baseline is re-measured and re-published.
**The cost and the risk.** A production change in the acquisition layer — the risk is a changed
corpus, so the 18/29 baseline and the held-out set must be re-measured on it.

### J.4 The learning evidence needs a positive control (C2A.4)

**The user problem.** The learning subsystem cannot be said to learn: on real data every
representation is at chance.
**The design.** Either a feature that carries the label (not a hash of the text), or an
explicit statement that the label is not recoverable from the episode's text.
**The baseline.** The production fold .441/.588/.559 accuracy with .402/.540/.500 balanced;
the 1-NN ceiling on the unfolded feature .529 / .544; the synthetic control .714.
**The acceptance criterion.** A representation that beats the majority baseline on the
two-class partition **with the label carried by the feature**, or the honest conclusion that
the question cannot be answered with the current label rule — in which case the label rule
becomes the owner's question.
**The cost and the risk.** Research only; no production change; the risk is a long measurement
campaign.

### J.5 The smaller ones

**C2A.5** — the window's tail must be measured as an estimator on an organically mixed log of
≥400 entries (the wave's collection was designed, so it cannot answer this).
**C2A.6** — the packaged store must open without a manual property.
**C2A.7** — the Docker Hub publication needs the owner's credentials.

**What this wave deliberately did not do.** It did not propose a fold (A/B/C stay OPEN and
unimplied), it did not widen the Tsetlin machine, it did not bring the C/JNI/FFM/GPU path back,
it did not relax an epistemic threshold, it did not add a network-facing enablement, it did not
promote a candidate whose benefit it could not measure, and it did not replace the artifact's
durable audit architecture.
