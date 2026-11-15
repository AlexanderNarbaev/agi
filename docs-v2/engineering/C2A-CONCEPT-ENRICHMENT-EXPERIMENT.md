# C2A — the concept-enrichment experiment (H1, H2, H3)

**Status:** `MEASURED` — the three frozen candidates were run against the 29-case evaluation of the versioned dataset.
**Date:** 2026-11-15.
**Branch / commit:** `feature/concept-to-action` / `d15eb3340e99b31aebff552791b0f1fd6a90e095`.
**Freeze:** `docs-v2/engineering/C2A-CANDIDATE-FREEZE.md` — sha256 `14251fbcdf06001b670d919621b64079a35229ebabe1577081fb0b6222c08cf4`.
**Produced by:** `matrix-core/src/test/java/io/matrix/knowledge/concept/ConceptEnrichmentExperimentTest.java` (research only; the production retrieval path is unchanged).

> The candidates were frozen **before** this measurement. The results below were produced against the frozen definitions; the SHA-256 above binds them to that freeze.

## 1. The workspaces, prepared as the dataset says

| Scope | Workspace | Preparations | Documents acquired | Claims added | Claims in the workspace | Concepts |
|---|---|---|---|---|---|---|
| `S1` | `plate-tectonics-eval-seed` | 19 | 38 | 6612 | 348 | 326 |
| `P1` | `plate-tectonics-eval` | 6 | 30 | 4032 | 752 | 690 |
| `R1` | `plate-tectonics-eval` | 1 | 6 | 686 | 752 | 690 |
| `R2` | `plate-tectonics-eval` | 1 | 7 | 751 | 752 | 690 |
| `P1'` | `plate-tectonics-eval-neg` | 1 | 5 | 672 | 766 | 699 |
| `P1''` | `plate-tectonics-eval-neg2` | 1 | 5 | 672 | 718 | 654 |

Every scope of the dataset was prepared with the production methods behind the HTTP calls: `AcquisitionCycle.loadSeed` (the seed route), `AcquisitionCycle.run` (the learn route) and `AcquisitionCycle.acquireAndRevise` (the revise route). The preparation is repeated before every case, exactly as the official runner does it. Preparation cost: 88717 ms.

## 2. What the source-grounded extraction found

The extraction of the workspace of the first answer case (S1, 348 claims, 326 concepts):

| Measure | Value |
|---|---|
| concepts | 326 |
| definition candidates | 162 |
| — of which SUPPORTED (the production validator promoted a claim carrying the same sentence) | 162 |
| concepts with **no** definition found | 164 |
| relation candidates | 7 |
| — of which SUPPORTED | 7 |
| relation candidates rejected | 116 |
| extraction time | 130 ms |
| retained text of the extraction | 53411 bytes |

The rejections, by reason:

| Reason | Count |
|---|---|
| the same concept on both sides | 18 |
| the concept name is a single function word | 62 |
| no concept after the trigger | 27 |
| passive voice | 8 |
| no concept before the trigger | 1 |

The relations, by type:

| Type | Count |
|---|---|
| PRODUCES | 3 |
| IS_A | 2 |
| PART_OF | 2 |

The extraction over the whole run took 1301 ms and retained 1292127 bytes of text.

### 2.1 The definitions the seed scope yielded

Every definition is the **verbatim sentence** of the concept's own source document, with the offsets that sentence occupies in the extracted text of that document. The full list, with the provenance of every one, is in `C2A-CONCEPT-ENRICHMENT-RESULTS.json` under `definitionsOfTheSeedScope`. The first twenty:

| Concept | Definition (verbatim) | Source | Offsets | Precision | Status |
|---|---|---|---|---|---|
| A profound consequence of seafloor spreading | A profound consequence of seafloor spreading is that new crust was, and still is, being continually created along the oceanic ridges. | wikipedia-plate-tectonics.md | 39152-39285 | EXACT | SUPPORTED |
| A symposium on continental drift | A symposium on continental drift was held at the Royal Society of London in 1965 which must be regarded as the official start of the acceptance of plate tectonics by the scientific community, and which abstracts are issued as Blackett, Bullard & Runcorn (1965). | wikipedia-plate-tectonics.md | 47382-47643 | EXACT | SUPPORTED |
| A trench | A trench forms and, as the subducted plate is heated, it releases volatiles, mostly water from hydrous minerals, into the surrounding mantle. | wikipedia-plate-tectonics.md | 8504-8645 | EXACT | SUPPORTED |
| As a | Possible tidal effect on plate tectonics Of the many forces discussed above, tidal force is still highly debated and defended as a possible principal driving force of plate tectonics. | wikipedia-plate-tectonics.md | 19-58930 | SEGMENT | SUPPORTED |
| As a consequence, a powerful source generating plate motion | As a consequence, a powerful source generating plate motion is the excess density of the oceanic lithosphere sinking in subduction zones. | wikipedia-plate-tectonics.md | 10617-10754 | EXACT | SUPPORTED |
| As oceanic lithosphere | As oceanic lithosphere is formed at spreading ridges from hot mantle material, it gradually cools and thickens with age (and thus adds distance from the ridge). | wikipedia-plate-tectonics.md | 15671-15831 | EXACT | SUPPORTED |
| As old oceanic crust | As old oceanic crust is "consumed" in the trenches (like Holmes and others, he thought this was done by thickening of the continental lithosphere, not, as later understood, by underthrusting at a larger scale of the oceanic crust itself into the mantle), new magma rises and erupts along the spreading ridges to form new crust. | wikipedia-plate-tectonics.md | 42181-42508 | EXACT | SUPPORTED |
| As the name implies, volcanic island arcs, which closely parallel the trenches, | As the name implies, volcanic island arcs, which closely parallel the trenches, are generally curved. | usgs-this-dynamic-earth.md | 22-17143 | SEGMENT | SUPPORTED |
| At a seafloor spreading ridge, plates | At a seafloor spreading ridge, plates move away from the ridge, which is a topographic high, and the newly formed crust cools as it moves away, increasing its density and contributing to the motion. | wikipedia-plate-tectonics.md | 2293-2491 | EXACT | SUPPORTED |
| At continental collision zones, there | At continental collision zones, there are two masses of continental lithosphere converging. | wikipedia-plate-tectonics.md | 9175-9266 | EXACT | SUPPORTED |
| Average oceanic lithosphere | Average oceanic lithosphere is typically 100 km (62 mi) thick. | wikipedia-plate-tectonics.md | 4842-4904 | EXACT | SUPPORTED |
| Because it | Oceanic crust is denser than continental crust because it has less silicon and more of the heavier elements than continental crust. | wikipedia-plate-tectonics.md | 4564-4695 | EXACT | SUPPORTED |
| Consideration of plate tectonics | Consideration of plate tectonics is a part of the search for extraterrestrial intelligence and extraterrestrial life. | wikipedia-plate-tectonics.md | 57907-58024 | EXACT | SUPPORTED |
| Continental crust | The distinction between oceanic crust and continental crust is based on their modes of formation. | wikipedia-plate-tectonics.md | 4300-4397 | EXACT | SUPPORTED |
| Continental drift In the late 19th and early 20th centuries, geologists | Continental drift In the late 19th and early 20th centuries, geologists assumed that Earth's major features were fixed, and that most geologic features such as basin development and mountain ranges could be explained by vertical crustal movement, described in what is called the geosynclinal theory. | wikipedia-plate-tectonics.md | 19-58930 | SEGMENT | SUPPORTED |
| Continental lithosphere | Tectonic plates are composed of the oceanic lithosphere and the thicker continental lithosphere, each topped by its own kind of crust. | wikipedia-plate-tectonics.md | 1155-1289 | EXACT | SUPPORTED |
| Convergent boundaries -- where crust | Convergent boundaries -- where crust is destroyed as one plate dives under another. | usgs-this-dynamic-earth.md | 22-17143 | SEGMENT | SUPPORTED |
| Cool oceanic lithosphere | Cool oceanic lithosphere is significantly denser than the hot mantle material from which it is derived and so with increasing thickness it gradually subsides into the mantle to compensate the greater load. | wikipedia-plate-tectonics.md | 15832-16037 | EXACT | SUPPORTED |
| Dates derived | Dates derived are dominantly in the range 500 to 750 million years ago, although ages of up to 1,200 million years ago have been calculated. | wikipedia-plate-tectonics.md | 55465-55605 | EXACT | SUPPORTED |
| Deep marine trenches | Deep marine trenches are typically associated with subduction zones, and the basins that develop along the active boundary are often called "foreland basins". | wikipedia-plate-tectonics.md | 8250-8408 | EXACT | SUPPORTED |

**The concepts with NO definition found:** 164 of 326 — the rule is closed, so a concept whose own document never states it with a definitional copula has no definition, and nothing was invented for it. The full list is in the results file under `conceptsWithoutDefinitionOfTheSeedScope`.

### 2.2 The typed relations the seed scope yielded

| From | Type | To | Direction | Source | Offsets | Precision | Status |
|---|---|---|---|---|---|---|---|
| Picture two giant conveyor belts, facing each other but slowly moving in opposite directions as they | PRODUCES | Oceanic crust | Picture two giant conveyor belts, facing each other but slowly moving in opposite directions as they --PRODUCES--> Oceanic crust | usgs-this-dynamic-earth.md | 22-17143 | SEGMENT | SUPPORTED |
| Plate tectonics (from Latin tectonicus, from Ancient Greek τεκτονικός (tektonikós) 'pertaining to building') | IS_A | Tectonic plates | Plate tectonics (from Latin tectonicus, from Ancient Greek τεκτονικός (tektonikós) 'pertaining to building') --IS_A--> Tectonic plates | wikipedia-plate-tectonics.md | 19-281 | EXACT | SUPPORTED |
| The processes that | PRODUCES | Earth's crust | The processes that --PRODUCES--> Earth's crust | wikipedia-plate-tectonics.md | 519-600 | EXACT | SUPPORTED |
| Tectonic lithospheric plates | PART_OF | Oceanic crust | Tectonic lithospheric plates --PART_OF--> Oceanic crust | wikipedia-plate-tectonics.md | 4063-4299 | EXACT | SUPPORTED |
| Tectonic plates may | PART_OF | Continental crust | Tectonic plates may --PART_OF--> Continental crust | wikipedia-plate-tectonics.md | 6342-6414 | EXACT | SUPPORTED |
| Wegener later | IS_A | Plate tectonics | Wegener later --IS_A--> Plate tectonics | wikipedia-plate-tectonics.md | 18453-18623 | EXACT | SUPPORTED |
| Starting from the idea (also expressed by his forerunners) that the present continents once | PRODUCES | Wegener | Starting from the idea (also expressed by his forerunners) that the present continents once --PRODUCES--> Wegener | wikipedia-plate-tectonics.md | 25549-25830 | EXACT | SUPPORTED |

The supporting span of every relation is the sentence it was read from; the full spans are in the results file under `relationsOfTheSeedScope`.

**What `SUPPORTED` says.** It says that the **sentence** is an established claim of the workspace — the production validator promoted a claim carrying it. It does **not** say that the relation is true: the rule reads the sentence lexically, and a reader may call a relation wrong (the `IS_A` between "Plate tectonics ..." and "Tectonic plates" is read from a sentence that says plate tectonics *comprises* plates). The status is about the provenance of the text, not about the semantics of the relation — which is exactly why the relation is a **candidate**.

## 3. Does the in-process harness reproduce the official 18/29?

The official result (`docs-v2/engineering/E2-FLOOR-0p25.json`) is **18 / 29**, with the eleven failures `A-02, A-04, A-05, A-06, F-02, M-01, P-01, P-02, M-03, R-04, R-05`.

The in-process harness, on the production path, passes **18 / 29** — **the same 18/29, and the same eleven failures.**

## 4. The metrics

All the numbers are on the 29 cases. `BASELINE-PATH` is the full production path (`KnowledgeWorkbench.ask`); the other rows are the production retriever alone, which is the level the candidates live at. The recall and the MRR are measured on the floor-0.0 candidate list; the answers, the abstentions and the false positives on the floor-0.25 answer.

| Candidate | Passed | Recall@1 | Recall@5 | Recall@10 | Recall@20 | MRR | Supported answer | Abstention | Unsupported answer | Mean non-gold cited | Top-1 not gold | Mean retrieval (ms) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `BASELINE-PATH` | 18/29 | 0.2024 | 0.3214 | 0.3690 | 0.5952 | 0.4615 | 0.3571 | 1.0000 | 0.6429 | 38.2857 | 9 | 32 |
| `BASELINE-RETRIEVER` | 8/20 | 0.2024 | 0.3214 | 0.3690 | 0.5952 | 0.4615 | 0.3571 | 0.3333 | 0.6429 | 38.2857 | 9 | 10 |
| `H1-definition-enrichment` | 7/20 | 0.2381 | 0.3214 | 0.3690 | 0.5238 | 0.4987 | 0.2857 | 0.3333 | 0.7143 | 44.4286 | 8 | 12 |
| `H2-relation-expansion` | 8/20 | 0.2024 | 0.3214 | 0.3690 | 0.5952 | 0.4613 | 0.3571 | 0.3333 | 0.6429 | 39.0000 | 9 | 10 |
| `H3-query-lexical-expansion` | 5/20 | 0.1429 | 0.3214 | 0.4048 | 0.5476 | 0.3932 | 0.1429 | 0.3333 | 0.8571 | 17.6429 | 11 | 10 |
| `H1+H2-definition-and-relation-expansion` | 7/20 | 0.2024 | 0.2857 | 0.3690 | 0.4643 | 0.4404 | 0.2857 | 0.3333 | 0.7143 | 46.4286 | 9 | 11 |

The failure lists:

* `BASELINE-PATH`: [A-02, A-04, A-05, A-06, F-02, M-01, P-01, P-02, M-03, R-04, R-05]
* `BASELINE-RETRIEVER`: [A-02, A-04, A-05, A-06, U-01, U-03, U-04, M-01, P-01, P-02, U-06, M-03]
* `H1-definition-enrichment`: [A-02, A-03, A-04, A-05, A-06, U-01, U-03, U-04, M-01, P-01, P-02, U-06, M-03]
* `H2-relation-expansion`: [A-02, A-04, A-05, A-06, U-01, U-03, U-04, M-01, P-01, P-02, U-06, M-03]
* `H3-query-lexical-expansion`: [A-02, A-03, A-04, A-05, A-06, U-01, U-03, U-04, M-01, M-02, P-01, P-02, U-06, M-03, R-03]
* `H1+H2-definition-and-relation-expansion`: [A-02, A-03, A-04, A-05, A-06, U-01, U-03, U-04, M-01, P-01, P-02, U-06, M-03]

### 4.1 The two baseline rows, and why they differ

`BASELINE-PATH` is the whole production path: `KnowledgeWorkbench.ask`, which adds the workbench-level gates (the demand analysis, the quantity gate, the premise check) and the dialogue context. `BASELINE-RETRIEVER` is the production retriever alone. The candidates are retriever-level by construction — the candidate is applied to the snapshot before the retriever sees it, or to the question before it is asked — so they are compared against `BASELINE-RETRIEVER`.

The two rows differ on exactly the four **quantity** cases `U-01`, `U-03`, `U-04` and `U-06`: the quantity gate lives in the workbench (`KnowledgeWorkbench.applyDemand`), above the retriever, and it refuses a question that asks for a number the workspace does not state. The retriever alone answers them. This is an asymmetry of the **instrument**, not a property of the candidates, and it is the reason the abstention accuracy of the retriever-level rows is lower than the path row's.

### 4.2 The coverage shifts: what an enrichment does to the floor

A candidate can **lose** a gold claim that the baseline cited. The table gives the coverage floor at which the claim enters the answer, measured with the production retriever alone.

| Case | Candidate | Gold proposition | Floor under the baseline | Floor under the candidate |
|---|---|---|---|---|
| A-03 | `H1-definition-enrichment` | In modern views, gravity is invoked as the major driving force, through slab pull along su… | 0.2600 | 0.2400 |
| A-03 | `H1+H2-definition-and-relation-expansion` | In modern views, gravity is invoked as the major driving force, through slab pull along su… | 0.2600 | 0.2400 |

The coverage is a **ratio**: the matched weight of the question's terms over the total weight of the question's terms, and the weights are the IDF of the corpus (`TextFeatures.idf`: `log(1 + N / df)`). The enrichment changes the claim texts, so it changes the document frequencies, so it changes the weights, so it can move a claim in **either** direction. The enrichment is not monotone: it can push a claim below the floor that it was above.

## 5. The baseline, case by case

| Case | Category | Scope | Verdict | Answered | Claims | Sources | Gold cited | Gold ranks | Failure (H4) |
|---|---|---|---|---|---|---|---|---|---|---|
| A-01 | answerable | S1 | PASS | true | 41 | 2 | 2/2 | [1, 3] | — |
| A-02 | answerable | S1 | **FAIL** | true | 18 | 2 | 2/3 | [1, 12, 17] | BELOW_FLOOR |
| A-03 | answerable | S1 | PASS | true | 30 | 2 | 3/3 | [3, 13, 25] | — |
| A-04 | answerable | S1 | **FAIL** | true | 9 | 1 | 2/3 | [2, 10, 1] | BELOW_FLOOR |
| A-05 | answerable | S1 | **FAIL** | true | 16 | 2 | 2/4 | [33, 11, 0, 13] | BELOW_FLOOR |
| A-06 | answerable | S1 | **FAIL** | true | 7 | 1 | 0/3 | [41, 9, 0] | BELOW_FLOOR |
| P-01 | answerable | P1 | **FAIL** | true | 63 | 5 | 0/3 | [0, 129, 0] | BELOW_FLOOR |
| P-02 | answerable | P1 | **FAIL** | true | 104 | 5 | 0/3 | [0, 0, 0] | NO_LEXICAL_MATCH |
| U-01 | unanswerable | S1 | PASS | false | 0 | 0 | 0/0 | [] | — |
| U-02 | unanswerable | S1 | PASS | false | 0 | 0 | 0/0 | [] | — |
| U-03 | unanswerable | S1 | PASS | false | 0 | 0 | 0/0 | [] | — |
| U-04 | unanswerable | S1 | PASS | false | 0 | 0 | 0/0 | [] | — |
| U-05 | unanswerable | S1 | PASS | false | 0 | 0 | 0/0 | [] | — |
| U-06 | unanswerable | P1 | PASS | false | 0 | 0 | 0/0 | [] | — |
| F-01 | false-premise | S1 | PASS | false | 0 | 0 | 0/0 | [] | — |
| F-02 | false-premise | S1 | **FAIL** | false | 0 | 0 | 0/0 | [] | FALSE_PREMISE_MISMATCH |
| F-03 | false-premise | S1 | PASS | false | 0 | 0 | 0/0 | [] | — |
| F-04 | false-premise | S1 | PASS | false | 0 | 0 | 0/0 | [] | — |
| F-05 | false-premise | P1 | PASS | false | 0 | 0 | 0/0 | [] | — |
| M-01 | multi-claim | S1 | **FAIL** | true | 18 | 2 | 1/3 | [1, 17, 0] | BELOW_FLOOR |
| M-02 | multi-claim | S1 | PASS | true | 86 | 2 | 3/3 | [1, 17, 81] | — |
| M-03 | multi-claim | P1 | **FAIL** | true | 25 | 4 | 0/5 | [303, 0, 0, 0, 368] | BELOW_FLOOR |
| K-01 | knows-what-it-does-not-know | S1 | PASS | true | 6 | 2 | 1/1 | [1] | — |
| K-02 | knows-what-it-does-not-know | S1 | PASS | true | 6 | 2 | 1/2 | [2, 11] | — |
| R-01 | revision | P1 | PASS | false | 0 | 0 | 0/0 | [] | — |
| R-02 | revision | R2 | PASS | false | 0 | 0 | 0/0 | [] | — |
| R-03 | revision | R1 | PASS | true | 126 | 7 | 2/2 | [16, 120] | — |
| R-04 | revision | P1' | **FAIL** | false | 0 | 0 | 0/0 | [] | REVISION_NOT_APPLIED |
| R-05 | revision | P1'' | **FAIL** | false | 0 | 0 | 0/0 | [] | REVISION_NOT_APPLIED |

## 6. H4 — what the failures actually are

| Case | Classification | Evidence |
|---|---|---|
| A-02 | `BELOW_FLOOR` | the gold claim is in the floor-0.0 candidate list but not in the floor-0.25 evidence set |
| A-04 | `BELOW_FLOOR` | the gold claim is in the floor-0.0 candidate list but not in the floor-0.25 evidence set |
| A-05 | `BELOW_FLOOR` | the gold claim is in the floor-0.0 candidate list but not in the floor-0.25 evidence set |
| A-06 | `BELOW_FLOOR` | the gold claim is in the floor-0.0 candidate list but not in the floor-0.25 evidence set |
| P-01 | `BELOW_FLOOR` | the gold claim is in the floor-0.0 candidate list but not in the floor-0.25 evidence set |
| P-02 | `NO_LEXICAL_MATCH` | the gold claim is in the workspace but shares no term with the question: it is not in the floor-0.0 candidate list either |
| F-02 | `FALSE_PREMISE_MISMATCH` | the verdict is UNRESOLVED, expected CONTRADICTED |
| M-01 | `BELOW_FLOOR` | the gold claim is in the floor-0.0 candidate list but not in the floor-0.25 evidence set |
| M-03 | `BELOW_FLOOR` | the gold claim is in the floor-0.0 candidate list but not in the floor-0.25 evidence set |
| R-04 | `REVISION_NOT_APPLIED` | 2 supersession(s) applied, the case expects none |
| R-05 | `REVISION_NOT_APPLIED` | 1 supersession(s) applied, the case expects none |

## 7. The candidates, case by case

The gold ranks (the rank of each gold proposition in the floor-0.0 candidate list; 0 means the gold is not in the list at all) and the verdict of each candidate.

| Case | Gold ranks | BASELINE-PATH | BASELINE-RETRIEVER | H1-definition-enrichment | H2-relation-expansion | H3-query-lexical-expansion | H1+H2-definition-and-relation-expansion |
|---|---|---|---|---|---|---|---|
| A-01 | [1, 3] | [1, 3] PASS | [1, 3] PASS | [1, 4] PASS | [1, 3] PASS | [3, 5] PASS | [5, 7] PASS |
| A-02 | [1, 12, 17] | [1, 12, 17] FAIL | [1, 12, 17] FAIL | [1, 14, 22] FAIL | [1, 12, 17] FAIL | [1, 12, 17] FAIL | [1, 14, 22] FAIL |
| A-03 | [3, 13, 25] | [3, 13, 25] PASS | [3, 13, 25] PASS | [3, 20, 34] FAIL | [3, 13, 25] PASS | [2, 56, 75] FAIL | [3, 23, 37] FAIL |
| A-04 | [2, 10, 1] | [2, 10, 1] FAIL | [2, 10, 1] FAIL | [2, 10, 1] FAIL | [2, 10, 1] FAIL | [2, 10, 1] FAIL | [2, 10, 1] FAIL |
| A-05 | [33, 11, 0, 13] | [33, 11, 0, 13] FAIL | [33, 11, 0, 13] FAIL | [15, 17, 0, 21] FAIL | [33, 11, 0, 13] FAIL | [33, 11, 0, 13] FAIL | [16, 18, 0, 22] FAIL |
| A-06 | [41, 9, 0] | [41, 9, 0] FAIL | [41, 9, 0] FAIL | [45, 8, 0] FAIL | [41, 9, 0] FAIL | [41, 9, 0] FAIL | [45, 8, 0] FAIL |
| P-01 | [0, 129, 0] | [0, 129, 0] FAIL | [0, 129, 0] FAIL | [0, 146, 0] FAIL | [0, 131, 0] FAIL | [0, 129, 0] FAIL | [0, 151, 0] FAIL |
| P-02 | [0, 0, 0] | [0, 0, 0] FAIL | [0, 0, 0] FAIL | [0, 0, 0] FAIL | [0, 0, 0] FAIL | [0, 0, 142] FAIL | [0, 0, 0] FAIL |
| U-01 | [] | [] PASS | [] FAIL | [] FAIL | [] FAIL | [] FAIL | [] FAIL |
| U-02 | [] | [] PASS | [] PASS | [] PASS | [] PASS | [] PASS | [] PASS |
| U-03 | [] | [] PASS | [] FAIL | [] FAIL | [] FAIL | [] FAIL | [] FAIL |
| U-04 | [] | [] PASS | [] FAIL | [] FAIL | [] FAIL | [] FAIL | [] FAIL |
| U-05 | [] | [] PASS | [] PASS | [] PASS | [] PASS | [] PASS | [] PASS |
| U-06 | [] | [] PASS | [] FAIL | [] FAIL | [] FAIL | [] FAIL | [] FAIL |
| F-01 | [] | [] PASS | — | — | — | — | — |
| F-02 | [] | [] FAIL | — | — | — | — | — |
| F-03 | [] | [] PASS | — | — | — | — | — |
| F-04 | [] | [] PASS | — | — | — | — | — |
| F-05 | [] | [] PASS | — | — | — | — | — |
| M-01 | [1, 17, 0] | [1, 17, 0] FAIL | [1, 17, 0] FAIL | [1, 22, 0] FAIL | [1, 17, 0] FAIL | [1, 17, 0] FAIL | [1, 22, 0] FAIL |
| M-02 | [1, 17, 81] | [1, 17, 81] PASS | [1, 17, 81] PASS | [1, 25, 96] PASS | [1, 17, 83] PASS | [2, 63, 165] FAIL | [1, 28, 100] PASS |
| M-03 | [303, 0, 0, 0, 368] | [303, 0, 0, 0, 368] FAIL | [303, 0, 0, 0, 368] FAIL | [328, 0, 0, 0, 386] FAIL | [309, 0, 0, 0, 371] FAIL | [485, 443, 320, 0, 532] FAIL | [317, 0, 0, 199, 390] FAIL |
| K-01 | [1] | [1] PASS | [1] PASS | [1] PASS | [1] PASS | [1] PASS | [1] PASS |
| K-02 | [2, 11] | [2, 11] PASS | [2, 11] PASS | [1, 12] PASS | [2, 11] PASS | [2, 11] PASS | [1, 12] PASS |
| R-01 | [] | [] PASS | — | — | — | — | — |
| R-02 | [] | [] PASS | — | — | — | — | — |
| R-03 | [16, 120] | [16, 120] PASS | [16, 120] PASS | [20, 136] PASS | [17, 124] PASS | [10, 248] FAIL | [26, 142] PASS |
| R-04 | [] | [] FAIL | — | — | — | — | — |
| R-05 | [] | [] FAIL | — | — | — | — | — |

## 8. What the measurement says

**The headline: none of the three candidates turns a single baseline failure into a pass.** The three explanations the brief offers for the residual retrieval failures are all measurable, and all three of them leave the eleven failures where they were — while each of them costs something (a lost case, a diluted coverage, a larger answer). The numbers are below, case by case.

* `H1-definition-enrichment`: **newly passing** none; **newly failing** [A-03].
* `H2-relation-expansion`: **newly passing** none; **newly failing** none.
* `H3-query-lexical-expansion`: **newly passing** none; **newly failing** [A-03, M-02, R-03].
* `H1+H2-definition-and-relation-expansion`: **newly passing** none; **newly failing** [A-03].

The baseline failures, by classification: `BELOW_FLOOR` × 7; `FALSE_PREMISE_MISMATCH` × 1; `NO_LEXICAL_MATCH` × 1; `REVISION_NOT_APPLIED` × 2; 

## 9. The limits of this measurement

* The candidates are applied to the **snapshot** or to the **question**; the production retriever is reused as it is and `matrix-core/src/main/java` is not modified.
* The **false-premise** path (`PremiseAssessment.assess`) builds its own retriever from the stored workspace, and the **revision** path does not retrieve at all, so neither path can receive the enrichment under the no-production-change constraint. The rows of those cases are the baseline's own.
* The ranking metrics need the ranked candidate list, which the production retriever only exposes at floor 0.0; that pass is timed separately and is the cost of the instrument, not of the candidates.
* The held-out dataset is owned by another worker; this experiment has never seen it, and it must be run against the freeze in `docs-v2/engineering/C2A-CANDIDATE-FREEZE.md`.
