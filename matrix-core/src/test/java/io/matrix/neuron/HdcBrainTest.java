package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class HdcBrainTest {

    private static float[] makeFeatures(long[] bipolarTemplate, float value) {
        float[] f = new float[HdcEncoding.DIM];
        for (int w = 0; w < HdcEncoding.WORDS; w++) {
            for (int b = 0; b < 64; b++) {
                int pos = (w << 6) + b;
                boolean bit = ((bipolarTemplate[w] >>> b) & 1L) != 0L;
                f[pos] = bit ? value : -value;
            }
        }
        return f;
    }

    private static long[] r(int seed) {
        return HdcEncoding.random(new Random(seed));
    }

    @Test
    void constructorRejectsBadArgs() {
        assertThatThrownBy(() -> new HdcBrain(0, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HdcBrain(10, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyBrainReturnsNullOnForward() {
        HdcBrain brain = new HdcBrain(10, new Random(1));
        assertThat(brain.size()).isEqualTo(0);
        assertThat(brain.forward(new float[HdcEncoding.DIM])).isNull();
    }

    @Test
    void encodeFeaturesProducesBipolarVector() {
        HdcBrain brain = new HdcBrain(10, new Random(1));
        float[] features = new float[HdcEncoding.DIM];
        for (int i = 0; i < features.length; i++) features[i] = (i % 2 == 0) ? 1.0f : -1.0f;
        long[] code = brain.encodeFeatures(features);
        // Even positions should be 1, odd should be 0
        for (int i = 0; i < HdcEncoding.DIM; i++) {
            int w = i >>> 6;
            int b = i & 63;
            boolean bit = ((code[w] >>> b) & 1L) != 0L;
            assertThat(bit).isEqualTo(i % 2 == 0);
        }
    }

    @Test
    void encodeFeaturesRejectsBadInput() {
        HdcBrain brain = new HdcBrain(10, new Random(1));
        assertThatThrownBy(() -> brain.encodeFeatures(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> brain.encodeFeatures(new float[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> brain.encodeFeatures(new float[100]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void codeForLabelIsDeterministic() {
        HdcBrain brain = new HdcBrain(10, new Random(42));
        long[] a = brain.codeForLabel("dog");
        long[] b = brain.codeForLabel("dog");
        assertThat(HdcEncoding.hamming(a, b)).isEqualTo(0);
    }

    @Test
    void codeForLabelYieldsDifferentCodesForDifferentLabels() {
        HdcBrain brain = new HdcBrain(10, new Random(42));
        long[] a = brain.codeForLabel("dog");
        long[] b = brain.codeForLabel("cat");
        int d = HdcEncoding.hamming(a, b);
        assertThat(d).isBetween(380, 644);
    }

    @Test
    void codeForLabelRejectsNull() {
        HdcBrain brain = new HdcBrain(10, new Random(1));
        assertThatThrownBy(() -> brain.codeForLabel(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void learnStoresMemory() {
        HdcBrain brain = new HdcBrain(10, new Random(1));
        float[] features = makeFeatures(r(1), 1.0f);
        brain.learn(features, "dog");
        assertThat(brain.size()).isEqualTo(1);
        assertThat(brain.labels()).contains("dog");
    }

    @Test
    void learnStrengthensExisting() {
        HdcBrain brain = new HdcBrain(10, new Random(1));
        float[] features = makeFeatures(r(1), 1.0f);
        brain.learn(features, "dog");
        brain.learn(features, "dog");
        // Still size 1 (same label)
        assertThat(brain.size()).isEqualTo(1);
    }

    @Test
    void learnReturnsSimilarityOneForFirstLearn() {
        HdcBrain brain = new HdcBrain(10, new Random(1));
        float[] features = makeFeatures(r(1), 1.0f);
        double sim = brain.learn(features, "dog");
        assertThat(sim).isEqualTo(1.0);
    }

    @Test
    void forwardRecallsLearnedLabel() {
        HdcBrain brain = new HdcBrain(10, new Random(1));
        float[] features = makeFeatures(r(1), 1.0f);
        brain.learn(features, "dog");
        // Query with the same features
        HdcBrain.Recall hit = brain.forward(features);
        assertThat(hit).isNotNull();
        assertThat(hit.label).isEqualTo("dog");
        assertThat(hit.distance).isLessThan(50); // should be near-exact
    }

    @Test
    void forwardRecallsBestAmongMultipleLabels() {
        HdcBrain brain = new HdcBrain(10, new Random(1));
        float[] dogFeat = makeFeatures(r(1), 1.0f);
        float[] catFeat = makeFeatures(r(2), 1.0f);
        brain.learn(dogFeat, "dog");
        brain.learn(catFeat, "cat");
        // Query with dog features
        HdcBrain.Recall hit = brain.forward(dogFeat);
        assertThat(hit.label).isEqualTo("dog");
    }

    @Test
    void forwardRejectsUnseenQuery() {
        HdcBrain brain = new HdcBrain(10, new Random(1));
        brain.learn(makeFeatures(r(1), 1.0f), "dog");
        // Query with a different vector
        HdcBrain.Recall hit = brain.forward(makeFeatures(r(99), 1.0f));
        // Should still return some label, but with lower similarity
        assertThat(hit).isNotNull();
        assertThat(hit.similarity).isLessThan(0.5);
    }

    @Test
    void forwardTopKFindsAllCandidates() {
        HdcBrain brain = new HdcBrain(10, new Random(1));
        brain.learn(makeFeatures(r(1), 1.0f), "a");
        brain.learn(makeFeatures(r(2), 1.0f), "b");
        brain.learn(makeFeatures(r(3), 1.0f), "c");
        List<HdcBrain.Recall> top3 = brain.forwardTopK(makeFeatures(r(1), 1.0f), 3);
        assertThat(top3).hasSize(3);
        assertThat(top3.get(0).label).isEqualTo("a");
    }

    @Test
    void forwardTopKWithEmptyBrainReturnsEmpty() {
        HdcBrain brain = new HdcBrain(10, new Random(1));
        assertThat(brain.forwardTopK(new float[HdcEncoding.DIM], 5)).isEmpty();
    }

    @Test
    void forgetRemovesMemory() {
        HdcBrain brain = new HdcBrain(10, new Random(1));
        brain.learn(makeFeatures(r(1), 1.0f), "dog");
        assertThat(brain.forget("dog")).isTrue();
        assertThat(brain.size()).isEqualTo(0);
        assertThat(brain.forget("dog")).isFalse();
    }

    @Test
    void lruEvictionWhenOverCapacity() {
        HdcBrain brain = new HdcBrain(3, new Random(1));
        brain.learn(makeFeatures(r(1), 1.0f), "a");
        brain.learn(makeFeatures(r(2), 1.0f), "b");
        brain.learn(makeFeatures(r(3), 1.0f), "c");
        // Add 4th → evicts LRU (a)
        brain.learn(makeFeatures(r(4), 1.0f), "d");
        assertThat(brain.size()).isEqualTo(3);
        assertThat(brain.labels()).containsExactly("b", "c", "d");
    }

    @Test
    void getReturnsStoredMemory() {
        HdcBrain brain = new HdcBrain(10, new Random(1));
        brain.learn(makeFeatures(r(1), 1.0f), "dog");
        HdcBrain.Memory m = brain.get("dog");
        assertThat(m).isNotNull();
        assertThat(m.label).isEqualTo("dog");
        assertThat(m.code).hasSize(HdcEncoding.WORDS);
    }

    @Test
    void recallToStringContainsLabel() {
        HdcBrain brain = new HdcBrain(10, new Random(1));
        brain.learn(makeFeatures(r(1), 1.0f), "dog");
        HdcBrain.Recall hit = brain.forward(makeFeatures(r(1), 1.0f));
        assertThat(hit.toString()).contains("dog");
    }

    @Test
    void learnRejectsNullLabel() {
        HdcBrain brain = new HdcBrain(10, new Random(1));
        assertThatThrownBy(() -> brain.learn(new float[HdcEncoding.DIM], null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bindRecordIsApproximatelyRecoverable() {
        HdcBrain brain = new HdcBrain(10, new Random(1));
        long[] feat = r(1);
        long[] lab = r(2);
        long[] record = brain.bindRecord(feat, lab);
        // Unbind with feature should give label
        long[] unbound = HdcBinding.unbind(record, HdcBinding.sequence(feat, 0));
        int d = HdcEncoding.hamming(unbound, HdcBinding.sequence(lab, 1));
        assertThat(d).isLessThan(50);
    }

    @Test
    void capacityGetterReturnsMax() {
        HdcBrain brain = new HdcBrain(42, new Random(1));
        assertThat(brain.capacity()).isEqualTo(42);
    }
}
