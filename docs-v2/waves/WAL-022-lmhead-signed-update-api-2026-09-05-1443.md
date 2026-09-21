# WAL 22 — LmHead signed update API (2026-09-05 14:43)

**Date:** 2026-09-18
**Branch:** `main`
**Focus:** LmHead signed update API (2026-09-05 14:43)

## Extracted from WAL.md

This wave was automatically extracted from the monolithic WAL.md file.

— LmHead signed update API (2026-09-05 14:43)

- New LmHead.applyUpdate(boolean[], int, double) — single source of truth
  for weight mutation. Both positive (training) and negative (feedback)
  paths route through it.
- New telemetry: positiveUpdateCount, negativeUpdateCount.
- LmHeadFeedbackTrainer.decrementForToken now calls
  applyUpdate(features, token, -0.1) — replaces RUN 19 no-op placeholder.
- 12 LmHeadTest (5 RUN 22 added) + 8 LmHeadFeedbackTrainerTest
  (1 RUN 22 added, 1 updated). All 20 pass.

---

**Checkpoint Hash:** `430de111` (current HEAD at extraction time)
**Previous Checkpoint:** see git log -1
**Next Wave:** W23

*Auto-extracted by extract-waves.py*
