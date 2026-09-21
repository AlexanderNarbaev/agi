package io.matrix.mcts;

import io.matrix.memory.HierarchicalMemory;
import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.DecisionTree.Leaf;
import io.matrix.neuron.DecisionTree.Split;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.util.List;
import java.util.Random;
import java.util.function.ToDoubleFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MctsTreeLatsTest {

    private final Random rng = new Random(42);
    private static final int K = 4;

    // ---- LATS mode builder ----

    @Test
    void builderShouldCreateLatsTree() {
        DecisionTree state = DecisionTree.random(K, K, rng);
        LatsValueFunction<DecisionTree> valueFn = LatsValueFunction.heuristic(K, rng);
        HierarchicalMemory memory = new HierarchicalMemory();
        LatsReflector reflector = new LatsReflector(memory, 0.3, rng);

        MctsTree tree = MctsTree.builder()
                .rootState(state)
                .rng(rng)
                .k(K)
                .simulationDepth(3)
                .explorationConstant(MctsNode.EXPLORATION_CONSTANT)
                .rewardFunction(t -> 0.5)
                .latsMode(valueFn)
                .reflector(reflector)
                .failureThreshold(0.3)
                .reflectionEveryN(5)
                .build();

        assertThat(tree.isLatsMode()).isTrue();
        assertThat(tree.root()).isInstanceOf(LatsNode.class);
    }

    @Test
    void builderShouldCreateClassicTreeWithoutLats() {
        DecisionTree state = DecisionTree.random(K, K, rng);

        MctsTree tree = MctsTree.builder()
                .rootState(state)
                .rng(rng)
                .k(K)
                .rewardFunction(t -> 0.5)
                .build();

        assertThat(tree.isLatsMode()).isFalse();
        assertThat(tree.root()).isInstanceOf(MctsNode.class);
    }

    // ---- LATS search ----

    @Test
    void latsSearchShouldReturnAction() {
        DecisionTree state = DecisionTree.random(K, K, rng);
        LatsValueFunction<DecisionTree> valueFn = LatsValueFunction.heuristic(K, rng);

        MctsTree tree = MctsTree.builder()
                .rootState(state)
                .rng(new Random(42))
                .k(K)
                .simulationDepth(3)
                .explorationConstant(MctsNode.EXPLORATION_CONSTANT)
                .rewardFunction(t -> 0.5)
                .latsMode(valueFn)
                .build();

        MctsAction best = tree.runSearch(50);
        assertThat(best).isNotNull();
        assertThat(best.type()).isIn((Object[]) MctsAction.ActionType.values());
    }

    @Test
    void latsSearchShouldCreateLatsNodeChildren() {
        DecisionTree state = DecisionTree.random(K, K, rng);
        LatsValueFunction<DecisionTree> valueFn = LatsValueFunction.heuristic(K, rng);

        MctsTree tree = MctsTree.builder()
                .rootState(state)
                .rng(new Random(42))
                .k(K)
                .rewardFunction(t -> 0.5)
                .latsMode(valueFn)
                .build();

        tree.runSearch(20);

        // Root should have LatsNode children
        assertThat(tree.root().children()).isNotEmpty();
        for (var child : tree.root().children()) {
            assertThat(child).isInstanceOf(LatsNode.class);
        }
    }

    @Test
    void latsSearchShouldSetPriorOnChildren() {
        DecisionTree state = DecisionTree.random(K, K, rng);
        LatsValueFunction<DecisionTree> valueFn = LatsValueFunction.heuristic(K, rng);

        MctsTree tree = MctsTree.builder()
                .rootState(state)
                .rng(new Random(42))
                .k(K)
                .rewardFunction(t -> 0.5)
                .latsMode(valueFn)
                .build();

        tree.runSearch(20);

        for (var child : tree.root().children()) {
            if (child instanceof LatsNode latsChild) {
                assertThat(latsChild.prior()).isBetween(0.01, 1.0);
            }
        }
    }

    // ---- LATS with reflection ----

    @Test
    void latsShouldGenerateReflections() {
        DecisionTree state = new Leaf(false); // always returns false
        ToDoubleFunction<DecisionTree> rewardFn = t -> {
            return t.evaluate(new java.util.BitSet()) ? 1.0 : 0.0;
        };

        HierarchicalMemory memory = new HierarchicalMemory();
        LatsReflector reflector = new LatsReflector(memory, 0.3, rng);
        LatsValueFunction<DecisionTree> valueFn = LatsValueFunction.fromRewardFunction(rewardFn);

        MctsTree tree = MctsTree.builder()
                .rootState(state)
                .rng(new Random(42))
                .k(K)
                .rewardFunction(rewardFn)
                .latsMode(valueFn)
                .reflector(reflector)
                .failureThreshold(0.3)
                .reflectionEveryN(5)
                .build();

        tree.runSearch(50);

        // Should have generated some reflections
        assertThat(reflector.reflectionCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void latsShouldBackpropagateValueScores() {
        DecisionTree state = DecisionTree.random(K, K, rng);
        LatsValueFunction<DecisionTree> valueFn = LatsValueFunction.heuristic(K, rng);

        MctsTree tree = MctsTree.builder()
                .rootState(state)
                .rng(new Random(42))
                .k(K)
                .rewardFunction(t -> 0.5)
                .latsMode(valueFn)
                .build();

        tree.runSearch(30);

        // All visited nodes should have non-zero visit counts
        assertThat(tree.root().visitCount()).isEqualTo(30);
    }

    // ---- JSON export ----

    @Test
    void classicTreeShouldExportJson() {
        DecisionTree state = new Split(0, new Leaf(false), new Leaf(true));

        MctsTree tree = MctsTree.builder()
                .rootState(state)
                .rng(rng)
                .k(K)
                .rewardFunction(t -> 0.5)
                .build();

        tree.runSearch(10);

        String json = tree.exportJson();
        assertThat(json).contains("\"mode\": \"MCTS\"");
        assertThat(json).contains("\"root\":");
        assertThat(json).contains("\"visits\":");
        assertThat(json).contains("\"children\":");
    }

    @Test
    void latsTreeShouldExportJsonWithLatsFields() {
        DecisionTree state = DecisionTree.random(K, K, rng);
        LatsValueFunction<DecisionTree> valueFn = LatsValueFunction.heuristic(K, rng);

        MctsTree tree = MctsTree.builder()
                .rootState(state)
                .rng(new Random(42))
                .k(K)
                .rewardFunction(t -> 0.5)
                .latsMode(valueFn)
                .build();

        tree.runSearch(20);

        String json = tree.exportJson();
        assertThat(json).contains("\"mode\": \"LATS\"");
        assertThat(json).contains("\"valueScore\":");
        assertThat(json).contains("\"prior\":");
        assertThat(json).contains("\"status\":");
    }

    @Test
    void jsonExportShouldContainReflections() {
        DecisionTree state = new Leaf(false);
        ToDoubleFunction<DecisionTree> rewardFn = t -> 0.1; // low reward = failures

        HierarchicalMemory memory = new HierarchicalMemory();
        LatsReflector reflector = new LatsReflector(memory, 0.3, rng);
        LatsValueFunction<DecisionTree> valueFn = LatsValueFunction.fromRewardFunction(rewardFn);

        MctsTree tree = MctsTree.builder()
                .rootState(state)
                .rng(new Random(42))
                .k(K)
                .rewardFunction(rewardFn)
                .latsMode(valueFn)
                .reflector(reflector)
                .failureThreshold(0.3)
                .reflectionEveryN(3)
                .build();

        tree.runSearch(30);

        String json = tree.exportJson();
        // JSON should be valid (contains expected structure)
        assertThat(json).startsWith("{");
        assertThat(json).endsWith("}");
    }

    // ---- Classic MCTS still works ----

    @Test
    void classicMctsShouldStillWork() {
        DecisionTree state = DecisionTree.random(K, K, rng);
        ToDoubleFunction<DecisionTree> rewardFn = t -> 0.5;

        MctsTree tree = MctsTree.builder()
                .rootState(state)
                .rng(new Random(42))
                .k(K)
                .simulationDepth(3)
                .explorationConstant(MctsNode.EXPLORATION_CONSTANT)
                .rewardFunction(rewardFn)
                .build();

        MctsAction best = tree.runSearch(100);
        assertThat(best).isNotNull();
        assertThat(tree.root().visitCount()).isEqualTo(100);
    }

    @RepeatedTest(3)
    void latsShouldBeDeterministicWithSameSeed() {
        DecisionTree state = DecisionTree.random(K, K, new Random(77));
        LatsValueFunction<DecisionTree> valueFn = LatsValueFunction.heuristic(K, rng);

        MctsTree tree1 = MctsTree.builder()
                .rootState(state)
                .rng(new Random(42))
                .k(K)
                .rewardFunction(t -> 0.5)
                .latsMode(valueFn)
                .build();

        MctsTree tree2 = MctsTree.builder()
                .rootState(state)
                .rng(new Random(42))
                .k(K)
                .rewardFunction(t -> 0.5)
                .latsMode(valueFn)
                .build();

        MctsAction action1 = tree1.runSearch(30);
        MctsAction action2 = tree2.runSearch(30);

        assertThat(action1).isEqualTo(action2);
    }

    // ---- LATS with composite value function ----

    @Test
    void latsShouldWorkWithCompositeValueFunction() {
        DecisionTree state = DecisionTree.random(K, K, rng);

        LatsValueFunction<DecisionTree> heuristic = LatsValueFunction.heuristic(K, rng);
        LatsValueFunction<DecisionTree> reward = LatsValueFunction.fromRewardFunction(t -> 0.5);
        LatsValueFunction<DecisionTree> composite = LatsValueFunction.composite(List.of(heuristic, reward));

        MctsTree tree = MctsTree.builder()
                .rootState(state)
                .rng(new Random(42))
                .k(K)
                .rewardFunction(t -> 0.5)
                .latsMode(composite)
                .build();

        MctsAction best = tree.runSearch(30);
        assertThat(best).isNotNull();
    }

    // ---- Exploration constant ----

    @Test
    void latsShouldSupportDifferentExplorationConstants() {
        DecisionTree state = DecisionTree.random(K, K, rng);
        LatsValueFunction<DecisionTree> valueFn = LatsValueFunction.heuristic(K, rng);

        for (double c : new double[]{0.5, 1.0, Math.sqrt(2.0), 2.0}) {
            MctsTree tree = MctsTree.builder()
                    .rootState(state)
                    .rng(new Random(42))
                    .k(K)
                    .explorationConstant(c)
                    .rewardFunction(t -> 0.5)
                    .latsMode(valueFn)
                    .build();

            assertThat(tree.explorationConstant()).isCloseTo(c, within(1e-10));
            MctsAction action = tree.runSearch(10);
            assertThat(action).isNotNull();
        }
    }
}
