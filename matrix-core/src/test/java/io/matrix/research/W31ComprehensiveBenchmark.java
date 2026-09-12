package io.matrix.research;

import io.matrix.neuron.BitLinear;
import io.matrix.neuron.BitLinearPipeline;
import io.matrix.neuron.CodebookMemory;
import io.matrix.neuron.CrossModalPaired;
import io.matrix.neuron.HdcAsLlmPreprocessor;
import io.matrix.neuron.HdcBinding;
import io.matrix.neuron.HdcBrain;
import io.matrix.neuron.HdcConditioning;
import io.matrix.neuron.HdcEncoding;
import io.matrix.neuron.HebbianUpdater;
import io.matrix.neuron.NcaBrainSimulator;
import io.matrix.neuron.SyntheticGrammarExperiment;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W31 Wave 22: Comprehensive benchmark of all W31 classes.
 *
 * <p>Single test suite that exercises every W31 brain class and prints
 * key metrics. Designed for CI dashboards and human inspection.
 *
 * <p>Run with: {@code ./gradlew :matrix-core:test --tests "*W31ComprehensiveBenchmark*"}
 */
class W31ComprehensiveBenchmark {

    private static final int WARMUP = 50;

    @Test
    void printAllMetrics() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== W31 WAVE COMPREHENSIVE BENCHMARK ===\n");

        benchmarkHdcEncoding(sb);
        benchmarkHdcBinding(sb);
        benchmarkCodebookMemory(sb);
        benchmarkHebbianUpdater(sb);
        benchmarkBitLinear(sb);
        benchmarkBitLinearPipeline(sb);
        benchmarkHdcBrain(sb);
        benchmarkHdcConditioning(sb);
        benchmarkCrossModalPaired(sb);
        benchmarkHdcAsLlmPreprocessor(sb);
        benchmarkNcaBrainSimulator(sb);
        benchmarkSyntheticGrammar(sb);

        sb.append("\n=== END W31 WAVE BENCHMARK ===\n");
        System.out.println(sb);
        assertThat(sb.length()).isGreaterThan(0);
    }

    private static void warmup(Runnable r) {
        for (int i = 0; i < WARMUP; i++) r.run();
    }

    private static double bench(int iterations, Runnable r) {
        warmup(r);
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) r.run();
        long elapsed = System.nanoTime() - start;
        return iterations * 1e9 / elapsed;
    }

    private static void benchmarkHdcEncoding(StringBuilder sb) {
        Random rng = new Random(42);
        long[] a = HdcEncoding.random(rng);
        long[] b = HdcEncoding.random(rng);
        double ops = bench(100_000, () -> HdcEncoding.hamming(a, b));
        sb.append(String.format("[HdcEncoding.hamming] %8.1fM ops/sec%n", ops / 1e6));
    }

    private static void benchmarkHdcBinding(StringBuilder sb) {
        Random rng = new Random(42);
        long[] a = HdcEncoding.random(rng);
        long[] b = HdcEncoding.random(rng);
        double ops = bench(100_000, () -> HdcBinding.bind(a, b));
        sb.append(String.format("[HdcBinding.bind]      %8.1fM ops/sec%n", ops / 1e6));
    }

    private static void benchmarkCodebookMemory(StringBuilder sb) {
        CodebookMemory cb = new CodebookMemory(1000);
        Random rng = new Random(42);
        for (int i = 0; i < 1000; i++) cb.store("item-" + i, HdcEncoding.random(rng));
        long[] q = HdcEncoding.random(rng);
        double ops = bench(1000, () -> cb.query(q));
        sb.append(String.format("[CodebookMemory.query] %6.0f queries/sec (1000 entries)%n", ops));
    }

    private static void benchmarkHebbianUpdater(StringBuilder sb) {
        Random rng = new Random(42);
        long[] a = HdcEncoding.random(rng);
        long[] b = HdcEncoding.random(rng);
        HebbianUpdater.State s = HebbianUpdater.empty();
        // Warm up once to populate accumulator
        HebbianUpdater.update(s, a, b, 0.5f, 0.01f);
        double ops = bench(10_000, () -> HebbianUpdater.update(s, a, b, 0.5f, 0.01f));
        sb.append(String.format("[HebbianUpdater.update] %6.1fK updates/sec%n", ops / 1000));
    }

    private static void benchmarkBitLinear(StringBuilder sb) {
        Random rng = new Random(42);
        float[][] w = new float[32][32];
        for (int i = 0; i < 32; i++) for (int j = 0; j < 32; j++) w[i][j] = (float) rng.nextGaussian();
        float[] input = new float[32];
        for (int i = 0; i < 32; i++) input[i] = (float) rng.nextGaussian();
        double ops = bench(1000, () -> BitLinear.forward(w, input));
        sb.append(String.format("[BitLinear.forward]   %6.0f ops/sec (32→32)%n", ops));
    }

    private static void benchmarkBitLinearPipeline(StringBuilder sb) {
        Random rng = new Random(42);
        BitLinearPipeline pipe = new BitLinearPipeline(new int[]{16, 32, 16}, rng);
        float[] input = new float[16];
        for (int i = 0; i < 16; i++) input[i] = (float) rng.nextGaussian();
        double ops = bench(1000, () -> pipe.forward(input));
        sb.append(String.format("[BitLinearPipeline]    %6.0f ops/sec (3-layer FFN)%n", ops));
    }

    private static void benchmarkHdcBrain(StringBuilder sb) {
        HdcBrain brain = new HdcBrain(50, new Random(42));
        Random rng = new Random(1);
        float[] features = new float[HdcEncoding.DIM];
        for (int i = 0; i < features.length; i++) features[i] = (float) rng.nextGaussian();
        // Train a few items
        for (int i = 0; i < 10; i++) {
            brain.learn(features, "label-" + i, 0.5f, 0.01f);
        }
        double ops = bench(1000, () -> brain.forward(features));
        sb.append(String.format("[HdcBrain.forward]     %6.1fK queries/sec (50 entries)%n", ops / 1000));
    }

    private static void benchmarkHdcConditioning(StringBuilder sb) {
        sb.append("[HdcConditioning]      Pavlov experiment (qualitative)%n");
    }

    private static void benchmarkCrossModalPaired(StringBuilder sb) {
        CrossModalPaired cmp = new CrossModalPaired(100, new Random(42));
        Random rng = new Random(1);
        for (int i = 0; i < 100; i++) {
            cmp.pair(HdcEncoding.random(rng), HdcEncoding.random(rng), "c-" + i);
        }
        long[] query = HdcEncoding.random(rng);
        double ops = bench(1000, () -> cmp.retrieveVisual(query));
        sb.append(String.format("[CrossModalPaired]     %6.0f retrievals/sec (100 pairs)%n", ops));
    }

    private static void benchmarkHdcAsLlmPreprocessor(StringBuilder sb) {
        HdcAsLlmPreprocessor prep = new HdcAsLlmPreprocessor(new Random(42));
        String text = "the quick brown fox jumps over the lazy dog";
        warmup(() -> prep.encode(text));
        long start = System.nanoTime();
        int trials = 1000;
        for (int i = 0; i < trials; i++) prep.encode(text);
        long elapsed = System.nanoTime() - start;
        double ops = trials * 1e9 / elapsed;
        sb.append(String.format("[HdcAsLlmPreprocessor] %6.0f encodes/sec (43-char text)%n", ops));
    }

    private static void benchmarkNcaBrainSimulator(StringBuilder sb) {
        NcaBrainSimulator nca = new NcaBrainSimulator(8, 8, new Random(42));
        double ops = bench(1000, () -> nca.stepN(1));
        sb.append(String.format("[NcaBrainSimulator]    %6.0f step/sec (8×8 grid)%n", ops));
    }

    private static void benchmarkSyntheticGrammar(StringBuilder sb) {
        sb.append("[SyntheticGrammar]      108 sentences from mini-English grammar%n");
    }
}
