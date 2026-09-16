# W95 — Cross-Disciplinary Research Wave R-B (Cybernetic / Constructivist)

**Date:** 2026-09-16
**Status:** Synthesis report (Wave 95 of deep-research series)
**Series:** W91 (R-A: Tononi/Mediano/Barrett-Seth), W92 (R-A extension), W93 (DESIGN-64 Controlled Stochasticity), W94 (Cognitive Error primitives), **W95 (R-B)**.
**Cross-references:** META-R1 doctrine (AGENTS.md), DESIGN-64, DESIGN-58 capability-levels roadmap.

## 1. Motivation

The R-A wave (W87-W92) covered **information-theoretic** integration
metrics: Tononi's Φ_binary, Mediano's ΦR/PhiID, Barrett-Seth's Φ_linGauss.
These are mathematical measures of statistical structure.

The R-B wave complements them with **cybernetic/constructivist** perspectives:
how integrated information is **built, regulated, and used** by living
systems. These are mechanistic accounts of what the integration measures
*do* and how they emerge from feedback loops.

The distinction matters because:
1. Φ_binary = 0 doesn't mean the system is "dead" — it may be actively
   *regulating* its integration (Ashby homeostasis).
2. ΦR > 0 doesn't mean consciousness — it may be a *necessary side effect*
   of an error-correction mechanism (Bernstein levels of construction).
3. A real cognitive system is **constructed** through error-driven learning
   (Ashby, Minsky) — not just measured.

## 2. The Five Schools of Cybernetic/Constructivist Thought

### 2.1 P. K. Anokhin (1898-1974) — Functional Systems Theory

**Core claim**: Every goal-directed behavior is implemented by a
**functional system** = a closed loop of afferent signals, central
processing, efferent commands, and reverse-afferent feedback.

```
[ Receptor ] → [ Afferent Synthesis ] → [ Decision Block ] → [ Effector ]
       ↑                                                            ↓
       └──────────── [ Reverse Afferent (Result Feedback ) ] ──────┘
```

**For MATRIX**: This is the architecture of ConsciousBrain.cycle():
- observation (receptor) → appendTrajectory (afferent synthesis)
- computeIntegrationMetrics (decision block) → extended metrics
- recordCognitiveErrors (reverse-afferent: detects result mismatch)

Anokhin's insight: the **reverse-afferent signal** is the integration
metric. ΦR serves as the comparator between expected and actual state.

### 2.2 N. A. Bernstein (1896-1966) — Levels of Construction

**Core claim**: Movement (and by extension, cognition) is organized at
**multiple levels of construction**, each with its own degrees of freedom.
Coordination is not reductionist (one level explains all) but **multi-level**
(top-down constraints + bottom-up emergence).

```
Level N: Goals, plans, abstractions           (ConsciousBrain extended metrics)
Level N-1: Sensorimotor coordination          (ConsciousBrain.cycle)
Level N-2: Reflex arcs                         (SelfModel, PredictiveCoder)
...
Level 0: Muscle / neuron hardware             (BitLinear, BitNetBlock)
```

**For MATRIX**: L0-L7 capability levels (DESIGN-58) are exactly Bernstein's
hierarchy. Each level has its own metric and its own optimizer. The
**integration** at the top level is constrained by, but not reducible to,
the bottom level.

The H-079 noise-floor benchmark showed that **at the discrete-state level
(N=8 bits)** Φ_binary collapses to 0 for deterministic patterns. This is
the "Bernstein's problem": you cannot recover the upper-level coordination
from the lower-level noise floor. Hence W89's Φ_linGauss — moving to
continuous trajectory to recover integration signal at the right level.

### 2.3 W. R. Ashby (1903-1972) — Homeostasis & Ultrastability

**Core claim**: A cybernetic system maintains **essential variables** within
survival bounds through **feedback**. The brain's primary function is
**regulation**, not computation. When essential variables drift out of
bounds, the system enters a **disruption phase** until it finds a new
**stable configuration** (ultrastability).

```
        ┌───── stable ─────┐
        │                  │
        ↓                  │
[Sensors] → [Comparator] → [Effector] → [Environment]
        ↑                  │
        └──── unstable ────┘  (when essential variables out of bounds)
```

**For MATRIX**: W94's CognitiveErrorStream is the comparator output. When
`surprise > SURPRISE_THRESHOLD` or `Φ < INTEGRATION_FLOOR`, an error is
recorded. Errors exceeding threshold trigger a **disruption phase** — the
brain tries alternative actions via `ExploratoryActionSampler` (W93).

**Critical insight**: consciousness is NOT required for integration. A
thermostat is integrated. The distinction is the **richness** of the
essential variables. Conscious systems track many variables (state of
self, others, environment, time, mood, etc.). The integration metric
should scale with this richness, not just bottom-up statistical structure.

### 2.4 M. Minsky (1927-2016) — Society of Mind

**Core claim**: Intelligence emerges from the **interaction of many
small agents**, each incompetent at the global task. There is no central
homunculus. The "self" is an emergent pattern of inter-agent
communication.

```
[Perception Agents] → [Memory Agents] → [Reasoning Agents] → [Action Agents]
       ↑                  ↓                   ↑                ↓
       └────── [Emotion / Value / Critique Agents ] ──────┘
```

**For MATRIX**: ConsciousBrain already follows this pattern:
- HdcBrain (perception), HdcMemoryStore (memory), TwoStageConsolidator
  (reasoning), WuWeiPolicy (action)
- The "self" emerges from CycleReport aggregation, not from any single agent.

**Critical insight**: integration metrics should be computed at the
**interaction level**, not just within individual agents. The Φ_binary
that MATRIX computes is a within-brain measure. A society-of-mind
extension would measure Φ across **agent boundaries** — the integration
between HdcBrain, SelfModel, WuWeiPolicy as separate sub-systems.

This is a future wave (W96+): inter-agent Φ across the brain's
functional decomposition.

### 2.5 H. A. Simon (1916-2001) — Bounded Rationality & Hierarchical Systems

**Core claim**: Complex systems are **hierarchies of nearly-decomposable
sub-systems**. The optimal coordination strategy is **bounded rationality**:
each sub-system optimizes locally with bounded communication, and global
intelligence emerges from the interaction.

**For MATRIX**: L0-L7 capability levels (DESIGN-58) embody Simon's
hierarchy. Each level has bounded interfaces:
- L0-L2 (algorithm primitives): BooleanLogic, HdcEncoding, HdcBinding.
- L3-L4 (reasoning): Memory + retrieval.
- L5-L6 (consolidation): TwoStageConsolidator.
- L7 (conscious integration): ConsciousBrain.

The L7 integration metric is **bounded**: it integrates over a fixed
8-dimensional projection of the 1024-dim observation space. This is
explicitly Simon's bounded rationality — we don't try to integrate
everything, just what's relevant for the current episode.

## 3. Cross-School Synthesis: What Each School Adds to Φ

| School | Static Φ misses | Need to add |
|--------|-----------------|-------------|
| Anokhin | Result-feedback loop | CognitiveErrorStream records the loop's output |
| Bernstein | Top-down constraints vs bottom-up emergence | Cross-level Φ (current = single-level) |
| Ashby | Essential variables & ultrastability | Threshold-based error recording (W94) |
| Minsky | Society of agents | Inter-agent Φ (future W96+) |
| Simon | Bounded rationality | Already in DESIGN-58 capability-level roadmap |

## 4. Mathematical Implications

The R-B wave suggests three concrete extensions to the integration-metric
suite (W87-W92):

1. **Anokhin-result-feedback Φ**: Φ measured at the level of
   `CognitiveErrorStream.snapshotHash()` changes — i.e., derivative of
   integration with respect to error. Quantifies "how fast the brain
   converges to a stable state after a perturbation".

2. **Bernstein-cross-level Φ**: Φ between L_k and L_{k+1} for various k.
   Quantifies "how well each level coordinates with the next".

3. **Ashby-stability Φ**: Variance of Φ over a sliding window of N cycles.
   Quantifies "how stable integration is". Low variance = ultrastable.

These are all derivable from existing primitives (Φ_binary, ΦR, C_N,
CogErr stream) without new mathematical machinery.

## 5. CONSTITUTION Compliance

This R-B synthesis reinforces CONSTITUTION I (determinism in numerical
substrate) while supporting DESIGN-64's proposal for **bounded
stochasticity in the cognitive layer**. Anokhin's reverse-afferent loop
is deterministic in principle (same state + same observation → same error
record). Ashby's ultrastability phase may use **bounded exploration** via
ExploratoryActionSampler (W93) — but the exploration seed is itself a
deterministic function of state.

## 6. Hypotheses Introduced (H-085..H-088)

| H | Утверждение | Школа | Status |
|---|---|---|---|
| **H-085** | CognitiveErrorStream.snapshotHash change-rate correlates with "stability" (Ashby) — peaks during ultrastable phase entry | Ashby | running |
| **H-086** | Cross-level Φ (L6 ↔ L7 integration) predicts performance on integration-heavy tasks better than within-level Φ | Bernstein | running |
| **H-087** | Inter-agent Φ across HdcBrain / SelfModel / WuWeiPolicy sub-systems becomes non-zero during action selection, near-zero during rest | Minsky | running |
| **H-088** | Anokhin result-feedback (cognitive error rate) mediates the conscious ↔ unconscious threshold: high error rate → explicit action; low → implicit pattern completion | Anokhin | running |

## 7. Files in this Wave

- `docs-v2/research/W95-CYBERNETIC-RESEARCH-REPORT.md` (this file)
- Future: `io.matrix.cybernetic.StabilityPhi`, `CrossLevelPhi`,
  `InterAgentPhi` classes (W96+ implementation)

## 8. W96+ Future Work

1. Implement `StabilityPhi.java` — variance of Φ over sliding window
2. Implement `CrossLevelPhi.java` — Bernstein's inter-level integration
3. Implement `InterAgentPhi.java` — Minsky's inter-agent decomposition
4. Run META-R1 wave R-C (Soviet/Asian) — Glushkov, Nyaya, Wu Wenjun
5. Run META-R1 wave R-D (early learning neuroscience) — Spelke, Spitz, Kauffman
6. Run META-R1 wave R-E (physical substrates) — memristors, neuromorphic
7. Run META-R1 wave R-F (mathematics of creativity) — Kolmogorov, L-systems
