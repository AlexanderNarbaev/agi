package io.matrix.benchmark;

import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.DecisionTreeBatch;
import org.openjdk.jmh.annotations.*;

import java.util.BitSet;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for {@link DecisionTreeBatch}.
 *
 * <p>Run with: {@code ./gradlew :matrix-core:jmh -PjmhBenchmark=DecisionTreeBatchBenchmark}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class DecisionTreeBatchBenchmark {

    private DecisionTree tree;
    private BitSet[] inputs64;

    @Setup(Level.Trial)
    public void setup() {
        Random rng = new Random(42L);
        tree = DecisionTree.random(6, 6, rng);
        inputs64 = new BitSet[64];
        for (int i = 0; i < 64; i++) {
            BitSet bs = new BitSet(6);
            int v = i;
            for (int b = 0; b < 6; b++) {
                if ((v & 1) != 0) bs.set(b);
                v >>= 1;
            }
            inputs64[i] = bs;
        }
    }

    @Benchmark
    public long evaluateAll64() {
        return DecisionTreeBatch.evaluateAll64(tree, inputs64);
    }

    /** Baseline: per-element loop without the helper. */
    @Benchmark
    public long perElementLoop64() {
        long r = 0L;
        for (int i = 0; i < 64; i++) {
            if (tree.evaluate(inputs64[i])) r |= (1L << i);
        }
        return r;
    }
}
