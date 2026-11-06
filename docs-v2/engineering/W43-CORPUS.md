# W43 — The corpus foundation

**Status:** implemented and measured · **Date:** 2026-11-02 · **Wave:** W43
**Owner decision:** none required (no schema change, no new dependency)

The mission's §7 asks for a corpus that can be ingested with its structure,
its locations, its warnings, its hashes, its deduplication, its licensing
and its traceability from a claim back to a span of a source — and it asks
that the parsers' actual behaviour be **verified**, not assumed.

---

## 1. What was built

| piece | where | what it is |
|---|---|---|
| the corpus | `matrix-core/src/main/resources/corpus/` | three files, one manifest |
| the manifest | `io.matrix.knowledge.acquisition.CorpusManifest` | the record of what each file is: the format, the language, the sha256, the licence |
| the import report | `io.matrix.knowledge.acquisition.CorpusImport` | what happened to each file: the licence, the segments, the claims, the trace, the warnings |
| the tests | `CorpusFoundationTest`, `DocumentFormatVerificationTest` | the verification, the idempotence and the measured parser table |

**The corpus is small and controlled.** Three files, 3,449 bytes in total,
every one of them written for this wave. There is no book in it, no
copyrighted text, no scraped material and no unbounded collection. The
mission's "no large uncontrolled corpus" is met by the size itself.

**Nothing is fitted to it.** The pipeline reads, extracts, links and
validates; the only thing it writes is the knowledge change. There is no
weight, no checkpoint and no training anywhere in the path — the corpus is
read, and reading is not learning.

## 2. The licensing — and why it is not in the database

The store's `source` row carries the content hash of a source, and it has
**no field for the licence of the text**. Adding one is a schema change, and
the schema is the owner's to change (the ADR's §4 already asks for a
decision on the schema-16 migration).

So the licence lives **beside the corpus**, in the manifest, and the import
**verifies it before it reads a byte of the text**:

- a listed file whose bytes differ from the manifest is **reported with both
  hashes and is not imported**;
- a file in the corpus directory the manifest does not list has **no
  recorded licence**, and that is reported too — an unlicenced file in a
  corpus is a finding, not a detail;
- a listed file that is not on disk is reported as missing.

## 3. The measured parser behaviour

`DocumentFormatVerificationTest` builds a well-formed sample of each format
and records what came out. The numbers below are the measured ones.

| format | segments | the location survives | the heading survives | the language is detected |
|---|---|---|---|---|
| TXT | 2 | yes | no | en |
| MARKDOWN | 2 | yes | **yes** | en |
| HTML | 2 | yes | **yes** | en |
| CSV | 1 | yes | yes | **no** |
| PDF | 1 | yes | no | en |
| EPUB | 1 | yes | **yes** | en |
| DOCX | 1 | yes | no | **no** |
| JSON | — | **refused** by the document pipeline — a knowledge package is imported through the package importer | | |

**The location survives in every format the pipeline reads** ✓ — that is the
mission's requirement, and it is met by measurement rather than by
assumption.

The heading survives in the four formats that carry one (Markdown, HTML,
CSV, EPUB) and does not in the flat ones (TXT, PDF, DOCX) ✓ — which is what
those formats contain ✓.

The language is detected in five of the seven, and **not** in CSV or DOCX ✓
— the detection runs over prose, and those two samples are structured
records. The consequence is recorded as **R52**: a source imported from a
CSV or a DOCX has no language in the corpus registry unless the caller
declares one.

## 4. What the samples are, and what they are not

The text formats are written by the test, so they are exactly what the test
says. The container formats are written by the test as well: a minimal PDF
(with a text layer, and no cross-reference table — PDFBox rebuilds one),
a minimal EPUB (a ZIP with a chapter) and a minimal DOCX (a ZIP with
`word/document.xml`).

**So this proves that the parser handles a WELL-FORMED file of the
format.** It does **not** prove that it handles the files of the wild: a
PDF with a broken xref, a scanned PDF with no text layer, an EPUB with
nested navigation, a DOCX with tracked changes. None of those is in the
corpus of this wave, and the document says so rather than implying a
coverage that does not exist.

## 5. The import, the report and the idempotence

`CorpusImport.run(workbench, workspace, corpusDirectory)` produces a report
that carries, for every file: the format, the licence, whether the bytes
matched the manifest, the source the import created, the segments and
claims it produced, the warnings the import raised, and the chain from each
claim back to the span of the file it came from.

**Measured:** three files imported, three licences recorded, the claims
traced to a span **3,449 bytes** of corpus — and the imported claims enter
as **CANDIDATE**. The import does not promote anything, and nothing it read
is a VERIFIED claim.

**The idempotence is measured, not asserted.** The source id is derived
from the workspace and the content hash, and the claim id from the
workspace, the hash and the proposition — so the second import of the same
corpus writes the **same** source and the **same** claims, and the counts
do not move ✓✓. The report says so too, rather than pretending it imported
something new ✓✓.

## 6. A defect this work found

**Importing a document into an empty workspace crashed.** The import calls
`generateAssessment`, which loads the packaged assessment dataset — and
that dataset names concepts of the photosynthesis package, which an empty
workspace does not have. The loader threw "the assessment dataset refers to
an unknown concept: Cellular respiration", and the import died with it.

The dataset is **shared between workspaces**, so a workspace that does not
hold a concept the dataset names is a normal state, not an error. The
loader now **leaves out the items it cannot anchor and says which ones**,
and the generation result carries them, so the omission is never silent ✓✓.

Recorded as **R51** (fixed).

## 7. The limits of this foundation

1. **The corpus is three files.** It proves the path works end to end; it
   says nothing about how the import behaves on a large or heterogeneous
   corpus.
2. **The licence is in the manifest, not in the store.** A source imported
   from outside the corpus has no licence recorded anywhere in the system —
   the caller must declare it, and there is nowhere to put it yet.
3. **The span and the claim's scope share a field.** The extraction stage
   records the location of a claim in the claim's `scope` column. The ADR's
   §4 proposes a structured scope; until the owner decides on that, one
   column carries two meanings, and the corpus report reads it as the span.
4. **The claim extraction is unchanged.** The corpus goes through the
   existing `ClaimExtractor`, and its proposals are what they were. This
   wave did not improve the extraction, and it does not claim to.
5. **No retrieval is measured here.** The corpus is imported and traced; the
   mission's §7 warns against claiming RAG without a measured
   retrieval-augmented answer path, and **this document claims no such
   path** ✓✓.

## 8. Reproducing

```bash
export JAVA_HOME=/home/alexandr-narbaev/jdk25; export PATH="$JAVA_HOME/bin:$PATH"

# the corpus foundation: the manifest, the import, the report, the idempotence
./gradlew :matrix-core:test --tests "io.matrix.knowledge.acquisition.CorpusFoundationTest" -i --rerun-tasks

# the measured parser table
./gradlew :matrix-core:test --tests "io.matrix.knowledge.acquisition.DocumentFormatVerificationTest" -i --rerun-tasks
```
