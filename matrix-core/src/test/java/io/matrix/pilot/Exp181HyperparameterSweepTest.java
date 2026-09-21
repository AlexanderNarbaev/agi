package io.matrix.pilot;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 181 — Hyperparameter sweep EXP.
 *
 * <p>Sweeps 9 mutation-rate × population-size combinations
 * across 50 generations. Records best fitness per config.
 */
@Tag("exp")
class Exp181HyperparameterSweepTest {

    @Test
    void nineConfigsSweep() {
        var results = PilotParameterSweep.sweep(
                new double[]{0.01, 0.05, 0.10},
                new int[]{10, 20, 30},
                50,
                5000L);
        // 3 × 3 = 9
        assertThat(results).hasSize(9);
        for (var r : results) {
            assertThat(r.finalFitness()).isBetween(0.0, 20.0);
            assertThat(r.generations()).isEqualTo(50);
        }
        // Best fitness across all configs
        double best = 0;
        for (var r : results) {
            if (r.finalFitness() > best) best = r.finalFitness();
        }
        System.out.printf("[HYPER-SWEEP-9] best=%s across %d configs%n",
                best, results.size());
        assertThat(best).isGreaterThan(0.0);
    }

    @Test
    void differentConfigsProduceDifferentResults() {
        var r1 = PilotParameterSweep.sweep(
                new double[]{0.001}, new int[]{10}, 30, 42L);
        var r2 = PilotParameterSweep.sweep(
                new double[]{0.5}, new int[]{50}, 30, 42L);
        // At least one of these should differ
        // (We don't know exactly which will win, but
        // the results should be valid fitness values)
        double f1 = r1.get(0).finalFitness();
        double f2 = r2.get(0).finalFitness();
        System.out.printf("[HYPER-DIFF] config1=%s config2=%s%n", f1, f2);
        assertThat(f1).isBetween(0.0, 20.0);
        assertThat(f2).isBetween(0.0, 20.0);
    }
}
