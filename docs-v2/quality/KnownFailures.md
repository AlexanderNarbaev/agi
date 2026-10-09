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

---

## RECON-W33.2 — Science-debt triage of the remaining 27 core failures

Applying the operator's 3-approach limit. I did **not** attempt three fixes on each: the limit
is a ceiling on effort, not a quota, and burning three attempts on a Kolmogorov bound is
exactly the time-wasting the directive forbids. Instead each failure was classified by
reading the assertion against the code it exercises, then bucketed. Where a claim is a true
statement about the algorithm, retrying is pointless.

### Bucket A — RESEARCH_OPEN_QUESTION (scientific/unproven claims). 12 failures.

Not bugs. The tests assert properties that are either open questions or statements about
floating-point reality that no tolerance can rescue. Retrying cannot make them pass.

| test | claim that cannot currently be met |
|---|---|
| `CausalEmergencePropertyTest.propertyMaxCausalEmergenceForUniformIsZero` | uniform input yields 0.06303440583379408, asserted < 1e-9. Is "uniform input implies zero emergence" true, or is the measure entropy-biased? |
| `CausalEmergenceTest.maxCausalEmergenceNonNegativeForUniform` | same measure, non-negativity expectation |
| `KolmogorovComplexityTest.propertyKolmogorovConstantIsLow` | asks a complexity constant to be small with no defined threshold |
| `KolmogorovComplexityTest.constant_trajectoryLowComplexity` | constant trajectory should be low-K; depends on the estimator |
| `KolmogorovComplexityPropertyTest.propertyConstantTrajectoryLowK` | same |
| `KolmogorovComplexitySnapshotTest.normalizedKBoundedZeroOne` | normalisation must land in [0,1]; currently outside |
| `W120LSystemPhiCorrelation.constantOutputHasLowComplexity` | constant output should show low phi-complexity |
| `PhiComplexityFingerprintPropertyTest.propertySequenceFingerprintBounded` | fingerprint bounds depend on the encoding, which is unspecified |
| `PhiMaxCalculatorPropertyTest.propertyPhiMaxGreedyNonNegative` | greedy phi-max non-negativity |
| `VariationalFreeEnergyPropertyTest.propertyELBOFiniteForList` | ELBO finiteness for a degenerate list |
| `MultivariateGaussianAnalyzerPropertyTest.propertyCorrelationBounded` | correlation range under degenerate covariance |
| `SeriesCorrelatorPropertyTest.propertyPearsonRandomIndependentIsSmall` | Pearson r must be small for independent series; false for near-constant series, where r is undefined or +/-1 |

Evidence that resolves these: a defined estimator and a stated tolerance, or an explicit
statement that the property is empirical and given a percentile band. Until then a red test
here is a research note, not a defect. **Owner: unassigned.**

### Bucket B — TEST-HARNESS DEFECT (the oracle is wrong). 8 failures.

The production code is probably fine; the test asserts something unachievable or is
mis-wired. These are the cheapest real fixes.

| test | defect |
|---|---|
| `MemristorSwitchPropertyTest.propertyStdpLargeDtNoChange` | expects exactly 0.0, measured 9.64e-24. Float noise asserted as zero. Tolerance absent |
| `SeriesCorrelatorPropertyTest.propertyAutocorrelationZeroIsOne` | lag-0 autocorrelation must be 1; fails when the series is constant (zero variance) |
| `RegimeTrajectoryAnalyzerTest.alternatingRegimesHaveMultipleRuns` | an alternating regime is counted as one run; definition conflict |
| `RegimeTrajectoryAnalyzerTest.stabilityForAlternatingIsZero` | maximum instability asserted on an alternating series; definition conflict |
| `ProfileStabilityMetricsTest.alternatingProfileIsOscillatory` | same class of definition conflict |
| `CognitivePhaseDetectorPropertyTest.propertyTransitionRateBounded` | transition rate can exceed the assumed bound for short series |
| `CognitivePhaseDetectorPropertyTest.propertyDominantRegimeValid` | dominant regime falls outside the assumed enum for degenerate series |
| `CognitiveGenesisProfilePropertyTest.propertyUnifiedScoreBounded` | unified score escapes the assumed range on edge input |

**Disposition: fix-in-wave W33.3.** Bucket B is where the 3-attempt budget belongs, because
these have a defensible oracle and a known wrong one.

### Bucket C — SHAPE / STRUCTURAL, likely REAL-DEFECT. 7 failures.

| test | finding |
|---|---|
| `CognitiveGroupedQueryAttentionPropertyTest.propertyCompressionRatio` | **throws `IllegalArgumentException: dim must be divisible by numQueryHeads`** — a hard throw inside a property test. Config generation produces incompatible shapes |
| `CognitiveHeatmapTest.profileFieldsMappedToHeatmap` | mapping incomplete |
| `QuantumEmulatorTest.testHadamardGate` | simulator correctness |
| `LSystemTest.unknownSymbolsPassThrough` | unknown-symbol behaviour |
| `InterAgentPhiPropertyTest.propertyMeasureTimeSeriesBounded` | inter-agent measure escapes bounds |
| `Exp228ComplianceTest.realProjectCompliance` | an EXP-228 compliance assertion is false. Highest severity: it is a *compliance* test, and it fails |
| `SleepConsolidationStudyTest.testSleepImprovesRetention` | sleep does not improve retention in the tested setup. Either the feature is ineffective or the study is mis-specified |

**Disposition:** `CognitiveGroupedQueryAttentionPropertyTest` is the clearest defect — a
property test that throws rather than asserts is a generator bug. `Exp228ComplianceTest` is
the most consequential and should be triaged first.

### What was NOT done, deliberately

No test was deleted, skipped, or weakened. No tolerance was loosened to turn red green. The
two tests this session touched were touched because they were **untested**, not because they
were inconvenient: the GPU test asserted enum members that never existed, and the federation
test asserted no validation at all.

---

## RECON-W34.1 — DistillationLedger: 4 of 5 recovered-test failures fixed, 1 needs an owner

The two production defects the W32.34 recovery surfaced are closed, and the fix exposed a
further real bug that no test had been pointing at.

### CLOSED 1 — one corrupt line destroyed the entire audit trail

`readAll()` parsed every field unguarded: `Integer.parseInt(extract(...))`. A line missing a
numeric field yields `""` from `extract`, which throws `NumberFormatException` and **aborts
the whole read** — discarding every valid entry alongside the broken one. For a ledger, whose
entire purpose is accounting for past work, one torn line silently erasing the record is the
worst available failure mode.

Now: damaged lines are skipped, counted and logged at WARNING, and the rest survive. Verified
by a test that writes one good line, one truncated line, one good line, and asserts both good
entries come back.

### CLOSED 2 — the ledger recorded its own impact and never reported it

`append()` persists `hdcPromoted`, `birClausesSynthesized`, `tsetlinLiterals` and `fidelity`
on every line. `summary()` aggregated **only** `runs` and `total_inputs_bytes`. So the one
number that answers *"did distillation actually teach the mind anything"* was written down
every run and never surfaced.

The recovered tests found this as `NullPointerException`s, not as a small wrong number —
`s.get("total_bir_clauses_induced")` returned null and the test called `.intValue()` on it. A
crash that is really a **missing aggregate**.

`summary()` now also emits `total_hdc_promoted`, `total_bir_clauses_induced`,
`total_tsetlin_automata_updated` and `mean_eval_delta`. None of this data was lost; it was
simply never added up.

### CLOSED 3 — `extract()` threw on the input it was designed to receive

Two unguarded indexes: a key with no colon fed a negative index into `substring(...)`, and the
quote-scan called `charAt(i - 1)` at `i == 0`. Both threw `StringIndexOutOfBoundsException` on
exactly the half-written lines a crashed process leaves behind — the input this method most
needs to survive.

### CLOSED 4 — provenance was discarded, and the "content hash" depended on machine speed

Found while fixing the ledger, and not previously reported by anything. In
`TrueDistillationFactory.distillCustom`:

1. `sourceId` was written as `"run-" + System.currentTimeMillis()`, **discarding the provenance
   the caller had already supplied.** `distillCustom("synthetic:teacher", ...)` recorded a
   timestamp instead, so two different teachers distilled in the same millisecond were
   indistinguishable in the audit trail. `sourceId` is now `r.source`.
2. `artifactHash` mixed in `durationMs`, which is wall-clock derived. Article III requires a
   **content** hash, but this one hashed how fast the machine was: the same teacher and the
   same inputs produced two different hashes on two runs. It therefore could not function as
   an identity. The hash is now over `(source, size, clauses)` only and is reproducible.
3. `durationMs` and the row timestamp were two separate clock reads, so a row could report a
   timestamp earlier than the duration preceding it. Now one read feeds both.

### OPEN — NEEDS-OWNER: `samplesUsed` is defined as two different things at once

`TrueDistillationFactoryIntegrationTest.distill_appends_ledger_row_with_real_engine_identity`
expects `inputsCount() == 8`, and `inputsCount()` is an alias for `samplesUsed` — so
`samplesUsed` is a **count**.

`DistillationFactoryIntegrationTest.distill_ledger_summary_aggregates` expects
`total_inputs_bytes == 300` from entries whose `samplesUsed` values are 100 and 200 — so
`samplesUsed` is a **byte count**.

Both cannot hold. The current factory writes `(int) sourceBytes` into `samplesUsed` and
`calibrationInputs.size()` into `inputBits`, which inverts both names. This looks like a
mishap from the snake_case → camelCase schema migration, where `inputs_count` and
`inputs_bytes` were separate fields that no longer have separate homes.

**Not guessed at.** Picking either side changes the meaning of persisted ledger data, and the
wrong pick silently corrupts a record other tools read. Recorded as NEEDS-OWNER with both
test citations so the decision is one reading rather than an archaeology exercise.

### Also corrected in tests, with the reasoning kept in place

- `ledger_handles_corrupt_lines_gracefully` wrote the **old snake_case schema**
  (`inputs_count`, `bir_clauses_induced`, `duration_ms`). Every field extracted as `""` and all
  three lines were skipped, so a test asserting "a malformed line is skipped" was actually
  measuring schema drift and reading `size 0` as a pass on a different defect. Fixture
  migrated to the current schema.
- `distill_ledger_persists_across_reopen` read the **wrong constructor slot**: it appended
  `("run-1", "test:run1", ...)` then asserted `source()` returned `"test:run1"`, which is the
  second argument, not the first. It could only ever have failed, and was reported as a
  ledger-identity defect. Now asserts round-trip fidelity on both fields.

**Result: brain-runtime 657 tests / 5 failures -> 666 tests / 1 failure.** Nothing was skipped
or deleted to achieve it.

---

## RECON-W34.3 — `AdvancedTsetlinMachine.init()` produces a model that can never fire

Found while building the monotone-tightening classifier. It is a defect in the existing class,
not a misuse of it, and it is recorded here because "the Tsetlin machine was not used for the
safety gate" is a claim that needs its reason stated.

### Measured

```
avg includes per clause at init : 255.2   (of 512 features)
active features in one sample   : 43
predict at init                 : class=0, confidence=0.000
```

`predict` votes for a class only when a clause's **every** `include` feature is present in the
input. With ~255 required features and 43 present, no clause can match any real sample. The
model therefore returns class 0 with zero total votes and confidence 0.0, for every input,
forever, unless training first thins the include sets.

### Why training does not rescue it

`updateClause` can clear includes only on the **anti-reinforce** branch, which fires when
`predict(model, features).predicted() != actualLabel`. That is a real mechanism and it does
eventually thin the harmful class — but the benign class is reinforced on every benign sample,
adding includes, so the two classes start from opposite pathologies: harmful clauses are far
too dense to fire, benign clauses drift toward vacuous "include everything" clauses that match
everything. Convergence is slow and, at the confidence threshold a safety gate needs,
unreliable.

### Disposition

**Not fixed here.** Changing a core Tsetlin engine's initialisation is a behaviour change to a
class other subsystems use, and it deserves its own wave with its own evidence. The classifier
built for W34.3 therefore uses a transparent indicator score over the same trigram features,
and says so in its own Javadoc, so nobody later assumes a trained Tsetlin model is behind the
safety gate when it is not.

This is the second time in two waves that a shipped component turned out to be inert for its
stated purpose — `ReflexEngine` ordering being the first. A component that is present,
plausible, and never fires is more dangerous than one that is absent, because the code reads
as if the capability exists.

---

## RECON-W34.4 — RESOLVED: `samplesUsed` unit ambiguity, by operator ruling

Escalated rather than guessed at in W34.1. The operator ruled (2026-10-09):

> `total_inputs_bytes` must report the real byte value; `inputsCount()` must return a count,
> not bytes. Fix both.

So **each field reports its own unit**, and the ambiguity is resolved by disambiguation rather
than by picking a winner and discarding the other meaning.

| field | unit | read by |
|---|---|---|
| `inputBits` | **bytes** of input consumed | `summary().total_inputs_bytes` |
| `samplesUsed` | **count** of samples consumed | `Entry.inputsCount()` |

### What was actually wrong

Both writers passed these arguments **inverted**:

```
TrueDistillationFactory : inputBits <- r.captures (count), samplesUsed <- sourceBytes (bytes)
ModelToMatrix           : inputBits <- tokens.size(), samplesUsed <- (int) inputsBytes
```

and `summary()` summed `samplesUsed` into a key called `total_inputs_bytes`. The consequence
was exactly as diagnosed: a ledger reporting a **sample count under a byte key**, and
`inputsCount()` returning bytes.

### Disposition of already-written ledger files (Article VII)

Field *names* and *positions* in the NDJSON line are unchanged — `inputBits` was always the
third numeric field and `samplesUsed` the fourth. **No file needs rewriting**, and no
migration is required, because what changed is which quantity each writer supplies for those
fields, not the schema itself.

Files written by an earlier build DO carry inverted values: their `inputBits` holds a count
and their `samplesUsed` holds bytes. They remain readable, and `readAll()` reproduces the
values as stored. For such a file `total_inputs_bytes` now reports the older count value.

That is recorded rather than silently corrected, because rewriting a number in an audit trail
after the fact is exactly the kind of edit this ledger exists to make impossible. **Owner
action:** re-distil, or accept that pre-W34.4 ledger rows carry inverted units. No file in the
repository currently holds such a row — the ledgers found on disk are the DISK-LEDGER, which
uses a different shape entirely.

---

## RECON-W34.5 — KolmogorovComplexity: a constant tax that looked like a measurement

Five red tests across four files shared one cause, and the number that revealed it was
**exactly 64.0** — not 63.9, not 65.1. That is `Long.SIZE`, added unconditionally:

```java
double modelBits = logarithmicEncoding(alphabet) + Long.SIZE;
```

A constant trajectory therefore scored 64.0 regardless of length or content. A compile-time
constant had been given the appearance of a measurement, and four unrelated tests were
reporting it as one.

### Why it was wrong, from the class's own contract

`estimate` documents *"bounded by 8·trajectory.length"*, and the failing test's own comment
reads *"much less than 8*20=160 raw bits"*. Both models treat a state as costing at most 8
bits. A flat 64 bits per **sequence** is 3.2 bits per state at length 20, and more than 3 at
any length below 8 — so the model cost dominated the quantity it was meant to be a rounding
error within, and a maximally compressible sequence could never score below it.

Naming one representative state is a real cost, but it is a **model** cost: it does not grow
with N. Adding it to a per-symbol measure distorts the comparison the estimator exists to
support. The fix removed it. `logarithmicEncoding(alphabet)` — which does vary with the data
— is now the whole model cost.

### A second, genuine contradiction inside the suite

Removing the tax was not sufficient, and the reason is worth more than the fix.

| contract | asserted by | count |
|---|---|---|
| `estimate([x]) == 64.0` | `singleStateReturnsOneLong`, `propertySingleStateIs64`, `propertyKolmogorovSingleIs64`, `propertySingleStateReturns64`, `degenerate_inputs_are_handled` | 5 |
| constant trajectory has K < 20 for lengths **1..32** | `propertyKolmogorovConstantIsLow`, `propertyConstantTrajectoryLowK` | 2 |

At length 1 these demand 64.0 and <20 of the same input. **No implementation can satisfy
both**, so this was a contradiction in the specification, not a bug in the code.

Resolved on the merits rather than by picking a side: at N = 1 a "constant sequence" is not a
pattern at all — there is no repetition to exploit, because there is nothing after the first
element. You are naming one specific 64-bit value, and that costs 64 bits. The five-test
contract is correct and the single-element early return was **restored** as deliberate. The
two property providers were drawing `1..32` and are now `2..32`, with the reason recorded at
the property itself.

Note the trap: the flat tax had been **masking** the boundary. With it in place, every
constant scored 64.0 and no test could tell a real boundary from a bug. Fixing the tax is what
made the boundary visible, and fixing the tax is what made it look like a regression.

### A third, in a test outside this package

`W120LSystemPhiCorrelation.constantOutputHasLowComplexity` used the L-system rule `F -> "F"`,
which is a **fixed point**: ten generations of `"F"` yield the one-character string `"F"`. The
test asserted *"single character repeating"* over a string that never repeated, so it fed a
length-1 sequence into the boundary case above. The rule is now `F -> "FF"` at depth 3, which
produces `"FFFFFFFF"`, and the test **asserts the output length is 8** so the fixture cannot
silently go vacuous again.

### What this cost and bought

Five failures closed, and the estimator now behaves as documented: constant data is cheap,
structured data sits between, and random data is expensive. A new contract test
(`KolmogorovEstimatorContractTest`) pins the boundary from both sides, so the flat tax cannot
return unnoticed — its fingerprint is the exact value 64.0, which the test asserts against.

**Generalisable lesson, now the second time in two waves** (the first was
`AdvancedTsetlinMachine.init()`): a component that is present, plausible and never fires is
more dangerous than one that is absent, because the code reads as though the capability
exists. Here it was a constant masquerading as a measurement; there it was a model that could
not classify anything. Both were found by a test asserting a value and getting a suspiciously
round one.

---

## RECON-W34.6 — four more oracles were wrong, not four defects in production code

All four of these were property tests whose ASSERTION was the problem. In each case the
production code was either correct or correctly documented, and changing it to satisfy the
test would have made it worse. That is the distinction this wave exists to establish, and it
is not always obvious from a red test.

### 1. `CognitiveGroupedQueryAttentionPropertyTest.propertyCompressionRatio` — a generator bug

A property test that **throws** is not testing a property. It was constructing
`CognitiveGroupedQueryAttention(64, qh, kvh, seed)` with a fixed dim of 64 while drawing
`qh` from 1..16, and its guard checked only `qh % kvh`. It never checked `64 % qh`, so every
head count not dividing 64 — 5, 6, 7, 9, 11, 13, 14, 15 — threw
`IllegalArgumentException` from the constructor and failed the test.

Worse in the other direction: the early `return` meant most of the 30 tries asserted nothing
at all, so the property was **silently vacuous** for the majority of its budget while also
failing on the tries that did construct. Providers now generate valid configurations only
(query heads from the divisors of 64, KV heads intersected against the drawn qh), so every
try asserts. The production precondition is legitimate and unchanged.

### 2. `MemristorSwitchPropertyTest.propertyStdpLargeDtNoChange` — bit-exact equality on a float

Measured failure: `expected 0.0 but was 9.64374923981959E-24`.

The update computes `exp(-|dt|/tau)` = `exp(-2000)`, which underflows toward zero but leaves a
residue. `"exp(-2000) == 0.0 exactly"` is **not a property of IEEE-754 doubles**, and asserting
it makes the test sensitive to the last bit of a result already indistinguishable from zero
at any working precision. The claim the property means is "a spike 2000 tau out leaves the
weight unchanged", so the assertion is now a tolerance on that claim.

### 3. `SeriesCorrelatorPropertyTest.propertyAutocorrelationZeroIsOne` — asserted past the definition

Autocorrelation at lag 0 of a **constant** series is mathematically undefined — every point
equals every other, so the normalised quantity is 0/0. `SeriesCorrelator` resolves this
deliberately and documents it: *"Returns 0 if either has zero variance."* Verified directly:

```
autocorrelation({5,5,5,5,5,5}, 2)[0] = 0.0
autocorrelation({1,2,3,4,5,6}, 2)[0] = 1.0
```

The production behaviour was right and the property contradicted its own documentation. The
property is narrowed to the domain where it is defined, and a new test pins the documented
zero-variance answer so the boundary is explicit rather than incidental.

### 4. `SeriesCorrelatorPropertyTest.propertyPearsonRandomIndependentIsSmall` — a statistical claim as a hard bound

Asserted `|r| < 0.5` for independent Gaussians, over a deterministic seed generator. Measured
on this machine across 19,600 draws, lengths 16..64:

```
draws with |r| >= 0.5 : 117
worst observed |r|    : 0.6587  (at length 16)
```

The property was **false about mathematics**, not unlucky. For n independent normal samples
the sampling distribution of r has standard deviation ~`1/sqrt(n-2)`, which at n = 16 is
0.286 — so `|r| >= 0.5` is roughly a 1.7-sigma event and WILL occur on some seeds. A fixed 0.5
also cannot hold at every length, since the bound must widen as n shrinks.

The threshold is now the **derived** 4-sigma value, `4/sqrt(n-2)`: 1.03 at n = 16 (beyond the
mathematical maximum, so vacuously satisfied) and 0.50 at n = 64. The honest statement is made
explicit in the assertion message — this checks that correlation does not **scale** with n,
not that it is small in absolute terms. A property that cannot be falsified is not useful, so
that limitation is stated rather than hidden.

### The pattern, now four instances deep

`AdvancedTsetlinMachine.init()` (a model that can never classify), `KolmogorovComplexity`
(a constant masquerading as a measurement), and now two generators that produced invalid
inputs and one oracle that demanded a value IEEE-754 does not promise.

**All four were found by reading the failure VALUE rather than the failure message.** A
message says *what* failed; a suspiciously round number, an exception from a constructor, or a
threshold that no statistic can honour, says *why it was never going to pass*.

---

## RECON-W34.7 — a Hadamard gate that was not a Hadamard gate

The most consequential defect found in this debt-burn-down, and the only one where the
production code was genuinely wrong rather than the test being unfair.

**Found by an existing test that was only half right.** `QuantumEmulatorTest.testHadamardGate`
asserts `probabilityZero == 0.5` first, which the broken implementation DOES satisfy, and then
`probabilityOne == 0.5`, which it does not. So the failure presented as "expected 0.5 but was
0.0" with no indication that a fundamental invariant had been violated underneath it.

**What the code did.** For `|alpha, beta>` it computed:

```
alpha' = alpha / sqrt(2)      beta' = beta / sqrt(2)      <- scaling, not mixing
```

**What Hadamard is.** The mixing gate:

```
alpha' = (alpha + beta) / sqrt(2)
beta'  = (alpha - beta) / sqrt(2)
```

**Why scaling is dangerous rather than merely wrong.** For `|0> = (1, 0)` the two agree on
`alpha' = 1/sqrt(2)`, which is why the first assertion passed and the bug hid. They disagree
on `beta'`, and the error is not small:

```
measured, after H on |0>:   p0 = 0.5000   p1 = 0.0000   TOTAL = 0.5000
```

A quantum state must satisfy `p0 + p1 == 1`. This one totals **0.5**: the simulator was
silently discarding half the probability mass on every Hadamard. Since `measure()` samples from
`p0` without renormalising, **every collapse was biased toward the wrong outcome** — and the
outputs still looked plausible, which is the worst way for a simulator to fail.

It compounded too: `H(H(|0>))` gave `p0 = 0.25` instead of `1.0`, because each application
multiplied the mass by 0.5 again. A scaling operation can never satisfy `H = NOT`.

Fixed to the mixing form, which is unitary and therefore preserves normalisation by
construction. `QuantumGateNormalisationTest` now pins all four gates' `p0 + p1 == 1`, both
superpositions, and involution.

### Two more in the same wave

`MultivariateGaussianAnalyzer.correlation` divided covariance by `stdJ * stdK` **without
clamping**, and a property caught it returning `1.0000000000000002`. A correlation
coefficient is bounded to [-1, 1] by Cauchy-Schwarz, so this is a contract violation, not a
rounding curiosity — and any consumer doing a downstream range check breaks on it. Clamped in
production rather than loosened in the test: a function named `correlation` that returns
outside the mathematical range of correlation is wrong at **any** tolerance, and a tolerance
would only hide it from the next caller.

`LSystemTest.unknownSymbolsPassThrough` expected `"FFXXY"` from `F->"FF"` on `"FXY"` at depth
2, with the comment `FX+Y -> FF + XY = FFXXY` — which conjures an extra X. The generations are:

```
gen 0: F X Y
gen 1: FF X Y
gen 2: FFFF X Y
```

`"FFFFXY"` was correct throughout. The expectation was wrong, and the derivation is now
recorded in the test so the intent is checkable rather than merely asserted.

### Two generators that crashed before reaching the code under test

`CognitivePhaseDetectorPropertyTest` seeded its RNG with `seeds[0]` while its provider drew
sizes 0..16 — so an empty list threw `ArrayIndexOutOfBoundsException` inside the generator,
before `CognitivePhaseDetector` was ever called. Production was already correct and untouched:
`dominantRegime` returns `"UNKNOWN"` for empty, and the property already accepted `"UNKNOWN"`.
The test was failing on its own fixture.

`InterAgentPhiPropertyTest` guarded `dim < 1` but `measureTimeSeries(trajectory, 2)` requires
every row to have at least `N = 2` columns. So `dim == 1` built a trajectory the production
method is right to refuse, and the property reported that refusal as a failure of
`InterAgentPhi`. The precondition is now stated where the call is made.

### `CognitiveHeatmapTest` compared two different quantities

It asserted the heatmap cell equals `MemristorSwitch.conductance(0.5)` = `0.5000005`, while
the heatmap holds `0.5` — the value written into the profile. One is the field as recorded,
the other is the memristor model applied to it a second time. The test was asserting that the
heatmap **re-derives a physical model from stored data**, which is not what a projection does.

**The running theme is unchanged from W34.5 and W34.6:** eight of the failures so far were
wrong assertions and two were real defects, and the real ones were found by reading the failure
*value* — 1.0000000000000002, a total of 0.5000, an exception from a constructor — rather than
the failure message.

---

## RECON-W34.8 — mutual information computed across mismatched random variables

`PhiMaxCalculator.propertyPhiMaxGreedyNonNegative` failed with `expected >= 0.0 but was -1.0`.
Mutual information is a KL divergence and is **non-negative by Gibbs' inequality**, so a negative
value is not a tolerance question — it is proof that the terms being combined did not belong
together.

### The bug

The identity in use is

```
I(L;R) = H(L) + H(R) - H(L,R)
```

which requires all three terms to come from the **same random variable**. In the code:

- `H(L)` and `H(R)` were computed over **projected** indices (`projectState(state, mask, n)`)
- `H(L,R)` was computed over the **raw** state values

So `H(L,R)` described a different variable from `H(L)` and `H(R)`, and the identity evaluated
to an arbitrary number — sometimes negative.

### Reproduced exactly

For the 2-element sequence `[4, 0]`, the low two bits of `4` are `00`, identical to `0`, so both
states project to the same index while remaining distinct raw values:

```
left  = {0: 1}                          -> H(L)   = 0
right = {0: 1}                          -> H(R)   = 0
joint over RAW states {4:1, 0:1}        -> H(L,R) = log 2
=> mi = 0 + 0 - 0.693 = -0.693;  / log 2 = -1.0
```

After the fix `[4, 0]` returns `0.0`, and a sweep over the property's own generated range
(lengths 2..8, values 0..7) yields no negatives.

The joint term is now taken over the same projected `(left, right)` pairs the marginals use.
`entropy`'s key type was generalised from `Integer` to `Object` rather than duplicating the
method, since entropy depends only on the counts and never on the key — the packed pair key
does not fit in an `Integer`.

### A second real defect: the batch counter raced its own futures

`ContinuousBatchScheduler.totalProcessed()` returned **8 for 10 submitted requests**. Requests
whose futures had demonstrably completed were not yet counted, because `processBatch` completes
each future as it goes while the counter was incremented **once per batch, after the whole batch
returned**. Any caller that awaited all futures and then read the counter could observe
completion before the increment that publishes it had run.

This was exposed, not caused, by the W34.2 harness fix: the test previously returned early via
`if (!bridge.load()) return;` — a vacuous PASS — so it rarely ran at all. Counting now happens at
the point of completion, immediately before `future.complete()`, which makes the counter
consistent with the futures by construction: **if `future.isDone()` then the count includes it.**
Verified stable across three consecutive runs.

**Both defects share a signature.** In each case a quantity was computed from a subtly wrong
input — mismatched variables in one, a stale read in the other — and the resulting wrongness was
a *plausible* number rather than an obvious garbage value. Neither would have been caught by a
sanity check that only asked "is this a number".
