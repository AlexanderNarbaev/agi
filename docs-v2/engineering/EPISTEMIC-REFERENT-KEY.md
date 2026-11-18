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
**Evidence:** the JUnit XML of the run — 9 tests, 0 failures, 0 errors (§11).

> **Revision 3 (2026-11-16, the third pass).** The owner has decided: **OD-062 — the
> operating point is STRICT** (not the LENIENT point this document recommended), and
> **OD-061 — the schema fork is the claim-scoped record** (option B, the additive one).
> This revision records both decisions, names STRICT as the operating point everywhere,
> and adds **§8, the migration rehearsal** — the copy-forward, the lineage, the
> recomputed representation ids, the orphan audit (zero on the correct copy, non-zero on
> a deliberately broken one), the rollback and the re-measurement, all on COPIES.
>
> **Revision 2 (2026-11-16, the second pass).** The first version of this document
> reported a policy that accepted 73 of the frozen 150 spans and got 40 of them wrong
> (45.21 %). The owner's review was right: **a closed word list cannot fix a clause, an
> appositive or a concatenation.** This revision classifies the 40 false positives by
> cause, implements the **structural** part of the policy, and measures the result at two
> operating points. The false positives are now **2** (LENIENT) or **0** (STRICT), and the
> price is published next to them (§3, §4.1).

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
support a retrieval gain claim. §4.7 measures the retrieval effect for the first time and
the answer is a **null result**, published as such. §7 answers the promotion question with
the numbers.

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
  documents are two occurrences — the **policy** decides whether they are one referent, and
  the key alone does not;
* the **anchor span** says *which part* of the document, in the convention the repository
  already declares (`io.matrix.knowledge.ClaimSpan`: characters of the extracted text, the
  start inclusive, the end exclusive). It is what makes a key **verifiable** — a reader can
  be sent to the exact characters the referent was read at.

The **key of a referent** is the key of its **earliest** member, so that the key does not
move when a later mention is added.

### 1.2 The resolution — the lexical rules

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

### 1.3 The structural rules — the second pass

The first version of the policy was **lexical only**, and it was measured to accept 73 of
the frozen 150 spans with **40** false positives (§3). The 40 were classified, and 37 of
them are **structural**: a word list cannot see a clause, an appositive or a
concatenation. The structural rules are therefore bounded, deterministic and **named**:

| # | the rule | the structural fact it looks at | the constant |
|---|---|---|---|
| **S1** | the **embedded clause** | a relative pronoun or adverb ("that", "which", "who", "where") followed by more words: the head belongs to an embedded clause, not to the span | `RELATIVISERS` |
| **S2** | the **subordinator** | the span opens with a subordinating conjunction: it is a clause, whatever its head says | `SUBORDINATORS` |
| **S3** | the **participle** | a participle that is not the head: the span carries a clause | `PARTICIPLE_SUFFIXES` |
| **S4** | the **appositive** | the comma-delimited part is an insertion: it is not the head phrase and it does not **specify** the head | the comma in the tokenised span |
| **S5** | the **leading adverbial** | the span opens with a preposition or an adverbial and carries a comma: the part before the comma modifies the sentence, the head phrase is what follows | `PREPOSITIONS`, `DISCOURSE_ADVERBIALS` |
| **S6** | the **capitalisation** | a capitalised word in the middle of the span, between two lower-case words, not after a determiner, not a name's possessive and not followed by another capital: the span concatenates a heading and a sentence | `DETERMINERS`, `CAPITALISED_FUNCTION_WORDS` |
| **S7** | the **repetition** | a phrase of two or more words occurs twice: the span is a caption | — |

and three **morphological** rules that are neither lexical lists nor structural:
the **bare plural** (a plural with no modifier and no determiner names a class), the
**possessive without a noun** ("Wegener's" names a possessor but not what is possessed),
and the **head's role under a complement** (in "the theory **of** plate tectonics" the
complement modifies the head, so the head is "theory", not "tectonics").

**Two operating points, measured, not chosen silently.** The structural rules do not come
free: each removes false positives and also removes true referents, and which error matters
more is an **owner's** question, not a researcher's. The policy therefore has two levels
(`ReferentPolicy.Strictness`):

* **LENIENT** — the structural rules that, on the frozen set, remove **no** referent the
  hand labelling marks true;
* **STRICT** — LENIENT plus the "-ing" participle rule, which removes more false positives
  and more true referents.

**The operating point is STRICT (OD-062).** The owner chose the strict level: the
precision over what the policy accepts is **29/29 = 100 %** with **no** false positive,
at the price of **six true referents** the lenient level keeps (§4.1). LENIENT stays in
the code as the measured alternative, and every number below is labelled with the level
it was measured at.

### 1.4 The alias layer

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
by hand against this corpus, it is fitted to it, and **it is [speculative]**. The
**DISTINCT** entries are, today, a **guard and not a rule**: the lemma rule already keeps
the generic and the specific apart, so the entries state an intention that another rule
would otherwise override. **The homonym property is therefore carried by the lemma, not by
the alias layer** — an honest statement of where the correctness lives today.

---

## 2. The methodology

| the element | the value |
|---|---|
| the corpora | the **seed** (the two licensed documents of `corpus/plate-tectonics`) and the **wider** (the seed + the seven documents of `research-pool/plate-tectonics`); nine documents, one domain, one language |
| the import | the **production** importer through the **production** workbench into a **temporary** database |
| the instrument | `io.matrix.knowledge.concept.research.ReferentResolver` over the production claims |
| the A1 instrument | the **frozen** `HandLabelledSpans` of 150 spans, read **reflectively** from the A1 instrument itself, so that the frozen set stays the single source of truth and this instrument cannot drift from it |
| the retrieval instrument | the official dataset (`epistemic-eval/cases.json`), the official `judgeAnswer`, the official `goldOf`, and the production retriever at the ranking floor 0.0 |
| the calibration | the CURRENT arm reproduces the published official row: **18/29** with the identical eleven failures (`A-02, A-04, A-05, A-06, F-02, M-01, P-01, P-02, M-03, R-04, R-05`), recall@1 0.2024, recall@5 0.3214, MRR 0.5078, the abstention 6/6 |
| the interpolation | none: the key, the policy and the resolution are deterministic |

**The corpora are the measured ones, and the baseline is reproduced**: 348 claims / 326
concepts on the seed and 888 claims / 813 concepts on the wider corpus. The instrument
**asserts** those numbers, so a silent change in the production acquisition path fails the
instrument instead of producing new numbers quietly.

---

## 3. The 40 false positives, by cause — and what a structural test has to look at

The first version of the policy accepted 73 of the frozen 150 spans and got **40** of them
wrong. Every one was read and classified. **37 of the 40 are structural**; only **3** are
purely lexical.

| the cause | the count | the primary examples | what a structural test must look at | what it costs |
|---|---|---|---|---|
| **C1 the embedded clause** — the head belongs to a clause inside the span | **11** | "the area where they stand", "They also found that the oceanic crust", "the fact that the North American plate", "The concept that mountains", "because plate motions", "When the new crust", "a powerful source generating plate motion" | **the clause shape**: a relative pronoun or adverb with a complement, a participle that is not the head, a subordinating conjunction at the opening | a closed class of relativisers and subordinators plus a participle suffix test; the "-ing" test collides with the technical nouns ("seafloor spreading") and therefore belongs to the STRICT level |
| **C2 the appositive** — a comma-delimited insertion | **3** | "This theory, called \"surge tectonics\",", "This finding, though unexpected,", "An alternative explanation, though," | **the punctuation**: the comma-delimited part is not the head phrase, and it does not SPECIFY the head | the head phrase = the part before the comma; the appositive is excluded from the specifier count — no list needed |
| **C3 the leading adverbial** — the span is an adverbial plus a subject | **6** | "By the late 1920s, seismologists", "Since that time, many theories", "At a seafloor spreading ridge, plates", "Between 1975 and 1984, the displacements" | **the opening plus the punctuation**: the part before the comma modifies the sentence, the head phrase is the part after it | a class of prepositions and adverbials; the head phrase then has to pass the ordinary rules (which is where "many theories" and "seismologists" fall) |
| **C4 the caption** — a heading and a sentence concatenated | **6** | "Key principles The outer layers of Earth", "Role of water The role of water", "Defining plate boundaries Active plate boundaries", "Driving forces related to Earth rotation Alfred Wegener," | **the capitalisation map**: a capitalised word in the middle, between two lower-case words | the capitalisation of every word, position by position; the three refinements (not after a determiner, not a name's possessive, not followed by another capital) keep the rule off the proper nouns |
| **C5 the generic nominal** — the head is a class word, or the head's role is wrong | **9** | "The evidence for such an erstwhile joining of these continents", "One explanation for Venus's lack of plate tectonics", "Space-geodetic data", "This force", "The latter" | **the head's syntactic role**: in "the theory **of** plate tectonics" the complement modifies the head, so the head is "theory" and not "tectonics" | a class of complement prepositions; the head is then judged by the ordinary lists |
| **C6 the lexical residue** — the head is a word no list can be expected to contain | **3** | "The three most commonly", "Hess therefore", "a few" | — a **word list** only | a closed list, fitted to this corpus; this is the part that does not generalise |
| **C7 the artefact** — the quote, the possessive without a noun | **2** | "\"MORVEL plate velocity", "Wegener's" | **the character level**: an unbalanced quote; a possessive with no noun | a regex each |
| **the total** | **40** | | | **37 structural, 3 lexical** |

**The conclusion the owner's review pointed at, now measured:** the structural causes are
**37 of 40**, and every one of them is a **shape** — a clause, a comma, a capital, a
preposition, a complement — not a word. A word list cannot reach them. The structural rules
of §1.3 remove **38 of the 40** at LENIENT and **40 of the 40** at STRICT (§4.1).

---

## 4. The measurements

### 4.1 The A1 precision — the frozen 150, the two operating points

The draw is over the **concepts the current path produced**, so it measures **precision**
and not recall: the referents the current path MISSED are out of frame, and the policy's
recall over the corpus's true referents is **not measurable here**.

| the policy | what it accepts of the 150 | its precision | the 95 % Wilson interval | the false positives | the true referents it rejects |
|---|---|---|---|---|---|
| **the current splitter** (it accepts every concept it produces) | 150 | **35/150 = 23.33 %** | **[17.28 %, 30.72 %]** | — | — |
| **the structural rules only** (the residue lexicon removed) | 55 | **34/55 = 61.82 %** | **[48.61 %, 73.48 %]** | 21 | 1 |
| **the referent key's policy, LENIENT** | 36 | 34/36 = 94.44 % | [81.86 %, 98.46 %] | 2 | 1 |
| **the referent key's policy, STRICT — THE OPERATING POINT (OD-062)** | **29** | **29/29 = 100 %** | **[88.30 %, 100 %]** | **0** | **6** |

**The operating point is STRICT (OD-062).** The trade, in full: the strict level accepts
**29** of the 150 drawn spans against the lenient level's **36**; it keeps every referent
that survives the policy **except the six the lenient level would have kept** — the four
participial collisions ("In this understanding, plate motion", "The greater density of
old lithosphere relative to the underlying asthenosphere", "Seafloor spreading over the
past 100 to 200 million years", "Past plate boundaries within existing plates"), the
concatenation ("Two- and three-dimensional imaging of Earth's interior (seismic
tomography)") and the same 18-word span. The owner took the strict point: **no false
positive is worth a real concept.**

**The intervals do not overlap at any level.** The policy's lower bound (81.86 % at
LENIENT) is above the splitter's upper bound (30.72 %); even the **structural-rules-only**
counterfactual (48.61 %) is above it.

**The in-sample caveat, stated plainly.** The frozen 150 is the set the policy was tuned
on: the residue lexicon (the "structural rules only" row) was written by reading this
draw's false positives. So **94.44 % is an in-sample figure** and must not be read as a
generalisation. The honest reading is:

* the **structural** part of the gain — 23.33 % → **61.82 %** — is carried by rules that
  look at shapes and not at this corpus's vocabulary, and its interval is disjoint from
  the baseline's;
* the **lexical** part — 61.82 % → 94.44 % — is the in-sample fit, and it is exactly the
  part that does not generalise;
* **the out-of-sample precision is UNKNOWN and this corpus cannot measure it**: there is
  no second frozen set. A sealed held-out set would be needed before the number is
  quoted outside this document. **The in-sample caveat travels with every number of this
  document, and with the operating point: 100 % is the in-sample figure of the strict
  level, and 61.82 % is the structural floor that does not depend on the residue
  lexicon.**

**The two false positives that remain at LENIENT** (both are C1, and STRICT removes both):

| the span | the cause |
|---|---|
| "As a consequence, a powerful source generating plate motion" | the participial clause ("generating plate motion") — the "-ing" test, which is in the STRICT level because it collides with the technical nouns |
| "The Himalayas, towering as high as 8,854 m above sea level," | the trailing participial appositive — the same "-ing" test |

**The one true referent LENIENT rejects**: "The increasingly felsic nature of preserved
rocks between 3 and 2.5 billion years ago implies that subduction zones" — 18 words, over
the research's own 12-word cap. **The six STRICT rejects** are the four participial
collisions ("In this understanding, plate motion", "The greater density of old lithosphere
relative to the underlying asthenosphere", "Seafloor spreading over the past 100 to 200
million years", "Past plate boundaries within existing plates"), the concatenation
("Two- and three-dimensional imaging of Earth's interior (seismic tomography)") and the
same 18-word span.

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
| the homonym | **the lemma rule** (the DISTINCT entries are a guard today, §1.4) |

**The alias layer is where the synonym and paraphrase correctness lives, and the lemma is
where the homonym correctness lives.** A policy that wanted the alias layer to carry the
homonyms too would need a *sense* annotation, which this corpus cannot support.

### 4.3 The fragmentation — what the key costs

| the measure | the seed (2 documents) | the wider (9 documents) |
|---|---|---|
| claims | 348 | 888 |
| **the current splitter: the concepts** | **326** | **813** |
| **the control: the source-anchoring alone** (the key with no rejection rule) | **325** | **824** |
| **LENIENT: the key + the policy** | **107 referents** (110 claims accepted, **238 rejected**) | **335 referents** (362 accepted, **526 rejected**) |
| **STRICT: the key + the policy** | **90 referents** (93 accepted, **255 rejected**) | **292 referents** (319 accepted, **569 rejected**) |
| the share of the claims that would lose their concept | **68.4 %** (LENIENT) / 73.3 % (STRICT) | **59.2 %** / 64.1 % |

**The research's expectation is confirmed on the wider corpus and inverted by the rejection
rule.** The source-anchoring alone costs **+11 referents** on the wider corpus (824 against
813: the lemmas attested in more than one document become two referents each) and **−1** on
the seed. But the **reject-only rule dominates**: with it, the key yields **335** referents
for the wider corpus against the current 813.

**The cost of that is the headline.** **238 of the seed's 348 claims (68.4 %) and 526 of the
wider 888 (59.2 %) would lose their concept** at LENIENT. A claim with no concept needs
either a **nullable `concept_id`** (a schema change plus a validation change plus a
retriever change) or a **claim-scoped record** whose name is the claim's own text (the
research's option 2). **Both are production changes and both are owner-gated.** The key is
not a rename; it is a **population change**, and the population change is the expensive part.

**The structural rules made the deletion cheaper than it looks and more expensive than it
feels**: the first version of the policy rejected 180 of the seed's claims (52 %); the
structural rules reject **238** (68 %) while removing 38 of the 40 false positives. The
trade is explicit: **a junk concept is not worth a real concept.**

### 4.4 Why the claims are rejected — the measured reasons (the seed, LENIENT)

| the reason | the count | an example |
|---|---|---|
| `FUNCTION_HEAD` — the head is a function word | 76 | `It`, `There`, `The`, `To` |
| `TOO_LONG` — more than 12 words: a clause, not a phrase | 38 | `The Earth's unchanging size implies that the crust must` |
| `GENERIC_HEAD` — the head is a class of persons or of unnamed things | 34 | `Scientists`, `Geologists` |
| `CONCATENATION` — a heading and a sentence | 22 | `Role of water The role of water` |
| `CLASS_OPENER` — the surface opens with a class word | 15 | `Such volcanoes`, `Many were published` |
| `CLAUSE_SUBORDINATOR` — the span opens with a subordinate clause | 14 | `because plate motions`, `When the new crust` |
| `CLAUSE_RELATIVE` — the span carries an embedded relative clause | 11 | `the area where they stand` |
| `ARTEFACT` — a caption marker, a dash, a colon or a quote | 10 | `Lava Fountains, Krafla Volcano [35 k] …` |
| `CLAUSE_PARTICIPLE` — the span carries a participial clause | 9 | `a powerful source generating plate motion` |
| `DISCOURSE_HEAD` — the head is a discourse nominal with nothing that specifies it | 3 | `The mechanism`, `This theory` |
| `BARE_PLURAL` — a plural with no modifier names a class | 3 | `plates` |
| `CAPTION_REPEAT` — a repeated phrase | 2 | `Role of water The role of water` |
| `CLAUSE_CUT` — the head is a word the extractor stops at | 1 | `Plate tectonics came to` |
| **the total rejected** | **238** | (the accepted: 110) |

### 4.5 The definitions' coverage (A2)

| | the units | with a definition | the coverage |
|---|---|---|---|
| **the current path** | 326 concepts | 120 | **36.8 %** |
| **under the key, LENIENT** | 107 referents | 55 | **51.4 %** |
| **under the key, STRICT** | 90 referents | 46 | **51.1 %** |

The absolute number falls (120 → 55) because the population falls (326 → 107); the **rate
rises by 14.6 points**, because the fragments that had a definition attached to them are no
longer concepts. The definitions stay the corpus's own sentences with their spans (the
definition half is unchanged and untouched by this document). The definition half's own
numbers — **120/326** with the human-verified **24/25** — are unchanged.

### 4.6 The relations' usefulness (A3)

The frozen §3.2 rule produces **7** relation candidates on the seed corpus. **2** of them
have both endpoints accepted by the policy — at **both** levels; **5** do not, and the 5
are exactly the ones the definition half's own hand-labelling found to have a **fragmentary
endpoint**:

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
five endpoints. The **0/7 human-verified precision of the current path is unchanged** — the
key does not make a relation TRUE.

### 4.7 The retrieval effect (A4 and A5) — the first measurement for any alternative

The research was explicit: *"no retrieval effect has been measured for any alternative"*.
Here it is, for the alias layer, **without migrating anything**: the arm writes the alias
surface forms into the `concept.definition` column of a **temporary** store — the column
the retriever appends to the searchable text — so the concept ids, the claims and the rows
do not move.

**The official 20 ask cases:**

| the arm | passed | answered | the unanswerable abstained | answered without the gold | recall@1 | recall@5 | recall@10 | recall@20 | the MRR | the mean ask |
|---|---|---|---|---|---|---|---|---|---|---|
| **CURRENT** (the production state) | **12 / 20** | 14 | **6 / 6** ✓ | 8 | 0.2024 | 0.3214 | 0.3690 | 0.5952 | 0.5077 | 69 ms |
| **KEY** (the alias surface forms enter) | **12 / 20** | 14 | **6 / 6** ✓ | 8 | 0.2024 | 0.3214 | 0.3690 | 0.5952 | 0.5077 | 66 ms |
| **KEY_WITH_DEFINITIONS** | **11 / 20** ✗ | 15 | **5 / 6** ✗ | 9 ✗ | 0.2381 | 0.3214 | 0.3690 | 0.5952 | 0.5422 | 71 ms |

**The frozen held-out set (16 cases): every arm is identical** — 8/16 passed, 12 answered,
4 abstentions, 4 answered without the gold, 31–33 ms.

**Three findings.**

1. **The alias layer alone changes NOTHING.** Every metric of the KEY arm is identical to
   CURRENT. The aliases of this corpus are too rare to reach a question. **A null result,
   published as such.**
2. **The alias + definitions arm DEGRADES the refusal**, exactly as the definition half's own
   measurement found: `U-04` — an **unanswerable** case — is **answered** instead of
   abstained (the abstention 6/6 → 5/6) and the answered-without-the-gold rises 8 → 9.
   The recall@1 (+0.036) and the MRR (+0.035) rise at the price of the refusal. **The
   definitions' harm is reproduced under the key, and it is not caused by the key** — it is
   caused by the wider searchable text, whatever put it there.
3. **The calibration holds**: the CURRENT arm reproduces the published official row
   (12/20 of the ask cases, the eight named ask failures, recall@1 0.2024, recall@5 0.3214,
   MRR 0.5077 against the published 0.5078 — a rounding difference), and the held-out set is
   identical in every arm.

### 4.8 The cost (A7)

| the cost | the value |
|---|---|
| the seed import (the production path, unchanged by this document) | **1 675 ms** |
| the wider import (nine documents) | **10 505 ms** |
| the mean retrieval, CURRENT / KEY / KEY_WITH_DEFINITIONS | **69 / 66 / 71 ms** |
| the policy itself | in-process, over the claims of the workspace; it is a research instrument and is not wired into the production path |

---

## 5. The comparison (Gate 4)

| the measure | (a) the current splitter | (d) the strict source-span rule (implemented) | (c) the referent key + the policy (LENIENT) |
|---|---|---|---|
| **A1** the concept-identification precision | 35/150 = **23.33 %** [17.28 %, 30.72 %] | 23.33 % (the names and the ids do not change, so it cannot move) | **29/29 = 100 %** [88.30 %, 100 %] (STRICT, the operating point, **in-sample**); the lenient level 34/36 = 94.44 %; the structural-only counterfactual 34/55 = 61.82 % [48.61 %, 73.48 %] — **the intervals do not overlap** |
| **A1** the accepted share of the drawn spans | 150/150 | 150/150 | 29/150 |
| **A2 (i)** the definition coverage | 121/326 = 37.1 % adjacency-passing | **120/326 = 36.8 %**; the human-verified share 24/25 = 96 % | **46/90 = 51.1 %** (STRICT); the lenient level 55/107 = 51.4 % |
| **A3** the relation usefulness | 19 candidates (the research's own count); the definition half measured **7** candidates and **0/7** human-verified | 7 candidates, 0/7 human-verified | 7 candidates, **2 of them have both endpoints accepted** — the policy rejects exactly the 5 the human labelling rejected |
| **A4** the retrieval | 18/29 official (12/20 of the ask cases); recall@1 0.2024, recall@5 0.3214, MRR 0.5077; the identical eleven failures | **unchanged** — the definitions are deliberately not in the searchable text | **identical** (the alias arm): 12/20, recall@1 0.2024, MRR 0.5077; the held-out 16 identical |
| **A5** the false positives | 8 answered without the gold; the abstention **6/6**; the unsupported rate 0.3000 | **unchanged** | **unchanged** (the alias arm); **regressed** with the definitions (5/6, 9 without the gold) |
| **A6** the answer accuracy | not measurable on this corpus | not claimed | not claimed |
| **A7** the cost | the import 1 675 ms; the mean ask 69 ms | the definitions are recorded as their own records (+120 rows) | the import is unchanged; the mean ask 66–71 ms; the policy itself is free |
| **A8** the id stability | — | **no id changed** | **no id changed** — the key is a research artefact; the ids are untouched |
| the population | 326 concepts (the seed), 813 (the wider) | 326 concepts | **90 referents (the seed), 292 (the wider)** under STRICT — and **255 / 569 claims have no referent** |
| the promotion verdict | the baseline | **adopted** (the definition half) | **the promotion rule is met; the owner has decided the operating point (OD-062) and the schema fork (OD-061); the migration is rehearsed in §8** |

**What the comparison says.** The referent key is the only candidate that moves **A1** (the
precision of the concept identification nearly quadruples, with non-overlapping intervals)
and the only one that moves **A2's rate**. It is **neutral** on A4 and A5 when the alias
layer alone enters the searchable text. And it carries a cost the other two do not:
**59–68 % of the claims lose their concept**, which needs a schema decision that is the
owner's.

---

## 6. The negative results, published as such

1. **The alias layer has no measurable effect on the retrieval.** Every metric of the KEY
   arm is identical to CURRENT. The corpus's aliases are too rare to reach a question. **A
   null result.**
2. **The definitions in the searchable text still cost a case and the refusal.** The
   KEY_WITH_DEFINITIONS arm loses `U-04` (the abstention 6/6 → 5/6) and gains one
   answered-without-the-gold. This **reproduces** the definition half's own finding under a
   second instrument.
3. **The structural rules trade true referents for false positives, and the trade is not
   free.** LENIENT rejects 1 of the 35 true referents of the draw to remove 38 of the 40
   false positives; STRICT rejects 6 to remove all 40. The **in-sample** precision of
   94.44 % is inflated by the residue lexicon; the **structural-only** figure of 61.82 %
   is the honest floor for this corpus, and the out-of-sample figure is unknown.
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
   rejected. The **0/7 human-verified precision of the current path is unchanged**.
7. **The out-of-sample precision of the policy is not measurable here** (§4.1). The frozen
   150 is the set the policy was tuned on.

---

## 7. The promotion question, answered with the numbers

**The directive's rule:** *"A1 and A2 improve AND A4 does not regress AND A5 does not
regress"*, and the owner's question is the **schema fork** for the claims without a concept.

| the criterion | the measurement | the verdict |
|---|---|---|
| **A1 improves** | 23.33 % [17.28; 30.72] → **100 %** [88.30; 100] (STRICT, the operating point, **in-sample**); the structural-only floor 61.82 % [48.61; 73.48]; the intervals are disjoint at every level | **YES** |
| **A2 improves** | the coverage 36.8 % → **51.1 %** (STRICT) | **YES** |
| **A4 does not regress** | the alias arm is byte-identical to CURRENT (12/20, recall@1 0.2024, MRR 0.5077; the held-out 16 identical) | **YES** |
| **A5 does not regress** | the alias arm is byte-identical (the abstention 6/6, 8 answered without the gold) | **YES** (with the definitions it regresses → the definitions stay out of the searchable text) |

**The verdict: the promotion rule is MET for the referent key's policy and its alias layer,
and the owner has answered both questions** — OD-062 (the operating point is STRICT) and
OD-061 (the schema fork is the claim-scoped record) — with three qualifications that
travel with the answer:

1. the A1 figure is **in-sample**; the honest floor is the structural-only 61.82 % and the
   out-of-sample figure is **unknown** (no second frozen set exists in this corpus);
2. the **cost** is quantified and large: at the STRICT operating point **255/348** (the
   seed) and **569/888** (the wider) claims have no referent and become claim-scoped
   records;
3. **A6** (the answer accuracy) is not measurable here and is not claimed.

### 7.1 The schema fork, with the measured consequences

| | **(a) a nullable `concept_id`** | **(b) a claim-scoped record** (the research's option 2) |
|---|---|---|
| the shape | the claim keeps its text as the retrievable unit; `claim.concept_id` becomes NULL for the rejected claims | every rejected claim gets a record of its own, marked *not a concept*; the concept table is untouched |
| the measured volume (LENIENT) | the seed: **238** claims with a NULL concept (68.4 %); the wider: **526** (59.2 %) | the seed: **+238** records; the wider: **+526** |
| what changes in the code | the column's nullability, the validator (the `concept_id` is currently NOT NULL with a throwing validator), the retriever (a claim with no concept must still be retrievable by its proposition), every query that joins the claims to the concepts | a new table and a second write path; the retriever must search both tables; the "concept" word carries two meanings |
| the effect on the identity migration | the schema change is in the **core** table that every other table points at — it is done **before** the migration and it touches the very table the migration rewrites | **additive**: it does not block the identity migration at all; the referents can be migrated later, and the rejected claims stay where they are |
| the effect on the retrieval (A4/A5) | the searchable text of a concept-less claim is only its proposition: the retriever's unit count rises by 59–68 % and the **floor must be re-measured** | the same re-measurement, but the production tables are untouched |
| the reversible part | the nullability change is reversible (the migration can fill the NULLs back) | the new table can be dropped; the concept table never moved |
| the risk | a partial migration becomes **invisible** in the core table — the very failure mode the ten referencing tables make dangerous | two notions of "concept" in the vocabulary; the "referent" and the "record" must be distinguished everywhere |

**The owner's decision (OD-061): (b), the claim-scoped record.** A claim with no concept
keeps its own text as the retrievable unit through an **additive** structure, and
`concept_id` in the core table stays **NOT NULL**. The rehearsal of §8 executes exactly
that: the rejected claims become claim-scoped records, the old concept rows stay (marked
superseded by the lineage), and the core table's schema does not change. The reasoning
below is the rehearsal's evidence for the decision.

**The recommendation was: (b), the claim-scoped record.** It is additive, it does not touch the
ten referencing tables, it does not block the identity migration, and it keeps the concept
id stable — the constraint that made the identity half owner-gated in the first place.
**(a) is the better model of the world but the worse order of operations**: it changes the
core table's schema before the migration, in the table where a partial migration cannot be
detected.

### 7.2 The alias layer — the owner's choice, with the measurement attached

**The measurement: the alias layer has ZERO effect on the retrieval** (§4.7). The corpus's
aliases are too rare to reach a question, and the arm is byte-identical to the production
state. So the alias layer is **not justified by the retrieval** — it is justified by the
**fragmentation** (it is what keeps "the motion of the tectonic plates" and "plate motion"
one referent) and by the **properties** (the synonym and paraphrase cases, §4.2).

| the option | what it is | what it costs | the measurement |
|---|---|---|---|
| **a declared alias table** (what this document implements) | a small, hand-written, auditable table over lemmas; 12 entries for this corpus | every new synonym must be added by hand; the table is **fitted to this corpus** | it decides the 4 synonym and 3 paraphrase property cases (7 of 14) and the 7 alias entries of §1.4; its retrieval effect is **zero** |
| **a derived alias table** | the aliases are computed from a source of truth (a thesaurus, an embedding index, a co-occurrence model) | it needs a source of truth **this corpus does not have** — there is no thesaurus in the repository, and the corpus's nine documents are too few to learn a synonymy from | **not measurable here**: a derived table would have to be built and evaluated against the same hand-labelled sets, and the corpus is too small to serve as its own evaluation |

**The recommendation: the declared table, for now, and explicitly as a stopgap.** It is
honest (it is auditable, it is small, and its limits are visible), it is what the property
measurement is built on, and the derived alternative cannot be evaluated on this corpus at
all. The owner's choice, with the measurement attached: the declared table is the only
option whose behaviour is known.

---

## 8. The migration rehearsal — on copies, with the evidence

**What was run:** the copy-forward of the STRICT operating point, the lineage, the
recomputed representation ids, the prerequisite graph's acyclicity, the explicit orphan
audit, the rollback and the re-measurement — **on copies of a temporary workspace that
the production importer built from the seed corpus**. The owner's store was **never
opened for writing**: the rehearsal's workspace is a fresh temporary database, and every
mutation happens on copies taken from it with SQLite's `VACUUM INTO` (the store runs
WAL, so a byte copy of the main file would miss the committed rows that still live in
the write-ahead log). **The committed schema is untouched**: the additive tables exist
only in the copies.

The instrument: `matrix-core/src/main/java/io/matrix/knowledge/concept/research/ReferentKeyMigration.java`
(the plan, the copy-forward, the audit) and
`matrix-core/src/test/java/io/matrix/knowledge/concept/research/ReferentKeyMigrationRehearsalTest.java`
(the rehearsal and its evidence).

### 8.1 The rehearsal's steps and their results

| # | the step | the command | the result |
|---|---|---|---|
| 1 | **the workspace** | the production importer (`ClasspathCorpus` → `workbench.importDocument`) into a temporary database | the concepts **326**, the claims **348**, the sources **2**, the representations **335**, the prerequisites **0** |
| 2 | **the snapshot** (the rollback reference) | `store.takeWorkspaceSnapshot(workspace, Kind.MANUAL, createdAt)` | the snapshot `snap-referent-key-rehearsal-1`, the concepts 326, the claims 348, the cost **179–226 ms** (across the runs), the database grew **1 859 584 bytes** (1 875 968 → 3 735 552) |
| 3 | **the copies** | `VACUUM INTO` (three copies: the migrated one, the broken one, the rollback one) | three self-contained copies; the owner's store untouched |
| 4 | **the plan** (STRICT) | `ReferentKeyMigration.plan(snapshot, workspace, STRICT, createdAt)` | the **referents 90**, the **claim-scoped records 255**, the rejected claims 255, the **new concept rows 345**, the concept-lineage rows 90 |
| 5 | **the copy-forward** | `ReferentKeyMigration.apply(copy, plan, workspace)` — one transaction | the concepts inserted **345**, the claims re-pointed **348**, the representations re-pointed **93**, the refines links carried **0**, the prerequisites re-pointed **0**, the other rows re-pointed **759**, the lineage rows **696**, the cost **60 ms** |
| 6 | **the lineage** | the SQL over `concept_lineage`, `claim_lineage`, `claim_scoped_record` | **348 of the 348** claims point at the concept the lineage names; the claim-scoped records **255**; the definition merges **0** |
| 7 | **the representation ids** | the before/after comparison over the snapshot | before **335**; the old ids kept **242**, the recomputed **93**; the refines links **0 → 0**, dangling **0** |
| 8 | **the prerequisite graph** | the depth-first cycle search over the migrated copy | **0 nodes, 0 edges, no cycle** — the seed corpus carries no prerequisites, so this check is **vacuous on this corpus** (§8.5) |
| 9 | **the orphan audit** (the correct copy) | `ReferentKeyMigration.audit(copy, workspace)` | **13 tables**, the orphans **0**, the stale pointers **0** |
| 10 | **the orphan audit** (a deliberately broken copy) | the same audit on a migrated copy that was then broken | the orphans **6**, the stale pointers **1** — the audit **sees** the damage |
| 11 | **the rollback** | `store.restoreWorkspaceSnapshot(snapshotId, createdAt)` | the concepts **671 → 326**, the claims **348 → 348**, the restore **89–127 ms** |
| 12 | **the re-measurement** | the official ask cases on the migrated scopes, the frozen set on a migrated copy | **12/20** official ask cases (the identical eight failures), the abstention **6/6**, the frozen set **8/16** (12 answered, 4 abstentions) |

### 8.2 The tables and their counts, before and after

| the table | before | after | what changed |
|---|---|---|---|
| `concept` | 326 | **671** | the 326 old rows **stay** (marked superseded by the lineage) and the 345 new rows are added |
| `claim` | 348 | 348 | the rows stay; **their `concept_id` changes** |
| `representation` | 335 | 335 | the rows stay; **93 of their ids are recomputed** (they embed the concept id) |
| `concept_definition` | 27 | 27 | the rows are re-pointed; **0 merges** on this corpus |
| `assessment_item` | 2 654 | 2 654 | the rows are re-pointed (they carry a `concept_id`) |
| `evidence` | 348 | 348 | untouched — the evidence hangs off the claim, and the claim's id does not change |
| `concept_lineage` (additive) | — | **90** | the old id → the new id, with the reason and the time |
| `claim_lineage` (additive) | — | **348** | the claim id (**unchanged**), the old and the new concept id |
| `claim_scoped_record` (additive) | — | **255** | the claim-scoped record: the concept id, the claim id, the claim's own text, the reason |
| `concept_definition_merge` (additive) | — | **0** | the definitions that collided — none on this corpus, but the table is there because they can |

**Which ids change and which do not.**

| the id | changes? | why |
|---|---|---|
| the **claim id** | **NO** | it is a digest of the workspace, the content hash and the proposition — none of the three moves, so the claims, the evidence and the proposition-based golds survive |
| the **concept id** (a referent) | **YES** | it is now `digest(workspace + "|referent|" + the referent key)` — the lemma, the source and the anchor |
| the **concept id** (a claim-scoped record) | **YES** | it is `digest(workspace + "|claim|" + the claim id)` |
| the **representation id** | **YES, for the 93 whose concept changed** | `Ids.representation(workspace, conceptId, level, statement)` embeds the concept id; the other 242 keep their ids because their concept did not change |
| the **source id**, the **evidence id**, the **workspace id** | NO | they do not embed the concept id |
| the **old concept rows** | they stay | superseded, never removed; the lineage is what marks them |

### 8.3 The orphan audit — the proof

**Why the audit exists.** SQLite declares no foreign key for the concept id, so the
store's own `validate` **will not see a partial migration**. The audit therefore walks
**every table that carries a `concept_id`** — discovered from the live schema, not from a
hand-written list — and reports two numbers per table: the **orphans** (the rows whose
referenced concept does not exist) and the **stale pointers** (the rows that still point
at a concept the lineage marks as superseded).

| the copy | the tables walked | the orphans | the stale pointers | where they were found |
|---|---|---|---|---|
| **the migrated copy** (correct) | **13** | **0** | **0** | — |
| **the broken copy** (3 concept rows deleted, one claim re-pointed at its old concept) | 13 | **6** | **1** | the orphans: `claim` (3), `claim_scoped_record` (3); the stale pointer: `claim` (1) |

**The audit is therefore tested in both directions**: it reports zero on a correct
copy-forward and it finds both kinds of damage on a deliberately broken one. Without the
broken copy the audit would be an untested claim.

**The tables the audit walked** (the live schema's ten referencing tables plus the
migration's own additive tables): `assessment_item`, `assessment_outcome`, `claim`,
`claim_scoped_record`, `concept_definition`, `epistemic_case`, `hebbian_binding`,
`learning_error`, `learning_state`, `learning_transition`, `prerequisite` (+ the
prerequisite's `requires_concept_id`), `representation` — **13**.

### 8.4 The rollback

| the measure | the value |
|---|---|
| the snapshot's kind | `MANUAL` (taken on request, nothing replaced) |
| the snapshot's cost | **190–226 ms** for a 326-concept workspace |
| the snapshot's size | the database grew **1 859 584 bytes** (1.86 MB) — the workspace's content written twice |
| the restore's cost | **89–127 ms** |
| the restore's result | the concepts **671 → 326**, the claims **348 → 348** — the workspace is back |

**The restore is exactly as destructive as a load**, and exactly as safe: it takes a
snapshot of what it replaces before it erases anything (the store's own contract, and the
reason the rehearsal's rollback works at all).

### 8.5 What the rehearsal does NOT establish

1. **The prerequisite graph's acyclicity is not exercised.** The seed corpus carries **no
   prerequisite rows** (0 nodes, 0 edges), so the cycle search returned "no cycle" over an
   empty graph. The check is implemented and it runs, but on this corpus it proves
   nothing. **The real migration must be rehearsed on a workspace that has prerequisites.**
2. **The `refines` chain is not exercised either.** The seed corpus has **no** refines
   links (0 → 0), so the chain-carrying code path ran without a link to carry. The
   representation **ids** are proven (93 recomputed), the chain is not.
3. **The C2A `BASELINE-PATH` (17/25) was not re-measured.** That number is produced by
   `HeldOutEvaluationTest`, whose judgement (the premise assessment, the gold accounting)
   is private to that harness and whose workspace it prepares itself; re-running it
   against a migrated copy would mean editing that harness, which is outside this task's
   scope. **What the rehearsal does measure is the same A4/A5 row the instrument produces
   (the official ask cases: 12/20 before and after, the identical eight failures, the
   abstention 6/6) and the frozen set (8/16 before and after).**
4. **The A1/A2 figures are in-sample.** The A1 is a property of the policy, not of the
   store: the migration does not change it, and it stays the in-sample **29/29 = 100 %**
   (STRICT) with the structural floor **61.82 %**. The A2 (the definitions' coverage) was
   re-measured on the migrated copy (§8.6).
5. **The owner's store was never exercised.** The rehearsal's workspace is a fresh
   temporary database built by the production importer; the owner's store is a different
   database with a different history, and the migration's cost on it is unknown.
6. **The migration is not proposed yet.** What the rehearsal proposes is the *order*:
   the snapshot, the copy-forward, the lineage, the audit, the re-measurement, the
   rollback — and the owner's schema decision (the additive tables in the committed
   schema) still has to be taken before any of it runs for real.

### 8.6 The re-measurement on the migrated copy

| the measure | before the migration | after the copy-forward | the verdict |
|---|---|---|---|
| the official ask cases | 12/20 | **12/20** | unchanged ✓ |
| the ask-case failures | A-02, A-04, A-05, A-06, M-01, P-01, P-02, M-03 | **the identical eight** | unchanged ✓ |
| the unanswerable abstention | 6/6 | **6/6** | unchanged ✓ |
| the frozen held-out set (16) | 8/16, 12 answered, 4 abstentions | **8/16, 12 answered, 4 abstentions** | unchanged ✓ |
| the A1 on the frozen draw (STRICT) | 29/29 = 100 % [88.30; 100], **in-sample** | the same (the policy does not change) | unchanged, in-sample |
| the definitions' count | 27 | 27 | no definition lost ✓ |
| the A2, the definitions' coverage | the seed workspace as it stands: **27 definitions over 326 concepts (8.3 %)** | the same **27 definitions over 90 referents (30.0 %)** — no definition was lost and no merge was needed | the rate rises, the absolute number is unchanged |

**The rehearsal's verdict: the copy-forward, as rehearsed, does NOT regress the
retrieval and does NOT regress the refusal.** Every scope of the official dataset was
prepared, migrated and asked on its own migrated workspace — the same method the A4/A5
instrument uses — and the row is byte-identical.

## 9. The migrated copy: the C2A re-measurement

**The change to the harness.** `HeldOutEvaluationTest` gained a second phase: the
workspace is copied, the referent key's copy-forward is applied to the copy, and the
**BASELINE-PATH** row is measured on it with **the same `judge` method, the same
questions, the same dataset and the same level** — the judge code is untouched, and the
migrated row is kept **out** of the published artefact (that artefact is the frozen
measurement of the unmigrated path). The file: `matrix-core/src/test/java/io/matrix/knowledge/concept/HeldOutEvaluationTest.java`
(the migrated phase after the main measurement; the calibration and the retrievability
tests).

### 9.1 The calibration — the judge is unchanged, BY COMPARISON

| the row | the published artefact (the committed `C2A-HELDOUT-EVALUATION.json`) | the harness's own row after the change | the same? |
|---|---|---|---|
| `BASELINE-PATH` passed | 17 | 17 | ✓ |
| the comparable cases | 25 | 25 | ✓ |
| the judged cases | 38 | 38 | ✓ |
| the failed cases | H-P02, H-P05, H-P06, H-P08, H-P10, H-P11, H-P13, H-P17 | the identical eight | ✓ |
| the recall@1 | 0.5351 | 0.5351 | ✓ |
| the recall@5 / @10 / @20 | 0.7719 / 0.8158 / 0.8596 | the same | ✓ |
| the MRR | 0.8600 | 0.8600 | ✓ |
| the abstention | 1.0000 | 1.0000 | ✓ |
| the unsupported-answer rate | 0.2800 | 0.2800 | ✓ |
| the gold availability | 0.7368 | 0.7368 | ✓ |
| the supported-answer accuracy | 0.5789 | 0.5789 | ✓ |

**The comparison is against the COMMITTED artefact** (`git show
HEAD:docs-v2/engineering/C2A-HELDOUT-EVALUATION.json`), which is an independent
artefact — the harness rewrites its own copy on every run, so the committed one is the
only independent reference. The judge is unchanged: the row the harness computes after
the change is **identical** to the row the committed artefact carries.

### 9.2 The side-by-side: the migrated copy against the unmigrated one

| the measure | the unmigrated path | the migrated copy | the difference |
|---|---|---|---|
| the C2A `BASELINE-PATH` | **17 / 25** | **17 / 25** | **none** |
| the failed cases | the eight above | **the identical eight** | none |
| the recall@1 | 0.5351 | 0.5351 | none |
| the MRR | 0.8600 | 0.8600 | none |
| the abstention | 1.0000 (6/6) | 1.0000 | none |
| the unsupported-answer rate | 0.2800 | 0.2800 | none |
| the gold availability | 0.7368 | 0.7368 | none |
| **the mean non-gold cited** | **5.2105** | **4.6842** | **−0.53 claims per case** — the migrated copy cites fewer irrelevant claims |
| the official evaluation | 18/29, the identical eleven failures | not re-measured on the migrated copy (the official harness prepares its own six workspaces; §9.5) | — |
| the unanswerable abstention | 6/6 | 6/6 on the migrated seed copy (§8.6) | none |
| the W42 abstention floor | 0.861 | 0.861 (the asking path is not touched) | none |

**The one difference is a difference, not an improvement:** the migrated copy cites
**fewer** claims that are not the gold (4.68 against 5.21 per case). The retrieved
material is narrower. The harness's own numbers say the same thing and the artefact
records it.

### 9.3 The claim-scoped records stay retrievable

The migration of the pool workspace produced **322 claim-scoped records**, each carrying
the claim's own text as the retrievable unit. **17 of the 25 comparable cases depend on
a claim whose gold lives in a claim-scoped record** (their gold claim was one the
policy rejected as a surface form). (The **314** this section carried before the
prepositional-phrase rule was written was stale; the count the test prints on every
run is **322**.)

| the measure | the value |
|---|---|
| the cases that depend on a claim-scoped record | **17** |
| of those, the cases that still answer after the migration | **16** |
| the one that does not | **H-P02 (POSITIVE)** — and it did **not** answer before the migration either: the same case, the same failure mode, before and after |
| the cases whose answered flag changed | **0** — the migration silenced nothing |

**The claim-scoped records are retrievable: a claim whose surface form the policy
rejected still answers, because the record carries the claim's own text.** The one case
that does not answer is a case that did not answer before the migration either.

### 9.4 The prerequisites and the `refines` chain — what is real and what is not

| the check | the seed corpus | the research pool | the bundled package |
|---|---|---|---|
| the prerequisite rows | **0** | **0** | **5** |
| the graph after the copy-forward | 0 nodes, 0 edges | 0 nodes, 0 edges | **4 nodes, 5 edges, no cycle** |
| the `refines` links before → after | 0 → 0 | **1 → 1, 0 dangling** | 0 → 0 |

**Stated plainly: the C2A corpora have NO prerequisites at all.** The prerequisite rows
come from the **knowledge packages**, not from the corpus import, and the workspace that
has a prerequisite graph in this repository is the **bundled package** (photosynthesis: 5
edges; logic: 6). The acyclicity check therefore runs where there is something to
check: the package was migrated (8 concepts, 17 claims, 17 concept rows inserted, the
prerequisites re-pointed) and the graph came out with **the same 5 edges, no cycle** ✓.

**The `refines` chain is real on the pool** (the revise flow leaves one link) and the
migration **carries it: 1 before, 1 after, 0 dangling** ✓.

### 9.5 What the migration does NOT establish

1. **The official 18/29 was not re-measured on a migrated copy.** The official harness
   (`ConceptEnrichmentExperimentTest`) prepares six workspaces of its own, and its
   measurement of a migrated copy would need the same treatment the held-out harness
   got. What is measured is the **same A4/A5 row by the instrument that produced it**
   (the official ask cases: 12/20 before and after, the identical eight failures, the
   abstention 6/6) and the **C2A `BASELINE-PATH` by the C2A harness itself** (17/25
   before and after) ✓.
2. **The out-of-sample A1 is still unknown** (§4.1): the frozen 150 is the set the
   policy was tuned on, and the strict level's 29/29 is an in-sample figure.
3. **The migrated copy was measured on the pool and the package, not on the owner's
   live store** (OD-063). The cost on the live store is still unknown.
4. **The migration's effect on the learning tables** (the assessment items, the learning
   state) was not measured — the rows are re-pointed (2 089 of them on the pool) and the
   audit finds no orphan, but nothing asks whether the learning still works.
5. **The seed's claim-scoped records were measured on the C2A pool's migration** (322
   records); the seed corpus's own 255 records were not asked a question directly.
6. **The representation id rule breaks on a collision** — and the pool has **one** such
   collision (two representations of two merged concepts carry the same statement at
   the same level). Nothing is deleted: the row keeps its own id, is re-pointed at the
   referent, and the collision is counted — but the recomputed id is not unique, and the
   migration needs a decision about the duplicates.

## 10. The migration rehearsal plan — a plan, not a migration

**Nothing in this section was executed.** The identity half is owner-gated: the concept id
is referenced by ten tables (`claim.concept_id`, `representation.concept_id` — and through
the representation id every representation row —, `prerequisite.concept_id`,
`prerequisite.requires_concept_id`, `epistemic_case`, `hebbian_binding`, `assessment_item`,
`learning_error`, `learning_state`, `learning_transition`, `assessment_outcome` and
`dialogue_turn.concept_ids`), and **SQLite declares no FOREIGN KEY** for any of them, so a
partial migration is **invisible** to the database.

### 10.1 The steps

| # | the step | the detail |
|---|---|---|
| 1 | **the snapshot** | `takeWorkspaceSnapshot` **before** anything is written. The store already has `takeWorkspaceSnapshot`/`restoreWorkspaceSnapshot`; a migration without a pre-migration snapshot is not reversible, and the store's own replacement contract treats a missing snapshot as fail-closed. **The snapshot is the rollback reference.** |
| 2 | **the lineage table** | an append-only `concept_lineage(old_id, new_id, reason, created_at)` (a new schema version, a `schema_meta` bump). The old rows are **marked superseded, never deleted**. The `reason` names the rule that produced the new id (the lemma, the alias entry, the rejection). |
| 3 | **the copy-forward** | new concept rows with new ids; the old rows kept and marked; every referencing row rewritten **in one transaction** through the existing atomic `applyChanges` path. **No rewrite in place.** |
| 4 | **the representation ids** | recomputed — they embed the concept id — and the `refines` chain carried across explicitly. |
| 5 | **the prerequisite graph** | re-checked for acyclicity (`KnowledgeStore.validate` does this). |
| 6 | **the orphan audit** | an **explicit** audit over the tables `validate` does **not** cover — the learning, hebbian, assessment, epistemic-case and dialogue rows — because SQLite's foreign keys are not declared and `validate` would not catch a partial migration. The audit is a query per table, and it must run before and after the migration and report **zero** orphans on both sides. |
| 7 | **the claim-scoped records** | the **238 / 526 rejected claims** must keep their text and stay retrievable. **§7.1 recommends (b), a claim-scoped record** — additive, and it does not block this migration. If the owner chooses (a), the nullability change lands here, before step 3. |
| 8 | **the re-measurement** | the **18/29** official row (with the identical eleven failures), the **6/6** abstention, the **C2A `BASELINE-PATH`**, the frozen held-out set, the A1 precision on the frozen 150, the definitions' coverage, and the four published artefacts — because the retriever's searchable text is `proposition + concept name + definition` and **both the name and the definition change**. |
| 9 | **the rollback** | `restoreWorkspaceSnapshot` of the pre-migration snapshot; the lineage table is left in place (append-only) so the rollback itself is auditable. |

### 10.2 The rehearsal the owner would need before deciding

1. a **rehearsal on a copy** of the seed workspace: the copy-forward of 326 concepts and 348
   claims, the recomputation of the representation ids, the lineage table, the orphan audit
   over the ten tables, and the rollback by snapshot restore — with the **count of the
   rewritten rows** as the number;
2. the **A8 number**: how many concept ids change and how many references are rewritten;
3. the **A4/A5 re-measurement** of §8.1 step 8 — the alias layer measured neutral here must
   be measured again on the migrated ids, because the searchable text changes;
4. the **decision on the rejected claims** (§7.1) — the only decision that changes the
   schema.

---

## 11. What this corpus cannot support

* **Nine documents, one domain (plate tectonics), one language (English), two licence
  families**, one register (encyclopaedic prose plus one USGS explainer), no dialogue, no
  tabular prose, no non-English text (the importer supports Russian; the corpus holds
  none).
* **The recall of the policy is not measurable here.** The A1 draw is over the concepts the
  **current** path produced, so the referents the current path MISSED are out of frame. The
  measured 94.44 % is a precision over what the policy accepts, **not** a recall over the
  corpus's referents.
* **The out-of-sample precision is not measurable here.** The frozen 150 is the set the
  policy was tuned on; the structural-only counterfactual (61.82 %) is the honest floor,
  and a **sealed held-out set** would be needed to quote the number outside this document.
* **The closed word lists were written against this corpus.** They are an upper bound on
  their own generalisation — **[speculative]** outside this domain. The same is true of the
  alias table, of the property set and of the policy as a whole. **The structural rules are
  the part that is expected to generalise**, because they look at shapes and not at
  vocabulary.
* **The corpus cannot establish a retrieval GAIN.** The owner's promotion rule requires A4
  and A5 not to regress; a gain would need a new sealed held-out set.
* **A6** (the answer accuracy) is not measurable on this corpus and is not claimed.
* **The relation usefulness was not re-measured by hand under the key**; what was measured
  is the agreement of the policy with the existing hand-labelling on the endpoints.

---

## 12. Every rule chosen here, marked

| the rule | where it comes from | its status |
|---|---|---|
| the reject-only rule (R1) | the research's §6.2 | **the research's** |
| the whole-phrase lemma (R2) | the research's normalisation | **the research's**, with the plural rule **corrected** (§6.5) |
| the anchor (R3) | the repository's own span convention (`ClaimSpan`) | **the repository's** |
| the within-document cluster | this document | **[speculative]** — fitted to this corpus |
| the cross-document merge for a specific lemma | this document | **[speculative]** |
| the context signature for a bare head | this document | **[speculative]** |
| the alias table (12 entries) | this document, by hand | **[speculative]** — fitted to this corpus; §4.2 measures how many entries fire |
| the **structural rules S1–S7** | this document, second pass | **the part expected to generalise** — they look at shapes; the "-ing" participle rule is the exception and is in the STRICT level because it collides with the technical nouns |
| the **bare plural**, the **possessive without a noun**, the **head under a complement** | this document, second pass | **morphological**; they are shape rules, not word lists |
| the FUNCTION_WORDS / CLAUSE_CUT_WORDS / CLASS_OPENERS / GENERIC_HEADS / DISCOURSE_HEADS / DISCOURSE_ADVERBIALS lists | this document | **[speculative]** — the residue lexicon (57 generic heads and 18 function words, added across the two passes) was written by reading this draw's false positives, and it is the **in-sample** part of the measurement (§4.1) |
| the 12-word cap | the research's NP rule | **the research's** — it rejects one true referent of the draw |
| the strip of the leading discourse adverbial | the A1 labelling protocol | **the A1 instrument's** |
| the property set (14 cases) | this document, by hand | **[speculative]** — 14 cases of one corpus |
| the Wilson interval at 95 % | standard | **standard** |

---

## 13. The evidence

```
export JAVA_HOME=$HOME/jdk25
flock /tmp/gradle-matrix.lock bash -c 'rm -rf matrix-core/build/test-results && \
  ./gradlew :matrix-core:test --tests "io.matrix.knowledge.concept.*" --offline --console=plain'
```

| the scope | the tests | the failures | the errors | the XML |
|---|---|---|---|---|
| `io.matrix.knowledge.concept.research.ReferentKeyInstrumentTest` | 7 | 0 | 0 | `docs-v2/engineering/evidence/TEST-io.matrix.knowledge.concept.research.ReferentKeyInstrumentTest.xml` <!-- no-link: the JUnit XML is a runtime artefact of the run, not a repository file --> |
| `io.matrix.knowledge.concept.research.ReferentKeyRetrievalTest` | 2 | 0 | 0 | `docs-v2/engineering/evidence/TEST-io.matrix.knowledge.concept.research.ReferentKeyRetrievalTest.xml` <!-- no-link: the JUnit XML is a runtime artefact of the run, not a repository file --> |
| **the whole scope `io.matrix.knowledge.concept.*`** | **51** | **0** | **0** | |

The seven instrument tests are: the baseline is reproduced; the fragmentation is measured;
the A1 precision on the frozen 150; the four properties; the definition coverage; the
relations; the two intervals. The two retrieval tests are: the official ask cases in the
three arms; the frozen held-out set in the three arms.

**The numbers that must not regress, checked in the same run:** the scope **51 tests, 0
failures**; the official evaluation **18/29** with the identical eleven failures
(`A-02, A-04, A-05, A-06, F-02, M-01, P-01, P-02, M-03, R-04, R-05`), recall@1 0.2024,
recall@5 0.3214, MRR 0.5078, the abstention 6/6; the definitions' coverage **120/326** with
the human-verified **24/25**; **no concept id changed**.

**The build directory is shared** with the other agents: a concurrent run overwrites
`matrix-core/build/test-results`, so the XML of this measurement was copied out inside the
lock. **No production file was modified, nothing was staged or committed, and the owner's
store was never touched.**

---

## 14. Related documents

* `docs-v2/engineering/EPISTEMIC-CONCEPT-IDENTITY.md` — the baseline and the four
  alternatives; the source of the shape this document completes.
* `docs-v2/engineering/EPISTEMIC-CONCEPT-DEFINITIONS.md` — the definition half, already
  implemented and measured; the source of the retrieval harness this document reuses.
* `docs-v2/engineering/EPISTEMIC-EXECUTION-PLAN.md` — the plan this document serves (P1).
* `docs-v2/engineering/C2A-CANDIDATE-FREEZE.md` — the frozen definition and relation rules.
