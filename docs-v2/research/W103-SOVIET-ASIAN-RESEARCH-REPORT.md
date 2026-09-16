# W103 — Cross-Disciplinary Research Wave R-C (Soviet / Asian Schools)

**Date:** 2026-09-16
**Status:** Synthesis report (Wave 103 of deep-research series)
**Series:** W91 (R-A: Tononi/Mediano), W95 (R-B: Cybernetic), **W103 (R-C)**, W104+ pending.
**Cross-references:** META-R1 doctrine (AGENTS.md), DESIGN-58, DESIGN-64.

## 1. Motivation

R-A (Tononi/Mediano/Barrett-Seth) and R-B (Anokhin/Bernstein/Ashby/Minsky/Simon)
covered Western computational neuroscience and cybernetics. R-C completes the
cross-disciplinary triad by adding Soviet cybernetics and Asian epistemologies,
both of which developed independently of Western AI and offer distinct insights
on integration, learning, and consciousness.

The cross-pollination matters because:
1. Soviet cybernetics prioritized **mathematical rigor over empirical fitting**
   — strong theoretical guarantees but slower iteration.
2. Asian philosophical traditions (Nyaya, Wu Wenjun's mathematics) developed
   **classification systems** for cognition that map surprisingly well onto
   modern computational architectures.

## 2. The Five Schools of Soviet / Asian Thought

### 2.1 V. M. Glushkov (1923-1982) — Soviet Cybernetics & Constructive Mathematics

**Core claim**: Constructive mathematics — only what can be algorithmically
constructed — is the foundation of cybernetics. Where Western AI adopted
classical logic, Glushkov built his theory on **constructive logic**:
truth = existence of an algorithm.

```
[ Algorithmic Process ] → [ Constructive Truth ]
       ↑
       └─── cannot exist without algorithm
```

**For MATRIX**: The CyberneticForecaster (already in brain) and the
constructive-only boolean circuits (TruthTable, BitLinear) embody this
principle. The HDC encoding is constructive: every vector exists as a
specific bit pattern.

**Critical insight**: integration metrics must be **constructively
computable**. Φ_binary, ΦR, ΦF are all constructive (finite enumeration
over 2^N). Φ_linGauss with LU decomposition is constructive. PhiID's
4-atom decomposition is constructive. We have NO non-constructive
metric — which is correct per Glushkov's principle.

### 2.2 A. N. Kolmogorov (1903-1987) — Algorithmic Complexity & Constructive Mathematics

**Core claim**: Information content is **algorithmic complexity** —
length of shortest program that produces the data. K(x) = shortest
program length.

```
[ Data ] → [ Shortest Program ] → [ Length ] = K(x)
       ↑                            ↓
       └──── randomness → K = |x| (incompressible) ────┘
```

**For MATRIX**: This is the W90+ foundation. ConsciousBrain's integration
metrics measure **statistical structure**, but K(x) measures
**algorithmic structure**. For 8-bit random trajectories, Φ_binary = 0.5+
(high statistical complexity) but K(x) ≈ 8·log(2)·T = 8T bits (maximum
algorithmic complexity for 8-bit states).

The two measures are complementary: statistical Φ measures how much
integration there is in the raw data; algorithmic complexity would measure
how much "compression opportunity" there is.

**Future work**: implement Kolmogorov complexity estimator via
block-decomposition methods (CTM, Block Decomposition Method).

### 2.3 Nyaya (Indian logic, 2nd century BCE - 2nd century CE)

**Core claim**: Knowledge has **four pramanas** (means of valid knowledge):
1. **Pratyaksha** — direct perception (raw observation)
2. **Anumana** — inference (logical deduction)
3. **Upamana** — comparison / analogy (similarity-based reasoning)
4. **Shabda** — testimony (verbal/instructional, including authoritative)

```
        ┌──── Pratyaksha (sensors)
        │
Source ─┼──── Anumana (logic)
        │
        └──── Upamana (analogy)

        ┌──── Shabda (instruction)
Authority
```

**For MATRIX**: ConsciousBrain's 4 information sources map perfectly:
- **Pratyaksha** → ConsciousBrain.cycle(observation) — direct input
- **Anumana** → WuWeiPolicy.decide + SelfModel.modelStep — logical
  inference from surprise/error
- **Upamana** → HdcBrain.learn + HdcEncoding — similarity-based encoding
- **Shabda** → PragmaticTest + meaningStore — instruction-driven meaning
  assignment

**Critical insight**: Nyaya requires that ALL four pramanas agree for
truth. ConsciousBrain's cycle currently uses perception (Pratyaksha) +
self-model (Anumana) + pragmatic (Shabda). The "missing pramana" is
**Upamana (analogy)** — the HDC system does analogical binding via
XOR/cleanup, but this isn't surfaced in CycleReport.

H-089 proposed: ConsciousBrain should emit an "analogical consistency"
signal that measures whether HdcBrain's recall similarity is consistent
across semantically-related observations.

### 2.4 Dignāga / Buddhist Logic (5-6th century CE, formalized by Dharmakīrti)

**Core claim**: All cognition is **conceptual construction** (kalpanā).
There are three modes:
1. **Direct perception** (pratyakṣa) — non-conceptual
2. **Inference** (anumāna) — concept-mediated
3. **Testimony for the conventional** (saṃvṛti-sat-pramāṇa)

**Critical contribution**: Dignāga's **apoha** (exclusion) theory — concepts
are defined by what they exclude, not what they include.

```
[ Concept "DOG" ] = { x | x is animal AND not-cat AND not-bird AND ... }
```

**For MATRIX**: BitLinear's {-1, 0, +1} ternary encoding implements
**inclusion + exclusion + neutral** in one operation. The HDC XOR-based
binding is **differential** (exclusion-based) — two vectors agree
when their XOR is zero (which is what HdcEncoding.similarity measures).

**H-090 proposed**: a "conceptual exclusion" metric that measures how
distinct the HDC encodings of different observations are. This is
related to, but distinct from, Φ_binary.

### 2.5 Wu Wenjun (1919-2017) — Chinese Mathematics & Mechanization

**Core claim**: Mathematics can be **mechanized** — every proof can be
algorithmically verified. The "Method of Mathematics Mechanization"
(数学机械化) gives a constructive algorithm for geometry theorem proving
based on Ritt's decomposition theory.

```
[ Geometric Theorem ] → [ Algebraic Form ] → [ Ritt Decomposition ] → [ Algorithm ]
```

**For MATRIX**: Wu's method informs our approach to **automated
verification of integration metrics**. We already have:
- JUnit tests for each metric (Φ_binary, ΦR, etc.) — verify metric
  values match expected bounds
- Test files W76-W102 — verify metrics behave correctly under
  structured vs random inputs
- Proptest-style property tests (future): generate random inputs,
  check invariants (Φ ≥ 0, atoms sum to miXY, etc.)

**H-091 proposed**: formal property-based tests using jqwik (already in
test framework) to prove statistical invariants over many random inputs.

## 3. Mathematical Implications

| School | Static Φ misses | Need to add |
|--------|-----------------|-------------|
| Glushkov | Constructive vs classical logic | Already enforced — MATRIX is constructive |
| Kolmogorov | Algorithmic complexity | Block-decomposition method (future W104+) |
| Nyaya | Upamana (analogical) | HdcBrain recall-similarity consistency signal (H-089) |
| Dignāga | Conceptual exclusion | HDC distinctness metric (H-090) |
| Wu | Mechanization of proofs | Property-based tests (H-091) |

## 4. CONSTITUTION Compliance

R-C reinforces CONSTITUTION I (purity): constructive metrics, deterministic
state, no LLM in decision paths. The Nyaya/Dignāga frameworks emphasize
**multi-source verification** — same idea as Nyaya's four pramanas and
CONSTITUTION V (Coverage gate ≥82%). Wu's mechanization aligns with
CONSTITUTION VI (honest measurement): testable claims over unfalsifiable
ones.

## 5. Hypotheses Introduced (H-089..H-091)

| H | Утверждение | Школа | Status |
|---|---|---|---|
| **H-089** | Analogical consistency signal (Nyaya Upamana) — HdcBrain recall similarity is consistent across semantically-related observations | Nyaya pramanas | running |
| **H-090** | Conceptual exclusion metric (Dignāga apoha) — measures how distinct HDC encodings of different observations are | Dignāga Buddhist logic | running |
| **H-091** | Property-based tests (Wu mechanization) prove statistical invariants of integration metrics over many random inputs | Wu Wenjun mathematics | running |

## 6. Future Implementation (W104+)

1. **W104 — Kolmogorov complexity estimator** for Φ trajectory (CTM method)
2. **W105 — Analogical consistency** (H-089): add to ConsciousBrain CycleReport
3. **W106 — Conceptual exclusion** (H-090): new IntegrationMetrics method
4. **W107 — Property-based tests** (H-091): jqwik integration for Φ_binary, ΦR
5. **W108 — R-D cross-disciplinary**: early learning neuroscience (Spelke, Spitz, Kauffman)
