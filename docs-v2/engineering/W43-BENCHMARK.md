# W43 — The benchmark of the epistemic slice

**Status:** measured · **Date:** 2026-11-02 · **Wave:** W43 · **Owner decision:** none required
**Baseline:** `b303f22e` → `9cb03ffc` (the wave) → the commits of this document

This is the mission's §10 deliverable: a benchmark for the new vertical
slice, with a train/validation/test split, at least the mission's fourteen
metrics, the denominators of every number, the results per language, the
definitions of the metrics and — the part that matters most — what the
numbers do **not** say.

---

## 1. The two instruments, and why there are two

| | the slice benchmark | the dialogue benchmark |
|---|---|---|
| dataset | `w43-slice-benchmark-v1.json` | `w43-dialogue-benchmark-v1.json` |
| runner | `io.matrix.knowledge.benchmark.SliceBenchmark` | `io.matrix.knowledge.benchmark.DialogueBenchmark` |
| test | `W43SliceBenchmarkTest` | `W43DialogueBenchmarkTest` |
| what it asks | what does the system conclude? | what does it do when it must NOT conclude? |
| cases | 32 | 8 |

They are separate instruments over separate cases, and **their numbers are not
comparable to each other or to the W42 benchmarks.** The W42 v3 benchmark
answers 101 questions with 85 correct (0.842) and refuses 36 with 31 correct
(0.861); the v1 benchmark answers 24 with 24 and refuses 10 with 9 (0.900).
Those two are not comparable with each other either — v3 reuses 24 of v1's
questions verbatim, four more are semantically identical, none shares an id.
The same rule applies here: the slice benchmark says nothing about the
answering accuracy, and the dialogue benchmark says nothing about it either.

## 2. The datasets

### 2.1 The slice dataset — 32 cases

| category | cases | what the case is |
|---|---|---|
| `counterexample_unqualified_claim` | 4 | a contradiction of a claim that states no scope |
| `counterexample_with_condition` | 4 | a contradiction recorded under a condition |
| `counterexample_without_condition` | 4 | a contradiction recorded with no condition |
| `counterexample_narrows_the_scope` | 2 | the condition narrows the scope the claim states |
| `unrestricted_claim_refuted` | 2 | the mission's example: "All birds fly" and a penguin |
| `narrower_claim_not_refuted` | 2 | the mission's example: "Most birds fly under ordinary conditions" and a penguin |
| `exception_qualifies` | 2 | an observation that QUALIFIES the claim rather than contradicting it |
| `supporting_and_contradicting` | 2 | two sources that disagree |
| `insufficient_evidence` | 2 | a link that fails to support |
| `positive_example_only` | 4 | an example and nothing else |
| `no_evidence` | 2 | a claim with nothing recorded |

The split: **train 10, validation 8, test 14.** The test split is not
available to the learning cycle — and the isolation is structural, not only
declared: the runner builds a **store per case** from the case alone, so a
case cannot see another case's claims even by accident.

The languages: **EN 16, RU 16.** The two subsets carry the same logical
cases in both languages.

### 2.2 The dialogue dataset — 8 cases

| category | cases | what the case is |
|---|---|---|
| `clarification_required` | 2 | the referent is unsettled — the honest move is to ask |
| `answered` | 2 | the workspace can answer |
| `abstention_by_coverage` | 2 | the workspace holds nothing that covers it |
| `false_premise` | 2 | the question carries a presupposition that is false |

The split: **train 2, validation 4, test 2.** The languages: **EN 4, RU 4.**

## 3. The metrics, and what each one means

| metric | definition | denominator |
|---|---|---|
| the standing | the epistemic status the system gives the claim, against the truth of the case | all cases |
| the reading | how the counterexample is read — REFUTES, REVEALS_MISSING_CONDITION, INCONCLUSIVE, SCOPE_UNRESOLVED — against the truth | the cases where a reading was expected (22) |
| the proposal | whether a narrowing is proposed, against the truth | all cases |
| the quotation | whether the proposed narrowing quotes the recorded condition verbatim | all cases |
| the answer decision | whether the question was answered, against the truth | all cases |
| the clarification | whether a question back was returned, against the truth | all cases |
| the clarification recall | of the questions whose referent is unsettled, how many got a question back | 2 |
| the clarification precision | of the questions that could be answered, how many were answered rather than refused with a question | 6 |
| the abstention | of the questions the workspace cannot cover, how many were refused | 4 of 6 refusals required |
| the false-premise rejection | of the questions with a false presupposition, how many were refused | 2 |

**The denominator is always written next to the number.** A rate without its
denominator is a decoration.

## 4. The measured results

### 4.1 The slice benchmark — 32 cases

```
the cases 32 | the standing 32/32 | the reading 22/22 | the proposal 32/32 | the quotation 32/32
  by language: en {cases=16, standing 16, reading 16, proposal 16}  ru {cases=16, standing 16, reading 16, proposal 16}
  by split:    train {10, 10, 10}   validation {8, 8, 8}   test {14, 14, 14}
```

The reading is reported over the **22 cases where a reading was expected**,
not over all 32: ten cases have no counterexample, so there is nothing to
read, and counting them as correct readings would inflate the number with
cases that measure nothing.

### 4.2 The dialogue benchmark — 8 cases

```
the cases 8 | the answer decision 6/8 | the clarification 8/8
  by language: en {cases=4, answered 3, clarification 4}   ru {cases=4, answered 3, clarification 4}
  by split:    train {2, 2, 2}   validation {4, 4, 4}   test {2, 0, 2}
  by category: clarification_required {2, 2, 2}   answered {2, 2, 2}
               abstention_by_coverage {2, 2, 2}   false_premise {2, 0, 2}
```

The two failures are the **false-premise** cases, in both languages: the
question presupposes that photosynthesis releases carbon dioxide, and the
system **answers it anyway**.

## 5. What the benchmark found — two defects, measured

### 5.1 The scope comparison was language-dependent, and the benchmark caught it

The first version of the scope test matched a stem **inside** a longer word.
That made "daylight" and "light" match, so the English cases were read one
way; "свете" and "света" — the same idea in Russian, inflected — did not
match, so the Russian cases were read **another way**. The same logical case
was decided differently in two languages, and the asymmetry was invisible
until the two subsets were measured side by side.

The test is now **stem equality**. It has no such asymmetry: it is wrong in
both languages or right in both, which is the only property a lexical test
can honestly claim. The cost is that it misses overlaps that a morphological
analyser would find — the system now says "I cannot decide" and asks, where
it could have decided.

### 5.2 A false premise is answered

The dialogue benchmark's two false-premise cases fail in both languages. The
system accepts the presupposition to get past it. **This is a measured
failure, not a hypothesis**, and it is written down here, in the risk
register, and in the backlog — not in a floor that hides it.

## 6. The limitations — what these numbers do not say

1. **They do not measure understanding.** The slice's decisions depend on
   the evidence relation and on whether a condition was recorded, not on the
   words. The Russian subset therefore measures that the text is carried
   through unchanged and the structure is read — **not** that the system
   understands Russian. The dialogue benchmark is different: its decisions
   DO depend on the words (a reference word, a subject, a presupposition),
   and it is measured separately for exactly that reason.
2. **They do not measure the answering accuracy.** The accuracy of the
   answers the dialogue benchmark accepts is not measured here; the W42
   instrument over its own cases is the only one that measures it.
3. **They do not measure retrieval.** No case here measures whether the
   right claim was found.
4. **The datasets are small.** 32 and 8 cases. The denominators are small
   too: the clarification recall is 2/2, and one case is half the metric.
   A number over two cases is a measurement, but it is not a rate anyone
   should quote without the denominator — which is why the denominator is
   printed next to every one.
5. **The test split is small** (14 of 32 and 2 of 8), and the false-premise
   category has **two** cases in it. The measured rate of 0/2 is a real
   finding, and it is also a rate over two cases.
6. **There are no claims of human-like understanding** anywhere in this
   document, and the system makes none about itself.

## 7. The mission's fourteen metrics, and where each one is measured

| the mission's metric | instrument | status |
|---|---|---|
| traceability of a claim to its evidence | the slice benchmark (`the standing`, `the quotation`) | measured |
| the classification of the evidence relation | the slice benchmark (`the reading`) | measured |
| the detection of a counterexample | the slice benchmark (`the reading`) | measured |
| the rejection of a false premise | the dialogue benchmark | measured — **0/2, both languages** |
| the scope and the negation | the slice benchmark (the scope reading) | measured |
| the rate of unsupported assertions | **not measured** | no instrument counts the assertions the system makes without evidence |
| the abstention precision and recall | the dialogue benchmark | measured (clarification precision 6/6, recall 2/2) |
| the quality of the clarification | the dialogue benchmark | measured — the marker is a phrase, not a judgement of the question |
| the retrieval recall | **not measured here** | the W42 instrument over its own cases |
| the correctness of a revision | the counterexample journey (the live script) | measured, 0 failures |
| the delayed recall | **not measured** | no instrument revisits a claim after a delay |
| the transfer | **not measured** | no case applies a refined model to a new question |
| the persistence after a forced restart | the counterexample journey (`kill -9` and restart) | measured, 0 failures |
| the idempotence of the import and the consolidation | **not measured** | no instrument imports the same source twice |

Seven of the fourteen are measured by these two instruments or by the
journey; seven are not, and each one says so rather than borrowing a number
from another instrument.

## 8. Reproducing

```bash
export JAVA_HOME=/home/alexandr-narbaev/jdk25; export PATH="$JAVA_HOME/bin:$PATH"

# the slice benchmark, with its denominators printed
./gradlew :matrix-core:test --tests "io.matrix.knowledge.benchmark.W43SliceBenchmarkTest" -i --rerun-tasks

# the dialogue benchmark, with its denominators printed
./gradlew :matrix-core:test --tests "io.matrix.knowledge.benchmark.W43DialogueBenchmarkTest" -i --rerun-tasks

# the live journey: example, counterexample, refinement, kill -9, restart
bash scripts/w43-counterexample-journey.sh
```

The datasets are at
`matrix-core/src/main/resources/benchmark/w43-slice-benchmark-v1.json` and
`matrix-core/src/main/resources/benchmark/w43-dialogue-benchmark-v1.json`.
