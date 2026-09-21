# EXP-044 — Saliency calibration (online)

## Hypothesis
Saliency weights calibrate from prediction-error stream online.
Calibration error (ECE) ≤ 0.1 after 1000 cycles (synthetic-scope).

## Results (real measurements, 2026-08-28)
- **ECE = 0.050** after 1000 cycles
- Online update rule: `saliency' = 0.9·saliency + 0.1·PE`

## Verdict
**ACCEPTED** — measured 0.050, comfortably below the 0.1 gate.

## Honest framing
- The 10-bin ECE formula compares per-bin mean predicted-vs-actual
  saliency after 1000 cycles. The exponential moving average with
  α=0.9 tracks the PE stream closely enough that the bin
  distributions agree to within 0.05.
- Real ConsciousnessLoop uses uniform saliency (the default) and
  does NOT actually adapt the weights from PE; this measurement
  characterizes a *candidate* online calibration rule, not the
  current production behaviour. The integration with
  `ConsciousnessLoop.tick` is left as future work.

## Files
- Preregistration: `docs-v2/research/protocols/H-044.md`
- Harness: `matrix-core/src/test/java/io/matrix/research/Exp044SaliencyCalibrationTest.java`