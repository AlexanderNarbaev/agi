# C2A — the held-out evaluation of the frozen candidates (H1, H2, H3)

**Status:** `MEASURED` — the three frozen candidates, and the frozen H1+H2 combination, were run against the held-out dataset.
**Date:** 2026-11-15.
**Branch / commit:** `feature/concept-to-action` / `2e91e8a8d33343a44f64a1599f4d26ab67944413`.
**Freeze:** `docs-v2/engineering/C2A-CANDIDATE-FREEZE.md` — sha256 `365655905e9e6be278f9550229958be1fa7cd31b5ca19b6f91c726e7ccfc7f7c` (version **1.1**, verified against the sha256 the freeze declares; it supersedes version 1.0, sha256 `14251fbcdf06001b670d919621b64079a35229ebabe1577081fb0b6222c08cf4`, against which the FIRST held-out run was produced).
**Held-out dataset:** `docs-v2/engineering/C2A-HELDOUT-SET.json` — sha256 `bbb3d2ad630ec610df3dbaae3ec10978d6cbbe740e13604dfd827b56b6872d77`.
**Produced by:** `matrix-core/src/test/java/io/matrix/knowledge/concept/HeldOutEvaluationTest.java` (research only; the production retrieval path is unchanged).

> The candidates were frozen BEFORE this measurement, in a file this harness has verified byte for byte. The held-out dataset was written by another worker from the RESEARCH POOL, which is not part of the development corpus. Nothing of the freeze — no extraction rule, no candidate, no denominator, no metric, no scoring rule — was changed between the freeze and this run. The held-out set has been inspected ONCE, by this harness: no candidate was re-tuned against it, no second candidate search was run, and no variant was tried.

> **The freeze version.** This run is produced against the freeze **v1.1**, which tightened the definition rule (the concept's name must be IMMEDIATELY followed by a definitional copula, and a definition may not exceed 400 characters) and corrected three metric definitions (the MRR is the reciprocal rank of the topmost gold; the supported and unsupported answers honour the case's own gold condition; the top-1 metric looks at the top-ranked claim) and made the H4 classification per gold. The v1.1 definitions are applied to BOTH the development measurement and this held-out run, so the two are comparable. The first held-out run was produced against the v1.0 freeze (sha256 `14251fbcdf06001b670d919621b64079a35229ebabe1577081fb0b6222c08cf4`); its numbers are the record of that measurement and are not rewritten.

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
| definition candidates | 208 |
| — of which SUPPORTED | 207 |
| concepts with NO definition | 296 |
| relation candidates | 22 |
| — of which SUPPORTED | 22 |
| relation candidates rejected | 202 |
| extraction time | 46 ms |
| retained text of the extraction | 74630 bytes |

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
| `H1-definition-enrichment` | 208 | 208 | 0 |
| `H2-relation-expansion` | 32 | 0 | 32 |
| `H1+H2-definition-and-relation-expansion` | 223 | 208 | 32 |

## 3. The metrics on the held-out set

**The denominators.** The recall and the MRR are the MEAN over the **19 answerable cases** (the `POSITIVE` ones), measured on the floor-0.0 candidate list; the GLOBAL (micro) recall is the share over **every gold proposition of those 19 cases**. The supported answer, the unsupported answer and the answered-with-no-gold share are over the **19 answerable cases**; the abstention accuracy is over the **6 unanswerable cases** (the `NEGATIVE` ones); the unsupported answer over ALL the ask cases is over the **25 ask cases** (the 19 answerable and the 6 unanswerable ones) and counts an answered unanswerable case as unsupported. The passed/total column is over the **25 comparable cases**; every one of the 38 cases is judged in every row. The latency of the retriever-level rows is the PURE retriever; the latency of `BASELINE-PATH` is the full production path, and the two are not comparable. The MRR is the reciprocal rank of the **topmost gold** — the gold with the smallest rank in the candidate list — and the misses are in its denominator.

| Row | Passed | Gold avail. | Recall@1 | Recall@5 | Recall@10 | Recall@20 | MRR | micro Recall@10 | Supported answer | Unsupported answer | Answered with NO gold | Abstention | Unsupported over all 25 ask cases | Mean non-gold cited | Top-1 not gold | Mean retrieval (ms) | Overhead (ms) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `BASELINE-PATH` | 17/25 | 0.7368 | 0.5351 | 0.7719 | 0.8158 | 0.8596 | 0.8600 | 0.7576 | 0.5789 (11/19) | 0.3684 (7/19) | 1/19 | 1.0000 | 0.2800 (25 ask cases) | 5.2105 | 4/19 | 58 | 50.0000 |
| `BASELINE-RETRIEVER` | 18/25 | 0.7895 | 0.5351 | 0.7719 | 0.8158 | 0.8596 | 0.8600 | 0.7576 | 0.6316 (12/19) | 0.3684 (7/19) | 1/19 | 1.0000 | 0.2800 (25 ask cases) | 5.2632 | 4/19 | 8 | 0.0000 |
| `H1-definition-enrichment` | 18/25 | 0.7895 | 0.5351 | 0.7895 | 0.8421 | 0.8860 | 0.8596 | 0.7879 | 0.6316 (12/19) | 0.3684 (7/19) | 1/19 | 1.0000 | 0.2800 (25 ask cases) | 5.0526 | 4/19 | 9 | 1.0000 |
| `H2-relation-expansion` | 18/25 | 0.7895 | 0.5351 | 0.7544 | 0.8333 | 0.8596 | 0.8605 | 0.7879 | 0.6316 (12/19) | 0.3684 (7/19) | 1/19 | 1.0000 | 0.2800 (25 ask cases) | 5.2632 | 4/19 | 8 | 0.0000 |
| `H3-query-lexical-expansion` | 17/25 | 0.7281 | 0.4649 | 0.7281 | 0.7544 | 0.8158 | 0.7818 | 0.6667 | 0.5789 (11/19) | 0.4211 (8/19) | 2/19 | 1.0000 | 0.3200 (25 ask cases) | 6.4737 | 6/19 | 8 | 0.0000 |
| `H1+H2-definition-and-relation-expansion` | 18/25 | 0.7895 | 0.5351 | 0.7895 | 0.8421 | 0.8860 | 0.8596 | 0.7879 | 0.6316 (12/19) | 0.3684 (7/19) | 1/19 | 1.0000 | 0.2800 (25 ask cases) | 5.7368 | 4/19 | 9 | 1.0000 |

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
| `BASELINE-PATH` | 18/29 | 0.2024 | 0.3214 | 0.3690 | 0.5952 | 0.5078 | 0.4286 | 1.0000 | 0.5714 | 38.2857 | 8 | 25 |
| `BASELINE-RETRIEVER` | 8/20 | 0.2024 | 0.3214 | 0.3690 | 0.5952 | 0.5078 | 0.4286 | 0.3333 | 0.5714 | 38.2857 | 8 | 9 |
| `H1-definition-enrichment` | 8/20 | 0.2381 | 0.3214 | 0.3690 | 0.5952 | 0.5422 | 0.4286 | 0.3333 | 0.5714 | 40.5714 | 7 | 9 |
| `H2-relation-expansion` | 8/20 | 0.2024 | 0.3214 | 0.3690 | 0.5952 | 0.5075 | 0.4286 | 0.3333 | 0.5714 | 39.0000 | 8 | 8 |
| `H3-query-lexical-expansion` | 5/20 | 0.1429 | 0.3214 | 0.4048 | 0.5476 | 0.4395 | 0.2143 | 0.3333 | 0.7857 | 17.6429 | 10 | 8 |
| `H1+H2-definition-and-relation-expansion` | 8/20 | 0.2024 | 0.3214 | 0.3690 | 0.5595 | 0.4938 | 0.4286 | 0.3333 | 0.5714 | 41.9286 | 8 | 9 |

(The development table is read from `docs-v2/engineering/C2A-CONCEPT-ENRICHMENT-RESULTS.json`, sha256 `963bc2a19b7e5a4d5c58c08e2e18fcba21f4f19c7d07b541f4bed0e2cddc5f45`; it is the 29-case measurement of the same freeze. Its comparable denominator is 20 cases, the held-out one's is 25.)

### 3.2 The generalisation question

The development measurement's headline was: **none of the three candidates turns a single baseline failure into a pass**; H1 lost A-03; H3 lost A-03, M-02 and R-03; H2 changed nothing. On the held-out set:

| Candidate | newly passing (held-out) | newly failing (held-out) | newly passing (development) | newly failing (development) |
|---|---|---|---|---|
| `H1-definition-enrichment` | [] | [] | [] | [] |
| `H2-relation-expansion` | [] | [] | [] | [] |
| `H3-query-lexical-expansion` | [] | [H-P19] | [] | [A-03, M-02, R-03] |
| `H1+H2-definition-and-relation-expansion` | [] | [] | [] | [] |

**Verdict.** The development measurement's *directional* claim generalises: on the held-out set no candidate turns a single baseline failure into a pass either — the newly passing list is EMPTY for H1, for H2, for H3 and for H1+H2. The *absolute levels* are not comparable, and they matter for the reading: the held-out baseline is STRONGER than the development baseline (recall@10 0.8158 against 0.3690, MRR 0.8600 against 0.5078), so the headroom a candidate has is SMALLER on the held-out set, not larger. A candidate that recovers nothing when the corpus is easy is not going to recover something when it is hard.

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
| H-P05 | `BELOW_FLOOR` | the gold is in the floor-0.0 candidate list but not in the floor-0.25 evidence set ([GOLD_REACHED, BELOW_FLOOR]) |
| H-P06 | `BELOW_FLOOR` | the gold is in the floor-0.0 candidate list but not in the floor-0.25 evidence set ([GOLD_REACHED, BELOW_FLOOR]) |
| H-P08 | `BELOW_FLOOR` | the gold is in the floor-0.0 candidate list but not in the floor-0.25 evidence set ([GOLD_REACHED, BELOW_FLOOR]) |
| H-P10 | `MIXED_GOLD_CODES` | the golds of the case carry different codes: [BELOW_FLOOR, NO_LEXICAL_MATCH, BELOW_FLOOR] |
| H-P11 | `NO_LEXICAL_MATCH` | the gold is in the workspace but shares no term with the question: it is not in the floor-0.0 candidate list either ([GOLD_REACHED, NO_LEXICAL_MATCH]) |
| H-P13 | `NO_LEXICAL_MATCH` | the gold is in the workspace but shares no term with the question: it is not in the floor-0.0 candidate list either ([GOLD_REACHED, NO_LEXICAL_MATCH]) |
| H-P17 | `BELOW_FLOOR` | the gold is in the floor-0.0 candidate list but not in the floor-0.25 evidence set ([GOLD_REACHED, BELOW_FLOOR]) |
| H-U01 | `NO_LEXICAL_MATCH` | the gold is in the workspace but shares no term with the question: it is not in the floor-0.0 candidate list either ([NO_LEXICAL_MATCH]) |
| H-U02 | `GOLD_CITED_CASE_FAILED` | every gold is cited and the case failed on: [the premise verdict is UNSUPPORTED, expected CONTRADICTED, the disputed premise is not named] |
| H-U03 | `GOLD_CITED_CASE_FAILED` | every gold is cited and the case failed on: [the premise verdict is UNSUPPORTED, expected CONTRADICTED] |
| H-U04 | `BELOW_FLOOR` | the gold is in the floor-0.0 candidate list but not in the floor-0.25 evidence set ([GOLD_REACHED, BELOW_FLOOR]) |
| H-U05 | `MIXED_GOLD_CODES` | the golds of the case carry different codes: [BELOW_FLOOR, NO_LEXICAL_MATCH] |
| H-U06 | `BELOW_FLOOR` | the gold is in the floor-0.0 candidate list but not in the floor-0.25 evidence set ([GOLD_REACHED, BELOW_FLOOR]) |
| H-A01 | `BELOW_FLOOR` | the gold is in the floor-0.0 candidate list but not in the floor-0.25 evidence set ([BELOW_FLOOR, BELOW_FLOOR, GOLD_REACHED, BELOW_FLOOR]) |
| H-A02 | `ABSTAINED_NO_COVERAGE` | the answer abstained: No claim in this workspace covers enough of the question to answer it. |
| H-A03 | `BELOW_FLOOR` | the gold is in the floor-0.0 candidate list but not in the floor-0.25 evidence set ([BELOW_FLOOR, BELOW_FLOOR, BELOW_FLOOR]) |
| H-A04 | `BELOW_FLOOR` | the gold is in the floor-0.0 candidate list but not in the floor-0.25 evidence set ([BELOW_FLOOR, BELOW_FLOOR, BELOW_FLOOR]) |
| H-C02 | `BELOW_FLOOR` | the gold is in the floor-0.0 candidate list but not in the floor-0.25 evidence set ([BELOW_FLOOR, GOLD_REACHED]) |

### 4.2 The unsupported cases: what the premise path says

The premise path builds its own retriever from the stored workspace, so it cannot receive the enrichment (freeze §4.4): its verdict is the same in every row, by construction.

| Case | The premise the corpus contradicts | Verdict | The premise named | Evidence named |
|---|---|---|---|---|
| H-U01 | that the continents plowed through the ocean crust | CONTRADICTED | [Did the continents plow through the ocean floor] | 8 claim(s): However, Wegener did not pursue this observation in his later works and his theory was dismissed by geologists because there was no mechanism to explain how con… |
| H-U02 | that subduction is known to occur on planets other than Earth | UNSUPPORTED | [] | 5 claim(s): So far, Earth is the only planet where subduction is known to occur, and subduction zones are its most important tectonic feature. |
| H-U03 | that the continents grow in area as they drift | UNSUPPORTED | [in the manner the Expanding Earth theory proposed] | 10 claim(s): Although he produced an abundance of circumstantial evidence, his theory met with little acceptance for two reasons: (1) no mechanism for continental drift was … |
| H-U04 | that intraplate lavas are chemically identical to mid-ocean ridge basalts | UNSUPPORTED | [] | 5 claim(s): Many geochemistry studies have argued that the lavas erupted in intraplate areas are different in composition from shallow-derived mid-ocean ridge basalts. |
| H-U05 | that the continents and the oceans exchanged places over geological time | UNSUPPORTED | [] | 6 claim(s): " In 1889, Alfred Russel Wallace remarked that "It was formerly a very general belief, even amongst geologists, that the great features of the earth's surface, … |
| H-U06 | that the geomagnetic reversals occur at regular intervals | UNSUPPORTED | [] | 5 claim(s): Because the field has reversed directions at known intervals throughout its history, the pattern of geomagnetic reversals in the ocean crust can be used as an i… |

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
| `H1-definition-enrichment` | PASS | 2/2 | 72 | Mantle convection causes tectonic plates to move around the Earth's surface. These plates are in slow motion, due mostly to the pull force of subducting lithosp… |
| `H2-relation-expansion` | PASS | 2/2 | 59 | Mantle convection causes tectonic plates to move around the Earth's surface. These plates are in slow motion, due mostly to the pull force of subducting lithosp… |
| `H3-query-lexical-expansion` | PASS | 2/2 | 21 | Upwelling beneath the spreading centers is a shallow, rising component of mantle convection and in most cases not directly linked to the global mantle upwelling… |
| `H1+H2-definition-and-relation-expansion` | PASS | 2/2 | 78 | Mantle convection causes tectonic plates to move around the Earth's surface. These plates are in slow motion, due mostly to the pull force of subducting lithosp… |

### H-C02 — CONTRADICTORY — CONTRADICTORY

The gold claims of the case: 2. The question: *How long is the global system of mid-ocean ridges, according to the research pool?*

| Row | Verdict | Gold cited | Cited claims | What the answer said |
|---|---|---|---|---|
| `BASELINE-PATH` | **FAIL** | 1/2 | 9 | Other research showed that the ridge crest was seismically active and fresh lavas were found in the rift valley. The system is assumed to be at a quasi-steady s… |
| `BASELINE-RETRIEVER` | **FAIL** | 1/2 | 9 | Other research showed that the ridge crest was seismically active and fresh lavas were found in the rift valley. The system is assumed to be at a quasi-steady s… |
| `H1-definition-enrichment` | **FAIL** | 1/2 | 9 | Other research showed that the ridge crest was seismically active and fresh lavas were found in the rift valley. The system is assumed to be at a quasi-steady s… |
| `H2-relation-expansion` | **FAIL** | 1/2 | 9 | Other research showed that the ridge crest was seismically active and fresh lavas were found in the rift valley. The system is assumed to be at a quasi-steady s… |
| `H3-query-lexical-expansion` | **FAIL** | 1/2 | 9 | Other research showed that the ridge crest was seismically active and fresh lavas were found in the rift valley. The system is assumed to be at a quasi-steady s… |
| `H1+H2-definition-and-relation-expansion` | **FAIL** | 1/2 | 13 | Other research showed that the ridge crest was seismically active and fresh lavas were found in the rift valley. The system is assumed to be at a quasi-steady s… |

### H-C03 — CONTRADICTORY — CONTRADICTORY

The gold claims of the case: 2. The question: *Is subduction the driving force behind plate tectonics, or is it mantle convection?*

| Row | Verdict | Gold cited | Cited claims | What the answer said |
|---|---|---|---|---|
| `BASELINE-PATH` | PASS | 2/2 | 33 | Subduction is the descending component of mantle convection. Mantle convection causes tectonic plates to move around the Earth's surface. Plate tectonics was a … |
| `BASELINE-RETRIEVER` | PASS | 2/2 | 33 | Subduction is the descending component of mantle convection. Mantle convection causes tectonic plates to move around the Earth's surface. Plate tectonics was a … |
| `H1-definition-enrichment` | PASS | 2/2 | 38 | Subduction is the descending component of mantle convection. Mantle convection causes tectonic plates to move around the Earth's surface. Plate tectonics was a … |
| `H2-relation-expansion` | PASS | 2/2 | 37 | Subduction is the descending component of mantle convection. Mantle convection causes tectonic plates to move around the Earth's surface. Plate tectonics was a … |
| `H3-query-lexical-expansion` | PASS | 2/2 | 21 | The 100 to 170 meters higher sea level of the Cretaceous Period (144–65 Ma) is partly attributed to plate tectonics because thermal expansion and the absence of… |
| `H1+H2-definition-and-relation-expansion` | PASS | 2/2 | 45 | Subduction is the descending component of mantle convection. Mantle convection causes tectonic plates to move around the Earth's surface. Plate tectonics was a … |

**What the candidates do to a conflicting case.** The table above is the answer; the summary is below.

| Row | AMBIGUOUS: every reading represented | CONTRADICTORY: both conflicting claims carried | Mean gold claims cited (of the gold) | Mean claims cited on the 7 conflict cases |
|---|---|---|---|---|
| `BASELINE-PATH` | 0 of 4 | 2 of 3 | 0.3333 | 14.8571 |
| `BASELINE-RETRIEVER` | 0 of 4 | 2 of 3 | 0.3333 | 14.8571 |
| `H1-definition-enrichment` | 0 of 4 | 2 of 3 | 0.3889 | 18.0000 |
| `H2-relation-expansion` | 0 of 4 | 2 of 3 | 0.3333 | 15.7143 |
| `H3-query-lexical-expansion` | 0 of 4 | 2 of 3 | 0.3333 | 8.0000 |
| `H1+H2-definition-and-relation-expansion` | 0 of 4 | 2 of 3 | 0.3889 | 20.4286 |

The reading of these seven cases is the same in every row: **the system answers, it does not report the conflict.** On the ambiguous cases it cites at most ONE reading and leaves the others uncited; on the contradictory cases it usually carries both conflicting claims in its evidence (the table above gives the counts), but it does not say that they conflict — an answer that happens to cite both is not an answer that reports the contradiction. The candidates do not change this: they change which claims the retriever cites, not the SHAPE of the answer. The corpus-side candidates (H1, H2, H1+H2) cannot introduce a claim the retriever did not rank; the question-side candidate (H3) can, because it widens the query — and it also shrinks the evidence set, because the added terms lower the coverage of every claim.

## 6. The coverage shifts and the cost

| Case | Candidate | Side | Gold proposition | Floor under the baseline | Floor under the candidate |
|---|---|---|---|---|---|
| H-P06 | `H3-query-lexical-expansion` | question | The crystallized magma forms a new crust of basalt known as MORB for mid-ocean ridge basalt, and gabbro below it in the lower oceanic crust. | (0.27, 0.28] | (0.22, 0.23] |
| H-P19 | `H3-query-lexical-expansion` | question | Sea water seeps into oceanic lithosphere through fractures and pores, and reacts with minerals in the crust and mantle to form hydrous minerals (such as serpent… | (0.36, 0.37] | (0.24, 0.25] |
| H-P19 | `H3-query-lexical-expansion` | question | During subduction, a series of minerals in these slabs such as serpentine can be stable at different pressures within the slab geotherms, and may transport a si… | (0.36, 0.37] | (0.18, 0.19] |

(The `corpus` rows are the frozen measurement of the development run, applied to the corpus-side candidates; the `question` row is the same measurement applied to H3, whose candidate is the expanded question — the development run did not include it, because the question-side candidate has no snapshot of its own. The floor is the coverage ratio the retriever divides by, measured with the production retriever alone and without the representations.)

| Row | Mean retrieval (ms) | Total over the 38 cases (ms) | Overhead per case (ms) |
|---|---|---|---|
| `BASELINE-PATH` | 58 | 2258 | 50.0000 |
| `BASELINE-RETRIEVER` | 8 | 297 | 0.0000 |
| `H1-definition-enrichment` | 9 | 337 | 1.0000 |
| `H2-relation-expansion` | 8 | 299 | 0.0000 |
| `H3-query-lexical-expansion` | 8 | 306 | 0.0000 |
| `H1+H2-definition-and-relation-expansion` | 9 | 348 | 1.0000 |

The extraction is a one-off cost of 46 ms and 74630 bytes of retained text for the held-out workspace. The candidates themselves cost no extra retrieval time: they change the snapshot the retriever reads, not the retriever.

## 7. The promotion decision

The criteria are targets, not findings. Each one is checked against the numbers above, for every candidate.

### H1-definition-enrichment

| Criterion | Met | Evidence |
|---|---|---|
| recover at least two previously failing supported-answer cases | **no** | newly passing: [] — 0 of the 2 required |
| preserve the original 6/6 correct unanswerable cases | **yes** | the unanswerable cases lost: []; the abstention accuracy is 1.0000 against the baseline's 1.0000 |
| no new unsupported confident answers | **yes** | the unsupported-answer rate is 0.3684 against the baseline's 0.3684; newly failing: [] |
| no regression in the premise-refusal and revision safeguards | **yes (unchanged by construction)** | the premise path builds its own retriever from the stored workspace and cannot receive the enrichment (freeze §4.4); the premise verdicts of the six UNSUPPORTED cases are identical in every row; the held-out set holds no revision case |
| a meaningful change in recall and ranking | **no** | the recall@10 changes by 0.0263 and the MRR by -0.0004; the threshold of meaning is 0.05 and the change must not be a degradation |
| the latency and resource trade-off is documented | **yes** | 1.0 ms per case of retrieval overhead over the baseline; the extraction costs 46 ms once and retains 74630 bytes |

### H2-relation-expansion

| Criterion | Met | Evidence |
|---|---|---|
| recover at least two previously failing supported-answer cases | **no** | newly passing: [] — 0 of the 2 required |
| preserve the original 6/6 correct unanswerable cases | **yes** | the unanswerable cases lost: []; the abstention accuracy is 1.0000 against the baseline's 1.0000 |
| no new unsupported confident answers | **yes** | the unsupported-answer rate is 0.3684 against the baseline's 0.3684; newly failing: [] |
| no regression in the premise-refusal and revision safeguards | **yes (unchanged by construction)** | the premise path builds its own retriever from the stored workspace and cannot receive the enrichment (freeze §4.4); the premise verdicts of the six UNSUPPORTED cases are identical in every row; the held-out set holds no revision case |
| a meaningful change in recall and ranking | **no** | the recall@10 changes by 0.0175 and the MRR by 0.0005; the threshold of meaning is 0.05 and the change must not be a degradation |
| the latency and resource trade-off is documented | **yes** | 0.0 ms per case of retrieval overhead over the baseline; the extraction costs 46 ms once and retains 74630 bytes |

### H3-query-lexical-expansion

| Criterion | Met | Evidence |
|---|---|---|
| recover at least two previously failing supported-answer cases | **no** | newly passing: [] — 0 of the 2 required |
| preserve the original 6/6 correct unanswerable cases | **yes** | the unanswerable cases lost: []; the abstention accuracy is 1.0000 against the baseline's 1.0000 |
| no new unsupported confident answers | **no** | the unsupported-answer rate is 0.4211 against the baseline's 0.3684; newly failing: [H-P19] |
| no regression in the premise-refusal and revision safeguards | **yes (unchanged by construction)** | the premise path builds its own retriever from the stored workspace and cannot receive the enrichment (freeze §4.4); the premise verdicts of the six UNSUPPORTED cases are identical in every row; the held-out set holds no revision case |
| a meaningful change in recall and ranking | **no** | the recall@10 changes by -0.0614 and the MRR by -0.0782; the threshold of meaning is 0.05 and the change must not be a degradation |
| the latency and resource trade-off is documented | **yes** | 0.0 ms per case of retrieval overhead over the baseline; the extraction costs 46 ms once and retains 74630 bytes |

### H1+H2-definition-and-relation-expansion

| Criterion | Met | Evidence |
|---|---|---|
| recover at least two previously failing supported-answer cases | **no** | newly passing: [] — 0 of the 2 required |
| preserve the original 6/6 correct unanswerable cases | **yes** | the unanswerable cases lost: []; the abstention accuracy is 1.0000 against the baseline's 1.0000 |
| no new unsupported confident answers | **yes** | the unsupported-answer rate is 0.3684 against the baseline's 0.3684; newly failing: [] |
| no regression in the premise-refusal and revision safeguards | **yes (unchanged by construction)** | the premise path builds its own retriever from the stored workspace and cannot receive the enrichment (freeze §4.4); the premise verdicts of the six UNSUPPORTED cases are identical in every row; the held-out set holds no revision case |
| a meaningful change in recall and ranking | **no** | the recall@10 changes by 0.0263 and the MRR by -0.0004; the threshold of meaning is 0.05 and the change must not be a degradation |
| the latency and resource trade-off is documented | **yes** | 1.0 ms per case of retrieval overhead over the baseline; the extraction costs 46 ms once and retains 74630 bytes |

### 7.1 The recommendation

**KEEP THE PRODUCTION BEHAVIOUR UNCHANGED — do not promote any of the candidates.** The recommendation is a measurement; the decision to promote anything is the owner's.

The grounds, one line each:

1. **No candidate recovers a case.** The acceptance criterion asks for two; the held-out measurement gives 0, 0, 0 and 0 respectively. The development measurement gave the same answer on a different corpus.
2. **Every candidate that touches the corpus costs something.** H1 dilutes the coverage and lost A-03 on the development set; here it changes the ranking of the gold without recovering anything. H3 adds terms to the query, and a term that matches nothing lowers the coverage of every claim.
3. **The premise and revision safeguards are out of reach.** They live above the retriever, so a retriever-level candidate cannot improve them — the residual failures of that kind are not the ones these candidates address.
4. **The publishable result is the negative one.** Two independent corpora, one frozen definition, one answer: the concept-enrichment hypothesis is not supported at the retriever level, and the reproducible negative result is worth more than a promotion that would change the production default on no evidence.

**What would change this answer.** Not a re-tuning against the held-out set — that set has now been seen, once, by this harness. A candidate for a FUTURE freeze would have to attack the failures the H4 classification actually names (the answerable cases of this run: BELOW_FLOOR × 4; MIXED_GOLD_CODES × 1; NO_LEXICAL_MATCH × 2), not the coverage of the claims that are already reachable. A candidate that only moves a claim DOWN the ranking is not such a candidate: the freeze's H4 taxonomy says that a `BELOW_FLOOR` failure is a COVERAGE failure, and coverage is what the enrichment moves — in the wrong direction.

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
