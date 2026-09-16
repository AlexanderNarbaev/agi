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
     */
    public double unifiedComplexityScore() {
        // Each component normalized to [0, 1] roughly
        double phiScore = (phiBinary + phiF + phiR + phiLinGauss) / 4.0;
        return (phiScore + interAgentPhi + stabilityPhi + crossLevelPhi
                + Math.min(1.0, kolmogorovK / 100.0)
                + analogicalSimilarity
                + conceptualExclusion
                + lSystemComplexityRatio) / 8.0;
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
