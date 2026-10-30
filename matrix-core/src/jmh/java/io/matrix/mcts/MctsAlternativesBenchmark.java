package io.matrix.mcts;

import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.DecisionTree.Leaf;
import io.matrix.neuron.DecisionTree.Split;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import org.openjdk.jmh.annotations.*;

/**
 * RECON-W39 — the production MCTS engine against its ablations.
 *
 * <p>The mission asked for the MCTS algorithm to be benchmarked against
 * alternatives. The honest observation is that BFS, DFS and A* are **not**
 * alternatives here: the production engine does not search a graph. It
 * performs a bounded stochastic search over **decision-tree mutations**
 * ({@code MctsAction.apply}), and that space has no enumerative structure an
 * exhaustive or best-first method could exploit. Benchmarking against BFS on
 * a different problem would compare nothing.
 *
 * <p>What the comparison therefore measures is the value of the parts that
 * can be ablated on the same problem and with the same budget:
 *
 * <ul>
 *   <li>{@code mctsUct} — the production search: UCB1 selection, random
 *       simulation, backpropagation over the whole ancestor chain, and the
 *       final arm chosen by VISIT COUNT.</li>
 *   <li>{@code randomMutations} — the same number of mutations with no
 *       selection rule at all: the ablation that says what UCB1 buys.</li>
 *   <li>{@code greedyOneStep} — the cheapest possible search: apply every
 *       action once and keep the best.</li>
 * </ul>
 *
 * <p>Every method returns the reward it achieved, so the benchmark reports
 * time per method and the accompanying test
 * ({@code MctsAlternativesComparisonTest}) records the reward each method
 * reaches. Nothing here claims that MCTS is optimal — it is a bounded
 * stochastic search, and its convergence bound is recorded as UNVERIFIED.
 *
 * <p>Run with:
 * {@code ./gradlew :matrix-core:jmh -PjmhBenchmark=MctsAlternativesBenchmark}
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class MctsAlternativesBenchmark {

    /** Inputs per tree, as the production PlanningStage uses. */
    private static final int K = 4;

    /** The iteration budget the PRO tier uses (PlanningStage). */
    private static final int ITERATIONS = 20;

    /** Simulation depth, as the production PlanningStage uses. */
    private static final int SIMULATION_DEPTH = 4;

    /**
     * The reward: the number of true leaves. It is deterministic, so two
     * methods that reach the same tree are comparable, and it is bounded by
     * the tree size.
     */
    private static final Reward REWARD = MctsAlternativesBenchmark::countTrueLeaves;

    private DecisionTree rootState;
    private Random rng;

    @Setup(Level.Trial)
    public void setup() {
        // A fixed seed: the benchmark measures the algorithm, not the luck of
        // the draw, and a run is reproducible.
        rng = new Random(42L);
        rootState = DecisionTree.random(K, K, rng);
    }

    // ── the methods ───────────────────────────────────────────────────────────

    /** The production engine, unchanged. */
    @Benchmark
    public double mctsUct() {
        MctsTree tree = MctsTree.builder()
                .rootState(rootState)
                .rng(rng)
                .k(K)
                .simulationDepth(SIMULATION_DEPTH)
                .explorationConstant(MctsNode.EXPLORATION_CONSTANT)
                .rewardFunction(t -> REWARD.of(t))
                .build();
        tree.runSearch(ITERATIONS);
        MctsNode best = tree.root().mostVisitedChild();
        return best == null ? REWARD.of(rootState) : REWARD.of(best.state());
    }

    /**
     * The ablation: the same budget of mutations, but no selection rule —
     * every mutation is applied to the root and the best is kept.
     */
    @Benchmark
    public double randomMutations() {
        DecisionTree best = rootState;
        double bestReward = REWARD.of(rootState);
        for (MctsAction action : MctsAction.singleTreeActions()) {
            DecisionTree mutated = action.apply(rootState, rng, K);
            double reward = REWARD.of(mutated);
            if (reward > bestReward) {
                bestReward = reward;
                best = mutated;
            }
        }
        return bestReward;
    }

    /** The cheapest search: one greedy step over every action. */
    @Benchmark
    public double greedyOneStep() {
        double bestReward = REWARD.of(rootState);
        for (MctsAction action : MctsAction.singleTreeActions()) {
            double reward = REWARD.of(action.apply(rootState, rng, K));
            if (reward > bestReward) {
                bestReward = reward;
            }
        }
        return bestReward;
    }

    /** The reward function. A local interface, because ToDoubleFunction's
     *  method is applyAsDouble and reads badly at every call site. */
    @FunctionalInterface
    interface Reward {
        double of(DecisionTree tree);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static double countTrueLeaves(DecisionTree tree) {
        if (tree instanceof Leaf leaf) {
            return leaf.value() ? 1.0 : 0.0;
        }
        Split split = (Split) tree;
        return countTrueLeaves(split.leftChild()) + countTrueLeaves(split.rightChild());
    }

    /** Exposed for the companion test, which records the rewards reached. */
    public static List<MctsAction> singleTreeActions() {
        return MctsAction.singleTreeActions();
    }
}
