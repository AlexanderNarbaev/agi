# v17.6.0-mind — Release Caveats

**Tag:** `v17.6.0-mind` · **Tagged at:** `fa8c3b6a`
**Covers:** RECON-W32.22 → W33.3 · **Date:** 2026-10-05

This document exists so that nobody has to take this release's word for anything. Every claim
below carries the command that produced it, and every number is measured on the tagged commit
rather than carried forward from an earlier report.

---

## 1. What is genuinely better in this release

### BitNet is tested, not assumed

**Before this release:** the BitNet suite had never executed. 28 tests failed for weeks with no
attributed cause; D1 converted them to 32 skips, which was honest but permanent — the path was
`/tmp`, and `/tmp` is not persistent.

**Now:** `data/models/bitnet-checkpoint/` holds the real checkpoint.

| | value |
|---|---|
| `model.safetensors` | 1,178,623,988 bytes |
| sha256 | `8143ae115ed6babe5e5ada8fb8c5b769d8f417802b2db042ad98b4f7ed73975b` |
| repo | `microsoft/bitnet-b1.58-2B-4T`, `gated=False` |
| **result** | **37 tests, 0 skipped, 0 failed** |

Forward pass, prefill, KV-cache, autoregressive and sampled generation, weight unpacking and
quantization all execute against real weights.

```
./gradlew :matrix-core:test --tests 'io.matrix.research.BitNet*'
```

**A caution about this number.** The 37 tests require a 1.2 GB checkpoint that is *not* in the
repository. On a machine without it, all 37 skip with a message naming
`scripts/provision-bitnet.sh`. A green local run and a green CI run are therefore not the same
claim, and neither is wrong.

### Federation can no longer learn from a message that never arrived

`KnowledgeExchangeProtocol.fromJsonLine` performed no shape validation. A line truncated
mid-write produced `Fact("","","",0.0,0)` — indistinguishable downstream from a real fact — which
merged into the store and rendered as `fed-<node>- => `: blank subject mapped to blank answer.
**A torn transmission became an invented fact, quietly.** `id`, `input` and `answer` are now
required and non-blank; the rejection names the missing field.

### Refused media is now remembered

`MediaDecoding.probe` extracts metadata from **any** byte sequence — size, magic-byte signature,
printable ratio, bounded container headers — and `describe` renders it into a knowledge
document. Drop a JPEG in, ask later what it was, and MATRIX answers *"4096 bytes beginning
`ffd8ff`, JPEG header present, I did not read the contents"* — which is true.

Before, the refusal discarded the observation. The mind knew and then forgot.

### A class was unreachable from its own module's tests

`RealGpuKernelEngine` was **never constructible** in any brain-runtime test: the test JVM
lacked `--add-modules=jdk.incubator.vector`, so construction threw
`NoClassDefFoundError(jdk/incubator/vector/Vector)`. The flag existed in matrix-core and in
the gateway, where it stopped unrelated wiring tests from failing — and not upstream, in the
module that owns the class. **A downstream workaround for a missing upstream flag hid the fact
that the owning module could not load its own production code.**

### A correction to this section, recorded rather than quietly fixed

An earlier draft of this document claimed *"nine deliberately-empty `catch` blocks now log at
their real severity."* That number came from an inherited session summary and **could not be
verified** when checked: the `RECON-W32.28/29` markers in the tree concern sensor extrema and
KL-divergence edges, not catch blocks. The claim has been removed rather than restated. The
measurable state is 130 catch blocks that log and 44 with no executable body, but "no
executable body" is not the same as "unjustified" and this document does not claim it is.

Reporting the removal is the point. A release note that keeps an unverified number because it
sounds like progress is the same failure mode this project exists to catch.

---

## 2. Measured state at the tagged commit

```
./gradlew :matrix-core:cleanTest :matrix-core:test
./gradlew :matrix-brain-runtime:cleanTest :matrix-brain-runtime:test
./gradlew :matrix-api-gateway:cleanTest :matrix-api-gateway:test
```

| module | tests | failures | skipped |
|---|---|---|---|
| matrix-core | 8107 | **28** | 18 |
| matrix-brain-runtime | 657 | **5** | 2 |
| matrix-api-gateway | 152 | 0 | 0 |

An earlier draft of this table carried **8111 / 27 / 22** — numbers carried forward from the
W33.1 run rather than measured at the tag. The release gate re-ran the full suite and they did
not reproduce. Corrected to what was actually measured. The counts drift between runs because
jqwik property tests generate a varying number of cases; the *failure set* is what matters, and
one member of it is new (see below).

Skipped are environment-blocked and labelled as such. **They are not passes and are not counted
as passes anywhere in this repository.**

---

## 3. What still fails — and it is not rounded up to green

### 28 core failures — 27 documented, plus one newly observed flake

**`Exp089ContinuousBatchingTest.tenConcurrentRequests()` is new and is a flake, not a
regression.** Evidence gathered rather than assumed:

| context | result |
|---|---|
| 26-minute full suite (8100+ tests) | FAILED |
| 3 runs back-to-back | 2 failed, 1 passed |
| 8 runs isolated | 8 passed |

Cause, from reading the test: it submits 10 concurrent inferences to a `QwenOnnxBridge` and
awaits each with `f.get(60, TimeUnit.SECONDS)`. On this host ONNX Runtime falls back to CPU
(`libcudart.so.12` symbol mismatch), so 10 concurrent CPU inferences contend with the rest of
the suite and can exceed a wall-clock timeout. A timing assertion is a test-harness defect, not
a property of the scheduler. Filed as Bucket B alongside the other 8.

Separately, the same test contains `if (!bridge.load()) return;` — an **early return that makes
it pass vacuously** when the model is absent. It is therefore simultaneously a flake and a
test that can report success without testing anything.

### The other 27, triaged into three buckets

| bucket | count | meaning | disposition |
|---|---|---|---|
| RESEARCH_OPEN_QUESTION | 12 | the claim is unproven or the estimator undefined | accept as research debt |
| TEST-HARNESS DEFECT | 8 | the test's oracle is wrong, production code is probably fine | fix-in-wave W33.3 |
| STRUCTURAL (likely real) | 7 | probable production defects | needs triage |

**The two you should know about by name:**

- `Exp228ComplianceTest.realProjectCompliance` — a **compliance assertion is false.** The most
  consequential failure in the suite, because it is not a numerical tolerance but a claim that
  the project satisfies something it does not.
- `CognitiveGroupedQueryAttentionPropertyTest.propertyCompressionRatio` — **throws**
  `IllegalArgumentException: dim must be divisible by numQueryHeads` inside a property test.
  A property test that throws is a generator bug, not a property failure.

**Not one of the 27 was deleted, skipped, or had a tolerance loosened to pass.** Two tests were
touched in this cycle, and both were *untested* rather than inconvenient: the GPU test asserted
enum members that never existed, and the federation test asserted no validation at all.

Representative research debt, unfixable by retrying:

- `KolmogorovComplexityTest.propertyKolmogorovConstantIsLow` asks for a small complexity
  constant **with no threshold stated.** No tolerance can satisfy an undefined target.
- `SeriesCorrelatorPropertyTest.propertyPearsonRandomIndependentIsSmall` is **false for
  near-constant series**, where Pearson's r is undefined rather than small.

### 5 brain-runtime failures — all findings, all new to this cycle

These are **not regressions.** They come from integration tests recovered in W32.34 that had not
run in weeks. Their appearing is the coverage increase itself.

**Two are genuine defects the recovered tests discovered:**

1. The ledger **summary aggregation throws `NullPointerException`** on a null map value
   (`distillation_summary_measures_cumulative_impact`, `distill_ledger_summary_aggregates`).
2. `ledger_handles_corrupt_lines_gracefully` **throws** `NumberFormatException` on an empty
   field — a test whose *name* promises graceful handling of corrupt lines.

**Three are stale assertions** about a ledger identity that changed from caller-supplied
provenance (`"synthetic:teacher"`) to a generated run id (`"run-1791201430272"`). These were
recorded as NEEDS-OWNER rather than massaged green, because a timestamp-derived identity is
*weaker* provenance than a content-derived one, and making them pass by accepting the weaker
guarantee would trade a red test for a worse property.

---

## 4. Capabilities deliberately absent

| absent | why | status |
|---|---|---|
| JPEG/BMP/GIF/WebP/TIFF **decoders** | forbidden by directive; unnecessary for the capability | format is *identified*; contents are not read |
| **Generic GPU operation dispatch** | `RealGpuKernelEngine` exposes only `runBitCosine(long[],long[])` | **NEEDS-OWNER.** Tests preserved uncompiled at `docs-v2/quality/lost-capability-tests/` |
| **Goal-progress tracking** | `GoalTracker.Goal` has no `progress` field; ids changed `String`→`int` | **NEEDS-OWNER.** Same preservation |
| **CUDA acceleration** | `libcudart.so.12` symbol mismatch — ONNX Runtime falls back to CPU | **0 tests fail from this** |
| Datasets ingestion pipeline | spec drafted, not implemented | W34+ |
| Command bus / actuation | not started | W34 |

The two NEEDS-OWNER entries are **findings, not noise**: each is a test that is the only
remaining record of a capability that existed. They were preserved verbatim and uncompiled
rather than deleted, because deleting them would have erased the only trace.

---

## 5. Operational notes

- **Disk:** D5 reclaimed 29 GB (108 G → 137 G free). `DISK-LEDGER.ndjson` seq 180.
- **D3 — NOT DONE.** `gitverse/master` is a vestigial 2-file branch. `git merge-base
  gitverse/master develop` returns **empty**, so no fast-forward exists and every alignment is
  a rewrite, which standing policy forbids. The mirror's default is `main`, which already
  equals `develop`. One command resolves it: `git push gitverse --delete master`. See
  `docs-v2/operations/RUNBOOK.md`.
- **FROZEN zones:** 0-diff across `.github/`, `CONSTITUTION.md`, `AGENTS.md`, `ethics/`,
  `EvalBattery.java`.
- **Quality gate:** exit 0.
- **Reviewer protocol:** 8+ reviewer verdicts were specified for this cycle and **not
  achieved.** Four specialists were dispatched in W33.1 and produced zero artifacts in 71
  minutes; they were cancelled and the work was done directly. Findings were self-reviewed and
  mutation-verified, which is weaker than the protocol requires. **This release does not claim
  the multi-agent review it was supposed to receive.**

## 6. Honest summary

This release makes MATRIX measurably more truthful in three places where it previously
fabricated, guessed, or forgot: it can no longer learn an invented fact from a truncated
transmission, it remembers what it refused and why, and a 2B model that had never once been
executed is now tested for real.

It does not have a green suite, and it is not presented as having one. 27 core failures and 5
runtime findings stand, triaged and owned rather than suppressed. Two capabilities were lost
without anyone noticing until a stale test noticed, and both are recorded rather than deleted.

The single largest gap in this release is not in the code. It is that the review protocol
mandated for it did not happen, and this document says so instead of implying otherwise.