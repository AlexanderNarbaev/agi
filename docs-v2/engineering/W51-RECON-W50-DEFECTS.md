# W51 — RECON: the current state of the W50 epistemic layer

**Wave:** W51 §6 · **Date:** 2026-11-08 · **Branch:** `develop` ·
**HEAD:** `a098f283` ("WAL: W50 — the report records the pull request's CI job by job")
· **Machine:** AMD Ryzen 9 9955HX, JDK 25.0.4
· **Method:** read-only with respect to the repository. The only file this
wave writes is this report. The evaluation was re-run offline against the
packaged build that ships in the tree.

**One sentence.** The W50 epistemic layer has not changed since W50 was
measured: the same 29-case evaluation, re-run today against the same
packaged jar, gives **12 of 29 passes — the identical number, the identical
case-by-case verdicts and the identical defect signatures** — and **all eight
W50 defects are still present in the current code**, each with the file and
line that carries it.

---

## 1. The dataset

| item | value |
|---|---|
| **Path** | `docs-v2/demos/local-learning-loop/EVALUATION-SET.md` |
| **Version** | 1.1 · dated 2026-11-07 · 549 lines · 33 829 bytes |
| **Format** | Markdown, human-written; **not** machine-readable. Each case is a `####` section with `**Split**`, `**Scope**`, `**Family**`, a `**Call:**` line and a bullet list of `**Expectation:**` conditions. |
| **Size** | 29 cases: answerable 8 (A-01…A-06, P-01, P-02), unanswerable 6 (U-01…U-06), false premise 5 (F-01…F-05), multi-claim 3 (M-01…M-03), knows-what-it-does-not-know 2 (K-01, K-02), revision 5 (R-01…R-05). The index is §6 of the file. |
| **Not loaded by the application** | The file says so in its own header: "This file is **not** loaded into the application… The application never reads it." |

The companion answer key is `docs-v2/demos/local-learning-loop/ANSWER-KEY.md`
and the owner prompt deck is `OWNER-SEQUENCE.md`; both live in the same
folder.

The corpus the cases are measured against is the licensed plate-tectonics
material that ships with the application:
`matrix-core/src/main/resources/corpus/plate-tectonics/` (2 seed documents)
and `matrix-core/src/main/resources/research-pool/plate-tectonics/`
(7 pool documents), each with a `corpus-manifest.json` carrying the sha256,
the licence, the canonical URL, the revision and the access date.

---

## 2. The runner

### 2.1 There is no in-repo runner for the 29 cases

The W50 report describes its checker as "`/tmp/w50-eval/check.py`" and its
aggregator as "`/tmp/w50-eval/aggregate.py`" (`W50-EVALUATION.md:43-46`;
`W50-STATUS.md` §9). **Neither file was ever committed.** The evidence:

- `git log --all --diff-filter=A --name-only` finds no `w50-eval/`,
  `check.py` or `aggregate.py` anywhere in the history — the only
  `check.py` in the repository is `scripts/docs-link-check.py`, an unrelated
  documentation-link checker.
- The commit that added the evaluation (`e6fbe593`, "WAL: W50 — the
  evaluation dataset and the measured report of it") added **exactly two
  files**: `docs-v2/demos/local-learning-loop/EVALUATION-SET.md` and
  `docs-v2/engineering/W50-EVALUATION.md`.
- `/tmp/w50-eval/` no longer exists on this machine, so the original
  checker and its raw responses are **not recoverable**.

**Consequence for W51:** the dataset is a written specification, not a
benchmark file, and the harness that scored it is gone. Any re-measurement
has to be reconstructed. §3 below is that reconstruction.

### 2.2 What IS in the repository and runs offline

| piece | path | what it does |
|---|---|---|
| the application | `scripts/run-local.sh` → `matrix-core/build/quarkus-app/quarkus-run.jar` | starts the workbench on `127.0.0.1:9091` |
| the offline proof | `scripts/offline-answer-proof.sh` | replays the four demonstration turns against an instance whose HTTP/HTTPS clients point at a **closed proxy** (`127.0.0.1:9`), so no request can leave the machine |
| the learning surface | `matrix-core/src/main/java/io/matrix/knowledge/LearningCycleResource.java` | `GET /v1/learning/catalogue`, `POST /v1/learning/workspaces/{id}/seed`, `POST /v1/learning/workspaces/{id}/learn?question=&documents=`, `POST /v1/learning/workspaces/{id}/assess`, `POST /v1/learning/workspaces/{id}/revise?file=` |
| the asking surface | `matrix-core/src/main/java/io/matrix/knowledge/LearningWorkbenchResource.java:479` | `POST /v1/workbench/workspaces/{id}/ask` with `{"question":…,"level":…}` |

The answering path makes **no LLM call and no network call**: the W50
deterministic acquisition measured 0 teacher invocations
(`W50-STATUS.md` §4), and `matrix-core/src/test/java/io/matrix/knowledge/OfflineAnswerPathTest.java`
scans the compiled epistemic core for any network or model-runtime
reference.

---

## 3. The measurement I ran (2026-11-08)

### 3.1 How it was run — offline, against the packaged jar

```
# the build that ships in the tree; its last Java commit is 8e03e620 (20:43),
# the jar was packaged at 20:44 the same day, so it is current for `develop`
QUARKUS_HTTP_PORT=9099 MATRIX_KNOWLEDGE_PATH=/tmp/w51-eval/knowledge.sqlite \
java -Dhttp.proxyHost=127.0.0.1  -Dhttp.proxyPort=9 \
     -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=9 \
     -Dhttp.nonProxyHosts=  -Dhttps.nonProxyHosts= \
     -jar matrix-core/build/quarkus-app/quarkus-run.jar
```

Every JVM HTTP(S) client is pointed at a closed port, exactly as
`scripts/offline-answer-proof.sh` does. **No request left the machine.**

Then, over HTTP, the sequence of §2.2 of the dataset was executed:

1. `POST /v1/learning/workspaces/plate-tectonics-eval-seed/seed` → 2 documents, **348 claims**
2. the 15 S1 cases (11 asks + 4 assesses) on that workspace
3. `seed` + `learn?question=<the demonstration's gap question>&documents=3` on `plate-tectonics-eval` → **672 claims**, 5 sources
4. the 5 P1 cases (4 asks + 1 assess)
5. `revise?file=wikipedia-slab-pull.md` → 14 claims added, 1 supersession; then the R-03 answer
6. `revise?file=wikipedia-paleomagnetism.md` → 65 claims added, 4 supersessions
7. `seed` + `learn` + `revise?file=wikipedia-seafloor-spreading.md` on a fresh copy of P1 → 92 claims added, 5 supersessions
8. `seed` + `learn` + `revise?file=wikipedia-mantle-convection.md` on a second fresh copy of P1 → 45 claims added, 2 supersessions

The checker was rewritten from §3 of the dataset (the pass conditions) into
`/tmp/w51-eval/check.py`; the driver is `/tmp/w51-eval/drive.py`; every raw
response is under `/tmp/w51-eval/captures/` (61 files); the verdict file is
`/tmp/w51-eval/results.json`.

**Honest note on provenance.** The checker is a reconstruction, not the
original `check.py`. Its conditions are the dataset's own words, applied
literally:

- *answerable* — `answered == true`; the named source file among
  `sources[].title`; every named proposition present **verbatim** in
  `claims[].proposition`; at least the required number of distinct claims.
- *unanswerable* — `answered == false` **and** `statement` empty **and**
  `sources` empty (the strict criterion the dataset states in §3).
- *false premise* — the verdict equals the named one; the disputed premise
  named; the contradicting claim and its source named; `promoted == false`;
  the claim count unchanged across the check.
- *multi-claim* — as answerable, plus the required distinct claim and
  source counts.
- *knows what it does not know* — answered; the named claim cited; the gap
  list names the named term.
- *revision* — the counts of the report, the statuses and versions of the
  claims before and after, and the reason the application gives.

The reconstruction reproduces W50's per-case outcome on **all 29 cases**,
including the exact claim counts, source counts and coverage values in the
W50 report (A-01: 41 claims / 2 documents / coverage 0.360; P-01: 63
claims / 5 documents; P-02: 104 claims / 5 documents; M-02: 86 claims;
R-03: 128 claims / 7 source records; the seed 348 claims; the pool 672).
That agreement is the evidence that the reconstruction is faithful.

### 3.2 The result: 12 of 29 pass, 17 fail

| case | family | verdict | expected | observed |
|---|---|---|---|---|
| A-01 | answerable | **PASS** | answered; ≥2 claims; `wikipedia-plate-tectonics.md`; 2 propositions verbatim | answered; 41 claims; 2 sources; 2/2 propositions cited |
| A-02 | answerable | **FAIL** | answered; ≥3 claims; `wikipedia-plate-tectonics.md`; 3 propositions | answered; 18 claims; 2 sources; 2/3 — "Convergent boundaries … subduction zone …" absent |
| A-03 | answerable | **PASS** | answered; ≥3 claims; 3 propositions | answered; 30 claims; 2 sources; 3/3 cited |
| A-04 | answerable | **FAIL** | answered; ≥3 claims; `usgs-this-dynamic-earth.md`; 3 propositions | answered; 9 claims; 1 source; 2/3 — "However, because plate motions are global in scale …" absent |
| A-05 | answerable | **FAIL** | answered; ≥4 claims; ≥2 sources; 4 propositions | answered; 16 claims; 2 sources; 2/4 — "Oceanic crust is formed at sea-floor spreading centers." and the magnetic-stripe sentence absent |
| A-06 | answerable | **FAIL** | answered; ≥3 claims; `usgs-this-dynamic-earth.md`; 3 propositions | answered; 7 claims; **1 source (`wikipedia-plate-tectonics.md`)**; 0/3 cited |
| P-01 | answerable | **FAIL** | answered; ≥3 claims; `wikipedia-mid-ocean-ridge.md`; 3 propositions | answered; 63 claims; 5 sources; **0/3** cited |
| P-02 | answerable | **FAIL** | answered; ≥3 claims; `wikipedia-subduction.md`; 3 propositions | answered; 104 claims; 5 sources; **0/3** cited |
| U-01 | unanswerable | **FAIL** | abstention (no answer, empty statement, no source) | **answered**; 2 claims, 1 source; gap names boiling/kilimanjaro/celsius |
| U-02 | unanswerable | **PASS** | abstention | abstained; 0 claims, 0 sources; gap names annual/tourism/revenue/euros |
| U-03 | unanswerable | **FAIL** | abstention | abstained, but **3 claims and 1 source still attached**; gap names probability/magnitude/istanbul |
| U-04 | unanswerable | **FAIL** | abstention | **answered**; 4 claims, 2 sources; gap names tonnes |
| U-05 | unanswerable | **PASS** | abstention | abstained; 0 claims, 0 sources |
| U-06 | unanswerable | **FAIL** | abstention | **answered**; 5 claims, 3 sources; gap names vessels |
| F-01 | false premise | **PASS** | SUPPORTED; claim+source named; `promoted=false`; 348→348 claims | SUPPORTED; 5 evidence refs; `promoted=false`; 348→348 |
| F-02 | false premise | **FAIL** | CONTRADICTED | **UNRESOLVED** (5 differently-worded denials that do not settle each other); disputed premise named; the USGS claim named; `promoted=false`; 348→348 |
| F-03 | false premise | **PASS** | UNSUPPORTED; no evidence; `promoted=false` | UNSUPPORTED; 0 evidence refs; reason says absence of evidence is not a contradiction; `promoted=false` |
| F-04 | false premise | **PASS** | UNRESOLVED; both denials named; `promoted=false` | UNRESOLVED; 5 CONTRADICTS refs; `promoted=false`; 348→348 |
| F-05 | false premise | **PASS** | CONTRADICTED; the continental-drift claim named; `promoted=false`; 672→672 | CONTRADICTED; 2 evidence refs; `promoted=false`; 672→672 |
| M-01 | multi-claim | **FAIL** | ≥3 claims; `wikipedia-plate-tectonics.md`; 3 propositions | answered; 18 claims; 2 sources; **1/3** cited |
| M-02 | multi-claim | **PASS** | ≥3 claims; 3 propositions | answered; 86 claims; 2 sources; 3/3 cited |
| M-03 | multi-claim | **FAIL** | ≥6 claims; ≥3 sources; 5 propositions | answered; 25 claims; 4 sources; **0/5** cited |
| K-01 | knows-what-it-does-not-know | **PASS** | answered; the boundary-types claim cited; gap names "tsunami" | answered; named claim cited; gap = {tsunami} |
| K-02 | knows-what-it-does-not-know | **PASS** | answered; one of the two named claims cited; gap names "tsunami" | answered; named claim cited; gap = {tsunamis} |
| R-01 | revision | **FAIL** | ≥1 supersession; **the named claim** SUPERSEDED v2; the new claim SUPPORTED v2; the reason names the denial | 14 claims added, 14 decisions, **1 supersession**, relatedness 0.83, reason names the denial — but the superseded claim is `clm-fd907c47` ("In this understanding, plate motion is mostly driven by the weight of cold, dense plates sinking into the mantle at trenches."), not the claim the dataset names; the new claim `cl-899acaa0` is SUPPORTED v2 |
| R-02 | revision | **FAIL** | **no** supersession; no claim changes status | 65 claims added, 65 decisions, **4 supersessions**; 3 claims changed: `clm-25f4cee7` CANDIDATE v1 → SUPERSEDED v5, `clm-61e75bc7` CANDIDATE v1 → SUPERSEDED v1, `cl-899acaa0` SUPPORTED v2 → VERIFIED v2 |
| R-03 | revision (answer after R-01) | **PASS** | answered; ≥5 claims; ≥5 sources; both slab-pull propositions | answered; 128 claims; 7 sources; 2/2 cited |
| R-04 | revision | **FAIL** | **no** supersession | 92 claims added, 92 decisions, **5 supersessions**; 2 claims changed: `clm-78991d7b` CANDIDATE v1 → SUPERSEDED v6, `clm-b0f96c3f` CANDIDATE v1 → SUPERSEDED v1 |
| R-05 | revision | **FAIL** | **no** supersession | 45 claims added, 45 decisions, **2 supersessions**; 1 claim changed: `clm-d02a0f3` CANDIDATE v1 → SUPERSEDED v3 |

**Totals — measured today vs. the W50 report of 2026-11-07:**

| | W50 report | W51 re-measurement (2026-11-08) |
|---|---|---|
| cases passed | 12 / 29 | **12 / 29** |
| cases failed | 17 / 29 | **17 / 29** |
| answerable correct | 2 / 8 | 2 / 8 |
| answerable answered-but-wrong | 6 / 8 | 6 / 8 |
| answerable missed | 0 / 8 | 0 / 8 |
| unanswerable correct abstentions | 3 / 6 (2 / 6 under the strict criterion) | 3 / 6 (2 / 6 strict) |
| unanswerable invented answers | 3 / 6 | 3 / 6 |
| false-premise verdict matched | 3 / 5 | 3 / 5 |
| false-premise assertions promoted | 0 / 5 | 0 / 5 |
| multi-claim fully answered | 1 / 3 | 1 / 3 |
| revision attempts that applied a supersession | 4 / 4 (0 / 3 "must not revise" held) | 4 / 4 (0 / 3 held) |
| cited claims carrying a source identity | 689 / 689 | 571 / 571 |
| cited claims whose **source** carries an exact location | 0 / 689 | **0 / 571** |

(The citation counts differ only because this re-run asked 18 questions
that cited a claim, against 19 in the W50 run; the per-case claim counts
match one for one.)

### 3.3 Two additional measurements the W50 report did not record

- **The claim row carries a segment locator; the source row does not.**
  571 of 571 cited claims carry a `context` of the form
  `wikipedia-plate-tectonics.md: "Plate tectonics" (line 1)` — the file,
  the heading and the **starting line of the segment** the sentence came
  from. What is missing is (a) any byte/character offset of the sentence
  itself and (b) any locator on the **source** record, which is still
  `the whole document` for every one of the 37 source records.
  The W50 report's sentence "Nothing in the store names a line, an offset,
  a section or a byte position inside the document" (`W50-EVALUATION.md:325-326`)
  is therefore **too strong for the claim row**; it holds for the source row.
  Q-W50-02 stays open, but it should be re-stated as "the source locator is
  absent and the claim locator is segment-level, not span-level".
- **The status route is still empty for a learning workspace.**
  After seeding 348 claims into `plate-tectonics-eval-seed` (and 672 / 756
  claims into the other workspaces), `GET /v1/workbench/status` returned
  `"workspaces": []` and `"counts": {}` — Q-W50-03 reproduced exactly, and
  its cause is identified in §4 (D5).

---

## 4. The eight W50 defects — verdicts on the current code

**Summary: 0 fixed, 8 still present.** Every verdict below is backed by the
current source and, where the behaviour is observable, by the measurement of
§3.

| # | defect | verdict | evidence |
|---|---|---|---|
| **D1** | Candidate claims can enter answers without adequate status filtering | **STILL PRESENT** | `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeRetriever.java:340` iterates `snapshot.claims()` with **no status condition**; the filter at `:386-388` is `coverage >= minimumCoverage` only; `:469-470` puts every such claim into the answer's evidence. The only status-aware code in the class is `:569-575`, which adds a *limitation line* for `CONTESTED`/`SUPPORTED` and nothing for `CANDIDATE`/`SUPERSEDED`/`REFUTED`. `Claim.isEstablished()` exists (`Claim.java:61-64`) and is **never called** anywhere in `src/main` (verified by grep). **Measured:** 569 of 571 cited claims are `CANDIDATE` with `confidence = 0.0`; the R-03 answer cites `clm-fd907c47` with status `SUPERSEDED` v2 — a claim the model had explicitly withdrawn — as evidence for "What drives the motion of the tectonic plates?". |
| **D2** | Retrieval can return irrelevant / insufficiently supported claims | **STILL PRESENT** | `KnowledgeRetriever.java:339-388` ranks claims by IDF-weighted lexical overlap alone (`TextFeatures.idf`, `:158`); `Claim.confidence` is never consulted for eligibility — it is only averaged into the aggregate score at `:553`. The floor is `DEFAULT_MINIMUM_COVERAGE = 0.25` (`:36`), a coverage floor, not a support floor. **Measured:** P-01 answered from 63 claims of 5 documents with **0 of 3** required propositions cited; P-02 from 104 claims of 5 documents, **0 of 3**; A-06 from 7 claims of the **wrong document** (Wikipedia instead of USGS), 0 of 3. |
| **D3** | The system can answer where it should abstain | **STILL PRESENT** | `matrix-core/src/main/java/io/matrix/knowledge/QuestionDemand.java:102` — `public static final double MINIMUM_DEMAND_COVERAGE = 0.0;`, and its own comment says the demand-coverage refusal threshold "was tried as a refusal threshold and REMOVED". `KnowledgeWorkbench.java:634-637` (`applyDemand`) therefore returns a KNOWLEDGE answer **unchanged**. The only gate left is `KnowledgeRetriever.java:424-434` — "at least one claim with coverage ≥ 0.25". `KnowledgeRetriever.java:576-578` reports the uncovered terms as a *limitation*, not as a refusal. **Measured:** U-01, U-04 and U-06 were answered where the dataset expects abstention (today's `evidenceCoverage`: U-01 **0.268**, U-04 **0.288**, U-06 **0.319** — bit-for-bit the W50 values, all above the 0.25 floor). |
| **D4** | Source provenance lacks precise offsets | **STILL PRESENT** (at source level) | `matrix-core/src/main/java/io/matrix/knowledge/acquisition/SourceRecord.java:65` — `private String location = "the whole document";` is the default and **nothing ever overrides it**: the only `.location(` call in the package is the builder setter (`SourceRecord.java:99`). `acquisition/DocumentImporter.java:193-196` writes that value into `Source.locator`. The per-segment location that *does* exist (`acquisition/TextDocumentExtractor.java:153-161`, `filename + ": \"" + heading + "\" (line " + line + ")"`) is stored on the **claim**'s `context` (`DocumentImporter.java:227-231`), never on the source. **Measured:** 0 of 571 cited claims have a source with an exact locator; the locator is `the whole document` for all 38 source records in the run. A second, separate defect in the same area: `acquisition/CorpusImport.java:157-158` reports the claim's **`scope`** (the subject phrase) as the claim's "span", not the location — so the import report's span column is wrong even where a location exists. |
| **D5** | Status counts can be empty or incorrect | **STILL PRESENT** | `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeWorkbench.java:2107-2136` (`status()`) builds its `counts` map by iterating `workspaces()`; `KnowledgeStore.java:4685-4696` (`workspaces()`) is `SELECT id FROM workspace`. The learning path never writes that table: `KnowledgeStore.applyChanges` (`:2428-2503`) inserts concepts, sources, claims, evidence, representations and revisions — **never a `workspace` row**. The only writer of the table is `insertWorkspace(KnowledgeSnapshot)` (`:2094-2102`), which the package-import path uses. **Measured:** with six learning workspaces holding 4 080 claims in total (348 / 756 / 769 / 769 / 719 / 719), the `workspace` table was **empty** (`SELECT id FROM workspace` → 0 rows) and `GET /v1/workbench/status` returned `"workspaces": []`, `"counts": {}`. And there is **no per-status breakdown anywhere**: no method in `io.matrix.knowledge` counts claims by `ClaimStatus` (grep for `ClaimStatus.values()` / `byStatus` → no hits), so "status counts" are not merely empty, they do not exist. |
| **D6** | Source records can be rewritten (mutated in place) | **STILL PRESENT** | `KnowledgeStore.upsertSource` (`:2525-2543`) is `INSERT OR REPLACE INTO source(...)`. The refinement's source id is derived from the **locator**, not from the document: `KnowledgeRefiner.java:126-133` → `Ids.source(workspaceId, kind, locator)`; `Ids.java:33-35` → `"s-" + slug(kind) + "-" + digest(workspaceId\|kind\|locator)`. `ModelRevisionDecider.java:206` passes `SourceKind.PUBLICATION, newSource.title(), newSource.locator()` — and the locator of an imported corpus document is the constant `"the whole document"`, so **every refinement in a workspace derives the same source id**. **Measured:** between R1 and R2 the record `s-publication-66bb6e0c` — *the same identifier as in the W50 report* — was rewritten in place: title `wikipedia-slab-pull.md` → `wikipedia-paleomagnetism.md`, locator unchanged. Five claims point at that record in the R2 state, including `cl-899acaa0`, the claim R-01 produced, which now names the wrong document. |
| **D7** | Revision decisions can supersede claims incorrectly | **STILL PRESENT** | `ModelRevisionDecider.inForce` (`:123-128`) treats a `CANDIDATE` claim as "in force" and therefore supersedable. `decide` (`:52-109`) picks the claim with the **maximum relatedness** (`:78-83`) — not the claim the new evidence actually denies — and the denial branch **short-circuits the strength comparison** (`:90`: `if (!denies && newStrength <= bestStrength)`). `ModelRevisionDecider.refinement` (`:196-212`) then marks the new claim `SUPPORTED` regardless of the old one. **Measured:** R-01 superseded `clm-fd907c47`, not the claim the dataset named; R-02/R-04/R-05 applied **4 / 5 / 2** supersessions where the dataset expects **0**, and R-02 changed three claims — including a `CANDIDATE → SUPERSEDED` at version 5 and a `SUPPORTED v2 → VERIFIED v2` promotion triggered by a document that was supposed to change nothing. |
| **D8** | Lexical polarity / relatedness heuristics are used beyond what they can establish | **STILL PRESENT** | The revision decider's polarity is a **substring list**: `ModelRevisionDecider.denies` (`:168-179`) against `DENIAL_MARKERS` (`:182-185`, e.g. `"is not"`, `"rather than"`, `"not directly"`). Its relatedness is term overlap (`:251-259`) plus substring containment in the new source's excerpt (`:143-155`). The premise check's polarity is the same kind of instrument: `PremiseAssessment.denied` (`:1116-1139`) against `DENIAL_MARKERS` (`:199-208`) and `RESTRICTION_MARKERS` (`:221-222`), and `sameAssertion` (`:570-578`) is stem-set containment. `PremiseAssessment.read` (`:426-459`) turns a marker hit into a `CONTRADICTS` relation, and `agreeOn` (`:592-603`) then decides `UNRESOLVED` when the contradicting claims are worded differently. Both classes **document** the limit (the `PremiseAssessment` javadoc: "the subject matching, the polarity reading and the verdict rules are lexical rules that a reader can check by hand"; `W50-STATUS.md` §6: "The premise check is **lexical**") and then use it anyway to emit verdicts and to supersede claims. **Measured:** F-02 came back UNRESOLVED where the dataset expects CONTRADICTED, because five differently-worded denials do not settle each other; and in R-02/R-04/R-05 a lexical marker ("is not", "rather than") in a pool document was enough to make the whole document a revision against evidence that was **no stronger** (0.055 against 0.055). |

### 4.1 What the eight verdicts have in common

They are not eight independent bugs. They are three missing gates in one
pipeline:

1. **No epistemic gate on the answering path.** A claim enters an answer on
   lexical overlap alone (D1, D2), and the answer is given whenever one
   claim clears a 0.25 coverage floor (D3). Nothing between "the retriever
   ranked it" and "the answer cites it" asks whether the claim is still in
   force, whether it is supported, or whether the question's *demand* is
   covered.
2. **No identity gate on the provenance.** The source record is keyed by a
   constant string (D6) and carries no location (D4), so two different
   documents are the same row and the row cannot say where it came from.
3. **No semantic gate on the revision and the premise check.** Lexical
   polarity and lexical relatedness decide that one proposition denies
   another (D8), and that denial then supersedes a claim (D7). The classes
   are honest about being lexical; they are not honest about the
   consequence.

---

## 5. The class map

All paths are relative to `matrix-core/src/main/java/io/matrix/knowledge/`.

### 5.1 Claim representation and the status enum

| class | path | main signatures |
|---|---|---|
| `Claim` (record) | `Claim.java` | `Claim(String id, String workspaceId, String conceptId, String proposition, String scope, String context, ClaimStatus status, double confidence, int version, String introducedBySourceId, String extractionMethod, String validationMethod, ClaimWarrant warrant, DerivationStatus derivationStatus, String createdAt, String updatedAt)`; `boolean isEstablished()` (`:61`) — **never called** |
| `ClaimStatus` (enum) | `ClaimStatus.java` | `DRAFT, CANDIDATE, SUPPORTED, VERIFIED, CONTESTED, SUPERSEDED, REFUTED, UNKNOWN, OBSERVED, HYPOTHESIS, INSUFFICIENT_EVIDENCE, REFINED` (`:33-65`) |
| `ClaimWarrant`, `DerivationStatus` | `ClaimWarrant.java`, `DerivationStatus.java` | the other two epistemic axes of a claim |
| `KnowledgeSnapshot` (record) | `KnowledgeSnapshot.java:42-56` | the read model the retriever sees: `workspaceId`, `workspaceName`, `concepts()`, `sources()`, `claims()`, `evidence()`, `representations()`, `prerequisites()`, `revisions()`, `derivations()`, `contradictions()`, `goals()` |

### 5.2 Evidence and provenance records

| class | path | main signatures |
|---|---|---|
| `Evidence` (record) | `Evidence.java` | `Evidence(String id, String workspaceId, String claimId, String sourceId, EvidenceRelation relation, String excerpt, int quality, String recordedAt)` |
| `EvidenceRelation` (enum) | `EvidenceRelation.java` | `SUPPORTS, CONTRADICTS, EXEMPLIFIES, QUALIFIES, CONTEXTUAL, INSUFFICIENT, FAILS_TO_SUPPORT` |
| `Source` (record) | `Source.java` | `Source(String id, String workspaceId, SourceKind kind, String title, String locator, int authority, int freshness, String excerpt, String addedAt, String contentHash, String publishedAt, String language, String format)` |
| `SourceKind` (enum) | `SourceKind.java:10-24` | `AUTHORITATIVE_REFERENCE, PUBLICATION, OBSERVATION, EXPERIMENT, DATASET, SECONDARY_REPORT, MATHEMATICAL_DERIVATION, HUMAN_ASSERTION` |
| `SourceRecord` (record) | `acquisition/SourceRecord.java` | `SourceRecord(String sourceId, String filename, String contentHash, DocumentFormat format, String language, String author, String publicationDate, String importedAt, String location, String extractedStructure, List<String> pageReferences, double extractionQuality, List<String> processingHistory)`; `Builder.location(String)` (`:99`) — **never called**; the default `location = "the whole document"` (`:65`) |
| `ExtractedDocument.Segment` | `acquisition/ExtractedDocument.java:29` | `Segment(String id, String text, String location, int order, String heading, String language)` — the only record that names a line |

### 5.3 Retrieval and scoring

| class | path | main signatures |
|---|---|---|
| `KnowledgeRetriever` | `KnowledgeRetriever.java` | `KnowledgeRetriever(KnowledgeSnapshot)`; `KnowledgeRetriever(KnowledgeSnapshot, double minimumCoverage)`; `KnowledgeRetriever(KnowledgeSnapshot, double, Map<String,float[]> weakenedBindings)`; `Answer answer(String question)` (`:162`); `Answer answer(String question, AbstractionLevel level)` (`:167`); `Set<String> unmatchedTerms(String)` (`:713`); `double DEFAULT_MINIMUM_COVERAGE = 0.25` (`:36`) |
| `TextFeatures` | `TextFeatures.java` | tokenisation, stop-words, `idf(List<Set<String>>)`, `originalTokens`, `foldIn` |
| `TextFeatureVector` | `TextFeatureVector.java` | the feature vector used by the supervised learner |
| `KnowledgeGapDetector` | `KnowledgeGapDetector.java` | `uncoveredTerms(String question)` — the gap list the answer reports |
| `BilingualLexicon`, `EnglishLemma`, `TypoCorrector` | `BilingualLexicon.java`, `EnglishLemma.java`, `TypoCorrector.java` | the recall-widening channels; all **search** terms, never evidence |

### 5.4 Answering

| class | path | main signatures |
|---|---|---|
| `KnowledgeWorkbench` | `KnowledgeWorkbench.java` | `Answer ask(String workspaceId, String question, AbstractionLevel level)` (`:393`); `private Answer applyDemand(Answer, QuestionDemand.Analysis)` (`:634`); `private Answer applyPremise(Answer, String, KnowledgeSnapshot)` (`:613`); `Map<String,Object> status()` (`:2107`); `Map<String,Object> integrity()` (`:2146`); `KnowledgeSnapshot workspace(String)` (`:301`); `List<String> workspaces()` (`:296`) |
| `QuestionDemand` | `QuestionDemand.java` | `static Analysis analyse(String, Set<String> unmatched, Set<String> contentTerms)` (`:114`); `static Analysis analyse(…, boolean referentSettled)` (`:139`); `static double coverage(Set,Set)` (`:178`); `double MINIMUM_DEMAND_COVERAGE = 0.0` (`:102`) |
| `Answer` (record) | `Answer.java` | `Answer(String question, AbstractionLevel level, boolean answered, String abstentionReason, String statement, double supportScore, SupportScore support, List<Concept>, List<Claim>, List<Source>, List<String> limitations, List<String> gaps, List<String> unmatchedTerms)`; `boolean abstained()` (`:49`) |
| `SupportScore` (record) | `SupportScore.java` | the decomposed score; `ContradictionStatus.NOT_COMPUTED` on every imported claim |
| `LearningWorkbenchResource` | `LearningWorkbenchResource.java` | `Response ask(String workspaceId, AskRequest request)` (`:480`); `List<Claim> claims(String)` (`:444`); `List<Source> sources(String)` (`:451`); `List<Evidence> evidence(String)` (`:458`) |
| `PremiseChecker` | `PremiseChecker.java` | the premise of a *question* (`premiseOf`, `refutation`) — distinct from `PremiseAssessment` |

### 5.5 Revision and supersession

| class | path | main signatures |
|---|---|---|
| `ModelRevisionDecider` | `ModelRevisionDecider.java` | `static Decision decide(KnowledgeSnapshot, String conceptId, String newProposition, double newConfidence, Source newSource)` (`:52`); `static KnowledgeRefiner.Refinement refinement(Decision, KnowledgeSnapshot, Claim, Source)` (`:196`); `static boolean denies(String proposition)` (`:168`); `static double relatedness(Set<String>, Set<String>)` (`:251`); `static double bridge(Set<String>, String newText)` (`:143`); `static double strength(double, List<Source>, Source)` (`:225`); `double MINIMUM_RELATEDNESS = 0.30` (`:32`) |
| `KnowledgeRefiner` | `KnowledgeRefiner.java` | `KnowledgeChanges refine(String workspaceId, KnowledgeSnapshot snapshot, Refinement request)` (`:100`); `record Refinement(...)` (`:63`) |
| `KnowledgeRevision`, `RevisionKind` | `KnowledgeRevision.java`, `RevisionKind.java` | the revision record and its kinds (`SUPERSESSION`, …) |
| `KnowledgeChanges` | `KnowledgeChanges.java` | the unit of write: sources, claims, evidence, representations, revisions |
| `AcquisitionCycle` | `acquisition/AcquisitionCycle.java` | `static Report run(KnowledgeWorkbench, String workspaceId, String question, int maximumDocuments)` (`:74`); `static Report loadSeed(KnowledgeWorkbench, String workspaceId)` (`:133`); `static RevisionReport acquireAndRevise(KnowledgeWorkbench, String workspaceId, String file)` (`:174`); `String BOUNDARY` (`:45`) — "no request leaves the machine" |

### 5.6 Premise assessment (the false-premise path)

| class | path | main signatures |
|---|---|---|
| `PremiseAssessment` | `PremiseAssessment.java` | `static Assessment assess(KnowledgeWorkbench, String workspaceId, String assertion)` (`:265`); `private static Finding assessPremise(String, KnowledgeRetriever, KnowledgeSnapshot)` (`:299`); `private static Reading read(String premise, Claim claim)` (`:426`); `private static boolean denied(String)` (`:1116`); `MINIMUM_SUBJECT_OVERLAP = 0.30` (`:168`), `MINIMUM_SHARED_TERMS = 2` (`:171`), `MINIMUM_DENIAL_COVERAGE = 0.50` (`:181`), `MAXIMUM_EVIDENCE = 5` (`:157`) |
| `LearningCycleResource` | `LearningCycleResource.java` | `Response assess(String workspaceId, AssertionRequest)` (`:141`); `Response seed(...)` (`:83`); `Response learn(...)` (`:101`); `Response revise(String, String file)` (`:168`) |

### 5.7 Status counting and source storage

| class | path | main signatures |
|---|---|---|
| `KnowledgeStore` | `KnowledgeStore.java` | `void applyChanges(String workspaceId, KnowledgeChanges changes, List<Concept> concepts)` (`:2428`) — the transactional write; `private void upsertSource(Source)` (`:2525`, `INSERT OR REPLACE`); `private void upsertClaim(Claim)` (`:2547`); `List<String> workspaces()` (`:4685`, `SELECT id FROM workspace`); `List<Claim> claims(String)` (`:3222`); `List<Source> sources(String)` (`:3210`); `int outcomeCount(String)` (`:3195`); `int schemaVersion()` (`:450`); `int SCHEMA_VERSION = 19` (`:92`) |
| `KnowledgeWorkbench.status()` | `KnowledgeWorkbench.java:2107` | the status route's payload: `databasePath`, `exists`, `sizeBytes`, `schemaVersion`, `workspaces`, `counts`, `integrity`, `ok` |
| `DocumentImporter` | `acquisition/DocumentImporter.java` | `ImportResult importDocument(String workspaceId, Path path, String filename, String declaredLanguage)` (`:133`); `static String sourceId(String, String contentHash)` (`:312`); `static String claimId(String, String, String)` (`:332`) |
| `CorpusImport` | `acquisition/CorpusImport.java` | `static Report run(KnowledgeWorkbench, String workspaceId, Path corpusDirectory)` (`:105`); `private static List<ClaimTrace> traces(...)` (`:150`) — reports `claim.scope()` as the span |
| `ClaimValidator`, `PromotionPolicy` | `acquisition/ClaimValidator.java`, `acquisition/PromotionPolicy.java` | the validation stage of the import pipeline |

---

## 6. The database schema

There is **no `.sql` file** in the repository: the schema is created in Java,
in `KnowledgeStore.java`, by `createSchemaV1()` (`:479-584`) plus the
migration chain in `migrateFrom(int)` (`:911-1370`), which runs every
version from 0 to `SCHEMA_VERSION = 19`.

The authoritative shape was dumped from a database the current code
created: `/tmp/w51-eval/knowledge.sqlite`, `schema_meta.version = 19`,
**27 tables**.

### 6.1 The epistemic core (the tables the W50 workbench uses)

**`workspace`** — `id TEXT PK`, `name TEXT NOT NULL`, `created_at TEXT NOT NULL`, `version INTEGER NOT NULL`
> Written **only** by the package-import path (`insertWorkspace`,
> `KnowledgeStore.java:2094`). The learning path never writes it — which is
> why the status route reports no workspaces (D5).

**`concept`** — `id TEXT PK`, `workspace_id TEXT NOT NULL`, `name TEXT NOT NULL`, `definition TEXT`, `domain TEXT`, `created_at TEXT NOT NULL`

**`source`** — `id TEXT PK`, `workspace_id TEXT NOT NULL`, `kind TEXT NOT NULL`, `title TEXT NOT NULL`, `locator TEXT`, `authority INTEGER NOT NULL`, `freshness INTEGER NOT NULL`, `excerpt TEXT`, `added_at TEXT NOT NULL`, `content_hash TEXT`, `published_at TEXT`, `language TEXT`, `format TEXT`
> `locator` is the column the W50 provenance measurement reads; for every
> imported document it holds the constant `the whole document`. `language`
> and `format` were added by migration v13 (`KnowledgeStore.java:1108-1111`);
> `content_hash` and `published_at` by v5 (`:1304-1305`).

**`claim`** — `id TEXT PK`, `workspace_id TEXT NOT NULL`, `concept_id TEXT NOT NULL`, `proposition TEXT NOT NULL`, `scope TEXT`, `context TEXT`, `status TEXT NOT NULL`, `confidence REAL NOT NULL`, `version INTEGER NOT NULL`, `introduced_by_source_id TEXT`, `created_at TEXT NOT NULL`, `updated_at TEXT NOT NULL`, `extraction_method TEXT`, `validation_method TEXT`, `warrant TEXT`, `derivation_status TEXT`
> `scope` holds the claim's **subject phrase** and `context` holds the
> **segment locator** (file + heading + starting line) for imported claims
> (`DocumentImporter.java:227-231`). `warrant` and `derivation_status` were
> added by migration v7 (`:1274-1275`); `extraction_method` and
> `validation_method` by v5 (`:1306-1307`).

**`evidence`** — `id TEXT PK`, `workspace_id TEXT NOT NULL`, `claim_id TEXT NOT NULL`, `source_id TEXT NOT NULL`, `relation TEXT NOT NULL`, `excerpt TEXT`, `quality INTEGER NOT NULL`, `recorded_at TEXT NOT NULL`, `case_id TEXT`

**`representation`** — `id TEXT PK`, `workspace_id TEXT NOT NULL`, `concept_id TEXT NOT NULL`, `level TEXT NOT NULL`, `audience TEXT`, `statement TEXT NOT NULL`, `scope TEXT`, `assumptions TEXT NOT NULL`, `included_facts TEXT NOT NULL`, `omitted TEXT NOT NULL`, `validity_conditions TEXT NOT NULL`, `prerequisites TEXT NOT NULL`, `refines TEXT`, `confidence REAL NOT NULL`, `created_at TEXT NOT NULL`

**`prerequisite`** — `workspace_id TEXT PK`, `concept_id TEXT PK`, `requires_concept_id TEXT PK`, `required INTEGER NOT NULL`, `note TEXT`

**`revision`** — `id TEXT PK`, `workspace_id TEXT NOT NULL`, `target_type TEXT NOT NULL`, `target_id TEXT NOT NULL`, `kind TEXT NOT NULL`, `summary TEXT NOT NULL`, `added_facts TEXT NOT NULL`, `still_valid TEXT NOT NULL`, `no_longer_valid TEXT NOT NULL`, `newly_explained TEXT NOT NULL`, `evidence_ids TEXT NOT NULL`, `recorded_at TEXT NOT NULL`

**`asked_question`** — `id TEXT PK`, `workspace_id TEXT NOT NULL`, `canonical_key TEXT NOT NULL`, `question TEXT NOT NULL`, `uncovered_terms TEXT NOT NULL`, `times_asked INTEGER NOT NULL`, `first_asked_at INTEGER NOT NULL`, `last_asked_at INTEGER NOT NULL`, `status TEXT NOT NULL`, `resolution_note TEXT`, `answered INTEGER NOT NULL`, `forgotten_at INTEGER`, `forgotten_kind TEXT`, `forgotten_reason TEXT`

**`schema_meta`** — `key TEXT PK`, `value TEXT NOT NULL` (holds `version`)

### 6.2 The rest of the schema (the learning and micro-world tables)

| table | columns |
|---|---|
| `assessment_item` | `id PK`, `workspace_id`, `concept_id`, `dimension`, `prompt`, `key_terms`, `difficulty`, `variant`, `critical`, `held_out`, `recorded_at` |
| `assessment_outcome` | `id PK`, `workspace_id`, `item_id`, `concept_id`, `dimension`, `correct`, `score`, `claimed_confidence`, `learner_answer`, `session_id`, `answered_at`, `held_out` |
| `contradiction` | `id PK`, `workspace_id`, `claim_a_id`, `claim_b_id`, `kind`, `detected_by`, `recorded_at` |
| `derivation` | `id PK`, `workspace_id`, `rule_id`, `rule_name`, `connective`, `conclusion_claim_id`, `status`, `recorded_at` |
| `derivation_premise` | `derivation_id PK`, `ordinal PK`, `claim_id` |
| `dialogue_turn` | `id PK`, `workspace_id`, `question`, `concept_ids`, `concept_names`, `claim_ids`, `language`, `answered`, `act`, `recorded_at`, `forgotten_at` |
| `epistemic_case` | `id PK`, `workspace_id`, `concept_id`, `statement`, `conditions`, `outcome`, `source_id`, `origin`, `observed_at` |
| `hebbian_binding` | `workspace_id PK`, `concept_id PK`, `weights`, `accumulator`, `updated_at` |
| `learning_error` | `id PK`, `workspace_id`, `item_id`, `concept_id`, `dimension`, `learner_answer`, `expected_answer`, `verified`, `recorded_at` |
| `learning_goal` | `id PK`, `workspace_id`, `description`, `success_criteria`, `prerequisites`, `status`, `progress_evidence`, `failed_attempts`, `remaining_work`, `budget_units`, `budget_used`, `last_checkpoint`, `recovery_instructions`, `created_at`, `updated_at` |
| `learning_goal_subtask` | `goal_id PK`, `subtask_id PK`, `description`, `required`, `prerequisites`, `status`, `evidence`, `note` |
| `learning_state` | `id PK`, `workspace_id`, `concept_id`, `dimension`, `stage`, `score`, `evidence_count`, `transitioned_at`, `note` |
| `learning_transition` | `id PK`, `workspace_id`, `concept_id`, `dimension`, `from_stage`, `to_stage`, `reason`, `outcome_id`, `occurred_at` |
| `observation_occurrence` | `id PK`, `workspace_id`, `case_id`, `episode_id`, `signal_id`, `source_id`, `evidence_id`, `observed_at`, `disposition`, `reason` |
| `observation_adjudication` | `decision_id PK`, `workspace_id`, `case_id`, `previous_disposition`, `disposition`, `reason`, `contradicted_prediction_id`, `resolved_by`, `resolved_at`, `model_revision_id`, `recorded_at`, `request_id` |
| `observation_disposition` | `case_id PK`, `workspace_id`, `disposition`, `reason`, `contradicted_prediction_id`, `resolved_by`, `resolved_at`, `model_revision_id`, `recorded_at` |
| `workspace_snapshot` | `id PK`, `workspace_id`, `created_at`, `kind`, `payload` |

### 6.3 The indexes

`idx_concept_ws`, `idx_claim_ws`, `idx_claim_concept`, `idx_evidence_claim`,
`idx_repr_concept`, `idx_revision_target`, `idx_asked_ws`, `idx_asked_key`,
`idx_outcome_ws`, `idx_outcome_concept`, `idx_assessment_item_workspace`,
`idx_case_workspace`, `idx_occurrence_workspace`, `idx_occurrence_case`,
`idx_adjudication_workspace`, `idx_adjudication_case`,
`idx_disposition_workspace`, `idx_derivation_workspace`,
`idx_derivation_premise_claim`, `idx_dialogue_turn_workspace`,
`idx_learning_error_workspace`, `idx_learning_state_workspace`,
`idx_learning_transition_workspace`, `idx_goal_workspace`,
`idx_contradiction_workspace`, `idx_workspace_snapshot_workspace`.

**Note on the schema and the defects:** there is **no table, column or
index that carries a claim's byte/char offset**, no table that records a
source's version history, and no table that counts claims by status. The
three gates the eight defects name are absent from the data model as well
as from the code.

---

## 7. What this means for W51 §6

The W50 epistemic layer is, in code and in measurement, **exactly where
W50 left it**. The wave closed with "12 of 29"; it closes today with 12 of
29 as well, and the seventeen failures are the same seventeen failures.

Three facts make W51 §6 ("Make W50 epistemic reliability a first-class
requirement") a real piece of work rather than a documentation exercise:

1. **There is no harness.** The dataset is prose and the checker is gone.
   The first deliverable of W51 §6 has to be a machine-readable dataset and
   a committed runner, or the next measurement will be as unrepeatable as
   this one was.
2. **The gates are absent from the data model, not only from the code.**
   A status filter, an abstention gate, an immutable source identity and a
   span-level locator are not three line edits in `KnowledgeRetriever`; they
   are three columns and one write-path rule.
3. **Two of the eight defects are not defects of W50 at all but of the
   store's write path** (D5, D6), and one of them (D5) has been sitting in
   the open-questions register as `Q-W50-03` with a cause nobody had
   identified. The cause is now identified: the learning path writes no
   `workspace` row, and the refinement's source id is derived from a
   constant string.

### 7.1 Artefacts this recon left behind (outside the repository)

| path | what |
|---|---|
| `/tmp/w51-eval/drive.py` | the HTTP driver that executes the 29 cases |
| `/tmp/w51-eval/drive2.py` | the supplementary negative-revision captures (R-04, R-05 pre-snapshots) |
| `/tmp/w51-eval/check.py` | the reconstruction of the dataset's pass conditions |
| `/tmp/w51-eval/provenance.py` | the D4 / D6 measurements |
| `/tmp/w51-eval/results.json` | the per-case verdicts, machine-readable |
| `/tmp/w51-eval/captures/*.json` | every raw response (61 files) |
| `/tmp/w51-eval/knowledge.sqlite` | the database the run produced (schema 19) |

The application instance used for the run was stopped and its port
released; the database and the captures are left in place for inspection.

---

## 8. Evidence index

- the dataset: `docs-v2/demos/local-learning-loop/EVALUATION-SET.md`
- the W50 report: `docs-v2/engineering/W50-EVALUATION.md`
- the W50 status: `docs-v2/engineering/W50-STATUS.md`
- the workbench guide: `docs-v2/engineering/LEARNING-WORKBENCH.md`
- the offline proof: `scripts/offline-answer-proof.sh`
- the run script: `scripts/run-local.sh`
- the learning routes: `matrix-core/src/main/java/io/matrix/knowledge/LearningCycleResource.java`
- the asking routes: `matrix-core/src/main/java/io/matrix/knowledge/LearningWorkbenchResource.java`
- the retriever: `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeRetriever.java`
- the answering gate: `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeWorkbench.java`,
  `matrix-core/src/main/java/io/matrix/knowledge/QuestionDemand.java`
- the revision rule: `matrix-core/src/main/java/io/matrix/knowledge/ModelRevisionDecider.java`,
  `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeRefiner.java`
- the premise check: `matrix-core/src/main/java/io/matrix/knowledge/PremiseAssessment.java`
- the store and the schema: `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeStore.java`
- the acquisition pipeline: `matrix-core/src/main/java/io/matrix/knowledge/acquisition/`
- the corpus: `matrix-core/src/main/resources/corpus/plate-tectonics/`,
  `matrix-core/src/main/resources/research-pool/plate-tectonics/`
