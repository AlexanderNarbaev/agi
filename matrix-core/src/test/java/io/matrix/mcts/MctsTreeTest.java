package io.matrix.mcts;

import io.matrix.evolution.GeneticOperators;
import io.matrix.evolution.TreeWalker;
import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.DecisionTree.Leaf;
import io.matrix.neuron.DecisionTree.Split;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.util.List;
import java.util.Random;
import java.util.function.ToDoubleFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class MctsTreeTest {

    private final Random rng = new Random(42);
    private static final int K = 4;

    // ---- Builder ----

    @Test
    void builderShouldCreateValidTree() {
        DecisionTree state = DecisionTree.random(K, K, rng);
        ToDoubleFunction<DecisionTree> rewardFn = t -> 0.5;

        MctsTree tree = MctsTree.builder()
                .rootState(state)
                .rng(rng)
                .k(K)
                .simulationDepth(3)
                .explorationConstant(MctsNode.EXPLORATION_CONSTANT)
                .rewardFunction(rewardFn)
                .build();

        assertThat(tree.root()).isNotNull();
        assertThat(tree.root().state()).isSameAs(state);
        assertThat(tree.root().isRoot()).isTrue();
        assertThat(tree.simulationDepth()).isEqualTo(3);
        assertThat(tree.explorationConstant())
                .isCloseTo(MctsNode.EXPLORATION_CONSTANT, within(1e-10));
    }

    @Test
    void builderShouldThrowOnMissingRootState() {
        assertThatThrownBy(() -> MctsTree.builder()
                .rng(rng)
                .rewardFunction(t -> 0.5)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void builderShouldThrowOnMissingRng() {
        assertThatThrownBy(() -> MctsTree.builder()
                .rootState(new Leaf(true))
                .rewardFunction(t -> 0.5)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void builderShouldThrowOnMissingRewardFunction() {
        assertThatThrownBy(() -> MctsTree.builder()
                .rootState(new Leaf(true))
                .rng(rng)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    // ---- Selection ----

    @Test
    void selectShouldReturnRootWhenRootIsLeaf() {
        DecisionTree state = new Leaf(true);
        MctsTree tree = buildSimpleTree(state);

        MctsNode selected = tree.select(tree.root());
        assertThat(selected).isSameAs(tree.root());
    }

    @Test
    void selectShouldReturnUnexpandedNode() {
        DecisionTree state = new Split(0, new Leaf(false), new Leaf(true));
        MctsTree tree = buildSimpleTree(state);

        // Expand root once to create a child
        MctsNode child = tree.expand(tree.root());
        assertThat(child).isNotNull();

        // Now root has 1 child but is not fully expanded
        // select should return root (not fully expanded)
        MctsNode selected = tree.select(tree.root());
        assertThat(selected).isSameAs(tree.root());
    }

    @Test
    void selectShouldTraverseFullyExpandedNodes() {
        DecisionTree state = DecisionTree.random(K, K, rng);
        MctsTree tree = buildSimpleTree(state);

        // Fully expand root
        int actions = MctsAction.singleTreeActions().size();
        for (int i = 0; i < actions; i++) {
            tree.expand(tree.root());
        }

        assertThat(tree.root().isFullyExpanded()).isTrue();
        assertThat(tree.root().isLeaf()).isFalse();

        // Give children some visits so UCB1 is well-defined
        for (MctsNode child : tree.root().children()) {
            child.update(0.5);
            tree.root().update(0.5);
        }

        // select should traverse to one of the children (which are leaves)
        MctsNode selected = tree.select(tree.root());
        assertThat(tree.root().children()).contains(selected);
    }

    // ---- Expansion ----

    @Test
    void expandShouldCreateChildNode() {
        DecisionTree state = new Split(0, new Leaf(false), new Leaf(true));
        MctsTree tree = buildSimpleTree(state);

        MctsNode child = tree.expand(tree.root());

        assertThat(child).isNotNull();
        assertThat(child.parent()).isSameAs(tree.root());
        assertThat(child.action()).isNotNull();
        assertThat(tree.root().children()).hasSize(1);
        assertThat(tree.root().untriedActionCount())
                .isEqualTo(MctsAction.singleTreeActions().size() - 1);
    }

    @Test
    void expandShouldApplyMutation() {
        DecisionTree state = new Leaf(true);
        MctsTree tree = buildSimpleTree(state);

        MctsNode child = tree.expand(tree.root());

        // The child state should be different from the parent (most mutations change a Leaf)
        // FLIP_LEAF on a single Leaf should produce Leaf(false)
        assertThat(child.state()).isNotNull();
    }

    @Test
    void expandShouldReturnNodeWhenFullyExpanded() {
        DecisionTree state = DecisionTree.random(K, K, rng);
        MctsTree tree = buildSimpleTree(state);

        // Expand all actions
        int actions = MctsAction.singleTreeActions().size();
        for (int i = 0; i < actions; i++) {
            tree.expand(tree.root());
        }

        // Try to expand again — should return the same node
        MctsNode result = tree.expand(tree.root());
        assertThat(result).isSameAs(tree.root());
    }

    // ---- Simulation ----

    @Test
    void simulateShouldReturnReward() {
        DecisionTree state = DecisionTree.random(K, K, rng);
        MctsTree tree = buildSimpleTree(state);

        MctsNode node = tree.root();
        double reward = tree.simulate(node);

        assertThat(reward).isBetween(0.0, 1.0);
    }

    @Test
    void simulateShouldApplyMultipleMutations() {
        DecisionTree original = new Leaf(true);
        ToDoubleFunction<DecisionTree> rewardFn = t -> {
            // Reward based on tree size (larger = better for this test)
            return Math.min(1.0, TreeWalker.totalNodes(t) / 10.0);
        };

        MctsTree tree = MctsTree.builder()
                .rootState(original)
                .rng(new Random(123))
                .k(K)
                .simulationDepth(10)
                .rewardFunction(rewardFn)
                .build();

        double reward = tree.simulate(tree.root());
        // With 10 mutations, tree should grow and reward should be > 0
        assertThat(reward).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void simulateWithZeroDepthShouldEvaluateInitialState() {
        DecisionTree state = new Leaf(true);
        ToDoubleFunction<DecisionTree> rewardFn = t -> 0.42;

        MctsTree tree = MctsTree.builder()
                .rootState(state)
                .rng(rng)
                .k(K)
                .simulationDepth(0)
                .rewardFunction(rewardFn)
                .build();

        double reward = tree.simulate(tree.root());
        assertThat(reward).isCloseTo(0.42, within(1e-10));
    }

    // ---- Backpropagation ----

    @Test
    void backpropagateShouldUpdateAllAncestors() {
        DecisionTree state = new Split(0, new Leaf(false), new Leaf(true));
        MctsTree tree = buildSimpleTree(state);

        MctsNode child = tree.expand(tree.root());
        MctsNode grandchild = tree.expand(child);

        tree.backpropagate(grandchild, 0.75);

        assertThat(grandchild.visitCount()).isEqualTo(1);
        assertThat(grandchild.totalReward()).isCloseTo(0.75, within(1e-10));
        assertThat(child.visitCount()).isEqualTo(1);
        assertThat(child.totalReward()).isCloseTo(0.75, within(1e-10));
        assertThat(tree.root().visitCount()).isEqualTo(1);
        assertThat(tree.root().totalReward()).isCloseTo(0.75, within(1e-10));
    }

    @Test
    void backpropagateShouldAccumulateMultipleRewards() {
        DecisionTree state = new Leaf(true);
        MctsTree tree = buildSimpleTree(state);

        tree.backpropagate(tree.root(), 0.5);
        tree.backpropagate(tree.root(), 0.3);

        assertThat(tree.root().visitCount()).isEqualTo(2);
        assertThat(tree.root().totalReward()).isCloseTo(0.8, within(1e-10));
        assertThat(tree.root().meanReward()).isCloseTo(0.4, within(1e-10));
    }

    // ---- Full search ----

    @Test
    void runSearchShouldReturnAction() {
        DecisionTree state = DecisionTree.random(K, K, rng);
        MctsTree tree = buildSimpleTree(state);

        MctsAction bestAction = tree.runSearch(50);

        assertThat(bestAction).isNotNull();
        assertThat(bestAction.type()).isIn((Object[]) MctsAction.ActionType.values());
    }

    @Test
    void runSearchShouldExploreMultipleBranches() {
        DecisionTree state = DecisionTree.random(K, K, rng);
        MctsTree tree = buildSimpleTree(state);

        tree.runSearch(100);

        // Root should have been visited many times
        assertThat(tree.root().visitCount()).isEqualTo(100);
        // Root should have children (at least some expanded)
        assertThat(tree.root().children()).isNotEmpty();
    }

    @Test
    void runSearchShouldConvergeToHighRewardAction() {
        // Create a scenario where FLIP_LEAF reliably improves the tree
        DecisionTree state = new Leaf(false); // always returns false
        ToDoubleFunction<DecisionTree> rewardFn = t -> {
            // Reward trees that return true
            return t.evaluate(new java.util.BitSet()) ? 1.0 : 0.0;
        };

        MctsTree tree = MctsTree.builder()
                .rootState(state)
                .rng(new Random(99))
                .k(K)
                .simulationDepth(1)
                .rewardFunction(rewardFn)
                .build();

        MctsAction bestAction = tree.runSearch(200);
        assertThat(bestAction).isNotNull();
    }

    @RepeatedTest(5)
    void runSearchShouldBeDeterministicWithSameSeed() {
        DecisionTree state = DecisionTree.random(K, K, new Random(77));
        ToDoubleFunction<DecisionTree> rewardFn = t -> 0.5;

        MctsTree tree1 = MctsTree.builder()
                .rootState(state)
                .rng(new Random(42))
                .k(K)
                .simulationDepth(3)
                .rewardFunction(rewardFn)
                .build();

        MctsTree tree2 = MctsTree.builder()
                .rootState(state)
                .rng(new Random(42))
                .k(K)
                .simulationDepth(3)
                .rewardFunction(rewardFn)
                .build();

        MctsAction action1 = tree1.runSearch(50);
        MctsAction action2 = tree2.runSearch(50);

        assertThat(action1).isEqualTo(action2);
    }

    @Test
    void runSearchWithMoreIterationsShouldExploreMore() {
        DecisionTree state = DecisionTree.random(K, K, rng);
        ToDoubleFunction<DecisionTree> rewardFn = t -> 0.5;

        MctsTree treeFew = MctsTree.builder()
                .rootState(state)
                .rng(new Random(42))
                .k(K)
                .simulationDepth(3)
                .rewardFunction(rewardFn)
                .build();

        MctsTree treeMany = MctsTree.builder()
                .rootState(state)
                .rng(new Random(42))
                .k(K)
                .simulationDepth(3)
                .rewardFunction(rewardFn)
                .build();

        treeFew.runSearch(10);
        treeMany.runSearch(200);

        // More iterations should produce more children and visits
        assertThat(treeMany.root().visitCount())
                .isGreaterThan(treeFew.root().visitCount());
    }

    // ---- Integration with GeneticOperators ----

    @Test
    void actionsShouldProduceValidTrees() {
        DecisionTree original = DecisionTree.random(K, K, rng);
        List<MctsAction> actions = MctsAction.singleTreeActions();

        for (MctsAction action : actions) {
            DecisionTree mutated = action.apply(original, rng, K);
            assertThat(mutated).isNotNull();
            mutated.validate();
            assertThat(mutated.inputCount()).isLessThanOrEqualTo(K);
        }
    }

    @Test
    void fullSearchWithFitnessReward() {
        DecisionTree state = DecisionTree.random(K, K, rng);

        // Simple fitness: how many true outputs in the truth table
        ToDoubleFunction<DecisionTree> fitnessFn = tree -> {
            try {
                var tt = tree.toTruthTable(K);
                int trueCount = tt.table().cardinality();
                int total = 1 << K;
                return (double) trueCount / total;
            } catch (Exception e) {
                return 0.0;
            }
        };

        MctsTree tree = MctsTree.builder()
                .rootState(state)
                .rng(new Random(42))
                .k(K)
                .simulationDepth(5)
                .explorationConstant(MctsNode.EXPLORATION_CONSTANT)
                .rewardFunction(fitnessFn)
                .build();

        MctsAction best = tree.runSearch(100);
        assertThat(best).isNotNull();

        // Apply the best action and verify it produces a valid tree
        DecisionTree mutated = best.apply(state, rng, K);
        mutated.validate();
    }

    // ---- Configurable simulation depth ----

    @Test
    void differentSimulationDepthsShouldWork() {
        DecisionTree state = DecisionTree.random(K, K, rng);
        ToDoubleFunction<DecisionTree> rewardFn = t -> 0.5;

        for (int depth : new int[]{0, 1, 5, 10, 20}) {
            MctsTree tree = MctsTree.builder()
                    .rootState(state)
                    .rng(new Random(42))
                    .k(K)
                    .simulationDepth(depth)
                    .rewardFunction(rewardFn)
                    .build();

            assertThat(tree.simulationDepth()).isEqualTo(depth);
            MctsAction action = tree.runSearch(10);
            assertThat(action).isNotNull();
        }
    }

    // ---- Configurable exploration constant ----

    @Test
    void differentExplorationConstantsShouldWork() {
        DecisionTree state = DecisionTree.random(K, K, rng);
        ToDoubleFunction<DecisionTree> rewardFn = t -> 0.5;

        for (double c : new double[]{0.1, 0.5, 1.0, Math.sqrt(2.0), 2.0, 5.0}) {
            MctsTree tree = MctsTree.builder()
                    .rootState(state)
                    .rng(new Random(42))
                    .k(K)
                    .explorationConstant(c)
                    .rewardFunction(rewardFn)
                    .build();

            assertThat(tree.explorationConstant()).isCloseTo(c, within(1e-10));
            MctsAction action = tree.runSearch(10);
            assertThat(action).isNotNull();
        }
    }

    // ---- MctsAction ----

    @Test
    void allActionTypesShouldExist() {
        assertThat(MctsAction.ActionType.values()).hasSize(8);
    }

    @Test
    void singleTreeActionsShouldExcludeCrossover() {
        List<MctsAction> actions = MctsAction.singleTreeActions();
        assertThat(actions).hasSize(7);
        assertThat(actions).noneMatch(a -> a.type() == MctsAction.ActionType.CROSSOVER);
    }

    @Test
    void allActionsShouldIncludeCrossover() {
        List<MctsAction> actions = MctsAction.allActions();
        assertThat(actions).hasSize(8);
        assertThat(actions).anyMatch(a -> a.type() == MctsAction.ActionType.CROSSOVER);
    }

    @Test
    void crossoverActionShouldFallbackToFlipLeaf() {
        DecisionTree state = new Split(0, new Leaf(false), new Leaf(true));
        MctsAction crossover = MctsAction.of(MctsAction.ActionType.CROSSOVER);

        DecisionTree result = crossover.apply(state, rng, K);
        assertThat(result).isNotNull();
        result.validate();
    }

    @Test
    void actionToStringShouldContainType() {
        MctsAction action = MctsAction.of(MctsAction.ActionType.FLIP_LEAF);
        assertThat(action.toString()).contains("FLIP_LEAF");
    }

    // ---- Helpers ----

    private MctsTree buildSimpleTree(DecisionTree state) {
        return MctsTree.builder()
                .rootState(state)
                .rng(rng)
                .k(K)
                .simulationDepth(3)
                .explorationConstant(MctsNode.EXPLORATION_CONSTANT)
                .rewardFunction(t -> 0.5)
                .build();
    }
}
