# WAL 32 — wire OutputSafetyFilter into ChainTextGenerator (2026-09-05 15:16)

**Date:** 2026-09-18
**Branch:** `main`
**Focus:** wire OutputSafetyFilter into ChainTextGenerator (2026-09-05 15:16)

## Extracted from WAL.md

This wave was automatically extracted from the monolithic WAL.md file.

— wire OutputSafetyFilter into ChainTextGenerator (2026-09-05 15:16)

- ChainTextGenerator now applies OutputSafetyFilter during generation.
- Token-level: skippedForbiddenTokens counter; control bytes/surrogates
  dropped from output.
- String-level: forbidden phrases filtered via isStringAllowed.
- 7 ChainTextGeneratorSafetyTest (all pass).

---

**Checkpoint Hash:** `430de111` (current HEAD at extraction time)
**Previous Checkpoint:** see git log -1
**Next Wave:** W33

*Auto-extracted by extract-waves.py*
