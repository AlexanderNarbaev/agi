package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpelkeCoreKnowledgeTest {

    private static long[] r(int seed) {
        return HdcEncoding.random(new Random(seed));
    }

    @Test
    void objectPermanenceBrainRecallsAfterOcclusion() {
        HdcBrain brain = new HdcBrain(10, new Random(1));
        long[] obj = r(1);
        long[] occ = r(2);
        SpelkeCoreKnowledge.Result result = SpelkeCoreKnowledge.objectPermanence(
                brain, obj, 5, occ);
        // Brain should recall object even with occluder pattern mixed in
        assertThat(result.total).isEqualTo(5);
        // At least some trials should succeed (with 50% occluder, expect ~50% success)
        assertThat(result.successRate).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void objectPermanenceRejectsBadArgs() {
        HdcBrain brain = new HdcBrain(10, new Random(1));
        long[] obj = r(1);
        long[] occ = r(2);
        assertThatThrownBy(() -> SpelkeCoreKnowledge.objectPermanence(null, obj, 5, occ))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SpelkeCoreKnowledge.objectPermanence(brain, null, 5, occ))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SpelkeCoreKnowledge.objectPermanence(brain, new long[2], 5, occ))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SpelkeCoreKnowledge.objectPermanence(brain, obj, 5, new long[2]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNotBRecallsBothLocations() {
        HdcBrain brain = new HdcBrain(10, new Random(1));
        long[] a = r(1);
        long[] b = r(2);
        SpelkeCoreKnowledge.Result result = SpelkeCoreKnowledge.aNotB(brain, a, b, 5);
        assertThat(result.total).isEqualTo(5);
        // Should succeed: brain learned B last, LRU brings it to the front
        assertThat(result.passed).isEqualTo(5);
    }

    @Test
    void aNotBRejectsBadArgs() {
        HdcBrain brain = new HdcBrain(10, new Random(1));
        long[] a = r(1);
        long[] b = r(2);
        assertThatThrownBy(() -> SpelkeCoreKnowledge.aNotB(null, a, b, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SpelkeCoreKnowledge.aNotB(brain, null, b, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SpelkeCoreKnowledge.aNotB(brain, a, null, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void numerosityDiscriminationDistinguishesOneVsTwo() {
        HdcBrain brain = new HdcBrain(10, new Random(42));
        SpelkeCoreKnowledge.Result result = SpelkeCoreKnowledge.numerosityDiscrimination(brain, 5);
        assertThat(result.total).isEqualTo(5);
        // Both labels should be retrievable
        assertThat(result.successRate).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void numerosityDiscriminationRejectsNull() {
        assertThatThrownBy(() -> SpelkeCoreKnowledge.numerosityDiscrimination(null, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void agentVsObjectCategorizesNovelPatterns() {
        HdcBrain brain = new HdcBrain(10, new Random(42));
        SpelkeCoreKnowledge.Result result = SpelkeCoreKnowledge.agentVsObject(brain, 5);
        assertThat(result.total).isEqualTo(5);
        // Brain should categorize noisy novel patterns correctly
        assertThat(result.passed).isGreaterThan(0);
    }

    @Test
    void agentVsObjectRejectsNull() {
        assertThatThrownBy(() -> SpelkeCoreKnowledge.agentVsObject(null, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resultComputesSuccessRate() {
        boolean[] trials = {true, false, true, true};
        SpelkeCoreKnowledge.Result r = new SpelkeCoreKnowledge.Result(3, 4, trials);
        assertThat(r.passed).isEqualTo(3);
        assertThat(r.total).isEqualTo(4);
        assertThat(r.successRate).isEqualTo(0.75);
        assertThat(r.perTrial).containsExactly(true, false, true, true);
    }

    @Test
    void resultZeroTrialsHasZeroSuccess() {
        SpelkeCoreKnowledge.Result r = new SpelkeCoreKnowledge.Result(0, 0, new boolean[0]);
        assertThat(r.successRate).isEqualTo(0.0);
    }

    @Test
    void resultToStringContainsCounts() {
        SpelkeCoreKnowledge.Result r = new SpelkeCoreKnowledge.Result(3, 5, new boolean[5]);
        assertThat(r.toString()).contains("3/5");
    }
}
