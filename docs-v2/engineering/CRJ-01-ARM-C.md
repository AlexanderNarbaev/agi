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
developmental milestone: the sealed set does not exist, the oracle is not written, and no
three-arm comparison has been run. And one measured result is a **null**: on this workspace the
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
| the **producer's entry point** | `…/cli/LearnReportProcedureCommand.java` | the operator's command; it runs **alone** (its own `main`), so promoting a procedure does not boot the application or start the background trainers |
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

---

## 4. What is NOT claimed, and the measured null

| the claim | the state |
|---|---|
| **transfer to a different task** | **DEMONSTRATED, in one domain**: a question the procedure was neither proposed from nor validated on was answered by it. It is a **same-domain** transfer over a **17-claim** workspace |
| **the saving is worth anything** | **MEASURED NULL**: one measurement, **5.32 ms → 5.18 ms** (**2.7 %**), because the support covers **15 of the workspace's 17 claims**. The mechanism is right; the workload is too small for it to matter. A benchmark on a larger workspace is `NOT_INSTRUMENTED` |
| **the kernel chose the procedure for a measured reason** | **NOT YET**: the decision used the procedure's **DECLARED** complexity (1 ms against the reference's 25 ms), because the procedure had never run before and the ledger had no measurement of it. The first decision therefore rests on a **promise**; the next one would rest on the measurement. This is a real defect of the promotion path, named below |
| **the CRJ-01 developmental milestone** | **NOT DONE**: no sealed set, no independent oracle, no three-arm comparison, no `δ_MI = 2` measurement. The protocol's D5 — the freeze by a reviewer who is neither the case author nor a solver author — is still open |
| **the learned-claims half of arm C** (OD-082's second half) | **NOT DONE**: this slice builds the **procedures**. The learned claims need their own promotion-and-validation contract with a held-out-case rule, and the oracle (OD-081's D3) must exist before a claim can be validated against anything but the reference |
| **the quarantine list** | **SUPPLIED BY THE CALLER**: the gate is fail-closed, but the list's correctness is the caller's. The chain-A episode ids cannot yet be enumerated from the store (the journey does not open an episode row), so the operator names them |

## 5. The residual defects

| the defect | the state | the owner |
|---|---|---|
| the selection rests on the procedure's **declared** cost, not on a measured one | **OPEN** | the coordinator: the promotion should record the measured cost, or the selector should prefer a measured cost when it has one |
| the journey does not open an **episode row**, so a workspace's journeys cannot be enumerated from the store | **OPEN** | the coordinator: the journey's trace writes events; opening the episode would make the learning path discoverable without the operator naming the ids |
| the **quarantine list** is the caller's | **OPEN** | the programme: chain A needs the same role/quarantine discipline chain B has, or the caller's list must be audited |
| the arm C **cost** claim rests on one measurement over a 17-claim workspace | **OPEN** | the programme: the protocol's instrumented run |
| the learned-claims half of arm C | **OPEN** | the programme, after the oracle exists |

## 6. The decision: **CONTINUE**, with the next step named

The mechanism is built, gated and demonstrated; the honest measurement says the **capability
is real and the benefit is not yet**. The next step is therefore **not** more of this: it is

1. the **oracle** (OD-081's D3) and the **sealed set** with its freeze (D5) — without them, no
   `δ_MI` can be measured and arm C's correctness stays unclaimed;
2. the **learned-claims** half of arm C, validated against that oracle rather than against the
   reference — which is what could make arm C **more correct** rather than merely cheaper;
3. the two defects above (the declared cost, the episode row), which are cheap and make the
   promotion's own evidence honest.

**Nothing here is pushed past the owner:** the merge, the review and the freeze's reviewer stay
the owner's.
