# E2 — the retrieval experiment (EVID-BASE, 2026-11-14)

**Status:** `ACTIVE` · **Branch:** `feature/evid-base-evidence-to-action`
**Runner:** `python3 scripts/w51-epistemic-eval.py` (the official 29-case evaluation)
**Harness:** `python3 scripts/e2-retrieval-experiment.py --floors … [--force]` (one mode: the answer floor)
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

**Provenance of those two numbers (corrected after the independent review).** They were
measured on a temporary build that carried the two-floor change, which was reverted before
any commit. `matrix.retrieval.citation-coverage` is read by no production code in this tree,
so the CITE runs are **not reproducible here**; the artifacts were removed and the numbers
are kept only as the record of the experiment's result.

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
| gold proposition entries in the artifact | **27 entries, 22 distinct propositions** (the dataset's required propositions per case) |
| gold claim present in the workspace | **27 of 27 entries** — all present |
| gold claim's epistemic status | **`SUPPORTED` for all 27 — citable** |
| cited exactly at floor 0.0 | **16 of 27 entries** (ranks 1, 1, 1, 2, 9, 10, 11, 12, 13, 17, 17, 33, 41, 129, 303, 368) |
| cited nowhere even at floor 0.0 | **11 of 27 entries** — no term match against the question |
| required source present at floor 0.0 | yes for `A-05`, `A-06`, `M-01`, `P-01`, `P-02`; `None` for `M-03` (no required source in that case) |
| **Recall@1 / @5 / @10 / MRR** | **3 / 4 / 6 of 16 cited** · **MRR 0.2593** (mean 1/rank over the 16 cited; **0.1536** over all 27 with misses counted as zero) |

**The classification, per case (entries cited of entries required):**

| Case | Measured cause |
|---|---|
| `A-02`, `A-04` | **the floor** — the gold claims are cited at ranks 1/2 and 9/11 at floor 0.0 and drop out at 0.25; both PASS at 0.0 |
| `A-05`, `A-06`, `M-01`, `P-01` | **the floor AND the term match** — part of their required propositions is cited exactly at floor 0.0 (`A-05`: 3 of 4, `A-06`: 2 of 3, `M-01`: 2 of 3, `P-01`: 1 of 3), another part is not cited at any floor |
| `M-03`, `P-02` | **the term match (H3)** — `M-03`: 2 of 5 cited (one at rank 303); `P-02`: 0 of 3 cited at floor 0.0 |
| `F-02`, `R-04`, `R-05` | **downstream (H4)** — unchanged by any floor setting |

**H2 (the status filter) is refuted by measurement**: every gold claim is `SUPPORTED`, and the
status filter removed none of them. The evidence that remains is that the retrieval's TEXT
match misses two of the required propositions completely — that is the ranking/term-matching
hypothesis (H3), and it is the next experiment's target.

## 4c. The middle floors, and why the floor cannot be the fix (measured)

The sweep was extended to the middle of the interval to test whether a floor exists that
recovers the answerable cases **and** keeps the abstentions:

| Floor | Total | answerable | unanswerable | source report |
|---|---|---|---|---|
| 0.25 (default) | 18/29 | 2/8 | 6/6 | `E2-FLOOR-0p25.json` |
| **0.15** | 17/29 | 4/8 | **3/6** (`U-01`, `U-05` break) | `E2-FLOOR-0p15.json` |
| **0.10** | 15/29 | 4/8 | 1/6 | `E2-FLOOR-0p10.json` |
| 0.00 | 15/29 | 4/8 | 1/6 | `E2-FLOOR-0p0.json` |

(The comparison artifact `E2-FLOOR-SWEEP.json` covers the two floors measured together —
0.10 and 0.15; the 0.25 and 0.0 numbers are in their individual reports. A reproduce run
must pass `--force`, because the harness reuses a committed report by default.)

**No floor in the interval separates "the workspace should cite this claim" from "the
workspace should answer at all".** The gain of `A-02`/`A-04` comes only with a loss of three
abstentions at 0.15 — the cost rises as the benefit rises. That is the measurement that rules
the single-floor sweep out as a production fix, and it is why the answer floor stays at 0.25.

## 4d. The residual: semantic adjacency the representation does not carry (measured)

The two propositions cited at no floor (`P-02`'s "Oceanic trenches are the deepest parts of
the ocean floor", `M-03`'s copy) were traced to their concept:

```
claim clm-2a740579  status=SUPPORTED  concept=d542c957
concept name="Oceanic trenches"      definition=None
representation mentions trench: true   oceanic: true
                          subduction: false   produce: false   forms: false   volcan: false
```

The question is `"What is subduction, and what does it produce?"` — the claim answers it
through the **semantic relation subduction → trenches**, and that relation is nowhere in the
workspace: the concept's definition is **empty**, and no relation edge exists. The retrieval
is lexical; it cannot recover a fact the representation does not carry. The same shape holds
for the volcanic-arc proposition.

**So the deepest E2 residual is not a ranking bug — it is an enrichment gap** (concept
definitions and inter-concept relations), which is a data/architecture decision for the
owner, not a threshold or a scoring patch. The next measurement that matters is how many
concepts have a definition at all.

## 5. What this round did NOT establish

No production improvement survived. The next lever is **H3 (ranking/subject
representation)** — but measuring it requires the per-claim coverage of the failing
questions, which the current API does not expose. The next experiment therefore builds a
Java experiment harness over the same packaged corpus that reads the retriever's scored
candidates directly (a test-scope measurement; it does not change production code), and only
if that measurement shows a rankable separation is a production change proposed.

## 5b. The held-out question, stated plainly

The sweep above ran on **all twenty-nine cases** — the whole documented corpus — and there
is **no held-out partition**: twenty-nine cases do not divide into statistically useful
subsets, and the directive anticipates exactly this ("if the original 29 cases cannot be
divided into statistically useful subsets, add a separately identified evaluation set with
independently defined expectations").

Two things follow, and both are stated here rather than implied:

1. **No generalization claim is made.** The sweep's numbers are a **diagnostic of the
   mechanism** ("what happens to each case when the floor moves"), not an estimate of
   accuracy on unseen cases. Because **no candidate was adopted** (§5, §7), the absence of
   a held-out split cannot inflate any production claim: the only production change is an
   inert knob whose default is unchanged.
2. **What a held-out evaluation would take.** A separately identified case set with
   independently defined expectations over the same licensed corpus, and a sweep measured
   on it without ever having been read during a parameter choice. The corpus is the
   project's own `w50-plate-tectonics` seed (two documents, 41 claims); a second case set
   means a second set of independently verified propositions, which is a **content** task
   (reading and verifying propositions against the source), not a threshold task. That is
   why the next cycle's first priority is the concept enrichment the residual points at
   (§4d), not another sweep.

**The honest summary:** the floor is refuted as the cause of the eleven failures, the
status filter is refuted, and the residual is a representation gap — with **no production
improvement claimed and no held-out generalization number available**.

## 6. Artifacts

| Artifact | What it holds |
|---|---|
| `E2-FLOOR-0p25.json` | the baseline run (18/29) |
| `E2-FLOOR-0p0.json` | the answer-floor sweep at 0.0 |
| `E2-FLOOR-SWEEP.json` | the per-case comparison, answer floor |
| (no citation-floor artifacts are in the tree: that experiment needed a temporary build that was reverted; the measured numbers are in §3) | — |
| `scripts/e2-retrieval-experiment.py` | the harness that runs the official runner at each configuration |

## 7. The one production change this round kept

`KnowledgeRetriever.configuredMinimumCoverage()` — the floor reads an optional system
property (`matrix.retrieval.minimum-coverage`), with the pre-existing `0.25` as the default
when the property is absent or unusable. This is the knob the next rounds' experiments need;
it changes no unconfigured behaviour, and it is covered by a contract test
(`KnowledgeRetrieverTest.theCoverageFloorOverride`; the class runs 11 tests with 0 failures).
