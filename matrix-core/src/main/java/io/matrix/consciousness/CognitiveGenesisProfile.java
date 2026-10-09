package io.matrix.consciousness;

import java.util.Objects;

/**
 * W111 — Cognitive genesis profile: unified cross-disciplinary synthesis.
 *
 * <p>After 25+ waves of cross-disciplinary research, MATRIX cognitive
 * architecture now integrates insights from:
 *
 * <ul>
 *   <li>PhiID (Mediano-Seth-Barrett 2020) — 4-atom decomposition</li>
 *   <li>Phi_linGauss (Barrett-Seth 2011) — closed-form Gaussian Φ</li>
 *   <li>PhiR (Mediano 2022) — redundancy-suppressing integration</li>
 *   <li>PhiF (Tononi 2008) — feedback integration</li>
 *   <li>CrossLevelPhi (Bernstein 1947) — between-level coordination</li>
 *   <li>StabilityPhi (Ashby 1960) — variance/ultrastability</li>
 *   <li>InterAgentPhi (Minsky 1986) — between-agent integration</li>
 *   <li>Kolmogorov complexity (Rissanen-Langdon 1981) — algorithmic info</li>
 *   <li>Analogical consistency (Nyaya Upamana) — comparison/analogy</li>
 *   <li>Conceptual exclusion (Dignāga apoha) — differentiability</li>
 *   <li>NK Boolean networks (Kauffman 1969/1993) — edge of chaos</li>
 *   <li>Memristor dynamics (Chua 1971/HP 2008) — physical substrate</li>
 *   <li>L-systems (Lindenmayer 1968) — generative grammar</li>
 * </ul>
 *
 * <p>This record unifies the snapshot view of one cognitive cycle into a
 * single queryable structure. Use CognitiveGenesisProfile.fromCycleReport()
 * to construct it from a ConsciousBrain cycle.
 *
 * <p>CONSTITUTION VI compliance: integration substrate for measurement,
 * not a phenomenal consciousness claim.
 */
public record CognitiveGenesisProfile(
    // Integration metrics (W87-W99)
    double phiBinary,
    double phiF,
    double phiR,
    double phiLinGauss,
    double interAgentPhi,
    double stabilityPhi,
    double crossLevelPhi,
    // Algorithmic complexity (W104)
    double kolmogorovK,
    // Cross-disciplinary metrics (W105-W106)
    double analogicalSimilarity,
    double conceptualExclusion,
    // Dynamical regime (W108-W109-W110)
    int nkEdgeOfChaosK,
    double memristorConductance,
    double lSystemComplexityRatio
) {
    public CognitiveGenesisProfile {
        // Validate ranges where applicable
        Objects.requireNonNull(Double.valueOf(phiBinary), "phiBinary");
    }

    /**
     * Compute a unified complexity score, weighted combination of all
     * information-content measures.
     *
     * <p>RECON-W34.9: the previous version divided eight components by 8 while clamping only
     * {@code kolmogorovK}. The javadoc claimed "each component normalized to [0, 1] roughly"
     * -- and the word "roughly" was load-bearing, because nothing enforced it. A component
     * above 1.0 pushed the mean above 1.0, and
     * {@code CognitiveGenesisProfilePropertyTest.propertyUnifiedScoreBounded} caught it at
     * <b>1.0816</b>.</p>
     *
     * <p>Every component is now clamped to [0, 1]. That makes the documented contract true
     * rather than approximate, and it matters because callers use this score comparatively:
     * {@code W174EmpiricalBenchmark} asserts it lands in [0, 1] over real cycles, so a value
     * above 1 breaks that contract for everyone downstream.</p>
     *
     * <p>The clamping is per-component rather than on the final sum, so one oversized input
     * cannot drag the others toward saturation. That is the same reasoning already applied to
     * {@code kolmogorovK}, now applied consistently.</p>
     *
     * @return a complexity score in [0, 1]
     */
    public double unifiedComplexityScore() {
        double phiScore = unit(phiBinary + phiF + phiR + phiLinGauss, 4.0);
        return (phiScore
                + unit(interAgentPhi, 1.0)
                + unit(stabilityPhi, 1.0)
                + unit(crossLevelPhi, 1.0)
                + Math.min(1.0, kolmogorovK / 100.0)
                + unit(analogicalSimilarity, 1.0)
                + unit(conceptualExclusion, 1.0)
                + unit(lSystemComplexityRatio, 1.0)) / 8.0;
    }

    /**
     * Clamp a normalised component into [0, 1].
     *
     * <p>Unit: a value already expressed in the target range. Non-finite input collapses to 0
     * rather than propagating NaN through a score that callers compare against 1.</p>
     *
     * @param value  the component value
     * @param divisor factor by which to scale the value first
     * @return the value clamped to [0, 1], or 0.0 when it is not finite
     */
    private static double unit(double value, double divisor) {
        double scaled = divisor == 0.0 ? 0.0 : value / divisor;
        if (Double.isNaN(scaled) || Double.isInfinite(scaled)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, scaled));
    }

    /**
     * Classify the cognitive regime into one of:
     * - FROZEN: low Φ, low K, high stability
     * - EDGE_OF_CHAOS: balanced Φ, moderate K, moderate stability
     * - CHAOTIC: high Φ, high K, low stability
     */
    public String regime() {
        double avgPhi = (phiBinary + phiF + phiR) / 3.0;
        double avgStability = stabilityPhi;
        if (avgStability > 0.7 && avgPhi < 0.3) return "FROZEN";
        if (avgStability < 0.3 && avgPhi > 0.7) return "CHAOTIC";
        return "EDGE_OF_CHAOS";
    }
}
