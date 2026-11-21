# CRJ-01 — Risk verdict: the changed-payload idempotency case and the false-green instruments

**Status:** DRAFT · **Date:** 2026-11-20 · **Baseline SHA:** `b7bd1be4` (branch `develop`)
**Task:** CRJ-01.1 and CRJ-01.2 of `docs-v2/engineering/CRJ-01-ACCEPTANCE.md`, executed as
CRJ.0.3 and CRJ.0.4 of `docs-v2/engineering/CRJ-01-EXECUTION-PLAN.md`.
**Nature:** a review of the baseline tree, and the record of an executed reproduction. Where a
finding was **fixed** under the owner's decision of 2026-11-20, the fix and its evidence are
named here as well — the review and the remedy are one record, not two.

**Verdict in one line:** the changed-payload idempotency risk is **PROVEN** (it was executed
against the baseline and it failed), and the Article I instrument gap **R-EPIS-10** is
**CONFIRMED** by the source and now closed by a member-granular fix whose negative control is
an assertion.

---

## 1. Part 1 — the changed-payload idempotency red team (CRJ-01.1)

### 1.1 The verdict

| | |
|---|---|
| the question | may a request under a **reused** idempotency key with a **changed** payload be answered with the earlier journey's success? |
| the verdict | **PROVEN RISK** — reproduced against `b7bd1be4` by an executable test, not inferred from the source |
| the deciding code line | `matrix-core/src/main/java/io/matrix/actions/ArtifactActionService.java:199` |
| the evidence of the defect | `docs-v2/engineering/evidence/CRJ-01-REPRODUCTION-idempotency-changed-payload.xml` — 3 tests, **1 failure** |
| the evidence of the repair | `docs-v2/engineering/evidence/TEST-io.matrix.knowledge.ReportIdempotencyConflictTest.xml` — 3 tests, **0 failures** |

### 1.2 The mechanism, step by step

1. **The identity follows the key, not the question.**
   `ArtifactActionService.java:198-205` derives the action identity from the workspace and a
   *subject*; the subject is the idempotency key when the caller offered one (`:199`) and the
   question otherwise. A reused key therefore names **one** identity for **any** payload.

2. **The route resolves the identity before it asks the workspace.**
   `ArtifactReportResource.java:282` computes that identity; the question is not part of it.

3. **The facts that admit the replay do not mention the question.**
   `ArtifactReportResource.java:315` builds the fact set from the retained journey — the
   identity is committed, the workspace fingerprint is unchanged, the report name is unchanged.
   At the baseline, the question and the level were not among the facts, so a changed question
   did not make the replay inapplicable.

4. **The replay method serves the RETAINED bytes.**
   `ReportJourneyMethod.replay()` (the baseline's `preconditionsHold`) checked the file name,
   the fingerprint and the commit — and then read the earlier artifact's bytes off the disk
   and wrote them back under the same identity.

5. **The writer sees a legitimate replay.**
   `ArtifactWriter.java:319-343`: the identity is committed, the file name matches, and the
   digest matches — because the bytes being written ARE the earlier bytes — so the disposition
   is `REPLAYED` and the route answers **HTTP 200**.

6. **The writer's own conflict rule never fires.**
   `ArtifactWriter.java:328` refuses a *different* content under a committed identity
   (`REFUSED_IDENTITY`, HTTP 409) — but the replay writes the OLD content, so the digests
   agree and the refusal is never reached.

**The two mechanisms that decide the outcome, distinguished.** The writer's content conflict
(`ArtifactWriter.java:328`) and the replay's retained bytes (`ReportJourneyMethod.replay()`)
are different guards. The first is sound and was never the problem; the second is what turned
a changed question into a success. The reproduction confirms it: the *changed report name*
variant was already refused with `REFUSED_IDENTITY`, because the name differs and the writer's
guard fires — while the *changed question* variant was served as a success, because the
replay replaced the request before the writer could compare anything.

### 1.3 The four-variant table (baseline `b7bd1be4`)

| # | the reused key with… | what the baseline did | the deciding line | the status |
|---|---|---|---|---|
| 1 | a **changed question** | **HTTP 200, `REPLAYED`, the earlier report's digest** — a stale success | `ArtifactActionService.java:199` + `ReportJourneyMethod.replay()` | **PROVEN RISK** (executed) |
| 2 | a **changed level** | the same shape as #1: the level was not part of the replay's domain, so the answer retrieved at the earlier level was served for the new level | `ReportJourneyMethod.preconditionsHold` (baseline) | **PROVEN RISK** (the same mechanism; the level was not in the fact set) |
| 3 | a **changed report name** | **HTTP 409 `REFUSED_IDENTITY`** — the writer's name guard fires, the artifact is untouched | `ArtifactWriter.java:312-318` | **REFUTED** (executed) |
| 4 | a **changed workspace state** | the workspace's fingerprint changed → the replay's precondition failed → the workspace was asked again → the new content conflicted with the committed identity → **HTTP 409 `REFUSED_IDENTITY`** | `ReportJourneyMethod.preconditionsHold` + `ArtifactWriter.java:328` | **REFUTED** (the fingerprint guard holds) |

Variants #1 and #2 are the same defect: **the replay's applicability domain was the identity
and the file, not the request.**

### 1.4 The blast radius

| the dependent | where | the risk |
|---|---|---|
| `ArtifactActionService.identity(...)` | `matrix-core/src/main/java/io/matrix/actions/ArtifactActionService.java:198` | the single derivation every report identity comes from |
| `ArtifactReportResource.run(...)` | `matrix-core/src/main/java/io/matrix/knowledge/ArtifactReportResource.java:282`, `:433` | the served route: the write path and the outcome path both derive the identity the same way |
| `ArtifactReportResource.journeyFacts(...)` | `matrix-core/src/main/java/io/matrix/knowledge/ArtifactReportResource.java:604-617` | the fact set that admits the replay — the place the question was missing |
| `ReportJourneyMethod.replay()` | `matrix-core/src/main/java/io/matrix/cognitive/report/ReportJourneyMethod.java` | the method that served the retained bytes |
| `ReportJourneyTrace` / `ReportJourney.Retained` | `matrix-core/src/main/java/io/matrix/cognitive/report/ReportJourneyTrace.java`, `ReportJourney.java` | the retained record did not name the question, so the domain could not be stated even if it had been checked |

No other production caller of `identity()` exists: the two call sites above are the whole
surface (`grep -rn "\.identity(" matrix-*/src/main/java`).

### 1.5 The reproduction — proposed, then executed

**The proposed reproduction** (CRJ.0.3's deliverable): the fixture is the served route wired
by hand exactly as `ArtifactReportResourceTest` wires it (a temporary SQLite workbench with the
bundled photosynthesis package, an owner-only output root, the kernel's planner and the
cognitive service over the same store); the two requests are the **same** question and
**consent: true** with the **same** idempotency key; the assertion is that the second response
must not carry the first response's digest, and that the report on disk must answer the
question that was asked.

**The executed reproduction**: `matrix-core/src/test/java/io/matrix/knowledge/ReportIdempotencyConflictTest.java`,
run against the baseline with the fix stashed.

| the test | the baseline | after the fix |
|---|---|---|
| the exact replay of a request stays a correct replay | **PASSED** | PASSED |
| a reused key with a different question is never answered with the earlier report | **FAILED** — the digests were equal (`efe568902bf073cef8ade3b8adc49f31d590f6e4db92103e61224fd40dfca0c9` for both) | PASSED |
| a reused key with a different report name does not serve the earlier report | PASSED (409) | PASSED |

### 1.6 The remedy, as the owner decided it

The owner selected **option B** on 2026-11-20: *keep the identity, make the replay's domain
honest, and let the writer's existing conflict answer the reused key with a changed payload.*

| the change | the file |
|---|---|
| the retained journey names the question and the level it answered | `matrix-core/src/main/java/io/matrix/cognitive/report/ReportJourney.java` (`ReportJourney.Retained`) |
| the trace stores and reads them; a record without them is unnamed and therefore not replayable | `matrix-core/src/main/java/io/matrix/cognitive/report/ReportJourneyTrace.java` |
| the replay declares `report.question-unchanged` and `report.level-unchanged` as preconditions, and checks them | `matrix-core/src/main/java/io/matrix/cognitive/report/ReportJourneyMethod.java` |
| the route establishes the two facts from the request's own comparison with the retained record | `matrix-core/src/main/java/io/matrix/knowledge/ArtifactReportResource.java` (`journeyFacts`) |

**The behaviour after the fix, measured:** a reused key with a changed question answers
**HTTP 409 `REFUSED_IDENTITY`** — the writer's own words, "the report name is already bound to
another action identity; one idempotency key names one report" — no second artifact is
written, and the earlier artifact's digest is unchanged. The exact replay is untouched: HTTP
200 `REPLAYED`, the same digest, one artifact. **The identity derivation did not change**, so
no `taskId` moved and no previously committed artifact became unreachable.

**The fail-closed rule.** A retained journey written before this change names no question. It
is not replayable: the replay's precondition fails, the workspace is asked again, and the
request meets the writer's conflict. The old record is therefore never served as an answer to
a question it cannot state.

### 1.7 What this finding means for the programme

The reachability map names the same break: *"BREAK-4 — Chain A's 'later task' is the same
task"*. The defect was the mechanism by which the system could **report** a later task while
answering the earlier one. Closing it does not create transfer; it removes a false claim of
transfer, which is the precondition for measuring transfer honestly.

---

## 2. Part 2 — the false-green instrument review (CRJ-01.2)

### 2.1 R-EPIS-10 — the Article I exemption was file-granular: **CONFIRMED, now closed**

| | |
|---|---|
| the finding | the decision-path auditor armed its `@ArticleIPermitted` exemption on the mere **text** of the name — the `import` statement included |
| the deciding line | `matrix-core/src/main/java/io/matrix/auditor/DecisionPathAuditor.java:310` (the baseline's `:305`) — `line.contains("ArticleIPermitted")`, with the arming depth at `:321` and the member test at `:334` |
| the consequence | the exemption was armed at brace depth **0**, before the type was opened, and it covered the **whole class body** |
| the files outside the gate's reach | `matrix-core/src/main/java/io/matrix/actions/ArtifactWriter.java:4`, `matrix-core/src/main/java/io/matrix/knowledge/concept/research/LiveStoreMigration.java:3`, `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/EpisodeQuarantineTool.java:3` — all three verified as **importing** the annotation, exactly as the risk register named them |
| the fix | one condition: an `import` line no longer arms the exemption (`DecisionPathAuditor.java:310`), **plus** the member's *declaration* line is recognised as part of the annotated member (`DecisionPathAuditor.java:351`) — without it, a member written on one line (`public long stamp() { return System.currentTimeMillis(); }`) fell outside its own annotation |
| the governance consequence | the accepted baseline `docs-v2/quality/decision-path-baseline.txt` — **and it did NOT change**: the gate stayed green with the baseline untouched, because all three files' wall-clock reads are **adjudicated** (they sit inside properly annotated members) and a permitted, adjudicated finding does not block |
| the negative control | `matrix-core/src/test/java/io/matrix/auditor/DecisionPathImportExemptionTest.java` — a planted, unannotated `Instant.now()` in a class that merely **imports** the annotation must be **found and must block**; the annotated member must still be exempt |

**The instrument's own regression was caught by the repository's existing test.**
`DecisionPathAuditorTest.articleIPermittedAnnotationClearsASite()` failed on the first version
of the fix: the fixture writes its member on one line, and the depth rule cannot see a read on
the declaration line. The failure is not noise — it is the exact place where the file-granular
exemption had been doing the work. The declaration-line rule closed it, and the existing test
now passes for the right reason.

### 2.2 R-EPIS-11 — the print-only probe family: **CONFIRMED, named, not converted**

Measured over the whole tree (the classes whose file name carries the `HardReview` or
`AdversarialProbe` marker), by counting the `@Test` methods, the assertion calls and the print
calls in each file:

| the class | the kind | the evidence |
|---|---|---|
| `HardReviewConceptIdStabilityTest` | measurement | 1 method, 0 assertions, 8 prints |
| `HardReviewCorrectedRuleTest` | measurement | 4 methods, 0 assertions, 13 prints |
| `HardReviewP2JourneyTest` | measurement | 6 methods, 0 assertions, 26 prints |
| `HardReviewThirdGateTest` | measurement | 3 methods, 0 assertions, 8 prints |
| `HardReviewDefinitionPrecisionTest` | measurement | 3 methods, 0 assertions, 11 prints |
| `HardReviewP1DefinitionsTest` | mix | 3 methods, 5 assertions, 14 prints |
| `AdversarialProbePremiseTest` | mix | 13 methods, 21 assertions, 58 prints |
| `AdversarialProbeRound3Test` | mix | 7 methods, 14 assertions, 26 prints |
| `AdversarialProbeRound4Test` | mix | 6 methods, 14 assertions, 23 prints |
| `AdversarialProbeRound5Test` | mix | 4 methods, 15 assertions, 18 prints |
| `HardReviewLeakagePlantTest` | mix (the fixed one) | 1 method, 10 assertions, 8 prints |

**The totals: 11 classes, 51 methods, 79 assertion calls, 213 print calls, and not one class
that is a pure gate.** A class is called a **gate** here only if it asserts and can fail the
build on the property it names; a class that also prints is a **mix**; a class that only
prints is a **measurement probe** — an instrument, not a check.

**Which path each family covers:**

| the path | the covering class | the kind |
|---|---|---|
| the idempotency path | **none** | the family covered no idempotency probe — the defect in Part 1 had no adversarial probe at all, which is why it survived |
| the Article I path | none of this family (the gate is `DecisionPathGateTest`, a real gate) | the gate is a gate; the family is not involved |
| the sealed-set leakage path | `HardReviewLeakagePlantTest` | mix, and the two false-success probes of it are **closed** — it asserts, and it has a mutation proof |
| the learning-quarantine path | `C2ALearningEvidenceTest` (outside this family) | a gate |

**The consequence for the programme.** The phrase *"the printed numbers became assertions"*
holds for the held-out evaluation, **not** for this family. The newly written
`ReportIdempotencyConflictTest` and `DecisionPathImportExemptionTest` were written as **gates**
on purpose: every claim asserts, and the first one was proven red before the fix.

### 2.3 What remains UNKNOWN

| the item | what would settle it |
|---|---|
| whether the **other** decision-path packages contain a file that imports the annotation *and* carries an unannotated wall-clock read — none was found at the baseline, but the instrument could not see inside the three exempt files at all | the fixed auditor: the whole tree is now scanned, and the gate is green |
| whether the **adversarial probes' printed numbers** match what they claim | the conversion of each probe into an assertion is the next wave's work; it is a design decision, not a defect |
| whether the **one-line member** shape occurs elsewhere in the tree (a read on a declaration line) | a tree-wide scan for a one-line member carrying a wall-clock read |
| the **cost of the seal** on the protocol's arms (CPU/RAM/latency per arm) | the CRJ-01 protocol marks it `NOT_INSTRUMENTED`; only an instrumented run fills it |
