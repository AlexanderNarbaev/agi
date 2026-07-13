package io.matrix.benchmark;

import io.matrix.neuron.binary.BinaryLayer;
import io.matrix.neuron.binary.BinaryNetwork;
import io.matrix.neuron.binary.BinaryNeuron;
import io.matrix.neuron.binary.BinaryTrainer;

import org.openjdk.jmh.annotations.*;

import java.util.BitSet;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark comparing binary neural network operations vs floating-point equivalents.
 *
 * <p>Measures:
 * <ul>
 *   <li>XNOR + Popcount throughput (binary neuron forward pass)</li>
 *   <li>Floating-point dot product + threshold (equivalent neuron)</li>
 *   <li>Binary weight update vs floating-point SGD update</li>
 *   <li>Speedup factor</li>
 *   <li>Memory usage comparison</li>
 * </ul>
 *
 * <p>Ref: arXiv:2412.00119 §5 — "2-3 orders of magnitude reduction in Boolean gates"
 *
 * <p>Run: {@code ./gradlew :matrix-core:jmh}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class BinaryBenchmark {

    private static final int INPUT_SIZE = 128;
    private static final int HIDDEN_SIZE = 64;
    private static final int OUTPUT_SIZE = 10;

    private BinaryNeuron binaryNeuron;
    private BinaryLayer binaryLayer;
    private BinaryNetwork binaryNetwork;
    private BinaryTrainer trainer;

    private BitSet binaryInput;
    private BitSet binaryTarget;

    // Floating-point equivalents for comparison
    private float[] fpWeights;
    private float[] fpInput;
    private float fpThreshold;

    private Random rng;

    @Setup
    public void setup() {
        rng = new Random(42);

        // Binary components
        binaryNeuron = new BinaryNeuron(INPUT_SIZE, rng);
        binaryLayer = new BinaryLayer(INPUT_SIZE, HIDDEN_SIZE, rng);
        binaryNetwork = new BinaryNetwork(new int[]{INPUT_SIZE, HIDDEN_SIZE, OUTPUT_SIZE}, rng);
        trainer = new BinaryTrainer(binaryNetwork, rng);

        binaryInput = randomBitSet(INPUT_SIZE, rng);
        binaryTarget = randomBitSet(OUTPUT_SIZE, rng);

        // Floating-point equivalents
        fpWeights = new float[INPUT_SIZE];
        fpInput = new float[INPUT_SIZE];
        fpThreshold = INPUT_SIZE / 2.0f;
        for (int i = 0; i < INPUT_SIZE; i++) {
            fpWeights[i] = rng.nextFloat() * 2 - 1; // [-1, 1]
            fpInput[i] = rng.nextBoolean() ? 1.0f : 0.0f;
        }
    }

    // ─── Binary neuron forward pass ───

    @Benchmark
    public int binaryNeuronForward() {
        return binaryNeuron.forward(binaryInput);
    }

    @Benchmark
    public int binaryNeuronActivation() {
        return binaryNeuron.computeActivation(binaryInput);
    }

    // ─── Floating-point equivalent ───

    @Benchmark
    public int floatingPointForward() {
        float sum = 0;
        for (int i = 0; i < INPUT_SIZE; i++) {
            sum += fpInput[i] * fpWeights[i];
        }
        return sum >= fpThreshold ? 1 : 0;
    }

    @Benchmark
    public float floatingPointDotProduct() {
        float sum = 0;
        for (int i = 0; i < INPUT_SIZE; i++) {
            sum += fpInput[i] * fpWeights[i];
        }
        return sum;
    }

    // ─── Binary layer ───

    @Benchmark
    public BitSet binaryLayerForward() {
        return binaryLayer.forward(binaryInput);
    }

    // ─── Binary network ───

    @Benchmark
    public BitSet binaryNetworkForward() {
        return binaryNetwork.forward(binaryInput);
    }

    // ─── Training ───

    @Benchmark
    public int binaryTrainStep() {
        return binaryNetwork.trainStep(binaryInput, binaryTarget);
    }

    @Benchmark
    public int binaryNeuronTrainStep() {
        return binaryNeuron.trainStep(binaryInput, 1);
    }

    // ─── Memory comparison ───

    @Benchmark
    public long binaryMemoryUsage() {
        return binaryNetwork.memoryBytes();
    }

    @Benchmark
    public long floatingPointMemoryUsage() {
        // FP32 weights: INPUT_SIZE * HIDDEN_SIZE + HIDDEN_SIZE * OUTPUT_SIZE
        return (long) INPUT_SIZE * HIDDEN_SIZE * 4 + (long) HIDDEN_SIZE * OUTPUT_SIZE * 4;
    }

    // ─── Helpers ───

    private static BitSet randomBitSet(int size, Random rng) {
        BitSet bs = new BitSet(size);
        for (int i = 0; i < size; i++) {
            if (rng.nextBoolean()) {
                bs.set(i);
            }
        }
        return bs;
    }
}
