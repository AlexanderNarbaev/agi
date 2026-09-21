package io.matrix.evolution;

import io.matrix.bir.ClauseSetForm;
import io.matrix.tsetlin.TsetlinTrainer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-002 measurement harness (H-002): Tsetlin CLAUSESET vs MPDT-GA producer
 * on identical synthetic binary datasets — accuracy, artifact literals,
 * training wall-clock (single-run JVM, deterministic seeds; NOT JMH-grade).
 */
class Exp002ComparisonTest {

    private record Dataset(int bits, int informative, double pHi, double pLo) {}

    private static final long SEED = 42L;
    private static final int TRAIN = 320;
    private static final int TEST = 80;

    private static long pack(boolean[] bits) {
        long w = 0;
        for (int i = 0; i < bits.length && i < 64; i++) {
            if (bits[i]) w |= 1L << i;
        }
        return w;
    }

    private record Data(long[] trainX, boolean[] trainY, long[] testX, boolean[] testY) {}

    private static Data gen(Dataset ds, long seed) {
        Random r = new Random(seed);
        Data d = new Data(new long[TRAIN], new boolean[TRAIN],
                new long[TEST], new boolean[TEST]);
        for (int i = 0; i < TRAIN + TEST; i++) {
            boolean label = r.nextBoolean();
            boolean[] bits = new boolean[ds.bits()];
            for (int b = 0; b < ds.bits(); b++) {
                double p = b < ds.informative() ? (label ? ds.pHi() : ds.pLo()) : 0.5;
                bits[b] = r.nextDouble() < p;
            }
            if (i < TRAIN) {
                d.trainX()[i] = pack(bits);
                d.trainY()[i] = label;
            } else {
                d.testX()[i - TRAIN] = pack(bits);
                d.testY()[i - TRAIN] = label;
            }
        }
        return d;
    }

    private static long[][] toWords(long[] packed) {
        long[][] words = new long[packed.length][];
        for (int i = 0; i < packed.length; i++) {
            words[i] = new long[]{packed[i]};
        }
        return words;
    }

    private static double acc(TsetlinTrainer t, long[] xs, boolean[] ys) {
        int hit = 0;
        for (int i = 0; i < xs.length; i++) {
            if (t.predict(xs[i]) == ys[i]) hit++;
        }
        return hit / (double) xs.length;
    }

    private static double gaAcc(MpdtGaProducer ga, long[] xs, boolean[] ys) {
        int hit = 0;
        for (int i = 0; i < xs.length; i++) {
            if (ga.predict(xs[i]) == ys[i]) hit++;
        }
        return hit / (double) xs.length;
    }

    private static int litT(ClauseSetForm csf) {
        int lit = 0;
        for (ClauseSetForm.Clause c : csf.clauses()) {
            lit += Long.bitCount(c.pos[0]) + Long.bitCount(c.neg[0]);
        }
        return lit;
    }

    @Test
    void exp002MeasuredComparison() {
        List<Dataset> datasets = List.of(
                new Dataset(16, 10, 0.7, 0.3),
                new Dataset(16, 12, 0.8, 0.2),
                new Dataset(20, 14, 0.7, 0.3));
        int[][] grid = {{20, 5, 4}, {50, 10, 4}, {100, 20, 8}, {50, 20, 8}};

        for (Dataset ds : datasets) {
            Data d = gen(ds, SEED);

            // Tsetlin grid selection by TRAIN accuracy.
            TsetlinTrainer best = null;
            double bestTrain = -1;
            int[] bestCfg = grid[0];
            for (int[] cfg : grid) {
                var cand = new TsetlinTrainer(ds.bits(), cfg[0], 100, new Random(SEED));
                cand.trainBatch(toWords(d.trainX()), d.trainY(), cfg[1]);
                double a = acc(cand, d.trainX(), d.trainY());
                if (a > bestTrain) {
                    bestTrain = a;
                    best = cand;
                    bestCfg = cfg;
                }
            }
            long t0 = System.nanoTime();
            var tsetlin = new TsetlinTrainer(ds.bits(), bestCfg[0], 100, new Random(SEED));
            tsetlin.trainBatch(toWords(d.trainX()), d.trainY(), bestCfg[1]);
            long t1 = System.nanoTime();

            // GA baseline.
            MpdtGaProducer ga = new MpdtGaProducer(ds.bits(), 12, 40, SEED);
            long g0 = System.nanoTime();
            ga.trainBatch(d.trainX(), d.trainY(), 30);
            long g1 = System.nanoTime();

            double accT = acc(tsetlin, d.testX(), d.testY());
            double accG = gaAcc(ga, d.testX(), d.testY());
            double msT = (t1 - t0) / 1e6;
            double msG = (g1 - g0) / 1e6;

            System.out.printf(
                    "EXP002 run bits=%d inf=%d cfg=%dx%dx%d tsetlinMs=%.3f gaMs=%.3f accT=%.4f accGA=%.4f litT=%d litGA=%d%n",
                    ds.bits(), ds.informative(), bestCfg[0], bestCfg[1], bestCfg[2],
                    msT, msG, accT, accG, litT(best.toDecisionClauseSet("exp002")), ga.literalCount());

            assertThat(accT).isGreaterThan(0.5);
            assertThat(accG).isGreaterThan(0.5);
        }
    }
}
