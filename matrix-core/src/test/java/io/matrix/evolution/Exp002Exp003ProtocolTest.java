package io.matrix.evolution;

import io.matrix.tsetlin.TsetlinTrainer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-002/003 convergence protocol (H-002/H-003): examples-to-99%-train-accuracy
 * for Tsetlin CLAUSESET line vs MPDT-GA «living learner» line on identical data.
 *
 * <p>METHODOLOGY: deterministic synthetic datasets (seed 42), doubling subsets
 * 20→320; single-run JVM wall-clock; NOT JMH-grade. Verdicts recorded in
 * docs/research/reports/ per preregistered gates.
 */
class Exp002Exp003ProtocolTest {

    private record Dataset(int bits, int informative, double pHi, double pLo) {}

    private record Data(long[] trainX, boolean[] trainY, long[] testX, boolean[] testY) {}

    private static long pack(boolean[] bits) {
        long w = 0;
        for (int i = 0; i < bits.length && i < 64; i++) {
            if (bits[i]) w |= 1L << i;
        }
        return w;
    }

    private static Data gen(Dataset ds, long seed) {
        Random r = new Random(seed);
        Data d = new Data(new long[320], new boolean[320], new long[80], new boolean[80]);
        for (int i = 0; i < 400; i++) {
            boolean label = r.nextBoolean();
            boolean[] bits = new boolean[ds.bits()];
            for (int b = 0; b < ds.bits(); b++) {
                double p = b < ds.informative() ? (label ? ds.pHi() : ds.pLo()) : 0.5;
                bits[b] = r.nextDouble() < p;
            }
            if (i < 320) {
                d.trainX()[i] = pack(bits);
                d.trainY()[i] = label;
            } else {
                d.testX()[i - 320] = pack(bits);
                d.testY()[i - 320] = label;
            }
        }
        return d;
    }

    /** Minimal prefix size reaching ≥0.99 train accuracy (∞ → 999). */
    private static int examplesToTarget(long[] xs, boolean[] ys, TrainerLine line) {
        int[] sizes = {20, 40, 80, 160, 320};
        for (int n : sizes) {
            if (line.trainSubsetAcc(xs, ys, n) >= 0.99) {
                return n;
            }
        }
        return 999;
    }

    private interface TrainerLine {
        String name();

        void trainFull(long[] xs, boolean[] ys);

        double trainSubsetAcc(long[] xs, boolean[] ys, int n);

        double testAcc(long[] xs, boolean[] ys);

        long wallClockFullMs();
    }

    private final class TsetlinLine implements TrainerLine {
        private long ms;

        @Override public String name() { return "tsetlin"; }

        @Override
        public void trainFull(long[] xs, boolean[] ys) {
            // Grid-best config from EXP-010 protocol.
            var t = new TsetlinTrainer(xs.length > 0 ? mostFrequentBits(xs) : 16, 100, 100, new Random(SEED));
            long s = System.nanoTime();
            t.trainBatch(toWords(xs), ys, 20);
            ms = (System.nanoTime() - s) / 1_000_000;
            this.trained = t;
        }

        private TsetlinTrainer trained;

        @Override
        public double trainSubsetAcc(long[] xs, boolean[] ys, int n) {
            var cand = new TsetlinTrainer(mostFrequentBits(xs), 50, 100, new Random(SEED));
            long[][] w = new long[n][];
            boolean[] l = new boolean[n];
            for (int i = 0; i < n; i++) {
                w[i] = new long[]{xs[i]};
                l[i] = ys[i];
            }
            cand.trainBatch(w, l, 20);
            int hit = 0;
            for (int i = 0; i < n; i++) {
                if (cand.predict(xs[i]) == ys[i]) hit++;
            }
            return hit / (double) n;
        }

        @Override
        public double testAcc(long[] xs, boolean[] ys) {
            int hit = 0;
            for (int i = 0; i < xs.length; i++) {
                if (trained.predict(xs[i]) == ys[i]) hit++;
            }
            return hit / (double) xs.length;
        }

        @Override public long wallClockFullMs() { return ms; }
    }

    private final class GaLine implements TrainerLine {
        private long ms;
        private MpdtGaProducer trainedGa;

        @Override public String name() { return "mpdt-ga"; }

        @Override
        public void trainFull(long[] xs, boolean[] ys) {
            var ga = new MpdtGaProducer(mostFrequentBits(xs), 12, 40, SEED);
            long s = System.nanoTime();
            ga.trainBatch(xs, ys, 30);
            ms = (System.nanoTime() - s) / 1_000_000;
            this.trainedGa = ga;
        }

        @Override
        public double trainSubsetAcc(long[] xs, boolean[] ys, int n) {
            var ga = new MpdtGaProducer(mostFrequentBits(xs), 12, 40, SEED);
            long[] sub = java.util.Arrays.copyOf(xs, n);
            boolean[] l = java.util.Arrays.copyOf(ys, n);
            ga.trainBatch(sub, l, 30);
            return gaAccOn(ga, sub, l);
        }

        @Override
        public double testAcc(long[] xs, boolean[] ys) {
            return gaAccOn(trainedGa, xs, ys);
        }

        @Override public long wallClockFullMs() { return ms; }
    }

    private static double gaAccOn(MpdtGaProducer ga, long[] xs, boolean[] ys) {
        int hit = 0;
        for (int i = 0; i < xs.length; i++) {
            if (ga.predict(xs[i]) == ys[i]) hit++;
        }
        return hit / (double) xs.length;
    }

    /** Dominant input width proxy: highest set bit across samples +1, clamped to [4,20]. */
    private static int mostFrequentBits(long[] xs) {
        long or = 0;
        for (long v : xs) or |= v;
        int k = 64 - Long.numberOfLeadingZeros(or);
        return Math.max(4, Math.min(20, k == 0 ? 4 : k));
    }

    private static long SEED = 42L;

    private static long[][] toWords(long[] packed) {
        long[][] w = new long[packed.length][];
        for (int i = 0; i < packed.length; i++) w[i] = new long[]{packed[i]};
        return w;
    }

    @Test
    void exp002Exp003ConvergenceProtocol() {
        List<Dataset> datasets = List.of(
                new Dataset(16, 10, 0.7, 0.3),
                new Dataset(16, 12, 0.8, 0.2),
                new Dataset(20, 14, 0.7, 0.3));

        double sumTsetlinTo99 = 0, sumGaTo99 = 0;
        int tsetlinReached = 0, gaReached = 0;
        double sumTestT = 0, sumTestG = 0;

        for (Dataset ds : datasets) {
            Data d = gen(ds, SEED);
            TrainerLine tl = new TsetlinLine();
            TrainerLine gl = new GaLine();
            tl.trainFull(d.trainX(), d.trainY());
            gl.trainFull(d.trainX(), d.trainY());

            int nT = examplesToTarget(d.trainX(), d.trainY(), tl);
            int nG = examplesToTarget(d.trainX(), d.trainY(), gl);
            double aT = tl.testAcc(d.testX(), d.testY());
            double aG = gl.testAcc(d.testX(), d.testY());

            sumTsetlinTo99 += Math.min(nT, 999);
            sumGaTo99 += Math.min(nG, 999);
            if (nT <= 320) tsetlinReached++;
            if (nG <= 320) gaReached++;
            sumTestT += aT;
            sumTestG += aG;

            System.out.printf(
                    "EXP003 run bits=%d inf=%d to99 tsetlin=%d ga=%d | fullMs t=%d g=%d | testAcc t=%.4f g=%.4f%n",
                    ds.bits(), ds.informative(), nT, nG,
                    tl.wallClockFullMs(), gl.wallClockFullMs(), aT, aG);

            assertThat(Math.max(aT, aG)).isGreaterThan(0.5);
        }

        System.out.printf(
                "EXP002_PROTOCOL datasets=3 avgTo99 tsetlin=%.1f ga=%.1f reached99 t=%d g=%d avgTestAcc t=%.4f g=%.4f%n",
                sumTsetlinTo99 / datasets.size(), sumGaTo99 / datasets.size(),
                tsetlinReached, gaReached,
                sumTestT / datasets.size(), sumTestG / datasets.size());
    }
}
