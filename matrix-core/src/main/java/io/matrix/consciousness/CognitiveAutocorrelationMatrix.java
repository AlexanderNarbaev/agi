package io.matrix.consciousness;

import java.util.List;

/**
 * W200 — Cognitive autocorrelation matrix.
 *
 * <p>Compute pairwise autocorrelation between all 13 cognitive fields
 * over a profile sequence. Identifies which fields co-vary over time.
 *
 * <p>Returns 13×13 matrix where (i,j) entry is Pearson correlation
 * between field i and field j time series.
 *
 * <p>CONSTITUTION VI compliance: cross-field correlation analysis,
 * not phenomenal consciousness claim.
 */
public final class CognitiveAutocorrelationMatrix {

    private CognitiveAutocorrelationMatrix() {}

    private static final int N_FIELDS = 13;

    /**
     * Compute pairwise correlation matrix over a profile sequence.
     */
    public static double[][] compute(List<CognitiveGenesisProfile> profiles) {
        double[][] result = new double[N_FIELDS][N_FIELDS];
        if (profiles == null || profiles.size() < 2) return result;
        double[][] fields = extractFields(profiles);
        for (int i = 0; i < N_FIELDS; i++) {
            for (int j = 0; j < N_FIELDS; j++) {
                result[i][j] = SeriesCorrelator.pearson(fields[i], fields[j]);
            }
        }
        return result;
    }

    /**
     * Get most correlated field pairs (above threshold).
     */
    public static java.util.List<FieldPair> highlyCorrelated(
            List<CognitiveGenesisProfile> profiles, double threshold) {
        java.util.List<FieldPair> pairs = new java.util.ArrayList<>();
        double[][] matrix = compute(profiles);
        for (int i = 0; i < N_FIELDS; i++) {
            for (int j = i + 1; j < N_FIELDS; j++) {
                if (Math.abs(matrix[i][j]) > threshold) {
                    pairs.add(new FieldPair(i, j, matrix[i][j]));
                }
            }
        }
        pairs.sort((a, b) -> Double.compare(Math.abs(b.correlation()), Math.abs(a.correlation())));
        return pairs;
    }

    /**
     * Compute average off-diagonal correlation magnitude.
     */
    public static double averageOffDiagonal(List<CognitiveGenesisProfile> profiles) {
        double[][] matrix = compute(profiles);
        double sum = 0;
        int count = 0;
        for (int i = 0; i < N_FIELDS; i++) {
            for (int j = i + 1; j < N_FIELDS; j++) {
                sum += Math.abs(matrix[i][j]);
                count++;
            }
        }
        return count == 0 ? 0.0 : sum / count;
    }

    private static double[][] extractFields(List<CognitiveGenesisProfile> profiles) {
        int n = profiles.size();
        double[][] fields = new double[N_FIELDS][n];
        for (int i = 0; i < n; i++) {
            CognitiveGenesisProfile p = profiles.get(i);
            fields[0][i] = p.phiBinary();
            fields[1][i] = p.phiF();
            fields[2][i] = p.phiR();
            fields[3][i] = p.phiLinGauss();
            fields[4][i] = p.interAgentPhi();
            fields[5][i] = p.stabilityPhi();
            fields[6][i] = p.crossLevelPhi();
            fields[7][i] = Math.min(1.0, p.kolmogorovK() / 100.0);
            fields[8][i] = p.analogicalSimilarity();
            fields[9][i] = p.conceptualExclusion();
            fields[10][i] = p.nkEdgeOfChaosK() / 8.0;
            fields[11][i] = p.memristorConductance();
            fields[12][i] = Math.min(1.0, p.lSystemComplexityRatio() / 5.0);
        }
        return fields;
    }

    public record FieldPair(int fieldI, int fieldJ, double correlation) {}
}
