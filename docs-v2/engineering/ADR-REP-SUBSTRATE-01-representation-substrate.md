# ADR-REP-SUBSTRATE-01 — The representation is chosen by the workload, and the choice is measured

**Status:** `CANONICAL` for the wave · **Date:** 2026-11-13 ·
**Subject:** which representation and which computational method each workload of MATRIX deserves
**Companions:** `docs-v2/research/REP-SUBSTRATE-00-baseline.md` (the stage that lost the
`/tmp/w54` harnesses and fixed the environment) · `docs-v2/research/REP-SUBSTRATE-02-workloads.md`
(the harness and the measurements) · `docs-v2/governance/ROADMAP.md` §4.14 (the wave's tasks) ·
`docs-v2/governance/DECISION-REGISTER.md`

---

## 1. The decision

**MATRIX selects the representation and the computational method per workload, according to
the correctness requirements, the resource limits and the measured results. There is no
universal substrate: a representation is not the substrate of the system because it is newer,
smaller, more biologically suggestive or theoretically attractive.**

The decision has two halves. The first half is the **principle**, which the owner set. The
second half is the **table of decisions**, and every row of it carries the measurement that
produced it — because a decision without a measurement is an opinion, and the wave's own
rule is that a number without its method is a number nobody can reproduce.

### 1.1 The table

| the direction | the decision | the evidence behind it |
|---|---|---|
| the dense `long[]` truth table (`TtForm`/`BirForm`) | **ADOPT** | the canonical form; the scalar evaluation costs **0.643 ± 0.007 ns** with a reused output buffer (`rep-substrate-02-workloads-20261113.json`) |
| the pure-Java `Long.bitCount` | **ADOPT** | **1.316 ± 0.011 ns** per comparison; the FFM downcall to the repository's own C function costs **14.922 ± 0.064 ns** — **11.3×** — and the production helper never calls it (§3) |
| a buffer-reuse API for `HdcEncoding.bundle` and `BooleanRuntime.evaluate` | **ADOPT** | the runtime's allocation costs **3.51×** the reused buffer (2.255 against 0.643 ns); the bundle allocates an `int[1024]` per call and the XOR a `long[16]` |
| the batch evaluation for repeated evaluations | **ADOPT** | **0.560 ns** per evaluation against **0.643 ns** for a single one — the batch amortizes |
| the native/FFM path for the Hamming distance | **REJECT** | **11.3×** the Java path per call, **~348×** at the batch level (the Java loop is vectorized, a downcall cannot be), **and it does not run**: the production helper loads its library with `System.load` and then asks the default lookup, which does not hold it (`NativeHammingAgreementTest` proves the C function is correct and that the production path still does not call it) |
| the XOR-parity fold as a **similarity-preserving** projection | **REJECT** | on the served width (256 → 16) its kernel has dimension **240**, and it cannot tell a feature from its complement — a parity over sixteen bits does not see the flip of all of them (`EpisodeFeatureFoldLossTest`) |
| the 8-bit bundling | **DEFER** | not measured; the bundle's cost is measured (264 ns per bundled vector) but the alternative's is not |
| the packing of `BitLinear`'s ternary weights | **DEFER** | not measured |
| widening the trainer beyond 20 bits | **DEFER** | Article II caps the width at 20, and widening it changes the learning space and its cost — the owner forbade it as the answer to the fold's loss |
| the Vector API for the BIR kernel | **OPEN** | not measured in this wave |
| the GPU path | **OPEN** | not measured in this wave; the registry already records the GPU as an optional accelerator (OD-047) |
| the optimal HDC width | **OPEN** | the measurements show that the width is not the binding constraint today (§4.3) |
| **the replacement of the fold** | **OPEN — the owner's decision** | it touches serving code; the three options and their cost are in §4.4 |

### 1.2 What the decision is not

It is **not** a promise that one format serves everything, and it is **not** an optimisation
programme. It is the statement that the choice of a representation is a decision with a
price, and that the price is measured before the choice is made.

## 2. The measurement that made the table possible

The wave's first premise was a comparison made by **two harnesses**: *«FFM 112.65 ns
against Java's 6–16 ns, and the two harnesses disagree on the absolute figure by 2.7×»*.
Two harnesses cannot settle a question about the same code — the difference between them is
the harness.

So the wave built **one** harness that measures both sides of every comparison, with the same
inputs, the same JVM and the same clock, and publishes the **ratio**:

```
./gradlew :matrix-core:jmh -PjmhBenchmark=NativeBitCountBenchmark --offline
./gradlew :matrix-core:jmh -PjmhBenchmark=RepresentationWorkloadBenchmark --offline
```

The budget is the repository's own (warmup 5 × 1 s, measurement 10 × 1 s, fork 2, one
thread), the seed is 42, the inputs are the production widths, and the results are committed
with their environment block:
`docs-v2/operations/bench/rep-substrate-02-native-bitcount-20261113.{json,txt}` and
`…-workloads-20261113.{json,txt}`.

**The rule the wave keeps:** a number without its environment is not evidence, and a ratio
is the only figure that transfers between machines. The quoted 112.65 against 6–16 ns is
7× to 19×; the measurement in one harness is **11.3×** — the same order of magnitude, which
is exactly what "the ratio transfers and the absolutes do not" means.

## 3. The premise was wrong in a way worth recording

The comparison was made against a **silent fallback**. `HammingNative` loads its library
with `System.load` and then asks `Linker.nativeLinker().defaultLookup()` for the symbol —
and a library loaded that way is **not** in the default lookup. The symbol is never found,
the static initialiser swallows the exception, and the production path answers with
`Long.bitCount` every time.

So the "FFM against Java" comparison was a downcall against a fallback, and the system's
actual bit count costs exactly what Java's costs. The native path is not merely slower: it
is **absent**, and even if it were wired correctly it would cost 11.3× more for an operation
the JIT already does in 1.3 ns.

**Nothing in the runtime was changed.** The wave's directive keeps the native path out of
the runtime, and the decision whether it should exist at all is §1.1's REJECT row — with a
number behind it.

## 4. The information loss of the fold — and what the data says about its size

### 4.1 The characterization

The trainer folds a multi-word feature into its own width by XOR-parity: the source bit `j`
(counted over the whole vector) lands in the output bit `j mod inputBits`. For the served
width — the brain runtime's `EpisodeFeatureExtractor` produces **256 bits**, and
`RuleInductionEngine` trains a **16-bit** trainer on them — the map is
`F: F₂²⁵⁶ → F₂¹⁶` with:

- **rank 16** (the sixteen unit source bits map onto the sixteen unit output bits, and the
  parity groups are disjoint), hence a **kernel of dimension 240** — 2²⁴⁰ vectors share one
  folded value;
- **F(v) = F(¬v)**, because a parity over an *even* number of bits does not see the flip of
  all of them.

Both are proved on the production code, at the production width, and against the real
episodic log: `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/EpisodeFeatureFoldLossTest.java`
(3 tests) and `matrix-core/src/test/java/io/matrix/tsetlin/TsetlinPackInformationLossTest.java`
(6 tests).

### 4.2 The path is served

Chain B's gateway constructs the engine and hands it the extractor for the sleep cycle
(`MinimalHttpServer`, the *"rule induction ARMED"* branch), the engine trains on the
extractor's 256-bit features, and the serving path folds the same way — the code says it
outright: *"the caller MUST pass raw words, not a pre-folded long, or training and serving
disagree"*.

**So the owner's 240 is the production number, not a hypothetical.**

### 4.3 What the real data says about the size of the loss

Measured on the running gateway's own episodic log (108 entries, 24 distinct texts):

```
[REP-01] the real episodic log: 108 entries, 24 distinct texts, 24 distinct 256-bit features,
         23 distinct folded values, 1 folded values carry more than one text, 2 entries fall
         into a collision
```

**One pair of distinct real episodes is already indistinguishable.** The information-theoretic
loss is 2²⁴⁰ preimages per folded value; the observed cost on this log is 1 of 24 distinct
episodes, and the explanation is the data's shape: the production encoder hashes **one bit
per token**, so a real feature carries **6.5 set bits of 256** on average, and the sixteen
parity groups mix the bits of different tokens.

**The curve, not the number.** A single collision rate says nothing about *when* the loss
becomes the binding constraint, so the wave measured the curve over the real episodes —
the number of collisions as the distinct episodes grow:

```
[REP-01] the fold's collisions over the real episodes: 1:0 2:1 3:1 4:1 … 24:1
         | 24 distinct episodes, 1 collisions at the end, the mean number of set bits in a
           feature 6.5 of 256
[REP-01] the folded values of the real episodes: 23 distinct values for 24 distinct episodes,
         the largest bucket holds 2 of them
```

The collisions are **monotone** — adding an episode can open a new folded value or collide
with an existing one, and it can never repair a collision that already happened — so the
curve never falls. On *this* data the curve is flat at one collision from the second
distinct episode to the twenty-fourth, because the data holds three distinct texts and one
class. **At the production's window (Article II's K_MAX = 20 episodes) the fold's cost is
one pair of episodes.** That is a floor, and it is the floor of a dataset that has not
diversified yet.

And the deeper measurement, on the production's own window (the last twenty episodes, the
production's feature and label rules):

```
[REP-01] the real episodes: 20 entries, 16 of the 256 feature bits vary, 20 positive labels
[REP-01] the downstream fidelity: the production fold 1.0000, the informative projection 1.0000
[REP-01] the window of 20 episodes with 17 positive labels: the original features separate
         them with 1.0000 accuracy, the folded ones with 1.0000
```

**Three facts, and they change the priority:**

1. **The informative width of the production's data is 16 bits — exactly the trainer's
   width.** The trainer is *not* width-limited today.
2. **The production window has one class.** Every one of the twenty labels is positive, and
   the window holds three distinct texts. The reported fidelity of 1.0000 therefore says
   nothing about the learner's discriminative power — it is the accuracy of answering
   "positive" to everything. **The path is data-limited before it is information-limited.**
3. **The fold costs no measurable accuracy on this data** — but the fold is a function of
   the original, so its partition can only be coarser, and the accuracy can only fall as the
   episodes diversify. The measured cost today is a floor, not a ceiling.

**Therefore the first thing to fix on this path is not the fold, but the evidence base**:
the window needs both classes and a diversity of episodes before any fidelity number means
anything. The fold's replacement is a decision about the *next* data, not about today's.

### 4.4 The three options for the fold, and their price

| the option | what it changes | the price | the risk |
|---|---|---|---|
| **A. The trainer refuses a source wider than its width** | the contract: `trainStep`/`trainBatch` reject a source that does not fit | a small change in `TsetlinTrainer` and in the call that feeds it (chain B's rule induction) | the rule induction would **stop working** until the caller is changed — a visible break, which is the point |
| **B. The projection keeps the informative bits** | the fold becomes a projection onto the bits that vary | the definition of "informative" must exist in production (the method already does: `TsetlinTrainer.countInformativeBits`), and the width must grow when the informative set does | the informative set is measured on the *training* data, so it moves with the data; a projection onto it is not a fixed transform |
| **C. The fold stays, and the loss is declared** | nothing in the code; the reported "fidelity" is renamed to what it measures — the fidelity **on the folded representation** | the smallest change of the three | the loss stays, and the name of the number must carry it everywhere it is read |

**The wave does not choose.** Option A and option B touch serving code, and the owner
decides serving code. What the wave hands over is the measurement that makes the choice
possible, and the finding that the **evidence base is the cheaper win**.

## 5. What this wave does not decide

- **The replacement of the fold** (§4.4) — the owner's, because it touches serving code.
- **The width of the HDC vector** and **the GPU path** — not measured, therefore OPEN.
- **The merge of `main` into `develop` and the release of pull request #50** — the owner's
  release decisions, untouched by this wave.
- **Whether the native path should exist at all** — the measurements say it should not be
  *used*; whether the code stays is a separate question, and the answer is a REJECT row in
  §1.1 either way.

## 6. The evidence index

| what | where | how it was produced |
|---|---|---|
| the harness (both sides of every comparison) | `matrix-core/src/jmh/java/io/matrix/benchmark/NativeBitCountBenchmark.java`, `…/RepresentationWorkloadBenchmark.java` | `./gradlew :matrix-core:jmh -PjmhBenchmark=<Name> --offline` |
| the results and their environment | `docs-v2/operations/bench/rep-substrate-02-*.json` and `…*.txt` | the same command, the repository's budget |
| the native path's defect and its correctness | `matrix-core/src/test/java/io/matrix/imports/NativeHammingAgreementTest.java` | `./gradlew :matrix-core:test --tests "io.matrix.imports.NativeHammingAgreementTest"` |
| the fold's properties on the production width and on the real log | `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/EpisodeFeatureFoldLossTest.java` | `./gradlew :matrix-brain-runtime:test --tests "io.matrix.brain.runtime.EpisodeFeatureFoldLossTest"` |
| the fold's properties on the trainer's own width | `matrix-core/src/test/java/io/matrix/tsetlin/TsetlinPackInformationLossTest.java` | `./gradlew :matrix-core:test --tests "io.matrix.tsetlin.TsetlinPackInformationLossTest"` |
| the downstream accuracy and the informative width | `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/RuleInductionDownstreamAccuracyTest.java` | `./gradlew :matrix-brain-runtime:test --tests "io.matrix.brain.runtime.RuleInductionDownstreamAccuracyTest"` |
| the fold's collision curve over the real episodes | `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/EpisodeFeatureFoldScalingTest.java` | `./gradlew :matrix-brain-runtime:test --tests "io.matrix.brain.runtime.EpisodeFeatureFoldScalingTest"` |
| the stage that lost the `/tmp/w54` harnesses, and the environment it fixed | `docs-v2/research/REP-SUBSTRATE-00-baseline.md` | the commands it quotes |
| the measurements of stage 2 | `docs-v2/research/REP-SUBSTRATE-02-workloads.md` | the commands it quotes |

**The standing caveat.** Every figure above is a measurement of **this** machine, **this**
JVM and **this** load; the ratios are the transferable part. The wall-clock assertion of the
repository (`Exp324EndToEndForwardPassLatency`) fails on this host at its 2 ms bound, which
is the same sensitivity the wave's own measurements carry — and the reason this ADR quotes
ratios before absolutes.
