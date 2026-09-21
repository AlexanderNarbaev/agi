package io.matrix.benchmark;

import io.matrix.cluster.NeuronId;
import io.matrix.cluster.Signal;
import io.matrix.cluster.SignalBuffer;
import io.matrix.evolution.BooleanMinimizer;
import io.matrix.neuron.TruthTable;

import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for the Phase-3 compression pathways:
 * <ul>
 *   <li>neuron signal batch pack/unpack through the {@link SignalBuffer}</li>
 *   <li>{@link BooleanMinimizer} Quine-McCluskey compression</li>
 * </ul>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class CompressionBenchmark {

    @Param({"100", "1000", "10000"})
    public int signalCount;

    private List<Signal> signals;
    private TruthTable compressibleTable;

    @Setup
    public void setup() {
        NeuronId src = NeuronId.create();
        NeuronId tgt = NeuronId.create();
        signals = new ArrayList<>(signalCount);
        for (int i = 0; i < signalCount; i++) {
            signals.add(new Signal(src, tgt, (i & 1) == 0));
        }

        // bit0 AND bit1 with bits 2..7 as don't-cares -> 1 prime implicant.
        int k = 8;
        int size = 1 << k;
        BitSet table = new BitSet(size);
        for (int i = 0; i < size; i++) {
            if ((i & 0b11) == 0b11) {
                table.set(i);
            }
        }
        compressibleTable = TruthTable.of(k, table);
    }

    /**
     * Packs a batch of signals into a {@link SignalBuffer} and unpacks them
     * back into a list, measuring the batch round-trip cost.
     */
    @Benchmark
    public List<Signal> neoronBatchPackUnpack() {
        SignalBuffer buffer = new SignalBuffer(signalCount);
        for (Signal s : signals) {
            buffer.push(s);
        }
        List<Signal> unpacked = new ArrayList<>(signalCount);
        buffer.drainTo(unpacked);
        return unpacked;
    }

    /**
     * Minimises a compressible k=8 truth table with Quine-McCluskey.
     */
    @Benchmark
    public List<int[]> booleanMinimize() {
        return BooleanMinimizer.minimize(compressibleTable.table(),
                compressibleTable.k());
    }
}
