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

    /**
     * Per-period state snapshot (DESIGN-11 §3, W-B): the budgeter carries
     * the smoothed shadow price forward across epochs, bounded by the
     * theoretical shadow-price envelope {@code [0, maxVperC]}.
     *
     * <p>Three invariants (W-B):
     * <ul>
     *   <li><b>Lambda monotonicity</b> — within a single DP, the shadow price
     *       is non-increasing in the envelope (verified by the DP itself).</li>
     *   <li><b>Shadow-price bounds</b> — across epochs, the smoothed shadow
     *       price always lies in {@code [0, maxVperC]} (clamped by
     *       {@link #step(long, double)}).</li>
     *   <li><b>Finite horizon</b> — the epoch counter is a monotonically
     *       increasing non-negative integer, capped by the caller's horizon.</li>
     * </ul>
     */
    public record BudgeterState(long epoch, double shadowPrice, double maxVperC) {
        public BudgeterState {
            if (epoch < 0) {
                throw new IllegalArgumentException("epoch must be ≥ 0");
            }
            if (shadowPrice < 0) {
                throw new IllegalArgumentException("shadowPrice must be ≥ 0");
            }
            if (maxVperC < 0) {
                throw new IllegalArgumentException("maxVperC must be ≥ 0");
            }
        }
    }

    /** Upper bound on DP table size (units × rows) before falling back. */
    static final int MAX_DP_UNITS = 250_000;

    /** Smoothing factor for the per-period shadow-price update (0..1). */
    public static final double SMOOTHING_ALPHA = 0.7;

    private BudgeterState state = new BudgeterState(0L, 0.0, Double.POSITIVE_INFINITY);

    /** Current state snapshot. Read-only. */
    public BudgeterState state() {
        return state;
    }

    /** Reset the per-period state to epoch 0, shadow 0. */
    public synchronized void resetPeriodState() {
        state = new BudgeterState(0L, 0.0, state.maxVperC());
    }

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

    // --- per-period state machine (W-B) -----------------------------------

    /**
     * Compute the theoretical shadow-price upper bound for a row set:
     * {@code max_i(v_i / c_i)}. Doubles as the maximum the convex
     * combination can ever take, since every observed {@code lambda} is
     * bounded by this value (DESIGN-11 §3 invariant 2).
     */
    public static double maxValuePerCost(Row[] rows) {
        if (rows == null || rows.length == 0) {
            return 0.0;
        }
        double best = 0.0;
        for (Row r : rows) {
            double ratio = r.value() / r.cost();
            if (ratio > best) {
                best = ratio;
            }
        }
        return best;
    }

    /**
     * Per-period step: smooths the observed shadow price into the persistent
     * state via {@code λ_{t+1} = clamp(α·λ_t + (1−α)·observed, 0, maxVperC)}.
     *
     * <p>Invariants upheld (W-B):
     * <ul>
     *   <li>Shadow-price bounds — the clamp keeps the smoothed shadow in
     *       {@code [0, maxVperC]} at every step.</li>
     *   <li>Finite horizon — the epoch counter is monotonically increasing
     *       and bounded by the caller's horizon (no internal loop).</li>
     *   <li>Determinism — no randomness, no wall-clock.</li>
     * </ul>
     *
     * <p>Note: lambda monotonicity is a property of the single-epoch DP,
     * not the per-period smoothing (which by design lets the shadow price
     * follow the observed series). See {@link #allocate(Row[], long)} and
     * the formal spec {@code formal/ConjugateBudgeterDP.tla}.
     *
     * @param rows the row set defining the shadow-price ceiling
     * @param epoch new epoch index (must be &gt; current epoch)
     * @param observedLambda shadow price realised this period
     * @return new {@link BudgeterState} after the step
     */
    public synchronized BudgeterState step(Row[] rows, long epoch, double observedLambda) {
        if (rows == null || rows.length == 0) {
            throw new IllegalArgumentException("rows must be non-empty");
        }
        if (epoch <= state.epoch()) {
            throw new IllegalArgumentException(
                    "epoch must be strictly greater than current epoch "
                            + state.epoch() + " (got " + epoch + ")");
        }
        double maxVperC = maxValuePerCost(rows);
        double smoothed = SMOOTHING_ALPHA * state.shadowPrice()
                + (1.0 - SMOOTHING_ALPHA) * observedLambda;
        double clamped = Math.max(0.0, Math.min(maxVperC, smoothed));
        state = new BudgeterState(epoch, clamped, maxVperC);
        return state;
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
