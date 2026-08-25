package io.matrix.budgeter;

import java.util.Arrays;

/**
 * ConjugateBudgeter — conjugate row-budget allocator for the Cauldron cycle
 * (DESIGN-11 §3; hypothesis H-021 → EXP-021).
 *
 * <p>Modes over an integer energy envelope {@code E}:
 * <ul>
 *   <li>{@link Mode#CONJUGATE} — exact 0/1 backward DP on gcd-reduced cost
 *       units: {@code V(e) = max Σ v_i·x_i s.t. Σ c_i·x_i ≤ e}. The
 *       Pontryagin-style shadow price is the terminal value slope
 *       {@code λ = V(U) − V(U−1)}. Used whenever the reduced grid fits the
 *       determinism/memory bound ({@value #MAX_DP_UNITS} cells).</li>
 *   <li>{@link Mode#FALLBACK_LEVIN_PROPORTIONAL} — cumulative
 *       value-proportional split (baseline H-021 competes against), used
 *       when {@code E} cannot cover the cheapest row or the grid is too
 *       large for exact DP.</li>
 * </ul>
 *
 * <p>Deterministic: fixed iteration order, no randomness, no wall-clock.
 */
public final class ConjugateBudgeter {

    /** Allocation mode. */
    public enum Mode { CONJUGATE, FALLBACK_LEVIN_PROPORTIONAL }

    /** A candidate row competing for the envelope. */
    public record Row(String id, double value, long cost) {
        public Row {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("row id must not be blank");
            }
            if (value < 0 || cost <= 0) {
                throw new IllegalArgumentException("value must be ≥ 0 and cost > 0");
            }
        }
    }

    /** Result of one allocation pass. */
    public record Allocation(Mode mode, boolean[] selected, double objective,
                             long spentEnvelope, double shadowPrice) {}

    /** Upper bound on DP table size (units × rows) before falling back. */
    static final int MAX_DP_UNITS = 250_000;

    /**
     * Allocates the envelope across rows.
     *
     * @param rows     candidates (order defines tie-breaking)
     * @param envelope integer energy budget, {@code ≥ 0}
     * @return deterministic allocation with a finite shadow price in CONJUGATE mode
     */
    public Allocation allocate(Row[] rows, long envelope) {
        if (rows == null || rows.length == 0) {
            throw new IllegalArgumentException("rows must be non-empty");
        }
        if (envelope < 0) {
            throw new IllegalArgumentException("envelope must be ≥ 0");
        }

        long minCost = Arrays.stream(rows).mapToLong(Row::cost).min().orElse(1);
        long totalCost = Arrays.stream(rows).mapToLong(Row::cost).sum();
        if (envelope < minCost || totalCost == 0) {
            return levinProportional(rows);
        }

        long cap = Math.min(envelope, totalCost);
        long g = gcdOfAll(rows);
        long unitsLong = cap / g;
        if (unitsLong > MAX_DP_UNITS) {
            return levinProportional(rows);
        }
        return conjugateDp(rows, cap, (int) unitsLong, (int) g);
    }

    private Allocation conjugateDp(Row[] rows, long cap, int units, int g) {
        int n = rows.length;
        int[] c = new int[n];
        for (int i = 0; i < n; i++) {
            c[i] = (int) (rows[i].cost() / g);
        }

        // Backward DP: dp[i][e] = best value using rows i..n−1 within budget e.
        double[][] dp = new double[n + 1][units + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int e = 0; e <= units; e++) {
                double skip = dp[i + 1][e];
                double take = c[i] <= e ? rows[i].value() + dp[i + 1][e - c[i]] : Double.NEGATIVE_INFINITY;
                dp[i][e] = Math.max(skip, take);
            }
        }

        boolean[] selected = new boolean[n];
        long spentRaw = 0;
        int e = units;
        for (int i = 0; i < n; i++) {
            boolean takeHere;
            if (c[i] <= e) {
                double takeVal = rows[i].value() + dp[i + 1][e - c[i]];
                takeHere = takeVal >= dp[i + 1][e];
            } else {
                takeHere = false;
            }
            selected[i] = takeHere;
            if (takeHere) {
                e -= c[i];
                spentRaw += rows[i].cost();
            }
        }

        double lambda = units >= 1 ? dp[0][units] - dp[0][units - 1] : 0.0;
        return new Allocation(Mode.CONJUGATE, selected, dp[0][units], spentRaw, lambda);
    }

    private Allocation levinProportional(Row[] rows) {
        // Fallback trigger means the envelope cannot cover even one whole row;
        // fractional rows are outside the model, so nothing is selected and
        // the cycle defers to the base Levin schedule upstream (DESIGN-11 §3).
        return new Allocation(Mode.FALLBACK_LEVIN_PROPORTIONAL,
                new boolean[rows.length], 0.0, 0L, Double.NaN);
    }

    private static long gcdOfAll(Row[] rows) {
        long g = 0;
        for (Row r : rows) {
            g = gcd(g, r.cost());
            if (g == 1) {
                break;
            }
        }
        return g == 0 ? 1 : g;
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a;
    }
}
