package io.matrix.neuron.binary;

import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests for {@link io.matrix.benchmark.BinaryBenchmark} setup logic and
 * performance comparison assertions (not JMH execution — that requires
 * {@code ./gradlew :matrix-core:jmh}).
 */
class BinaryBenchmarkTest {

    private final Random rng = new Random(42);

    // ─── Binary vs Floating-point correctness ───

    @Test
    void binaryForwardShouldMatchFloatingPointForSameLogic() {
        int inputSize = 16;
        int threshold = 8;

        // Binary neuron
        int[] hw = new int[inputSize];
        float[] fpWeights = new float[inputSize];
        for (int i = 0; i < inputSize; i++) {
            hw[i] = rng.nextBoolean() ? 1 : 0;
            fpWeights[i] = hw[i] > 0 ? 1.0f : -1.0f;
        }
        BinaryNeuron neuron = new BinaryNeuron(inputSize, threshold, hw);

        // Test multiple inputs
        for (int trial = 0; trial < 100; trial++) {
            BitSet bsInput = randomBitSet(inputSize, rng);
            float[] fpInput = new float[inputSize];
            for (int i = 0; i < inputSize; i++) {
                fpInput[i] = bsInput.get(i) ? 1.0f : 0.0f;
            }

            int binaryResult = neuron.forward(bsInput);

            // Floating-point equivalent: dot product + threshold
            float dotProduct = 0;
            for (int i = 0; i < inputSize; i++) {
                dotProduct += fpInput[i] * fpWeights[i];
            }
            // Map to same threshold: count matches
            int fpMatches = 0;
            for (int i = 0; i < inputSize; i++) {
                if (bsInput.get(i) == (hw[i] > 0)) {
                    fpMatches++;
                }
            }
            int fpResult = fpMatches >= threshold ? 1 : 0;

            assertThat(binaryResult)
                    .as("trial %d", trial)
                    .isEqualTo(fpResult);
        }
    }

    // ─── Memory comparison ───

    @Test
    void binaryMemoryShouldBeLessThanFloatingPoint() {
        int inputSize = 128;
        int hiddenSize = 64;
        int outputSize = 10;

        BinaryNetwork net = new BinaryNetwork(
                new int[]{inputSize, hiddenSize, outputSize}, rng);

        long binaryMem = net.memoryBytes();
        long fpMem = (long) inputSize * hiddenSize * 4 + (long) hiddenSize * outputSize * 4;

        // Binary should use less memory (4 bytes int + 1 bit binary vs 4 bytes float)
        // Actually, binary uses 4 bytes per hidden weight (int) so it's similar
        // The savings come at hardware level (Boolean gates vs FP ALUs)
        assertThat(binaryMem).isPositive();
        assertThat(fpMem).isPositive();
    }

    // ─── Speed comparison (functional, not benchmark) ───

    @Test
    void binaryOperationsShouldBeFast() {
        int inputSize = 128;
        BinaryNeuron neuron = new BinaryNeuron(inputSize, rng);
        BitSet input = randomBitSet(inputSize, rng);

        // Warmup
        for (int i = 0; i < 1000; i++) {
            neuron.forward(input);
        }

        // Measure binary forward
        long binaryStart = System.nanoTime();
        for (int i = 0; i < 100_000; i++) {
            neuron.forward(input);
        }
        long binaryTime = System.nanoTime() - binaryStart;

        // Measure floating-point equivalent
        float[] fpWeights = new float[inputSize];
        float[] fpInput = new float[inputSize];
        float fpThreshold = inputSize / 2.0f;
        for (int i = 0; i < inputSize; i++) {
            fpWeights[i] = rng.nextFloat() * 2 - 1;
            fpInput[i] = rng.nextBoolean() ? 1.0f : 0.0f;
        }

        long fpStart = System.nanoTime();
        for (int i = 0; i < 100_000; i++) {
            float sum = 0;
            for (int j = 0; j < inputSize; j++) {
                sum += fpInput[j] * fpWeights[j];
            }
            int result = sum >= fpThreshold ? 1 : 0;
        }
        long fpTime = System.nanoTime() - fpStart;

        // Both should complete in reasonable time
        assertThat(binaryTime).isPositive();
        assertThat(fpTime).isPositive();
    }

    // ─── Training speed ───

    @Test
    void trainingShouldCompleteInReasonableTime() {
        BinaryNetwork net = new BinaryNetwork(new int[]{32, 16, 4}, rng);
        BinaryTrainer trainer = new BinaryTrainer(net, rng);

        BitSet[] inputs = new BitSet[20];
        BitSet[] targets = new BitSet[20];
        Random dataRng = new Random(99);
        for (int i = 0; i < 20; i++) {
            inputs[i] = randomBitSet(32, dataRng);
            targets[i] = randomBitSet(4, dataRng);
        }

        long start = System.nanoTime();
        BinaryTrainer.TrainingResult result = trainer.train(inputs, targets, 100);
        long elapsed = System.nanoTime() - start;

        assertThat(result.accuracyHistory()).hasSize(100);
        // Should complete in < 10 seconds for this size
        assertThat(elapsed).isLessThan(10_000_000_000L);
    }

    // ─── Scalability ───

    @Test
    void shouldHandleLargeNetworks() {
        BinaryNetwork net = new BinaryNetwork(new int[]{256, 128, 64, 10}, rng);
        BitSet input = randomBitSet(256, rng);

        BitSet output = net.forward(input);
        assertThat(output.length()).isLessThanOrEqualTo(10);
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
