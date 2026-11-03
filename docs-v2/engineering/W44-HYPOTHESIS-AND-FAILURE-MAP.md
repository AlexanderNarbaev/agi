# W44 — Hypothesis and failure map

**Audit:** RECON-W44.0 · 2026-11-02
**Purpose:** to keep the negative results and the open hypotheses visible, so that a
future wave does not repeat a failed attempt or re-derive a known contradiction.

---

## 1. The hypothesis registries

| registry | entries | state |
|---|---|---|
| `docs-v2/research/HYPOTHESES-NEW.md` | **46** (H-051…H-128) | each carries a source and a status |
| `docs-v2/research/HYPOTHESES.md` | 0 matched by the audit's pattern | it uses a different format; it is the older registry |
| `docs-v2/research/MATRIX-CROSS-DISCIPLINARY-RESEARCH.md` | 16 hypotheses from the W44 waves (H-113…H-128) | each tied to a primary source |

**The status vocabulary is not uniform.** `HYPOTHESES-NEW.md` uses
ПОДТВЕРЖДЕНА / ЧАСТИЧНО / ОТКРЫТА / НЕ ПОДТВЕРЖДЕНА; the cross-disciplinary document
uses "ОТКРЫТА" plus English phrases; `HYPOTHESES.md` uses its own. The audit did not
normalise them — a historical report is not to be rewritten — but **a machine-readable
status column would be the single highest-value addition to the registry**.

---

## 2. The open hypotheses that bear on the next integration

These are the ones the audit's evidence touches directly.

| H | the claim | the audit's evidence | status |
|---|---|---|---|
| **H-113** | agreement with a reference on 5 cases does not certify general agreement (rule of three: 5/5 is compatible with a true rate of ≈0.40) | the W44 `ProcedureValidator` requires exactly that | **OPEN** — and the audit adds that the promotion also stores no *regime* (H-126) |
| **H-114** | the accuracy estimate must be shrunk by its denominator | the portfolio's `accuracyOf` uses the raw value; the denominator is printed, not scored | **OPEN** |
| **H-115** | a drive must have credit assignment to change behaviour | the kernel's drives carry a `source`, but not a link to a completed episode | **PARTIAL** |
| **H-116** | a promotion must be reversible | `ProcedureStore.invalidate` exists; nothing calls it on a live disagreement | **PARTIAL** |
| **H-117** | the declared worst case must enter admission | `ComputeBudget.maxSearchNodes` bounds the search; the declared worst case does not gate admission | **OPEN** |
| **H-118** | the number of live hypotheses must be bounded | the space is 16 by construction, with no general cap | **OPEN** |
| **H-119** | a hypothesis set must be compared with a random-hypothesis baseline | no baseline exists | **OPEN** |
| **H-120** | "sleep" consolidation is not unconditionally good | W44 has no offline phase | **OPEN** |
| **H-121** | a refusal must carry a calibrated risk level | `AlgorithmDecision.Status.REFUSED` names the precondition only | **OPEN** |
| **H-122** | "enumerate all 16 Boolean functions" is not a neutral start | the audit confirms the space is Boolean-complete over two variables — a declared assumption, not a discovered neutrality | **OPEN** |
| **H-123** | covariation is free evidence; a confounded intervention must be distinguishable | no observational channel, no `CONFOUNDED` flag | **OPEN** |
| **H-124** | an error must be attributed before it may revise the model | `ErrorSignal` carries a disposition; the perceptual-noise floor is not measured | **PARTIAL** |
| **H-125** | the experiment chooser needs a locality audit | no metric exists | **OPEN** |
| **H-126** | a promotion must record its regime | the procedure stores a domain and a reference, not a regime | **OPEN** |
| **H-127** | offline consolidation must generalise | no offline phase | **OPEN** |
| **H-128** | a published accuracy needs a second, independently seeded run | one run only | **OPEN** |

**Thirteen of sixteen are open.** That is the honest state of the research line, and it is
the reason the next wave should measure rather than add.

---

## 3. The failures the audit found in the repository's own claims

| claim | where | what the evidence says |
|---|---|---|
| "the gateway serves the brain" | `matrix-api-gateway/build.gradle` | true, but only through the hand-written `MinimalHttpServer`; the JAX-RS facade is deliberately unmounted and would call `StubBrainCycle` |
| "HDC memory" | `HdcRetrievalStage` | the serving path scores **token overlap**; the hypervector machine (`HdcBrain`) is a different component with its own callers |
| "text encoder" | `io.matrix.perception.TextEncoder` | a **SHA-256 digest** as bits — a fingerprint, not a representation |
| "GPU acceleration" | `io.matrix.api.OnnxRuntimeAdapter` | the bundled CUDA provider fails to load (`undefined symbol: cudaLibraryGetKernel, version libcudart.so.12`) and the provider list is hard-coded to `{"CPUExecutionProvider"}` |
| "GPU task executor" | `io.matrix.federation.gpu.GpuTaskExecutor` | it echoes its input |
| "consensus" | `formal/spec-implementation-map.yml` | the registry says `NO_PRODUCTION_CALLER` and the bytecode agrees: the only callers are `SystemDemo`, the benchmark and the inactive `MultiAgentLoop` |
| "one serving conductor" | the ADR | five cycles coexist; none delegates to another |
| "the goal manager" | the earlier recon | **corrected**: it is served — `KnowledgeWorkbench` calls six of its methods |

---

## 4. The pre-existing failures, with executed evidence

The audit ran the whole matrix-core suite (see `RECON-W44-ARCHAEOLOGY.md` §1.1bis for the
disclosure) and **every failing class needs a GraalVM native image that this repository
does not build**:

| class | failures | what it asserts |
|---|---|---|
| `io.matrix.nativebin.NativeBinaryCLITest` | 8 | the presence and the behaviour of `build/native/nativeCompile/matrix-core` |
| `io.matrix.nativebin.NativeBinaryStartupBenchmarkTest` | 3 | that the native binary starts in under 200 ms |
| `io.matrix.nativebin.NativeBinaryBackgroundServiceTest` | 3 | the native binary's background service |
| `io.matrix.benchmark.NativeBinaryLaunchBenchmarkTest` | 3 | the native launch benchmark |
| `io.matrix.nativebin.NativeBinaryPerformanceBenchmarkTest` | 2 | the native performance benchmark |
| `io.matrix.nativebin.NativeBinaryFullIntegrationTest` | 2 | the native binary end to end |
| `io.matrix.nativebin.NativeBinaryDockerTest` | 1 | the native Docker image |
| `io.matrix.benchmark.JVMvsNativeBenchmarkTest` | 1 | the JVM/native comparison |
| `io.matrix.consciousness.Exp228ComplianceTest` | 1 | the EXP-228 compliance rule |
| **total** | **24** | of 8817 executions |

**The measured state of the repository's test suite:** 1320 test classes executed across
all 14 modules, **9816 test executions, 24 failures, 0 errors, 26 skipped**, and 66 test
classes with no executed evidence. The 24 failures are the 9 classes above; everything
else is green.

**The consequence for planning.** The suite is green apart from the native-image tests. A
plan that says "run the full suite and see it green" is wrong on this host: it needs a
native build first, and the native build is not part of the ordinary `test` task.

## 5. The failures the audit recorded as open risks

| risk | severity | where |
|---|---|---|
| the workspace reload deletes `contradiction`, `evidence`, `derivation` and `revision` | **HIGH** | `KnowledgeStore.deleteWorkspace`, `KnowledgeStore.java:700-726` |
| the `Impulse` producer does not exist: the router merges nothing | medium | `io.matrix.consciousness.AttentionRouter.merge` |
| the chemical modulator coefficients have no source | medium | `io.matrix.federation.liquid.biochemistry` |
| the `SleepConsolidator` anneal guard is sound only for edges that entered `CONNECTED` | medium | `SleepConsolidator.java:251-268` (its own javadoc) |
| `FnlGate`/`FnlGateV2`, `ActionGate`/`ActionGatePolicy`, `ConjugateBudgeter`/`Multi` are duplicated with the older one inactive | low | — |
| two classes named `ArousalDynamics` in two packages, both served | low | `io.matrix.consciousness`, `io.matrix.reasoning` |
| the gateway's REST facade is a stub and is not mounted | low | `io.matrix.api.resource.AnalyzeResource` |

---

## 6. What has already been tried and disproved — the short list

1. **Making the connectome the integration substrate.** 159 tests, a closed island, a
   documented guard defect, no caller.
2. **The biochemical modulator as a real mechanism.** Hand-picked coefficients, no
   source, an island root.
3. **A second serving conductor.** `BrainLoopService` has 25 callers and does not
   delegate to `TrueMindCycle`.
4. **The JAX-RS gateway facade.** Deliberately unmounted; a stub behind it.
5. **GPU inference.** The provider cannot load; the executor echoes.
6. **A single memory taxonomy.** Five exist.
7. **A single goal model.** Four exist.
