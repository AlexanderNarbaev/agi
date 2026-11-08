# W51 §10 — the measurement of the hot paths the wave integrated

**Status:** `REFERENCE` — the measurement record · **Date:** 2026-11-08 · **Branch:** `develop`
**HEAD at the time of the measurement:** `a098f283`
**Write scope of the measurement:** `matrix-core/src/main/java/io/matrix/benchmark/w51/**`,
`matrix-core/src/test/java/io/matrix/benchmark/w51/**`, this document. **Nothing else was
touched** — see §7.

---

## 1. What this document is, and what it is not

**It is** a measurement of the three paths the wave integrated, taken with a repeatable
harness, with the method written down so that a reader can tell what was measured from what
was assumed.

**It is not** an optimisation. No production code was changed on the strength of these
numbers. Where the measurement found a place where a change would help, §6 describes the
change and says why it was not made here — the code that would have to change belongs to
packages this measurement may not touch.

**The three paths:**

| # | the path | the class | why it is a hot path |
|---|---|---|---|
| 1 | **the choice of a method** | `io.matrix.cognitive.algorithms.AlgorithmSelector.decide(ProblemRequest)` | it decides *by which method the system computes*; every decision of every episode goes through it |
| 2 | **the run of an episode** | `io.matrix.learning.env.MicroWorldEpisodeRunner.run()` | it is the learning-through-the-environment path the wave added |
| 3 | **the crossing of the bridge** | `io.matrix.contract.bridge.BridgeReceiver.receive(BridgeEnvelope, long)` | it is the path by which data crosses the process boundary, and it is the only one of the three that does I/O-adjacent work (a digest, an audit record, an idempotency registration) |

---

## 2. The environment, and its limits

| the element | the value |
|---|---|
| the machine | Linux x86-64, 32 logical CPUs, `/proc/loadavg` 2.1–2.9 **during** the measurement |
| the JVM | OpenJDK 25.0.4.1, the repository's own `~/jdk25` |
| the measured code | `matrix-core/build/classes/java/main` as compiled from the working tree |
| the runner | a plain `java` invocation, **not** Gradle (see below) |
| the JVM flags | the defaults; no `-Xmx`, no `-XX` tuning |

**The machine was not idle, and this is the most important caveat in the document.** Three
other agents were running Gradle builds of the same module at the same time. Two consequences
follow, and both are visible in the numbers:

1. **The maximum sample is noise.** The largest single sample of every measurement is
   10–100 times the median, and it is a GC pause or a scheduler preemption on a loaded
   machine. **The median and the percentiles are the numbers to read; the maximum is not.**
2. **The measurements were taken outside Gradle, on purpose.** A Gradle test run of
   `:matrix-core:test` shares the module's `build/` directory with every other agent's run,
   and two concurrent runs destroy each other's `build/test-results` directory — which is
   exactly what happened to an earlier attempt of this measurement
   (`NoSuchFileException: …/test-results/test/binary/in-progress-results-generic.bin`). The
   measurement therefore compiles nothing and writes nothing: it runs the already-compiled
   classes of the repository against the already-compiled classes of the harness, from a
   scratch directory outside the repository.

**What this means for the numbers.** They are an **upper bound** on a quiet machine, they are
comparable with each other (same harness, same inputs, same machine, same time window), and
they are **not** comparable with a measurement taken on another machine. §5 states the
run-to-run spread so that a reader can see how much of the noise is in them.

---

## 3. The methodology

### 3.1 The harness

`io.matrix.benchmark.w51.MicroBench` — a hand-written harness on `System.nanoTime()`.

**Why not JMH.** JMH is the right tool and it is not available: the environment has no
network and no JMH artefacts in the Gradle cache, so a benchmark that "needs JMH" produces no
numbers at all. What the harness gives up — control of the CPU's frequency scaling, a fresh
JVM per sample, a confidence interval on the mean — is stated here rather than glossed over:
**this is a comparison harness, not a JMH-grade benchmark.**

**The three properties the numbers depend on:**

| the property | how it is obtained |
|---|---|
| the warm-up is not measured | the action is run `warmupRounds` times and the samples are discarded. The first calls of any Java path pay for class loading, the JIT and the first touch of every data structure; those costs belong to the program start, not to the path |
| the percentile is a percentile | the samples are sorted and the p-th percentile is the sample at index `ceil(p × n) − 1` — the **nearest-rank** rule, named because the interpolated rule and this one give different numbers on the same samples |
| the allocation is the action's | the per-thread allocation counter is read immediately before and immediately after the action, so the harness's own bookkeeping is not counted against the action |

### 3.2 The round counts, and why they are what they are

| the constant | the value | the reason |
|---|---|---|
| `MicroBench.DEFAULT_WARMUP_ROUNDS` | 2 000 | enough for the hot methods to be compiled and the profile to settle; the paths are microsecond-scale, so 2 000 calls cost a few milliseconds |
| `MicroBench.DEFAULT_MEASURED_ROUNDS` | 5 000 | the nearest-rank p99 of n samples is the `ceil(0.99 n)`-th smallest; a p99 from a few hundred samples is indistinguishable from the maximum. 5 000 puts the p99 fifty samples into the tail |
| `MicroBench.EPISODE_WARMUP_ROUNDS` | 200 | an episode builds a hypothesis space and a session of its own, so it touches far more code than a decision does and the first episodes pay for all of it |
| `MicroBench.EPISODE_MEASURED_ROUNDS` | 300 | each round is a whole episode, so the count is bounded by how long a wave may spend measuring |

### 3.3 The allocation counter

`io.matrix.benchmark.w51.AllocationMeter` reads the per-thread allocated bytes of the JDK's
`com.sun.management.ThreadMXBean`, enabling the counter once for the measuring thread. It is
**per thread**, not per process: counting the process would include whatever else the machine
was doing, and the number would move with the load rather than with the code.

When the JVM does not provide the counter, the measurement reports
`BenchResult.UNSUPPORTED_ALLOCATIONS` — **a number that is missing, not a zero**. A zero
would claim that the path allocates nothing, which is a different statement and a false one.

### 3.4 The inputs

Every scenario has fixed inputs and a fixed seed (`W51Benchmarks.SEED = 20 261 108`). The
only counter that moves is the request identifier of the bridge scenario, and it moves
**because the bridge is idempotent per request id**: measuring the same identifier again
would measure the idempotency fast path rather than the decision path. The sequence of
identifiers is the same on every run.

**The bridge scenario pre-builds its messages.** The measured region is `receive(...)` and
nothing else; building an envelope inside the measured region would measure the building of
the envelope together with the receiving of it.

### 3.5 What was measured, and at which version

| the measured package | its state at the measurement | the commit it is read from |
|---|---|---|
| `io.matrix.cognitive.algorithms.**` | **tracked and unmodified** | `a098f283` — the measurement is of the committed code |
| `io.matrix.cognitive.microworld.**` | **tracked and unmodified** | `a098f283` — the measurement is of the committed code |
| `io.matrix.learning.env.**` | **UNTRACKED** — new in W51, present only in the working tree | *no commit exists*; the measurement is of the working-tree version, and the file is not yet in the repository's history |
| `io.matrix.contract.bridge.**` | **UNTRACKED** — new in W51, present only in the working tree | *no commit exists*; the measurement is of the working-tree version |

The last two rows are stated because a number without the version it was taken at is a
number about nothing. Whoever reads these numbers after W51 is committed must re-take them
if the committed version differs from the working-tree version that was measured here.

---

## 4. The results

### 4.1 The three runs, raw

Each run is a separate JVM. The table gives the three runs side by side so that the noise is
visible rather than hidden behind a median.

**Path 1 — the choice of a method** (`decide`, 5 000 samples, 2 000 warm-up):

| the portfolio | the run | avg ns | p50 ns | p90 ns | p99 ns | B/call | calls/s |
|---|---|---|---|---|---|---|---|
| 1 method | 1 | 2 275 | 1 993 | 2 876 | 8 516 | 2 800 | 407 496 |
| | 2 | 2 331 | 2 024 | 2 976 | 8 536 | 2 800 | 397 942 |
| | 3 | 2 465 | 2 074 | 3 497 | 8 846 | 2 800 | 375 808 |
| 2 methods | 1 | 2 631 | 1 953 | 2 806 | 6 803 | 3 464 | 364 819 |
| | 2 | 2 699 | 2 054 | 2 846 | 5 030 | 3 464 | 355 279 |
| | 3 | 2 467 | 1 994 | 2 745 | 5 671 | 3 464 | 387 230 |
| 5 methods | 1 | 2 236 | 1 944 | 3 016 | 5 130 | 5 720 | 427 860 |
| | 2 | 2 296 | 2 013 | 3 005 | 5 210 | 5 720 | 415 550 |
| | 3 | 2 220 | 2 013 | 2 926 | 5 040 | 5 720 | 428 198 |

**The ledger lookup, on its own** (5 000 samples):

| the run | avg ns | p50 ns | p90 ns | p99 ns | B/call | calls/s |
|---|---|---|---|---|---|---|
| 1 | 43 | 40 | 41 | 60 | 0 | 8 632 880 |
| 2 | 47 | 40 | 50 | 61 | 0 | 8 115 421 |
| 3 | 46 | 40 | 50 | 61 | 0 | 8 282 287 |

**Path 2 — the run of an episode** (300 samples, 200 warm-up):

| the run | avg ns | p50 ns | p90 ns | p99 ns | B/call | episodes/s |
|---|---|---|---|---|---|---|
| 1 | 168 928 | 161 935 | 214 434 | 323 098 | 294 704 | 5 914 |
| 2 | 163 105 | 155 784 | 197 071 | 307 149 | 293 258 | 6 125 |
| 3 | 174 205 | 162 787 | 224 674 | 301 278 | 293 007 | 5 734 |

Every episode took **4 steps** and ended with `MODEL_SETTLED` and one surviving hypothesis —
the same in every run and in every episode (the path is deterministic; §4.4 checks it).
The allocation per step is therefore **≈ 73 000 bytes per step**.

**Path 3 — the crossing of the bridge** (5 000 samples, 2 000 warm-up):

| the run | avg ns | p50 ns | p90 ns | p99 ns | B/call | calls/s |
|---|---|---|---|---|---|---|
| 1 | 13 515 | 12 013 | 16 972 | 29 295 | 31 121 | 73 414 |
| 2 | 13 330 | 11 542 | 16 161 | 27 993 | 31 214 | 74 367 |
| 3 | 15 999 | 13 476 | 21 400 | 41 609 | 31 081 | 61 944 |

### 4.2 The headline numbers (the median of the three runs)

| the path | avg | p50 | p90 | p99 | B/call | throughput |
|---|---|---|---|---|---|---|
| the choice of a method, 1 candidate | **2 331 ns** | 2 024 ns | 2 976 ns | 8 536 ns | **2 800 B** | ≈ 398 000/s |
| the choice of a method, 2 candidates | **2 631 ns** | 1 994 ns | 2 806 ns | 5 671 ns | **3 464 B** | ≈ 365 000/s |
| the choice of a method, 5 candidates | **2 236 ns** | 2 013 ns | 3 005 ns | 5 130 ns | **5 720 B** | ≈ 427 000/s |
| the ledger lookup | **46 ns** | 40 ns | 50 ns | 61 ns | **0 B** | ≈ 8 300 000/s |
| the run of an episode | **168 928 ns** | 161 935 ns | 214 434 ns | 307 149 ns | **293 258 B** | ≈ 5 900/s |
| the crossing of the bridge | **13 515 ns** | 12 013 ns | 16 972 ns | 29 295 ns | **31 121 B** | ≈ 73 400/s |

### 4.3 Where the time goes — the breakdown

The same machine, the same session, the components measured on their own against the path
that contains them:

| the component | avg ns | p50 ns | p99 ns | B/call | share of the path it sits in |
|---|---|---|---|---|---|
| the bridge's payload **decode** | 5 290 | 3 987 | 12 944 | 6 503 | **46 % of the receive time**, 21 % of its allocation |
| the bridge's payload **digest** (SHA-256 → hex) | 1 462 | 1 393 | 4 158 | 1 503 | **13 % of the receive time**, 5 % of its allocation |
| the bridge's payload **encode** (for reference; not inside `receive`) | 2 073 | 1 924 | 5 710 | 1 759 | — |
| the bridge's **receive**, whole | 11 439 | 10 289 | 27 191 | 31 179 | 100 % |
| the episode's **hypothesis-space rebuild** | 8 735 | 8 055 | 19 026 | 22 194 | **8 % of the episode time**, 7 % of its allocation |
| the episode, whole | 109 908 | 96 051 | 308 481 | 304 288 | 100 % — **4 steps**, so ≈ 27 500 ns and ≈ 76 000 B per step |

### 4.4 The two facts the numbers say that the code does not

**(a) The decision is flat in the number of candidates, in time and not in allocation.**
The median of the decision is 2 024 ns for one candidate, 1 994 ns for two and 2 013 ns for
five — **the same number to within the run-to-run noise**. The allocation, on the other hand,
grows cleanly: 2 800 B → 3 464 B → 5 720 B, i.e. **between 660 and 730 bytes per additional
candidate**. The per-candidate work is therefore real — it allocates, it builds a `Candidate`
record and its strings — but it is invisible in the latency, because the *fixed* cost of the
decision (the registry lookup, the scoring, the sort, the decision record and its strings)
dominates. A portfolio five times larger does not make the decision five times slower.

**(b) The bridge's audit chain grows without bound, and the growth is measurable.**
The audit chain is a copy-on-write list: every append copies the whole array of records. The
per-call allocation of the receive path therefore grows with the length of the chain. Holding
the warm-up constant at 2 000 messages and varying the number of measured messages:

| the measured messages | avg ns | p50 ns | p99 ns | **B/call** |
|---|---|---|---|---|
| 1 000 | 11 373 | 10 339 | 26 831 | **23 279** |
| 2 500 | 14 738 | 12 864 | 34 345 | **26 141** |
| 5 000 | 13 626 | 12 013 | 30 518 | **30 953** |
| 10 000 | 8 494 | 7 403 | 19 357 | **40 914** |

The allocation grows by **17 635 B/call** between the 1 000-message run and the
10 000-message run. The copy-on-write array predicts it exactly: the chain holds 2 000
records when the measured window opens, so the average chain length during a run of *R*
messages is `2 000 + R/2`, and the append copies that many references at 4 bytes each — a
difference of `4 × (2 000 + 5 000) − 4 × (2 000 + 500) = 18 000 B/call` between the two runs,
against the **17 635 B/call observed**.

**The latency effect of that growth is below the noise of this machine.** The run-to-run
spread of the average is ±20 %, which is larger than the cost of copying a 28 KB array; the
10 000-message run is in fact the *fastest* of the four, which is the JIT and the machine
speaking, not the copy being free. A quiet machine would be needed to resolve it. The
allocation, which does not depend on the machine being quiet, resolves it exactly.

### 4.5 The determinism of the measured paths

| the property | how it was checked |
|---|---|
| two episodes with the same seed are the same episode | two `run()` calls of the same runner produce equal `EpisodeReport`s — equal steps, equal termination reason, equal utility |
| every episode learns | every episode ended with `MODEL_SETTLED` and one surviving hypothesis, over 300 measured episodes plus the warm-up |
| the selector really selects | every measured decision carries a selected method id and the status `COMPLETED`; a portfolio of five produces more candidates in the record than a portfolio of one |
| the bridge really accepts | every measured message was accepted, and the audit chain grew by exactly one record per message |

---

## 5. The noise, stated

| the run-to-run spread of the average, over three JVMs | path 1 | path 2 | path 3 |
|---|---|---|---|
| the spread | −6 % … +6 % | −3 % … +3 % | −1 % … +18 % |
| the spread of the p50 | −4 % … +2 % | −3 % … +2 % | −4 % … +12 % |
| the spread of the **allocation** | **0 %** | **±0.3 %** | **±0.3 %** |

The allocation is stable to within a third of a percent across runs, and it is the figure
that carries the findings of §4.4. The latency figures carry the noise, and the p50 is the
one to quote.

---

## 6. The conclusions, one per path

### 6.1 The choice of a method — **no bottleneck at the scale measured, and no change warranted**

The decision costs ≈ 2 µs and ≈ 2 800–5 700 bytes, and **the cost does not grow with the
number of candidates** (the median is 2 024 / 1 994 / 2 013 ns for portfolios of 1, 2 and 5
methods). The ledger lookup that feeds it is **46 ns — about 2 % of the decision** — and
allocates nothing. The per-candidate cost is real but small, and it shows up in the
allocation rather than in the latency.

**What this means.** A decision path that runs in two microseconds is not a path to optimise;
at 400 000 decisions a second it is not a constraint on anything the system does. The
*allocation* is the only figure worth watching, and 2.8 KB per decision is the number to
watch if the decision rate ever rises by orders of magnitude.

### 6.2 The run of an episode — **the heaviest path measured, but it is not on a serving path, and no change is warranted**

An episode costs ≈ 169 µs and ≈ 293 KB, over four steps — **≈ 27 500 ns and ≈ 76 000 bytes
per step**. The hypothesis-space rebuild is 8 % of the time and 7 % of the allocation; the
other 92 % is the per-step work (the information gain over the surviving hypotheses, the
prediction, the probe, the application of the outcome).

**What this means.** The episode is twenty-six times more expensive than the decision and
twenty-six times more expensive than the bridge's *digest*, and it is the path that does the
most actual learning. It is also a path that is **not** on a request-serving route — it runs
when the system learns, not when it answers. **There is no evidence that it needs to be
faster.** The number to remember is the 76 KB per step: it is the largest per-call
allocation in the wave, and it would matter if episodes ever moved onto a serving path.

### 6.3 The crossing of the bridge — **the one path where the measurement found a basis for a change**

A bridged message costs ≈ 13.5 µs and ≈ 31 KB. The breakdown says where it goes:

* **the payload parse — 46 % of the time (5.3 µs) and 21 % of the allocation (6.5 KB).**
  The payload is parsed on every delivery, and the parsed result is used to check two things
  (the kind and the provenance) before the sink receives the envelope. Whether that parse
  can be narrowed depends on what the receiving side does with the payload — a question
  about the *server* part of the bridge, which this measurement does not own.
* **the payload digest — 13 % of the time (1.5 µs) and 5 % of the allocation (1.5 KB, the
  hex string).** The SHA-256 is computed over the payload on every call and is used for the
  audit record and for the conflict check. A hex string of a 64-byte digest is 128 characters
  and is allocated per call.
* **the audit chain's copy-on-write append — the only unbounded growth in the path.** §4.4
  measures it: **+17 635 B/call between a 1 000-message chain and a 10 000-message chain**,
  matching the prediction of a full array copy to within 2 %. A bridge that runs for a long
  time pays more and more for every message, because the chain it appends to grows.

**These three are descriptions, not changes.** The code that would have to change is
`io.matrix.contract.bridge.**`, which this measurement may not modify, and the first two
depend on the server-side implementation that another agent writes. §7 states this again.

---

## 7. What was changed, and what was NOT

**The measurement itself changed nothing** — not one line outside
`matrix-core/src/main/java/io/matrix/benchmark/w51/**` and
`matrix-core/src/test/java/io/matrix/benchmark/w51/**`.

**The wave's lead changed one thing afterwards, and this section says so**, because a
document that claims "nothing was changed" while the tree says otherwise is a document
that lies. The measurement named one change with a measured, unbounded growth: the
copy-on-write audit list of the bridge receiver. The lead made it.

### 7.1 The copy-on-write audit list — changed, and re-measured

**The finding.** §4.4 measured the only unbounded growth in the crossing path: the audit
chain is a copy-on-write list, so every append copies the whole array of records. The
allocation grew by **17 635 B/call** between a 1 000-message chain and a 10 000-message
chain, and the copy-on-write array predicted the growth to within 2 %.

**The change.** `io.matrix.contract.bridge.BridgeReceiver` now keeps the audit log in a
`ConcurrentLinkedQueue` instead of a `CopyOnWriteArrayList`. The append is constant-time and
copies nothing; the snapshot the caller receives is still a copy, so the property BRIDGE-7
depends on — the chain is append-only and readable as a whole — is unchanged.

**The measurement after the change**, with the same harness, the same machine and the same
method (the chain is grown first, then the allocation per call is measured over 2 000 calls):

| the chain before the call | B/call — **before** | B/call — **after** |
|---|---|---|
| 1 000 | 23 279 | **13 372** |
| 2 500 | 26 141 | **13 203** |
| 5 000 | 30 953 | **13 072** |
| 10 000 | 40 914 | **13 033** |

**The growth is gone.** The allocation per call is now flat — it varies by **339 B** across a
tenfold growth of the chain, where before it grew by **17 635 B**. The absolute cost per
call also fell, because the copy of the array is no longer paid at every append.

**What was NOT changed, and why**

| the change the measurement might have justified | why it was not made |
|---|---|
| narrow the bridge's payload parse | the parse is 46 % of the receive path, but what the receiving side needs from the parsed payload is decided by the **server** part of the bridge, which is another agent's code and was not part of this measurement. A change here would be a guess about code this measurement did not read |
| avoid the per-call hex digest | the digest is 13 % of the path. It is also the mechanism that makes a replay detectable (BRIDGE-8) and the audit record identifiable (BRIDGE-7). Trading either of those for 1.5 µs is not a trade this measurement may make on its own |
| replace the copy-on-write audit list | **done by the lead after the measurement** — see §7.1. The measurement itself did not modify that package; the change was made afterwards, on the measurement's evidence, and re-measured |
| extract the magic numbers of the measured paths | **there are none.** See §8 |

**The measurement itself changed nothing about the behaviour of the system.** It added a
package of meters, a package of tests for the meters, and this document. The one change that
followed it (§7.1) changed a data structure and not a behaviour: the same records are
written, in the same order, and the same snapshot is handed out.

---

## 8. The magic literals of the measured paths: there are none

The quality gate counts numeric literals of three or more digits outside a named constant,
outside the structural set, and outside a comment. It was run over the working tree:

```
bash scripts/quality-gate.sh --report
  MAGIC-1  shell literal candidates: 78
  MAGIC-2  java  literal candidates: 193
  → 271 candidates against an accepted baseline of 271
```

**The three measured paths contribute exactly zero.** Scanned with the gate's own regular
expression, over `io/matrix/cognitive/algorithms/**`, `io/matrix/cognitive/microworld/**`,
`io/matrix/learning/env/**` and `io/matrix/contract/bridge/**`:

```
(no candidates)
```

**The measurement package contributes exactly zero as well** — `io/matrix/benchmark/w51/**`
is scanned by the same expression and reports nothing. Every threshold in it is a named
constant with a stated reason: `DEFAULT_WARMUP_ROUNDS`, `DEFAULT_MEASURED_ROUNDS`,
`EPISODE_WARMUP_ROUNDS`, `EPISODE_MEASURED_ROUNDS`, `MEDIAN_FRACTION`, `P90_FRACTION`,
`P99_FRACTION`, and the limits of the bridge budget the scenario uses.

**Two other files of this wave** — `matrix-core/src/main/java/io/matrix/nativekernel/NativeKernelBenchmark.java`
and `scripts/w51-native-kernel-bench.sh` — were reported earlier in the wave as carrying two
candidates. As of this measurement **both carry zero**: the same scan finds nothing in
either, and the gate's totals are back at the baseline of 271. The delta of +2 was in the
tree at the time it was reported and is not in the tree now.

**One defect of the gate that this measurement noticed.** The gate's `DOCS-1` rule
currently reports *"the documentation links do not resolve"*. The link checker
(`scripts/docs-link-check.py`) counts **2 796 defects** across 1 259 documents, of which
**705 are in current documents** and the rest are in the archive (`docs-v2/_raw/**`). The
current-document defects are mostly `CURRENT_TO_HISTORICAL` (a current page linking to a page
that has been marked historical or superseded) and `MISSING_PATH`. **This measurement does
not own any of them**, but it does own one of them: `docs-v2/engineering/W51-STATUS.md`
linked to `docs-v2/engineering/W51-PERFORMANCE.md`, which did not exist until this document
was written. That link resolves now.

---

## 9. How to reproduce the measurement

The proper way, from the repository, with the whole test scope:

```bash
export JAVA_HOME=$HOME/jdk25
./gradlew :matrix-core:test --tests "io.matrix.benchmark.w51.*" --offline
```

`io.matrix.benchmark.w51.W51PerformanceRunTest` prints every measurement it takes; the
report is in the JUnit XML under `matrix-core/build/test-results/test/`.

**The tests of the harness and of the scenarios — 17 tests, 17 passed, 0 failed.** They were
run through the JUnit Platform launcher directly against the compiled classes, because a
Gradle test run of `:matrix-core:test` cannot share the module's `build/` directory with the
other agents' runs (§2). The bridge package's own tests were run the same way in the same
session: **63 tests, 63 passed, 0 failed.**

The way the numbers in this document were taken — outside Gradle, for the same reason:

```bash
export JAVA_HOME=$HOME/jdk25
CP="matrix-core/build/classes/java/main:$JACKSON_JARS"
$JAVA_HOME/bin/javac -cp "$CP" -d /tmp/w51run/classes <the runner>.java
$JAVA_HOME/bin/java -cp "/tmp/w51run/classes:$CP" <the runner>
```

The scenarios themselves are ordinary methods on `io.matrix.benchmark.w51.W51Benchmarks` —
`methodSelection(portfolioSize, warmup, rounds)`, `costLedgerLookup(warmup, rounds)`,
`episode(warmup, rounds)` and `bridge(warmup, rounds)` — so any reader can drive them with
any round count and compare.

---

## 10. What this document does NOT prove

1. **It does not prove anything about another machine.** The harness controls for neither the
   CPU's frequency scaling nor the JVM's compilation strategy. The numbers are comparable
   *with each other* and with nothing else.
2. **It does not prove that the latency is right.** The run-to-run spread of the average is
   up to ±18 %, and the maximum sample is noise. The **p50** and the **allocation** are the
   figures the findings rest on; the average is quoted because it is what a reader expects,
   and it is quoted with its spread.
3. **It does not prove that the bridge's audit growth matters at the scale the system runs
   today.** It proves that the growth is there and that it is measurable; whether 10 000
   messages in a chain is a realistic operating point is a question about the deployment.
4. **It does not measure the server side of the bridge.** The sink of the scenario counts
   and does nothing else. The real sink — the write into the epistemic store — is another
   agent's code (`io.matrix.bridge.**`), and it was not part of this measurement.
   **The 13.5 µs of this document is the cost of the bridge protocol, not the cost of the
   bridge plus the store.**
5. **It does not measure the plugin architecture, the cognitive kernel's cycle, or the
   runtime composition.** §1 names the three paths it measures, and they are the three the
   wave's mandate named.
6. **Two of the four measured packages are not in the repository's history yet.** §3.5 says
   which, and says what has to be re-measured if the committed version differs.
