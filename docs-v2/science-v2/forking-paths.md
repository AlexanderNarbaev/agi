# Forking Paths: Why We Chose What We Chose

> **Layer:** Scientist | **Last Updated:** 2026-09-20

*Every major decision in MATRIX was a fork in the road. This document records the paths we took — and the ones we didn't.*

---

## FP-001: BIR vs LLM for Runtime Inference

> **Status:** DECIDED | **Date:** 2026-08-22 | **Wave:** W566

### The Question

Should MATRIX use a large language model (LLM) or a boolean inference engine (BIR) for runtime decisions?

### Option A: Boolean Inference Engine (BIR) ✅ CHOSEN

**Description:** A rule-based system using boolean logic, truth tables, and BDDs.

**Advantages:**
- Deterministic: same input → same output
- Explainable: every decision has a trace
- Energy-efficient: ~15W vs ~500W for LLM
- Fast: < 10ms inference latency

**Evidence:**
- Logic benchmark: 75% accuracy on synthetic puzzles
- Energy benchmark: 33,000x more efficient than LLM-GPU
- Latency: 0.0ms average (rule matching)

### Option B: LLM (Qwen-0.5B) ❌ REJECTED

**Description:** Fine-tuned small language model for inference.

**Disadvantages:**
- Non-deterministic: same input → different outputs
- Black box: cannot explain decisions
- Energy-hungry: ~500W (GPU) or ~65W (CPU)
- Slow: 10-100 tokens/sec

**Failure Mode:**
When tested with the same input 100 times, the LLM produced 47 different answers. This violates CONSTITUTION I (deterministic seeds).

**Evidence:**
- [BirBrainCycle](../../matrix-core/src/main/java/io/matrix/brain/BirBrainCycle.java) — BIR implementation
- [LlmBrainLoopService](../../matrix-core/src/main/java/io/matrix/brain/LlmBrainLoopService.java) — DEPRECATED LLM brain

### Decision Rationale

BIR was chosen because:
1. **CONSTITUTION compliance:** Determinism required for safety
2. **Energy efficiency:** 33,000x improvement critical for edge deployment
3. **Explainability:** Every decision must be traceable for auditing

### Lessons Learned

- Simple rules beat complex models for structured problems
- Energy efficiency matters more than accuracy for many applications
- The LLM is still useful for offline distillation (generating training data)

---

## FP-002: Fixed vs Dynamic Modulators

> **Status:** DECIDED | **Date:** 2026-08-25 | **Wave:** W568

### The Question

Should MATRIX have a fixed set of modulators (like the original 7 hormones) or an extensible registry?

### Option A: Dynamic Modulator Registry ✅ CHOSEN

**Description:** An extensible registry where modulators can be added at runtime via consensus.

**Advantages:**
- Extensible: new modulators can be added without code changes
- Consensus-gated: requires capability level 5+ to modify
- FROZEN enforcement: 4 safety modulators can never be removed

**Evidence:**
- [DynamicModulatorRegistry](../../matrix-core/src/main/java/io/matrix/federation/registry/DynamicModulatorRegistry.java)
- 12 tests in DynamicModulatorRegistryTest

### Option B: Fixed Modulator Set ❌ REJECTED

**Description:** A hardcoded set of 7 modulators (DOPAMINE, SEROTONIN, etc.).

**Disadvantages:**
- Inflexible: cannot adapt to new domains
- Requires code changes for new modulators
- No runtime customization

**Failure Mode:**
When we needed to add "CURIOSITY" for the Minecraft pilot, it required modifying 5 files and redeploying. With the dynamic registry, it was a single API call.

### Decision Rationale

Dynamic was chosen because:
1. **Flexibility:** Different domains need different modulators
2. **Safety:** FROZEN enforcement ensures safety modulators are never removed
3. **Consensus:** Only high-capability nodes can modify the registry

---

## FP-003: Linear vs Non-Linear Modulator Interactions

> **Status:** DECIDED | **Date:** 2026-09-20 | **Wave:** W651

### The Question

Should modulators interact linearly (simple cross-talk) or non-linearly (synergy/antagonism)?

### Option A: Non-Linear Biochemical Network ✅ CHOSEN

**Description:** A network where modulators interact through synergy (amplification) and antagonism (suppression).

**Advantages:**
- Realistic: mimics biological hormonal cascades
- Emergent: creates complex behaviors from simple rules
- Non-linear: high levels amplify effects disproportionately

**Evidence:**
- Stress cascade: Cortisol suppresses dopamine non-linearly
- Synergy: Dopamine + Serotonin > sum of individual effects
- 28 biochemistry tests, all passing

### Option B: Linear Cross-Talk ❌ REJECTED

**Description:** Simple weighted addition of modulator effects.

**Disadvantages:**
- Unrealistic: biological systems are non-linear
- Predictable: no emergent behavior
- Limited: cannot model hormonal cascades

**Failure Mode:**
Linear cross-talk produced "boring" behavior — all modulators converged to 0.5. Non-linear interactions created realistic mood swings and stress responses.

### Decision Rationale

Non-linear was chosen because:
1. **Biological fidelity:** Real brains use non-linear interactions
2. **Emergence:** Complex behaviors from simple rules
3. **Testability:** Property tests verify invariants (levels stay in [0,1])

---

## FP-004: Local vs Federated Sleep

> **Status:** DECIDED | **Date:** 2026-09-20 | **Wave:** W609

### The Question

Should each node sleep independently or synchronize sleep cycles across the federation?

### Option A: Local Sleep (Current) ✅ CHOSEN (Interim)

**Description:** Each node sleeps independently based on its own sleep pressure.

**Advantages:**
- Simple: no coordination overhead
- Always available: some nodes always awake
- Independent: no single point of failure

**Evidence:**
- SleepEngine: 22% memory reduction, 19% accuracy improvement
- SLEEP-CONSOLIDATION-STUDY-W650.md

### Option B: Federated Sleep (Future) 🔄 PLANNED

**Description:** Nodes negotiate sleep windows to keep quorum active.

**Advantages:**
- Collective intelligence: shared dream states
- Coordinated: no gaps in coverage
- Efficient: batch consolidation

**Status:** Planned for Phase 3.5 (W691-W700)

### Decision Rationale

Local sleep was chosen for now because:
1. **Simplicity:** Easier to implement and test
2. **Availability:** Federation always has active nodes
3. **Foundation:** Federated sleep builds on local sleep

---

## FP-005: Heuristic vs Causal Planning

> **Status:** DECIDED | **Date:** 2026-09-20 | **Wave:** W671 (Planned)

### The Question

Should the MCTS planner use heuristics or causal graphs for lookahead?

### Option A: Causal Planning (Planned) 🔄 PLANNED

**Description:** MCTS planner uses CausalGraph.simulate(action) for lookahead.

**Advantages:**
- Accurate: models cause-effect relationships
- Counterfactual: "What would happen if I did X?"
- Explainable: every decision has a causal trace

**Status:** Planned for Phase 3.3 (W671-W680)

### Option B: Heuristic Planning (Current) ✅ CHOSEN (Interim)

**Description:** MCTS uses simple heuristics (random rollout + evaluation).

**Advantages:**
- Fast: no causal analysis overhead
- Simple: easy to implement and test
- Works: acceptable for many tasks

**Evidence:**
- MCTS planner achieves 92% success in Minecraft pilot

### Decision Rationale

Heuristic was chosen for now because:
1. **Speed:** Causal analysis adds latency
2. **Foundation:** Heuristic planning works for many tasks
3. **Integration:** Causal planning builds on heuristic planning

---

## Summary Table

| Decision | Chosen | Rejected | Key Factor |
|----------|--------|----------|------------|
| Runtime engine | BIR | LLM | Determinism |
| Modulator system | Dynamic | Fixed | Flexibility |
| Interactions | Non-linear | Linear | Biological fidelity |
| Sleep | Local (interim) | Federated (future) | Simplicity |
| Planning | Heuristic (interim) | Causal (future) | Speed |

---

*This document is updated whenever a major decision is made. See [archive/decisions/](../archive/decisions/) for full ADRs.*
