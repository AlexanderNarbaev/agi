package io.matrix.neuron;

import java.util.BitSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Binary decision tree representing a Boolean function for an MPDT neuron.
 *
 * <p>Ref: L1_MPDT_neuron.md §3.2
 *
 * <p>Internal nodes ({@link Split}) test a single input bit; leaves ({@link Leaf})
 * return a constant value. No path checks the same bit twice.
 */
@Deprecated(since = "2.2.0", forRemoval = true)
@SuppressWarnings("removal")
public sealed interface DecisionTree permits DecisionTree.Leaf, DecisionTree.Split {

    int K_MAX = TruthTable.K_MAX;

    /**
     * Evaluates the tree for the given input vector.
     *
     * @param input bit vector where {@code input.get(i)} is the i-th input signal
     * @return tree output for this input
     */
    boolean evaluate(BitSet input);

    /**
     * Compiles this tree into an equivalent truth table using {@link #inputCount()}
     * as the number of inputs.
     *
     * @return the compiled truth table
     */
    TruthTable toTruthTable();

    /**
     * Compiles this tree into an equivalent truth table with exactly {@code k} inputs.
     *
     * <p>If the tree references fewer than {@code k} bits, the output is
     * constant with respect to the unused bits.
     *
     * @param k number of inputs, 1..K_MAX
     * @return the compiled truth table of size {@code 2^k}
     */
    TruthTable toTruthTable(int k);

    /**
     * Returns the number of input variables (k) this tree operates on.
     */
    int inputCount();

    /**
     * Returns the maximum depth of this tree.
     */
    int depth();

    /**
     * Validates tree invariants: no repeated bit checks on a path, depth ≤ k.
     *
     * @throws IllegalStateException if any invariant is violated
     */
    void validate();

    /**
     * A leaf node returning a constant Boolean value.
     */
    record Leaf(boolean value) implements DecisionTree {

        @Override
        public boolean evaluate(BitSet input) {
            return value;
        }

        @Override
        public TruthTable toTruthTable() {
            return compile(1);
        }

        @Override
        public TruthTable toTruthTable(int k) {
            return compile(k);
        }

        /**
         * Compiles this leaf to a truth table for {@code k} inputs.
         */
        public TruthTable compile(int k) {
            if (k < 1 || k > K_MAX) {
                throw new IllegalArgumentException("k must be in [1, " + K_MAX + "], got: " + k);
            }
            int size = 1 << k;
            BitSet table = new BitSet(size);
            if (value) {
                table.set(0, size);
            }
            return TruthTable.of(k, table);
        }

        @Override
        public int inputCount() {
            return 0;
        }

        @Override
        public int depth() {
            return 0;
        }

        @Override
        public void validate() {
        }

        @Override
        public String toString() {
            return value ? "1" : "0";
        }
    }

    /**
     * An internal node that tests a single input bit and branches.
     *
     * @param inputIndex the index of the bit to test (0-based, LSB)
     * @param leftChild  subtree for bit == 0
     * @param rightChild subtree for bit == 1
     */
    record Split(int inputIndex, DecisionTree leftChild, DecisionTree rightChild)
            implements DecisionTree {

        public Split {
            Objects.requireNonNull(leftChild, "leftChild");
            Objects.requireNonNull(rightChild, "rightChild");
        }

        @Override
        public boolean evaluate(BitSet input) {
            if (!input.get(inputIndex)) {
                return leftChild.evaluate(input);
            } else {
                return rightChild.evaluate(input);
            }
        }

        @Override
        public TruthTable toTruthTable() {
            int k = inputCount();
            if (k < 1) {
                throw new IllegalStateException("inputCount is 0; add at least one Split.");
            }
            return toTruthTable(k);
        }

        @Override
        public TruthTable toTruthTable(int k) {
            if (k < 1 || k > K_MAX) {
                throw new IllegalArgumentException("k must be in [1, " + K_MAX + "], got: " + k);
            }
            int size = 1 << k;
            BitSet table = new BitSet(size);
            for (int i = 0; i < size; i++) {
                if (evaluateIndex(i, k)) {
                    table.set(i);
                }
            }
            return TruthTable.of(k, table);
        }

        /**
         * Evaluates the tree at a specific integer-encoded input.
         */
        boolean evaluateIndex(int input, int k) {
            int index = input & ((1 << k) - 1);
            return evaluateBitEncoded(index);
        }

        private boolean evaluateBitEncoded(int x) {
            DecisionTree node = this;
            while (true) {
                if (node instanceof Leaf leaf) {
                    return leaf.value();
                }
                Split split = (Split) node;
                if ((x & (1 << split.inputIndex)) == 0) {
                    node = split.leftChild;
                } else {
                    node = split.rightChild;
                }
            }
        }

        @Override
        public int inputCount() {
            return Math.max(
                    Math.max(leftChild.inputCount(), rightChild.inputCount()),
                    inputIndex + 1);
        }

        @Override
        public int depth() {
            return 1 + Math.max(leftChild.depth(), rightChild.depth());
        }

        @Override
        public void validate() {
            leftChild.validate();
            rightChild.validate();
            validatePathBitSet(new java.util.HashSet<>());
            if (depth() > inputCount()) {
                throw new IllegalStateException(
                        "Tree depth " + depth() + " exceeds input count " + inputCount());
            }
        }

        void validatePathBitSet(Set<Integer> seen) {
            if (!seen.add(inputIndex)) {
                throw new IllegalStateException(
                        "Bit " + inputIndex + " tested twice on a path");
            }
            if (leftChild instanceof Split leftSplit) {
                leftSplit.validatePathBitSet(new java.util.HashSet<>(seen));
            }
            if (rightChild instanceof Split rightSplit) {
                rightSplit.validatePathBitSet(new java.util.HashSet<>(seen));
            }
        }

        @Override
        public String toString() {
            return "(" + inputIndex + " ? " + rightChild + " : " + leftChild + ")";
        }
    }

    /**
     * Creates a random decision tree (non-deterministic, uses ThreadLocalRandom).
     *
     * @param k        number of inputs, 1..K_MAX
     * @param maxDepth maximum depth of the generated tree
     * @return a random decision tree
     */
    static DecisionTree random(int k, int maxDepth) {
        return random(k, maxDepth, ThreadLocalRandom.current());
    }

    /**
     * Creates a random decision tree using the provided RNG.
     *
     * @param k        number of inputs, 1..K_MAX
     * @param maxDepth maximum depth of the generated tree
     * @param rng      random number generator (seeded for reproducibility)
     * @return a random decision tree
     */
    static DecisionTree random(int k, int maxDepth, Random rng) {
        if (k < 1 || k > K_MAX) {
            throw new IllegalArgumentException("k must be in [1, " + K_MAX + "], got: " + k);
        }
        if (k == 1 || maxDepth <= 0) {
            return new Leaf(rng.nextBoolean());
        }
        DecisionTree tree = randomTree(k, Math.min(maxDepth, k), rng, new java.util.HashSet<>());
        if (tree instanceof Leaf leaf) {
            int bit = rng.nextInt(k);
            return new Split(bit, new Leaf(rng.nextBoolean()), new Leaf(rng.nextBoolean()));
        }
        return tree;
    }

    private static DecisionTree randomTree(int k, int maxDepth, Random rng,
                                            Set<Integer> usedBits) {
        if (maxDepth <= 0 || usedBits.size() >= k) {
            return new Leaf(rng.nextBoolean());
        }
        int remainingBits = k - usedBits.size();
        boolean forceLeaf = rng.nextInt(Math.max(remainingBits, maxDepth)) == 0;
        if (forceLeaf) {
            return new Leaf(rng.nextBoolean());
        }

        var availableBits = IntStream.range(0, k)
                .filter(i -> !usedBits.contains(i))
                .boxed()
                .collect(Collectors.toList());
        int chosenBit = availableBits.get(rng.nextInt(availableBits.size()));

        var newUsedBits = new java.util.HashSet<>(usedBits);
        newUsedBits.add(chosenBit);

        DecisionTree left = randomTree(k, maxDepth - 1, rng, newUsedBits);
        DecisionTree right = randomTree(k, maxDepth - 1, rng, newUsedBits);
        return new Split(chosenBit, left, right);
    }

    /**
     * Creates a random decision tree with max depth equal to {@code k}.
     */
    static DecisionTree random(int k) {
        return random(k, k);
    }

    /**
     * Creates a constant tree (always returns the given value).
     */
    static DecisionTree constant(boolean value) {
        return new Leaf(value);
    }
}
