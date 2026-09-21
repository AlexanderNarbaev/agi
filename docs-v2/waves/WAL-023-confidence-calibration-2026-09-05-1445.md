# WAL 23 — confidence calibration (2026-09-05 14:45)

**Date:** 2026-09-18
**Branch:** `main`
**Focus:** confidence calibration (2026-09-05 14:45)

## Extracted from WAL.md

This wave was automatically extracted from the monolithic WAL.md file.

— confidence calibration (2026-09-05 14:45)

- New LmHead.scoreWithConfidence(boolean[], int, int[]) returns
  ScoreWithConfidence{score, confidence} with softmax-calibrated confidence.
- Temperature scaling via setTemperature(T); default T=1.0.
- 3 new LmHeadTest (15 total, all pass).

---

**Checkpoint Hash:** `430de111` (current HEAD at extraction time)
**Previous Checkpoint:** see git log -1
**Next Wave:** W24

*Auto-extracted by extract-waves.py*
