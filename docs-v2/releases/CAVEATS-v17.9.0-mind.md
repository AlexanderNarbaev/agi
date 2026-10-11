# CAVEATS — v17.9.0-mind

Tag: `v17.9.0-mind` · 2026-10-11

This release exists because three components were advertising capabilities they did not have.
All three are corrected here, and the corrections are the substance of the release rather than
footnotes to it.

## Measured at this tag

    MODULE                 TESTS FAIL SKIP
    matrix-core             8042    0   22
    matrix-brain-runtime     732    0    2
    matrix-api-gateway       236    0    0
    matrix-spigot             22    0    2
    matrix-quality           23    0    0
    matrix-billing           55    0    0
    matrix-audit             40    0    0
    matrix-operator          20    0    0
    matrix-sdk-java          13    0    0
    matrix-observability     22    0    0
    matrix-tools-distill      4    0    0
    TOTAL (11 modules)      9209    0

    quality gate   exit 0
    Article I      0 blocking, 0 unclassified, 83 permitted
    Exp228         COMPLIANT, onnx=0

All eleven buildable modules are now in one verification command. Previously this campaign
quoted a three-module figure of 9,001 while eight modules had never been run.

## The three corrections

### 1. "BitNet GPU acceleration verified" was false

Claimed in CAVEATS-v17.6.0-mind and TUNING-PARAMETERS.md: *"CPU 9.68 ms vs CUDA 6.55 ms
(1.48x), verified by hiding the GPU."*

What was measured was an **ONNX matmul benchmark**. BitNet is not ONNX:

    grep -ciE 'onnx|cuda|OrtSession' matrix-core/src/main/java/io/matrix/neuron/BitNet*.java
        BitNetModel             0
        BitNetInferenceForward  0
        BitNetBlock             0

Every BitNet matmul is a hand-written triple loop. There is no tensor backend, no native
kernel, no JNI, no CUDA execution provider in the model's path. The CUDA figure is a true
measurement **of a CUDA matmul**; quoting it as BitNet acceleration substituted one claim for
a different claim. Verifying it by hiding the GPU proved the benchmark used the GPU — not that
BitNet could.

A real BitNet GPU path requires exporting weights to a GPU-consumable format, a native kernel,
or a backend abstraction with a CUDA implementation. **None exists.**

### 2. Production Tsetlin training does not learn at the setting production uses

Earlier this campaign I reported the production `TsetlinTrainer` at 100%. That was one
operating point, and I generalised from it. The sweep:

    trainBatch(.., 1)  -> 46%     trainBatch(.., 200)  -> 100%
    trainBatch(.., 5)  -> 46%     trainBatch(.., 1000) -> 46%
    trainBatch(.., 10) -> 46%
    trainBatch(.., 50) -> 46%

One working point with failing neighbours is not a learning curve. And
`RuleInductionEngine.induce()` calls `trainBatch(inputs, labels, 10)` — measured **46%**, which
is chance on a task any learner should solve outright. **Production Tsetlin training is not
demonstrated to learn.** `ProductionTsetlinLearningTest` pins that 46% so the moment it changes
the claim gets revisited.

### 3. "Contradiction detection" detected similarity, not contradiction

`PersistentHdcStore.checkContradiction` measured Jaccard similarity. Measured on real
contradictions:

    'The capital of France is Paris' vs '...is Lyon'   -> DUPLICATE  0.714
    'Water boils at 100 degrees'    vs '...50 degrees' -> DUPLICATE  0.714
    'The server runs on port 8080'  vs '...9090'       -> DUPLICATE  0.714
    'The patient is allergic to X'   vs 'has no X allergy' -> NOVEL   0.333

A contradiction is textually near-identical, so it scored **highest** and was labelled
DUPLICATE — the most confident label available, attached to exactly where it was most wrong.
And the only pair carrying logical negation was a **miss**.

`FactTriples` now compares subject-predicate-object propositions. Same measurement, now:

    capital Paris vs capital Lyon        CONTRADICTION
    boils 100 vs boils 50                CONTRADICTION
    port 8080 vs port 9090               CONTRADICTION
    allergic to X vs not allergic to X   NEGATION
    identical facts                      DUPLICATE
    'is located in' vs 'is in'           DUPLICATE   (was a false CONTRADICTION)

## What is genuinely true in this release

**Article I is enforced as written.** `BrainLoopCompliance` previously computed
`compliant = onnx == 0` while its auditor reported 123 violations and printed all three counts.
It enforced 2. Reading the constitution showed the auditor was wrong in both directions:
it under-enforced wall-clock and unseeded randomness, and over-enforced *seeded* Random, which
Article I explicitly permits in the cognitive layer. The 84 advisory sites are now adjudicated —
**0 blocking, 0 unclassified, 83 permitted** — and unadjudicated sites now BLOCK, so the debt
cannot regrow unnoticed.

One genuine violation was fixed rather than annotated: `InputFilterGuard` decided a rate-limit
window with `Instant.now()`, so the same requests could be permitted or denied depending on
machine load. Its clock is now injected.

**Actuation cannot bypass its gate.** No adapter method is callable except from
`CommandBus.dispatch()`, which calls an adapter only after a permitting verdict. Proven by
mutation, not assertion: 4 guards broken, 4 killed, 0 survived.

**The safety classifier refuses far less ordinary English while catching more harm.** Harmful
missed 9/18 → 5/18; benign falsely refused 5/16 → 1/16.

## Still not true

- **The 8+ independent reviewer gate is NOT satisfied.** No external verdicts exist. Proven
  non-functional here: nine reviewers returned nothing in 77 minutes, and a control task whose
  entire output was the literal string `SMOKE TEST OK` was still `[RUNNING]` after 180 seconds.
- **Contradiction detection does not do paraphrase.** "Paris is the capital of France" and
  "paris france is the capital" extract to different subjects, so they are NOVEL. That is the
  safe direction — a false contradiction accusation would be worse — but paraphrase detection is
  not provided and is not claimed.
- **Two cognitive-loop stages return `miss()` in production** (`TsetlinStage`,
  `BirInferenceStage`), never enabled. They emit explicit `*_SIMULACRUM` trace markers, which is
  why they were left in place: removing them would delete that honesty from the trace.
- **`AdvancedTsetlinMachine` is a research prototype** in `src/experimental`, excluded from the
  production build. It cannot learn.
- **BitNet has no GPU path.** See correction 1.
- **`nvidia-smi` is unusable** until a reboot fixes a driver kernel/userspace mismatch
  (595.91.07 vs 595.99.02). CUDA inference is unaffected because it uses the driver API.
- **LSP diagnostics are unreliable here.** They repeatedly report unresolved symbols in files
  Gradle compiles cleanly. Gradle and on-disk source are authoritative; every LSP claim in this
  release was verified against both before commit.
- **Open cascades:** BitNet quantization drift, HDC capacity binding, contradiction detection
  beyond copular sentences, gzip/Brotli Kolmogorov tiering.

## A note on how this release was produced

Four bugs found in this release were in the tooling that verifies the code, not in the code:

- the auditor's "inactive" sentinel was `Integer.MIN_VALUE` and the test was `depth > sentinel`,
  which is **always true** — every line was exempt and the auditor reported zero findings, i.e.
  it passed;
- an annotation was matched short-form while every call site wrote it fully-qualified, so the
  exemption never fired;
- a mutation harness reported every mutation surviving because `cp` hit a disk quota and the
  tests never ran;
- a mutation written as `false && f(x)` on a two-line expression compiled, changed the file, and
  **still called the classifier** through the `||` below it.

Each produced a confident, specific, entirely fictional result. A gate that silently skips
everything looks exactly like a clean one, and that equivalence is the single most expensive
failure mode in this codebase.