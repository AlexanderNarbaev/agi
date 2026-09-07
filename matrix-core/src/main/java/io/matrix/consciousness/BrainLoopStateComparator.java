package io.matrix.consciousness;

import io.matrix.auditor.MatrixTrace;

/**
 * RUN 229 — BrainLoopStateComparator (compare two traces).
 *
 * <p>Compares two MatrixTraces and reports divergence metrics.
 * Useful for testing snapshot/restore correctness.
 */
public final class BrainLoopStateComparator {

    public record Comparison(int stepsA, int stepsB, int hashMatches,
                            boolean identical,
                            double divergenceRatio) {}

    public static Comparison compare(MatrixTrace a, MatrixTrace b) {
        int stepsA = a.count();
        int stepsB = b.count();
        int min = Math.min(stepsA, stepsB);
        int hashMatches = 0;
        for (int i = 0; i < min; i++) {
            var ha = a.steps().get(i).hash;
            var hb = b.steps().get(i).hash;
            if (ha != null && ha.equals(hb)) hashMatches++;
        }
        boolean identical = stepsA == stepsB
                && hashMatches == min
                && stepsA > 0;
        double ratio = stepsA == 0 ? 0
                : (double) (min - hashMatches) / Math.max(1, stepsA);
        return new Comparison(stepsA, stepsB, hashMatches,
                identical, ratio);
    }

    public static String format(Comparison c) {
        return String.format(
                "Compare{ stepsA=%d, stepsB=%d, hashMatches=%d, identical=%s, divergence=%.2f }",
                c.stepsA(), c.stepsB(), c.hashMatches(),
                c.identical() ? "YES" : "no", c.divergenceRatio());
    }
}
