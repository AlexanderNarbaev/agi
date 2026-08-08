package io.matrix.bir;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for BIR evaluation performance.
 * Measures ns/op for TT, CLAUSESET, and BDD forms.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class BirEvaluateBenchmark {

    private TtForm tt;
    private ClauseSetForm cs;
    private BddForm bdd;
    private long[] input;

    @Setup
    public void setup() {
        int k = 8;
        // TT: AND gate
        long[] table = new long[(1 << k) / 64 + 1];
        for (int i = 0; i < (1 << k); i++) {
            if (Integer.bitCount(i) >= 4) table[i >>> 6] |= (1L << (i & 63));
        }
        tt = new TtForm(k, table, "bench", 1.0);

        // CLAUSESET: single clause x0 AND x1
        long[] pos = new long[1]; pos[0] = 0b11L;
        long[] neg = new long[1];
        cs = new ClauseSetForm(k, java.util.List.of(new ClauseSetForm.Clause(pos, neg)), "bench", 1.0);

        // BDD: simple if-then
        var builder = new BddForm.Builder();
        builder.mk(0, 0, 1);
        bdd = builder.build(k, "bench");

        input = new long[]{0b11111111L};
    }

    @Benchmark
    public void ttEval(Blackhole bh) {
        long[] out = new long[1];
        tt.eval(input, out);
        bh.consume(out[0]);
    }

    @Benchmark
    public void clauseSetEval(Blackhole bh) {
        long[] out = new long[1];
        cs.eval(input, out);
        bh.consume(out[0]);
    }

    @Benchmark
    public void bddEval(Blackhole bh) {
        long[] out = new long[1];
        bdd.eval(input, out);
        bh.consume(out[0]);
    }

    @Benchmark
    public void runtimeEval(Blackhole bh) {
        long[] out = BooleanRuntime.evaluate(tt, input);
        bh.consume(out[0]);
    }
}
