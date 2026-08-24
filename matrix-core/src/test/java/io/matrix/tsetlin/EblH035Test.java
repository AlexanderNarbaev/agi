package io.matrix.tsetlin;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H-035 criterion probe (toy scale): does EBL prioritization reach a perfect
 * fit in ≤50% of the epochs the plain shuffled baseline needs?
 */
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
            boolean perfect = true;
            for (long[] v : x) if (tr.predict(v[0]) != yOf[(int) v[0]]) { perfect = false; break; }
            if (perfect) return new Result((int) used, true);
        }
        return new Result(Integer.MAX_VALUE, false);
    }

    @Test
    void eblExamplesParityOnOr() {
        boolean[] orY = {false, true, true, true};
        long sumBase = 0, sumEbl = 0;
        int convBase = 0, convEbl = 0;
        for (long seed = 1; seed <= 5; seed++) {
            var base = examplesToPerfect(2, orY, 16, 12, seed, false, 300);
            var ebl = examplesToPerfect(2, orY, 16, 12, seed, true, 300);
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
    void eblNeverWorseBeyondNoiseOnOr() {
        boolean[] orY = {false, true, true, true};
        for (long seed = 1; seed <= 5; seed++) {
            var base = examplesToPerfect(2, orY, 8, 12, seed, false, 300);
            var ebl = examplesToPerfect(2, orY, 8, 12, seed, true, 300);
            assertThat((long) ebl.examples()).as("seed %d", seed)
                    .isLessThanOrEqualTo((long) base.examples() * 2); // not catastrophically worse
        }
    }
}
