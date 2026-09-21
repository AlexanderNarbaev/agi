package io.matrix.neuron;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.BitSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DecisionTreeTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 8})
    void randomTreeShouldRespectKAndMaxDepth(int k) {
        DecisionTree tree = DecisionTree.random(k, k);
        assertThat(tree.inputCount()).isLessThanOrEqualTo(k);
        assertThat(tree.depth()).isLessThanOrEqualTo(k);
        tree.validate();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 21, 100})
    void shouldRejectInvalidK(int k) {
        assertThatThrownBy(() -> DecisionTree.random(k))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void leafShouldEvaluateCorrectly() {
        DecisionTree trueLeaf = DecisionTree.constant(true);
        DecisionTree falseLeaf = DecisionTree.constant(false);

        assertThat(trueLeaf.evaluate(new BitSet())).isTrue();
        assertThat(falseLeaf.evaluate(new BitSet())).isFalse();
    }

    @Test
    void leafToTruthTableShouldBeConstant() {
        for (int k = 1; k <= 4; k++) {
            TruthTable ttTrue = new DecisionTree.Leaf(true).compile(k);
            TruthTable ttFalse = new DecisionTree.Leaf(false).compile(k);

            assertThat(ttTrue.isConstant()).as("k=%d true", k).isTrue();
            assertThat(ttFalse.isConstant()).as("k=%d false", k).isTrue();
            assertThat(ttTrue.evaluate(0)).isTrue();
            assertThat(ttFalse.evaluate(0)).isFalse();
        }
    }

    @Test
    void leafToTruthTableShouldReturnConstantTable() {
        DecisionTree.Leaf leafTrue = new DecisionTree.Leaf(true);
        DecisionTree.Leaf leafFalse = new DecisionTree.Leaf(false);

        TruthTable ttTrue = leafTrue.toTruthTable();
        TruthTable ttFalse = leafFalse.toTruthTable();

        assertThat(ttTrue.k()).isEqualTo(1);
        assertThat(ttFalse.k()).isEqualTo(1);
        assertThat(ttTrue.isConstant()).isTrue();
        assertThat(ttFalse.isConstant()).isTrue();
    }

    @Test
    void simpleTreeWithOneSplit() {
        DecisionTree tree = new DecisionTree.Split(0,
                new DecisionTree.Leaf(false),
                new DecisionTree.Leaf(true));

        assertThat(tree.inputCount()).isEqualTo(1);
        assertThat(tree.depth()).isEqualTo(1);

        assertThat(tree.evaluate(bs(1, 0b0))).isFalse();
        assertThat(tree.evaluate(bs(1, 0b1))).isTrue();
    }

    @Test
    void allInputsTreeK2() {
        DecisionTree tree = DecisionTree.random(2, 2);
        TruthTable tt = tree.toTruthTable();
        tree.validate();

        for (int input = 0; input < 4; input++) {
            BitSet bs = toBitSet(input, 2);
            assertThat(tree.evaluate(bs))
                    .as("k=2, input=%d", input)
                    .isEqualTo(tt.evaluate(input));
        }
    }

    @Test
    void allInputsTreeK3() {
        DecisionTree tree = DecisionTree.random(3, 3);
        TruthTable tt = tree.toTruthTable();
        tree.validate();

        for (int input = 0; input < 8; input++) {
            BitSet bs = toBitSet(input, 3);
            assertThat(tree.evaluate(bs))
                    .as("k=3, input=%d", input)
                    .isEqualTo(tt.evaluate(input));
        }
    }

    @Test
    void allInputsTreeK4() {
        DecisionTree tree = DecisionTree.random(4, 4);
        TruthTable tt = tree.toTruthTable();
        tree.validate();

        for (int input = 0; input < 16; input++) {
            BitSet bs = toBitSet(input, 4);
            assertThat(tree.evaluate(bs))
                    .as("k=4, input=%d", input)
                    .isEqualTo(tt.evaluate(input));
        }
    }

    @Test
    void toTruthTableShouldBeConsistentWithEvaluate() {
        for (int k = 1; k <= 4; k++) {
            for (int trial = 0; trial < 10; trial++) {
                DecisionTree tree = DecisionTree.random(k, k);
                TruthTable tt = tree.toTruthTable();

                int size = 1 << k;
                for (int input = 0; input < size; input++) {
                    BitSet bs = toBitSet(input, k);
                    assertThat(tree.evaluate(bs))
                            .as("k=%d, trial=%d, input=%d", k, trial, input)
                            .isEqualTo(tt.evaluate(input));
                }
            }
        }
    }

    @Test
    void trivialXorTree() {
        DecisionTree tree = new DecisionTree.Split(0,
                new DecisionTree.Split(1,
                        new DecisionTree.Leaf(false),
                        new DecisionTree.Leaf(true)),
                new DecisionTree.Split(1,
                        new DecisionTree.Leaf(true),
                        new DecisionTree.Leaf(false)));

        assertThat(tree.inputCount()).isEqualTo(2);
        tree.validate();

        TruthTable tt = tree.toTruthTable();
        assertThat(tt.evaluate(0b00)).isFalse();
        assertThat(tt.evaluate(0b01)).isTrue();
        assertThat(tt.evaluate(0b10)).isTrue();
        assertThat(tt.evaluate(0b11)).isFalse();
    }

    @Test
    void validateShouldRejectRepeatedBits() {
        DecisionTree tree = new DecisionTree.Split(0,
                new DecisionTree.Split(0,
                        new DecisionTree.Leaf(false),
                        new DecisionTree.Leaf(true)),
                new DecisionTree.Leaf(false));

        assertThatThrownBy(tree::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Bit 0 tested twice");
    }

    @Test
    void validateShouldRejectRepeatedBitsInDeepTree() {
        DecisionTree tree = buildDeepTree(3, 5);
        assertThatThrownBy(tree::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Bit");
    }

    @Test
    void depthCalculation() {
        DecisionTree leaf = new DecisionTree.Leaf(true);
        assertThat(leaf.depth()).isEqualTo(0);

        DecisionTree oneLevel = new DecisionTree.Split(0, leaf, leaf);
        assertThat(oneLevel.depth()).isEqualTo(1);

        DecisionTree twoLevels = new DecisionTree.Split(0, oneLevel, leaf);
        assertThat(twoLevels.depth()).isEqualTo(2);
    }

    @Test
    void constantTreeShouldWork() {
        DecisionTree tree = DecisionTree.constant(true);
        assertThat(tree).isInstanceOf(DecisionTree.Leaf.class);
        assertThat(tree.evaluate(new BitSet())).isTrue();
    }

    @Test
    void randomTreesShouldPassValidation() {
        for (int k = 1; k <= 8; k++) {
            for (int trial = 0; trial < 20; trial++) {
                DecisionTree tree = DecisionTree.random(k, k);
                tree.validate();
                assertThat(tree.depth()).isLessThanOrEqualTo(k);
                assertThat(tree.inputCount()).isLessThanOrEqualTo(k);
            }
        }
    }

    @Test
    void splitToString() {
        DecisionTree tree = new DecisionTree.Split(0,
                new DecisionTree.Leaf(false),
                new DecisionTree.Leaf(true));
        assertThat(tree.toString()).contains("0").contains("?");

        DecisionTree.Leaf leaf = new DecisionTree.Leaf(true);
        assertThat(leaf.toString()).isEqualTo("1");

        DecisionTree.Leaf leafFalse = new DecisionTree.Leaf(false);
        assertThat(leafFalse.toString()).isEqualTo("0");
    }

    private static DecisionTree buildDeepTree(int k, int depth) {
        DecisionTree leaf = new DecisionTree.Leaf(true);
        DecisionTree current = leaf;
        for (int d = 1; d <= depth; d++) {
            int bit = d % k;
            current = new DecisionTree.Split(bit, new DecisionTree.Leaf(false), current);
        }
        return current;
    }

    private static BitSet bs(int k, int value) {
        return toBitSet(value, k);
    }

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
