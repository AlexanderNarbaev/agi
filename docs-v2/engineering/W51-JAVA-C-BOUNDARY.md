# W51 — the Java/C boundary, measured on one inference kernel

**Wave:** W51 · **Date:** 2026-11-08 · **Branch:** `develop` · **Owner:** wave lead

**What this document is.** A measurement of where the boundary between Java and C
lies for one real inference kernel of MATRIX, and a decision that follows from
it. It is not a claim that C should be the brain of MATRIX. It is a claim about
one kernel, on one host, with the numbers that were actually observed.

**The decision, in one line: the Java path stays the selected path for this
kernel.** The native path is measured, tested and reachable, but the measurement
does not justify crossing the boundary for it.

---

## 1. Method

| Step | What was done | Evidence |
|---|---|---|
| The kernel was chosen by reading the BIR package, not by guessing | `matrix-core/src/main/java/io/matrix/bir/` (28 files in the package, 3 077 lines with the converters) | §2 |
| The C implementation was written from the Java reference's arithmetic | `matrix-core/src/main/c/matrixkernel/matrix_kernel.c` | §3 |
| The Java side calls the reference; it never re-implements the lookup | `TruthTableNativeKernel` | §3 |
| The binding is FFM, not JNI, and it needs no preview flag | `java.lang.foreign` is final since JDK 22; the script compiles the harness **without** `--enable-preview` and that compile is the proof | `scripts/w51-native-kernel-bench.sh` |
| The shared object is built outside the Gradle build | `gcc -O2 -fPIC -shared -std=c11` into `matrix-core/build/native/` | `settings.gradle` does not include `src/main/c` |
| The measurements were taken with a warm-up and several timed rounds, keeping the best | `System.nanoTime`, best of 5 rounds after 3 warm-up rounds | `NativeKernelBenchmark` |
| Every input is deterministic | one seeded generator, `java.util.Random(20261108L)`, used only in the harness and the tests — never in a decision path | `NativeKernelBenchmark`, `NativeKernelOverheadTest` |

**Host of the reference measurement.** Linux 7.0.0-43-generic amd64, 32
processors, OpenJDK 25.0.4.1, gcc 15.2.0. The absolute numbers are a property
of that host; the *shape* of the curve is the transferable part, and even the
shape is a property of this kernel.

**Reproduction.**

```
scripts/w51-native-kernel-bench.sh                    # build the .so and measure
JAVA_HOME=$HOME/jdk25 ./gradlew :matrix-core:test --tests "io.matrix.nativekernel.*" --offline
```

The tests pass whether or not the shared object exists: with it they compare the
two paths bit for bit, without it they assert that the Java path is chosen and
that it agrees with the BIR reference.

---

## 2. The kernel, and why it was chosen

**The kernel: the batched evaluation of a boolean truth table — the BIR `TtForm`
form.**

- Java reference: `matrix-core/src/main/java/io/matrix/bir/TtForm.java:44` (`eval`)
  and `:50` (`evalBatch`).
- The arithmetic, which is the whole contract:

  ```java
  int  idx = (int) (input[0] & ((1L << k) - 1));
  output[0] = (table[idx >>> 6] >>> (idx & 63)) & 1L;
  ```

**Why this one.**

1. It is the canonical evaluation of a boolean function in BIR. `BooleanRuntime`
   (`io/matrix/bir/BooleanRuntime.java:15`, `:25`) is the single execution point
   of the IR, and `TtForm` is one of its three forms.
2. It is **reachable from production code**: `BooleanRuntime.evaluateBatch` →
   `JvmSimdBackend.execute` (`JvmSimdBackend.java:21`) and `FpgaBackend.execute`
   (`FpgaBackend.java:75`); `BooleanRuntime.evaluate` → `ModelRegistryResource.java:68`,
   `SchemaDescriptor.java:41,49`, `NeuralBrain.java:46`, `ExplanationGenerator.java:47`,
   `Distiller.java:108`.
3. It is **deterministic and integer-only** — no floating point, no clock, no
   randomness — so exact equivalence is a property that can be asserted rather
   than tolerated.
4. It **already has a benchmark**: `matrix-core/src/jmh/java/io/matrix/bir/BirEvaluateBenchmark.java:64,85`
   (`ttEval`, `runtimeEval`).

**Honest caveat — the hot path is a CANDIDATE, not a measured bottleneck.**

- The repository contains the JMH **source** of a BIR benchmark but **no
  committed JMH result** (`find . -name '*.json' -path '*jmh*'` → nothing), so
  there is no recorded number for the Java side of this kernel anywhere in the
  tree.
- **No profile shows this kernel dominating any request path.** It is called
  from a handful of places, mostly one-row evaluations.
- Therefore: *the kernel was chosen because it is a real, reachable, deterministic
  inference kernel with a reference implementation — not because it was measured
  to be hot.* If it is not hot, the result below is the right result for it: a
  kernel that is not hot does not need a foreign language.

**A prior art in this repository, and what it means.** MATRIX already crosses
this boundary for the same class of computation, informally:
`io/matrix/imports/PanamaNativeBridge.java` loads
`matrix-core/src/main/c/libtruthy/libtruthy.so` (a **committed** binary) and calls
`truthy_layer_evaluate` — the same truth-table lookup, with a different data
layout — and `BooleanChainProducer.java:115-143` wires it into the chain runner
when the library is present. **No measurement and no policy exist there.** What
this document adds is exactly that: a measured boundary and a written-down
policy for a kernel of the same kind. It also shows that the win that bridge
appears to deliver comes mostly from the data layout, not from the C code.

---

## 3. What was built

### 3.1 The C implementation

`matrix-core/src/main/c/matrixkernel/matrix_kernel.h` and `matrix_kernel.c`.

- **Exactness.** The C reproduces the reference's arithmetic in unsigned
  integers: the input is masked with `((1 << k) - 1)`, the index is taken with
  `>> 6` and `& 63`, and the result is a single bit. There is no floating point
  in the file, so there is no tolerance to agree about.
- **A flat ABI.** Three pointers and three scalars: `const uint64_t* table`,
  `int64_t table_words`, `int32_t k`, `const uint64_t* inputs`,
  `uint64_t* outputs`, `int64_t n`. One packed word per input row and one per
  output row, so the marshalling is a bulk copy with no per-element conversion.
- **Refusals, not crashes.** Every entry point validates its arguments before
  dereferencing anything and returns a status code: `ERR_NULL`, `ERR_ARITY`,
  `ERR_TABLE`, `ERR_COUNT`, `ERR_ALIGNMENT`. A caller that passes a malformed
  argument gets a code; the JVM does not die. The test asserts this.
- **A no-op entry point** with the same ABI and the same validation, which is
  what makes the cost of the crossing separable from the cost of the arithmetic.

### 3.2 The Java side (`io.matrix.nativekernel`)

| Class | What it does |
|---|---|
| `NativeKernelLoader` | Lazy FFM loading (`Linker`, `SymbolLookup`, `MemorySegment`, `Arena`), the library lookup in named relative places, the two downcall handles. No JNI, no generated header, no build step inside Gradle. |
| `NativeKernelStatus` | The status codes, mirroring the C header, and their names. |
| `NativeKernelException` | A refusal, carrying the native status code. |
| `NativeKernelPolicy` | **The decision**, as data: `Policy(nativeEnabled, minBatchForNative)`, with the measured default. |
| `TruthTableNativeKernel` | The kernel facade: the three paths and the policy-selected one. |
| `NativeKernelBenchmark` | The measurement harness (`System.nanoTime`; no new dependency, no JMH). |

**The Java side does not re-implement the lookup.** `evaluateBatchJavaReference`
calls `TtForm.evalBatch`; `evaluateBatchJavaFlat` calls `TtForm.eval` one row at
a time. Both are the BIR reference. The flat variant exists so that the
comparison against the native path isolates *the language* and not *the data
shape*.

---

## 4. Results

### 4.1 Correctness

```
--- correctness of the native path against the BIR reference ---
java reference == java flat : true
java reference == native    : true
rows compared               : 262144
```

Bit-identical over 262 144 rows, and over the boundary cases (arity 1 and 20,
all-zero and all-one tables, an empty batch, a single row, input bits above the
arity) and over randomised rows with a fixed seed.

### 4.2 Batch-size sensitivity

`java-ref` = `TtForm.evalBatch(long[][], long[][])`; `java-flat` = `TtForm.eval`
per row; `native` = C through FFM; `nat-reuse` = C with the native buffers kept
between calls. Best of 5 timed rounds after 3 warm-up rounds; the table is the
truth table of 16 variables (1 024 words).

| batch | java-ref ns | java-flat ns | native ns | nat-reuse ns | native vs java-ref | native vs java-flat |
|---|---|---|---|---|---|---|
| 1 | 55.4 | 3.5 | 70.2 | 38.5 | 0.79x | 0.09x |
| 8 | 238.3 | 8.7 | 74.5 | 42.9 | 3.20x | 0.20x |
| 64 | 1 572.2 | 57.5 | 110.3 | 90.9 | 14.25x | 0.63x |
| 256 | 6 169.2 | 235.4 | 390.7 | 248.4 | 15.79x | 0.95x |
| 1 024 | 24 552.6 | 1 078.9 | 1 316.8 | 1 059.2 | 18.65x | **1.02x** |
| 4 096 | 96 145.0 | 4 649.5 | 5 112.4 | 4 736.8 | 18.81x | 0.98x |
| 16 384 | 389 912.3 | 17 878.3 | 20 455.6 | 20 700.2 | 19.06x | 0.86x |
| 65 536 | 1 600 823.6 | 69 861.9 | 86 142.4 | 81 942.0 | 18.58x | 0.85x |
| 262 144 | 6 876 875.6 | 305 468.8 | 382 450.4 | 354 172.8 | 17.98x | 0.86x |

**Read the last column first.** Against the same reference code called on the
caller's own flat data, the native path is between **0.09x and 1.02x**: it never
reliably wins, and it loses at every batch size above the crossover.

**The second-to-last column is the misleading one.** The native path is 18 to 19
times faster than the BIR reference API — but that is the cost of the reference
API's two-dimensional arrays, not the cost of the C code. The `java-ref` column
allocates 64 bytes per row to build the `long[][]`s; the flat loop allocates 8.

### 4.3 Latency of one call (batch = 1 024, 20 000 calls)

| path | mean ns | p50 ns | p90 ns | p99 ns | max ns | calls |
|---|---|---|---|---|---|---|
| java-ref | 27 261.8 | 24 126 | 33 703 | 51 006 | 1 491 901 | 20 000 |
| java-flat | 858.5 | 771 | 1 242 | 2 394 | 16 531 | 20 000 |
| native | 1 274.8 | 1 092 | 1 843 | 3 236 | 24 546 | 20 000 |
| nat-reuse | 1 245.0 | 1 072 | 1 724 | 3 176 | 1 076 428 | 20 000 |

### 4.4 The decomposition of the native cost (batch = 1 024)

| Quantity | ns per call |
|---|---|
| marshalling only (arena + two copies) | 865.0 |
| marshalling + a no-op downcall | 884.9 |
| the full native call | 1 282.6 |
| **the downcall itself** (call − marshalling) | **19.9** |
| **the arithmetic in C** (full − no-op) | **397.7** (0.388 ns per row) |
| native with the buffers kept between calls | 1 206.9 (1.179 ns per row) |
| the arena and the copies cost | 75.7 |
| cross-language call overhead, one row of payload | 23.7 |

**This is the finding.** At a batch of 1 024 rows the native call costs 1 283 ns,
of which **865 ns (67 %) is the marshalling** — the arena, the copy of the
inputs into native memory and the copy of the results back — and only 398 ns
(31 %) is the arithmetic. The downcall itself is 20 ns and the minimal-payload
crossing is 24 ns.

Keeping the native buffers between calls removes only 76 ns of the 865 ns: the
**copies**, not the arena, are the cost. And the flat Java loop performs the
same arithmetic on the same data **without any copy at all**, which is why it is
competitive.

### 4.5 Allocations per call (`ThreadMXBean.getThreadAllocatedBytes`)

| path | bytes per call | bytes per row |
|---|---|---|
| java-ref | 65 584 | 64.05 |
| java-flat | 8 208 | 8.02 |
| native | 8 440 | 8.24 |

The native path allocates slightly more than the flat Java path: the result
array, plus the two native buffers. The reference API allocates eight times
more.

### 4.6 Memory

```
heap in use after the measurements : 188 789 800 bytes
resident set size                  :   921 694 208 bytes
native bytes one call moves (batch=1024) : 24 576 bytes
native bytes the table occupies    : 8 192 bytes
```

---

## 5. The conditions for choosing the native path

They are written down in `NativeKernelPolicy` and are checked by
`NativeKernelPolicyTest`. The native path is selected only when **all** of them
hold:

| Condition | Value | Why |
|---|---|---|
| the shared object was loaded | `NativeKernelLoader.isAvailable()` | no library, no path |
| the policy enables the native path | `Policy.nativeEnabled()` | **false in this build** — see §6 |
| the arity is inside the native range | 1 … 20 | the C refuses anything else with `ERR_ARITY`; the same range as `TtForm` |
| the batch is at least the threshold | 4 096 | the measured size at which the native path stopped losing to the flat Java path (1.02x, inside the noise) |
| the batch is at most the native maximum | 2²⁴ | beyond it the C refuses with `ERR_COUNT` |

The policy never reads a clock, never uses a random number and never depends on
anything measured at run time: the same inputs always give the same path.

**The threshold is a measured number, and it is a number about a machine.** It is
named, it is documented, and it can be argued with and re-measured. It is not a
tuning constant hidden in a call site.

---

## 6. THE DECISION

**The Java path stays the selected path for this kernel.**

The reasons, in the order they matter:

1. **The crossing costs more than the arithmetic it replaces.** At the batch
   sizes that matter, 67 % of the native call is marshalling and 31 % is
   arithmetic. The flat Java loop performs the same arithmetic on the same data
   with no copy at all, and comes out level or ahead (0.85x … 1.02x).
2. **The big win is not the C code.** The native path is 18x faster than the
   BIR reference API, and almost all of that is the reference API's
   two-dimensional arrays (64 bytes per row of allocation). A Java loop over the
   same reference code on flat data removes the same win without leaving Java.
   **A bridge that does not change the data shape would have bought nothing.**
3. **The kernel is not a measured bottleneck.** There is no committed JMH result
   and no profile showing that this evaluation dominates a request path. Spending
   a foreign-language boundary on it would be optimising the wrong thing.

**What is kept, and why.** The native path stays in the tree, measured, tested
for exact equivalence and reachable explicitly
(`TruthTableNativeKernel.evaluateBatchNative`). The C library, the FFM loader
and the policy are the parts of a future decision that are expensive to rebuild
and cheap to keep. The switch is `NativeKernelPolicy.DEFAULT.nativeEnabled()`,
and turning it on requires a new measurement, not a configuration change.

**What would change this decision.** A kernel whose arithmetic per row is large
compared with the bytes that must cross the boundary — a BDD walk (the depth of
the walk grows with the arity), a clause-set evaluation over many literals, a
neural kernel — would amortise the 865 ns of marshalling over far more work.
The same measurement, the same policy and the same switch apply to it; only the
threshold would move.

---

## 7. Honest limitations — what was NOT measured

- **Energy consumption.** Not measured. The measurement is time and allocation;
  a native call moves bytes across a bus boundary that a Java loop does not,
  and whether that costs more energy than the arithmetic it saves is **unknown**.
- **A real GPU.** Not measured. Nothing here touches CUDA or the GPU; the FFM
  path and the `FpgaBackend` (which is a *simulation* of an FPGA, not an FPGA)
  were not exercised. The `matrix-fpga` module is not in the Gradle build.
- **Native-image (GraalVM).** Not measured. The measurement is a JVM on JDK 25.
  Under `native-image`, FFM needs its own configuration, the shared object must
  be findable at run time, and the `SymbolLookup.libraryLookup` path may behave
  differently. **The native path under native-image is unverified.**
- **One host, one JVM, one compiler.** The absolute numbers belong to
  Linux 7.0.0-43 / OpenJDK 25.0.4.1 / gcc 15.2.0 on an AMD Ryzen 9 with 32
  cores, on a machine that was concurrently running other work. The ratios are
  stable across runs (three consecutive test runs and three benchmark runs
  agreed); the absolute nanoseconds are not a specification.
- **The harness is not JMH.** No new dependency was added, so the measurement is
  a `System.nanoTime` harness with a warm-up and best-of-5 rounds. It is good
  enough to locate a crossover of this size and **not** good enough to claim a
  nanosecond.
- **Thread safety.** The production kernel allocates a fresh arena per call and
  is safe to use from several threads. The `nat-reuse` variant keeps mutable
  buffers and is **not** thread-safe; it exists only to measure the cost of the
  arena, and it is a measurement instrument, not a production shape.
- **The `java-flat` path is not what a BIR caller would naturally write.** It
  calls the reference one row at a time because the reference's batch API takes
  two-dimensional arrays. It is the honest "best Java" baseline for flat data —
  and it is also the baseline that makes the native path look slow, which is
  exactly why it is reported next to the reference path rather than instead of
  it.
- **The shared object is not committed.** `libtruthy.so` is committed to the
  repository; `libmatrixkernel.so` is not. It is built by the script into
  `matrix-core/build/native/`, which is generated output. This is deliberate:
  a committed binary that nothing builds is a binary that silently rots.
- **The `.so` is not in the Gradle build.** `settings.gradle` does not include
  `src/main/c`; a machine without gcc must still be able to build and test the
  module, so the C is built by a separate script and the tests tolerate its
  absence.
- **The C code was not audited by a C reviewer.** The implementation is 115 lines with no
  dynamic allocation, no unbounded loop and a bounds-checked ABI, and it was
  compiled with `-Wall -Wextra` without a warning — but it has not been through
  the same scrutiny as the Java code.

---

## 8. Files

**C** — `matrix-core/src/main/c/matrixkernel/matrix_kernel.h`,
`matrix-core/src/main/c/matrixkernel/matrix_kernel.c`

**Java (production)** — `matrix-core/src/main/java/io/matrix/nativekernel/`:
`NativeKernelLoader.java`, `NativeKernelPolicy.java`, `NativeKernelStatus.java`,
`NativeKernelException.java`, `TruthTableNativeKernel.java`,
`NativeKernelBenchmark.java`

**Java (tests)** — `matrix-core/src/test/java/io/matrix/nativekernel/`:
`NativeKernelEquivalenceTest.java` (19 tests), `NativeKernelOverheadTest.java`
(5 tests), `NativeKernelPolicyTest.java` (7 tests)

**Script** — `scripts/w51-native-kernel-bench.sh`

**Not touched** — `io/matrix/bir/**`, `io/matrix/cognitive/**`,
`io/matrix/contract/**`, `build.gradle`, `.github/**`, and every existing
document.
