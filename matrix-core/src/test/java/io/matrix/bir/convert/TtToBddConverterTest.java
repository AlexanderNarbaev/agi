package io.matrix.bir.convert;

import io.matrix.bir.BddForm;
import io.matrix.bir.BirCompiler;
import io.matrix.bir.TtForm;
import io.matrix.neuron.TruthTable;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TtToBddConverter}: exact TT → canonical BDD conversion
 * and the legacy {@link TruthTable} adapter path.
 *
 * <p>Eval parity is checked exhaustively on every case (parity, AND,
 * constants — including reduced diagrams where the unique table collapses
 * levels or the whole function to a terminal).
 */
class TtToBddConverterTest {

    private static TtForm parity(int k) {
        int size = 1 << k;
        long[] table = new long[(size + 63) / 64];
        for (int i = 0; i < size; i++) {
            if (Integer.bitCount(i) % 2 == 1) table[i >>> 6] |= (1L << (i & 63));
        }
        return new TtForm(k, table, "test-parity", 1.0);
    }

    private static void assertEvalParity(TtForm tt, BddForm bdd) {
        long[] in = new long[1];
        long[] outTt = new long[1];
        long[] outBdd = new long[1];
        for (int i = 0; i < (1 << tt.k()); i++) {
            in[0] = i;
            tt.eval(in, outTt);
            bdd.eval(in, outBdd);
            assertThat(outBdd[0]).as("input %d", i).isEqualTo(outTt[0]);
        }
    }

    @Test
    void convertParity4EvalParity() {
        TtForm tt = parity(4);
        assertEvalParity(tt, TtToBddConverter.convert(tt));
    }

    @Test
    void convertAndGateEvalParity() {
        TtForm tt = new TtForm(2, new long[]{0b1000L}, "test-and", 1.0);
        assertEvalParity(tt, TtToBddConverter.convert(tt));
    }

    @Test
    void convertConstantOne() {
        TtForm one = new TtForm(3, new long[]{0xFFL}, "test-one", 1.0);
        assertEvalParity(one, TtToBddConverter.convert(one));
    }

    /** Constant-zero collapses the BDD to terminal 0 — the root is explicit. */
    @Test
    void convertConstantZero() {
        TtForm zero = new TtForm(3, new long[]{0L}, "test-zero", 1.0);
        assertEvalParity(zero, TtToBddConverter.convert(zero));
    }

    @Test
    void convertMatchesBirCompiler() {
        TtForm tt = parity(5);
        BddForm viaConverter = TtToBddConverter.convert(tt);
        BddForm viaCompiler = BirCompiler.ttToBdd(tt);
        assertThat(viaConverter.equivalentTo(viaCompiler)).isTrue();
        assertThat(viaConverter.contentHash()).isEqualTo(viaCompiler.contentHash());
    }

    @Test
    void convertIsExactAndPreservesArity() {
        BddForm bdd = TtToBddConverter.convert(parity(6));
        assertThat(bdd.form()).isEqualTo("bdd");
        assertThat(bdd.fidelity()).isEqualTo(1.0);
        assertThat(bdd.inputBits()).isEqualTo(6);
        assertThat(bdd.outputBits()).isEqualTo(1);
    }

    @Test
    void fromLegacyParity() {
        int k = 4;
        BitSet bits = new BitSet(1 << k);
        for (int i = 0; i < (1 << k); i++) {
            if (Integer.bitCount(i) % 2 == 1) bits.set(i);
        }
        TruthTable legacy = TruthTable.of(k, bits);
        BddForm bdd = TtToBddConverter.fromLegacy(legacy);
        long[] out = new long[1];
        for (int i = 0; i < (1 << k); i++) {
            bdd.eval(new long[]{i}, out);
            assertThat(out[0]).as("input %d", i).isEqualTo(legacy.evaluate(i) ? 1L : 0L);
        }
    }

    @Test
    void fromLegacyConstantOne() {
        BitSet bits = new BitSet(8);
        bits.set(0, 8);
        TruthTable legacy = TruthTable.of(3, bits);
        BddForm bdd = TtToBddConverter.fromLegacy(legacy);
        long[] out = new long[1];
        for (int i = 0; i < 8; i++) {
            bdd.eval(new long[]{i}, out);
            assertThat(out[0]).isOne();
        }
    }

    @Test
    void fromLegacyConstantZero() {
        TruthTable legacy = TruthTable.of(3, new BitSet(8));
        BddForm bdd = TtToBddConverter.fromLegacy(legacy);
        long[] out = new long[1];
        for (int i = 0; i < 8; i++) {
            bdd.eval(new long[]{i}, out);
            assertThat(out[0]).isZero();
        }
    }

    @Test
    void fromLegacyMatchesConvert() {
        int k = 3;
        BitSet bits = new BitSet(1 << k);
        for (int i = 0; i < (1 << k); i++) {
            if (Integer.bitCount(i) % 2 == 1) bits.set(i);
        }
        TruthTable legacy = TruthTable.of(k, bits);
        BddForm fromLegacy = TtToBddConverter.fromLegacy(legacy);
        BddForm fromTt = TtToBddConverter.convert(TtForm.fromTruthTable(legacy));
        assertThat(fromLegacy.equivalentTo(fromTt)).isTrue();
    }
}
