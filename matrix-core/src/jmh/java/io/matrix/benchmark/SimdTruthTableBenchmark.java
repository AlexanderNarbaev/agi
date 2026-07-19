package io.matrix.benchmark;

import io.matrix.neuron.BatchEvaluator;
import io.matrix.neuron.SimdTruthTableEval;
import io.matrix.neuron.TruthTable;
import org.openjdk.jmh.annotations.*;

import java.util.BitSet;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark: SIMD batch evaluation (via {@link SimdTruthTableEval})
 * versus the scalar batch helper ({@link BatchEvaluator}).
 *
 * <p>Run with: {@code ./gradlew :matrix-core:jmh -PjmhBenchmark=SimdTruthTableBenchmark}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class SimdTruthTableBenchmark {

    private TruthTable table;
    private int[] inputs64;

    @Setup(Level.Trial)
    public void setup() {
        BitSet bits = new BitSet(64);
        Random rng = new Random(42L);
        for (int i = 0; i < 64; i++) if (rng.nextBoolean()) bits.set(i);
        table = TruthTable.of(6, bits);

        inputs64 = new int[64];
        for (int i = 0; i < 64; i++) inputs64[i] = i;
    }

    @Benchmark
    public long simdEvaluate() {
        return SimdTruthTableEval.evaluateAll64(table, inputs64);
    }

    @Benchmark
    public long scalarBatchEvaluate() {
        return BatchEvaluator.evaluateAll64(table, inputs64);
    }
}
