package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HebbianUpdaterTest {

    private static long[] r(int seed) {
        return HdcEncoding.random(new Random(seed));
    }

    @Test
    void emptyReturnsInitialState() {
        HebbianUpdater.State s = HebbianUpdater.empty();
        assertThat(s.weights).hasSize(HdcEncoding.WORDS);
        assertThat(s.accumulator).hasSize(HdcEncoding.DIM);
        for (long word : s.weights) {
            assertThat(word).isEqualTo(0L); // all -1 in bipolar convention
        }
        for (float v : s.accumulator) {
            assertThat(v).isEqualTo(0.0f);
        }
    }

    @Test
    void updateWithIdenticalPreAndPostConvergesToAllPositive() {
        HebbianUpdater.State state = HebbianUpdater.empty();
        long[] v = r(1);
        for (int i = 0; i < 100; i++) {
            HebbianUpdater.update(state, v, v, 0.5f, 0.01f);
        }
        // Hebbian rule: pre==post everywhere → all positions agree → all
        // accumulators go positive → all weights become +1.
        int positives = HebbianUpdater.positiveCount(state);
        assertThat(positives).isEqualTo(HdcEncoding.DIM);
    }

    @Test
    void updateWithComplementaryPreAndPostConvergesToAllNegative() {
        HebbianUpdater.State state = HebbianUpdater.empty();
        long[] v = r(1);
        long[] complement = new long[HdcEncoding.WORDS];
        for (int i = 0; i < HdcEncoding.WORDS; i++) {
            complement[i] = ~v[i];
        }
        for (int i = 0; i < 100; i++) {
            HebbianUpdater.update(state, v, complement, 0.5f, 0.01f);
        }
        // Anti-Hebbian at every position (v and complement always disagree) →
        // all accumulators go negative → all weights become -1.
        int positives = HebbianUpdater.positiveCount(state);
        assertThat(positives).isEqualTo(0);
    }

    @Test
    void updateMutatesStateInPlace() {
        HebbianUpdater.State state = HebbianUpdater.empty();
        long[] v = r(1);
        HebbianUpdater.State result = HebbianUpdater.update(state, v, v, 0.5f, 0.01f);
        assertThat(result).isSameAs(state);
        assertThat(HebbianUpdater.activeCount(state)).isGreaterThan(0);
    }

    @Test
    void updateWithHighDecayConvergesSlower() {
        HebbianUpdater.State lowDecay = HebbianUpdater.empty();
        HebbianUpdater.State highDecay = HebbianUpdater.empty();
        long[] v = r(1);
        // Use small eta so decay effect is observable
        for (int i = 0; i < 5; i++) {
            HebbianUpdater.update(lowDecay, v, v, 0.1f, 0.001f);
            HebbianUpdater.update(highDecay, v, v, 0.1f, 0.5f);
        }
        // High decay should produce fewer positives in early iterations
        int lowPos = HebbianUpdater.positiveCount(lowDecay);
        int highPos = HebbianUpdater.positiveCount(highDecay);
        assertThat(lowPos).isGreaterThan(highPos);
    }

    @Test
    void updateRejectsBadInputs() {
        HebbianUpdater.State state = HebbianUpdater.empty();
        long[] v = r(1);
        assertThatThrownBy(() -> HebbianUpdater.update(null, v, v, 0.5f, 0.01f))
                .isInstanceOf(IllegalArgumentException.class);
        HebbianUpdater.State badState = new HebbianUpdater.State(
                new long[2], new float[HdcEncoding.DIM]);
        assertThatThrownBy(() -> HebbianUpdater.update(badState, v, v, 0.5f, 0.01f))
                .isInstanceOf(IllegalArgumentException.class);
        HebbianUpdater.State badState2 = new HebbianUpdater.State(
                new long[HdcEncoding.WORDS], new float[10]);
        assertThatThrownBy(() -> HebbianUpdater.update(badState2, v, v, 0.5f, 0.01f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HebbianUpdater.update(state, null, v, 0.5f, 0.01f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HebbianUpdater.update(state, v, null, 0.5f, 0.01f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HebbianUpdater.update(state, v, v, 0.0f, 0.01f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HebbianUpdater.update(state, v, v, 1.5f, 0.01f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HebbianUpdater.update(state, v, v, 0.5f, -0.01f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HebbianUpdater.update(state, v, v, 0.5f, 1.5f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HebbianUpdater.update(state, v, v, 0.5f, 0.01f, -1.0f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void activeCountOnEmptyIsZero() {
        HebbianUpdater.State state = HebbianUpdater.empty();
        assertThat(HebbianUpdater.activeCount(state)).isEqualTo(0);
    }

    @Test
    void activeCountOnNullIsZero() {
        assertThat(HebbianUpdater.activeCount(null)).isEqualTo(0);
    }

    @Test
    void positiveCountOnEmptyIsZero() {
        HebbianUpdater.State state = HebbianUpdater.empty();
        assertThat(HebbianUpdater.positiveCount(state)).isEqualTo(0);
    }

    @Test
    void updateWithDefaultThreshold() {
        HebbianUpdater.State state = HebbianUpdater.empty();
        long[] v = r(1);
        for (int i = 0; i < 50; i++) {
            HebbianUpdater.update(state, v, v, 0.5f, 0.01f);
        }
        assertThat(HebbianUpdater.activeCount(state)).isGreaterThan(0);
    }

    @Test
    void updatePreservesStabilityAfterConvergence() {
        HebbianUpdater.State state = HebbianUpdater.empty();
        long[] v = r(1);
        for (int i = 0; i < 100; i++) {
            HebbianUpdater.update(state, v, v, 0.5f, 0.01f);
        }
        long[] snapshotBefore = state.weights.clone();
        HebbianUpdater.update(state, v, v, 0.5f, 0.01f);
        int dist = HdcEncoding.hamming(snapshotBefore, state.weights);
        assertThat(dist).isLessThan(HdcEncoding.DIM / 8);
    }

    @Test
    void updateWithMixedPatternsProducesUnion() {
        HebbianUpdater.State state = HebbianUpdater.empty();
        long[] a = r(1);
        long[] b = r(2);
        // Pattern 1: a ↔ a
        for (int i = 0; i < 50; i++) {
            HebbianUpdater.update(state, a, a, 0.5f, 0.01f);
        }
        // Pattern 2: b ↔ b
        for (int i = 0; i < 50; i++) {
            HebbianUpdater.update(state, b, b, 0.5f, 0.01f);
        }
        // Both a and b should have substantial positive weights
        int positives = HebbianUpdater.positiveCount(state);
        assertThat(positives).isGreaterThan(HdcEncoding.DIM / 4);
    }

    @Test
    void weightsGetterReturnsUnderlyingArray() {
        HebbianUpdater.State state = HebbianUpdater.empty();
        assertThat(HebbianUpdater.weights(state)).isSameAs(state.weights);
    }
}
