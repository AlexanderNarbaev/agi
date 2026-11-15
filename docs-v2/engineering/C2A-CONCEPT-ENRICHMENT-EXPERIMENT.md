# C2A — the concept-enrichment experiment (H1, H2, H3)

**Status:** `MEASURED` — the three frozen candidates were run against the 29-case evaluation of the versioned dataset, under the definition rule of the freeze v1.1 and under the rule of the freeze v1.1 supersedes.
**Date:** 2026-11-15.
**Branch / commit:** `feature/concept-to-action` / `d15eb3340e99b31aebff552791b0f1fd6a90e095`.
**Freeze:** `docs-v2/engineering/C2A-CANDIDATE-FREEZE.md` — sha256 `365655905e9e6be278f9550229958be1fa7cd31b5ca19b6f91c726e7ccfc7f7c` (the v1.1 version; it supersedes the v1.0 version, sha256 `14251fbcdf06001b670d919621b64079a35229ebabe1577081fb0b6222c08cf4`, whose first measurement is preserved in §0).
**Produced by:** `matrix-core/src/test/java/io/matrix/knowledge/concept/ConceptEnrichmentExperimentTest.java` (research only; the production retrieval path is unchanged).

> The candidates were frozen **before** this measurement. The results below were produced against the frozen definitions; the SHA-256 above binds them to that freeze. The first measurement — the one the freeze v1.1 supersedes — is preserved in §0 and is not rewritten.

## 0. The v1.0 measurement, preserved

The freeze v1.1 supersedes the freeze v1.0 (sha256 `14251fbcdf06001b670d919621b64079a35229ebabe1577081fb0b6222c08cf4`). The first measurement was produced against v1.0, and it is kept here as the record of that measurement: its numbers are quoted **as they were published**, under the metric definitions of v1.0 — including the three that the independent review found defective (the MRR, the supported and unsupported answers, and the top-1 metric). The corrected numbers are in §4.

| Row | Passed | Recall@1 | Recall@5 | Recall@10 | Recall@20 | MRR | Supported answer | Abstention | Unsupported answer | Mean non-gold cited | Top-1 not gold | Mean retrieval (ms) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `BASELINE-PATH` | 18/29 | 0.2024 | 0.3214 | 0.3690 | 0.5952 | 0.4615 | 0.3571 | 1.0000 | 0.6429 | 38.2857 | 9 | 32 |
| `BASELINE-RETRIEVER` | 8/20 | 0.2024 | 0.3214 | 0.3690 | 0.5952 | 0.4615 | 0.3571 | 0.3333 | 0.6429 | 38.2857 | 9 | 10 |
| `H1-definition-enrichment` | 7/20 | 0.2381 | 0.3214 | 0.3690 | 0.5238 | 0.4987 | 0.2857 | 0.3333 | 0.7143 | 44.4286 | 8 | 12 |
| `H2-relation-expansion` | 8/20 | 0.2024 | 0.3214 | 0.3690 | 0.5952 | 0.4613 | 0.3571 | 0.3333 | 0.6429 | 39.0000 | 9 | 10 |
| `H3-query-lexical-expansion` | 5/20 | 0.1429 | 0.3214 | 0.4048 | 0.5476 | 0.3932 | 0.1429 | 0.3333 | 0.8571 | 17.6429 | 11 | 10 |
| `H1+H2-definition-and-relation-expansion` | 7/20 | 0.2024 | 0.2857 | 0.3690 | 0.4643 | 0.4404 | 0.2857 | 0.3333 | 0.7143 | 46.4286 | 9 | 11 |

The v1.0 H4 classification, as published: `BELOW_FLOOR` × 7 (A-02, A-04, A-05, A-06, P-01, M-01, M-03); `NO_LEXICAL_MATCH` × 1 (P-02); `FALSE_PREMISE_MISMATCH` × 1 (F-02); `REVISION_NOT_APPLIED` × 2 (R-04, R-05); 

The v1.0 classification counted a CASE, not a GOLD, and it counted a case as `BELOW_FLOOR` when ANY of its golds was in the floor-0.0 candidate list. §6 gives the per-gold classification the freeze v1.1 requires.

## 1. The workspaces, prepared as the dataset says

| Scope | Workspace | Preparations | Documents acquired | Claims added | Claims in the workspace | Concepts |
|---|---|---|---|---|---|---|
| `S1` | `plate-tectonics-eval-seed` | 19 | 38 | 6612 | 348 | 326 |
| `P1` | `plate-tectonics-eval` | 6 | 30 | 4032 | 752 | 690 |
| `R1` | `plate-tectonics-eval` | 1 | 6 | 686 | 752 | 690 |
| `R2` | `plate-tectonics-eval` | 1 | 7 | 751 | 752 | 690 |
| `P1'` | `plate-tectonics-eval-neg` | 1 | 5 | 672 | 766 | 699 |
| `P1''` | `plate-tectonics-eval-neg2` | 1 | 5 | 672 | 718 | 654 |

Every scope of the dataset was prepared with the production methods behind the HTTP calls: `AcquisitionCycle.loadSeed` (the seed route), `AcquisitionCycle.run` (the learn route) and `AcquisitionCycle.acquireAndRevise` (the revise route). The preparation is repeated before every case, exactly as the official runner does it. Preparation cost: 89394 ms.

## 2. What the source-grounded extraction found

The extraction of the workspace of the first answer case (S1, 348 claims, 326 concepts), under both definition rules:

| Measure | The tight rule (v1.1) | The loose rule (v1.0) |
|---|---|---|
| definitions | 120 | 162 |
| — of which SUPPORTED | 120 | 162 |
| concepts with **no** definition | 206 | 164 |
| relation candidates | 7 | 7 |
| — of which SUPPORTED | 7 | 7 |
| relation candidates rejected | 116 | 116 |
| extraction time | 115 ms | 48 ms |
| retained text of the extraction | 43020 bytes | 53411 bytes |

**Why the rule was tightened.** The v1.0 rule asked for two things that may happen ANYWHERE in the same sentence: the concept's name and a definitional copula. Of the 162 definitions it produced, only 109 have the name **immediately** followed by a definitional copula (67.3%), and 3 are longer than the 400 characters the v1.1 rule allows. The other sentences state something ELSE about the concept — they are not definitions, and a candidate that carries them does not test the hypothesis it claims to test. The v1.1 rule therefore requires the **adjacency** and the **length cap**. It keeps 120 definitions; for 11 of those concepts it is a DIFFERENT sentence — the loose rule stopped at the first sentence that carried the name and a copula anywhere, and the tight rule kept looking for one that carried the name immediately before the copula. The examples the loose rule produced are below.

### 2.1 The definitions the loose rule produced that are not definitions

The first twelve of them — the sentences that carry the concept's name and a definitional copula somewhere, but do not state a definition:

| Concept | The definition the loose rule produced | Length |
|---|---|---|
| A symposium on continental drift | A symposium on continental drift was held at the Royal Society of London in 1965 which must be regarded as the official start of the acceptance of plate tectonics by the scientific community, and which abstracts are issued as Blackett, Bullard & Runcorn (1965). | 261 |
| A trench | A trench forms and, as the subducted plate is heated, it releases volatiles, mostly water from hydrous minerals, into the surrounding mantle. | 141 |
| As a | Possible tidal effect on plate tectonics Of the many forces discussed above, tidal force is still highly debated and defended as a possible principal driving force of plate tectonics. | 183 |
| At a seafloor spreading ridge, plates | At a seafloor spreading ridge, plates move away from the ridge, which is a topographic high, and the newly formed crust cools as it moves away, increasing its density and contributing to the motion. | 198 |
| Because it | Oceanic crust is denser than continental crust because it has less silicon and more of the heavier elements than continental crust. | 131 |
| Continental drift In the late 19th and early 20th centuries, geologists | Continental drift In the late 19th and early 20th centuries, geologists assumed that Earth's major features were fixed, and that most geologic features such as basin development and mountain ranges could be explained by vertical crustal movement, described in what is called the geosynclinal theory. | 299 |
| Continental lithosphere | Tectonic plates are composed of the oceanic lithosphere and the thicker continental lithosphere, each topped by its own kind of crust. | 134 |
| Early subduction zones appear to | Early subduction zones appear to have been temporary and localized, though to what degree is controversial. | 107 |
| Earth may | Some authors have suggested that during at least part of the Archean period (~4-2.5 billion years ago) the mantle was between 100 and 250 °C warmer than at present, which is thought to be incompatible with modern-style plate tectonics, and that Earth may have had a stagnant lid or other kinds of regimes. | 305 |
| Earth's crust | Surge tectonics Another theory is that the mantle flows neither in cells nor large plumes but rather as a series of channels just below Earth's crust, which then provide basal friction to the lithosphere. | 204 |
| GPS Satellite and Ground Receiver [63 k] Current plate movement can | GPS Satellite and Ground Receiver [63 k] Current plate movement can be tracked directly by means of ground-based or space-based geodetic measurements; geodesy is the science of the size and shape of the Earth. | 209 |
| Illustration of the Main Types of Plate Boundaries [55 k] Divergent boundaries Divergent boundaries | Illustration of the Main Types of Plate Boundaries [55 k] Divergent boundaries Divergent boundaries occur along spreading centers where plates are moving apart and new crust is created by magma pushing up from the mantle. | 221 |

### 2.2 The definitions the tight rule kept

| Concept | Definition (verbatim) | Source | Offsets | Precision | Status |
|---|---|---|---|---|---|
| A profound consequence of seafloor spreading | A profound consequence of seafloor spreading is that new crust was, and still is, being continually created along the oceanic ridges. | wikipedia-plate-tectonics.md | 39152-39285 | EXACT | SUPPORTED |
| As a consequence, a powerful source generating plate motion | As a consequence, a powerful source generating plate motion is the excess density of the oceanic lithosphere sinking in subduction zones. | wikipedia-plate-tectonics.md | 10617-10754 | EXACT | SUPPORTED |
| As oceanic lithosphere | As oceanic lithosphere is formed at spreading ridges from hot mantle material, it gradually cools and thickens with age (and thus adds distance from the ridge). | wikipedia-plate-tectonics.md | 15671-15831 | EXACT | SUPPORTED |
| As old oceanic crust | As old oceanic crust is "consumed" in the trenches (like Holmes and others, he thought this was done by thickening of the continental lithosphere, not, as later understood, by underthrusting at a larger scale of the oceanic crust itself into the mantle), new magma rises and erupts along the spreading ridges to form new crust. | wikipedia-plate-tectonics.md | 42181-42508 | EXACT | SUPPORTED |
| As the name implies, volcanic island arcs, which closely parallel the trenches, | As the name implies, volcanic island arcs, which closely parallel the trenches, are generally curved. | usgs-this-dynamic-earth.md | 22-17143 | SEGMENT | SUPPORTED |
| At continental collision zones, there | At continental collision zones, there are two masses of continental lithosphere converging. | wikipedia-plate-tectonics.md | 9175-9266 | EXACT | SUPPORTED |
| Average oceanic lithosphere | Average oceanic lithosphere is typically 100 km (62 mi) thick. | wikipedia-plate-tectonics.md | 4842-4904 | EXACT | SUPPORTED |
| Because it | Because it is formed at mid-ocean ridges and spreads outwards, its thickness is therefore a function of its distance from the mid-ocean ridge where it was formed. | wikipedia-plate-tectonics.md | 5117-5279 | EXACT | SUPPORTED |
| Consideration of plate tectonics | Consideration of plate tectonics is a part of the search for extraterrestrial intelligence and extraterrestrial life. | wikipedia-plate-tectonics.md | 57907-58024 | EXACT | SUPPORTED |
| Continental crust | The distinction between oceanic crust and continental crust is based on their modes of formation. | wikipedia-plate-tectonics.md | 4300-4397 | EXACT | SUPPORTED |
| Continental lithosphere | Continental lithosphere is typically about 200 km (120 mi) thick, though this varies considerably between basins, mountain ranges, and stable cratonic interiors of continents. | wikipedia-plate-tectonics.md | 5620-5795 | EXACT | SUPPORTED |
| Convergent boundaries -- where crust | Convergent boundaries -- where crust is destroyed as one plate dives under another. | usgs-this-dynamic-earth.md | 22-17143 | SEGMENT | SUPPORTED |
| Cool oceanic lithosphere | Cool oceanic lithosphere is significantly denser than the hot mantle material from which it is derived and so with increasing thickness it gradually subsides into the mantle to compensate the greater load. | wikipedia-plate-tectonics.md | 15832-16037 | EXACT | SUPPORTED |
| Dates derived | Dates derived are dominantly in the range 500 to 750 million years ago, although ages of up to 1,200 million years ago have been calculated. | wikipedia-plate-tectonics.md | 55465-55605 | EXACT | SUPPORTED |
| Deep marine trenches | Deep marine trenches are typically associated with subduction zones, and the basins that develop along the active boundary are often called "foreland basins". | wikipedia-plate-tectonics.md | 8250-8408 | EXACT | SUPPORTED |
| Defining plate boundaries Active plate boundaries | Defining plate boundaries Active plate boundaries are defined by their seismicity. | wikipedia-plate-tectonics.md | 19-58930 | SEGMENT | SUPPORTED |
| Dissipation of heat from the mantle | Dissipation of heat from the mantle is the original source of the energy required to drive plate tectonics through convection or large scale upwelling and doming. | wikipedia-plate-tectonics.md | 10454-10616 | EXACT | SUPPORTED |
| Driving forces of plate motion Tectonic plates | Driving forces of plate motion Tectonic plates are able to move because of the relative density of oceanic lithosphere and the relative weakness of the asthenosphere. | wikipedia-plate-tectonics.md | 19-58930 | SEGMENT | SUPPORTED |
| Driving forces related to gravity Forces related to gravity | Driving forces related to gravity Forces related to gravity are invoked as secondary phenomena within the framework of a more general driving mechanism such as the various forms of mantle dynamics described above. | wikipedia-plate-tectonics.md | 19-58930 | SEGMENT | SUPPORTED |
| Earth's crust | The processes that result in plates and shape Earth's crust are called tectonics. | wikipedia-plate-tectonics.md | 519-600 | EXACT | SUPPORTED |

**The concepts with NO definition found under the tight rule:** 206 of 326 — the rule is closed, so a concept whose own document never states it with the name IMMEDIATELY followed by a definitional copula has no definition, and nothing was invented for it. The full list is in the results file under `conceptsWithoutDefinitionOfTheSeedScope`.

### 2.3 The typed relations the seed scope yielded

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

The extraction over the whole run took 1328 ms under the tight rule and 1137 ms under the loose one, and retained 1059921 and 1292127 bytes of text.

## 3. Does the in-process harness reproduce the official 18/29?

The official result (`docs-v2/engineering/E2-FLOOR-0p25.json`) is **18 / 29**, with the eleven failures `A-02, A-04, A-05, A-06, F-02, M-01, P-01, P-02, M-03, R-04, R-05`.

The in-process harness, on the production path, passes **18 / 29** — **the same 18/29, and the same eleven failures.**

## 4. The metrics

**The denominators.** The recall and the MRR are the MEAN over the **14 answerable cases** (the cases whose `expect.answered` is true: `A-01…A-06`, `P-01`, `P-02`, `M-01…M-03`, `K-01`, `K-02`, `R-03`), measured on the floor-0.0 candidate list. The GLOBAL (micro) recall is the share over **every gold proposition of those 14 cases** — the cases are not weighted equally there. The supported answer, the unsupported answer and the answered-with-no-gold share are over the **14 answerable cases**; the abstention accuracy is over the **6 unanswerable cases** (`U-01…U-06`); the unsupported answer over ALL the ask cases is over the **20 ask cases** (the 14 answerable and the 6 unanswerable ones) and counts an answered unanswerable case as unsupported. The passed/total column is over the **29 cases** for the production path and over the **20 ask cases** for the retriever-level rows. The latency of the retriever-level rows is the PURE retriever; the latency of `BASELINE-PATH` is the full production path, and the two are not comparable.

### 4.1 The tight rule — the rule of the freeze v1.1

The ranking metrics (the recall and the MRR are measured on the floor-0.0 candidate list of the **14 answerable cases**):

| Row | Passed | Recall@1 | Recall@5 | Recall@10 | Recall@20 | MRR | micro Recall@1 | micro Recall@5 | micro Recall@10 | micro Recall@20 |
|---|---|---|---|---|---|---|---|---|---|---|
| `BASELINE-PATH` | 18/29 | 0.2024 | 0.3214 | 0.3690 | 0.5952 | 0.5078 | 0.1500 | 0.2500 | 0.3000 | 0.5250 |
| `BASELINE-RETRIEVER` | 8/20 | 0.2024 | 0.3214 | 0.3690 | 0.5952 | 0.5078 | 0.1500 | 0.2500 | 0.3000 | 0.5250 |
| `H1-definition-enrichment` | 8/20 | 0.2381 | 0.3214 | 0.3690 | 0.5952 | 0.5422 | 0.1750 | 0.2500 | 0.3000 | 0.5250 |
| `H2-relation-expansion` | 8/20 | 0.2024 | 0.3214 | 0.3690 | 0.5952 | 0.5075 | 0.1500 | 0.2500 | 0.3000 | 0.5250 |
| `H3-query-lexical-expansion` | 5/20 | 0.1429 | 0.3214 | 0.4048 | 0.5476 | 0.4395 | 0.1000 | 0.2500 | 0.3250 | 0.4750 |
| `H1+H2-definition-and-relation-expansion` | 8/20 | 0.2024 | 0.3214 | 0.3690 | 0.5595 | 0.4938 | 0.1500 | 0.2500 | 0.3000 | 0.5000 |

The answer metrics (the supported answer, the unsupported answer and the answered-with-no-gold share are over the **14 answerable cases**; the abstention accuracy over the **6 unanswerable cases**; the unsupported answer over all the ask cases over the **20 ask cases**):

| Row | Supported answer | Unsupported answer | Answered with NO gold | Abstention | Unsupported over all 20 ask cases | Gold availability | Mean non-gold cited | Top-1 not gold |
|---|---|---|---|---|---|---|---|---|
| `BASELINE-PATH` | 0.4286 (6/14) | 0.5714 (8/14) | 4/14 | 1.0000 | 0.4000 (20 ask cases) | 0.5476 | 38.2857 | 8/14 |
| `BASELINE-RETRIEVER` | 0.4286 (6/14) | 0.5714 (8/14) | 4/14 | 0.3333 | 0.6000 (20 ask cases) | 0.5476 | 38.2857 | 8/14 |
| `H1-definition-enrichment` | 0.4286 (6/14) | 0.5714 (8/14) | 4/14 | 0.3333 | 0.6000 (20 ask cases) | 0.5476 | 40.5714 | 7/14 |
| `H2-relation-expansion` | 0.4286 (6/14) | 0.5714 (8/14) | 4/14 | 0.3333 | 0.6000 (20 ask cases) | 0.5476 | 39.0000 | 8/14 |
| `H3-query-lexical-expansion` | 0.2143 (3/14) | 0.7857 (11/14) | 4/14 | 0.3333 | 0.7500 (20 ask cases) | 0.4167 | 17.6429 | 10/14 |
| `H1+H2-definition-and-relation-expansion` | 0.4286 (6/14) | 0.5714 (8/14) | 4/14 | 0.3333 | 0.6000 (20 ask cases) | 0.5476 | 41.9286 | 8/14 |

The cost (the retriever-level rows are the PURE retriever; the `BASELINE-PATH` row is the full production path — the two are different instruments and their latencies are not comparable; the overhead column is only meaningful for the retriever-level rows):

| Row | Mean retrieval (ms) | Total over the row (ms) | Overhead per case against `BASELINE-RETRIEVER` (ms) |
|---|---|---|---|
| `BASELINE-PATH` | 46 | 863 | 37.0000 |
| `BASELINE-RETRIEVER` | 9 | 174 | 0.0000 |
| `H1-definition-enrichment` | 9 | 182 | 0.0000 |
| `H2-relation-expansion` | 9 | 167 | 0.0000 |
| `H3-query-lexical-expansion` | 9 | 172 | 0.0000 |
| `H1+H2-definition-and-relation-expansion` | 10 | 193 | 1.0000 |

The failure lists:

* `BASELINE-PATH`: [A-02, A-04, A-05, A-06, F-02, M-01, P-01, P-02, M-03, R-04, R-05]
* `BASELINE-RETRIEVER`: [A-02, A-04, A-05, A-06, U-01, U-03, U-04, M-01, P-01, P-02, U-06, M-03]
* `H1-definition-enrichment`: [A-02, A-04, A-05, A-06, U-01, U-03, U-04, M-01, P-01, P-02, U-06, M-03]
* `H2-relation-expansion`: [A-02, A-04, A-05, A-06, U-01, U-03, U-04, M-01, P-01, P-02, U-06, M-03]
* `H3-query-lexical-expansion`: [A-02, A-03, A-04, A-05, A-06, U-01, U-03, U-04, M-01, M-02, P-01, P-02, U-06, M-03, R-03]
* `H1+H2-definition-and-relation-expansion`: [A-02, A-04, A-05, A-06, U-01, U-03, U-04, M-01, P-01, P-02, U-06, M-03]

### 4.2 The two baseline rows, and where they differ

`BASELINE-PATH` is the whole production path: `KnowledgeWorkbench.ask`, which adds the workbench-level gates (the demand analysis, the quantity gate, the premise check) and the dialogue context. `BASELINE-RETRIEVER` is the production retriever alone. The candidates are retriever-level by construction — the candidate is applied to the snapshot before the retriever sees it, or to the question before it is asked — so they are compared against `BASELINE-RETRIEVER`.

The cases on which the two rows differ, computed from the run, with the gate read from the abstention reason of the production path itself:

| Case | The path row answered | Its abstention reason | The gate | The retriever row |
|---|---|---|---|---|
| U-01 | false | the question asks for a measured quantity, and none of the 2 claim(s) this workspace holds about it states one: a description of the subject is not a measurement of it, and the question is not answered from one | `ABSTAINED_QUANTITY` | answered |
| U-03 | false | the question asks about a time that has not happened | `ABSTAINED_TIME_DEMAND` | answered |
| U-04 | false | the question asks for a measured quantity, and none of the 4 claim(s) this workspace holds about it states one: a description of the subject is not a measurement of it, and the question is not answered from one | `ABSTAINED_QUANTITY` | answered |
| U-06 | false | the question asks for a measured quantity, and none of the 5 claim(s) this workspace holds about it states one: a description of the subject is not a measurement of it, and the question is not answered from one | `ABSTAINED_QUANTITY` | answered |

The two rows differ on exactly 4 cases: [U-01, U-03, U-04, U-06]. The gate is not assumed: it is read from the abstention reason the production path itself gives, so the quantity gate and the time-demand gate are told apart by the instrument rather than by prose. This is an asymmetry of the **instrument**, not a property of the candidates, and it is the reason the abstention accuracy of the retriever-level rows is lower than the path row's.

### 4.3 The loose rule — the rule of the freeze v1.1 supersedes

The ranking metrics (the recall and the MRR are measured on the floor-0.0 candidate list of the **14 answerable cases**):

| Row | Passed | Recall@1 | Recall@5 | Recall@10 | Recall@20 | MRR | micro Recall@1 | micro Recall@5 | micro Recall@10 | micro Recall@20 |
|---|---|---|---|---|---|---|---|---|---|---|
| `BASELINE-PATH` | 18/29 | 0.2024 | 0.3214 | 0.3690 | 0.5952 | 0.5078 | 0.1500 | 0.2500 | 0.3000 | 0.5250 |
| `BASELINE-RETRIEVER` | 8/20 | 0.2024 | 0.3214 | 0.3690 | 0.5952 | 0.5078 | 0.1500 | 0.2500 | 0.3000 | 0.5250 |
| `H1-definition-enrichment` | 7/20 | 0.2381 | 0.3214 | 0.3690 | 0.5238 | 0.5418 | 0.1750 | 0.2500 | 0.3000 | 0.4500 |
| `H2-relation-expansion` | 8/20 | 0.2024 | 0.3214 | 0.3690 | 0.5952 | 0.5075 | 0.1500 | 0.2500 | 0.3000 | 0.5250 |
| `H3-query-lexical-expansion` | 5/20 | 0.1429 | 0.3214 | 0.4048 | 0.5476 | 0.4395 | 0.1000 | 0.2500 | 0.3250 | 0.4750 |
| `H1+H2-definition-and-relation-expansion` | 7/20 | 0.2024 | 0.2857 | 0.3690 | 0.4643 | 0.4836 | 0.1500 | 0.2250 | 0.3000 | 0.4000 |

The answer metrics (the supported answer, the unsupported answer and the answered-with-no-gold share are over the **14 answerable cases**; the abstention accuracy over the **6 unanswerable cases**; the unsupported answer over all the ask cases over the **20 ask cases**):

| Row | Supported answer | Unsupported answer | Answered with NO gold | Abstention | Unsupported over all 20 ask cases | Gold availability | Mean non-gold cited | Top-1 not gold |
|---|---|---|---|---|---|---|---|---|
| `BASELINE-PATH` | 0.4286 (6/14) | 0.5714 (8/14) | 4/14 | 1.0000 | 0.4000 (20 ask cases) | 0.5476 | 38.2857 | 8/14 |
| `BASELINE-RETRIEVER` | 0.4286 (6/14) | 0.5714 (8/14) | 4/14 | 0.3333 | 0.6000 (20 ask cases) | 0.5476 | 38.2857 | 8/14 |
| `H1-definition-enrichment` | 0.3571 (5/14) | 0.6429 (9/14) | 4/14 | 0.3333 | 0.6500 (20 ask cases) | 0.5417 | 44.4286 | 7/14 |
| `H2-relation-expansion` | 0.4286 (6/14) | 0.5714 (8/14) | 4/14 | 0.3333 | 0.6000 (20 ask cases) | 0.5476 | 39.0000 | 8/14 |
| `H3-query-lexical-expansion` | 0.2143 (3/14) | 0.7857 (11/14) | 4/14 | 0.3333 | 0.7500 (20 ask cases) | 0.4167 | 17.6429 | 10/14 |
| `H1+H2-definition-and-relation-expansion` | 0.3571 (5/14) | 0.6429 (9/14) | 4/14 | 0.3333 | 0.6500 (20 ask cases) | 0.5417 | 46.4286 | 8/14 |

The cost (the retriever-level rows are the PURE retriever; the `BASELINE-PATH` row is the full production path — the two are different instruments and their latencies are not comparable; the overhead column is only meaningful for the retriever-level rows):

| Row | Mean retrieval (ms) | Total over the row (ms) | Overhead per case against `BASELINE-RETRIEVER` (ms) |
|---|---|---|---|
| `BASELINE-PATH` | 46 | 863 | 37.0000 |
| `BASELINE-RETRIEVER` | 9 | 168 | 0.0000 |
| `H1-definition-enrichment` | 10 | 192 | 1.0000 |
| `H2-relation-expansion` | 9 | 167 | 0.0000 |
| `H3-query-lexical-expansion` | 9 | 172 | 0.0000 |
| `H1+H2-definition-and-relation-expansion` | 10 | 194 | 1.0000 |

The failure lists:

* `BASELINE-PATH`: [A-02, A-04, A-05, A-06, F-02, M-01, P-01, P-02, M-03, R-04, R-05]
* `BASELINE-RETRIEVER`: [A-02, A-04, A-05, A-06, U-01, U-03, U-04, M-01, P-01, P-02, U-06, M-03]
* `H1-definition-enrichment`: [A-02, A-03, A-04, A-05, A-06, U-01, U-03, U-04, M-01, P-01, P-02, U-06, M-03]
* `H2-relation-expansion`: [A-02, A-04, A-05, A-06, U-01, U-03, U-04, M-01, P-01, P-02, U-06, M-03]
* `H3-query-lexical-expansion`: [A-02, A-03, A-04, A-05, A-06, U-01, U-03, U-04, M-01, M-02, P-01, P-02, U-06, M-03, R-03]
* `H1+H2-definition-and-relation-expansion`: [A-02, A-03, A-04, A-05, A-06, U-01, U-03, U-04, M-01, P-01, P-02, U-06, M-03]

### 4.4 The coverage shifts under the tight rule

A candidate can **lose** a gold claim that the baseline cited. The table gives the coverage floor at which the claim enters the answer, measured with the production retriever alone. The value is a **bracket**: the floors are scanned from 0.00 upwards in steps of 0.01, and the interval is the one the scan can resolve — the claim is cited at every floor inside the bracket and not at the next one.

| Case | Candidate | Gold proposition | Floor under the baseline | Floor under the candidate |
|---|---|---|---|---|
| A-03 | `H3-query-lexical-expansion` | The greater density of old lithosphere relative to the underlying asthenosphere allows it … | (0.33, 0.34] | (0.12, 0.13] |
| A-03 | `H3-query-lexical-expansion` | In modern views, gravity is invoked as the major driving force, through slab pull along su… | (0.25, 0.26] | (0.08, 0.09] |
| M-02 | `H3-query-lexical-expansion` | The greater density of old lithosphere relative to the underlying asthenosphere allows it … | (0.50, 0.51] | (0.10, 0.11] |
| M-02 | `H3-query-lexical-expansion` | In modern views, gravity is invoked as the major driving force, through slab pull along su… | (0.30, 0.31] | (0.04, 0.05] |
| R-03 | `H3-query-lexical-expansion` | However, most scientists working today believe that the asthenosphere does not directly ca… | (0.26, 0.27] | (0.04, 0.05] |

The coverage is a **ratio**: the matched weight of the question's terms over the total weight of the question's terms, and the weights are the IDF of the corpus (`TextFeatures.idf`: `log(1 + N / df)`). The enrichment changes the claim texts, so it changes the document frequencies, so it changes the weights, so it can move a claim in **either** direction. The enrichment is not monotone: it can push a claim below the floor that it was above.

### 4.5 The scale of every candidate

A candidate that touches almost nothing cannot change much. The table gives, for the seed scope (the workspace of the first answer case), the number of concepts the candidate WROTE INTO and the number of claims whose searchable text it changed — the production retriever composes the searchable text of a claim out of its proposition, its concept's name and its concept's definition, so a claim's text changes exactly when the definition of ITS concept changes.

| Candidate | Concepts written into | of the workspace's concepts | Claims whose text changed | of the workspace's claims | Other |
|---|---|---|---|---|---|
| `H1-definition-enrichment` | 120 | 326 | 141 | 348 | 0 related concept name(s), 0 relation text(s) |
| `H2-relation-expansion` | 13 | 326 | 16 | 348 | 14 related concept name(s), 13 relation text(s) |
| `H3-query-lexical-expansion` | 0 | 326 | 0 | 348 | 1 term(s) added to the question |
| `H1+H2-definition-and-relation-expansion` | 127 | 326 | 149 | 348 | 14 related concept name(s), 13 relation text(s) |

**The scale of H2 is the size of the problem it was asked to solve.** It writes into 13 of the 326 concepts of the scope (4.0%), because the workspace holds 7 relation candidates — the relation extraction is the binding constraint, not the corpus. The remaining 313 concepts keep the definition they had, so H2 cannot help them.

### 4.6 The coverage shifts under the loose rule

A candidate can **lose** a gold claim that the baseline cited. The table gives the coverage floor at which the claim enters the answer, measured with the production retriever alone. The value is a **bracket**: the floors are scanned from 0.00 upwards in steps of 0.01, and the interval is the one the scan can resolve — the claim is cited at every floor inside the bracket and not at the next one.

| Case | Candidate | Gold proposition | Floor under the baseline | Floor under the candidate |
|---|---|---|---|---|
| A-03 | `H1-definition-enrichment` | In modern views, gravity is invoked as the major driving force, through slab pull along su… | (0.25, 0.26] | (0.23, 0.24] |
| A-03 | `H1+H2-definition-and-relation-expansion` | In modern views, gravity is invoked as the major driving force, through slab pull along su… | (0.25, 0.26] | (0.23, 0.24] |
| A-03 | `H3-query-lexical-expansion` | The greater density of old lithosphere relative to the underlying asthenosphere allows it … | (0.33, 0.34] | (0.12, 0.13] |
| A-03 | `H3-query-lexical-expansion` | In modern views, gravity is invoked as the major driving force, through slab pull along su… | (0.25, 0.26] | (0.08, 0.09] |
| M-02 | `H3-query-lexical-expansion` | The greater density of old lithosphere relative to the underlying asthenosphere allows it … | (0.50, 0.51] | (0.10, 0.11] |
| M-02 | `H3-query-lexical-expansion` | In modern views, gravity is invoked as the major driving force, through slab pull along su… | (0.30, 0.31] | (0.04, 0.05] |
| R-03 | `H3-query-lexical-expansion` | However, most scientists working today believe that the asthenosphere does not directly ca… | (0.26, 0.27] | (0.04, 0.05] |

The coverage is a **ratio**: the matched weight of the question's terms over the total weight of the question's terms, and the weights are the IDF of the corpus (`TextFeatures.idf`: `log(1 + N / df)`). The enrichment changes the claim texts, so it changes the document frequencies, so it changes the weights, so it can move a claim in **either** direction. The enrichment is not monotone: it can push a claim below the floor that it was above.

## 5. The baseline, case by case

| Case | Category | Scope | Verdict | Answered | Claims | Sources | Gold cited | Gold condition | Gold ranks | Top-ranked claim is gold | Failure (H4) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| A-01 | answerable | S1 | PASS | true | 41 | 2 | 2/2 | ALL | [1, 3] | yes | — |
| A-02 | answerable | S1 | **FAIL** | true | 18 | 2 | 2/3 | ALL | [1, 12, 17] | yes | BELOW_FLOOR |
| A-03 | answerable | S1 | PASS | true | 30 | 2 | 3/3 | ALL | [3, 13, 25] | no | — |
| A-04 | answerable | S1 | **FAIL** | true | 9 | 1 | 2/3 | ALL | [2, 10, 1] | yes | BELOW_FLOOR |
| A-05 | answerable | S1 | **FAIL** | true | 16 | 2 | 2/4 | ALL | [33, 11, 0, 13] | no | MIXED_GOLD_CODES |
| A-06 | answerable | S1 | **FAIL** | true | 7 | 1 | 0/3 | ALL | [41, 9, 0] | no | MIXED_GOLD_CODES |
| P-01 | answerable | P1 | **FAIL** | true | 63 | 5 | 0/3 | ALL | [0, 129, 0] | no | MIXED_GOLD_CODES |
| P-02 | answerable | P1 | **FAIL** | true | 104 | 5 | 0/3 | ALL | [0, 0, 0] | no | NO_LEXICAL_MATCH |
| U-01 | unanswerable | S1 | PASS | false | 0 | 0 | 0/0 | — | [] | — | — |
| U-02 | unanswerable | S1 | PASS | false | 0 | 0 | 0/0 | — | [] | — | — |
| U-03 | unanswerable | S1 | PASS | false | 0 | 0 | 0/0 | — | [] | — | — |
| U-04 | unanswerable | S1 | PASS | false | 0 | 0 | 0/0 | — | [] | — | — |
| U-05 | unanswerable | S1 | PASS | false | 0 | 0 | 0/0 | — | [] | — | — |
| U-06 | unanswerable | P1 | PASS | false | 0 | 0 | 0/0 | — | [] | — | — |
| F-01 | false-premise | S1 | PASS | false | 0 | 0 | 0/0 | — | [] | — | — |
| F-02 | false-premise | S1 | **FAIL** | false | 0 | 0 | 0/0 | — | [] | — | FALSE_PREMISE_MISMATCH |
| F-03 | false-premise | S1 | PASS | false | 0 | 0 | 0/0 | — | [] | — | — |
| F-04 | false-premise | S1 | PASS | false | 0 | 0 | 0/0 | — | [] | — | — |
| F-05 | false-premise | P1 | PASS | false | 0 | 0 | 0/0 | — | [] | — | — |
| M-01 | multi-claim | S1 | **FAIL** | true | 18 | 2 | 1/3 | ALL | [1, 17, 0] | yes | MIXED_GOLD_CODES |
| M-02 | multi-claim | S1 | PASS | true | 86 | 2 | 3/3 | ALL | [1, 17, 81] | yes | — |
| M-03 | multi-claim | P1 | **FAIL** | true | 25 | 4 | 0/5 | ALL | [303, 0, 0, 0, 368] | no | MIXED_GOLD_CODES |
| K-01 | knows-what-it-does-not-know | S1 | PASS | true | 6 | 2 | 1/1 | ALL | [1] | yes | — |
| K-02 | knows-what-it-does-not-know | S1 | PASS | true | 6 | 2 | 1/2 | ANY | [2, 11] | no | — |
| R-01 | revision | P1 | PASS | false | 0 | 0 | 0/0 | — | [] | — | — |
| R-02 | revision | R2 | PASS | false | 0 | 0 | 0/0 | — | [] | — | — |
| R-03 | revision | R1 | PASS | true | 126 | 7 | 2/2 | ALL | [16, 120] | no | — |
| R-04 | revision | P1' | **FAIL** | false | 0 | 0 | 0/0 | — | [] | — | REVISION_NOT_APPLIED |
| R-05 | revision | P1'' | **FAIL** | false | 0 | 0 | 0/0 | — | [] | — | REVISION_NOT_APPLIED |

## 6. H4 — what the failures actually are, per gold

The freeze v1.1 §5 classifies **per gold proposition**: every gold of a failing case gets its own code, and the case gets the code its golds agree on — or `MIXED_GOLD_CODES` when they do not agree. A gold the answer cited is `GOLD_REACHED`: it is not a defect of that gold. The classification is computed for the FAILING cases only; a passing case carries no code at all.

| Case | The case's code | The code of each gold | Evidence |
|---|---|---|---|
| A-02 | `BELOW_FLOOR` | [GOLD_REACHED, GOLD_REACHED, BELOW_FLOOR] | the gold is in the floor-0.0 candidate list but not in the floor-0.25 evidence set: every uncited gold of the case is reachable and below the floor ([GOLD_REACHED, GOLD_REACHED, BELOW_FLOOR]) |
| A-04 | `BELOW_FLOOR` | [GOLD_REACHED, BELOW_FLOOR, GOLD_REACHED] | the gold is in the floor-0.0 candidate list but not in the floor-0.25 evidence set: every uncited gold of the case is reachable and below the floor ([GOLD_REACHED, BELOW_FLOOR, GOLD_REACHED]) |
| A-05 | `MIXED_GOLD_CODES` | [BELOW_FLOOR, GOLD_REACHED, NO_LEXICAL_MATCH, GOLD_REACHED] | the golds of the case carry different codes: [BELOW_FLOOR, GOLD_REACHED, NO_LEXICAL_MATCH, GOLD_REACHED] |
| A-06 | `MIXED_GOLD_CODES` | [BELOW_FLOOR, BELOW_FLOOR, NO_LEXICAL_MATCH] | the golds of the case carry different codes: [BELOW_FLOOR, BELOW_FLOOR, NO_LEXICAL_MATCH] |
| P-01 | `MIXED_GOLD_CODES` | [NO_LEXICAL_MATCH, BELOW_FLOOR, NO_LEXICAL_MATCH] | the golds of the case carry different codes: [NO_LEXICAL_MATCH, BELOW_FLOOR, NO_LEXICAL_MATCH] |
| P-02 | `NO_LEXICAL_MATCH` | [NO_LEXICAL_MATCH, NO_LEXICAL_MATCH, NO_LEXICAL_MATCH] | the gold is in the workspace but shares no term with the question: it is not in the floor-0.0 candidate list either ([NO_LEXICAL_MATCH, NO_LEXICAL_MATCH, NO_LEXICAL_MATCH]) |
| F-02 | `FALSE_PREMISE_MISMATCH` | [] | the verdict is UNRESOLVED, expected CONTRADICTED |
| M-01 | `MIXED_GOLD_CODES` | [GOLD_REACHED, BELOW_FLOOR, NO_LEXICAL_MATCH] | the golds of the case carry different codes: [GOLD_REACHED, BELOW_FLOOR, NO_LEXICAL_MATCH] |
| M-03 | `MIXED_GOLD_CODES` | [BELOW_FLOOR, NO_LEXICAL_MATCH, NO_LEXICAL_MATCH, NO_LEXICAL_MATCH, BELOW_FLOOR] | the golds of the case carry different codes: [BELOW_FLOOR, NO_LEXICAL_MATCH, NO_LEXICAL_MATCH, NO_LEXICAL_MATCH, BELOW_FLOOR] |
| R-04 | `REVISION_NOT_APPLIED` | [] | 2 supersession(s) applied, the case expects none |
| R-05 | `REVISION_NOT_APPLIED` | [] | 1 supersession(s) applied, the case expects none |

**The counts, per code:**

* `BELOW_FLOOR` × 2 — [A-02, A-04]
* `MIXED_GOLD_CODES` × 5 — [A-05, A-06, P-01, M-01, M-03]
* `NO_LEXICAL_MATCH` × 1 — [P-02]
* `FALSE_PREMISE_MISMATCH` × 1 — [F-02]
* `REVISION_NOT_APPLIED` × 2 — [R-04, R-05]

**What the counts say.** The v1.0 report counted a CASE and called seven of them `BELOW_FLOOR`. Under the per-gold rule of the freeze v1.1 only **two** of the failures are purely a floor failure: every gold of those cases is reachable and below the floor. **Five** of them are `MIXED_GOLD_CODES`: their golds carry DIFFERENT codes, and in every one of them at least one gold is `NO_LEXICAL_MATCH` — a gold the question shares no term with, so that gold is unreachable at ANY floor, and the floor is not what failed to reach it. The report of the first measurement therefore attributed to the floor a share of the failures that the floor does not carry.

## 7. The candidates, case by case

The gold ranks (the rank of each gold proposition in the floor-0.0 candidate list; 0 means the gold is not in the list at all) and the verdict of each candidate, under the tight rule of the freeze v1.1.

| Case | Gold ranks | BASELINE-PATH | BASELINE-RETRIEVER | H1-definition-enrichment | H2-relation-expansion | H3-query-lexical-expansion | H1+H2-definition-and-relation-expansion |
|---|---|---|---|---|---|---|---|
| A-01 | [1, 3] | [1, 3] PASS | [1, 3] PASS | [1, 3] PASS | [1, 3] PASS | [3, 5] PASS | [3, 5] PASS |
| A-02 | [1, 12, 17] | [1, 12, 17] FAIL | [1, 12, 17] FAIL | [1, 15, 20] FAIL | [1, 12, 17] FAIL | [1, 12, 17] FAIL | [1, 15, 20] FAIL |
| A-03 | [3, 13, 25] | [3, 13, 25] PASS | [3, 13, 25] PASS | [3, 15, 27] PASS | [3, 13, 25] PASS | [2, 56, 75] FAIL | [3, 16, 28] PASS |
| A-04 | [2, 10, 1] | [2, 10, 1] FAIL | [2, 10, 1] FAIL | [2, 10, 1] FAIL | [2, 10, 1] FAIL | [2, 10, 1] FAIL | [2, 10, 1] FAIL |
| A-05 | [33, 11, 0, 13] | [33, 11, 0, 13] FAIL | [33, 11, 0, 13] FAIL | [33, 11, 0, 13] FAIL | [33, 11, 0, 13] FAIL | [33, 11, 0, 13] FAIL | [34, 11, 0, 13] FAIL |
| A-06 | [41, 9, 0] | [41, 9, 0] FAIL | [41, 9, 0] FAIL | [42, 10, 0] FAIL | [41, 9, 0] FAIL | [41, 9, 0] FAIL | [42, 10, 0] FAIL |
| P-01 | [0, 129, 0] | [0, 129, 0] FAIL | [0, 129, 0] FAIL | [0, 131, 0] FAIL | [0, 131, 0] FAIL | [0, 129, 0] FAIL | [0, 133, 0] FAIL |
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
| M-01 | [1, 17, 0] | [1, 17, 0] FAIL | [1, 17, 0] FAIL | [1, 20, 0] FAIL | [1, 17, 0] FAIL | [1, 17, 0] FAIL | [1, 20, 0] FAIL |
| M-02 | [1, 17, 81] | [1, 17, 81] PASS | [1, 17, 81] PASS | [1, 19, 84] PASS | [1, 17, 83] PASS | [2, 63, 165] FAIL | [1, 20, 86] PASS |
| M-03 | [303, 0, 0, 0, 368] | [303, 0, 0, 0, 368] FAIL | [303, 0, 0, 0, 368] FAIL | [302, 0, 0, 0, 377] FAIL | [309, 0, 0, 0, 371] FAIL | [485, 443, 320, 0, 532] FAIL | [311, 0, 0, 191, 381] FAIL |
| K-01 | [1] | [1] PASS | [1] PASS | [1] PASS | [1] PASS | [1] PASS | [1] PASS |
| K-02 | [2, 11] | [2, 11] PASS | [2, 11] PASS | [1, 12] PASS | [2, 11] PASS | [2, 11] PASS | [1, 12] PASS |
| R-01 | [] | [] PASS | — | — | — | — | — |
| R-02 | [] | [] PASS | — | — | — | — | — |
| R-03 | [16, 120] | [16, 120] PASS | [16, 120] PASS | [18, 126] PASS | [17, 124] PASS | [10, 248] FAIL | [23, 135] PASS |
| R-04 | [] | [] FAIL | — | — | — | — | — |
| R-05 | [] | [] FAIL | — | — | — | — | — |

## 8. What the measurement says

**Under the tight rule (the freeze v1.1):** none of the three candidates turns a single baseline failure into a pass.

* `H1-definition-enrichment`: **newly passing** none; **newly failing** none.
* `H2-relation-expansion`: **newly passing** none; **newly failing** none.
* `H3-query-lexical-expansion`: **newly passing** none; **newly failing** [A-03, M-02, R-03].
* `H1+H2-definition-and-relation-expansion`: **newly passing** none; **newly failing** none.

**Under the loose rule (the rule of the freeze v1.1 supersedes):** the same, and the rule changes nothing about the outcome.

* `H1-definition-enrichment`: **newly passing** none; **newly failing** [A-03].
* `H2-relation-expansion`: **newly passing** none; **newly failing** none.
* `H3-query-lexical-expansion`: **newly passing** none; **newly failing** [A-03, M-02, R-03].
* `H1+H2-definition-and-relation-expansion`: **newly passing** none; **newly failing** [A-03].

**The headline.** The three explanations the brief offers for the residual retrieval failures are all measurable, and none of them recovers a failure under either definition rule: the enrichment does not reach the golds the question shares no term with (the `NO_LEXICAL_MATCH` defect of §6), and where it does reach them it moves the coverage of the claims it touches in BOTH directions (§4.4). What the definition rule changes is what H1 COSTS: under the loose rule H1 lost A-03, a case the baseline passed, because the sentences it wrote into the concepts were not definitions; under the tight rule it loses nothing, and it still recovers nothing. A candidate that recovers no failure under either rule is not one that is merely too weak — it is one that does not reach the failing golds at all.

## 9. The limits of this measurement

* The candidates are applied to the **snapshot** or to the **question**; the production retriever is reused as it is and `matrix-core/src/main/java` is not modified.
* The **false-premise** path (`PremiseAssessment.assess`) builds its own retriever from the stored workspace, and the **revision** path does not retrieve at all, so neither path can receive the enrichment under the no-production-change constraint. The rows of those cases are the baseline's own.
* The ranking metrics need the ranked candidate list, which the production retriever only exposes at floor 0.0; that pass is timed separately and is the cost of the instrument, not of the candidates.
* The **coverage floors** are brackets of 0.01, not exact values: the scan resolves the floor only to the step it was run with.
* The held-out dataset is owned by another worker; this experiment has never seen it, and it must be run against the freeze in `docs-v2/engineering/C2A-CANDIDATE-FREEZE.md`.
