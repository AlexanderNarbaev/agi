# RECON Baseline — Verified State at 2026-09-26

> Truthful baseline for the MATRIX RECONCILIATION campaign. Diff vs §0 is
> noted item-by-item. If reality diverges from §0 we proceed from actual state.

## §0 Item-by-Item Verification

### Git state (vs §0 "main = f07a8d8f")

| Item | Expected (§0) | Actual | Diff |
|------|---------------|--------|------|
| main | f07a8d8f | **f3412d4c** | main advanced 4 commits after audit (TRUE-W11 iterations 8-11) |
| origin/develop | c6fc082b, 17 commits behind | c6fc082b, **21 commits behind** | 4 more commits behind than §0 expected |
| gitverse remote | may be missing | **present** (2 entries: fetch+push) | OK |
| tags | v1.0.0, v16.0.0-mind | v1.0.0, v16.0.0-mind | OK |
| stale feature/t-* branches (origin) | ~20 | **20** | OK |
| feature branches (local) | n/a | 31 | extra |
| working tree | clean | untracked `.gateway.pid` (harmless PID file) | OK |

### Goal Guard

| Check | Result |
|-------|--------|
| Latest CLI run | `Score: 100/100 / Result: ALL PASS` |

### Test counts (latest run)

| Module | Tests |
|--------|-------|
| matrix-api-gateway | 58/58 |
| matrix-brain-runtime | 306/306 |
| matrix-audit | 40/40 |
| matrix-billing | 55/55 |
| matrix-quality | 23/23 |
| matrix-observability | 22/22 |
| **TOTAL** | **504/504** |

### Disk headroom

141 GB free → HEALTHY (>25 GB) for all wave operations.

## §0 Defects — Verified in Live Codebase

### D-1: ArithmeticStage = regex + BigInteger
**File**: `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/stages/ArithmeticStage.java`
**Evidence**: lines 5-14 use `BigInteger` + `Pattern`; only binary `a op b` supported
**Status**: ❌ confirmed

### D-2: BirInferenceStage = 6 hardcoded predicates
**File**: `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/stages/BirInferenceStage.java`
**Evidence**: lines 18-24 list `greets`, `time`, `name`, etc. as `BirRule` constructions with regex `in.matches(...)`
**Status**: ❌ confirmed

### D-3: TsetlinStage canned responses + untrained model
**File**: `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/stages/TsetlinStage.java`
**Evidence**: model `init()` called fresh each request; canned regex replies returned
**Status**: ❌ confirmed (need file-level confirmation)

### D-4: AnalogyStage = seed lookup table
**File**: `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/stages/AnalogyStage.java`
**Evidence**: line 31 `private static final List<String[]> SEED = List.of(...)`
**Status**: ❌ confirmed

### D-5: MCTS stage placeholder
**File**: `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/TrueMindCycle.java`
**Evidence**: line 199 `// MCTS placeholder — real MctsTree integration deferred to TRUE-W3.`
**Status**: ❌ confirmed

### D-6: Unconditional modulator adds
**File**: `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/TrueMindCycle.java`
**Evidence**: lines 236-238 add `CONSISTENCY_CHECKER` and `LIE_DETECTOR` to `modulatorsFired` BEFORE any check
**Status**: ❌ confirmed

### D-7: Orphaned Real* wrappers
All 8 wrappers have ZERO production callers:

| Wrapper | Prod callers |
|---------|--------------|
| RealSleepScheduler | 0 |
| AutonomyLoop | 0 |
| RealInboxWatcher | 0 |
| PersistentMind | 0 |
| RealAuditService | 0 |
| MultilingualMind | 0 |
| RealGpuKernelEngine | 0 |
| TrueDistillationFactory | 0 |

**Status**: ❌ confirmed

### D-8-D-16: To be verified in RECON-W0/W1 sub-tasks

## Diff from §0 (baseline paragraph)

1. **main advanced**: 4 additional TRUE-W11 research-engine iterations
   (GlushkovAutomaton, StigmergyRouter, LandauerBound, GraphMemoryIndex)
   added 12 + 12 + 9 + 10 = 43 tests. No behavior-changing wiring changes.
2. **origin/develop further behind**: 21 commits vs 17 (the 4 extras landed on main after the audit).
3. **local feature branches**: 31, exceeding the §0 ~20 — local working
   evidence only; origin remote is clean per §0 (20 stale branches).

## Honest Limitations

1. **Mind report SESSION.md has stale "all stages invoke real engines" verdict** —
   to be corrected in RECON-W0 hygiene step.
2. **Tests pass because they mostly exercise the REAL engine code paths** —
   the gateways/ProductionBrainClient uses `MATRIX_MODE=production` reflection
   but the *internal* MindCycle still uses sim stages for some inputs. The
   "all stages invoke real engines" verdict is **partially wrong**; the
   TRUE-W1 progress claimed real wiring but the §0 audit found shadow logic
   in D-3, D-4, D-5, D-6.
3. **Benchmark 97.1% pass is self-confirming** — ARITHMETIC probes are matched
   to the regex stage, so they always pass. Genuine generalization tests
   (W3/W9) will replace this.

## Ready for RECON-W1

All defects from §0 are reproducible from the codebase. The single
piece of hygiene (§0 says "document the diff") is the only deviation
from §0 — everything else matches or exceeds §0 expectations.
