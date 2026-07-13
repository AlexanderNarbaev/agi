package io.matrix.benchmark;

import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.TruthTable;
import io.matrix.neuron.WeightVector;
import io.matrix.rag.BooleanIndex;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.BitSet;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for all critical performance paths in the MATRIX project.
 *
 * <p>Covers:
 * <ul>
 *   <li>TruthTable.evaluate() latency (optimized vs baseline)</li>
 *   <li>DecisionTree.evaluate() latency (recursive vs flattened)</li>
 *   <li>BooleanIndex.search() latency (sequential vs parallel)</li>
 *   <li>Hamming distance computation</li>
 *   <li>Memory allocation profiling</li>
 * </ul>
 *
 * <p>Run: {@code ./gradlew :matrix-core:jmh -PjmhBenchmark=PerformanceBenchmark}
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(2)
public class PerformanceBenchmark {

    // ─── TruthTable benchmarks ───

    @Param({"4", "8", "12", "16"})
    public int k;

    private TruthTable table;
    private TruthTable weightedTable;
    private int intInput;
    private long[] longInput;
    private BitSet bitSetInput;
    private Random rng;

    @Setup
    public void setup() {
        rng = new Random(42);
        table = TruthTable.random(k, rng);
        weightedTable = TruthTable.random(k, rng, WeightVector.uniform(k));
        intInput = rng.nextInt(1 << k);
        longInput = new long[]{intInput};
        bitSetInput = new BitSet(k);
        for (int i = 0; i < k; i++) {
            if ((intInput & (1 << i)) != 0) {
                bitSetInput.set(i);
            }
        }
    }

    @Benchmark
    public boolean truthTableEvaluateInt() {
        return table.evaluate(intInput);
    }

    @Benchmark
    public boolean truthTableEvaluateLong() {
        return table.evaluate(longInput);
    }

    @Benchmark
    public boolean truthTableEvaluateBitSet() {
        return table.evaluate(bitSetInput);
    }

    @Benchmark
    public boolean truthTableEvaluateWeighted() {
        return weightedTable.evaluate(intInput);
    }

    // ─── DecisionTree benchmarks ───

    @State(Scope.Thread)
    public static class DecisionTreeState {
        @Param({"4", "8", "12"})
        public int treeK;

        private DecisionTree tree;
        private int[] flatTree;
        private BitSet bitSetInput;
        private int intInput;
        private BitSet[] batchInputs;
        private int[] intBatchInputs;
        private Random rng;

        @Setup
        public void setup() {
            rng = new Random(42);
            tree = DecisionTree.random(treeK, treeK, rng);
            flatTree = tree.flatten();

            intInput = rng.nextInt(1 << treeK);
            bitSetInput = new BitSet(treeK);
            for (int i = 0; i < treeK; i++) {
                if ((intInput & (1 << i)) != 0) {
                    bitSetInput.set(i);
                }
            }

            // Batch of 100 inputs for batch benchmarks
            batchInputs = new BitSet[100];
            intBatchInputs = new int[100];
            for (int i = 0; i < 100; i++) {
                int val = rng.nextInt(1 << treeK);
                intBatchInputs[i] = val;
                BitSet bs = new BitSet(treeK);
                for (int b = 0; b < treeK; b++) {
                    if ((val & (1 << b)) != 0) {
                        bs.set(b);
                    }
                }
                batchInputs[i] = bs;
            }
        }
    }

    @Benchmark
    public boolean decisionTreeEvaluateRecursive(DecisionTreeState state) {
        return state.tree.evaluate(state.bitSetInput);
    }

    @Benchmark
    public boolean decisionTreeEvaluateFlat(DecisionTreeState state) {
        return DecisionTree.evaluateFlat(state.flatTree, state.bitSetInput);
    }

    @Benchmark
    public boolean decisionTreeEvaluateFlatInt(DecisionTreeState state) {
        return DecisionTree.evaluateFlatInt(state.flatTree, state.intInput);
    }

    @Benchmark
    public boolean[] decisionTreeEvaluateBatch(DecisionTreeState state) {
        return DecisionTree.evaluateFlatBatch(state.flatTree, state.batchInputs);
    }

    @Benchmark
    public boolean[] decisionTreeEvaluateIntBatch(DecisionTreeState state) {
        return DecisionTree.evaluateFlatIntBatch(state.flatTree, state.intBatchInputs);
    }

    // ─── BooleanIndex benchmarks ───

    @State(Scope.Benchmark)
    public static class BooleanIndexState {
        @Param({"100", "1000", "10000"})
        public int indexSize;

        private BooleanIndex index;
        private long[] query;
        private Random rng;

        @Setup
        public void setup() {
            rng = new Random(42);
            index = BooleanIndex.builder().dimensions(64).build();
            for (int i = 0; i < indexSize; i++) {
                index.add("vec-" + i, new long[]{rng.nextLong()});
            }
            query = new long[]{rng.nextLong()};
        }
    }

    @Benchmark
    public Object booleanIndexSearch(BooleanIndexState state) {
        return state.index.search(state.query, 10);
    }

    // ─── Hamming distance benchmarks ───

    @State(Scope.Thread)
    public static class HammingState {
        private long[] a64;
        private long[] b64;
        private long[] a128;
        private long[] b128;

        @Setup
        public void setup() {
            Random rng = new Random(42);
            a64 = new long[]{rng.nextLong()};
            b64 = new long[]{rng.nextLong()};
            a128 = new long[]{rng.nextLong(), rng.nextLong()};
            b128 = new long[]{rng.nextLong(), rng.nextLong()};
        }
    }

    @Benchmark
    public int hammingDistance64(HammingState state) {
        return BooleanIndex.hammingDistance(state.a64, state.b64);
    }

    @Benchmark
    public int hammingDistance128(HammingState state) {
        return BooleanIndex.hammingDistance(state.a128, state.b128);
    }

    // ─── Memory allocation profiling ───

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Warmup(iterations = 3)
    @Measurement(iterations = 5)
    public TruthTable truthTableAllocation() {
        return TruthTable.random(8, new Random(42));
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Warmup(iterations = 3)
    @Measurement(iterations = 5)
    public DecisionTree decisionTreeAllocation() {
        return DecisionTree.random(8, 8, new Random(42));
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Warmup(iterations = 3)
    @Measurement(iterations = 5)
    public int[] flattenAllocation(DecisionTreeState state) {
        return state.tree.flatten();
    }
}
