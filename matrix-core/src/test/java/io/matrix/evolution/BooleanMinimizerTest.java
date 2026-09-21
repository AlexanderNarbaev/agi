package io.matrix.evolution;

import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.TruthTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.BitSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BooleanMinimizerTest {

    // ─── XOR (2-input XOR embedded in k=4) ────────────────────────────────

    @Test
    void minimizeXorK4ShouldProduceTwoImplicants() {
        BitSet table = xor2InK4();
        List<int[]> result = BooleanMinimizer.minimize(table, 4);

        assertThat(result).hasSize(2);
        assertThat(BooleanMinimizer.isEquivalent(table, result, 4)).isTrue();
    }

    @Test
    void xorImplicantsShouldBeEssentialPair() {
        List<int[]> result = BooleanMinimizer.minimize(xor2InK4(), 4);
        // Each implicant must have exactly 2 fixed literals (bits 0,1) and
        // 2 don't-cares (bits 2,3).
        for (int[] imp : result) {
            assertThat(imp).hasSize(4);
            long cared = java.util.Arrays.stream(imp).filter(v -> v != BooleanMinimizer.DONT_CARE).count();
            assertThat(cared).isEqualTo(2);
            assertThat(imp[2]).isEqualTo(BooleanMinimizer.DONT_CARE);
            assertThat(imp[3]).isEqualTo(BooleanMinimizer.DONT_CARE);
        }
        // The two implicants must be complementary on bits 0 and 1.
        int[] a = result.get(0);
        int[] b = result.get(1);
        assertThat(a[0]).isNotEqualTo(b[0]);
        assertThat(a[1]).isNotEqualTo(b[1]);
    }

    private static BitSet xor2InK4() {
        int size = 1 << 4;
        BitSet table = new BitSet(size);
        for (int i = 0; i < size; i++) {
            boolean bit0 = (i & 1) != 0;
            boolean bit1 = (i & 2) != 0;
            if (bit0 ^ bit1) {
                table.set(i);
            }
        }
        return table;
    }

    // ─── AND (4-input AND) ────────────────────────────────────────────────

    @Test
    void minimizeAndK4ShouldProduceOneImplicant() {
        int size = 1 << 4;
        BitSet table = new BitSet(size);
        table.set(0b1111);                       // only all-ones input is true
        List<int[]> result = BooleanMinimizer.minimize(table, 4);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsExactly(1, 1, 1, 1);
        assertThat(BooleanMinimizer.isEquivalent(table, result, 4)).isTrue();
    }

    // ─── Constant true / false ─────────────────────────────────────────────

    @Test
    void minimizeConstantTrueReturnsSingleEmptyImplicant() {
        int k = 4;
        BitSet table = new BitSet(1 << k);
        table.set(0, 1 << k);
        List<int[]> result = BooleanMinimizer.minimize(table, k);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEmpty();
        assertThat(BooleanMinimizer.isEquivalent(table, result, k)).isTrue();
    }

    @Test
    void minimizeConstantFalseReturnsEmptyList() {
        BitSet table = new BitSet(1 << 4);
        List<int[]> result = BooleanMinimizer.minimize(table, 4);

        assertThat(result).isEmpty();
        assertThat(BooleanMinimizer.isEquivalent(table, result, 4)).isTrue();
    }

    // ─── Random function equivalence ──────────────────────────────────────

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    void minimizeRandomFunctionShouldBeEquivalent(int k) {
        Random rng = new Random(k * 1234567L);
        int size = 1 << k;
        BitSet table = new BitSet(size);
        for (int i = 0; i < size; i++) {
            if (rng.nextBoolean()) {
                table.set(i);
            }
        }
        // Skip trivial constant functions (already covered above).
        int ones = table.cardinality();
        if (ones == 0 || ones == size) {
            return;
        }
        List<int[]> result = BooleanMinimizer.minimize(table, k);
        assertThat(BooleanMinimizer.isEquivalent(table, result, k))
                .as("k=%d", k)
                .isTrue();
    }

    // ─── Compression ratio ────────────────────────────────────────────────

    @Test
    void compressionRatioShouldBePositiveForCompressibleFunction() {
        // AND of 4 inputs: 1 implicant × 4 literals vs 16-bit table.
        BitSet table = new BitSet(1 << 4);
        table.set(0b1111);
        List<int[]> minimized = BooleanMinimizer.minimize(table, 4);
        double ratio = BooleanMinimizer.compressionRatio(table, minimized);
        assertThat(ratio).isGreaterThan(0.0);
    }

    @Test
    void compressionRatioShouldBeOneForConstantTrue() {
        BitSet table = new BitSet(1 << 4);
        table.set(0, 1 << 4);
        List<int[]> minimized = BooleanMinimizer.minimize(table, 4);
        assertThat(BooleanMinimizer.compressionRatio(table, minimized)).isEqualTo(1.0);
    }

    // ─── toDecisionTree ───────────────────────────────────────────────────

    @Test
    void toDecisionTreeShouldMatchOriginalTable() {
        List<int[]> implicants = BooleanMinimizer.minimize(xor2InK4(), 4);
        DecisionTree tree = BooleanMinimizer.toDecisionTree(implicants, 4);

        BitSet original = xor2InK4();
        for (int i = 0; i < (1 << 4); i++) {
            BitSet input = toBitSet(i, 4);
            assertThat(tree.evaluate(input))
                    .as("input=%d", i)
                    .isEqualTo(original.get(i));
        }
    }

    @Test
    void toDecisionTreeConstantFalse() {
        DecisionTree tree = BooleanMinimizer.toDecisionTree(List.of(), 3);
        for (int i = 0; i < (1 << 3); i++) {
            assertThat(tree.evaluate(toBitSet(i, 3))).isFalse();
        }
    }

    @Test
    void toDecisionTreeConstantTrue() {
        DecisionTree tree = BooleanMinimizer.toDecisionTree(List.of(new int[]{}), 3);
        for (int i = 0; i < (1 << 3); i++) {
            assertThat(tree.evaluate(toBitSet(i, 3))).isTrue();
        }
    }

    // ─── Performance ──────────────────────────────────────────────────────

    @Test
    void minimizeK16ShouldCompleteUnder100ms() {
        // AND of all 16 bits: a single minterm (index 0xFFFF) minimises to
        // one implicant [1,1,...,1]. Verifies k=16 table traversal is fast.
        int k = 16;
        int size = 1 << k;
        BitSet table = new BitSet(size);
        table.set(size - 1);

        long start = System.nanoTime();
        List<int[]> result = BooleanMinimizer.minimize(table, k);
        long elapsed = System.nanoTime() - start;
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(elapsed);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).hasSize(k);
        assertThat(result.get(0)[0]).isEqualTo(1);
        assertThat(result.get(0)[1]).isEqualTo(1);
        assertThat(BooleanMinimizer.isEquivalent(table, result, k)).isTrue();
        assertThat(elapsedMs)
                .as("minimize k=16 should complete in <100ms, took %dms", elapsedMs)
                .isLessThan(100);
    }

    // ─── Validation ────────────────────────────────────────────────────────

    @Test
    void shouldRejectInvalidK() {
        assertThatThrownBy(() -> BooleanMinimizer.minimize(new BitSet(), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BooleanMinimizer.minimize(new BitSet(), 21))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BooleanMinimizer.minimize(null, 1))
                .isInstanceOf(NullPointerException.class);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private static BitSet toBitSet(int value, int bits) {
        BitSet bs = new BitSet(bits);
        for (int i = 0; i < bits; i++) {
            if ((value & (1 << i)) != 0) {
                bs.set(i);
            }
        }
        return bs;
    }
}
