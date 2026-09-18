# WAL 18 — native build attempts (RFC blocked) (2026-09-05 13:13)

**Date:** 2026-09-18
**Branch:** `main`
**Focus:** native build attempts (RFC blocked) (2026-09-05 13:13)

## Extracted from WAL.md

This wave was automatically extracted from the monolithic WAL.md file.

— native build attempts (RFC blocked) (2026-09-05 13:13)

- Local GraalVM CE 25.0.2 IS installed; class-init list extended from 5 to 17 entries.
- Build still fails with cascading UnsupportedFeatureException + NoClassDefFoundError
  for io.netty.resolver.dns + org.tukaani.xz.
- User RFC required for: Mandrel token / Scala-Pekko replacement /
  --report-unsupported-elements-at-runtime fallback.

---

**Checkpoint Hash:** `430de111` (current HEAD at extraction time)
**Previous Checkpoint:** see git log -1
**Next Wave:** W19

*Auto-extracted by extract-waves.py*
