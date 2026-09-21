package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PragmaticTestTest {

    @Test
    void trialReturnsResult() {
        float[] obs = {1.0f, 2.0f};
        PragmaticTest.PragmaticResult r = PragmaticTest.trial(obs, "act_1", true);
        assertThat(r.action()).isEqualTo("act_1");
        assertThat(r.success()).isTrue();
        assertThat(r.percept()).containsExactly(1.0f, 2.0f);
    }

    @Test
    void trialRejectsNullInputs() {
        assertThatThrownBy(() -> PragmaticTest.trial(null, "act", true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PragmaticTest.trial(new float[]{1}, null, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isMeaningfulRequiresMeaningStore() {
        Map<String, Double> store = new HashMap<>();
        store.put("p_2.0|act_A", 0.8);
        assertThat(PragmaticTest.isMeaningful(store, new float[]{1, 1}, "act_A")).isTrue();
        assertThat(PragmaticTest.isMeaningful(store, new float[]{1, 1}, "act_B")).isFalse();
    }

    @Test
    void isMeaningfulWithNulls() {
        assertThat(PragmaticTest.isMeaningful(null, new float[]{1}, "act")).isFalse();
        assertThat(PragmaticTest.isMeaningful(new HashMap<>(), null, "act")).isFalse();
        assertThat(PragmaticTest.isMeaningful(new HashMap<>(), new float[]{1}, null)).isFalse();
    }

    @Test
    void mostMeaningfulActionPicksHighest() {
        Map<String, Double> store = new HashMap<>();
        store.put("p_2.0|act_A", 0.9);
        store.put("p_2.0|act_B", 0.3);
        store.put("p_2.0|act_C", 0.6);
        String best = PragmaticTest.mostMeaningfulAction(
                store, new float[]{1, 1}, Arrays.asList("act_A", "act_B", "act_C"));
        assertThat(best).isEqualTo("act_A");
    }

    @Test
    void mostMeaningfulActionWithNoEntries() {
        Map<String, Double> store = new HashMap<>();
        String best = PragmaticTest.mostMeaningfulAction(
                store, new float[]{1, 1}, Arrays.asList("act_A", "act_B"));
        assertThat(best).isNull();
    }

    @Test
    void mostMeaningfulActionRejectsNulls() {
        assertThat(PragmaticTest.mostMeaningfulAction(null, new float[]{1}, Arrays.asList("a")))
                .isNull();
        assertThat(PragmaticTest.mostMeaningfulAction(new HashMap<>(), null, Arrays.asList("a")))
                .isNull();
        assertThat(PragmaticTest.mostMeaningfulAction(new HashMap<>(), new float[]{1}, null))
                .isNull();
    }

    @Test
    void runTrialsAccumulatesMeaning() {
        Map<String, Double> store = new HashMap<>();
        List<PragmaticTest.Trial> trials = new ArrayList<>();
        // Success on act_A
        trials.add(new PragmaticTest.Trial(new float[]{1, 1}, "act_A", true));
        trials.add(new PragmaticTest.Trial(new float[]{1, 1}, "act_A", true));
        trials.add(new PragmaticTest.Trial(new float[]{1, 1}, "act_A", true));
        // Failure on act_B
        trials.add(new PragmaticTest.Trial(new float[]{1, 1}, "act_B", false));

        PragmaticTest.MeaningSequence r = PragmaticTest.runTrials(trials, store);
        assertThat(r.total()).isEqualTo(4);
        assertThat(r.successes()).isEqualTo(3);
        assertThat(r.failures()).isEqualTo(1);
        assertThat(r.successRate()).isEqualTo(0.75);
        // act_A should be meaningful (>0.5)
        assertThat(PragmaticTest.isMeaningful(store, new float[]{1, 1}, "act_A")).isTrue();
    }

    @Test
    void runTrialsRejectsNulls() {
        assertThatThrownBy(() -> PragmaticTest.runTrials(null, new HashMap<>()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PragmaticTest.runTrials(new ArrayList<>(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void trialRecordHasAllFields() {
        PragmaticTest.Trial t = new PragmaticTest.Trial(new float[]{1, 2}, "act", true);
        assertThat(t.percept()).containsExactly(1.0f, 2.0f);
        assertThat(t.action()).isEqualTo("act");
        assertThat(t.success()).isTrue();
    }

    @Test
    void meaningSequenceTotalAndRate() {
        PragmaticTest.MeaningSequence r = new PragmaticTest.MeaningSequence(
                Arrays.asList(
                        new PragmaticTest.Trial(new float[]{1}, "a", true),
                        new PragmaticTest.Trial(new float[]{1}, "b", false)),
                1, 1);
        assertThat(r.total()).isEqualTo(2);
        assertThat(r.successRate()).isEqualTo(0.5);
    }

    @Test
    void meaningSequenceEmptyRate() {
        PragmaticTest.MeaningSequence r = new PragmaticTest.MeaningSequence(
                new ArrayList<>(), 0, 0);
        assertThat(r.total()).isEqualTo(0);
        assertThat(r.successRate()).isEqualTo(0.0);
    }
}
