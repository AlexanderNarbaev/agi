# Open Problems — MATRIX Research Directions

This document lists open research problems suitable for:
- PhD dissertations and Master's theses
- Academic publications (ArXiv, conferences, journals)
- Independent research contributions
- Grant applications (NLNet, EU Horizon, Mozilla)

Each problem is rated by difficulty and estimated effort.

---

## Category 1: Formal Verification

### OP1. Convergence of Genetic Algorithm for MPDT Networks
**Difficulty:** Hard | **Effort:** 10–20 weeks

Prove (or disprove) that the genetic algorithm described in L5 converges to 
a global optimum for a given fitness function. Characterize the fitness landscape 
for MPDT truth tables. Is the landscape deceptive? Under what conditions does 
premature convergence occur?

**Skills needed:** Evolutionary computation theory, Markov chains, fitness landscape analysis.

### OP2. Expressive Power of MPDT Networks
**Difficulty:** Medium | **Effort:** 8–12 weeks

Characterize the class of Boolean functions computable by a network of `n` MPDT 
neurons with `k` inputs each. Compare with:
- Single hidden layer neural networks (universal approximation)
- Decision tree ensembles (Random Forest, XGBoost)
- Boolean circuits (NC, AC hierarchies)

**Skills needed:** Computational complexity, Boolean function theory.

### OP3. Formal Verification of FROZEN Ethical Filter
**Difficulty:** Hard | **Effort:** 12–20 weeks

Prove that the Ethical Filter (L7) cannot be bypassed by any sequence of 
mutations within the genetic algorithm. Model the filter in TLA+ or Coq. 
Prove invariants: "No neuron can learn to output 1 for kill-related inputs."

**Skills needed:** Formal methods (TLA+/Coq/Isabelle), program verification.

### OP4. Optimal K_MAX for Practical Problems
**Difficulty:** Easy | **Effort:** 4–8 weeks

Empirically determine the optimal `k` (number of inputs) for various problem 
classes: navigation, classification, regression approximation, game playing. 
Trade off: larger k = more expressive but harder to train (2^k table size).

**Skills needed:** Experimental design, statistics, benchmarking.

---

## Category 2: Learning and Optimization

### OP5. Curriculum Learning for MPDT Evolution
**Difficulty:** Medium | **Effort:** 8–12 weeks

Design and evaluate curriculum learning strategies for MPDT evolution. Start 
with simple environments, gradually increase complexity. Compare: fixed curriculum 
vs adaptive curriculum based on population fitness variance.

**Skills needed:** Reinforcement learning, curriculum design.

### OP6. Multi-Objective Fitness Functions
**Difficulty:** Medium | **Effort:** 8–12 weeks

Design Pareto-optimal fitness functions that balance: survival, energy efficiency, 
interpretability (tree size), and ethical compliance. Apply NSGA-II or MOEA/D 
to MPDT populations.

**Skills needed:** Multi-objective optimization, evolutionary algorithms.

### OP7. Transfer Learning Between FNL Domains
**Difficulty:** Medium | **Effort:** 10–16 weeks

Can an FNL (Functional Neural Lobe) trained in GridWorld transfer to a different 
environment (e.g., Minecraft)? Measure: zero-shot performance, few-shot adaptation 
via additional evolution generations.

**Skills needed:** Transfer learning, domain adaptation.

---

## Category 3: Systems and Performance

### OP8. MPDT on FPGA: Performance Analysis
**Difficulty:** Medium | **Effort:** 8–12 weeks

Implement an MPDT evaluation pipeline on FPGA (Lattice iCE40 or Xilinx Artix). 
Compare: latency, throughput, energy per inference vs CPU (x86) and GPU (CUDA). 
Implement batch evaluation (multiple inputs per cycle).

**Skills needed:** FPGA design (Verilog/VHDL), hardware benchmarking.

### OP9. Distributed Consensus for FROZEN Neurons
**Difficulty:** Hard | **Effort:** 12–20 weeks

Design a Byzantine Fault Tolerant consensus protocol for updating FROZEN neurons 
across a distributed cluster. Security model: up to f malicious nodes out of 
3f+1 total. Prove safety and liveness.

**Skills needed:** Distributed systems, consensus protocols, BFT.

### OP10. Energy Efficiency: MPDT vs Transformer Inference
**Difficulty:** Easy | **Effort:** 4–8 weeks

Rigorously compare the energy consumption of MPDT inference (single neuron → 
multi-neuron network) with a small transformer (BERT-tiny, GPT-2-small) on 
equivalent tasks. Measure: joules per inference, CO2 per 1000 inferences.

**Skills needed:** Energy measurement, benchmarking.

---

## Category 4: Ethics and Safety

### OP11. Detecting Ethical Violations via Formal Methods
**Difficulty:** Hard | **Effort:** 12–20 weeks

Extend OP3: Build a static analyzer that can prove a given FNL never violates 
any of the Three Prohibitions, regardless of input. Use SAT/SMT solvers to 
enumerate all possible input combinations and verify output constraints.

**Skills needed:** SAT/SMT solving (Z3), static analysis.

### OP12. Adversarial Robustness of MPDT Networks
**Difficulty:** Medium | **Effort:** 8–12 weeks

Can MPDT networks be adversarially attacked? Since inputs are discrete (0/1), 
traditional gradient-based attacks don't apply. But bit-flip attacks on the 
truth table itself are possible. Characterize vulnerability and propose defenses.

**Skills needed:** Adversarial ML, fault injection, security.

---

## Category 5: Interdisciplinary

### OP13. MPDT as a Model of Biological Neurons
**Difficulty:** Medium | **Effort:** 12–20 weeks

Compare MPDT neurons with biological neurons: dendritic computation, synaptic 
plasticity, spike-timing-dependent plasticity. Can MPDT truth tables model 
observed neural behaviors? Can biological learning rules inspire new MPDT 
genetic operators?

**Skills needed:** Computational neuroscience, neurobiology.

### OP14. Philosophical Implications of Deterministic AI
**Difficulty:** Easy | **Effort:** 4–8 weeks (essay/paper)

Explore the philosophical implications of a deterministic, interpretable AI 
system. Topics: free will (agent vs environment), responsibility (who is 
accountable for a deterministic system's actions?), consciousness (does 
deterministic computation preclude consciousness?).

**Skills needed:** Philosophy of mind, AI ethics.

---

## How to contribute

1. Pick a problem from this list (or propose your own)
2. Open a GitHub issue with label `research` describing your planned approach
3. We'll help with access to code, data, and guidance
4. Publish your results — we'll feature them on the project website and blog

For university collaborations, see `docs/L22.md` (Partnerships with Universities).

**Contact:** alexander@narbaev.com
