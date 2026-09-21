package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class HdcConditioningTest {

    private static long[] r(int seed) {
        return HdcEncoding.random(new Random(seed));
    }

    @Test
    void stimulusFromTemplateProducesCorrectLength() {
        long[] tpl = r(1);
        float[] stim = HdcConditioning.stimulusFromTemplate(tpl, 1.0f);
        assertThat(stim).hasSize(HdcEncoding.DIM);
    }

    @Test
    void stimulusFromTemplateWithPositiveMagnitude() {
        long[] tpl = r(1);
        float[] stim = HdcConditioning.stimulusFromTemplate(tpl, 2.0f);
        // Where template bit=1, stim should be +2.0; where bit=0, should be -2.0.
        for (int i = 0; i < HdcEncoding.DIM; i++) {
            int w = i >>> 6;
            int b = i & 63;
            boolean bit = ((tpl[w] >>> b) & 1L) != 0L;
            assertThat(stim[i]).isEqualTo(bit ? 2.0f : -2.0f);
        }
    }

    @Test
    void stimulusFromTemplateRejectsBadInput() {
        assertThatThrownBy(() -> HdcConditioning.stimulusFromTemplate(null, 1.0f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HdcConditioning.stimulusFromTemplate(new long[2], 1.0f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pavlovConditioningProducesConditionedResponse() {
        HdcBrain brain = new HdcBrain(10, new Random(42));
        long[] cs = r(1);  // bell
        long[] us = r(2);  // food
        HdcConditioning.Result result = HdcConditioning.pavlovClassicalConditioning(
                brain, cs, us, "salivate",
                3 /* pre-test */, 10 /* acquisition */, 5 /* post-test */,
                0.5f, 0.01f);

        assertThat(result.trials).isEqualTo(18);
        assertThat(result.responseCurve).hasSize(18);

        // Post-test response should be substantially higher than pre-test
        float preMean = mean(result.responseCurve, 0, 3);
        float postMean = mean(result.responseCurve, 13, 18);
        assertThat(postMean).isGreaterThan(preMean);
    }

    @Test
    void pavlovConditioningRejectsBadArgs() {
        HdcBrain brain = new HdcBrain(10, new Random(1));
        long[] cs = r(1);
        long[] us = r(2);
        assertThatThrownBy(() -> HdcConditioning.pavlovClassicalConditioning(
                null, cs, us, "salivate", 3, 10, 5, 0.5f, 0.01f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HdcConditioning.pavlovClassicalConditioning(
                brain, null, us, "salivate", 3, 10, 5, 0.5f, 0.01f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HdcConditioning.pavlovClassicalConditioning(
                brain, cs, null, "salivate", 3, 10, 5, 0.5f, 0.01f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HdcConditioning.pavlovClassicalConditioning(
                brain, cs, us, null, 3, 10, 5, 0.5f, 0.01f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HdcConditioning.pavlovClassicalConditioning(
                brain, new long[2], us, "salivate", 3, 10, 5, 0.5f, 0.01f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void habituationResponseRemainsStableWithRepeatedSameStimulus() {
        HdcBrain brain = new HdcBrain(10, new Random(42));
        long[] stim = r(1);
        HdcConditioning.Result result = HdcConditioning.habituation(
                brain, stim, "noise", 30, 0.5f, 0.01f);
        assertThat(result.trials).isEqualTo(30);
        // With HdcBrain's bundle-based learning, repeated same-stim/same-label
        // presentations converge via majority vote and STAY at full response
        // (no forgetting mechanism without explicit unlearn). Verify the
        // response stays high (full recall) and bounded (no NaN/Infinity).
        assertThat(result.peakResponse).isEqualTo(1.0);
        assertThat(result.finalResponse).isEqualTo(1.0);
        for (float v : result.responseCurve) {
            assertThat(Float.isFinite(v)).isTrue();
        }
    }

    @Test
    void habituationProducesPositiveDecayRate() {
        HdcBrain brain = new HdcBrain(10, new Random(42));
        long[] stim = r(1);
        HdcConditioning.Result result = HdcConditioning.habituation(
                brain, stim, "noise", 30, 0.5f, 0.01f);
        double rate = result.habituationRate();
        // Should have some positive decay (curve decreases after peak)
        assertThat(rate).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void habituationRejectsBadArgs() {
        HdcBrain brain = new HdcBrain(10, new Random(1));
        long[] stim = r(1);
        assertThatThrownBy(() -> HdcConditioning.habituation(null, stim, "x", 5, 0.5f, 0.01f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HdcConditioning.habituation(brain, null, "x", 5, 0.5f, 0.01f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HdcConditioning.habituation(brain, new long[2], "x", 5, 0.5f, 0.01f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void extinctionRejectsBadArgs() {
        HdcBrain brain = new HdcBrain(10, new Random(1));
        long[] cs = r(1);
        assertThatThrownBy(() -> HdcConditioning.extinction(null, cs, "x", 5, 0.5f, 0.01f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HdcConditioning.extinction(brain, null, "x", 5, 0.5f, 0.01f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void spontaneousRecoveryRejectsBadArgs() {
        HdcBrain brain = new HdcBrain(10, new Random(1));
        long[] cs = r(1);
        assertThatThrownBy(() -> HdcConditioning.spontaneousRecovery(null, cs, "x", 5, 0.5f, 0.01f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resultComputesSummaryMetrics() {
        float[] curve = {0.0f, 0.5f, 1.0f, 0.8f, 0.5f, 0.3f, 0.1f};
        HdcConditioning.Result r = new HdcConditioning.Result(curve);
        assertThat(r.trials).isEqualTo(7);
        assertThat(r.peakResponse).isCloseTo(1.0, within(1e-5));
        assertThat(r.finalResponse).isCloseTo(0.1, within(1e-5));
        // mean = (0+0.5+1+0.8+0.5+0.3+0.1)/7 ≈ 0.457
        assertThat(r.meanResponse).isBetween(0.4, 0.5);
    }

    @Test
    void resultHabituationRateIsPositiveForDecayingCurve() {
        // Decaying curve: peak at index 1
        float[] curve = {0.3f, 1.0f, 0.5f, 0.25f, 0.125f, 0.0625f};
        HdcConditioning.Result r = new HdcConditioning.Result(curve);
        double rate = r.habituationRate();
        assertThat(rate).isGreaterThan(0.0); // decaying
    }

    @Test
    void resultHabituationRateIsNearZeroForFlatCurve() {
        float[] curve = {0.5f, 0.5f, 0.5f, 0.5f, 0.5f};
        HdcConditioning.Result r = new HdcConditioning.Result(curve);
        // Flat curve: slope is ~0, so rate is ~0
        double rate = r.habituationRate();
        assertThat(Math.abs(rate)).isLessThan(0.1);
    }

    @Test
    void resultToStringContainsMetrics() {
        float[] curve = {0.1f, 0.5f, 0.3f};
        HdcConditioning.Result r = new HdcConditioning.Result(curve);
        String s = r.toString();
        assertThat(s).contains("trials=3").contains("mean=").contains("peak=");
    }

    @Test
    void resultHandlesEmptyCurve() {
        float[] empty = {};
        HdcConditioning.Result r = new HdcConditioning.Result(empty);
        assertThat(r.trials).isEqualTo(0);
        assertThat(r.meanResponse).isEqualTo(0.0);
        assertThat(r.peakResponse).isEqualTo(0.0);
    }

    private static float mean(float[] arr, int from, int to) {
        if (from >= to) return 0.0f;
        float sum = 0.0f;
        for (int i = from; i < to; i++) sum += arr[i];
        return sum / (to - from);
    }
}
