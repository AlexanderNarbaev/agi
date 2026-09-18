# WAL 12 — Brain Loop wired into /v1/chat (2026-09-05 12:50)

**Date:** 2026-09-18
**Branch:** `main`
**Focus:** Brain Loop wired into /v1/chat (2026-09-05 12:50)

## Extracted from WAL.md

This wave was automatically extracted from the monolithic WAL.md file.

— Brain Loop wired into /v1/chat (2026-09-05 12:50)

- New io.matrix.reasoning.BrainLoopService (ApplicationScoped): production wiring of the
  nine-stage ConsciousnessLoop. tick(BitSet) returns Trace(tickId, phasePath, attentionScore,
  predictionError, actionsSubmitted).
- OpenAIChatResource now invokes brainLoop.tick(observation) before generation; emits
  X-Matrix-Trace header on every chat response.
- 9/9 BrainLoopServiceTest pass, 19/19 OpenAIChatResourceTest pass (header present when wired,
  absent when not).
- Total tests: 41/41 (+9 from RUN 12).

---

**Checkpoint Hash:** `430de111` (current HEAD at extraction time)
**Previous Checkpoint:** see git log -1
**Next Wave:** W13

*Auto-extracted by extract-waves.py*
