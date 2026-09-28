# RECON-W22 — Root-Cause GENERALIZATION 0/7 (D-W13-1)

> Date: 2026-09-28
> Result: **GENERALIZATION 0/7 → 6/7. Headline 33/45 (0.733) → 43/48 (0.896).**
> Status: **PASS** (bar was ≥5/7, no other category regressed)

## 1. Failure classification — published BEFORE any fix (per §3 W22 requirement)

Primary evidence: `data/mind/benchmarks/w13-live.csv`, all 7 GE rows, `reply` field
decoded. Every one returned `"answer": ""` at confidence 0.75/0.50, with all three
modulators fired and latency 1 ms. The pipeline ran; nothing answered.

| Probe | Sub-class | Observed answer | Failure mode | Root cause (traced) |
|---|---|---|---|---|
| GE-1 | transitivity | `""` | **(a) + (d)** | No stage implements relational reasoning at all |
| GE-2 | transitivity | `""` | (a) + (d) | same |
| GE-3 | transitivity | `""` | (a) + (d) | same |
| GE-4 | cross-lingual | `""` | **(b) + (e)** | RU transliterated to Latin before the mind; no semantic fact store |
| GE-5 | cross-lingual | `""` | (b) + (e) | same |
| GE-6 | novel-composition | `""` | **(a) + knowledge gap** | exemplars disagree (red/orange/yellow); needs lemon→yellow world knowledge |
| GE-7 | novel-composition | `"9.8 m/s^2"` | **(d) false positive** | HDC cosine matched an unrelated stored physics fact |

Two further root causes were found *below* these, and both were necessary:

| # | Root cause | Evidence |
|---|---|---|
| **R1** | **`BenchmarkRunner.passes()` had a terminal `return false`.** It handled 5 categories; GENERALIZATION, PLANNING_DEPTH and RETRIEVAL had no branch and could never pass regardless of the answer. | In `w22-live.csv`, GE-4 recorded `reply="Paris"` with `expected="Paris"` and `passed=false`. The published 0/7 described the **scorer**, not the mind. |
| **R2** | **`MinimalHttpServer.extractField` was string surgery that never decoded `\uXXXX`.** | Same query answered correctly via `curl` (raw UTF-8) and incorrectly via Python `json.dumps` (which escapes by default, as does Jackson). A standards-compliant client got a literal `\u0441\u0442...` string. |

Stage roster at diagnosis time — none of these can do relation composition:
Arithmetic (regex), Analogy (seed table), BIR rules (opaque bitmask lookup: the
registry holds 4 entries, `rule-A`/`r0`/`r4`, whose clauses are literal `long[]`),
HDC retrieval (cosine), Tsetlin (inline classifier, `simulacrumEnabled=false` so it
always misses), MCTS (a placeholder that emits `budget=12`).

## 2. What was built

| File | LOC | Purpose |
|---|---|---|
| `stages/RelationalReasoningStage.java` | 357 | Transitivity over comparative chains + unanimous-attribute propagation |
| `stages/BilingualFactLookup.java` | 150 | Country→capital facts, EN + RU (61 entries) |
| `BenchmarkRunner.java` (edit) | +40 | Scorer defect R1 fixed |
| `MinimalHttpServer.java` (edit) | +45 | JSON defect R2 fixed, original form threaded |
| `TrueMindCycle.java` / `MindCycle.java` (edit) | +60 | New stages wired; `think(input, original)` overload |
| `ProductionBrainClient.java` (edit) | +25 | Carries both retrieval and original form |

**Tests added: 35** (14 RelationalReasoningStage + 10 BilingualFactLookup +
11 BenchmarkScoringContract), all green.

## 3. Measured evidence — live, per-probe

```
GE-1: PASS  exp='carol'  got='carol'
GE-2: PASS  exp='z'      got='z'
GE-3: PASS  exp='c'      got='c'
GE-4: PASS  exp='Paris'  got='Paris'
GE-5: PASS  exp='London' got='London'
GE-6: FAIL  exp='yellow' got="I don't have a confident answer to that."
GE-7: PASS  exp='small'  got='small'
*** GENERALIZATION LIVE: 6/7 ***
```

Official frozen harness (`scripts/w13-live-benchmark.sh`, no probe modified):

```
TOTAL=48 PASSED=43 PASSRATE=0.8958  MEAN_CONF=0.86  MEAN_LATENCY=0.94ms

CAT ARITHMETIC        14/14 (1.00)     [was 14/14]
CAT ANALOGY            5/6  (0.83)     [was 5/6]
CAT CONTRADICTION      4/4  (1.00)     [was 4/4]
CAT ETHICS             3/3  (1.00)     [was 3/3]
CAT RU                 3/3  (1.00)     [was 3/3]
CAT TAUGHT_RETRIEVAL   4/4  (1.00)     [was 4/4]
CAT GENERALIZATION     6/7  (0.86)     [was 0/7  ← +6]
CAT PLANNING_DEPTH     1/4  (0.25)     [was 0/4  ← +1, scorer only]
CAT RETRIEVAL          3/3  (1.00)     [was 0/3  ← +3, scorer only]
```

**No category regressed.** The `+1 PLANNING_DEPTH` and `+3 RETRIEVAL` came from
fixing R1 alone — no mind change was involved.

## 4. Anti-hardcoding evidence (this is a real capability, not probe fitting)

`BilingualFactLookup` holds 61 country→capital entries; only 2 countries appear in
the frozen probes. `BilingualFactLookupTest` asserts answers to questions that were
never asked:

```java
assertThat(lookup.lookup("столица японии").reply()).isEqualTo("Tokyo");
assertThat(lookup.lookup("столица бразилии").reply()).isEqualTo("Brasilia");
assertThat(lookup.lookup("столица египта").reply()).isEqualTo("Cairo");
assertThat(lookup.lookup("what is the capital of japan").reply()).isEqualTo("Tokyo");
```

`RelationalReasoningStageTest` likewise asserts unseen entities and adjectives:

```java
stage.tryEvaluate("Zara richer than Yara, Yara richer than Wanda. Who is poorest?") → "wanda"
stage.tryEvaluate("P taller than Q, Q taller than R, R taller than S. Who is shortest?") → "s"
```

Both stages are pure functions: 25- and 50-iteration repeat calls assert
byte-identical answers (Article III).

## 5. PASS checklist

| # | Criterion | Verdict |
|---|---|---|
| 1 | Failure classification published BEFORE fixing | ✅ §1 |
| 2 | GENERALIZATION ≥5/7 live | ✅ **6/7** |
| 3 | Per-probe engine citations | ✅ §3 + BrcStep `RELATIONAL`/`BILINGUAL_FACTS` now emitted in the trace with `rule=` and `declined=` |
| 4 | Simplification applied without fidelity loss | ✅ `SymbolicSimplifier` corrected in W20 (inverted subsumption) |
| 5 | No other category regressed | ✅ all six others identical or improved |
| 6 | Guards green | ✅ 5 implemented guards re-run green |
| 7 | Probes unmodified | ✅ `BenchmarkScoringContractTest.frozen_generalization_probes_still_carry_their_original_expectations` pins all 7 expected values |

## 6. Deviations & decisions

- **The scorer was fixed rather than the probes.** The anti-regression law forbids
  changing probe definitions to inflate scores. Here the probe set was already
  untouched and *correct*; the code that graded it was returning a hard-coded
  `false`. Fixing a grader that marks `"Paris"` as not-`"Paris"` is the opposite of
  inflation. GE-6, whose answer is an honest refusal, still fails — as it should.
- **GE-6 was NOT forced.** "tomato is red; carrot is orange; banana is yellow.
  lemon is ?" requires knowing lemons are yellow. The stage deliberately declines
  when exemplars disagree rather than picking one arbitrarily. A rule that returned
  "yellow" here would be fitting the probe.
- **The W2 transliteration was kept, not reverted.** It is useful for HDC retrieval.
  Both forms are now carried side by side (`think(input, originalInput)`), so no
  behaviour was removed.
- **A bilingual fact table is knowledge, not a lookup of expected answers.** 61
  entries vs 2 probed, asserted by test.

## 7. "What still fails"

1. **GE-6 (novel-composition) genuinely fails.** Needs world knowledge
   (lemon→yellow), not inference. The stage declines honestly and says why.
2. **PLANNING_DEPTH is 1/4, not 0/4.** One probe now passes; three still do not.
   Diagnosed as a separate root cause — see the W23 report. **OPEN.**
3. **The mind still has no semantic fact store.** The BIR registry still holds 4
   opaque bitmask rules. `BilingualFactLookup` is a 61-entry static table, not a
   learned store; teaching the mind a capital does not work yet. **OPEN — W21.**
4. **70 pre-existing test failures (D-W20-1) remain.** Not touched this wave.
5. **`SimulacrumDefaultOffTest` still does not exist (D-W20-2).** **OPEN.**
6. **GE-7 previously leaked `"9.8 m/s^2"` from HDC.** Fixed by stage ordering, but
   HDC retrieval still has no similarity floor — a different query could still
   return an unrelated stored fact. **OPEN — W24.**

## 8. Next wave

**RECON-W23 — Root-Cause PLANNING_DEPTH 0/4.** The user can newly observe: asking
"Alice is taller than Bob, Bob is taller than Carol. Who is shortest?" now answers
**carol** instead of nothing; asking in Russian "столица франции" now answers
**Paris**; and any unanswered question now returns an explicit
"I don't have a confident answer to that" instead of an empty string — from any
standards-compliant JSON client, which previously got corrupted text.
