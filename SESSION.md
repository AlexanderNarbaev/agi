# SESSION

**Status:** ephemeral single-entry pointer to the latest wave.

This file contains **one entry at a time**: the most recent wave.
Each wave's full content lives in `docs-v2/waves/WAL-<NUM>.md`.
Historical waves are also in `docs-v2/waves/` (or archived to
`docs-v2/archive/waves-001-296.tar.gz` after WAL split migration).

---

## Latest Wave: W351

- **File:** [`docs-v2/waves/WAL-351.md`](docs-v2/waves/WAL-351.md)
- **Checkpoint Hash:** `430de111`
- **Date:** 2026-09-18
- **Previous:** [W350](docs-v2/waves/WAL-350.md) (`7b839a0e`)

---

## Wave Commit Rule (AGENTS.md)

**At end of every wave session:**

1. Stage all wave artifacts: `git add -A`
2. Commit: `git commit -m "WAL: W<NUM> — <description>"`
3. **Push to BOTH remotes:**
   ```bash
   git push origin main
   git push gitverse main
   ```
4. Update this `SESSION.md` with the new wave number + commit hash.
5. Commit `SESSION.md` as part of the wave commit (or in the next commit if amended).

**Both remotes:**
- `origin` → https://github.com/AlexanderNarbaev/agi.git (primary)
- `gitverse` → git@gitverse.ru:AlexandrNarbaev/agi.git (mirror)

**Failure mode:** If either push fails, the wave is NOT complete. Investigate and re-push.

---

**Last updated:** 2026-09-18 (W351 complete)
