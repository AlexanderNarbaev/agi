# E2 — the retrieval experiment (EVID-BASE, 2026-11-14)

**Status:** `ACTIVE` · **Branch:** `feature/evid-base-evidence-to-action`
**Runner:** `python3 scripts/w51-epistemic-eval.py` (the official 29-case evaluation)
**Harness:** `python3 scripts/e2-retrieval-experiment.py --floors … [--mode answer|cite]`
**What this document records:** what was measured, which hypothesis each measurement supports,
and the one negative result that stopped a production change.

---

## 1. The baseline, reproduced

Before any experiment the official evaluation was run on the packaged jar built from this
branch: **18 of 29 pass**, the documented eleven failing cases
(`A-02, A-04, A-05, A-06, F-02, M-01, M-03, P-01, P-02, R-04, R-05`), the per-category split
`answerable 2/8 · unanswerable 6/6 · false-premise 4/5 · multi-claim 1/3 ·
knows-what-it-does-not-know 2/2 · revision 3/5`. The artifact is `E2-FLOOR-0p25.json`.

## 2. The floor sweep (H1: "the relevance threshold is too restrictive")

The production code now reads the floor from the property
`matrix.retrieval.minimum-coverage` (default `0.25`, unchanged when unset — the contract is
tested in `KnowledgeRetrieverTest.theCoverageFloorOverride`). The sweep moved only that
property:

| Floor | Total | answerable | unanswerable | moved cases |
|---|---|---|---|---|
| **0.25 (default)** | **18/29** | 2/8 | **6/6** | — |
| 0.0 | 15/29 | **4/8** | **1/6** | `A-02`, `A-04` → PASS; `U-01`, `U-02`, `U-04`, `U-05`, `U-06` → FAIL |

**Reading of the measurement.** H1 is *partially confirmed and rejected as a fix*: lowering
the floor does recover two of the citation failures (`A-02`, `A-04` now cite their gold
claims) and it destroys the abstention on five of six unanswerable questions — a net
regression of three cases and, worse, exactly the "confident unsupported answer" the
epistemic boundary forbids. **The floor is not too high; it is doing two jobs at once.**
Artifact: `E2-FLOOR-SWEEP.json`.

## 3. The citation-floor experiment (the two-threshold hypothesis) — NEGATIVE

If one threshold serves both jobs, separate them: keep the answerability floor at `0.25`
(so the abstention stays) and cite evidence down to a lower citation floor. Implemented as a
second property (`matrix.retrieval.citation-coverage`, default = the answer floor, so
unconfigured behaviour is unchanged), swept at `0.0` and `0.10`:

| Citation floor (answer floor 0.25) | Total | answerable | unanswerable |
|---|---|---|---|
| 0.0 | 17/29 | 4/8 | **3/6** |
| 0.10 | 17/29 | 4/8 | **3/6** |

**Why it failed — measured.** The epistemic gates downstream of retrieval do not read the
*answerability* set; they read the **cited** set. At citation floor 0.0, `U-01` ("What is the
boiling point of water at the summit of Mount Kilimanjaro?") cites 14 claims about the role of
water in plate tectonics; one of them states a number, so the quantity boundary
(`QuestionDemand.quantityEvidence(answer.claims())` in `KnowledgeWorkbench.applyDemand`)
admits the answer and the system answers a question it must refuse. The direct probe is
recorded in this experiment's log: `answered=True, 14 claims, 2 sources` at citation 0.0,
against `answered=False, 0 claims` at citation 0.25.

**Decision.** The two-threshold change was **reverted before any commit**. The directive is
explicit: a candidate does not enter production because it raises a score; and this one
raises a score by weakening an epistemic boundary — the exact trade the owner reserved. The
existing implementation is preserved; the negative result is recorded here with its numbers.

## 4. What the measurements say about the eleven failures

| Cases | Measured cause | Evidence |
|---|---|---|
| `A-02`, `A-04` | the floor drops their gold claims **and** the same floor is what keeps `U-*` abstained (H1 as a trade-off) | they PASS at floor 0.0; `U-*` fails there |
| `A-05`, `A-06`, `M-01`, `M-03`, `P-01`, `P-02` | **not the floor**: they fail at 0.0 as well; their gold propositions exist in the corpus (checked: the required sentences are in `usgs-this-dynamic-earth.md` / `wikipedia-plate-tectonics.md`), so the cause is downstream of the candidate filter — either the claim's epistemic status keeps it out of the cited evidence (H2) or the ranking never lets it reach the answer (H3) | unchanged verdicts across the sweep |
| `F-02` | **downstream (H4)**: the verdict is decided by `PremiseAssessment.agreeOn`; the retrieval floor does not change it | verdict `UNRESOLVED` at every floor |
| `R-04`, `R-05` | **downstream (H4)**: over-supersession in `ModelRevisionDecider`/`KnowledgeRefiner`; the floor does not change it | `2` and `1` supersessions at every floor |

## 4b. Candidate recall and the exact cause of every citation failure (measured)

The candidate list was read without changing production code: the application was started with
`-Dmatrix.retrieval.minimum-coverage=0.0`, so every citable claim with any term match is cited
**in rank order**; the workspace's claims and their statuses came from
`GET /v1/workbench/workspaces/{id}/claims`. Artifact: `E2-CANDIDATE-RECALL.json`,
harness: `scripts/e2-candidate-recall.py`, command
`JAVA_HOME=<jdk-25> python3 scripts/e2-candidate-recall.py`.

| Fact | Measured |
|---|---|
| gold claim present in the workspace | **18 of 18 required propositions** |
| gold claim's epistemic status | **`SUPPORTED` for all 18 — citable** |
| cited exactly at floor 0.0 | **16 of 18** (ranks 1, 2, 9, 10, 11, 17, 33, 41, 129, 303, …) |
| cited nowhere even at floor 0.0 | **2 of 18** — no term match against the question at all |
| required source present at floor 0.0 | yes for `A-05`, `A-06`, `M-01`, `P-01`, `P-02` |
| **Recall@1 / @5 / @10 / MRR** (floor 0.0) | **3 / 4 / 6 of 18** · **MRR 0.2593** |

**The classification, per case:**

| Case | Measured cause |
|---|---|
| `A-02`, `A-04` | **the floor** — the gold claims are cited at ranks 1/2 and 9/11 at floor 0.0 and drop out at 0.25; both PASS at 0.0 |
| `A-05`, `A-06`, `M-01`, `P-01` | **the floor AND the term match** — part of their required propositions is cited exactly at floor 0.0 (ranks 11–129), another part is not cited at any floor |
| `M-03`, `P-02` | **the term match (H3)** — a required proposition ranks 303 and another never appears; `P-02`'s two required propositions are absent at floor 0.0 entirely |
| `F-02`, `R-04`, `R-05` | **downstream (H4)** — unchanged by any floor setting |

**H2 (the status filter) is refuted by measurement**: every gold claim is `SUPPORTED`, and the
status filter removed none of them. The evidence that remains is that the retrieval's TEXT
match misses two of the required propositions completely — that is the ranking/term-matching
hypothesis (H3), and it is the next experiment's target.

## 5. What this round did NOT establish

No production improvement survived. The next lever is **H3 (ranking/subject
representation)** — but measuring it requires the per-claim coverage of the failing
questions, which the current API does not expose. The next experiment therefore builds a
Java experiment harness over the same packaged corpus that reads the retriever's scored
candidates directly (a test-scope measurement; it does not change production code), and only
if that measurement shows a rankable separation is a production change proposed.

## 6. Artifacts

| Artifact | What it holds |
|---|---|
| `E2-FLOOR-0p25.json` | the baseline run (18/29) |
| `E2-FLOOR-0p0.json` | the answer-floor sweep at 0.0 |
| `E2-FLOOR-SWEEP.json` | the per-case comparison, answer floor |
| `E2-CITE-0p0.json`, `E2-CITE-0p10.json`, `E2-CITE-SWEEP.json` | the citation-floor experiment |
| `scripts/e2-retrieval-experiment.py` | the harness that runs the official runner at each configuration |

## 7. The one production change this round kept

`KnowledgeRetriever.configuredMinimumCoverage()` — the floor reads an optional system
property (`matrix.retrieval.minimum-coverage`), with the pre-existing `0.25` as the default
when the property is absent or unusable. This is the knob the next rounds' experiments need;
it changes no unconfigured behaviour, and it is covered by a contract test
(`KnowledgeRetrieverTest.theCoverageFloorOverride`, 13/13 tests in the class pass).
