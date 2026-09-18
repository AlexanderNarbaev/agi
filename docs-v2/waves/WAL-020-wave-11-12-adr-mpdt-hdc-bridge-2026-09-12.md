# WAL 20 — Wave 11-12: ADR + MPDT × HDC bridge (2026-09-12)

**Date:** 2026-09-18
**Branch:** `main`
**Focus:** Wave 11-12: ADR + MPDT × HDC bridge (2026-09-12)

## Extracted from WAL.md

This wave was automatically extracted from the monolithic WAL.md file.

— Wave 11-12: ADR + MPDT × HDC bridge (2026-09-12)

**Wave 11 — ADR-2026-09-12-001:**
- HDC × BitLinear hybrid brain принят как архитектурный примитив
- STANDARDS-MATRIX обновлён (W31 Brain + libtruthy_hamming + BitNet b1.58 ref)
- 2 docs файла, 0 новых тестов

**Wave 12 — RUN 451 MpdtHdcBridge:**
- Integration MPDT (existing HierarchicalBrain) × HDC (W31 HdcBrain)
- decideAndRemember: MPDT → store in HDC
- decideWithMemory: try HDC, fall back to MPDT if low similarity
- benchmark: train + test with accuracy and memory hit rate
- 11 tests, 0 fails
- HEAD: 2a669b70

**W31 wave extended:** 15 brain classes + ADR + integration bridge.

---

**Checkpoint Hash:** `430de111` (current HEAD at extraction time)
**Previous Checkpoint:** see git log -1
**Next Wave:** W21

*Auto-extracted by extract-waves.py*
