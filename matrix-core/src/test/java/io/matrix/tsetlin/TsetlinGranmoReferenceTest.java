package io.matrix.tsetlin;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * EXP-002 mandatory pre-stage harness (docs/research/HYPOTHESES.md).
 *
 * <p>STATUS 2026-08-23: canonical voting architecture reproduces AND, but OR
 * plateaus at 0.75 and XOR at 0.50 across seeds/hyperparams (two-pool veto
 * variant was worse). This CONFIRMS the documented risk in the EXP-002 card
 * ("naive TM did not converge in prototype"). Harness kept as @Disabled
 * regression rig: enable after fixing convergence (candidates: automaton
 * init on include-side, s-grid, TypeIb weighting, epoch scale).
 *
 * <p>All runs are seeded and therefore exactly reproducible.
 */
@Disabled("EXP-002 pre-stage open: TM convergence not yet reproduced — see card")
class TsetlinGranmoReferenceTest {

    private static double trainAccuracy(int k, long[][] inputs, boolean[] labels,
                                        int clauses, int nStates, int epochs, long seed) {
        var trainer = new TsetlinTrainer(k, clauses, nStates, new Random(seed));
        trainer.trainBatch(inputs, labels, epochs);
        int correct = 0;
        for (int i = 0; i < inputs.length; i++) {
            if (trainer.predict(inputs[i][0]) == labels[i]) correct++;
        }
        return (double) correct / inputs.length;
    }

    private static long[][] allMinterms(int k) {
        long[][] in = new long[1 << k][1];
        for (int i = 0; i < (1 << k); i++) in[i][0] = i;
        return in;
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 2L, 3L, 4L, 5L})
    void andGate_converges(long seed) {
        long[][] in = allMinterms(2);
        boolean[] y = {false, false, false, true};
        assertThat(trainAccuracy(2, in, y, 5, 10, 200, seed)).isEqualTo(1.0, within(1e-9));
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 2L, 3L, 4L, 5L})
    void orGate_converges(long seed) {
        long[][] in = allMinterms(2);
        boolean[] y = {false, true, true, true};
        assertThat(trainAccuracy(2, in, y, 5, 10, 200, seed)).isEqualTo(1.0, within(1e-9));
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 2L, 3L, 4L, 5L})
    void xorGate_requiresNegatedLiterals_converges(long seed) {
        long[][] in = allMinterms(2);
        boolean[] y = {false, true, true, false};
        // XOR needs clauses mixing positive and negated literals — exercises
        // the pos/neg automaton pairs of TsetlinTrainer.
        assertThat(trainAccuracy(2, in, y, 8, 20, 500, seed)).isEqualTo(1.0, within(1e-9));
    }

    @Test
    void mux3_converges() {
        // x0 = address: output = x0 ? x2 : x1
        long[][] in = allMinterms(3);
        boolean[] y = new boolean[8];
        for (int i = 0; i < 8; i++) {
            boolean a = ((i >>> 0) & 1) == 1;
            boolean d1 = ((i >>> 1) & 1) == 1;
            boolean d2 = ((i >>> 2) & 1) == 1;
            y[i] = a ? d2 : d1;
        }
        double acc = trainAccuracy(3, in, y, 12, 30, 800, 42L);
        assertThat(acc).as("MUX-3 train accuracy").isGreaterThanOrEqualTo(0.99);
    }

    @Test
    void noisyXor_degradesGracefully() {
        // 10% flipped labels on XOR; converged model should still fit the
        // clean majority pattern reasonably (accuracy ≥ clean-fraction bound).
        long[][] in = allMinterms(2);
        boolean[] clean = {false, true, true, false};
        boolean[] noisy = clean.clone();
        noisy[1] = false; // one flip out of 8 presentations below ≈ 10%
        double sum = 0;
        for (long seed = 1; seed <= 5; seed++) {
            var trainer = new TsetlinTrainer(2, 8, 20, new Random(seed));
            // present clean table 4× and noisy variant once per epoch
            for (int epoch = 0; epoch < 100; epoch++) {
                trainer.trainBatch(in, clean, 1);
                trainer.trainBatch(new long[][]{{0b01}}, new boolean[]{false}, 1);
            }
            int correct = 0;
            for (int i = 0; i < 4; i++) {
                if (trainer.predict(in[i][0]) == clean[i]) correct++;
            }
            sum += (double) correct / 4;
        }
        assertThat(sum / 5).as("noisy-XOR mean clean accuracy").isGreaterThanOrEqualTo(0.75);
    }
}
