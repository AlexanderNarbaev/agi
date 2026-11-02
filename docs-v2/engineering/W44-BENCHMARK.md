# W44 — Benchmark

**Directive:** W44 Cognitive Kernel Foundation · 2026-11-02
**Repository:** `AlexanderNarbaev/agi` · branch `develop` · baseline `99d35f25`
**Host:** AMD Ryzen 9 9955HX, 32 threads, 59 GiB RAM, NVIDIA GeForce RTX 5070 Ti Laptop GPU
**JVM:** OpenJDK 25.0.4.1 · **Build:** `JAVA_HOME=/home/alexandr-narbaev/jdk25 ./gradlew …`

Every number below was produced by a run in this wave. Where a number comes from a test that
PRINTS it, the test is named. Where a number is a constant, it is a constant and is not
presented as a measurement.

---

## 1. The test scope, and what it is not

| Scope | Tests | Failures | Skipped |
|---|---|---|---|
| `io.matrix.cognitive.*` (the whole W44 addition) | **256** | **0** | 0 |
| — `io.matrix.cognitive.algorithms.*` (W44.2) | 104 | 0 | 0 |
| — `io.matrix.cognitive.neuromodulation.*` (W44.3) | 59 | 0 | 0 |
| — `io.matrix.cognitive.microworld.*` (W44.4) | 50 | 0 | 0 |
| — `io.matrix.cognitive.CognitiveKernelTest` (W44.1/W44.5) | 9 | 0 | 0 |
| — `io.matrix.cognitive.CognitiveSolverTest` (W44.2 reachability) | 5 | 0 | 0 |
| `matrix-brain-runtime:test` (the whole module, unchanged by this wave) | 914 | 0 | 2 |
| `matrix-core:test` — the W43 targeted scope: `knowledge`, `memory`, `brain`, `api`, `rag` | 1518 | 0 | 0 |

**The denominator that matters:** `matrix-core` holds **8284 `@Test` methods across 1179 test
files** (the build prints this in `generateBuildInfo`). The 1518 above are the W43 *targeted
scope*, not the module. **The full `matrix-core` suite was NOT run in this wave** — the owner
rule D-017 reserves it, and it is an OOM risk. The 256 W44 tests were run against the module.

**Coverage of the new packages** (measured with `:matrix-core:jacocoTestReport` over the
filtered run's exec file, so it is the package in isolation):

| Package | METHOD | LINE | BRANCH |
|---|---|---|---|
| `io/matrix/cognitive/algorithms` | **171/171 = 100.0 %** | 808/860 = 94.0 % | 369/510 = 72.4 % |
| `io/matrix/cognitive/neuromodulation` | **35/35 = 100.0 %** | 191/198 = 96.5 % | 83/107 |
| `io/matrix/cognitive/microworld` | **91/95 = 95.8 %** | 480/521 = 92.1 % | 272/344 = 79.1 % |

The 82 % METHOD gate in `matrix-core/build.gradle:271-283` is a **BUNDLE** rule and
`io/matrix/cognitive/**` is NOT in the exclusion list, so these packages count towards it.
They raise it.

---

## 2. W44.2 — the algorithm portfolio, measured

Source: `PortfolioBenchmarkTest`, which PRINTS its numbers and asserts the comparisons.

### 2.1 A scan against a prebuilt index

```
linear scan of 64 queries       p50=  45506 ns   p95=  60995 ns   mean=  48755.0 ns
building the trie               p50= 103054 ns   p95= 158870 ns   mean= 121138.5 ns
trie lookup of 64 queries       p50=  11001 ns   p95=  41078 ns   mean=  21832.2 ns
building the Bloom filter       p50=  56406 ns   p95=  70513 ns   mean=  53336.3 ns
Bloom lookup of 64 queries      p50=  17262 ns   p95=  33043 ns   mean=  21226.1 ns

the scan costs 761.8 ns/query; the trie costs 341.1 ns/query after a build of 121138.5 ns,
so it repays the build after 288 queries (this corpus asks 64)
end to end over 64 queries: the scan costs 48755.0 ns and the trie 142970.7 ns
```

**Read honestly, this is a case where the popular answer is wrong at this scale.** The index is
2.2× cheaper per query, but its build costs 121 µs, so it repays itself only after **288**
queries, and this corpus asks **64**. End to end the **scan is 2.9× cheaper** (48.8 µs vs
143.0 µs).

The selector nonetheless chose `collection.membership.trie-index`, because the cost ledger holds
the **per-call** cost and the `INDEX_PREBUILT` precondition records that the caller has already
paid the build. **The decision to assert `INDEX_PREBUILT` is the caller's decision, and at 64
queries it is the wrong one.** This is a measured limitation, not a hidden one: the benchmark
prints the break-even and the end-to-end comparison next to the decision.

### 2.2 Two exact shortest-path methods on the same graph (120 vertices, 360 edges, seed 42)

```
Dijkstra on 120 vertices        p50= 158299 ns   p95= 201651 ns   mean= 167689.3 ns
Bellman-Ford on 120 vertices    p50= 245283 ns   p95= 355591 ns   mean= 307483.3 ns
```

Both are exact, so the quality is identical (1.0 over 12 validation cases) and the **measured
cost alone decides**: Dijkstra is **1.83× cheaper**. The two methods were asserted to produce
identical distances at every one of the 120 vertices — the cost decides only because the
quality is equal.

### 2.3 A bounded search against the exact search

```
the exact search                     p50= 156956 ns  p95=  207702 ns  mean= 174769.2 ns
the bounded search, generous budget  p50= 277914 ns  p95= 2373537 ns  mean= 581630.2 ns
the bounded search, node budget      p50=  22212 ns  p95=   56387 ns  mean=  31549.2 ns
```

The bounded search that **finishes** is slower than the exact one — it is a separate
implementation. The bounded search that **gives up under a node budget** is **5.5× faster** and
returns a result explicitly marked `complete=false`. It is admitted **only** when the request
carries `BOUNDED_RESULT_ACCEPTED`; without that fact it is not a candidate at all and Dijkstra
answers. Outside its budget it throws `ResourceLimitException`, whose partial result has
`complete=false`.

### 2.4 What the benchmark asserts, not just prints

- the **cheaper** method is selected when the **quality is equal** — asserted for scan-vs-trie
  and Dijkstra-vs-Bellman-Ford, decided from the measurements themselves;
- the selected method's **answer equals the reference answer** — the trie's `present` list
  equals the scan's; the bounded search's distances equal Dijkstra's at all 120 vertices;
- a method whose **precondition is violated is never selected** — asserted for the
  negative-weight graph, where the decision record shows Dijkstra blocked with the missing
  precondition named;
- the **bounded** search outside its budget **throws** rather than returning a truncated answer.

---

## 3. W44.3 — the regulatory layer, measured

Source: `RegulationEffectMeasurementTest` and `RegulationControllerTest`.

### 3.1 The load drive changes the cost weight and nothing else

The same scenario, run twice with `SUSTAINED_LOAD` at 0.0 and at 1.0, with every other drive
byte-identical:

| knob | load = 0.0 | load = 1.0 |
|---|---|---|
| `costWeightMultiplier` | **1.000000** | **1.500000** (Δ = 0.500000) |
| `evidenceRequestThreshold` | 0.925000 | 0.925000 |
| `admitNonCriticalTask` | true | true |
| `taskPriorityMultiplier` | 1.100000 | 1.100000 |

The difference is attributable: the two runs agree on the other three knobs exactly.

### 3.2 The load → cost curve

Ten samples, one step each, no decay:

| load | 0.1 | 0.2 | 0.3 | 0.4 | 0.5 | **0.6** | 0.7 | 0.8 | 0.9 | 1.0 |
|---|---|---|---|---|---|---|---|---|---|---|
| cost weight | 1.000000 | 1.000000 | 1.000000 | 1.000000 | 1.000000 | **1.100000** | 1.200000 | 1.300000 | 1.400000 | **1.500000** |

The preference starts to bite between the fifth and the sixth sample (threshold 0.5, gain 1.0).

### 3.3 The drives after a mixed scenario

Six events, one step:

| dimension | level | effect it may bias |
|---|---|---|
| ATTENTION | 0.0900 | NONE |
| UNCERTAINTY | 0.1500 | EVIDENCE_REQUEST |
| REWARD_PREDICTION_ERROR | 0.1750 | NONE |
| NOVELTY | 0.2000 | TASK_PRIORITY |
| SUSTAINED_LOAD | 0.2000 | ALGORITHM_COST_PREFERENCE |
| VIGILANCE | 0.2500 | NONE |
| CURIOSITY | 0.1500 | TASK_PRIORITY |
| CONSOLIDATION_DEMAND | 0.1050 | TASK_ADMISSION |

With `UNCERTAINTY` at 0.1500 the evidence-request threshold is **0.925000**; without the
uncertainty it is **1.000000**, which means "never ask for anything the model did not already
need".

### 3.4 `MODEL_UNSETTLED` touches exactly one dimension

A batch containing only `MODEL_UNSETTLED` raises `UNCERTAINTY` from 0.40 to **0.50** and leaves
**every other dimension at exactly its previous value**. The outstanding error, the load, the
novelty and the consolidation demand are untouched, and the reward relief ignores it — it is not
a reward.

### 3.5 What is NOT measured here

The **magnitudes**. Every constant in the layer is a named CONVENTION, chosen so that the
DIRECTION of each influence is visible and testable. No constant is calibrated against an
observation, and this document does not claim that any is. The wave's claim is about direction,
boundedness and attribution — not about size.

---

## 4. W44.4 — the cognitive micro-world, measured

Source: `MicroWorldSessionTest`. The hypothesis space starts UNBIASED — all sixteen Boolean
functions of the two variables — so the survivor counts below are a consequence of the
observations and not of a chosen fixture.

### 4.1 The survivors

| step | observation | lamp | survivors | rejected |
|---|---|---|---|---|
| 0 | — | — | **16** | 0 |
| 1 | power=0,switch=1 | false | **8** | 8 |
| 2 | power=1,switch=1 | true | **4** | 12 |
| 3 | power=1,switch=0 | false | **2** | 14 |
| 4 | power=0,switch=0 | false | **1** | 15 |

The single survivor is `h-0001` — the conjunction — with the statement "lamp is true exactly
when power=1,switch=1".

### 4.2 The information gain

The metric is the **fraction of the surviving hypotheses an assignment eliminates**. With a
two-valued outcome the best possible split leaves half the hypotheses alive whichever way the
outcome falls, so **the maximum attainable gain is 0.5, not 1.0**. It is a comparable
fraction, not a probability.

| state | assignment | gain |
|---|---|---|
| after 2 observations (4 survivors) | power=0,switch=0 | **0.500** |
| after 2 observations | power=1,switch=0 | **0.500** |
| after 2 observations | an already-observed assignment | 0.000 |
| after 3 observations (2 survivors) | power=0,switch=0 | **0.500** |
| after 3 observations | power=1,switch=0 | 0.000 (the two survivors agree there) |
| settled (1 survivor) | any | **0.000** |

The proposal is deterministic: ties are broken by the smallest assignment key, so the kernel
probes `power=0,switch=0` first and `power=1,switch=0` only afterwards.

### 4.3 The ambiguity is visible

After three observations, `predict(power=1,switch=0)` returns `value=false`, `unanimous=false`
and the disagreeing hypotheses `h-0011` and `h-1011`. The prediction is recorded with its
ambiguity rather than resolved silently.

### 4.4 The adversarial case

An adversarial observation (predict `false`, observe `true`) produces **one** `PredictionError`
naming the responsible hypothesis `h-0001`; the survivors stay `[h-0001, h-1001]` — **the
conjunction is still standing** — and `hypothesesNeedingReview()` is non-empty. The
observation is recorded but NOT applied within the cycle.

---

## 5. The kernel, end to end

Source: `CognitiveKernelTest`. The kernel is driven exactly as the HTTP endpoint drives it.

| Scenario | What was observed |
|---|---|
| A malformed signal | refused; the trace names the refusal; **no prediction is recorded** |
| A readable assignment | a trace with PERCEIVE, RESTORE, PREDICT, SELECT, ANSWER present; the prediction names its basis (`surviving-hypotheses`) |
| An observation | becomes a **case** with its conditions; the case **survives a restart** over the same file, with the same id |
| The next cycle | the session was rebuilt from the recorded cases ("rebuilt from 2 recorded case(s)") |
| A settled model | a procedure is promoted with its measured agreement (1.0 over ≥ 2 held-out cases), its applicability domain (`MODEL_SETTLED`) and its reference fallback (`micro-world-hypothesis-enumeration`) |
| A contradicting observation | an error with provenance; the falsified prediction is still readable; a **bounded** task is generated (budget, stop condition, completion condition, allow-list of one action) |
| The regulation | with a neutral regulator no confirmation is requested; with the uncertainty raised one is, and the difference is attributable to that one drive |

---

## 6. The portfolio, reached from a request

Source: `CognitiveSolverTest`, over `POST /v1/cognitive/solve`.

| Request | Outcome |
|---|---|
| a graph with non-negative weights | `graph.shortest-path.dijkstra` selected; the distance is exact; the decision names what it rejected |
| a graph with a **negative** weight | Dijkstra is **not** a candidate — the decision record shows it blocked with the missing precondition — and Bellman-Ford answers |
| a graph with a **negative cycle** | the method refuses; the answer says **"No shortest path exists"**; a finite distance is never reported |
| `allowBounded` | the bounded search may be chosen; if it did not finish, the answer is labelled **NOT proven** and the notes say "bounded, not proven" |
| any call | the measured cost lands in the shared ledger |

---

## 7. The failures and the limitations, listed

1. **The selector does not amortise a preprocessing cost.** §2.1 measures the break-even (288
   queries) and shows the scan is cheaper end to end at 64 queries, and the selector still picks
   the index. The precondition `INDEX_PREBUILT` moves the decision to the caller, which is the
   right place for it — but the caller has to know.
2. **The portfolio's own methods are reachable from ONE request family.** The portfolio has
   four families; the kernel serves the micro-world family and the HTTP surface serves the
   shortest-path family. The arithmetic and entailment families are reachable through the
   portfolio's own API and its tests, not through a user request.
3. **The declared-cost fallback is a convention.** When a method has never been measured the
   selector falls back to a declared complexity, and the decision says so per candidate
   (`MEASURED` vs `DECLARED_COMPLEXITY`). The growth table is an ORDERING placeholder.
4. **The neuromodulatory constants are conventions**, not calibrated values. §3 measures the
   direction and the attribution, not the magnitude.
5. **The micro-world's information gain cannot exceed 0.5** because the outcome is two-valued.
   A micro-world with a continuous outcome would need a different metric, and the current one
   would understate the achievable gain.
6. **The reference method is O(2^v · m).** At 16 candidates it is cheap; the research agenda's
   Q6 asks what happens at 4096.
7. **The full `matrix-core` suite was not run.** The 1518 tests above are the W43 targeted
   scope. The module's total is 8284 tests, and a full run is reserved by owner rule D-017.
8. **No GPU number is published.** `OrtEnvironment.getAvailableProviders()` is never called in
   this repository and `OnnxRuntimeAdapter.activeProviders()` is hard-coded to
   `{"CPUExecutionProvider"}`. The host HAS a CUDA-capable GPU (`nvidia-smi` reports the RTX
   5070 Ti), and that is the whole of the claim: **the hardware is present and the software
   path is not observable.**
