# WAL 37 — auto-migrate legacy corpus on reload (2026-09-05 15:23)

**Date:** 2026-09-18
**Branch:** `main`
**Focus:** auto-migrate legacy corpus on reload (2026-09-05 15:23)

## Extracted from WAL.md

This wave was automatically extracted from the monolithic WAL.md file.

— auto-migrate legacy corpus on reload (2026-09-05 15:23)

- QaCorpusIndex.reload() auto-detects v1 corpus and migrates via CorpusMigration.
- loadQaPairs() handles both v1 (bare array) and v2 (envelope) formats.
- All 12 QaCorpusIndexTest + 10 Exp025SemanticRetrievalTest still pass.

---

**Checkpoint Hash:** `430de111` (current HEAD at extraction time)
**Previous Checkpoint:** see git log -1
**Next Wave:** W38

*Auto-extracted by extract-waves.py*
