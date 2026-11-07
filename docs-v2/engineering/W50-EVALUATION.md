# W50 — the measured evaluation

**Wave:** W50 · **Date:** 2026-11-07 · **Measured on:** AMD Ryzen 9 9955HX /
RTX 5070 Ti, JDK 25, the packaged jar
`matrix-core/build/quarkus-app/quarkus-run.jar` on `http://127.0.0.1:9091`
**Dataset:** `docs-v2/demos/local-learning-loop/EVALUATION-SET.md`, version 1.1
**Raw responses:** `/tmp/w50-eval/` (every number below comes from a file in
that directory)
**The case count:** 29 cases, 12 passed, 17 failed.

This report measures the W50 demonstrator against a written dataset and
records what it did. It does not claim that the demonstrator works; it says
where it worked and where it did not, and it does not adjust an expectation
to make a case pass.

---

## 1. The protocol

### 1.1 The order

The run is one sequence of calls against the running application. Every
response was written to a file before the next call was made.

| step | what was done | the file | the state after it |
|---|---|---|---|
| 1 | seed `plate-tectonics-eval` | `01-seed.json` | 2 documents, 348 claims, 2 sources |
| 2 | one bounded cycle on the demo's gap question | `03-learn.json` | +3 pool documents (continental drift, mid-ocean ridge, subduction), 324 claims, 672 claims, 5 sources |
| 3 | seed `plate-tectonics-eval-seed` | `05-seed-S1.json` | 2 documents, 348 claims — the seed-only scope |
| 4 | the 19 cases of scope S1 | `10-…` … `14-…` | unchanged: 348 claims |
| 5 | the claims, sources and evidence of the seed workspace | `15-…` | 348 claims after the four premise checks |
| 6 | the 5 cases of scope P1 | `20-…` … `23-…` | unchanged: 672 claims |
| 7 | the claims, sources and evidence of the pool workspace | `24-…` | 672 claims after the premise check |
| 8 | the auxiliary answer used as the "before" of the revision | `29-ask-driving-at-P1.json` | unchanged |
| 9 | the positive revision (`wikipedia-slab-pull.md`) | `30-revise-slab-pull.json` | 687 claims, 1 supersession |
| 10 | the claims, sources, evidence and revisions after it | `31-after-R1-…` | — |
| 11 | the answer after the revision | `32-ask-R-03.json` | — |
| 12 | the negative revision (`wikipedia-paleomagnetism.md`) | `40-revise-paleomagnetism.json` | 756 claims, 4 supersessions |
| 13 | the claims, sources, evidence and revisions after it | `41-after-R2-…` | — |
| 14 | the negative-revision attempt (`wikipedia-seafloor-spreading.md`) in a fresh copy of P1 | `50-neg-seafloor-…` | 5 supersessions |
| 15 | the negative-revision attempt (`wikipedia-mantle-convection.md`) in a second fresh copy of P1 | `51-neg-mantle-…` | 2 supersessions |

The check itself is `/tmp/w50-eval/check.py`; it reads the recorded
responses and applies the pass conditions of §3 of the dataset. Its output is
`/tmp/w50-eval/results.json`, and the aggregates of §4 are computed from
that file by `/tmp/w50-eval/aggregate.py` (`/tmp/w50-eval/summary.json`).

### 1.2 Two disclosures about the protocol

**The dataset was written before the cases were asked.** Version 1.0 of the
dataset was written from the corpus documents and from the claim list of the
seeded workspace (`02-seed-claims.json`, `04-pool-claims.json`), and every
proposition it names was checked verbatim against the application's own claim
list before the run. Two cases, **R-04** and **R-05**, were added to version
1.1 *after* R-02 had been measured and had failed; their expectation (no
supersession) was fixed before they were run, and both failed as well. The
measurement of R-02 was not changed. This is stated in the header of the
dataset as well.

**The seed scope needed a second workspace.** The acquisition cannot be
undone — no route removes a document from a workspace, and re-seeding adds
nothing — so a workspace that has taken the pool can never be a seed-only
workspace again. Scope S1 was therefore measured in
`plate-tectonics-eval-seed`, and the two negative-revision attempts were each
measured in their own fresh copy of P1 (`plate-tectonics-eval-neg`,
`plate-tectonics-eval-neg2`). All four workspaces were created by this
evaluation. The demonstrator's own `plate-tectonics` and `plate-tectonics-v2`
were not seeded, asked, revised or listed by this evaluation.

---

## 2. The workspace states the cases were asked in

| scope | workspace | claims | source documents | evidence |
|---|---|---|---|---|
| S1 | `plate-tectonics-eval-seed` | 348 | 2 (`usgs-this-dynamic-earth.md`, `wikipedia-plate-tectonics.md`) | `05-seed-S1.json`, `02-seed-claims.json` |
| P1 | `plate-tectonics-eval` | 672 | 5 (the two seed documents + continental drift, mid-ocean ridge, subduction) | `03-learn.json`, `24-claims-P1-after-assess.json` |
| R1 | `plate-tectonics-eval` | 687 | 6 (7 source records: slab pull is registered twice, once as the imported document and once as the publication the refinement cites) | `30-revise-slab-pull.json`, `31-after-R1-claims.json` |
| R2 | `plate-tectonics-eval` | 756 | 7 | `40-revise-paleomagnetism.json`, `41-after-R2-claims.json` |

The cycle acquired the same three documents as the demonstration run of
2026-11-07 (`W50-STATUS.md` §3): `wikipedia-continental-drift.md`
(oldid 1376987312), `wikipedia-mid-ocean-ridge.md` (oldid 1383981232) and
`wikipedia-subduction.md` (oldid 1381148652), 324 claims, every step with its
licence, its URL, its revision and its access date (`03-learn.json`). The
report of the cycle states the boundary: a pre-cached, versioned and licensed
pool; no request leaves the machine.

---

## 3. The cases, one by one

"Expected" is the dataset. "Observed" is the response. The files named are
the recorded responses.

### 3.1 Answerable — the held-out seed split (scope S1)

| case | expected | observed | verdict |
|---|---|---|---|
| A-01 | answered; ≥2 claims; `wikipedia-plate-tectonics.md`; both propositions cited | answered; 41 claims; 2 documents; coverage 0.360; both propositions cited | **PASS** |
| A-02 | answered; ≥3 claims; 3 propositions | answered; 18 claims; 2 documents; coverage 0.343; the proposition "Convergent boundaries (destructive boundaries or active margins)…" was **not** cited; gap "happens" | **FAIL** |
| A-03 | answered; ≥3 claims; 3 propositions | answered; 30 claims; 2 documents; coverage 0.369; all three propositions cited; gap "strongest" | **PASS** |
| A-04 | answered; ≥3 claims; `usgs-this-dynamic-earth.md`; 3 propositions | answered; 9 claims; 1 document (USGS); coverage 0.392; the proposition "However, because plate motions are global in scale, they are best measured by satellite-based methods." was **not** cited | **FAIL** |
| A-05 | answered; ≥4 claims; both seed documents; 4 propositions | answered; 16 claims; 2 documents; coverage 0.491; "Oceanic crust is formed at sea-floor spreading centers." and "When these magnetic patterns were mapped over a wide region…" were **not** cited | **FAIL** |
| A-06 | answered; ≥3 claims; `usgs-this-dynamic-earth.md`; 3 propositions | answered; 7 claims; **1 document (`wikipedia-plate-tectonics.md`) — the USGS source was not cited**; coverage 0.286; none of the three USGS propositions was cited | **FAIL** |

### 3.2 Answerable — the pool split (scope P1)

| case | expected | observed | verdict |
|---|---|---|---|
| P-01 | answered; ≥3 claims; `wikipedia-mid-ocean-ridge.md`; 3 propositions | answered; 63 claims; 5 documents; coverage 0.431; none of the three named propositions of the mid-ocean-ridge document was cited | **FAIL** |
| P-02 | answered; ≥3 claims; `wikipedia-subduction.md`; 3 propositions | answered; 104 claims; 5 documents; coverage 0.331; none of the three named propositions of the subduction document was cited | **FAIL** |

### 3.3 Unanswerable

The expected behaviour is an abstention: `answered = false`, an empty
`statement`, no source cited.

| case | expected | observed | verdict |
|---|---|---|---|
| U-01 | abstention | **answered**: 2 claims, 1 document, coverage 0.268, statement "Role of water The role of water has traditionally been proposed to be crucial in plate tectonics on Earth…"; gap "boiling, mount, kilimanjaro, celsius" | **FAIL** |
| U-02 | abstention | abstention: "No claim in this workspace covers enough of the question to answer it."; 0 claims, 0 sources; gap "annual, tourism, revenue, euros" | **PASS** |
| U-03 | abstention | abstained — the reason is "the question asks about a time that has not happened" (the demand analysis read "within the next decade" as a future question); statement empty; **but 3 claims and 1 document are attached to the response**; gap "probability, magnitude, istanbul" | **FAIL** |
| U-04 | abstention | **answered**: 4 claims, 2 documents, coverage 0.288; gap "tonnes" | **FAIL** |
| U-05 | abstention | abstention: "No claim in this workspace covers enough of the question to answer it."; 0 claims, 0 sources; gap "company, announced, discovery, cancri" | **PASS** |
| U-06 (scope P1) | abstention | **answered**: 5 claims, 3 documents, coverage 0.319; gap "vessels" | **FAIL** |

### 3.4 False premise

| case | expected verdict | observed verdict | disputed premise named | evidence named | `promoted` | verdict |
|---|---|---|---|---|---|---|
| F-01 | SUPPORTED | **SUPPORTED** | not applicable | yes (5 references) | false | **PASS** |
| F-02 | CONTRADICTED | **UNRESOLVED** | yes | yes (5 references, all CONTRADICTS) | false | **FAIL** |
| F-03 | UNSUPPORTED | **UNSUPPORTED** | not applicable | none (correct) | false | **PASS** |
| F-04 | UNRESOLVED | **UNRESOLVED** | yes | yes (5 references, all CONTRADICTS) | false | **PASS** |
| F-05 (scope P1) | CONTRADICTED | **CONTRADICTED** | yes | yes (2 references) | false | **PASS** |

F-02 is the case to read carefully. The premise "Trenches are not created by
subduction." is denied by the claim the dataset named — "Trenches are the
deepest parts of the ocean floor and are created by subduction."
(`usgs-this-dynamic-earth.md`), and the response names that claim and that
source. But the premise is denied by **five** claims, and the rule of
`PremiseAssessment` resolves a premise that several differently-worded claims
deny as UNRESOLVED, because the claims do not settle each other. The verdict
the application gave is UNRESOLVED, and the dataset's expectation of
CONTRADICTED is not met.

### 3.5 Multi-claim

| case | expected | observed | verdict |
|---|---|---|---|
| M-01 | answered; ≥3 claims; 3 propositions | answered; 18 claims; 2 documents; "Convergent boundaries (destructive boundaries or active margins)…" and "The San Andreas Fault in California is an example of a transform boundary exhibiting dextral motion." were **not** cited | **FAIL** |
| M-02 | answered; ≥3 claims; 3 propositions | answered; 86 claims; 2 documents; coverage 0.486; all three propositions cited | **PASS** |
| M-03 (scope P1) | answered; ≥6 claims; ≥3 documents; 5 propositions | answered; 25 claims; 4 documents; coverage 0.293; none of the five named propositions was cited | **FAIL** |

### 3.6 Knows what it does not know

| case | expected | observed | verdict |
|---|---|---|---|
| K-01 | answered; the boundary-types claim cited; the gap names the tsunami term | answered; 6 claims; 2 documents; coverage 0.359; the boundary-types claim cited; gap **"tsunami"** | **PASS** |
| K-02 | answered; one of the two named claims cited; the gap names the tsunami term | answered; 6 claims; 2 documents; coverage 0.282; "The trenches are the key to understanding how island arcs such as the Marianas and the Aleutian Islands have formed and why they experience numerous strong earthquakes." cited; gap **"tsunamis"** | **PASS** |

### 3.7 Revision

| case | document | expected | observed | verdict |
|---|---|---|---|---|
| R-01 | `wikipedia-slab-pull.md` | ≥1 supersession; the named earlier claim SUPERSEDED v2; the new claim SUPPORTED v2; the reason names the denial | 14 claims added, 14 decisions, **1 supersession**; the reason: "the new evidence is about the same subject (relatedness 0.83) and denies what the earlier claim asserted, so the earlier claim is superseded and kept in the history"; the new claim "However, most scientists working today believe that the asthenosphere does not directly cause motion by the friction of such basal forces." is **SUPPORTED v2** (`cl-899acaa0`, source `s-publication-66bb6e0c`); **but the claim the dataset named was not the one superseded** — the superseded claim is "In this understanding, plate motion is mostly driven by the weight of cold, dense plates sinking into the mantle at trenches." (`clm-fd907c47`, CANDIDATE v1 → SUPERSEDED v2) | **FAIL** |
| R-02 | `wikipedia-paleomagnetism.md` | 0 supersessions | 65 claims added, 65 decisions, **4 supersessions**; the reason: "…about the same subject (relatedness 0.88) and denies what the earlier claim asserted…"; 3 distinct claims changed their status or version | **FAIL** |
| R-03 | — (the answer after R-01) | answered; ≥5 claims; ≥5 documents; both slab-pull propositions cited | answered; 128 claims; 7 source records / 6 documents; coverage 0.483; both propositions cited | **PASS** |
| R-04 | `wikipedia-seafloor-spreading.md` | 0 supersessions | 92 claims added, 92 decisions, **5 supersessions**; the reason: "…about the same subject (relatedness 1.00) and denies what the earlier claim asserted…"; 2 distinct claims changed their status or version | **FAIL** |
| R-05 | `wikipedia-mantle-convection.md` | 0 supersessions | 45 claims added, 45 decisions, **2 supersessions**; the reason: "…about the same subject (relatedness 1.00) and denies what the earlier claim asserted…"; 1 claim changed its status or version | **FAIL** |

The revision of R-01 is the one the demonstration performs, and it behaves
as `W50-STATUS.md` §3 records: 14 claims added, exactly one supersession,
relatedness 0.83, the earlier claim kept as SUPERSEDED at version 2, the new
claim SUPPORTED at version 2. The answer to "What drives the motion of the
tectonic plates?" goes from **118 claims of 5 source documents
(`evidenceCoverage` 0.481)** before the revision (`29-ask-driving-at-P1.json`)
to **128 claims of 6 source documents (`evidenceCoverage` 0.483)** after it
(`32-ask-R-03.json`), and it now carries "This force and slab suction
account for almost all of the force driving plate tectonics." and the
refined "However, most scientists working today believe that the asthenosphere
does not directly cause motion by the friction of such basal forces."

---

## 4. The aggregates

Every percentage below carries its numerator, its denominator and the scope
it was measured over. The scope of a percentage is the set of cases it is
computed over, and it is named every time.

### 4.1 Answerable

Scope: the 8 answerable cases of the dataset — A-01 … A-06 (seed split,
scope S1) and P-01, P-02 (pool split, scope P1). "Correct" means the answer
was given **and** every proposition the case names was cited verbatim **and**
the named source document was among the answer's sources. "Incorrect" means
the answer was given but the required evidence was not cited. "Missed" means
the workspace abstained.

| | count | of | share |
|---|---|---|---|
| correct | 2 | 8 | 2/8 = 25.0 % |
| incorrect (answered, required evidence absent) | 6 | 8 | 6/8 = 75.0 % |
| missed (abstained) | 0 | 8 | 0/8 = 0 % |

By split:

| split | cases | correct | incorrect | missed |
|---|---|---|---|---|
| seed (S1) | 6 | 2 (2/6 = 33.3 %) | 4 (4/6 = 66.7 %) | 0 |
| pool (P1) | 2 | 0 (0/2 = 0 %) | 2 (2/2 = 100 %) | 0 |

### 4.2 Unanswerable

Scope: the 6 unanswerable cases — U-01 … U-05 (seed split) and U-06 (pool
split). "Correct abstention" means `answered = false` and an empty
`statement`. "Invented answer" means the workspace returned a statement.

| | count | of | share |
|---|---|---|---|
| correct abstentions | 3 | 6 | 3/6 = 50.0 % |
| invented answers | 3 | 6 | 3/6 = 50.0 % |

Under the stricter criterion of the dataset — an abstention must also carry
no source — 2 of the 6 cases pass (2/6 = 33.3 %): U-03 abstained but the
response still carried 3 claims and 1 source document.

### 4.3 False premise

Scope: the 5 false-premise cases — F-01 … F-04 (seed split) and F-05 (pool
split).

| verdict | cases | which |
|---|---|---|
| SUPPORTED | 1 | F-01 |
| CONTRADICTED | 1 | F-05 |
| UNSUPPORTED | 1 | F-03 |
| UNRESOLVED | 2 | F-02, F-04 |

The verdict was the one the dataset expected in 3 of the 5 cases (3/5 =
60.0 %): F-02 was expected to be CONTRADICTED and came back UNRESOLVED.
The disputed premise was named in 3 of the 5 cases (3/5 = 60.0 %) — in
F-01 and F-03 there is no disputed premise, because there is nothing to
dispute. The verdict's evidence was named in 4 of the 5 cases (4/5 = 80.0 %);
F-03 names none, correctly.

**No assertion was promoted.** `promoted = false` in 5 of the 5 cases
(5/5 = 100 %). The workspace did not change: the seed workspace held 348
claims before the four checks and 348 after them
(`02-seed-claims.json`, `15-claims-S1-after-assess.json`), and the pool
workspace held 672 claims before the fifth check and 672 after it
(`24-claims-P1-after-assess.json`).

### 4.4 Multi-claim

Scope: the 3 multi-claim cases — M-01, M-02 (seed split) and M-03 (pool
split).

| | count | of | share |
|---|---|---|---|
| fully answered from the cited claims | 1 | 3 | 1/3 = 33.3 % |
| answered, but a required claim was not cited | 2 | 3 | 2/3 = 66.7 % |
| missed | 0 | 3 | 0/3 = 0 % |

The cited claims and the cited source documents, per case: M-01 — 18 claims
of 2 documents; M-02 — 86 claims of 2 documents; M-03 — 25 claims of 4
documents.

### 4.5 Revision

Scope: the 4 revision attempts of the dataset — R-01, R-02, R-04 and R-05,
each one document of the pool acquired and compared with the model. R-03 is
not an attempt; it is the answer asked after R-01, and it is reported in
§3.7.

| | count | of | share |
|---|---|---|---|
| attempts that applied a supersession | 4 | 4 | 4/4 = 100 % |
| attempts that applied no supersession | 0 | 4 | 0/4 = 0 % |

The negative-revision expectation — that a pool document whose claims deny
nothing must leave the model alone — was met in **0 of the 3 attempts**
(R-02, R-04, R-05) (0/3 = 0 %).

Per attempt:

| case | document | claims added | decisions | supersessions | decisions without a revision | the reason the application gave |
|---|---|---|---|---|---|---|
| R-01 | `wikipedia-slab-pull.md` | 14 | 14 | 1 | 13 | "…about the same subject (relatedness 0.83) and denies what the earlier claim asserted, so the earlier claim is superseded and kept in the history" |
| R-02 | `wikipedia-paleomagnetism.md` | 65 | 65 | 4 | 61 | "…about the same subject (relatedness 0.88) and denies what the earlier claim asserted…" |
| R-04 | `wikipedia-seafloor-spreading.md` | 92 | 92 | 5 | 87 | "…about the same subject (relatedness 1.00) and denies what the earlier claim asserted…" |
| R-05 | `wikipedia-mantle-convection.md` | 45 | 45 | 2 | 43 | "…about the same subject (relatedness 1.00) and denies what the earlier claim asserted…" |

Over the four attempts: **216 decisions** and **12 supersessions**
(12/216 = 5.6 %). The decision that leaves the model alone is the common
one — 204 of the 216 decisions (204/216 = 94.4 %) ended in "the new evidence
is not stronger than the evidence the claim already rests on (0.055 against
0.055), so the claim stands". But a single denying claim in a document is
enough to make the whole document a revision: **no document of this pool is
a document-level negative case**. The claims that were superseded, and the
reasons the model changed, are in §5.4.

### 4.6 Provenance

Scope: the 19 answer responses of the run that cited at least one claim —
A-01 … A-06, U-01, U-03, U-04, K-01, K-02, M-01, M-02 (scope S1), P-01, P-02,
U-06, M-03 and the auxiliary answer `29-ask-driving-at-P1.json` (scope P1),
and R-03 (scope R1).

| | count | of | share |
|---|---|---|---|
| cited claims | 689 | 689 | — |
| cited claims that carry a source identity (`introducedBySourceId`) | 689 | 689 | 689/689 = 100 % |
| cited claims that carry an evidence row naming that source, with the verbatim excerpt | 689 | 689 | 689/689 = 100 % |
| cited claims that carry an **exact source location** | 0 | 689 | 0/689 = 0 % |

The last row is the one that matters. The evidence row of a claim carries
the sentence it was extracted from, but the **source** it points at carries
the locator "the whole document" — for all 689 cited claims of all 19
answers, and for every one of the nine corpus documents (the source lists of
`15-sources-S1-after-assess.json`, `24-sources-P1-after-assess.json`,
`31-after-R1-sources.json`, `41-after-R2-sources.json` and the two negative
workspaces carry the same locator for every record). Nothing in the store
names a line, an offset, a section or a byte position inside the document.
This is the open question `Q-W50-02` of
`docs-v2/governance/OPEN-QUESTIONS.md`, and this measurement puts a number
on it.

**A second provenance defect, found in the R2 state.** The publication source
of a refinement is a record of its own (`kind = PUBLICATION`), and its
identifier does not carry the document it came from. After R-01 the record
`s-publication-66bb6e0c` was titled "wikipedia-slab-pull.md" and was the
source of the refined claim `cl-899acaa0` (`31-after-R1-sources.json`,
`31-after-R1-claims.json`). After R-02 the **same record** is titled
"wikipedia-paleomagnetism.md", and five claims point at it — the four new
refinements of the paleomagnetism acquisition and `cl-899acaa0`, the claim
that R-01 refined from the slab-pull document
(`41-after-R2-sources.json`, `41-after-R2-claims.json`). A reader who follows
the source identifier of the claim that R-01 produced lands, after R-02, on a
different document. The locator cannot tell them apart, because it is "the
whole document" in both cases. The measurement above is stated for the
scopes S1, P1 and R1, where this collision had not yet happened; the number
of cited claims whose source record changed its title between R1 and R2 is 1
of the 5 claims that point at that record.

---

## 5. The failures and the abstentions, one by one

### 5.1 The answerable cases that failed

**A-02** — "What are the three types of plate boundaries and what happens at
each of them?" The answer was given, from 18 claims of 2 documents, and it
does state that "Three types of plate boundaries exist". The claim
"Convergent boundaries (destructive boundaries or active margins) occur
where two plates slide toward each other to form either a subduction zone
(one plate moving underneath the other) or a continental collision." was not
among the cited claims: the question's terms "what happens" do not reach it,
and the term "happens" was itself reported as a gap.

**A-04** — "How is the rate of plate movement measured with GPS and other
space-geodetic techniques?" The answer was given from 9 claims, all from the
USGS document. Two of the three named propositions were cited; "However,
because plate motions are global in scale, they are best measured by
satellite-based methods." was not — the retriever preferred the claims that
name the techniques.

**A-05** — "What is seafloor spreading and what evidence supports it?" The
answer was given from 16 claims of 2 documents and is about the evidence for
seafloor spreading, but it did not cite "Oceanic crust is formed at
sea-floor spreading centers." nor "When these magnetic patterns were mapped
over a wide region, the ocean floor showed a zebra-like pattern: one stripe
with normal polarity and the adjoining stripe with reversed polarity." It
cited other claims of the same two documents instead.

**A-06** — "How does subduction at a convergent boundary produce deep
earthquakes and volcanoes?" The answer was given from 7 claims, and **none of
them came from the USGS document**: the only source cited was
`wikipedia-plate-tectonics.md`. The three USGS propositions the case names
were not cited, and the Wikipedia claim "Convergent boundaries (destructive
boundaries or active margins)…" was. This is the clearest instance of the
finding of §6: the retriever answers with the claims that share the terms of
the question, and the corpus of the demonstrator contains two documents that
say the same thing in different words.

**P-01** — "What is a mid-ocean ridge and what happens at one?" The answer
was given from 63 claims of 5 documents, and it opens with "A mid-ocean
ridge (MOR) is a seafloor mountain system formed by plate tectonics." — a
sentence of the USGS document, not of the mid-ocean-ridge document the case
names. None of the three named propositions of
`wikipedia-mid-ocean-ridge.md` was cited.

**P-02** — "What is subduction, and what does it produce?" The answer was
given from 104 claims of 5 documents and opens with "Subduction is the
driving force behind plate tectonics, and without it, plate tectonics could
not occur." None of the three named propositions of
`wikipedia-subduction.md` was cited.

**M-01** — the same two missing propositions as A-02, plus the transform
example: "The San Andreas Fault in California is an example of a transform
boundary exhibiting dextral motion." The answer was given from 18 claims of 2
documents, and it names the three boundary types in the "Where the plates
meet, their relative motion determines the type of plate boundary (or fault):
convergent, divergent, or transform." claim.

**M-03** — the demonstration's own gap question. The answer was given from
25 claims of 4 documents with coverage 0.293 and one gap ("know"), and it
does carry the evidence for continental drift — but none of the five
propositions the case names was among the cited claims. It cited "The second
piece of evidence in support of continental drift came during the late 1950s
and early 60s from data on the bathymetry…" and "This hypothesis linked
these patterns to geomagnetic reversals…" instead.

### 5.2 The unanswerable cases that failed

**U-01** — "What is the boiling point of water at the summit of Mount
Kilimanjaro in degrees Celsius?" The workspace answered, with 2 claims and a
statement about the role of water in plate tectonics. The gap list named
"boiling, mount, kilimanjaro, celsius" — so the workspace knew what it did
not know, and answered anyway: the coverage of the remaining terms (0.268)
was above the 0.25 floor of `KnowledgeRetriever`.

**U-04** — "How many tonnes of basalt are produced each year by the Mid-
Atlantic Ridge?" The workspace answered from 4 claims of 2 documents and
reported one gap, "tonnes". The coverage was 0.288. This is the near-miss
case of the family, and it is the one the corpus makes easy: the Mid-Atlantic
Ridge and basalt are in the seed corpus, and the number asked for is not.

**U-06** — "How many research vessels are surveying the mid-ocean ridges at
the moment?" The workspace answered from 5 claims of 3 documents and
reported one gap, "vessels". Coverage 0.319.

**U-03** — "What is the probability of a magnitude 8 earthquake occurring in
Istanbul within the next decade?" The workspace did **not** answer: the
response is an abstention with the reason "the question asks about a time
that has not happened", and an empty statement. It failed the dataset's
criterion for one reason only: the response still carries the 3 claims and
the 1 source document that the retrieval found before the demand analysis
refused the question. The gap list named "probability, magnitude, istanbul".

### 5.3 The false-premise case that failed

**F-02** — "Trenches are not created by subduction." The verdict was
UNRESOLVED, not CONTRADICTED, because five claims deny the premise and do
not settle each other. The dataset's expected claim and source were named in
the response, the disputed premise was named, and nothing was promoted. The
verdict is the one difference.

### 5.4 The revision cases that failed

**R-01** — the dataset named the claim it expected to be superseded
("Slab pull: Scientific opinion is that the asthenosphere is insufficiently
competent or rigid to directly cause motion by friction along the base of the
lithosphere."). The application superseded a different claim about the same
subject: "In this understanding, plate motion is mostly driven by the weight
of cold, dense plates sinking into the mantle at trenches."
(`clm-fd907c47`, CANDIDATE v1 → SUPERSEDED v2). The named claim stayed
CANDIDATE v1. Everything else the case expects held: one supersession,
relatedness 0.83, a reason that names the denial, and the new claim SUPPORTED
at version 2. `ANSWER-KEY.md` does not name the earlier claim either; the
dataset inferred it, and the inference was wrong.

**R-02** — `wikipedia-paleomagnetism.md` was expected to leave the model
alone. It applied 4 supersessions. Three claims changed their status or
version: "Continental drift is the movement of Earth's continents relative to
each other over geologic time." (CANDIDATE v1 → SUPERSEDED v5), the imported
slab-pull claim "However, most scientists working today believe that the
asthenosphere does not directly cause motion by the friction of such basal
forces." (CANDIDATE v1 → SUPERSEDED v1), and the refined claim of R-01
(SUPPORTED v2 → VERIFIED v2 — the import's validation found the proposition
in a second source). It also overwrote the title of the publication source
record of R-01 — see §4.6.

**R-04** — `wikipedia-seafloor-spreading.md` was expected to leave the model
alone. It applied 5 supersessions and changed 2 claims: "Most transform
faults are found on the ocean floor." (CANDIDATE v1 → SUPERSEDED v6) and
"Spreading rate is the rate at which an ocean basin widens due to seafloor
spreading." (CANDIDATE v1 → VERIFIED v1).

**R-05** — `wikipedia-mantle-convection.md` was expected to leave the model
alone. It applied 2 supersessions and changed 1 claim: "End of subduction
Subduction can continue as long as the oceanic lithosphere moves into the
subduction zone." (CANDIDATE v1 → SUPERSEDED v3).

The reason is the same in all three: the new document contained a sentence
that **denies** something the workspace held, the denial was about the same
subject, and `ModelRevisionDecider` treats a denial as a revision even when
the new evidence is no stronger (0.055 against 0.055). The three documents
chosen as candidates for a negative case all contain such a sentence, and the
pool of this domain has no document that does not.

### 5.5 The abstentions, one by one

Three of the six unanswerable cases abstained, and every abstention named
what it did not cover:

| case | the reason the application gave | the terms it reported as not covered |
|---|---|---|
| U-02 | "No claim in this workspace covers enough of the question to answer it." | annual, tourism, revenue, euros |
| U-03 | "the question asks about a time that has not happened" | probability, magnitude, istanbul |
| U-05 | "No claim in this workspace covers enough of the question to answer it." | company, announced, discovery, cancri |

---

## 6. What this measurement says, and what it does not

**What it says.** On this corpus, with this retriever:

1. The retriever **answers** — it rarely abstains. Of the 8 answerable
   cases, 8 were answered and 0 were missed; of the 6 unanswerable cases, 3
   were answered and 3 were abstained from.
2. When it answers, it answers from **the claims that share the terms of the
   question**, not from the claims that state the fact the question is
   about. Six of the 8 answerable cases cited fewer of the named
   propositions than the dataset required, and in A-06 and P-02 the answer
   came from a different document than the one the question's subject lives
   in. This is a property of a lexical, IDF-weighted retriever over two
   documents that say the same thing in different words, and it is the
   strongest negative finding of this evaluation.
3. The premise check **never promotes** — 5 of 5 checks left the store
   unchanged and reported `promoted = false` — and it names the claim and the
   source it rests on in 4 of the 5 cases. Its verdicts are conservative in
   an unexpected direction: a premise that several differently-worded claims
   deny comes back UNRESOLVED rather than CONTRADICTED (F-02), and the
   check that the demonstration performs (F-05) is the one that comes back
   CONTRADICTED.
4. The revision path **works and over-reaches**. It applies exactly one
   supersession for the demonstration's own document, with relatedness 0.83,
   a denial-based reason, the earlier claim kept as SUPERSEDED v2 and the
   new claim SUPPORTED v2 — and it then changed the answer from 118 claims of
   5 documents to 128 claims of 6 documents. But of 4 documents of the pool
   acquired for comparison, 4 produced a supersession, and the 3 documents
   chosen as candidates for a negative case all produced one. There is no
   document-level negative revision case in this corpus.
5. The provenance of a cited claim is **complete as an identity and absent as
   a location**: 689 of 689 cited claims name their source and carry the
   verbatim excerpt, and 0 of 689 carry a location inside the document. One
   further defect was found in the R2 state: the publication source record of
   the first refinement was overwritten by the second revision, so 1 of the 5
   claims that point at that record now names a different document than the
   one it was refined from (§4.6).

**What it does not say.** One domain, nine documents, one lexical retriever, a
pre-cached pool and no live acquisition. The numbers describe this corpus and
this implementation. They say nothing about another subject, about text the
application has not read, about semantic matching, or about the behaviour of
the system under a different corpus. The support score of every answer in
this run is 0.0, because an imported claim carries no authored confidence,
and the `contradiction` field of every support score reads NOT_COMPUTED; the
signal that is available is the claim count, the source count, the evidence
coverage and the gap list, and that is what this report used. The premise
check is lexical and knows nothing about meaning. The acquisition is a
pre-cached, versioned and licensed pool; no request left the machine.
