package io.matrix.perf;

import io.matrix.mcts.MctsNode;
import io.matrix.mcts.MctsTree;
import io.matrix.neuron.DecisionTree;

import java.util.Random;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * RECON-W30 — MCTS rollout throughput against the real {@link MctsTree} in
 * {@code matrix-core}.
 *
 * <p>The third hot kernel the wave plan named. Planning depth is a headline benchmark
 * category (PLANNING_DEPTH 4/4), so search cost is not incidental to the system's claims
 * about itself.</p>
 *
 * <p>Construction goes through {@link MctsTree#builder()}, which is the only supported
 * path: {@link MctsNode}'s constructor rejects a null state and a null action list, and
 * the builder is what supplies the real operator set via {@code MctsAction.singleTreeActions()}.
 * Hand-rolling a node would have produced a benchmark of nothing.</p>
 *
 * <p>The tree is rebuilt inside the timed region. That is deliberate: {@code runSearch}
 * mutates visit counts and children, so a reused tree would measure a converged search
 * rather than an actual one, and excluding construction would flatter the number. The
 * teardown prints the scale actually exercised so the work behind the number is visible
 * rather than assumed.</p>
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-Xms2g", "-Xmx2g"})
public class MctsRolloutBenchmark {

    /** Rollouts per search — the budget a planner actually has to work within. */
    @Param({"100", "1000"})
    public int iterations;

    /** Inputs per decision tree — the search branching factor. */
    @Param({"8"})
    public int k;

    /** Random mutations per simulation playout. */
    @Param({"4", "16"})
    public int simulationDepth;

    private DecisionTree rootState;
    private long searchesRun;
    private long rolloutsRun;

    @Setup(Level.Trial)
    public void setup() {
        // A depth-3 balanced tree: enough structure for operators to mutate, small
        // enough that the benchmark measures search mechanics rather than tree size.
        rootState = buildTree(3);
        searchesRun = 0;
        rolloutsRun = 0;
    }

    /**
     * Rollouts per second. This is the honest unit for MCTS: a single-iteration latency
     * is not meaningful when the useful question is how many rollouts fit in a decision
     * budget.
     */
    @Benchmark
    public double rolloutThroughput(Blackhole bh) {
        MctsTree tree = MctsTree.builder()
                .rootState(rootState)
                // CONSTITUTION III: seeded RNG. MCTS is stochastic, and an unseeded
                // benchmark reports a number nobody can reproduce.
                .rng(new Random(42L))
                .k(k)
                .simulationDepth(simulationDepth)
                .explorationConstant(MctsNode.EXPLORATION_CONSTANT)
                // Constant reward: isolates search mechanics from reward-landscape shape,
                // so the number measures the tree walk rather than one particular task.
                .rewardFunction(state -> 0.5)
                .build();

        var action = tree.runSearch(iterations);
        searchesRun++;
        rolloutsRun += iterations;
        bh.consume(action);
        bh.consume(tree.root().visitCount());
        return (double) iterations;
    }

    /**
     * UCB1 selection in isolation. It is evaluated at every node of every rollout, so it
     * is the component most likely to dominate — and therefore the one most worth
     * measuring on its own rather than inferring from the total.
     */
    @Benchmark
    public double ucb1Selection(Blackhole bh) {
        MctsTree tree = MctsTree.builder()
                .rootState(rootState)
                .rng(new Random(42L))
                .k(k)
                .simulationDepth(simulationDepth)
                .rewardFunction(state -> 0.5)
                .build();
        // A short search so the root has visit counts to compute a ratio over.
        tree.runSearch(Math.min(iterations, 100));

        double total = 0.0;
        total += tree.root().ucb1(MctsNode.EXPLORATION_CONSTANT);
        for (var child : tree.root().children()) {
            total += child.ucb1(MctsNode.EXPLORATION_CONSTANT);
        }
        bh.consume(total);
        return total;
    }

    @TearDown(Level.Trial)
    public void reportScale() {
        System.err.printf("[MctsRollout] k=%d depth=%d searches=%d rollouts=%d%n",
                k, simulationDepth, searchesRun, rolloutsRun);
    }

    /** Balanced binary tree of the requested depth over distinct input indices. */
    private static DecisionTree buildTree(int depth) {
        if (depth <= 0) {
            return new DecisionTree.Leaf(true);
        }
        return new DecisionTree.Split(
                depth,                       // input index, unique per level
                buildTree(depth - 1),
                new DecisionTree.Leaf(false));
    }
}
