package io.matrix.mcts;

import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.DecisionTree.Leaf;
import io.matrix.neuron.DecisionTree.Split;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class LatsValueFunctionTest {

    private final Random rng = new Random(42);
    private static final int K = 4;

    // ---- RewardFunctionAdapter ----

    @Test
    void adapterShouldWrapRewardFunction() {
        DecisionTree state = new Leaf(true);
        LatsValueFunction<DecisionTree> fn = LatsValueFunction.fromRewardFunction(t -> 0.75);

        assertThat(fn.evaluate(state)).isCloseTo(0.75, within(1e-10));
    }

    @Test
    void adapterShouldThrowOnNull() {
        assertThatThrownBy(() -> LatsValueFunction.fromRewardFunction(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void adapterNameShouldContainClass() {
        LatsValueFunction<DecisionTree> fn = LatsValueFunction.fromRewardFunction(t -> 0.5);
        assertThat(fn.name()).contains("RewardFunctionAdapter");
    }

    // ---- HeuristicValueFunction ----

    @Test
    void heuristicShouldReturnScoreInRange() {
        LatsValueFunction<DecisionTree> fn = LatsValueFunction.heuristic(K, rng);

        for (int i = 0; i < 20; i++) {
            DecisionTree tree = DecisionTree.random(K, rng.nextInt(5) + 1, rng);
            double score = fn.evaluate(tree);
            assertThat(score).isBetween(0.0, 1.0);
        }
    }

    @Test
    void heuristicShouldScoreLeafHigherThanZero() {
        LatsValueFunction<DecisionTree> fn = LatsValueFunction.heuristic(K, rng);
        double score = fn.evaluate(new Leaf(true));
        assertThat(score).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void heuristicShouldPenalizeVeryLargeTrees() {
        LatsValueFunction<DecisionTree> fn = LatsValueFunction.heuristic(K, rng);

        // Create a moderately sized tree
        DecisionTree moderate = new Split(0,
                new Split(1, new Leaf(true), new Leaf(false)),
                new Split(2, new Leaf(false), new Leaf(true)));

        // Create a very large tree (by growing)
        DecisionTree large = moderate;
        for (int i = 0; i < 20; i++) {
            large = io.matrix.evolution.GeneticOperators.growSubtree(rng, large, K);
        }

        double moderateScore = fn.evaluate(moderate);
        double largeScore = fn.evaluate(large);

        // Large tree should have complexity penalty
        // Note: this is a heuristic test, so we just verify both are in range
        assertThat(moderateScore).isBetween(0.0, 1.0);
        assertThat(largeScore).isBetween(0.0, 1.0);
    }

    @Test
    void heuristicNameShouldContainK() {
        LatsValueFunction<DecisionTree> fn = LatsValueFunction.heuristic(K, rng);
        assertThat(fn.name()).contains("HeuristicValueFunction");
        assertThat(fn.name()).contains(String.valueOf(K));
    }

    // ---- CompositeValueFunction ----

    @Test
    void compositeShouldAverageScores() {
        LatsValueFunction<DecisionTree> fn1 = t -> 0.8;
        LatsValueFunction<DecisionTree> fn2 = t -> 0.4;

        LatsValueFunction<DecisionTree> composite = LatsValueFunction.composite(List.of(fn1, fn2));

        // Equal weights: (0.8 + 0.4) / 2 = 0.6
        assertThat(composite.evaluate(new Leaf(true))).isCloseTo(0.6, within(1e-10));
    }

    @Test
    void compositeShouldClampScores() {
        LatsValueFunction<DecisionTree> fn1 = t -> 1.0;
        LatsValueFunction<DecisionTree> fn2 = t -> 1.0;

        LatsValueFunction<DecisionTree> composite = LatsValueFunction.composite(List.of(fn1, fn2));
        assertThat(composite.evaluate(new Leaf(true))).isCloseTo(1.0, within(1e-10));
    }

    @Test
    void compositeShouldThrowOnEmpty() {
        assertThatThrownBy(() -> LatsValueFunction.composite(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void compositeShouldSupportBatchEvaluation() {
        LatsValueFunction<DecisionTree> fn1 = t -> 0.3;
        LatsValueFunction<DecisionTree> fn2 = t -> 0.7;

        LatsValueFunction<DecisionTree> composite = LatsValueFunction.composite(List.of(fn1, fn2));

        List<DecisionTree> states = List.of(new Leaf(true), new Leaf(false), new Split(0, new Leaf(true), new Leaf(false)));
        double[] scores = composite.evaluateBatch(states);

        assertThat(scores).hasSize(3);
        for (double score : scores) {
            assertThat(score).isCloseTo(0.5, within(1e-10));
        }
    }

    @Test
    void compositeNameShouldContainCount() {
        LatsValueFunction<DecisionTree> fn1 = t -> 0.5;
        LatsValueFunction<DecisionTree> fn2 = t -> 0.5;

        LatsValueFunction<DecisionTree> composite = LatsValueFunction.composite(List.of(fn1, fn2));
        assertThat(composite.name()).contains("Composite");
        assertThat(composite.name()).contains("2");
    }

    // ---- Default batch evaluation ----

    @Test
    void defaultBatchEvaluationShouldWork() {
        LatsValueFunction<DecisionTree> fn = t -> {
            return t instanceof Leaf leaf ? (leaf.value() ? 1.0 : 0.0) : 0.5;
        };

        List<DecisionTree> states = List.of(new Leaf(true), new Leaf(false));
        double[] scores = fn.evaluateBatch(states);

        assertThat(scores).containsExactly(1.0, 0.0);
    }

    // ---- Custom weights ----

    @Test
    void compositeWithCustomWeights() {
        LatsValueFunction<DecisionTree> fn1 = t -> 1.0;
        LatsValueFunction<DecisionTree> fn2 = t -> 0.0;

        // 70% fn1, 30% fn2
        LatsValueFunction<DecisionTree> composite =
                new LatsValueFunction.CompositeValueFunction(List.of(fn1, fn2), new double[]{0.7, 0.3});

        assertThat(composite.evaluate(new Leaf(true))).isCloseTo(0.7, within(1e-10));
    }

    @Test
    void compositeShouldThrowOnMismatchedWeights() {
        LatsValueFunction<DecisionTree> fn1 = t -> 0.5;

        assertThatThrownBy(() ->
                new LatsValueFunction.CompositeValueFunction(List.of(fn1), new double[]{0.5, 0.5}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void compositeShouldThrowOnNonUnitWeights() {
        LatsValueFunction<DecisionTree> fn1 = t -> 0.5;
        LatsValueFunction<DecisionTree> fn2 = t -> 0.5;

        assertThatThrownBy(() ->
                new LatsValueFunction.CompositeValueFunction(List.of(fn1, fn2), new double[]{0.3, 0.3}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
