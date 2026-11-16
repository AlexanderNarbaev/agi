# EPISTEMIC-LEARNING-LABEL — the meaning of the learning label

**Status:** `MEASURED` — the independently verified signal exists and is measured; **the headline result is a null result**, published as such.
**Date:** 2026-11-16.

**Why this document exists.** The owner's directive, P3: *"confidence computed from retrieval overlap is not
independent task correctness. Add a research-only independently verified outcome/utility signal
where possible (task success, error, cost, validated feedback) without laundering gold labels into the
feature. Distinguish prediction, validation, learning and evaluation provenance."* Today's label is
`confidence >= 0.7 AND accepted` — a measure of the pipeline, not of whether the task was right.

**What is fixed and not negotiable here:** `K_MAX = 20`, the trainer width and the production fold are
retained until an owner-approved change.

**Artefacts (research-only, a new package):**
`matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/research/` — `OutcomeSignal`,
`OutcomeTerms`, `StoreGroundingVerifier`, `LeakageControlledPartition`, `RepresentationComparison`,
`LearningLabelExperiment`; and
`matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/research/LearningLabelExperimentTest.java`.

**Nothing in the production learning path was changed.** `RealSleepScheduler`, `RuleInductionEngine`,
`TsetlinTrainer`, `PromotionGate`, `EpisodicLog`, `LearningAdmission`, `EpisodeQuarantine`,
`TrueMindCycle`, `MindResult`, `K_MAX = 20`, the trainer width (16 bits) and the production fold are
untouched — they are *measured*, not modified. The machine's real store, log and quarantine ledger are
read through **copies** in a temporary directory; nothing under `data/mind/` was written, and the test
asserts the digests of the real log and store before and after.

---

## 1. The signal: what it measures, and how it is obtained without the gold

### 1.1 The four provenances, kept apart on the record

`OutcomeSignal` carries one record per interaction with four distinct provenances:

| Provenance | What it holds | Where it comes from |
|---|---|---|
| `PREDICTION` | the reply, the confidence, the acceptance, the modulator trace | the system's own claim — never a verdict |
| `VALIDATION` | the verdict, the evidence it rests on, and the cost of obtaining it | an independent checker |
| `LEARNING` | the provenance role and the P0.4 admission disposition (`ADMITTED` / `QUARANTINED` / `EXCLUDED`) | the P0.4 gate |
| `EVALUATION` | the harness's expectation — carried, and **never an input to the verdict** | the harness |

The record's constructor refuses a mixed provenance set and refuses `expectationUsedInOutcome == true`,
so the separation is a type-level property, not a promise.

### 1.2 The independent checker

`StoreGroundingVerifier` asks one question of the reply: **is this answer backed by something the
machine actually knows?** It compares the reply against the **machine's own knowledge store** (the
store the mind retrieved from, read as text — 2 015 facts on this machine), never against an
evaluation's gold answers:

* `GROUNDED` — some store fact covers ≥ `GROUNDING_COVERAGE` (0.6) of the reply's content terms;
* `NOT_GROUNDED` — the store holds facts, none covers that share. **This is not "wrong"** — it is
  "not backed by anything the machine holds";
* `REFUSED` — the runtime declined;
* `UNKNOWN` — no independent verdict is possible (no content term, or no store). `UNKNOWN` is kept
  **out of the denominator**, because absence of a verification is not a failure.

The gold answers of any evaluation set are not read by the checker: the checker's only input is the
machine's own knowledge.

### 1.3 The rules I had to choose myself (`[speculative]`)

| Rule | Value | Why it is mine and not the directive's |
|---|---|---|
| what a "content term" is | a word of ≥ 3 characters, lower-cased, minus a small stop list | the directive names no vocabulary rule |
| the grounding threshold | 0.6 of the reply's content terms covered by one fact | the directive names no threshold |
| the stop list | sixty-odd English function words plus a few Russian ones | mine |
| the cost model | wall-clock nanos of the checker, and the bytes a representation keeps | mine |

---

## 2. The comparison

Two datasets, two targets, the same representations, the same partitions.

* **Dataset A — the machine's own observations**: the 226 records of `data/mind/episodic.ndjson`
  (copied), read through the P0.4 gate: 201 labelled, 25 `UNKNOWN`.
* **Dataset B — fresh observations**: 59 interactions produced by the production path
  (`TrueMindCycle` → `EpisodicLog.append` → `PromotionGate`) against a **copy** of the store, over
  60 designed inputs in four families (capital / outside / arithmetic / refusal); 46 labelled,
  13 `UNKNOWN`.

The representations are the production ones: the raw 256-bit feature (`EpisodeFeatureExtractor`),
the production fold (256 → 16 XOR-parity, `TsetlinTrainer.pack`), the informative projection (the
bit positions that vary in the training split), the discriminative projection (the 16 positions with
the largest class-conditional separation), and the simple baselines: the majority class, a
label-blind random projection, and the production fold trained on **shuffled labels**. The 1-NN on
the raw feature is the ceiling of any function of that feature. The trainer is the production one
(`TsetlinTrainer(16, 64, 200, seed)`, 10 epochs).

### 2.1 Dataset A — the machine's observations (226): the production label as the target

| Representation | partition | test | +/- (test) | accuracy | balanced | train ms | infer µs | bytes |
|---|---|---|---|---|---|---|---|---|
| RAW_256 | by-input | 60 | 60/0 | 0.200 | 0.100 | 8 | 1.6 | 32 |
| **FOLD_16** | by-input | 60 | 60/0 | **0.750** | 0.375 | 9 | 0.8 | 2 |
| INFORMATIVE_16 | by-input | 60 | 60/0 | 0.183 | 0.092 | 8 | 1.2 | 2 |
| DISCRIMINATIVE_16 | by-input | 60 | 60/0 | 0.183 | 0.092 | 7 | 0.8 | 2 |
| RANDOM_PROJECTION_16 | by-input | 60 | 60/0 | 0.000 | 0.000 | 9 | 0.7 | 2 |
| **SHUFFLED_LABELS (null)** | by-input | 60 | 60/0 | **0.033** | 0.017 | 9 | 0.9 | 2 |
| MAJORITY_CLASS | by-input | 60 | 60/0 | 0.000 | 0.000 | 0 | 0.0 | 2 |
| ONE_NN_256 | by-input | 60 | 60/0 | 0.767 | 0.383 | 0 | 1.0 | 32 |

**The validated outcome on the same observations is degenerate: 199 `GROUNDED`, 2 `NOT_GROUNDED`,
25 `UNKNOWN`.** Every representation reaches accuracy 0.967 and balanced 0.500 (the majority class)
on the by-input half, which is single-class. **The signal cannot be measured on this dataset** — and
the reason is published rather than hidden.

### 2.2 Dataset B — the fresh observations (59; 46 labelled: 18 / 28): the validated outcome

| Representation | partition | train | test | +/- (test) | accuracy | balanced | train ms | infer µs | bytes |
|---|---|---|---|---|---|---|---|---|---|
| RAW_256 | stratified | 33 | 13 | 5/8 | 0.692 | 0.713 | 2 | 2.3 | 32 |
| FOLD_16 | stratified | 33 | 13 | 5/8 | 0.846 | 0.838 | 2 | 1.7 | 2 |
| INFORMATIVE_16 | stratified | 33 | 13 | 5/8 | 0.923 | 0.938 | 2 | 1.9 | 2 |
| DISCRIMINATIVE_16 | stratified | 33 | 13 | 5/8 | 0.923 | 0.938 | 1 | 1.6 | 2 |
| RANDOM_PROJECTION_16 | stratified | 33 | 13 | 5/8 | 0.615 | 0.500 | 2 | 1.1 | 2 |
| **SHUFFLED_LABELS (null)** | stratified | 33 | 13 | 5/8 | **0.538** | 0.475 | 2 | 1.3 | 2 |
| MAJORITY_CLASS | stratified | 33 | 13 | 5/8 | 0.071 | 0.500 | 0 | 0.0 | 2 |
| ONE_NN_256 | stratified | 33 | 13 | 5/8 | 0.929 | 0.962 | 0 | 6.7 | 32 |

**The seed spread (42/43/44), balanced accuracy, the best usable half per seed:**

| Representation | min | mean | max |
|---|---|---|---|
| RAW_256 | 0.500 | 0.756 | 0.923 |
| FOLD_16 | 0.846 | 0.902 | 0.938 |
| INFORMATIVE_16 | 0.938 | 0.966 | 1.000 |
| DISCRIMINATIVE_16 | 0.938 | 0.954 | 0.962 |
| RANDOM_PROJECTION_16 | 0.500 | 0.792 | 1.000 |
| **SHUFFLED_LABELS (null)** | **0.438** | **0.748** | **1.000** |
| MAJORITY_CLASS | 0.500 | 0.500 | 0.500 |
| ONE_NN_256 | 0.962 | 0.962 | 0.962 |

**The null control's spread (0.438 – 1.000) overlaps every real representation's.** At this sample
size the measurement's noise floor is as high as the signal it is measuring.

### 2.3 The cross-task generalisation

* **Dataset A: impossible, and measured as impossible.** The machine's log has **one** family
  (`machine-log`), so the leave-one-family-out folds have an **empty training half**. The rows are
  reported as empty — never as a score of zero, which would be a false zero.
* **Dataset B: every fold has a single-class test half.** The leave-one-family-out test halves are
  2/0, 15/0, 1/13 and one more. **The store-grounding verdict is a property of the QUESTION
  FAMILY** — the store knows the capital family and does not know the outside family — so it carries
  no information *within* a family, and across families the test half has one class. This is the
  measured reason the cross-task number cannot be quoted.

### 2.4 The acquisition cost

| Item | Measured |
|---|---|
| the acquisition of one observation (the production `think`) | 475.4 ms total / 59 observations = **8.06 ms** per observation |
| the acquisition of one independent verdict (the checker) | 35.9 ms total / 59 = **0.61 ms** per observation (7.5 % of the acquisition) |
| the same over the machine's 226 observations | 52.2 ms total = **0.23 ms** per observation |
| feature storage | raw 256-bit: 32 B/episode; every 16-bit representation: 2 B/episode |
| trainer state | 64 clauses × 2 polarities × 16 bits × 200 states = 409 600 cells (unchanged) |
| training cost | 1 – 9 ms per run, unchanged by this work |

---

## 3. The controls

| Control | What it is | Measured | Expected |
|---|---|---|---|
| **positive** | the **production label** — a signal that IS in the feature (the reply the retrieval produced) — recovered by the production fold against the null control | fold **0.750**, 1-NN **0.767**, null control **0.033** | the harness must find a signal that is there → **PASS** |
| **null** | the same fold trained on **shuffled labels**, scored against the validated outcome | balanced **0.500** | at chance → **PASS** |
| **contamination** | the same observations on **both** halves of the split | balanced **1.000** vs the clean **0.500** | the harness must see a deliberate leak → **PASS** |
| **leakage check** | does any input appear on both sides? | by-family 0, by-input 0, stratified 0; the contamination control 46 (on purpose) | 0 for the clean partitions → **PASS** |

The contamination control is the evidence that the clean numbers are numbers: a split that leaks
raises the balanced accuracy from 0.500 to 1.000 on the same data and the same code.

---

## 4. The negative results, published as such

1. **No representation can be shown to predict the independently verified outcome better than
   chance.** On dataset A the signal is degenerate (199 / 2); on dataset B the null control's seed
   spread (0.438 – 1.000) overlaps every real representation's. This is a **null result**, and the
   null control is the reason it is credible.
2. **The independently verified signal, as constructible on this corpus, is a FAMILY-level
   property.** The leave-one-family-out folds are single-class. A signal that is constant within a
   family cannot serve as a per-observation learning label — whatever the representation.
3. **The production label IS largely recoverable from the production feature** (fold 0.750, 1-NN
   0.767, against a 0.033 null control). That is the **self-fulfilling label** the directive names,
   quantified on this machine's own data: the production feature contains the reply, the reply is
   what the retrieval produced, and the label is the retrieval's score. A fidelity number measured
   against that label measures the pipeline's consistency with itself, not the task's correctness.
4. **The cross-task generalisation cannot be measured on the machine's own observations** — one
   family, and the family is the signal.
5. **`GROUNDED` is not "correct", and the corpus cannot upgrade it.** The checker verifies that the
   answer is faithful to the machine's own knowledge. Whether that knowledge is true is a further
   question this corpus cannot answer: the licensed plate-tectonics corpus (USGS + Wikipedia) does
   not cover the store's country facts, and the store's facts (Wikidata-shaped) are not in the
   licensed corpus at all. **There is no external ground truth for this machine's answers in the
   repository.**
6. **The production trainer cannot be validated as a learner on this data**: the null control
   reaches a balanced accuracy of 1.000 on one 14-record half of dataset B. At this sample size the
   fold's fidelity number is not a measurement.

---

## 5. What this corpus cannot support, and what was NOT verified

* **No task-correctness signal.** Only source-groundedness, refusal and cost are verifiable
  without gold. A signal for "the answer was RIGHT" needs a source outside the machine, and the
  repository licenses none that covers the store's domain.
* **No cross-task claim** on dataset A; on dataset B every cross-task fold is single-class.
* **No claim above chance** for any representation on the validated outcome (the null control
  overlaps).
* **Not verified:** that the store's facts are TRUE (the checker does not verify the store); the
  sensitivity of §2.2 to the 0.6 grounding threshold (published as a fixed value, not a sweep); the
  behaviour of the signal on any dataset larger than 59 observations; anything about the C2A
  designed workload, whose expectations were deliberately not read; the other modules
  (`matrix-core`, the gateway); CI.
* **The machine's real store holds no plate-tectonics facts** — which is why the licensed corpus
  could not serve as the verifier's ground truth: the corpus sits in `matrix-core`'s resources and
  the runtime's knowledge is a different corpus entirely.

---

## 6. The commands, the exit statuses and the evidence

```bash
export JAVA_HOME=$HOME/jdk25
flock /tmp/gradle-matrix.lock bash -c 'rm -rf matrix-brain-runtime/build/test-results && \
  ./gradlew :matrix-brain-runtime:test --tests "io.matrix.brain.runtime.*" --offline --console=plain'
```

* exit **0** — `BUILD SUCCESSFUL in 14s`.
* JUnit XML: `matrix-brain-runtime/build/test-results/test/TEST-io.matrix.brain.runtime.research.LearningLabelExperimentTest.xml`
  — `tests="2" skipped="0" failures="0" errors="0"`.
* The whole scope: **137 classes, 995 tests, 4 skipped, 0 failures, 0 errors**; every number of
  this document is printed to the test's standard output and preserved in that XML.
* The machine's real log and store digests are asserted unchanged by the test
  (`5732004d24956132712f6a875ef6082faa3b8df251421f07390b500e894ca11b` for the log).
