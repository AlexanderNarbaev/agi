package io.matrix.consciousness;

import java.util.List;

/**
 * W166 — Cognitive heatmap.
 *
 * <p>Generates a 2D heatmap representation of cognitive profile
 * sequences. Each row is a cycle, each column is a profile field,
 * and the cell value is the normalized field value.
 *
 * <p>Provides:
 * - toHeatmap(profiles): 2D array [nCycles × nFields]
 * - columnMeans(profiles): per-field average across cycles
 * - columnVariances(profiles): per-field variance across cycles
 *
 * <p>CONSTITUTION VI compliance: heatmap of cognitive state
 * descriptors, not phenomenal consciousness claim.
 */
public final class CognitiveHeatmap {

    private CognitiveHeatmap() {}

    private static final int N_FIELDS = 13;  // Matches CognitiveGenesisProfile fields

    /**
     * Convert profile sequence to heatmap [nCycles × N_FIELDS].
     * Each cell is the normalized value of the field at that cycle.
     */
    public static double[][] toHeatmap(List<CognitiveGenesisProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) return new double[0][0];
        double[][] heatmap = new double[profiles.size()][N_FIELDS];
        for (int i = 0; i < profiles.size(); i++) {
            CognitiveGenesisProfile p = profiles.get(i);
            heatmap[i][0] = p.phiBinary();
            heatmap[i][1] = p.phiF();
            heatmap[i][2] = p.phiR();
            heatmap[i][3] = p.phiLinGauss();
            heatmap[i][4] = p.interAgentPhi();
            heatmap[i][5] = p.stabilityPhi();
            heatmap[i][6] = p.crossLevelPhi();
            // Normalize K by 100 for visualization
            heatmap[i][7] = Math.min(1.0, p.kolmogorovK() / 100.0);
            heatmap[i][8] = p.analogicalSimilarity();
            heatmap[i][9] = p.conceptualExclusion();
            heatmap[i][10] = p.nkEdgeOfChaosK() / 8.0;
            heatmap[i][11] = p.memristorConductance();
            heatmap[i][12] = Math.min(1.0, p.lSystemComplexityRatio() / 5.0);
        }
        return heatmap;
    }

    /**
     * Compute column means (per-field average across cycles).
     */
    public static double[] columnMeans(double[][] heatmap) {
        if (heatmap.length == 0) return new double[0];
        int n = heatmap.length;
        int d = heatmap[0].length;
        double[] means = new double[d];
        for (int j = 0; j < d; j++) {
            double sum = 0;
            for (int i = 0; i < n; i++) sum += heatmap[i][j];
            means[j] = sum / n;
        }
        return means;
    }

    /**
     * Compute column variances.
     */
    public static double[] columnVariances(double[][] heatmap) {
        if (heatmap.length == 0) return new double[0];
        double[] means = columnMeans(heatmap);
        int n = heatmap.length;
        int d = heatmap[0].length;
        double[] variances = new double[d];
        for (int j = 0; j < d; j++) {
            double sum = 0;
            for (int i = 0; i < n; i++) {
                double diff = heatmap[i][j] - means[j];
                sum += diff * diff;
            }
            variances[j] = sum / n;
        }
        return variances;
    }

    /** Names of fields for display. */
    public static String[] fieldNames() {
        return new String[]{
            "PhiBinary", "PhiF", "PhiR", "PhiLinGauss",
            "InterAgentPhi", "StabilityPhi", "CrossLevelPhi",
            "KolmogorovK", "AnalogicalSim", "ConceptExclusion",
            "NKEdgeK", "MemristorG", "LSystemRatio"
        };
    }

    /** Number of fields per profile. */
    public static int fieldCount() {
        return N_FIELDS;
    }
}
