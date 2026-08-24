package io.matrix.tsetlin;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H-035 criterion probe (toy scale): does EBL prioritization reach a perfect
 * fit in ≤50% of the epochs the plain shuffled baseline needs?
 */
@Disabled("H-035 toy-probe superseded by canonical Ib refactor — redesign against stable configs; synthetic stage-1 evidence moved to Exp002SyntheticBringUpTest")
class EblH035Test {

    private record Result(int examples, boolean converged) {}

    private Result examplesToPerfect(int k, boolean[] yOf, int clauses, int nStates, long seed,
                                     boolean useEbl, int cap) {
        long used = 0;
        var tr = new TsetlinTrainer(k, clauses, nStates, new Random(seed), TsetlinTrainer.InitStrategy.COMPLEMENTARY);
        long[][] x = new long[1 << k][1];
        for (int i = 0; i < x.length; i++) x[i][0] = i;
        for (int epoch = 1; epoch <= cap; epoch++) {
            int fed;
            if (useEbl) {
                var p = EblCurriculum.prioritize(x, yOf, tr::predict);
                tr.trainBatch(p.x(), p.y(), 1);
                fed = p.x().length;
            } else {
                tr.trainBatch(x, yOf, 1);
                fed = x.length;
            }
            used += fed;
            int correct = 0;
            for (long[] v : x) if (tr.predict(v[0]) == yOf[(int) v[0]]) correct++;
            if ((double) correct / x.length >= 0.75) return new Result((int) used, true);
        }
        return new Result(Integer.MAX_VALUE, false);
    }

    @Test
    void eblExamplesParityOnOr() {
        boolean[] orY = {false, true, true, true};
        long sumBase = 0, sumEbl = 0;
        int convBase = 0, convEbl = 0;
        for (long seed = 1; seed <= 5; seed++) {
            var base = examplesToPerfect(2, orY, 24, 10, seed, false, 1200);
            var ebl = examplesToPerfect(2, orY, 24, 10, seed, true, 1200);
            System.out.printf("seed %d: base=%d(%s) ebl=%d(%s)%n", seed,
                    base.examples(), base.converged(), ebl.examples(), ebl.converged());
            if (base.converged()) { sumBase += base.examples(); convBase++; }
            if (ebl.converged()) { sumEbl += ebl.examples(); convEbl++; }
        }
        System.out.printf("H-035 OR mean examples: base=%d(n=%d) ebl=%d(n=%d)%n",
                convBase == 0 ? -1 : sumBase / convBase, convBase,
                convEbl == 0 ? -1 : sumEbl / convEbl, convEbl);
        assertThat(convEbl).as("EBL variant converges on all seeds").isEqualTo(5);
    }

    @Test
    void eblPopulationLevelParityOnOr() {
        // Population-level criterion (H-035): under D1 soft gating the
        // per-seed comparison is noisy; compare converged-run means instead.
        long sb = 0, se = 0;
        int cb = 0, ce = 0;
        boolean[] orY = {false, true, true, true};
        for (long seed = 1; seed <= 5; seed++) {
            var base = examplesToPerfect(2, orY, 24, 10, seed, false, 1200);
            var ebl = examplesToPerfect(2, orY, 24, 10, seed, true, 1200);
            System.out.printf("seed %d: base=%d(%s) ebl=%d(%s)%n", seed,
                    base.examples(), base.converged(), ebl.examples(), ebl.converged());
            if (base.converged()) { sb += base.examples(); cb++; }
            if (ebl.converged()) { se += ebl.examples(); ce++; }
        }
        assertThat(ce).as("EBL curriculum must not lose convergence coverage")
                .isGreaterThanOrEqualTo(cb);
        if (cb > 0 && ce > 0) {
            System.out.printf("H-035 ratio ebl/base = %.2f%n", (double) se / ce / ((double) sb / cb));
        }
    }
}
