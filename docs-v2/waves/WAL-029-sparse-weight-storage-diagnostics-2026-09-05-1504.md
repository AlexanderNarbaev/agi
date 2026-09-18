# WAL 29 — sparse weight storage diagnostics (2026-09-05 15:04)

**Date:** 2026-09-18
**Branch:** `main`
**Focus:** sparse weight storage diagnostics (2026-09-05 15:04)

## Extracted from WAL.md

This wave was automatically extracted from the monolithic WAL.md file.

— sparse weight storage diagnostics (2026-09-05 15:04)

- New LmHead memory-footprint diagnostics:
  denseMemoryBytes, sparseMemoryBytes, sparsityRatio,
  nonZeroWeightCount, totalWeightSlots.
- Honest finding: with current Hebbian decay, ALL slots end up
  non-zero. Sparse storage break-even requires changing decay
  to floor-at-zero.
- 4 new LmHeadTest (19 total, all pass).
- EXP-MATRIX.24 documents the honest finding.

---

**Checkpoint Hash:** `430de111` (current HEAD at extraction time)
**Previous Checkpoint:** see git log -1
**Next Wave:** W30

*Auto-extracted by extract-waves.py*
