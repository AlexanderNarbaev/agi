# W87–W91 Final Synthesis — Multi-Timestep Integration Metrics & PhiID

**Status**: ✅ W87, W88, W89, W90 PASS · W91 (this report) compiled
**Date**: 2026-09-14
**Author**: wal-wal (MATRIX deep-research-wave series)
**Prior context**: W80 Final Synthesis Report (`docs-v2/research/W80-FINAL-SYNTHESIS-REPORT.md`)

This synthesis covers the four-wave deep research progression that brought
ConsciousBrain from single-state snapshot integration to multi-timestep
trajectory analysis with four metrics, plus the introduction of closed-form
linear-Gaussian Φ and the four-atom PhiID decomposition.

---

## 1. Overview

| Wave | Theme | Test artifact | Result |
|------|-------|---------------|--------|
| **W87** | Multi-timestep integration metrics | `MultiTimestepIntegrationTest` (4 tests) | ✅ PASS — Φ_binary=0.51, ΦR=0.49, C_N=0.43 on Gaussian |
| **W88** | Noise-floor re-validation | `W88MultiTimestepNoiseFloorTest` (5 tests) | ✅ PASS — re-evaluated H-082 across all 5 pattern types |
| **W89** | Φ_linGauss closed-form (W80 priority 3) | `PhiLinGaussTest` (9 tests) | ✅ PASS — closed-form covariance ln-determinant |
| **W90** | PhiID 4-atom decomposition (W80 priority 4) | `PhiIdTest` (9 tests) | ✅ PASS — Mediano 2020 Gaussian PID |
| **W91** | This synthesis report + WAL/PROTOCOL/INDEX/HYPOTHESES sync | `docs-v2/research/W87-W91-FINAL-SYNTHESIS-REPORT.md` | ✅ committed |

Tests added: **27 new tests** (4 + 5 + 9 + 9), all passing.
Lines added (new code): 1,090 (ConsciousBrain, IntegrationMetrics, PhiId, tests).

---

## 2. W87 — Multi-Timestep Integration Metrics

### Motivation
Prior to W87, `ConsciousBrain.cycle()` computed integration metrics inside
a `cycleCount % 10 == 0 && cycleCount > 0` gate, which only emitted at
cycles 10, 20, 30, … Within each emit window, the trajectory buffer
recorded only the *current* observation (single state), so Φ_binary always
collapsed to 0 — there was no multi-timestep diversity in the discrete
8-bit state vector.

### Fix
- `appendTrajectory(observation)` is called on *every* cycle, populating a
  circular `long[8]` buffer (`TRAJECTORY_LEN=8`) with `extractBits(obs, 8)`.
- `computeIntegrationMetrics(observation)` reads back the buffer in
  chronological order (oldest → newest), expanding to a contiguous
  `long[actualLen]` of length `actualLen = min(trajectoryIdx, 8)`.
- `Φ_binary`, `ΦR`, `C_N` are then computed on the multi-state trajectory.
- `ΦF` distribution was sized `N+1 = 9` which violated the
  power-of-2 requirement of `IntegrationMetrics.phiF()` (it threw
  IllegalArgumentException). Fixed by using `nextPow2(N+1)` (matches
  `phiFFromBitLinear` convention): gives 16 bins for N=8.

### Empirical results (MultiTimestepIntegrationTest)

```
Multi-timestep means — Φ_binary=0.5134, ΦR=0.4921, C_N=0.4250   (Gaussian)
Tickling flags: true=0, false=20, null=0
Type PERIODIC:      Φ_binary=0.0000  (deterministic → single state)
Type SPARSE:        Φ_binary=0.0000  C_N=1.2093
Type RECURRENT:     Φ_binary=0.0000  C_N=1.2093
Type HIERARCHICAL:  Φ_binary=0.0000
Type GAUSSIAN:      Φ_binary=0.5134  ΦR=0.4921  C_N=0.4250
```

These are *real* measurements: the previously-gated single-state
integration was effectively NaN — every cycle had `metrics=null`.

### CONSTITUTION VI compliance
The metrics are mathematical measurements of integration, not claims of
phenomenal consciousness. The trajectory buffer is a memory structure;
integration values are substrate observables. Documented in test class
Javadoc.

---

## 3. W88 — Multi-Timestep Noise-Floor Re-Validation

### What changed vs W76
W76 (`W76EmpiricalValidationTest`) used a test-side `c % 10 == 0` filter
to skip most cycles and only emit metrics every 10th step. W88 re-runs
the same H-082 hypothesis battery against the new multi-timestep
ConsciousBrain *without* the filter.

### Empirical results (W88MultiTimestepNoiseFloorTest)

```
H-082a Φ_binary (signal-vs-noise):
  PERIODIC       0.0000    (deterministic → 1 unique state)
  SPARSE         0.0000
  RECURRENT      0.0000
  HIERARCHICAL   0.0000
  GAUSSIAN       0.6495    (multi-state diversity)

H-082b ΦR early vs late (50 cycles each):
  PERIODIC      early=0.0000  late=0.0000
  SPARSE        early=0.0000  late=0.0000
  RECURRENT     early=0.0000  late=0.0000
  HIERARCHICAL  early=0.0000  late=0.0000
  GAUSSIAN      early=0.5832  late=0.5917  (slight rise expected)

H-082c ΦF bounded [0,1]:  ΦF=1.0  for all (forward = backward by construction)

H-070 C_N non-negative across types:
  PERIODIC       0.0000   SPARSE/RECURRENT   0.7197
  HIERARCHICAL   0.0000   GAUSSIAN           0.2076
```

### Honest finding (revising H-081 / W80 priority 1)
W80's H-082a claim was: "structured patterns yield higher Φ_binary than
random noise." With **multi-timestep** 8-bit trajectories this is **refuted**:
- PERIODIC (deterministic) → 1 unique 8-bit state across 100 cycles →
  Φ_binary = 0 (marginal entropy is 0).
- GAUSSIAN (random) → many unique 8-bit states → Φ_binary > 0.

This is *not* a bug — Φ_binary measures **integration of distinct states**,
not signal-to-noise in the classical sense. The H-082 noise-floor claim
was honest for single-state metrics but is **inverted** for multi-timestep
metrics. To recover the original claim, we would need to either (a)
test pattern-specific predictability, or (b) compare same-state patterns
of different meta-structures (e.g., autocorrelated random vs iid random).

### CONSTITUTION VI compliance
The honest finding is recorded. H-081 partially refuted — the
multi-timestep formulation inverts the original H-082a claim.

---

## 4. W89 — Φ_linGauss Closed-Form Integration Metric

### Motivation
Discrete Φ_binary enumerates all 2^N bipartitions × 2^N states → O(2^N · 2^N).
For N=8 this is 65,536 bipartitions; for N=16 it's 4.3 billion.
Closed-form linear-Gaussian Φ (Barrett & Seth 2011) uses covariance
matrices and the ln-determinant identity:

```
I(A; B) = (1/2) ln(det(C_AA) · det(C_BB) / det(C_AB))
Φ_linGauss = min over bipartitions of I(A; B)
```

This is O(2^N · N³) — feasible for N ≤ 16 with full enumeration,
or analytically approximated beyond.

### Implementation
- `IntegrationMetrics.phiLinGauss(long[] trajectory, int N)` — binary
  trajectory → ±1 continuous samples → Pearson correlation matrix →
  enumerate masks 1..(1<<N)-1 → bipartition MI via LU ln(determinant).
- `IntegrationMetrics.phiLinGaussFromSamples(double[][] samples, int N)` —
  continuous variant.
- 1e-10 ridge on diagonal for numerical stability.
- N=1 short-circuits to 0 (no valid bipartition).

### Empirical results (PhiLinGaussTest, 9/9 PASS)

```
Independent random (256 samples, N=8):  Φ_linGauss = 0.0058   (very low)
Partial-corr trajectory (16 timesteps):  Φ_linGauss = 0.1309   (positive)
Constant trajectory:                    Φ_linGauss = 0.0      (singular)
Permutation invariance:                 Δ < 1e-6
phiLinGaussFromSamples ≡ phiLinGauss:   Δ < 1e-6 (binary case)
N=1 ⇒ 0 (no bipartition)
N=0 or N=17 ⇒ IllegalArgumentException
```

### CONSTITUTION VI compliance
Φ_linGauss is a closed-form mathematical integral over Gaussian
distributions — explicitly a measurement substrate, not a consciousness
claim. Documented in class Javadoc.

---

## 5. W90 — PhiID Integrated Information Decomposition

### Motivation
W80 priority 4 was PhiID (Mediano, Seth, Barrett 2020
arXiv:2004.13314v1), the bivariate/trivariate 4-atom decomposition of
integrated information. Without external JIDT dependency, we implement
the closed-form Gaussian variant directly in MATRIX.

### 4-atom decomposition

```
For variables (X, Y) with optional context Z:

  redundancy r   = I(X; Y) - I(X; Y|Z) (...); always ≥ 0
  synergy s       = |I(X; Y; Z)|  when co-info < 0; ≥ 0
  unqX (X → Y)   = unique info in X not in Y; ≥ 0
  unqY (Y → X)   = unique info in Y not in X; ≥ 0
                  ═══════
  I(X; Y)         = sum of four atoms  (information conservation)

For bivariate (no Z): r = I(X; Y), s = unqX = unqY = 0 (degenerate)
For trivariate: r/s/unqX/unqY split non-trivially by sign of co-info
```

### Three-tier API

1. `PhiId.bivariateGaussian(samples)` — 2-var (degenerate: all redundancy)
2. `PhiId.trivariateGaussian(samples)` — 3-var with full 4-atom
3. `PhiId.system(samples)` — N-var: averages all C(N,2) pairs
   using mean-of-rest as context proxy

### Closed-form formulas

```
ρ_{XY·Z} = (ρ_XY - ρ_XZ · ρ_YZ) / sqrt((1-ρ_XZ²)(1-ρ_YZ²))
I(X; Y)  = -0.5 · log(1 - ρ_XY²)
I(X;Y|Z) = -0.5 · log(1 - ρ_{XY·Z}²)
co-info  = I(X;Y) - I(X;Y|Z)
```

### Empirical results (PhiIdTest, 9/9 PASS)

```
Bivariate correlated (ρ=0.7):  r ≈ miXY ≈ 0.32,  s = unqX = unqY = 0
Bivariate independent:         r ≈ miXY ≈ 0,    s = unqX = unqY = 0
Trivariate redundant chain:    r = 4.68,        s = unqX = unqY = 0
Trivariate XOR-like synergy:   r > 0,  s > 0 (positive synergy atom)
Constant variables:            all atoms = 0  (degenerate covariance)
System-level (4 vars):         averages across 6 pairs, all atoms ≥ 0
Invalid samples (null/wrong shape): IllegalArgumentException
```

### CONSTITUTION VI compliance
PhiID is information-theoretic decomposition. The atoms measure *how*
information is shared (redundantly, synergistically, transfer-only),
not whether the system is conscious. Documented in class Javadoc.

---

## 6. Hypothesis Status (H-078..H-084)

See `docs-v2/research/HYPOTHESES-NEW.md` for the canonical entries.

| Hypothesis | Status | Evidence |
|------------|--------|----------|
| **H-078** TicklingDetector detects non-monotonic Φ-vs-surprise minima | running | W82 (TicklingDetectorTest 11/11 PASS) |
| **H-079** Noise-floor benchmark on 8-bit single-state ⇒ Φ_binary=0 | **CONFIRMED** | W85 (NoiseCeilingBenchmarkTest 5/5 PASS) |
| **H-080** ΦR detects genuine integration independent of marginal entropy | **CONFIRMED** | W83 (PhiR on HDC), W84 (multi-timestep) |
| **H-081** Structured > random in Φ_binary (W76 H-082a) | **PARTIALLY REFUTED** | W88 (multi-timestep inverts the claim) |
| **H-082** Multi-timestep trajectory ⇒ non-zero Φ for diverse inputs | **CONFIRMED** | W87 (MultiTimestepIntegrationTest 4/4) |
| **H-083** Φ_linGauss closed-form O(2^N · N³) computable for N ≤ 16 | **CONFIRMED** | W89 (PhiLinGaussTest 9/9) |
| **H-084** PhiID 4-atom decomposition for Gaussian triples/quartets | **CONFIRMED** | W90 (PhiIdTest 9/9) |

---

## 7. Architecture Diagram (post-W91)

```
                       ┌─────────────────────────────────────┐
                       │       ConsciousBrain (W77-W87)      │
                       │   integrated L7 capability (DESIGN-58)│
                       └──────────────────┬──────────────────┘
                                          │ cycle(obs)
                                          ▼
                  ┌───────────────────────────────────────────────┐
                  │              Multi-timestep trajectory        │
                  │           long[8] circular buffer             │
                  └──────────────────┬────────────────────────────┘
                                     │
            ┌────────────────────────┼────────────────────────┐
            ▼                        ▼                        ▼
   ┌────────────────┐    ┌────────────────┐    ┌────────────────┐
   │  Discrete Φ   │    │ Continuous Φ    │    │ PhiID         │
   │  (N ≤ 8)      │    │ (N ≤ 16)        │    │ (N ≥ 2)       │
   ├────────────────┤    ├────────────────┤    ├────────────────┤
   │ Φ_binary      │    │ Φ_linGauss      │    │ redundancy    │
   │ ΦR            │    │ (closed-form    │    │ synergy       │
   │ ΦF            │    │  covariance ln- │    │ unqX, unqY    │
   │ C_N           │    │  determinant)   │    │ system-level  │
   └────────────────┘    └────────────────┘    └────────────────┘
            │                        │                        │
            └────────────────────────┼────────────────────────┘
                                     ▼
                       ┌─────────────────────────┐
                       │  TicklingDetector (W82) │
                       │   Φ-vs-surprise minima  │
                       └─────────────────────────┘
                                     │
                                     ▼
                       ┌─────────────────────────┐
                       │   IntegrationMetricsResult │
                       │   r, s, Φ_binary, ΦR, ΦF,  │
                       │   C_N, ticklingFlag        │
                       └─────────────────────────┘
```

CONSTITUTION VI: metrics remain *measurement substrates*, never claims of
phenomenal consciousness. Each class Javadoc carries this disclaimer.

---

## 8. Open Threads / Future Work

The following items remain open and were not tackled in W87-W91:

1. **W76 hypothesis revalidation** with corrected noise-floor definition:
   the original H-082a claim needs to be reformulated for multi-timestep
   metrics. Suggested test: compare same-state patterns (autocorrelated
   random vs iid random) to recover signal-vs-noise intuition.

2. **Φ_linGauss with HDC vectors** as input — currently we treat each 8-bit
   state as ±1 scalar. With long[] HDC codes we could compute Φ on the
   raw 1024-bit code space (extends phiRFromHdcCodes to linear-Gaussian
   regime).

3. **PhiID for non-Gaussian / discrete systems** — current implementation
   is closed-form Gaussian only. For binary {0,1} systems the appropriate
   extension is the Icard / Finn / Mediano discrete PhiID formulas,
   which require explicit marginal entropy computations.

4. **ConsciousBrain integration of Φ_linGauss and PhiID** — currently
   the brain emits discrete metrics every cycle. Adding the continuous
   variants would be a 2-line change to computeIntegrationMetrics but
   adds O(2^N · N³) per cycle. Recommend gating to every 10 cycles.

5. **Cross-disciplinary synthesis (META-R1)** — W91 implements R-A
   (computational neuroscience / information theory) only. R-B
   (cybernetic / constructivist — Anokhin, Bernstein), R-C (Soviet
   / Asian — Glushkov, Nyaya), R-D (early learning neuroscience —
   Spelke, Kauffman), R-E (physical substrates — memristors,
   neuromorphic), R-F (mathematics of creativity — Kolmogorov,
   L-systems) remain underexplored for integration metrics specifically.

6. **JNI/FFM acceleration** — `logDeterminant` and bipartitionMi loops
   could be vectorised in C/avx2 for ~100× speed-up on N=16 systems.

---

## 9. Verification Ledger

| Command | Result | Criteria |
|---------|--------|----------|
| `./gradlew :matrix-core:test --tests "io.matrix.research.MultiTimestepIntegrationTest"` | 4/4 PASS | W87 met |
| `./gradlew :matrix-core:test --tests "io.matrix.research.W88MultiTimestepNoiseFloorTest"` | 5/5 PASS | W88 met |
| `./gradlew :matrix-core:test --tests "io.matrix.consciousness.PhiLinGaussTest"` | 9/9 PASS | W89 met |
| `./gradlew :matrix-core:test --tests "io.matrix.consciousness.PhiIdTest"` | 9/9 PASS | W90 met |
| `./gradlew :matrix-core:test --tests "io.matrix.neuron.ConsciousBrainTest"` | PASS (no regressions) | regression check |

Total new tests added: **27**, all green.

---

## 10. Files Modified / Created

### Created
- `matrix-core/src/test/java/io/matrix/research/W88MultiTimestepNoiseFloorTest.java` (W88)
- `matrix-core/src/test/java/io/matrix/consciousness/PhiLinGaussTest.java` (W89)
- `matrix-core/src/test/java/io/matrix/consciousness/PhiIdTest.java` (W90)
- `matrix-core/src/main/java/io/matrix/consciousness/PhiId.java` (W90)
- `docs-v2/research/W87-W91-FINAL-SYNTHESIS-REPORT.md` (W91 — this file)

### Modified
- `matrix-core/src/main/java/io/matrix/neuron/ConsciousBrain.java` (W87)
  — added `appendTrajectory`, split `computeIntegrationMetrics`, fixed ΦF bin size
- `matrix-core/src/main/java/io/matrix/consciousness/IntegrationMetrics.java`
  — added `phiLinGauss`, `phiLinGaussFromSamples`, `logDeterminant`,
    `correlationMatrix`, `bipartitionMi`, `subMatrix` (W89)
- `docs-v2/research/HYPOTHESES-NEW.md` (W91) — H-078..H-084 entries

### Commits
```
281b092c WAL: Wave 89 — Φ_linGauss closed-form integration metric
f7da75a4 WAL: Wave 90 — PhiID Integrated Information Decomposition
25e2cc32 WAL: Wave 88 — Multi-timestep noise-floor benchmark (H-082)
96ce195f WAL: Wave 87 — ConsciousBrain multi-timestep integration metrics
```

All four commits pushed to `origin/main`.

---

## 11. CONSTITUTION Compliance Audit

| Article | Compliance | Notes |
|---------|-----------|-------|
| I: Pure functions, no Random, no wall-clock in runtime | ✅ | All metrics deterministic; trajectory buffer is pure. |
| II: Determinism | ✅ | Same seed → same metric values (verified for all 4 new metrics). |
| III: (no runtime constraints) | ✅ | — |
| IV: Honest measurement | ✅ | H-082a "partially refuted" recorded; W88 noise-floor inversion documented. |
| V: (tests cover new code) | ✅ | 27 new tests covering all new metrics. |
| VI: Integration metrics are measurement substrates, not phenomenal consciousness | ✅ | Each test class has explicit Javadoc disclaimer. PhiID atoms are explicitly information-theoretic. |
| VII: (cross-disciplinary) | ✅ | This wave draws from Tononi 2004 BMC, Mediano 2020, Mediano 2022 PhiR, Barrett-Seth 2011. R-B..R-F threads remain open (see §8). |

---

## 12. Closing Remarks

The MATRIX deep-research-wave series has now extended the integration
metric suite from a single gated 4-metric set to a **two-tier
discrete/continuous formulation**:

- **Discrete tier** (Φ_binary, ΦR, ΦF, C_N): exact for binary systems of
  N ≤ 8 (or ≤ 16 for ΦF/C_N). Already integrated into ConsciousBrain.
- **Continuous tier** (Φ_linGauss, PhiID 4-atom): closed-form for Gaussian
  systems of N ≤ 16. Available as standalone metrics; ConsciousBrain
  integration deferred to a later wave per the recommendation in §8.

Total LOC added by W87-W90: ~1090 lines (mostly test code).
Total tests: 27 new (4+5+9+9), all green.
Hypotheses confirmed in this wave: H-082, H-083, H-084.
Hypotheses partially refuted: H-081 (W76 noise-floor inversion).
Hypotheses still running: H-069..H-080, H-085+ (TBD by future waves).

The Goal-Contract for "Multi-timestep integration metrics and deep
research W87-W91" is satisfied. The full task list:

- ✅ W87: MultiTimestepIntegrationTest 4/4 PASS
- ✅ W88: W88MultiTimestepNoiseFloorTest 5/5 PASS
- ✅ W89: PhiLinGaussTest 9/9 PASS
- ✅ W90: PhiIdTest 9/9 PASS
- ✅ W91: This synthesis report committed
