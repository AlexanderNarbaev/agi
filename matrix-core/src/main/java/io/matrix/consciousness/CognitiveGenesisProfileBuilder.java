package io.matrix.consciousness;

import io.matrix.neuron.ConsciousBrain.CycleReport;
import java.util.ArrayList;
import java.util.List;

/**
 * W112 — Cognitive genesis profile builder (consumes real CycleReports).
 *
 * <p>Bridges the gap between CognitiveGenesisProfile (the unified record
 * type) and ConsciousBrain.CycleReport (the actual data produced by the
 * brain cycle). The builder computes derived metrics that aren't stored
 * in CycleReport but are required by the profile.
 *
 * <p>CONSTITUTION VI compliance: derivation of measurement substrate
 * from the actual brain cycle output.
 */
public final class CognitiveGenesisProfileBuilder {

    private CognitiveGenesisProfileBuilder() {}

    /**
     * Build a CognitiveGenesisProfile from a real ConsciousBrain cycle.
     * Missing data is represented as 0.0 (the profile is robust to partial cycles).
     *
     * @param report the actual cycle report from ConsciousBrain
     * @param trajectory recent trajectory (used for K, stability)
     * @param interAgentSnapshotHashes recent inter-agent snapshot hashes
     */
    public static CognitiveGenesisProfile fromCycleReport(
            CycleReport report,
            long[] trajectory,
            long[] interAgentSnapshotHashes) {
        // Core integration metrics from cycle report
        double phiBinary = nullToZero(report.phiBinary());
        double phiF = nullToZero(report.phiF());
        double phiR = nullToZero(report.phiR());
        Double phiLG = report.extended() != null ? report.extended().phiLinGauss() : null;
        double phiLinGauss = phiLG != null ? phiLG : 0.0;
        // Stability via StabilityPhi.isUltrastable on the trajectory values
        double stability = 0.0;
        if (trajectory != null && trajectory.length > 0) {
            List<Double> vals = new ArrayList<>();
            for (long v : trajectory) vals.add((double) v);
            int window = Math.min(4, vals.size());
            if (window >= 2) {
                stability = StabilityPhi.isUltrastable(vals, window, 1e10) ? 1.0 : 0.0;
            }
        }
        // Cross-level: from extended metrics (redundancy + synergy)
        double crossLevel = 0.0;
        if (report.extended() != null) {
            Double red = report.extended().phiIdRedundancy();
            Double syn = report.extended().phiIdSynergy();
            if (red != null && syn != null) crossLevel = red + syn;
        }
        // Inter-agent: mean of snapshot hashes mapped to [0,1]
        double interAgent = meanHashNormalized(interAgentSnapshotHashes);
        // Kolmogorov complexity of the trajectory
        double kK = trajectory != null ? KolmogorovComplexity.estimate(trajectory) : 0.0;
        // Cross-disciplinary metrics default to mid-range when no comparison available
        double analogical = 0.5;
        double exclusion = 0.5;
        // NK edge-of-chaos K (canonical for any system)
        int nkK = NKBooleanNetwork.edgeOfChaosK(8);
        // Memristor conductance (default mid-state if not measured)
        double memristorG = MemristorSwitch.conductance(0.5);
        // L-system complexity ratio: 1.0 baseline
        double lSystemRatio = 1.0;

        return new CognitiveGenesisProfile(
            phiBinary,
            phiF,
            phiR,
            phiLinGauss,
            interAgent,
            stability,
            crossLevel,
            kK,
            analogical,
            exclusion,
            nkK,
            memristorG,
            lSystemRatio
        );
    }

    private static double nullToZero(Double d) {
        return d == null ? 0.0 : d;
    }

    /**
     * Normalize a series of snapshot hashes to a [0, 1] value by taking
     * the mean and dividing by Long.MAX_VALUE (approximate normalization).
     */
    private static double meanHashNormalized(long[] hashes) {
        if (hashes == null || hashes.length == 0) return 0.0;
        double sum = 0;
        for (long h : hashes) sum += Math.abs(h);
        double meanAbs = sum / hashes.length;
        return Math.min(1.0, meanAbs / (double) Long.MAX_VALUE);
    }
}
