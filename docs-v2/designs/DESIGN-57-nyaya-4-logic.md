# DESIGN-57 — Nyaya 4-state Logic for Uncertainty Quantization

**Status:** v1 design.

## 1. What is Nyaya?

Nyaya (literally "logic" or "rule") — one of six orthodox Hindu philosophical schools,
focused on logic, epistemology, and debate. Around 5-6 век н.э., Dignāga and Dharmakīrti
developed a 4-fold classification of propositions (or states) based on the kind of
evidence available:

1. **Svatantra (svat-anartha)**: directly evident — **TRUE**. Established by perception, inference, comparison, or testimony without depending on other evidence.
2. **Paratantra**: evident by way of contradiction — **FALSE**. The negation of svatantra.
3. **Anekata**: un-determined — **NEITHER TRUE NOR FALSE**. There is *no* evidence either way.
4. **Dvaya-anekata**: paradoxical — **BOTH TRUE AND FALSE**. Paradox of seeing the rope as snake (true-seeming while actually false).

This 4-fold scheme is **precision** — recognizes that propositions can be in intermediate 
or contradictory states. Stcherbatsky (1930-е перевод Dignāga's "Pramāṇa-samuccaya") brought this
to Western attention.

## 2. Why relevant to BitNet b1.58

BitNet b1.58 (Ma et al., 2024) quantizes weights to **3 states {-1, 0, +1}**:
- `-1`: strongly inhibits
- `0`: filtered / non-essential
- `+1`: strongly promotes

But it has **no built-in uncertainty quantification.** When a network says "I think `0`"
it doesn't distinguish "model is uncertain" from "this feature is irrelevant."

Nyaya-style 4-state would be:
- `+1` (svatantra-promote)
- `-1` (paratantra-inhibit)
- `0` (anekata-irrelevant)
- `?` (dvaya-anekata-paradoxical) ← **new state, captures uncertainty**

The 4th state `?` is **distributional** — it doesn't fix the contribution; instead it carries
information that prevents application until further confirmation.

## 3. Mathematical encoding

```
Symbol        | Real  | Effect
--------------|-------|------------------
+1  svatantra |  +1   | contribution = +1·activation
-1  paratantra|  -1   | contribution = -1·activation
0   anekata   |   0   | contribution = 0 (no influence)
?   dvaya-anekata | probabilistic | contribution = ±ε with low |ε|
```

Training: when uncertain, weight becomes `?`. At inference, `?` is sampled to ±ε for `ε << 1`
(low-confidence magnitude), or propagated as entropy into the next layer.

## 4. Use case in our brain simulator

**Spelke core knowledge of agent detection** (DESIGN-58 §2):
- "is animate" feature should activate with confidence when input shows self-propelled motion.
- If uncertain (occlusion), should **delay** rather than assert.
- With BitNet b1.58 alone, the network commits to either 0 or ±1 — it can't say "wait, more info needed."
- With Nyaya 4-state, the network can mark `?` and propagate as uncertainty to downstream query.

In practice: the `?` state becomes "ask for more data" trigger — clean interface between
brain simulation and supervisor LLM (which can resolve ambiguity).

## 5. Implementation strategy

For our CPU-friendly brain simulator, we can implement Nyaya without complex machinery:

```java
enum WeightState { SVATANTRA(1), PARATANTRA(-1), ANEKATA(0), DVAYA_ANEKATA(2) }
```

Internal representation uses 2 bits (so 0.5× the storage of 3-state {-1,0,+1} which uses 1.58 bits
on average; nyaya would use exactly 2 bits).

Aggregation per input:
```java
float aggregate(int[] weights, float[] activations) {
    // Aggregates only non-? weights; ? weights carry forward as uncertainty vector
    float sum = 0;
    int uncertaintyCount = 0;
    for (int i = 0; i < weights.length; i++) {
        int w = weights[i];
        if (w == WeightState.DVAYA_ANEKATA.code()) uncertaintyCount++;
        else sum += w * activations[i];
    }
    return sum / (weights.length - uncertaintyCount);  // renormalise
}
```

## 6. Benchmark hypothesis

For tasks requiring **abstention / "I don't know"** behavior:
- 3-state BitNet: 70% accuracy, 30% errors when uncertain
- 4-state Nyaya: 75% accuracy, 25% explicit "uncertain" propagation
- Decision-maker receiving these: 95% accuracy (vs 70% baseline) because 25% gets re-routed to clarification step.

**Расчёт:** если за 30% uncertain cases baseline модель делает wrong calls, а Nyaya правильно flags them
для supervisor, то end-to-end accuracy = 0.7·1.0 + 0.3·(0.95) = 0.985.

Качественный выигрыш — прежде всего в **trust calibration** и **safety**.

## 7. RUN-цель

- `NyayaWeight.java` — 2-bit ternary with explicit uncertainty.
- `NyayaQuantizer.java` — converts Absmean-quantized weights to Nyaya 4-state via second-pass entropy threshold.
- `UncertaintyAggregator.java` — aggregates weighted inputs carrying through uncertainty.

## 8. Why this is unusual

Most modern neural networks use **continuous probabilistic uncertainty** (Bayesian NNs,
MC dropout, ensemble disagreement). Nyaya's value is **explicit categorical uncertainty** — fits
with discrete state machines, makes uncertainty transportable across layers without
interpolation noise.

## 9. Connection to broader W31 doctrine

This design implements META-R1 (parallel cross-disciplinary research): brings Nyaya (Indian logic
школа 5-6 век н.э., formalized through Stcherbatsky translation 1930s) into a modern
binarized neural network context. The 4th state `?` connects classical Indian logic with
modern AI alignment research.

