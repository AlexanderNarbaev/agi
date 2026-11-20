# C2A — Concept coverage baseline (Workstream B, CONCEPT-TO-ACTION)

**Status:** `MEASURED` — a baseline of the knowledge representation as it exists today, before any data-model change.
**Repository:** `agi`, branch `develop`, HEAD `429eba27`.
**Date of measurement:** 2026-11-15.
**Author:** Workstream B research worker (read-only).

## 0. What this document is, and the rule it follows

This is a **measurement**, not a design. Every number below is traceable to a file in this
repository or to a command whose algorithm is reproduced in §8. Where something could not be
measured it says `NOT MEASURED` and why. Where a relation is *proposed* rather than *observed* it
is marked `UNVERIFIED-CANDIDATE`.

Nothing in this document changes a file, a schema or a production behaviour.

### 0.1 The measurement instrument, and why its numbers may be trusted

No Gradle build was run (the build lock is owned by another process). The corpus was therefore
measured with a **read-only re-implementation of the production acquisition pipeline**, written
against the Java sources of record:

| Stage | Java source of record |
|---|---|
| normalisation + segmentation | `matrix-core/src/main/java/io/matrix/knowledge/acquisition/TextDocumentExtractor.java:59-77` (normalize), `:85-146` (segment) |
| sentence + subject extraction | `matrix-core/src/main/java/io/matrix/knowledge/acquisition/ClaimExtractor.java:133-164` (sentences), `:262-309` (subjectOf), `:48-67` (verb list) |
| concept / claim / evidence identity | `matrix-core/src/main/java/io/matrix/knowledge/acquisition/DocumentImporter.java:227-246`, `:338-346`; `matrix-core/src/main/java/io/matrix/knowledge/Ids.java:117-124` |
| representations | `matrix-core/src/main/java/io/matrix/knowledge/RepresentationComposer.java:72-126`, `:151-181` |
| validation / promotion | `matrix-core/src/main/java/io/matrix/knowledge/acquisition/ClaimValidator.java:102-260` |

The instrument is validated against **five independent values that were produced by the
production system and are retained in this repository**:

| # | Retained value | Where | Reproduced by the instrument |
|---|---|---|---|
| 1 | `348` seed claims | `docs-v2/engineering/E2-RETRIEVAL-EXPERIMENT.md:188`; `docs-v2/engineering/B3-EVIDENCE-TO-ACTION-RESULT.json` (`"seedClaimsAdded": 348`) | 348 — exact |
| 2 | concept id `d542c957` = "Oceanic trenches" | `docs-v2/engineering/E2-RETRIEVAL-EXPERIMENT.md:139-140` | `digest("plate-tectonics-eval\|concept\|oceanic trenches")` — exact |
| 3 | claim id `clm-2a740579` = "Oceanic trenches are the deepest parts of the ocean floor." | `docs-v2/engineering/E2-RETRIEVAL-EXPERIMENT.md:139` | exact, from `wikipedia-subduction.md` |
| 4 | source ids `src-c3ad97fa`, `src-4d135a23` | `docs-v2/demos/evidence-to-action/output/evidence-report.md` ("## Sources") | exact for workspace `b3-demonstration` |
| 5 | the 41 cited claim ids of the B3 answer | `docs-v2/demos/evidence-to-action/output/evidence-report.md` ("## Supported claims") | 41 of 41 reproduced exactly |

Because (1)–(5) are exact, the counts derived from the same pipeline are treated as measured.

---

## 1. The existing knowledge model — exact fields

All paths below are relative to `matrix-core/src/main/java/io/matrix/knowledge/`.

### 1.1 `Concept` — `Concept.java:10-16`

```
String id, String workspaceId, String name, String definition, String domain, String createdAt
```

* A concept is **a stable identity for an idea** (`Concept.java:3-9`): id, workspace, name,
  definition, domain, creation instant.
* Construction invariants (`Concept.java:18-28`): id, workspaceId and name must be non-blank.
  **`definition` and `domain` are NOT required and may be `null`.**
* Identity in the acquisition path is a pure function of the workspace and the lower-cased name
  (`DocumentImporter.java:338-341`): `Ids.digest(workspaceId + "|concept|" + name.strip().toLowerCase())`.
  There is **no separate "concept kind", "alias" or "relation" field**.

### 1.2 `Claim` — `Claim.java:18-34`

```
String id, String workspaceId, String conceptId, String proposition, String scope, String context,
ClaimStatus status, double confidence, int version, String introducedBySourceId,
String extractionMethod, String validationMethod, ClaimWarrant warrant,
DerivationStatus derivationStatus, String createdAt, String updatedAt
```

* A claim is **one proposition about exactly one concept** (`Claim.java:3-16`). `conceptId` is
  mandatory and non-blank (`Claim.java:40-42`).
* `scope` carries the context in which the proposition holds; `context` carries the provenance
  location (`DocumentImporter.java:239`).
* The three epistemic fields are deliberately separate (`Claim.java:11-17`): `status` (layer),
  `warrant` (kind of thing that established it), `derivationStatus` (whether an inference was sound).
* `confidence` is an **authored** field, never computed (`ClaimValidator.java:34-41`).

### 1.3 `Evidence` — `Evidence.java:11-19`

```
String id, String workspaceId, String claimId, String sourceId, EvidenceRelation relation,
String excerpt, int quality, String recordedAt
```

* Evidence is a **verifiable relationship between a source and a claim** (`Evidence.java:3-10`).
* `EvidenceRelation` (`EvidenceRelation.java:4-34`) is an existing **typed vocabulary of 7 values**:
  `SUPPORTS`, `CONTRADICTS`, `CONTEXTUAL`, `EXEMPLIFIES`, `QUALIFIES`, `FAILS_TO_SUPPORT`,
  `INSUFFICIENT`.
* The edge direction is fixed: **source → claim**. There is no concept on either end.

### 1.4 `Source` — `Source.java:12-25`

```
String id, String workspaceId, SourceKind kind, String title, String locator,
int authority, int freshness, String excerpt, String addedAt, String contentHash,
String publishedAt, String language, String format
```

* `contentHash` is what makes "two independent sources" computable
  (`DocumentImporter.java:41-46`, `ClaimValidator.java:132-145`).
* In the acquisition path a document becomes `SourceKind.HUMAN_ASSERTION` with authority 50,
  freshness 50 (`DocumentImporter.java:199-202`).

### 1.5 `Representation` — `Representation.java:14-29`

```
String id, String workspaceId, String conceptId, AbstractionLevel level, String audience,
String statement, String scope, List<String> assumptions, List<String> includedFacts,
List<String> deliberatelyOmitted, List<String> validityConditions,
List<String> prerequisiteConceptIds, String refinesRepresentationId,
double confidence, String createdAt
```

* A representation is **an account built FROM claims**, at a level, for an audience
  (`Representation.java:5-13`). It is **not** a claim.
* `includedFacts` holds **proposition strings, not claim ids** (`RepresentationComposer.java:158-164`).
* **`prerequisiteConceptIds` (`Representation.java:26`) is the only field in any of the seven
  listed types that can hold another *concept id* in a directed way.** It is persisted
  (`KnowledgeStore.java:2246`) and validated (`KnowledgeStore.java:2358-2363`).
* `refinesRepresentationId` is a representation → representation edge (the refinement chain),
  not a concept → concept edge.
* The only four levels are `AbstractionLevel.INTRODUCTORY / INTERMEDIATE / ADVANCED /
  MECHANISTIC` (`RepresentationComposer.java:86-124`).

### 1.6 `Derivation` — `Derivation.java:25-34` (+ `Connective` `:44-58`)

```
String id, String workspaceId, String ruleId, String ruleName, Derivation.Connective connective,
String conclusionClaimId, List<String> premiseClaimIds, DerivationStatus status, String recordedAt
```

* A derivation is **a recorded inference**: the rule applied, the premises in order, the
  conclusion (`Derivation.java:3-24`).
* `ruleId` / `ruleName` are free-form strings; `Connective` is a **closed logical vocabulary of
  4 values**: `AND`, `OR`, `IMPLIES`, `NOT` (`Derivation.java:44-58`).
* Premises and conclusion are **claims, not concepts**.
* The store refuses a derivation whose premises or conclusion are not claims of the workspace
  (`KnowledgeStore.java:3465-3495`). A derivation therefore asserts *a checked inference*, and
  `status` carries an entailment verdict — not "the corpus states this relation".

### 1.7 `KnowledgeSnapshot` — `KnowledgeSnapshot.java:42-55`

```
String workspaceId, String workspaceName,
List<Concept> concepts, List<Source> sources, List<Claim> claims, List<Evidence> evidence,
List<Representation> representations, List<Prerequisite> prerequisites,
List<KnowledgeRevision> revisions, List<Derivation> derivations,
List<Contradiction> contradictions, List<LearningGoal> goals, String exportedAt
```

* The snapshot is the unit of import/export/persistence; it is self-contained
  (`KnowledgeSnapshot.java:6-19`).
* **It carries a `List<Prerequisite>`** — the one persisted concept → concept edge list in the
  model. `Prerequisite` is `Prerequisite.java:7-12`:
  `(workspaceId, conceptId, requiresConceptId, required, note)` — a directed edge whose **relation
  type is fixed to "requires"** and whose **free-text `note` is the only place any qualifier can
  live**. The `note` is not queryable as a type. It is persisted
  (`KnowledgeStore.java:581-589`) and projected as a graph relation
  (`KnowledgeGraphProjector.java:85-87`).

### 1.8 The acquisition path, and what it writes

`DocumentImporter.importDocument` (`DocumentImporter.java:113-310`) runs:
`SOURCE → EXTRACTION → NORMALIZATION → SEGMENTATION → CONCEPT DISCOVERY → CLAIM EXTRACTION →
EVIDENCE LINKING → VALIDATION → KNOWLEDGE INTEGRATION` (`DocumentImporter.java:28-33`).

What it actually writes, per document:

| Record | How it is created | Source line |
|---|---|---|
| `Source` | one per document, `HUMAN_ASSERTION`, authority/freshness 50/50 | `DocumentImporter.java:199-202` |
| `Concept` | **one per distinct claim subject**, with **`definition = null` and `domain = null`** | `DocumentImporter.java:225-231` |
| `Claim` | one per extracted sentence, `status = CANDIDATE`, `confidence = 0.0` | `DocumentImporter.java:232-242` |
| `Evidence` | one per claim, `relation = SUPPORTS`, `excerpt = proposition` | `DocumentImporter.java:243-245` |
| `Representation` | 1 INTRODUCTORY per touched concept + conditional INTERMEDIATE/ADVANCED/MECHANISTIC | `DocumentImporter.java:266-284`, `RepresentationComposer.java:72-126` |
| `Derivation` | **none** — nothing in the acquisition path calls `recordDerivation` | `DocumentImporter.java:286-309` (only `ClaimValidator.validate` at `:305`) |
| `Prerequisite` | **none** — `new KnowledgeChanges(..., List.of())` | `DocumentImporter.java:286-287` |

**`ClaimValidator` (`ClaimValidator.java:102-260`) writes only claim statuses and evidence rows.**
It promotes `CANDIDATE → SUPPORTED` on one supporting source and `→ VERIFIED` on two independent
content hashes; it never creates a concept relation, never writes a definition and never records a
derivation.

### 1.9 The knowledge-package path, for contrast

`KnowledgeImporter` (pack import) is a **different route** with different writers:

| Route | Concept id | Definition | Concept → concept edges | Derivations |
|---|---|---|---|---|
| acquisition (documents) | `Ids.digest(ws+"\|concept\|"+name.lower())` (`DocumentImporter.java:338-341`) | **always `null`** (`:227-228`) | **none** | **none** |
| package import | `Ids.concept(ws, name)` = `"c-"+slug(name)+"-"+digest(ws+"\|"+name)` (`Ids.java:25-27`, `KnowledgeImporter.java:130`) | from the pack entry | `Prerequisite` rows (`KnowledgeImporter.java:254-258`); `Representation.prerequisiteConceptIds` (`:243-251`) | from the pack entry (`KnowledgeImporter.java:230`) |

The two id schemes are **not the same function**: the package path digests the *raw* name, the
acquisition path digests the *lower-cased* name. The same name therefore yields **two different
concept ids** depending on the route (§3.6).

### 1.10 `KnowledgeRetriever` — the consumer

`KnowledgeRetriever.retrieve` (`KnowledgeRetriever.java:540-701`) builds an answer from a
workspace snapshot:

* cited claims = scored candidates + bridged claims (`KnowledgeRetriever.java:552-553`);
* the answer's **statement is one paragraph per selected concept**, taken from that concept's
  representation at the requested level (`KnowledgeRetriever.java:566-594`), joined with `"\n\n"`
  at `KnowledgeRetriever.java:701`;
* the concepts are ranked by score and then by **name coverage** — a lexical measure
  (`KnowledgeRetriever.java:540-549`).

The retrieval is therefore **lexical over claim and concept-name text**. There is no relation
lookup in the retrieval path: nothing in `KnowledgeRetriever` consults a concept → concept edge.

---

## 2. The corpus

### 2.1 The seed corpus (`w50-plate-tectonics`)

`matrix-core/src/main/resources/corpus/plate-tectonics/corpus-manifest.json` — the manifest that
`ClasspathCorpus.extract` reads as the index (`ClasspathCorpus.java:57-83`), imported by
`AcquisitionCycle.loadSeed` (`AcquisitionCycle.java:36`, `:133-151`).

| # | File | Licence | Attribution | Bytes | sha256 (manifest) | Claims extracted |
|---|---|---|---|---|---|---|
| 1 | `usgs-this-dynamic-earth.md` | `US-PUBLIC-DOMAIN` | U.S. Geological Survey, "This Dynamic Earth" | 17 144 | `a82d4ca7…c2467cdc` | **77** |
| 2 | `wikipedia-plate-tectonics.md` | `CC-BY-SA-4.0` | Wikipedia, revision `oldid=1381934246`, 2026-10-25 | 58 972 | `db794272…a8fc1c853` | **271** |
| | | | | **76 116** | | **348** |

Claim counts: instrument, §8; corroborated by `E2-RETRIEVAL-EXPERIMENT.md:188` and
`B3-EVIDENCE-TO-ACTION-RESULT.json` (`seedClaimsAdded: 348`).
Licences, byte counts and hashes: the manifest file above (lines 5-33); the measured sha256 of
the bytes on disk equals the manifest value for both files.

### 2.2 The research pool

`matrix-core/src/main/resources/research-pool/plate-tectonics/corpus-manifest.json` — 7
`CC-BY-SA-4.0` Wikipedia documents, acquired by the bounded cycle
(`AcquisitionCycle.java:39`, `:42` — at most 3 documents per cycle).

| File | Bytes | sha256 (manifest) | Claims extracted | Distinct concepts contributed **alone** |
|---|---|---|---|---|
| `wikipedia-continental-drift.md` | 19 426 | `0c7724c9…65d2` | 90 | 85 |
| `wikipedia-seafloor-spreading.md` | 15 697 | `ebd0d04c…06c4` | 92 | 89 |
| `wikipedia-subduction.md` | 29 731 | `a44b13d8…7b74` | 152 | 143 |
| `wikipedia-mid-ocean-ridge.md` | 17 922 | `0cb70b43…60dd` | 82 | 80 |
| `wikipedia-mantle-convection.md` | 9 218 | `7991295c…f682` | 45 | 44 |
| `wikipedia-slab-pull.md` | 3 259 | `3bddce4a…bc30` | 14 | 14 |
| `wikipedia-paleomagnetism.md` | 10 981 | `af0b1cec…eb00` | 65 | 65 |

Byte counts and sha256: the pool manifest. **Every one of the nine corpus files' measured sha256
equals the manifest value** (command: `sha256sum matrix-core/src/main/resources/corpus/
plate-tectonics/*.md matrix-core/src/main/resources/research-pool/plate-tectonics/*.md`).

Claim and concept counts: instrument, §8; each document imported **alone** into an empty
workspace, so the last column is "concepts this document brings" and not "concepts newly created
in a cumulative workspace" (the cumulative numbers are §3.1). *Which* three documents the P1 scope
acquires is **NOT
re-measured** here (it depends on `gapDetector.uncoveredTerms`); it is taken from the retained
artefact `E2-CANDIDATE-RECALL.json`, whose `citedSources` for the P1 cases are `usgs-this-dynamic-earth.md`,
`wikipedia-continental-drift.md`, `wikipedia-mid-ocean-ridge.md`, `wikipedia-plate-tectonics.md`,
`wikipedia-subduction.md` — i.e. the seed plus **continental-drift, mid-ocean-ridge, subduction**.

### 2.3 The other corpus in the tree

`matrix-core/src/main/resources/corpus/corpus-manifest.json` — 3 `AGPL-3.0` documents
(`photosynthesis-notes.md`, `water-boiling.txt`, `photosynthesis-page.html`). They are **not** part
of the E2/B3 corpus and are not used by any E2 scope.

### 2.4 The evaluation dataset

`matrix-core/src/main/resources/epistemic-eval/cases.json` — `dataset = "w50-plate-tectonics"`,
`version = 1.1`, `date = 2026-11-07`, **29 cases**, six scopes:

| Scope | Workspace | Preparation | Cases |
|---|---|---|---|
| `S1` | `plate-tectonics-eval-seed` | seed | A-01…A-06, U-01…U-05, F-01…F-04, M-01, M-02, K-01, K-02 (19) |
| `P1` | `plate-tectonics-eval` | seed + learn(3 docs) | P-01, P-02, U-06, F-05, M-03, R-01 (6) |
| `R1` | `plate-tectonics-eval` | P1 + revise(`wikipedia-slab-pull.md`) | R-03 (1) |
| `R2` | `plate-tectonics-eval` | R1 + revise(`wikipedia-paleomagnetism.md`) | R-02 (1) |
| `P1'` | `plate-tectonics-eval-neg` | a fresh copy of P1 | R-04 (1) |
| `P1''` | `plate-tectonics-eval-neg2` | a second fresh copy of P1 | R-05 (1) |

The `S1` scope description in the dataset reads, verbatim: *"the two seed documents, 348 claims,
2 sources, no pool document"*.

---

## 3. Measured coverage

### 3.0 The counting rules, stated before the numbers

* **Distinct concept** = one distinct `Concept.id` in the workspace. Because the acquisition
  path's id is a pure function of the workspace and the lower-cased name
  (`DocumentImporter.java:338-341`), the count of distinct concept ids equals the count of
  distinct `name.strip().toLowerCase()` values. Measured: 326 ids / 326 distinct lower-cased
  names in S1 — the two rules agree exactly.
* **Usable definition** = the concept's `definition` field is non-null, contains at least one
  non-whitespace character, and contains at least one token of length ≥ 3 that is not an English
  function word (so that a lexical retriever can match a question against it). A definition that
  fails any of the three is counted as **not usable**.
* **Claim has a representation** = the claim's `proposition` string occurs in the
  `includedFacts` of at least one `Representation` of the workspace. (`Representation` holds
  proposition strings, not claim ids — `RepresentationComposer.java:158-164` — so the join must
  be made on the proposition.)
* **Relation coverage** is counted over the *persisted* edges only: `Prerequisite` rows (the only
  persisted concept → concept edge), `Representation.prerequisiteConceptIds`, and `Derivation`
  rows. The `KnowledgeGraphProjector` edges are reported separately because that graph is a
  read-only projection rebuilt from the snapshot and discarded
  (`KnowledgeGraphProjector.java:24-33`, `KnowledgeGraphStore.java`).

### 3.1 The coverage table

| Measure | **S1** (seed only) | **P1** (seed + 3) | **R2** (P1 + 2 revisions) |
|---|---|---|---|
| sources | 2 | 5 | 7 |
| claims | **348** | **672** | **751** |
| evidence records | 348 | 672 | 751 |
| **distinct concepts** | **326** | **615** | **690** |
| distinct lower-cased names (cross-check) | 326 | 615 | 690 |
| **concepts WITH a usable definition** | **0** | **0** | **0** |
| concepts with a non-blank `definition` field | 0 | 0 | 0 |
| concepts with a non-blank `domain` field | 0 | 0 | 0 |
| concepts with a non-null `definition` of any kind | 0 | 0 | 0 |
| **claims carrying a concept id** | **348 (100 %)** | **672 (100 %)** | **751 (100 %)** |
| claims with no concept id | 0 | 0 | 0 |
| concepts holding exactly one claim | 319 | 595 | 667 |
| claims-per-concept histogram (S1) | 1→319, 2→3, 3→1, 4→1, 6→1, 10→1 | | |
| **representations** | **335** | **654** | **733** |
| — INTRODUCTORY | 328 | 636 | 715 |
| — INTERMEDIATE | 7 | 18 | 18 |
| — ADVANCED | **0** | **0** | **0** |
| — MECHANISTIC | **0** | **0** | **0** |
| concepts with ≥ 1 representation | 326 (100 %) | 615 (100 %) | 690 (100 %) |
| **claims covered by a representation** | **348 (100 %)** | **672 (100 %)** | **751 (100 %)** |
| representations with non-empty `prerequisiteConceptIds` | **0** | **0** | **0** |
| representations with a non-null `refinesRepresentationId` | **0** | **0** | **0** |
| **persisted `Prerequisite` rows (concept → concept)** | **0** | **0** | **0** |
| **persisted `Derivation` rows** | **0** | **0** | **0** |
| projection edges `states` (claim → concept) | 348 | 672 | 751 |
| projection edges `supported by` (claim → source) | 348 | 672 | 751 |
| projection edges `requires` (concept → concept, typed) | **0** | **0** | **0** |
| projection edges `related by` (concept ↔ concept, **untyped**) | **144** | **410** | **446** |

Sources for every cell: the instrument of §8 applied to the corpus files of §2; the projection
edge counts follow `KnowledgeGraphProjector.java:77-113` — the `related by` cell is the number of
**ordered** edges the projector emits (two per unordered pair of concepts that share a claim), so
144 ordered edges = 72 unordered pairs in S1, 410 = 205 in P1, 446 = 223 in R2.

### 3.2 Definition coverage — the headline

**0 of 326 concepts in the seed workspace carry a usable definition; 0 of 615 in P1; 0 of 690 in
R2.** The reason is a single line of production code: `DocumentImporter.java:227-228` constructs
every document-derived concept as
`new Concept(id, workspaceId, subject.strip(), null, null, importedAt)` — the `definition` and the
`domain` are literal `null`.

For contrast, the hand-authored knowledge packages (which are **not** on the E2/B3 path):

| Pack | Concepts | With a non-blank definition | With a non-blank domain | Claims | Representations | `Prerequisite` rows | `Derivation` rows |
|---|---|---|---|---|---|---|---|
| `matrix-core/src/main/resources/knowledge/photosynthesis.pack.json` | 8 | **8 (100 %)** | 8 | 17 | 13 | 5 | 0 |
| `matrix-core/src/main/resources/knowledge/logic.pack.json` | 11 | **11 (100 %)** | 11 | 28 | 8 | 6 | **3** |

So the model **can** hold a definition and a typed concept → concept edge; the *acquisition
route* simply never writes either.

### 3.3 The three derivations that exist in the whole repository

All three live in `logic.pack.json` (`derivations` array), imported by
`KnowledgeImporter.java:230`:

| Rule name | Connective | Premises | Conclusion |
|---|---|---|---|
| `modus ponens` | `AND` | `c-p-true`, `c-p-implies-q` | `c-q-true` |
| `modus tollens` | `AND` | `c-p-implies-q`, `c-q-false` | `c-p-false` |
| `modus ponens over two steps` | `AND` | `c-p-true`, `c-p-implies-q`, `c-q-implies-r` | `c-r-true` |

Relation kinds actually in use across the model: `EvidenceRelation` has 7 values
(`EvidenceRelation.java:4-34`); `Derivation.Connective` has 4 (`Derivation.java:44-58`);
`Prerequisite` has exactly one kind, `requires`. **No other relation kind exists anywhere.**

### 3.4 The B3 demonstration answer: 41 claims, 36 concepts, 200 paragraphs

From `docs-v2/engineering/B3-EVIDENCE-TO-ACTION-RESULT.json` and
`docs-v2/demos/evidence-to-action/output/evidence-report.md`:

| Quantity | Value | Source |
|---|---|---|
| claims cited by the answer | **41** | `B3-EVIDENCE-TO-ACTION-RESULT.json` `"claims": 41`; the report's "## Supported claims" list has 41 `- \`clm-…\`` entries |
| distinct concepts touched by those 41 claims | **36** | instrument, §8, against the report's 41 claim ids |
| claims per touched concept (max) | 6 | instrument |
| paragraphs in the answer's statement | **200** | `B3-EVIDENCE-TO-ACTION-RESULT.json` `"statement"` split on `"\n\n"`; the report's "## Answer" section |
| distinct concepts behind those 200 paragraphs | **200** | instrument — each paragraph equals exactly one seed claim's proposition |
| share of the workspace's concepts that reached the answer prose | 200 / 326 = **61.3 %** | instrument |
| sources cited | 2 (`src-c3ad97fa`, `src-4d135a23`) | report "## Sources" |
| seed claims added by the import | 348 | `B3-EVIDENCE-TO-ACTION-RESULT.json` `"seedClaimsAdded"` |
| limitations recorded | 42 (41 claim-level + 1 support-score note) | `B3-EVIDENCE-TO-ACTION-RESULT.json` `"limitations"` |

**The answer's prose is concept-level (200 concepts) while its citations are claim-level
(41 claims)** — the two numbers are not the same population, and `KnowledgeRetriever.java:566-594`
and `:701` show why: the statement is one representation statement per selected concept, joined
with `"\n\n"`.

### 3.5 "Concepts requiring enrichment" vs the 41 claims of one B3 answer — the numerical difference

* **Concepts requiring enrichment** (no usable definition) in the seed workspace: **326**.
* **Claims cited in one B3 answer**: **41**.
* **Distinct concepts behind those 41 claims**: **36**.

The difference, stated exactly:

1. **326 − 36 = 290** concepts that need enrichment are outside the footprint of that one answer.
   The answer's concept footprint (36) is **11 %** of the enrichment backlog (326).
2. **326 ÷ 41 ≈ 8.0**: the workspace holds roughly eight enrichment targets for every claim the
   answer cites, because **one claim carries exactly one concept id** (`Claim.java:21`) and
   **319 of 326 concepts hold exactly one claim** (§3.1) — i.e. the concept population is nearly
   one-to-one with the claim population, not a small overlay on it.
3. **41 is a per-answer count, not a corpus size.** The E2 experiment says so in its own words:
   *"the 41 claims of one answer are a per-answer count, not the corpus's size"*
   (`E2-RETRIEVAL-EXPERIMENT.md:189-190`). The corpus size is **348 claims / 326 concepts**.
4. The two numbers are **not** in conflict: enriching 326 concepts would raise the *ceiling* of
   what the retriever can reach, while the 41 is what one question actually pulled out of the
   348-claim workspace at floor 0.25. Enrichment work is sized by the **326**, and validated by
   the **41**.

### 3.6 Ambiguous names and conflicting concept identities

Measured on the S1 workspace (326 concepts):

| Check | Result | Rule |
|---|---|---|
| same lower-cased name carrying **two different concept ids** | **0** | the acquisition id function is injective on the lower-cased name (`DocumentImporter.java:338-341`) |
| **same name, two different ids across the two routes** | **possible and structural** | acquisition: `digest(ws+"\|concept\|"+name.lower())`; package: `"c-"+slug(name)+"-"+digest(ws+"\|"+name)` (`Ids.java:25-27`, `KnowledgeImporter.java:130`). Example, name `"Oceanic trenches"`, workspace `plate-tectonics-eval`: acquisition `d542c957` vs package `c-oceanic-trenches-9c168dd7` |
| concept name that is a **strict substring of another concept name** | **124 ordered pairs** | e.g. `Trenches` ⊂ `Deep marine trenches`, `The trenches`; `This` ⊂ `This theory`, `This process`, … |
| concept name that is a **single English function word** (It, There, This, To, Most, Other, Many, They, These) | **9** | closed list of 120 function words; the subject extractor stops at the first declarative verb (`ClaimExtractor.java:282-300`), so a sentence starting with a pronoun yields a pronoun-named concept |
| concept name **ending in a dangling function word** (the subject was cut mid-phrase at the verb) | **61** | e.g. `Convergence can`, `Magmas that`, `the crust tends to buckle and`, `Understanding plate motions [This Dynamic Earth, USGS] Scientists now` |
| concept name shorter than 4 characters | 3 | |
| **concept names that are semantically the same entity under two names** | **NOT MEASURED** | would require an author judgement, not a script; the 124 substring pairs above are the mechanical proxy and are a *superset* of the true near-duplicates |

The dominant ambiguity is therefore **not** id collision — it is **identity fragmentation**: the
same real entity acquires a different concept whenever the document's sentence subject phrase
differs, and 61 of the 326 names (19 %) are grammatical fragments rather than entity names. **[corrected 2026-11-15 — the EPISTEMIC-CONCEPT-IDENTITY measurement could not reproduce this figure from the repository: the 120-word list behind it was never published. The reconstructed count is 61 on that list (18.7 %) and 75 on a wider one (23.0 %), so the honest statement is a BAND, and any acceptance criterion of the form "the rate is 0" must name its list]**

The fragmentation is visible in the claim distribution itself — the concepts that hold the **most**
claims are the fragments, because a pronoun or a dangling phrase is the subject of many sentences
(S1, measured):

| Concept name | Claims held | Representations (level / facts / omitted) |
|---|---|---|
| `This` | **10** | INTRO (1/0), INTRO (1/8), INTERMEDIATE (8/1) |
| `It` | **6** | INTRO (1/5), INTERMEDIATE (5/1) |
| `There` | **4** | INTRO (1/0), INTRO (1/2), INTERMEDIATE (2/1) |

So the three largest "concepts" of the seed corpus are pronouns, and their representations are
assembled from the sentences that happened to begin with them — a concrete instance of the
"definition is empty and no relation edge exists" shape that `E2-RETRIEVAL-EXPERIMENT.md:138-149`
traces for `d542c957`.

### 3.7 Gold propositions of the 29-case evaluation

**Reading A — gold proposition ↔ its source claim (verbatim), over all 29 cases:**
**43 of 43 required propositions are present verbatim in the claims of their scope's workspace.**
No case is missing a required proposition from the corpus. The gap is therefore **not** a
missing-content gap. (Instrument, §8; per-case table: A-01 2/2, A-02 3/3, A-03 3/3, A-04 3/3,
A-05 4/4, A-06 3/3, P-01 3/3, P-02 3/3, F-01 1/1, F-02 1/1, F-04 2/2, F-05 1/1, M-01 3/3,
M-02 3/3, M-03 5/5, K-01 1/1, R-03 2/2; all other cases declare 0 required propositions.)

**Reading B — gold proposition ↔ the question (lexical match at retrieval time):**
from the retained artefact `docs-v2/engineering/E2-CANDIDATE-RECALL.json`:

| Fact | Value | Where in the artefact |
|---|---|---|
| gold entries | **27** (22 distinct propositions) | `E2-CANDIDATE-RECALL.json` — 8 cases × their gold lists |
| gold claim present in the workspace | **27 of 27** | every entry's `"inWorkspace": true` |
| gold claim's epistemic status | `SUPPORTED` for all 27 | every entry's `"status"` |
| cited at floor 0.0 | **16 of 27** | `"citedExact": true` |
| **cited nowhere even at floor 0.0 (no lexical match)** | **11 of 27 entries / 9 distinct propositions** | `"citedExact": false, "citedNormalised": false, "citedRank": null` |
| **cases with at least one such entry** | **A-05, A-06, M-01, M-03, P-01, P-02** | the `cases` keys carrying such an entry |

The 11 entries with no lexical match, verbatim from the artefact:

| Case | Gold proposition (truncated in the artefact) |
|---|---|
| A-05 | "when these magnetic patterns were mapped over a wide region, the ocean floor showed a zebra-like pattern: one stripe wit…" |
| A-06 | "magmas that form island arcs are produced by the partial melting of the descending plate and/or the overlying oceanic li…" |
| M-01 | "the san andreas fault in california is an example of a transform boundary exhibiting dextral motion" |
| M-03 | "the fossils of mesosaurus, a freshwater reptile rather like a small crocodile, found both in brazil and south africa, ar…" |
| M-03 | "this feature is where seafloor spreading takes place along a divergent plate boundary" |
| M-03 | "beyond the forearc basin, volcanoes are found in long chains called volcanic arcs" |
| P-01 | "this feature is where seafloor spreading takes place along a divergent plate boundary" |
| P-01 | "in the 1960s, geologists discovered and began to propose mechanisms for seafloor spreading" |
| P-02 | "oceanic trenches are the deepest parts of the ocean floor" |
| P-02 | "beyond the forearc basin, volcanoes are found in long chains called volcanic arcs" |
| P-02 | "since the earth's circumference has not changed over geologic time, hess concluded that older seafloor has to be consume…" |

`E2-RETRIEVAL-EXPERIMENT.md:138-149` (§4d) traces two of them to a concept whose **definition is
empty** and to a **relation that is nowhere in the workspace** — the recorded shape of the gap is
an enrichment gap, not a ranking bug.

---

## 4. The E2 baseline, exactly as the retained artefacts state it

**Not re-run.** The experiment was not executed (no Gradle build; the artefacts are the record).
Every number in this section is read out of a file in `docs-v2/engineering/`.

### 4.1 The 29-case results

| Artefact | Total | answerable | unanswerable | false-premise | multi-claim | knows-what-it-does-not-know | revision |
|---|---|---|---|---|---|---|---|
| `E2-FLOOR-0p25.json` (**baseline**, floor 0.25) | **18 / 29** | 2 / 8 | **6 / 6** | 4 / 5 | 1 / 3 | 2 / 2 | 3 / 5 |
| `E2-FLOOR-0p15.json` (floor 0.15) | **17 / 29** | 4 / 8 | 3 / 6 | 4 / 5 | 1 / 3 | 2 / 2 | 3 / 5 |
| `E2-FLOOR-0p10.json` (floor 0.10) | **15 / 29** | 4 / 8 | 1 / 6 | 4 / 5 | 1 / 3 | 2 / 2 | 3 / 5 |
| `E2-FLOOR-0p0.json` (floor 0.0) | **15 / 29** | 4 / 8 | 1 / 6 | 4 / 5 | 1 / 3 | 2 / 2 | 3 / 5 |

Each artefact carries `"passed": N, "total": 29` and a `byCategory` object; the rows above are
those objects verbatim.

Failing case ids, read from the `results[].verdict` field of each artefact:

* floor **0.25** (the baseline): `A-02, A-04, A-05, A-06, F-02, M-01, P-01, P-02, M-03, R-04, R-05`
  — **eleven** failures, matching `EVID-BASE-BASELINE.json:34-46` exactly.
* floor 0.15: `A-05, A-06, U-02, U-04, F-02, M-01, P-01, P-02, U-06, M-03, R-04, R-05`
* floor 0.10: `A-05, A-06, U-01, U-02, U-04, U-05, F-02, M-01, P-01, P-02, U-06, M-03, R-04, R-05`
* floor 0.0: identical to floor 0.10.

`E2-FLOOR-SWEEP.json` covers floors **0.10** and **0.15** only, and its `cases` map shows the
only two cases whose verdict differs between them: `U-01` and `U-05` (FAIL at 0.10, PASS at 0.15).
This is exactly the "loss of three abstentions at 0.15" of `E2-RETRIEVAL-EXPERIMENT.md:120`.

### 4.2 The candidate-recall measurement

`E2-CANDIDATE-RECALL.json`, `"summary"`:

```
recallAt1 = 3, recallAt5 = 4, recallAt10 = 6, goldCitedWithFloorZero = 16, mrr = 0.2593
```

with the note: *"floor 0.0 cites every citable claim with any term match, so a gold claim absent
here has matched zero terms."*

The per-case `citedRank` values of the 16 cited entries are
`1, 1, 1, 2, 9, 10, 11, 12, 13, 17, 17, 33, 41, 129, 303, 368`.

### 4.3 What the artefacts say about reproducibility

* `E2-RETRIEVAL-EXPERIMENT.md:11-17` — the baseline was produced by
  `python3 scripts/w51-epistemic-eval.py` on the packaged jar; the artefact is `E2-FLOOR-0p25.json`.
* `E2-RETRIEVAL-EXPERIMENT.md:126` — *"A reproduce run must pass `--force`, because the harness
  reuses a committed report by default."*
* `E2-RETRIEVAL-EXPERIMENT.md:50-54` — **the citation-floor numbers (17/29 at 0.0 and 0.10) are
  NOT reproducible in this tree**: the two-threshold change was reverted before any commit and
  the artefacts were removed. Only §3 of that document preserves them.
* `EVID-BASE-BASELINE.json:26-74` — the baseline record: dataset `w50-plate-tectonics` v1.1,
  runner `scripts/w51-epistemic-eval.py`, 18/29, the same eleven failing ids, same per-category
  split, `"reproduced_exactly": true`.
* The seed corpus the 29 cases run against is **reproducible from the data in this repository**
  (§2.1): two documents, byte counts and sha256 in the manifest, both verified against the bytes
  on disk; the claim count that the pipeline derives from them (348) is the count the artefacts
  and the documents state.

---

## 5. Gap analysis

### 5.1 What is measured to be missing

| Gap | Measured extent | Source |
|---|---|---|
| **concepts without a usable definition** | 326 of 326 (S1); 615 of 615 (P1); 690 of 690 (R2) | §3.1, §3.2 |
| **concept → concept edges, typed** | **0** persisted, in every scope | §3.1 |
| **derivations** | **0** in every acquisition scope; 3 in the whole repository, all in `logic.pack.json`, all logical inferences | §3.3 |
| **representations above INTRODUCTORY** | 7 of 335 (S1); 18 of 654 (P1) — all INTERMEDIATE; **0** ADVANCED, **0** MECHANISTIC | §3.1 |
| **representations carrying a prerequisite concept** | **0** of 335 / 654 / 733 | §3.1 |
| **concept identities fragmented** | 61 of 326 names (19 %) end in a dangling function word — **a band, not a number**: the list behind it was never published, and the reconstruction gives 61 (18.7 %) on a 120-word list and 75 (23.0 %) on a wider one (EPISTEMIC-CONCEPT-IDENTITY.md, 2026-11-15); the root cause is measured: the concept NAME is the claim's subject phrase (`DocumentImporter.java:225-231`) and the concept ID is a digest of that name (`:338-341`), so a concept is the left side of a copula, cut wherever the verb list stopped | §3.6 |
| **gold propositions unreachable by lexical retrieval** | 11 of 27 entries, 9 distinct propositions, cases A-05, A-06, M-01, M-03, P-01, P-02 | §3.7 Reading B |
| **gold propositions missing from the corpus** | **0** of 43 — the content is all there | §3.7 Reading A |

### 5.2 The relation that the residual points at

`E2-RETRIEVAL-EXPERIMENT.md:138-149` records the traced shape:

```
claim clm-2a740579  status=SUPPORTED  concept=d542c957
concept name="Oceanic trenches"      definition=None
representation mentions trench: true   oceanic: true
                          subduction: false   produce: false   forms: false   volcan: false
```

with the analysis: *"the claim answers it through the semantic relation subduction → trenches,
and that relation is nowhere in the workspace: the concept's definition is empty, and no relation
edge exists."*

**This document does not state that relation as an established fact.** The measurement supports
exactly two statements:

1. **Measured:** the concept `d542c957` ("Oceanic trenches") has `definition = null` and the
   workspace has **0** persisted concept → concept edges (§3.1).
2. **Measured:** the claim `clm-2a740579` and the proposition "Oceanic trenches are the deepest
   parts of the ocean floor." both exist and are `SUPPORTED` (§3.7, and the artefact
   `E2-CANDIDATE-RECALL.json`, case M-03 and P-02).

Whether the *semantic* relation `subduction → produces → oceanic trenches` holds is **not**
established by this baseline, and nothing in the corpus directory states it as a relation —
the corpus states it as prose inside a sentence. Any typed relation derived from it is therefore

> **UNVERIFIED-CANDIDATE** — *subduction produces oceanic trenches*.

and it is listed here only because the retained artefact names it as the residual's shape. It
must not be treated as measured. The same marking applies to every relation a future enrichment
step would extract from these documents: the *documents* state propositions; the *model* has no
place to state a relation.

### 5.3 The candidates, by the kind of relation they would be

Marked by what the measurement can and cannot support:

| Candidate relation | Measured support | Status |
|---|---|---|
| `subduction → produces → oceanic trenches` | named as the residual's shape in `E2-RETRIEVAL-EXPERIMENT.md:138-149`; the propositions are present in the corpus | **UNVERIFIED-CANDIDATE** |
| `plate tectonics → states → lithosphere` | A-01's gold propositions are present verbatim; no edge exists | **UNVERIFIED-CANDIDATE** |
| `concept → requires → concept` | **MEASURED** to exist as a mechanism: 11 `Prerequisite` rows across the two packages, projected as the `requires` predicate (`KnowledgeGraphProjector.java:85-87`) | relation kind **exists**; 0 instances in the acquisition scopes |
| `claim → states → concept` | **MEASURED**: 348 edges in S1, projected at `KnowledgeGraphProjector.java:77-79` | exists; derived, not authored |
| `claim → supported by → source` | **MEASURED**: 348 edges in S1, projected at `:81-83` | exists; derived |
| `concept ↔ concept (co-occurrence)` | **MEASURED**: 144 ordered edges = **72 unordered pairs** in S1, projected as the untyped `related by` predicate at `:100-113` | exists; **untyped** |

---

## 6. Can the existing types carry a typed relation between concepts?

### 6.1 The answer

**No — not an authored, typed, persisted relation between two concepts — with the seven types
named in the brief.** The nearest existing carrier is `Prerequisite`, whose relation kind is fixed
to `requires` and whose only extra slot is a free-text `note`.

But the *shape* of such a relation already exists in the codebase, in a read-only projection:

> **`KnowledgeGraphStore.Relation(String fromId, String toId, String predicate, double weight)`**
> — `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeGraphStore.java:71`

`fromId` / `toId` carry the **direction**, `predicate` carries the **relation type**, `weight`
carries the confidence. That is exactly the four fields a typed concept → concept edge needs.
**What it cannot do is carry an authored relation**: `KnowledgeGraphProjector.project(snapshot)`
builds the graph from the snapshot and throws it away
(`KnowledgeGraphProjector.java:24-33`, `:30` "It holds no state of its own"), and it emits only
four fixed predicates — `states`, `supported by`, `requires`, `related by`
(`KnowledgeGraphProjector.java:77-113`).

### 6.2 Type by type

| Type | Field that could hold a concept → concept edge | Verdict |
|---|---|---|
| `Concept` (`Concept.java:10-16`) | none — `id, workspaceId, name, definition, domain, createdAt` | **cannot**. There is no relation field at all. `definition`/`domain` are free text, not edges. |
| `Claim` (`Claim.java:18-34`) | `conceptId` — exactly **one** concept per claim | **cannot** carry an edge between two concepts. A claim is the *payload* of a relation, not a relation: it has one concept, one proposition, and no second endpoint. |
| `Evidence` (`Evidence.java:11-19`) | `claimId` + `sourceId` + `EvidenceRelation relation` | **cannot** as-is: the endpoints are fixed to *source → claim*, and a concept may not be an endpoint. **The `EvidenceRelation` vocabulary itself (7 values) is the natural source of relation kinds if a relation type is ever introduced.** |
| `Source` (`Source.java:12-25`) | none | **cannot**. A source is the provenance of a relation, not the relation. |
| `Representation` (`Representation.java:14-29`) | **`prerequisiteConceptIds` (`Representation.java:26`)** — `List<String>` of concept ids, persisted (`KnowledgeStore.java:2246`) and validated (`KnowledgeStore.java:2358-2363`) | **closest of the seven**. It is a real, persisted, validated concept → concept channel. **But:** (a) the relation type is fixed to *prerequisite* by the field's semantics; (b) the direction is fixed — the representation's concept *requires* the listed ones; (c) the acquisition route writes `List.of()` every time (`RepresentationComposer.java:179`), so the channel is **0 % populated** in all measured scopes; (d) the edge's owner is the **representation**, not the concept, so a concept's edge set would depend on which representation happened to be loaded. |
| `Derivation` (`Derivation.java:25-34`) | `premiseClaimIds` → `conclusionClaimId`, `ruleId`/`ruleName`, `Connective` | **cannot, without changing its meaning.** The direction field exists (premise → conclusion) and `ruleName` is free text that could hold a relation kind — but (a) the endpoints are **claims**, not concepts (a concept edge would be an *interpretation* of the claim edge); (b) the connective vocabulary is **logical** (`AND`/`OR`/`IMPLIES`/`NOT`), not semantic; (c) the store enforces that premises and conclusion are claims of the workspace (`KnowledgeStore.java:3465-3495`) and that the derivation names a rule; (d) a derivation's `status` is an **entailment verdict** — recording a corpus-stated relation here would assert that a checker produced that verdict, which no checker did. |
| `KnowledgeSnapshot` (`KnowledgeSnapshot.java:42-55`) | `List<Prerequisite> prerequisites` (`KnowledgeSnapshot.java:50`) | **the only persisted concept → concept edge list in the model.** See 6.3. |

### 6.3 The narrowest existing carrier: `Prerequisite`

`Prerequisite` (`Prerequisite.java:7-12`):

```java
public record Prerequisite(
        String workspaceId,
        String conceptId,        // the edge's SOURCE endpoint
        String requiresConceptId,// the edge's TARGET endpoint
        boolean required,
        String note)             // free text — the only place a qualifier can live
```

* It is already in the snapshot (`KnowledgeSnapshot.java:50`), already persisted
  (`KnowledgeStore.java:581-589`), already written by the package importer
  (`KnowledgeImporter.java:254-258`), already transferred between workspaces
  (`KnowledgeTransfer.java:205`), and already projected as the typed graph edge `requires`
  (`KnowledgeGraphProjector.java:85-87`).
* Its **relation kind is fixed to one value** (`requires`), and the direction is fixed
  (`conceptId` requires `requiresConceptId`; a concept may not require itself —
  `Prerequisite.java:19-21`).
* Any other relation kind — `produces`, `part_of`, `causes`, `is_a` — would have to be written
  into `note`, i.e. into free text that cannot be queried, filtered or counted by kind.

**So: with existing types, a typed relation is representable only as "requires", or as free text
in `Prerequisite.note` / `Derivation.ruleName` / `Representation` prose.**

### 6.4 The narrowest implementation scope, for the owner's decision

Three options, from narrowest to widest. **None is implemented here.**

**Option A — reuse `Representation.prerequisiteConceptIds` (no new type).**
Populate it for document-derived concepts and keep the relation kind fixed to *prerequisite*.
Cost: one writer (`RepresentationComposer`), one field already persisted and validated.
Limit: **only one relation kind, one direction, and the edge hangs off the representation rather
than the concept.** Sufficient for a prerequisite graph; **not** sufficient for
`subduction → produces → trenches`.

**Option B — widen `Prerequisite` (no new type, changed meaning).**
Add a `relationKind` field and a nullable/inverted target, so one row can express
`from —kind→ to`.
Cost: one record, one DDL migration (`KnowledgeStore.java:581-589`), one importer, one projection
update, one migration of the 11 existing rows.
Limit: the name `Prerequisite` stops describing the record, and `required`/`note` become
redundant. It also collides with the frozen-ish semantics the directive gave the type.

**Option C — one new type (recommended shape; NOT implemented).**

```java
// io/matrix/knowledge/ConceptRelation.java   (proposed, not written)
public record ConceptRelation(
        String id,
        String workspaceId,
        String fromConceptId,   // the edge's SOURCE endpoint
        String toConceptId,     // the edge's TARGET endpoint
        RelationKind kind,      // the TYPED relation kind
        String note,
        String sourceId,        // the document the relation was read from
        String recordedAt) {}
```

plus a `RelationKind` enum whose first members are taken from the **existing**
`EvidenceRelation` vocabulary (`EvidenceRelation.java:4-34`) rather than invented a second time,
a `concept_relation` table alongside `prerequisite` (`KnowledgeStore.java:581`), one field on
`KnowledgeSnapshot` (`KnowledgeSnapshot.java:42-55`), one writer in the acquisition path next to
`DocumentImporter.java:266-284`, and one predicate in `KnowledgeGraphProjector.java:77-113`
(which then also makes the relation *persistable* instead of projection-only).

**Minimum blast radius of Option C: 1 new record + 1 new enum + 1 new table + 1 new snapshot
field + 1 writer + 1 projection predicate.** It is the smallest change that yields a relation
whose **kind** and **direction** are both queryable.

---

## 7. Limits — what was NOT measured, and why

| Not measured | Why |
|---|---|
| The E2 experiment was **not re-run** | No Gradle build (another process owns the build lock; a heavy build would break the machine). §4 reports the retained artefacts verbatim, as instructed. |
| The citation-floor numbers (17/29 at 0.0 and 0.10) | **Not reproducible in this tree** — the two-threshold change was reverted before any commit and its artefacts were removed (`E2-RETRIEVAL-EXPERIMENT.md:50-54`). Only the document preserves them. |
| The claim statuses in the measured workspaces | The instrument reproduces the extraction, the identity functions and the representation composition, and it reproduces the 348-claim count and the 41 cited ids; but it does **not** reproduce `ClaimValidator`'s promotion outcome for every claim (the validation groups by normalised proposition across the whole workspace, `ClaimValidator.java:114-124`). The status of the 41 B3 claims is taken from the retained report (`SUPPORTED`), not re-derived. |
| The **exact selection** of the three pool documents by the bounded cycle | It depends on `gapDetector.uncoveredTerms(question)` (`AcquisitionCycle.java:77`), which needs the live workspace. The three documents were taken from `E2-CANDIDATE-RECALL.json`'s `citedSources` for the P1 cases. The claims-per-pool-document table in §2.2 is the per-document measurement of each document imported on its own, not of the selected triple. |
| The **grounding of the workspace's `workspace` table row** and the `registerWorkspace` path | Not re-implemented; irrelevant to coverage. |
| **Semantic** duplicate concepts (same entity, two names) | Requires an author judgement. §3.6 gives the mechanical proxy (124 substring pairs, 61 dangling names) and states the true count as **NOT MEASURED**. |
| The 200 paragraphs of the B3 answer → 200 concepts | Measured. *Why* 200 concepts and only 41 claims are cited is **NOT MEASURED**: `KnowledgeRetriever.java:552-553` (cited claims) and `:566-594` (statement per concept) are separate lists, and the mechanism that narrows one but not the other was not traced. |
| Any **relation kind** beyond `requires`, `states`, `supported by`, `related by` and the four logical connectives | None exists in the code. The relations proposed in §5.2 and §5.3 are marked `UNVERIFIED-CANDIDATE` and are **not** counted anywhere in §3. |
| The `photosynthesis.pack.json` / `logic.pack.json` workspaces | Not part of the E2/B3 path. Their numbers in §3.2 are read from the pack files themselves, not from a running workspace. |
| Runtime behaviour of the retriever (scores, ranks, floors) | Not measured; §4 reports the artefacts. The only runtime numbers used here are the ones the artefacts record. |
| Anything under `OpeAi/` and `.dsh-project-memory/` | Out of scope by instruction; not read, not written. |

**No file in the repository was modified by this work.** The only file created is this document.

*Audit note:* the working tree also shows changes to
`matrix-core/src/main/java/io/matrix/actions/ArtifactWriter.java`,
`matrix-core/src/test/java/io/matrix/actions/EvidenceToActionTest.java` and new untracked files
(`docs-v2/engineering/C1-LEARNING-WINDOW-DIAGNOSIS.md`,
`docs-v2/engineering/C2A-EXECUTION-PLAN.md`,
`matrix-core/src/test/java/io/matrix/actions/ArtifactWriterConcurrencyTest.java`,
`matrix-core/src/test/java/io/matrix/actions/LeaseHolder.java`,
`scripts/artifact-writer-negative-control.sh`). **These were produced by a concurrent process in
the same repository during this measurement; they are not part of Workstream B and were neither
read nor written here.**

---

## 8. Reproducing the measurements

### 8.1 The algorithm (language-neutral)

For each corpus document, in manifest order:

1. **Normalize** (`TextDocumentExtractor.java:59-77`): unify `\r\n`/`\r` to `\n`; strip trailing
   whitespace from every line; collapse runs of blank lines to one; strip the whole result.
2. **Segment** (`TextDocumentExtractor.java:85-146`): a Markdown heading (`#{1,6} `) starts a new
   segment and names it; a blank line also ends a segment; the segment text is the joined lines,
   stripped.
3. **Sentences** (`ClaimExtractor.java:133-218`): a line that starts a block (`#`, `|`, `>`,
   `- `, `* `, `+ `, or `N. `) is its own sentence; otherwise consecutive lines are joined with a
   space and then split on `.`, `!`, `?` — skipping a `.` that is followed by a digit, and a `.`
   that ends `e.` (the `e.g.` guard).
4. **Subject** (`ClaimExtractor.java:262-309`): reject a sentence outside 20…600 characters; drop a
   leading discourse marker from a closed list of 23; reject a sentence ending in `?`/`!` or
   starting with `how to `/`what is `/`why `; take the text before the **first** word that is a
   declarative verb (the 166-word list at `:48-67` — 128 English and 38 Russian — with `-s`, `-ed`
   and `-es` folding); reject an empty subject, a subject longer than 120 characters, or a subject
   whose first `,` precedes its first space.
5. **Identity**: `conceptId = digest(workspaceId + "|concept|" + subject.strip().toLowerCase())`;
   `claimId = "clm-" + digest(workspaceId + "|" + contentHash + "|" + proposition.strip().toLowerCase())`;
   `evidenceId = digest(claimId + "|evidence|" + sourceId)`; `digest` is **FNV-1a 64** rendered as
   8 lower-case hex characters (`Ids.java:117-124`); `contentHash` is the SHA-256 of the file's bytes.
6. **Records**: one `Source` per document; one `Concept` per distinct subject with
   `definition = null, domain = null`; one `Claim` and one `Evidence(SUPPORTS)` per extracted
   sentence.
7. **Representations** (`RepresentationComposer.java:72-181`): per concept touched by the
   import, sort its claims by the number of letters in the proposition (stable);
   INTRODUCTORY = the shortest claim; INTERMEDIATE = the claims that mention another concept's
   name; ADVANCED = the claims containing a mechanism word; MECHANISTIC = the claims containing a
   quantitative word; each level holds only what the level below does not; `prerequisiteConceptIds`
   and `refinesRepresentationId` are always empty/null; the representation id is
   `digest(workspaceId + "|representation|" + conceptId + "|" + level + "|" + sourceId)`.

### 8.2 The workspaces measured

| Workspace id | Documents (in import order) |
|---|---|
| `plate-tectonics-eval-seed` | `usgs-this-dynamic-earth.md`, `wikipedia-plate-tectonics.md` |
| `plate-tectonics-eval` (P1) | the two above + `wikipedia-continental-drift.md`, `wikipedia-mid-ocean-ridge.md`, `wikipedia-subduction.md` |
| `plate-tectonics-eval` (R1) | P1 + `wikipedia-slab-pull.md` |
| `plate-tectonics-eval` (R2) | R1 + `wikipedia-paleomagnetism.md` |
| `b3-demonstration` | the two seed documents (for the B3 cross-check) |

### 8.3 The cross-checks that make the numbers usable

The five checks of §0.1 — 348 claims, `d542c957`, `clm-2a740579`, `src-c3ad97fa`/`src-4d135a23`,
and 41 of 41 B3 claim ids — are the acceptance criterion for this instrument. **If a future
reader's re-implementation does not reproduce those five values, its other numbers are not
usable.**

---

## 9. One-page summary

| | S1 | P1 | R2 |
|---|---|---|---|
| concepts | 326 | 615 | 690 |
| **with a usable definition** | **0** | **0** | **0** |
| claims | 348 | 672 | 751 |
| claims with a concept id | 348 | 672 | 751 |
| representations (INTRO / INTER / ADV / MECH) | 335 (328/7/0/0) | 654 (636/18/0/0) | 733 (715/18/0/0) |
| **typed concept → concept edges (persisted)** | **0** | **0** | **0** |
| derivations | 0 | 0 | 0 |
| untyped co-occurrence edges (projection only, ordered) | 144 | 410 | 446 |

* The corpus is complete: **43 of 43** gold propositions are present verbatim in their scope.
* The retriever cannot reach 11 of 27 gold entries lexically — cases **A-05, A-06, M-01, M-03,
  P-01, P-02**.
* The enrichment backlog is **326 concepts**; one B3 answer cites **41 claims** touching
  **36 concepts** — a per-answer count, not the corpus size (§3.5).
* Typed relations between concepts: **not representable** by the seven named types as authored,
  persisted edges. The shape exists in `KnowledgeGraphStore.Relation`
  (`KnowledgeGraphStore.java:71`), the fixed-kind carrier exists in `Prerequisite`
  (`Prerequisite.java:7-12`), and the smallest correct addition is **one new record +
  one new enum + one new table + one new snapshot field + one writer + one projection predicate**
  (§6.4, Option C) — **not implemented here**.
