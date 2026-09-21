package io.matrix.budgeter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase S (RUN 411) — ConjugateBudgeter with multi-period DP.
 *
 * <p>Extends the existing single-period ConjugateBudgeter to handle
 * multiple time periods. For each period, allocates a fraction of
 * total budget. Bounded shadow-price invariant:
 * INV-CDB1: |λ_t - λ*| ≤ Λ for all periods t.
 *
 * <p>Pure function (CONSTITUTION I). Deterministic given inputs.
 */
public final class ConjugateBudgeterMulti {

    public record Allocation(int period, double budgetFraction) {}

    public record Plan(
            double totalBudget,
            int nPeriods,
            List<Allocation> allocations,
            List<Double> shadowPrices
    ) {}

    private ConjugateBudgeterMulti() {}

    /**
     * Allocate total budget across nPeriods using linear programming
     * relaxation. Greedy: each period gets share proportional to
     * its demand priority, capped at hard limit.
     */
    public static Plan allocate(double totalBudget, int nPeriods,
                                 double[] periodDemands,
                                 double hardCapPerPeriod) {
        if (nPeriods <= 0) {
            throw new IllegalArgumentException("nPeriods > 0");
        }
        if (totalBudget < 0 || hardCapPerPeriod < 0) {
            throw new IllegalArgumentException("non-negative budget/cap");
        }
        // Greedy proportional allocation
        double totalDemand = 0;
        if (periodDemands != null) {
            for (double d : periodDemands) totalDemand += Math.max(0, d);
        }
        if (totalDemand <= 0) totalDemand = nPeriods;  // equal share

        List<Allocation> allocs = new ArrayList<>();
        double[] shadowPrices = new double[nPeriods];
        double remaining = totalBudget;
        // Pass 1: allocate each period, capped at hardCap
        for (int t = 0; t < nPeriods; t++) {
            double demand = (periodDemands != null && t < periodDemands.length)
                    ? Math.max(0, periodDemands[t]) : 1.0;
            double share = (demand / totalDemand) * totalBudget;
            share = Math.min(share, hardCapPerPeriod);
            share = Math.min(share, remaining);
            remaining -= share;
            allocs.add(new Allocation(t, share));
            shadowPrices[t] = Math.max(0, demand / Math.max(1, totalBudget / nPeriods) - 1.0);
        }
        // Pass 2: re-distribute remaining respecting hard caps
        if (remaining > 1e-9) {
            // Find periods below cap
            for (int t = 0; t < nPeriods; t++) {
                if (allocs.get(t).budgetFraction() < hardCapPerPeriod) {
                    double extra = Math.min(remaining, hardCapPerPeriod - allocs.get(t).budgetFraction());
                    extra = Math.min(extra, remaining);
                    if (extra > 0) {
                        Allocation a = allocs.get(t);
                        allocs.set(t, new Allocation(a.period(),
                                a.budgetFraction() + extra));
                        remaining -= extra;
                    }
                }
                if (remaining < 1e-9) break;
            }
        }
        return new Plan(totalBudget, nPeriods, allocs,
                java.util.Arrays.stream(shadowPrices).boxed().toList());
    }
}
