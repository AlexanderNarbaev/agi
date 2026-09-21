package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SokolovHabituationExperimentTest {

    private static long[] r(int seed) {
        return HdcEncoding.random(new Random(seed));
    }

    @Test
    void runProducesResponseCurve() {
        HdcBrain brain = new HdcBrain(20, new Random(1));
        SokolovHabituationExperiment.Result result = SokolovHabituationExperiment.run(
                brain, r(1), "noise", 10);
        assertThat(result.responseCurve).hasSize(10);
    }

    @Test
    void runFitsDecayRate() {
        HdcBrain brain = new HdcBrain(20, new Random(1));
        SokolovHabituationExperiment.Result result = SokolovHabituationExperiment.run(
                brain, r(1), "noise", 30);
        // Decay rate should be some value (could be 0 if curve doesn't decay)
        assertThat(result.decayRate).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void runInitialResponseIsFirstTrial() {
        HdcBrain brain = new HdcBrain(20, new Random(1));
        SokolovHabituationExperiment.Result result = SokolovHabituationExperiment.run(
                brain, r(1), "noise", 10);
        assertThat(result.initialResponse).isEqualTo(result.responseCurve[0]);
        assertThat(result.finalResponse).isEqualTo(result.responseCurve[9]);
    }

    @Test
    void runRejectsBadArgs() {
        HdcBrain brain = new HdcBrain(20, new Random(1));
        long[] stim = r(1);
        assertThatThrownBy(() -> SokolovHabituationExperiment.run(null, stim, "x", 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SokolovHabituationExperiment.run(brain, null, "x", 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SokolovHabituationExperiment.run(brain, new long[2], "x", 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SokolovHabituationExperiment.run(brain, stim, "x", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void spontaneousRecoveryProducesThreePhases() {
        HdcBrain brain = new HdcBrain(20, new Random(1));
        SokolovHabituationExperiment.Result result = SokolovHabituationExperiment.spontaneousRecovery(
                brain, r(1), r(2), "tone", "light", 5);
        assertThat(result.responseCurve).hasSize(15);
    }

    @Test
    void spontaneousRecoveryRejectsBadArgs() {
        HdcBrain brain = new HdcBrain(20, new Random(1));
        long[] s1 = r(1);
        long[] s2 = r(2);
        assertThatThrownBy(() -> SokolovHabituationExperiment.spontaneousRecovery(
                null, s1, s2, "a", "b", 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SokolovHabituationExperiment.spontaneousRecovery(
                brain, null, s2, "a", "b", 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SokolovHabituationExperiment.spontaneousRecovery(
                brain, s1, null, "a", "b", 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SokolovHabituationExperiment.spontaneousRecovery(
                brain, new long[2], s2, "a", "b", 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resultToStringContainsMetrics() {
        float[] curve = {0.5f, 0.3f, 0.1f};
        SokolovHabituationExperiment.Result r = new SokolovHabituationExperiment.Result(
                curve, 0.5, 0.5, 0.1);
        assertThat(r.toString()).contains("decayRate").contains("R0").contains("Rfinal");
    }
}
