# W53 — the epistemic comparison, case by case

**Status:** `ACTIVE` — the evidence of this wave: the baseline is W52's, and this wave did not move it.

## What this document is for

The mandate asks for the epistemic evaluation and a **per-case comparison**: not a single
number that happens to be the same as before, but the evidence that each of the 29 cases
still means what it meant, and that the distributed path did not change the epistemic
model's behaviour.

Three things are compared here:

1. the **baseline** — the W52 result, measured by the same runner on the same dataset;
2. the **W53 tree** — the same runner, the same dataset, the same 29 expectations, run
   against the tree that carries the distributed cognitive path;
3. the **distributed path's own effect** — what a remote report does to the epistemic
   model, and what it is not allowed to do.

## The measurement

| | |
|---|---|
| the dataset | `w50-plate-tectonics` version 1.1 |
| the runner | `scripts/w51-epistemic-eval.py` |
| the source | `docs-v2/demos/local-learning-loop/EVALUATION-SET.md` |
| the boundary | the JVM's HTTP and HTTPS clients are pointed at a closed port on the loopback interface; no request leaves the machine and no model is called |
| the machine-readable result | `docs-v2/engineering/W53-EPISTEMIC-EVALUATION.json` |
| the run this document describes | **2026-11-12**, against the tree that carries the whole wave — the peer-side stage measurement, the reconciliation timer, the named chain A stores, the reliability rule of §4m and the measured performance — on `feature/w53-distributed-cognition` at the head `64b1657a` with the wave's own rounds in the working tree |
| the command | `python3 scripts/w51-epistemic-eval.py --work /tmp/w53-eval-final --report docs-v2/engineering/W53-EPISTEMIC-EVALUATION.json` |

The run is **offline**: True.

**The table below was verified against that run, row by row.** All twenty-nine rows were
compared with the machine-readable result — the verdict, the observation, the first
recorded problem and the place it comes from — and every row matches. The table is not a
remembering of the earlier run: it is the current run's own record.

**And the machine-readable result did not change at all.** The run of 2026-11-11 against
the wave's final tree produced a file with the checksum
`957f97de55fa40ba5b4858babc023889` — **the checksum of the file committed on 2026-11-09**.
The wave's later rounds (the episode integration, the adjudication of the remote report,
the durable trace, the reconciliation timer, the named stores and the measured
performance) did not move a single byte of the epistemic evaluation: the same 18 of 29,
the same observations, the same failures for the same reasons. **A byte-identical result
is a stronger statement than an equal score**: it says that what the retriever does did
not change at all, not only that it did not change its verdict.

## The score

| | passed | failed | total |
|---|---|---|---|
| answerable | 2 | 6 | 8 |
| unanswerable | 6 | 0 | 6 |
| false-premise | 4 | 1 | 5 |
| multi-claim | 1 | 2 | 3 |
| knows-what-it-does-not-know | 2 | 0 | 2 |
| revision | 3 | 2 | 5 |
| **TOTAL** | **18** | **11** | **29** |

**18/29 — the W52 baseline, unchanged.** No expectation was edited,
no threshold was moved, and no case was removed: the W53 file
`docs-v2/engineering/W53-EPISTEMIC-EVALUATION.json` is byte-identical to the W51 runner's
output for the same tree (both files carry the checksum
`957f97de55fa40ba5b4858babc023889` of the run of 2026-11-11), which is the check that the
tree's changes do not touch the retriever.

The eleven failures are the ones W52 named — A-02, A-04, A-05, A-06, M-01, M-03, P-01,
P-02, F-02, R-04, R-05 — and they fail for the same reasons W52 recorded: the relevance
floor and the status filter in `KnowledgeRetriever`, and the evidence demand of the
multi-claim and revision cases. **That is a retrieval redesign, and it belongs to a
dedicated follow-up wave** (§14 of the mandate); W53 was explicitly not to rewrite the
retriever, and did not.

## The cases

The column "observed" is the runner's own observation of the case; the last column is
the first recorded problem and where it comes from.

| case | category | scope | verdict | observed | first problem (and where) |
|---|---|---|---|---|---|
| A-01 | answerable | S1 | PASS | answered=True; 41 claims; 2 sources | — |
| A-02 | answerable | S1 | FAIL | answered=True; 18 claims; 2 sources | the proposition is not cited: Convergent boundaries (destructive boundaries or active ma… (KnowledgeRetriever.java:386) |
| A-03 | answerable | S1 | PASS | answered=True; 30 claims; 2 sources | — |
| A-04 | answerable | S1 | FAIL | answered=True; 9 claims; 1 sources | the proposition is not cited: However, because plate motions are global in scale, they a… (KnowledgeRetriever.java:386) |
| A-05 | answerable | S1 | FAIL | answered=True; 16 claims; 2 sources | the proposition is not cited: Oceanic crust is formed at sea-floor spreading centers. (KnowledgeRetriever.java:386) |
| A-06 | answerable | S1 | FAIL | answered=True; 7 claims; 1 sources | the source usgs-this-dynamic-earth.md is absent (KnowledgeRetriever.java:386) |
| U-01 | unanswerable | S1 | PASS | answered=False; 0 claims; 0 sources | — |
| U-02 | unanswerable | S1 | PASS | answered=False; 0 claims; 0 sources | — |
| U-03 | unanswerable | S1 | PASS | answered=False; 0 claims; 0 sources | — |
| U-04 | unanswerable | S1 | PASS | answered=False; 0 claims; 0 sources | — |
| U-05 | unanswerable | S1 | PASS | answered=False; 0 claims; 0 sources | — |
| F-01 | false-premise | S1 | PASS | verdict=SUPPORTED; promoted=False; disputed=False; 5 evidence ref(s) | — |
| F-02 | false-premise | S1 | FAIL | verdict=UNRESOLVED; promoted=False; disputed=True; 5 evidence ref(s) | the verdict is UNRESOLVED, expected CONTRADICTED (PremiseAssessment.java:592) |
| F-03 | false-premise | S1 | PASS | verdict=UNSUPPORTED; promoted=False; disputed=False; 0 evidence ref(s) | — |
| F-04 | false-premise | S1 | PASS | verdict=UNRESOLVED; promoted=False; disputed=True; 5 evidence ref(s) | — |
| M-01 | multi-claim | S1 | FAIL | answered=True; 18 claims; 2 sources | the proposition is not cited: Convergent boundaries (destructive boundaries or active ma… (KnowledgeRetriever.java:386) |
| M-02 | multi-claim | S1 | PASS | answered=True; 86 claims; 2 sources | — |
| K-01 | knows-what-it-does-not-know | S1 | PASS | answered=True; 6 claims; 2 sources | — |
| K-02 | knows-what-it-does-not-know | S1 | PASS | answered=True; 6 claims; 2 sources | — |
| P-01 | answerable | P1 | FAIL | answered=True; 63 claims; 5 sources | the proposition is not cited: This feature is where seafloor spreading takes place along… (KnowledgeRetriever.java:386) |
| P-02 | answerable | P1 | FAIL | answered=True; 104 claims; 5 sources | the proposition is not cited: Oceanic trenches are the deepest parts of the ocean floor. (KnowledgeRetriever.java:386) |
| U-06 | unanswerable | P1 | PASS | answered=False; 0 claims; 0 sources | — |
| F-05 | false-premise | P1 | PASS | verdict=CONTRADICTED; promoted=False; disputed=True; 2 evidence ref(s) | — |
| M-03 | multi-claim | P1 | FAIL | answered=True; 25 claims; 4 sources | the proposition is not cited: Similar plant and animal fossils are found around the shor… (KnowledgeRetriever.java:386) |
| R-01 | revision | P1->R1 | PASS | claimsAdded=14; decisions=14; revisionsApplied=1 | — |
| R-03 | revision | R1 | PASS | answered=True; 126 claims; 7 sources | — |
| R-02 | revision | R1->R2 | PASS | claimsAdded=65; decisions=65; revisionsApplied=0 | — |
| R-04 | revision | P1' | FAIL | claimsAdded=92; decisions=92; revisionsApplied=2 | 2 supersessions applied, 0 expected (ModelRevisionDecider.java:52) |
| R-05 | revision | P1'' | FAIL | claimsAdded=45; decisions=45; revisionsApplied=1 | 1 supersessions applied, 0 expected (ModelRevisionDecider.java:52) |

## What the distributed path does to the epistemic model

This is the part that is genuinely new in W53, and it is a question about the *model*,
not about the retriever.

**A remote report is a secondary report.** It enters chain A through the governed bridge
receiver (`EpistemicBridgeSink`), with the same authentication, the same rights and the
same audit as every other message. It is stored as a report with its provenance — the
responder, the capability, the request, the execution trace — and it is **not** a belief.

**A report cannot promote itself.** The adjudicator (`RemoteReportAssessor`) decides
between six dispositions — INVALID, REJECTED, QUALIFIED, SUPPORTED, UNRESOLVED,
ABSTAINED — and it may answer SUPPORTED only when the report rests on evidence that is
*independent* of what chain A itself supplied. The confidence the peer declares is not
evidence: a basis of `DECLARED` or `MEASURED` never becomes support, and a report that
rests on the material chain A sent it comes back **QUALIFIED** at best.

**The lineage is preserved, and the proposition itself is compared too.** The request
carries the records chain A supplied (`SUPPLIED_KEY`); the report carries its lineage; the
adjudicator compares the two. And since round 18 it compares the **proposition** with the
propositions of those records as well: a peer that states the very proposition chain A
supplied cannot make it independent by leaving its lineage undeclared. A proposition that
travelled to chain B and came back is the same proposition, not a second one — the same
information returning through another process is not independent corroboration.

**Chain A's own knowledge is read, not assumed.** The adjudicator asks what chain A
already holds: `KnowledgeStoreEvidence` reads the knowledge store the bridge writes its
reports into — a citable claim with supporting evidence supports the proposition, a
candidate supports nothing (the rule that keeps a candidate from answering as an
established fact applies to chain A's own records too), and a claim whose evidence
contradicts the proposition contradicts it. Before that, the only answer available was
"nothing", and every report came back unresolved for want of a reading rather than for
want of evidence.

**The evidence for these claims** is not a claim in a report but the code and its tests:

| what is claimed | where it lives | what proves it |
|---|---|---|
| the report enters as a report, not as a belief | `io.matrix.bridge.EpistemicBridgeSink` | `TaskReportSinkTest` (4 tests) |
| a peer's confidence is not support | `io.matrix.cognitive.RemoteReportAssessor` | `RemoteReportAssessorTest` (7 tests) |
| a report resting on chain A's own material is qualified, not supported | `RemoteReportAssessor` | `RemoteReportAssessorTest` — the derived-from case |
| the remote result reaches the task as a traceable fact | `io.matrix.cognitive.distributed.CognitiveCoordinator` | `CognitiveCoordinatorTest`, `CognitiveCoordinatorRecoveryTest` |
| the end-to-end path keeps the rule | `scripts/w53-distributed-demo.sh` | `docs-v2/engineering/W53-DISTRIBUTED-DEMONSTRATION.json` — the verdict is `UNRESOLVED` while the report is `ACCEPTED` |

That last row is the whole of the W53 epistemic claim in one line: the peer's report was
accepted as a well-formed report, and the proposition still came back **unresolved**,
because the peer's confidence was `MEASURED` and chain A's own records settle nothing.
The remote execution worked, and the epistemic model refused to be impressed by it.

## The conclusion

- The W52 baseline of **18/29 is preserved**, case by case, with the same runner and the
  same expectations.
- The distributed path **does not degrade** the epistemic model: the remote report enters
  as a secondary report, it cannot bypass the `CANDIDATE` and evidence policies, and an
  unsupported or late result produces a qualification, an abstention or an unresolved
  proposition — never an established belief.
- **29/29 is not claimed and is not a target of this wave.** The remaining failures are
  a retrieval question and are recorded as deferred to a follow-up wave.
