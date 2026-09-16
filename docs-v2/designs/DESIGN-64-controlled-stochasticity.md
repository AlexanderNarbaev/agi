# DESIGN-64 — Controlled Stochasticity & Error-Driven Learning

**Date:** 2026-09-16
**Status:** Design specification, W93 implementation pending
**Capability Level:** L0-L7 cross-cutting (architectural primitive)
**Cross-references:** CONSTITUTION.md (Articles I, IV), AGENTS.md (META-R1-R5), DESIGN-58-capability-levels-roadmap.md

## 1. Motivation — The Tension

A canonical observation: a person answering the same question twice may
produce **different answers** depending on mood, attention, hormonal state,
social context, prior stimuli, time-of-day. This is not a bug — it is the
mechanism by which humans explore hypothesis space, detect their own errors,
and update beliefs through interleaved experience.

If MATRIX's runtime path is fully deterministic (CONSTITUTION I), every
question gets the same answer. There is no exploration. There is no error
detection. There is no learning.

If MATRIX's runtime path is fully stochastic, we lose reproducibility
(CONSTITUTION II) and the user's "excludes errors" requirement fails.

The resolution: **stratified stochasticity**. Different parts of the
system operate under different randomness regimes, and the regime is
explicitly tagged in code:

| Regime | Where | Example | Default |
|--------|-------|---------|---------|
| **Pure deterministic** | Numerical kernels, integration metrics, byte-for-byte inference | `phiBinary(traj, N)` returns same value for same input | ✓ required |
| **Reproducible seeded** | Training loops, monte-carlo simulators, A/B tests | `Random(seed=42)` — same seed → same output | ✓ default for learning |
| **Exploratory bounded** | Hypothesis generation, brainstorming, alternative-paths exploration | `Random(seed=env)` where env captures context (time, prior state) | optional, behind flag |
| **Adversarial unbounded** | Red-teaming, robustness testing, evolutionary search | Truly random `Random()` per call | experimental, gated |

## 2. CONSTITUTION I Revision

CONSTITUTION I currently states:
> Runtime paths must be pure functions with no Random and no wall-clock.

This is **correct for the numerical substrate** (integration metrics, weight
inference, model evaluation) but **too restrictive for cognition**.
A real cognitive system needs:

1. **Deterministic substrate** — the math is reproducible.
2. **Seeded exploration** — the same "cognitive episode" with the same
   starting state and same inputs produces the same exploration sequence.
3. **Error-driven learning** — failed attempts must lead to modified
   future attempts.

**Proposed revision** (to be voted on in subsequent wave):

> Article I (Determinism & Stochasticity):
> Runtime paths in the **numerical substrate** (integration metrics, weight
> inference, deterministic kernels) MUST be pure functions with no Random
> and no wall-clock. Runtime paths in the **cognitive layer** (hypothesis
> generation, alternative-path exploration, learning-loop updates) MAY
> use seeded Random where the seed is derived from a deterministic state
> context (episode ID, prior trajectory hash). The seed derivation itself
> MUST be a pure function. Unbounded Random (no seed) is permitted only in
> **experimental exploration mode** behind a build flag.

## 3. Where Each Regime Applies in MATRIX

### Pure deterministic (CONSTITUTION I, no revision)
- `IntegrationMetrics.phiBinary` / `phiR` / `phiF` / `cN` / `phiLinGauss`
- `PhiId.bivariateGaussian` / `trivariateGaussian` / `system`
- `BitNetBlock.forward` / `BitNetModel.generate`
- `HdcBinding` / `HdcEncoding` operations
- `PredictiveCoder.computeError`
- `TicklingDetector.detect`
- `SelfModel.modelStep` (when observation is the only input)

### Reproducible seeded (CONSTITUTION I expanded)
- `BatchTrainer` — same seed → same parameter updates
- `BitLinearDreamer` — dream sampling with seeded RNG
- `TwoStageConsolidator.consolidate` — replay selection
- `HdcBrain.learn` — stochastic binding (already uses seeded Random)
- `ConsciousBrain.cycle` — currently no randomness, but future creativity
  primitives should use the deterministic episode seed

### Exploratory bounded (NEW)
- `HypothesisGenerator` — generates alternative framings, seeded by
  episode context hash. Not yet implemented; proposed for W93.
- `CreativeSearch.explore(neighborhood, k)` — samples k alternative next
  states within a deterministically-defined neighborhood.
- `MetacognitionMonitor.exploreFailureModes` — when the brain makes an
  error, this generates k candidate explanations seeded by failure signature.

### Adversarial unbounded (NEW, gated)
- `RedTeamTest` — generates adversarial inputs without seed for testing
  robustness. Behind `-Pexperimental=true` Gradle flag.
- `EvolutionarySearch.mutate` — random bit flips, no seed.

## 4. The "Error-Driven Learning" Mechanism

A real learner **fails** in specific, identifiable ways. The proposed
mechanism for W93:

```java
/**
 * W93 — CognitiveError (concrete failure record).
 *
 * <p>Captures one specific way the brain failed at a task. The error
 * signature is deterministic (depends on inputs + state), so re-running
 * with same state produces same error record.
 *
 * <p>NOT a generic exception — it is a structured signal that feeds back
 * into learning updates.
 */
public record CognitiveError(
        long cycleCount,
        ErrorKind kind,           // PREDICTION_ERROR, EXPLORATION_DIVERGENCE, ...
        double[] stateSnapshot,   // float[] snapshot of brain state at failure
        String description) {
    public enum ErrorKind {
        PREDICTION_ERROR,         // observation didn't match prediction
        EXPLORATION_DIVERGENCE,   // alternative paths diverged > threshold
        INTEGRATION_VIOLATION,    // Φ dropped below floor
        MEMORY_RETRIEVAL_MISS,    // couldn't recall expected context
        ACTION_INEFFECTIVE        // action didn't reduce surprise
    }
}
```

The `CognitiveErrorStream` accumulates errors deterministically (with the
episode seed). On cycle N, if errors[0..N] cross a threshold, the
`ErrorDrivenLearner` updates HDC bindings with the negative feedback.

This is **not** Random in the runtime path — it is structural error
analysis. Random comes in **only** when the brain needs to generate
candidate alternative actions, which is bounded exploration.

## 5. CONSTITUTION VI Alignment

This design preserves CONSTITUTION VI (integration metrics are measurement
substrates, not consciousness claims). The variability mechanism is about
**action selection and learning**, not about consciousness.

## 6. Implementation Plan (W93)

1. Create `io.matrix.cognitive.CognitiveError` record + `ErrorKind` enum
2. Create `io.matrix.cognitive.CognitiveErrorStream` (deterministic
   bounded ring buffer, indexed by episode ID)
3. Create `io.matrix.cognitive.ExploratoryActionSampler` (seeded Random
   using episode hash as seed; selects among candidate actions)
4. Wire `ConsciousBrain.cycle()` to record CognitiveError when surprise
   exceeds dynamic threshold
5. Add test `W93ControlledStochasticityTest` verifying:
   - Same seed → same action sequence
   - Different seed → different action sequence
   - Error stream accumulates deterministically
   - W87-W92 tests still pass (no regression)

## 7. Tradeoffs

- **Pro:** Real exploration, real error-driven learning, alignment with
  human cognition.
- **Pro:** Reproducibility preserved where it matters (numerical substrate).
- **Pro:** Explicit regime tagging makes the stochasticity surface clear.
- **Con:** More moving parts than pure determinism. Need discipline to
  keep CognitiveError records bounded.
- **Con:** CONSTITUTION I needs formal revision. Risk of opening the flood
  gates to unbounded randomness if not careful.
