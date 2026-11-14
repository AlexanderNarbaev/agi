# REP-SUBSTRATE.2 — one harness, the premise settled, and the five workloads measured

**Status:** `CANONICAL` for the wave's second stage · **Date:** 2026-11-13 ·
**Artefacts:** `docs-v2/operations/bench/rep-substrate-02-native-bitcount-20261113.json` and
`…-workloads-20261113.json` (machine-readable) with their environment-stamped transcripts
`…-20261113.txt` · **Sources:** `matrix-core/src/jmh/java/io/matrix/benchmark/NativeBitCountBenchmark.java`
and `…/RepresentationWorkloadBenchmark.java`

---

## 1. Why this stage exists

The wave's premise was a comparison made by **two harnesses**: *«FFM 112.65 ns against
Java's 6–16 ns, and the two harnesses disagree on the absolute figure by 2.7×»*. Two
harnesses cannot settle a question about the same code — the difference between them is
the harness. So the stage's first act was to build **one** harness that measures **both
sides of every comparison**, with the same inputs, the same JVM and the same clock, and to
publish the **ratio** rather than the absolute figure.

The rule the stage sets, and the one the wave will keep: **a number without its
environment is not evidence, and a ratio is the only figure that transfers between
machines.**

## 2. The harness

| the item | the value |
|---|---|
| the measurement | JMH **1.37** through the repository's own `me.champeau.jmh` **0.7.3** plugin, the JSON result format |
| the command | `./gradlew :matrix-core:jmh -PjmhBenchmark=<Name> --offline` |
| the budget | the repository's standard: **warmup 5 × 1 s, measurement 10 × 1 s, fork 2, 1 thread** |
| the machine | AMD Ryzen 9 9955HX (16 physical / 32 logical), AVX2 and AVX-512 present, 59 GiB, OpenJDK 25.0.4.1, G1 |
| the seeds | **42** for every random input of every arm |
| the widths | the production ones: the HDC vector is 1024 bits in 16 longs, the trainer's width is 16, the boolean function is over 8 inputs |
| the failure rule | if the native library is missing, the setup **fails loudly** — a benchmark that measures nothing must never exit cleanly with an empty result (the repository's own rule, `matrix-core/build.gradle`) |
| the code under test | the **production** classes: `HammingNative`, `HdcEncoding`, `HdcBinding`, `NeuronLayer`, `TtForm`, `BooleanRuntime`, `TsetlinTrainer` |

**The machine was not idle** — a browser was running during the measurement. The figures
below are the measurements of *this* machine in *this* state; the ratios are what the wave
publishes, and the absolutes are the reason the wave's own rule exists.

## 3. The premise, settled in one harness

Three arms, one harness, 1024 random pairs (seed 42):

| the arm | 1 pair per operation | 1024 pairs per operation |
|---|---|---|
| `javaBitCount` — `Long.bitCount(a ^ b)` | **1.316 ± 0.011 ns** | **43.406 ± 0.359 ns** (0.042 ns per pair) |
| `nativeDowncall` — the FFM downcall to the repository's own `hamming_distance_u64` | **14.922 ± 0.064 ns** | **15 116.263 ± 68.995 ns** (14.8 ns per call) |
| `productionHelper` — `HammingNative.hamming`, the path the system takes today | **1.311 ± 0.008 ns** | **43.565 ± 0.236 ns** |

**Three facts, each measured:**

1. **The FFM downcall costs 11.3× the Java bit count per call** (14.922 / 1.316). The
   wave's quoted ratio — 112.65 ns against 6–16 ns — is 7× to 19×, so **the ratio
   transfers and the absolutes do not**: the same order of magnitude, measured in one
   harness, on this machine.
2. **At the batch level the ratio grows to ~348×**, because the Java arm's loop is
   vectorized by the JIT (0.042 ns per pair is below the cost of a scalar bit count)
   while a downcall cannot be vectorized at all. The per-call figure is the honest one
   for a single comparison; the batch figure is the honest one for a workload that
   compares many pairs at once.
3. **The production helper *is* the Java path.** 1.311 against 1.316 ns — the difference
   is inside the error bands — because the native library is never reached (§4.1 of
   `REP-SUBSTRATE-00-baseline.md`). **The system's bit count costs what Java's bit count
   costs, and the C function costs nothing at all, because it does not run.**

**What follows for the wave.** The native path is not merely slower — it is **absent**,
and even if it were wired correctly it would cost 11.3× more per call for an operation
that the JIT already does in 1.3 ns. The decision that was open — *"the native path is
probably not worth it"* — is now **measured**, and it is a **REJECT** with a number behind
it. Nothing in the runtime changes: the path stays dormant, and this document is the
evidence for that decision.

## 4. The five workloads

The wave's comparison table, measured in one place. All figures are ns per operation with
the repository's standard budget (5 × 1 s warmup, 10 × 1 s measurement, 2 forks).

| the direction | the arm | ns/op | what the number says |
|---|---|---|---|
| **BIR** | `birScalarReusedBuffer` | **0.643 ± 0.007** | one evaluation of the truth table form with the output buffer reused |
| | `birScalarRuntimeAllocates` | **2.255 ± 0.029** | the same evaluation through the runtime, which allocates its own output — **3.51× the reused buffer** |
| | `birBatch` | **143.471 ± 0.358** | 256 evaluations ⇒ **0.560 ns per evaluation**, cheaper than the scalar one |
| **HDC** | `hdcHamming` | **4.010 ± 0.017** | 16 words of `Long.bitCount` — the cheapest distance |
| | `hdcSimilarity` | **4.453 ± 0.071** | the distance plus the arithmetic |
| | `hdcXor` | **10.688 ± 0.217** | **2.7× the Hamming distance** — the difference is the fresh `long[16]` it allocates |
| | `hdcBundle` | **2 109.341 ± 10.862** | 8 vectors ⇒ **264 ns per bundled vector**: the bundle allocates an `int[1024]` per call |
| | `hdcCleanup` | **354.945 ± 2.566** | the nearest item of a corpus of 64 ⇒ **5.5 ns per item** |
| **NeuronLayer** | `neuronLayerEvaluate` | **4 688.089 ± 80.907** | 32 neurons ⇒ **146.5 ns per neuron** (a BitSet slice, an array copy and a BIR call each) |
| | `neuronLayerInputCopy` | **1.812 ± 0.052** | the caller's copy of the input — **0.04 %** of the evaluation, so the cost is inside the layer |
| **Tsetlin** | `tsetlinPackWide` | **572.389 ± 1.427** | the production fold of a 1024-bit vector: **half a microsecond** for the transform that loses 240 bits of information |
| **byte serialization** | `byteSerializationEncode` | **837.266 ± 9.422** | the textual form of 1024 bits (1024 characters, 1 KiB) |
| | `byteSerializationDecode` | **1 121.364 ± 11.034** | the parsing back — **1.34× the encoding** |
| | `byteSerializationSize` | **771.248 ± 17.168** | the encoding again plus the length: **the size itself is a constant** (1 KiB per vector), not a measurement |

### The four readings that change a decision

1. **The buffer-reuse decision is worth 3.5×** on the BIR path (2.255 → 0.643 ns). The
   wave's §F adopted a buffer-reuse API for `HdcEncoding.bundle` and
   `BooleanRuntime.evaluate` — the measurement says the same lever is worth 3.5× where it
   is applied, and it says the *bundle* (which allocates an `int[1024]` per call) is the
   place where the lever is worth the most.
2. **The batch path amortizes**: 0.560 ns per evaluation against 0.643 ns for one
   evaluation on its own. A workload that evaluates one function on many inputs should
   batch — the saving is small, but it is free.
3. **The neuron layer's cost is inside the layer, not at the boundary**: the caller's copy
   of the input is 1.8 ns against 146.5 ns per neuron. A buffer-reuse API at that boundary
   would buy nothing; the slice-and-copy inside `NeuronLayer.evaluate` is the target.
4. **The textual representation costs three orders of magnitude more than the
   representation's own distance**: 837 ns to encode a vector and 1 121 ns to decode it,
   against 4.0 ns for the Hamming distance between two vectors. The wave's earlier audit
   found that the served "HDC retrieval" is lexical; **this measures what that costs** —
   and it is not the distance that is expensive, it is the serialisation around it.

## 5. What this stage does not claim

- **The absolute figures are not a property of the code.** They are a property of this
  machine, this JVM, this budget and this load. Only the ratios are published as
  findings.
- **The machine was not idle.** The repository's wall-clock assertions are sensitive to
  that (see the catalogue's `known_flakes`), and the same caution applies here.
- **The native arm is a harness arm, not a production arm.** It binds the C function
  itself, because the production helper cannot reach it (§3, fact 3). It measures what a
  downcall *would* cost — it does not make the production path use it, and it must not:
  the wave's directive keeps the native path out of the runtime.
- **The correctness of the native function was not part of this measurement.** It is
  proved separately, and it is a test:
  `matrix-core/src/test/java/io/matrix/imports/NativeHammingAgreementTest.java` binds the
  C function the way the FFM API intends (`SymbolLookup.libraryLookup`) and asserts that
  it returns exactly the Java distance over 512 random pairs — and that the production
  helper returns the same. **So the C function is correct and the production path still
  does not call it**: the defect is the lookup, not the function.

## 6. What the wave may now decide

| the direction | the label it can carry now | the number behind it |
|---|---|---|
| the native/FFM path | **REJECT** | 11.3× the Java bit count per call, and **the production path does not use it at all** |
| the buffer-reuse API for `HdcEncoding.bundle` and `BooleanRuntime.evaluate` | **ADOPT** | 3.5× on the BIR path; the bundle allocates an `int[1024]` per call and the XOR a `long[16]` |
| the batch path for repeated evaluations | **ADOPT** | 0.560 ns per evaluation against 0.643 ns |
| the neuron layer's inner slice-and-copy | **OPEN** (the target, not the boundary) | 146.5 ns per neuron against 1.8 ns at the boundary |
| the textual serialisation of a vector | **OPEN** (REP-03's subject) | 837 ns to encode, 1 121 ns to decode, against 4.0 ns for the distance |
| the Tsetlin fold | **REJECT as a similarity-preserving projection** (REP-01) | the kernel's dimension 240 and the collapse of the polarity — already proved on the production code |
