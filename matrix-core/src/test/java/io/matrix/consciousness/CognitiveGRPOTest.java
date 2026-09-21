package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveGRPOTest {

    @Test
    void emptyReturnsEmpty() {
        CognitiveGRPO grpo = new CognitiveGRPO(42L);
        CognitiveGRPO.GRPOGroup r = grpo.computeGroup(new ArrayList<>());
        assertThat(r.advantages()).isEmpty();
        assertThat(r.meanReward()).isEqualTo(0.0);
    }

    @Test
    void nullReturnsEmpty() {
        CognitiveGRPO grpo = new CognitiveGRPO(42L);
        CognitiveGRPO.GRPOGroup r = grpo.computeGroup(null);
        assertThat(r.advantages()).isEmpty();
    }

    @Test
    void advantagesNormalized() {
        CognitiveGRPO grpo = new CognitiveGRPO(42L);
        List<Double> rewards = new ArrayList<>();
        for (int i = 0; i < 5; i++) rewards.add((double) i);
        CognitiveGRPO.GRPOGroup r = grpo.computeGroup(rewards);
        // Mean of advantages should be ~0
        double mean = 0;
        for (double a : r.advantages()) mean += a;
        mean /= r.advantages().size();
        assertThat(mean).isCloseTo(0.0, offset(1e-9));
    }

    @Test
    void meanComputed() {
        CognitiveGRPO grpo = new CognitiveGRPO(42L);
        List<Double> rewards = new ArrayList<>();
        rewards.add(0.2);
        rewards.add(0.4);
        rewards.add(0.6);
        CognitiveGRPO.GRPOGroup r = grpo.computeGroup(rewards);
        assertThat(r.meanReward()).isCloseTo(0.4, offset(1e-9));
    }

    @Test
    void lossComputed() {
        CognitiveGRPO grpo = new CognitiveGRPO(42L);
        double loss = grpo.loss(0.5, 0.55, 1.0);
        assertThat(loss).isLessThanOrEqualTo(0.0);
    }

    @Test
    void trainReturnsValue() {
        CognitiveGRPO grpo = new CognitiveGRPO(42L);
        List<List<Double>> groups = new ArrayList<>();
        List<Double> g1 = new ArrayList<>();
        for (int i = 0; i < 4; i++) g1.add((double) i);
        groups.add(g1);
        double avgLoss = grpo.train(groups, 2);
        assertThat(avgLoss).isFinite();
    }

    @Test
    void clipRatioRecorded() {
        CognitiveGRPO grpo = new CognitiveGRPO(42L, 0.3);
        assertThat(grpo.clipRatio()).isEqualTo(0.3);
    }

    private static org.assertj.core.data.Offset<Double> offset(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
