package io.matrix.mcts;

import io.matrix.neuron.DecisionTree;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.StringJoiner;
import java.util.function.ToDoubleFunction;

/**
 * Monte Carlo Tree Search with LATS (Language Agent Tree Search) extensions.
 *
 * <p>Implements two modes:
 * <ul>
 *   <li><b>Classic MCTS:</b> four phases — Selection, Expansion, Simulation (random), Backpropagation</li>
 *   <li><b>LATS mode:</b> replaces random simulation with value function evaluation,
 *       adds reflection phase for failed branches, backpropagates insights</li>
 * </ul>
 *
 * <p>LATS key enhancements (arXiv:2310.04406):
 * <ol>
 *   <li><b>Value Function:</b> evaluates node quality using LLM/heuristic instead of random playout</li>
 *   <li><b>Self-Reflection:</b> generates insights on failed branches to guide future search</li>
 *   <li><b>PUCT Selection:</b> uses Predictor + UCB when in LATS mode</li>
 * </ol>
 *
 * <p>Ref: Phase 4 MCTS-Guided Evolution, LATS (arXiv:2310.04406)
 */
public final class MctsTree {

    private final MctsNode root;
    private final Random rng;
    private final int k;
    private final int simulationDepth;
    private final double explorationConstant;
    private final ToDoubleFunction<DecisionTree> rewardFunction;

    // LATS extensions
    private final boolean latsMode;
    private final LatsValueFunction<DecisionTree> valueFunction;
    private final LatsReflector reflector;
    private final double failureThreshold;
    private final int reflectionEveryN;

    /**
     * Creates a new MCTS tree (classic mode).
     */
    public MctsTree(MctsNode root, Random rng, int k, int simulationDepth,
                    double explorationConstant, ToDoubleFunction<DecisionTree> rewardFunction) {
        this(root, rng, k, simulationDepth, explorationConstant, rewardFunction,
                false, null, null, 0.3, 5);
    }

    /**
     * Creates a new MCTS/LATS tree.
     *
     * @param root               the root node
     * @param rng                random number generator
     * @param k                  number of inputs for the decision tree
     * @param simulationDepth    number of random mutations in simulation playout
     * @param explorationConstant UCB1/PUCT exploration constant (C)
     * @param rewardFunction     function to evaluate a DecisionTree's reward (0.0 to 1.0)
     * @param latsMode           true to enable LATS mode
     * @param valueFunction      LATS value function (null for classic mode)
     * @param reflector          LATS reflector (null for classic mode)
     * @param failureThreshold   reward threshold for failure detection
     * @param reflectionEveryN   generate reflections every N iterations
     */
    public MctsTree(MctsNode root, Random rng, int k, int simulationDepth,
                    double explorationConstant, ToDoubleFunction<DecisionTree> rewardFunction,
                    boolean latsMode, LatsValueFunction<DecisionTree> valueFunction,
                    LatsReflector reflector, double failureThreshold, int reflectionEveryN) {
        this.root = Objects.requireNonNull(root, "root");
        this.rng = Objects.requireNonNull(rng, "rng");
        this.k = k;
        this.simulationDepth = simulationDepth;
        this.explorationConstant = explorationConstant;
        this.rewardFunction = Objects.requireNonNull(rewardFunction, "rewardFunction");
        this.latsMode = latsMode;
        this.valueFunction = valueFunction;
        this.reflector = reflector;
        this.failureThreshold = failureThreshold;
        this.reflectionEveryN = reflectionEveryN;
    }

    /**
     * Runs the MCTS/LATS search for the specified number of iterations.
     *
     * @param iterations number of iterations to run
     * @return the best action to take from the root state
     */
    public MctsAction runSearch(int iterations) {
        for (int i = 0; i < iterations; i++) {
            MctsNode leaf = select(root);
            MctsNode expanded = expand(leaf);
            double reward;

            if (latsMode && valueFunction != null) {
                // LATS: use value function instead of random simulation
                reward = valueFunction.evaluate(expanded.state());
                if (expanded instanceof LatsNode latsNode) {
                    latsNode.setValueScore(reward);
                }
            } else {
                // Classic MCTS: random simulation
                reward = simulate(expanded);
            }

            backpropagate(expanded, reward);

            // LATS: reflection phase (every N iterations)
            if (latsMode && reflector != null && i > 0 && i % reflectionEveryN == 0) {
                reflectOnWeakBranches();
            }
        }

        MctsNode best = root.mostVisitedChild();
        return best.action();
    }

    /**
     * LATS reflection phase: reflect on the weakest branches.
     */
    private void reflectOnWeakBranches() {
        if (!(root instanceof LatsNode latsRoot)) return;

        for (MctsNode child : root.children()) {
            if (child instanceof LatsNode latsChild) {
                if (latsChild.meanReward() < failureThreshold && latsChild.visitCount() > 0) {
                    reflector.reflect(latsChild, latsRoot);
                }
            }
        }
    }

    /**
     * Phase 1 — Selection: traverse the tree from the given node to a leaf.
     *
     * <p>In LATS mode, uses PUCT (Predictor + UCB applied to Trees).
     * In classic mode, uses UCB1.
     */
    MctsNode select(MctsNode node) {
        while (!node.isLeaf()) {
            if (!node.isFullyExpanded()) {
                return node;
            }
            if (latsMode && node instanceof LatsNode latsNode) {
                // LATS: PUCT selection
                LatsNode best = latsNode.bestLatsChild(explorationConstant);
                if (best != null) {
                    node = best;
                } else {
                    node = node.bestChild(explorationConstant);
                }
            } else {
                node = node.bestChild(explorationConstant);
            }
        }
        return node;
    }

    /**
     * Phase 2 — Expansion: add a new child for an untried action.
     *
     * <p>In LATS mode, creates {@link LatsNode} children with prior probabilities.
     */
    MctsNode expand(MctsNode node) {
        if (node.isFullyExpanded()) {
            return node;
        }

        MctsAction action = node.nextUntriedAction();
        DecisionTree newState = action.apply(node.state(), rng, k);

        List<MctsAction> childActions = MctsAction.singleTreeActions();
        MctsNode child;

        if (latsMode) {
            LatsNode latsChild = new LatsNode(node, action, newState, childActions);
            // Set prior based on value function if available
            if (valueFunction != null) {
                double prior = valueFunction.evaluate(newState);
                latsChild.setPrior(Math.max(0.01, Math.min(1.0, prior)));
            }
            child = latsChild;
        } else {
            child = new MctsNode(node, action, newState, childActions);
        }

        node.addChild(child);
        return child;
    }

    /**
     * Phase 3 — Simulation: random playout (classic mode only).
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
     * Phase 4 — Backpropagation: update statistics up to the root.
     *
     * <p>In LATS mode, also backpropagates reflection insights.
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
     * Returns true if LATS mode is enabled.
     */
    public boolean isLatsMode() {
        return latsMode;
    }

    /**
     * Exports the tree as a JSON string for visualization.
     *
     * <p>The JSON structure:
     * <pre>
     * {
     *   "root": {
     *     "action": "FLIP_LEAF",
     *     "visits": 10,
     *     "reward": 5.0,
     *     "meanReward": 0.5,
     *     "valueScore": 0.7,
     *     "prior": 0.8,
     *     "status": "SUCCESS",
     *     "reflection": "...",
     *     "children": [...]
     *   },
     *   "mode": "LATS",
     *   "explorationConstant": 1.414
     * }
     * </pre>
     *
     * @return JSON string representation of the tree
     */
    public String exportJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"root\": ").append(nodeToJson(root, "  ")).append(",\n");
        sb.append("  \"mode\": \"").append(latsMode ? "LATS" : "MCTS").append("\",\n");
        sb.append("  \"explorationConstant\": ").append(explorationConstant).append(",\n");
        sb.append("  \"simulationDepth\": ").append(simulationDepth).append("\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Recursively serializes a node to JSON.
     */
    private String nodeToJson(MctsNode node, String indent) {
        String childIndent = indent + "  ";
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        // Basic MCTS fields
        sb.append(childIndent).append("\"action\": ").append(jsonString(node.action())).append(",\n");
        sb.append(childIndent).append("\"visits\": ").append(node.visitCount()).append(",\n");
        sb.append(childIndent).append("\"totalReward\": ").append(String.format("%.4f", node.totalReward())).append(",\n");
        sb.append(childIndent).append("\"meanReward\": ").append(String.format("%.4f", node.meanReward())).append(",\n");
        sb.append(childIndent).append("\"depth\": ").append(node.depth()).append(",\n");

        // LATS-specific fields
        if (node instanceof LatsNode latsNode) {
            sb.append(childIndent).append("\"valueScore\": ").append(String.format("%.4f", latsNode.valueScore())).append(",\n");
            sb.append(childIndent).append("\"prior\": ").append(String.format("%.4f", latsNode.prior())).append(",\n");
            sb.append(childIndent).append("\"status\": \"").append(latsNode.status()).append("\",\n");
            sb.append(childIndent).append("\"hasReflection\": ").append(latsNode.hasReflection()).append(",\n");
            if (latsNode.hasReflection()) {
                sb.append(childIndent).append("\"reflection\": ").append(jsonString(latsNode.reflection())).append(",\n");
            }
        }

        // Children
        sb.append(childIndent).append("\"children\": [");
        if (node.children().isEmpty()) {
            sb.append("]\n");
        } else {
            sb.append("\n");
            for (int i = 0; i < node.children().size(); i++) {
                sb.append(childIndent).append("  ").append(nodeToJson(node.children().get(i), childIndent + "  "));
                if (i < node.children().size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append(childIndent).append("]\n");
        }

        sb.append(indent).append("}");
        return sb.toString();
    }

    private String jsonString(Object obj) {
        if (obj == null) return "null";
        return "\"" + obj.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * Creates a builder for constructing MCTS/LATS trees.
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
        private boolean latsMode = false;
        private LatsValueFunction<DecisionTree> valueFunction;
        private LatsReflector reflector;
        private double failureThreshold = 0.3;
        private int reflectionEveryN = 5;

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
         * Enables LATS mode with the given value function.
         */
        public Builder latsMode(LatsValueFunction<DecisionTree> valueFunction) {
            this.latsMode = true;
            this.valueFunction = valueFunction;
            return this;
        }

        /**
         * Sets the LATS reflector.
         */
        public Builder reflector(LatsReflector reflector) {
            this.reflector = reflector;
            return this;
        }

        /**
         * Sets the failure threshold for LATS reflection.
         */
        public Builder failureThreshold(double failureThreshold) {
            this.failureThreshold = failureThreshold;
            return this;
        }

        /**
         * Sets how often to generate reflections (every N iterations).
         */
        public Builder reflectionEveryN(int n) {
            this.reflectionEveryN = n;
            return this;
        }

        /**
         * Builds the MCTS/LATS tree.
         *
         * @return a new MctsTree
         * @throws IllegalStateException if required fields are missing
         */
        public MctsTree build() {
            Objects.requireNonNull(rootState, "rootState");
            Objects.requireNonNull(rng, "rng");
            Objects.requireNonNull(rewardFunction, "rewardFunction");

            List<MctsAction> actions = MctsAction.singleTreeActions();
            MctsNode root;

            if (latsMode) {
                root = new LatsNode(rootState, actions);
            } else {
                root = new MctsNode(null, null, rootState, actions);
            }

            return new MctsTree(root, rng, k, simulationDepth, explorationConstant,
                    rewardFunction, latsMode, valueFunction, reflector,
                    failureThreshold, reflectionEveryN);
        }
    }
}
