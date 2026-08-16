package io.matrix.benchmark;

import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.TruthTable;
import org.openjdk.jmh.annotations.*;

import java.util.BitSet;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for the **core neuron evaluation hot path** —
 * every sensor input flows through these primitives.
 *
 * <p>Compares:
 * <ul>
 *   <li>TruthTable.evaluate(int) — direct lookup of 1 input
 *   <li>TruthTable.evaluate(BitSet) — same, via BitSet extraction
 *   <li>DecisionTree.evaluate(BitSet) — full tree walk
 *   <li>Combined pipeline: TT + DT chain (the realistic agent decision)
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :matrix-core:jmh -PjmhBenchmark=NeuronHotPathBenchmark}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class NeuronHotPathBenchmark {

    private TruthTable table6;
    private TruthTable table10;
    private TruthTable table20;
    private DecisionTree tree6;
    private DecisionTree tree20;
    private BitSet input6;
    private BitSet input20;
    private int inputInt6;

    @Setup(Level.Trial)
    public void setup() {
        Random rng = new Random(42L);
        // TruthTable with k=6 (single-long representation)
        java.util.BitSet tt6 = new java.util.BitSet(64);
        for (int i = 0; i < 64; i++) if (rng.nextBoolean()) tt6.set(i);
        table6 = TruthTable.of(6, tt6);

        // TruthTable with k=10 (multi-long)
        java.util.BitSet tt10 = new java.util.BitSet(1 << 10);
        for (int i = 0; i < (1 << 10); i++) if (rng.nextBoolean()) tt10.set(i);
        table10 = TruthTable.of(10, tt10);

        // TruthTable with k=20 (large)
        java.util.BitSet tt20 = new java.util.BitSet(1 << 20);
        for (int i = 0; i < (1 << 20); i++) if (rng.nextBoolean()) tt20.set(i);
        table20 = TruthTable.of(20, tt20);

        // DecisionTree (6-bit tree, depth 6)
        tree6 = DecisionTree.random(6, 6, rng);
        // DecisionTree (20-bit tree, depth 6)
        tree20 = DecisionTree.random(20, 6, rng);

        input6 = new BitSet(6);
        for (int b = 0; b < 6; b++) if (rng.nextBoolean()) input6.set(b);
        input6.set(0); input6.set(3);

        input20 = new BitSet(20);
        for (int b = 0; b < 20; b++) if (rng.nextBoolean()) input20.set(b);

        // Pack to int for direct API
        int v = 0;
        for (int b = 0; b < 6; b++) if (input6.get(b)) v |= (1 << b);
        inputInt6 = v;
    }

    @Benchmark
    public boolean truthTable_k6_int() {
        return table6.evaluate(inputInt6);
    }

    @Benchmark
    public boolean truthTable_k6_bitset() {
        return table6.evaluate(input6);
    }

    @Benchmark
    public boolean truthTable_k10() {
        return table10.evaluate(input6);
    }

    @Benchmark
    public boolean truthTable_k20() {
        return table20.evaluate(input20);
    }

    @Benchmark
    public boolean decisionTree_k6() {
        return tree6.evaluate(input6);
    }

    @Benchmark
    public boolean decisionTree_k20() {
        return tree20.evaluate(input20);
    }
}