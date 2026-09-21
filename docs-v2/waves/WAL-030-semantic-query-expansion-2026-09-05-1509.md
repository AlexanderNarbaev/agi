# WAL 30 — semantic query expansion (2026-09-05 15:09)

**Date:** 2026-09-18
**Branch:** `main`
**Focus:** semantic query expansion (2026-09-05 15:09)

## Extracted from WAL.md

This wave was automatically extracted from the monolithic WAL.md file.

— semantic query expansion (2026-09-05 15:09)

- New SemanticExpander: query + vocab → expanded token set with
  character-trigram fuzzy matches.
- 11 SemanticExpanderTest + 6 Exp025SemanticRetrievalTest (all pass).
- EXP-MATRIX.25 documents the Cyrillic regex bug (?U flag required).
- Honest caveat: cheap heuristic, NOT a substitute for true embeddings.

---

**Checkpoint Hash:** `430de111` (current HEAD at extraction time)
**Previous Checkpoint:** see git log -1
**Next Wave:** W31

*Auto-extracted by extract-waves.py*
