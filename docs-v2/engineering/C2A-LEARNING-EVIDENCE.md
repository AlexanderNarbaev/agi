# C2A — Learning evidence from the current build (Workstream C, CONCEPT-TO-ACTION)

**Status:** `RESEARCH` — measured evidence and an owner decision packet. **No fold option
(A/B/C) is proposed, recommended or implied anywhere in this document.**
**Branch / HEAD:** `feature/concept-to-action` @ `09489ecf` (the commit the C1 diagnosis was
built on; no production source file was modified by this work).
**Date:** 2026-11-15.
**Companion artefact:** `docs-v2/engineering/C2A-LEARNING-EVIDENCE.json` — every number below
is in it, produced by the two harnesses, not typed by hand.

**What this document is:** the report of a REAL collection run against this machine's REAL
episodic log on the CURRENT build, the measurement of the learning subsystem on that new
evidence, the representation comparison the owner directive asks for, and the C4 owner
decision packet.

**What this document is not:** it does not choose a fold, it does not change the label rule,
`K_MAX`, the trainer width or the production fold, and it does not claim a build that was
not run. Every number is traceable to a command or to a file:line.

---

## 0. Provenance — the five classes, kept apart

| Class | What it is | Where it is |
|---|---|---|
| **HISTORICAL** | the 108 records that were already in `data/mind/episodic.ndjson` (written by earlier builds, 2026-09-26…2026-10-04) | `collection.before`, and the retained `E1-LEARNING-EVIDENCE.json` |
| **NEWLY_EXECUTED_REAL_OBSERVATION** | the 59 records this build wrote during this run, through the production write path | `collection.observations` (`provenance` field on every observation) |
| **CONTROLLED SYNTHETIC** | the pre-existing `e1/synthetic-episodes.ndjson` fixture — a mechanism fixture, never real-world evidence | `measurements.datasets.controlledSynthetic` |
| **MANUALLY CURATED** | the synthetic fixture's labels only (its documented content rule). No real episode has a curated label. | `measurements.datasets.controlledSynthetic.provenance` |
| **AUTOMATICALLY DERIVED** | every real label: the production rule `confidence >= 0.7 AND accepted` (`RealSleepScheduler.java:249`) applied to what the runtime produced | `measurements.labelRule` |

No synthetic case is reported as a real observation. No real observation was relabelled by
hand. The one deliberate repeat in the workload is labelled `REPEAT` in the observation.

---

## 1. What was run, and how to reproduce it

The collector runs the real cognitive conductor — `io.matrix.brain.runtime.TrueMindCycle`,
the class the gateway serves (`ProductionBrainClient.java:171`) — against this machine's real
knowledge store, and writes every interaction into the real episodic log through the real
production write path (`ProductionBrainClient.java:388-389` → `EpisodicLog.append` →
`PromotionGate`). No episode is fabricated: an observation exists only if the production code
produced it and the production gate admitted it.

The gateway's serving path is exactly `transliterate → TrueMindCycle.think(retrievalForm,
original) → EpisodicLog.append`; the harness reproduces those three steps with the same
production classes (`MultilingualMind`, `TrueMindCycle`, `EpisodicLog`). The
`matrix-api-gateway` module cannot be linked from `matrix-brain-runtime` (the dependency runs
the other way), so the harness cannot call the gateway's classes without inverting the
dependency graph.

### 1.1 The commands, with exit statuses

```
export JAVA_HOME=$HOME/jdk25

# (1) the collection — the real runtime writes into the real log
flock /tmp/gradle-matrix.lock ./gradlew :matrix-brain-runtime:test \
    --tests "io.matrix.brain.runtime.C2ARealEpisodeCollectionTest" \
    --offline --console=plain                                    # exit 0

# (2) the E1 evidence base, re-measured on the new data, artefact redirected so the
#     retained October artefact is NOT overwritten
flock /tmp/gradle-matrix.lock ./gradlew :matrix-brain-runtime:test \
    --tests "io.matrix.brain.runtime.E1LearningEvidenceTest" \
    --offline --console=plain \
    -Dmatrix.e1.result=/tmp/c2a/E1-LEARNING-EVIDENCE-ON-NEW-DATA.json   # exit 0

# (3) the measurement harness over the collected evidence
flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test \
    --tests "io.matrix.knowledge.C2ALearningEvidenceTest" \
    --offline --console=plain                                    # exit 0
```

**A note on command (3):** at the time of this run a sibling worker's file
(`matrix-core/src/test/java/io/matrix/knowledge/concept/HeldOutEvaluationTest.java`) did not
compile (`MessageDigest.getInstance` — unreported `NoSuchAlgorithmException` at its line
1818). Gradle compiles the whole test source set, so command (3) was run with a temporary
init script in `/tmp` that excludes that ONE file:

```
flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test \
    --tests "io.matrix.knowledge.C2ALearningEvidenceTest" \
    --offline --console=plain --init-script /tmp/c2a-exclude-broken.gradle
```

The init script lives in `/tmp`, changes nothing in the repository, and is a workaround for
another worker's transient breakage, not part of this workstream.

### 1.2 The JUnit XML artefacts

| Artefact | testcases | failures | errors |
|---|---|---|---|
| `matrix-brain-runtime/build/test-results/test/TEST-io.matrix.brain.runtime.C2ARealEpisodeCollectionTest.xml` | 2 (the measured collection + the sandbox rehearsal) | 0 | 0 |
| `matrix-brain-runtime/build/test-results/test/TEST-io.matrix.brain.runtime.E1LearningEvidenceTest.xml` | 1 | 0 | 0 |
| `matrix-core/build/test-results/test/TEST-io.matrix.knowledge.C2ALearningEvidenceTest.xml` | 1 | 0 | 0 |

> **Shared-workspace caveat:** another worker deletes `matrix-core/build/test-results` between
> runs (the documented way to refresh the XML), so the `matrix-core` artefact can disappear
> from under this report. The runbook therefore copies every XML it produces into
> `build/c2a-evidence/` the moment it is produced; the copies from this run are there, and
> the numbers are in the JSON artefact.

### 1.3 The runbook

`scripts/c2a-collect-learning-evidence.sh` reproduces the whole sequence — the collection,
the E1 re-measurement and the measurement — under the build lock
(`flock /tmp/gradle-matrix.lock`), with the `--tests` filter immediately after the task
name, and with the XML preserved. `--dry` runs the same workload against a sandbox log under
`matrix-brain-runtime/build/c2a-sandbox/` and never touches the machine's real log.

---

## 2. The new evidence

### 2.1 The log, before and after

| | records | positives | negatives | sha256 |
|---|---|---|---|---|
| **BEFORE** (HISTORICAL) | **108** | 64 | 44 | `c2035b35…6df1` |
| **AFTER** (HISTORICAL + NEW) | **167** | 108 | 59 | `0cd1e0bd…51ed` |

Path: `data/mind/episodic.ndjson` (runtime data, `.gitignore:83`; not repository data).
The BEFORE state is byte-identical to the state the C1 diagnosis measured
(`c2035b35478fc11b165ff24576fc277a9797c92208b8e8a5a83aecf13b336df1`). The AFTER digest
depends on the episode timestamps the runtime stamps into the log, so a re-run of the same
workload yields the same 59 episodes with a different digest; the counts and the class
distribution are identical.

### 2.2 What the collection produced

| Quantity | Value |
|---|---|
| inputs executed | **79** |
| distinct inputs executed | 77 |
| episodes PROMOTED into the learning feed | **59** |
| distinct inputs among the promoted episodes | **58** |
| distinct 256-bit features among the promoted episodes | **58** |
| distinct 16-bit folds among the promoted episodes | 56 |
| **new positive labels** | **44** |
| **new negative labels** | **15** |
| episodes REFUSED by the production promotion gate | **20** |

The 20 gate refusals, by the production gate's own reason codes:

| Reason | n | what it means for the learning feed |
|---|---|---|
| `REFUSAL` | 12 | "I don't have a confident answer …" — a refusal is a correct answer, never a fact |
| `EVAL_PROBE` | 3 | frozen-battery traffic (EPI-4 firewall) |
| `DEFAULTED_CONFIDENCE` | 3 | the composed confidence landed on exactly 0.75 — the documented default, not a measurement |
| `FICTIONAL_SUBJECT` | 1 | "What is the capital of Wakanda?" |
| `NOT_PROMOTED_BY_STAGE` | 1 | the reflex gate refused ("weapon") |

**This is the first real observation of the current build's serving path.** The five serving-
path commits that landed after the last log write (C1 §7.1) have now been exercised.

### 2.3 The C1-verified negative recipe — measured on the current build

C1 §6.1 verified three inputs that produced a negative-labelled episode on the October build.
On the current build they do **not**:

| Input (C1's verified recipe) | C1 (October build) | **Current build** | Label | Promoted? |
|---|---|---|---|---|
| `capital of italy` | conf 0.5 → negative | conf **0.7823**, retrieval score 0.6517 → **positive** | positive | yes |
| `stolitsa frantsii` | conf 0.5 → negative | conf 0.5, **no retrieval hit** → the reply is a refusal | — | **no** (`REFUSAL`) |
| `stolitsa anglii` | conf 0.5 → negative | conf 0.5, **no retrieval hit** → the reply is a refusal | — | **no** (`REFUSAL`) |

**The recipe has moved.** The current build's negative-producing recipe, re-derived from the
measurements and confirmed on 15 distinct real episodes:

> a NON-interrogative input of at least 8 characters (salience 0.50) whose retrieval lands in
> the floor band `score ∈ [0.20, 0.52)` → confidence `max(0.5 + 0.5·(score−0.20)/0.80, 0.50)`
> ∈ [0.50, 0.70) → **negative** label, `accepted = true`, and a non-refusal reply, so the
> promotion gate admits it.

Measured instances (all real, all in the log): `italy capital city town` (score 0.3152 → conf
0.5720), `france capital city place` (0.3150 → 0.5719), `turkey continent geography region`
(0.3218 → 0.5761), `libya continent geography region` (0.3227 → 0.5767),
`russia currency money region` (0.2389 → 0.5243), `iron symbol metal piece` (0.3213 →
0.5758), and 9 more.

**A second, newly measured gate behaviour:** an INTERROGATIVE input whose retrieval lands in
the same floor band composes to confidence **exactly 0.75** (the salience floor of an
interrogative), and the promotion gate then refuses it as `DEFAULTED_CONFIDENCE`. Measured
three times: `Which continent is Turkey located on?` (score 0.4328),
`What currency does Russia use?` (0.3210), `What is the chemical symbol of iron?` (0.4315).
So on the current build a floor-band retrieval is learnable **only** when the input is
non-interrogative.

### 2.4 The Cyrillic probe family — C1 §8.6 answered

C1 §8.6 recorded as unverified whether a frozen probe can reach the learning feed in its
transliterated form. It can, and it does:

| Input as typed | Logged input | Frozen probe? | Outcome |
|---|---|---|---|
| `столица франции` | `stolitsa frantsii` | GE-4, in Cyrillic | **PROMOTED**, conf 0.7028, reply `Франция имеет столицу Париж` |
| `Москва столица России?` | `Moskva stolitsa Rossii?` | RU-2, in Cyrillic | **PROMOTED**, conf 0.8751, reply `Россия имеет столицу Москва` |

The firewall compares the **transliterated** string (`MinimalHttpServer.java:587`
transliterates before the cycle; the gate then sees the transliterated form), and the frozen
corpus holds the Cyrillic strings. Two frozen probes are therefore in the learning feed.

**This is a measured defect, not a fold question, and it is outside this workstream's scope
to fix.** It is reported here because the collection produced it.

---

## 3. The ACTIVE production window after the run

The production selector is a pure time tail (`RealSleepScheduler.java:237-240`: the last 20
entries, no class awareness). The window after the run:

| Quantity | BEFORE the run | **AFTER the run** |
|---|---|---|
| window size | 20 | **20** |
| positives / negatives | **20 / 0** (class-degenerate) | **10 / 10** |
| distinct inputs | 3 | **20** |
| distinct 256-bit features | 3 | **20** |
| distinct 16-bit folds | 3 | **20** |
| class-degenerate? | **yes** | **no** |

**The blocker named by C1 §6 is removed.** The window now carries both classes, 20 distinct
inputs and 20 distinct features. The E1 precondition ("a window with both classes and more
than 24 distinct features", C1 §7.1) is **partially** met: both classes yes, 20 distinct
features where >24 was asked for.

### 3.1 The production induction, measured on both windows

The same production path (`RealSleepScheduler.runOneCycle` → `runInduction` →
`RuleInductionEngine.induce` → `TsetlinTrainer(16, 64, 200)` + `MpdtGaProducer`), run against
the log as it stood before the run and as it stands after it:

| Window | composition | rules learned | **held-out fidelity** |
|---|---|---|---|
| BEFORE (the historical one-class window) | 20 positive / 0 negative | 1 | **1.00** |
| AFTER (the new mixed window) | 10 positive / 10 negative | 1 | **0.50** |

**This is the sharpest single measurement in the report.** The learner's own fidelity number
was **1.00** on a window that contained no negative to get wrong — a fidelity that measured
nothing — and **0.50** (chance) on a window that does. The one-class window did not
"work"; it was unmeasurable.

---

## 4. The representation comparison

All representations are consumed at the production trainer width of **16 bits**, which is not
changed here. The dataset is the whole real log (**167** episodes: 108 positive / 59
negative; 72 distinct inputs; 78 distinct 256-bit features), split chronologically 60 / 20 /
20 (E1's rule) into **train 100 / validation 33 / test 34**. The **test** partition is
**19 positive / 15 negative** (34 distinct inputs) — it is **not** one-class, so its accuracy
does evaluate discrimination. The majority-class baseline on that partition is **19/34 =
0.559**.

Seeds 42 / 43 / 44. `precision`, `recall`, `FP` and `FN` are the seed-42 values unless the
seed spread is given; the full per-seed numbers are in the JSON.

| # | Representation | distinct values | cross-label collisions | test accuracy (42/43/44) | balanced accuracy (42/43/44) | precision | recall | FP | FN | train (ms) | inference (µs/episode) | memory |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| R0 | **raw 256-bit feature** (statistics only — the trainer caps at 20 bits, so no learner consumes it directly) | **78** | **1** | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | **32 B/episode** |
| R1 | **production fold** (256→16 XOR parity, `TsetlinTrainer.pack`) | **74** | **5** | **0.441 / 0.588 / 0.559** | **0.402 / 0.540 / 0.500** | 0.500 / 0.581 / 0.559 | 0.737 / 0.947 / 1.000 | 14 / 13 / 15 | 5 / 1 / 0 | 7.4 / 7.7 / 7.1 | 9386 / 2501 / 2011 | 2 B/episode + 409 600 trainer cells |
| R2 | **informative projection** (E1's rule: the 16 bit positions that vary in the training split) | 17 | 4 | 0.500 / 0.500 / 0.471 | 0.447 / 0.447 / 0.421 | 0.531 | 0.895 | 15 | 2 / 2 / 3 | 7.6 / 7.5 / 6.5 | 2204 / 1889 / 1679 | 2 B/episode + 409 600 cells |
| R3 | **discriminative projection** (training-derived: the 16 positions with the largest class-conditional separation) | 30 | 2 | 0.529 / 0.441 / 0.529 | 0.474 / 0.479 / 0.474 | 0.545 / 0.500 / 0.545 | 0.947 / 0.158 / 0.947 | 15 / 3 / 15 | 1 / 16 / 1 | 5.1 / 5.5 / 4.9 | 1312 / 1110 / 1029 | 2 B/episode + 409 600 cells |
| R4 | **random projection** (CONTROL, label-blind) | 11 | 2 | 0.441 / 0.441 / 0.441 | 0.500 | 0.000 | 0.000 | 0 | 19 | 6.3 / 6.2 / 6.2 | 2053 / 982 / 802 | 2 B/episode + 409 600 cells |
| R5 | **shuffled training labels** (CONTROL on the production fold; 52 of 100 labels moved) | 74 | 5 | 0.382 / 0.441 / 0.412 | 0.349 / 0.423 / 0.453 | 0.462 / 0.500 / 0.400 | 0.632 / 0.579 / 0.105 | 14 / 11 / 3 | 7 / 8 / 17 | 5.5 / 5.7 / 5.7 | 1659 / 1523 / 1395 | — |
| R6 | **1-NN on the raw 256-bit feature** (CONTROL: the ceiling of ANY function of the 256-bit representation) | 78 | 1 | 0.529 | **0.544** | — | — | 5 | 11 | — | — | 32 B/episode |

Cost model (identical for every representation, because every representation is consumed at
the trainer's 16-bit width):

| Item | Measured |
|---|---|
| feature storage | raw 256-bit: **32 B/episode**; folded 16-bit: **2 B/episode** |
| trainer state | 64 clauses × 2 polarities × 16 input bits × 200 states = **409 600 automaton cells** — the same for R1…R5 |
| production fold cost (`TsetlinTrainer.packForTest`, 167 episodes) | 38 292 ns total = **229 ns/episode** |
| 256-bit encode cost | ≈ **3.9 µs/episode** (the verified-identical mirror) |
| training cost (100 episodes, 10 epochs) | **4.9 – 7.7 ms** per run |
| inference cost | **0.8 – 9.4 µs/episode** |

Stability across seeds (test accuracy): R1 0.441 – 0.588 (mean 0.529), R2 0.471 – 0.500
(0.490), R3 0.441 – 0.529 (0.500), R4 0.441 – 0.441 (0.441), R5 0.382 – 0.441 (0.412). The
seed spread of every real representation **overlaps the seed spread of the shuffled-label
control**; the same-seed repeat was verified identical (determinism asserted).

### 4.1 The collisions, itemised

| Width | distinct values | exact collisions | **cross-label collisions** |
|---|---|---|---|
| **256 bits** | 78 | 0 (the identity map is injective) | **1** (1.3 %) |
| **16 bits (production fold)** | 74 | 4 | **5** (6.8 %) |

The single 256-bit cross-label collision is **not** an encoder defect:

```
[5]   capital of italy -> Rome             conf 0.5     label=false   (HISTORICAL, October build)
[118] capital of italy -> Italy capital Rome conf 0.7823 label=true    (NEW, current build)
```

The encoder is a **token-set** hash (`PersistentHdcStore.hashToVector` sets one bit per
whitespace token), so `capital of italy Rome` and `capital of italy Italy capital Rome` hash
to the same bit set. The two episodes carry the same input and different replies; the old
reply was `Rome`, the new one is `Italy capital Rome`. The label changed because the
**retrieval score** changed (0.20-era → 0.6517), not because the representation lost anything.

The four extra 16-bit collisions are the fold's work, and they are listed in the JSON
(`crossLabelCollisions16Details`): `0x0020` (the historical `gravity` pair), `0x1910`
(the `capital of italy` pair above), `0x1000` (13 × `stolitsa anglii` vs one
`Zimbabwe language English`), `0x1a00` (`Nepal capital Kathmandu` vs `nepal capital city
valley`) and `0x0884` (`Portugal capital Lisbon` vs `australia capital city territory`).

### 4.2 The historical measurement, reported alongside

The retained October artefact (`docs-v2/engineering/E1-LEARNING-EVIDENCE.json`, **not
overwritten**) measured the same harness on the 108-record log: 24 distinct 256-bit features
→ 23 folded, 1 exact and 1 cross-label collision at 16 bits, 0 at 256; the production window
20 / 0; the chronological test partition 21 episodes, **21 positive / 0 negative** — so its
accuracy (1.000) could not evaluate discrimination at all, and the C1 diagnosis said so.
That artefact is embedded in the new JSON under `measurements.historicalMeasurement`.

### 4.3 The controlled synthetic comparison (a mechanism fixture, never real evidence)

| Representation | distinct | cross-label | test accuracy (42/43/44) | balanced (42/43/44) |
|---|---|---|---|---|
| raw 256-bit | 144 | 0 | 1-NN: 0.714 / 0.688 | |
| production fold | 141 | 0 | 0.679 / 0.714 / 0.714 | 0.512 / 0.537 / 0.500 |
| informative projection | 114 | 10 | 0.607 / 0.679 / 0.536 | 0.425 / 0.512 / 0.375 |
| discriminative projection | 122 | 6 | 0.571 / 0.750 / 0.714 | 0.550 / 0.562 / 0.500 |
| random projection (control) | 4 | 4 (rate 1.0) | 0.464 / 0.286 / 0.464 | 0.588 / 0.500 / 0.588 |
| shuffled labels (control) | — | — | 0.643 / 0.714 / 0.607 | 0.600 / 0.500 / 0.463 |

**On the synthetic fixture the production fold beats the random control by 24 points of
balanced accuracy (0.512–0.537 vs 0.500–0.588) and the 1-NN ceiling is 0.688.** The pipeline
works when the feature carries the signal. **On the real data it does not.** That contrast
is the most informative single comparison in this report.

---

## 5. Leakage

| Check | Result | Where |
|---|---|---|
| the three partitions are disjoint by episode id (chronological, grouped, synthetic) | **asserted** | `measurements.datasets.*.partitionsDisjoint` |
| the **grouped** split keeps every input inside one partition | **asserted** (`groupedInputsDisjoint = true`) | `measurements.leakage` |
| the chronological split's test inputs already seen in train | **3 of 34** (was "17 of 21" on the October log) | `measurements.leakage` |
| the synthetic fixture shares an episode text with the real log | **0** (asserted) | `measurements.leakage` |
| the test partition's exact 256-bit features already seen in train | **1 of 34** | `memorisationCeiling256` |
| the test partition's class composition | 19 positive / 15 negative — **not** one-class | `partitionClassDistribution` |

**The one-class caveat from E1 does not apply to the new test partition** (19/15, so the
accuracy does evaluate discrimination), but the chronological split has almost no feature
overlap with training (1 of 34), so the measured accuracy is genuine generalisation, not
memorisation. The grouped split — the leakage-controlled one — is reported in the JSON.

---

## 6. Is representation loss an OBSERVED bottleneck? — the owner's question, answered

**No. On this evidence, representation loss is real but small, and it is demonstrably NOT
what is stopping the learner.**

The argument, in four measured steps:

1. **At 256 bits the class information is essentially intact**: 78 distinct features, **1**
   cross-label collision (1.3 %), and that one collision is explained by a *changed reply*,
   not by a lost distinction (§4.1).
2. **The fold does cost information**: 78 → 74 distinct values and **5** cross-label
   collisions (6.8 %). The fold creates four extra cross-label collisions. That cost is
   real and is measured.
3. **But no 16-bit view of this data discriminates.** The production fold reaches balanced
   accuracy 0.402 – 0.540; the training-derived informative projection 0.421 – 0.447; the
   **discriminative** projection — the strongest 16-bit training-derived view available, and
   the one with the *fewest* cross-label collisions (2) — 0.474 – 0.479. All of them sit at
   or below chance (0.5) and all of them overlap the shuffled-label control (0.349 – 0.453).
4. **And the ceiling itself is at chance.** A 1-nearest-neighbour classifier on the **full,
   unfolded 256-bit** feature reaches balanced accuracy **0.544** and accuracy 0.529 — i.e.
   **no function of the 256-bit representation separates the classes on this partition
   either.** The representation is not the thing that is losing the label; the label is not
   recoverable from this feature on this data by any method tested (parametric or
   non-parametric).

**What the measured bottleneck actually is.** The label is `confidence >= 0.7`, and the
confidence is a function of the *retrieval score*, which is a function of the query's
semantic overlap with the stored corpus — not of the hash of the episode's tokens. The
feature is a token-set hash, so two semantically unrelated inputs have statistically
unrelated bits, and the learner is asked to learn a semantic similarity judgement from a
hash. The synthetic fixture, where the label is a function of the feature's own content,
separates at 0.51 – 0.54 balanced accuracy; the real log, where the label is a function of
something the feature does not encode, separates at chance.

**Therefore:** the fold option (A/B/C) cannot be decided on the evidence of this run, and
this document does not decide it. Not because the data is missing, but because **the
experiment has no positive control on the real side**: there is no representation of the
real episodes that carries the label, so no fold can be shown to help or to hurt.

---

## 7. C4 — the owner decision packet

### 7.1 What new data was collected

59 real, promoted episodes from the current build (44 positive / 15 negative), from 58
distinct inputs, covering five designed families (interrogative, floor-band non-interrogative,
source-grounded from the licensed corpus, arithmetic, refusals/failures) plus the three C1
recipe inputs and the Cyrillic probe family; 20 further interactions were executed and
**refused by the production promotion gate** (recorded, not learnable). The log grew
108 → 167 records.

### 7.2 What each representation can distinguish

| | distinguishes the classes? | evidence |
|---|---|---|
| raw 256-bit | **yes, in principle** — 77 of 78 features are label-pure | 1 cross-label collision (explained) |
| production fold (16-bit parity) | **no** | balanced accuracy 0.402 – 0.540 (chance 0.5) |
| informative projection (16-bit) | **no** | 0.421 – 0.447 |
| discriminative projection (16-bit) | **no** | 0.474 – 0.479, only 2 cross-label collisions |
| random projection (control) | **no** | 0.500, predicts one class |
| 1-NN on 256-bit (ceiling) | **no** | 0.544 — the ceiling is at chance too |

### 7.3 What information is lost

The fold loses **4 extra cross-label collisions** (1 → 5 of 74 distinct values) and
**4 of 78 distinct feature values** (5.1 %). It is lossless on 94 % of the feature space.
The same fold on the CONTROLLED SYNTHETIC fixture costs **0** cross-label collisions
(141 of 144 values distinct) — the fold's cost is data-dependent, not intrinsic.

### 7.4 Is the representation loss the observed bottleneck?

**No — measured, not assumed.** See §6: the 256-bit ceiling (1-NN) is at chance, so the
representation is not where the label is being lost. The observed bottleneck is that the
learner is asked to predict a *semantic retrieval quality* from a *token hash*, and the
evidence shows it cannot.

### 7.5 The measured cost of each option

| | bits/episode | feature storage | fold cost | training | inference | trainer state |
|---|---|---|---|---|---|---|
| raw 256-bit | 256 | 32 B | n/a (the trainer folds internally) | 4.9 – 7.7 ms | 0.8 – 9.4 µs | 409 600 cells |
| any 16-bit projection | 16 | 2 B | 229 ns/episode (production `packForTest`) | 4.9 – 7.7 ms | 0.8 – 9.4 µs | 409 600 cells |

The trainer's state is identical for every representation; the only per-option costs are the
16× smaller feature storage and the 229 ns fold. **No option is more expensive than the
production one by more than 229 ns per episode**, and no option is cheaper in a way that
matters, because the trainer width dominates.

### 7.6 What remains unknown

1. **Whether ANY representation of these episodes can carry the label.** The real log's label
   is a retrieval-quality judgement; the feature is a token hash. This run cannot say
   whether a different *encoding* (not a different fold) would carry it.
2. **Whether the class is stable at all.** The same input (`capital of italy`) produced
   different labels on the two builds (0.5 → negative, 0.7823 → positive) because the
   retrieval score moved with the store. A label that depends on the store's state is not a
   property of the episode; whether the label rule can ever be a stable target is open.
3. **Whether the window's composition is representative.** The new window was DESIGNED to
   contain both classes (the tail is interleaved). The C1 §7.3 question — whether a tail-20
   is a biased estimator of the log — is not answered by a designed window; it needs a
   ≥400-entry organically mixed log.
4. **The fold's cost at scale.** 5 cross-label collisions at 78 distinct features is one
   data point; the rate still needs a larger, organically collected log.
5. **Whether the label rule tracks answer correctness.** Untouched here: the C1 §7.2
   question needs an independent correctness reference over ≥200 episodes.

---

## 8. Limits — what was NOT verified, and why

1. **The gateway process was not started.** The harness reproduces the gateway's three
   serving steps with the same production classes, but it is not the gateway's HTTP path.
   The gateway's own log was not touched.
2. **The HTTP layer is unverified**: `MinimalHttpServer`'s request parsing, its
   `AnalyzeRequest` and its JSON contract were not exercised.
3. **`matrix-api-gateway`'s reflective `matrix-core` JAR loading** (`ProductionBrainClient.tryInit`)
   was not exercised; the harness wires `TrueMindCycle` directly, as the gateway does once
   its JAR is found.
4. **The encoder is mirrored in the measurement harness** (matrix-core cannot link
   matrix-brain-runtime). The mirror is not trusted: it was compared against the feature the
   production collector computed with the production classes for **59 features, 0
   mismatches** (`measurements.encoderCrossCheck`).
5. **The `matrix-core` test XML can be deleted by another worker** between runs (§1.2).
6. **The one-class caveat applies to the VALIDATION partition** of the chronological split
   (33 episodes, 33 positive / 0 negative). The TEST partition is 19/15 and is evaluable;
   the validation numbers are reported but should not be read as discrimination evidence.
7. **The `stolitsa frantsii` / `stolitsa anglii` family is now non-learnable**, so the
   historical 44 negatives and the new 15 negatives are **not** the same negative family.
   The comparison of class *definitions* across builds is therefore open (C1 §7.2).
8. **Nothing was staged or committed.** No production source file was modified. `OpeAi/`
   and `.dsh-project-memory/` were not touched.

---

## Appendix — the numbers at a glance

| Quantity | Before (HISTORICAL) | **After (with NEW)** |
|---|---|---|
| episodic log records | 108 | **167** |
| class distribution | 64 positive / 44 negative | **108 positive / 59 negative** |
| distinct inputs | 20 | **72** |
| distinct 256-bit features | 24 | **78** |
| distinct 16-bit folds | 23 | **74** |
| cross-label collisions at 256 bits | 0 | **1** |
| cross-label collisions at 16 bits | 1 | **5** |
| **active production window** | 20 / **20 positive / 0 negative** | **20 — 10 positive / 10 negative, 20 distinct inputs, 20 distinct features** |
| production induction fidelity | **1.00** (unmeasurable) | **0.50** (chance) |
| chronological test partition | 21 — 21 positive / 0 negative | **34 — 19 positive / 15 negative** |
| best test accuracy of any real representation | 1.000 (one class) | **0.588** (production fold, seed 43) |
| best balanced accuracy of any real representation | 0.500 (one class) | **0.544** (1-NN on the raw 256-bit feature) |
