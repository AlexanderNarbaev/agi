package io.matrix.mcts;

import io.matrix.neuron.DecisionTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A node in the MCTS search tree.
 *
 * <p>Each node represents a state (a {@link DecisionTree}) and tracks visit
 * counts and cumulative rewards for UCB1-based selection.
 *
 * <p>Ref: Phase 4 MCTS-Guided Evolution
 */
public class MctsNode {

    /** Exploration constant C = √2. */
    public static final double EXPLORATION_CONSTANT = Math.sqrt(2.0);

    private final MctsNode parent;
    private final MctsAction action;
    private final DecisionTree state;
    private final List<MctsAction> untriedActions;
    private final List<MctsNode> children;

    private int visitCount;
    private double totalReward;

    /**
     * Creates a new MCTS node.
     *
     * @param parent         parent node (null for root)
     * @param action         the action that led to this node (null for root)
     * @param state          the DecisionTree state at this node
     * @param availableActions actions available for expansion from this node
     */
    public MctsNode(MctsNode parent, MctsAction action, DecisionTree state,
                    List<MctsAction> availableActions) {
        this.parent = parent;
        this.action = action;
        this.state = Objects.requireNonNull(state, "state");
        this.untriedActions = new ArrayList<>(Objects.requireNonNull(availableActions, "availableActions"));
        this.children = new ArrayList<>();
        this.visitCount = 0;
        this.totalReward = 0.0;
    }

    /**
     * Returns the parent node, or null if this is the root.
     */
    public MctsNode parent() {
        return parent;
    }

    /**
     * Returns the action that led to this node, or null for the root.
     */
    public MctsAction action() {
        return action;
    }

    /**
     * Returns the DecisionTree state at this node.
     */
    public DecisionTree state() {
        return state;
    }

    /**
     * Returns an unmodifiable view of child nodes.
     */
    public List<MctsNode> children() {
        return Collections.unmodifiableList(children);
    }

    /**
     * Returns the number of times this node has been visited.
     */
    public int visitCount() {
        return visitCount;
    }

    /**
     * Returns the cumulative reward accumulated at this node.
     */
    public double totalReward() {
        return totalReward;
    }

    /**
     * Returns the mean reward (exploitation term) for this node.
     */
    public double meanReward() {
        return visitCount == 0 ? 0.0 : totalReward / visitCount;
    }

    /**
     * Calculates the UCB1 score for this node.
     *
     * <p>UCB1(i) = X̄ᵢ + C * √(ln(N) / nᵢ)
     * <ul>
     *   <li>X̄ᵢ = mean reward of node i</li>
     *   <li>C  = exploration constant (√2)</li>
     *   <li>N  = parent visit count</li>
     *   <li>nᵢ = visit count of node i</li>
     * </ul>
     *
     * @return the UCB1 score, or {@link Double#MAX_VALUE} if unvisited
     */
    public double ucb1() {
        return ucb1(EXPLORATION_CONSTANT);
    }

    /**
     * Calculates the UCB1 score with a custom exploration constant.
     *
     * @param explorationConstant the C parameter
     * @return the UCB1 score, or {@link Double#MAX_VALUE} if unvisited
     */
    public double ucb1(double explorationConstant) {
        if (visitCount == 0) {
            return Double.MAX_VALUE;
        }
        double parentVisits = (parent != null) ? parent.visitCount() : visitCount;
        if (parentVisits == 0) {
            return Double.MAX_VALUE;
        }
        return meanReward() + explorationConstant * Math.sqrt(Math.log(parentVisits) / visitCount);
    }

    /**
     * Returns true if all available actions have been tried (node is fully expanded).
     */
    public boolean isFullyExpanded() {
        return untriedActions.isEmpty();
    }

    /**
     * Returns true if this node has no children (terminal or not yet expanded).
     */
    public boolean isLeaf() {
        return children.isEmpty();
    }

    /**
     * Returns true if this node is the root of the tree.
     */
    public boolean isRoot() {
        return parent == null;
    }

    /**
     * Returns the number of untried actions remaining.
     */
    public int untriedActionCount() {
        return untriedActions.size();
    }

    /**
     * Selects and removes an untried action for expansion.
     *
     * @return the next untried action
     * @throws IllegalStateException if no untried actions remain
     */
    public MctsAction nextUntriedAction() {
        if (untriedActions.isEmpty()) {
            throw new IllegalStateException("No untried actions remaining");
        }
        return untriedActions.remove(untriedActions.size() - 1);
    }

    /**
     * Adds a child node.
     *
     * @param child the child node to add
     */
    public void addChild(MctsNode child) {
        children.add(child);
    }

    /**
     * Selects the child with the highest UCB1 score.
     *
     * @return the best child
     * @throws IllegalStateException if no children exist
     */
    public MctsNode bestChild() {
        return bestChild(EXPLORATION_CONSTANT);
    }

    /**
     * Selects the child with the highest UCB1 score using a custom exploration constant.
     *
     * @param explorationConstant the C parameter
     * @return the best child
     * @throws IllegalStateException if no children exist
     */
    public MctsNode bestChild(double explorationConstant) {
        if (children.isEmpty()) {
            throw new IllegalStateException("No children to select from");
        }
        MctsNode best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (MctsNode child : children) {
            double score = child.ucb1(explorationConstant);
            if (score > bestScore) {
                bestScore = score;
                best = child;
            }
        }
        return best;
    }

    /**
     * Selects the child with the highest visit count (for final action selection).
     *
     * @return the most visited child
     * @throws IllegalStateException if no children exist
     */
    public MctsNode mostVisitedChild() {
        if (children.isEmpty()) {
            throw new IllegalStateException("No children to select from");
        }
        MctsNode best = null;
        int maxVisits = -1;
        for (MctsNode child : children) {
            if (child.visitCount() > maxVisits) {
                maxVisits = child.visitCount();
                best = child;
            }
        }
        return best;
    }

    /**
     * Updates this node's statistics after a simulation.
     *
     * @param reward the reward from the simulation
     */
    public void update(double reward) {
        visitCount++;
        totalReward += reward;
    }

    /**
     * Returns the depth of this node in the tree (root = 0).
     */
    public int depth() {
        int d = 0;
        MctsNode n = parent;
        while (n != null) {
            d++;
            n = n.parent;
        }
        return d;
    }

    @Override
    public String toString() {
        return "MctsNode[action=" + action
                + ", visits=" + visitCount
                + ", reward=" + String.format("%.2f", totalReward)
                + ", children=" + children.size()
                + ", untried=" + untriedActions.size() + ']';
    }
}
