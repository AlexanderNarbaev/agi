# CRJ-01 — Arm C: the governed learned competence, built and measured

**Status:** MEASURED · **Date:** 2026-11-20 · **Baseline:** `b7bd1be4` · **Branch:** `feature/crj-01-arm-c`
**Owner decisions:** **OD-081** (the arms and the oracle) and **OD-082** (what arm C builds)
**Contract:** `docs-v2/engineering/CRJ-01-ACCEPTANCE.md` §2, §4 (the REUSE phase)
**Parent:** `docs-v2/engineering/CRJ-01-VALUE-CHECKPOINT.md`

---

## 0. The answer in one paragraph

The mechanism the owner authorised exists and works end to end on the **real packaged
application**: a journey's independently observed effect becomes a candidate, the candidate is
checked on **held-out** questions it was never proposed from, and only a candidate that
reproduces the reference on every one of them is **promoted** with a version and a provenance;
after a **restart** the promoted procedure is offered to the kernel and **answers a different
question**. That is the contract's REUSE phase, executed. What it is **not** is the CRJ-01
developmental milestone: the sealed set does not exist and no three-arm comparison has been
run. **The oracle now exists** — both of them, built as test-tree instruments, with their
controls — and **the learned-claims half is built**: a workspace claim the system validated
against the document's own text, promoted with a version and a provenance, and **cited by the
served answer**. And one measured result is a **null**: on this workspace the
procedure saves **2.7 %** of the reading time — the mechanism is right, the workload is too
small for it to matter.

---

## 1. What was built

| the piece | the file | what it is |
|---|---|---|
| the **definition** of a domain-A procedure | `matrix-core/src/main/java/io/matrix/cognitive/report/ReportProcedure.java` | the codec that carries the procedure's **support set** in the portfolio's own durable record — the `Procedure` record's applicability field, which is exactly "the domain it may be used in" |
| the **method** the kernel selects | `…/report/ReportProcedureMethod.java` | a member of the report-journey family: it asks the workspace **as the support set** and writes the artifact through the same writer and the same identity |
| the **producer** | `…/report/ReportProcedureLearner.java` | observed episodes → a candidate → the held-out validation → the promotion, with **four gates** that can each refuse |
| the **restricted reading** | `…/knowledge/KnowledgeWorkbench.java` | one asking path with an optional narrower snapshot; the three-argument overload behaves exactly as before |
| the **producer's entry point** | `…/cli/LearnReportProcedureCommand.java` | the operator's command; it runs **alone** (its own `main`), so promoting a procedure does not boot the application or start the background trainers. **It is deliberately not registered in the root command**: a changed file in the root `io.matrix` package makes the fast lane derive the scope `--tests io.matrix.*` — the WHOLE suite — which does not fit the lane's twenty-minute limit |
| the **served path** | `…/knowledge/ArtifactReportResource.java` · `…/report/ReportJourneyPlanner.java` | the workspace's promoted procedures are offered to the kernel with the facts that establish their domain |

**No new subsystem.** The procedure's durable home is the cognitive store's **existing**
`cognitive_procedure` table (with its `(id, version)` identity, `learned_in_episode` and
`validation_note` columns); the promotion path is the **existing** `CognitiveStore.recordProcedure`;
the selection path is the **existing** `AlgorithmRegistry`/`KernelSelector`; the trace, the
writer, the consent and the local-only gate are untouched.

## 2. The four gates, and what each of them refuses

| the gate | the rule | the refusal |
|---|---|---|
| the **admission** | a record is admitted only when it is learning-eligible and it is **not** quarantined; the quarantine list is supplied by the caller and the gate is **fail-closed** | a missing quarantine list admits **nothing** — the chain-B isolation mechanism falls back to "nothing is quarantined" when its ledger is missing, and this gate does not repeat that mistake |
| the **held-out-case rule** | a validation case may not be one of the observed episodes — neither by identity nor by question | the promotion is refused, naming the case |
| the **case count** | at least `ProcedureValidator.MINIMUM_VALIDATION_CASES` (**5**), the portfolio's own named constant | two cases are not evidence |
| the **agreement** | the candidate must agree with the reference on **every** held-out case: the same verdict **and** the same cited claims | one disagreement refuses the promotion and names the case — because a procedure is replayed *without* being checked |

## 3. The evidence

### 3.1 The gate — 9 tests, 0 failures

`matrix-core/src/test/java/io/matrix/cognitive/report/ReportProcedureLearningTest.java`
(retained: `docs-v2/engineering/evidence/TEST-io.matrix.cognitive.report.ReportProcedureLearningTest.xml`)

| the test | what it pins |
|---|---|
| the learner fails closed when the quarantine list is absent | the admission gate refuses |
| the learner refuses a quarantined episode | the evaluation traffic does not become learning |
| the learner refuses a validation case that was one of the observed episodes | the held-out-case rule |
| the learner refuses below the minimum case count | the named threshold |
| a candidate that agrees on every held-out case is promoted, with its provenance | the version, the measured agreement, the kind `INDEPENDENT`, the episodes it was learned from, and the note that says what its standing rests on |
| the promoted procedure is durable | a store opened over the same file after the learner is gone still holds it |
| **a later, DIFFERENT question is answered by the promoted procedure** | the trace's PLAN step names the procedure's capability |
| a procedure whose support set is absent is not offered | the reference method answers instead |
| the procedure reads FEWER claims than the reference | the saving's mechanism, deterministically |

### 3.2 The served path — 20 of 20 checks, over real HTTP

`docs-v2/engineering/CRJ-01-JOURNEY-DEMONSTRATION.json` (the script
`scripts/crj01-journey-demonstration.py`, the packaged `quarkus-run.jar` on port 9098).

The arm C leg runs in three beats, and **the order is the point**:

1. the serving process **observes** three journeys on the `photosynthesis` workspace and writes
   their traces;
2. the serving process **stops**, and the operator's command learns the procedure from those
   traces — the store has one writer, and the command is that writer;
3. the serving process **starts again** and is asked a **different** question.

**What the command reported:**

```
promoted: true
reason: the candidate agreed with the reference on all 5 held-out case(s) and was promoted
procedure: report.procedure.faca1d3b.23d63528 version 1.0.0
validated on: 5 held-out case(s), agreement 1.0, kind INDEPENDENT
learned in episode(s): report-c2a.report.v1.b3ebd243…, …695498a8…, …d4430fbb…
support: 4 source(s), 15 of the workspace's 17 claim(s)
one measurement of the two readings of 'What is the role of water in photosynthesis?':
  full 5.319553 ms, restricted 5.176083 ms
```

**What the kernel chose after the restart, for the later question:**

> `procedure.report.procedure.faca1d3b.23d63528: selected procedure.report.procedure.faca1d3b.23d63528@1.0.0
> with score 1.0 over 2 admitted candidate(s); the accuracy evidence was 1.0 on the DECLARED basis
> over 5 case(s); the cost came from DECLARED_COMPLEXITY …`

The answer cited **4** sources — the support set's own sources.

### 3.3 The regression

**51 suites / 297 tests / 0 failures / 0 errors / 0 skipped** — the report journey, the closed
loop and its measurement, the counterfactual, the restart, the fingerprint, the route's own
contract, the epistemic package, the live-store journey, the whole algorithms package (including
`ProcedureStoreTest`) and the whole auditor package. Every XML is retained.

### 3.4 What the FULL suite found, and what it cost to find it

The narrow scope was green, so the **full** suite was run — 1358 suites, 9803 test methods. It
failed **twice**, and neither failure was in the code this slice wrote. Both are recorded here
because they are what the measurement actually found:

| the failure | the attribution | the evidence |
|---|---|---|
| `ArtifactReportResourceTest > two concurrent requests with the same identity write one artifact` — HTTP **503** where 200/201 was expected | **A PRE-EXISTING DEFECT of the served path**, found because the full suite was run. The store's read-then-write held a read snapshot across the write, and the trace's own write into the **same SQLite file** invalidated it; SQLite cannot upgrade a stale snapshot and answers `SQLITE_BUSY` at once. The route then reported it as `output_root_unavailable` — a wrong name for a busy database | the **control tree** (the commit before arm C) fails the same probe at the same rate: **32 of 40 rounds** refused against **33 of 40** on the arm C tree. Fixed and proven: **RED** (`CRJ-01-REPRODUCTION-concurrent-journey-sqlite-busy.xml`, one test, one failure) → **GREEN** (`ConcurrentReportJourneyTest`, 40 rounds, not one refusal) |
| `Exp324EndToEndForwardPassTest > endToEndForwardPassLatency` — the chain-forward median measured **2184 µs** against a **2000 µs** bound | **A LOAD-SENSITIVE WALL-CLOCK ASSERTION**, not a property of the code: the same test measures **1434 µs** in isolation, and the assertion is on the median of 50 passes — a statistic the test's own comment says is the right one for exactly this reason. The suite that failed it was running the model-heavy research suites concurrently | the isolated run's own output: `chain_forward distribution: median=1434.66 us p95=1907.99 us max=1980.52 us` |

**The cost of the measurement:** the full suite is **32 minutes** on this machine (the control's
14 minutes are not comparable — the control worktree had no model checkpoints, so it **skipped**
the five heaviest research suites and did **not** run the latency test at all). It has to run
**alone**: the two earlier failures of this session were produced by a run that overlapped
another Gradle JVM, and a control run that overlapped one failed for the same reason and was
discarded. **One heavy JVM at a time** is not a convenience here — it is the difference between a
measurement and an anecdote.

---

## 4. What is NOT claimed, and the measured null (characterised in §8)

| the claim | the state |
|---|---|
| **transfer to a different task** | **DEMONSTRATED, in one domain**: a question the procedure was neither proposed from nor validated on was answered by it. It is a **same-domain** transfer over a **17-claim** workspace |
| **the saving is worth anything** | **CHARACTERISED, NOT NULL** (§8): the saving is a function of the support set's **fraction** of the workspace — on a **540-claim** corpus at a fraction of **1/6** it is **78 %** of the reading (**20 ms against 92 ms**), and the bundled 17-claim workspace at its own fraction of **0.88** measures **−0.6 % to 2.8 %**, which is exactly the earlier "2.7 % null". **The null was the workload, not the mechanism** |
| **the kernel chose the procedure for a measured reason** | **MEASURED, and the first one was not**: the decision used the procedure's **DECLARED** complexity (1 ms against the reference's 25 ms), because the procedure had never run before and the ledger had no measurement of it. The first decision therefore rests on a **promise**; the second rests on the measurement — and that transition is now **pinned by a test** (`ReportProcedureCostBasisTest`): the decision's own stated basis changes from `DECLARED_COMPLEXITY` to `MEASURED` after the procedure has run once, and the trace says so. The remaining defect is the one below |
| **the CRJ-01 developmental milestone** | **NOT DONE**: no sealed set, no independent oracle, no three-arm comparison, no `δ_MI = 2` measurement. The protocol's D5 — the freeze by a reviewer who is neither the case author nor a solver author — is still open |
| **the learned-claims half of arm C** (OD-082's second half) | **NOT DONE**: this slice builds the **procedures**. The learned claims need their own promotion-and-validation contract with a held-out-case rule, and the oracle (OD-081's D3) must exist before a claim can be validated against anything but the reference |
| **the quarantine list** | **SUPPLIED BY THE CALLER**: the gate is fail-closed, but the list's correctness is the caller's. The chain-A episode ids **can now be enumerated from the store** — the journey opens its episode (the fix of 2026-11-21, `ReportJourneyEpisodeTest`), so `store.episodes(workspace)` lists them — but the list itself is still the caller's to name |

## 5. The residual defects

| the defect | the state | the owner |
|---|---|---|
| the **first** selection of a promoted procedure rests on its **declared** cost | **MITIGATED, NOT CLOSED** | the coordinator: the selector already prefers a **measured** cost when the ledger holds one, and the transition is now pinned by a test — the first decision rests on a promise, every decision after it rests on a measurement. The residual is that the *first* decision is made on an estimate; the promotion could carry the validation's own measured cost instead of a declared one |
| the journey did not open an **episode row**, so a workspace's journeys could not be enumerated from the store | **CLOSED (2026-11-21)** | the journey now **opens** its episode before it writes a step into it (`CognitiveStore.ensureEpisode` → `ReportJourneyTrace.ensureEpisode` → the route), and the episode's account is never rewritten by a later journey. **Proven RED → GREEN** (`CRJ-01-REPRODUCTION-journey-episode-missing.xml`: 2 tests, 2 failures; `TEST-io.matrix.cognitive.report.ReportJourneyEpisodeTest.xml`: 2 tests, 0 failures). The gap it closed was real: the learned procedure's provenance names the episode it was learned in, and a reader who followed it found nothing |
| the **quarantine list** is the caller's | **OPEN** | the programme: chain A needs the same role/quarantine discipline chain B has, or the caller's list must be audited |
| the report journey could refuse a request with **HTTP 503 `output_root_unavailable`** because the **database was busy** — a wrong name for the cause, and a **pre-existing** defect of the served path (`R-EPIS-13`) | **FIXED and PROVEN** | the coordinator: the knowledge store's read-then-write is now one IMMEDIATE transaction. RED without the fix (the retained XML), GREEN with it. The control tree — the commit before arm C — fails the same probe at the same rate (32 of 40 rounds against 33 of 40), so arm C did not cause it |
| a **full-suite run rewrites the retained C2A enrichment documents** (`R-EPIS-12`) | **OPEN** | the coordinator: the measurement must write into a temporary directory and the committed document must be copied out deliberately. Found while running the suite the fast lane derives when a changed file sits in the root package: the run left five of the owner's documents modified, and they were restored from `HEAD` |
| the learning command's **first entry point booted the whole application**, which started the background trainers and rewrote their state in `data/` | **FIXED in this slice** | the command now runs **alone** through its own `main`, so promoting a procedure touches nothing but the knowledge database it was pointed at |
| the arm C **cost** claim rests on one measurement over a 17-claim workspace | **OPEN** | the programme: the protocol's instrumented run |
| the learned-claims half of arm C | **CLOSED (2026-11-21)** — see §7 | the programme: the two oracles are built (`docs-v2/engineering/CRJ-01-INSTRUMENTS.md`), so a learned claim can be validated against the corpus's **own bytes** instead of against the reference. What is still missing is the corpus and the cases, which §5.6 requires from a case author who implements no arm |

## 6. The decision: **CONTINUE**, with the next step named

The mechanism is built, gated and demonstrated; the honest measurement says the **capability
is real and the benefit is not yet**. The next step is therefore **not** more of this: it is

1. the **oracle** (OD-081's D3) and the **sealed set** with its freeze (D5) — without them, no
   `δ_MI` can be measured and arm C's correctness stays unclaimed;
2. the **corpus and the cases**, commissioned from a **case author who implements no arm** and
   frozen by an **independent reviewer** — the oracle and the producer are ready for them, and
   the corpus's required shape is now **measured** (`docs-v2/engineering/CRJ-01-INSTRUMENTS.md` §4);
3. the **operator's path** for the claim learner, which the procedure learner has and the
   claim learner does not yet;
4. the **declared cost** of the first selection — the one defect above that is still open;
4. the **load-sensitive latency assertions** of the research suite (`Exp324` among them): they
   measure the machine and not the code, and they go red whenever the full suite runs the
   model-heavy suites next to them. They are the suite's own known class (`CI-SIMPLIFICATION-RFC`
   §7.1) and they are **not** silenced here — but the owner may want them to assert on a
   property of the code rather than on a wall-clock bound.

**Nothing here is pushed past the owner:** the merge, the review and the freeze's reviewer stay
the owner's.

## 7. The learned-claims half — BUILT (2026-11-21)

**OD-082's second half is no longer open.** A **learned claim** is a workspace claim the
system has **validated**, with the provenance of that validation: the production claim id, the
source it was checked against, the held-out cases it survived, the measured agreement, the
episode it was learned in, and the note that says what its standing rests on.

### 7.1 What the gates are, and what each of them refuses

| the gate | what it refuses |
|---|---|
| the **quarantine** gate | everything, when the quarantine list is **absent** — it fails closed, because the evaluation traffic must never become the durable learning |
| the **held-out-case rule** | a validation case that is one of the observed episodes, **by identity or by question** — a case the proposal came from proves nothing |
| the **minimum case count** | a promotion decided on fewer than **5** held-out cases — the repository's own convention for a validated procedure |
| the **agreement gate** | a candidate the reference does **not** carry on some held-out case; the reason names the case |
| the **containment gate** | a candidate whose proposition is **not a verbatim span of its source's text** — a learned claim may assert only what the document literally contains |

The containment gate is the new one, and it is the gate that makes the learned claim safe.
It asks the same question the CRJ-01 **source-span oracle** asks — independently, against the
corpus file's bytes instead of the workspace's copy of the text. A paraphrase is the
workspace's business (the extraction machinery's), and a learned claim that asserted one would
be asserting something nobody checked against the text.

### 7.2 The serving role: it reaches the answer

A learned claim that is promoted and never used is a record, not a capability. So the journey
**applies** it: the workspace's learned claims join the answer's evidence when the question
resolved to the claim's **concept** — and the concept is the workspace's own semantic anchor,
not the question's tokens, because a rule that matched on tokens would be the retriever's own
rule, and the protocol names that confound exactly (A3: "the gain is retrieval by overlap",
REJECTED for transfer).

The response **says** which claim was learned and on how many held-out cases it was
validated: a caller reading the evidence has to be able to tell what the retrieval found from
what the system learned.

### 7.3 The evidence

| the artefact | what it holds |
|---|---|
| `…/evidence/TEST-io.matrix.cognitive.report.ReportClaimLearningTest.xml` | **8 tests, 0 failures** — the promotion with its version and provenance, and **each of the five gates refusing** |
| `…/evidence/TEST-io.matrix.cognitive.report.LearnedClaimServingTest.xml` | **1 test, 0 failures** — a validated learned claim **in the served evidence** of a different question, with its provenance named |
| the regression | **227 suites / 1395 tests / 0 failures** over `io.matrix.knowledge.*`, `io.matrix.cognitive.*` and `io.matrix.crj.*` |

### 7.4 The operator's path

The claim learner has its own command, `matrix learn-report-claim`, on the same footing as the
procedure learner's: it has **its own `main`**, so promoting a claim does not boot the
application and start the background trainers, and it refuses **without the quarantine file** —
the gate fails closed, so a missing list is not an empty one. The one-writer rule is kept the
way the procedure's command keeps it: the operator runs it while the serving process is not
holding the database.

**The evidence:** `…/evidence/TEST-io.matrix.cli.LearnReportClaimCommandTest.xml` — 3 tests, 0
failures: the two refusals and the promotion, driven through the command against the journeys'
own traces.

### 7.5 What it is NOT, and what the owner must know

- **The workspace the tests use is imported from the research pool, not the bundled package** —
  and that is not a convenience: the bundled package's claims are **paraphrases** (measured:
  0 of 7 cited claims are spans), so the containment gate would refuse every candidate there.
  The production path's claims are spans (measured: **483 of 540**). The gate is therefore
  **proved** on a corpus where it can pass, and it is **proved to refuse** on the bundled one.
- **The learned claim is not yet measured against the oracle.** The oracle can check the
  property the gate uses — the proposition is a span of the document — and it will, at the
  sealed split. What it cannot yet do is score a **learned claim's** contribution, because the
  sealed set does not exist.
- **The transfer claim is still unmeasured.** A learned claim adds evidence for a question
  about its concept; whether that makes the answer **more correct** on a sealed set of novel
  questions is exactly the `δ_MI` the protocol asks for, and it is **not** claimed.
- **The applicability is the concept alone.** Two claims of the same concept are both
  applicable; the system's own machinery for a narrower class does not exist yet.
- The **learner promotes EVERY candidate that survives the gates**, not one: the journeys
  usually agreed on more than one claim, and each is a separate candidate with its own
  provenance. The operator's command against the research pool promoted **23** claims from one
  journey — every claim the reference carried on all five held-out questions. That is the
  mechanism working, and it is also a surface: with **one** observed episode, "the claims every
  journey cited" is every claim that episode cited.

---

## 8. WHERE the mechanism pays off — the cost curve (2026-11-21)

**The "measured null" of §4 was a property of the WORKLOAD, and this measurement says so.**

The arm C report carried one number: the procedure saves **2.7 %** of the reading time,
because its support set covered **15 of the workspace's 17 claims**. A null on one workload
does not say whether the mechanism is worthless or merely mis-applied — the mechanism's
premise is that a **narrower reading is cheaper**, so the saving must grow with the ratio of
the support set to the workspace.

**The instrument.** The same two readings — the full one and the restricted one — over
workspaces of different sizes and support sets of different fractions, each timed **30 times
after a 10-pair warmup**, **interleaved** (full, restricted, full, restricted, …) so that a
drift in the machine hits both sides equally. The reported statistic is the **median**, not the
mean: the distribution of a reading on a shared machine has a long tail, and the repository's
own latency assertion uses the median of 50 passes for exactly this reason. The test asserts
only what is **not** a matter of timing — the restricted reading cites a **subset** of the
full reading's claims and answers whenever the full one answers — so no load-sensitive
assertion is introduced.

### 8.1 The curve — three runs, two workspaces

| the workspace | the support's fraction of the claims | the full reading (median ms) | the restricted reading (median ms) | the saving |
|---|---|---|---|---|
| **research pool — 540 claims** | 0.167 (90 claims) | 92.6 / 93.4 | 20.1 / 20.3 | **78.25 % / 78.22 %** |
| | 0.337 (182 claims) | 92.0 / 92.3 | 29.8 / 29.8 | **67.59 % / 67.75 %** |
| | 0.770 (416 claims) | 93.9 / 92.2 | 69.0 / 67.6 | **26.58 % / 26.65 %** |
| | 1.000 (540 claims) | 91.6 / 93.3 | 91.7 / 93.6 | **−0.08 % / −0.32 %** |
| **bundled photosynthesis — 17 claims** | 0.059 (1 claim) | 3.90 / 3.76 | 2.90 / 2.82 | **25.69 % / 24.97 %** |
| | 0.294 (5 claims) | 3.18 / 3.29 | 2.46 / 2.51 | **22.69 % / 23.62 %** |
| | 0.412 (7 claims) | 3.14 / 3.00 | 2.70 / 2.58 | **14.07 % / 14.21 %** |
| | 0.882 (15 claims) | 3.14 / 3.07 | 3.16 / 2.98 | **−0.60 % / 2.79 %** |

(The two numbers per cell are **two consecutive runs**; a third, earlier run — taken while the
machine was busier, its full reading a 143 ms median — gave 85.46 %, 77.83 %, 44.36 % and
0.02 % on the pool's four fractions, and is quoted here rather than hidden: **the spread of
this measurement on a loaded machine is real**, and the owner should read the two-run figures
as the stable ones.)

### 8.2 What the curve says — three findings

1. **The saving grows as the support fraction shrinks**, on both workspaces, and the shape is
   the same: ~0 % at the whole workspace, ~27 % at three quarters, ~68 % at a third, ~78 % at
   a sixth.
2. **The ABSOLUTE saving scales with the workspace.** The pool's full reading is ~92 ms; the
   bundled one is ~3.1 ms. The same fraction therefore buys a saving of tens of milliseconds
   on one workspace and of about one on the other. **A percentage alone hides this**, and the
   percentage is what the earlier report quoted.
3. **The "null" is fully explained.** The bundled support covered **15 of 17 claims** — a
   fraction of **0.88** — and the curve at that fraction measures **−0.60 % and 2.79 %**,
   which is exactly the 2.7 % the earlier report quoted. **The null was the workload, not the
   mechanism.**

### 8.3 What it means for the programme — a decision the owner can act on

- **The sealed split's corpus must be a LARGE workspace.** A corpus of a few dozen claims
  cannot show this mechanism's value at **any** support fraction: the best the bundled
  workspace offers is ~25 % of a 3 ms reading. The research pool's **540 claims** is the
  shape that can.
- **The arm C support set must be a SMALL FRACTION of that corpus** — about a sixth — or the
  mechanism has nothing to save. A support set that covers most of the corpus is the case the
  mechanism was built for and does not help.
- **The programme may therefore expect a real cost advantage**, and it should not be
  surprised by one: on the pool at a sixth, the restricted reading is **~20 ms against
  ~92 ms**. What is still unmeasured is the **correctness** side — the `δ_MI` the protocol
  asks for — and that still needs the sealed set and the oracle's verdict on every answer.
- **The measurement is the reading, not the journey.** The artifact write is excluded, and it
  is common to both paths — but a journey's total cost would be a smaller difference. The
  saving quoted here is the reading's, and it is what the mechanism changes.

**The evidence:** `docs-v2/engineering/evidence/TEST-io.matrix.cognitive.report.ArmCCostMeasurementTest.xml`
— 3 tests, 0 failures: the curve, the small workspace's curve, and the subset invariant.

---
