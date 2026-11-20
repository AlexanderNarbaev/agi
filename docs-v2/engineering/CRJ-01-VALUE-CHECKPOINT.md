# CRJ-01 — Value checkpoint: the reliability milestone of the cognitive development programme

**Status:** ACTIVE · **Date:** 2026-11-20 · **Branch:** `develop` · **Baseline SHA:** `b7bd1be4`
**Commission:** `docs-v2/engineering/AGENT-COMMISSION-COGNITIVE-PROGRAM.md` (C0 → C2)
**Program:** `docs-v2/governance/COGNITIVE-DEVELOPMENT-PROGRAM.md` · **Contract:** `docs-v2/engineering/CRJ-01-ACCEPTANCE.md`
**Owner decisions taken this round:** **OD-079** (the idempotency semantics), **OD-080** (the Article I instrument)

---

## 0. The answer in one paragraph

The programme's first executable milestone is **done and measured**: a served-path defect that
the source only *suggested* is now **reproduced** (a stale success over real HTTP, red before
the fix), **fixed** under the owner's decision, and **re-proved green** — the exact replay still
replays, and a reused key with a changed question is now an explicit `409 REFUSED_IDENTITY`
instead of the earlier journey's report. In the same round the Article I decision-path
instrument was **narrowed to its true reach** (`R-EPIS-10` closed, the accepted baseline
untouched), and the CRJ-01 local journey was demonstrated over the **real packaged
application**: 16 of 16 checks, including a restart. What is **not** done — and is not claimed
— is the developmental milestone: no transfer to a different task has been shown, because the
preregistration is not frozen and no arm C exists yet.

---

## 1. What was executed, and what it produced

| the id | the task | the classification | the measured outcome | the evidence |
|---|---|---|---|---|
| **CRJ.0.7** | the changed-payload idempotency reproduction | `RESEARCH_INFORMATION_GAIN` + `PRODUCT_GAIN` | **PROVEN**: at `b7bd1be4` a reused key with a different question answered **HTTP 200 `REPLAYED`** carrying the earlier report's digest (`efe56890…`); after the fix it answers **HTTP 409 `REFUSED_IDENTITY`**, writes no second artifact and leaves the first artifact's digest unchanged | `docs-v2/engineering/evidence/CRJ-01-REPRODUCTION-idempotency-changed-payload.xml` (3 tests, **1 failure**) and `…/TEST-io.matrix.knowledge.ReportIdempotencyConflictTest.xml` (3 tests, **0 failures**) |
| **CRJ.0.7 (fix)** | the honest replay domain (OD-079) | `PRODUCT_GAIN` | the replay's applicability domain is the **request**: the retained journey names the question and the level, and a record that names neither is not replayable (fail-closed). **No `taskId` moved**; the identity derivation is untouched | the same two XMLs; the packaged journey's `409` over real HTTP |
| **CRJ.0.4** | the Article I instrument (R-EPIS-10, OD-080) | `ENGINEERING_MAINTENANCE` | the exemption is **member-granular**: an `import` no longer arms it, and the member's declaration line is part of the member. The gate is **green with the baseline unchanged** | `…/TEST-io.matrix.auditor.DecisionPathImportExemptionTest.xml` (2 tests), `…/TEST-io.matrix.auditor.DecisionPathGateTest.xml` (2 tests), the whole auditor suite **11 suites / 40 tests / 0 failures** |
| **CRJ.0.4** | the probe family (R-EPIS-11) | `RESEARCH_INFORMATION_GAIN` | the `HardReview*`/`AdversarialProbe*` family is **11 classes, 51 methods, 79 assertion calls, 213 print calls, 0 pure gates** — 5 measurement-only, 6 mix. **It covered no idempotency probe at all**, which is one reason the defect survived | `docs-v2/engineering/CRJ-01-RISK-VERDICT.md` §2.2 |
| **CRJ.0.1 / CRJ.0.2** | the reachability map (COG-00) | `RESEARCH_INFORMATION_GAIN` | the verdict is **PARTIAL**: one connected served path (Chain A's report journey), five named break points — the learning path stops at the promoted rule, the kernel never delegates, the crossing is conditional, **Chain A's "later task" is the same task**, and the two chains' learning stores never meet | `docs-v2/engineering/CRJ-01-REACHABILITY.md` (535 lines) |
| **CRJ.0.6** | the CRJ-01 preregistration (COG-01) | `RESEARCH_INFORMATION_GAIN` | a **draft, not frozen**: two domains (a new third corpus; the existing lamp microworld, whose input domain is 4 and therefore has **no generalization gap**), three arms, two independent oracles, the negative controls, and five owner decisions still open | `docs-v2/engineering/CRJ-01-PROTOCOL.md` (950 lines) |
| **CRJ.2.2** | the local journey over the packaged route | `PRODUCT_GAIN` | **16 of 16 checks**: the source-bearing corpus enters through the acquisition route (**348 claims, 2 sources**); the question is answered with **41 cited claims**, every one of them in the store and every one naming a source; the trace records **PLAN, PERMISSION, ACTION, OBSERVE, VERIFY, RESULT, RETAINED**; the plan names the method and the reason; the artifact on disk carries the reported digest; the index holds **one STAGED and one COMMITTED** record; no consent writes nothing; the exact replay replays; the changed question is refused; **after a restart** the repeated request is still the same replay and the conflict is still refused | `docs-v2/engineering/CRJ-01-JOURNEY-DEMONSTRATION.json`; the script `scripts/crj01-journey-demonstration.py` |

**The regression denominator.** The targeted suites that cover the touched paths — the report
journey, the closed loop and its measurement, the counterfactual, the restart, the fingerprint,
the route's own contract, the epistemic package, the live-store journey and the whole auditor
package — are **36 suites / 160 tests / 0 failures / 0 errors / 0 skipped**, and their XML is
retained in `docs-v2/engineering/evidence/`.

---

## 2. The evidence chain

Every number above names a file that exists in the repository. The chain, in the order the
project's own rule requires — *the evidence is the XML, not the console summary*:

| the claim | the artefact |
|---|---|
| the defect existed at the baseline | `docs-v2/engineering/evidence/CRJ-01-REPRODUCTION-idempotency-changed-payload.xml` — the same test class, the fix stashed, **1 failure**, the equal digests in the message |
| the defect is fixed | `docs-v2/engineering/evidence/TEST-io.matrix.knowledge.ReportIdempotencyConflictTest.xml` — **3 tests, 0 failures** |
| the instrument's reach is true | `docs-v2/engineering/evidence/TEST-io.matrix.auditor.DecisionPathImportExemptionTest.xml` — the plant is caught and blocks; the annotated member is still exempt |
| the gate is green with the baseline unchanged | `docs-v2/engineering/evidence/TEST-io.matrix.auditor.DecisionPathGateTest.xml` |
| the journey is real, over a socket, and survives a restart | `docs-v2/engineering/CRJ-01-JOURNEY-DEMONSTRATION.json` — 16 checks, each with its own denominator, plus the server logs under `data/crj01-demo/` |
| the journey's code is where the report says it is | `docs-v2/engineering/CRJ-01-REACHABILITY.md` (every link with a `path:line`) and `docs-v2/engineering/CRJ-01-RISK-VERDICT.md` |

---

## 3. The resource expenditure — what it actually cost

| the item | the cost | the note |
|---|---|---|
| the heavy Gradle runs | **9 invocations**, of which one was the 160-test regression (**1 m 14 s**), two were the auditor package (**4 s** each), one was the packaged build (**8 s**, incremental), and the rest were the single-class runs | **one** `matrix-core` JVM at a time; `--offline` throughout; **no** `--rerun` (the Quarkus native build is never triggered) |
| the packaged journey | **2 runs** of the script, each = one incremental build + **two** server starts + the requests; the second run was the one that passed **16/16** | the server is the packaged `quarkus-run.jar`, started as a process on port 9098 |
| the CPU / RAM / wall time of the journey | **NOT_INSTRUMENTED** — the script measures the *checks*, not the resources | the protocol marks the per-arm cost `NOT_INSTRUMENTED` too, and it stays so until an instrumented run fills it |
| the agents | **3 durable teammates + 2 managed subagents** were dispatched for the four read-only analysis tasks. **Five process failures** occurred (the teammates' runs died twice each mid-task). The **coordinator executed CRJ.0.3 and CRJ.0.4 itself**; the reachability map and the protocol were delivered by the surviving workers | a real cost, and a real lesson: the plan's "4 read-only agents" assumption did not hold on this host, and the critical path did not depend on it |
| the CI | **the fast lane ran on the pull request** and is **green**: `CONSTITUTION + frozen boundary gate`, `Documentation consistency`, `Scope the pull request`, `Secret scan`, `Compile + targeted tests`, `Compile + targeted tests (matrix-core)` (1 m 55 s) and `build` (2 m 55 s) — the runs `44759150245` and `44759150247` | the lane is the technical gate; **the merge is the owner's**, and the review is the owner's as well — this repository has a single collaborator |
| the tokens | **NOT_INSTRUMENTED** | no cost meter was read in this session |
| the risk budget | none: no production data written, no network exposure, no protected file touched, no live store opened | the demo's own store is a fresh SQLite file under `data/crj01-demo/` |

---

## 4. The remaining defects — named, with their owners

| the defect | the state | the owner |
|---|---|---|
| the **developmental** milestone: no transfer to a different task is demonstrated; the reuse that exists is the **exact replay** (arm B) | **OPEN** | the programme: it needs COG-01 frozen and an arm C that exists |
| the CRJ-01 preregistration is a **draft**: the domains, the arms, the oracle, the minimum meaningful improvement and the freeze are five open owner decisions | **OPEN** | the owner (D1–D5 in `CRJ-01-PROTOCOL.md` §10) |
| the **microworld domain has no generalization gap** (input domain 4): arm C cannot be more *correct* there, only cheaper — so the protocol's primary metric for that domain is cost | **OPEN, and recorded** | the science specialist's finding; the owner's D1 |
| the `HardReview*`/`AdversarialProbe*` family remains **mostly measurement**: 5 classes assert nothing | **OPEN as a labelled gap** | the coordinator (Level 1, next wave) |
| the idempotency path had **no adversarial probe** before this round | **CLOSED for the changed-payload case**; the family as a whole still does not probe it | the coordinator |
| the retained journey's **question and level** are new fields: a record written before the change is not replayable (fail-closed), and such records are not migrated | **OPEN by design** — the alternative was guessing which question an old record answers | the programme |
| the **exact replay's** cost advantage was measured on the *same* task; the CRJ-01 protocol requires it to be re-measured against arm C on different tasks | **OPEN** | the programme |
| the `data/crj01-demo/` directory and the demo's SQLite store remain on disk after the run | **OPEN, harmless** (the demo's own directory, under the ignored `data/`) | the coordinator; removed with `rm -rf data/crj01-demo` on request |
| the owner's live store was **not** opened, migrated or written | **no defect** — stated as a fact of the run | — |

---

## 5. A premise of the recommendation that the measurement refuted

The recommendation put to the owner for the Article I instrument said: *"fix the auditor and
extend the accepted baseline with three lines"*. The owner approved it. **The implementation
showed the premise was wrong**, and the report says so rather than shipping the three lines:

- the three files that were outside the gate's reach (`ArtifactWriter`, `LiveStoreMigration`,
  `EpisodeQuarantineTool`) **adjudicate their own reads** — each wall-clock read sits inside a
  properly annotated member;
- a permitted, adjudicated finding **does not block** the gate, so those reads never belonged in
  the baseline;
- the gate therefore stayed **green with the baseline unchanged**, and the instrument's reach
  grew for free.

The baseline was left untouched. The owner's approval covered the change; the measurement
narrowed it, and the difference is recorded here.

A second finding came out of the same work: the repository's own
`DecisionPathAuditorTest.articleIPermittedAnnotationClearsASite()` **failed** on the first
version of the fix, because a member written on one line (`public long stamp() { return
System.currentTimeMillis(); }`) falls outside its own annotation under a pure depth rule. The
existing test was passing **for the wrong reason** — the file-granular exemption had been doing
the work. The declaration-line rule closed it.

---

## 6. The decision: **CONTINUE**

| the criterion | the reading |
|---|---|
| was the capability improved? | **Yes, measurably**: a served path stopped reporting a success for a question it did not answer, and the claim is now backed by a red-before / green-after pair and by 16/16 checks over real HTTP |
| was the hypothesis falsified? | **No** — the null hypothesis of the CRJ-01 experiment (that any improvement is replay, preexisting rules, lexical overlap or selection bias) is **untouched**: no arm C exists, so no transfer claim is made |
| is the next step blocked? | **Partly**: COG-01 needs the owner's D1–D5; COG-03's developmental milestone needs a frozen protocol and a new arm C. Neither blocks the *reliability* work that remains |
| is there new information? | **Yes**: the reachability map's five break points, the microworld's absent generalization gap, and the probe family's measured classification are all new |
| would more of the same help? | **No** — the two defects are closed; another optimisation round on them would be wasted |

**CONTINUE**, with the next wave being: (a) the owner's D1–D5 on the protocol, (b) an arm C that
exists at all, and (c) the conversion of the measurement probes into gates, one class at a time.

---

## 7. The owner questions that were asked, and the answers

| the question | the answer | the effect |
|---|---|---|
| the semantics of a reused idempotency key with a changed payload | **B — the honest replay domain and an explicit conflict** | OD-079; the fix; the `409` |
| the Article I instrument | **fix the auditor and extend the baseline** | OD-080; the fix; **and the measurement showed the baseline extension was unnecessary** (§5) |
| the scope of the next executable slice | **reliability first, then the journey over the existing route** | the order this round actually executed |
| the commit and the PR | **commit and open a PR** | the branch, the commits and the pull request; **no merge** — the merge stays the owner's |

---

## 8. The wave's files

**The production changes (5 files).** `ReportJourney.Retained` (the question and the level),
`ReportJourneyTrace` (the payload), `ReportJourneyMethod` (the two facts and the preconditions),
`ArtifactReportResource` (the fact set and the retained payload), `DecisionPathAuditor` (the
member-granular exemption).

**The new tests (2 classes, 5 tests).** `ReportIdempotencyConflictTest` — the replay contract,
written as a **gate** and proven red before the fix; `DecisionPathImportExemptionTest` — the
instrument's reach, written as a **gate** with a planted read.

**The new artefacts (7).** `CRJ-01-REACHABILITY.md`, `CRJ-01-RISK-VERDICT.md`,
`CRJ-01-PROTOCOL.md`, `CRJ-01-EXECUTION-PLAN.md`, `CRJ-01-JOURNEY-DEMONSTRATION.json`,
`CRJ-01-VALUE-CHECKPOINT.md` (this file), and the script
`scripts/crj01-journey-demonstration.py`.

**The canonical updates.** `ROADMAP.md` (the COG rows' states), `DECISION-REGISTER.md` (OD-079,
OD-080), `RISK-REGISTER.md` (R-EPIS-10 closed, R-EPIS-11 measured), and the retained evidence
in `docs-v2/engineering/evidence/`.
