# C2A — Candidate freeze (Workstream B3+B5, CONCEPT-TO-ACTION)

**Status:** `FROZEN` — written **before** any measurement of the candidates.
**Date of the freeze:** 2026-11-15.
**Repository:** `agi`, branch `feature/concept-to-action`, HEAD `d15eb3340e99b31aebff552791b0f1fd6a90e095`.
**Author:** Workstream B3+B5 research worker (test-only prototype).
**Companion artefacts:** `docs-v2/engineering/C2A-CONCEPT-ENRICHMENT-EXPERIMENT.md` (findings),
`docs-v2/engineering/C2A-CONCEPT-ENRICHMENT-RESULTS.json` (machine-readable metrics).

## 0. What this file is, and the rule it follows

This file **fixes** the three retrieval candidates (H1, H2, H3), the baseline, and the metrics
of the experiment, **before** a single number is computed. It is written in the past tense of the
specification, not of the result: it says what the candidates ARE, not what they achieve.

The rule it follows is the one the owner directive set:

> a held-out evaluation will later be run against this frozen definition by another worker; the
> freeze is what makes that evaluation meaningful.

Concretely: after this file exists, the implementation of the candidates may not be tuned to the
result, the metric definitions may not be changed to make a candidate look better, and the
held-out dataset (owned by another worker, never seen by this experiment) must be run against
**exactly** the definitions written here.

**The results file carries the SHA-256 of this file**, so that a reader can check that the numbers
were produced against THIS freeze and not against a later, kinder one.

### 0.1 What this freeze is NOT

* It is not a design of the production retrieval path. **No line of
  `matrix-core/src/main/java` is modified by this experiment.** The whole prototype lives in
  `matrix-core/src/test/java/io/matrix/knowledge/concept/`.
* It is not a claim that any candidate works. The outcome is whatever the measurement says.
* It does not create, read or depend on the held-out dataset.

---

## 1. The material

### 1.1 The corpus (fixed, verified)

The source documents are the licensed corpus that ships with the application. They are read
through the production `ClasspathCorpus.extract`, and every file's SHA-256 is checked against the
manifest before it is used.

| Corpus | Files | sha256 (short) |
|---|---|---|
| `corpus/plate-tectonics` (seed) | `usgs-this-dynamic-earth.md`, `wikipedia-plate-tectonics.md` | `a82d4ca7…c2467cdc`, `db794272…a8fc1c853` |
| `research-pool/plate-tectonics` | `wikipedia-continental-drift.md`, `wikipedia-mantle-convection.md`, `wikipedia-mid-ocean-ridge.md`, `wikipedia-paleomagnetism.md`, `wikipedia-seafloor-spreading.md`, `wikipedia-slab-pull.md`, `wikipedia-subduction.md` | `0c7724c9…65d2`, `7991295c…f682`, `0cb70b43…60dd`, `af0b1cec…eb00`, `ebd0d04c…06c4`, `3bddce4a…bc30`, `a44b13d8…7b74` |

A document of the workspace is identified by its `Source.contentHash`; the extraction uses the
bytes whose SHA-256 equals that hash, so a definition or a relation always points at the exact
document the workspace imported.

### 1.2 The sentences (fixed, production)

A **sentence** is a unit of the production extraction: the propositions produced by
`ClaimExtractor.propose` over the production `ExtractedDocument` of the document. The workspace's
claims ARE those propositions (the importer writes one claim per proposal), so the sentence
inventory of a workspace is exactly its claim set.

* The verbatim span is the proposition, character for character.
* The offsets are the offsets of the sentence inside the **extracted text** of the source
  document — the convention `io.matrix.knowledge.ClaimSpan` already declares
  (`Provenance.Span.Unit.CHARACTERS`, start inclusive, end exclusive). The extracted text differs
  from the raw bytes only by the production whitespace normalisation.
* The **precision** of the offsets is recorded with every candidate:
  * `EXACT` — the sentence was located character for character inside the extracted text;
  * `SEGMENT` — the extractor joined the lines of the segment, so the sentence is not a literal
    substring of the extracted text; the span of the **segment** is what can be located exactly,
    and the sentence is inside it once the whitespace is normalised.
* Every definition and every relation records: the originating **source id**, the **verbatim
  supporting span**, its **character offsets**, the **concept ids** involved, the **relation type
  and direction**, the **extraction method name**, and the **evidentiary status**.

### 1.3 The evaluation set (fixed)

`matrix-core/src/main/resources/epistemic-eval/cases.json` — dataset `w50-plate-tectonics`,
version `1.1`, **29 cases**, six scopes. The dataset is read at run time; it is not transcribed.

### 1.4 The gold matching rule (fixed)

The gold claims are the `expect` block's propositions. **Claim ids differ per workspace, so the
gold is matched by PROPOSITION TEXT, never by id.** The comparison is normalised the way
`scripts/e2-candidate-recall.py` normalises: whitespace collapsed, lower-cased, trailing
`.`/`?`/`!` stripped. The strict (byte-exact) comparison of the official runner is reported
alongside.

---

## 2. The baseline (frozen)

**BASELINE** is the production retrieval path, unchanged:

* the **snapshot** is the workspace's `KnowledgeSnapshot`, as the production store exports it;
* the **retriever** is `io.matrix.knowledge.KnowledgeRetriever`, constructed by production code;
* the **floor is 0.25** (`KnowledgeRetriever.DEFAULT_MINIMUM_COVERAGE`, the value the official
  18/29 result was measured at);
* the **candidate list** used for the ranking metrics is the same production retriever at
  **floor 0.0** — the same trick `scripts/e2-candidate-recall.py` used: at floor 0.0 the
  retriever cites every citable claim with any term match, in its own rank order. The floor-0.0
  list is a strict superset of the floor-0.25 evidence set, and it is the only way to measure a
  RANK (recall@K, MRR, "the top-ranked claim") without touching production code.

Two baseline rows are reported, and the difference between them is stated:

| Row | What it is |
|---|---|
| `BASELINE-PATH` | the full production path: `KnowledgeWorkbench.ask(workspace, question, level)`, which adds the workbench-level gates (the demand analysis, the quantity gate, the premise check) and the dialogue context. This is what the official HTTP runner exercises. |
| `BASELINE-RETRIEVER` | the production `KnowledgeRetriever` alone over the same snapshot, at floor 0.25. This is the row the candidates are compared against, because the candidates are retriever-level. |

The **official reference** is the retained artefact `docs-v2/engineering/E2-FLOOR-0p25.json`:
**18 / 29**, with the eleven failures `A-02, A-04, A-05, A-06, F-02, M-01, P-01, P-02, M-03,
R-04, R-05`.

---

## 3. The extraction contract (frozen)

### 3.1 Definitions

A **definition candidate** for a concept `C` is found **only** by this method:

1. `C`'s **own source documents** are the sources of `C`'s claims, taken in the order the
   snapshot lists the sources.
2. The sentences of those documents are scanned **in document order**.
3. A sentence qualifies when it **contains `C`'s name as a word** (for a multi-word name: the
   whole token sequence, case-insensitively, as a contiguous subsequence of the sentence's
   tokens) **and** contains a **definitional copula**.
4. The **FIRST** qualifying sentence wins. If no sentence qualifies, the concept has **NO
   definition** — recorded as `no definition found`.

The **definitional copula** vocabulary (closed):

```
is · are · refers to · means · describes · consists of · is defined as · is called
```

The extracted **definition is the whole qualifying sentence, verbatim**. It is never paraphrased,
never shortened, and never invented: a sentence that does not state the definition is not turned
into one.

**Method name:** `definitional-copula-first-sentence-v1`.

### 3.2 Typed relations

A **relation candidate** between concepts `A` and `B` is found **only** when

1. **ONE sentence** contains **both** concept names (the same word-sequence rule as §3.1), and
2. the sentence contains a **trigger phrase** of the relation type, and
3. the trigger phrase **separates** the two concepts: `A` is the concept whose name ends
   **closest before** the trigger phrase, `B` is the concept whose name begins **closest after**
   it.

**Direction (fixed, and the same for all five types):** the relation reads
`A --TYPE--> B`, where `A` is the concept before the trigger phrase and `B` the concept after it.
So `IS_A`, `PRODUCES`, `PART_OF` and `CAUSED_BY` all read in the order the sentence reads
(A before B), and `CAUSED_BY` means "`A` is caused by `B`", `MEASURED_BY` means "`A` is measured
by `B`". The direction is recorded on every candidate.

**Relation vocabulary (closed, five values):** `IS_A`, `PRODUCES`, `PART_OF`, `CAUSED_BY`,
`MEASURED_BY`.

**Trigger phrases (closed):**

| Type | Trigger phrases |
|---|---|
| `IS_A` | is a · is an · are a · are an · is the · are the · is called · is known as · refers to · is defined as · is classified as · is considered a · is considered an · is considered the · is a type of · is a kind of · is a form of · is an example of · is an instance of · is one of |
| `PRODUCES` | produces · produce · produced · produces · creates · created · creates · forming · forms · formed · gives rise to · give rise to · results in · result in · leading to · leads to · causes · cause · causing · triggers · trigger · is responsible for · are responsible for · contributes to · contribute to · generates · generate · yields · yield · emits · emit |
| `PART_OF` | is part of · are part of · part of · belongs to · belong to · makes up · make up · comprises · comprise · includes · include · consists of · consist of · is located in · are located in · is found in · are found in · is situated in · lies in · is located within · is contained in · is made of · is made up of · is divided into · are divided into · within |
| `CAUSED_BY` | caused by · resulting from · results from · resulted from · due to · as a result of · driven by · produced by · created by · formed by · induced by · owing to · attributed to · attributable to · triggered by |
| `MEASURED_BY` | measured by · measured with · measured using · measured through · determined by · tracked by · recorded by · observed by · dated by · estimated by · calculated by · quantified by · monitored by |

**The passive guard (fixed).** A `PRODUCES` trigger phrase that is immediately preceded by a
passive auxiliary (`is`, `are`, `was`, `were`, `be`, `been`, `being`) is **rejected** — the
sentence does not say that the concept before the trigger produces the concept after it. The
passive form of the same verb belongs to `CAUSED_BY`, whose direction is the other way round.

**The longest match (fixed).** When several trigger phrases match at the same place, the
**longest** one wins (`"is part of"` before `"part of"`, `"produced by"` before `"produced"`). Two
phrases of the **same length** and **different types** cannot both match under the vocabulary
above; if they did, the candidate would be rejected as `ambiguous trigger phrase`.

**Rejections (counted and reported with the reason).** A candidate is rejected when:

| Reason | Meaning |
|---|---|
| `passive voice` | a `PRODUCES` trigger preceded by a passive auxiliary |
| `no concept before the trigger` | nothing that names a concept precedes the trigger |
| `no concept after the trigger` | nothing that names a concept follows the trigger |
| `the same concept on both sides` | the concept before and the concept after the trigger are the same |
| `a relation of another type already exists for this pair` | the ordered pair already carries a candidate of a different type; the first type wins |
| `the concept name is a single function word` | a one-word concept name that is a pronoun or a function word cannot carry a relation |
| `ambiguous trigger phrase` | two trigger phrases of different types match the same place |

**The function-word filter (fixed).** A concept name may take part in a relation only when it has
at least two tokens, or one token of at least four characters that is not one of
`it · this · that · there · they · these · those · most · many · other · some · such · then ·
than · when · where · which · what · who · how · why`.

**Method name:** `typed-relation-trigger-phrase-v1`.

### 3.3 One hop, and what is never done

* **One hop only.** No transitive closure: a concept related to a concept related to `C` is not
  related to `C`.
* **Never a relation from co-occurrence alone.** Two concept names in one sentence are not a
  relation; the trigger phrase is required.
* **Never generated output as evidence.** The evidence for a relation is the source sentence; the
  evidence for a definition is the source sentence. Nothing that the system generated (a
  definition, a relation, an answer) is ever used as evidence for another candidate.

### 3.4 Evidentiary status (fixed)

Every definition and every relation carries one of two statuses:

| Status | When |
|---|---|
| `CANDIDATE` | extracted from the source document, **not independently verified** |
| `SUPPORTED` | the **production validator** has already promoted a claim that carries this same sentence to `SUPPORTED`/`VERIFIED`/`CONTESTED` (`EpistemicPolicy.isCitable`) — i.e. the workspace itself holds this sentence as established |

**What `SUPPORTED` says, and what it does not.** It says that the **sentence** is an established
claim of the workspace. It does **not** say that the definition or the relation is true: the
sentence may state a relation that a reader would call wrong (the rule reads the sentence
lexically, and the report shows the sentence). The status is about the provenance of the text, not
about the semantics of the relation.

No candidate is ever promoted by this experiment. The validator's verdict is read, never produced.

---

## 4. The candidates (frozen)

### H1 — DEFINITION ENRICHMENT

* **What is changed:** the concept's `definition` field of the **snapshot**, and nothing else.
  The claims, the evidence, the sources, the representations and the statuses are **byte-identical**
  to the baseline snapshot.
* **What the definition is:** the verbatim sentence of §3.1.
* **How it reaches the retriever:** a **fresh production `KnowledgeRetriever`** is constructed
  over the enriched snapshot, at floor 0.25. The production retriever composes each claim's
  searchable text as `proposition + concept name + concept definition`
  (`KnowledgeRetriever.java:148-154`), so the definition enlarges the claim's searchable text.
* **Concepts with no definition** keep `definition = null` — nothing is invented.

### H2 — EXPLICIT CONCEPT RELATIONS (one hop, corpus side)

* **What is changed:** the concept's `definition` field of the snapshot again — because that is
  the only free-text slot the production retriever composes into the claim's searchable text.
  The text written there is **NOT a definition**; it is a retrieval aid, and the report says so.
* **What is written:** for a concept `C`, the **retrieval text of the related concepts** — for
  every relation candidate in which `C` is an endpoint, the related concept's **name** (and, in
  the combined variant of §4.3, its definition).
* **The expansion is symmetric and one hop:** a claim of `C` also carries the text of the
  concepts `C` is related to, in either direction of the relation. The relation's **direction**
  is recorded on the candidate and is what the relation MEANS; the expansion deliberately does not
  distinguish it, because the point of H2 is that a claim about the effect reaches the vocabulary
  of the cause and the other way round.
* **No transitive closure.**

### H3 — QUERY-AWARE LEXICAL EXPANSION (one hop, question side)

* **What is changed:** the **question**, and nothing else. The corpus is untouched; no snapshot
  is enriched.
* **What is appended:** for every concept whose name occurs in the question (the word-sequence
  rule of §3.1), the **names** of the concepts that are related to it by a relation candidate
  (one hop, symmetric) are appended to the question text.
* **How it reaches the retriever:** the expanded question is put to a fresh production
  `KnowledgeRetriever` over the **unmodified** snapshot, at floor 0.25.
* **The cost is part of the result:** the expansion adds terms to the query, and the retriever
  divides the matched weight by the **total** weight of the query's terms. A term that matches
  nothing therefore lowers the coverage of every claim. This is measured, not assumed.

### 4.3 H1 + H2 (the combination, frozen as well)

The union of H1 and H2: the concept's `definition` carries **its own definition** (if one was
found) **and** the retrieval text of the related concepts (name **and**, when the related concept
has one, its definition). It is reported as a supplementary row; it is not one of the three
competing explanations.

### 4.4 What the candidates do NOT touch (fixed)

* The production retrieval path is not modified — **not one line** of `matrix-core/src/main/java`.
* The **false-premise path** (`PremiseAssessment.assess`) and the **revision path**
  (`AcquisitionCycle.acquireAndRevise`) are **not** enriched: `PremiseAssessment.assess` builds
  its own `KnowledgeRetriever` from the **stored** workspace, and the constraint forbids changing
  production code, so the enrichment cannot reach it. This is a stated limitation of the
  experiment, not a silent omission.
* The **scope preparation** is the production one, unchanged, for every candidate.

### 4.5 The level the candidates live at, and the asymmetry that follows (fixed)

The candidates are **retriever-level**: the candidate is applied to the snapshot before the
production retriever sees it, or to the question before it is asked. The production retriever is
reused as it is.

The full production path (`KnowledgeWorkbench.ask`) adds gates **above** the retriever — the
demand analysis, the quantity gate, the premise check — and the dialogue context. A candidate
applied to the snapshot cannot pass through those gates, because they live in the workbench and
the workbench builds its own snapshot from the store.

The metric table therefore carries **two** baseline rows:

* `BASELINE-PATH` — the full production path, the official 18/29 reference;
* `BASELINE-RETRIEVER` — the production retriever alone, **the row the candidates are compared
  against**, because the candidates are retriever-level.

The difference between the two rows is reported per case, and it is an asymmetry of the
**instrument**, not a property of any candidate.

---

## 5. H4 — the other bottleneck (a classification, not a score)

For every case that **fails in the baseline**, the failure is classified from the answer's own
fields. The taxonomy (closed) and the **decision tree** that produces exactly one code per
failure:

| Code | Meaning | Evidence used |
|---|---|---|
| `GOLD_NOT_IN_WORKSPACE` | the gold proposition is not a claim of the workspace at all | the workspace's claims |
| `NO_LEXICAL_MATCH` | the gold claim IS in the workspace but is not even in the floor-0.0 candidate list: the question and the claim share no term | the floor-0.0 candidate list |
| `BELOW_FLOOR` | the gold claim is in the floor-0.0 candidate list but not in the floor-0.25 evidence set | both lists |
| `WRONG_SCOPE` | the answer was given and cited claims, but the gold's concept is not among the answer's concepts: the answer was about another subject | the answer's concepts and claims |
| `GOLD_CITED_CASE_FAILED` | the gold claim IS cited and the case still fails: the failure is in another condition of the case (the number of distinct claims or sources, the required source, the gap terms, the `Any` variant) | the answer's claims and the case's conditions |
| `ABSTAINED_PREMISE` | the answer abstained because the workspace contradicts the question's premise | the abstention reason |
| `ABSTAINED_QUANTITY` | the answer abstained because the question asks for a measured quantity the workspace does not state | the abstention reason |
| `ABSTAINED_TIME_DEMAND` | the answer abstained because the question asks about a time that has not happened | the abstention reason |
| `ABSTAINED_DEMAND` | the answer abstained because the demand analysis refused the question | the abstention reason |
| `ABSTAINED_NO_COVERAGE` | the answer abstained because no claim covers enough of the question | the abstention reason |
| `ABSTAINED_OTHER` | the answer abstained for any other reason | the abstention reason |
| `FALSE_PREMISE_MISMATCH` | the assessment's verdict is not the one the case expects | the finding's verdict |
| `FALSE_PREMISE_EVIDENCE_MISSING` | the verdict is right but the disputed premise, the contradicting claim or the source is not named | the finding's evidence |
| `REVISION_NOT_APPLIED` | the revision the case expects was not applied | the revision report |
| `REVISION_REASON_MISSING` | the revision was applied but the decision does not carry the reason the case names | the decisions |

**The decision tree, in this order:**

1. the answer **abstained** → the abstention code, read from the abstention reason;
2. the answer was given and the gold is **cited** → `GOLD_CITED_CASE_FAILED`;
3. the answer was given and the gold is **not** cited:
   1. the gold is not a claim of the workspace → `GOLD_NOT_IN_WORKSPACE`;
   2. the gold is not in the floor-0.0 candidate list → `NO_LEXICAL_MATCH`;
   3. the gold's concept is not among the answer's concepts → `WRONG_SCOPE`;
   4. otherwise → `BELOW_FLOOR`.

Every classification is reported **with the evidence** (the answer's fields: `answered`, the
abstention reason, the cited propositions, the cited sources, the gaps, the unmatched terms), so
that a reader can check it.

---

## 6. The metrics (frozen)

All metrics are computed on the 29 cases of the dataset.

| Metric | Definition |
|---|---|
| **Baseline per-case outcome** | for every case: the verdict (PASS/FAIL) against the dataset's conditions, judged with the official runner's rules, re-implemented in the harness (`scripts/w51-epistemic-eval.py`: `judge_answer`, `judge_premise`, `judge_revision`) |
| **Gold availability** | for a case with a gold set `G`: the share of `G` that is among the claims the answer cites at floor 0.25 |
| **Recall@K (K = 1, 5, 10, 20)** | over the answerable cases that carry a gold set: per case, the share of `G` found within the **top K of the floor-0.0 candidate list**; the reported value is the mean over the cases. The global share (over all gold propositions of all cases) is reported alongside |
| **MRR** | over the same cases: the reciprocal rank of the **first** gold proposition in the floor-0.0 candidate list; **a case whose gold is not in the list contributes 0** — the misses are in the denominator |
| **Supported answer accuracy** | over the answerable cases: the share that produced `answered = true` **and** met the case's own gold condition (all of `requiredPropositions`, or at least one of `requiredPropositionsAny`) |
| **Abstention accuracy** | over the unanswerable cases: the share that abstained (`answered = false`, no claims cited, no sources cited) |
| **Unsupported-answer rate** | over the answerable cases: the share that answered **without** the gold claim. Reported alongside over all the *ask* cases, where answering an unanswerable case counts as an unsupported answer |
| **False-positive retrieval** | (a) the mean number of **non-gold** claims cited per answerable case at floor 0.25; (b) the number of cases whose **top-ranked** claim (rank 1 of the floor-0.0 list) is not a gold claim |
| **Latency** | the wall time of the retrieval per case and per candidate (mean and total), and the overhead against the baseline (mean and total). The **extraction** is timed and its retained size measured separately |
| **Coverage shift** | when a candidate **loses** a gold claim the baseline cited, the **coverage** of that claim — the ratio the retriever divides by — is measured under the baseline and under the candidate, with the production retriever alone: the floors are scanned from 0.00 upwards in steps of 0.01 and the highest floor at which the claim is still cited is its coverage. The representations are removed for this measurement, because the retriever's concept **bridge** adds the claims of a concept whose representation covers enough of the question, and that would let a claim be cited at a floor its own coverage never reached |

**The answerable cases** are the 14 cases whose `expect.answered` is `true` (`A-01…A-06`, `P-01`,
`P-02`, `M-01`, `M-02`, `M-03`, `K-01`, `K-02`, `R-03`); the **unanswerable cases** are the 6
cases whose `expect.answered` is `false` (`U-01…U-06`). The false-premise and revision cases are
neither: they are judged by their own conditions.

---

## 7. The scope preparation (frozen)

The workspace is prepared **exactly as the dataset says**, using the production methods behind
the HTTP calls:

| Scope | Workspace | Preparation (production methods) |
|---|---|---|
| `S1` | `plate-tectonics-eval-seed` | `AcquisitionCycle.loadSeed(workbench, workspace)` — behind `POST /v1/learning/workspaces/{id}/seed` |
| `P1` | `plate-tectonics-eval` | `loadSeed` + `AcquisitionCycle.run(workbench, workspace, question, 3)` — behind `POST /v1/learning/workspaces/{id}/learn` with the dataset's question |
| `R1` | `plate-tectonics-eval` | `P1` + `AcquisitionCycle.acquireAndRevise(workbench, workspace, "wikipedia-slab-pull.md")` — behind `POST /v1/learning/workspaces/{id}/revise` |
| `R2` | `plate-tectonics-eval` | `R1` + `acquireAndRevise(..., "wikipedia-paleomagnetism.md")` |
| `P1'` | `plate-tectonics-eval-neg` | `loadSeed` + `run` — a fresh copy of P1 |
| `P1''` | `plate-tectonics-eval-neg2` | `loadSeed` + `run` — a second fresh copy of P1 |

**The preparation is repeated before every case**, in the order the dataset declares the scopes
and the cases — this is what `scripts/w51-epistemic-eval.py` does, and the repetition is part of
the protocol (the bounded acquisition cycle selects its documents from the workspace's **current**
gaps, so the workspace state at the moment of a case is part of the case's definition).

**Which scopes could be prepared exactly** is reported. A scope that could not be prepared is
reported as such; its cases are not silently dropped.

---

## 8. The held-out evaluation (for the next worker)

The next stage runs the **held-out** dataset (owned by another worker; this experiment has never
seen it) against **this** definition. To make that evaluation valid:

1. The candidate implementation must be the one written against this freeze — the same
   `SourceGroundedEnrichment`, the same `EnrichmentCandidates`, the same metric code.
2. The extraction must run over the held-out workspace's **own** source documents, with the same
   rules of §3, and the extraction's counts must be reported the same way.
3. The held-out dataset's gold propositions are matched by **proposition text** (§1.4), never by
   id.
4. Nothing in §3–§6 may be changed between the freeze and the held-out run. A change invalidates
   the freeze and must be recorded as a **new** freeze with a new SHA-256.
5. The held-out run must report the same metric names and the same denominators as §6, so that
   the two runs are comparable.

---

## 9. The freeze's own identity

* The SHA-256 of this file, as it stood when the results were computed, is written into
  `docs-v2/engineering/C2A-CONCEPT-ENRICHMENT-RESULTS.json` under `freeze.sha256`.
* The results file also carries `freeze.path`, the date, the branch and the commit, and a
  statement that the results were produced **against the frozen candidates**.
* If the SHA-256 in the results file does not match this file, the results were produced
  against a different freeze and must not be compared with a held-out run of this one.

### 9.1 The amendments, and what they did not touch

This freeze was written **before** the measurement, and it was amended while the implementation
was being written — before any result was produced. The amendments are listed here so that a
reader can see exactly what was touched and what was not:

1. the **longest-match tie-break** of the trigger phrases and the `ambiguous trigger phrase`
   rejection (§3.2);
2. the **`SEGMENT` offset precision** and what it means (§1.2);
3. the **level the candidates live at**, and the two baseline rows it forces (§4.5);
4. the **H4 decision tree**, stated as a tree, plus the `ABSTAINED_TIME_DEMAND` code (§5);
5. the **coverage-shift metric** (§6).

**No extraction rule, no candidate definition, no metric denominator and no scoring rule changed
after the measurement.** The amendments above name rules that were always implemented; they do
not introduce a new rule, and the results were produced against the file as it stands now — its
SHA-256 is the one in the results file.

---

# FREEZE VERSION 1.1 (appended; it supersedes v1.0)

**Status:** `FROZEN` — written **before** the v1.1 measurement.
**Date of this version:** 2026-11-15.
**Supersedes:** version 1.0 of this file, whose SHA-256 is
`14251fbcdf06001b670d919621b64079a35229ebabe1577081fb0b6222c08cf4`.
**The v1.0 text above is NOT rewritten and NOT deleted.** It is the record of the definitions the
first measurement was produced against, and the first measurement itself is preserved: its
numbers are quoted **as they were published** in `C2A-CONCEPT-ENRICHMENT-EXPERIMENT.md` §0 and
under `v1_0Measurement` in `C2A-CONCEPT-ENRICHMENT-RESULTS.json`.

## A. Why this version exists

An independent scientific review reproduced every number of the first measurement and found
defects in it. Three of them are defects of the METRIC DEFINITIONS and one is a defect of the
DEFINITION RULE; the rest are defects of the reporting. This version fixes the first four and
states, for each of them, what changed, why, and what did not change.

The findings that produced this version:

1. **The definition rule of §3.1 does not test the hypothesis it claims to test.** The rule only
   asked for the concept's name as a word AND a definitional copula SOMEWHERE in the sentence.
   The first measurement showed that most of the definitions it produced are not definitions:
   of the 162 definitions it gave the seed scope, only 109 (67.3 %) have the name **immediately**
   followed by a definitional copula, and 3 of the 162 are longer than the 400 characters this
   version allows. The sentences the rule accepted include "A trench forms and, as the
   subducted plate is heated, it releases volatiles …" as the definition of "A trench",
   "Consideration of plate tectonics is a part of the search for extraterrestrial intelligence
   and extraterrestrial life." as the definition of "Consideration of plate tectonics", and a
   paragraph that begins "Possible tidal effect on plate tectonics …" as the definition of a
   concept named "As a".
2. **The H4 classification was applied per CASE, with ANY semantics over the case's golds.**
   The freeze's tree is per GOLD. Five of the seven failures the first report called
   `BELOW_FLOOR` have at least one gold that is not in the floor-0.0 candidate list at all, so
   that gold is unreachable at ANY floor. The classification therefore attributed to the floor a
   share of the failures the floor does not carry, and it gave P-01 (two of three golds
   unreachable) and P-02 (three of three) different codes for the same defect.
3. **The MRR was not an MRR.** It took the reciprocal rank of the FIRST gold in the CASE'S LIST
   ORDER rather than of the TOPMOST gold by rank.
4. **The supported-answer accuracy and the unsupported-answer rate ignored the case's own gold
   condition.** The freeze requires "all of `requiredPropositions`, or AT LEAST ONE of
   `requiredPropositionsAny`"; the implementation required every gold to be cited, so an
   `Any`-case could pass and still be counted unsupported.

## B. What this version changes

### B.1 §3.1 — the definition rule (tightened)

The rule of §3.1 is replaced by the following. Everything else of §3.1 (the sources of the
concept, the document order, the FIRST qualifying sentence wins, the closed copula vocabulary,
the verbatim sentence as the definition, nothing paraphrased and nothing invented) is unchanged.

A **definition candidate** for a concept `C` is found **only** by this method:

1. `C`'s **own source documents** are the sources of `C`'s claims, taken in the order the
   snapshot lists the sources.
2. The sentences of those documents are scanned **in document order**.
3. A sentence **QUALIFIES** when all three of the following hold:
   1. it **contains `C`'s name as a word** (for a multi-word name: the whole token sequence,
      case-insensitively, as a contiguous subsequence of the sentence's tokens) — the same rule
      as v1.0;
   2. the name is **IMMEDIATELY FOLLOWED by a definitional copula**: the token sequence that
      follows the occurrence of the name is one of the closed vocabulary of §3.1 —
      `is · are · refers to · means · describes · consists of · is defined as · is called`. The
      explicit phrase "is defined as" is a member of that vocabulary and therefore satisfies
      this condition in the ordinary way;
   3. the sentence is **at most 400 characters long**.
4. The **FIRST** qualifying sentence wins. If no sentence qualifies, the concept has **NO
   definition** — recorded as `no definition found`.

**Method name:** `definitional-adjacency-first-sentence-v1.1`.

**The length cap.** A definition is ONE sentence. The production extractor joins the lines of a
segment, so a "sentence" can be a whole paragraph; the cap keeps a paragraph out of the
concept's definition field. The cap is a property of the RULE, not of the corpus: it is the
same 400 characters for every sentence of every scope.

**What the tightening does NOT change.** The relation extraction of §3.2 is untouched: the
relations, the trigger phrases, the passive guard, the longest-match tie-break, the rejections
and the evidentiary status are exactly the ones of v1.0. H2 and H3 are therefore measured under
both versions with the same relation candidates.

### B.2 §5 — the H4 classification is per GOLD

The taxonomy of §5 is unchanged; the tree is walked **per gold proposition**, and the
classification is computed for the **FAILING cases only** — a passing case carries no code at
all.

For every gold proposition `g` of a failing case, in this order:

1. `g` is **cited** by the answer → `GOLD_REACHED` — the gold was reached, it is not a defect;
2. `g` is **not a claim of the workspace** → `GOLD_NOT_IN_WORKSPACE`;
3. `g` is **not in the floor-0.0 candidate list** → `NO_LEXICAL_MATCH`;
4. the **gold's concept is not among the answer's concepts** (and the answer has concepts) →
   `WRONG_SCOPE`;
5. otherwise → `BELOW_FLOOR`.

The **code of the case** is then:

* every gold is cited → `GOLD_CITED_CASE_FAILED` (the case failed on another condition);
* the golds that are not cited **agree** on one code → that code;
* the golds that are not cited **disagree** → `MIXED_GOLD_CODES`.

The abstention branch is unchanged: the answer abstained → the abstention code, read from the
abstention reason.

### B.3 §6 — the metrics

| Metric | The v1.1 definition |
|---|---|
| **MRR** | over the same cases: the reciprocal rank of the **TOPMOST GOLD** — the gold with the **SMALLEST** rank in the floor-0.0 candidate list; **a case whose gold is not in the list contributes 0** — the misses are in the denominator |
| **Supported answer accuracy** | over the **14 answerable cases**: the share that produced `answered = true` **AND met the case's own gold condition** — **all of `requiredPropositions`, or AT LEAST ONE of `requiredPropositionsAny`** |
| **Unsupported-answer rate** | over the **14 answerable cases**: the share that answered **without** meeting the case's own gold condition |
| **Unsupported-answer rate, third reading** | over the **14 answerable cases**: the share that answered and cited **NO** gold at all — reported separately, because it is a different defect from "answered without the condition" |
| **Unsupported-answer rate over ALL the ask cases** | over the **20 ask cases** (the 14 answerable and the 6 unanswerable ones): answering an unanswerable case counts as an unsupported answer |
| **False-positive retrieval (b)** | the number of cases whose **top-ranked claim** (rank 1 of the floor-0.0 list) **is not a gold claim** |
| **Global (micro) recall share** | over **every gold proposition of the answerable cases**: the share found within the top K — the cases are not weighted equally |
| **Aggregate gold availability** | the mean over the answerable cases of the share of the gold set the answer cites at floor 0.25 |
| **Latency overhead** | the mean retrieval time of the candidate minus the mean retrieval time of `BASELINE-RETRIEVER`, in ms per case, written out |

The definitions that are **unchanged**: the recall@K (the mean over the answerable cases of the
share of the gold set found within the top K of the floor-0.0 list), the abstention accuracy
(over the unanswerable cases), the mean number of non-gold claims cited (over the answerable
cases), the latency (the mean over the answerable cases, the total over every row of the
candidate), and the coverage shift (the same measurement — but see B.5 for its presentation).

### B.4 §6 — the metrics that were never delivered

The v1.0 freeze named four metrics that the first measurement did not write out: the global
(micro) recall share, the unsupported-answer rate over all the ask cases, the latency overhead,
and the aggregate gold availability. This version **delivers** them; the table above is their
definition, and every one of them is in the results file.

### B.5 §4 and §6 — the scale of every candidate, and the coverage shift

* For every candidate, the **number of concepts it wrote into** and the **number of claims whose
  searchable text it changed** are measured and reported. The searchable text of a claim is its
  proposition, its concept's name and its concept's definition
  (`KnowledgeRetriever.java:148-154`), so a claim's text changes exactly when the definition of
  ITS concept changes.
* The **coverage shift** is measured for **H3 as well** — over the unmodified snapshot and with
  the **expanded question**, which is the candidate H3 actually is.
* The coverage floor is a **bracket**: the floors are scanned from 0.00 upwards in steps of 0.01,
  so the reported value is the upper bound of a 0.01-wide interval, and it is reported **as the
  interval**.

### B.6 The reporting

* Every table states its **denominator** in its caption.
* The latency table says which **instrument** each row is: `BASELINE-PATH` is the full
  production path, the other rows are the pure retriever, and the two are not comparable.
* The comparison of the two baseline rows is **computed from the run**, and the gate is read
  from the **abstention reason the production path itself gives** — not from prose.

## C. What this version does NOT change

* The corpus, its manifest and its digests (§1.1); the sentences and their provenance (§1.2);
  the gold matching rule (§1.4).
* The baseline rows, the floors and the two baseline rows' definitions (§2, §4.5).
* The extraction contract of §3.2 — the typed relations, their trigger phrases, the passive
  guard, the rejections, the one-hop rule, the evidentiary status (§3.3, §3.4).
* The candidates H1, H2, H3 and H1+H2 themselves (§4): what they change, where they change it
  and what they never touch (§4.4).
* The scope preparation (§7) and the judging rules (the official runner's rules).
* The held-out dataset. It is owned by another worker and it is not touched by this version.

## D. The identity of this version

* The SHA-256 of this file **as it stands after this section was appended** is the digest the
  v1.1 results and the v1.1 held-out run are bound to; it is written into
  `C2A-CONCEPT-ENRICHMENT-RESULTS.json` under `freeze.sha256` and into
  `C2A-HELDOUT-EVALUATION.json`.
* The SHA-256 of the **v1.0** version of this file is
  `14251fbcdf06001b670d919621b64079a35229ebabe1577081fb0b6222c08cf4`. It is recorded here, in
  the first measurement's block of the results file, and in the first measurement's own
  artefacts; the held-out evaluation of the first measurement cites it.
* The v1.1 measurement is produced against **this** file, and the v1.1 held-out evaluation is
  produced against **this** file — the same definitions for both, as §8 of v1.0 requires.
