# C2A — the held-out evaluation of the frozen candidates (H1, H2, H3)

**Status:** `MEASURED` — the three frozen candidates, and the frozen H1+H2 combination, were run against the held-out dataset.
**Date:** 2026-11-15.
**Branch / commit:** `feature/concept-to-action` / `0aef4596e8e75895485144512a9db73f8a015382`.
**Freeze:** `docs-v2/engineering/C2A-CANDIDATE-FREEZE.md` — sha256 `14251fbcdf06001b670d919621b64079a35229ebabe1577081fb0b6222c08cf4` (verified against the sha256 the freeze declares).
**Held-out dataset:** `docs-v2/engineering/C2A-HELDOUT-SET.json` — sha256 `bbb3d2ad630ec610df3dbaae3ec10978d6cbbe740e13604dfd827b56b6872d77`.
**Produced by:** `matrix-core/src/test/java/io/matrix/knowledge/concept/HeldOutEvaluationTest.java` (research only; the production retrieval path is unchanged).

> The candidates were frozen BEFORE this measurement, in a file this harness has verified byte for byte. The held-out dataset was written by another worker from the RESEARCH POOL, which is not part of the development corpus. Nothing of the freeze — no extraction rule, no candidate, no denominator, no metric, no scoring rule — was changed between the freeze and this run. The held-out set has been inspected ONCE, by this harness: no candidate was re-tuned against it, no second candidate search was run, and no variant was tried.

## 1. The held-out set

| Measure | Value |
|---|---|
| dataset | `c2a-heldout-plate-tectonics` version `1.0` |
| cases | 38 |
| kinds | POSITIVE 19, NEGATIVE 6, UNSUPPORTED 6, AMBIGUOUS 4, CONTRADICTORY 3 |
| distinct gold claims | 59 |
| source documents | 7 (the research pool) |
| acquisition path | `io.matrix.knowledge.acquisition.AcquisitionCycle.RESEARCH_POOL -> io.matrix.knowledge.acquisition.ClasspathCorpus.extract -> io.matrix.knowledge.KnowledgeWorkbench.importDocument` |
| builder | `io.matrix.knowledge.HeldOutSetBuilderTest` |

The gold claims are the PRODUCTION claims: the harness verified that every gold claim of the dataset is in the workspace with the id the builder gave it and with the proposition the dataset records. The gold is matched by PROPOSITION TEXT (the normalisation of the freeze §1.4), never by id.

### 1.1 The denominators, and how the kinds map onto them

The freeze §6 measures over the **answerable** cases (those that carry a gold set) and the **unanswerable** ones (those that must abstain). The held-out dataset names the same two classes differently:

| Held-out kind | expected disposition | n | the freeze's class |
|---|---|---|---|
| `POSITIVE` | `ANSWERED` | 19 | the **answerable** class — the recall, the MRR, the supported-answer accuracy, the unsupported-answer rate and the false-positive retrieval are measured here |
| `NEGATIVE` | `ABSTAINED` | 6 | the **unanswerable** class — the abstention accuracy is measured here |
| `UNSUPPORTED` | `UNSUPPORTED` | 6 | neither — judged by its own condition, like the development set's false-premise cases |
| `AMBIGUOUS` | `AMBIGUOUS` | 4 | neither — judged by its own condition |
| `CONTRADICTORY` | `CONTRADICTORY` | 3 | neither — judged by its own condition |

The comparable denominator is therefore **25 cases** (19 + 6), where the development measurement's comparable denominator was 20 (14 + 6). The 13 cases of the three other kinds are reported per case and per kind, and they are NOT in the metric denominators. The per-case verdicts of the three new kinds are this harness's reading of the dataset's own manifest, and they are stated here so that a reader can check them: a POSITIVE case must be answered and must cite every gold proposition; a NEGATIVE case must abstain and cite neither a claim nor a source; an UNSUPPORTED case must have its premise assessed as `CONTRADICTED` with the premise named, and the row must not assert the disputed premise (it abstains, or it carries the counter-evidence); an AMBIGUOUS case must represent EVERY reading in its evidence, or must abstain asking rather than guessing (the production demand analysis's own disposition for an unsettled question); a CONTRADICTORY case must carry BOTH conflicting claims. The rules are applied identically to every row, so a difference between the rows is a difference of the candidate and nothing else.

## 2. The workspace and the extraction

| Measure | Value |
|---|---|
| workspace | `c2a-heldout-plate-tectonics` |
| documents imported | 7 (the research pool, through the production importer) |
| claims in the workspace | 540 |
| concepts | 504 |
| sources | 7 |
| representations | 534 |
| concepts the extraction saw | 504 |
| definition candidates | 274 |
| — of which SUPPORTED | 273 |
| concepts with NO definition | 230 |
| relation candidates | 22 |
| — of which SUPPORTED | 22 |
| relation candidates rejected | 202 |
| extraction time | 172 ms |
| retained text of the extraction | 90293 bytes |

The rejections of the held-out extraction, by the frozen reasons:

| Reason | Count |
|---|---|
| no concept after the trigger | 56 |
| passive voice | 8 |
| the concept name is a single function word | 106 |
| the same concept on both sides | 27 |
| no concept before the trigger | 4 |
| a relation of another type already exists for this pair | 1 |

The relations, by type:

| Type | Count |
|---|---|
| PRODUCES | 4 |
| IS_A | 9 |
| CAUSED_BY | 7 |
| PART_OF | 2 |

The candidates, and what each of them changed in the workspace:

| Candidate | concepts changed | definitions applied | relation texts applied |
|---|---|---|---|
| `H1-definition-enrichment` | 274 | 274 | 0 |
| `H2-relation-expansion` | 32 | 0 | 32 |
| `H1+H2-definition-and-relation-expansion` | 281 | 274 | 32 |

## 3. The metrics on the held-out set

All the numbers are on the 19 answerable and the 6 unanswerable cases (the comparable denominator 25). The recall and the MRR are measured on the floor-0.0 candidate list, the answers, the abstentions and the false positives on the floor-0.25 answer. The misses are in the denominator of the MRR.

| Row | Passed | Gold avail. | Recall@1 | Recall@5 | Recall@10 | Recall@20 | MRR | Supported answer | Abstention | Unsupported answer | Mean non-gold cited | Top-1 not gold | Mean retrieval (ms) | Overhead (ms) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `BASELINE-PATH` | 17/25 | 0.7368 | 0.5351 | 0.7719 | 0.8158 | 0.8596 | 0.7741 | 0.5789 | 1.0000 | 0.3684 | 5.2105 | 6 | 65 | 52.0000 |
| `BASELINE-RETRIEVER` | 18/25 | 0.7895 | 0.5351 | 0.7719 | 0.8158 | 0.8596 | 0.7741 | 0.6316 | 1.0000 | 0.3684 | 5.2632 | 6 | 13 | 0.0000 |
| `H1-definition-enrichment` | 18/25 | 0.7895 | 0.5351 | 0.7895 | 0.8158 | 0.8596 | 0.7715 | 0.6316 | 1.0000 | 0.3684 | 5.2105 | 6 | 15 | 2.0000 |
| `H2-relation-expansion` | 18/25 | 0.7895 | 0.5351 | 0.7544 | 0.8333 | 0.8596 | 0.7724 | 0.6316 | 1.0000 | 0.3684 | 5.2632 | 6 | 12 | -1.0000 |
| `H3-query-lexical-expansion` | 17/25 | 0.7281 | 0.4649 | 0.7281 | 0.7544 | 0.8158 | 0.7292 | 0.5789 | 1.0000 | 0.4211 | 6.4737 | 7 | 12 | -1.0000 |
| `H1+H2-definition-and-relation-expansion` | 18/25 | 0.7895 | 0.5351 | 0.7719 | 0.7895 | 0.8596 | 0.7697 | 0.6316 | 1.0000 | 0.3684 | 5.9474 | 6 | 14 | 1.0000 |

The failure lists (the comparable cases only):

* `BASELINE-PATH`: [H-P02, H-P05, H-P06, H-P08, H-P10, H-P11, H-P13, H-P17]
* `BASELINE-RETRIEVER`: [H-P05, H-P06, H-P08, H-P10, H-P11, H-P13, H-P17]
* `H1-definition-enrichment`: [H-P05, H-P06, H-P08, H-P10, H-P11, H-P13, H-P17]
* `H2-relation-expansion`: [H-P05, H-P06, H-P08, H-P10, H-P11, H-P13, H-P17]
* `H3-query-lexical-expansion`: [H-P05, H-P06, H-P08, H-P10, H-P11, H-P13, H-P17, H-P19]
* `H1+H2-definition-and-relation-expansion`: [H-P05, H-P06, H-P08, H-P10, H-P11, H-P13, H-P17]

### 3.1 The same table from the development set, for the comparison

| Row | Passed | Recall@1 | Recall@5 | Recall@10 | Recall@20 | MRR | Supported answer | Abstention | Unsupported answer | Mean non-gold cited | Top-1 not gold | Mean retrieval (ms) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `BASELINE-PATH` | 18/29 | 0.2024 | 0.3214 | 0.3690 | 0.5952 | 0.4615 | 0.3571 | 1.0000 | 0.6429 | 38.2857 | 9 | 32 |
| `BASELINE-RETRIEVER` | 8/20 | 0.2024 | 0.3214 | 0.3690 | 0.5952 | 0.4615 | 0.3571 | 0.3333 | 0.6429 | 38.2857 | 9 | 10 |
| `H1-definition-enrichment` | 7/20 | 0.2381 | 0.3214 | 0.3690 | 0.5238 | 0.4987 | 0.2857 | 0.3333 | 0.7143 | 44.4286 | 8 | 12 |
| `H2-relation-expansion` | 8/20 | 0.2024 | 0.3214 | 0.3690 | 0.5952 | 0.4613 | 0.3571 | 0.3333 | 0.6429 | 39.0000 | 9 | 10 |
| `H3-query-lexical-expansion` | 5/20 | 0.1429 | 0.3214 | 0.4048 | 0.5476 | 0.3932 | 0.1429 | 0.3333 | 0.8571 | 17.6429 | 11 | 10 |
| `H1+H2-definition-and-relation-expansion` | 7/20 | 0.2024 | 0.2857 | 0.3690 | 0.4643 | 0.4404 | 0.2857 | 0.3333 | 0.7143 | 46.4286 | 9 | 11 |

(The development table is read from `docs-v2/engineering/C2A-CONCEPT-ENRICHMENT-RESULTS.json`, sha256 `29874a24536d9b70ccb9f4f958c7fe06407c1a65b6c36802623f1f03f00fd5d8`; it is the 29-case measurement of the same freeze. Its comparable denominator is 20 cases, the held-out one's is 25.)

### 3.2 The generalisation question

The development measurement's headline was: **none of the three candidates turns a single baseline failure into a pass**; H1 lost A-03; H3 lost A-03, M-02 and R-03; H2 changed nothing. On the held-out set:

| Candidate | newly passing (held-out) | newly failing (held-out) | newly passing (development) | newly failing (development) |
|---|---|---|---|---|
| `H1-definition-enrichment` | [] | [] | [] | [A-03] |
| `H2-relation-expansion` | [] | [] | [] | [] |
| `H3-query-lexical-expansion` | [] | [H-P19] | [] | [A-03, M-02, R-03] |
| `H1+H2-definition-and-relation-expansion` | [] | [] | [] | [A-03] |

**Verdict.** The development measurement's *directional* claim generalises: on the held-out set no candidate turns a single baseline failure into a pass either — H1 [], H2 [], H3 [], H1+H2 [] (the newly passing lists). The *size* of the effect does NOT carry over: the held-out baseline's recall@10 is 0.8158 against the development set's 0.3690, and its MRR is 0.7741 against 0.4615.

**How far the generalisation claim reaches.** The two measurements are NOT numerically comparable: the held-out set is a different corpus (the research pool), a different question style, a different kind mix and a different denominator (25 against 20). What IS comparable is the DIRECTION of the change, and on that the development measurement generalises cleanly: the candidates do not recover cases on a corpus they were never fitted to either. And the bound of the claim must be stated: 38 cases, one corpus of seven documents, one author of the dataset, 19 answerable cases. That is enough to REJECT a candidate — the acceptance criterion asks for two recovered cases and the measurement gives none — and it is NOT enough to assert a positive effect that the held-out set did not show. The correct reading is: the development measurement's negative result is CONFIRMED on independent data; the held-out set adds no evidence for any positive claim.

## 4. The cases, one by one

The gold ranks are the ranks of the gold propositions in the floor-0.0 candidate list; 0 means the gold is not in the list at all.

| Case | Kind | Gold ranks | BASELINE-PATH | BASELINE-RETRIEVER | H1 | H2 | H3 | H1+H2 |
|---|---|---|---|---|---|---|---|---|
| H-P01 | POSITIVE | [1] | PASS | PASS | PASS | PASS | PASS | PASS | 
| H-P02 | POSITIVE | [1] | **FAIL** | PASS | PASS | PASS | PASS | PASS | 
| H-P03 | POSITIVE | [1] | PASS | PASS | PASS | PASS | PASS | PASS | 
| H-P04 | POSITIVE | [1, 3, 4] | PASS | PASS | PASS | PASS | PASS | PASS | 
| H-P05 | POSITIVE | [1, 8] | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | 
| H-P06 | POSITIVE | [4, 134] | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | 
| H-P07 | POSITIVE | [4, 1] | PASS | PASS | PASS | PASS | PASS | PASS | 
| H-P08 | POSITIVE | [1, 17] | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | 
| H-P09 | POSITIVE | [1, 2] | PASS | PASS | PASS | PASS | PASS | PASS | 
| H-P10 | POSITIVE | [117, 0, 11] | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | 
| H-P11 | POSITIVE | [1, 0] | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | 
| H-P12 | POSITIVE | [1] | PASS | PASS | PASS | PASS | PASS | PASS | 
| H-P13 | POSITIVE | [1, 0] | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | 
| H-P14 | POSITIVE | [2] | PASS | PASS | PASS | PASS | PASS | PASS | 
| H-P15 | POSITIVE | [1] | PASS | PASS | PASS | PASS | PASS | PASS | 
| H-P16 | POSITIVE | [2] | PASS | PASS | PASS | PASS | PASS | PASS | 
| H-P17 | POSITIVE | [1, 26] | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | 
| H-P18 | POSITIVE | [1] | PASS | PASS | PASS | PASS | PASS | PASS | 
| H-P19 | POSITIVE | [5, 1, 7] | PASS | PASS | PASS | PASS | **FAIL** | PASS | 
| H-N01 | NEGATIVE | [] | PASS | PASS | PASS | PASS | PASS | PASS | 
| H-N02 | NEGATIVE | [] | PASS | PASS | PASS | PASS | PASS | PASS | 
| H-N03 | NEGATIVE | [] | PASS | PASS | PASS | PASS | PASS | PASS | 
| H-N04 | NEGATIVE | [] | PASS | PASS | PASS | PASS | PASS | PASS | 
| H-N05 | NEGATIVE | [] | PASS | PASS | PASS | PASS | PASS | PASS | 
| H-N06 | NEGATIVE | [] | PASS | PASS | PASS | PASS | PASS | PASS | 
| H-U01 | UNSUPPORTED | [0] | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | 
| H-U02 | UNSUPPORTED | [2] | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | 
| H-U03 | UNSUPPORTED | [2, 6] | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | 
| H-U04 | UNSUPPORTED | [1, 52] | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | 
| H-U05 | UNSUPPORTED | [6, 0] | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | 
| H-U06 | UNSUPPORTED | [2, 29] | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | 
| H-A01 | AMBIGUOUS | [68, 136, 1, 119] | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | 
| H-A02 | AMBIGUOUS | [37, 38] | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | 
| H-A03 | AMBIGUOUS | [63, 31, 28] | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | 
| H-A04 | AMBIGUOUS | [98, 34, 86] | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | 
| H-C01 | CONTRADICTORY | [1, 5] | PASS | PASS | PASS | PASS | PASS | PASS | 
| H-C02 | CONTRADICTORY | [12, 5] | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | **FAIL** | 
| H-C03 | CONTRADICTORY | [1, 11] | PASS | PASS | PASS | PASS | PASS | PASS | 

### 4.1 The failures of the baseline, classified

The taxonomy and the decision tree are the frozen ones (freeze §5).

| Case | Classification | Evidence |
|---|---|---|
| H-P05 | `BELOW_FLOOR` | the gold claim is in the floor-0.0 candidate list but not in the floor-0.25 evidence set |
| H-P06 | `BELOW_FLOOR` | the gold claim is in the floor-0.0 candidate list but not in the floor-0.25 evidence set |
| H-P08 | `BELOW_FLOOR` | the gold claim is in the floor-0.0 candidate list but not in the floor-0.25 evidence set |
| H-P10 | `BELOW_FLOOR` | the gold claim is in the floor-0.0 candidate list but not in the floor-0.25 evidence set |
| H-P11 | `BELOW_FLOOR` | the gold claim is in the floor-0.0 candidate list but not in the floor-0.25 evidence set |
| H-P13 | `BELOW_FLOOR` | the gold claim is in the floor-0.0 candidate list but not in the floor-0.25 evidence set |
| H-P17 | `BELOW_FLOOR` | the gold claim is in the floor-0.0 candidate list but not in the floor-0.25 evidence set |
| H-U01 | `NO_LEXICAL_MATCH` | the gold claim is in the workspace but shares no term with the question: it is not in the floor-0.0 candidate list either |
| H-U02 | `GOLD_CITED_CASE_FAILED` | the gold is cited and the case failed on: [the premise verdict is UNSUPPORTED, expected CONTRADICTED, the disputed premise is not named] |
| H-U03 | `GOLD_CITED_CASE_FAILED` | the gold is cited and the case failed on: [the premise verdict is UNSUPPORTED, expected CONTRADICTED] |
| H-U04 | `BELOW_FLOOR` | the gold claim is in the floor-0.0 candidate list but not in the floor-0.25 evidence set |
| H-U05 | `BELOW_FLOOR` | the gold claim is in the floor-0.0 candidate list but not in the floor-0.25 evidence set |
| H-U06 | `BELOW_FLOOR` | the gold claim is in the floor-0.0 candidate list but not in the floor-0.25 evidence set |
| H-A01 | `BELOW_FLOOR` | the gold claim is in the floor-0.0 candidate list but not in the floor-0.25 evidence set |
| H-A02 | `ABSTAINED_NO_COVERAGE` | the answer abstained: No claim in this workspace covers enough of the question to answer it. |
| H-A03 | `BELOW_FLOOR` | the gold claim is in the floor-0.0 candidate list but not in the floor-0.25 evidence set |
| H-A04 | `BELOW_FLOOR` | the gold claim is in the floor-0.0 candidate list but not in the floor-0.25 evidence set |
| H-C02 | `BELOW_FLOOR` | the gold claim is in the floor-0.0 candidate list but not in the floor-0.25 evidence set |

### 4.2 The unsupported cases: what the premise path says

The premise path builds its own retriever from the stored workspace, so it cannot receive the enrichment (freeze §4.4): its verdict is the same in every row, by construction.

| Case | The premise the corpus contradicts | Verdict | The premise named | Evidence named |
|---|---|---|---|---|
| H-U01 | that the continents plowed through the ocean crust | CONTRADICTED | [Did the continents plow through the ocean floor] | [However, Wegener did not pursue this observation in his later works and his theory was dismissed by geologists because there was no mechanism to explain how continents could plow through ocean crust, and the theory became largely forgotten., When Alfred Wegener first presented a hypothesis of continental drift in 1912, he suggested that continents plowed through the ocean crust., History of study Earlier theories by Alfred Wegener and Alexander du Toit of continental drift postulated that continents in motion "plowed" through the fixed and immovable seafloor., This suggested that the oceans were a permanent feature of the Earth's surface, rather than having "changed places" with the continents., At a spreading center, basaltic magma rises up the fractures and cools on the ocean floor to form new seabed., Impact of discovery: seafloor spreading Alfred Wegener proposed the theory of continental drift in 1912., It was that temporary feature that inspired Wegener to propose continental drift., Alfred Wegener first proposed in 1915 that continents had once been joined together and had since moved apart.] |
| H-U02 | that subduction is known to occur on planets other than Earth | UNSUPPORTED | [] | [So far, Earth is the only planet where subduction is known to occur, and subduction zones are its most important tectonic feature., Since the Earth's circumference has not changed over geologic time, Hess concluded that older seafloor has to be consumed somewhere else, and suggested that this process takes place at oceanic trenches, where the crust would be melted and recycled into the Earth's mantle., " In 1889, Alfred Russel Wallace remarked that "It was formerly a very general belief, even amongst geologists, that the great features of the earth's surface, no less than the smaller ones, were subject to continual mutations, and that during the course of known geological time the continents and great oceans had, again and again, changed places with each other., This suggested that the oceans were a permanent feature of the Earth's surface, rather than having "changed places" with the continents., During subduction, a series of minerals in these slabs such as serpentine can be stable at different pressures within the slab geotherms, and may transport a significant amount of water into the Earth's interior.] |
| H-U03 | that the continents grow in area as they drift | UNSUPPORTED | [in the manner the Expanding Earth theory proposed] | [Although he produced an abundance of circumstantial evidence, his theory met with little acceptance for two reasons: (1) no mechanism for continental drift was known, and (2) there was no way to reconstruct the movements of the continents over time., He proposed that the continents had once formed a single landmass, which he called Pangaea, before breaking apart and drifting to their present locations., Bernauer thought this extension had drifted the continents only 100–200 km (60–120 mi) apart, the approximate width of the volcanic zone in Iceland., Continental drift is the movement of Earth's continents relative to each other over geologic time., In Mantovani's conjecture, this continent broke due to volcanic activity caused by thermal expansion, and the new continents drifted away from each other because of further expansion of the rip-zones, where the oceans now lie., This led Mantovani to propose a now-discredited Expanding Earth theory., Eduard Suess had proposed a supercontinent Gondwana in 1885, and the Tethys Ocean in 1893, assuming a land-bridge between the present continents submerged in the form of a geosyncline, and John Perry had written an 1895 paper proposing that the Earth's interior was fluid, and disagreeing with Lord Kelvin on the age of the Earth., When initially proposed as a scientific theory in the early 20th century, the idea that continents could move was widely rejected., Holmes's views were particularly influential: in his bestselling textbook, Principles of Physical Geology, he included a chapter on continental drift, proposing that Earth's mantle contained convection cells which dissipated radioactive heat and moved the crust at the surface., The discovery of mid-ocean ridges and the process of seafloor spreading allowed for Wegener's theory to be expanded so that it included the movement of oceanic crust as well as the continents.] |
| H-U04 | that intraplate lavas are chemically identical to mid-ocean ridge basalts | UNSUPPORTED | [] | [Many geochemistry studies have argued that the lavas erupted in intraplate areas are different in composition from shallow-derived mid-ocean ridge basalts., Fast spreading rates will expand the mid-ocean ridge causing basalt reactions with seawater to happen more rapidly., The elevated He-3:He-4 ratio of ocean island basalts suggest that they must be sourced from a part of the Earth that has not previously been melted and reproduced in the same way as mid-ocean ridge basalts have been., Mid-ocean ridge basalt is a tholeiitic basalt and is low in incompatible elements., The crystallized magma forms a new crust of basalt known as MORB for mid-ocean ridge basalt, and gabbro below it in the lower oceanic crust.] |
| H-U05 | that the continents and the oceans exchanged places over geological time | UNSUPPORTED | [] | [" In 1889, Alfred Russel Wallace remarked that "It was formerly a very general belief, even amongst geologists, that the great features of the earth's surface, no less than the smaller ones, were subject to continual mutations, and that during the course of known geological time the continents and great oceans had, again and again, changed places with each other., Geological maps of the time showed huge land bridges spanning the Atlantic and Indian oceans to account for the similarities of fauna and flora and the divisions of the Asian continent in the Permian period, but failing to account for glaciation in India, Australia and South Africa., This suggested that the oceans were a permanent feature of the Earth's surface, rather than having "changed places" with the continents., Since the Earth's circumference has not changed over geologic time, Hess concluded that older seafloor has to be consumed somewhere else, and suggested that this process takes place at oceanic trenches, where the crust would be melted and recycled into the Earth's mantle., The flanks of mid-ocean ridges are in many places marked by the inactive scars of transform faults called fracture zones., History Discovery The first indications that a ridge bisects the Atlantic Ocean basin came from the results of the British Challenger expedition in the nineteenth century.] |
| H-U06 | that the geomagnetic reversals occur at regular intervals | UNSUPPORTED | [] | [Because the field has reversed directions at known intervals throughout its history, the pattern of geomagnetic reversals in the ocean crust can be used as an indicator of age; given the crustal age and distance from the ridge axis, spreading rates can be calculated., In the 1960s, the past record of geomagnetic reversals of Earth's magnetic field was noticed by observing magnetic stripe "anomalies" on the ocean floor., Magnetostratigraphy uses the polarity reversal history of Earth's magnetic field recorded in rocks to determine the age of those rocks., Reversals have occurred at irregular intervals throughout Earth's history., Japanese geophysicist Motonori Matuyama showed in the late 1920s that Earth's magnetic field reversed in the mid-Quaternary, a reversal now known as the Brunhes–Matuyama reversal.] |

**What this shows.** The dedicated premise-assessment route returns `CONTRADICTED` for 1 of the 6 unsupported cases; the production answering path refuses the disputed premise in 0 of the 6 — it answers them as if the premise held. The two routes are not the same mechanism: `KnowledgeWorkbench.ask` refuses through `PremiseChecker` (a lexical premise and a refutation), while `PremiseAssessment.assess` is the full assessment. This is a property of the production path, not of the candidates: a retriever-level candidate cannot reach either route (freeze §4.4), so the verdicts above are identical in every row.

## 5. The contradictory and the ambiguous cases

These are the cases the dataset built to see what the system does when the corpus holds more than one reading, or two claims that cannot both hold. The expected disposition is to REPORT the conflict, not to pick one silently.

### H-A01 — AMBIGUOUS — AMBIGUOUS

The gold claims of the case: 4. The question: *In the research pool, which plate does the word slab name?*

| Row | Verdict | Gold cited | Cited claims | What the answer said |
|---|---|---|---|---|
| `BASELINE-PATH` | **FAIL** | 1/4 | 2 | Moreover, the word slab is specifically attached to the "subducting plate", even though in English the upper plate is just as much of a slab. Other research sho… |
| `BASELINE-RETRIEVER` | **FAIL** | 1/4 | 2 | Moreover, the word slab is specifically attached to the "subducting plate", even though in English the upper plate is just as much of a slab. Other research sho… |
| `H1-definition-enrichment` | **FAIL** | 2/4 | 3 | Moreover, the word slab is specifically attached to the "subducting plate", even though in English the upper plate is just as much of a slab. Other research sho… |
| `H2-relation-expansion` | **FAIL** | 1/4 | 2 | Moreover, the word slab is specifically attached to the "subducting plate", even though in English the upper plate is just as much of a slab. Other research sho… |
| `H3-query-lexical-expansion` | **FAIL** | 1/4 | 2 | Moreover, the word slab is specifically attached to the "subducting plate", even though in English the upper plate is just as much of a slab. Other research sho… |
| `H1+H2-definition-and-relation-expansion` | **FAIL** | 2/4 | 3 | Moreover, the word slab is specifically attached to the "subducting plate", even though in English the upper plate is just as much of a slab. Other research sho… |

### H-A02 — AMBIGUOUS — AMBIGUOUS

The gold claims of the case: 2. The question: *In the research pool, which kinds of arc are distinguished, and what tells them apart?*

| Row | Verdict | Gold cited | Cited claims | What the answer said |
|---|---|---|---|---|
| `BASELINE-PATH` | **FAIL** | 0/2 | 0 |  |
| `BASELINE-RETRIEVER` | **FAIL** | 0/2 | 0 |  |
| `H1-definition-enrichment` | **FAIL** | 0/2 | 0 |  |
| `H2-relation-expansion` | **FAIL** | 0/2 | 0 |  |
| `H3-query-lexical-expansion` | **FAIL** | 0/2 | 0 |  |
| `H1+H2-definition-and-relation-expansion` | **FAIL** | 0/2 | 0 |  |

### H-A03 — AMBIGUOUS — AMBIGUOUS

The gold claims of the case: 3. The question: *In the research pool, what does the word ridge stand for?*

| Row | Verdict | Gold cited | Cited claims | What the answer said |
|---|---|---|---|---|
| `BASELINE-PATH` | **FAIL** | 0/3 | 1 | Other research showed that the ridge crest was seismically active and fresh lavas were found in the rift valley. Moreover, the word slab is specifically attache… |
| `BASELINE-RETRIEVER` | **FAIL** | 0/3 | 1 | Other research showed that the ridge crest was seismically active and fresh lavas were found in the rift valley. Moreover, the word slab is specifically attache… |
| `H1-definition-enrichment` | **FAIL** | 0/3 | 1 | Other research showed that the ridge crest was seismically active and fresh lavas were found in the rift valley. Moreover, the word slab is specifically attache… |
| `H2-relation-expansion` | **FAIL** | 0/3 | 1 | Other research showed that the ridge crest was seismically active and fresh lavas were found in the rift valley. Moreover, the word slab is specifically attache… |
| `H3-query-lexical-expansion` | **FAIL** | 0/3 | 1 | Other research showed that the ridge crest was seismically active and fresh lavas were found in the rift valley. Moreover, the word slab is specifically attache… |
| `H1+H2-definition-and-relation-expansion` | **FAIL** | 0/3 | 1 | Other research showed that the ridge crest was seismically active and fresh lavas were found in the rift valley. Moreover, the word slab is specifically attache… |

### H-A04 — AMBIGUOUS — AMBIGUOUS

The gold claims of the case: 3. The question: *In the research pool, what does the word plate denote?*

| Row | Verdict | Gold cited | Cited claims | What the answer said |
|---|---|---|---|---|
| `BASELINE-PATH` | **FAIL** | 0/3 | 2 | Other research showed that the ridge crest was seismically active and fresh lavas were found in the rift valley. The plate has a constant temperature at its bas… |
| `BASELINE-RETRIEVER` | **FAIL** | 0/3 | 2 | Other research showed that the ridge crest was seismically active and fresh lavas were found in the rift valley. The plate has a constant temperature at its bas… |
| `H1-definition-enrichment` | **FAIL** | 0/3 | 3 | Other research showed that the ridge crest was seismically active and fresh lavas were found in the rift valley. The plate has a constant temperature at its bas… |
| `H2-relation-expansion` | **FAIL** | 0/3 | 2 | Other research showed that the ridge crest was seismically active and fresh lavas were found in the rift valley. The plate has a constant temperature at its bas… |
| `H3-query-lexical-expansion` | **FAIL** | 0/3 | 2 | Other research showed that the ridge crest was seismically active and fresh lavas were found in the rift valley. The plate has a constant temperature at its bas… |
| `H1+H2-definition-and-relation-expansion` | **FAIL** | 0/3 | 3 | Other research showed that the ridge crest was seismically active and fresh lavas were found in the rift valley. The plate has a constant temperature at its bas… |

### H-C01 — CONTRADICTORY — CONTRADICTORY

The gold claims of the case: 2. The question: *Does mantle convection cause the movement of the plates?*

| Row | Verdict | Gold cited | Cited claims | What the answer said |
|---|---|---|---|---|
| `BASELINE-PATH` | PASS | 2/2 | 57 | Mantle convection causes tectonic plates to move around the Earth's surface. These plates are in slow motion, due mostly to the pull force of subducting lithosp… |
| `BASELINE-RETRIEVER` | PASS | 2/2 | 57 | Mantle convection causes tectonic plates to move around the Earth's surface. These plates are in slow motion, due mostly to the pull force of subducting lithosp… |
| `H1-definition-enrichment` | PASS | 2/2 | 73 | Mantle convection causes tectonic plates to move around the Earth's surface. These plates are in slow motion, due mostly to the pull force of subducting lithosp… |
| `H2-relation-expansion` | PASS | 2/2 | 59 | Mantle convection causes tectonic plates to move around the Earth's surface. These plates are in slow motion, due mostly to the pull force of subducting lithosp… |
| `H3-query-lexical-expansion` | PASS | 2/2 | 21 | Upwelling beneath the spreading centers is a shallow, rising component of mantle convection and in most cases not directly linked to the global mantle upwelling… |
| `H1+H2-definition-and-relation-expansion` | PASS | 2/2 | 82 | Mantle convection causes tectonic plates to move around the Earth's surface. These plates are in slow motion, due mostly to the pull force of subducting lithosp… |

### H-C02 — CONTRADICTORY — CONTRADICTORY

The gold claims of the case: 2. The question: *How long is the global system of mid-ocean ridges, according to the research pool?*

| Row | Verdict | Gold cited | Cited claims | What the answer said |
|---|---|---|---|---|
| `BASELINE-PATH` | **FAIL** | 1/2 | 9 | Other research showed that the ridge crest was seismically active and fresh lavas were found in the rift valley. The system is assumed to be at a quasi-steady s… |
| `BASELINE-RETRIEVER` | **FAIL** | 1/2 | 9 | Other research showed that the ridge crest was seismically active and fresh lavas were found in the rift valley. The system is assumed to be at a quasi-steady s… |
| `H1-definition-enrichment` | **FAIL** | 1/2 | 10 | Other research showed that the ridge crest was seismically active and fresh lavas were found in the rift valley. The system is assumed to be at a quasi-steady s… |
| `H2-relation-expansion` | **FAIL** | 1/2 | 9 | Other research showed that the ridge crest was seismically active and fresh lavas were found in the rift valley. The system is assumed to be at a quasi-steady s… |
| `H3-query-lexical-expansion` | **FAIL** | 1/2 | 9 | Other research showed that the ridge crest was seismically active and fresh lavas were found in the rift valley. The system is assumed to be at a quasi-steady s… |
| `H1+H2-definition-and-relation-expansion` | **FAIL** | 1/2 | 18 | Other research showed that the ridge crest was seismically active and fresh lavas were found in the rift valley. The system is assumed to be at a quasi-steady s… |

### H-C03 — CONTRADICTORY — CONTRADICTORY

The gold claims of the case: 2. The question: *Is subduction the driving force behind plate tectonics, or is it mantle convection?*

| Row | Verdict | Gold cited | Cited claims | What the answer said |
|---|---|---|---|---|
| `BASELINE-PATH` | PASS | 2/2 | 33 | Subduction is the descending component of mantle convection. Mantle convection causes tectonic plates to move around the Earth's surface. Plate tectonics was a … |
| `BASELINE-RETRIEVER` | PASS | 2/2 | 33 | Subduction is the descending component of mantle convection. Mantle convection causes tectonic plates to move around the Earth's surface. Plate tectonics was a … |
| `H1-definition-enrichment` | PASS | 2/2 | 35 | Subduction is the descending component of mantle convection. Mantle convection causes tectonic plates to move around the Earth's surface. Plate tectonics was a … |
| `H2-relation-expansion` | PASS | 2/2 | 37 | Subduction is the descending component of mantle convection. Mantle convection causes tectonic plates to move around the Earth's surface. Plate tectonics was a … |
| `H3-query-lexical-expansion` | PASS | 2/2 | 21 | The 100 to 170 meters higher sea level of the Cretaceous Period (144–65 Ma) is partly attributed to plate tectonics because thermal expansion and the absence of… |
| `H1+H2-definition-and-relation-expansion` | PASS | 2/2 | 43 | Subduction is the descending component of mantle convection. Mantle convection causes tectonic plates to move around the Earth's surface. Plate tectonics was a … |

**What the candidates do to a conflicting case.** The table above is the answer; the summary is below.

| Row | AMBIGUOUS: every reading represented | CONTRADICTORY: both conflicting claims carried | Mean gold claims cited (of the gold) | Mean claims cited on the 7 conflict cases |
|---|---|---|---|---|
| `BASELINE-PATH` | 0 of 4 | 2 of 3 | 0.3333 | 14.8571 |
| `BASELINE-RETRIEVER` | 0 of 4 | 2 of 3 | 0.3333 | 14.8571 |
| `H1-definition-enrichment` | 0 of 4 | 2 of 3 | 0.3889 | 17.8571 |
| `H2-relation-expansion` | 0 of 4 | 2 of 3 | 0.3333 | 15.7143 |
| `H3-query-lexical-expansion` | 0 of 4 | 2 of 3 | 0.3333 | 8.0000 |
| `H1+H2-definition-and-relation-expansion` | 0 of 4 | 2 of 3 | 0.3889 | 21.4286 |

The reading of these seven cases is the same in every row: **the system answers, it does not report the conflict.** On the ambiguous cases it cites at most ONE reading and leaves the others uncited; on the contradictory cases it usually carries both conflicting claims in its evidence (the table above gives the counts), but it does not say that they conflict — an answer that happens to cite both is not an answer that reports the contradiction. The candidates do not change this: they change which claims the retriever cites, not the SHAPE of the answer. The corpus-side candidates (H1, H2, H1+H2) cannot introduce a claim the retriever did not rank; the question-side candidate (H3) can, because it widens the query — and it also shrinks the evidence set, because the added terms lower the coverage of every claim.

## 6. The coverage shifts and the cost

**No candidate lost a gold claim that the baseline cited.** There is no coverage shift to report.

| Row | Mean retrieval (ms) | Total over the 38 cases (ms) | Overhead per case (ms) |
|---|---|---|---|
| `BASELINE-PATH` | 65 | 2184 | 52.0000 |
| `BASELINE-RETRIEVER` | 13 | 454 | 0.0000 |
| `H1-definition-enrichment` | 15 | 512 | 2.0000 |
| `H2-relation-expansion` | 12 | 437 | -1.0000 |
| `H3-query-lexical-expansion` | 12 | 436 | -1.0000 |
| `H1+H2-definition-and-relation-expansion` | 14 | 517 | 1.0000 |

The extraction is a one-off cost of 172 ms and 90293 bytes of retained text for the held-out workspace. The candidates themselves cost no extra retrieval time: they change the snapshot the retriever reads, not the retriever.

## 7. The promotion decision

The criteria are targets, not findings. Each one is checked against the numbers above, for every candidate.

### H1-definition-enrichment

| Criterion | Met | Evidence |
|---|---|---|
| recover at least two previously failing supported-answer cases | **no** | newly passing: [] — 0 of the 2 required |
| preserve the original 6/6 correct unanswerable cases | **yes** | the unanswerable cases lost: []; the abstention accuracy is 1.0000 against the baseline's 1.0000 |
| no new unsupported confident answers | **yes** | the unsupported-answer rate is 0.3684 against the baseline's 0.3684; newly failing: [] |
| no regression in the premise-refusal and revision safeguards | **yes (unchanged by construction)** | the premise path builds its own retriever from the stored workspace and cannot receive the enrichment (freeze §4.4); the premise verdicts of the six UNSUPPORTED cases are identical in every row; the held-out set holds no revision case |
| a meaningful change in recall and ranking | **no** | the recall@10 changes by 0.0000 and the MRR by -0.0027; the threshold of meaning is 0.05 |
| the latency and resource trade-off is documented | **yes** | 2.0 ms per case of retrieval overhead over the baseline; the extraction costs 172 ms once and retains 90293 bytes |

### H2-relation-expansion

| Criterion | Met | Evidence |
|---|---|---|
| recover at least two previously failing supported-answer cases | **no** | newly passing: [] — 0 of the 2 required |
| preserve the original 6/6 correct unanswerable cases | **yes** | the unanswerable cases lost: []; the abstention accuracy is 1.0000 against the baseline's 1.0000 |
| no new unsupported confident answers | **yes** | the unsupported-answer rate is 0.3684 against the baseline's 0.3684; newly failing: [] |
| no regression in the premise-refusal and revision safeguards | **yes (unchanged by construction)** | the premise path builds its own retriever from the stored workspace and cannot receive the enrichment (freeze §4.4); the premise verdicts of the six UNSUPPORTED cases are identical in every row; the held-out set holds no revision case |
| a meaningful change in recall and ranking | **no** | the recall@10 changes by 0.0175 and the MRR by -0.0018; the threshold of meaning is 0.05 |
| the latency and resource trade-off is documented | **yes** | -1.0 ms per case of retrieval overhead over the baseline; the extraction costs 172 ms once and retains 90293 bytes |

### H3-query-lexical-expansion

| Criterion | Met | Evidence |
|---|---|---|
| recover at least two previously failing supported-answer cases | **no** | newly passing: [] — 0 of the 2 required |
| preserve the original 6/6 correct unanswerable cases | **yes** | the unanswerable cases lost: []; the abstention accuracy is 1.0000 against the baseline's 1.0000 |
| no new unsupported confident answers | **no** | the unsupported-answer rate is 0.4211 against the baseline's 0.3684; newly failing: [H-P19] |
| no regression in the premise-refusal and revision safeguards | **yes (unchanged by construction)** | the premise path builds its own retriever from the stored workspace and cannot receive the enrichment (freeze §4.4); the premise verdicts of the six UNSUPPORTED cases are identical in every row; the held-out set holds no revision case |
| a meaningful change in recall and ranking | **yes** | the recall@10 changes by -0.0614 and the MRR by -0.0449; the threshold of meaning is 0.05 |
| the latency and resource trade-off is documented | **yes** | -1.0 ms per case of retrieval overhead over the baseline; the extraction costs 172 ms once and retains 90293 bytes |

### H1+H2-definition-and-relation-expansion

| Criterion | Met | Evidence |
|---|---|---|
| recover at least two previously failing supported-answer cases | **no** | newly passing: [] — 0 of the 2 required |
| preserve the original 6/6 correct unanswerable cases | **yes** | the unanswerable cases lost: []; the abstention accuracy is 1.0000 against the baseline's 1.0000 |
| no new unsupported confident answers | **yes** | the unsupported-answer rate is 0.3684 against the baseline's 0.3684; newly failing: [] |
| no regression in the premise-refusal and revision safeguards | **yes (unchanged by construction)** | the premise path builds its own retriever from the stored workspace and cannot receive the enrichment (freeze §4.4); the premise verdicts of the six UNSUPPORTED cases are identical in every row; the held-out set holds no revision case |
| a meaningful change in recall and ranking | **no** | the recall@10 changes by -0.0263 and the MRR by -0.0045; the threshold of meaning is 0.05 |
| the latency and resource trade-off is documented | **yes** | 1.0 ms per case of retrieval overhead over the baseline; the extraction costs 172 ms once and retains 90293 bytes |

### 7.1 The recommendation

**KEEP THE PRODUCTION BEHAVIOUR UNCHANGED — do not promote any of the candidates.** The recommendation is a measurement; the decision to promote anything is the owner's.

The grounds, one line each:

1. **No candidate recovers a case.** The acceptance criterion asks for two; the held-out measurement gives 0, 0, 0 and 0 respectively. The development measurement gave the same answer on a different corpus.
2. **Every candidate that touches the corpus costs something.** H1 dilutes the coverage and lost A-03 on the development set; here it changes the ranking of the gold without recovering anything. H3 adds terms to the query, and a term that matches nothing lowers the coverage of every claim.
3. **The premise and revision safeguards are out of reach.** They live above the retriever, so a retriever-level candidate cannot improve them — the residual failures of that kind are not the ones these candidates address.
4. **The publishable result is the negative one.** Two independent corpora, one frozen definition, one answer: the concept-enrichment hypothesis is not supported at the retriever level, and the reproducible negative result is worth more than a promotion that would change the production default on no evidence.

**What would change this answer.** Not a re-tuning against the held-out set — that set has now been seen, once, by this harness. A candidate for a FUTURE freeze would have to attack the failures the H4 classification actually names (in this run: BELOW_FLOOR × 14; NO_LEXICAL_MATCH × 1; GOLD_CITED_CASE_FAILED × 2; ABSTAINED_NO_COVERAGE × 1), not the coverage of the claims that are already reachable.

## 8. The limits of this measurement

* The held-out set is 38 cases over seven documents of ONE corpus, written by ONE author. It is enough to reject a candidate; it is not a measure of generalisation beyond that corpus.
* The questions were written independently of the candidates, but the CORPUS is the same topic (plate tectonics) as the development set, and the extraction rules are lexical: their behaviour on a different domain is untested.
* The three new kinds (UNSUPPORTED, AMBIGUOUS, CONTRADICTORY) are judged by the rules this harness states; they are not part of the frozen metric set, and their numbers are reported as observations.
* The candidates are retriever-level. The demand analysis, the quantity gate, the premise check and the revision cycle live above the retriever and cannot receive the enrichment (freeze §4.4). The rows of those cases are the baseline's own.
* The latency is the wall time of one machine, one run, one JVM; it is an order of magnitude, not a benchmark.
* The held-out dataset has been SEEN — by this harness, once. Any further candidate search against it would be a re-tune on the evaluation set, and is not a result.

---

**Reproduce:**

```
export JAVA_HOME=$HOME/jdk25
flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test --tests "io.matrix.knowledge.concept.HeldOutEvaluationTest" --offline --console=plain
```
