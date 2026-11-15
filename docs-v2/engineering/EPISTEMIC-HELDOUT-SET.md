# EPISTEMIC-HELDOUT-SET — the new held-out evaluation set for the epistemic semantics (P0.3)

**Record:** `EPISTEMIC-HELDOUT-SET`
**Status:** `FROZEN` — the dataset exists, the builder is green, the gold is source-verified, the freeze precedes the measurement.
**Repository:** `agi`, branch `develop`, base commit `0a4336d1daad7c8ffe3ce760cd1938975238aef5`.
**Date:** 2026-11-15.
**Artefact:** `docs-v2/engineering/EPISTEMIC-HELDOUT-SET.json` (version 1.0, 16 cases).
**Builder:** `matrix-core/src/test/java/io/matrix/knowledge/epistemic/EpistemicHeldOutSetBuilderTest.java`.
**Reader / baseline:** `matrix-core/src/test/java/io/matrix/knowledge/epistemic/EpistemicBaselineTest.java`.

---

## 0. What this is, and the rule it follows

This is a **dataset**, not a candidate. No repair of the premise semantics was designed,
tuned or measured here, and no production file was touched.

The rule the directive sets is a rule about ORDER: **the set must be fixed before any
candidate exists**, or the comparison measures the candidate's own fitting. So:

1. the dataset, its manifest and its digest were written **first**, by a builder that
   asserts its own ground truth;
2. the freeze was taken over the gold-bearing content of the dataset, and that digest is
   recorded **inside the dataset** and re-derived by any reader;
3. only then was the current production path measured on it.

The two sets that already existed — the official 29-case evaluation
(`matrix-core/src/main/resources/epistemic-eval/cases.json`, dataset `w50-plate-tectonics`
v1.1, sha256 `214e0b9a7e59a227e2cf3c688623b05d8e039a53de9c5cffb8b2d82b4b575a1f`) and the
C2A held-out set (`docs-v2/engineering/C2A-HELDOUT-SET.json`, sha256
`bbb3d2ad630ec610df3dbaae3ec10978d6cbbe740e13604dfd827b56b6872d77`) — are **regression sets**
from here on. Neither was modified. This set is checked against both: no gold proposition of
this set is gold of either, and no question of this set shares a normalised token sequence of
length ≥ 4 with any of their 69 questions, assertions or disputed premises.

---

## 1. The corpora — the material the set is built from

`matrix-core/src/main/resources/corpus/plate-tectonics/` — the licensed seed corpus, two
documents, both with their licence in `corpus-manifest.json`:

| File | Attribution | Licence | Claims the production importer derived | Source id |
|---|---|---|---|---|
| `usgs-this-dynamic-earth.md` | U.S. Geological Survey, *This Dynamic Earth* | `US-PUBLIC-DOMAIN` | 77 | `src-…` (written by the production importer) |
| `wikipedia-plate-tectonics.md` | *Plate tectonics*, Wikipedia, rev. 1381934246 | `CC-BY-SA-4.0` | 271 | `src-…` (written by the production importer) |

Every document's licence, canonical URL, snapshot revision, access date and content digest
are recorded in the dataset's manifest, and the manifest digest itself is pinned there.

**The research pool was NOT used.** The seven versioned Wikipedia snapshots in
`matrix-core/src/main/resources/research-pool/plate-tectonics/` are the material the C2A set
was built from; using them here would have made the two sets share their gold. This set's
gold is therefore **document-disjoint** from the C2A gold, and the builder proves it.

---

## 2. The method — the gold is the source's text

1. **The workspace is assembled through the production acquisition path.**
   `AcquisitionCycle.SEED_CORPUS` → `ClasspathCorpus.extract(...)` →
   `KnowledgeWorkbench.importDocument(workspace, path, file, language)`. The builder re-uses
   the production importer, so every gold claim carries the **real claim id** the production
   system gave it (`clm-…`) and the **real source id** (`src-…`). **No identifier in the
   dataset is written by hand.**
2. **A gold claim is a production claim whose proposition is verbatim in its document.**
   The corpus files are hard-wrapped Markdown, so the check compares the texts with their
   line wrapping collapsed: a single word or a single mark still has to match exactly. What
   the check forgives is the line break and nothing else.
3. **The questions are the author's**, and they are independent of the two existing sets
   (the ≥ 4-token rule above).

---

## 3. The classes and the cases

Nine classes, the ones the directive names. `expectedDisposition` is the dataset's own
vocabulary; the production path's vocabulary is mapped onto it in §6.

| Class | n | Expected disposition | What the case is | What the gold is |
|---|---|---|---|---|
| `SUPPORTED_PREMISE` | 2 | `ANSWERED` | the corpus states the presupposition | the production claims that state it |
| `REFUTED_PREMISE` | 2 | `REFUTED` | the corpus denies the presupposition | the production claim that denies it |
| `UNSUPPORTED_PREMISE` | 2 | `UNSUPPORTED` | no evidence either way | none — the absence is the result, and the content terms are the evidence of it |
| `IRRELEVANT_REFUTATION` | 1 | `NOT_REFUTED` | the corpus holds a denial of a *different* subject that looks like a refutation | the claim the trap must not be confused with, plus the trap claim |
| `CONFLICTING_EVIDENCE` | 1 | `CONFLICT_SURFACED` | two corpus claims that cannot both hold | both claims, with both ids and both spans |
| `AMBIGUOUS_READINGS` | 2 | `AMBIGUITY_SURFACED` | several defensible readings | every reading, each with its own span(s) |
| `LEXICAL_NEGATION` | 2 | follows the evidence | the wording negates or inverts the relation | the claim that settles it, or none |
| `PARAPHRASE` | 2 | `ANSWERED` | the same presupposition, phrased differently | the same claims as its twin |
| `NO_PREMISE` | 2 | `ANSWERED` | a plain factual question | the claim that answers it |

### The cases

| id | class | expected | question (abridged) | gold | source |
|---|---|---|---|---|---|
| E-S01 | SUPPORTED_PREMISE | ANSWERED | *Given that new crust is generated where the plates pull away from each other, what happens where Mid-Atlantic Ridge runs?* | 2 | USGS |
| E-S02 | SUPPORTED_PREMISE | ANSWERED | *Given that a subduction zone is where a sinking plate goes, what becomes of the crust there?* | 2 | USGS |
| E-R01 | REFUTED_PREMISE | REFUTED | *Given that there was an apparent mechanism for continental drift when Wegener proposed it, how was his theory received?* | 2 | Wikipedia |
| E-R02 | REFUTED_PREMISE | REFUTED | *Given that a horizontal pushing force is what moves the plates apart at spreading ridges, what drives that motion?* | 1 | Wikipedia |
| E-U01 | UNSUPPORTED_PREMISE | UNSUPPORTED | *Given that foraminifera in palaeomagnetic core samples date the Triassic, how is the age of the layer established?* | — | — |
| E-U02 | UNSUPPORTED_PREMISE | UNSUPPORTED | *Given that an accretionary prism of eclogite marks a Moho discontinuity, what does the structure indicate?* | — | — |
| E-I01 | IRRELEVANT_REFUTATION | NOT_REFUTED | *Given that the horizontal grinding of the Pacific Plate past the North American Plate is caused by a pushing force, what causes that motion?* | 1 + 1 trap | USGS + Wikipedia |
| E-C01 | CONFLICTING_EVIDENCE | CONFLICT_SURFACED | *Given that slab pull along subduction zones is the major driving force of plate motion, what does the corpus say about the Pacific plate?* | 2 | Wikipedia |
| E-A01 | AMBIGUOUS_READINGS | AMBIGUITY_SURFACED | *Given that plate boundaries are narrow lines between two plates, how are they described?* | 2 readings | USGS |
| E-A02 | AMBIGUOUS_READINGS | AMBIGUITY_SURFACED | *Which forces does the corpus list as possible drivers of plate motion?* | 4 readings | Wikipedia |
| E-L01 | LEXICAL_NEGATION | REFUTED | *Given that no new crust is created where the plates pull away from each other, what happens there?* | 1 | USGS |
| E-L02 | LEXICAL_NEGATION | UNSUPPORTED | *Поскольку тектонические плиты никогда не расходились, как образовались срединно-океанические хребты?* | — | — |
| E-P01 | PARAPHRASE | ANSWERED | *Given that Mid-Atlantic Ridge is a divergent boundary, what takes place along it?* | 2 | USGS |
| E-P02 | PARAPHRASE | ANSWERED | *Assuming that the plates pull apart where Mid-Atlantic Ridge runs, what happens there?* | 2 | USGS |
| E-N01 | NO_PREMISE | ANSWERED | *What is the place where a sinking plate goes under another one called?* | 1 | USGS |
| E-N02 | NO_PREMISE | ANSWERED | *Which ridge spreads fastest and which spreads slowest?* | 1 | USGS |

**The counts:** 16 cases · 9 classes · 16 distinct gold propositions (17 gold references) ·
9 cases draw on the USGS document, 4 on the Wikipedia document, 3 on neither (the absence is
the result) · 7 named readings across the two ambiguous cases · 1 trap claim.

---

## 4. What was verified, and by what

Every line below is asserted **inside the builder**, so a green test is the evidence:

| Assertion | Where in the builder |
|---|---|
| each corpus document was imported through the production path and yielded claims | `result.claimsAdded()` > 0 per document |
| every gold proposition occurs **verbatim** in its document (line wrapping collapsed) | `flatten(document).contains(flatten(proposition))` |
| every gold proposition is a **production claim** of that document's source | claim lookup + `introducedBySourceId == sourceId(workspace, sha256)` |
| a refuted case is refuted **by the text**: at least one gold claim denies something | `carriesDenial(...)` — the marker list is the dataset's own, not the production rule's |
| an unsupported case names content terms that occur in **no** corpus document | per term, per document |
| an unsupported case names its content terms at all | `contentTerms` is not empty |
| a lexical-negation case's premise carries a negation, in any language | `carriesNegation(premise)` |
| an irrelevant-refutation case's trap denies something and is not the case's own gold | `carriesDenial(trap)` + inequality |
| the paraphrase pair really is a paraphrase: different words, same presupposition | the two premises differ and share ≥ 3 tokens |
| the gold of this set is not the gold of the 29-case set nor of the C2A set | set intersection over both files' gold fields |
| no question of this set shares a ≥ 4-token sequence with any of the 69 existing texts | token n-gram intersection |
| the nine classes are present, every document carries a case, no id or question repeats | shape assertions |
| a case names a source span unless its expected disposition is `UNSUPPORTED` | the absence of evidence is the result there |

The baseline reader adds four more, about the measurement and about leakage:

| Assertion | Where in the reader |
|---|---|
| the freeze digest re-derives from the file itself | `sha256(compact JSON of the cases array + "\n")` |
| the production code contains **no** reference to the set and **no** question of the set | walk of `matrix-core/src/main/java`, `matrix-core/src/test/java`, `matrix-brain-runtime/src`, `matrix-api-gateway/src`, `docs-v2/research` |
| the reading is repeatable: the same case read twice gives the same verdict | two passes, equal sets |
| the assessment of an assertion promotes nothing into the workspace | `assessment.promoted()` is false |

---

## 5. The freeze

| Measure | Value |
|---|---|
| freeze algorithm | SHA-256 |
| freeze scope | the UTF-8 bytes of the compact JSON document that is the `cases` array of the dataset, followed by a single line feed |
| **freeze sha256** | **`2f4347da61b67df5f039f4dc72c985a8187f5c211e91faf7cd4a7ae2e791acbe`** |
| frozen at | 2026-11-15 |
| frozen before | the measurement of any candidate implementation of the premise semantics, and before any algorithm was selected for the repair |
| sha256 of the whole artefact file | `133f3ed006dea7c17e55c7818902448eaa0625f06ce184cf655174b2fd859069` |

The digest is recorded **in the dataset** (`freeze.sha256`) and re-derived by the reader, so a
set that was edited after the freeze cannot be measured quietly. The artefact is
**byte-reproducible**: two consecutive builds produced the same file digest
(`133f3ed0…`) and the same freeze digest (`2f4347da…`).

---

## 6. The baseline — the CURRENT production path on this set

This is the **starting point, not a target**. It says where the production path stands; it is
not a candidate result and it is not a pass mark.

**What was measured.** Two production paths, because there are two:
`KnowledgeWorkbench.ask(workspace, question, INTRODUCTORY)` — the path a user question takes,
which answers or refuses — and `PremiseAssessment.assess(workbench, workspace, premise)` — the
path an assertion takes, which says what the workspace makes of each premise. The dataset's
dispositions are mapped onto the production vocabulary: `ANSWERED` ↦ `SUPPORTED`/`NO_PREMISE`,
`REFUTED` ↦ `REFUTED`, `UNSUPPORTED` ↦ `UNSUPPORTED`, `CONFLICT_SURFACED` ↦ `CONTRADICTORY`,
`AMBIGUITY_SURFACED` ↦ `AMBIGUOUS`, `NOT_REFUTED` ↦ anything but `REFUTED`.

**The state of the code that was measured** (the working tree is shared and in motion, so the
production path is pinned by digest, not by the commit):

```
matrix-core/src/main/java/io/matrix/knowledge/KnowledgeWorkbench.java   sha256 a740c3517293f1c4fb4df46b762d74a0f41d52471f83b8d69fd244f6718d2757
matrix-core/src/main/java/io/matrix/knowledge/PremiseChecker.java        sha256 a37e4f2f8aeb18712eb8f05940448d3c50eb9af81adcd111054a8a7f2a76f873
matrix-core/src/main/java/io/matrix/knowledge/PremiseAssessment.java     sha256 3c4d81a9e4f95dbf5206874c5c2d22aa36cb23ca089cc86b3a09e9575bb6772f
matrix-core/src/main/java/io/matrix/knowledge/PremiseSemantics.java      sha256 d136e7dff2b1b6a5b10f15dbfe686faed6154c6f06fb65aec4771cac3a082f48
```

### Per class

| Class | n | answered | refused | status not as expected | mean latency | max latency |
|---|---|---|---|---|---|---|
| `SUPPORTED_PREMISE` | 2 | 2 | 0 | 0 | 78 ms | 99 ms |
| `REFUTED_PREMISE` | 2 | 2 | 0 | 2 | 47 ms | 49 ms |
| `UNSUPPORTED_PREMISE` | 2 | 0 | 2 | 0 | 53 ms | 55 ms |
| `IRRELEVANT_REFUTATION` | 1 | 1 | 0 | 0 | 54 ms | 54 ms |
| `CONFLICTING_EVIDENCE` | 1 | 1 | 0 | 1 | 49 ms | 49 ms |
| `AMBIGUOUS_READINGS` | 2 | 2 | 0 | 2 | 40 ms | 45 ms |
| `LEXICAL_NEGATION` | 2 | 1 | 1 | 1 | 43 ms | 45 ms |
| `PARAPHRASE` | 2 | 2 | 0 | 1 | 39 ms | 40 ms |
| `NO_PREMISE` | 2 | 2 | 0 | 0 | 30 ms | 32 ms |

The counts are stable across the runs that were made; the latency is not — it is a wall-clock
measurement on a shared machine and moves by tens of milliseconds between runs. The numbers
above are the run whose JUnit XML is retained (§6.3).

### The metrics

| Metric | Value | What it means here |
|---|---|---|
| **false refusals** | **0** | no supported premise was refused |
| **false contradictions** | **0** | no unsupported or supported premise was labelled refuted |
| **unsupported answering** | **3** | E-R01, E-R02 and E-L01 — three refuted premises were **answered** as if they held |
| **ambiguity coverage** | **0 / 7 readings surfaced** | neither ambiguous case surfaced a single one of the readings it names |
| **conflict coverage** | **0 / 2 sides surfaced** | the conflicting pair was not surfaced as a conflict |
| **latency** | 30–78 ms mean per class, 99 ms worst | the path is fast; nothing here is a performance problem |

**Reading the numbers honestly.** The path does not over-refuse (0 false refusals) and it does
not invent contradictions (0 false contradictions) — but it **answers refuted premises** (3:
E-R01, E-R02, E-L01), it **resolves ambiguity silently** (0 of 7 readings surfaced), it **does
not surface the conflict** (0 of 2 sides), and it treats the two **paraphrases differently**
(E-P01 `SUPPORTED`, E-P02 `UNSUPPORTED`) — which is the string-matching failure the
`PARAPHRASE` class was built to expose. None of this is a regression against the previous
behaviour: it is the state of the tree at the measurement.

**The tree moved while the set was being written, and the baseline moved with it.** Two
measurements were made on two code states: `PremiseAssessment.java` and `PremiseSemantics.java`
were changed by the parallel P0.1 work between them (`sha256 c17db22b…` → `3c4d81a9…` and
`531fff75…` → `d136e7df…`). The **metrics** were identical across the two states (0 false
refusals, 0 false contradictions, 3 unsupported answering, 0/7 ambiguity coverage, 0/2 conflict
coverage); the **per-case status** was not — E-S02 and E-R01 changed. The lesson for whoever
compares a candidate against this set is the one the table itself teaches: **a baseline is
only comparable to a candidate measured on the same production digests.**

### The commands and their exit statuses

```bash
export JAVA_HOME=$HOME/jdk25
flock /tmp/gradle-matrix.lock bash -c 'rm -rf matrix-core/build/test-results && \
  ./gradlew :matrix-core:test --tests "io.matrix.knowledge.epistemic.EpistemicHeldOutSetBuilderTest" --offline --console=plain'
# exit 0 — BUILD SUCCESSFUL; the artefact is written and read back byte-for-byte
flock /tmp/gradle-matrix.lock bash -c 'rm -rf matrix-core/build/test-results && \
  ./gradlew :matrix-core:test --tests "io.matrix.knowledge.epistemic.EpistemicBaselineTest" --offline --console=plain'
# exit 0 — BUILD SUCCESSFUL
```

* JUnit XML: `matrix-core/build/test-results/test/TEST-io.matrix.knowledge.epistemic.EpistemicHeldOutSetBuilderTest.xml`
  — `tests="1" skipped="0" failures="0" errors="0"`.
* JUnit XML: `matrix-core/build/test-results/test/TEST-io.matrix.knowledge.epistemic.EpistemicBaselineTest.xml`
  — `tests="5" skipped="0" failures="0" errors="0"`.
* The measurement is printed to the test's standard output, which the XML preserves:
  `[P0.3-baseline] …` lines, one per case and one per class.

---

## 7. What this set does NOT establish

* **It is not external validation.** Every case is drawn from two documents of one corpus in
  one repository. The set is held out from the *evaluation* of the premise semantics, not
  from the *material* the system reads.
* **It is written by one author** and the questions are that author's phrasing. No second
  annotator checked the gold, and no disagreement between annotators was measured.
* **It covers one domain** (plate tectonics) and one abstraction level (`INTRODUCTORY`).
  Nothing here says anything about cross-domain generalisation.
* **Several negatives are mechanically easier than a human negative.** The `UNSUPPORTED` cases
  are made easy on purpose: their content terms occur **nowhere** in the corpus
  (`foraminifera`, `palaeomagnetic`, `triassic`, `accretionary`, `eclogite`, `moho`), so a
  retriever that finds nothing is right for the wrong reason. A negative whose subject the
  corpus mentions but does not settle is harder, and the set contains only one such case
  (E-I01).
* **The `LEXICAL_NEGATION` case E-L02 is weaker than it looks.** It is written in Russian
  while the corpus is English-only, so it measures the conservative behaviour (a negated
  premise in another language must not become a contradiction) rather than the reading of a
  negation inside the corpus's own language. E-L01 is the case that carries the class.
* **The `CONFLICTING_EVIDENCE` class has one case.** One pair of claims that cannot both
  hold is a demonstration, not a measurement of the class.
* **The baseline is a snapshot of a tree in motion.** The production path was being repaired
  while this set was written; the numbers in §6 belong to the four pinned file digests, not
  to the commit `0a4336d1`.
* **The set can reject, it cannot establish a positive effect.** A candidate that scores well
  on 16 cases has been measured on 16 cases of one domain; it has not been shown to work.

---

## 8. How to re-run it

```bash
export JAVA_HOME=$HOME/jdk25
flock /tmp/gradle-matrix.lock bash -c 'rm -rf matrix-core/build/test-results && \
  ./gradlew :matrix-core:test --tests "io.matrix.knowledge.epistemic.Epistemic*" --offline --console=plain'
```

The builder rewrites the artefact; the reader measures the production path against it. If the
artefact's digest changes, the set changed — and the freeze says when it was supposed to.
