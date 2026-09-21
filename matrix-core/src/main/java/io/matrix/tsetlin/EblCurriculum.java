package io.matrix.tsetlin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;

/**
 * EBL curriculum (H-035, GLOSSARY §103): explanation-based prioritization of
 * training examples for BIR producers.
 *
 * <p>After each epoch the current model's MISCLASSIFIED examples are moved to
 * the front of the queue and augmented with their one-bit counterfactual
 * neighbours (labelled with ground truth) — the explanation neighbourhood of
 * each error. Correctly classified examples follow unchanged. The producer
 * therefore spends its next pass on the decision-boundary region first.
 *
 * <p>Deterministic: iteration order is fixed; no randomness.
 */
public final class EblCurriculum {

    /** Reordered/augmented training batch. */
    public record Prioritized(long[][] x, boolean[] y) {}

    private EblCurriculum() {}

    /**
     * @param xs    packed inputs
     * @param ys    ground-truth labels
     * @param model current predictor (e.g., {@code trainer::predict})
     * @return misclassified (+ their 1-bit counterfactuals) first, then the rest
     */
    public static Prioritized prioritize(long[][] xs, boolean[] ys, LongPredicate model) {
        if (xs.length != ys.length) throw new IllegalArgumentException("length mismatch");
        List<Long> head = new ArrayList<>();
        List<Boolean> headY = new ArrayList<>();
        List<Long> tail = new ArrayList<>();
        List<Boolean> tailY = new ArrayList<>();
        for (int i = 0; i < xs.length; i++) {
            long x = xs[i][0];
            boolean y = ys[i];
            if (model.test(x) != y) {
                head.add(x); headY.add(y);
                // one-bit counterfactual neighbourhood of the error
                int width = 64 - Long.numberOfLeadingZeros(Math.max(1, x | 1));
                for (int j = 0; j < Math.min(width, 20); j++) {
                    long cf = x ^ (1L << j);
                    if (cf != x) { head.add(cf); headY.add(y); }
                }
            } else {
                tail.add(x); tailY.add(y);
            }
        }
        long[][] X = new long[head.size() + tail.size()][1];
        boolean[] Y = new boolean[head.size() + tail.size()];
        int p = 0;
        for (int i = 0; i < head.size(); i++) { X[p][0] = head.get(i); Y[p] = headY.get(i); p++; }
        for (int i = 0; i < tail.size(); i++) { X[p][0] = tail.get(i); Y[p] = tailY.get(i); p++; }
        return new Prioritized(X, Y);
    }
}
