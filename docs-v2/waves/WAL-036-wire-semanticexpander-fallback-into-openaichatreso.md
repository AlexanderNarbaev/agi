# WAL 36 — wire SemanticExpander fallback into OpenAIChatResource (2026-09-05 15:22)

**Date:** 2026-09-18
**Branch:** `main`
**Focus:** wire SemanticExpander fallback into OpenAIChatResource (2026-09-05 15:22)

## Extracted from WAL.md

This wave was automatically extracted from the monolithic WAL.md file.

— wire SemanticExpander fallback into OpenAIChatResource (2026-09-05 15:22)

- Plain QaCorpusIndex.search returns no hits → fallback to searchWithExpansion.
- Threshold (0.5) unchanged; backward compatible.

---

**Checkpoint Hash:** `430de111` (current HEAD at extraction time)
**Previous Checkpoint:** see git log -1
**Next Wave:** W37

*Auto-extracted by extract-waves.py*
