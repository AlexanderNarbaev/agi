# CRJ-01 — the instruments: the two oracles, built and proven

**Status:** BUILT · **Date:** 2026-11-20 · **Branch:** `feature/crj-01-arm-c`
**Protocol:** `docs-v2/engineering/CRJ-01-PROTOCOL.md` §4 (the oracle), §5.5 (the freeze fields), §7.6 (the instrument's own control)
**Contract:** `docs-v2/engineering/CRJ-01-ACCEPTANCE.md` §2 (the oracle requirement), G3 (the three-arm comparison)

---

## 0. The answer in one paragraph

The **instrument the comparison needs** now exists. Both oracles the protocol names as
non-existent are built, in the **test tree**, with **11 tests and 0 failures**, and each one
carries the control that makes it an instrument rather than a restatement of the solver: the
source-span oracle **fails loudly** on a gold corrupted by a single character, and the lamp
truth oracle **voids the run** on any disagreement with the world. The **cross-check has been
executed**: the oracle and the world agree on all four assignments, and the record is
`instrument_crosscheck: {assignments: 4, agreements: 4}`. The oracle was then run against the **real served path** — the journey over the bundled
photosynthesis package — and it **measured** something the programme needs: the bundled package
is a **paraphrase corpus** (the journey cited 7 claims; the oracle grounded **0** of them,
while it **does** ground a proposition that is a genuine span — the positive control). So the
bundled package **cannot** be the sealed split's corpus, and the protocol's new-corpus
requirement is a **hard condition, now measured** rather than assumed.

What is **still missing** is not code:
it is the **corpus**, the **cases**, the **generator** and the **freeze** — and by the
protocol's own rule those belong to a **case author who implements no arm** and an **independent
reviewer**, and this agent implemented arm C.

## 1. What was built

| the instrument | the file | the tests | the independence |
|---|---|---|---|
| the **domain-A oracle** — the source-span oracle | `matrix-core/src/test/java/io/matrix/crj/evidence/SourceSpanOracle.java` | `…/SourceSpanOracleTest.java` — **7 tests** | it reads the corpus file's **bytes** and re-computes their sha256; it never calls the retriever, the workbench, the kernel selector or the session, and it takes the served answer as **data** |
| the **domain-B oracle** — the independent truth table | `matrix-core/src/test/java/io/matrix/crj/microworld/LampTruthOracle.java` | `…/LampTruthOracleTest.java` — **4 tests** | the table is **four literal rows** written out from the specification; the class **does not import the lamp environment at all** and cannot ask it anything |

**The two oracles meet the solver exactly once each**, and both meetings are of the same kind:
at a **record of data**. The harness reads the served response and reduces it to the oracle's
own type; the oracle scores that. Neither oracle can be fooled by a retrieval bug, because
neither one retrieves.

## 2. The source-span oracle's steps, and what each one can catch

The oracle answers one question: **does the served response cite the evidence the source
document actually contains, for this question?**

1. it re-reads the named corpus file's bytes and re-computes their sha256, comparing it with
   the digest the manifest froze → a corpus that changed is **reported**, not scored
   (`CORPUS_CHANGED`);
2. it asserts that every gold proposition occurs **verbatim** in those bytes → a gold that is
   not in the corpus is a **broken case**, and it is reported as such
   (`GOLD_NOT_IN_CORPUS`), **never** as an ungrounded answer — otherwise a broken dataset would
   hide behind a solver's failure;
3. it extracts the served answer's cited propositions and claim ids;
4. it scores a **positive** case as `GROUNDED` only when at least one cited proposition is
   **byte-identical** to a gold proposition **and** its claim id is the production claim id the
   manifest froze → a correct answer cited under the **wrong claim** is `UNGROUNDED`, and so is
   a correct proposition cited under a **different** one;
5. it scores a **negative** case by comparing the disposition field the gold names — the
   premise's status, the answer's epistemic disposition or the action's disposition — with the
   expected one, and the expected refuting source with the sources the answer names.

### The instrument's own control (§7.6) — the part that makes it an instrument

> a deliberately corrupted gold record: one sealed case's gold proposition is altered by a
> single character.

The fixture is in the test suite, and the answer is *loud*: the oracle reports
`GOLD_NOT_IN_CORPUS` with the reason `the gold proposition does not occur verbatim in …`, and
the case **cannot** score as grounded — the test asserts exactly that, with a served answer
that cites the corrupted proposition, so a lenient oracle would have called it grounded.
**A control fails if the corrupted case still scores as grounded.** It does not.

### The defect the control caught in the instrument itself

The instrument's own first draft compared the citations with `byte[].equals(...)`, which is
**reference** equality: two identical propositions compared unequal, and a correct answer
scored as ungrounded. The control caught it — the positive case failed while the negative ones
passed. The comparison is now `Arrays.equals`, and the reason is written down in the code, so
the next reader does not "simplify" it back.

## 3. The lamp truth oracle, and the binding cross-check

The oracle answers one question: **what is the lamp world's outcome for a given assignment?**

Its truth is the **specification's**, not the code's: the world is documented as "two inputs,
one output, one hidden rule", and the hypothesis space documents the conjunction as "the
function that is true only at `power=1,switch=1`". The table is therefore four constants in a
table, written out one by one:

| `power` | `switch` | `lamp` |
|---|---|---|
| 0 | 0 | false |
| 0 | 1 | false |
| 1 | 0 | false |
| 1 | 1 | true |

**The cross-check has been executed**, and it is binding:

```
instrument_crosscheck: {assignments: 4, agreements: 4}
```

Two negative cases are tested, and both make the run **VOID** with the reason
`INSTRUMENT_MISMATCH`: a world that answers differently, and a world that **does not answer
one of the four assignments** — a missing answer is a disagreement, never a pass. Neither is
scored and neither is re-run to a nicer number; the mismatch is a finding in its own right.

## 4. The measurement the oracle made against the SERVED path — and what it found

A fixture cannot tell the owner whether the workspace the system actually serves could ever be
scored by the oracle. So the oracle was run against the **real served path** — the report
journey over the bundled photosynthesis package — over a corpus built from the workspace's own
source documents (one file per source, holding the document text the package carries, which is
the shape the protocol's corpus has, §2.1.3.1).

**The measurement:**

| | |
|---|---|
| the workspace | **4** sources, **17** claims, **18** evidence rows |
| the journey cited | **7** claims |
| the oracle grounded | **0** of the 7 |
| of the 18 evidence rows, how many are a **span** of their document | **17** |

**The finding: the bundled package is a PARAPHRASE corpus.** The package carries each document's
text as the source's excerpt, and **17 of the 18** evidence excerpts **are** spans of it — but
the claim's **proposition**, which is what the served answer cites, is a paraphrase of that
text and is **never** byte-identical to a span of it. So the oracle, asked "does the served
response cite the evidence the source document actually contains?", answers **no** for a
correct answer: the answer cites a *true* proposition the document does not *contain* in those
words.

**The positive control, so the finding is about the package and not about the instrument.** The
claim's own **evidence excerpt** IS a span of its document, and the oracle **grounds** it. So the
instrument discriminates over the real path: it grounds what is a span and refuses what is a
paraphrase. The measurement is a property of the package.

**What this means for the programme — and it is a decision-grade result:**

1. **The bundled photosynthesis package CANNOT be the sealed split's corpus.** The oracle's
   question is unanswerable over it. This is not a nuance of the instrument: the package's
   propositions are paraphrases, and a verbatim-span instrument has nothing to match.
2. **The protocol's requirement of a NEW corpus directory, imported through the production
   acquisition path (§2.1.3.1–2), is a HARD CONDITION, not a formality.** The import is what
   makes the document's text the source's excerpt **and** the claim's proposition a span of it,
   and it is what makes the claim id the **production** one — the answer carries `cl-…`
   (production ids), while the package's own keys are something else. The measurement records
   this too: the served answer's claim ids **are** the production ones, so a case built by the
   production path would match on the id; it is the **proposition** that does not.
3. **The case author must be told this before they start.** A case built over the bundled
   package would be scored UNGROUNDED on every case, and the run would look like a failure of
   the arms rather than a property of the corpus.

**The evidence:** `docs-v2/engineering/evidence/TEST-io.matrix.crj.evidence.BundledCorpusSpanMeasurementTest.xml`
— three tests, zero failures: the paraphrase measurement, the production-id measurement, and the
positive control.

## 5. The freeze fields this closes, and the ones it does not

| the field of §5.5 | the state |
|---|---|
| `oracle.sourceSha256` (the source-span oracle) | **re-derivable** — the file exists, and the reviewer re-derives the digest of the file as it stands at the freeze. The digest at the moment of writing is `2f1f5579717272ee10cccbf32bcd82aab7d73d62adbd23087bdee0a980c65d9d`, and **the freeze records the reviewer's own digest, not this one** |
| `oracle.sourceSha256` (the lamp truth oracle) | as above — `c669dfb70825796bec3cc4e58d39cb8f997871175593d736906d3ae5565cff52` at the moment of writing |
| `instrumentCrosscheck` | **EXECUTED** — `{assignments: 4, agreements: 4}`, and the mechanism that voids a run on a disagreement is tested |
| `dataset.sha256`, `dataset.fullFileSha256` | **OPEN** — the corpus and the cases do not exist |
| `generator.sourceSha256`, `generator.id`, `generator.seed` | **OPEN** — the builder does not exist |
| `corpusManifestSha256` | **OPEN** — the corpus directory does not exist |
| `branchSha` | **OPEN** — the freeze records its own SHA, which is not the baseline `b7bd1be4` |
| `roles` | **OPEN** — and this is the owner's to name |

## 6. Why the corpus and the cases are **not** built here

The protocol's §5.6 is binding and it names roles:

1. the **case author** — *not a solver author* — produces the corpus, the cases and the builder;
2. the **independent reviewer** — *not the case author, not a solver author* — runs the
   builder, re-derives every hash of §5.5 independently, and confirms the builder's own
   assertions;
3. the reviewer writes the freeze record;
4. after the freeze the sealed split is **read-only**.

**This agent implemented arm C.** Arm C is a solver: it is one of the three arms the sealed
set is scored against. Authoring the cases that score it would put the author of the cases on
both sides of the comparison, and the protocol forbids exactly that. The instrument is the part
that is not a solver — it is a **second implementation of the truth**, and a second
implementation is required rather than forbidden; the *cases* are what must be independent.

So the remaining work is not "more code for this agent to write". It is the owner's to
commission:

| the step | who | what it needs |
|---|---|---|
| the **corpus** — a new directory with its own `corpus-manifest.json`, imported through the **production acquisition path** so the claim ids are the production ones | the **case author** (no arm) | the licence, attribution and canonical URL of every document |
| the **cases** — with the class coverage of §7, the lexical disjointness of A3 and the gold disjointness of §5.4 rule 5 | the **case author** | the three existing sets, read to prove disjointness — **and only to prove disjointness** |
| the **builder** — a byte-reproducible JSON artefact, asserting the nine rules of §5.4 | the **case author** | the corpus |
| the **freeze** — the hashes, the roles and the statement that the freeze precedes any measurement | the **independent reviewer** | the builder and the corpus |
| the **three-arm run** on the sealed split, in the pre-registered order A, B, C | the programme, after the freeze | the frozen set and the frozen oracles |

## 7. The evidence

| the artefact | what it holds |
|---|---|
| `docs-v2/engineering/evidence/TEST-io.matrix.crj.evidence.SourceSpanOracleTest.xml` | 7 tests, 0 failures — the grounded case, the wrong-claim case, the wrong-proposition case, the changed corpus, the two negative cases and the **corrupted-gold control** |
| `docs-v2/engineering/evidence/TEST-io.matrix.crj.microworld.LampTruthOracleTest.xml` | 4 tests, 0 failures — the table, the **cross-check on all four assignments**, the disagreement and the missing answer |
| `docs-v2/engineering/evidence/TEST-io.matrix.crj.evidence.BundledCorpusSpanMeasurementTest.xml` | 3 tests, 0 failures — the **measurement against the served path**: the paraphrase finding, the production ids, and the **positive control** that grounds a genuine span |

## 8. The decision: **CONTINUE**, with the next step named

The instrument is built and proven, so the next step is the one the instrument was missing:
**the corpus, the cases, the builder and the freeze** — commissioned from a **case author who
implements no arm**, and frozen by an **independent reviewer**.

What this agent can do **while** that happens, and what it will do next:

1. **the learned-claims half of arm C** — the second half of OD-082, which needs the oracle to
   validate a claim against something other than the reference. The oracle now exists, so the
   claim's promotion can be scored against **the corpus's own bytes**;
2. the **two open defects** of the arm C slice — the declared cost of the first selection and
   the journey's missing episode row;
3. the **load-sensitive latency assertions** of the research suite, which the owner may want to
   turn from wall-clock bounds into properties of the code.

**Nothing is pushed past the owner:** the merge, the commissioning of the case author, the
appointment of the reviewer and the freeze itself all stay the owner's.
