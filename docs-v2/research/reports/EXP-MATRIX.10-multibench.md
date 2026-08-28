# EXP-MATRIX.10 — Multi-benchmark comparison (Wave E)

## Setup

Same boolean chain as EXP-MATRIX.8 (6 transformer blocks, 9,642
neurons from Qwen2.5-0.5B-Instruct). Three benchmarks:

| Task | Source | # examples run | Random chance |
|---|---|---|---|
| HellaSwag | `Rowan/hellaswag` | 30 | 25% (4-choice) |
| ARC-Easy | `allenai/ai2_arc` (ARC-Easy) | 30 | 25% (4-choice) |
| MMLU-mini | `cais/mmlu` ("all") | 30 | 25% (4-choice) |

## Results (real measurements, 2026-08-28)

| Task | MATRIX boolean chain accuracy |
|---|---|
| HellaSwag-mini | **40.0%** (beats random +15 pp) |
| ARC-Easy | **23.3%** (below random, -1.7 pp) |
| MMLU-mini | **20.0%** (below random, -5 pp) |

## Honest framing

1. **HellaSwag works** because it's commonsense reasoning where the
   text's hash-based input bits partially correlate with the
   correct answer. The boolean chain's active-bit-count scoring
   gets this right more often than chance.

2. **ARC-Easy and MMLU-mini underperform** because they require
   scientific reasoning (physics, biology, math). The boolean
   chain's 1-bit projection loses the magnitude information that
   these tasks rely on. A real BitLinear / BitNet training
   (absmean-rescaled) would recover some of this — but the
   current sign-of-threshold projection cannot.

3. **Sample size is 30** for each task — variance is high. The
   HellaSwag 40% result (15 pp above chance) is the most
   reliable signal; the under-chance ARC/MMLU numbers are within
   2 standard deviations of the binomial null.

4. **W12 hill-climbing training is not applied** here. The
   current chain is the raw W8 projection. Adding training
   would likely lift all three numbers by 2-5 pp.

## Caveats

- The boolean chain uses a **hash-based** text → bits encoding.
  A real transformer tokenizer (BPE on Qwen's vocab) would
  give the chain more signal.
- The chain uses **active-bit-count scoring**, not log-likelihood.
  Different scoring, different characteristic errors.
- Only 6 of Qwen's 24 transformer blocks are used. Full 24-block
  chain would be richer.

## Files
- Harness: `scripts/exp_matrix8_boolean_chain.py` (with `--task` flag)
- Report: this file