package io.matrix.mcts;

import io.matrix.neuron.DecisionTree;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.ToDoubleFunction;

/**
 * Monte Carlo Tree Search for guiding evolutionary mutation of {@link DecisionTree}.
 *
 * <p>Implements the four phases of MCTS:
 * <ol>
 *   <li><b>Selection:</b> traverse the tree using UCB1 to find a leaf</li>
 *   <li><b>Expansion:</b> add a new child for an untried action</li>
 *   <li><b>Simulation:</b> random playout from the new node</li>
 *   <li><b>Backpropagation:</b> update statistics up to the root</li>
 * </ol>
 *
 * <p>The search recommends the best single mutation to apply to the current
 * DecisionTree, replacing random mutation selection in the evolution loop.
 *
 * <p>Ref: Phase 4 MCTS-Guided Evolution
 */
public final class MctsTree {

    private final MctsNode root;
    private final Random rng;
    private final int k;
    private final int simulationDepth;
    private final double explorationConstant;
    private final ToDoubleFunction<DecisionTree> rewardFunction;

    /**
     * Creates a new MCTS tree.
     *
     * @param root               the root node
     * @param rng                random number generator
     * @param k                  number of inputs for the decision tree
     * @param simulationDepth    number of random mutations in simulation playout
     * @param explorationConstant UCB1 exploration constant (C)
     * @param rewardFunction     function to evaluate a DecisionTree's reward (0.0 to 1.0)
     */
    public MctsTree(MctsNode root, Random rng, int k, int simulationDepth,
                    double explorationConstant, ToDoubleFunction<DecisionTree> rewardFunction) {
        this.root = Objects.requireNonNull(root, "root");
        this.rng = Objects.requireNonNull(rng, "rng");
        this.k = k;
        this.simulationDepth = simulationDepth;
        this.explorationConstant = explorationConstant;
        this.rewardFunction = Objects.requireNonNull(rewardFunction, "rewardFunction");
    }

    /**
     * Runs the MCTS search for the specified number of iterations.
     *
     * @param iterations number of MCTS iterations to run
     * @return the best action to take from the root state
     */
    public MctsAction runSearch(int iterations) {
        for (int i = 0; i < iterations; i++) {
            MctsNode leaf = select(root);
            MctsNode expanded = expand(leaf);
            double reward = simulate(expanded);
            backpropagate(expanded, reward);
        }
        MctsNode best = root.mostVisitedChild();
        return best.action();
    }

    /**
     * Phase 1 — Selection: traverse the tree from the given node to a leaf
     * using UCB1 at each level.
     *
     * @param node the starting node (typically the root)
     * @return a leaf node (fully expanded or terminal)
     */
    MctsNode select(MctsNode node) {
        while (!node.isLeaf()) {
            if (!node.isFullyExpanded()) {
                return node;
            }
            node = node.bestChild(explorationConstant);
        }
        return node;
    }

    /**
     * Phase 2 — Expansion: if the node has untried actions, pick one,
     * apply it to create a new child, and return the child.
     *
     * @param node the leaf node to expand
     * @return the newly created child node, or the input node if fully expanded
     */
    MctsNode expand(MctsNode node) {
        if (node.isFullyExpanded()) {
            return node;
        }

        MctsAction action = node.nextUntriedAction();
        DecisionTree newState = action.apply(node.state(), rng, k);

        List<MctsAction> childActions = MctsAction.singleTreeActions();
        MctsNode child = new MctsNode(node, action, newState, childActions);
        node.addChild(child);
        return child;
    }

    /**
     * Phase 3 — Simulation: from the given node, perform a random playout
     * by applying random mutations for {@code simulationDepth} steps,
     * then evaluate the resulting tree's reward.
     *
     * @param node the node to simulate from
     * @return the reward of the simulated playout (0.0 to 1.0)
     */
    double simulate(MctsNode node) {
        DecisionTree current = node.state();
        List<MctsAction> actions = MctsAction.singleTreeActions();

        for (int i = 0; i < simulationDepth; i++) {
            MctsAction randomAction = actions.get(rng.nextInt(actions.size()));
            current = randomAction.apply(current, rng, k);
        }

        return rewardFunction.applyAsDouble(current);
    }

    /**
     * Phase 4 — Backpropagation: update visit counts and rewards from the
     * given node up to the root.
     *
     * @param node   the node to start backpropagation from
     * @param reward the reward to propagate
     */
    void backpropagate(MctsNode node, double reward) {
        while (node != null) {
            node.update(reward);
            node = node.parent();
        }
    }

    /**
     * Returns the root node of the tree.
     */
    public MctsNode root() {
        return root;
    }

    /**
     * Returns the exploration constant.
     */
    public double explorationConstant() {
        return explorationConstant;
    }

    /**
     * Returns the simulation depth.
     */
    public int simulationDepth() {
        return simulationDepth;
    }

    /**
     * Creates a builder for constructing MCTS trees.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing {@link MctsTree} instances.
     */
    public static final class Builder {
        private DecisionTree rootState;
        private Random rng;
        private int k = 4;
        private int simulationDepth = 5;
        private double explorationConstant = MctsNode.EXPLORATION_CONSTANT;
        private ToDoubleFunction<DecisionTree> rewardFunction;

        public Builder rootState(DecisionTree rootState) {
            this.rootState = rootState;
            return this;
        }

        public Builder rng(Random rng) {
            this.rng = rng;
            return this;
        }

        public Builder k(int k) {
            this.k = k;
            return this;
        }

        public Builder simulationDepth(int simulationDepth) {
            this.simulationDepth = simulationDepth;
            return this;
        }

        public Builder explorationConstant(double explorationConstant) {
            this.explorationConstant = explorationConstant;
            return this;
        }

        public Builder rewardFunction(ToDoubleFunction<DecisionTree> rewardFunction) {
            this.rewardFunction = rewardFunction;
            return this;
        }

        /**
         * Builds the MCTS tree.
         *
         * @return a new MctsTree
         * @throws IllegalStateException if required fields are missing
         */
        public MctsTree build() {
            Objects.requireNonNull(rootState, "rootState");
            Objects.requireNonNull(rng, "rng");
            Objects.requireNonNull(rewardFunction, "rewardFunction");

            List<MctsAction> actions = MctsAction.singleTreeActions();
            MctsNode root = new MctsNode(null, null, rootState, actions);
            return new MctsTree(root, rng, k, simulationDepth, explorationConstant, rewardFunction);
        }
    }
}
