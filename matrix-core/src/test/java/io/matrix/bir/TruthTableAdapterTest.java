package io.matrix.bir;

import io.matrix.neuron.TruthTable;
import io.matrix.neuron.WeightVector;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TruthTableAdapter} (SPEC-002 FR-A4, strangler-fig):
 * both directions of the legacy {@code io.matrix.neuron.TruthTable} ↔ BIR
 * {@link TtForm} adaptation must preserve the evaluated function exactly.
 */
class TruthTableAdapterTest {

    private static BitSet parityBits(int k) {
        BitSet bits = new BitSet(1 << k);
        for (int i = 0; i < (1 << k); i++) {
            if (Integer.bitCount(i) % 2 == 1) bits.set(i);
        }
        return bits;
    }

    @Test
    void toBirEvalParity() {
        int k = 4;
        TruthTable legacy = TruthTable.of(k, parityBits(k));
        TtForm form = TruthTableAdapter.toBir(legacy);
        long[] out = new long[1];
        for (int i = 0; i < (1 << k); i++) {
            form.eval(new long[]{i}, out);
            assertThat(out[0]).as("input %d", i).isEqualTo(legacy.evaluate(i) ? 1L : 0L);
        }
    }

    @Test
    void toBirMetadata() {
        TtForm form = TruthTableAdapter.toBir(TruthTable.of(3, parityBits(3)));
        assertThat(form.k()).isEqualTo(3);
        assertThat(form.form()).isEqualTo("tt");
        assertThat(form.fidelity()).isEqualTo(1.0);
        assertThat(form.provenance()).isEqualTo("legacy-truthtable");
    }

    @Test
    void toBirHonorsPriorityWeights() {
        // Weighted evaluation permutes input bits by priority; the adapter
        // must bake the weighted semantics into the plain BIR table.
        int k = 4;
        WeightVector weights = WeightVector.random(k, new Random(42));
        TruthTable legacy = TruthTable.of(k, parityBits(k), weights);
        TtForm form = TruthTableAdapter.toBir(legacy);
        long[] out = new long[1];
        for (int i = 0; i < (1 << k); i++) {
            form.eval(new long[]{i}, out);
            assertThat(out[0]).as("weighted input %d", i).isEqualTo(legacy.evaluate(i) ? 1L : 0L);
        }
    }

    @Test
    void fromBirEvalParity() {
        int k = 5;
        long[] table = new long[]{0xDEADBEEFCAFEBABEL, 0x0123456789ABCDEFL};
        TtForm form = new TtForm(k, table, "test", 1.0);
        TruthTable legacy = TruthTableAdapter.fromBir(form);
        long[] out = new long[1];
        for (int i = 0; i < (1 << k); i++) {
            form.eval(new long[]{i}, out);
            assertThat(legacy.evaluate(i)).as("input %d", i).isEqualTo(out[0] == 1L);
        }
    }

    @Test
    void legacyRoundTripPreservesEquality() {
        int k = 4;
        TruthTable legacy = TruthTable.of(k, parityBits(k));
        TruthTable back = TruthTableAdapter.fromBir(TruthTableAdapter.toBir(legacy));
        assertThat(back).isEqualTo(legacy);
        assertThat(back.k()).isEqualTo(k);
    }

    @Test
    void birRoundTripPreservesTable() {
        int k = 4;
        long[] table = {0b0110100110010110L};
        TtForm form = new TtForm(k, table, "test", 1.0);
        TtForm back = TruthTableAdapter.toBir(TruthTableAdapter.fromBir(form));
        assertThat(back.table()).isEqualTo(form.table());
    }

    @Test
    void constantsRoundTrip() {
        for (int k = 1; k <= 6; k++) {
            BitSet all = new BitSet(1 << k);
            all.set(0, 1 << k);
            for (BitSet bits : new BitSet[]{new BitSet(1 << k), all}) {
                TruthTable legacy = TruthTable.of(k, bits);
                assertThat(TruthTableAdapter.fromBir(TruthTableAdapter.toBir(legacy)))
                        .as("constant round-trip k=%d", k)
                        .isEqualTo(legacy);
            }
        }
    }
}
