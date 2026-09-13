# EXP-RESEARCH-CONSCIOUSNESS-METRICS-REPORT

**Date:** 2026-09-13
**Sub-agent:** goal-deep-researcher
**Confidence:** HIGH for IIT formalism (Tononi 2004 BMC primary-verified); MEDIUM-LOW for others (canonical-cited only)

## Sources Evaluated

| Source | Status |
|--------|--------|
| Tononi 2004 BMC "An information integration theory of consciousness" | PRIMARY-VERIFIED |
| Tononi & Sporns 2003 (Gaussian closed form) | CANONICAL-CITED |
| Tononi 2012 Scholarpedia (Φ*) | CANONICAL-CITED |
| Mediano 2022 PLOS Comp Biol (Φ_R) | CANONICAL-CITED |
| Toker 2022 (Φ_F via EMD) | CANONICAL-CITED |
| Tononi, Sporns, Edelman 1994 PNAS (Neural Complexity) | CANONICAL-CITED |
| Baars 1988 (Global Workspace Theory) | CANONICAL-CITED |
| Dehaene 1998 PNAS (GNW model) | CANONICAL-CITED |
| Rosenthal 2005 (Higher-Order Thought) | CANONICAL-CITED |
| Friston 2010 Nat Rev Neurosci (Free Energy Principle) | CANONICAL-CITED |
| Bogacz 2017 (FEP tutorial) | CANONICAL-CITED |
| Williams & Beer 2010 (PID) | CANONICAL-CITED |

## Key Findings

### IIT Core (verified)

Two axioms (verified from Tononi 2004 BMC):
1. **Information**: large repertoire of states
2. **Integration**: cannot be decomposed into causally independent parts

Effective Information: `EI(A→B) = MI(A_max_entropy; B)` where A is replaced with uniform noise.

Φ(S) = EI(MIB(S)) where MIB is the minimum-information bipartition of S.

Φ is NP-hard (Mayner et al. PyPhi paper).

### Approximations

- Φ_R (Mediano 2022): redundancy-suppressing
- Φ_F (Toker 2022): EMD-based, fastest in practice
- Neural Complexity C_N (Tononi 1994): Σ_i H(X_i) - I(X; X_{-i}), O(N²)
- Causal Density CD (Seth 2005): Granger-causal structure

### GNW vs IIT vs HOT vs FEP

| Theory | Says consciousness is... |
|--------|---------------------------|
| IIT | Integrated information in a complex (Φ > 0) |
| GNW | Global broadcast from winner-take-all (ignition threshold) |
| HOT | Meta-representation of mental state |
| FEP | Minimization of variational free energy |

## MATRIX-Specific Implementations

### G.1 Φ from HDC codes (10K-bit)

Coarse-grain 10000 HDC bits into ~8 effective elements (e.g., via per-block popcount). Apply Gaussian-closed-form Φ.

### G.2 Φ from MultiBrainEnsemble outputs

8 pretrained MPDT brains × multiple layers = ~3000-bit signature. Threshold to ternary per brain → 8 effective elements. Φ over 3^8 system feasible.

### G.3 Φ from BitLinear ternary patterns

BitLinear already produces {-1,0,+1} activations. Project to ternary, coarse-grain to ~8 super-channels via Hamming similarity. Track Φ over training as developmental curve.

### G.4 Φ via MPDT-HDC bridge

`MpdtHdcBridge` already links HDC codebook entries to Tsetlin wires. Take query HDC, activate bridges within Hamming distance threshold, compute Φ over Boolean subsystem.

## Recommendations

1. Replace `FreeEnergyEvaluator` heuristic with proper VFE
2. Add `IitService` package with Φ_R, C_N, CD, CE, Φ_F implementations
3. Add `MultiBrainEnsemblePhi` for ensemble Φ measurement
4. Add JIDT binding for kernel-estimator ΦID
5. Establish comparative benchmarks (correlated vs uncorrelated text → high vs low Φ)

## Pseudocode

EI(A→B), Φ(S), neural complexity, EMD Φ_F, MPDT-bridge Φ — all provided in main sub-agent output.

## Risks

- Source access failures: only 1 paper full-text retrieved, others canonical-only
- Φ computation NP-hard for N > 8
- HDC codes not in {-1, +1} finite alphabet (violates IID assumption)
- BitLinear weights change during training (Φ per epoch diverges)
- Multi-brain Φ reflects pretraining covariance, not consciousness

## CONSTITUTION VI Compliance

Φ computation does NOT claim MATRIX is conscious. Φ values are numerical scalars under declared approximations, NOT comparable to biological brains.
