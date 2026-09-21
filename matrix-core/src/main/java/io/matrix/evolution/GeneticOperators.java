package io.matrix.evolution;

import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.DecisionTree.Leaf;
import io.matrix.neuron.DecisionTree.Split;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Genetic operators for mutating {@link DecisionTree} chromosomes.
 *
 * <p>All operators return new trees; originals are never modified.
 * Operators return the input tree unchanged if not applicable.
 *
 * <p>Ref: L1_MPDT_neuron.md §4.2, L5_DNA.md §2.2
 */
public final class GeneticOperators {

    private GeneticOperators() {}

    // ---- Public operators ----

    public static DecisionTree flipLeaf(Random rng, DecisionTree tree) {
        int n = TreeWalker.countLeaves(tree);
        if (n == 0) return tree;
        int target = rng.nextInt(n);
        return flipLeafAt(tree, target).tree();
    }

    public static DecisionTree splitLeaf(Random rng, DecisionTree tree, int k) {
        int n = TreeWalker.countLeaves(tree);
        if (n == 0) return tree;
        int target = rng.nextInt(n);
        Set<Integer> path = pathToLeaf(tree, target);
        var available = availableBits(k, path);
        if (available.isEmpty()) return tree;
        int newBit = available.get(rng.nextInt(available.size()));
        LeafInfo info = getLeaf(tree, target);
        DecisionTree replacement = new Split(newBit, new Leaf(info.value()), new Leaf(info.value()));
        return replaceLeafAt(tree, target, replacement);
    }

    public static DecisionTree pruneTree(Random rng, DecisionTree tree) {
        List<Split> pruneable = collectPruneableSplits(tree);
        if (pruneable.isEmpty()) return tree;
        int target = rng.nextInt(pruneable.size());
        Split s = pruneable.get(target);
        return replaceNode(tree, s, new Leaf(((Leaf) s.leftChild()).value()));
    }

    public static DecisionTree changeInput(Random rng, DecisionTree tree, int k) {
        List<Split> splits = collectSplits(tree);
        if (splits.isEmpty()) return tree;
        int target = rng.nextInt(splits.size());
        Split s = splits.get(target);

        var ancestorBits = ancestorBits(tree, s);
        var subtreeBits = bitsInSubtree(s);
        var forbidden = new HashSet<Integer>();
        forbidden.addAll(ancestorBits);
        forbidden.addAll(subtreeBits);

        var available = availableBits(k, forbidden);
        if (available.isEmpty()) return tree;
        int newBit = available.get(rng.nextInt(available.size()));
        DecisionTree replacement = new Split(newBit, s.leftChild(), s.rightChild());
        return replaceNode(tree, s, replacement);
    }

    public static DecisionTree swapChildren(Random rng, DecisionTree tree) {
        List<Split> splits = collectSplits(tree);
        if (splits.isEmpty()) return tree;
        int target = rng.nextInt(splits.size());
        Split s = splits.get(target);
        DecisionTree replacement = new Split(s.inputIndex(), s.rightChild(), s.leftChild());
        return replaceNode(tree, s, replacement);
    }

    public static DecisionTree growSubtree(Random rng, DecisionTree tree, int k) {
        int n = TreeWalker.countLeaves(tree);
        if (n == 0) return tree;
        int target = rng.nextInt(n);
        Set<Integer> path = pathToLeaf(tree, target);
        int subDepth = 1 + rng.nextInt(3);
        Set<Integer> pool = new HashSet<>();
        for (int i = 0; i < k; i++) {
            if (!path.contains(i)) pool.add(i);
        }
        DecisionTree subtree = randomSubtree(rng, subDepth, pool);
        return replaceLeafAt(tree, target, subtree);
    }

    public static DecisionTree[] crossover(Random rng, DecisionTree a, DecisionTree b) {
        List<NodeRef> nodesA = collectNodes(a);
        List<NodeRef> nodesB = collectNodes(b);
        if (nodesA.isEmpty() || nodesB.isEmpty()) return new DecisionTree[]{a, b};

        int maxAttempts = 10;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            NodeRef refA = nodesA.get(rng.nextInt(nodesA.size()));
            NodeRef refB = nodesB.get(rng.nextInt(nodesB.size()));

            DecisionTree childA = replaceNode(a, refA.node(), refB.node());
            DecisionTree childB = replaceNode(b, refB.node(), refA.node());

            if (isValid(childA) && isValid(childB)) {
                return new DecisionTree[]{childA, childB};
            }
        }
        return new DecisionTree[]{a, b};
    }

    private static boolean isValid(DecisionTree tree) {
        try {
            tree.validate();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static DecisionTree compressBranch(Random rng, DecisionTree tree) {
        return compress(tree);
    }

    // ---- Internal: node collection & path queries ----

    /** Collects all nodes (leaves and splits) with identity references. */
    private static List<NodeRef> collectNodes(DecisionTree root) {
        List<NodeRef> nodes = new ArrayList<>();
        collectNodes(root, nodes);
        return nodes;
    }

    private static void collectNodes(DecisionTree node, List<NodeRef> acc) {
        acc.add(new NodeRef(node));
        if (node instanceof Split s) {
            collectNodes(s.leftChild(), acc);
            collectNodes(s.rightChild(), acc);
        }
    }

    private record NodeRef(DecisionTree node) {
        @Override
        public boolean equals(Object o) { return this == o || (o instanceof NodeRef r && node == r.node); }
        @Override
        public int hashCode() { return System.identityHashCode(node); }
    }

    /** Collects all Split nodes (for operators that target splits). */
    private static List<Split> collectSplits(DecisionTree root) {
        List<Split> splits = new ArrayList<>();
        collectSplits(root, splits);
        return splits;
    }

    private static void collectSplits(DecisionTree node, List<Split> acc) {
        if (node instanceof Split s) {
            acc.add(s);
            collectSplits(s.leftChild(), acc);
            collectSplits(s.rightChild(), acc);
        }
    }

    /** Collects Splits whose both children are identical leaves. */
    private static List<Split> collectPruneableSplits(DecisionTree root) {
        List<Split> result = new ArrayList<>();
        collectPruneable(root, result);
        return result;
    }

    private static void collectPruneable(DecisionTree node, List<Split> acc) {
        if (node instanceof Split s) {
            if (s.leftChild() instanceof Leaf l && s.rightChild() instanceof Leaf r && l.value() == r.value()) {
                acc.add(s);
            }
            collectPruneable(s.leftChild(), acc);
            collectPruneable(s.rightChild(), acc);
        }
    }

    /** Gets all Split ancestors of a given descendant node by identity. */
    private static Set<Integer> ancestorBits(DecisionTree root, Object descendant) {
        Set<Integer> bits = new HashSet<>();
        ancestorBits(root, descendant, bits);
        return bits;
    }

    private static boolean ancestorBits(DecisionTree node, Object target, Set<Integer> acc) {
        if (node == target) return true;
        if (node instanceof Split s) {
            acc.add(s.inputIndex());
            if (ancestorBits(s.leftChild(), target, acc)) return true;
            if (ancestorBits(s.rightChild(), target, acc)) return true;
            acc.remove(s.inputIndex());
        }
        return false;
    }

    /** All bits tested in a subtree. */
    private static Set<Integer> bitsInSubtree(DecisionTree node) {
        Set<Integer> bits = new HashSet<>();
        collectBits(node, bits);
        return bits;
    }

    private static void collectBits(DecisionTree node, Set<Integer> acc) {
        if (node instanceof Split s) {
            acc.add(s.inputIndex());
            collectBits(s.leftChild(), acc);
            collectBits(s.rightChild(), acc);
        }
    }

    /** Path bits from root to the n-th leaf. */
    static Set<Integer> pathToLeaf(DecisionTree root, int targetLeaf) {
        PathCtx ctx = new PathCtx(targetLeaf);
        pathToLeaf(root, new HashSet<>(), ctx);
        return ctx.result();
    }

    private static class PathCtx {
        final int target;
        int leafCount;
        Set<Integer> result;
        PathCtx(int target) { this.target = target; }
        Set<Integer> result() { return result != null ? result : Set.of(); }
    }

    private static void pathToLeaf(DecisionTree node, Set<Integer> currentPath, PathCtx ctx) {
        if (ctx.result != null) return;
        if (node instanceof Leaf) {
            if (ctx.leafCount == ctx.target) {
                ctx.result = new HashSet<>(currentPath);
            }
            ctx.leafCount++;
            return;
        }
        Split s = (Split) node;
        currentPath.add(s.inputIndex());
        pathToLeaf(s.leftChild(), currentPath, ctx);
        if (ctx.result != null) return;
        pathToLeaf(s.rightChild(), currentPath, ctx);
        currentPath.remove(s.inputIndex());
    }

    /** Gets the n-th leaf's value. */
    private record LeafInfo(boolean value) {}

    private static LeafInfo getLeaf(DecisionTree root, int target) {
        int[] count = new int[1];
        return getLeaf(root, target, count);
    }

    private static LeafInfo getLeaf(DecisionTree node, int target, int[] count) {
        if (node instanceof Leaf l) {
            if (count[0] == target) return new LeafInfo(l.value());
            count[0]++;
            return null;
        }
        Split s = (Split) node;
        LeafInfo left = getLeaf(s.leftChild(), target, count);
        if (left != null) return left;
        return getLeaf(s.rightChild(), target, count);
    }

    /** Available bits in [0, k) that are not in the forbidden set. */
    private static List<Integer> availableBits(int k, Set<Integer> forbidden) {
        return IntStream.range(0, k)
                .filter(i -> !forbidden.contains(i))
                .boxed()
                .toList();
    }

    // ---- Internal: node replacement ----

    /**
     * Replaces a leaf at the n-th position with a new subtree.
     */
    private static DecisionTree replaceLeafAt(DecisionTree root, int target, DecisionTree replacement) {
        int[] count = new int[1];
        return replaceLeafAt(root, target, count, replacement);
    }

    private static DecisionTree replaceLeafAt(DecisionTree node, int target, int[] count,
                                               DecisionTree repl) {
        if (node instanceof Leaf) {
            if (count[0] == target) return repl;
            count[0]++;
            return node;
        }
        Split s = (Split) node;
        DecisionTree newLeft = replaceLeafAt(s.leftChild(), target, count, repl);
        if (newLeft != s.leftChild()) {
            return new Split(s.inputIndex(), newLeft, s.rightChild());
        }
        DecisionTree newRight = replaceLeafAt(s.rightChild(), target, count, repl);
        if (newRight != s.rightChild()) {
            return new Split(s.inputIndex(), s.leftChild(), newRight);
        }
        return node;
    }

    /**
     * Replaces a specific node (by identity) with a new subtree.
     */
    static DecisionTree replaceNode(DecisionTree root, DecisionTree target, DecisionTree replacement) {
        if (root == target) return replacement;
        if (root instanceof Split s) {
            DecisionTree newLeft = replaceNode(s.leftChild(), target, replacement);
            if (newLeft != s.leftChild()) {
                return new Split(s.inputIndex(), newLeft, s.rightChild());
            }
            DecisionTree newRight = replaceNode(s.rightChild(), target, replacement);
            if (newRight != s.rightChild()) {
                return new Split(s.inputIndex(), s.leftChild(), newRight);
            }
        }
        return root;
    }

    // ---- Internal: flipLeaf ----

    private record FlipResult(DecisionTree tree, int leafCount) {}

    private static FlipResult flipLeafAt(DecisionTree node, int target) {
        return flipLeafAt(node, target, 0);
    }

    private static FlipResult flipLeafAt(DecisionTree node, int target, int counter) {
        if (node instanceof Leaf l) {
            if (counter == target) {
                return new FlipResult(new Leaf(!l.value()), counter + 1);
            }
            return new FlipResult(node, counter + 1);
        }
        Split s = (Split) node;
        FlipResult left = flipLeafAt(s.leftChild(), target, counter);
        if (left.tree() != s.leftChild()) {
            return new FlipResult(
                    new Split(s.inputIndex(), left.tree(), s.rightChild()), left.leafCount());
        }
        FlipResult right = flipLeafAt(s.rightChild(), target, left.leafCount());
        if (right.tree() != s.rightChild()) {
            return new FlipResult(
                    new Split(s.inputIndex(), s.leftChild(), right.tree()), right.leafCount());
        }
        return new FlipResult(node, right.leafCount());
    }

    // ---- Internal: compress ----

    private static DecisionTree compress(DecisionTree node) {
        if (node instanceof Split s) {
            DecisionTree left = compress(s.leftChild());
            DecisionTree right = compress(s.rightChild());
            if (left instanceof Leaf l && right instanceof Leaf r && l.value() == r.value()) {
                return new Leaf(l.value());
            }
            return new Split(s.inputIndex(), left, right);
        }
        return node;
    }

    // ---- Internal: randomSubtree ----

    private static DecisionTree randomSubtree(Random rng, int maxDepth, Set<Integer> pool) {
        if (maxDepth <= 0 || pool.isEmpty()) {
            return new Leaf(rng.nextBoolean());
        }
        var bits = pool.stream().toList();
        int bit = bits.get(rng.nextInt(bits.size()));
        var smallerPool = new HashSet<>(pool);
        smallerPool.remove(bit);
        return new Split(bit,
                randomSubtree(rng, maxDepth - 1, smallerPool),
                randomSubtree(rng, maxDepth - 1, smallerPool));
    }
}
