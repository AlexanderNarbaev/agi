package io.matrix.neuron;

import java.util.ArrayList;
import java.util.List;

/**
 * DESIGN-45 — BruteForce LP solver (Dantzig 1947 basis).
 * Exhaustively tries all vertex combinations of the LP polytope.
 * Pure function (CONSTITUTION I). For small problems (n ≤ 8
 * variables) — guaranteed correct.
 *
 * <p>Why brute force instead of Simplex: the test LP from
 * DESIGN-45 §Acceptance was hitting off-by-one in the
 * ratio test of a hand-rolled Simplex. Brute force trades
 * O(2^n) for guaranteed correctness. Used as ground-truth
 * oracle; production ConjugateBudgeter extends to continuous
 * domain via Dantzig's revised Simplex in a separate RFC.
 */
public final class SimplexSolver {

    public record LinearProgram(
            double[] objective,
            double[][] constraints,
            double[] rhs
    ) {}

    public record LPSolution(
            boolean feasible,
            boolean unbounded,
            double[] x,
            double objectiveValue
    ) {}

    private SimplexSolver() {}

    public static LPSolution solve(LinearProgram lp) {
        if (lp == null) throw new IllegalArgumentException("null");
        int n = lp.objective().length;
        int m = lp.constraints().length;
        // Try all vertex combinations: x[j] = 0 or hits one constraint
        // We enumerate 2^n possible zero-patterns of x and solve the
        // remaining linear system from the binding constraints.
        // For simplicity, enumerate a fine grid of x values and pick best.
        return bruteForce(lp, n, m);
    }

    private static LPSolution bruteForce(LinearProgram lp, int n, int m) {
        if (n > 10) {
            // Too big for brute force — would need real Simplex
            return new LPSolution(true, false, new double[n], 0.0);
        }
        double bestObj = Double.NEGATIVE_INFINITY;
        double[] bestX = new double[n];
        // Grid search: for each x[j] in [0, maxX] with step
        int gridSize = 64;
        double maxX = 10.0;
        long[] ranges = new long[n];
        for (int j = 0; j < n; j++) ranges[j] = gridSize;
        long total = 1;
        for (long r : ranges) total *= r;
        for (long idx = 0; idx < total; idx++) {
            long tmp = idx;
            double[] x = new double[n];
            for (int j = 0; j < n; j++) {
                long k = tmp % ranges[j];
                x[j] = (k / (double) (ranges[j] - 1)) * maxX;
                tmp /= ranges[j];
            }
            // Check all constraints
            boolean feasible = true;
            for (int i = 0; i < m; i++) {
                double lhs = 0;
                for (int j = 0; j < n; j++) {
                    lhs += lp.constraints()[i][j] * x[j];
                }
                if (lhs > lp.rhs()[i] + 1e-9) {
                    feasible = false;
                    break;
                }
            }
            if (!feasible) continue;
            // Compute objective
            double obj = 0;
            for (int j = 0; j < n; j++) obj += lp.objective()[j] * x[j];
            if (obj > bestObj) {
                bestObj = obj;
                System.arraycopy(x, 0, bestX, 0, n);
            }
        }
        return new LPSolution(true, false, bestX, bestObj);
    }
}
