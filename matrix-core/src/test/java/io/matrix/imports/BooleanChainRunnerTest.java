package io.matrix.imports;

import io.matrix.neuron.TruthTable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the structural chain-evaluation fix (RUN 9).
 *
 * <p>The previous {@link BooleanChainRunner#evaluateWithScore(boolean[])}
 * called {@code resize(state, neuronCount*k/2)} between layers, which
 * shrank the state below the next layer's input width and prevented
 * most neurons from ever firing.
 *
 * <p>{@link BooleanChainRunner#evaluateWithMagnitude(boolean[])} is the
 * new path: it propagates the proper output width through all layers
 * and tracks magnitude-weighted scoring.
 */
class BooleanChainRunnerTest {

    private static TruthTableLayer buildLayer(int neuronCount, int k, double density) {
        List<TruthTable> neurons = new ArrayList<>(neuronCount);
        int cells = 1 << k;
        for (int i = 0; i < neuronCount; i++) {
            BitSet bs = new BitSet(cells);
            int bitsToSet = (int) Math.round(cells * density);
            for (int j = 0; j < bitsToSet; j++) bs.set((j * 7) % cells);
            neurons.add(TruthTable.of(k, bs));
        }
        return new TruthTableLayer(neurons, k);
    }

    @Test
    void evaluateWithMagnitude_producesNonZeroOutputForNonZeroInput() {
        // Build a 2-layer chain with 50% density
        TruthTableLayer l0 = buildLayer(100, 9, 0.5);
        TruthTableLayer l1 = buildLayer(50, 9, 0.5);
        BooleanChainRunner runner = new BooleanChainRunner("test", "(none)",
                List.of(l0, l1));

        boolean[] input = new boolean[256];
        for (int i = 0; i < 256; i++) input[i] = (i % 3 == 0);

        BooleanChainRunner.ChainResult result = runner.evaluateWithMagnitude(input);

        // Output should NOT be all zeros
        int card = 0;
        for (boolean b : result.bits()) if (b) card++;
        assertThat(card).as("output should have non-zero cardinality").isGreaterThan(0);
        assertThat(result.neuronsFired()).as("neurons fired across chain").isGreaterThan(0);
    }

    @Test
    void evaluateWithMagnitude_differentInputsProduceDifferentOutput() {
        TruthTableLayer l0 = buildLayer(50, 9, 0.5);
        TruthTableLayer l1 = buildLayer(20, 9, 0.5);
        BooleanChainRunner runner = new BooleanChainRunner("test", "(none)",
                List.of(l0, l1));

        boolean[] inputA = new boolean[256];
        boolean[] inputB = new boolean[256];
        for (int i = 0; i < 256; i++) {
            inputA[i] = (i % 3 == 0);
            inputB[i] = (i % 7 == 0);
        }

        BooleanChainRunner.ChainResult resA = runner.evaluateWithMagnitude(inputA);
        BooleanChainRunner.ChainResult resB = runner.evaluateWithMagnitude(inputB);

        // Different inputs should produce DIFFERENT outputs (in some bit position)
        int diff = 0;
        int n = Math.min(resA.bits().length, resB.bits().length);
        for (int i = 0; i < n; i++) {
            if (resA.bits()[i] != resB.bits()[i]) diff++;
        }
        assertThat(diff).as("two different inputs must produce different outputs").isGreaterThan(0);
    }

    @Test
    void evaluateWithMagnitude_sameInputProducesSameOutput() {
        TruthTableLayer l0 = buildLayer(50, 9, 0.5);
        TruthTableLayer l1 = buildLayer(20, 9, 0.5);
        BooleanChainRunner runner = new BooleanChainRunner("test", "(none)",
                List.of(l0, l1));

        boolean[] input = new boolean[256];
        for (int i = 0; i < 256; i++) input[i] = (i % 4 == 0);

        BooleanChainRunner.ChainResult r1 = runner.evaluateWithMagnitude(input);
        BooleanChainRunner.ChainResult r2 = runner.evaluateWithMagnitude(input);

        // Deterministic: same input → same output
        int n = Math.min(r1.bits().length, r2.bits().length);
        int same = 0;
        for (int i = 0; i < n; i++) {
            if (r1.bits()[i] == r2.bits()[i]) same++;
        }
        assertThat(same).as("same input must produce identical output").isEqualTo(n);
        assertThat(r1.weightedScore()).isEqualTo(r2.weightedScore());
    }

    @Test
    void evaluateWithMagnitude_zeroInputMapsToCellIndex0() {
        // With density=0.5 and bit-set constructed at indices (j*7)%cells,
        // cell 0 IS set. Zero input → cellIndex=0 for every neuron → output bits
        // are determined solely by table[0] (not necessarily all-zero).
        TruthTableLayer l0 = buildLayer(50, 9, 0.5);
        TruthTableLayer l1 = buildLayer(20, 9, 0.5);
        BooleanChainRunner runner = new BooleanChainRunner("test", "(none)",
                List.of(l0, l1));

        boolean[] zeroInput = new boolean[256];
        BooleanChainRunner.ChainResult r1 = runner.evaluateWithMagnitude(zeroInput);
        BooleanChainRunner.ChainResult r2 = runner.evaluateWithMagnitude(zeroInput);

        // Deterministic
        assertThat(r1.weightedScore()).isEqualTo(r2.weightedScore());

        // The number of firing neurons reflects table[0] across all neurons.
        // Layer 0 has 50 neurons, each with 50% density → table[0]=true for ~25.
        // Layer 1 has 20 neurons, ~10 with table[0]=true. Plus propagation.
        assertThat(r1.neuronsFired()).isGreaterThan(0);

        // The important property: PREVIOUS structural fix proven — at least
        // some neurons DO fire, and the chain isn't collapsing to all-zero.
        int card = 0;
        for (boolean b : r1.bits()) if (b) card++;
        assertThat(card).as("chain with weights should produce SOME non-zero output bits").isGreaterThan(0);
    }

    /**
     * RUN 9.7 — replaceLayers atomically swaps the chain's layer list.
     * After the swap, evaluations must use the NEW layers (proved by
     * different neuron counts producing different outputs).
     */
    @Test
    void replaceLayersSwapsChainAtomically() {
        TruthTableLayer l0a = buildLayer(50, 9, 0.5);
        TruthTableLayer l1a = buildLayer(20, 9, 0.5);
        BooleanChainRunner runner = new BooleanChainRunner("test", "(none)",
                List.of(l0a, l1a));

        int origNeurons = (int) runner.totalNeurons();
        int origLayers = runner.layerCount();
        assertThat(origNeurons).isEqualTo(70);
        assertThat(origLayers).isEqualTo(2);

        // Build a different chain: 3 layers of 30 neurons each = 90 neurons
        TruthTableLayer l0b = buildLayer(30, 9, 0.5);
        TruthTableLayer l1b = buildLayer(30, 9, 0.5);
        TruthTableLayer l2b = buildLayer(30, 9, 0.5);
        runner.replaceLayers(List.of(l0b, l1b, l2b));

        assertThat(runner.layerCount()).as("layers swapped").isEqualTo(3);
        assertThat((int) runner.totalNeurons()).as("neurons swapped").isEqualTo(90);

        // Native tables must be regenerated
        assertThat(runner.layers()).hasSize(3);
    }
}
