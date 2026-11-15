# EPISTEMIC-CONCEPT-IDENTITY — the baseline and the bounded alternatives

**Status:** `MEASURED` (research and instrumentation only; no production file was changed)
**Date:** 2026-11-15
**Scope:** P1 — CONCEPT-MODEL REPAIR, research stage. The owner's directive
(`MATRIX_AUTONOMOUS_DEVELOPMENT_MANDATE_2026-11-15.md` §2 item 5, §3 P1, §9, §11 Gate 4).
**Instrument:** `matrix-core/src/test/java/io/matrix/knowledge/conceptidentity/ConceptIdentityBaselineTest.java`
**Evidence:** the JUnit XML of that test (path in §9) and the measurement tables below.

---

## 0. What this document is, and what it is not

It is the **instrumented baseline** of the concept layer of the measured corpus and a
**comparison of bounded, deterministic identity policies** — no embeddings, no third-party
model, no network. It is the evidence the later implementation must be chosen against.

It is **not** a proposal to change production code, and it deletes nothing. Where a fragment
cannot become a concept, the proposal is to **stop claiming it as one** and to keep the
claim's own text as the retrievable unit — never to drop the claim.

The one production fact that decides the whole design is measured first, because everything
else follows from it:

> **`DocumentImporter.java:225-231` sets the concept's name to the claim's *subject phrase*
> — the text before the first declarative verb of the sentence.** The concept is therefore
> not a concept at all: it is the left-hand side of a copula, cut wherever the verb list
> happened to stop. The concept's identity is a pure function of that text
> (`DocumentImporter.java:338-341`: `digest(workspaceId + "|concept|" + name.toLowerCase())`).

---

## 1. The measured baseline

Reproduced with the **production** importer and the **production** store over the measured
corpus. The seed corpus is the two licensed documents of
`matrix-core/src/main/resources/corpus/plate-tectonics/` (USGS *This Dynamic Earth* —
US-PUBLIC-DOMAIN; Wikipedia *Plate tectonics* — CC-BY-SA-4.0; both manifest-verified with
sha256). The research pool is the seven further CC-BY-SA-4.0 Wikipedia documents of
`matrix-core/src/main/resources/research-pool/plate-tectonics/`.

| measure | **S1 — seed (2 docs)** | **S1 + pool (9 docs)** |
|---|---|---|
| sources | 2 | 9 |
| claims | **348** | **888** |
| distinct concepts | **326** | **813** |
| claims carrying a concept id | 348 (100 %) | 888 (100 %) |
| claims with 0 / >1 concepts | 0 / 0 (by construction) | 0 / 0 (by construction) |
| concepts holding exactly **one** claim | **319 (97.9 %)** | 784 (96.4 %) |
| claims-per-concept histogram | 1→319, 2→3, 3→1, 4→1, 6→1, 10→1 | 1→784, … |
| concepts with a usable definition | **0** | 0 |
| persisted concept→concept relations | **0** | 0 |
| representations / with a prerequisite | **335 / 0** | not measured here |
| **bare function-word names** | **9** (10 with the articles) | 9 (10) |
| **dangling function-word names** | **61 (18.7 %)** / 75 (23.0 %) on the wider list | 134 (16.5 %) / 168 (20.7 %) |
| **NP-shaped names** (strict closed rule) | **190 / 326 (58.3 %)** | 499 / 813 (61.4 %) |
| largest concepts | *This* (10 claims), *It* (6), *There* (4), *They* (3) | *This* (20), *It* (9), *There* (7), *The* (5), *He* (5) |
| distinct normalised lemmas | 323 | 807 |
| lemmas with >1 surface form | 5 | 9 |
| lemmas attested in >1 document | 3 | 25 |
| definitions obtainable under the tight v1.1 rule | **121 / 326 (37.1 %)** | 319 / 813 (39.2 %) |
| typed relations under the freeze §3.2 rule | 19 (29 concepts touched) | 96 (108 touched) |

Every figure above is reproduced by the instrument, which **asserts** the published baseline
(326 / 348 / 319 / the histogram / 9 / 61) so that a silent change of the production
acquisition path fails the instrument instead of quietly producing new numbers.

### 1.1 The name classes, with examples

| class | S1 count | example names |
|---|---|---|
| dangling function word (published list) | 61 | `Picture two giant conveyor belts, facing each other but slowly moving in opposite directions as they` · `Convergence can` · `Magmas that` · `The map also` · `the crust tends to buckle and` |
| bare function word | 10 (9 without the articles) | `It` (6 claims) · `There` (4) · `This` (10) · `They` (3) · `These` · `To` · `Most` · `Other` · `Many` · `The` |
| dangling (wider list only) | 14 | `The Earth's unchanging size implies that the crust must` · `A new spreading center may` · `The rising magma, whether or not it erupts, puts more pressure on the crust to` |
| over 12 tokens (a clause, not a phrase) | 23 | `This submerged mountain range, which extends from the Arctic Ocean to beyond the southern tip of Africa,` |
| trailing punctuation (a truncated clause) | 12 | `As the name implies, volcanic island arcs, which closely parallel the trenches,` |
| lowercase head (not determiner-led) | 8 | `because plate motions` |
| caption marker (a figure caption read as a sentence) | 4 | `Understanding plate motions [This Dynamic Earth, USGS] Scientists now` · `Lava Fountains, Krafla Volcano [35 k] Thingvellir Fissure Zone, Iceland [80 k] In East Africa, spreading processes` |
| em-dash artefact | 2 | `Convergent boundaries -- where crust` |
| contains a verb | 2 | `Slab pull: Scientific opinion` |
| **NP-shaped (well-formed noun phrase)** | **190 (58.3 %)** | `Plate boundaries` · `Trenches` · `Slab pull` · `The key principle of plate tectonics` · `Dissipation of heat from the mantle` · `The supercontinent Columbia or Nuna` |

The classes are a **decision cascade** in the instrument (bare → dangling → verb → caption →
dash → length → punctuation → lowercase head → NP-shaped), so the counts sum to 326. The
NP-shape test is **syntactic**: a name can be a well-formed noun phrase and still not denote
a referent — the hand judgement in §1.2 is what measures that, and it puts the true rate at
**30 %**, not 58 %.

**Two honest notes on the metrics.**

1. The published figure **61 of 326** was measured with a closed list of 120 function words
   that the repository does not publish. It is reproducible **only** under one particular
   list (this document's `PUBLISHED_DANGLING`); a wider closed list of the same family
   measures **75 (23.0 %)**. The dangling rate is therefore a **band (18.7 % – 23.0 %)**, not
   a point, and any acceptance criterion of the form "the dangling rate is 0" must state its
   list. The bare-word count (9) **is** reproducible and is asserted.
2. The published **124 ordered substring pairs** is not reproducible under either rule tried
   here (422 character-wise, 312 token-wise). The published rule differs; the figures in this
   document are this measurement's own.

### 1.2 The referent reality, measured by hand on a sample

The word statistics above are necessary but not sufficient: the question is whether a name
denotes **the thing its claim is about**. A deterministic sample of **40** concepts (seed
20261115 over the 326 ids) was labelled by hand against the claim's own sentence.

**12 of 40 (30 %, 95 % Wilson CI 18.1 % – 45.4 %) of the concept names denote a genuine
referent of their claim.** The 28 failures include every class above: pronouns (`In the 1990s,
it`), clause fragments (`Since they`), captions (`Lava Fountains, Krafla Volcano [35 k] …`),
discourse nominals (`the question`, `An important conclusion`, `Some authors`), and the
article `The` (the sentence is *"The result is a slight lateral incline…"* — the subject
extractor stopped at the **noun** `result`, which the production verb list contains).

This is a **human judgement**, not a script output, and it is a judgement about *reference*,
not about grammaticality.

**The smallest measured instance of the referent problem.** The concept id is a pure function
of the *lower-cased* name, so two surface forms that differ only in case share one concept —
and the concept keeps the name it was created with. **Three claims of the seed corpus carry a
subject that differs from their concept's name only in case** (`This`←`this` twice, `It`←`it`):
the same referent, two surface forms, one concept, and a name that is not the one the claim
actually used. The same lower-cased name carrying **two different ids** happens **0** times,
because the id function is injective on the name — the identity is the **text**, not the
**referent**, and that is the whole problem in one line.

### 1.3 A production defect the measurement exposes

`ClaimExtractor.DECLARATIVE_VERBS` contains nouns used as verbs — `result`, `process`,
`form`, `state`, `work`, `move`, `use`, `need` — so the subject extractor stops **inside** a
noun phrase: `The result is a slight lateral incline…` yields the concept name **`The`**, and
`Plate tectonics came to…` yields `Plate tectonics came to`. The verb list is the machine for
both the "declarative verb" test **and** the concept boundary; a noun in it is a boundary
error. This is a *bounded, deterministic* fix candidate that the alternatives below do not
cover, and it is listed in §6 as a candidate in its own right.

---

## 2. The alternatives, compared

All four are bounded and deterministic. (a) is the baseline; (b), (c), (d) are measured.

| | **(a) current splitter** | **(b) NP-boundary filter** | **(c) referent key** | **(d) source-span-anchored definitions** |
|---|---|---|---|---|
| what it changes | — | the **name** of the concept | the **identity** of the concept | the **definition field** + its provenance |
| rule | the text before the first declarative verb | cut the subject at the first clause-introducer (closed list), trim trailing function words, reject a name that contains a verb | key = (normalised lemma, source document, anchor span); a display name and an alias list on top | the first sentence of the concept's **own** source document that contains the name **immediately** followed by a definitional copula, ≤ 400 chars, verbatim, with the character offsets |
| measured on S1 | 326 concepts / 348 claims | 303 concepts; **42 claims left without a concept**; 34 referents **narrowed** | 299 source-anchored keys; 5 keys carry 2 surface forms | definitions 0 → **121/326 (37.1 %)** adjacency-passing |
| dangling rate | 61 (18.7 %) | 0 **by construction** | 0 by construction | — |
| bare names | 9 | 0 by construction | 0 by construction | — |
| NP-shaped names | 190/326 (58.3 %) | **250/303 (82.5 %)** — the 53 residual are 26 truncated clauses, 9 lowercase heads, 10 over 12 tokens, 6 caption artefacts, 2 bare words | — | — |
| typed relations | 19 (29 concepts) | **29 (43 concepts)** | — | — |
| what it fixes | — | the word-level defect | the anchoring of identity | the definition defect |
| what it costs | — | a **model change** (see §3) + 42 claims lose a concept + a verb lexicon to maintain + 34 silent referent changes | the **alias/resolution layer**, which is the unsolved part; the concept population becomes source-anchored, so one referent in two documents is two concepts | the importer must write the definition field; the definitions are still **not semantically verified** |
| what it risks | — | **silent semantic corruption** — the filter truncates a coordinated NP: `Mars and Venus` → `Mars`, `The Blanco, Mendocino, Murray, and Molokai fracture zones` → `The Blanco, Mendocino, Murray,` | identity fragmentation grows (813 concepts for 888 claims); the alias table becomes the place where correctness lives | the tight rule still yields only 37 %; `There` gets the definition *"There are four types of plate boundaries…"*, which is not a definition of `There` |
| verdict | baseline | **REJECT as implemented** — the conjunct drop is disqualifying: it corrupts meaning silently | the right **shape**, but the resolution policy is unsolved and must not be assumed | **ADOPT** — orthogonal to (b)/(c), independently measurable, and it is the only one of the four that raises a measured coverage number |

**The decisive measurement of (b) vs (d):** repairing the *name* does **not** raise definition
coverage. Under the tight v1.1 rule the current names yield 121 definitions (37.1 %); the
NP-filtered names yield **83 (27.8 %)** — fewer, because trimming the name makes the
adjacency test harder and because 42 claims lose their concept entirely. **The definition
defect and the identity defect are separate defects.** (b) fixes neither.

**And the relation yield is still not a semantic result.** (b) raises the relation count from
19 to 29, but a hand look shows the endpoints are still often wrong: *"The consequences of
plate movement are easy to see around Krafla Volcano, in the northeastern part of Iceland"*
yields `PART_OF` → `Iceland`. A trigger phrase is a lexical fact, not a relation. The
acceptance criterion for relations must be **human-verified precision**, not count.

---

## 3. The constraint that decides the implementation

> `Claim.conceptId` is `TEXT NOT NULL` (`KnowledgeStore.java:540`) and `KnowledgeStore.validate`
> throws when a claim references a concept that does not exist
> (`KnowledgeStore.java:2338-2342`). A representation's concept id is `NOT NULL` too
> (`KnowledgeStore.java:566`), and the representation's **own id** embeds the concept id
> (`RepresentationComposer.java:175-176`).

So "do not claim a fragment as a concept" **cannot** be implemented by simply unlinking the
claim: it needs one of

1. a nullable `concept_id` plus a validation change plus a retriever change (the retriever
   composes `proposition + concept name + definition` — `KnowledgeRetriever.java:148-154`, so
   a claim without a concept is still rankable, but the concept bridge at
   `KnowledgeRetriever.java:458-520` changes);
2. a **claim-scoped record** whose name is the claim's own text, marked *not a concept* — the
   claim stays retrievable, the workspace gains no false concept;
3. a concept whose name is a **verified** referent (rule-verified or author-verified), the
   fragment being kept as the claim's text and never as a name.

Option 3 is the only one that needs no schema change and no retrieval change, and it is the
one the measured evidence supports: **the name must be verified, not truncated.**

---

## 4. ID stability and the migration lineage

The concept id is `digest(workspaceId + "|concept|" + name.strip().toLowerCase())`
(`DocumentImporter.java:338-341`) — an FNV-1a digest, prefixed `c-`. It is **not** a
surrogate key, and the name is its only input. Therefore:

* **Renaming a concept changes its id**, and the id is referenced by
  `claim.concept_id`, `representation.concept_id` (and, through the representation id,
  every representation row), `prerequisite.concept_id` and `prerequisite.requires_concept_id`,
  `epistemic_case`, `hebbian_binding`, `assessment_item`, `learning_error`, `learning_state`,
  `learning_transition`, `assessment_outcome`, and `dialogue_turn.concept_ids` (a CSV).
* **The claim id does NOT change**: it is `digest(workspaceId + "|" + contentHash + "|"
  proposition)` (`DocumentImporter.java:343-346`), so the claim's identity, its evidence and
  the evaluation golds (which are propositions) survive a concept-identity change. This is
  the property that makes the migration feasible.
* **SQLite declares no FOREIGN KEY** for any of these columns (`PRAGMA foreign_keys=ON` is
  set, but no `REFERENCES` clause exists in the schema) — a partial migration is **not**
  caught by the database. `KnowledgeStore.validate` catches only claims, representations and
  prerequisites; the learning, hebbian, assessment, epistemic-case and dialogue rows are
  **outside** its coverage and need an explicit orphan check.

### The lineage a migration would have to carry

1. **An append-only `concept_lineage(old_id, new_id, reason, created_at)`** table (new
   schema version, `schema_meta` bump) — the old row is **marked superseded, never deleted**.
2. **A copy-forward migration**, not a rewrite: new concept rows with new ids; the old rows
   kept and marked; every referencing row rewritten **in one transaction** through the
   existing atomic `applyChanges` path; the `prerequisite` graph re-checked for acyclicity
   (`KnowledgeStore.validate` does this).
3. **Representation ids must be recomputed** (they embed the concept id), and the
   `refines` chain must be carried across explicitly.
4. **A workspace snapshot taken before the migration is the rollback reference** — the store
   already has `takeWorkspaceSnapshot` / `restoreWorkspaceSnapshot`
   (`KnowledgeStore.java:1532`, `:1565`). A migration without a pre-migration snapshot is
   not reversible, and the store's own replacement contract already treats a missing
   snapshot as fail-closed.
5. **A post-migration orphan audit** over the tables `validate` does not cover.
6. **Re-publication of every artefact that quotes a concept id** — the C2A results JSON, the
   coverage baseline, the B3 evidence report — and **re-measurement of the 18/29 baseline**,
   because the retriever's searchable text is `proposition + concept name + definition`
   and both the name and the definition change.

**Cost.** On the seed workspace the migration touches 326 concept rows, 348 claim rows, 335
representation rows and 0 prerequisite rows; on the full measured corpus 813 / 888 / ~733.
That is small in rows. The real cost is **the re-measurement**: the 18/29 baseline, the
held-out set and the three published artefacts must be re-run and re-published against the new
ids, and the migration rehearsal itself must be re-run after every future identity change.
The row counts here are measured; the effort estimate is a judgement — **[speculative]**.

---

## 5. The acceptance measurement for the future implementation

| # | the measure | how it is measured | the baseline it must beat | can this corpus support it? |
|---|---|---|---|---|
| A1 | **concept identification precision / recall** | a **frozen, hand-labelled** set of ≥150 subject spans (deterministic draw, labelled against the source sentence: *is a concept / is not a concept*) | precision **30 %** (12/40, CI 18–45 %) | **partly** — the labels are cheap to make on 9 documents, but the set is single-domain, single-register and English-only |
| A2 | **definition coverage** | (i) the fraction of concepts with a usable definition; (ii) the fraction of those a human marks as *about that concept* | 0 usable; 121/326 = **37.1 %** adjacency-passing; human-verified share **not measured** | **yes** for (i); (ii) needs a labelled subset of ~100 definitions |
| A3 | **relation usefulness** | the fraction of typed relations a human marks **true and correctly directed** (not the count) | 19 candidates, 7 published by C2A; human-verified share **not measured** (a hand look finds wrong endpoints) | **yes** for a sample of ~50 relations |
| A4 | **retrieval effect** | the production retriever at floor 0.25 on the frozen 29 cases **and** on a new held-out set: recall@K, MRR | 18/29; recall@5 0.3214; MRR 0.4615; supported-answer 0.3571; unsupported-answer 0.6429 | **no** — a 29-case single-domain set can detect a regression, not establish a gain; a new sealed held-out set is required |
| A5 | **false-positive unsupported claims** | the rate of answers that assert what the corpus does not support, and the number of claims asserted without evidence | unsupported-answer rate **0.6429** on the 20 ask cases | **no** — needs the fresh adversarial set of P0.3 |
| A6 | **real answer accuracy** | answer-level correctness on the new held-out set, not the retriever-level metric | not measured at the answer level | **no** |
| A7 | **cost** | import time, store size, retrieval latency, migration wall-clock, rows rewritten | mean retrieval 32 ms (baseline path), 10 ms (retriever) | **yes** |
| A8 | **ID stability** | the number of concept ids that change, and the number of references rewritten, per identity-policy change | — | **yes** |

**The rule that governs the promotion:** a candidate is accepted only if **A1 and A2 improve**
**and** A4 does not regress **and** A5 does not regress (the directive: *promote only on
independent data without degrading refusal correctness*). A1 and A2 are computable on this
corpus; A4–A6 are not, and no candidate may be promoted on this corpus alone.

### What this corpus can and cannot support — stated plainly

* **Nine documents, one domain (plate tectonics), one language (English), two licence
  families** (US-PUBLIC-DOMAIN ×1, CC-BY-SA-4.0 ×8), one register (encyclopaedic prose plus
  one USGS explainer), no dialogue, no tabular prose, no multi-author documents, no
  non-English text (the importer supports Russian; the corpus holds none).
* **It CAN** measure the identity defect, the definition/relation extraction coverage, the
  migration rehearsal, and a retrieval *regression* check in one domain.
* **It CANNOT** establish general concept-identification quality, cross-domain
  generalisation, a retrieval *gain*, or anything about a different licence family. The
  closed verb/function lists used in §2 were written **against this corpus**, so the NP-shape
  rates are an **upper bound** on their own generalisation — **[speculative]** outside this
  domain.

---

## 6. The recommended direction (a direction, not an implementation)

1. **Adopt (d) first.** The definition policy is orthogonal to the identity question,
   independently measurable, and the only candidate that raises a measured coverage number
   (0 → 121/326 adjacency-passing, subject to a human-verified subset). Write the definition
   **verbatim, with its source span**, and keep the concept's `definition` field nullable —
   a concept without a definition is honest.
2. **Do not adopt (b) as implemented.** It corrupts meaning silently (34 referents narrowed,
   42 claims orphaned). If a name filter is adopted at all, it must be **reject-only**
   (a name that cannot be a concept is *not claimed as one*), never **truncate-only**.
3. **Move to (c) as the identity shape** — a **referent key** of (normalised lemma, source
   document, anchor span) with a display name and an alias list — **but do not adopt it until
   the alias/resolution policy exists**, because the key alone does not resolve the referent;
   it only anchors it. The alias layer is where correctness will live, and it is the part
   that must be measured on human-verified examples.
4. **Keep every claim.** A claim whose subject is a fragment keeps its own text as the
   retrievable unit. Under the current schema this means either a claim-scoped record
   (option 2 in §3) or a nullable `concept_id` (option 1); both are production changes and
   are owner-gated. **No evidence is deleted to make a rate go down.**
5. **Fix the verb list separately** (§1.3): the nouns in `DECLARATIVE_VERBS` (`result`,
   `process`, `form`, `state`, `work`) are boundary errors, and the fix is bounded,
   deterministic and measurable on the same instrument.

---

## 7. What was NOT verified

* **No retrieval effect was measured for any alternative.** Nothing was wired into the
  production retriever, so **no recall@K / MRR delta exists**. The retriever composes
  `proposition + concept name + definition` (`KnowledgeRetriever.java:148-154`), so the
  numbers must be re-measured before any promotion. **[speculative]** the effect is small
  but of unknown sign.
* **The hand-labelled precision sample is n = 40** (30 %, CI 18–45 %), not the ≥150 the
  acceptance measurement requires. It is a sample of one corpus.
* **The precision of (b) and (c) was not measured by hand** — only (a) was.
* **The migration was not executed.** The lineage in §4 is a sketch; the row counts are
  measured, the effort estimate is a judgement. **[speculative]**
* **The published 61 is not reproducible from the repository** (the 120-word list is not
  published); the reconstruction here is one list that yields 61. The published **124**
  substring pairs are not reproducible under either rule tried.
* **The relation counts (19 / 29) are this measurement's own re-implementation** of the
  freeze §3.2 rule over every proposition of every concept; the published C2A figure
  (7 relations over 326 concepts) uses a different scope and is **not** reproduced. The
  (a)-vs-(b) comparison is internally consistent; the absolute numbers are not comparable to
  C2A's.
* **The definition count (121) is one above the published 120** — the sentence set differs
  marginally (document sentences vs claim propositions). The reproduction is close, not
  exact.
* **The Python replication** used for the fast iteration reproduces 326/348 exactly and was
  confirmed by the JVM run for the aggregate; it is **not** part of the evidence — the
  evidence is the JUnit XML.

---

## 8. Commands

```
# the instrument (the only build run; the lock serialises it with the other agents)
export JAVA_HOME=$HOME/jdk25
flock /tmp/gradle-matrix.lock bash -c 'rm -rf matrix-core/build/test-results && \
  ./gradlew :matrix-core:test --tests "io.matrix.knowledge.conceptidentity.*" --offline --console=plain'
# final run: BUILD SUCCESSFUL, 6 tests, 0 failures, 0 errors
# JUnit XML (the evidence):
#   matrix-core/build/test-results/test/TEST-io.matrix.knowledge.conceptidentity.ConceptIdentityBaselineTest.xml
```

The command was run six times while the instrument was being written (compile errors and two
miscounts in the assertions were fixed); the **final** run is the one whose XML is retained.
No other build was run. No production file was modified. Nothing was staged or committed.

---

## 9. Related documents

* `docs-v2/engineering/C2A-CONCEPT-COVERAGE-BASELINE.md` — the published baseline this
  document re-measures and, in two places, corrects the reproducibility of.
* `docs-v2/engineering/C2A-CANDIDATE-FREEZE.md` — the frozen definition and relation rules
  reused here.
* `docs-v2/engineering/C2A-FINAL-REPORT.md` — the wave report that named the identity layer
  as the blocker.
* `docs-v2/engineering/EPISTEMIC-EXECUTION-PLAN.md` — the plan this document serves (P1).
