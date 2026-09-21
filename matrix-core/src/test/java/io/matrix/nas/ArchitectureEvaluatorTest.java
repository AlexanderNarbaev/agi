package io.matrix.nas;

import io.matrix.nas.ArchitectureSpec.Activation;
import io.matrix.nas.ArchitectureSpec.LayerSpec;
import io.matrix.nas.ArchitectureSpec.LayerType;
import io.matrix.neuron.HierarchicalBrain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchitectureEvaluatorTest {

    private Random rng;
    private ArchitectureEvaluator evaluator;

    @BeforeEach
    void setUp() {
        rng = new Random(42);
        evaluator = new ArchitectureEvaluator(50, 20, rng);
    }

    @AfterEach
    void tearDown() {
        evaluator.shutdown();
    }

    // ─── Evaluation ───

    @Test
    void evaluateShouldReturnPositiveFitness() {
        ArchitectureSpec spec = ArchitectureSpec.random(3, 16, rng);
        long fitness = evaluator.evaluate(spec);

        assertThat(fitness).isGreaterThanOrEqualTo(0);
    }

    @Test
    void evaluateShouldReturnZeroForEmptySpec() {
        ArchitectureSpec spec = ArchitectureSpec.of(List.of());
        long fitness = evaluator.evaluate(spec);

        assertThat(fitness).isZero();
    }

    @Test
    void evaluateShouldReturnBoundedFitness() {
        ArchitectureSpec spec = ArchitectureSpec.random(3, 32, rng);
        long fitness = evaluator.evaluate(spec);

        assertThat(fitness).isBetween(0L, 1000L);
    }

    @Test
    void evaluateDifferentArchitecturesShouldGiveDifferentScores() {
        ArchitectureSpec spec1 = ArchitectureSpec.of(List.of(
                new LayerSpec(LayerType.DENSE, 4, Activation.RELU, 0)));
        ArchitectureSpec spec2 = ArchitectureSpec.of(List.of(
                new LayerSpec(LayerType.DENSE, 64, Activation.SIGMOID, 0)));

        long fitness1 = evaluator.evaluate(spec1);
        long fitness2 = evaluator.evaluate(spec2);

        // Different architectures should generally produce different fitness
        // (not guaranteed, but highly likely with different sizes)
        assertThat(fitness1).isNotEqualTo(fitness2);
    }

    // ─── Detailed Evaluation ───

    @Test
    void evaluateDetailedShouldReturnBreakdown() {
        ArchitectureSpec spec = ArchitectureSpec.random(3, 16, rng);
        ArchitectureEvaluator.FitnessBreakdown breakdown = evaluator.evaluateDetailed(spec);

        assertThat(breakdown.totalFitness()).isBetween(0L, 1000L);
        assertThat(breakdown.accuracy()).isBetween(0.0, 1.0);
        assertThat(breakdown.latency()).isBetween(0.0, 1.0);
        assertThat(breakdown.memory()).isBetween(0.0, 1.0);
        assertThat(breakdown.complexity()).isGreaterThan(0);
        assertThat(breakdown.totalNeurons()).isGreaterThan(0);
    }

    @Test
    void evaluateDetailedForEmptyShouldReturnZeros() {
        ArchitectureSpec spec = ArchitectureSpec.of(List.of());
        ArchitectureEvaluator.FitnessBreakdown breakdown = evaluator.evaluateDetailed(spec);

        assertThat(breakdown.totalFitness()).isZero();
        assertThat(breakdown.accuracy()).isZero();
    }

    // ─── Brain Construction ───

    @Test
    void buildBrainShouldCreateValidBrain() {
        ArchitectureSpec spec = ArchitectureSpec.random(3, 16, rng);
        HierarchicalBrain brain = evaluator.buildBrain(spec);

        assertThat(brain).isNotNull();
        assertThat(brain.sensorLayer()).isNotNull();
        assertThat(brain.featureLayer()).isNotNull();
        assertThat(brain.actionLayer()).isNotNull();
    }

    @Test
    void buildBrainShouldHandleSingleLayer() {
        ArchitectureSpec spec = ArchitectureSpec.of(List.of(
                new LayerSpec(LayerType.DENSE, 8, Activation.RELU, 0)));
        HierarchicalBrain brain = evaluator.buildBrain(spec);

        assertThat(brain).isNotNull();
    }

    @Test
    void buildBrainShouldHandleManyLayers() {
        ArchitectureSpec spec = ArchitectureSpec.random(6, 16, rng);
        HierarchicalBrain brain = evaluator.buildBrain(spec);

        assertThat(brain).isNotNull();
    }

    // ─── Accuracy Measurement ───

    @Test
    void measureAccuracyShouldReturnBetweenZeroAndOne() {
        ArchitectureSpec spec = ArchitectureSpec.random(3, 16, rng);
        HierarchicalBrain brain = evaluator.buildBrain(spec);
        double accuracy = evaluator.measureAccuracy(brain);

        assertThat(accuracy).isBetween(0.0, 1.0);
    }

    // ─── Latency Measurement ───

    @Test
    void measureLatencyShouldReturnPositive() {
        ArchitectureSpec spec = ArchitectureSpec.random(3, 16, rng);
        HierarchicalBrain brain = evaluator.buildBrain(spec);
        double latency = evaluator.measureLatency(brain);

        assertThat(latency).isGreaterThanOrEqualTo(0);
    }

    // ─── Memory Measurement ───

    @Test
    void measureMemoryShouldReturnBetweenZeroAndOne() {
        ArchitectureSpec spec = ArchitectureSpec.random(3, 16, rng);
        double memory = evaluator.measureMemory(spec);

        assertThat(memory).isBetween(0.0, 1.0);
    }

    @Test
    void measureMemoryShouldPenalizeLargerArchitectures() {
        ArchitectureSpec small = ArchitectureSpec.of(List.of(
                new LayerSpec(LayerType.DENSE, 4, Activation.RELU, 0)));
        ArchitectureSpec large = ArchitectureSpec.of(List.of(
                new LayerSpec(LayerType.DENSE, 100, Activation.RELU, 0)));

        double smallMem = evaluator.measureMemory(small);
        double largeMem = evaluator.measureMemory(large);

        assertThat(smallMem).isGreaterThan(largeMem);
    }

    // ─── Batch Evaluation ───

    @Test
    void evaluateBatchShouldReturnCorrectCount() {
        List<ArchitectureSpec> specs = List.of(
                ArchitectureSpec.random(2, 8, rng),
                ArchitectureSpec.random(3, 16, rng),
                ArchitectureSpec.random(4, 32, rng));

        List<Long> fitnesses = evaluator.evaluateBatch(specs);

        assertThat(fitnesses).hasSize(3);
        fitnesses.forEach(f -> assertThat(f).isBetween(0L, 1000L));
    }

    @Test
    void evaluateBatchShouldHandleEmptyList() {
        List<Long> fitnesses = evaluator.evaluateBatch(List.of());
        assertThat(fitnesses).isEmpty();
    }

    // ─── Custom Weights ───

    @Test
    void customWeightsShouldAffectFitness() {
        // Accuracy-heavy evaluator
        var accuracyHeavy = new ArchitectureEvaluator(50, 20, 0.9, 0.05, 0.05, rng);
        // Memory-heavy evaluator
        var memoryHeavy = new ArchitectureEvaluator(50, 20, 0.1, 0.1, 0.8, rng);

        ArchitectureSpec spec = ArchitectureSpec.random(3, 16, rng);

        long accFitness = accuracyHeavy.evaluate(spec);
        long memFitness = memoryHeavy.evaluate(spec);

        // Different weights should produce different fitness
        assertThat(accFitness).isNotEqualTo(memFitness);

        accuracyHeavy.shutdown();
        memoryHeavy.shutdown();
    }

    @Test
    void weightsShouldSumToOne() {
        assertThatThrownBy(() -> new ArchitectureEvaluator(50, 20, 0.5, 0.5, 0.5, rng))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── FitnessBreakdown ───

    @Test
    void fitnessBreakdownToStringShouldBeFormatted() {
        ArchitectureSpec spec = ArchitectureSpec.random(3, 16, rng);
        ArchitectureEvaluator.FitnessBreakdown breakdown = evaluator.evaluateDetailed(spec);

        String str = breakdown.toString();
        assertThat(str).contains("FitnessBreakdown");
        assertThat(str).contains("total=");
        assertThat(str).contains("acc=");
    }

    // ─── Edge Cases ───

    @Test
    void evaluateShouldHandleSingleNeuronLayers() {
        ArchitectureSpec spec = ArchitectureSpec.of(List.of(
                new LayerSpec(LayerType.DENSE, 1, Activation.NONE, 0)));
        long fitness = evaluator.evaluate(spec);

        assertThat(fitness).isGreaterThanOrEqualTo(0);
    }

    @Test
    void evaluateShouldHandleLargeArchitectures() {
        ArchitectureSpec spec = ArchitectureSpec.of(List.of(
                new LayerSpec(LayerType.DENSE, 64, Activation.RELU, 0),
                new LayerSpec(LayerType.ATTENTION, 32, Activation.GELU, 0),
                new LayerSpec(LayerType.DENSE, 16, Activation.SOFTMAX, 0)));
        long fitness = evaluator.evaluate(spec);

        assertThat(fitness).isGreaterThanOrEqualTo(0);
    }
}
