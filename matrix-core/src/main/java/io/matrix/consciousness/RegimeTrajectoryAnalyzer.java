package io.matrix.consciousness;

import java.util.List;

/**
 * W178 — Regime trajectory analyzer.
 *
 * <p>Analyzes the time series of cognitive regimes (FROZEN /
 * EDGE_OF_CHAOS / CHAOTIC) and computes:
 * - run-length statistics (consecutive cycles in same regime)
 * - transition matrix (probability of regime B following regime A)
 * - regime stability (fraction of cycles staying in regime)
 *
 * <p>CONSTITUTION VI compliance: regime time series analysis,
 * not phenomenal consciousness claim.
 */
public final class RegimeTrajectoryAnalyzer {

    private RegimeTrajectoryAnalyzer() {}

    /**
     * Run-length statistics: list of run lengths for each regime.
     * Returns int[3] = {frozen_runs, edge_runs, chaotic_runs}.
     */
    public static int[] runLengths(List<CognitiveGenesisProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) return new int[]{0, 0, 0};
        int[] counts = new int[3];
        int currentRun = 0;
        String currentRegime = null;
        for (CognitiveGenesisProfile p : profiles) {
            String r = p.regime();
            if (r.equals(currentRegime)) {
                currentRun++;
            } else {
                if (currentRegime != null && currentRun > 0) {
                    counts[regimeIndex(currentRegime)]++;
                }
                currentRegime = r;
                currentRun = 1;
            }
        }
        if (currentRegime != null && currentRun > 0) {
            counts[regimeIndex(currentRegime)]++;
        }
        return counts;
    }

    /**
     * Average run length per regime.
     */
    public static double avgRunLength(List<CognitiveGenesisProfile> profiles) {
        int[] counts = runLengths(profiles);
        if (counts[0] + counts[1] + counts[2] == 0) return 0.0;
        int total = profiles.size();
        return (double) total / (counts[0] + counts[1] + counts[2]);
    }

    /**
     * Transition matrix: row = from regime, col = to regime.
     * Index 0=FROZEN, 1=EDGE, 2=CHAOTIC.
     * Returns 3x3 matrix.
     */
    public static int[][] transitionCounts(List<CognitiveGenesisProfile> profiles) {
        int[][] counts = new int[3][3];
        if (profiles == null || profiles.size() < 2) return counts;
        for (int i = 1; i < profiles.size(); i++) {
            int from = regimeIndex(profiles.get(i - 1).regime());
            int to = regimeIndex(profiles.get(i).regime());
            counts[from][to]++;
        }
        return counts;
    }

    /**
     * Regime stability: fraction of cycles where regime didn't change.
     */
    public static double stability(List<CognitiveGenesisProfile> profiles) {
        if (profiles == null || profiles.size() < 2) return 1.0;
        int unchanged = 0;
        for (int i = 1; i < profiles.size(); i++) {
            if (profiles.get(i).regime().equals(profiles.get(i - 1).regime())) {
                unchanged++;
            }
        }
        return (double) unchanged / (profiles.size() - 1);
    }

    private static int regimeIndex(String regime) {
        if (regime == null) return 0;
        if (regime.equals("FROZEN")) return 0;
        if (regime.equals("EDGE_OF_CHAOS")) return 1;
        if (regime.equals("CHAOTIC")) return 2;
        return 0;
    }
}
