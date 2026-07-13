package io.matrix.mcts;

import io.matrix.evolution.TreeWalker;
import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.TruthTable;

import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.ToDoubleFunction;

/**
 * Value function interface for LATS node evaluation.
 *
 * <p>In the LATS framework (arXiv:2310.04406), the value function replaces
 * random simulation playouts with intelligent evaluation. This interface
 * abstracts the evaluation strategy, allowing pluggable implementations:
 * <ul>
 *   <li>{@link BrainValueFunction} — uses the boolean neural network (HierarchicalBrain)</li>
 *   <li>{@link HeuristicValueFunction} — uses handcrafted heuristics (for testing)</li>
 *   <li>{@link CompositeValueFunction} — combines multiple evaluators with weights</li>
 * </ul>
 *
 * @param <T> the state type (typically {@link DecisionTree})
 */
@FunctionalInterface
public interface LatsValueFunction<T> {

    /**
     * Evaluates the quality of a state.
     *
     * @param state the state to evaluate
     * @return a score in [0.0, 1.0] where higher is better
     */
    double evaluate(T state);

    /**
     * Evaluates a batch of states.
     *
     * <p>Default implementation evaluates sequentially. Implementations
     * may override for parallel evaluation.
     *
     * @param states the states to evaluate
     * @return scores in the same order as states
     */
    default double[] evaluateBatch(List<T> states) {
        double[] scores = new double[states.size()];
        for (int i = 0; i < states.size(); i++) {
            scores[i] = evaluate(states.get(i));
        }
        return scores;
    }

    /**
     * Returns a human-readable name for this value function.
     */
    default String name() {
        return getClass().getSimpleName();
    }

    // ── Implementations ──

    /**
     * Value function using the existing reward function.
     *
     * <p>Wraps a {@link ToDoubleFunction} as a LatsValueFunction.
     * Useful for integrating existing reward functions into LATS.
     */
    final class RewardFunctionAdapter implements LatsValueFunction<DecisionTree> {

        private final ToDoubleFunction<DecisionTree> rewardFunction;

        public RewardFunctionAdapter(ToDoubleFunction<DecisionTree> rewardFunction) {
            this.rewardFunction = Objects.requireNonNull(rewardFunction, "rewardFunction");
        }

        @Override
        public double evaluate(DecisionTree state) {
            return rewardFunction.applyAsDouble(state);
        }

        @Override
        public String name() {
            return "RewardFunctionAdapter";
        }
    }

    /**
     * Heuristic value function for testing and bootstrapping.
     *
     * <p>Evaluates trees based on structural properties:
     * <ul>
     *   <li>Balanced trees score higher</li>
     *   <li>Trees with moderate depth score higher (not too shallow, not too deep)</li>
     *   <li>Trees that produce diverse outputs score higher</li>
     * </ul>
     */
    final class HeuristicValueFunction implements LatsValueFunction<DecisionTree> {

        private final int k;
        private final Random rng;

        /**
         * Creates a heuristic value function.
         *
         * @param k   number of inputs for truth table evaluation
         * @param rng random number generator for diversity sampling
         */
        public HeuristicValueFunction(int k, Random rng) {
            this.k = k;
            this.rng = Objects.requireNonNull(rng, "rng");
        }

        @Override
        public double evaluate(DecisionTree state) {
            double structuralScore = structuralScore(state);
            double diversityScore = diversityScore(state);
            double complexityPenalty = complexityPenalty(state);

            // Weighted combination
            return Math.max(0.0, Math.min(1.0,
                    0.4 * structuralScore + 0.4 * diversityScore - 0.2 * complexityPenalty));
        }

        private double structuralScore(DecisionTree tree) {
            int depth = tree.depth();
            int nodes = TreeWalker.totalNodes(tree);

            // Prefer moderate depth (2-5 is ideal for k=4)
            double depthScore = 1.0 - Math.abs(depth - 3.0) / 5.0;

            // Prefer trees with some complexity (not just a leaf)
            double nodeScore = Math.min(1.0, nodes / 10.0);

            return (depthScore + nodeScore) / 2.0;
        }

        private double diversityScore(DecisionTree tree) {
            try {
                TruthTable tt = tree.toTruthTable(k);
                int trueCount = tt.table().cardinality();
                int total = 1 << k;

                // Prefer balanced outputs (close to 50% true)
                double ratio = (double) trueCount / total;
                return 1.0 - 2.0 * Math.abs(ratio - 0.5);
            } catch (Exception e) {
                return 0.0;
            }
        }

        private double complexityPenalty(DecisionTree tree) {
            int nodes = TreeWalker.totalNodes(tree);
            // Penalize very large trees
            return nodes > 15 ? Math.min(1.0, (nodes - 15) / 20.0) : 0.0;
        }

        @Override
        public String name() {
            return "HeuristicValueFunction(k=" + k + ")";
        }
    }

    /**
     * Composite value function that combines multiple evaluators.
     *
     * <p>Scores are computed as a weighted average of component scores.
     */
    final class CompositeValueFunction implements LatsValueFunction<DecisionTree> {

        private final List<LatsValueFunction<DecisionTree>> functions;
        private final double[] weights;

        /**
         * Creates a composite value function.
         *
         * @param functions the component functions
         * @param weights   the weights for each function (must sum to 1.0)
         */
        public CompositeValueFunction(List<LatsValueFunction<DecisionTree>> functions,
                                      double[] weights) {
            this.functions = List.copyOf(Objects.requireNonNull(functions, "functions"));
            this.weights = Objects.requireNonNull(weights, "weights");

            if (functions.isEmpty()) {
                throw new IllegalArgumentException("At least one function required");
            }
            if (functions.size() != weights.length) {
                throw new IllegalArgumentException("Functions and weights must have same size");
            }

            double sum = 0;
            for (double w : weights) sum += w;
            if (Math.abs(sum - 1.0) > 1e-6) {
                throw new IllegalArgumentException("Weights must sum to 1.0, got: " + sum);
            }
        }

        @Override
        public double evaluate(DecisionTree state) {
            double score = 0.0;
            for (int i = 0; i < functions.size(); i++) {
                score += weights[i] * functions.get(i).evaluate(state);
            }
            return Math.max(0.0, Math.min(1.0, score));
        }

        @Override
        public double[] evaluateBatch(List<DecisionTree> states) {
            // Evaluate each component in batch, then combine
            double[][] componentScores = new double[functions.size()][];
            for (int i = 0; i < functions.size(); i++) {
                componentScores[i] = functions.get(i).evaluateBatch(states);
            }

            double[] result = new double[states.size()];
            for (int s = 0; s < states.size(); s++) {
                double score = 0.0;
                for (int f = 0; f < functions.size(); f++) {
                    score += weights[f] * componentScores[f][s];
                }
                result[s] = Math.max(0.0, Math.min(1.0, score));
            }
            return result;
        }

        @Override
        public String name() {
            return "Composite[" + functions.size() + " functions]";
        }
    }

    // ── Factory methods ──

    /**
     * Creates a value function from an existing reward function.
     */
    static LatsValueFunction<DecisionTree> fromRewardFunction(
            ToDoubleFunction<DecisionTree> rewardFunction) {
        return new RewardFunctionAdapter(rewardFunction);
    }

    /**
     * Creates a heuristic value function.
     */
    static LatsValueFunction<DecisionTree> heuristic(int k, Random rng) {
        return new HeuristicValueFunction(k, rng);
    }

    /**
     * Creates a composite of multiple value functions with equal weights.
     */
    static LatsValueFunction<DecisionTree> composite(
            List<LatsValueFunction<DecisionTree>> functions) {
        double equalWeight = 1.0 / functions.size();
        double[] weights = new double[functions.size()];
        java.util.Arrays.fill(weights, equalWeight);
        return new CompositeValueFunction(functions, weights);
    }
}
