package io.matrix.benchmark;

import io.matrix.neuron.BatchEvaluator;
import io.matrix.neuron.TruthTable;
import org.openjdk.jmh.annotations.*;

import java.util.BitSet;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for {@link BatchEvaluator} vs single-call {@link TruthTable#evaluate(int)}.
 *
 * <p>Run with: {@code ./gradlew :matrix-core:jmh -PjmhBenchmark=BatchEvaluatorBenchmark}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class BatchEvaluatorBenchmark {

    private TruthTable tt;
    private int[] inputs64;
    private int[] inputs32;

    @Setup(Level.Trial)
    public void setup() {
        BitSet bits = new BitSet(64);
        Random rng = new Random(42L);
        for (int i = 0; i < 64; i++) if (rng.nextBoolean()) bits.set(i);
        tt = TruthTable.of(6, bits);

        inputs64 = new int[64];
        for (int i = 0; i < 64; i++) inputs64[i] = i;

        inputs32 = new int[32];
        for (int i = 0; i < 32; i++) inputs32[i] = i;
    }

    @Benchmark
    public long evaluateAll64() {
        return BatchEvaluator.evaluateAll64(tt, inputs64);
    }

    @Benchmark
    public int evaluateAll32() {
        return BatchEvaluator.evaluateAll32(tt, inputs32);
    }

    /** Baseline: equivalent per-call loop (no batch helper). */
    @Benchmark
    public long perElementLoop64() {
        long r = 0L;
        for (int i = 0; i < 64; i++) {
            if (tt.evaluate(i)) r |= (1L << i);
        }
        return r;
    }
}
