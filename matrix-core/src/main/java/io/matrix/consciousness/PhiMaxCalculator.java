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
          // RECON-W34.8: H(L,R) was computed over the RAW state values, while H(L) and H(R)
          // were computed over the PROJECTED indices. The identity
          //
          //     I(L;R) = H(L) + H(R) - H(L,R)
          //
          // requires all three terms to come from the SAME random variable. Mixing them does
          // not produce mutual information; it produces an arbitrary number, and sometimes a
          // NEGATIVE one. Reproduced on a 2-element sequence [4, 0]:
          //
          //     left  = {0: 1}    (both states project to index 0)   -> H(L) = 0
          //     right = {0: 1}    (both states project to index 0)   -> H(R) = 0
          //     joint over RAW states {4:1, 0:1}                      -> H(L,R) = log 2
          //     => mi = 0 + 0 - 0.693 = -0.693,  / log 2 = -1.0
          //
          // Mutual information is a KL divergence and cannot be negative (Gibbs' inequality),
          // so a negative result is not a rounding curiosity -- it is proof that the three
          // terms described different variables. PhiMaxCalculatorPropertyTest caught it as
          // "expected >= 0.0 but was -1.0".
          //
          // The joint term is now taken over the SAME projected pairs the marginals use.
          java.util.Map<Long, Integer> joint = new java.util.HashMap<>();
          for (java.util.Map.Entry<Integer, Integer> entry : counts.entrySet()) {
              int state = entry.getKey();
              int count = entry.getValue();
              int l = projectState(state, mask, n);
              int r = projectState(state, ~mask & ((1 << n) - 1), n);
              long key = ((long) l << 32) | (r & 0xffffffffL);
              joint.merge(key, count, Integer::sum);
          }
          double hJoint = entropy(joint, total);
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

/**
     * Shannon entropy of a count distribution.
     *
     * <p>RECON-W34.8: the key type was narrowed from {@code Integer} to {@code Object}. The
     * joint term of the mutual-information identity is keyed by a packed {@code (left, right)}
     * pair, which does not fit in an {@code Integer}. Rather than duplicate this method for the
     * one call site, entropy depends only on the counts and never on the key, so the key type
     * was generalised to reflect that.</p>
     *
     * @param counts symbol -> occurrence count
     * @param total  total observations, used as the denominator
     * @return entropy in nats
     */
      private static double entropy(java.util.Map<?, Integer> counts, int total) {
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
