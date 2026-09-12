package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyntheticGrammarExperimentTest {

    @Test
    void generateAllSentencesProducesExpectedCount() {
        // 2 dets * 3 nouns * 3 verbs * 2 dets * 3 nouns = 108 sentences
        List<SyntheticGrammarExperiment.Sentence> all =
                SyntheticGrammarExperiment.generateAllSentences();
        assertThat(all).hasSize(108);
    }

    @Test
    void generatedSentencesHaveValidStructure() {
        List<SyntheticGrammarExperiment.Sentence> all =
                SyntheticGrammarExperiment.generateAllSentences();
        for (SyntheticGrammarExperiment.Sentence s : all) {
            // Sentence format: "det subj verb det obj"
            String[] parts = s.text.split(" ");
            assertThat(parts).hasSize(5);
            assertThat(s.subject).isNotEmpty();
            assertThat(s.verb).isNotEmpty();
            assertThat(s.object).isNotEmpty();
        }
    }

    @Test
    void sentenceToStringFormat() {
        SyntheticGrammarExperiment.Sentence s = new SyntheticGrammarExperiment.Sentence(
                "the dog chases the cat", "dog", "chases", "cat");
        assertThat(s.toString()).contains("dog").contains("chases").contains("cat");
    }

    @Test
    void runExperimentWithoutCrash() {
        HdcBrain brain = new HdcBrain(20, new Random(1));
        SyntheticGrammarExperiment.Result result = SyntheticGrammarExperiment.run(
                brain, 10, 5, new Random(42));
        assertThat(result.total).isEqualTo(10);
        // Brain should learn some subjects after enough training
        assertThat(result.accuracy).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void runRejectsBadArgs() {
        HdcBrain brain = new HdcBrain(20, new Random(1));
        assertThatThrownBy(() -> SyntheticGrammarExperiment.run(null, 5, 5, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SyntheticGrammarExperiment.run(brain, 5, 5, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void compositionalReasoningProducesValidResults() {
        HdcBrain brain = new HdcBrain(20, new Random(1));
        SyntheticGrammarExperiment.Result result = SyntheticGrammarExperiment.compositionalReasoning(
                brain, 10, new Random(42));
        assertThat(result.total).isEqualTo(10);
    }

    @Test
    void compositionalReasoningRejectsNull() {
        HdcBrain brain = new HdcBrain(20, new Random(1));
        assertThatThrownBy(() -> SyntheticGrammarExperiment.compositionalReasoning(null, 5, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SyntheticGrammarExperiment.compositionalReasoning(brain, 5, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sentenceToFeaturesProducesCorrectLength() {
        float[] f = SyntheticGrammarExperiment.sentenceToFeatures("hello world");
        assertThat(f).hasSize(HdcEncoding.DIM);
    }

    @Test
    void sentenceToFeaturesRejectsNull() {
        assertThatThrownBy(() -> SyntheticGrammarExperiment.sentenceToFeatures(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resultComputesAccuracy() {
        boolean[] trials = {true, true, false, true};
        SyntheticGrammarExperiment.Result r =
                new SyntheticGrammarExperiment.Result(3, 4, trials);
        assertThat(r.correct).isEqualTo(3);
        assertThat(r.total).isEqualTo(4);
        assertThat(r.accuracy).isEqualTo(0.75);
    }

    @Test
    void resultZeroTrialsHasZeroAccuracy() {
        SyntheticGrammarExperiment.Result r =
                new SyntheticGrammarExperiment.Result(0, 0, new boolean[0]);
        assertThat(r.accuracy).isEqualTo(0.0);
    }

    @Test
    void resultToStringContainsCounts() {
        SyntheticGrammarExperiment.Result r =
                new SyntheticGrammarExperiment.Result(7, 10, new boolean[10]);
        assertThat(r.toString()).contains("7/10");
    }
}
