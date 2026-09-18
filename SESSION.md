# SESSION

**Status:** ephemeral single-entry pointer to the latest wave.

This file contains **one entry at a time**: the most recent wave.
Each wave's full content lives in `docs-v2/waves/WAL-<NUM>.md`.
Historical waves are also in `docs-v2/waves/` (141 extracted from old monolithic WAL.md).

---

## Latest Wave: W354

- **File:** [`docs-v2/waves/WAL-354.md`](docs-v2/waves/WAL-354.md)
- **Checkpoint Hash:** `f8ccb84f`
- **Date:** 2026-09-18
- **Previous:** [W353](docs-v2/waves/WAL-353.md)

### W354 Summary
Gitverse shallow clone constraint discovered. Owner action required from gitverse web UI.

---

## Wave Commit Rule (Effective 2026-09-18, owner-mandated)

**At end of every wave session:**

1. Stage all wave artifacts: `git add -A`
2. Commit: `git commit -m "WAL: W<NUM> \u2014 <description>"`
3. **Push to BOTH remotes:**
   ```bash
   git push origin main
   git push gitverse main
   ```
4. Update this `SESSION.md` with the new wave number + commit hash.
5. Commit `SESSION.md` as part of the wave commit (or next commit if amended).

**Both remotes:**
- `origin` \u2192 https://github.com/AlexanderNarbaev/agi.git (primary, working)
- `gitverse` \u2192 git@gitverse.ru:AlexandrNarbaev/agi.git (mirror, server-side shallow issue)

**Failure mode:** If either push fails, the wave is NOT complete. Investigate and re-push.

**Currently failing:** `gitverse` rejects with "shallow update not allowed" until owner
disables shallow clone from gitverse web UI (Settings \u2192 Repository \u2192 disable "Shallow clone").

---

## Per-Wave File Format

Each wave's full content in `docs-v2/waves/WAL-<NUM>.md` contains:
- Date, branch, focus, checkpoint hash, previous checkpoint
- Objective / artifact references (commits, files)
- CONSTITUTION compliance notes
- Issues resolved + blockers
- Next steps pointer

Lightweight, single-purpose, easy to grep, easy for agents to load selectively.

---

**Last updated:** 2026-09-18 (W354 complete)
