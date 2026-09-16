package io.matrix.consciousness;

/**
 * W187 — Φ_max calculator (IIT 4.0 inspired).
 *
 * <p>Approximation of the maximal integrated complex: find the
 * subsystem subset with maximum integrated information.
 *
 * <p>For N systems, there are 2^N subsets. For N ≤ 16, we can
 * enumerate exhaustively. For larger N, we use greedy selection.
 *
 * <p>CONSTITUTION VI compliance: integrated information measurement,
 * not phenomenal consciousness claim.
 */
public final class PhiMaxCalculator {

    private PhiMaxCalculator() {}

    /**
     * Compute Φ_max over all possible bipartitions of binary states.
     * Returns max Φ (smallest MI) across bipartitions.
     *
     * @param states array of binary states (each int is a state value)
     * @return max Φ in bits
     */
    public static double phiMax(int[] states) {
        if (states == null || states.length == 0) return 0.0;
        int n = states.length;
        // Compute state distribution
        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
        for (int s : states) counts.merge(s, 1, Integer::sum);
        int distinct = counts.size();
        if (distinct <= 1) return 0.0;

        // Try all bipartitions (up to 2^N but skip trivial mask=0 and mask=2^N-1)
        double minMi = Double.POSITIVE_INFINITY;
        for (int mask = 1; mask < (1 << n) - 1; mask++) {
            double mi = mutualInformation(counts, mask, n);
            if (mi < minMi) minMi = mi;
        }
        return minMi == Double.POSITIVE_INFINITY ? 0.0 : minMi;
    }

    /**
     * Compute Φ_max using greedy bipartition for larger systems.
     * Approximation: find bipartition that minimizes MI.
     */
    public static double phiMaxGreedy(int[] states) {
        if (states == null || states.length == 0) return 0.0;
        int n = states.length;
        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
        for (int s : states) counts.merge(s, 1, Integer::sum);
        if (counts.size() <= 1) return 0.0;

        // Greedy: start with all in one part, move one element at a time
        int mask = 0;
        double minMi = Double.POSITIVE_INFINITY;
        // Try all single-element moves (2*N possibilities)
        for (int i = 0; i < n; i++) {
            int m = 1 << i;
            double mi = mutualInformation(counts, m, n);
            if (mi < minMi) {
                minMi = mi;
                mask = m;
            }
        }
        return minMi == Double.POSITIVE_INFINITY ? 0.0 : minMi;
    }

    /**
     * Compute mutual information across a bipartition.
     */
    private static double mutualInformation(java.util.Map<Integer, Integer> counts, int mask, int n) {
        // Marginalize counts over left/right parts
        java.util.Map<Integer, Integer> left = new java.util.HashMap<>();
        java.util.Map<Integer, Integer> right = new java.util.HashMap<>();
        int total = 0;
        for (java.util.Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            int state = entry.getKey();
            int count = entry.getValue();
            int leftIdx = projectState(state, mask, n);
            int rightIdx = projectState(state, ~mask & ((1 << n) - 1), n);
            left.merge(leftIdx, count, Integer::sum);
            right.merge(rightIdx, count, Integer::sum);
            total += count;
        }
        if (total == 0) return 0.0;

        double hLeft = entropy(left, total);
        double hRight = entropy(right, total);
        double hJoint = 0;
        for (java.util.Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            int state = entry.getKey();
            int count = entry.getValue();
            double p = (double) count / total;
            if (p > 0) hJoint -= p * Math.log(p);
        }
        // H(L,R) - H(L) - H(R) = -I(L;R)
        double mi = hLeft + hRight - hJoint;
        return mi / Math.log(2);
    }

    private static int projectState(int state, int mask, int n) {
        int result = 0;
        int bitPos = 0;
        for (int i = 0; i < n; i++) {
            if ((mask & (1 << i)) != 0) {
                result |= ((state >> i) & 1) << bitPos;
                bitPos++;
            }
        }
        return result;
    }

    private static double entropy(java.util.Map<Integer, Integer> counts, int total) {
        double h = 0;
        for (int c : counts.values()) {
            if (c > 0) {
                double p = (double) c / total;
                h -= p * Math.log(p);
            }
        }
        return h;
    }
}
