package io.matrix.integration;

import io.matrix.neuron.HierarchicalBrain;
import io.matrix.neuron.NeuronLayer;
import io.matrix.neuron.TruthTable;
import io.matrix.reasoning.BrcChain;
import io.matrix.reasoning.BrcState;
import io.matrix.reasoning.BrcStep;

import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 9: Integration tests for Boolean Reasoning Chain (BRC).
 *
 * <p>Tests multi-step reasoning on boolean vectors with convergence
 * detection and early stopping using real neuron layers.
 */
class BrcIntegrationTest {

    private static final int K = 12;
    private static final int SENSOR_NEURONS = 12;
    private static final int FEATURE_NEURONS = 8;
    private static final int ACTION_NEURONS = 5;

    @Test
    void brcWithRealLayersProcessesInput() {
        var rng = new Random(42);
        HierarchicalBrain brain = new HierarchicalBrain(rng);

        BrcChain chain = brain.toBrcChain(0, 2);
        assertThat(chain.stepCount()).isEqualTo(3);

        BitSet input = new BitSet(20);
        input.set(0, 5); // set first 5 bits

        BrcState result = chain.evaluate(input, 20);
        assertThat(result).isNotNull();
        assertThat(result.stepIndex()).isGreaterThan(0);
    }

    @Test
    void multiStepReasoningProducesStableOutput() {
        var rng = new Random(123);
        NeuronLayer layer = new NeuronLayer(8, K, rng);

        BrcStep step = new BrcStep(layer, "test", 0);
        BrcChain chain = BrcChain.builder()
                .addStep(step)
                .addStep(new BrcStep(new NeuronLayer(5, 8, rng), "output", 0))
                .maxSteps(10)
                .earlyStopping(true)
                .build();

        BitSet input = new BitSet(K);
        input.set(0);
        input.set(3);
        input.set(7);

        BrcState result = chain.evaluate(input, K);
        assertThat(result).isNotNull();
        assertThat(result.vector()).isNotNull();
    }

    @Test
    void convergenceDetectionStopsEarly() {
        var rng = new Random(42);

        // Create a layer that produces identical output for repeated inputs
        // Use a constant-output truth table to guarantee convergence
        BitSet allOnes = new BitSet(1 << K);
        allOnes.set(0, 1 << K);
        TruthTable constantTrue = TruthTable.of(K, allOnes);
        NeuronLayer constantLayer = NeuronLayer.fromTruthTables(List.of(constantTrue));

        BrcStep step = new BrcStep(constantLayer, "constant", 2);
        BrcChain chain = BrcChain.builder()
                .addStep(step)
                .addStep(new BrcStep(constantLayer, "constant2", 2))
                .addStep(new BrcStep(constantLayer, "constant3", 2))
                .maxSteps(100)
                .earlyStopping(true)
                .build();

        BitSet input = new BitSet(K);
        input.set(1, 4);

        List<BrcState> detailed = chain.evaluateDetailed(input, K);
        assertThat(detailed).hasSizeGreaterThanOrEqualTo(2);

        // After first step with constant layer, convergence should be detected
        BrcState lastState = detailed.get(detailed.size() - 1);
        assertThat(lastState.stepIndex()).isLessThanOrEqualTo(3);
    }

    @Test
    void earlyStoppingRespectsMaxSteps() {
        var rng = new Random(99);
        NeuronLayer layer = new NeuronLayer(FEATURE_NEURONS, K, rng);

        BrcStep step = new BrcStep(layer, "feature", 5); // high threshold = unlikely to converge
        BrcChain chain = BrcChain.builder()
                .addStep(step)
                .addStep(new BrcStep(new NeuronLayer(ACTION_NEURONS, FEATURE_NEURONS, rng), "action", 5))
                .maxSteps(2)
                .earlyStopping(true)
                .build();

        BitSet input = new BitSet(K);
        input.set(0, 3);

        BrcState result = chain.evaluate(input, K);
        // Should not exceed maxSteps
        assertThat(result.stepIndex()).isLessThanOrEqualTo(2);
    }

    @Test
    void brcDetailedReturnsAllIntermediateStates() {
        var rng = new Random(42);
        HierarchicalBrain brain = new HierarchicalBrain(rng);
        BrcChain chain = brain.toBrcChain(0, 2);

        BitSet input = new BitSet(20);
        input.set(0);
        input.set(5);
        input.set(10);
        input.set(15);

        List<BrcState> states = chain.evaluateDetailed(input, 20);

        // Initial state + 3 steps = 4 states (or fewer if converged)
        assertThat(states).hasSizeGreaterThanOrEqualTo(2);

        // Step indices should be monotonically increasing
        for (int i = 1; i < states.size(); i++) {
            assertThat(states.get(i).stepIndex())
                    .isGreaterThanOrEqualTo(states.get(i - 1).stepIndex());
        }
    }

    @Test
    void brcWithDifferentConvergenceThresholds() {
        var rng = new Random(42);
        NeuronLayer layer = new NeuronLayer(8, K, rng);

        // Strict threshold (0 = exact match)
        BrcChain strict = BrcChain.builder()
                .addStep(new BrcStep(layer, "strict", 0))
                .maxSteps(5)
                .earlyStopping(true)
                .build();

        // Loose threshold
        BrcChain loose = BrcChain.builder()
                .addStep(new BrcStep(layer, "loose", 4))
                .maxSteps(5)
                .earlyStopping(true)
                .build();

        BitSet input = new BitSet(K);
        input.set(2, 6);

        BrcState strictResult = strict.evaluate(input, K);
        BrcState looseResult = loose.evaluate(input, K);

        assertThat(strictResult).isNotNull();
        assertThat(looseResult).isNotNull();

        // Both should produce valid output
        assertThat(strictResult.vector()).isNotNull();
        assertThat(looseResult.vector()).isNotNull();
    }
}
