package io.matrix.bir;

import io.matrix.neuron.DecisionTree;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link DecisionTreeAdapter}: legacy {@code DecisionTree} → BIR
 * {@link TtForm} by exhaustive evaluation.
 *
 * <p>The adapter is one-directional (legacy → BIR). The reverse direction is
 * covered indirectly: the adapted TT must agree with the tree's own
 * {@code toTruthTable(k)} compilation, which anchors both representations of
 * the legacy artifact to the same function.
 */
class DecisionTreeAdapterTest {

    /** Tree for f = x0 AND x1: Split(x0, 0, Split(x1, 0, 1)). */
    private static DecisionTree and2() {
        return new DecisionTree.Split(0,
                new DecisionTree.Leaf(false),
                new DecisionTree.Split(1, new DecisionTree.Leaf(false), new DecisionTree.Leaf(true)));
    }

    private static void assertParity(DecisionTree dt, TtForm form) {
        long[] out = new long[1];
        for (int i = 0; i < (1 << form.k()); i++) {
            form.eval(new long[]{i}, out);
            boolean expected = dt.evaluate(BitSet.valueOf(new long[]{i}));
            assertThat(out[0]).as("input %d", i).isEqualTo(expected ? 1L : 0L);
        }
    }

    @Test
    void toBirAnd2Parity() {
        TtForm form = DecisionTreeAdapter.toBir(and2(), 2);
        assertThat(form.table()).isEqualTo(new long[]{0b1000L});
        assertParity(and2(), form);
    }

    @Test
    void toBirMetadata() {
        TtForm form = DecisionTreeAdapter.toBir(and2(), 2);
        assertThat(form.k()).isEqualTo(2);
        assertThat(form.fidelity()).isEqualTo(1.0);
        assertThat(form.provenance()).isEqualTo("legacy-decisiontree");
    }

    @Test
    void toBirConstantLeaves() {
        TtForm one = DecisionTreeAdapter.toBir(new DecisionTree.Leaf(true), 3);
        assertThat(one.table()).isEqualTo(new long[]{0xFFL});
        TtForm zero = DecisionTreeAdapter.toBir(new DecisionTree.Leaf(false), 3);
        assertThat(zero.table()).isEqualTo(new long[]{0L});
    }

    @Test
    void toBirRandomTreesSeeded() {
        // Deterministic: fixed seeds, no unseeded randomness.
        for (long seed : new long[]{1L, 7L, 42L, 1337L}) {
            DecisionTree dt = DecisionTree.random(4, 3, new Random(seed));
            TtForm form = DecisionTreeAdapter.toBir(dt, 4);
            assertParity(dt, form);
        }
    }

    @Test
    void toBirAgreesWithLegacyCompilation() {
        // Both directions anchored: adapter output must equal the tree's own
        // truth-table compilation evaluated bit-by-bit.
        DecisionTree dt = DecisionTree.random(5, 4, new Random(99));
        TtForm form = DecisionTreeAdapter.toBir(dt, 5);
        var legacyTt = dt.toTruthTable(5);
        long[] out = new long[1];
        for (int i = 0; i < (1 << 5); i++) {
            form.eval(new long[]{i}, out);
            assertThat(out[0]).as("input %d", i).isEqualTo(legacyTt.evaluate(i) ? 1L : 0L);
        }
    }

    @Test
    void toBirUnusedUpperBitsAreConstant() {
        // Tree uses only x0; k=3 pads with irrelevant bits.
        DecisionTree dt = new DecisionTree.Split(0,
                new DecisionTree.Leaf(false), new DecisionTree.Leaf(true));
        TtForm form = DecisionTreeAdapter.toBir(dt, 3);
        assertThat(form.table()).isEqualTo(new long[]{(long) 0b10101010});
        assertParity(dt, form);
    }

    @Test
    void toBirRejectsOutOfRangeK() {
        DecisionTree dt = and2();
        assertThatThrownBy(() -> DecisionTreeAdapter.toBir(dt, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DecisionTreeAdapter.toBir(dt, 21))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
