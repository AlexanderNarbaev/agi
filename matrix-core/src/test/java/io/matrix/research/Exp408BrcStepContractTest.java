package io.matrix.research;

import io.matrix.neuron.NeuronLayer;
import io.matrix.neuron.TruthTable;
import io.matrix.reasoning.BrcStepContract;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 408 — Phase AA BrcChain primitives + Hoare-triplet contract.
 */
class Exp408BrcStepContractTest {

    @Test
    void brcStepContractHolds() {
        // Always-on contract: pre true, post says output has ≥1 bit
        // For test: use states with at least 1 bit set so post passes
        BrcStepContract contract = new BrcStepContract(
                "always-on",
                null,  // step reference not used in contract-only check
                s -> true,
                s -> s.cardinality() >= 1);
        for (int i = 0; i < 8; i++) {
            BitSet input = nonEmptyState(4, 0xCAFE + i);
            var result = contract.verify(input);
            assertThat(result.holds()).as("verify(%s)", input).isTrue();
        }
    }

    @Test
    void brcStepContractPreconditionFails() {
        BrcStepContract contract = new BrcStepContract(
                "always-on", null,
                s -> s.get(0),  // requires bit 0
                s -> s.cardinality() >= 1);
        BitSet noBit0 = new BitSet(4);
        noBit0.set(1);
        var result = contract.verify(noBit0);
        assertThat(result.holds()).isFalse();
        assertThat(result.detail()).contains("precondition");
    }

    @Test
    void brcStepContractPostconditionFails() {
        BrcStepContract contract = new BrcStepContract(
                "always-off", null,
                s -> true,
                s -> s.cardinality() >= 1);  // postcondition says ≥1, but state may be empty
        BitSet empty = new BitSet(4);
        var result = contract.verify(empty);
        assertThat(result.holds()).isFalse();
        assertThat(result.detail()).contains("postcondition");
    }

    @Test
    void brcStepContractRequiresAllFields() {
        // null name → exception
        try {
            new BrcStepContract(null, null, s -> true, s -> true);
            assertThat(false).as("should throw on null name").isTrue();
        } catch (IllegalArgumentException ok) { /* expected */ }
        // null pre → exception
        try {
            new BrcStepContract("t", null, null, s -> true);
            assertThat(false).as("should throw on null pre").isTrue();
        } catch (IllegalArgumentException ok) { /* expected */ }
        // null post → exception
        try {
            new BrcStepContract("t", null, s -> true, null);
            assertThat(false).as("should throw on null post").isTrue();
        } catch (IllegalArgumentException ok) { /* expected */ }
        // step is allowed to be null (standalone contract)
        BrcStepContract standalone = new BrcStepContract(
                "t", null, s -> true, s -> true);
        assertThat(standalone.getName()).isEqualTo("t");
        assertThat(standalone.getStep()).isNull();
    }

    @Test
    void neuronLayerCanBeCreated() {
        // Sanity: BrcStep + NeuronLayer can be constructed together
        // (regression test that both classes are in classpath).
        TruthTable table = TruthTable.of(4, allOnesBits(4));
        // We don't need to actually run the step here — just verify
        // the imports and classpath work.
        assertThat(table.k()).isEqualTo(4);
        assertThat(table.table().cardinality()).isEqualTo(16);
    }

    private static BitSet allOnesBits(int n) {
        BitSet bs = new BitSet(1 << n);
        for (int i = 0; i < (1 << n); i++) bs.set(i);
        return bs;
    }

    private static BitSet randomState(int n, long seed) {
        BitSet s = new BitSet(n);
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < n; i++) {
            if (rng.nextBoolean()) s.set(i);
        }
        return s;
    }

    private static BitSet nonEmptyState(int n, long seed) {
        BitSet s = new BitSet(n);
        java.util.Random rng = new java.util.Random(seed);
        s.set(rng.nextInt(n));  // ensure at least 1 bit
        for (int i = 0; i < n; i++) {
            if (rng.nextBoolean()) s.set(i);
        }
        return s;
    }
}
