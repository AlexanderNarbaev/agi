# DESIGN-NN — Negative-Selection Anomaly Detector (META-R R-E biology)

> TRUE-W11 research-engine iteration #2.
> Domain: biology (immune-system-inspired anomaly detection).

## Inspiration

The biological immune system uses negative selection: T-cells that
bind to self-proteins are eliminated; the surviving repertoire detects
only "non-self" (foreign). Translating to MATRIX:

- "self" = known-good inputs (from KB or recent history)
- "non-self" = anomalous inputs that don't match any known pattern
- Detector: random detector rejected by training data; alive detectors
  flag anomalies.

## Algorithm sketch

1. Sample N random substrings of length L from a "self corpus"
   (recent gateway traffic + KB taught entries).
2. Reject any detector that matches any self-sample above threshold.
3. Surviving detectors cover non-self space.
4. At runtime: incoming input → if any surviving detector matches → flag
   as anomaly (potential injection / adversarial / novel).

## Implementation location

- `io.matrix.brain.runtime.NegativeSelectionDetector`
- Uses N-gram features (length 4 chars) hashed via FNV-1a
- Stores detectors in a Bloom-filter-like structure
- Threshold: match if >=2 detectors fire

## Status (TRUE-W11 iteration 2)

- [x] DESIGN-NN drafted
- [ ] Code stub (deferred — promote after iteration 1 measures)
