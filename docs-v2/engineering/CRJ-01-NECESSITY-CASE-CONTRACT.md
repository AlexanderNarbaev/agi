# CRJ-01 / EPISTEMIC-NECESSITY — the case-author contract

**Status:** the contract handed to an independent case author. **Date:** 2026-11-28.
**Milestone:** `docs-v2/engineering/CRJ-01-EPISTEMIC-NECESSITY.md` ·
**Pre-registration:** `docs-v2/engineering/CRJ-01-NECESSITY-PREREGISTRATION.md`

> **This contract is the ORACLE contract.** It says what a *correct answer* must be made of and
> which dispositions count as correct. It does **not** say how anything is implemented, and it
> does not name or describe any rule that will later be compared against it. An author who reads
> this contract knows what a correct answer is; an author who does not know what the system will
> do with the cases cannot tune the cases to flatter it.

---

## 1. Your role

You are an **independent case author**. You write **natural questions** over a **synthetic corpus**
together with the **oracle**: the evidence a correct answer must stand on, the alternative
sufficient evidence sets, and the dispositions that count as a correct answer.

You do **not** write code, you do **not** propose or suggest a mechanism, and you do **not**
evaluate any implementation. Your whole deliverable is one JSON file.

## 2. What you may read

| you may read | path |
|---|---|
| this contract | `docs-v2/engineering/CRJ-01-NECESSITY-CASE-CONTRACT.md` |
| the corpus of your domain | `matrix-core/src/test/resources/crj01-reach/<domain>/*.md` |
| the JSON schema of your output | the `TEMPLATE.json` beside your output file |
| the checker | `scripts/necessity-case-check.py` |

## 3. What you must NOT read

The following are **out of bounds**. If you open any of them, say so in the `notes` field of your
output and the milestone will record it.

- any file under `matrix-core/src/main/java/**` — **the implementation**;
- `matrix-core/src/test/java/**` — **the instruments and the previous milestone's suites**;
- `docs-v2/engineering/CRJ-01-REACH-DEV-CASES.json` and `scripts/reach-cases.py` — **the previous
  milestone's case set and its generator**;
- `docs-v2/engineering/CRJ-01-COMPETENCE-REACH.md`, `docs-v2/engineering/CRJ-01-REACH-PREREGISTRATION.md`,
  `docs-v2/engineering/evidence/crj01-reach/**` — **the previous milestone's documents and
  evidence**;
- `docs-v2/engineering/CRJ-01-NECESSITY-PREREGISTRATION.md` and
  `docs-v2/engineering/CRJ-01-EPISTEMIC-NECESSITY.md` — **the pre-registration and the milestone
  document, which name the rules that will be compared**;
- anything under `docs-v2/governance/`, `docs-v2/research/`, `.git/`, `wal/` — **owner state**.

**You may not ask for, and will be given, any information about the rules under comparison.** If
you believe you need it, write that in `notes` and stop; do not guess.

## 4. The corpus

Two synthetic domains, each of four Markdown documents. A document is a `#` title followed by
**declarative sentences**, one per paragraph. Every sentence is a proposition about the domain.

| domain | subject | files |
|---|---|---|
| `wbe` | wind-turbine blade leading-edge erosion | `wbe-a-erosion.md`, `wbe-b-inspection.md`, `wbe-c-coatings.md`, `wbe-d-aerodynamics.md` |
| `hpd` | hospital pharmacy unit-dose dispensing | `hpd-a-dispensing.md`, `hpd-b-identification.md`, `hpd-c-reconciliation.md`, `hpd-d-labelling.md` |

**The corpus carries no labels.** Every sentence is a proposition and nothing is marked as a
gold, a decoy or a distractor. **You** decide which sentences are necessary for a given question.

**The sentences must be quoted verbatim.** A proposition in your file must be byte-identical to
the sentence in the corpus file you name — the checker verifies this and rejects the file
otherwise. Quote the sentence, not a summary of it.

## 5. The unit: a *necessary set* and *sufficient sets*

For one question:

- the **necessary set** is the **minimal** set of corpus sentences a correct answer must stand on:
  remove any one of them and the answer is no longer correct;
- a **sufficient set** is a set of corpus sentences that, taken together, support a correct answer;
- an **alternative sufficient set** is a sufficient set that is **not** the necessary set — a
  different route to the same correct answer. A question may have several.

The necessary set and every alternative sufficient set are **lists of `{corpusFile, proposition}`**
where `proposition` is the corpus sentence **verbatim**.

**A necessary set of one sentence is fine.** Not every question needs two.

## 6. The dispositions

The system's answer to a question is one of three dispositions. Say which of them count as
**correct** for your case.

| disposition | meaning |
|---|---|
| `ANSWERED` | the answer states the proposition(s) as fact, without hedging, and its evidence carries the necessary set (or an alternative sufficient set) |
| `QUALIFIED` | the answer states the proposition(s) **with** an explicit condition, limit, caveat or stated uncertainty, and its evidence carries the necessary set (or an alternative sufficient set) |
| `ABSTAINED` | the answer says it cannot answer, or that the evidence does not settle the question |

`allowedDispositions` lists the dispositions that count as correct for the case. Order does not
matter. A case with **one** allowed disposition is a strict case; a case with **two or three** is
a tolerant one. Use a tolerant list only when the question genuinely admits more than one honest
answer.

## 7. The conditions

Every case carries exactly **one** condition. The condition says what makes the case interesting
for measuring *which evidence a task actually needs*.

| condition | the case is this when |
|---|---|
| `LOWER_RANKED_NECESSARY` | the question's wording points **most directly** at a corpus sentence that is **not** in the necessary set; the necessary sentence is reached only indirectly — by a paraphrase or by different vocabulary. A reader who stopped at the most direct match would miss the necessary evidence. |
| `JOINTLY_NECESSARY` | the necessary set has **two or more** sentences and **no single one of them** carries the answer alone: each contributes something the others do not. |
| `ALTERNATIVE_SUFFICIENT` | there is **at least one alternative sufficient set** besides the necessary set — different sentences, possibly a different file — that supports a correct answer too. |
| `MISSING_PREREQUISITE` | a correct answer needs a proposition the corpus **does not contain**. The workspace cannot carry the whole answer; the honest dispositions are `QUALIFIED` or `ABSTAINED`. |
| `CONTRADICTION` | two corpus sentences **conflict** about the same thing. A correct answer must carry the conflict — it states the conflict, or it abstains; it does not silently pick one side. |
| `SUPERSESSION` | the question's evidence exists in the corpus in a form that a **later** sentence replaces or corrects. Name the sentence that governs. |
| `CHANGED_BINDINGS` | the question's terms are **shared** between the necessary sentence and another sentence of the same file, so which of the two is reached depends on the state of the workspace rather than on the wording alone. |
| `AMBIGUITY` | the question has **two readings** that the corpus supports. A correct answer carries both, or qualifies, or asks. |
| `UNANSWERABLE` | the corpus says **nothing** about the subject of the question. The only correct disposition is `ABSTAINED`. |

**The conditions are about the QUESTION and the CORPUS, not about any implementation.** You are
describing the shape of the evidence, not predicting what a retriever will do.

## 8. The slices

Each case is in exactly **one** slice, and the slices are **disjoint**.

| slice | what it is for |
|---|---|
| `PROPOSAL` | the case the learning episode itself is built from — the question whose evidence the system studies |
| `VALIDATION` | a held-out case, used to decide whether a candidate may be promoted; it must not be the learning episode's own question |
| `EVALUATION` | a measured case |

**Your assignment's four cases must be split 1 `PROPOSAL` + 1 `VALIDATION` + 2 `EVALUATION`.**

**No necessary sentence may appear in two slices of your assignment.** The learning episode must
not be handed the evidence the validation is decided on. Across the whole case set the milestone
will check this again; if two assignments in the same domain collide, the milestone says so in
the open rather than hiding it.

**Put at least one `LOWER_RANKED_NECESSARY`, `JOINTLY_NECESSARY` or `ALTERNATIVE_SUFFICIENT`
case into `EVALUATION`** — those are the cases where the shape of the necessary set is the point.

## 9. The output contract

**One file, one path, assigned to you.** Write **exactly one** file and nothing else.

```
docs-v2/engineering/evidence/crj01-necessity/case-authors/<ASSIGNMENT-ID>.json
```

Do not create directories, do not write a second file, do not modify any file that already
exists, do not run Gradle, do not commit, do not `git add`.

### The schema

```json
{
  "assignment": "A1",
  "domain": "wbe",
  "author": "the DSH case-author subagent for assignment A1",
  "independence": "the author read ONLY this contract and the corpus of the assigned domain; no implementation, no instrument, no previous case set, no pre-registration",
  "cases": [
    {
      "id": "EN-WBE-A1-1",
      "domain": "wbe",
      "slice": "EVALUATION",
      "condition": "LOWER_RANKED_NECESSARY",
      "level": "INTRODUCTORY",
      "question": "…",
      "answerable": true,
      "necessaryEvidence": [ { "corpusFile": "wbe-a-erosion.md", "proposition": "…" } ],
      "alternativeSufficientSets": [ [ { "corpusFile": "…", "proposition": "…" } ] ],
      "allowedDispositions": [ "ANSWERED" ],
      "corroboration": "NONE",
      "contradiction": "NONE",
      "note": "why this case is in this condition, and what makes the necessary sentence necessary"
    }
  ],
  "notes": "anything the milestone must know: a constraint you hit, a file you opened that you were told not to open, a condition you could not satisfy"
}
```

Field rules:

| field | rule |
|---|---|
| `id` | unique across the whole case set; the prefix is `EN-<DOMAIN>-<ASSIGNMENT>-<n>` |
| `slice` | exactly one of `PROPOSAL`, `VALIDATION`, `EVALUATION` |
| `condition` | exactly one of the nine in §7 |
| `level` | `INTRODUCTORY` or `INTERMEDIATE` — the depth the question asks for |
| `question` | a **natural** question a person would ask; not a template, not "What does X depend on?" for every case; not a compound question unless the condition calls for one |
| `answerable` | `false` for `MISSING_PREREQUISITE` and `UNANSWERABLE`, `true` otherwise |
| `necessaryEvidence` | the minimal necessary set; at least one sentence; `proposition` verbatim |
| `alternativeSufficientSets` | a list of sets; `[]` when the condition is not `ALTERNATIVE_SUFFICIENT` |
| `allowedDispositions` | from `ANSWERED`, `QUALIFIED`, `ABSTAINED`; at least one |
| `corroboration` | `NONE`, or one sentence saying which corpus sentence corroborates the necessary set |
| `contradiction` | `NONE`, or one sentence saying which corpus sentences conflict and how |
| `note` | at least one sentence; **why the necessary sentence is necessary** — this is the field the milestone reads when it checks your work |

## 10. The check you must run before you hand off

```bash
cd /home/alexandr-narbaev/Projects/matrix-worktrees/dsh-writer
python3 scripts/necessity-case-check.py docs-v2/engineering/evidence/crj01-necessity/case-authors/<ASSIGNMENT-ID>.json
```

The checker verifies: the file exists and parses; the assignment and the domain are the ones you
were given; there are **exactly four** cases; the slices are 1/1/2; every condition is one of the
nine; **every proposition is byte-identical to a sentence of the corpus file it names**; every
`id` is unique and correctly prefixed; every question is unique; the `allowedDispositions` are
known; the `necessaryEvidence` is non-empty; and no necessary sentence appears in two slices.

**A green checker is necessary and not sufficient.** The checker cannot tell whether a necessary
set is really minimal, whether a paraphrase really is a paraphrase, or whether a contradiction is
really a contradiction. Those are your judgement, and the `note` field is where you show it.

**If the checker is red, fix the file and run it again.** Do not hand off a red file and do not
edit the checker.

## 11. The handoff

Report, in your final message and **nothing else**:

1. the path of the file you wrote;
2. the output of the checker (paste it verbatim);
3. the four case ids with their conditions and slices;
4. anything you could not satisfy, and why.

**Do not paste the case content into the chat** — the file is the deliverable, the chat is not.

---

## 12. The follow-up assignment — held-out cases only

**Why this round exists.** The first round produced **two** `VALIDATION` cases per domain. The
production promotion rule — the one the served path uses, and it is not this milestone's to
change — decides a promotion on **at least five** held-out cases
(`io.matrix.cognitive.algorithms.ProcedureValidator.MINIMUM_VALIDATION_CASES = 5`). With two, no
competence can be promoted at all, and the milestone's journey would have no competence to walk.
The milestone therefore commissions a **second, separate round**: more held-out cases, from the
same independent authors, under the same contract.

**What changes for this round and what does not.**

| | the first round | the follow-up |
|---|---|---|
| the corpus | the same | the same |
| the oracle contract | the same | the same |
| the implementation, the rules, the pre-registration | **not readable** | **not readable** |
| the cases | 4 per assignment | 4 per assignment |
| the slices | 1 `PROPOSAL`, 1 `VALIDATION`, 2 `EVALUATION` | **4 `VALIDATION`** |
| the conditions | one each, from §7 | one each: `LOWER_RANKED_NECESSARY`, `JOINTLY_NECESSARY`, `ALTERNATIVE_SUFFICIENT`, `CHANGED_BINDINGS` |

**The slice rule for this round.** Every case is `VALIDATION`. A held-out case is a question the
promotion is decided on, and it must be a question the learning episode did not ask.

**The one rule that is new.** The necessary sentence of a follow-up case must **not** be a
sentence that another case of the same domain already uses. The checker verifies the
**within-assignment** rule (§8); the whole-set rule is verified by the milestone afterwards, and
a collision is recorded rather than hidden.

