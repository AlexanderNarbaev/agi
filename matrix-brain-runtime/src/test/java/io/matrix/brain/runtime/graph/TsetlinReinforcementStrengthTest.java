package io.matrix.brain.runtime.graph;

import io.matrix.tsetlin.TsetlinTrainer;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W37.3b — regression guard for the reinforcement-strength finding.
 *
 * <p>Earlier notes recorded "parity-3 and wider do not learn" as an open defect. That was measured
 * at {@code s = 4.0} and was an under-provisioning result, not a broken learner: {@code s} controls
 * how often a clause is reinforced, and a non-monotone target needs a weaker reinforcement rate.
 * At {@code s >= 2^k * 4}, parity-3 through parity-6 reach 100% on 16/16 seeds.</p>
 *
 * <p>This pins the finding so the sizing cannot silently regress, and pins the no-regression
 * property in the other direction: conjunctions must stay at 100% across the whole {@code s} range,
 * otherwise the recommendation would be unusable.</p>
 */
class TsetlinReinforcementStrengthTest {

    /** A training set: packed inputs plus labels. */
    private record Data(long[][] x, boolean[] y) {}

    /** The full truth table for parity-k over k bits. */
    private static Data parity(int bits) {
        int n = 1 << bits;
        long[][] x = new long[n][];
        boolean[] y = new boolean[n];
        for (int i = 0; i < n; i++) {
            x[i] = new long[] {i};
            y[i] = (Integer.bitCount(i) & 1) == 1;
        }
        return new Data(x, y);
    }

    private static double parityAccuracy(int bits, double s, int clauses, int epochs, long seed) {
        Data data = parity(bits);
        var t = new TsetlinTrainer(bits, clauses, 200, new Random(seed),
                TsetlinTrainer.InitStrategy.RANDOM, s);
        t.trainBatch(data.x(), data.y(), epochs);
        int correct = 0;
        for (int i = 0; i < data.y().length; i++) {
            if (t.predict(data.x()[i][0]) == data.y()[i]) correct++;
        }
        return correct * 100.0 / data.y().length;
    }

    @Test
    @DisplayName("the recommended s reaches 100% on parity-3 over 16 seeds")
    void recommendedSReachesPerfectOnParity3() {
        double s = TsetlinTrainer.recommendS(3);
        for (long seed = 1; seed <= 16; seed++) {
            assertThat(parityAccuracy(3, s, 1024, 200, seed))
                    .as("seed %d at s=%.0f: the recommendation must hold for every seed, not just "
                            + "the average, or it is not a recommendation", seed, s)
                    .isEqualTo(100.0);
        }
    }

    @Test
    @DisplayName("the recommended s reaches 100% on parity-4 and parity-5")
    void recommendedSReachesPerfectOnWiderParity() {
        for (int bits : new int[] {4, 5}) {
            double s = TsetlinTrainer.recommendS(bits);
            for (long seed = 1; seed <= 8; seed++) {
                assertThat(parityAccuracy(bits, s, 1024, 200, seed))
                        .as("parity-%d seed %d at s=%.0f", bits, seed, s)
                        .isEqualTo(100.0);
            }
        }
    }

    @Test
    @DisplayName("conjunctions stay at 100% across the whole s range (no regression)")
    void conjunctionsUnaffectedByS() {
        for (double s : new double[] {4.0, 8.0, 16.0, 32.0}) {
            Random rng = new Random(7);
            int n = 600;
            long[][] in = new long[n][];
            boolean[] y = new boolean[n];
            for (int i = 0; i < n; i++) {
                boolean p = rng.nextBoolean();
                y[i] = p;
                long w = 0;
                if (p) { w |= 1L; w |= 2L; w |= 4L; } // x0 AND x1 AND x2
                in[i] = new long[] {w};
            }
            var t = new TsetlinTrainer(16, 64, 200, new Random(42L),
                    TsetlinTrainer.InitStrategy.RANDOM, s);
            t.trainBatch(in, y, 10);
            int correct = 0;
            for (int i = 0; i < n; i++) if (t.predict(in[i][0]) == y[i]) correct++;
            assertThat(correct * 100.0 / n)
                    .as("conjunction-3 at s=%.1f must not degrade; raising s has to stay safe for "
                            + "the existing callers", s)
                    .isEqualTo(100.0);
        }
    }

    @Test
    @DisplayName("recommendS returns the measured lower bound, not DEFAULT_S")
    void recommendSMatchesMeasuredBound() {
        assertThat(TsetlinTrainer.recommendS(3)).isEqualTo(32.0);
        assertThat(TsetlinTrainer.recommendS(4)).isEqualTo(64.0);
        assertThat(TsetlinTrainer.recommendS(5)).isEqualTo(128.0);
        assertThat(TsetlinTrainer.recommendS(6)).isEqualTo(256.0);
        assertThat(TsetlinTrainer.recommendS(0))
                .as("a target depending on nothing is the trivial case")
                .isEqualTo(TsetlinTrainer.DEFAULT_S);
        assertThat(TsetlinTrainer.recommendS(3))
                .as("recommendS must differ from DEFAULT_S, or the finding is not actionable")
                .isNotEqualTo(TsetlinTrainer.DEFAULT_S);
    }

    @Test
    @DisplayName("countInformativeBits ignores constant dimensions")
    void countsOnlyVaryingDimensions() {
        // 16 declared bits, only 3 ever vary.
        long[][] rows = new long[64][];
        Random rng = new Random(3);
        for (int i = 0; i < rows.length; i++) {
            long w = 0;
            for (int b = 0; b < 3; b++) if (rng.nextBoolean()) w |= 1L << b;
            rows[i] = new long[] {w};
        }
        assertThat(TsetlinTrainer.countInformativeBits(rows, 16))
                .as("constant dimensions must not inflate the estimate, or a 1024-bit HDC vector "
                        + "with 6 active dimensions would ask for s = 2^1024")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("countInformativeBits counts every dimension when all of them vary")
    void countsAllWhenEverythingVaries() {
        // The complement of the constant-dimension case: a function that counts everything must
        // count all 16. Without this, replacing the `sawOne && sawZero` test with an
        // unconditional increment goes unnoticed.
        Random rng = new Random(11);
        long[][] rows = new long[256][];
        for (int i = 0; i < rows.length; i++) {
            long w = rng.nextLong();
            rows[i] = new long[] {w};
        }
        assertThat(TsetlinTrainer.countInformativeBits(rows, 16))
                .as("every bit that takes both values must be counted")
                .isEqualTo(16);
    }

    @Test
    @DisplayName("recommendS clamps at the 20-bit input limit instead of overflowing")
    void recommendSClampsAtInputLimit() {
        double s20 = TsetlinTrainer.recommendS(20);
        assertThat(s20)
                .as("2^20 * 4 is the largest s the trainer can be asked for; a caller passing the "
                        + "full 20-bit width must get the cap, not 2^20 * 4")
                .isEqualTo(1 << 20);
        assertThat(TsetlinTrainer.recommendS(19))
                .as("19 bits is still addressable")
                .isGreaterThan(TsetlinTrainer.recommendS(18));
    }

    @Test
    @DisplayName("countInformativeBits reports zero for an all-constant training set")
    void countsZeroWhenNothingVaries() {
        long[][] rows = new long[10][];
        for (int i = 0; i < rows.length; i++) rows[i] = new long[] {0L};
        assertThat(TsetlinTrainer.countInformativeBits(rows, 16)).isZero();
    }
}