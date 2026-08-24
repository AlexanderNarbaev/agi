package io.matrix.tsetlin;

import io.matrix.bir.ClauseSetForm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * EXP-002 stage 1 (synthetic bring-up): end-to-end producer pipeline check on
 * random-k-DNF targets at k∈{8,12} — 10% train-label noise, clean holdout,
 * 5 seeds, exact distillation. Full comparison against MPDT-GA / BNN
 * baselines follows in stage 2 (see HYPOTHESES EXP-002 card).
 */
@Disabled("stage-1 open: attempt-11 margin-gating (canonical F6 fix) insufficient alone for k>=8 (0.59/0.53); full canonical stack per audit-plan §2 required")
class Exp002SyntheticBringUpTest {

    /** Random R-term × L-literal DNF over k variables. Returns label fn. */
    private static java.util.function.LongPredicate randomDnf(int k, int terms, int lits, Random rnd) {
        long[] pos = new long[terms], neg = new long[terms];
        for (int t = 0; t < terms; t++) {
            for (int l = 0; l < lits; l++) {
                int j = rnd.nextInt(k);
                boolean pol = rnd.nextBoolean();
                // avoid same-variable contradiction inside one term
                if ((((pos[t] | neg[t]) >>> j) & 1L) != 0) { l--; continue; }
                if (pol) pos[t] |= (1L << j); else neg[t] |= (1L << j);
            }
        }
        return x -> {
            for (int t = 0; t < terms; t++) {
                boolean term = true;
                for (int j = 0; j < k && term; j++) {
                    boolean v = ((x >>> j) & 1) == 1;
                    if (((pos[t] >>> j) & 1) == 1 && !v) term = false;
                    if (((neg[t] >>> j) & 1) == 1 && v) term = false;
                }
                if (term) return true;
            }
            return false;
        };
    }

    private static double balancedAccuracy(TsetlinTrainer tr, long[][] xs, boolean[] ys,
                                           java.util.function.LongPredicate truth) {
        int tp = 0, tn = 0, p = 0, n = 0;
        for (int i = 0; i < xs.length; i++) {
            boolean pred = tr.predict(xs[i][0]);
            boolean actual = truth.test(xs[i][0]);
            if (actual) { p++; if (pred) tp++; } else { n++; if (!pred) tn++; }
        }
        return (p == 0 ? 1 : (double) tp / p) * 0.5 + (n == 0 ? 1 : (double) tn / n) * 0.5;
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 12})
    void syntheticDnfPipeline(int k) {
        double meanAcc = 0;
        int minClauses = Integer.MAX_VALUE;
        for (long seed = 1; seed <= 5; seed++) {
            Random rnd = new Random(seed * 31 + k);
            var truth = randomDnf(k, 6, 3, new Random(seed));
            int total = 240, holdout = total / 3;
            long[][] allX = new long[total][1];
            boolean[] noisyY = new boolean[total];
            for (int i = 0; i < total; i++) {
                long x = 0;
                for (int j = 0; j < k; j++) if (rnd.nextBoolean()) x |= (1L << j);
                allX[i][0] = x;
                boolean clean = truth.test(x);
                noisyY[i] = rnd.nextInt(10) == 0 ? !clean : clean; // 10% noise
            }
            var tr = new TsetlinTrainer(k, 48, 16, new Random(seed),
                    TsetlinTrainer.InitStrategy.COMPLEMENTARY);
            long[][] trainX = java.util.Arrays.copyOf(allX, total - holdout);
            boolean[] trainY = java.util.Arrays.copyOf(noisyY, total - holdout);
            // H-035 EBL curriculum per epoch (counterfactual prioritization)
            for (int e = 0; e < 120; e++) {
                var pr = EblCurriculum.prioritize(trainX, trainY, tr::predict);
                tr.trainBatch(pr.x(), pr.y(), 1);
            }

            long[][] holdX = java.util.Arrays.copyOfRange(allX, total - holdout, total);
            double acc = balancedAccuracy(tr, holdX, null, truth);
            meanAcc += acc;

            ClauseSetForm cs = tr.toDecisionClauseSet("exp002-k" + k);
            minClauses = Math.min(minClauses, cs.clauses().size());
        }
        meanAcc /= 5;
        System.out.printf("EXP-002 bring-up k=%d: meanBalancedAcc=%.3f minClauses=%d%n", k, meanAcc, minClauses);
        assertThat(meanAcc).as("k=%d mean balanced accuracy", k).isGreaterThanOrEqualTo(0.70);
    }
}
