# WAL 19 — continuous LM head training via feedback (2026-09-05 13:21)

**Date:** 2026-09-18
**Branch:** `main`
**Focus:** continuous LM head training via feedback (2026-09-05 13:21)

## Extracted from WAL.md

This wave was automatically extracted from the monolithic WAL.md file.

— continuous LM head training via feedback (2026-09-05 13:21)

- New io.matrix.api.LmHeadFeedbackTrainer: POST /v1/chat/feedback now trains LM head.
- Honest caveat: LmHead.update is sign-positive only. Negative feedback does NOT
  decrement weights (signal preserved in store for future re-training).
- 7/7 LmHeadFeedbackTrainerTest pass.

---

**Checkpoint Hash:** `430de111` (current HEAD at extraction time)
**Previous Checkpoint:** see git log -1
**Next Wave:** W20

*Auto-extracted by extract-waves.py*
