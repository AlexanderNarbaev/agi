package io.matrix.bir.convert;

import io.matrix.bir.BddForm;
import io.matrix.bir.BirCompiler;
import io.matrix.bir.TtForm;
import io.matrix.neuron.TruthTable;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link BddToTtConverter}: exact BDD → TT reconstruction,
 * legacy conversion, arity limit, and round-trip fidelity — including
 * functions whose BDD reduction eliminates levels (level-skips) or collapses
 * the whole diagram to a terminal (constants).
 */
class BddToTtConverterTest {

    private static TtForm parity(int k) {
        int size = 1 << k;
        long[] table = new long[(size + 63) / 64];
        for (int i = 0; i < size; i++) {
            if (Integer.bitCount(i) % 2 == 1) table[i >>> 6] |= (1L << (i & 63));
        }
        return new TtForm(k, table, "test-parity", 1.0);
    }

    @Test
    void convertParityRoundTrip() {
        TtForm tt = parity(4);
        TtForm back = BddToTtConverter.convert(BirCompiler.ttToBdd(tt));
        assertThat(back.table()).isEqualTo(tt.table());
        assertThat(back.k()).isEqualTo(tt.k());
    }

    @Test
    void convertMajority3RoundTrip() {
        // majority-3 has no reduced-level skips: every subtree depends on
        // all variables below its root.
        TtForm tt = new TtForm(3, new long[]{(1L << 3) | (1L << 5) | (1L << 6) | (1L << 7)},
                "test-maj3", 1.0);
        TtForm back = BddToTtConverter.convert(TtToBddConverter.convert(tt));
        assertThat(back.table()).isEqualTo(tt.table());
    }

    @Test
    void convertConstantOneRoundTrip() {
        TtForm one = new TtForm(3, new long[]{0xFFL}, "test-one", 1.0);
        assertThat(BddToTtConverter.convert(BirCompiler.ttToBdd(one)).table())
                .isEqualTo(one.table());
    }

    @Test
    void convertRoundTripWithLevelSkips() {
        // f(x0..x3) = x0 & x3: variables x1, x2 are irrelevant, so the unique
        // table reduction eliminates their levels — the eval walk must follow
        // each node's own variable index, not its depth.
        long table = 0;
        for (int i = 0; i < 16; i++) {
            if ((i & 1) == 1 && (i & 8) == 8) table |= (1L << i);
        }
        TtForm tt = new TtForm(4, new long[]{table}, "test-skip", 1.0);
        TtForm back = BddToTtConverter.convert(BirCompiler.ttToBdd(tt));
        assertThat(back.table()).isEqualTo(tt.table());
    }

    @Test
    void convertConstantZeroRoundTrip() {
        TtForm zero = new TtForm(3, new long[]{0L}, "test-zero", 1.0);
        assertThat(BddToTtConverter.convert(BirCompiler.ttToBdd(zero)).table())
                .isEqualTo(zero.table());
    }

    @Test
    void convertTagsProvenanceAndFidelity() {
        TtForm back = BddToTtConverter.convert(BirCompiler.ttToBdd(parity(3)));
        assertThat(back.provenance()).isEqualTo("bdd-to-tt-converter");
        assertThat(back.fidelity()).isEqualTo(1.0);
    }

    @Test
    void convertRejectsArityAbove20() {
        BddForm.Builder builder = new BddForm.Builder();
        int root = builder.mk(0, 0, 1);
        BddForm bdd = builder.build(21, "test-large-k", root);
        assertThatThrownBy(() -> BddToTtConverter.convert(bdd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("21");
    }

    @Test
    void toLegacyMatchesTruthTable() {
        int k = 3;
        TtForm tt = parity(k);
        TruthTable legacy = BddToTtConverter.toLegacy(BirCompiler.ttToBdd(tt));
        BitSet expected = new BitSet(1 << k);
        for (int i = 0; i < (1 << k); i++) {
            if (Integer.bitCount(i) % 2 == 1) expected.set(i);
        }
        assertThat(legacy).isEqualTo(TruthTable.of(k, expected));
        for (int i = 0; i < (1 << k); i++) {
            assertThat(legacy.evaluate(i)).isEqualTo(Integer.bitCount(i) % 2 == 1);
        }
    }
}
