# WAL 31 — wire SemanticExpander into QaCorpusIndex (2026-09-05 15:15)

**Date:** 2026-09-18
**Branch:** `main`
**Focus:** wire SemanticExpander into QaCorpusIndex (2026-09-05 15:15)

## Extracted from WAL.md

This wave was automatically extracted from the monolithic WAL.md file.

— wire SemanticExpander into QaCorpusIndex (2026-09-05 15:15)

- New QaCorpusIndex.searchWithExpansion(query, topK) uses SemanticExpander.
- Existing search() unchanged (backward compatible).
- semanticExpansionEnabled flag (default true) lets callers opt out.
- 4 new Exp025SemanticRetrievalTest (10 total, all pass).

---

**Checkpoint Hash:** `430de111` (current HEAD at extraction time)
**Previous Checkpoint:** see git log -1
**Next Wave:** W32

*Auto-extracted by extract-waves.py*
