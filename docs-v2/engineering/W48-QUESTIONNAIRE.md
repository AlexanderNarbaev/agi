# W48 — THE END-OF-WAVE QUESTIONNAIRE

**Instrument:** the harness's structured questionnaire tool (`ask_user_question`) — the real
one, not a list of questions printed in a document. **Date:** 2026-11-06 · **Wave:** W48
**The answers are persisted** in `docs-v2/governance/DECISION-REGISTER.md` (the new
decisions), `docs-v2/governance/OPEN-QUESTIONS.md` (the closed questions) and
`docs-v2/engineering/W48-STATUS.md`.

**The answers, 2026-11-06:**

| the question | the answer | the decision |
|---|---|---|
| Q-W48-01 — the autonomy impulse consumer | **option A** — a consumer that creates and records typed internal proposals inside Chain B, implemented in the NEXT wave | OD-045 |
| Q-W48-02 — the native-image build | measure the resource cost and fix the build in the next wave; the capability stays BLOCKED until a build succeeds | OD-046 |
| Q-W48-03 — the role of the GPU | an **optional accelerator**, not part of the default serving path; the silent CPU fallback stays visible in the registry | OD-047 |

**What is NOT asked here:** anything OD-035…OD-044 already answers, and anything that does
not need the owner. Three questions, each one with a decision behind it that the wave
could not make on its own.

---

## Q-W48-01 — The autonomy impulse consumer

- **the question:** `ImpulseScheduler.fire()` accepts an impulse and returns a
  `FireRecord` whose `FIRED` outcome means *accepted*. The only consumer of that record in
  the tree is a log line, and no proposal, queue or consumer class exists under any name.
  Should MATRIX get a consumer that turns an accepted impulse into a **bounded internal
  proposal**, and if so, with what scope?
- **why it matters:** an acceptance that looks like a completion is the difference
  between "the curiosity cycle is an autonomous work system" and "the curiosity cycle
  decides whether it may want to do something". Only the owner may move that boundary.
- **the current evidence:** `ImpulseScheduler.java:22-41` (the outcomes), `AutonomyEngine.java:140-146`
  (the record is logged and dropped), `grep -rln "ImpulseConsumer\|ProposalQueue\|TaskProposal" --include=*.java .`
  → no match, and exactly two call sites of `scheduler.fire(...)` in production code.
- **the recommended option:** **option A** — a consumer that creates and records typed
  internal proposals inside Chain B, with stable ids, provenance, an accounted budget, a
  bounded queue with explicit backpressure, expiry and deduplication, a fresh FROZEN
  check at execution time, and an observable terminal outcome for every proposal. It
  sends nothing, executes nothing and touches nothing outside Chain B.
- **the alternatives:** **option B** — the same consumer, but with the scope reduced to
  "record only": no proposal is ever accepted for review, and the terminal outcomes are
  `EXPIRED`, `DUPLICATE` and the two refusals. **option C** — no consumer at all: the
  scheduler stays the whole autonomy story, and the capability is described as "decides,
  does not act".
- **the consequences:** option A gives the autonomy layer a place to put the wanting,
  and every guarantee is testable without a sandbox; option B gives it a place and a
  visible queue with nothing behind it; option C leaves the ambiguity in place but says
  so in the registry.
- **the risks:** a consumer that later grows an executor. The scope in
  `docs-v2/engineering/ADR-W48-AUTONOMY-IMPULSE-CONSUMER.md` §5 forbids it explicitly,
  and the FROZEN gate is re-checked at execution time so a later change cannot inherit
  an earlier permission.
- **reversibility:** high — a proposal store and a consumer are removable; nothing
  outside Chain B ever depends on them.
- **does it block implementation:** **yes** — the consumer is not implemented until the
  owner answers. The ADR is PROPOSED.
- **the answer (2026-11-06):** **option A** — the proposal-creating consumer of
  `docs-v2/engineering/ADR-W48-AUTONOMY-IMPULSE-CONSUMER.md` §5, implemented in the
  NEXT wave. Recorded as OD-045. **Nothing is implemented in W48.**

---

## Q-W48-02 — The native-image build

- **the question:** the 23 native-binary tests are now their own scope, and the scope
  refuses to run because the executable does not exist. The only workflow that builds it
  (`.github/workflows/native.yml`) triggers on `main` and its **last nine runs all died**
  with `OutOfMemoryError: Java heap space` — after the toolchain installed successfully.
  Should the native path get a measured resource budget and a working build in the next
  wave, or is the native-image capability formally out of scope for MATRIX?
- **why it matters:** the capability is currently BLOCKED and is reported as BLOCKED.
  Leaving it BLOCKED without a decision means the gap is permanent by accident.
- **the current evidence:** `command -v native-image` → nothing on this host;
  `.github/workflows/native.yml` → `on: push: branches: [main]`, nine runs, nine
  failures; the OOM is in the build, not in the setup.
- **the recommended option:** give the native build a **measured** budget in the next
  wave — the heap and the runner size it actually needs, recorded — and keep the
  capability BLOCKED until a build succeeds. The W48 directive forbids creating a native
  build requirement without measuring its resource cost.
- **the alternatives:** (a) drop the native-image capability from MATRIX's scope and
  say so in the registry; (b) keep it BLOCKED and revisit it later without a budget.
- **the consequences:** a native build costs runner minutes and memory; the nine
  failures show that the current configuration does not fit.
- **the risks:** a native build that is "fixed" without a measurement becomes an
  unbounded resource requirement — the failure mode the directive names.
- **reversibility:** high.
- **does it block implementation:** no — W48 records the block exactly as the directive
  requires. This decides the NEXT wave.
- **the answer (2026-11-06):** measure the resource cost and fix the build in the next
  wave; the capability stays **BLOCKED** until a build succeeds. Recorded as OD-046.

---

## Q-W48-03 — Is the GPU in scope for MATRIX at all?

- **the question:** the committed benchmark measures the CUDA path for real — the
  execution profile names `CUDAExecutionProvider` — and the serving path is **12.06×–12.69×**
  faster on the GPU. But the GPU only executes when `LD_LIBRARY_PATH` points at the
  right `libcudart`, and MATRIX's Java path still catches and falls back to the CPU. Is
  the GPU a capability MATRIX should claim, or an accelerator that the deployment may use
  when it is configured?
- **why it matters:** the registry's `W46-GPU` row has carried the note "the owner must
  also decide whether the GPU is in scope for MATRIX at all" since W46. The measurement
  now exists; the decision does not.
- **the current evidence:** `docs-v2/engineering/W48-GPU-BENCHMARK.md` — 12.06× / 11.68× /
  11.47× over three runs on the serving path, 23.73× on the raw compute path, 0.24× (the
  GPU is 4× **slower**) on a single-point ONNX call; the CUDA execution is positively
  verified; the historical 12.6× digit does not reproduce and stays historical.
- **the recommended option:** describe the GPU as an **optional accelerator** — measured,
  reproducible, and NOT part of the default serving path — and keep the silent CPU
  fallback visible in the registry rather than in a catch block.
- **the alternatives:** (a) make the GPU a first-class capability, which obliges the
  deployment to provide the CUDA libraries and to fail loudly when they are absent;
  (b) keep the row as it is today and decide nothing.
- **the consequences:** (a) changes a serving path and its failure behaviour; the
  recommended option changes a description, not a behaviour.
- **the risks:** claiming a GPU capability that a deployment cannot actually serve.
- **reversibility:** high for the recommended option, medium for (a).
- **does it block implementation:** no — W48 records the measurement and the honest
  status. This decides the next wave.
- **the answer (2026-11-06):** the GPU is an **optional accelerator**, not part of the
  default serving path; the silent CPU fallback stays visible in the registry rather
  than in a catch block. Recorded as OD-047.
