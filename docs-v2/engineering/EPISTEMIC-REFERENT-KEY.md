# EPISTEMIC-REFERENT-KEY — the resolution and alias policy of the referent key

**Status:** `MEASURED` (research and instrumentation only; no production file was changed,
no concept was renamed, no id moved, nothing was migrated)
**Date:** 2026-11-16 · **Scope:** P1 — CONCEPT-MODEL REPAIR, the identity half
**Authority:** the owner's directive of 2026-11-15, §3 P1 and §11 Gate 4, and the research
it rests on — `docs-v2/engineering/EPISTEMIC-CONCEPT-IDENTITY.md` (the measured root cause
and the four alternatives) and `docs-v2/engineering/EPISTEMIC-CONCEPT-DEFINITIONS.md` (the
definition half, already implemented).
**Instruments:** `matrix-core/src/main/java/io/matrix/knowledge/concept/research/**` (the
policy, the key and the resolver — a clearly-marked research package) and
`matrix-core/src/test/java/io/matrix/knowledge/concept/research/**` (the measurements).
**Evidence:** the JUnit XML of the run — 9 tests, 0 failures, 0 errors (§9).

---

## 0. What this document is, and what it is not

It is the **resolution and alias policy** the research left open, and its measurement.

The research recommended the **shape** of the referent key — a normalised lemma + the source
document + the anchor span, with a display name and an alias list — and said, in the same
sentence, that *"the resolution and alias policy is the unsolved part"*, that *"the alias
layer is where correctness will live"*, and that the key *"must not be adopted until the
alias/resolution policy exists"*. This document is that policy, designed, and measured.

**It is NOT a migration and NOT an implementation.** The identity half is **owner-gated**:
the concept id is referenced by ten tables and SQLite declares no foreign keys, so a
partial migration would be invisible. Nothing was renamed, no id changed, no claim moved, no
schema changed, and the owner's store was never touched — every measurement ran against a
**temporary** database.

**It is NOT a promotion.** The directive's rule is *"promote only on independent data without
degrading refusal correctness"*, and the research is explicit that this corpus cannot
support a retrieval gain claim. §4.6 measures the retrieval effect for the first time and
the answer is a **null result**, published as such.

---

## 1. The design

### 1.1 The key

```
ReferentKey = (normalised lemma, source document, anchor span)
```

* the **lemma** says *what* is denoted, normalised so that a spelling variant, a plural, a
  possessive and a determiner do not make three referents. It keeps the **modifier
  chain**, which is what makes "continental crust" and "oceanic crust" two lemmas and
  therefore two referents;
* the **source document** says *where* the reference occurs, so that the same word in two
  documents is two occurrences — the **policy** decides whether they are one referent, and
  the key alone does not;
* the **anchor span** says *which part* of the document, in the convention the repository
  already declares (`io.matrix.knowledge.ClaimSpan`: characters of the extracted text, the
  start inclusive, the end exclusive). It is what makes a key **verifiable** — a reader can
  be sent to the exact characters the referent was read at.

The **key of a referent** is the key of its **earliest** member, so that the key does not
move when a later mention is added.

### 1.2 The resolution — four rules, in this order

| # | the rule | what it decides |
|---|---|---|
| **R1** | **reject, never truncate.** A surface form whose head is a function word, whose head is a word the subject extractor stops at, whose first word is a class word, whose head is a class of persons, whose head is a discourse nominal with nothing that specifies it, that is longer than a phrase, or that carries a caption/dash artefact, is **not a referent**. Its claim keeps its own text and gets **no key**. | the referent test |
| **R2** | the **lemma** = the whole normalised surface phrase: the case folded, the leading "the " stripped, the possessive stripped **per word**, and the plural normalised by the English rule. | the identity of a surface form |
| **R3** | the **anchor** = the claim's own span, or "unknown" when the context says the whole document was read. | the verifiability of a key |
| **R4** | the **resolution**: two accepted claims with the same lemma in the same document are one referent; two clusters with the same lemma in **different** documents are one referent when the lemma is **specific**, and — for a **bare head** — when their **context signatures** overlap. | what is one referent |

**R1 is the reject-only rule the research insisted on.** The NP-boundary filter was
**rejected** because it dropped conjuncts and silently narrowed 34 referents
(`Mars and Venus` → `Mars`); a name that cannot be a concept must therefore **not be
claimed as one**, never truncated into one. **No evidence is deleted**: the rejected claims
stay in the store, keep their text and stay retrievable.

### 1.3 The alias layer

A **declared** table of equivalences and distinctions, stated over **lemmas**, never over a
claim id, a case id or a corpus phrase:

| the canonical lemma | the variant | the kind | the reason |
|---|---|---|---|
| seafloor spreading | sea-floor spreading | SPELLING | the corpus writes the term with and without the hyphen |
| slab pull | slab pull force | SYNONYM | "the slab pull force" and "slab pull" are the same force |
| lithosphere | earth lithosphere | SYNONYM | "Earth's lithosphere" and "the lithosphere" are the same layer |
| plate motion | motion of the tectonic plate | PARAPHRASE | "the motion of the tectonic plates" and "plate motion" are the same motion |
| hotspot track | track of hotspot | PARAPHRASE | "the tracks of hotspots" and "hotspot tracks" are the same feature |
| divergent boundary | divergent plate boundary | SYNONYM | the corpus writes both |
| continental drift | wegener theory of continental drift | SYNONYM | "Wegener's theory of continental drift" and "continental drift" are the same theory |
| crust | oceanic crust | **DISTINCT** | the generic crust and the oceanic crust are not one referent |
| crust | continental crust | **DISTINCT** | the generic crust and the continental crust are not one referent |
| plate | nazca plate | **DISTINCT** | the generic plate and one named plate are not one referent |
| plate | pacific plate | **DISTINCT** | the generic plate and one named plate are not one referent |
| trench | middle america trench | **DISTINCT** | the generic trench and one named trench are not one referent |

**What the alias table is NOT.** It is the **weakest part of the policy**: it was written
by hand against this corpus, it is fitted to it, and **it is [speculative]**. §4.3 measures
how many of its entries the corpus actually exercises — an entry that never fires is not
evidence of anything. And the **DISTINCT** entries are, today, a **guard and not a rule**:
the lemma rule already keeps the generic and the specific apart, so the entries state an
intention that another rule would otherwise override. **The homonym property is therefore
carried by the lemma, not by the alias layer** — an honest statement of where the
correctness lives today.

### 1.4 The display name and the aliases

The **display name** of a referent is its most frequent surface form (the lexicographically
smallest on a tie). The **aliases** are the referent's other surface forms plus the declared
alias variants. The retriever's searchable text is `proposition + concept name + definition`
(`KnowledgeRetriever.java:170-172`), so **the aliases enter the retrieval through the
alias surface forms** — which is why the retrieval effect is measurable without a migration
(§4.6).

---

## 2. The methodology

| the element | the value |
|---|---|
| the corpora | the **seed** (the two licensed documents of `corpus/plate-tectonics`) and the **wider** (the seed + the seven documents of `research-pool/plate-tectonics`); nine documents, one domain, one language |
| the import | the **production** importer through the **production** workbench into a **temporary** database |
| the instrument | `io.matrix.knowledge.concept.research.ReferentResolver` over the production claims |
| the A1 instrument | the **frozen** `HandLabelledSpans` of 150 spans, read **reflectively** from the A1 instrument itself, so that the frozen set stays the single source of truth and this instrument cannot drift from it |
| the retrieval instrument | the official dataset (`epistemic-eval/cases.json`), the official `judgeAnswer`, the official `goldOf`, and the production retriever at the ranking floor 0.0 |
| the calibration | the CURRENT arm reproduces the published official ask-case failures — `A-02, A-04, A-05, A-06, M-01, P-01, P-02, M-03` — and the published recall@1 0.2024, recall@5 0.3214 and MRR 0.5077 |
| the interpolation | none: the key, the policy and the resolution are deterministic |

**The corpora are the measured ones, and the baseline is reproduced**: 348 claims / 326
concepts on the seed and 888 claims / 813 concepts on the wider corpus. The instrument
**asserts** those numbers, so a silent change in the production acquisition path fails the
instrument instead of producing new numbers quietly.

---

## 3. The fragmentation — what the key costs

| the measure | the seed (2 documents) | the wider (9 documents) |
|---|---|---|
| claims | 348 | 888 |
| **the current splitter: the concepts** | **326** | **813** |
| **the control: the source-anchoring alone** (the key with no rejection rule) | **325** | **824** |
| **the policy: the key + the reject-only rule** | **163** | **465** |
| the claims the policy accepts | 168 (48 %) | 496 (56 %) |
| the claims the policy rejects | **180 (52 %)** | **392 (44 %)** |
| the referents per accepted claim | 0.97 | 0.94 |

**The research's expectation is confirmed on the wider corpus and inverted by the rejection
rule.** The source-anchoring alone costs **+11 referents** on the wider corpus (824 against
813: the 25 lemmas attested in more than one document become two referents each) and **−1**
on the seed. But the **reject-only rule dominates**: with it, the key yields **465** referents
for the wider corpus — **348 fewer** than the current 813 — because **44 % of the claims are
not referents at all** and the key does not pretend otherwise.

**The cost of that is the headline of this document.** The policy rejects **180 of the
seed's 348 claims (52 %)** and **392 of the wider 888 (44 %)**. A claim with no concept
needs either a **nullable `concept_id`** (a schema change plus a validation change plus a
retriever change) or a **claim-scoped record** whose name is the claim's own text (the
research's option 2). **Both are production changes and both are owner-gated.** The key is
not a rename; it is a **population change**, and the population change is the expensive part.

### 3.1 Why the claims are rejected — the measured reasons

| the reason | the seed | the wider | an example |
|---|---|---|---|
| `FUNCTION_HEAD` — the head is a function word | 91 | 205 | `It`, `There`, `The`, `To` |
| `TOO_LONG` — more than 12 words: a clause, not a phrase | 38 | 94 | `The Earth's unchanging size implies that the crust must` |
| `CLASS_OPENER` — the surface opens with a class word | 21 | 39 | `Such volcanoes`, `Many were published` |
| `GENERIC_HEAD` — the head is a class of persons or of unnamed things | 17 | 32 | `Scientists`, `Geologists` |
| `ARTEFACT` — a caption marker, a dash or a colon | 9 | 12 | `Lava Fountains, Krafla Volcano [35 k] …` |
| `DISCOURSE_HEAD` — the head is a discourse nominal with nothing that specifies it | 3 | 9 | `The mechanism`, `This theory` |
| `CLAUSE_CUT` — the head is a word the extractor stops at | 1 | 1 | `Plate tectonics came to` |
| **the total** | **180** | **392** | |

---

## 4. The measurements

### 4.1 The A1 precision — the frozen 150

The draw is over the **concepts the current path produced**, so it measures **precision** and
not recall: the referents the current path MISSED are out of frame, and the policy's recall
over the corpus's true referents is **not measurable here**.

| the policy | what it accepts of the 150 | its precision | the 95 % Wilson interval |
|---|---|---|---|
| **the current splitter** (it accepts every concept it produces) | 150 | **35/150 = 23.33 %** | **[17.28 %, 30.72 %]** |
| **the referent key's policy** | **73** | **33/73 = 45.21 %** | **[34.31 %, 56.58 %]** |

**The intervals do not overlap**: the policy's lower bound (34.31 %) is above the splitter's
upper bound (30.72 %). The precision of the concept identification **nearly doubles**.

**The cost, stated next to the gain**: the policy rejects **77 of the 150** spans the
current path calls concepts, and **40 false positives remain** among the 73 it accepts. The
residual false positives are the classes the closed lists do not reach — a clause fragment
whose head happens to be a content word (`Before uplift, the area where they stand`), an
appositive (`This theory, called "surge tectonics",`), a prepositional phrase with a comma
(`At a seafloor spreading ridge, plates`), a generic nominal with a modifier (`The evidence
for such an erstwhile joining of these continents`). **A closure that reaches every fragment
is not a closed list of words; it is a parser, and the directive forbids building one.**

### 4.2 The four properties the directive names

A hand-labelled set of **14** cases from the corpus's own sentences, with the labelling
protocol written down in the instrument (the same protocol the A1 set uses: a leading
discourse adverbial is stripped before the head is judged).

| the property | the cases | the policy's decisions |
|---|---|---|
| **the same word in different contexts stays distinct** | 4 | **4 / 4** ✓ |
| **a synonym does not fragment a concept** | 4 | **4 / 4** ✓ |
| **a paraphrase of the same term does not fragment it** | 3 | **3 / 3** ✓ |
| **a genuine homonym does not merge** | 3 | **3 / 3** ✓ |

**What each property is decided by, honestly:**

| the property | the mechanism that decides it |
|---|---|
| the same word in different contexts | **the modifier chain inside the lemma** (`oceanic crust` ≠ `continental crust`), and for a bare head the **context signature** — the content words of the claim's own sentence |
| the synonym | **the declared alias table** (the lemma rule alone would keep them apart) |
| the paraphrase | **the declared alias table** |
| the homonym | **the lemma rule** (the DISTINCT entries are a guard today, §1.3) |

**The homonym property is therefore carried by the lemma, and the synonym and paraphrase
properties by the alias table** — which is the answer to the question the research left
open: **the alias layer is where the synonym and paraphrase correctness lives, and the
lemma is where the homonym correctness lives.** A policy that wanted the alias layer to
carry the homonyms too would need a *sense* annotation, which this corpus cannot support.

### 4.3 The definitions' coverage (A2)

| | the concepts | with a definition | the coverage |
|---|---|---|---|
| **the current path** | 326 | 120 | **36.8 %** |
| **under the key** | 163 referents | 74 | **45.4 %** |

The absolute number falls (120 → 74) because the population falls (326 → 163); the
**rate rises by 8.6 points**, because the fragments that had a definition attached to them
are no longer concepts. The definitions stay the corpus's own sentences with their spans
(the definition half is unchanged and untouched by this document).

### 4.4 The relations' usefulness (A3)

The frozen §3.2 rule produces **7** relation candidates on the seed corpus. **2** of them
have both endpoints accepted by the policy; **5** do not — and the 5 are exactly the ones
the definition half's own hand-labelling found to have a **fragmentary endpoint**:

| the relation | the endpoint the policy rejects |
|---|---|
| PRODUCES | `Picture two giant conveyor belts, facing each other but slowly moving in opposite directions as they` |
| IS_A | `Plate tectonics (from Latin tectonicus, from Ancient Greek τεκτονικός …)` |
| PRODUCES | `The processes that` |
| PART_OF | `Tectonic plates may` |
| PRODUCES | `Starting from the idea (also expressed by his forerunners) that the present continents once` |

**The policy agrees with the human judgement on all seven.** That is an **independent
confirmation** of the rejection rule, on a different measurement than the A1 set: the human
labelling of the relations was made without the policy, and the policy rejects the same
five endpoints.

### 4.5 The cost (A7)

| the cost | the value |
|---|---|
| the seed import (the production path, unchanged by this document) | **1 650 ms** |
| the wider import (nine documents) | **10 500 ms** |
| the mean retrieval, CURRENT / KEY / KEY_WITH_DEFINITIONS | **69 / 61 / 63 ms** |
| the resolution itself | in-process, over the claims of the workspace; the instrument's whole run is under 2 s |

The policy adds **no** production cost: it is a research instrument, it is not wired into
the production path, and the retrieval timings above are the production path's own.

### 4.6 The retrieval effect (A4 and A5) — the first measurement for any alternative

The research was explicit: *"no retrieval effect has been measured for any alternative"*.
Here it is, for the alias layer, **without migrating anything**: the arm writes the alias
surface forms into the `concept.definition` column of a **temporary** store — the column
the retriever appends to the searchable text — so the concept ids, the claims and the rows
do not move.

**The official 20 ask cases:**

| the arm | passed | answered | the unanswerable abstained | answered without the gold | recall@1 | recall@5 | recall@10 | recall@20 | the MRR | the mean ask |
|---|---|---|---|---|---|---|---|---|---|---|
| **CURRENT** (the production state) | **12 / 20** | 14 | **6 / 6** ✓ | 8 | 0.2024 | 0.3214 | 0.3690 | 0.5952 | 0.5077 | 69 ms |
| **KEY** (the alias surface forms enter) | **12 / 20** | 14 | **6 / 6** ✓ | 8 | 0.2024 | 0.3214 | 0.3690 | 0.5952 | 0.5077 | 61 ms |
| **KEY_WITH_DEFINITIONS** | **11 / 20** ✗ | 15 | **5 / 6** ✗ | 9 ✗ | 0.2381 | 0.3214 | 0.3690 | 0.5952 | 0.5422 | 63 ms |

**The frozen held-out set (16 cases): every arm is identical** — 8/16 passed, 12 answered,
4 abstentions, 4 answered without the gold, 31–32 ms.

**Three findings.**

1. **The alias layer alone changes NOTHING.** Every metric of the KEY arm is byte-identical
   to CURRENT. The aliases of this corpus are too rare to reach a question. **A null result,
   published as such.**
2. **The alias + definitions arm DEGRADES the refusal**, exactly as the definition half's own
   measurement found: `U-04` — an **unanswerable** case — is **answered** instead of
   abstained (the abstention 6/6 → 5/6) and the answered-without-the-gold rises 8 → 9.
   The recall@1 (+0.036) and the MRR (+0.035) rise at the price of the refusal. **The
   definitions' harm is reproduced under the key, and it is not caused by the key** — it is
   caused by the wider searchable text, whatever put it there.
3. **The calibration holds**: the CURRENT arm reproduces the published official row
   (12/20 of the ask cases, the eight named failures, recall@1 0.2024, recall@5 0.3214,
   MRR 0.5077 against the published 0.5078 — a rounding difference), and the held-out set is
   identical in every arm.

**A4 and A5 under the alias layer: no regression and no gain. Under the alias layer PLUS
the definitions: A5 regresses.** Under the owner's promotion rule — *"A1 and A2 improve AND
A4 does not regress AND A5 does not regress"* — the alias layer alone is neutral on A4/A5
and improves A1 and A2; the definitions in the searchable text are still forbidden.

---

## 5. The comparison (Gate 4)

| the measure | (a) the current splitter | (d) the strict source-span rule (implemented) | (c) the referent key + the policy |
|---|---|---|---|
| **A1** the concept-identification precision | 35/150 = **23.33 %** [17.28 %, 30.72 %] | 35/150 = 23.33 % (the names and the ids do not change, so it cannot move) | **33/73 = 45.21 %** [34.31 %, 56.58 %] — **the intervals do not overlap** |
| **A1** the accepted share of the drawn spans | 150/150 | 150/150 | 73/150 |
| **A2 (i)** the definition coverage | 0 usable; 121/326 = 37.1 % adjacency-passing | **120/326 = 36.8 %**; the human-verified share 24/25 = 96 % | **74/163 = 45.4 %** |
| **A3** the relation usefulness | 19 candidates (the research's own count); the definition half measured **7** candidates and **0/7** human-verified | 7 candidates, 0/7 human-verified | 7 candidates, **2 of them have both endpoints accepted** — the policy rejects exactly the 5 the human labelling rejected |
| **A4** the retrieval | 12/20 of the official ask cases; recall@1 0.2024, recall@5 0.3214, MRR 0.5077; the official 18/29 with the identical eleven failures | **unchanged** — the definitions are deliberately not in the searchable text | **identical** (the alias arm): 12/20, recall@1 0.2024, MRR 0.5077; the held-out 16 identical |
| **A5** the false positives | 8 answered without the gold; the abstention **6/6**; the unsupported rate 0.3000 | **unchanged** | **unchanged** (the alias arm); **regressed** with the definitions (5/6, 9 without the gold) |
| **A6** the answer accuracy | not measurable on this corpus | not claimed | not claimed |
| **A7** the cost | the import 1 650 ms; the mean ask 69 ms | the definitions are recorded as their own records (+120 rows) | the import is unchanged; the mean ask 61–63 ms; the policy itself is free |
| **A8** the id stability | — | **no id changed** | **no id changed** — the key is a research artefact; the ids are untouched |
| the population | 326 concepts (the seed), 813 (the wider) | 326 concepts | **163 referents (the seed), 465 (the wider)** — and **180 / 392 claims have no concept** |
| the promotion verdict | the baseline | **adopted** (the definition half) | **the shape is right and the alias layer is now measured — but the identity half stays owner-gated** |

**What the comparison says.** The referent key is the only candidate that moves **A1** (the
precision of the concept identification nearly doubles, with non-overlapping intervals) and
the only one that moves **A2's rate**. It is **neutral** on A4 and A5 when the alias layer
alone enters the searchable text. And it carries a cost the other two do not: **44–52 % of
the claims lose their concept**, which needs a schema decision that is the owner's.

---

## 6. The negative results, published as such

1. **The alias layer has no measurable effect on the retrieval.** Every metric of the KEY
   arm is identical to CURRENT. The corpus's aliases are too rare to reach a question. **A
   null result.**
2. **The definitions in the searchable text still cost a case and the refusal.** The
   KEY_WITH_DEFINITIONS arm loses `U-04` (the abstention 6/6 → 5/6) and gains one
   answered-without-the-gold. This **reproduces** the definition half's own finding under a
   second instrument.
3. **The policy does not reach every fragment.** 40 false positives remain among the 73
   accepted spans of the frozen 150, and they are **not** of a kind a closed word list
   fixes: they are clause fragments whose head is a content word, appositives, and
   prepositional phrases with a comma.
4. **The source-anchoring does not increase the concept count on the seed** (325 against
   326) and increases it by 11 on the wider corpus (824 against 813). The research's
   generalisation that the key "increases the concept count" is **corpus-dependent**, and
   the reject-only rule inverts the sign on both corpora.
5. **The research's plural normalisation is defective**, and this document does not inherit
   it: the rule stripped "es" from every word ending in "es" that does not end in "ses", so
   "plates" became "plat" and "zones" became "zon". The policy uses the English rule
   ("-ies" → "-y"; "-sses", "-xes", "-ches", "-shes" lose the "es"; everything else that
   ends in a single "s" loses it). **This changes the lemma counts**, and it is the reason
   the control yields 325 and 824 where the research measured 299 on the seed.
6. **The relation usefulness was not measured for the key on a human-verified set.** What
   was measured is that the policy rejects the 5 fragmentary endpoints the human labelling
   rejected. The **0/7 human-verified precision of the current path is unchanged** — the key
   does not make a relation TRUE.

---

## 7. The migration rehearsal plan — a plan, not a migration

**Nothing in this section was executed.** The identity half is owner-gated: the concept id
is referenced by ten tables (`claim.concept_id`, `representation.concept_id` — and through
the representation id every representation row —, `prerequisite.concept_id`,
`prerequisite.requires_concept_id`, `epistemic_case`, `hebbian_binding`, `assessment_item`,
`learning_error`, `learning_state`, `learning_transition`, `assessment_outcome` and
`dialogue_turn.concept_ids`), and **SQLite declares no FOREIGN KEY** for any of them, so a
partial migration is **invisible** to the database.

### 7.1 The steps

| # | the step | the detail |
|---|---|---|
| 1 | **the snapshot** | `takeWorkspaceSnapshot` **before** anything is written. The store already has `takeWorkspaceSnapshot`/`restoreWorkspaceSnapshot`; a migration without a pre-migration snapshot is not reversible, and the store's own replacement contract treats a missing snapshot as fail-closed. **The snapshot is the rollback reference.** |
| 2 | **the lineage table** | an append-only `concept_lineage(old_id, new_id, reason, created_at)` (a new schema version, a `schema_meta` bump). The old rows are **marked superseded, never deleted**. The `reason` names the rule that produced the new id (the lemma, the alias entry, the rejection). |
| 3 | **the copy-forward** | new concept rows with new ids; the old rows kept and marked; every referencing row rewritten **in one transaction** through the existing atomic `applyChanges` path. **No rewrite in place.** |
| 4 | **the representation ids** | recomputed — they embed the concept id — and the `refines` chain carried across explicitly. |
| 5 | **the prerequisite graph** | re-checked for acyclicity (`KnowledgeStore.validate` does this). |
| 6 | **the orphan audit** | an **explicit** audit over the tables `validate` does **not** cover — the learning, hebbian, assessment, epistemic-case and dialogue rows — because SQLite's foreign keys are not declared and `validate` would not catch a partial migration. The audit is a query per table, and it must run before and after the migration and report **zero** orphans on both sides. |
| 7 | **the claim-scoped records** | the **180 / 392 rejected claims** must keep their text and stay retrievable: either a nullable `concept_id` plus a validation change plus a retriever change, or a claim-scoped record marked *not a concept* (the research's option 2). **This is the decision that decides the schema.** |
| 8 | **the re-measurement** | the **18/29** official row (with the identical eleven failures), the **6/6** abstention, the **C2A `BASELINE-PATH` 17/25**, the frozen held-out set, the A1 precision on the frozen 150, the definitions' coverage, and the four published artefacts (the C2A results JSON, the coverage baseline, the B3 evidence report) — because the retriever's searchable text is `proposition + concept name + definition` and **both the name and the definition change**. |
| 9 | **the rollback** | `restoreWorkspaceSnapshot` of the pre-migration snapshot; the lineage table is left in place (append-only) so the rollback itself is auditable. |

### 7.2 The rehearsal the owner would need before deciding

1. a **rehearsal on a copy** of the seed workspace: the copy-forward of 326 concepts and 348
   claims, the recomputation of the representation ids, the lineage table, the orphan audit
   over the ten tables, and the rollback by snapshot restore — with the **count of the
   rewritten rows** as the number;
2. the **A8 number**: how many concept ids change and how many references are rewritten;
3. the **A4/A5 re-measurement** of §7.1 step 8 — the alias layer measured neutral here must
   be measured again on the migrated ids, because the searchable text changes;
4. the **decision on the rejected claims** (step 7) — the only decision that changes the
   schema.

---

## 8. What this corpus cannot support

* **Nine documents, one domain (plate tectonics), one language (English), two licence
  families**, one register (encyclopaedic prose plus one USGS explainer), no dialogue, no
  tabular prose, no non-English text (the importer supports Russian; the corpus holds
  none).
* **The recall of the policy is not measurable here.** The A1 draw is over the concepts the
  **current** path produced, so the referents the current path MISSED are out of frame. The
  measured 45.21 % is a precision over what the policy accepts, **not** a recall over the
  corpus's referents.
* **The closed word lists were written against this corpus.** They are an upper bound on
  their own generalisation — **[speculative]** outside this domain. The same is true of the
  alias table, of the property set and of the policy as a whole.
* **The corpus cannot establish a retrieval GAIN.** The directive's own promotion rule
  requires A4 and A5 not to regress; a gain would need a new sealed held-out set, which
  this corpus is not.
* **The A6 answer accuracy is not measurable** on this corpus and is not claimed.
* **The relation usefulness was not re-measured by hand under the key**; what was measured
  is the agreement of the policy with the existing hand-labelling on the endpoints.

---

## 9. Every rule chosen here, marked

| the rule | where it comes from | its status |
|---|---|---|
| the reject-only rule (R1) | the research's §6.2 | **the research's** |
| the whole-phrase lemma (R2) | the research's normalisation | **the research's**, with the plural rule **corrected** (§6.5) |
| the anchor (R3) | the repository's own span convention (`ClaimSpan`) | **the repository's** |
| the within-document cluster | this document | **[speculative]** — fitted to this corpus |
| the cross-document merge for a specific lemma | this document | **[speculative]** |
| the context signature for a bare head | this document | **[speculative]** — the rule is plausible and it decides the homonym cases, but no human set validates it directly |
| the alias table (12 entries) | this document, by hand | **[speculative]** — fitted to this corpus; §4.2 measures how many entries fire |
| the FUNCTION_WORDS / CLAUSE_CUT_WORDS / CLASS_OPENERS / GENERIC_HEADS / DISCOURSE_HEADS / DISCOURSE_ADVERBIALS lists | this document | **[speculative]** — 40 false positives remain |
| the 12-word cap | the research's NP rule | **the research's** |
| the strip of the leading discourse adverbial | the A1 labelling protocol | **the A1 instrument's** |
| the property set (14 cases) | this document, by hand | **[speculative]** — 14 cases of one corpus |
| the Wilson interval at 95 % | standard | **standard** |

---

## 10. The evidence

```
export JAVA_HOME=$HOME/jdk25
flock /tmp/gradle-matrix.lock bash -c 'rm -rf matrix-core/build/test-results && \
  ./gradlew :matrix-core:test --tests "io.matrix.knowledge.concept.research.*" --offline --console=plain'
```

| the scope | the tests | the failures | the errors | the XML |
|---|---|---|---|---|
| `io.matrix.knowledge.concept.research.ReferentKeyInstrumentTest` | 7 | 0 | 0 | `matrix-core/build/test-results/test/TEST-io.matrix.knowledge.concept.research.ReferentKeyInstrumentTest.xml` <!-- no-link: the JUnit XML is a runtime artefact of the run, not a repository file --> |
| `io.matrix.knowledge.concept.research.ReferentKeyRetrievalTest` | 2 | 0 | 0 | `matrix-core/build/test-results/test/TEST-io.matrix.knowledge.concept.research.ReferentKeyRetrievalTest.xml` <!-- no-link: the JUnit XML is a runtime artefact of the run, not a repository file --> |
| **the total** | **9** | **0** | **0** | |

The seven instrument tests are: the baseline is reproduced; the fragmentation is measured;
the A1 precision on the frozen 150; the four properties; the definition coverage; the
relations; the two intervals. The two retrieval tests are: the official ask cases in the
three arms; the frozen held-out set in the three arms.

**The build directory is shared** with the other agents: a concurrent run overwrites
`matrix-core/build/test-results`, so the XML of this measurement was copied out inside the
lock. **No production file was modified, nothing was staged or committed, and the owner's
store was never touched.**

---

## 11. What the owner would need to decide

1. **The population change.** The policy rejects 44–52 % of the claims. The owner must
   choose the shape of a claim without a concept: a nullable `concept_id` (a schema change
   plus a validation change plus a retriever change) or a claim-scoped record that is marked
   *not a concept* (the research's option 2). **This is the decision the key needs, and it
   is a schema decision.**
2. **The alias table.** It is hand-written and fitted to this corpus. The owner must say
   whether the alias layer is to be a **declared** table (auditable, small, and this
   corpus's) or a **derived** one (larger, and it needs a source of truth this corpus does
   not have).
3. **The verb list.** Unchanged by this document and still a separate candidate (the
   research's §1.3): `ClaimExtractor.DECLARATIVE_VERBS` holds nouns (`result`, `process`,
   `form`, `state`, `work`), and a noun in it is a boundary error. **Fixing it changes the
   names and therefore the ids**, so it is owner-gated as well.
4. **The migration.** §7 is a plan; the rehearsal in §7.2 is what would make it decidable.
5. **Whether the definitions should ever enter the searchable text.** The answer measured
   here is **no** (the refusal regresses), and it is the same answer the definition half
   reached. **The key does not change it.**

---

## 12. Related documents

* `docs-v2/engineering/EPISTEMIC-CONCEPT-IDENTITY.md` — the baseline and the four
  alternatives; the source of the shape this document completes.
* `docs-v2/engineering/EPISTEMIC-CONCEPT-DEFINITIONS.md` — the definition half, already
  implemented and measured; the source of the retrieval harness this document reuses.
* `docs-v2/engineering/EPISTEMIC-EXECUTION-PLAN.md` — the plan this document serves (P1).
* `docs-v2/engineering/C2A-CANDIDATE-FREEZE.md` — the frozen definition and relation rules.
