package io.matrix.neuron;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W42.8 — the L7 cycle's prediction comes from the brain.
 *
 * <p>The prediction error used to be the observation against itself, so
 * the surprise was zero in every cycle and the error-driven part of the
 * cycle was dead by construction.
 */
class ConsciousBrainPredictionTest {

    @Test
    @DisplayName("the prediction error is not always zero")
    void thePredictionErrorIsNotAlwaysZero() {
        ConsciousBrain brain = new ConsciousBrain(1024, 7L);
        Random random = new Random(42);

        double first = brain.cycle(randomVector(random)).predictionError();
        double second = brain.cycle(randomVector(random)).predictionError();

        // The first cycle has nothing to recall: the surprise is the
        // largest the scale allows.
        assertThat(first).isGreaterThan(0.0);
        // And it is not the same number every time, which is what a
        // prediction error computed against itself would give.
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    @DisplayName("a familiar observation surprises the brain less than a strange one")
    void aFamiliarObservationSurprisesTheBrainLessThanAStrangeOne() {
        ConsciousBrain brain = new ConsciousBrain(1024, 11L);
        Random random = new Random(7);

        float[] familiar = randomVector(random);
        brain.cycle(familiar);

        // The same observation again: the brain recalls it, so the error
        // is the distance to itself, which is zero.
        double repeated = brain.cycle(familiar.clone()).predictionError();

        // Something far away: the brain has no prediction for it.
        float[] strange = new float[1024];
        for (int index = 0; index < strange.length; index++) {
            strange[index] = random.nextFloat() * 10.0f;
        }
        double unfamiliar = brain.cycle(strange).predictionError();

        assertThat(repeated).isLessThan(unfamiliar);
    }

    private float[] randomVector(Random random) {
        float[] vector = new float[1024];
        for (int index = 0; index < vector.length; index++) {
            vector[index] = random.nextFloat();
        }
        return vector;
    }
}
