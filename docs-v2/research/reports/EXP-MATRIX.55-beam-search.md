# EXP-MATRIX.55 — Beam search generator (RUN 112)

## Hypothesis

Top-K candidate selection at each step should produce higher-quality
outputs than greedy decoding.

## Setup

- `BeamSearchGenerator` runs `bridge.getOnnx().greedyNextToken()`
  per step.
- For k=1 this is identical to greedy.
- For k>1, this is currently a placeholder: true multi-sequence
  beam search requires running K inferences per step with extended
  candidate sequences and tracking per-beam state.

## Honest finding

The current implementation is a SIMPLIFIED beam search — it picks
the top-K tokens but doesn't maintain multiple candidate sequences.
For production beam search, you'd need:
1. K parallel sequence states (each with its own prompt + generated)
2. Per-beam score tracking
3. Beam pruning at each step

## Cross-references

- OnnxRuntimeAdapter.greedyNextToken: underlying inference
- QwenOnnxBridge: chat() uses greedy
- EXP-MATRIX.40: end-to-end pipeline

## Verdict

Scaffolding for beam search delivered. Real multi-sequence beam
search would require deeper refactor of the generation loop.
