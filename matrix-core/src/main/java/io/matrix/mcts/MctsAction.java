package io.matrix.mcts;

import io.matrix.evolution.GeneticOperators;
import io.matrix.neuron.DecisionTree;

import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;

/**
 * MCTS action representing a mutation operator applied to a {@link DecisionTree}.
 *
 * <p>Each action wraps one of the 8 genetic operators from {@link GeneticOperators}.
 * Actions are immutable; applying an action returns a new tree.
 *
 * <p>Ref: L5_DNA.md §2.2 (genetic operators), Phase 4 MCTS-Guided Evolution
 */
public record MctsAction(ActionType type,
                          BiFunction<DecisionTree, Random, DecisionTree> operator) {

    private static final List<ActionType> ALL_TYPES = List.of(ActionType.values());

    /**
     * Enumerates the 8 mutation operators from {@link GeneticOperators}.
     */
    public enum ActionType {
        FLIP_LEAF,
        SPLIT_LEAF,
        PRUNE_TREE,
        CHANGE_INPUT,
        SWAP_CHILDREN,
        GROW_SUBTREE,
        CROSSOVER,
        COMPRESS_BRANCH
    }

    /**
     * Applies this action to the given tree.
     *
     * @param tree the source tree (not modified)
     * @param rng  random number generator
     * @param k    number of inputs for operators that need it
     * @return a new mutated tree
     */
    public DecisionTree apply(DecisionTree tree, Random rng, int k) {
        return switch (type) {
            case FLIP_LEAF -> GeneticOperators.flipLeaf(rng, tree);
            case SPLIT_LEAF -> GeneticOperators.splitLeaf(rng, tree, k);
            case PRUNE_TREE -> GeneticOperators.pruneTree(rng, tree);
            case CHANGE_INPUT -> GeneticOperators.changeInput(rng, tree, k);
            case SWAP_CHILDREN -> GeneticOperators.swapChildren(rng, tree);
            case GROW_SUBTREE -> GeneticOperators.growSubtree(rng, tree, k);
            case COMPRESS_BRANCH -> GeneticOperators.compressBranch(rng, tree);
            case CROSSOVER -> {
                // For crossover within MCTS, we use the tree with itself as a no-op
                // or the caller should provide a second tree. Here we fallback to flipLeaf.
                yield GeneticOperators.flipLeaf(rng, tree);
            }
        };
    }

    /**
     * Creates an action for the given type.
     */
    public static MctsAction of(ActionType type) {
        return new MctsAction(type, null);
    }

    /**
     * Returns all 8 action types as MctsAction instances.
     */
    public static List<MctsAction> allActions() {
        return ALL_TYPES.stream().map(MctsAction::of).toList();
    }

    /**
     * Returns a subset of actions excluding CROSSOVER (which needs a second tree).
     */
    public static List<MctsAction> singleTreeActions() {
        return ALL_TYPES.stream()
                .filter(t -> t != ActionType.CROSSOVER)
                .map(MctsAction::of)
                .toList();
    }

    @Override
    public String toString() {
        return "MctsAction[" + type + ']';
    }
}
