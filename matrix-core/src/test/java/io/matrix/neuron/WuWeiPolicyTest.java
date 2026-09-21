package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WuWeiPolicyTest {

    @Test
    void lowFreeEnergyMeansNoOp() {
        WuWeiPolicy.Decision d = WuWeiPolicy.decide(0.1, 0.5, 3);
        assertThat(d.shouldAct()).isFalse();
        assertThat(d.isNoOp()).isTrue();
    }

    @Test
    void highFreeEnergyMeansAct() {
        WuWeiPolicy.Decision d = WuWeiPolicy.decide(1.0, 0.5, 3);
        assertThat(d.shouldAct()).isTrue();
        assertThat(d.isNoOp()).isFalse();
    }

    @Test
    void thresholdIsRespected() {
        // At threshold, shouldAct depends on >= or >; test boundary
        WuWeiPolicy.Decision at = WuWeiPolicy.decide(0.5, 0.5, 3);
        WuWeiPolicy.Decision below = WuWeiPolicy.decide(0.49, 0.5, 3);
        WuWeiPolicy.Decision above = WuWeiPolicy.decide(0.51, 0.5, 3);
        assertThat(at.shouldAct()).isTrue(); // >= threshold
        assertThat(below.shouldAct()).isFalse();
        assertThat(above.shouldAct()).isTrue();
    }

    @Test
    void zeroCandidateActionsReturnsNoOp() {
        WuWeiPolicy.Decision d = WuWeiPolicy.decide(5.0, 0.5, 0);
        assertThat(d.actionIndex()).isEqualTo(0);
        assertThat(d.isNoOp()).isTrue();
    }

    @Test
    void rejectsBadInputs() {
        assertThatThrownBy(() -> WuWeiPolicy.decide(0.5, 0.0, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WuWeiPolicy.decide(0.5, 0.5, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void selectBestActionReturnsLowestFE() {
        int[] candidates = {1, 2, 3, 4};
        double[] fePerAction = {0.8, 0.2, 0.5, 0.3};
        // Below threshold: no-op
        WuWeiPolicy.Decision low = WuWeiPolicy.selectBestAction(0.1, candidates, fePerAction, 0.5);
        assertThat(low.isNoOp()).isTrue();
        // Above threshold: pick action with lowest FE (action 2, index 1)
        WuWeiPolicy.Decision high = WuWeiPolicy.selectBestAction(1.0, candidates, fePerAction, 0.5);
        assertThat(high.actionIndex()).isEqualTo(2);
        assertThat(high.shouldAct()).isTrue();
    }

    @Test
    void excessSurpriseCalculation() {
        WuWeiPolicy.Decision d = WuWeiPolicy.decide(1.5, 1.0, 3);
        assertThat(d.excessSurprise()).isEqualTo(0.5);
    }

    @Test
    void rejectsBadInputsForSelectBest() {
        assertThatThrownBy(() -> WuWeiPolicy.selectBestAction(0.5, null, new double[]{1}, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WuWeiPolicy.selectBestAction(0.5, new int[]{1}, null, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void selectBestActionLengthMismatch() {
        assertThatThrownBy(() -> WuWeiPolicy.selectBestAction(0.5, new int[]{1, 2}, new double[]{1}, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decisionRecordIsNoOp() {
        WuWeiPolicy.Decision d = new WuWeiPolicy.Decision(0, false, 0.1, 0.5);
        assertThat(d.isNoOp()).isTrue();
        WuWeiPolicy.Decision d2 = new WuWeiPolicy.Decision(1, true, 0.8, 0.5);
        assertThat(d2.isNoOp()).isFalse();
    }
}
