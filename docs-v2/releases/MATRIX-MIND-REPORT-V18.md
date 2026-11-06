# MATRIX-MIND-REPORT-V18

**Tag:** `v18.0.0-mind` · 2026-10-12 · Connectome Alpha
**Baseline:** `045445b9` · **9,314 tests / 0 failures** across 11 buildable modules · gate 0 · FROZEN 0-diff
**Final close-out (RECON-W37.5, 2026-10-16): 9,324 tests / 0 failures** across 11 buildable
modules. Sleep pruning is cross-validated (worst-fold, mutation-verified) per SESSION.md W37.5;
delta vs the tagged baseline includes recovered lost-capability tests and post-tag additions
tracked in KnownFailures.md and SESSION.md.
**Design of record:** `docs-v2/designs/DESIGN-68-living-connectome.md`
**Debt of record:** `docs-v2/quality/KnownFailures.md`

---

## 0. What this release is

The Living Connectome **core** exists, is mutation-verified, and is wired into the retrieval
serving path. The linear nine-stage pipeline that serves user traffic is **unchanged and remains
the only live path**. This release is not a migration; it is the foundation a migration would be
built on, plus the three learner defects that had to be fixed before any of it meant anything.

### Wired vs. tested-only

| Component | State | Evidence |
|---|---|---|
| `NeuroModulatorHub` floor → `HdcRetrievalStage.retrieve` | **WIRED** | `8a994f96`, 841 runtime tests / 0 |
| `NeuroModulatorHub` constructed by `TrueMindCycle` | **NOT WIRED** | nothing constructs one in production |
| `Connectome`, `Synapse`, `CognitiveNode` | tested-only | 93 graph tests, 45 mutants killed |
| `ReplayPhase`, `SleepConsolidator`, `ClauseExplorer` | tested-only | operate on a `Connectome`; none is called |
| Tsetlin `typeOne` / `pack` fixes | **WIRED** | used by `RuleInductionEngine` and `AgentBrainService` |
| `RuleInductionEngine.predict` real routing | **WIRED** | `599adb9b` |

Attention therefore reaches the code that decides whether an answer is served, but nothing in
production moves it off neutral. That is a deliberate default, not an omission — see §4.

---

## 1. The three learner defects that had to go first

W37 arrived asserting `TsetlinTrainer` was a *"proven production learning engine… pinned at 46% due
to hyperparameter mismatch — see W37.3 fix."* W37.3's planned fix was to tune epochs. **That could
not have worked**, and tuning would have produced a curve that looked like progress over a fixture
that was measuring the wrong thing.

### 1.1 `pack()` discarded 15 of 16 words — FIXED

`pack()` was `return words[0]`. Production encodes `dim=1024` into 16 packed longs, so `fires()`
examined **16 bits of a 1024-bit feature vector** while reporting a legitimate-looking fidelity
number.

The pinned 46% was the same bug one level up: `long[16]` rows with `x[j]=rng.nextInt(2)` collapse
to **2 distinct inputs**. The learner was fitting a two-valued function while holding 400 samples.
No hyperparameter change helps when the signal is absent.

### 1.2 `typeOne` rewarded the wrong quantity — FIXED

The real blocker. Canonical Granmo Type Ia rewards a literal for being **true in the input**; the
code rewarded an automaton for **already being right**:

```java
if (incX == v) cl[0][j].inc();   // reward an automaton for agreeing with itself
```

The two coincide only while the automaton agrees with the input. **On a dimension that never
varies they are permanently opposed**, so the clause is driven to require the opposite of what every
sample contains.

Instrumented, 16-bit input with 15 constant dimensions:

```
nFiringPred(1) = 0     <- not ONE positive clause ever fires
nFiringPred(0) = 32    <- all 32 negative clauses fire on every input
typeTwo calls  = 0
301 false negatives, 0 false positives out of 600
```

It had learned literally `always false`.

| case | before | after |
|---|---|---|
| 1-bit | 100% | 100% |
| **16-bit, 15 constant dims** | **62.5%** (11/16 seeds at exactly 50%) | **100%** (16/16) |
| 16-bit random noise | 98.9% | 100% |

**Why it hid for so long:** every Tsetlin fixture written before W37 gave unused bits *random*
values — the one condition under which the old rule accidentally bootstraps. A real caller hits the
constant case whenever a feature vector has an inactive dimension, which is the normal case for a
20-bit automaton fed a 1024-bit vector.

### 1.3 Fidelity measured a stub, not the model — FIXED

`RuleInductionEngine.predict(Object, long[])` **ignored its predictor argument** and returned
"first set bit of the raw input is in an odd position". Every `tsetlinFidelity` and `mpdtFidelity`
in that file measured that placeholder, and the "pick the better engine" comparison compared one
stub against itself.

First real measurement of rule induction that has ever existed:

```
parity-4 of 16   tsetlin=1.000   mpdt=0.500
parity-8 of 16   tsetlin=0.250   mpdt=0.250
```

Note Article II `K_MAX=20` caps `induce()` at 20 inputs, so the 80/20 split leaves **4 test
samples**. Any fidelity figure from that path must state N; measured values ranged 0.000–1.000
across data seeds on the same task.

### 1.4 Parity was under-provisioned, not broken

Recorded for four hours as an OPEN defect. It was measured at the default `s=4.0` and **the
parameter was never swept**:

| parity-k | s=4 | s=8 | s=16 | s=32 | s=64 |
|---|---|---|---|---|---|
| 3 | 87.5% | **100%** | 100% | 100% | 100% |
| 4 | 48.0% | 75.0% | **100%** | 100% | 100% |
| 5 | 50.8% | 51.0% | 62.9% | **100%** | 100% |
| 6 | ~51% | 51.7% | 51.7% | 57.2% | **100%** |

64 → 2048 clauses changed nothing at `s=4`. `s=4 → 32` took parity-3 from 87.5% to 100%. Conjunctions
stay at 100% for every `s` from 4 to 32, so raising it is safe. `TsetlinTrainer.recommendS()` and
`countInformativeBits()` are now public so a caller cannot repeat the mistake.

The bound is **fitted to measurement, not derived**. It coincides with a canonical bound for k-bit
parity with 2^k clauses; the two were not proven equivalent.

---

## 2. Neuromodulation — measured, not asserted

The brief demands *"prove Attention_Level affects retrieval precision/recall."* Measured against
the real `ContentSimilarity` scorer, with negatives sharing templates with real facts so the floor
band is actually straddled:

```
floor=0.20 (attention 0.0): served=13 correct=12  precision=0.92  recall=1.00
floor=0.35 (attention 1.0): served=10 correct=10  precision=1.00  recall=0.83
```

**Attention buys precision by refusing more.** The recall cost is asserted alongside the precision
gain so the second cannot be quietly dropped.

The first corpus had every negative scoring 0.0000 except one at 0.1667 — all below the 0.20
floor — so precision was 1.00 at every floor and the claim was *untestable*. The claim was not
false; the fixture contained no hard cases.

Wired through `HdcRetrievalStage.retrieve(..., floor)`. Both retrieval bodies (persistent and
in-memory) honour it, and so does the ignorance trace. The clamp
`Math.max(RETRIEVAL_FLOOR, requested)` is duplicated in the stage as well as the hub: a safety bound
enforced in one class is a bound the next caller routes around.

---

## 3. Sleep 2.0 — all three phases, verified

| phase | behaviour | measured |
|---|---|---|
| **Replay** | priority = distance from confident-and-accepted | accepted-but-unsure > rejected > confident-correct |
| **Pruning** | batch apply, accept iff held-out accuracy holds | 3 synapses → 1, accuracy 1.0 held |
| **Exploration** | local search over the truth table | repairs real under-trained Tsetlin **75% → 100%**, 2 proposals |

Replaying successes consolidates nothing — a confident correct answer is the one case with nothing
unresolved. That ordering is the entire reason replay exists.

Exploration is **local search, not Gibbs sampling.** `toDecisionBir` exposes the learned function as
a truth table, which is exact, so a clause mutation becomes a single-entry flip. A real MCMC sampler
needs a stationary distribution and mixing guarantees; none of that is established here, and the
javadoc says so rather than borrowing the brief's name.

---

## 4. Safety invariants

- **Veto edges are structurally un-prunable.** Enforced inside `Connectome.prune()`, not by
  convention. Verified as defence in depth: removing the consolidator's own filter survives (the
  prune is the second layer); removing **both** is caught.
- **A modulator can only raise a retrieval floor**, never lower it. Worst case is refusing more.
- **Reward cannot suppress sleep indefinitely.** Capping its cancellation at "half the fatigue"
  looked safe but still fell below the sleep threshold at max fatigue. A fraction is only a floor
  when it cannot cross the threshold; the bound is now a floor on residual demand.
- **Default attention is 0.0.** At mid attention the floor rises to 0.275, which changes serving
  behaviour — more refusals. That is an operator decision, so the default reproduces production
  exactly. Changing it is one constant.

---

## 5. What this release does NOT claim

- **No graph is in the production path.** `TrueMindCycle` runs the same nine-stage ladder it ran
  before this wave.
- **No neuromodulator is constructed in production.** The floor reaches retrieval; nothing moves it.
- **Sleep phases are not scheduled.** All three exist and are proven; none is called by the runtime.
- **Parity at k=7+ is unproven** (~99% at `s≥512`, degrading with k).
- **`recommendS` is fitted, not derived.**
- **`ClauseExplorer` is not an MCMC sampler.**
- **Sleep consolidation uses a single held-out split** with no cross-validation; a graph could
  overfit that split and the pass criterion would not notice.
- **The 8+ independent reviewer gate is NOT satisfied.** No external verdicts exist. Proven
  non-functional in this environment: nine reviewers returned nothing in 77 minutes, and a control
  task whose entire output was the literal string `SMOKE TEST OK` was still `[RUNNING]` after 180
  seconds.
- **LSP is unreliable here**, reporting false duplicate-method and unresolved-symbol errors in
  files javac compiles cleanly. Every LSP claim was checked against javac and grep.

---

## 6. Verification

```
matrix-core             8042 tests    0 failures
matrix-brain-runtime     841 tests    0 failures
matrix-api-gateway       236 tests    0 failures
matrix-spigot             22 tests    0 failures
matrix-quality           23 tests    0 failures
matrix-billing           55 tests    0 failures
matrix-audit             40 tests    0 failures
matrix-operator          20 tests    0 failures
matrix-sdk-java          13 tests    0 failures
matrix-observability     22 tests    0 failures
matrix-tools-distill      4 tests    0 failures
TOTAL (11 modules)     9314 tests    0 failures
```

**Run the modules in sequence, not in one invocation.** A single 11-module Gradle line has reported
`> Task :matrix-core:test FAILED` with zero failing XML and two result files present; matrix-core
alone is 8042/0 in 23 minutes, and all ten others are green serially. That phantom cost about an hour
of chasing a defect that did not exist. Recorded in `KnownFailures.md` as W37.4.

---

## 7. What I got wrong this wave

Every surviving mutation found a **test that could not fail**, not code that was wrong:

- `markPruned`'s veto refusal had **no test at all**.
- The Top-K tie-break test compared signal **values**, which are identical for tied edges — so it
  could not see which edges were actually followed. Comparing the same graph twice could never have
  caught it: `LinkedHashMap` iteration is stable within one instance.
- `Result.modulator()` returned a **freshly constructed** hub, so asserting on it proved nothing
  about what the phase used, and a mutation setting attention to 1.0 survived.
- `totalPlasticity` summed activation deltas that are **zero by construction** for a node that
  forwards its input — every correct replay reported "nothing happened".
- The Professional Baseline test **skipped instead of failing**: Gradle runs tests with the module
  directory as CWD, the store path did not resolve, and `assumeTrue` turned a broken reader into a
  green test that measured nothing. The file written to prevent silently-unmeasured claims was
  itself silently unmeasured.

And two genuine defects surfaced only because a test asserted the *intended* behaviour and the code
did not deliver it: reward suppressing sleep forever, and `Activation.of(1.0, 42L)` resolving to
the varargs overload and putting **the seed into the signal**.

One more, worth stating plainly: the first Professional Baseline measurement reported **1/8 cleared**
and read as a severe usability defect. The system was at **4/4** — the harness parsed the store with
a separator the file does not use and fed truncated JSON punctuation to the scorer. A broken reader
produces confident, plausible, badly wrong numbers about the product, and every one of them was
heading for this document.

**A gate that silently skips everything looks exactly like a clean one.** That equivalence is the
single most expensive failure mode in this codebase, and it has now cost roughly a full working day.