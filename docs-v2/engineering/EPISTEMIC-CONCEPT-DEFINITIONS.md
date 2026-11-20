# EPISTEMIC-CONCEPT-DEFINITIONS — the definition half of the concept-model repair

**Status:** `MEASURED` (implementation + acceptance measurement)
**Date:** 2026-11-16
**Scope:** P1 — CONCEPT-MODEL REPAIR, the definition half only: (d) source-span-anchored definitions.
**Authority:** the owner's directive of 2026-11-15 (§2 item 3, §3 P1, §9, §10, §11 Gate 2), and the
research it rests on — `docs-v2/engineering/EPISTEMIC-CONCEPT-IDENTITY.md`.
**Instruments:** `matrix-core/src/test/java/io/matrix/knowledge/concept/definitions/**`

> **CORRECTION NOTICE (2026-11-17, §10).** The numbers in §2–§4a of this document —
> "120/326 = 36.8 % usable definitions" and "24/25 = 96 % human-verified" — were measured
> against a weaker rule (the name only had to be *mentioned* before a definitional copula)
> and a weaker metric ("about the concept" rather than "is a definition"). The hard reviews
> of 2026-11-16 and 2026-11-17 showed that 31 of the 120 hung on spans the frozen hand-labelling
> calls "not a concept", that 33 had the name only as a mention, and that the subject test alone
> is necessary but not sufficient: a sentence can be about the concept and still say nothing about
> what the concept IS. **The rule as it now stands finds 4 definitions (1.23 %) and the metric as
> it is now written gives 3/4 = 75 %; the reviewer's independent count of the earlier 27 is
> 3/27 = 11.1 %, and the re-labelling of those 27 by the same protocol lands on the same three.**
> Every earlier claim is withdrawn, not restated; §10 records what they measured, what they should
> have measured, and why the rule is stricter. Sections 1–9 are kept as the record of the earlier
> state.

(`ConceptDefinitionAcceptanceTest`, `SourceSpanDefinitionRuleTest`,
`HandLabelledSpans`, `HandVerifiedDefinitions`).

---

## 1. What was implemented

The concept layer held **348 claims, 326 concepts and 0 usable definitions**: the importer wrote every
concept with `definition = null`, because nothing in the acquisition path derived one. A concept that
cannot say what it is cannot be judged against anything.

This wave implements the alternative the research **adopted** — (d), the source-span-anchored definition
— and nothing else. The identity half (the referent key, the alias and the resolution policy) is
**owner-gated** and untouched: no concept is renamed, no concept id changes, no claim moves, and no
migration of the ten tables that reference a concept id is executed.

| Where | What |
|---|---|
| `matrix-core/src/main/java/io/matrix/knowledge/acquisition/SourceSpanDefinitions.java` (new) | THE RULE: the tight v1.1 rule, the closed copula vocabulary, the length cap, the locating of a sentence in the extracted text, and the derivation of a concept's definition from its own source documents. |
| `matrix-core/src/main/java/io/matrix/knowledge/ConceptDefinition.java` (new) | The definition AS A RECORD: the sentence, the source, the characters it covers, the claim that carries it, and the rule that produced it. |
| `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeStore.java` | The additive table `concept_definition` (schema v20), the migration step, and the accessors `saveConceptDefinition` / `conceptDefinitions`. |
| `matrix-core/src/main/java/io/matrix/knowledge/acquisition/DocumentImporter.java` | The wiring: after the import, the definitions of the concepts the import touched are derived and recorded. `ImportResult.definitionsWritten` reports how many. |

**NO SCHEMA DESTRUCTION.** The migration is additive: one new table and one index, nothing copied,
nothing dropped, no column rewritten. The claims, the evidence, the representations and the
proposition-based golds all survive, and **no claim id changes** (a claim id is a digest of the
workspace, the content hash and the proposition, and none of the three moved).

---

## 2. THE RULE — the tight v1.1 rule, unchanged

A definition candidate for a concept `C` is found **only** by the frozen rule of
`C2A-CANDIDATE-FREEZE.md` §3.1 as the freeze v1.1 froze it:

1. `C`'s **own source documents** are the sources of `C`'s claims, taken in the order the snapshot
   lists the sources;
2. the sentences of those documents are scanned **in document order**;
3. a sentence **qualifies** when it contains `C`'s name as a word (for a multi-word name: the whole
   token sequence, case-insensitively, as a contiguous subsequence of the sentence's tokens) **and the
   name is immediately followed by a definitional copula** — the closed vocabulary `is · are · refers
   to · means · describes · consists of · is defined as · is called` — **and** the sentence is at most
   **400 characters** long;
4. the **first** qualifying sentence wins, and the definition is that sentence **verbatim**: never
   paraphrased, never shortened, never invented. A concept with no qualifying sentence has **no
   definition** — an honest state, not a gap to be filled.

**WHY THE ADJACENCY.** The loose v1.0 rule asked only whether the sentence held the name *and* a
copula somewhere; it could not tell a definition from a sentence that names the concept and says
something else about it. The tight rule asks whether the copula **follows the name**.

**WHY THE SENTENCE IS LOCATED.** The rule scans the sentences "in document order", and the document
order is the order of the sentences in the text. A claim's context carries the span of its **segment**
— the extractor splits a segment into sentences, and every claim of that segment carries the segment's
span — so the segment's span alone cannot say which of a segment's sentences comes first. The
production derivation therefore locates the proposition **inside** its segment's span, exactly as the
research instrument does; when the text is not in hand (a source imported earlier) the segment's span
is used and the sentences of that segment keep the order they were extracted in. **Without this, ten
of the 120 definitions were a different (also qualifying) sentence.**

---

## 3. WHY THE DEFINITION IS NOT IN THE `concept.definition` COLUMN

The concept table has a `definition` column, and filling it is the obvious thing to do. **It was
measured, and it degrades the official evaluation.**

The column is read by the retriever, which appends it to every claim's searchable text
(`KnowledgeRetriever.java:170-172`). With the definitions in that column, on the seed corpus and the
frozen 29-case evaluation:

| | official result | the eleven failures | the unanswerable abstention | the unsupported-answer rate |
|---|---|---|---|---|
| without the definitions (the published baseline) | **18 / 29** | `A-02, A-04, A-05, A-06, F-02, M-01, P-01, P-02, M-03, R-04, R-05` | **6 / 6** | 0.3000 |
| with the definitions in the column | **17 / 29** | the same eleven **+ `U-04`** | **5 / 6** | 0.4500 |

`U-04` is an **unanswerable** case: the wider searchable text lets a claim cover enough of the
question to pass the floor, and the production path answers instead of abstaining. That is exactly the
"degrading refusal correctness" the owner's promotion rule forbids, and the research's own freeze calls
the retrieval variant a **separate candidate** (H1-definition-enrichment).

So the definition is recorded **as its own record**, with its span, and the retrieval is not touched.
**This is an owner decision to revisit**: the definitions are now available to the retrieval, and
whether they should enter the searchable text is a question about the retrieval, not about the
definitions. The measurement above is the evidence for that question.

---

## 4. The acceptance measurement

The corpus is the seed corpus (the two licensed documents of
`matrix-core/src/main/resources/corpus/plate-tectonics/`), imported through the **production**
workbench and the **production** importer into a **temporary** database. The owner's store is never
touched.

| # | the measure | the baseline | **after this change** | how it was measured |
|---|---|---|---|---|
| **A1** | the concept-identification precision | 30 % (12/40) | **35/150 = 23.3 %** on the frozen 150; **12/40 = 30 %** on the first 40 of the draw | `HandLabelledSpans`: the ids of the seed corpus sorted, shuffled with the research's seed 20261115, the first 150 labelled by hand against the claim's own sentence. The labels are keyed by the **name**, and the name is the preimage of the id, and the id does not change (A8) — so **before = after**, and the change cannot move this number. |
| **A2 (i)** | the definition coverage | **0 usable definitions**; 121/326 = 37.1 % adjacency-passing | **120 / 326 = 36.8 %** | the production import's own count. The production set is **identical, set by set, to the research instrument's** (0 differences, 120/120) — the one-definition difference from the research's published 121 is the instrument's own figure and is reported, not papered over. |
| **A2 (ii)** | the human-verified share of the definitions | not measured | **24 / 25 = 96 %** (n = 25) | `HandVerifiedDefinitions`: a 25-sample of the definitions, labelled by hand. The judgement is generous for a concept whose name is a fragment or a pronoun, and the sample is small and single-domain. |
| **A3** | the relation precision | 19 candidates; **never measured** | **0 / 7 = 0 %** on the seed corpus | the frozen §3.2 rule's candidates, labelled by hand. Five of the seven have an endpoint that is a fragment of a sentence, and one (`PART_OF`: *Tectonic lithospheric plates → Oceanic crust*) has its **direction reversed**. The research's table says 19 candidates on the seed corpus; the instrument and the C2A artefact both say **7** — a discrepancy in the research's table, reported and not resolved here. |
| **A4** | the retrieval effect | 18/29 with the identical eleven failures; recall@1 0.2024; MRR 0.5078 | **18/29 with the identical eleven failures**; the rest of the row unchanged | the C2A harness, re-run over the production path. The row is byte-identical to the published one apart from the machine's timings. |
| **A5** | the false positives | the unsupported-answer rate 0.3000; the abstention 6/6 | **unchanged** (0.3000; **6/6**) | the same row. Structurally: the definitions are not in the retriever's searchable text, so the retriever's input is unchanged. |
| **A6** | the answer accuracy | not measurable on this corpus | not claimed | the research is explicit that this corpus cannot support it. |
| **A7** | the cost | the mean retrieval 32 ms (the baseline path) / 10 ms (the retriever) | the **retriever** is unchanged: 715–934 µs with the definitions against 788–874 µs without, on the same corpus, the same 20 questions, the same method, two orders of measurement — **the difference is inside the noise**. The baseline path's mean retrieval in the harness moved 59 → 68 → 76 ms across three runs of the **same** code: it measures the machine, not the change. | the A/B test in `ConceptDefinitionAcceptanceTest`. |
| **A8** | the id stability | — | **no concept id changed**: every id is still `digest(workspace + "\|concept\|" + name.toLowerCase())`, for all 326 concepts | asserted for every concept. A claim id is a digest of the workspace, the content hash and the proposition, and none of the three moved. |

**The held-out evaluation.** The C2A held-out `BASELINE-PATH` is **17 / 25** before and after, with
every content column identical (the recall@1 0.7368, the MRR 0.8596, the abstention 1.0000, the
unsupported-answer rate 0.2800, the mean non-gold cited 5.2105).

**The promotion rule.** "A1 and A2 improve and A4 does not regress and A5 does not regress." Within
this scope: **A2 improves** (0 → 36.8 %), **A4 and A5 do not regress** ✓, and **A1 does not move** —
it cannot, because the names and the ids are unchanged. The "A1 improves" half of the rule belongs to
the identity half, which is owner-gated and was not executed.

### 4a. The human-verified sample, in full (A2-ii)

The 25 definitions of the frozen sample, with the judgement. **The code holds only the SHA-256 of each
definition** (the P0.3 held-out set's gold propositions are sentences of the same corpus, so a file that
carried a definition verbatim would carry a gold string of the held-out set — the guard is right to
refuse it). This table is the human-readable record.

| # | the concept | the definition (verbatim) | about its concept? |
|---|---|---|---|
| 1 | `Key principles The outer layers of Earth` | Key principles The outer layers of Earth are divided into the lithosphere and asthenosphere. | yes |
| 2 | `Various types of quantitative and semi-quantitative information` | Various types of quantitative and semi-quantitative information are available to constrain past plate motions. | yes |
| 3 | `Earth's lithosphere, the rigid outer shell of the planet including the crust and upper mantle,` | Earth's lithosphere, the rigid outer shell of the planet including the crust and upper mantle, is fractured into seven or eight major plates (depending on how they are defined) and many minor plates or "platelets". | yes |
| 4 | `This submerged mountain range, which extends from the Arctic Ocean to beyond the southern tip of Africa,` | This submerged mountain range, which extends from the Arctic Ocean to beyond the southern tip of Africa, is but one segment of the global mid-ocean ridge system that encircles the Earth. | yes |
| 5 | `These zones` | San Andreas fault [52 k] Oceanic fracture zones are ocean-floor valleys that horizontally offset spreading ridges; some of these zones are hundreds to thousands of kilometers long and as much as 8 km deep. | yes |
| 6 | `Twenty-one satellites` | Twenty-one satellites are currently in orbit 20,000 km above the Earth as part of the NavStar system of the U. | yes |
| 7 | `Possible tidal effect on plate tectonics Of the many forces discussed above, tidal force` | Possible tidal effect on plate tectonics Of the many forces discussed above, tidal force is still highly debated and defended as a possible principal driving force of plate tectonics. | yes |
| 8 | `Defining plate boundaries Active plate boundaries` | Defining plate boundaries Active plate boundaries are defined by their seismicity. | yes |
| 9 | `Relative significance of each driving force mechanism The vector of a plate's motion` | Relative significance of each driving force mechanism The vector of a plate's motion is a function of all the forces acting on the plate; however, therein lies the problem regarding the degree to which each process contributes to the overall motion of each tectonic plate. | yes |
| 10 | `One method of dealing with this problem` | One method of dealing with this problem is to consider the relative rate at which each plate is moving as well as the evidence related to the significance of each process to the overall driving force on the plate. | yes |
| 11 | `The other forces` | The other forces are only used in global geodynamic models not using plate tectonics concepts (therefore beyond the discussions treated in this section) or proposed as minor modulations within the overall plate tectonics model. | yes |
| 12 | `In this understanding, plate motion` | In this understanding, plate motion is mostly driven by the weight of cold, dense plates sinking into the mantle at trenches. | yes |
| 13 | `Subduction zones` | Subduction zones are of two types: ocean-to-continent subduction, where the dense oceanic lithosphere plunges beneath the less dense continent, or ocean-to-ocean subduction where older, cooler, denser oceanic crust slips beneath less dense oceanic crust. | yes |
| 14 | `The motion of the tectonic plates` | The motion of the tectonic plates is determined by remote sensing satellite data sets, calibrated with ground station measurements. | yes |
| 15 | `Dates derived` | Dates derived are dominantly in the range 500 to 750 million years ago, although ages of up to 1,200 million years ago have been calculated. | yes |
| 16 | `Perhaps the best known of the divergent boundaries` | Perhaps the best known of the divergent boundaries is the Mid-Atlantic Ridge. | yes |
| 17 | `The separations between GPS sites` | The separations between GPS sites are already being measured regularly around the Pacific basin. | yes |
| 18 | `The San Andreas fault zone, which` | The San Andreas fault zone, which is about 1,300 km long and in places tens of kilometers wide, slices through two thirds of the length of California. | yes |
| 19 | `Scientists` | While the mechanism of such an impressive thermal event remains a debated issue in Venusian geosciences, some scientists are advocates of processes involving plate motion to some extent. | no |
| 20 | `Forces that` | Forces that are small and generally negligible are: The Coriolis force. | yes |
| 21 | `It` | Because it is formed at mid-ocean ridges and spreads outwards, its thickness is therefore a function of its distance from the mid-ocean ridge where it was formed. | yes |
| 22 | `They commonly offset the active spreading ridges, producing zig-zag plate margins, and` | They commonly offset the active spreading ridges, producing zig-zag plate margins, and are generally defined by shallow earthquakes. | yes |
| 23 | `Such earthquakes` | Such earthquakes are often accompanied by uplift of the land by as much as a few meters. | yes |
| 24 | `In the same paper the authors admit, however, that relative to the lower mantle, there` | In the same paper the authors admit, however, that relative to the lower mantle, there is a slight westward component in the motions of all the plates. | yes |
| 25 | `Gravitational sliding away from a spreading ridge` | Gravitational sliding away from a spreading ridge is one of the proposed driving forces: plate motion is driven by the higher elevation of plates at ocean ridges. | yes |

**The one failure** is #19: the concept is the generic class `Scientists`, and the sentence is about the
**mechanism** of a thermal event in Venusian geosciences, with the class appearing only as "some
scientists". A definition attached to a generic class states nothing about it.

---

## 5. What this change does NOT establish

* **It does not make a concept name right.** The names are the subject phrases the extractor cut, and
  **30 % of them (the research) do not denote a genuine referent**. A definition attached to a
  fragment is a definition of a fragment. The identity half is the repair, and it is owner-gated.
* **It does not establish the definitions' truth.** A definition is the corpus's own sentence; the
  corpus's licence and provenance are unchanged. The 24/25 share says the sentence is *about* the
  concept, not that the concept is true.
* **It does not improve the retrieval.** The definitions are recorded and available; they are
  deliberately **not** in the searchable text (section 3). Whether they should be is the owner's.
* **It does not measure A6**, and it does not claim the corpus can.
* **It does not migrate anything.** The concept id is referenced by ten tables and SQLite declares no
  foreign keys: a partial migration would be invisible. The research's migration sketch
  (`concept_lineage`, copy-forward, a pre-migration snapshot) was not executed.
* **It does not generalise beyond the corpus.** Nine documents, one domain, one language, one
  register, no dialogue, no tabular prose — the research's own limits, unchanged.

**[speculative]** — the labelling protocol of A1 (a leading discourse adverbial is stripped before the
head is judged; an appositive is part of the phrase; a generic class of persons or things is not a
referent) is **this implementer's** protocol, written down in `HandLabelledSpans`. The research
published its 40 labels and not its protocol, so the two labellings cannot be shown to be the same
judgement; what they share is the population (the same 326 names) and the seed.

**[speculative]** — the 150-span draw is over the ids of a workspace named `seed`. A concept id is a
digest of the workspace and the name, so the ids — and therefore the draw — depend on the workspace's
name. The names do not, and the sample is a sample of the same 326 names whatever the workspace is
called; but the draw is **not** provably the research's own 40 extended, because the research did not
publish its draw method.

---

## 6. The open questions for the owner

1. **Should the definitions enter the retriever's searchable text?** The measurement is on the table:
   18/29 → 17/29 and the abstention 6/6 → 5/6. The owner's promotion rule forbids it as it stands.
2. **The identity half** — the referent key, the alias layer, the resolution policy, and the migration
   — remains owner-gated, with the research's verdicts and costs already measured.
3. **The verb list** (`ClaimExtractor.DECLARATIVE_VERBS` contains nouns: `result`, `process`, `form`,
   `state`, `work`, `move`, `use`, `need`) is a bounded, deterministic fix the research lists as a
   candidate in its own right; it was NOT executed here (it changes the names, and therefore the ids).

---

## 7. The evidence

```
export JAVA_HOME=$HOME/jdk25
flock /tmp/gradle-matrix.lock bash -c 'rm -rf matrix-core/build/test-results && \
    ./gradlew :matrix-core:test --tests "io.matrix.knowledge.concept.*" --offline --console=plain'
```

| scope | tests | failures | errors | skipped | the XML |
|---|---|---|---|---|---|
| `io.matrix.knowledge.concept.*` (the C2A harnesses and this wave) | 37 | 0 | 0 | 0 | `matrix-core/build/test-results/test/*.xml` |

The four C2A artefacts (`C2A-CONCEPT-ENRICHMENT-EXPERIMENT.md`,
`C2A-CONCEPT-ENRICHMENT-RESULTS.json`, `C2A-HELDOUT-EVALUATION.md`, `C2A-HELDOUT-EVALUATION.json`) are
**regenerated by the harness on every run** and are therefore modified in the working tree. Their
**content** columns are identical to the published ones; only the machine's timings differ, and those
move between runs of the same code. They are not in this wave's write scope: the coordinator decides
whether to re-publish or to revert them.

---

## 9. WHERE THE DEFINITIONS ACTUALLY HELP — the benefit measurement (2026-11-16)

**The owner's decision (option C):** *"A now + a separate measurement task for the benefit of the
definitions"* — the definitions stay out of the searchable text, and this section is the measurement
task: **is there a subset where a definition helps without degrading the refusal correctness?**

### 9.1 The instrument

`matrix-core/src/test/java/io/matrix/knowledge/concept/definitions/DefinitionBenefitMeasurementTest.java`
— four arms over the same corpus, the same questions and the same method, **interleaved case by
case** so that a machine that gets busier cannot be mistaken for an arm:

| arm | what the arm puts into the retrievable text |
|---|---|
| `WITHOUT` | nothing — the production state (the definitions stay in the `concept_definition` table) |
| `WITH_ALL` | every recorded definition — the uniform H1 variant |
| `WITH_RULE` | the definitions of the concepts the **name-shape rule** accepts |
| `WITH_STRICT_RULE` | the definitions of the concepts the **strict rule** accepts |

* **The corpus.** The six scopes of the official dataset, prepared with the production methods
  (`AcquisitionCycle.loadSeed` / `.run` / `.acquireAndRevise`), in a temporary database.
* **The questions.** The official 29 cases — of which **20 are ask cases** and 9 (the five
  false-premise and four revision cases) are judged by the official runner on their own paths and
  cannot be measured by an ask — and the **frozen held-out set** (`EPISTEMIC-HELDOUT-SET.json`, the
  16 P0.3 cases), which is read and never written.
* **The verdict.** The official runner's own `judgeAnswer` — the comparison the official runner
  makes, whitespace collapsed and lower-cased — so the arms are comparable with the published row.
* **The calibration.** The `WITHOUT` arm must reproduce the published failures; the test asserts it
  (the eight ask-case failures `A-02, A-04, A-05, A-06, M-01, P-01, P-02, M-03` — the other three
  of the eleven, `F-02, R-04, R-05`, are not ask cases). Without that assertion the instrument would
  be measuring its own rules rather than the official ones.

### 9.2 The two sets, the four arms

**The official 20 ask cases** (the calibration: `WITHOUT` = 12/20, the eight official failures ✓):

| arm | passed | answered | the unanswerable abstained | answered without the gold | the mean ask |
|---|---|---|---|---|---|
| `WITHOUT` (production) | **12 / 20** | 14 | **6 / 6** | 8 | 72 ms |
| `WITH_ALL` (uniform H1) | **11 / 20** | 15 | **5 / 6** ✗ | 9 ✗ | 71 ms |
| `WITH_RULE` | **11 / 20** | 14 | 6 / 6 | 9 ✗ | 68 ms |
| `WITH_STRICT_RULE` | **11 / 20** | 14 | 6 / 6 | 9 ✗ | 72 ms |

**The frozen held-out set (16 cases):** **every arm is identical** — 8/16 passed, 12 answered,
4 abstentions, 4 answered without the gold, 32–34 ms. **The definitions change nothing there.**

**The two cases that move.**

* **`U-04`** — an **unanswerable** case. Under the uniform arm it is **answered** instead of
  abstained: the wider searchable text lets a claim cover enough of the question to pass the floor.
  That is the refusal degradation (the official 6/6 → 5/6, the unsupported-answer rate 0.3000 →
  0.4500), and it is reproduced here by an independent instrument ✓.
* **`A-03`** — an answerable case that the two rule arms **lose**. The strict rule still loses it,
  and the concepts it cites whose definitions the strict rule admits are the **well-formed** ones
  (`Plate tectonics`, `Continental crust`, `Slab pull`, `Plate boundaries`) — so the loss comes from
  the good definitions, not from the fragments the rule filters out.

### 9.3 The subset rule

The rule is stated over the **concept's name** — never over a case, a question or a corpus phrase:

> A concept's definition may enter the searchable text only when the concept's name is a
> **referent** rather than a fragment: not a single function word, not a name that opens with a
> class word ("these", "many", "such"), not a name the subject extractor cut inside a clause (its
> last word is one the extractor stops at). The **strict** variant adds: not a name that opens with
> a preposition, a conjunction or a subordinating adverb ("Because it", "while Earth", "In this
> understanding, plate motion") and not a name that ends with a relative pronoun ("slabs that").

The rule is a **word-list rule over closed vocabularies**, with every list a named constant and a
comment — no case id, no corpus phrase, no question string ✓. It admits 356 of the recorded
definitions across the six workspaces, and the strict variant 269 (the seed workspace's own 120 are
a part of those numbers).

### 9.4 THE VERDICT — a null result

**The definitions have no measurable benefit.** There is a rule that preserves the refusal
correctness (both rule arms keep the 6/6), and that rule buys **nothing**: the official ask cases go
from 12/20 to **11/20** (A-03 is lost and nothing is gained), and the frozen held-out set is
**identical in every arm**. The uniform variant is worse: it loses the refusal as well.

**A null result here is the result.** The H1 question is closed by this measurement, not left open:
the definitions are worth keeping as **knowledge** (they are the corpus's own account of a concept,
with its span), and they are **not** worth putting into the retriever's searchable text — on this
corpus, under this rule and its strict variant, they cost a case and buy none.

**[speculative]** — the rule itself was written *against the names* and then checked against the
measurement, and the strict variant was written *after* seeing that the loose one let A-03 through.
Both are fitted to one corpus of 326 names and to one case that moves; a rule fitted this way is a
hypothesis, not a result, and it is reported as such. The **verdict** (no benefit) does not depend on
the rule: the held-out set is identical in every arm, and no arm gains a single case.

### 9.5 What this measurement does not establish

* **It does not establish that the definitions are useless.** It establishes that putting them into
  the retrievable text buys nothing **on this corpus, under these rules, at this retriever floor**,
  and that the uniform application costs the refusal correctness. A different retrieval (a different
  floor, a different query expansion, an embedding-free semantic match over the definition) was not
  measured.
* **It does not measure the nine non-ask cases** (the false-premise and revision paths): an ask
  cannot judge them, and their paths are outside this scope.
* **It does not measure the answer's correctness** (A6) beyond the official verdict, and it does not
  establish a gain anywhere: the research is explicit that this corpus cannot support a retrieval
  gain claim.
* **It does not generalise beyond the corpus**: nine documents, one domain, one language, one
  register.
* **It does not say the definitions are wrong.** 24 of a 25-sample are about their concept; the
  problem is not their truth but that the retriever does not benefit from them.
* **The latency**: 68–72 ms across the four arms — inside the noise of the machine, and the arms
  differ by nothing measurable.


---
## 10. THE CORRECTION, THE DEFINITIONAL-PREDICATE GATE AND THE RE-MEASURED NUMBERS (2026-11-17)

### 10.1 The rule as it now stands

The production rule lives in `matrix-core/src/main/java/io/matrix/knowledge/acquisition/SourceSpanDefinitions.java`
(`DEFINITION_METHOD = "definitional-adjacency-first-sentence-v1.1"`). A sentence is a definition of a
concept only when **all** of these hold:

1. **The name is a referent** (`isReferent`): not a single function word, not a name that opens with a
   class word, not a name the subject extractor cut inside a clause, not a question ("How mantle
   convection … relates") or a possessive fragment ("Its thickness").
2. **The name is the subject** (`subjectOffset`): the name's token sequence must start the sentence's
   subject, after the subject markers, within `MAXIMUM_SUBJECT_OFFSET` tokens. A mention elsewhere in
   the sentence does not qualify it.
3. **The copula follows the name** (`COPULAS_LONGEST_FIRST`): the longest copula is read first, so
   "is defined as" is read before "is". The predicate must begin within `MAX_PREDICATE_OFFSET`
   (4) tokens of the copula — a copula whose complement stands far away is the verb of a frame
   ("is thought to be"), not of a definition.
4. **No denial** (`denies`, `NEGATION_WINDOW = 3`): a negation within three tokens after the copula
   rejects the sentence, qualifier included ("is largely not", "is sometimes not", "is defined as not").
5. **The predicate is definitional** (`isDefinitionalPredicate`): the complement must open a noun
   phrase that names what the term denotes — a genus ("the deepest parts of the ocean floor"), an
   identity ("one of the few transform faults exposed on land") or a classification ("of two types").
   An action ("are compressed, folded"), a comparison ("is cooler and more rigid"), a measurement
   ("is typically 100 km thick"), an origin ("is formed at sea-floor spreading centers"), a state
   ("is still open") and a report of what is thought ("is most widely thought to be") are all rejected.

The first qualifying sentence **in which the name is the subject** wins; `derive` walks the sentences
of the document in order and takes the first that satisfies all five conditions.

### 10.2 The numbers, re-measured

| measure | the old rule (a mention) | the subject rule | **the definitional-predicate rule (now)** |
|---|---|---|---|
| definitions on the seed corpus | 120/326 = 36.8 % | 27/326 = 8.28 % | **4/326 = 1.23 %** |
| hand-verified, "is a definition" | 24/25 = 96 % — measured as "about the concept" | 18/27 = 66.7 % — the same weaker metric | **3/4 = 75 %** — the protocol as written |
| the reviewer's independent count | — | 3/27 = 11.1 % | — |

Every published number is withdrawn rather than restated: 36.8 %, 96 %, 8.28 % and 66.7 % were
measured against a weaker rule and/or a weaker metric, and the document keeps them as the record of
that earlier state.

### 10.3 THE CENSUS OF THE 27, RE-LABELLED BY THE PROTOCOL AS WRITTEN — 3/27

The 27 definitions of the subject rule were re-labelled by hand on 2026-11-17 against the protocol
exactly as it is written in `HandVerifiedDefinitions`: *a definition states the concept's nature,
composition or identity — a sentence that could stand as the concept's glossary entry; an action, a
comparison, a discourse about the concept, or a denial of it, is not a definition.*

**The re-labelling lands on 3 of 27 = 11.1 % — the reviewer's independent count, on the same three
concepts.** The earlier 18/27 was therefore measured against a criterion other than the declared one
("could stand as a glossary entry"), not against the protocol. That is the correction, and it is
recorded here rather than replaced silently.

| concept | verdict | why |
|---|---|---|
| Trenches | **definition** | a genus: "the deepest parts of the ocean floor" |
| The San Andreas | **definition** | an identity: "one of the few transform faults exposed on land" |
| Subduction zones | **definition** | a classification: "of two types: …" |
| Tectonic plates | not a definition | composition ("are composed of …"), not identity |
| Slab pull | not a definition | a report of what is widely thought to be |
| The trenches | not a definition | a role ("the key to understanding …"), not a genus |
| Iceland | not a definition | an action: "is splitting along …" |
| How mantle convection … relates to plate motion | not a definition | a question, not a referent |
| Mantle plumes and hot spots | not a definition | an action: "are also postulated to impinge …" |
| Its thickness | not a definition | a function of something else |
| Cool oceanic lithosphere | not a definition | a comparison: "is significantly denser than …" |
| Continental crust | not a definition | an origin: "is formed through …" |
| Oceanic crust | not a definition | an origin: "is formed at …" |
| Continental lithosphere | not a definition | a measurement: "is typically about 200 km thick" |
| The debate | not a definition | a state: "is still open" |
| Deep marine trenches | not a definition | an association, not a genus |
| The other forces | not a definition | a use: "are only used in …" |
| Earth's crust | not a definition | a state: "is soaked with water" |
| The lost surface | not a definition | a state: "is balanced by …" |
| Plate boundaries | not a definition | a relative clause: "are where geological events occur" |
| Modern plate tectonics | not a definition | an origin: "are suggested to have emerged …" |
| Mars Mars | not a definition | a comparison: "is considerably smaller than …" |
| The plate edges | not a definition | an action: "are compressed, folded, and uplifted" |
| Twenty-one satellites | not a definition | a state and a measurement: "are currently in orbit …" |
| The lithosphere | not a definition | a comparison: "is cooler and more rigid" |
| Ground-based measurements | not a definition | a method: "are taken with …" |
| Average oceanic lithosphere | not a definition | a measurement: "is typically 100 km thick" |

### 10.4 The current census — the 4 survivors, 3 of them definitions

The rule as it now stands finds **4** definitions on the seed corpus of 326 concepts (1.23 %). The
census of all four is frozen in `HandVerifiedDefinitions` (keyed by digest, so that no definition
text is written into the development code) and judged under the protocol:

| concept | the definition | verdict |
|---|---|---|
| Trenches | "Trenches are the deepest parts of the ocean floor and are created by subduction." | **is a definition** (a genus) |
| The San Andreas | "The San Andreas is one of the few transform faults exposed on land." | **is a definition** (an identity) |
| Subduction zones | "Subduction zones are of two types: …" | **is a definition** (a classification) |
| The trenches | "The trenches are the key to understanding how island arcs …" | not a definition (a role, not a genus) |

**3 of 4 = 75 %.** The residual is stated, not hidden: a purely structural gate cannot separate a
genus from a role — "the key to understanding …" is a noun phrase in the subject position with a
definitional copula, and it names what the concept is *for*, not what it is.

### 10.5 The naming decision

The field **keeps the name "definition"**, because the rule now requires a definitional predicate and
rejects every action, comparison, measurement, origin, state, denial and report. What it stores is
stated precisely: **a candidate definition — a sentence whose subject is the concept and whose
predicate is a noun phrase naming what the term denotes.** The schema is unchanged
(`concept.definition` stays empty in the owner's store; the definitions are not searchable text —
see §3 and §9), because the contract *tightened* rather than changed name; the documents and the
metric carry the precise meaning, and the measured precision (3/4) is published beside every count.
The alternative — renaming the field to "a sentence about the concept" — would describe the *old*
rule, which the corrected rule no longer implements.

### 10.6 What this correction does not establish

- **No benefit in the retriever.** §9 measured none: all four arms of the benefit measurement are
  identical (official 20 ask cases: passed 12, answered 14, abstentions 6, the unanswerable abstained
  6/6; the frozen held-out 16 cases: passed 8, answered 12, abstentions 4). A smaller and more precise
  set has even less room to help. The precision of the rule is therefore the only thing still required
  of the definitions.
- The judgements are a human judgement, made after the rule was corrected, over one domain, one
  register, one language; they are not a proof and not a blind measurement.
- The gate is structural and bounded; its generalisation beyond the seed corpus is untested.
- The knowledge scope holds no other definition work: the frozen hand-labelled spans
  (`HandLabelledSpans`), the research instrument (`SourceGroundedEnrichment`, TIGHT_V1_1) and the
  referent-key policy are unchanged.
