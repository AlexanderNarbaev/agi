package io.matrix.tsetlin;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-010 full mini-protocol (H-010): dataset grid × seeds, honest train-side
 * model selection for Tsetlin, single-run JVM wall-clock per run.
 *
 * <p>METHODOLOGY: deterministic synthetic binary datasets; numbers are real
 * measurements but NOT JMH-grade; verdict recorded in
 * docs/research/reports/EXP-010-report.md and HYPOTHESES.md.
 */
class Exp010ComparisonTest {

    private static final int EPOCHS_BASE = 5;
    private static final long SEED = 42L;

    private record Dataset(int bits, int informative, double pHi, double pLo, long seed) {}

    private static long pack(boolean[] bits) {
        long w = 0;
        for (int i = 0; i < bits.length && i < 64; i++) {
            if (bits[i]) {
                w |= 1L << i;
            }
        }
        return w;
    }

    /** One measured run over a dataset. Returns {speedup, accTsetlin, accWisard}. */
    private static double[] runOnce(Dataset ds) {
        Random dataRnd = new Random(ds.seed());
        int train = 320;
        int test = 80;
        boolean[][] x = new boolean[train + test][ds.bits()];
        boolean[] y = new boolean[train + test];
        for (int i = 0; i < x.length; i++) {
            y[i] = dataRnd.nextBoolean();
            for (int b = 0; b < ds.bits(); b++) {
                double pOne = b < ds.informative() ? (y[i] ? ds.pHi() : ds.pLo()) : 0.5;
                x[i][b] = dataRnd.nextDouble() < pOne;
            }
        }
        long[][] trainWords = new long[train][];
        long[][] testWords = new long[test][];
        boolean[] trainY = new boolean[train];
        boolean[] testY = new boolean[test];
        for (int i = 0; i < train; i++) {
            trainWords[i] = new long[]{pack(x[i])};
            trainY[i] = y[i];
        }
        for (int i = 0; i < test; i++) {
            testWords[i] = new long[]{pack(x[train + i])};
            testY[i] = y[train + i];
        }

        // Tsetlin grid — model selection strictly by TRAIN accuracy.
        int[][] grid = {{20, 5, 4}, {50, 10, 4}, {100, 20, 8}, {50, 20, 8}};
        TsetlinTrainer best = null;
        double bestTrainAcc = -1;
        int[] bestCfg = grid[0];
        for (int[] cfg : grid) {
            TsetlinTrainer cand = new TsetlinTrainer(ds.bits(), cfg[0], 100, new Random(SEED));
            cand.trainBatch(trainWords, trainY, cfg[1]);
            int hit = 0;
            for (int i = 0; i < train; i++) {
                if (cand.predict(trainWords[i][0]) == trainY[i]) hit++;
            }
            if (hit / (double) train > bestTrainAcc) {
                bestTrainAcc = hit / (double) train;
                best = cand;
                bestCfg = cfg;
            }
        }

        long t0 = System.nanoTime();
        TsetlinTrainer timed = new TsetlinTrainer(ds.bits(), bestCfg[0], 100, new Random(SEED));
        timed.trainBatch(trainWords, trainY, bestCfg[1]);
        long t1 = System.nanoTime();
        int correctT = 0;
        for (int i = 0; i < test; i++) {
            if (timed.predict(testWords[i][0]) == testY[i]) correctT++;
        }

        WisardProducer wisard = new WisardProducer(ds.bits(), 8, SEED);
        long w0 = System.nanoTime();
        for (int e = 0; e < EPOCHS_BASE; e++) {
            for (int i = 0; i < train; i++) {
                wisard.train(trainWords[i], trainY[i] ? 1 : 0);
            }
        }
        long w1 = System.nanoTime();
        int correctW = 0;
        for (int i = 0; i < test; i++) {
            if (wisard.classifyLabel(testWords[i]) == (testY[i] ? 1 : 0)) correctW++;
        }

        double tsetlinMs = (t1 - t0) / 1e6;
        double wisardMs = (w1 - w0) / 1e6;
        System.out.printf(
                "EXP010 run bits=%d inf=%d seed=%d cfg=%dx%dx%d tsetlinMs=%.3f wisardMs=%.3f accT=%.4f accW=%.4f speedup=%.2fx%n",
                ds.bits(), ds.informative(), ds.seed(), bestCfg[0], bestCfg[1], bestCfg[2],
                tsetlinMs, wisardMs,
                correctT / (double) test, correctW / (double) test,
                tsetlinMs / Math.max(wisardMs, 1e-9));
        return new double[]{
                tsetlinMs / Math.max(wisardMs, 1e-9),
                correctT / (double) test,
                correctW / (double) test};
    }

    @Test
    void exp010ProtocolNineRuns() {
        List<Dataset> datasets = List.of(
                new Dataset(16, 10, 0.7, 0.3, 42),
                new Dataset(16, 12, 0.8, 0.2, 42),
                new Dataset(20, 14, 0.7, 0.3, 42),
                new Dataset(16, 10, 0.7, 0.3, 43),
                new Dataset(16, 12, 0.8, 0.2, 43),
                new Dataset(20, 14, 0.7, 0.3, 43),
                new Dataset(16, 10, 0.7, 0.3, 44),
                new Dataset(16, 12, 0.8, 0.2, 44),
                new Dataset(20, 14, 0.7, 0.3, 44));

        List<Double> speedups = new ArrayList<>();
        int winsAccuracy = 0;
        double minAdvantagePp = Double.MAX_VALUE;
        for (Dataset ds : datasets) {
            double[] r = runOnce(ds);
            speedups.add(r[0]);
            double advantagePp = (r[2] - r[1]) * 100; // WiSARD minus Tsetlin
            minAdvantagePp = Math.min(minAdvantagePp, advantagePp);
            if (advantagePp >= -2.0) winsAccuracy++;
            assertThat(Math.max(r[1], r[2])).isGreaterThan(0.5); // learnability sanity
        }
        speedups.sort(Double::compareTo);
        double medianSpeedup = speedups.get(speedups.size() / 2);

        System.out.printf(
                "EXP010_PROTOCOL datasets=%d medianSpeedup=%.2fx minAdvantage_pp=%.2f wisardWins=%d/%d%n",
                datasets.size(), medianSpeedup, minAdvantagePp, winsAccuracy, datasets.size());
    }
}
