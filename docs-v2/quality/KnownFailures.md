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
