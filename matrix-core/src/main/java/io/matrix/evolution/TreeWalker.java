package io.matrix.evolution;

import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.DecisionTree.Leaf;
import io.matrix.neuron.DecisionTree.Split;

import java.util.HashSet;
import java.util.Set;

/**
 * Utility methods for traversing and inspecting {@link DecisionTree} nodes.
 *
 * <p>Ref: L5_DNA.md §2.1
 */
public final class TreeWalker {

    private TreeWalker() {}

    public static int countLeaves(DecisionTree tree) {
        return switch (tree) {
            case Leaf ignored -> 1;
            case Split split -> countLeaves(split.leftChild()) + countLeaves(split.rightChild());
        };
    }

    public static int countSplits(DecisionTree tree) {
        return switch (tree) {
            case Leaf ignored -> 0;
            case Split split -> 1 + countSplits(split.leftChild()) + countSplits(split.rightChild());
        };
    }

    public static int totalNodes(DecisionTree tree) {
        return countLeaves(tree) + countSplits(tree);
    }

    /**
     * Collects all input indices tested on the path from root to the target node.
     * Returns the set of bits that must NOT be reused near this node.
     */
    public static Set<Integer> pathBits(DecisionTree root, int targetLeafIndex) {
        return collectPathBits(root, targetLeafIndex, 0, new HashSet<>()).bits();

    }

    private record PathResult(Set<Integer> bits, int leafCount, boolean found) {}

    private static PathResult collectPathBits(DecisionTree node, int target, int leafCount,
                                               Set<Integer> currentBits) {
        return switch (node) {
            case Leaf ignored -> {
                if (leafCount == target) {
                    yield new PathResult(new HashSet<>(currentBits), leafCount + 1, true);
                }
                yield new PathResult(currentBits, leafCount + 1, false);
            }
            case Split split -> {
                var leftBits = new HashSet<>(currentBits);
                leftBits.add(split.inputIndex());
                var leftResult = collectPathBits(split.leftChild(), target, leafCount, leftBits);
                if (leftResult.found()) {
                    yield leftResult;
                }
                var rightBits = new HashSet<>(currentBits);
                rightBits.add(split.inputIndex());
                yield collectPathBits(split.rightChild(), target, leftResult.leafCount(), rightBits);
            }
        };
    }

    /**
     * Collects path bits from root to a specific split node (by preorder index among splits).
     */
    public static Set<Integer> pathBitsToSplit(DecisionTree root, int targetSplitIndex) {
        var result = collectSplitPathBits(root, targetSplitIndex, 0, new HashSet<>());
        return result.bits();
    }

    private record SplitPathResult(Set<Integer> bits, int splitCount, boolean found) {}

    private static SplitPathResult collectSplitPathBits(DecisionTree node, int target,
                                                         int splitCount, Set<Integer> currentBits) {
        return switch (node) {
            case Leaf ignored -> new SplitPathResult(currentBits, splitCount, false);
            case Split split -> {
                if (splitCount == target) {
                    yield new SplitPathResult(new HashSet<>(currentBits), splitCount + 1, true);
                }
                var newBits = new HashSet<>(currentBits);
                newBits.add(split.inputIndex());
                var leftResult = collectSplitPathBits(split.leftChild(), target,
                        splitCount + 1, newBits);
                if (leftResult.found()) yield leftResult;
                yield collectSplitPathBits(split.rightChild(), target,
                        leftResult.splitCount(), newBits);
            }
        };
    }

    /**
     * Counts the number of pruneable splits (both children are leaves with same value).
     */
    public static int countPruneable(DecisionTree tree) {
        return switch (tree) {
            case Leaf ignored -> 0;
            case Split split -> {
                int count = countPruneable(split.leftChild()) + countPruneable(split.rightChild());
                if (split.leftChild() instanceof Leaf left
                        && split.rightChild() instanceof Leaf right
                        && left.value() == right.value()) {
                    count++;
                }
                yield count;
            }
        };
    }
}
