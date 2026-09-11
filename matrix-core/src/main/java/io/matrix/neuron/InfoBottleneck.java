package io.matrix.neuron;

/**
 * DESIGN-41 — Information Bottleneck (Tishby 1999).
 * Greedy neuron selection by mutual information proxy.
 * Pure function (CONSTITUTION I).
 */
public final class InfoBottleneck {

    private InfoBottleneck() {}

    /**
     * Greedy selection: pick neurons that have highest correlation
     * with the target (proxy for I(T;Y)), avoiding redundant ones
     * (low pairwise correlation).
     */
    public static int[] selectNeurons(int[][] activations, int[] targets, int k) {
        if (activations == null || targets == null) {
            throw new IllegalArgumentException("null");
        }
        if (activations.length != targets.length) {
            throw new IllegalArgumentException("length mismatch");
        }
        if (k <= 0) return new int[0];
        int nNeurons = activations[0].length;
        double[] score = new double[nNeurons];
        for (int n = 0; n < nNeurons; n++) {
            score[n] = correlation(activations, targets, n);
        }
        // Greedy: pick top k, avoiding redundant (pairwise corr > 0.9)
        int[] selected = new int[Math.min(k, nNeurons)];
        boolean[] taken = new boolean[nNeurons];
        int count = 0;
        Integer[] order = sortIndicesByScore(score);
        for (int idx : order) {
            if (count >= k) break;
            if (taken[idx]) continue;
            // Check redundancy with already-selected
            boolean redundant = false;
            for (int j = 0; j < count; j++) {
                if (neuronCorrelation(activations, idx, selected[j]) > 0.9) {
                    redundant = true;
                    break;
                }
            }
            if (!redundant) {
                selected[count++] = idx;
                taken[idx] = true;
            }
        }
        // Trim to actual count
        int[] result = new int[count];
        System.arraycopy(selected, 0, result, 0, count);
        return result;
    }

    private static double correlation(int[][] activations, int[] targets, int neuron) {
        int n = activations.length;
        double sumXY = 0, sumX = 0, sumY = 0, sumX2 = 0, sumY2 = 0;
        for (int i = 0; i < n; i++) {
            double x = activations[i][neuron];
            double y = targets[i];
            sumXY += x * y;
            sumX += x;
            sumY += y;
            sumX2 += x * x;
            sumY2 += y * y;
        }
        double denom = Math.sqrt((n * sumX2 - sumX * sumX)
                * (n * sumY2 - sumY * sumY));
        if (denom == 0) return 0;
        return (n * sumXY - sumX * sumY) / denom;
    }

    private static double neuronCorrelation(int[][] activations, int a, int b) {
        int n = activations.length;
        double sumAB = 0, sumA = 0, sumB = 0, sumA2 = 0, sumB2 = 0;
        for (int i = 0; i < n; i++) {
            double va = activations[i][a];
            double vb = activations[i][b];
            sumAB += va * vb;
            sumA += va; sumB += vb;
            sumA2 += va * va; sumB2 += vb * vb;
        }
        double denom = Math.sqrt((n * sumA2 - sumA * sumA)
                * (n * sumB2 - sumB * sumB));
        if (denom == 0) return 0;
        return (n * sumAB - sumA * sumB) / denom;
    }

    private static Integer[] sortIndicesByScore(double[] score) {
        Integer[] idx = new Integer[score.length];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (a, b) -> Double.compare(score[b], score[a]));
        return idx;
    }
}
