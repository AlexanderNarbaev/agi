package io.matrix.benchmark;

import io.matrix.bir.BooleanRuntime;
import io.matrix.bir.TtForm;
import io.matrix.neuron.HdcBinding;
import io.matrix.neuron.HdcEncoding;
import io.matrix.neuron.NeuronLayer;
import io.matrix.tsetlin.TsetlinTrainer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * REP-02 — the workloads of the wave's comparison table, measured in one place.
 *
 * <p><b>The table this class realises.</b> The wave compares representations by workload,
 * not by the speed of one operation. The five directions are:</p>
 * <ul>
 *   <li><b>BIR</b> — the scalar evaluation (the output buffer reused) against the runtime
 *       evaluation (which allocates its own), and the batch evaluation;</li>
 *   <li><b>HDC</b> — XOR, the Hamming distance, the majority bundle, the similarity and the
 *       cleanup;</li>
 *   <li><b>NeuronLayer</b> — the layer's own evaluation (a BitSet slice, an array copy and a
 *       BIR call per neuron) against the copy of the input the caller pays for;</li>
 *   <li><b>Tsetlin</b> — the production fold of a wide vector;</li>
 *   <li><b>byte serialization</b> — the encoding of a vector, its size and the decoding.</li>
 * </ul>
 *
 * <p><b>What this class does not claim.</b> A nanosecond figure of one operation is not the
 * cost of a workload: the result is a <b>ratio</b> between the arms of one comparison, on
 * one machine, under one configuration. The absolute numbers are recorded together with
 * the machine, the JVM and the configuration, and they are not a property of the code.
 *
 * <p><b>The inputs are fixed.</b> The seed is 42, the widths are the production widths
 * (the HDC vector is 1024 bits in 16 longs, the trainer's width is 16), and the setup
 * fails loudly if a production default changes underneath the benchmark.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class RepresentationWorkloadBenchmark {

    /** The seed of every random input. */
    private static final long SEED = 42L;

    /** The width of the boolean function the BIR arms evaluate. */
    private static final int K = 8;

    /** How many evaluations the batch arms perform per operation. */
    private static final int BATCH = 256;

    /** How many vectors the cleanup arm searches. */
    private static final int CLEANUP_ITEMS = 64;

    /** The truth table form. */
    private TtForm truthTable;

    /** The one input of the scalar arms. */
    private long[] input;

    /** The inputs of the batch arm. */
    private long[][] batchInputs;

    /** The outputs of the batch arm. */
    private long[][] batchOutputs;

    /** The HDC vectors. */
    private long[] hdcA;

    /** The HDC vectors. */
    private long[] hdcB;

    /** The bundle's inputs. */
    private long[][] bundleInputs;

    /** The items the cleanup searches. */
    private Map<String, long[]> cleanupCorpus;

    /** The neuron layer. */
    private NeuronLayer layer;

    /** The layer's input. */
    private java.util.BitSet layerInput;

    /** The trainer whose fold is measured. */
    private TsetlinTrainer trainer;

    /** The wide vector the fold is measured on. */
    private long[] wideVector;

    /**
     * Prepares the fixed inputs.
     */
    @Setup
    public void setup() {
        Random rng = new Random(SEED);

        // BIR: a majority-of-four threshold over eight inputs, exactly as the BIR
        // benchmark of the repository builds it.
        long[] table = new long[(1 << K) / 64 + 1];
        for (int value = 0; value < (1 << K); value++) {
            if (Integer.bitCount(value) >= 4) {
                table[value >>> 6] |= 1L << (value & 63);
            }
        }
        truthTable = new TtForm(K, table, "rep-substrate-benchmark", 1.0);

        input = new long[] {0b11111111L};
        batchInputs = new long[BATCH][];
        batchOutputs = new long[BATCH][1];
        for (int index = 0; index < BATCH; index++) {
            batchInputs[index] = new long[] {rng.nextLong() & 0xFFL};
        }

        // HDC: the production width, and vectors that are not identical.
        hdcA = HdcEncoding.random(rng);
        hdcB = HdcEncoding.random(rng);
        bundleInputs = new long[8][];
        for (int index = 0; index < bundleInputs.length; index++) {
            bundleInputs[index] = HdcEncoding.random(rng);
        }
        cleanupCorpus = new LinkedHashMap<>();
        for (int index = 0; index < CLEANUP_ITEMS; index++) {
            cleanupCorpus.put("item-" + index, HdcEncoding.random(rng));
        }

        // NeuronLayer: the production shape — a layer of neurons over a bit input.
        layer = new NeuronLayer(32, K, rng);
        layerInput = new java.util.BitSet(K);
        layerInput.set(0, K);

        // Tsetlin: the production fold, on the widest vector the HDC produces.
        trainer = new TsetlinTrainer(16, 8, 100, new Random(SEED));
        wideVector = HdcEncoding.random(rng);
        if (wideVector.length != HdcEncoding.WORDS) {
            throw new IllegalStateException(
                    "the HDC width changed: the benchmark measures what the production does");
        }
    }

    /**
     * The BIR evaluation with a reused output buffer.
     *
     * @param bh the blackhole
     */
    @Benchmark
    public void birScalarReusedBuffer(Blackhole bh) {
        long[] out = new long[1];
        truthTable.eval(input, out);
        bh.consume(out[0]);
    }

    /**
     * The BIR evaluation through the runtime, which allocates its own output.
     *
     * @param bh the blackhole
     */
    @Benchmark
    public void birScalarRuntimeAllocates(Blackhole bh) {
        bh.consume(BooleanRuntime.evaluate(truthTable, input)[0]);
    }

    /**
     * The batch evaluation of the truth table form.
     *
     * @param bh the blackhole
     */
    @Benchmark
    public void birBatch(Blackhole bh) {
        truthTable.evalBatch(batchInputs, batchOutputs);
        bh.consume(batchOutputs[0][0]);
    }

    /**
     * The HDC XOR.
     *
     * @param bh the blackhole
     */
    @Benchmark
    public void hdcXor(Blackhole bh) {
        bh.consume(HdcEncoding.xor(hdcA, hdcB));
    }

    /**
     * The HDC Hamming distance.
     *
     * @param bh the blackhole
     */
    @Benchmark
    public void hdcHamming(Blackhole bh) {
        bh.consume(HdcEncoding.hamming(hdcA, hdcB));
    }

    /**
     * The HDC similarity.
     *
     * @param bh the blackhole
     */
    @Benchmark
    public void hdcSimilarity(Blackhole bh) {
        bh.consume(HdcEncoding.similarity(hdcA, hdcB));
    }

    /**
     * The majority bundle of eight vectors.
     *
     * @param bh the blackhole
     */
    @Benchmark
    public void hdcBundle(Blackhole bh) {
        bh.consume(HdcEncoding.bundle(bundleInputs));
    }

    /**
     * The cleanup: the nearest item of a small corpus.
     *
     * @param bh the blackhole
     */
    @Benchmark
    public void hdcCleanup(Blackhole bh) {
        bh.consume(HdcBinding.cleanup(hdcA, cleanupCorpus));
    }

    /**
     * The neuron layer's evaluation: a BitSet slice, an array copy and a BIR call per neuron.
     *
     * @param bh the blackhole
     */
    @Benchmark
    public void neuronLayerEvaluate(Blackhole bh) {
        bh.consume(layer.evaluate(layerInput));
    }

    /**
     * The copy of the layer's input that the caller pays for.
     *
     * @param bh the blackhole
     */
    @Benchmark
    public void neuronLayerInputCopy(Blackhole bh) {
        bh.consume(layerInput.get(0, K).toLongArray());
    }

    /**
     * The production fold of the trainer.
     *
     * @param bh the blackhole
     */
    @Benchmark
    public void tsetlinPackWide(Blackhole bh) {
        bh.consume(trainer.packForTest(wideVector));
    }

    /**
     * The encoding of a vector into its textual form.
     *
     * @param bh the blackhole
     */
    @Benchmark
    public void byteSerializationEncode(Blackhole bh) {
        bh.consume(HdcEncoding.toBits(hdcA));
    }

    /**
     * The decoding of a vector from its textual form.
     *
     * @param bh the blackhole
     */
    @Benchmark
    public void byteSerializationDecode(Blackhole bh) {
        bh.consume(HdcEncoding.fromBits(HdcEncoding.toBits(hdcA)));
    }

    /**
     * The size of the serialized form.
     *
     * @param bh the blackhole
     */
    @Benchmark
    public void byteSerializationSize(Blackhole bh) {
        bh.consume(HdcEncoding.toBits(hdcA).length());
    }

    /**
     * The list of the arms, for the report.
     *
     * @return the names of the benchmarks of this class
     */
    public static List<String> arms() {
        return List.of("birScalarReusedBuffer", "birScalarRuntimeAllocates", "birBatch",
                "hdcXor", "hdcHamming", "hdcSimilarity", "hdcBundle", "hdcCleanup",
                "neuronLayerEvaluate", "neuronLayerInputCopy", "tsetlinPackWide",
                "byteSerializationEncode", "byteSerializationDecode", "byteSerializationSize");
    }
}
