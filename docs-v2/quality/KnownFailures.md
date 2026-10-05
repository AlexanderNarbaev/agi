# KnownFailures.md — the honest failure ledger

**RECON-W30.** Every test failure in this repository, accounted for.

No test is deleted, skipped, `@Disabled`-ed or excluded to make a build green. If a test
fails, it is listed here with a root cause, an owner and a disposition. A failure that is
not in this file and not passing is a regression, and the ledger is the thing that makes
that claim checkable.

## How this list was produced

```bash
./gradlew cleanTest test --continue --no-daemon --console=plain
# then aggregate every module's JUnit XML:
#   */build/test-results/test/TEST-*.xml  +  pilots/*/build/test-results/test/TEST-*.xml
```

Counts are `<testcase>` invocations from the XML, not Gradle's console summary, and not a
filter. A `--tests`-filtered run overwrites the full run's results for that module, which
has twice produced a wrong count in this project; see `MIND-VALIDATION-CHECKLIST-v3.md`
Z7.

## Current state

| | Count |
|---|---|
| Total invocations | 8879 |
| Failures | **71–72, varying per run** (see below) |
| Skipped | 26 |
| Modules with zero failures | 13 of 14 |
| Failures outside `matrix-core` | **0** |

Every failure is in `matrix-core`, in `io.matrix.consciousness`, `io.matrix.research`,
`io.matrix.model` and `io.matrix.federation`. **Not one line of `matrix-core` has been
modified by RECON-W28 or RECON-W30** — verified with
`git diff <baseline> HEAD -- matrix-core/` returning empty. These are inherited debt, not
damage.

### The count is not a single number, and here is the evidence

Two full runs of the same tree on the same JVM, hours apart:

| Run | Total failures | KF-1 | KF-2 | KF-3 | KF-4 | KF-5 |
|---|---|---|---|---|---|---|
| A | **71** | 27 | 31 | 11 | 2 | 0 |
| B | **72** | 27 | **32** | 11 | 2 | 0 |

The delta is **entirely in KF-2** — one additional jqwik property assertion. Nothing else
moved. KF-1, KF-3 and KF-4 are byte-identical across both runs.

This is jqwik's non-deterministic input generation: the properties draw inputs from a
seed, so a property that holds for most inputs still fails for whichever one a given run
happens to draw. The consequence for this document is that **the total is a range, not a
number**, and any document quoting an exact figure — including earlier ones in this
repository that say 72, and the interim version of this file that said 71 — is quoting a
snapshot of a non-deterministic quantity.

**A speculation I had to withdraw.** When the ledger first showed 71 and earlier runs
showed 72, I attributed the variation to the intermittent federation registry test
(KF-5) and wrote that KF-5 "counts 1 in some runs and 0 in others". The two-run
comparison above does not support that: KF-5 was **0 in both runs**, and the extra
failure in run B is a KF-2 property. The intermittent family is KF-2, not KF-5, and the
claim in the previous draft of this file was wrong.

## Failure families

Grouped by root cause, not by test class, because a root cause is what can be assigned and
fixed. Counts are from the most recent run.

### KF-1 — BitNet model cache absent (27 failures, 38% of all failures)

| | |
|---|---|
| **Exception** | `java.nio.file.NoSuchFileException` |
| **Classes** | `BitNetModelLoadTest` (6), `BitNetPrefillGenerationTest` (4), `BitNetSampledGenerationTest` (4), `BitNetKvCacheGenerationTest` (4), `BitNetRealForwardTest` (3), `BitNetAutoregressiveGenerationTest` (2), +4 more |
| **Message** | `/tmp/hf_cache/models--microsoft--bitnet-b1.58-2B-4T/snapshots/... not found` |
| **Root cause** | The tests require a pre-downloaded HuggingFace model snapshot under `/tmp/hf_cache`. The file is not present on this machine. This is an **environment dependency, not a code defect**: the code under test was never entered. |
| **Owner** | whoever provisions test fixtures |
| **Disposition** | **ACCEPTED-AS-RESEARCH-DEBT**, with a caveat below |
| **Why not fixed here** | The model is ~1.2 GB. Downloading it is a network operation, needs a licence check, and lands in `/tmp`, which this project has already moved a 20 GB scratch tree out of. It is also not needed by any capability this campaign measures. |

**The caveat that matters:** 27 of 71 failures — more than a third — are one missing
directory. That inflates the appearance of a broken build and deflates the appearance of
the code. It also means the **BitNet inference path has effectively zero test coverage on
this machine**: the 27 tests that would exercise it do not run. That is a real gap in the
evidence, not just a red number, and it should be read as "unverified" rather than
"known-good".

**If you want these green:** provision the snapshot, then point the tests at it. That is a
fixture-provisioning task, not a code fix, and it belongs to whoever owns CI.

### KF-2 — jqwik property assertions failing (31–32 failures, varies per run)

| | |
|---|---|
| **Exception** | `AssertionError` (21), `AssertionFailedError` (12) |
| **Classes** | `PhiMaxCalculatorPropertyTest`, `CausalEmergencePropertyTest`, `MemristorSwitchPropertyTest`, `InferentialDistancePropertyTest`, `ProfileEmbedding2DPropertyTest`, `RegimeTrajectoryAnalyzerTest`, `EntropyDecompositionPropertyTest`, `KolmogorovComplexity*`, +7 more |
| **Representative** | `propertyPhiMaxNonNegative`: actual `-1.0`, expected `>= 0.0`. `propertyIdenticalKLIsZero`: a distribution over identical inputs is not returning zero. |
| **Root cause** | Mixed, and **not yet root-caused per property**. The visible pattern is that the properties assert physical or mathematical invariants (non-negativity, identity, boundedness) that the implementations do not actually guarantee. Two distinct sub-kinds: |
| | (a) genuine implementation gaps — e.g. Phi returning `-1.0` where a non-negative quantity is asserted; |
| | (b) over-strict properties — e.g. asserting a numerical result is exactly `0.0` where floating-point accumulation makes that unreasonable. |
| **Owner** | research owner for `io.matrix.consciousness` |
| **Disposition** | **ACCEPTED-AS-RESEARCH-DEBT** — the properties encode aspirations about consciousness measures that the code has not implemented, not regressions. |

**Honest caveat:** "the properties are over-strict" is a claim that is easy to make and
expensive to check, and it is exactly the claim that turns a red test green without
improving anything. Sub-kind (a) and (b) have **not** been separated. Until they are, this
family is "not root-caused", not "not a defect". Treating these as aspirational is a
decision to make with the research owner, not one to make by editing a threshold.

Two properties in this family were already investigated in RECON-W28 and found
unreachable by the code under test (`KolmogorovComplexity*` at `x=1` never enters the
changed branch; `+ Long.SIZE` producing `K=64.0` is unchanged from baseline). Those
specific cases are genuinely not regressions.

### KF-3 — jqwik generator producing invalid arguments (11 failures)

| | |
|---|---|
| **Exception** | `IllegalArgumentException` (6), `ArrayIndexOutOfBoundsException` (5) |
| **Classes** | `CognitiveGroupedQueryAttentionPropertyTest`, `InterAgentPhiPropertyTest`, `InferentialDistancePropertyTest`, `EntropyDecompositionPropertyTest`, `CognitivePhaseDetectorPropertyTest` |
| **Representative** | `propertyCompressionRatio`: `dim must be divisible by numQueryHeads` — the generator emits head counts that do not divide the dimension. `row length < N`. `Index -1 out of bounds for length`. |
| **Root cause** | **The test generators, not the production code.** The generators produce parameter combinations the API contract forbids: a head count that does not divide the dimension, a series shorter than the window it indexes into, a negative index. Production code is being handed inputs it correctly rejects. |
| **Owner** | research owner |
| **Disposition** | **FIX-IN-WAVE**, and the most tractable family here — these are test-side bugs with a clear correct answer. The generators need constraint annotations (jqwik `@Provide` with `@IntRange` and divisibility constraints). |

This family is the strongest candidate for near-term burn-down: unlike KF-2, fixing it
does not require deciding whether a research claim is true, only that the generator stops
producing contract-violating input.

### KF-4 — `ModelRegistryTest` default set drift (2 failures)

| | |
|---|---|
| **Exception** | `AssertionError` |
| **Message** | `Expecting Set12: ["topic-router"] to contain: ["sentiment-c..."]` |
| **Root cause** | A registry default set drifted from what the test expects: `topic-router` is registered where the test wants `sentiment-classifier`. Almost certainly a legitimate change to registry defaults that the test was not updated for. |
| **Owner** | gateway/model owner |
| **Disposition** | **FIX-IN-WAVE** — update the test to the current registry, or restore the default, after establishing which was intended. Not triaged far enough to say which. |

### KF-5 — federation registry (0 in both runs this wave; not reproduced)

| | |
|---|---|
| **Exception** | assertion failure inside a federation registry test |
| **Root cause** | **Not root-caused.** |
| **Owner** | federation owner |
| **Disposition** | **OPEN — not reproduced in RECON-W30.** |

An earlier full-suite snapshot (RECON-W28) counted one federation failure. It did **not**
reproduce across either run in RECON-W30, so it is recorded as unreproduced rather than
fixed. The live federation path was exercised end-to-end in RECON-W28 with the two-node
script passing 6/6, so whatever failed there was in a different area than the live-tested
one.

I previously wrote that this was the source of the 71-versus-72 variation. It was not —
see the correction above. A failure that appears once and then never again is the hardest
kind to act on, and the correct disposition is "unreproduced", not "fixed" and not
"intermittent".

## Disposition summary

| Family | Count | Disposition | Root-caused? |
|---|---|---|---|
| KF-1 BitNet model cache | 27 | accepted-as-research-debt | yes — environment |
| KF-2 property assertions | 31–32 | accepted-as-research-debt | **partly** — (a)/(b) not separated |
| KF-3 generator contract violations | 11 | fix-in-wave | yes — test-side |
| KF-4 registry default drift | 2 | fix-in-wave | partly |
| KF-5 federation registry | 0 | open — unreproduced | **no** |
| **Total** | **71–72** | | |

**A correction worth recording.** The first draft of this table claimed the families
summed to 75 against a live count of 71, and papered the 4-failure gap over with a
paragraph about classes contributing to two families. That explanation was plausible and
wrong: the real reason is that my first pass classified by *test class* while a class
holds several failing methods, so `InferentialDistancePropertyTest` was counted under
both KF-2 and KF-3 and its failures were double-booked. Rather than keep a tidy story,
the table is now a **disjoint partition computed from the XML**: each failing method is
classified once, first match wins, on the rule *contract-violating exception →
generator bug; otherwise assertion failure → property semantics*. That sums to 71
exactly.

The lesson generalises past this file: when a triage table does not reconcile with the
authoritative count, the table is wrong, and inventing a reconciling narrative is how a
ledger stops being a ledger. A real partition also changed two conclusions — KF-4 is 2
failures, not 3, and **KF-5 does not appear in this run at all** (the federation failure
is intermittent, see below).

## What would burn this down, in order of value per unit of effort

1. **KF-3** (11) — constrain the generators. Mechanical, unambiguous, no research decision.
2. **KF-4** (3) — decide what the registry default should be, then fix one side.
3. **KF-5** (1) — one investigation.
4. **KF-1** (27) — provision the model cache, or mark the tests as requiring a fixture
   with a clear skip reason. Note that skipping needs an owner decision, because this
   project's rules forbid skipping without one.
5. **KF-2** (33) — requires a research conversation about whether the asserted properties
   are the intended semantics. This is the expensive one and should not be rushed: it is
   the family where "fixing" the tests could destroy a real finding.

## Rules for this file

- A family leaves this file only by being fixed, or by an owner deciding to accept it in
  writing. Nobody may edit a threshold to make a row disappear.
- Every added row needs a root cause, an owner and a disposition. "Pre-existing" is not a
  root cause.
- If a number here disagrees with a live test run, the run wins and this file is wrong.


---

# RECON-W32.34 — full attribution of the research-core failures (operator decision D8)

**Produced by:** parsing all 1 101 JUnit XML result files of a full `matrix-core` run, then
grouping by root-cause family. **Owner:** unassigned — this section is the request for an
owner, and until one exists these failures are owned by nobody, which is stated rather than
glossed.

## Verified totals at commit `7e006c48`

```
matrix-core            8 111 tests   27 failures   54 skipped
matrix-brain-runtime     597 tests     0 failures
matrix-api-gateway       152 tests     0 failures
```

**28 failures were removed by D1, not by fixing anything.** They are now 32 **skips**, and
the correct description of them is *"environment-blocked, never executed"*. BitNet is
**untested**, not passing.

## The finding that mattered: 0 of these are caused by CUDA

The ONNX Runtime CUDA execution provider is built against CUDA 12
(`libcudart.so.12`) and this machine runs a CUDA 13.2 driver, so loading it fails with
`undefined symbol: cudaLibraryGetKernel`. That is real and it is loud on every run.

**It causes no test failure.** `OnnxRuntimeGpuTest` passes 6/6 while printing
`[CPUExecutionProvider]`, because the adapter catches the load failure and falls back to CPU.
Across all 1 101 XML files, no CUDA string appears inside a single `<failure>` element. The
campaign had suspected the GPU for weeks. It was never involved.

Separately, `Exp228ComplianceTest` mentions ONNX but is a **static source scan**, not a
runtime load, and has no causal relationship to the CUDA state.

## 27 failures, 8 root-cause families

| family | classes | n | cause | disposition |
|---|---|---|---|---|
| **F4 Kolmogorov 64-bit floor** | `KolmogorovComplexityTest`(2), `KolmogorovComplexityPropertyTest`, `KolmogorovComplexitySnapshotTest`, `W120LSystemPhiCorrelation` | **5** | `KolmogorovComplexity.java:52` adds `+ Long.SIZE` unconditionally, so the estimator has a hard floor of 64 bits for every input, constant or not. A constant trajectory scores the same as noise. | **OWNER REQUIRED — scientific, not a test bug.** Either the `+64` is wrong or the `< 20.0` thresholds are. The four tests cannot all be right. **Do not adjust thresholds until green**: that settles a live claim by editing its test. |
| **F5 causal emergence, unnormalised input** | `CausalEmergencePropertyTest`, `CausalEmergenceTest` | 2 | The test builds `uniform[i] = 1.0` for `n` entries — sum `n`, not `1`. The entropy and information terms then disagree. | **fix-in-next-wave.** Either normalise the fixture or make `CausalEmergence` normalise defensively. Decide which; the test comment and the implementation disagree about the contract. |
| **F9 regime / stability classifier** | `RegimeTrajectoryAnalyzerTest`(2), `ProfileStabilityMetricsTest` | 3 | The tests encode the hypothesis "alternating profile ⇒ OSCILLATORY"; `transitionRate` does not support it. | **accepted-as-research-debt.** This is an open research question, not a defect. The property is aspirational. |
| **F6 / F7 tolerance and exact equality** | `SeriesCorrelatorPropertyTest`, `MultivariateGaussianAnalyzerPropertyTest`, `CognitiveHeatmapTest`, `MemristorSwitchPropertyTest` | 5 | Floating-point overshoot and exact-equality assertions: `1.0000000000000002` asserted `<= 1.0`, `exp(-dt/tau)` asserted `isEqualTo(w)`. | **flaky-with-evidence.** Individually 1-ULP class. Needs an epsilon per assertion. |
| **F3 jqwik generator shape** | `CognitivePhaseDetectorPropertyTest` | 2 | `between(0, 16)` allows `n == 0`, then `seeds[0]` on a length-0 array. Firing probability ≈ 84%/run. | **fix-in-next-wave.** Change to `between(1, 16)`. Cheap, and it is the clearest flakiness contributor. |
| **F10 zero-norm / length convention** | *(resolved in W32.30)* | 0 | — | **CLOSED.** `l2Distance`/`cosineSimilarity` now return `NaN` for incomparable vectors instead of `0.0`, and the property generators share a dimension so the properties are not vacuous. Mutation-verified. |
| **F12 isolated single-cause defects** | `LSystemTest`, `QuantumEmulatorTest`, `PhiMaxCalculatorPropertyTest`, `VariationalFreeEnergyPropertyTest`, `InterAgentPhiPropertyTest`, `SleepConsolidationStudyTest`, `CognitiveGroupedQueryAttentionPropertyTest`, `CognitiveGenesisProfilePropertyTest`, `PhiComplexityFingerprintPropertyTest` | 9 | Individually distinct. `LSystemTest`'s own expected string is arithmetically wrong (`FXY`→`FFXY`→`FFFFXY`). `CognitiveGroupedQueryAttention` rejects a config its own generator produces. | **split.** `LSystemTest` and the GQA config are trivial corrections. The other seven need an owner decision because their production code was not read during attribution. |

## Why the total used to move (67, 68, 69, 70, 72, 73)

About eight property methods have firing probabilities in the **84–99%** band. Each can
independently flip either way per run while the *set of classes* stays fixed. That is the
mechanism, and it is now known rather than guessed at — but it is worth recording that for
weeks this looked like an unexplained mystery in a number nobody had attributed.

## The 54 skips, which are not failures and not invisible either

| cause | n | meaning |
|---|---|---|
| BitNet real-checkpoint guard (D1) | **32** | **environment-blocked, NEVER EXECUTED.** BitNet is untested. Option B of D1 — provisioning the 1.1 GB checkpoint — is the only thing that changes this. |
| pre-existing guards in sibling classes | 22 | long-standing, already justified in their own files |

A skip is a legitimate disposition **only when the reason is written down**, which is why
D1's guard carries its explanation in `BitNetRealModelFixture` rather than as a bare
`assumeTrue`.

## Standing rule this section establishes

`matrix-core` failing 27 tests is an **accepted, attributed, owned-pending** state. It is
not "research debt" in the sense of "nobody looked". Every one of the 27 has a family, a
cause and a named next step above. A *new* failure is still a regression, and the rule from
the top of this file still holds: anything failing that is not in this file is a regression.

## D2 recovery — what landed, and what genuinely needs a port

The six integration tests that existed only on stale branches were recovered and installed.
**Three compile and run. Three do not, and the reason is an intentional redesign, not a
breakage.**

| file | status | reason |
|---|---|---|
| `DistillationFactoryIntegrationTest` (11 tests) | **compiles, runs** | `PersistentHdcStore` API unchanged |
| `RealSleepSchedulerIntegrationTest` (10 tests) | **compiles, runs** | `RealSleepScheduler` API unchanged |
| `TrueDistillationFactoryIntegrationTest` (9 tests) | **compiles, runs** | `TrueDistillationFactory` API unchanged |
| `AutonomyIntegrationTest` (13 tests) | **needs a port** | `GoalTracker` was redesigned |
| `SleepAndConsolidationIntegrationTest` (12 tests) | **needs a port** | `ConsolidationCycle` moved to `io.matrix.memory`; `SleepScheduler` was renamed `RealSleepScheduler` |
| `GpuKernelEngineIntegrationTest` (6 tests) | **needs a port** | imports `io.matrix.federation.proto.GpuOperation` / `GpuTask`, which are not on the current classpath |

### The `GoalTracker` delta, in full, so the port is not rediscovered from scratch

The stale test calls eight methods that **no longer exist**:
`addGoal`, `get`, `listGoals`, `markAbandoned`, `markCompleted`, `updateProgress`,
`snapshot`, `size`.

The class today exposes: `add(String, int priority)`, `complete(int)`,
`abandon(int)`, `activeGoals()`, `activeCount()`, `completedCount()`, `size()`, and a
`Goal(id, description, ..., Status)` record with `Status { ACTIVE, COMPLETED, ABANDONED }`.

That is a **redesigned, narrower, and arguably better API** — goals are now integers with
explicit lifecycle methods rather than string-keyed objects with ad-hoc mutators. Porting 13
tests means rewriting most of the file against the new contract, and several of its
assertions encode the OLD semantics, so a mechanical rename would produce tests that
compile and assert the wrong thing. That is a piece of work, not a cleanup, and it is
recorded as open rather than faked.

**These three are not deleted and not committed broken.** They are held at
`/tmp/opencode/d2/` with this table as the port specification.

### What the 5 remaining failures actually are — 25 of 30 pass

`matrix-brain-runtime` went 597/0 -> **630 tests / 5 failures**. The five are not noise;
they are the point of D2 option B.

**Two are genuine defects the recovered tests just discovered:**

1. `distillation_summary_measures_cumulative_impact` and `distill_ledger_summary_aggregates`
   both fail with
   `NullPointerException: Cannot invoke "java.lang.Number.intValue()" because the return
   value of "java.util.Map.g..."` — the ledger **summary aggregation dereferences a null map
   value**. A summary path that NPEs is a defect regardless of which test found it, and it
   is now visible because a test that exercises it exists again.

2. `ledger_handles_corrupt_lines_gracefully` fails with
   `NumberFormatException: For input string: ""` — a test whose *name* promises graceful
   handling of corrupt lines now **throws** on an empty field. Either the loader regressed or
   it never covered the empty-string case; either way the promise in the name is currently
   false, which is a documentation-truth defect as much as a code one.

**Three are stale assertions about a ledger identity scheme that has since changed:**

3. `distill_appends_ledger_row_with_real_engine_identity` expected provenance
   `"synthetic:teacher"` and got `"run-1791201430272"`.
4. `distill_ledger_persists_across_reopen` expected `"test:run1"` and got `"run-1"`.

   The ledger now identifies a run by a generated run id rather than by a caller-supplied
   provenance string. **These assertions are not "wrong" so much as describing a contract
   that no longer exists**, and which of the two is correct is a design question, not a
   test fix: `TrueDistillationFactory` computes an `artifactHash` and reports it, and an
   identity derived from a timestamp is weaker provenance than one derived from content.
   Recorded as NEEDS-OWNER rather than massaged until green.

None of the five is skipped, disabled or deleted. They are the first honest evidence that
these subsystems have integration coverage at all.

---

## RECON-W33.1 — the BitNet skip record above is now HISTORICAL. They execute.

Everything the D1/D8 sections say about BitNet being environment-blocked was accurate on
2026-10-05 at 4c9eacc2 and stopped being accurate the moment the weights were provisioned.
Read this section before quoting a BitNet skip count from anything above.

### What was measured, not assumed

The D1 guard pointed at `/tmp/hf_cache/...`. At the start of W33.1 that path **did not
exist at all** — `/tmp` is not persistent, so the assumption had silently hardened into a
permanent skip, and "28 failures became 32 skips" had become a skip that could never
become a pass. Nothing about D1 was wrong when it was written; it was incomplete, and the
incompleteness was invisible precisely because a skip looks like a non-event.

Provisioned with `scripts/provision-bitnet.sh` into `data/models/bitnet-checkpoint/`
(git-ignored, repo-relative, reproducible):

| file | size |
|---|---|
| `model.safetensors` | 1,178,623,988 bytes |
| `model.safetensors.sha256` | `8143ae115ed6babe5e5ada8fb8c5b769d8f417802b2db042ad98b4f7ed73975b` |
| `tokenizer.json` | 9,085,698 bytes |
| `config.json`, `generation_config.json`, `special_tokens_map.json`, `tokenizer_config.json` | present |

`microsoft/bitnet-b1.58-2B-4T` reports `gated=False`, so no license click-through was
required. Verified via the Hub API before downloading rather than discovered by failure.

### Result: 37 tests, 0 skipped, 0 failed

```
PASS  BitNetAutoregressiveGenerationTest         tests=2
PASS  BitNetBlockRealForwardTest                 tests=1
PASS  BitNetKvCacheGenerationTest                tests=4
PASS  BitNetModelFullForwardTest                 tests=1
PASS  BitNetModelLoadTest                        tests=6
PASS  BitNetPrefillGenerationTest                tests=4
PASS  BitNetQuantizationBenchmarkTest            tests=5
PASS  BitNetRealForwardTest                      tests=3
PASS  BitNetRealLoadTest                         tests=2
PASS  BitNetSampledGenerationTest                tests=4
PASS  BitNetTextGenerationTest                   tests=2
PASS  BitNetWeightUnpackerIntegrationTest        tests=3
TOTAL 37 tests, 0 skipped, 0 failed
```

**BitNet went from UNTESTED to tested against the real 1.58 2B-4T checkpoint.** Forward
pass, prefill, KV-cache, autoregressive and sampled generation, weight unpacking and
quantization all execute and pass. The suite needed 6m54s, which is the honest cost of
running a 2B-parameter model for real rather than skipping it.

### The fixture change, and the two mistakes caught doing it

`BitNetRealModelFixture` no longer hardcodes a path. Resolution order is
`-Dmatrix.bitnet.model` -> `$BITNET_MODEL_PATH` -> repo-relative
`data/models/bitnet-checkpoint/model.safetensors`. A file under
`MIN_CHECKPOINT_BYTES` (1.0 GB, ~85% of the real payload) counts as ABSENT, not as broken:
a truncated download passes an `exists` check and then fails inside a safetensors reader,
where the error blames the reader instead of the download. Exact integrity is checked once
at provision time via the sha256 sidecar rather than hashing 1.2 GB in each of eleven test
classes.

Two real mistakes were made and caught during this change, both worth recording because both
were invisible in review:

1. Renaming the public `MODEL_PATH` field broke all eleven consumers before it was noticed.
   The field had to be retained; the eleven classes alias it as a constant, which is why
   fixing the fixture alone propagated to every one of them.
2. Two duplicate-definition errors from a tool that appended rather than replaced. Caught by
   `compileTestJava` and by counting declarations on disk (226 lines, 1 class declaration).
   **LSP continued to report the stale 3-copy/335-line version afterwards** — the same class
   of false positive already recorded for `FederationRegistryTest`. Gradle and the files on
   disk are authoritative; LSP was not.

### Consequence for every status line written before today

Any report, dashboard, capability level or release note claiming BitNet is untested,
unverified, or blocked is now **stale and wrong**. BitNet is executed. Conversely, the
claim that BitNet tests "pass" was previously unearned and only became true at this commit.

---

## RECON-W33.1 — CORRECTION: my W32.34 description of the 3 held-back D2 tests was wrong

W32.34 reported these three as "held back, needing genuine API ports", and gave a specific
reason for each. W33.1 measured the actual error counts and symbols instead of restating the
claim. **Two of the three reasons were wrong.** The decision to hold them back was right; the
justification was not.

Measured with `javac` against the current classpath, 1551 entries:

| test | errors | my W32.34 claim | what is actually true |
|---|---|---|---|
| `AutonomyIntegrationTest` | 50 | "GoalTracker was redesigned" | **correct.** Real drift |
| `SleepAndConsolidationIntegrationTest` | 30 | "packages/classes changed" | **imprecise.** Imports resolve fine; the `ConsolidationCycle` *constructor signature* changed |
| `GpuKernelEngineIntegrationTest` | 3 | "imports federation proto types not on the current classpath" | **wrong.** `GpuOperation`/`GpuTask` exist at `matrix-core/src/generated/java/io/matrix/federation/proto/`. The missing symbol is a method |

### The GPU one is a lost capability, not an obsolete import

The three errors are all the same call:

```
error: cannot find symbol
    var result = e.runKernel(task);
    symbol:   method runKernel(GpuTask)
    location: variable e of type RealGpuKernelEngine
```

`RealGpuKernelEngine` today exposes `runBitCosine(long[] a, long[] b) -> KernelResult`,
`backend()`, `stats()` and `snapshot()`. There is no generic protobuf-dispatched kernel
runner any more. The test exercised `GpuOperation.GPU_OPERATION_CONVOLUTION` and friends
through `runKernel` — **arbitrary GPU operation dispatch was deliberately replaced by one
specific bit-cosine kernel.**

This is a capability regression recorded in a test, which makes the test valuable evidence
rather than junk. The question is not "how do I make these 3 errors go away" but "was
generic GPU op dispatch dropped on purpose, and should it come back?"

- If deliberate: the test is correctly obsolete, and should be deleted with a note.
- If accidental: the capability was lost silently, and the test is the only record of it.

Disposition: **NEEDS-OWNER.** Not deleted, not massaged. Filed as its own entry because
"an API got smaller and a test is the only witness" is a different kind of finding from
"a test is out of date", and this session's own report had conflated the two.

### Method note: the wrong claim survived because I filtered the compiler output

The first attempt grepped javac for `error:` and got an empty result, which I read as
"all three compile". They did not. The empty output came from a grep pattern that matched
nothing, and 0 `.class` files were produced. The tell was available and ignored: checking
whether the compiler emitted output is not the same as checking whether it succeeded. The
corrected counts came from re-running with an argfile and counting `\.java:[0-9]+: error`
directly.
