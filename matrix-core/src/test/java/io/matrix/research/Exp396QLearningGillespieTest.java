package io.matrix.research;

import io.matrix.neuron.GillespieSimulator;
import io.matrix.neuron.QLearning;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 396-397 — DESIGN-46/47 implementations (Q-Learning, Gillespie).
 */
class Exp396QLearningGillespieTest {

    @Test
    void qLearningBellmanUpdate() {
        // Q(s,a) = 0.5, reward = 1, next state max = 0.7, α=0.1, γ=0.9
        // new Q = 0.5 + 0.1 * (1 + 0.9 * 0.7 - 0.5) = 0.5 + 0.1 * 1.13 = 0.613
        double[][] q = {{0.5, 0.0, 0.0}, {0.7, 0.0, 0.0}};
        var result = QLearning.update(q, 0, 0, 1.0, 1, 0.1, 0.9);
        assertThat(result.newQ()[0][0]).isCloseTo(0.613,
                org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void qLearningGreedySelectsBest() {
        double[][] q = {{0.0, 0.9, 0.3}};
        // ε = 0 → always greedy
        int action = QLearning.selectAction(q, 0, 0.0, 0xAL);
        assertThat(action).isEqualTo(1);
    }

    @Test
    void qLearningEpsilonGreedyExplores() {
        double[][] q = {{0.0, 0.9, 0.3}};
        // ε = 1 → always random
        int[] actions = new int[100];
        for (long s = 0; s < 100; s++) {
            actions[QLearning.selectAction(q, 0, 1.0, s)]++;
        }
        // All three actions should be chosen at least once
        assertThat(actions[0]).isPositive();
        assertThat(actions[1]).isPositive();
        assertThat(actions[2]).isPositive();
    }

    @Test
    void qLearningTrainsOnSimpleMDP() {
        // 2 states, 2 actions, simple MDP: from state 0, action 1 → state 1 + reward 1
        // Eventually Q(0, 1) should be > Q(0, 0)
        var trained = QLearning.train(2, 2, 200, 0.2, 0.9, 0.1, 0xCAFE,
                (s, a) -> {
                    if (s == 0 && a == 1) return new QLearning.EnvStep(1, 1.0);
                    if (s == 1) return new QLearning.EnvStep(0, 0.0);
                    return new QLearning.EnvStep(0, 0.0);
                });
        // After training, Q(0, 1) should be higher than Q(0, 0)
        assertThat(trained[0][1]).isGreaterThan(trained[0][0]);
    }

    @Test
    void gillespieLinearDecay() {
        // Single population, decay: X → ∅ with rate 1
        // delta: {0:-1} = population[0] decreases by 1
        GillespieSimulator.Reaction decay = new GillespieSimulator.Reaction(
                "decay", new int[]{-1}, 1.0);
        GillespieSimulator.Trajectory traj = GillespieSimulator.simulate(
                new int[]{100}, List.of(decay), 100.0, 1000, 0xCAFE);
        // Population should decrease over time
        assertThat(traj.finalPopulation()[0]).isLessThan(100);
        assertThat(traj.times()).hasSizeGreaterThan(0);
        // Times should be monotonically increasing
        for (int i = 1; i < traj.times().size(); i++) {
            assertThat(traj.times().get(i))
                    .isGreaterThanOrEqualTo(traj.times().get(i - 1));
        }
    }
}
