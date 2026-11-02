package io.matrix.cognitive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.matrix.neuron.HdcEncoding;
import io.matrix.neuron.HebbianUpdater;
import io.matrix.neuron.HebbianUpdater.State;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W42.7 — the math of the error-driven decay, verified.
 *
 * <p>The mission's §9: "error-driven learning integration only after
 * verifying the math", and "do not indiscriminately weaken neural
 * associations after every unexpected event".
 *
 * <p>The first version of the decay had no floor: it subtracted a fixed
 * step from the targeted accumulators and stopped there, so the same
 * error, repeated, drove the accumulator arbitrarily far below zero.
 */
class ErrorDrivenLearnerMathTest {

    /** A state with one active bit at position 0 and one at position 10. */
    private static State stateWithActiveBits(int... positions) {
        State state = HebbianUpdater.empty();
        for (int position : positions) {
            state.weights[position >>> 6] |= 1L << (position & 63);
            state.accumulator[position] = 0.5f;
        }
        return state;
    }

    /** An observation with a 1 at the given positions. */
    private static float[] observationWith(int... positions) {
        float[] observation = new float[HdcEncoding.DIM];
        for (int position : positions) {
            observation[position] = 1.0f;
        }
        return observation;
    }

    private static CognitiveErrorStream streamOf(CognitiveError... errors) {
        CognitiveErrorStream stream = new CognitiveErrorStream(Math.max(1, errors.length));
        for (CognitiveError error : errors) {
            stream.record(error);
        }
        return stream;
    }

    @Test
    @DisplayName("the decay touches only the active bits of the observation")
    void theDecayTouchesOnlyTheActiveBitsOfTheObservation() {
        State state = stateWithActiveBits(0, 10);
        // The observation says "the binding at 0 and 5 is what fired".
        CognitiveErrorStream stream = streamOf(new CognitiveError(1L,
                CognitiveError.ErrorKind.PREDICTION_ERROR, observationWith(0, 5), "the error"));

        ErrorDrivenLearner.applyDecay(state, stream, 0.1f);

        // The bit at 0 was active AND in the observation: it was weakened.
        assertThat(state.accumulator[0]).isLessThan(0.5f);
        // The bit at 10 was active but NOT in the observation: the
        // "what should be" is preserved.
        assertThat(state.accumulator[10]).isEqualTo(0.5f);
        // The bit at 5 was in the observation but not active: nothing to
        // weaken, and nothing was touched.
        assertThat(state.accumulator[5]).isEqualTo(0.0f);
    }

    @Test
    @DisplayName("the accumulator never falls below the deadband's lower edge")
    void theAccumulatorNeverFallsBelowTheDeadbandsLowerEdge() {
        float threshold = HebbianUpdater.DEFAULT_THRESHOLD;
        State state = stateWithActiveBits(0);
        CognitiveErrorStream stream = streamOf(new CognitiveError(1L,
                CognitiveError.ErrorKind.PREDICTION_ERROR, observationWith(0), "the error"));

        // The same error, fifty times over.
        for (int i = 0; i < 50; i++) {
            ErrorDrivenLearner.applyDecay(state, stream, 0.5f, threshold);
        }

        assertThat(state.accumulator[0]).isGreaterThanOrEqualTo(-threshold);
    }

    @Test
    @DisplayName("a repeated error does not weaken the association without bound")
    void aRepeatedErrorDoesNotWeakenTheAssociationWithoutBound() {
        State state = stateWithActiveBits(0);
        CognitiveErrorStream stream = streamOf(new CognitiveError(1L,
                CognitiveError.ErrorKind.PREDICTION_ERROR, observationWith(0), "the error"));

        ErrorDrivenLearner.applyDecay(state, stream, 0.1f);
        float afterOne = state.accumulator[0];
        for (int i = 0; i < 100; i++) {
            ErrorDrivenLearner.applyDecay(state, stream, 0.1f);
        }
        float afterMany = state.accumulator[0];

        // The first error moved it, and the errors after that walk it down
        // to the floor and stop there. Without the floor the hundredth
        // error would have driven it a hundred times further.
        assertThat(afterOne).isLessThan(0.5f);
        assertThat(afterMany).isEqualTo(-HebbianUpdater.DEFAULT_THRESHOLD);
        assertThat(afterMany).isGreaterThanOrEqualTo(-HebbianUpdater.DEFAULT_THRESHOLD);
    }

    @Test
    @DisplayName("the decay is deterministic")
    void theDecayIsDeterministic() {
        State first = stateWithActiveBits(0, 10);
        State second = stateWithActiveBits(0, 10);
        CognitiveErrorStream stream = streamOf(
                new CognitiveError(1L, CognitiveError.ErrorKind.PREDICTION_ERROR,
                        observationWith(0, 5), "the first error"),
                new CognitiveError(2L, CognitiveError.ErrorKind.INTEGRATION_VIOLATION,
                        observationWith(10), "the second error"));

        ErrorDrivenLearner.applyDecay(first, stream, 0.1f);
        ErrorDrivenLearner.applyDecay(second, stream, 0.1f);

        assertThat(first.accumulator).isEqualTo(second.accumulator);
        assertThat(first.weights).isEqualTo(second.weights);
    }

    @Test
    @DisplayName("an empty stream changes nothing")
    void anEmptyStreamChangesNothing() {
        State state = stateWithActiveBits(0);
        float[] before = state.accumulator.clone();

        ErrorDrivenLearner.applyDecay(state, new CognitiveErrorStream(4), 0.1f);

        assertThat(state.accumulator).isEqualTo(before);
    }

    @Test
    @DisplayName("the parameters are checked")
    void theParametersAreChecked() {
        State state = stateWithActiveBits(0);
        CognitiveErrorStream stream = streamOf(new CognitiveError(1L,
                CognitiveError.ErrorKind.PREDICTION_ERROR, observationWith(0), "the error"));

        assertThatThrownBy(() -> ErrorDrivenLearner.applyDecay(state, stream, 0.0f))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eta");
        assertThatThrownBy(() -> ErrorDrivenLearner.applyDecay(state, stream, 1.5f))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eta");
        assertThatThrownBy(() -> ErrorDrivenLearner.applyDecay(state, stream, 0.1f, -1.0f))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threshold");
    }

    @Test
    @DisplayName("the error's direction is weakened past the threshold")
    void theErrorsDirectionIsWeakenedPastTheThreshold() {
        float threshold = HebbianUpdater.DEFAULT_THRESHOLD;
        State state = stateWithActiveBits(0);
        CognitiveError error = new CognitiveError(1L,
                CognitiveError.ErrorKind.PREDICTION_ERROR, observationWith(0), "the error");
        CognitiveErrorStream stream = streamOf(error);

        // The decay moves the ACCUMULATOR. The binding itself - the weight
        // bit - is cleared by the next thresholding pass, and the
        // thresholding is what reads the accumulator. The first version of
        // this test compared the binding strength before and after the
        // decay and found it unchanged, which was correct: the decay does
        // not rewrite the weights, and a rule that claimed it did would be
        // claiming something the engine does not do.
        ErrorDrivenLearner.applyDecay(state, stream, 0.5f, threshold);

        assertThat(state.accumulator[0]).isLessThan(threshold);
        // The raw step would have taken it to -0.5; the floor stopped it
        // at -threshold.
        assertThat(state.accumulator[0]).isGreaterThanOrEqualTo(-threshold);
        // The bit is still set in the weights - the thresholding has not
        // run yet - and that is stated here rather than glossed over.
        assertThat(state.weights[0] & 1L).isEqualTo(1L);
    }
}
