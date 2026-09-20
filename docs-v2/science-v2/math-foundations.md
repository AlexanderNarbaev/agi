# Mathematical Foundations

> **Layer:** Scientist | **Mathematical Level:** Intermediate | **Last Updated:** 2026-09-20

---

## 1. Hyperdimensional Computing (HDC)

### Definition

An HDC vector $\mathbf{v} \in \{0, 1\}^D$ is a binary vector of dimension $D$.

### Operations

**Binding** (element-wise XOR):
$$\mathbf{v}_1 \otimes \mathbf{v}_2 = (v_{1,1} \oplus v_{2,1}, \ldots, v_{1,D} \oplus v_{2,D})$$

**Bundling** (element-wise majority):
$$\mathbf{v}_1 \oplus \mathbf{v}_2 = \text{majority}(\mathbf{v}_1, \mathbf{v}_2)$$

**Permutation** (cyclic shift):
$$\pi(\mathbf{v}) = (v_D, v_1, v_2, \ldots, v_{D-1})$$

### Similarity

Cosine similarity for binary vectors:
$$\text{sim}(\mathbf{v}_1, \mathbf{v}_2) = \frac{2 \cdot \text{agree}(\mathbf{v}_1, \mathbf{v}_2) - D}{D}$$

Where $\text{agree}$ counts positions where both vectors have the same value.

**Example:**
- $\mathbf{v}_1 = (1, 0, 1, 0)$, $\mathbf{v}_2 = (1, 1, 1, 0)$
- Agree: 3 positions (1st, 3rd, 4th)
- $\text{sim} = \frac{2 \cdot 3 - 4}{4} = 0.5$

---

## 2. Biochemical Network Dynamics

### Single Modulator

For modulator $M_i$ with production rate $\alpha_i$, decay rate $\beta_i$:

$$\frac{dM_i}{dt} = \alpha_i \cdot s_i(t) - \beta_i \cdot M_i(t)$$

Where $s_i(t)$ is the receptor sensitivity at time $t$.

### Receptor Adaptation

$$s_i(t+1) = s_i(t) \cdot (1 + \gamma \cdot (\bar{M} - M_i(t)))$$

Where $\gamma$ is the adaptation rate and $\bar{M} = \frac{\min + \max}{2}$ is the target level.

### Network Interactions

For modulator $M_i$ interacting with $M_j$:

**Synergy:**
$$\text{effect}_{j \to i} = w_{ji} \cdot M_j \cdot (1 + \alpha \cdot M_i \cdot M_j)$$

**Antagonism:**
$$\text{effect}_{j \to i} = -|w_{ji}| \cdot M_j \cdot \frac{1}{1 + \beta \cdot M_i}$$

**Catalysis:**
$$\text{effect}_{j \to i} = w_{ji} \cdot M_j \cdot M_i$$

### Total Effect

$$\Delta M_i = \sum_{j \neq i} \text{effect}_{j \to i}$$

**Example (Stress Cascade):**
- $M_{\text{cortisol}} = 0.9$, $M_{\text{dopamine}} = 0.5$
- $w_{\text{cortisol} \to \text{dopamine}} = 0.7$, $\beta = 0.8$
- $\text{effect} = -0.7 \cdot 0.9 \cdot \frac{1}{1 + 0.8 \cdot 0.5} = -0.63 \cdot 0.714 = -0.45$
- Dopamine decreases by 0.45

---

## 3. Stigmergy Protocol

### Pheromone Strength

Pheromone strength $S$ decays exponentially:

$$S(t+1) = S(t) \cdot (1 - \delta)$$

Where $\delta$ is the decay rate (default: 0.1).

### Aggregated Signal

For topic $T$ with pheromones $p_1, \ldots, p_n$:

$$\text{Signal}(T) = \sum_{i=1}^{n} S_i(t)$$

### Hot Topic Detection

Topics are ranked by signal strength. A topic is "hot" if:

$$\text{Signal}(T) > \theta_{\text{hot}}$$

Where $\theta_{\text{hot}}$ is a threshold (default: 5.0).

**Example:**
- Topic "emerging-pattern" has 10 pheromones, each with strength 0.7
- $\text{Signal} = 10 \cdot 0.7 = 7.0 > 5.0$ → Hot topic

---

## 4. Capability Consensus

### Voting Weight

Node $n$ with role $R$ has voting weight:

$$w(n) = \begin{cases}
0.3 & \text{if } R = \text{INFANT} \\
0.5 & \text{if } R = \text{LEARNER} \\
0.7 & \text{if } R = \text{ADULT} \\
0.9 & \text{if } R = \text{SPECIALIST} \\
1.0 & \text{if } R = \text{GUARDIAN}
\end{cases}$$

### Consensus Threshold

For capability level $L$:

$$\theta(L) = \begin{cases}
0.50 & \text{if } L \leq 4 \\
0.67 & \text{if } L = 5 \\
0.75 & \text{if } L = 6 \\
0.95 & \text{if } L \geq 7
\end{cases}$$

### Decision

$$\text{Decision} = \begin{cases}
\text{APPROVED} & \text{if } \frac{\sum w_{\text{yes}}}{\sum w_{\text{total}}} \geq \theta(L) \\
\text{REJECTED} & \text{otherwise}
\end{cases}$$

**Guardian Veto:** If any GUARDIAN votes NO, the proposal is rejected regardless of weight.

---

## 5. Sleep Consolidation

### Pruning Rule

Pattern $p$ with strength $S_p$ is pruned if:

$$S_p < \theta_{\text{prune}}$$

Where $\theta_{\text{prune}}$ is the pruning threshold (default: 0.3).

### Memory Retention

$$\text{Retention} = \frac{|\text{patterns after sleep}|}{|\text{patterns before sleep}|}$$

### Accuracy Improvement

$$\Delta \text{Accuracy} = \text{Accuracy}_{\text{after}} - \text{Accuracy}_{\text{before}}$$

**Example:**
- 100 patterns, threshold = 0.3
- 22 patterns have strength < 0.3 → pruned
- 78 patterns remain
- $\text{Retention} = 78/100 = 0.78$
- $\Delta \text{Accuracy} = 0.613 - 0.514 = +0.099$ (+19.4%)

---

## 6. BIR Inference

### Truth Table Evaluation

For input vector $\mathbf{x} \in \{0, 1\}^n$ and rule $R$:

$$R(\mathbf{x}) = \bigwedge_{i \in \text{positive}} x_i \wedge \bigwedge_{j \in \text{negative}} \neg x_j$$

### Confidence

$$\text{confidence}(R, \mathbf{x}) = \frac{|\{i : x_i = R_i\}|}{n}$$

**Example:**
- Rule: $(1, 0, 1, ?)$ (don't care on 4th position)
- Input: $(1, 0, 1, 0)$
- Match: 3/3 required positions match
- Confidence: $3/3 = 1.0$

---

## References

- [BENCHMARK-REPORT-W620.md](../research/BENCHMARK-REPORT-W620.md) — Empirical results
- [SLEEP-CONSOLIDATION-STUDY-W650.md](../research/SLEEP-CONSOLIDATION-STUDY-W650.md) — Sleep metrics
- [FEDERATION-SCALE-REPORT-W635.md](../research/FEDERATION-SCALE-REPORT-W635.md) — Consensus scaling
