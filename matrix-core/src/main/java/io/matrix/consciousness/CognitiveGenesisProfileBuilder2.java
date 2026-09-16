package io.matrix.consciousness;

import io.matrix.neuron.ConsciousBrain.CycleReport;
import java.util.ArrayList;
import java.util.List;

/**
 * W119 — Cognitive genesis profile builder v2 (with analogical/exclusion).
 *
 * <p>Extension of CognitiveGenesisProfileBuilder that takes optional
 * analogical-consistency and conceptual-exclusion measurements as inputs.
 * These can be supplied from prior cycles' trajectories (comparing the
 * current cycle's trajectory with stored exemplars).
 *
 * <p>CONSTITUTION VI compliance: derivation of measurement substrate
 * from actual brain cycle output.
 */
public final class CognitiveGenesisProfileBuilder2 {

    private CognitiveGenesisProfileBuilder2() {}

    /**
     * Build a profile from a cycle report + trajectory + comparison data.
     *
     * @param report the cycle report
     * @param trajectory recent trajectory (used for K, stability)
     * @param storedExemplar trajectory to compare against (for analogical sim.)
     * @param alternativeExemplar second trajectory (for conceptual exclusion)
     * @return a CognitiveGenesisProfile with all cross-disciplinary metrics
     */
    public static CognitiveGenesisProfile build(
            CycleReport report,
            long[] trajectory,
            long[] storedExemplar,
            long[] alternativeExemplar) {
        // Core integration metrics
        double phiBinary = nullToZero(report.phiBinary());
        double phiF = nullToZero(report.phiF());
        double phiR = nullToZero(report.phiR());
        Double phiLG = report.extended() != null ? report.extended().phiLinGauss() : null;
        double phiLinGauss = phiLG != null ? phiLG : 0.0;

        // Stability via StabilityPhi
        double stability = 0.0;
        if (trajectory != null && trajectory.length > 0) {
            List<Double> vals = new ArrayList<>();
            for (long v : trajectory) vals.add((double) v);
            int window = Math.min(4, vals.size());
            if (window >= 2) {
                stability = StabilityPhi.isUltrastable(vals, window, 1e10) ? 1.0 : 0.0;
            }
        }

        // Cross-level from extended
        double crossLevel = 0.0;
        if (report.extended() != null) {
            Double red = report.extended().phiIdRedundancy();
            Double syn = report.extended().phiIdSynergy();
            if (red != null && syn != null) crossLevel = red + syn;
        }

        // Inter-agent (default 0)
        double interAgent = 0.0;

        // Kolmogorov complexity
        double kK = trajectory != null ? KolmogorovComplexity.estimate(trajectory) : 0.0;

        // Analogical consistency computed against stored exemplar
        double analogical = (trajectory != null && storedExemplar != null
            && trajectory.length > 0 && storedExemplar.length > 0)
            ? AnalogicalConsistency.bitSimilarity(trajectory, storedExemplar)
            : 0.5;

        // Conceptual exclusion between current and alternative
        double exclusion = (trajectory != null && alternativeExemplar != null
            && trajectory.length > 0 && alternativeExemplar.length > 0)
            ? ConceptualExclusion.compositeExclusion(trajectory, alternativeExemplar)
            : 0.5;

        // NK edge-of-chaos K
        int nkK = NKBooleanNetwork.edgeOfChaosK(8);

        // Memristor conductance (mid-state)
        double memristorG = MemristorSwitch.conductance(0.5);

        // L-system baseline
        double lSystemRatio = 1.0;

        return new CognitiveGenesisProfile(
            phiBinary, phiF, phiR, phiLinGauss,
            interAgent, stability, crossLevel,
            kK, analogical, exclusion,
            nkK, memristorG, lSystemRatio
        );
    }

    private static double nullToZero(Double d) {
        return d == null ? 0.0 : d;
    }
}
