# WAL 133 — W87-W91: Multi-timestep integration metrics + Φ_linGauss + PhiID (2026-09-14)

**Date:** 2026-09-18
**Branch:** `main`
**Focus:** W87-W91: Multi-timestep integration metrics + Φ_linGauss + PhiID (2026-09-14)

## Extracted from WAL.md

This wave was automatically extracted from the monolithic WAL.md file.

— W87-W91: Multi-timestep integration metrics + Φ_linGauss + PhiID (2026-09-14)

**W87 — ConsciousBrain multi-timestep integration metrics:**
- Added `appendTrajectory(observation)` called every cycle, populating `long[8]` circular buffer
- Added `nextPow2(int)` helper; fixed ΦF distribution size to power-of-2 (was throwing IAE on N+1=9)
- MultiTimestepIntegrationTest 4/4 PASS: Φ_binary=0.513, ΦR=0.492, C_N=0.425 (Gaussian baseline)
- Tickling flag emitted 20×false, 0×null across cycles 10-29

**W88 — Multi-timestep noise-floor benchmark:**
- W88MultiTimestepNoiseFloorTest 5/5 PASS
- Re-runs H-082 hypothesis battery without `c % 10 == 0` filter
- Empirical finding: GAUSSIAN Φ_binary=0.6495, PERIODIC Φ_binary=0.0 (deterministic single-state)
- H-082a "structured > noise" is **partially refuted** in multi-timestep formulation (H-081 updated)

**W89 — Φ_linGauss closed-form (Barrett-Seth 2011):**
- Closed-form linear-Gaussian integration via covariance ln-determinant
- `IntegrationMetrics.phiLinGauss(trajectory, N)` and `phiLinGaussFromSamples(samples, N)`
- Complexity: O(2^N · N³) for N ≤ 16 (vs Φ_binary's O(2^N · 2^N))
- PhiLinGaussTest 9/9 PASS: independent random Φ=0.006, partial-corr Φ=0.131, permutation-invariant, N=1→0

**W90 — PhiID Integrated Information Decomposition (Mediano 2020):**
- Three-tier API: `bivariateGaussian`, `trivariateGaussian`, `system`
- Four atoms: redundancy (r), synergy (s), unique information (unqX, unqY); sum = I(X;Y)
- Closed-form Gaussian via partial correlation ρ_{XY·Z}
- PhiIdTest 9/9 PASS: XOR-like chain → positive synergy; redundant chain → r=4.68; system-level averages across C(N,2) pairs

**W91 — Synthesis report + WAL/PROTOCOL/INDEX/HYPOTHESES sync:**
- `docs-v2/research/W87-W91-FINAL-SYNTHESIS-REPORT.md` committed (610 lines)
- `docs-v2/research/HYPOTHESES-NEW.md` extended with H-078..H-084
- `docs-v2/INDEX.md` updated with W87-W91 references
- 27 new tests added (4+5+9+9), all PASS
- LOC added: ~1090 lines (mostly test code)
- 4 commits: W87 (96ce195f), W88 (25e2cc32), W89 (281b092c), W90 (f7da75a4) + W91 pending
- All 4 commits pushed to `origin/main`

**Hypothesis ledger updates:**
- H-082 ✅ CONFIRMED (multi-timestep trajectory ⇒ non-zero Φ for diverse inputs)
- H-083 ✅ CONFIRMED (Φ_linGauss closed-form O(2^N · N³) for N ≤ 16)
- H-084 ✅ CONFIRMED (PhiID 4-atom decomposition for Gaussian triples/quartets)
- H-081 ⚠ PARTIALLY REFUTED (multi-timestep inverts W76 H-082a signal-vs-noise claim)

**CONSTITUTION compliance (W91 audit):**
- I (pure) ✅: all metrics deterministic on seeded input
- II (determinism) ✅: same seed → same metric
- IV (honest) ✅: H-082a refutation documented; no fake numbers
- V (tests) ✅: 27 new tests covering all new metrics
- VI (substrate, not consciousness claim) ✅: every Javadoc has disclaimer
- VII (cross-disciplinary) ⚠ R-A done (Tononi/Mediano/Barrett-Seth); R-B..R-F open (see report §8)

Project total (W87-W91): 27 new tests, 0 failures.

HEAD: W91-pushed → ready for W92+ future waves.

---

**Checkpoint Hash:** `430de111` (current HEAD at extraction time)
**Previous Checkpoint:** see git log -1
**Next Wave:** W134

*Auto-extracted by extract-waves.py*
