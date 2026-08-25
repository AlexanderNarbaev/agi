package io.matrix.tsetlin;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-010 measurement harness (H-010): single-pass WNN (WiSARD) vs
 * TsetlinTrainer — training wall-clock and held-out accuracy on a
 * deterministic synthetic binary dataset.
 *
 * <p>METHODOLOGY NOTE: numbers are single-run JVM measurements with fixed
 * seeds (deterministic dataset/model init). This is NOT JMH-grade timing;
 * hypothesis verdicts remain governed by the preregistered gates in
 * docs/research/HYPOTHESES.md (H-010).
 */
class Exp010ComparisonTest {

    private static final int BITS = 16; // K_MAX-conform trainer arity
    private static final int INFORMATIVE = 10;
    private static final int TRAIN = 320;
    private static final int TEST = 80;
    private static final long SEED = 42L;
    private static final int EPOCHS = 5;

    private static long pack(boolean[] bits) {
        long w = 0;
        for (int i = 0; i < bits.length && i < 64; i++) {
            if (bits[i]) {
                w |= 1L << i;
            }
        }
        return w;
    }

    @Test
    void exp010MeasuredComparison() {
        Random dataRnd = new Random(SEED);

        // Dataset: informative bits biased by class, noise bits uniform.
        boolean[][] x = new boolean[TRAIN + TEST][BITS];
        boolean[] y = new boolean[TRAIN + TEST];
        for (int i = 0; i < x.length; i++) {
            y[i] = dataRnd.nextBoolean();
            for (int b = 0; b < BITS; b++) {
                double pOne = b < INFORMATIVE ? (y[i] ? 0.7 : 0.3) : 0.5;
                x[i][b] = dataRnd.nextDouble() < pOne;
            }
        }

        long[][] trainWords = new long[TRAIN][];
        long[][] testWords = new long[TEST][];
        boolean[] trainY = new boolean[TRAIN];
        boolean[] testY = new boolean[TEST];
        for (int i = 0; i < TRAIN; i++) {
            trainWords[i] = new long[]{pack(x[i])};
            trainY[i] = y[i];
        }
        for (int i = 0; i < TEST; i++) {
            testWords[i] = new long[]{pack(x[TRAIN + i])};
            testY[i] = y[TRAIN + i];
        }

        // --- Tsetlin ---
        long t0 = System.nanoTime();
        TsetlinTrainer tsetlin = new TsetlinTrainer(BITS, 20, 100, new Random(SEED));
        tsetlin.trainBatch(trainWords, trainY, EPOCHS);
        long t1 = System.nanoTime();
        int correctT = 0;
        for (int i = 0; i < TEST; i++) {
            if (tsetlin.predict(testWords[i][0]) == testY[i]) {
                correctT++;
            }
        }
        double accT = correctT / (double) TEST;

        // --- WiSARD ---
        WisardProducer wisard = new WisardProducer(BITS, 8, SEED);
        long w0 = System.nanoTime();
        for (int e = 0; e < EPOCHS; e++) {
            for (int i = 0; i < TRAIN; i++) {
                wisard.train(trainWords[i], trainY[i] ? 1 : 0);
            }
        }
        long w1 = System.nanoTime();
        int correctW = 0;
        for (int i = 0; i < TEST; i++) {
            if (wisard.classifyLabel(testWords[i]) == (testY[i] ? 1 : 0)) {
                correctW++;
            }
        }
        double accW = correctW / (double) TEST;

        double tsetlinMs = (t1 - t0) / 1_000_000.0;
        double wisardMs = (w1 - w0) / 1_000_000.0;

        System.out.printf(
                "EXP010 result: tsetlinMs=%.3f wisardMs=%.3f accTsetlin=%.4f accWisard=%.4f speedupWnnOverTsetlin=%.2fx%n",
                tsetlinMs, wisardMs, accT, accW, tsetlinMs / Math.max(wisardMs, 1e-9));

        // Sanity only — hypothesis gates are judged in the EXP-010 report.
        assertThat(accT).isGreaterThan(0.55);
        assertThat(accW).isGreaterThan(0.55);
    }
}
