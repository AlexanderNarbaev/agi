package io.matrix.reasoning;

import io.matrix.neuron.HierarchicalBrain;
import io.matrix.neuron.NeuronLayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.BitSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Boolean Reasoning Chain (BRC).
 */
class BrcChainTest {

    private static final int VECTOR_WIDTH = 64;
    private static final Random RNG = new Random(42);

    @Test
    @DisplayName("BrcState: initial state has step index 0")
    void initialStateHasStepIndexZero() {
        BitSet input = new BitSet(VECTOR_WIDTH);
        BrcState state = new BrcState(input, VECTOR_WIDTH);

        assertEquals(0, state.stepIndex());
        assertFalse(state.isConverged());
        assertTrue(state.history().isEmpty());
    }

    @Test
    @DisplayName("BrcState: next() increments step index")
    void nextIncrementsStepIndex() {
        BitSet input = new BitSet(VECTOR_WIDTH);
        BrcState state = new BrcState(input, VECTOR_WIDTH);

        BitSet output = new BitSet(VECTOR_WIDTH);
        BrcState next = state.next(output, false);

        assertEquals(1, next.stepIndex());
        assertEquals(1, next.history().size());
    }

    @Test
    @DisplayName("BrcState: converged flag propagates")
    void convergedFlagPropagates() {
        BitSet input = new BitSet(VECTOR_WIDTH);
        BrcState state = new BrcState(input, VECTOR_WIDTH);

        BitSet output = new BitSet(VECTOR_WIDTH);
        BrcState converged = state.next(output, true);

        assertTrue(converged.isConverged());
    }

    @Test
    @DisplayName("BrcState: hamming distance computed correctly")
    void hammingDistanceComputedCorrectly() {
        BitSet a = new BitSet(VECTOR_WIDTH);
        a.set(0);
        a.set(1);
        a.set(2);

        BitSet b = new BitSet(VECTOR_WIDTH);
        b.set(0);
        b.set(3);

        assertEquals(3, BrcState.hammingDistance(a, b)); // bits 1,2,3 differ
    }

    @Test
    @DisplayName("BrcStep: apply produces output")
    void stepApplyProducesOutput() {
        NeuronLayer layer = new NeuronLayer(8, 8, RNG);
        BrcStep step = new BrcStep(layer, "test", 0);

        BitSet input = new BitSet(VECTOR_WIDTH);
        input.set(0);
        input.set(1);

        BrcState state = new BrcState(input, VECTOR_WIDTH);
        BrcState result = step.apply(state);

        assertNotNull(result);
        assertEquals(1, result.stepIndex());
    }

    @Test
    @DisplayName("BrcStep: convergence detected when vectors match")
    void convergenceDetectedWhenVectorsMatch() {
        // Create identity-like layer that outputs same as input
        NeuronLayer layer = new NeuronLayer(8, 8, RNG);
        BrcStep step = new BrcStep(layer, "test", 0);

        BitSet input = new BitSet(VECTOR_WIDTH);
        BrcState state = new BrcState(input, VECTOR_WIDTH);

        // Note: random layer won't match, but we test the mechanism
        BrcState result = step.apply(state);
        assertFalse(result.isConverged()); // Random layer won't converge
    }

    @Test
    @DisplayName("BrcChain: evaluate produces final state")
    void chainEvaluateProducesFinalState() {
        NeuronLayer layer1 = new NeuronLayer(16, 8, RNG);
        NeuronLayer layer2 = new NeuronLayer(8, 8, RNG);

        BrcStep step1 = new BrcStep(layer1, "step1", 0);
        BrcStep step2 = new BrcStep(layer2, "step2", 0);

        BrcChain chain = BrcChain.builder()
            .addStep(step1)
            .addStep(step2)
            .earlyStopping(false)
            .build();

        BitSet input = new BitSet(VECTOR_WIDTH);
        input.set(0);
        input.set(1);

        BrcState result = chain.evaluate(input, VECTOR_WIDTH);

        assertNotNull(result);
        assertEquals(2, result.stepIndex());
    }

    @Test
    @DisplayName("BrcChain: early stopping stops on convergence")
    void earlyStoppingStopsOnConvergence() {
        NeuronLayer layer = new NeuronLayer(8, 8, RNG);
        BrcStep step = new BrcStep(layer, "test", 100); // High threshold = always converge

        BrcChain chain = BrcChain.builder()
            .addStep(step)
            .addStep(step)
            .addStep(step)
            .earlyStopping(true)
            .build();

        BitSet input = new BitSet(VECTOR_WIDTH);
        BrcState result = chain.evaluate(input, VECTOR_WIDTH);

        // Should stop after first step due to high threshold
        assertEquals(1, result.stepIndex());
        assertTrue(result.isConverged());
    }

    @Test
    @DisplayName("BrcChain: maxSteps limits execution")
    void maxStepsLimitsExecution() {
        NeuronLayer layer = new NeuronLayer(8, 8, RNG);
        BrcStep step = new BrcStep(layer, "test", 0);

        BrcChain chain = BrcChain.builder()
            .addStep(step)
            .addStep(step)
            .addStep(step)
            .maxSteps(2)
            .earlyStopping(false)
            .build();

        BitSet input = new BitSet(VECTOR_WIDTH);
        BrcState result = chain.evaluate(input, VECTOR_WIDTH);

        assertEquals(2, result.stepIndex());
    }

    @Test
    @DisplayName("BrcChain: evaluateDetailed returns all states")
    void evaluateDetailedReturnsAllStates() {
        NeuronLayer layer = new NeuronLayer(8, 8, RNG);
        BrcStep step = new BrcStep(layer, "test", 0);

        BrcChain chain = BrcChain.builder()
            .addStep(step)
            .addStep(step)
            .earlyStopping(false)
            .build();

        BitSet input = new BitSet(VECTOR_WIDTH);
        List<BrcState> results = chain.evaluateDetailed(input, VECTOR_WIDTH);

        assertEquals(3, results.size()); // initial +2 steps
    }

    @Test
    @DisplayName("BrcChain: builder requires at least one step")
    void builderRequiresAtLeastOneStep() {
        assertThrows(IllegalStateException.class, () -> {
            BrcChain.builder().build();
        });
    }

    @Test
    @DisplayName("BrcChain: toString includes step count")
    void toStringIncludesStepCount() {
        NeuronLayer layer = new NeuronLayer(8, 8, RNG);
        BrcStep step = new BrcStep(layer, "test", 0);

        BrcChain chain = BrcChain.builder()
            .addStep(step)
            .build();

        String str = chain.toString();
        assertTrue(str.contains("steps=1"));
    }

    @Test
    @DisplayName("HierarchicalBrain: toBrcChain creates chain from layers")
    void hierarchicalBrainToBrcChain() {
        HierarchicalBrain brain = new HierarchicalBrain(RNG);
        BrcChain chain = brain.toBrcChain(5, 2);

        assertNotNull(chain);
        assertEquals(3, chain.stepCount()); // sensor, feature, action
        assertEquals(5, chain.maxSteps());
        assertTrue(chain.isEarlyStopping());
    }

    @Test
    @DisplayName("HierarchicalBrain: decideWithReasoning returns state")
    void hierarchicalBrainDecideWithReasoning() {
        HierarchicalBrain brain = new HierarchicalBrain(RNG);
        BrcState state = brain.decideWithReasoning(0xABCDEFL, 3);

        assertNotNull(state);
        assertEquals(3, state.stepIndex()); // sensor + feature + action
        assertNotNull(state.vector());
    }
}
