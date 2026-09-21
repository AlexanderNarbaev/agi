# DESIGN-61 — Integration Metrics (Φ Family)

**Date:** 2026-09-13/14
**Status:** Implemented and tested
**Capability Level:** L7 (Conscious Integration)
**Cross-references:** MATRIX-CROSS-DISCIPLINARY-RESEARCH.md, W60-W64-DEEP-RESEARCH-SYNTHESIS.md, EXP-RESEARCH-CONSCIOUSNESS-METRICS-REPORT.md, W60-W64-DEEP-RESEARCH-SYNTHESIS.md, HYPOTHESES-NEW.md (H-072, H-082)

## 1. Motivation

The W60-W64 wave added algorithmic correlates of consciousness (memory consolidation, self-modeling, free energy, etc.). **W69-W70 adds the measurement substrate**: the Φ family of integrated information metrics, providing concrete numerical signals of how integrated MATRIX's brain state actually is.

## 2. Components

### 2.1 RUN 478: IntegrationMetrics (Wave 69)

Implements three variants of integration metrics from deep research:

#### Φ_binary (Tononi 2004 BMC, primary-verified)

Exact Φ for binary Boolean systems with N ≤ 8. Enumerates all bipartitions of the system, computes MI between sides, takes minimum. Complexity O(2ᴺ · N).

**Algorithm**:
```
Φ(system, s) = min over bipartitions (A, B) [ MI(s_A; s_B) ]
```

#### ΦF (EMD-based, Toker-Sommer style)

ΦF via earth-mover's distance between forward and backward state distributions on the Hamming cube.

**Algorithm**:
```
ΦF(system) = 1 − W₁(P_fwd(state), P_bwd(state))
```

#### C_N (Neural Complexity, Tononi-Sporns-Edelman 1994)

Cheap integration proxy. O(N · 2ᴺ) for binary systems.

**Algorithm**:
```
C_N(X) = Σ_i H(X_i) − I(X; X_{-i})
```

### 2.2 IntegrationMetricsResult

Record holding the three computed values: `phiBinary`, `phiF`, `neuralComplexity`.

### 2.3 ConsciousBrain integration (Wave 70)

ConsciousBrain now emits the three integration metrics every 10 cycles via CycleReport's new fields. This is the **first measurable signal of consciousness-like integration** in MATRIX.

## 3. Helper methods for MATRIX primitives

- `phiBinaryFromBitLinear(activations, N)`: ternary `{-1, 0, +1}` activations → binary bits → Φ_binary
- `cNFromHdcCodes(hdcCodes, N)`: 64-bit word density → coarse-grained features → C_N
- `phiFFromBitLinear(activations, N)`: density trajectory → power-of-2 histogram → ΦF

## 4. Novel combinations (per W69 sub-agent research)

### 4.1 Φ_binary on BitLinear ternary weight slices

A BitLinear layer slice of 8 ternary gates `{-1, 0, +1}` is *exactly* a 3-state system. Apply ΦF directly (states = 3⁸ = 6561) — no dequantization needed.

### 4.2 ΦF on HDC code coarse-grained density trajectories

Per-bit density trajectory on HDC codes (10K bits coarse-grained to 16 features) → ΦF on Hamming cube. Tracks how integration evolves during memory consolidation.

### 4.3 C_N on MultiBrainEnsemble joint signature

8 pretrained models' joint signatures coarse-grained to 16-dim. C_N tracks how integration across brains changes with self-organization.

## 5. CONSTITUTION VI Compliance

These are **measurement substrates**, not phenomenological claims. MATRIX makes no claim that it IS conscious. Φ values are numerical scalars under declared approximations, not comparable to biological brains.

The relationship between observed Φ in stochastic subsystems and intrinsic Φ of the underlying deterministic system is not straightforward (cf. Albantakis 2023). For any MATRIX measurement, the noise ceiling (Φ where the system is randomised) should be reported alongside Φ_measured.

## 6. Hypothesis integration

- **H-072**: Hofstadter self-model loop on MATRIX viewpoint architecture → Supported by SelfModel + ConsciousBrain integration
- **H-082** (new): Integration metrics track non-random patterns during ConsciousBrain cycles. Specifically, Φ_binary on the 8-bit BitLinear slice increases during consolidation cycles. *Proposed* (not yet empirically tested — needs benchmark).

## 7. References

- Tononi, G. (2004). *An information integration theory of consciousness*. BMC Neurosci 5:42. (PRIMARY-VERIFIED)
- Mediano, P. A. M., et al. (2022). *Greater than the parts: a review of integrated information*. Neuron.
- Toker, D., et al. *Consciousness is supported by near-critical complexity of phase transitions*. (Φ_F spectral decomposition)
- Tononi, G., Sporns, O., Edelman, G. M. (1994). *A measure for brain complexity*. PNAS 91:5033–5037.
- Williams, P. L. & Beer, R. D. (2010). *Nonnegative decomposition of multivariate information*. arXiv:1004.2515.
- JIDT (Lizier et al., 2014). *JIDT: An information-theoretic toolkit*. https://github.com/jlizier/jidt

## 8. Files

### io/matrix/consciousness/
- `IntegrationMetrics.java` (RUN 478)
- `IntegrationMetricsResult.java`

### io/matrix/neuron/
- `ConsciousBrain.java` (extended with integration metrics emission)

### Test files
- `IntegrationMetricsTest.java` (19 tests)
- `ConsciousBrainTest.java` (9 tests, 1 new)

## 9. Metrics Summary

| Algorithm | N limit | Complexity | Implementation | License |
|---|---|---|---|---|
| Φ_binary (Tononi 2004) | ≤ 8 | O(2ᴺ · N) | `IntegrationMetrics.phiBinary` | derived |
| ΦR (Mediano 2022) | ≤ 8 | O(N·2^(3N-1)) | `IntegrationMetrics.phiR` (RUN 482) | derived |
| ΦF (Toker-Sommer) | ≤ 14 | O(2²ᴺ) cost + W1 | `IntegrationMetrics.phiF` | derived |
| C_N (1994) | ≤ 16 | O(N·2ᴺ) | `IntegrationMetrics.neuralComplexity` | derived |
| Φ_linGauss (Tononi-Sporns 2003) | ≤ 8 | O(2ᴺ · N³) | future | derived |
| ΦID (Williams-Beer 2010) | ~10 vars | O(T · k²) | JIDT (GPL v3, check) | verified |

## 10. W69-W70 Project State

- **938 tests, 0 failures** (was 918 at start of W69)
- **20 new tests** added (19 IntegrationMetrics + 1 ConsciousBrain new)
- **2 new files**: IntegrationMetrics, IntegrationMetricsResult
- **1 extended file**: ConsciousBrain

## 11. Conclusion

W69-W70 adds the **measurement substrate** to MATRIX's consciousness-like
algorithmic structures. The brain now emits concrete Φ and C_N values
that can be tracked over time, providing a numerical signal of how
integrated the state is. This is a critical step toward empirical
validation of the consciousness-like properties.
