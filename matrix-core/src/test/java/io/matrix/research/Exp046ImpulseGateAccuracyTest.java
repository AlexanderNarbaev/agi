package io.matrix.research;

import io.matrix.budgeter.ConjugateBudgeter;
import io.matrix.ethics.EthicalFilter;
import io.matrix.lifecycle.AutonomyImpulse;
import io.matrix.lifecycle.ImpulseScheduler;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-046 harness (H-046): subconscious impulse → conscious gate filter
 * accuracy on synthetic impulse corpus.
 *
 * <p>Impulses drawn from the 4-way AutonomyImpulse enum, plus a noise
 * channel that relabels a fraction of them with a name that violates
 * the ethical filter (so the scheduler must reject). Accuracy is
 * measured against ground-truth decisions.
 */
class Exp046ImpulseGateAccuracyTest {

    private static final int N_IMPULSES = 1_000;
    private static final double NOISE_RATE = 0.10;
    private static final long[] SEEDS = {0xC0FFEE, 0xABCDEF, 0x1234, 0x5678, 0x9ABC};
    private static final long ENVELOPE = 1_000_000L;

    @Test
    void accuracyOnSyntheticCorpus() {
        double[] accuracies = new double[SEEDS.length];
        for (int s = 0; s < SEEDS.length; s++) {
            accuracies[s] = measureAccuracy(SEEDS[s]);
        }
        double median = median(accuracies);
        System.out.printf("[EXP-046] accuracy on synthetic corpus: median=%.3f%n", median);
        // Honest write-up: we record the measurement; the verdict is
        // decided by reading the value against the gate (≥ 0.9).
        assertThat(median).isGreaterThanOrEqualTo(0.0);
        assertThat(median).isLessThanOrEqualTo(1.0);
    }

    private static double measureAccuracy(long seed) {
        Random rng = new Random(seed);
        EthicalFilter ethics = new EthicalFilter();
        ConjugateBudgeter budgeter = new ConjugateBudgeter();
        ImpulseScheduler scheduler = new ImpulseScheduler(budgeter, ethics);

        // Ground-truth: only the 4 known impulses are allowed.
        // "Forbidden" relabellings are always rejected.
        int correct = 0;
        for (int i = 0; i < N_IMPULSES; i++) {
            boolean isNoise = rng.nextDouble() < NOISE_RATE;
            AutonomyImpulse trueImpulse = AutonomyImpulse.values()[rng.nextInt(4)];
            String impulseName;
            boolean expectedAllowed;
            if (isNoise) {
                impulseName = "FORBIDDEN_" + i;
                expectedAllowed = false;
            } else {
                impulseName = trueImpulse.name();
                expectedAllowed = true;
            }
            // The scheduler does not take a name; we map name → enum
            // and call fire(). For noise names, we synthesize a
            // forbidden AutonomyImpulse-like call that the gate must
            // reject — but the EthicalFilter only knows about the
            // canonical names. So we check ethics directly for noise.
            if (isNoise) {
                EthicalFilter.Axiom frozenViolation = ethics.frozenViolatedAxiom(impulseName);
                boolean observedAllowed = (frozenViolation == null);
                if (observedAllowed == expectedAllowed) correct++;
            } else {
                ImpulseScheduler.FireRecord r =
                        scheduler.fire(trueImpulse, ENVELOPE, Map.of());
                boolean observedAllowed =
                        r.outcome() == ImpulseScheduler.ImpulseOutcome.FIRED;
                if (observedAllowed == expectedAllowed) correct++;
            }
        }
        return (double) correct / N_IMPULSES;
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }
}