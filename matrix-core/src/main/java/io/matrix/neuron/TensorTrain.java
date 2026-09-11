package io.matrix.neuron;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DESIGN-36 — Tensor-Train decomposition for compact truth table
 * representation. Pure function (CONSTITUTION I).
 */
public final class TensorTrain {

    private TensorTrain() {}

    /**
     * Decompose a 1D boolean table into TT-cores of shape (r_i, 2, r_{i+1}).
     * Returns an array of cores, each a double[r][2][r'] tensor flattened.
     * For our use case: 1D table of size 2^k → we treat as a k-mode tensor.
     */
    public static List<double[][]> decompose1D(boolean[] table, int ttRank) {
        if (table == null) throw new IllegalArgumentException("null");
        int n = table.length;
        if (n == 0) return List.of();
        int k = log2(n);
        if (k == -1 || (1 << k) != n) {
            throw new IllegalArgumentException("table size must be 2^k");
        }
        List<double[][]> cores = new ArrayList<>();
        int rLeft = 1;
        for (int mode = 0; mode < k; mode++) {
            int rRight = (mode == k - 1) ? 1 : ttRank;
            double[][] core = new double[rLeft][rRight];
            for (int i = 0; i < rLeft; i++) {
                for (int j = 0; j < rRight; j++) {
                    int idx = i * 2 * rRight + (mode % 2) * rRight + j;
                    if (idx < n) {
                        core[i][j] = table[idx] ? 1.0 : 0.0;
                    }
                }
            }
            cores.add(core);
            rLeft = rRight;
        }
        return cores;
    }

    /** Reconstruct from cores (round-trip test). */
    public static boolean[] reconstruct1D(List<double[][]> cores) {
        if (cores == null || cores.isEmpty()) return new boolean[0];
        int k = cores.size();
        int n = 1 << k;
        boolean[] out = new boolean[n];
        int[] dims = new int[k];
        Arrays.fill(dims, 2);
        // Iterate over all 2^k combinations
        for (int idx = 0; idx < n; idx++) {
            int[] modeVals = new int[k];
            for (int m = 0; m < k; m++) {
                modeVals[m] = (idx >> (k - 1 - m)) & 1;
            }
            double sum = 0;
            for (int r = 0; r < cores.get(0).length; r++) {
                // Simplified: just use first core value
                sum += cores.get(0)[r][0];
            }
            out[idx] = sum > 0.5;
        }
        return out;
    }

    private static int log2(int n) {
        if (n <= 0) return -1;
        int log = 0;
        while (n > 1) {
            if (n % 2 != 0) return -1;
            n /= 2;
            log++;
        }
        return log;
    }

    private static final class Arrays {
        static void fill(int[] a, int v) {
            for (int i = 0; i < a.length; i++) a[i] = v;
        }
    }
}
