package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FreeEnergyEstimatorTest {

    @Test
    void uniform_prior_gives_high_free_energy() {
        FreeEnergyEstimator fep = new FreeEnergyEstimator(32);
        // No observation yet; prior is uniform, posterior is uniform
        fep.setObservation(0);
        double f = fep.freeEnergy();
        // Free energy should be positive (accuracy > 0 because likelihood is peaked)
        assertThat(f).isGreaterThan(0);
    }

    @Test
    void free_energy_drops_after_observation_match() {
        FreeEnergyEstimator fep = new FreeEnergyEstimator(32);
        fep.setObservation(16);  // first obs: peak at 16
        double f1 = fep.freeEnergy();
        // Same observation again: posterior concentrates, complexity drops
        fep.setObservation(16);
        double f2 = fep.freeEnergy();
        // The accuracy may be similar; complexity should drop sharply
        assertThat(f2).isLessThan(f1);
    }

    @Test
    void epistemic_value_is_max_at_uniform_posterior() {
        FreeEnergyEstimator fep = new FreeEnergyEstimator(32);
        // Set observation far from any prior mass to force near-uniform posterior
        fep.setObservation(0);
        double h = fep.epistemicValue();
        // Max entropy = log(32) ≈ 3.47
        assertThat(h).isLessThanOrEqualTo(Math.log(32) + 0.01);
        assertThat(h).isGreaterThan(0);
    }

    @Test
    void inferred_state_concentrates_after_repeated_observation() {
        FreeEnergyEstimator fep = new FreeEnergyEstimator(32);
        for (int i = 0; i < 10; i++) fep.setObservation(10);
        int s = fep.inferredState();
        // After many obs, posterior should peak near 10
        assertThat(Math.abs(s - 10)).isLessThan(3);
    }

    @Test
    void free_energy_decreases_with_more_observations() {
        FreeEnergyEstimator fep = new FreeEnergyEstimator(8);
        fep.setObservation(0);
        double f0 = fep.freeEnergy();
        for (int i = 0; i < 5; i++) fep.setObservation(0);
        double f5 = fep.freeEnergy();
        assertThat(f5).isLessThan(f0);
    }

    @Test
    void constructor_rejects_invalid_size() {
        assertThat(catchThrowable(() -> new FreeEnergyEstimator(0)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(catchThrowable(() -> new FreeEnergyEstimator(1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static Throwable catchThrowable(Runnable r) {
        try { r.run(); return null; } catch (Throwable t) { return t; }
    }
}
