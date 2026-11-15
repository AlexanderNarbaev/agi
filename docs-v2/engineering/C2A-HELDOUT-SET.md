# C2A — the independent held-out evaluation set (Workstream B6, CONCEPT-TO-ACTION)

**Record:** `C2A-HELDOUT-SET`
**Status:** `BUILT` — the dataset exists, the builder is green, the gold is source-verified.
**Repository:** `agi`, branch `feature/concept-to-action`.
**Date:** 2026-11-15.
**Artefact:** `docs-v2/engineering/C2A-HELDOUT-SET.json` (version 1.0).
**Builder:** `matrix-core/src/test/java/io/matrix/knowledge/HeldOutSetBuilderTest.java`.

---

## 0. What this is, and the rule it follows

This is a **dataset**, not a candidate. No retrieval improvement was designed, tuned or measured
here, and the production retrieval path, the knowledge model and every frozen zone are untouched.

The rule the workstream sets is a rule about ORDER: **the held-out set must be fixed before any
candidate exists**, or the comparison in stage 4 measures the candidate's own fitting. The set is
therefore built from material the development corpus does not contain (the *research pool*), its
gold is the **source's own text** rather than the system's output, and every claim in it is
traceable to a span in a licensed document.

What the dataset is checked against, and by what, is §5. What it does **not** establish is §8.

---

## 1. The corpora

### 1.1 The research pool — the material the set is built from

`matrix-core/src/main/resources/research-pool/plate-tectonics/` — 7 documents, every one of them
CC-BY-SA-4.0, with its own attribution, canonical URL and snapshot revision in
`corpus-manifest.json` (sha256 `fa7ba16e9baddea9d58109100c27d19a2404ee34391514a1ff4e2d785c0413b5`).

| File | Attribution | Claims the production importer derived | Source id |
|---|---|---|---|
| `wikipedia-continental-drift.md` | 'Continental drift', Wikipedia (CC BY-SA 4.0), rev. 1376987312 | 90 | `src-8b44c3d8` |
| `wikipedia-seafloor-spreading.md` | 'Seafloor spreading', Wikipedia (CC BY-SA 4.0), rev. 1372521821 | 92 | `src-16b81207` |
| `wikipedia-subduction.md` | 'Subduction', Wikipedia (CC BY-SA 4.0), rev. 1381148652 | 152 | `src-279a971d` |
| `wikipedia-mid-ocean-ridge.md` | 'Mid-ocean ridge', Wikipedia (CC BY-SA 4.0), rev. 1383981232 | 82 | `src-5c281231` |
| `wikipedia-mantle-convection.md` | 'Mantle convection', Wikipedia (CC BY-SA 4.0), rev. 1382079218 | 45 | `src-b9eb0cc6` |
| `wikipedia-slab-pull.md` | 'Slab pull', Wikipedia (CC BY-SA 4.0), rev. 1379867088 | 14 | `src-9b8764d` |
| `wikipedia-paleomagnetism.md` | 'Paleomagnetism', Wikipedia (CC BY-SA 4.0), rev. 1381061770 | 65 | `src-77f1debd` |

The claim counts are the **production importer's own numbers** (`ImportResult.claimsAdded()`), and
they reproduce the pool manifest's figures exactly: 90 / 92 / 152 / 82 / 45 / 14 / 65 = **540**.

These documents are **not** in the seed corpus of the development corpus
(`matrix-core/src/main/resources/corpus/plate-tectonics/`, the USGS *This Dynamic Earth* and the
Wikipedia *Plate tectonics* article). **They are, however, largely the same material the
development evaluation acquired**: scope P1 imports continental-drift, mid-ocean-ridge and
subduction, and R1/R2 add slab-pull and paleomagnetism (the scopes are declared in
`matrix-core/src/main/resources/epistemic-eval/cases.json` and measured in
`docs-v2/engineering/E2-FLOOR-0p25.json`).

**So the independence this set has is QUESTION independence, not corpus independence.** Measured:
**26 of the 38 cases (50 of the 61 gold references) draw their gold from a document the
development evaluation also imports**; only mantle-convection (5 cases) and seafloor-spreading
(4 cases) are free of it. A candidate fitted to the 29 development cases has not seen these
QUESTIONS, and has not seen these GOLD SENTENCES as the answer to these questions — but it has
seen the documents. The set can therefore REJECT a candidate (it did) and cannot establish a
positive effect: the corpus it shares with the development set is the corpus the candidates were
developed against.

### 1.2 The development set — the set this one must not resemble

`matrix-core/src/main/resources/epistemic-eval/cases.json`, dataset `w50-plate-tectonics`
v1.1, 29 cases, sha256 `214e0b9a7e59a227e2cf3c688623b05d8e039a53de9c5cffb8b2d82b4b575a1f`.

Twenty of its cases carry a `question` and five carry an `assertion`; **all 25 texts** are read by
the builder and used as the side of the independence check (§5).

---

## 2. The method — the gold is the source's, not the system's

1. **The workspace is assembled through the production acquisition path.**
   `AcquisitionCycle.RESEARCH_POOL` → `ClasspathCorpus.extract(...)` →
   `KnowledgeWorkbench.importDocument(workspace, path, file, language)`. The builder re-uses the
   production importer, so every gold claim carries the **real claim id** the production system
   gave it (`clm-…`) and the **real source id** (`src-…`). No identifier in the dataset is written
   by hand.

2. **A gold claim is a production claim whose proposition is VERBATIM in its pool document.**
   The builder reads the raw bytes of the pool document from the classpath and asserts that the
   proposition occurs in it. The span is then recorded twice per gold claim: as the proposition
   itself (`sourceSpan`) and as the production locator with the character offsets
   (`claimContext`, e.g. `wikipedia-subduction.md: "Subduction" (line 1) [characters 14-29694]`).
   A case whose proposition is not verbatim in its source **is not written out**.

3. **The questions are the author's, and they are independent of the development questions.**
   Every question was checked against all 25 development texts: normalised to lower-case word and
   number tokens, and no held-out question shares a contiguous token sequence of length **≥ 4** with
   any development question or assertion.

4. **The negative cases are checked mechanically.** Each declares its content terms, and the
   builder asserts that **none of them occurs in any pool document**.

5. **The contradictory cases name both sides**, with both claim ids and both spans. The ambiguous
   cases name each reading, with the spans that carry it.

---

## 3. The five kinds and the disposition each expects

| Kind | n | Expected disposition | What the case is | What the gold is |
|---|---|---|---|---|
| `POSITIVE` | 19 | `ANSWERED` | answerable from the pool | the production claims whose propositions answer it (1–3 per case) |
| `NEGATIVE` | 6 | `ABSTAINED` | a fact the corpus does not contain at all | none — the absence is the result |
| `UNSUPPORTED` | 6 | `UNSUPPORTED` | a question whose premise the corpus contradicts or cannot support | the claim(s) that carry the corpus's actual position, plus the disputed premise in words |
| `AMBIGUOUS` | 4 | `AMBIGUOUS` | a term the corpus uses for more than one thing | every reading, each with its own span(s) |
| `CONTRADICTORY` | 3 | `CONTRADICTORY` | two corpus claims that cannot both hold | both conflicting claims, with both ids and both spans |

`level` in every case is `INTRODUCTORY`, the abstraction level at which the question is asked — the
same level the development set asks its questions at.

---

## 4. The counts

| Measure | Value |
|---|---|
| cases | **38** |
| distinct gold claims | **59** (61 references: two claims serve two cases each) |
| distinct source documents | **7** |
| distinct sources (production source ids) | **7** |
| cases per kind | POSITIVE 19 · NEGATIVE 6 · UNSUPPORTED 6 · AMBIGUOUS 4 · CONTRADICTORY 3 |
| cases per document | subduction 10 · mid-ocean-ridge 7 · continental-drift 5 · mantle-convection 5 · seafloor-spreading 4 · slab-pull 3 · paleomagnetism 3 |
| gold-claim references per document | subduction 20 · mid-ocean-ridge 12 · continental-drift 7 · mantle-convection 7 · seafloor-spreading 7 · paleomagnetism 4 · slab-pull 4 |

Every one of the seven pool documents carries at least one case; every one of the five kinds is
present; every case names at least one source span.

---

## 5. What was verified, and by what

All of this is asserted **inside the builder**, so a green test is the evidence:

| Assertion | Where |
|---|---|
| each of the 7 pool documents was imported through the production path and yielded claims | `result.claimsAdded()` > 0 per document |
| every gold proposition occurs **verbatim** in its pool document | `rawText.get(file).contains(proposition)` |
| every gold proposition is a **production claim** of that document's source | claim lookup + `introducedBySourceId == sourceId(workspace, sha256)` |
| a negative case's content terms occur in **no** pool document | per term, per document |
| a held-out question shares **no** 4-token sequence with any development question or assertion | token n-gram intersection over all 25 texts |
| the five kinds are present, the set is 24–40 cases, every pool document has a case | shape assertions |
| no case repeats a question or an id; every non-negative case names a span | shape assertions |

### 5.1 The commands and their exit statuses

```bash
export JAVA_HOME=$HOME/jdk25
export MATRIX_C2A_DIR="$PWD/docs-v2/engineering"      # where the artefact is written
flock /tmp/gradle-matrix.lock bash -c 'rm -rf matrix-core/build/test-results && \
  ./gradlew :matrix-core:test --tests "io.matrix.knowledge.HeldOutSetBuilderTest" --offline --console=plain'
```

* exit status **0** — `BUILD SUCCESSFUL`; the test is green.
* The artefact is written to `docs-v2/engineering/C2A-HELDOUT-SET.json` and read back and compared
  byte-for-byte with the serialised dataset before the test ends.
* **The artefact is byte-reproducible.** Three consecutive builds produced the same digest:
  `sha256 = bbb3d2ad630ec610df3dbaae3ec10978d6cbbe740e13604dfd827b56b6872d77`.
  (The first drafts of the builder wrote two of the manifest maps with `Map.of(...)`, whose
  iteration order is not stable between JVM runs, so the digest changed on every build. Those
  maps are `LinkedHashMap`s now. A consumer that cites the dataset's digest must take it from a
  fresh build, not from an earlier one.)
* The JUnit XML: `matrix-core/build/test-results/test/TEST-io.matrix.knowledge.HeldOutSetBuilderTest.xml`
  — `tests="1" skipped="0" failures="0" errors="0"`, one testcase, about 6 s.
* The run prints the counts to the test's standard output, which the XML preserves:
  `[B6] … (38 cases)`, `[B6] kinds {POSITIVE=19, NEGATIVE=6, UNSUPPORTED=6, AMBIGUOUS=4, CONTRADICTORY=3}`.

**Operational note (a real failure mode, reproduced four times).** `rm -rf
matrix-core/build/test-results` outside the build lock races with a *concurrent* Gradle test in the
same module — another worker was running `:matrix-core:test --tests io.matrix.knowledge.concept.*`
at the same time — and the build then fails with
`java.nio.file.NoSuchFileException: …/test-results/test/binary/in-progress-results-generic.bin`.
This is Gradle's result aggregation losing the file, **not** a failure of the test. The working
procedure is to do the deletion **inside** the `flock` (as above) and to re-run if the error
appears; the test body itself completed in every one of those runs (the artefact was written each
time).

---

## 6. The cases

| id | kind | question | gold claims | source document(s) |
|---|---|---|---|---|
| H-P01 | POSITIVE | Which mechanism did Arthur Holmes advance for the movement of the continents, and what is said to power it? | 1 | continental-drift |
| H-P02 | POSITIVE | In the research pool, what did Wegener call the single landmass from which the continents later broke apart? | 1 | continental-drift |
| H-P03 | POSITIVE | By how much did a GPS receiver standing in Maui, Hawaii, shift over a fourteen-year period? | 1 | continental-drift |
| H-P04 | POSITIVE | How do the opening rates of fast, intermediate and slow spreading ridges compare in the research pool? | 3 | seafloor-spreading |
| H-P05 | POSITIVE | Where does the research pool say new seafloor is continually formed, and what happens there? | 2 | seafloor-spreading |
| H-P06 | POSITIVE | Which rock type crystallises at a ridge axis, and what lies beneath it in the lower oceanic crust? | 2 | mid-ocean-ridge |
| H-P07 | POSITIVE | How does a faster spreading rate change the magnesium and calcium balance of seawater? | 2 | mid-ocean-ridge |
| H-P08 | POSITIVE | Which survey team concluded that a rift valley runs down the middle of the Atlantic Ocean? | 2 | mid-ocean-ridge |
| H-P09 | POSITIVE | What is the Wadati-Benioff zone, and how deep does it reach? | 2 | subduction |
| H-P10 | POSITIVE | How does the water carried by a subducting slab generate magma beneath a volcanic arc? | 3 | subduction |
| H-P11 | POSITIVE | Which metamorphic path do subduction zones follow, and which facies zones does it cross? | 2 | subduction |
| H-P12 | POSITIVE | How long does a single shallow mantle convection cycle take, and how does that compare with a deeper one? | 1 | mantle-convection |
| H-P13 | POSITIVE | What do the elevated helium-3 to helium-4 ratios of ocean island basalts tell geochemists about their source? | 2 | mantle-convection |
| H-P14 | POSITIVE | At which temperature does magnetite lose its magnetic record, and how does that compare with the crystallisation of basalt? | 1 | paleomagnetism |
| H-P15 | POSITIVE | How are the two varieties of detrital remanent magnetization told apart? | 1 | paleomagnetism |
| H-P16 | POSITIVE | Which physical process does the research pool name as the origin of slab pull, and what happens to the rest of the plate? | 1 | slab-pull |
| H-P17 | POSITIVE | Which large plates keep moving although they are subducted nowhere, and what force is credited with their motion? | 2 | slab-pull |
| H-P18 | POSITIVE | What did the 1975 inverse-theory study of Forsyth and Uyeda find about the relative strength of the driving forces? | 1 | slab-pull |
| H-P19 | POSITIVE | Which minerals carry water into the deep mantle, and what happens to them during subduction? | 3 | subduction |
| H-N01 | NEGATIVE | Which company installed the first transatlantic telegraph cable in 1866? | 0 | — |
| H-N02 | NEGATIVE | Which studio recorded the first orchestral performance of the Sibelius violin concerto? | 0 | — |
| H-N03 | NEGATIVE | How many wheat sheaves did Rosario export in its busiest year? | 0 | — |
| H-N04 | NEGATIVE | Which pharmaceutical company announced the trial of the substance S-1423 in 1997? | 0 | — |
| H-N05 | NEGATIVE | Which football club recruited the retired midfielder from Zaragoza in 1994? | 0 | — |
| H-N06 | NEGATIVE | Which cathedral in Oviedo houses the medieval tapestry commissioned in 1388? | 0 | — |
| H-U01 | UNSUPPORTED | Did the continents plow through the ocean floor, as Wegener originally proposed? | 1 | seafloor-spreading |
| H-U02 | UNSUPPORTED | Is subduction known to take place on planets other than Earth? | 1 | subduction |
| H-U03 | UNSUPPORTED | Do the continents gain area as they drift, in the manner the Expanding Earth theory proposed? | 2 | continental-drift |
| H-U04 | UNSUPPORTED | Are the lavas of intraplate volcanoes chemically identical to mid-ocean ridge basalts? | 2 | mantle-convection |
| H-U05 | UNSUPPORTED | Do the continents and the oceans exchange places over geological time, as the fixists of the nineteenth century maintained? | 2 | continental-drift |
| H-U06 | UNSUPPORTED | Do the reversals of the geomagnetic field occur at regular intervals through the history of the Earth? | 2 | paleomagnetism |
| H-A01 | AMBIGUOUS | In the research pool, which plate does the word slab name? | 4 | subduction |
| H-A02 | AMBIGUOUS | In the research pool, which kinds of arc are distinguished, and what tells them apart? | 2 | subduction |
| H-A03 | AMBIGUOUS | In the research pool, what does the word ridge stand for? | 3 | mid-ocean-ridge |
| H-A04 | AMBIGUOUS | In the research pool, what does the word plate denote? | 3 | subduction, seafloor-spreading, mid-ocean-ridge |
| H-C01 | CONTRADICTORY | Does mantle convection cause the movement of the plates? | 2 | mantle-convection, mid-ocean-ridge |
| H-C02 | CONTRADICTORY | How long is the global system of mid-ocean ridges, according to the research pool? | 2 | subduction, mid-ocean-ridge |
| H-C03 | CONTRADICTORY | Is subduction the driving force behind plate tectonics, or is it mantle convection? | 2 | subduction, mantle-convection |

The JSON holds, per case: `id`, `kind`, `level`, `question`, `expectedDisposition`, `goldClaims`
(claim id, proposition, source span, production claim context, source id, source title, source
licence and licence URL, extraction method, claim status), `sourceFiles`, and — where the kind
calls for it — `disputedPremise`, `contentTerms` or `readings`.

---

## 7. What the production extractor actually yields (and what the builder therefore refuses)

The corpus is read by the production pipeline, and the pipeline's behaviour is part of what this
dataset rests on. Four observations, all of them consequences of
`ClaimExtractor`/`TextDocumentExtractor` as they are:

1. **A sentence that opens a section is not verbatim.** The extractor glues the heading onto the
   first sentence of a section (`"GPS evidence Today continental drift can be directly measured…"`),
   so that string does not occur in the document. **483 of the 540 derived claims are verbatim**;
   the builder refuses the other 57 and they cannot be gold.
2. **A list under one heading merges into one sentence.** The three ocean-ocean subduction models
   are one block, so the first of them arrives as
   `"The models are as follows: retreating subduction: caused by weak coupling…"`, which is not
   verbatim; the other two are. The set therefore does not use the retreating model as a gold
   claim.
3. **Some sentences are never proposed.** `"Being a primordial nuclide, helium-3 is not naturally
   produced on Earth."` is not a claim (`being` is in the extractor's verb list, so no subject
   follows it), and neither is `"The overridden plate (the slab) sinks at an angle…"` (`sinks` is
   not in the verb list). Both are in the documents; neither is in the store.
4. **The validator promotes.** All 61 gold references are claims with status `SUPPORTED` after the
   import's own validation stage.

---

## 8. What this dataset does NOT establish

* **It is not a measure of generalisation.** It covers seven Wikipedia articles about plate
  tectonics, all of them from the same publisher and the same licence, and it says nothing about
  any other domain, any other corpus, or any other language.
* **It is not a measure of any candidate.** No retrieval improvement was designed, tuned or
  evaluated here. The dataset measures itself: that its gold is the source's text, that its kinds
  are present, and that its questions are independent of the development questions. The comparison
  in stage 4 is still to be run, and its outcome is unknown.
* **It was written by one author.** The propositions are a careful reader's choices, the questions
  are one person's wording, and the expected dispositions are one person's reading of what the
  corpus says. There is no second annotator and no inter-annotator agreement figure.
* **The mechanical rule for `NEGATIVE` makes those cases easier than a real negative.** Because
  every content term of a negative case must occur in **no** pool document, the negative questions
  are necessarily about topics the corpus never mentions. A harder negative — a question about a
  topic the corpus *does* discuss, whose specific figure or name it does not contain — cannot be
  expressed under this rule and is **not** represented here.
* **The `UNSUPPORTED` and `CONTRADICTORY` kinds depend on the author's reading.** The corpus does
  not label its own disagreements. In particular `H-C02` is a discrepancy in the *numbers* the two
  articles give for the global ridge system (60,000 km against 80,000 km; the same sentence also
  gives 65,000 km for the continuous mountain range), which may be a difference of scope rather
  than a contradiction — the corpus does not reconcile it, and the expected disposition is to
  surface the discrepancy rather than to pick one.
* **The `AMBIGUOUS` kind is lexical.** The corpus does not declare its own ambiguities; the four
  ambiguous cases are terms the corpus demonstrably uses for more than one thing (`slab`, `arc`,
  `ridge`, `plate`), which is one kind of ambiguity and not all of them.
* **It does not measure the retrieval path's other behaviour.** Nothing here covers ranking
  quality beyond the cited-claim check, latency, the abstraction levels, the multilingual bridge
  or the promotion rules.
* **It is not a licence to relax the development set.** The 29 development cases remain the
  development set; this set adds independent evidence and replaces nothing.

---

## 9. Using the set in stage 4 (the runner's contract, not this document's)

The dataset is a set of **expectations**, not a script. For each case a runner asks the workspace
the question at `level` and compares the answer with `expectedDisposition`:

* `POSITIVE` → the answer must cite the gold claims (`goldClaims[].proposition` present verbatim in
  the cited claims) and must not be an abstention.
* `NEGATIVE` → the answer must abstain, with no claim cited.
* `UNSUPPORTED` → the answer must not accept the premise: it must either decline or name the
  contradicting claim, and `disputedPremise` must be acknowledged.
* `AMBIGUOUS` → the answer must surface the readings; it may not silently pick one.
* `CONTRADICTORY` → the answer must report the conflict and name both claims; it may not pick one
  silently.

The workspace to build is the one the builder builds: the **pool only**, imported through the
production path into a workspace of the runner's choosing. Whether the seed corpus is also loaded
is a decision for stage 4 and is **not** settled by this document; every gold claim here is
answerable from the pool alone.
