# DESIGN-62 — PatternGenerator & Empirical Validation

**Date:** 2026-09-14
**Status:** Implemented and tested
**Capability Level:** L7 (Conscious Integration)
**Cross-references:** W76-EMPIRICAL-VALIDATION-REPORT.md, W76-EMPIRICAL-VALIDATION-RESULTS.md, DESIGN-61-integration-metrics.md, HYPOTHESES-NEW.md (H-082)

## 1. Motivation

The W69-W72 integration metrics work provided a measurement substrate,
but the empirical validation was lacking:
- Used random Gaussian noise as the observation pattern
- Couldn't falsify H-082 (integration metrics track non-random patterns)
- Had no reproducible way to test consolidation effects

**PatternGenerator** provides structured inputs that allow systematic
empirical validation of integration hypotheses (H-070, H-082, etc.)

## 2. RUN 479 — PatternGenerator

Five pattern generators anchored to five cross-disciplinary schools:

| Pattern | School | Inspiration |
|---------|--------|--------------|
| `periodic` | R-A (SOTA ML): periodic signals | sensory stimuli |
| `sparse` | R-D (Neuroscience): Spelke core knowledge, ~3% firing rate | BitNet b1.58 sparsity + biology |
| `recurrent` | R-B (Cybernetics): Anokhin/Bernstein feedback | memory consolidation |
| `hierarchical` | R-C (Soviet): Glushkov associative memory | concepts / categories |
| `gaussian` | (baseline) | IID noise floor |

## 3. Pattern Implementations

```java
// All are pure functions of (dims, parameters, Random)
public static float[] periodic(int dims, double freq, double phase, double amp, Random rng)
public static float[] sparse(int dims, double density, Random rng)
public static float[] recurrent(float[] prev, float delta, Random rng)
public static float[] hierarchical(int dims, int nClusters, int clusterSize, Random rng)
public static float[] gaussian(int dims, double mean, double std, Random rng)
public static float[] generate(Type type, int dims, Random rng)
public static float[][] generateTrajectory(Type type, int dims, int length, float delta, Random rng)
```

## 4. W76EmpiricalValidationTest

Four hypothesis assertions:

- **H-082a**: Structured patterns produce higher Φ_binary than noise
- **H-082b**: Recurrent patterns show Φ_binary rising from baseline to post-consolidation
- **H-082c**: ΦF does NOT rise with consolidation (negative prediction)
- **H-070**: Consolidation reduces surprise for structured patterns

5 trials × 4 pattern types × 100 cycles = 2 000 cycle-observations per run.

5 tests, 0 failures.

## 5. CONSTITUTION VI Compliance

PatternGenerator produces controlled inputs that make integration
metrics **empirically falsifiable**. The metrics themselves remain
CONSTITUTION VI compliant: measurement substrate, not phenomenal claims.

Per the W76 report gaps:
- Φ_binary only sees first 8 bits (limit of N=8)
- ConsciousBrain uses observation-as-prediction (no real generative model)
- "Surprise" is computed against observation itself (baseline 0)

## 6. Files

- `matrix-core/src/main/java/io/matrix/research/PatternGenerator.java` (RUN 479)
- `matrix-core/src/test/java/io/matrix/research/PatternGeneratorTest.java` (13 tests)
- `matrix-core/src/test/java/io/matrix/research/W76EmpiricalValidationTest.java` (5 tests)
- `docs-v2/research/W76-EMPIRICAL-VALIDATION-REPORT.md` (sub-agent research)
- `docs-v2/research/W76-EMPIRICAL-VALIDATION-RESULTS.md` (initial findings)

## 7. Future Work

1. **Larger pattern slice**: 16 bits instead of 8 would let Φ_binary
   show more differentiation between structured and random patterns
2. **Predictive model**: ConsciousBrain uses observation-as-prediction;
   adding a real generative model would let H-070 be properly tested
3. **PredictiveGenerator**: Trained world model that produces predictions
4. **More pattern types**: spike trains, oscillatory, chaos
5. **Multi-agent integration**: MultiBrainEnsemble Φ across 8 brains
