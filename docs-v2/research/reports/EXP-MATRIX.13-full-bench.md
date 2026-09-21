# EXP-MATRIX.13 — Real-domain corpus benchmark (Wave K)

## Setup
- **Source**: Qwen2.5-0.5B-Instruct safetensors at `models/external/qwen2.5-0.5b/`
- **Chain**: full 24 transformer blocks (Wave I) via BitLinear projection (Wave D)
- **Tasks**: HellaSwag validation
- **Sample sizes**: 200, 500 examples

## Results (real measurements, 2026-08-30)

| Task | Sample size | BitLinear accuracy | Sign-only accuracy | Random chance |
|---|---|---|---|---|
| HellaSwag | 200 | 0.270 | n/a | 0.25 |
| HellaSwag | 500 | 0.292 | 0.292 | 0.25 |

## Verdict
**REFUTED-FOR-COMPETITIVE-PARITY** at this scale — the boolean chain
beats random by ~4 pp on 500 HellaSwag examples, but the gain is small.

## Honest framing

1. **The chain_score function is the bottleneck**. The current
   scoring counts active-bit matches per layer; the absmean scale
   (BitLinear's improvement over sign-of-zero) does not enter the
   score. Hence the two projections give identical accuracy.

2. **The 6-layer variant earlier got 40% on 25 samples** — that's a
   15 pp gap that the 24-layer chain can't reproduce on 500 samples.
   With more samples, the variance drops and we converge to the
   "true" accuracy of the projection method.

3. **The fundamental ceiling** is set by:
   - Hash-based text → bits (no semantic understanding)
   - Smaller layer coverage of the model (we use200 neurons/tensor
     out of ~3 MB per linear layer)
   - Boolean substrate's lossy compression (BitNet papers report
     5-15 pp drop vs float)
   - No real training (the chain is the raw projected weights)

## Next steps for higher accuracy

1. **Use a BPE tokenizer** (Qwen's `tokenizer.json`) instead of hash
   bits — gives semantically meaningful input representations.
2. **Increase per-tensor neuron budget** from 200 to 4000+ — the
   full tensor projection would preserve more of the weight
   distribution.
3. **Run real sign-descent training** (BitLinearTrainer.java in the
   codebase) — currently the chain is untrained.
4. **Use chain_score that respects magnitude** — multiply each
   neuron's contribution by its per-tensor absmean scale.

## Files
- Harness: `scripts/exp_matrix13_full_bench.py`
- Java trainer (not yet integrated into the benchmark): `matrix-core/src/main/java/io/matrix/imports/BitLinearTrainer.java`
- Report: this file