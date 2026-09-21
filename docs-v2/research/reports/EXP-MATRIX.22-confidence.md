# EXP-MATRIX.22 — Confidence calibration (H-024, RUN 23)

## Hypothesis

The LM head should expose a calibrated confidence score in [0, 1]
for token predictions. Calibration assumption: a prediction made
with confidence `c` is correct ~`c` × 100% of the time.

## Setup

- **`LmHead.scoreWithConfidence(boolean[] chainOutput, int token, int[] candidates)`** —
  new public API. Computes raw score + softmax-calibrated confidence
  over a candidate distribution.
- **Temperature scaling**: `setTemperature(T)` divides logits by `T`
  before softmax. Higher T → softer distribution → lower peak
  confidence. Default T = 1.0.
- Deterministic: no random source, no wall-clock (AGENTS.md compliant).

## Results (real measurements, 2026-09-05)

| Test | Property | Result |
|---|---|---|
| `confidenceIsInUnitInterval` | confidence ∈ [0, 1] | **PASS** |
| `higherTemperatureMakesConfidenceSofter` | T=10 → lower confidence than T=1 | **PASS** |
| `confidenceIsDeterministic` | same input → same confidence | **PASS** |

All **15 LmHeadTest pass** (3 RUN 23 additions).

## Calibration methodology

For each test fingerprint `fp`:
1. Train LM head with various token-fingerprint associations.
2. Compute `scoreWithConfidence(fp, candidateToken, vocab)`.
3. Confidence = softmax(logits / T) for that token.
4. Temperature tunes the calibration curve.

## Honest caveats

- The current calibration is unverified against a held-out
  accuracy benchmark. We measure "is confidence in [0,1]" and
  "does T behave as expected" but not yet "is the confidence
  calibrated to actual accuracy".
- EXP-MATRIX.22 is a STRUCTURAL verification (the API works as
  specified). A full H-024 verification needs:
  - 100+ held-out (question, answer) pairs.
  - For each, compute top-1 confidence.
  - Bin by confidence and measure actual accuracy per bin.
  - Compute ECE (Expected Calibration Error).
- That is beyond RUN 23 scope; documented as future work.

## Cross-references

- H-024 (HYPOTHESES-NEW.md): calibration acceptance criterion.
- LmHead signed-update (RUN 22) is a prerequisite — without
  negative updates, the LM head cannot learn what NOT to predict,
  which biases confidence toward the most-frequent token.
