package io.matrix.evolution;

import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.DecisionTree.Leaf;
import io.matrix.neuron.DecisionTree.Split;
import io.matrix.neuron.TruthTable;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class GeneticOperatorsTest {

    private final Random rng = new Random(42);

    @Test
    void flipLeafShouldChangeLeafValue() {
        DecisionTree tree = new Split(0, new Leaf(false), new Leaf(false));
        DecisionTree mutated = GeneticOperators.flipLeaf(rng, tree);

        assertThat(mutated).isNotEqualTo(tree);
        assertThat(TreeWalker.countLeaves(mutated)).isEqualTo(2);
    }

    @Test
    void flipLeafOnSingleLeafShouldInvertIt() {
        DecisionTree tree = new Leaf(true);
        DecisionTree mutated = GeneticOperators.flipLeaf(rng, tree);
        assertThat(mutated.evaluate(new java.util.BitSet())).isFalse();

        DecisionTree doubleMutated = GeneticOperators.flipLeaf(rng, mutated);
        assertThat(doubleMutated.evaluate(new java.util.BitSet())).isTrue();
    }

    @Test
    void flipLeafShouldPreserveTreeStructure() {
        for (int k = 2; k <= 6; k++) {
            DecisionTree tree = DecisionTree.random(k, k);
            int leavesBefore = TreeWalker.countLeaves(tree);
            int splitsBefore = TreeWalker.countSplits(tree);

            DecisionTree mutated = GeneticOperators.flipLeaf(rng, tree);
            assertThat(TreeWalker.countLeaves(mutated)).isEqualTo(leavesBefore);
            assertThat(TreeWalker.countSplits(mutated)).isEqualTo(splitsBefore);
        }
    }

    @Test
    void splitLeafShouldIncreaseTreeSize() {
        DecisionTree tree = new Leaf(true);
        DecisionTree mutated = GeneticOperators.splitLeaf(rng, tree, 3);

        assertThat(TreeWalker.countLeaves(mutated)).isGreaterThan(TreeWalker.countLeaves(tree));
        assertThat(TreeWalker.countSplits(mutated)).isGreaterThan(TreeWalker.countSplits(tree));
    }

    @Test
    void splitLeafShouldPreserveEquivalence() {
        for (int k = 2; k <= 4; k++) {
            DecisionTree tree = DecisionTree.random(k, k);
            TruthTable before = tree.toTruthTable(k);

            DecisionTree mutated = GeneticOperators.splitLeaf(rng, tree, k);
            TruthTable after = mutated.toTruthTable(k);

            assertThat(after).isEqualTo(before);
        }
    }

    @Test
    void splitLeafShouldNotReusePathBits() {
        DecisionTree tree = new Split(0,
                new Split(1, new Leaf(true), new Leaf(false)),
                new Leaf(true));
        int k = 3;
        DecisionTree mutated = GeneticOperators.splitLeaf(rng, tree, k);
        mutated.validate();
    }

    @Test
    void pruneTreeShouldReduceTreeSize() {
        DecisionTree tree = new Split(0, new Leaf(true), new Leaf(true));
        DecisionTree mutated = GeneticOperators.pruneTree(rng, tree);

        assertThat(mutated).isInstanceOf(Leaf.class);
        assertThat(((Leaf) mutated).value()).isTrue();
        assertThat(TreeWalker.countSplits(mutated)).isEqualTo(0);
    }

    @Test
    void pruneTreeShouldNotPruneNonIdenticalLeaves() {
        DecisionTree tree = new Split(0, new Leaf(true), new Leaf(false));

        DecisionTree mutated = GeneticOperators.pruneTree(rng, tree);
        assertThat(mutated).isEqualTo(tree);
    }

    @Test
    void pruneTreeShouldPreserveEquivalence() {
        for (int k = 2; k <= 4; k++) {
            DecisionTree tree = DecisionTree.random(k, k);
            int pruneable = TreeWalker.countPruneable(tree);
            if (pruneable == 0) continue;

            TruthTable before = tree.toTruthTable(k);
            DecisionTree mutated = GeneticOperators.pruneTree(rng, tree);
            TruthTable after = mutated.toTruthTable(k);

            assertThat(after).isEqualTo(before);
        }
    }

    @Test
    void changeInputShouldModifySplitBit() {
        DecisionTree tree = new Split(0, new Leaf(true), new Leaf(false));
        int k = 3;

        DecisionTree mutated = GeneticOperators.changeInput(rng, tree, k);
        assertThat(mutated).isNotEqualTo(tree);
        mutated.validate();
    }

    @Test
    void changeInputShouldNotReusePathBits() {
        DecisionTree tree = new Split(0,
                new Split(1, new Leaf(true), new Leaf(false)),
                new Leaf(false));
        int k = 3;

        for (int i = 0; i < 20; i++) {
            DecisionTree mutated = GeneticOperators.changeInput(rng, tree, k);
            mutated.validate();
        }
    }

    @Test
    void swapChildrenShouldSwap() {
        DecisionTree tree = new Split(0, new Leaf(false), new Leaf(true));
        DecisionTree mutated = GeneticOperators.swapChildren(rng, tree);

        assertThat(mutated).isNotEqualTo(tree);
        Split split = (Split) mutated;
        assertThat(((Leaf) split.leftChild()).value()).isTrue();
        assertThat(((Leaf) split.rightChild()).value()).isFalse();
    }

    @Test
    void swapChildrenShouldBeItsOwnInverse() {
        DecisionTree tree = new Split(0, new Leaf(false), new Leaf(true));
        DecisionTree once = GeneticOperators.swapChildren(rng, tree);
        DecisionTree twice = GeneticOperators.swapChildren(rng, once);

        assertThat(twice).isEqualTo(tree);
    }

    @Test
    void growSubtreeShouldIncreaseComplexity() {
        DecisionTree tree = new Leaf(true);
        int k = 4;

        DecisionTree mutated = GeneticOperators.growSubtree(rng, tree, k);
        assertThat(TreeWalker.totalNodes(mutated)).isGreaterThan(TreeWalker.totalNodes(tree));
        mutated.validate();
    }

    @Test
    void growSubtreeShouldPreserveValidation() {
        for (int k = 2; k <= 6; k++) {
            DecisionTree tree = DecisionTree.random(k, k);
            for (int i = 0; i < 10; i++) {
                DecisionTree mutated = GeneticOperators.growSubtree(rng, tree, k);
                mutated.validate();
                assertThat(mutated.inputCount()).isLessThanOrEqualTo(k);
            }
        }
    }

    @Test
    void crossoverShouldProduceValidTrees() {
        DecisionTree parent1 = DecisionTree.random(4, 4);
        DecisionTree parent2 = DecisionTree.random(4, 4);

        DecisionTree[] children = GeneticOperators.crossover(rng, parent1, parent2);

        assertThat(children).hasSize(2);
        children[0].validate();
        children[1].validate();
    }

    @Test
    void crossoverWithSingleNodesShouldWork() {
        DecisionTree parent1 = new Leaf(true);
        DecisionTree parent2 = new Leaf(false);

        DecisionTree[] children = GeneticOperators.crossover(rng, parent1, parent2);
        assertThat(children).hasSize(2);
    }

    @Test
    void compressBranchShouldSimplifyConstantSubtree() {
        DecisionTree tree = new Split(0,
                new Split(1, new Leaf(true), new Leaf(true)),
                new Leaf(false));

        DecisionTree compressed = GeneticOperators.compressBranch(rng, tree);
        assertThat(TreeWalker.totalNodes(compressed)).isLessThan(TreeWalker.totalNodes(tree));
        compressed.validate();
    }

    @Test
    void compressBranchShouldPreserveEquivalence() {
        for (int k = 2; k <= 4; k++) {
            DecisionTree tree = DecisionTree.random(k, k);
            TruthTable before = tree.toTruthTable(k);

            DecisionTree compressed = GeneticOperators.compressBranch(rng, tree);
            TruthTable after = compressed.toTruthTable(k);

            assertThat(after).isEqualTo(before);
        }
    }

    @RepeatedTest(20)
    void randomMutationSequenceShouldPreserveValidation() {
        int k = 4;
        DecisionTree tree = DecisionTree.random(k, k);

        for (int step = 0; step < 30; step++) {
            double roll = rng.nextDouble();
            if (roll < 0.20) {
                tree = GeneticOperators.flipLeaf(rng, tree);
            } else if (roll < 0.45) {
                tree = GeneticOperators.splitLeaf(rng, tree, k);
            } else if (roll < 0.60) {
                tree = GeneticOperators.pruneTree(rng, tree);
            } else if (roll < 0.75) {
                tree = GeneticOperators.changeInput(rng, tree, k);
            } else if (roll < 0.80) {
                tree = GeneticOperators.swapChildren(rng, tree);
            } else if (roll < 0.90) {
                tree = GeneticOperators.growSubtree(rng, tree, k);
            } else {
                tree = GeneticOperators.compressBranch(rng, tree);
            }
            tree.validate();
            assertThat(tree.inputCount()).isLessThanOrEqualTo(k);
            assertThat(tree.depth()).isLessThanOrEqualTo(k);
        }
    }

    @Test
    void allOperatorsShouldProduceValidTruthTables() {
        int k = 3;
        DecisionTree original = DecisionTree.random(k, k);
        DecisionTree other = DecisionTree.random(k, k);

        DecisionTree afterFlip = GeneticOperators.flipLeaf(rng, original);
        afterFlip.toTruthTable();
        afterFlip.validate();

        DecisionTree afterSplit = GeneticOperators.splitLeaf(rng, original, k);
        afterSplit.toTruthTable();
        afterSplit.validate();

        DecisionTree afterPrune = GeneticOperators.pruneTree(rng, original);
        afterPrune.toTruthTable();
        afterPrune.validate();

        DecisionTree afterChange = GeneticOperators.changeInput(rng, original, k);
        afterChange.toTruthTable();
        afterChange.validate();

        DecisionTree afterSwap = GeneticOperators.swapChildren(rng, original);
        afterSwap.toTruthTable();
        afterSwap.validate();

        DecisionTree afterGrow = GeneticOperators.growSubtree(rng, original, k);
        afterGrow.toTruthTable();
        afterGrow.validate();

        DecisionTree[] cross = GeneticOperators.crossover(rng, original, other);
        cross[0].toTruthTable();
        cross[0].validate();
        cross[1].toTruthTable();
        cross[1].validate();

        DecisionTree afterCompress = GeneticOperators.compressBranch(rng, original);
        afterCompress.toTruthTable();
        afterCompress.validate();
    }
}
