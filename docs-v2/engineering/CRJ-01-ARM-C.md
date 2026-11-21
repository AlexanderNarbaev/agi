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
| **the kernel chose the procedure for a measured reason** | **MEASURED, and now the FIRST one is too**: the decision used to rest on the procedure's **DECLARED** complexity (1 ms against the reference's 25 ms), because the procedure had never run and the ledger had no measurement of it — and the cost curve of §8 showed that constant to be off by about **twenty times**. The promotion now **measures** the restricted reading it runs during the validation, the store carries that measurement beside the procedure's other validation measurements, and the method declares it. The decision's own basis is now `MEASURED_COMPLEXITY` — a third basis the selector grew for it, so a record can no longer call a measurement a promise. **Proven by** `ReportProcedureMeasuredCostTest`; the ledger's own measurement still replaces it once the method has run, and that transition is pinned by `ReportProcedureCostBasisTest` |
| **the CRJ-01 developmental milestone** | **NOT DONE**: no sealed set, no independent oracle, no three-arm comparison, no `δ_MI = 2` measurement. The protocol's D5 — the freeze by a reviewer who is neither the case author nor a solver author — is still open |
| **the learned-claims half of arm C** (OD-082's second half) | **NOT DONE**: this slice builds the **procedures**. The learned claims need their own promotion-and-validation contract with a held-out-case rule, and the oracle (OD-081's D3) must exist before a claim can be validated against anything but the reference |
| **the quarantine list** | **SUPPLIED BY THE CALLER**: the gate is fail-closed, but the list's correctness is the caller's. The chain-A episode ids **can now be enumerated from the store** — the journey opens its episode (the fix of 2026-11-21, `ReportJourneyEpisodeTest`), so `store.episodes(workspace)` lists them — but the list itself is still the caller's to name |

## 5. The residual defects

| the defect | the state | the owner |
|---|---|---|
| the **first** selection of a promoted procedure rests on its **declared** cost | **CLOSED (2026-11-21)** — §8.3 | the promotion now **measures** the restricted reading it runs during the validation (after a warmup pass, the median over the held-out cases), the store carries that measurement beside the procedure's other validation measurements, and the method **declares the measurement** rather than the portfolio's constant. The selector's vocabulary grew a third basis for it: `MEASURED_COMPLEXITY` — "the method has not run, but its complexity profile carries a measurement somebody took of the work it stands for" — so a decision record can no longer call a measurement a promise. Pinned by `ReportProcedureMeasuredCostTest` |
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
3. the **load-sensitive latency assertions** of the research suite (`Exp324` among them): they
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

### 8.3 The first decision now rests on a measurement — the declared cost is closed

The cost curve says the portfolio's constant (**1 ms**) was off by about **twenty times** on
the research pool, so the first decision was made on a promise that was wrong by an order of
magnitude. It is closed:

1. **the promotion measures the work it already does.** The validation runs the restricted
   reading on every held-out case; the promotion now **times** those runs — after a **warmup
   pass** (the first reading of a path pays the JIT's warmup, and a cost measured on a cold
   path is not the cost the method will pay when the serving process runs it) — and takes the
   **median**, for the same reason the cost curve does;
2. **the store carries the measurement.** It is a column beside the procedure's other
   validation measurements (`validation_cases`, `validation_agreement`), and it is **not** part
   of the procedure's definition: the immutability check does not compare it, and a later
   re-measurement never rewrites the one the promotion was made with. The table's W44
   migration path carries the column too, so a store written before it still migrates;
3. **the method declares the measurement**, not the constant — and the declaration carries it
   as a **structured marker** (`costMeasuredNanos=…`), not as prose;
4. **the selector's vocabulary grew a third basis**: `MEASURED_COMPLEXITY` — "the method has
   not run, but its complexity profile carries a measurement somebody took of the work it
   stands for". Without it a decision record would call a measurement "DECLARED_COMPLEXITY",
   which is a description of the number's origin and a falsehood about the number's standing.

**What is still true:** the reference's own declared cost (**25 ms**) remains a declaration,
and the ledger's measurement of the reference replaces it once the reference has run. The
procedure's first decision therefore compares a **measured** reading with a **declared**
reading — which is honest and is stated in the record, but it is not a comparison of two
measurements. That is the portfolio's own boundary, and the arm C slice does not move it.

**The evidence:** `docs-v2/engineering/evidence/TEST-io.matrix.cognitive.report.ReportProcedureMeasuredCostTest.xml`
— one test, zero failures: the promotion records the measurement, the method declares it, and
the decision's own reason names the basis.

### 8.4 What it means for the programme — a decision the owner can act on

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

## 9. The corpus report — how much of the research pool is LEFT (2026-11-21)

**The corpus question was settled only partly, and the part that was left is the part the
sealed split turns on.**

Two measurements had already settled the corpus's *shape*: the bundled photosynthesis package
is a **paraphrase** corpus (0 of 7 cited claims are spans), and a corpus imported through the
**production acquisition path** serves **verbatim spans** (483 of 540). But the sealed split
needs **fresh** evidence, and freshness was unmeasured: the three regression sets — the
29-case development set, the C2A 38-case set and the EPISTEMIC 16-case set — have all drawn
their gold from this pool or its seed corpus, and a claim that is one of their gold is **not
held-out proof** by the protocol's own §5.2.

**The instrument.** `Crj01CorpusReportBuilder` imports the research pool through the
**production import path** (`workbench.importDocument(...)`), reads the documents' **bytes**,
and classifies every claim the importer derives. It writes a byte-reproducible artefact,
`docs-v2/engineering/CRJ-01-CORPUS-REPORT.json`, and reads it back and compares it
byte-for-byte.

**The numbers.**

| | |
|---|---|
| documents in the pool | **7** |
| claims the production path derives | **540** |
| claims that are **verbatim spans** of their document | **483** (89.4 %) |
| claims that are **fresh** — no regression set has taken their proposition | **470** (87.0 %) |
| claims that are **both** | **413** (76.5 %) |
| gold propositions the three regression sets hold | 101 (31 + 59 + 11) |

**The verdict for the commission: the corpus is SUFFICIENT.** The sealed split needs **18**
domain-A cases; the pool offers **413** claims that are fresh and literal spans — a factor of
**23** over the requirement. The corpus does **not** need to be replaced, and the recipe the
earlier measurements gave the case author ("import through the production path, pick a gold
that IS a span") is **sufficient**.

**And the support fractions the cost curve needs are here.** The pool's seven documents hold
90, 45, 82, 14, 90, 152 and 65 claims, so a support set of **one** document is **0.1667** of
the corpus — **exactly** the fraction at which the cost curve measured a **78 %** saving. The
corpus reproduces the measurement's conditions without adjustment.

### 9.1 The instrument's own honesty — a defect it caught in itself

The first run of the builder reported **"540 of 540 claims are fresh"** — a number that
looked like excellent news and was **false**. The cause: the builder read the sets with
Jackson's `readTree`, which returns a `JsonNode` and **not** a `Map`, so the shape test was
always false and **no set was read at all**. An instrument that reads nothing reports
everything as fresh.

Two things were changed, and both are in the artefact:

1. the read is `readValue(..., Map.class)`, so the sets are actually read;
2. the builder now **asserts that it read something** — that the discovery found the sets and
   that each set's gold is non-empty. An instrument that cannot report a broken read is not an
   instrument.

**And the governance.** The repository holds that a held-out set "is referenced by its own
builder and its own reader, and by nothing else in the production or research code", and the
check is enforced on every file. So the corpus report **does not name** the held-out sets:
it **discovers** them by **shape** — a JSON file whose cases carry gold claims with
propositions is a held-out set — which needs no list to maintain and names none. The
instrument carries **no gold proposition and no question** of any set: it reads the sets at
run time, exactly as the repository's own adversarial probes do. The artefact was checked
against the three sets: the overlap between the report's 412 candidates and the sets' 101 gold
propositions is **zero**.

**The evidence:** `docs-v2/engineering/evidence/TEST-io.matrix.crj.corpus.Crj01CorpusReportBuilder.xml`
— one test, zero failures.

---

## 10. The arm runner — the FIRST three-arm comparison on the served path (2026-11-21)

**Until now the arms had been measured one at a time, by hand, and no run could produce the
evidence a measurement is made of.** The protocol requires that "every run produces
`CRJ-01-EVIDENCE.json` with **one row per (case, arm)**, including the rows that crashed,
were refused, were inapplicable, or timed out", that the arms run on "identical inputs"
under an "identical and explicit" budget, and that "each arm runs against its **own**
workspace id and its **own** output root". `Crj01ArmRunner` is that instrument.

### 10.1 What it does

For each arm, in the pre-registered order **A, B, C**: it builds the arm's **own** workspace,
imports the corpus through the **production** path, runs every case against the arm's
**forced** method, scores the served answer with the **source-span oracle**, and measures
the **wall** and the **CPU** time — the two G5 metrics that were `NOT_INSTRUMENTED` at the
baseline. Every row is kept, whatever happened.

**Arm B is measured twice**, and the two readings are kept apart: once on a **novel** task,
where the replay's precondition cannot hold, and once on an exact **repeat**, where it can.
The contract requires the replay advantage to be "reported separately from the genuinely-new-task
subset", and the two variants are what make that separation possible.

**Arm C promotes a procedure** before it runs — the promotion is what makes the arm an arm.
Its support is the **union of the sources the reference drew on** for the five held-out
questions, and the five questions are the ones whose union is the **narrowest** available,
because the mechanism's premise is that a narrower support suffices.

### 10.2 The dev run — 9 cases, 36 rows

**This is a DEV run on the solver's own split: a DEBUGGING measurement, never evidence.**

| arm | variant | n | grounded | wall median | wall total | CPU median |
|---|---|---|---|---|---|---|
| **A** — the reference | measured | 9 | **6** | **79.41 ms** | 962.8 ms | 74.27 ms |
| **B** — the replay | **novel** | 9 | **0** | 7.61 ms | 79.7 ms | 7.62 ms |
| **B** — the replay | **repeat** | 9 | **6** | **10.97 ms** | 112.0 ms | 9.71 ms |
| **C** — the promoted procedure | measured | 9 | **6** | **58.70 ms** | 639.0 ms | 54.54 ms |

**Three things this says.**

1. **The arms are EQUAL in correctness on the dev split: 6 of 9 each.** Arm C has not yet
   earned an advantage — and on the dev split it is not supposed to have one. What the run
   shows is that the mechanism **does not cost correctness**: the same six cases are
   grounded by the reference and by the procedure.
2. **Arm C is CHEAPER on the served path: 26.1 % by the median (58.70 ms against 79.41 ms),
   33.6 % by the total.** This is the first arm-vs-arm cost comparison on the served path,
   and it is consistent with the cost curve of §8: the procedure's support is a narrow
   fraction of the corpus, and the narrower reading is cheaper.
3. **Arm B is 7.2× cheaper on an exact repeat — and answers 0 of 9 novel tasks.** That is
   precisely the contract's requirement ("cached identical queries must be scored
   separately"), and the runner now makes the separation measurable rather than asserted.

### 10.3 Four defects the runner found in ITSELF — and one it found in the protocol

The runner was wrong four times before it was right, and each failure was a defect the
instrument had to be fixed for:

1. **the record's argument order.** `ReportRequest(question, reportName, consent,
   idempotencyKey, level)` — the runner passed the level and the idempotency key swapped, so
   every case shared the key "INTRODUCTORY" and the route refused all but the first with
   "the report name is already bound to another action identity". **The dev split's fresh
   idempotency keys are what exposed it.**
2. **the report name.** A name binds to ONE action identity, so a name shared by cases with
   fresh keys is refused. The name must come from the identity.
3. **the replay cannot prime itself.** Arm B's replay precondition is that the identity was
   ALREADY retained, so a replay that primed itself would refuse every time. The priming run
   uses the **reference**.
4. **a closed store.** The runner closed the kernel's store after arm C's promotion, and the
   route then failed every journey with "failed to read the events" — the trace is read from
   that store.

**And the one the runner found in the PROTOCOL — the important one.** The production claim id
embeds the **workspace id** (`DocumentImporter.claimId`: `"clm-" + digest(workspaceId + "|" +
contentHash + "|" + proposition)`), and the protocol requires that "each arm runs against its
**own** workspace id". **So the same document imported into three workspaces yields three
different claim ids**, and a sealed split that froze a gold claim id could be groundable by
at most **one** of the three arms: the other two would read as ungrounded for a reason that
has nothing to do with the arms. The oracle now **resolves the gold in the workspace under
test** — the gold is the source's claim *in the workspace the answer came from*, checked by
its byte-identical proposition and by its id there — and the recorded id remains the set's
provenance. **Without this, the protocol's own comparison could not have been made.**

**The evidence:** `docs-v2/engineering/evidence/TEST-io.matrix.crj.runner.Crj01ArmRunnerTest.xml`
— one test, zero failures; the rows are in `docs-v2/engineering/CRJ-01-EVIDENCE.json`.

---

## 11. The metrics — the protocol's verdicts, and what they found (2026-11-21)

**A run that produces rows nobody turns into a verdict is not a measurement.** The protocol
defines its metrics with their numerators, their denominators and the exact population each
denominator counts (§6), the row schema the evidence artefact must carry (§6.3), and the
uncertainty treatment for a small sample (§6.4). `Crj01Metrics` computes all of it from the
evidence artefact — so the same code reads the dev run's rows and the sealed run's rows.

### 11.1 What it computes

- the **paired outcome format** (§6.3): one row per (case, arm) with `outcome` ∈ {CORRECT,
  INCORRECT, REFUSED, INAPPLICABLE, ERROR} and `oracle_verdict` ∈ {GROUNDED,
  REFUSED_CORRECTLY, DISPOSITION_MISMATCH, UNGROUNDED, N/A}. **No row is deleted**: a crash
  is a row with `outcome = ERROR` and the exception text;
- the **domain-A metrics** M-A1 … M-A6, each with its numerator and its denominator, and
  **no metric reported without its denominator** (§6.4.3);
- the **paired difference** Δ with a **seeded bootstrap percentile interval** (B = 10000
  resamples, the [2.5, 97.5] percentiles) for Δ(C − A) and for Δ(C − B) on the **novel**
  subset, with the repeat subset reported separately (§6.4.1);
- the **exact McNemar test** with its **power limitation printed next to the p-value**: at
  n = 18 the test reaches p ≤ 0.05 only with at least 6 discordant pairs and **all** of them
  favouring one arm (§6.4.2).

**The machinery reproduces the protocol's own worked examples**: 6/6 discordant pairs → p =
0.03125, 5/6 → p = 0.21875 — and the dev run's Δ(C − B) on the novel subset **is** a 6/0
case, so the test returns **0.031250** on its own.

### 11.2 The dev run's metrics

| metric | numerator / denominator | what it says |
|---|---|---|
| **M-A1** grounded answer rate | **12/18** | arm A grounded 6 of 9 and arm C grounded 6 of 9 — the mechanism does not cost correctness |
| **M-A2** abstention correctness | **0/6** | the premise-bearing negative classes were **not** refused |
| **M-A3** unsupported-answer rate | **0/18** | every measured answer cited at least one gold |
| **M-A4** premise-disposition accuracy | **0/8** | no premise-bearing case carried the expected disposition |
| **M-A5** conflict-surfacing rate | **0/0** | the dev split holds no CONTRADICTORY case — the denominator is right |
| **M-A6** effect-verification rate | **18/18** | every written artifact was independently verified |

| the paired difference | n | wins | losses | Δ | the bootstrap interval | McNemar |
|---|---|---|---|---|---|---|
| **C − A** (all cases) | 9 | 0 | 0 | **0.000** | [0.000, 0.000] | — (no discordant pairs) |
| **C − B** (the **novel** subset) | 9 | 6 | 0 | **0.667** | [0.333, 1.000] | **0.031250** |
| **C − B** (the **repeat** subset) | 9 | 0 | 0 | 0.000 | [0.000, 0.000] | — |

**The C − A row is the honest one**: on the dev split the two arms are **indistinguishable**,
there are **no discordant pairs**, and the exact test has **no power at all** — which the
row says in words, because a p-value that is not significant with no discordant pairs says
nothing.

### 11.3 The finding the metrics found — and it is a confound, not a win

**Arm A and arm C report DIFFERENT epistemic dispositions for the SAME case.**

| case | arm A | arm C |
|---|---|---|
| **D-A06** (an UNSUPPORTED premise) | premise **AMBIGUOUS**, epistemic ANSWERED | premise **REFUTED**, epistemic **ABSTAINED** |
| D-A05, D-A07, D-A08 | premise AMBIGUOUS | premise AMBIGUOUS |

**The mechanism is the restricted support.** The premise semantics judges a premise against
the accounts the workspace holds about the question's subject — and arm C's answer is read
from a **narrower** support set, so the premise checker sees a **different account set**.
That is not a cost optimisation with the same answer: the arm C path is **not merely
cheaper, it believes differently**.

**Why this matters to the programme.** The hypothesis arm C is built on is that a narrower
reading answers the same question *for less*. If the narrower reading changes the
**epistemic disposition**, then a comparison of the two arms on **grounding alone** is
measuring two different systems, and any advantage arm C shows could be an artefact of the
narrower support rather than of the learned competence. **The sealed comparison must
therefore score the DISPOSITION as well as the grounding** — which is exactly what M-A2 and
M-A4 are for, and exactly why the protocol put them there.

**And the dev split's own negative cases are not passing**: M-A2 = 0/6 and M-A4 = 0/8,
because the premise semantics classifies all three premise-bearing subjects as **AMBIGUOUS**
(the corpus's accounts of plate tectonics disagree with each other, which is the semantics'
designed behaviour for a question about a subject whose accounts disagree). **That is a
property of the DEV SPLIT's authoring, not of the product**: the split's premise-bearing
cases need subjects whose accounts do NOT disagree. The sealed split's author must choose
subjects accordingly — and the split's classes must be checked against the corpus's
behaviour before the freeze, which is what the reviewer's §5.4 corpus-containment check is
for.

**The evidence:** `docs-v2/engineering/evidence/TEST-io.matrix.crj.runner.Crj01MetricsTest.xml`
— two tests, zero failures; the numbers are in `docs-v2/engineering/CRJ-01-METRICS.json`.

---

## 12. Domain B — the lamp world, and what it can actually measure (2026-11-21)

**Domain B had no instrument at all.** Domain A's arms are the served report route; domain
B's are **different classes** — the reference enumerates the hypothesis space, the settled
model answers only when the model has settled, and the replay serves the retained episode.
`Crj01DomainBRunner` runs them side by side, one row per (episode, arm), and scores the
answers with the **independently written truth table** (`LampTruthOracle`), whose own
cross-check must agree on all four assignments or the run is VOID.

### 12.1 The structure of the world — measured, and it decides everything

The protocol's domain-B metrics are built on a **transfer**: M-B4 is "transfer answer
accuracy" over "the sealed episodes' **novel** query assignments (assignments that episode
had not observed)". The lamp world has exactly **four** assignments, so the structure
measurement asks: for every possible step bound, is there both a novel query **and** an arm
that can answer it?

| the bound | the episodes | settled | novel queries | the reference answered | the settled model applicable |
|---|---|---|---|---|---|
| 1 | 9 | 0 | 27 | **0** | 0 |
| 2 | 9 | 0 | 18 | **0** | 0 |
| 3 | 9 | 0 | 9 | **0** | 0 |
| **4** | 9 | **9** | **0** | 0 | 0 |

**There is NO bound that gives both.** A bound below the domain's size leaves a novel
assignment — but then the surviving hypotheses **disagree** about it, and the reference
method **refuses**: it refuses rather than answering from a hypothesis set that does not
choose. A bound of the domain's size settles the model and leaves **no** novel query.

**⇒ The lamp world as it exists has NO transfer case. M-B4's denominator is ZERO**, and the
metric is reported **with its empty denominator** rather than quietly dropped. **An owner
who commissions 18 sealed domain-B episodes should know this BEFORE commissioning them:**
the rows would be produced, and the transfer metrics would divide by nothing.

### 12.2 What domain B CAN measure — a pure cost comparison

At the pre-committed bound (the domain's size), the queries are the episode's **observed**
assignments, and the arms answer them:

| arm | the episodes | correct | **probes** | settled | inapplicable |
|---|---|---|---|---|---|
| **A** — the reference | 9 | **36/36** | **36** | 9 | 0 |
| **B** — the replay | 9 | **36/36** | **0** | 9 | 0 |
| **C** — the settled model | 9 | **36/36** | **0** | 9 | 0 |

- **M-B1** the utility: 0.9375 per episode (1 − 1/16) — the model settles on every episode;
- **M-B2** the model settled: **9/9**;
- **M-B3** the probes to settle: **4** per episode;
- **M-B5** the cost per correct answer — the domain's **primary** metric: arm A pays
  **36 probes for 36 answers**, arm C pays **0**;
- **M-B6** the probes saved against arm A: **36** (arm C), and the replay's **36**;
- **M-B8** the termination honesty: **9/9** on every arm — the reported reason agrees with
  the survivor count;
- **M-B4** the transfer accuracy: **0/0** — vacuous, as measured above.

### 12.3 The finding: in domain B, arm C is INDISTINGUISHABLE from the cache

**The protocol's P1 is confirmed**: "arm C's *correctness* will be **equal** to arm A's on
every case" — 36/36 against 36/36.

**And the cost advantage is real: arm C pays 0 probes against arm A's 36.** But **arm B —
the exact replay — also pays 0 probes and also answers 36/36**. So in the lamp world as it
exists, **arm C's measured advantage over arm A is indistinguishable from the replay's
advantage over arm A.** The null's **first term is complete** in this domain.

The contract's own rule therefore applies verbatim: *"If arm C wins only exact repeats, do
not call it a competence gain."* **On domain B, it wins only exact repeats.**

**What this means for the programme.** The sealed domain-B split, as the protocol describes
it, would measure a cost advantage that the replay explains in full, and its transfer
metric would divide by zero. Three things follow, and the third is the owner's:
1. **the domain-B arm comparison is a rehearsal of the null**, not a test of the hypothesis —
   which is worth running once, and is NOT worth 18 sealed episodes on its own;
2. **the arm C mechanism needs a domain where a transfer case exists** — a world with a
   larger input domain, so that an episode can settle **before** exhausting it. That is a
   change to D1's domain B, and it is the owner's decision;
3. **domain A remains the decisive domain**: the corpus is large (540 claims), the support
   can be a small fraction of it, and the questions are novel by construction. The arm
   runner's domain-A rows — 6/9 grounded for both arms, arm C 26.1 % cheaper — are the
   measurement the programme can actually make.

**The evidence:** `docs-v2/engineering/evidence/TEST-io.matrix.crj.runner.Crj01DomainBRunnerTest.xml`
and `…Crj01DomainBStructureMeasurementTest.xml` — the rows are in
`docs-v2/engineering/CRJ-01-EVIDENCE-DOMAIN-B.json`.

---

## 13. The learned claim's scope — the concept alone was too coarse (2026-11-21)

**R-EPIS-16 is closed.** The learned claim's serving rule was: it applies when the answer's
**concepts** include the claim's concept, and nothing else. The concept is the right anchor
— a token rule would be the retriever's own rule, and the protocol names that confound
exactly (A3, "the gain is retrieval by overlap") — but the anchor was **coarse**: a question
about a concept received **every** learned claim of that concept, from **any** document,
including a claim validated against a document the question's evidence never touches, and
therefore about a different part of the concept altogether.

### 13.1 The rule now: the concept AND a common document

**A learned claim applies only when the answer's evidence draws on one of the sources the
claim was VALIDATED AGAINST** — its own source, and the sources observed with it.

**Why that is the right scope and not a weaker one.** The learner's **agreement gate**
established that the reference carried the claim on **every** held-out question, and those
questions' evidence came from exactly those sources. So the claim's validated scope IS "what
the workspace says about this concept, drawn from these documents", and the narrowed rule
applies the claim **exactly where that validation speaks**. It can only **extend** the
coverage of a body of evidence the answer already rests on; it can never import a document's
subject into a question that never touched it.

**And it fails CLOSED**: an answer with no sources, or a claim with no support, adds nothing.
The rule may not be defeated by missing data.

### 13.2 What the coarse rule cost — measured

Over the research pool (seven real documents, **one learned claim per document**, each a
verbatim span of its document) and **thirteen** questions:

| | |
|---|---|
| the coarse rule would add | **22** claims |
| the narrowed rule adds | **20** |
| the coarse rule **over-applied** | **2** claims — every one from a document the answer **never drew on** |

**The honest reading: 9 % is a real over-application and not a dramatic one**, and the reason
is visible in the corpus — the pool's seven documents are all about plate tectonics, so a
question about any of them draws on most of them, and **the concept genuinely is most of the
scope**. **The coarse rule's error is bounded by how topically homogeneous the corpus is.**
The defect grows with a corpus of unrelated documents, and the research pool is close to the
best case for the coarse rule.

**The evidence:** `docs-v2/engineering/evidence/TEST-io.matrix.cognitive.report.LearnedClaimScopeTest.xml`
(the rule: the claim of the same concept from an unrelated document does **not** apply, the
one from a shared document **does**, and the rule fails closed) and
`…LearnedClaimScopeMeasurementTest.xml` (the measurement). The serving path is unchanged
and still proven by `…LearnedClaimServingTest.xml`.

---

## 14. The freeze record — computed, re-derivable, and refusing to be written without its roles (2026-11-21)

**§5.5 requires the freeze record to carry nine things**, and §5.6 binds the order: "the
reviewer publishes the freeze record and the manifest hash **before** the first arm run". A
freeze record is therefore a thing a reader who **was not there** must be able to **check** —
otherwise it is a claim, not a record. There was no mechanism that computed it: the record
would have been a hand-written list of digests, and a hand-written list of digests is a list
nobody can re-derive.

### 14.1 What the record now is

`Crj01FreezeRecord` computes every field **from the artefacts** — the split, the builder
source, the two oracle sources, the corpus manifest, the repository's HEAD — so a reader can
re-derive each one and compare it. The test proves the re-derivation **independently**: it
recomputes the dataset's digest from the compact JSON of the cases array, recomputes the
generator's digest from the file's bytes, and resolves the branch SHA with `git rev-parse
HEAD` — three independent derivations that must agree with the record.

| the field | the value, and how it is derived |
|---|---|
| `dataset.sha256` | `56779f8ade0e…` — the UTF-8 bytes of the **compact** JSON `cases` array **+ one line feed** (the protocol's exact scope) |
| `dataset.fullFileSha256` | `ddb0c602a90d…` — the file as it stands, recorded **separately** so a re-serialised file and the file on disk are both traceable |
| `generator.sourceSha256` | `4dfb5491bd1d…` — the builder's bytes |
| `oracle.sourceSha256` | `dda2e3e8a3a5…` (domain A) and `c669dfb70825…` (domain B) — **separately**, because they are different instruments |
| `seeds` | the generator's seed `20261121` and the **27** pre-committed domain-B episode seeds |
| `corpusManifestSha256` | `fa7ba16e9bad…` — the corpus manifest's digest, which **matches the value the protocol records** |
| `branchSha` | the freeze's **own** commit — resolved from `.git/HEAD` and that branch's ref (loose or packed) |
| `instrumentCrosscheck` | the domain-B truth table against the world: **4 assignments, 4 agreements, 0 disagreements** |
| `roles` | the case author, the freezing reviewer, and the statement that they are different people and neither wrote a solver |

**The branch SHA is the freeze's OWN commit, not a named branch's.** The protocol says the
freeze records "the exact commit the freeze was taken on" and that it is "normally **not**
`b7bd1be4`" — the protocol's baseline. Reading a **named** branch would have been wrong
twice over: the local `develop` ref is **stale** until it is updated (the first run of this
mechanism recorded `b7bd1be4` — the baseline — and was therefore wrong), and the freeze may
legitimately be taken on a feature branch. So the record resolves `.git/HEAD`, and records the
protocol's baseline **beside** it, under `baselineSha`.

### 14.2 The roles are BINDING — and a machine cannot fill them

The record **refuses to be written** without its roles, and refuses when one person holds
both: *a freeze record that does not say who froze what is not a freeze record.*

**And the publisher writes PLACEHOLDERS, deliberately.** `Crj01FreezeRecordPublisherTest`
writes the record with the two role fields marked `UNNAMED` and the status
`DIGEST RECORD — … the record is NOT a valid freeze until the reviewer fills them and
publishes it`. A machine cannot name a person, and a record that names a person it invented
would be **worse than no record**. The protocol's requirement is a fact about **people**, and
the programme is the one that must assert it.

**So the sealed freeze is two steps**: the publisher writes the **digest record** (final and
re-derivable), and the **reviewer** fills the two role fields and publishes it. The digests
are ready; the names are the owner's commission.

**One property worth stating.** The record names the commit the freeze was taken on, which is
the commit **before** the record itself is committed — so the record's own commit is always
one step ahead of the SHA it carries. That is inherent to a record that names a commit: the
**sealed** freeze must therefore be taken on a commit and published as a **later** commit,
which is exactly the order §5.6 prescribes.

**The evidence:** `docs-v2/engineering/evidence/TEST-io.matrix.crj.runner.Crj01FreezeRecordTest.xml`
(the record is complete, the digests are re-derivable, the roles are binding) and
`…Crj01FreezeRecordPublisherTest.xml` (the publication). The record itself is
`docs-v2/engineering/CRJ-01-FREEZE-RECORD.json`.

---

## 15. The learned claim's contribution, SCORED (2026-11-21)

**The gap this closes.** The learned claim reaches the served answer — proven by
`LearnedClaimServingTest` — but its contribution was **not scored**: nothing measured
whether the claim the system adds to the evidence is a claim that **grounds**, nor whether
adding it ever makes an answer **wrong**. A learned claim that is cited but ungrounded is a
citation the reader cannot check, and one that makes an answer wrong is worse than none.

**The measurement.** Over the research pool (seven documents, **one learned claim per
document**, each a claim whose proposition occurs **verbatim** in its document's text — the
containment gate the learner enforces), **thirteen** questions asked through the served
route, and every citation scored by the **source-span oracle's own rule**: the cited
proposition is **byte-identical** to the proposition of the claim in its **own** document,
and that document literally contains it — the same rule the domain-A arms are scored by.

| | |
|---|---|
| the learned claims cited | **20** |
| **grounded** | **20** — every one, by the oracle's rule |
| **ungrounded** | **0** |
| cited **out of scope** (the claim's document not among the answer's sources) | **0** |
| **ADDED** by the learned claim — the reference **alone** did not carry it | **14** of 20 |

**What that says.** The learned claim's contribution is **grounded every time it is
cited**, and the narrowed scope rule (R-EPIS-16) **holds on the served path**: no citation
was made for a claim whose document the answer never drew on.

**And the capability is real and measured.** **14 of the 20 citations are evidence the
reference alone did not carry** — the learned claim added claims to the evidence of a
question the reference answered without them. That is the contract's **REUSE** row, which
asks for "a **different related task** that retrieves the accepted knowledge and **changes
the result**", stated as a number rather than as a claim: **70 %** of the citations the
learned claim contributed were ones the reference did not make.

**What it does not say.** This is the **research pool**, not the sealed set: the questions
are the pool's own, and the split is not an author's. The **precision** of the serving rule
on a *sealed* set — how often a learned claim is cited where it should not have been — is
what the owner's commission still has to say. What is established here is that the
mechanism's contribution **grounds** and that it **changes** the evidence.

**The evidence:** `docs-v2/engineering/evidence/TEST-io.matrix.cognitive.report.LearnedClaimOracleScoringTest.xml`.

---

## 16. The excerpt's cut hid 44 % of the corpus's real spans (2026-11-21)

**R-EPIS-22 is closed.** The learner's **containment gate** admits a candidate only when
the claim's proposition "occurs verbatim in the text of" its source — and it checks that
against the source's stored **excerpt**. But the excerpt is not the document:
`DocumentImporter` kept only the **first 8000 characters** of the text.

**So a claim whose proposition is a genuine verbatim span of the document, but which lies
PAST the cut, was refused** — and refused with the sentence *"it is a paraphrase of the
document rather than a span of it"*. **That sentence is false for those claims: they are
spans.**

### 16.1 The measurement

Over the research pool:

| | |
|---|---|
| the claims | **540** |
| a **verbatim span** of the document's text | **483** |
| found in the stored **excerpt** | **270** — was |
| a span but **BEYOND THE CUT** (refused, wrongly) | **213** — **44 %** of the corpus's real spans** |
| not a span at all | **57** |

**The learner could only ever see 270 of the 483 real spans** — and every one of the 213
it could not see was refused with a reason that was **false**. This is not a nuance: the
learner's reach was bounded by a **storage** decision, not by the evidence, and a claim
past the cut was not *rejected on the evidence* — it was **never examined**.

### 16.2 The fix: the excerpt must cover every claim the workspace asserts

The importer now **computes the proposals before it writes the source**, and sizes the
excerpt to the **highest claim span end** — the floor stays 8 000 characters, and the
excerpt grows to cover the claims. The span's offsets are in **code points** of the
extracted text, so the cut is measured in the same unit.

**After the fix: 483 of 483** spans visible, **0** beyond the cut. The corpus's highest
claim end is **29 455** characters.

**And this restores the design the importer itself declares.** Its own comment reads
*"THE EXCERPT IS THE TEXT OF THE SOURCE"*, and the instruments' document says the import
"is what makes the document's text the source's excerpt **and** the claim's proposition a
span of it". The 8 000-character cut silently broke that for 44 % of the corpus. **The
excerpt now holds 105 771 of the pool's 105 778 characters — it is the document's text.**

**The refusal reason is now TRUE.** It distinguishes the two cases by the claim's **own
span**: *"the workspace cannot show the text this claim points at … that is a gap in the
stored evidence, not a paraphrase"* from *"the document does not contain this wording"*.
A workspace imported before the change still holds such claims, and its reason must say
what is actually the case.

**The storage cost, measured**: the pool's excerpts hold **105 771** characters against
**51 246** under the old floor — **2.06×**, about **53 KB** more over seven documents.
That is the price of the workspace's evidence being complete, and it is the owner's
number to weigh against the alternative.

### 16.3 The learner's precision — measured, and it REFUTES an earlier assumption

The earlier rounds recorded "the learner promotes **every** candidate (**23** from one
journey)" as an open concern. **Over this corpus that is not what happens**, and the
measurement says so:

| | |
|---|---|
| the questions | **26** |
| the observation triples whose intersection is carried by **five or more** questions | **108** of 2 600 — **4.2 %** |
| the candidate set | **3** |
| **promoted** | **2** — the third failed a gate |
| distinct **propositions** | **2** |
| distinct **concepts** | **2** |
| distinct **supports** | **1** — so they all become applicable **together** |
| **redundant** promotions (the same proposition twice) | **0** |

**So the "23 promotions" figure is NOT reproduced**: the learner promotes what passes the
gates, the promotions are **distinct facts**, and there is **no redundancy** here. What the
measurement *does* show is that the **agreement gate is demanding**: over 26 questions,
only **4.2 %** of the observation triples make it satisfiable at all — the gate needs five
held-out questions that **all** carry the candidate, and the corpus's questions share
claims too narrowly for that to be common. That is a property of **the corpus**, and it is
a constraint the sealed split's author must know.

### 16.4 What the fix does for the sealed split — the REACHABILITY check

**The oracle and the learner read DIFFERENT texts.** The oracle reads the corpus **file**;
the learner's containment gate reads the source's stored **excerpt**. So a candidate the
learner cannot see is a candidate the sealed split **cannot use** — and the corpus report
did not say so.

The corpus report now counts **reachability**, and it is the pre-flight check the sealed
split's author needs:

| | before the fix | after the fix |
|---|---|---|
| the claims | 540 | 540 |
| a **verbatim span** of the document's text (the oracle's read) | 483 | 483 |
| **whose span the learner can see** (the learner's read) | **270** | **483** |
| the **fresh-and-span candidates** | 413 | 413 |
| **the fresh-and-span candidates the learner can see** | **213** | **413** |

**So the sealed split's usable candidates went from 213 to 413 — 1.94×** — and the 18 cases
the protocol needs are now not merely *measurable* but **reachable**. Before the fix, a
case whose gold sat past the 8 000-character cut would have produced a row that could
never be learned from.

**The evidence:** `docs-v2/engineering/evidence/TEST-io.matrix.cognitive.report.ClaimSpanBeyondExcerptTest.xml`
(the measurement, the fix and the storage cost) and
`…ReportClaimLearnerPrecisionTest.xml` (the precision). The reachability is in
`docs-v2/engineering/CRJ-01-CORPUS-REPORT.json`.

---
