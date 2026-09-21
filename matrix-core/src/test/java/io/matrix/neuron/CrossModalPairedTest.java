package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class CrossModalPairedTest {

    private static long[] r(int seed) {
        return HdcEncoding.random(new Random(seed));
    }

    @Test
    void constructorRejectsBadArgs() {
        assertThatThrownBy(() -> new CrossModalPaired(0, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CrossModalPaired(10, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyStoreReturnsNulls() {
        CrossModalPaired cmp = new CrossModalPaired(10, new Random(1));
        assertThat(cmp.size()).isEqualTo(0);
        assertThat(cmp.retrieveVisual(r(1))).isNull();
        assertThat(cmp.retrieveAudio(r(1))).isNull();
        assertThat(cmp.nearestAudioLabel(r(1))).isNull();
        assertThat(cmp.pairSimilarity(r(1), r(2))).isEqualTo(0.0);
    }

    @Test
    void pairStoresMemory() {
        CrossModalPaired cmp = new CrossModalPaired(10, new Random(1));
        cmp.pair(r(1), r(2), "ball");
        assertThat(cmp.size()).isEqualTo(1);
        assertThat(cmp.labels()).contains("ball");
    }

    @Test
    void pairReplacesExistingLabel() {
        CrossModalPaired cmp = new CrossModalPaired(10, new Random(1));
        cmp.pair(r(1), r(2), "ball");
        cmp.pair(r(3), r(4), "ball"); // same label, replace
        assertThat(cmp.size()).isEqualTo(1);
    }

    @Test
    void pairRejectsBadArgs() {
        CrossModalPaired cmp = new CrossModalPaired(10, new Random(1));
        assertThatThrownBy(() -> cmp.pair(null, r(1), "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cmp.pair(new long[2], r(1), "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cmp.pair(r(1), null, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cmp.pair(r(1), r(2), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retrieveVisualReturnsCorrectCode() {
        CrossModalPaired cmp = new CrossModalPaired(10, new Random(1));
        long[] audio = r(1);
        long[] visual = r(2);
        cmp.pair(audio, visual, "ball");
        long[] recalled = cmp.retrieveVisual(audio);
        assertThat(recalled).isNotNull();
        // The recalled visual should match the original visual exactly
        assertThat(HdcEncoding.hamming(recalled, visual)).isEqualTo(0);
    }

    @Test
    void retrieveAudioReturnsCorrectCode() {
        CrossModalPaired cmp = new CrossModalPaired(10, new Random(1));
        long[] audio = r(1);
        long[] visual = r(2);
        cmp.pair(audio, visual, "ball");
        long[] recalled = cmp.retrieveAudio(visual);
        assertThat(recalled).isNotNull();
        assertThat(HdcEncoding.hamming(recalled, audio)).isEqualTo(0);
    }

    @Test
    void crossModalInferenceNoisyQueryStillWorks() {
        CrossModalPaired cmp = new CrossModalPaired(10, new Random(1));
        long[] audio = r(1);
        long[] visual = r(2);
        cmp.pair(audio, visual, "ball");
        // Noisy audio query
        long[] noisyAudio = audio.clone();
        for (int i = 0; i < 10; i++) {
            noisyAudio[i >>> 6] ^= (1L << (i & 63));
        }
        long[] recalled = cmp.retrieveVisual(noisyAudio);
        // Even with noise, recalled visual should be close (not exact)
        assertThat(recalled).isNotNull();
        // Hamming distance should be small but not zero
        int d = HdcEncoding.hamming(recalled, visual);
        assertThat(d).isLessThan(100);
    }

    @Test
    void pairSimilarityIsPositiveForMatchingPair() {
        CrossModalPaired cmp = new CrossModalPaired(10, new Random(1));
        long[] audio = r(1);
        long[] visual = r(2);
        cmp.pair(audio, visual, "ball");
        double sim = cmp.pairSimilarity(audio, visual);
        // bind is XOR; same bind → similarity = 1.0 (identical bit patterns)
        assertThat(sim).isCloseTo(1.0, within(0.05));
    }

    @Test
    void pairSimilarityIsNegativeForUnrelatedPair() {
        CrossModalPaired cmp = new CrossModalPaired(10, new Random(1));
        cmp.pair(r(1), r(2), "ball");
        // Unrelated pair: similarity should be ~0 or negative
        double sim = cmp.pairSimilarity(r(99), r(100));
        assertThat(sim).isLessThan(0.5);
    }

    @Test
    void nearestAudioLabelFindsCorrectLabel() {
        CrossModalPaired cmp = new CrossModalPaired(10, new Random(1));
        cmp.pair(r(1), r(2), "ball");
        cmp.pair(r(3), r(4), "dog");
        cmp.pair(r(5), r(6), "cat");
        // Query with audio of "ball"
        assertThat(cmp.nearestAudioLabel(r(1))).isEqualTo("ball");
        assertThat(cmp.nearestAudioLabel(r(5))).isEqualTo("cat");
    }

    @Test
    void removeRemovesPair() {
        CrossModalPaired cmp = new CrossModalPaired(10, new Random(1));
        cmp.pair(r(1), r(2), "ball");
        assertThat(cmp.remove("ball")).isTrue();
        assertThat(cmp.size()).isEqualTo(0);
        assertThat(cmp.remove("ball")).isFalse();
    }

    @Test
    void getReturnsStoredMemory() {
        CrossModalPaired cmp = new CrossModalPaired(10, new Random(1));
        long[] a = r(1);
        long[] v = r(2);
        cmp.pair(a, v, "ball");
        CrossModalPaired.PairMemory m = cmp.get("ball");
        assertThat(m).isNotNull();
        assertThat(m.label).isEqualTo("ball");
        assertThat(HdcEncoding.hamming(m.audioCode, a)).isEqualTo(0);
        assertThat(HdcEncoding.hamming(m.visualCode, v)).isEqualTo(0);
    }

    @Test
    void retrieveRejectsBadTemplates() {
        CrossModalPaired cmp = new CrossModalPaired(10, new Random(1));
        cmp.pair(r(1), r(2), "x");
        assertThatThrownBy(() -> cmp.retrieveVisual(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cmp.retrieveVisual(new long[2]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cmp.retrieveAudio(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
