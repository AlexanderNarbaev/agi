package io.matrix.bir;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for BIR evaluation performance.
 * Measures ns/op for TT, CLAUSESET, and BDD forms.
 *
 * <p>SPEC-002 acceptance criterion A: BIR execution must stay within 10% of
 * the legacy {@code TruthTable} path — {@code legacyTruthTableEval} is the
 * baseline, {@code ttEval}/{@code runtimeEval} the BIR path over the same
 * function. Publish both in the JMH report.
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
    private io.matrix.neuron.TruthTable legacyTt;
    private long[] input;
    private java.util.BitSet legacyInput;

    @Setup
    public void setup() {
        int k = 8;
        // TT: majority-of-4 threshold
        long[] table = new long[(1 << k) / 64 + 1];
        java.util.BitSet bits = new java.util.BitSet(1 << k);
        for (int i = 0; i < (1 << k); i++) {
            if (Integer.bitCount(i) >= 4) {
                table[i >>> 6] |= (1L << (i & 63));
                bits.set(i);
            }
        }
        tt = new TtForm(k, table, "bench", 1.0);
        legacyTt = io.matrix.neuron.TruthTable.of(k, bits);

        // CLAUSESET: single clause x0 AND x1
        long[] pos = new long[1]; pos[0] = 0b11L;
        long[] neg = new long[1];
        cs = new ClauseSetForm(k, java.util.List.of(new ClauseSetForm.Clause(pos, neg)), "bench", 1.0);

        // BDD: simple if-then
        var builder = new BddForm.Builder();
        int root = builder.mk(0, 0, 1);
        bdd = builder.build(k, "bench", root);

        input = new long[]{0b11111111L};
        legacyInput = new java.util.BitSet(k);
        legacyInput.set(0, k);
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

    /** Legacy baseline for criterion A (≤10% deviation vs ttEval/runtimeEval). */
    @Benchmark
    public void legacyTruthTableEval(Blackhole bh) {
        bh.consume(legacyTt.evaluate(legacyInput));
    }
}
