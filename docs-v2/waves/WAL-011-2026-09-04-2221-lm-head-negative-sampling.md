# WAL 11 — 2026-09-04 22:21 — LM head negative sampling

**Date:** 2026-09-18
**Branch:** `main`
**Focus:** 2026-09-04 22:21 — LM head negative sampling

## Extracted from WAL.md

This wave was automatically extracted from the monolithic WAL.md file.

— 2026-09-04 22:21 — LM head negative sampling

### Goal Guard cycle #0 (957557e3)
The Goal Guard plugin auto-applied three audit fixes before this commit:
- Doc-vs-code lie removed
- Thread-safety regression fixed (synchronized restored)
- Wall-clock RNG violation fixed (deterministic seed)

### RUN 11.1 (this commit, in progress)
Additional audit fixes per the FAIL verdict:
1. `nNegatives` opt-in (default 0, was hardcoded 5)
2. Vocab bound bug (`negMax = 200000`, was hardcoded `Math.min(200000, 100000)`)
3. Deterministic test assertions
4. Status exposes `nNegatives`

---

**Checkpoint Hash:** `430de111` (current HEAD at extraction time)
**Previous Checkpoint:** see git log -1
**Next Wave:** W12

*Auto-extracted by extract-waves.py*
