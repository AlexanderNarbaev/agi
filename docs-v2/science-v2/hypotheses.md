# Hypotheses: Status and Evidence

> **Layer:** Scientist | **Last Updated:** 2026-09-20

---

## Legend

- ✅ **ACCEPTED** — Hypothesis confirmed by evidence
- ❌ **REFUTED** — Hypothesis disproven by evidence
- 🔄 **RUNNING** — Hypothesis under investigation
- 🅿️ **PARKED** — Hypothesis deferred to future work

---

## Accepted Hypotheses

### H-001: BIR Achieves Competitive Accuracy on Structured Logic ✅

**Claim:** BIR can match rule-based systems on structured logic puzzles.

**Evidence:** Logic benchmark shows BIR achieves 75% accuracy, matching heuristic baseline.

**Source:** [LogicBenchmark.java](../../matrix-core/src/main/java/io/matrix/federation/liquid/benchmark/LogicBenchmark.java)

### H-002: HDC Shows Superior Sample Efficiency ✅

**Claim:** HDC can learn effectively from fewer than 100 examples.

**Evidence:** HDC achieves 89.9% AUC with only 50 training examples.

**Source:** [SampleEfficiencyBenchmark.java](../../matrix-core/src/main/java/io/matrix/federation/liquid/benchmark/SampleEfficiencyBenchmark.java)

### H-003: BIR is Massively More Energy Efficient ✅

**Claim:** CPU-based BIR inference is orders of magnitude more efficient than GPU-based LLM.

**Evidence:** BIR is 33,000x more energy-efficient than LLM-GPU.

**Source:** [EnergyBenchmark.java](../../matrix-core/src/main/java/io/matrix/federation/liquid/benchmark/EnergyBenchmark.java)

### H-004: Federation Survives 50% Node Failures ✅

**Claim:** The federation maintains consensus even with 50% node failures.

**Evidence:** Federation simulation shows 100% consensus with 50% kills.

**Source:** [FederationSimulation.java](../../matrix-core/src/main/java/io/matrix/federation/liquid/simulation/FederationSimulation.java)

### H-005: Sleep Improves Accuracy and Reduces Memory ✅

**Claim:** Sleep consolidation improves accuracy while reducing memory footprint.

**Evidence:** Sleep improves accuracy by 19.4% and reduces memory by 22%.

**Source:** [SleepConsolidationStudy.java](../../matrix-core/src/main/java/io/matrix/federation/liquid/simulation/SleepConsolidationStudy.java)

### H-006: Non-Linear Modulator Interactions Create Realistic Behavior ✅

**Claim:** Synergy/antagonism between modulators creates emergent mood states.

**Evidence:** Stress cascade test shows cortisol suppressing dopamine non-linearly.

**Source:** [StressCascadeSimulationTest.java](../../matrix-core/src/test/java/io/matrix/federation/liquid/biochemistry/StressCascadeSimulationTest.java)

### H-007: Stigmergy Enables Emergent Cluster Formation ✅

**Claim:** Digital pheromones enable spontaneous cluster formation without central coordination.

**Evidence:** Cluster formation test shows10 nodes clustering around a hot topic.

**Source:** [StigmergyProtocolTest.java](../../matrix-core/src/test/java/io/matrix/federation/liquid/biochemistry/StigmergyProtocolTest.java)

---

## Refuted Hypotheses

### H-035: EBL Outperforms BIR on XOR Tasks ❌

**Claim:** Explanation-Based Learning (EBL) outperforms BIR on XOR tasks.

**Evidence:** EBL is approximately 17x slower by examples on XOR under canonical trainer.

**Status:** REFUTED-toy (pinned)

**Source:** [archive/failures.md](../archive/failures.md)

---

## Running Hypotheses

### H-008: Causal Planning Improves Decision Quality 🔄

**Claim:** MCTS planner using CausalGraph produces better decisions than heuristic rollout.

**Status:** Planned for Phase 3.3 (W671-W700)

### H-009: Federated Sleep Accelerates Convergence 🔄

**Claim:** Synchronized sleep cycles across nodes accelerates collective learning.

**Status:** Planned for Phase 3.5 (W691-W700)

### H-010: Stigmergy Scales to 1000+ Nodes 🔄

**Claim:** Pheromone-based coordination remains effective at 1000+ node scale.

**Status:** Under investigation

---

## Parked Hypotheses

### H-011: BIR Generalizes to k≥8 Variables 🅿️

**Claim:** BIR can handle problems with 8 or more input variables.

**Status:** Parked with documented attempts (Waves 16-20)

---

## Summary

| Status | Count |
|--------|-------|
| ✅ ACCEPTED | 7 |
| ❌ REFUTED | 1 |
| 🔄 RUNNING | 3 |
| 🅿️ PARKED | 1 |
| **Total** | **12** |

---

## Adding New Hypotheses

Use the hypothesis template:

```markdown
### H-XXX: [Title] 🔄

**Claim:** [What we're testing]

**Evidence:** [What would prove/disprove it]

**Status:** [Current status]

**Source:** [Link to test/benchmark]
```
