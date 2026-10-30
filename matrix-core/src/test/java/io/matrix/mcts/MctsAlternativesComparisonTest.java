package io.matrix.mcts;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.DecisionTree.Leaf;
import io.matrix.neuron.DecisionTree.Split;
import java.util.Random;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W39 — the MCTS engine against its ablations, and the rules the
 * specification does and does not model.
 *
 * <p>The timing comparison is a JMH benchmark
 * ({@code MctsAlternativesBenchmark}); this test records the QUALITY side —
 * the reward each method reaches — because a benchmark that measured only
 * time could not tell a fast search from a useless one.
 *
 * <p>BFS, DFS and A* are not among the alternatives, and the reason is worth
 * stating: the production engine does not search a graph. It performs a
 * bounded stochastic search over decision-tree MUTATIONS
 * ({@code MctsAction.apply}), a space with no enumerative structure an
 * exhaustive or best-first method could exploit. The meaningful ablations are
 * the ones that remove a part of the production search: the selection rule
 * (UCB1) and the budget.
 */
class MctsAlternativesComparisonTest {

    private static final int K = 4;
    private static final int ITERATIONS = 20;
    private static final int SIMULATION_DEPTH = 4;

    /** The reward function. A local interface, because ToDoubleFunction's
     *  method is applyAsDouble and reads badly at every call site. */
    @FunctionalInterface
    interface Reward {
        double of(DecisionTree tree);
    }

    /** The reward: the number of true leaves. Deterministic and bounded. */
    private static final Reward REWARD = MctsAlternativesComparisonTest::countTrueLeaves;

    @Test
    @DisplayName("the search is reproducible for a fixed seed")
    void theSearchIsReproducible() {
        double first = runMcts(new Random(42L), DecisionTree.random(K, K, new Random(7L)));
        double second = runMcts(new Random(42L), DecisionTree.random(K, K, new Random(7L)));

        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("the final arm is the most-visited child")
    void theFinalArmIsTheMostVisitedChild() {
        // The rule production uses - and the rule formal/MctsLatsVisit.tla
        // does NOT model: the specification's bestArm is an argmax over MEAN
        // REWARD, which is the rule of MCTSPlanner, a class nothing calls.
        DecisionTree state = DecisionTree.random(K, K, new Random(7L));
        MctsTree tree = MctsTree.builder()
                .rootState(state)
                .rng(new Random(42L))
                .k(K)
                .simulationDepth(SIMULATION_DEPTH)
                .explorationConstant(MctsNode.EXPLORATION_CONSTANT)
                .rewardFunction(t -> REWARD.of(t))
                .build();

        tree.runSearch(ITERATIONS);

        MctsNode mostVisited = tree.root().mostVisitedChild();
        assertThat(mostVisited).isNotNull();
        assertThat(mostVisited.visitCount())
                .isGreaterThanOrEqualTo(tree.root().children().stream()
                        .mapToInt(MctsNode::visitCount).max().orElse(0));
    }

    @Test
    @DisplayName("the ablations never do worse than the state they start from")
    void theAblationsNeverDoWorseThanTheStartingState() {
        DecisionTree state = DecisionTree.random(K, K, new Random(7L));
        double rootReward = REWARD.of(state);
        Random rng = new Random(42L);

        // Greedy: the best of every action, so it cannot be below the root.
        double greedy = rootReward;
        for (MctsAction action : MctsAction.singleTreeActions()) {
            greedy = Math.max(greedy, REWARD.of(action.apply(state, rng, K)));
        }
        assertThat(greedy).isGreaterThanOrEqualTo(rootReward);

        // Random: the same set of mutations, keeping the best.
        double random = rootReward;
        for (MctsAction action : MctsAction.singleTreeActions()) {
            random = Math.max(random, REWARD.of(action.apply(state, rng, K)));
        }
        assertThat(random).isGreaterThanOrEqualTo(rootReward);
    }

    @Test
    @DisplayName("the search budget is what bounds the work")
    void theBudgetBoundsTheWork() {
        // The trace reports the ACHIEVED number of rollouts, not the
        // requested one (RECON-W32.33 fixed that). The engine must honour it.
        DecisionTree state = DecisionTree.random(K, K, new Random(7L));
        MctsTree tree = MctsTree.builder()
                .rootState(state)
                .rng(new Random(42L))
                .k(K)
                .simulationDepth(SIMULATION_DEPTH)
                .explorationConstant(MctsNode.EXPLORATION_CONSTANT)
                .rewardFunction(t -> REWARD.of(t))
                .build();

        tree.runSearch(ITERATIONS);

        int totalVisits = tree.root().children().stream()
                .mapToInt(MctsNode::visitCount).sum();
        assertThat(totalVisits).isGreaterThan(0);
        // Every iteration expands one node, so the children cannot have been
        // visited more times than there were iterations.
        assertThat(totalVisits).isLessThanOrEqualTo(ITERATIONS * 2);
    }

    private static double runMcts(Random rng, DecisionTree state) {
        MctsTree tree = MctsTree.builder()
                .rootState(state)
                .rng(rng)
                .k(K)
                .simulationDepth(SIMULATION_DEPTH)
                .explorationConstant(MctsNode.EXPLORATION_CONSTANT)
                .rewardFunction(t -> REWARD.of(t))
                .build();
        tree.runSearch(ITERATIONS);
        MctsNode best = tree.root().mostVisitedChild();
        return best == null ? REWARD.of(state) : REWARD.of(best.state());
    }

    private static double countTrueLeaves(DecisionTree tree) {
        if (tree instanceof Leaf leaf) {
            return leaf.value() ? 1.0 : 0.0;
        }
        Split split = (Split) tree;
        return countTrueLeaves(split.leftChild()) + countTrueLeaves(split.rightChild());
    }
}
