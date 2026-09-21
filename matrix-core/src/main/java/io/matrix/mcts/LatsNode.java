package io.matrix.mcts;

import io.matrix.neuron.DecisionTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A node in the LATS (Language Agent Tree Search) tree.
 *
 * <p>Extends {@link MctsNode} with reflection capabilities from the LATS framework
 * (arXiv:2310.04406). Each node stores:
 * <ul>
 *   <li>Reflection text generated after evaluating this branch</li>
 *   <li>Success/failure status of the branch</li>
 *   <li>LLM-based value function score (replaces random simulation)</li>
 *   <li>Prior probability for PUCT-style selection</li>
 * </ul>
 *
 * <p>LATS key differences from vanilla MCTS:
 * <ol>
 *   <li><b>Value function:</b> uses LLM (or brain) to evaluate node quality instead of random playout</li>
 *   <li><b>Reflection:</b> generates self-reflections on failed branches to guide future search</li>
 *   <li><b>Backpropagation:</b> propagates both rewards and reflection insights up the tree</li>
 * </ol>
 *
 * @see LatsValueFunction
 * @see LatsReflector
 */
public class LatsNode extends MctsNode {

    /** Default reflection when none is available. */
    public static final String NO_REFLECTION = "";

    private String reflection;
    private boolean reflectionGenerated;
    private BranchStatus status;
    private double valueScore;
    private double prior;
    private final List<String> ancestorReflections;

    /**
     * Branch outcome status.
     */
    public enum BranchStatus {
        /** Not yet evaluated. */
        PENDING,
        /** Branch led to a successful outcome. */
        SUCCESS,
        /** Branch led to a failure. */
        FAILURE,
        /** Branch was pruned (e.g., low value score). */
        PRUNED
    }

    /**
     * Creates a new LATS node.
     *
     * @param parent         parent node (null for root)
     * @param action         the action that led to this node (null for root)
     * @param state          the DecisionTree state at this node
     * @param availableActions actions available for expansion
     */
    public LatsNode(MctsNode parent, MctsAction action, DecisionTree state,
                    List<MctsAction> availableActions) {
        super(parent, action, state, availableActions);
        this.reflection = NO_REFLECTION;
        this.reflectionGenerated = false;
        this.status = BranchStatus.PENDING;
        this.valueScore = 0.0;
        this.prior = 1.0;
        this.ancestorReflections = new ArrayList<>();

        // Inherit reflections from parent chain
        if (parent instanceof LatsNode latsParent) {
            this.ancestorReflections.addAll(latsParent.ancestorReflections);
            if (latsParent.hasReflection()) {
                this.ancestorReflections.add(latsParent.reflection());
            }
        }
    }

    /**
     * Creates a root LATS node.
     *
     * @param state          the DecisionTree state
     * @param availableActions actions available for expansion
     */
    public LatsNode(DecisionTree state, List<MctsAction> availableActions) {
        this(null, null, state, availableActions);
    }

    /**
     * Returns the reflection text for this node.
     */
    public String reflection() {
        return reflection;
    }

    /**
     * Sets the reflection text for this node.
     *
     * @param reflection the reflection text
     */
    public void setReflection(String reflection) {
        this.reflection = Objects.requireNonNull(reflection, "reflection");
        this.reflectionGenerated = true;
    }

    /**
     * Returns true if a reflection has been generated for this node.
     */
    public boolean hasReflection() {
        return reflectionGenerated && !reflection.isEmpty();
    }

    /**
     * Returns the branch status.
     */
    public BranchStatus status() {
        return status;
    }

    /**
     * Sets the branch status.
     *
     * @param status the new status
     */
    public void setStatus(BranchStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    /**
     * Returns the value function score for this node.
     */
    public double valueScore() {
        return valueScore;
    }

    /**
     * Sets the value function score.
     *
     * @param score the score from the value function (0.0 to 1.0)
     */
    public void setValueScore(double score) {
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("Value score must be in [0, 1], got: " + score);
        }
        this.valueScore = score;
    }

    /**
     * Returns the prior probability for this node.
     */
    public double prior() {
        return prior;
    }

    /**
     * Sets the prior probability.
     *
     * @param prior the prior probability (0.0 to 1.0)
     */
    public void setPrior(double prior) {
        if (prior < 0.0 || prior > 1.0) {
            throw new IllegalArgumentException("Prior must be in [0, 1], got: " + prior);
        }
        this.prior = prior;
    }

    /**
     * Returns an unmodifiable view of ancestor reflections.
     */
    public List<String> ancestorReflections() {
        return Collections.unmodifiableList(ancestorReflections);
    }

    /**
     * Returns all reflections in the path from root to this node (inclusive),
     * combining ancestor reflections with this node's reflection.
     */
    public List<String> fullPathReflections() {
        List<String> all = new ArrayList<>(ancestorReflections);
        if (hasReflection()) {
            all.add(reflection);
        }
        return Collections.unmodifiableList(all);
    }

    /**
     * Calculates the PUCT (Predictor + UCB applied to Trees) score.
     *
     * <p>PUCT(i) = Q(i) + c_puct * P(i) * √(ΣN(parent)) / (1 + N(i))
     * <ul>
     *   <li>Q(i) = mean reward (exploitation)</li>
     *   <li>c_puct = exploration constant</li>
     *   <li>P(i) = prior probability</li>
     *   <li>ΣN(parent) = parent visit count</li>
     *   <li>N(i) = this node's visit count</li>
     * </ul>
     *
     * @param explorationConstant the c_puct parameter
     * @return the PUCT score
     */
    public double puct(double explorationConstant) {
        if (visitCount() == 0) {
            return Double.MAX_VALUE;
        }
        double parentVisits = (parent() != null) ? parent().visitCount() : visitCount();
        if (parentVisits == 0) {
            return Double.MAX_VALUE;
        }
        double exploitation = meanReward();
        double exploration = explorationConstant * prior * Math.sqrt(parentVisits) / (1.0 + visitCount());
        return exploitation + exploration;
    }

    /**
     * Selects the child with the highest PUCT score.
     *
     * @param explorationConstant the c_puct parameter
     * @return the best child
     * @throws IllegalStateException if no children exist
     */
    public LatsNode bestLatsChild(double explorationConstant) {
        if (children().isEmpty()) {
            throw new IllegalStateException("No children to select from");
        }
        LatsNode best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (MctsNode child : children()) {
            if (child instanceof LatsNode latsChild) {
                double score = latsChild.puct(explorationConstant);
                if (score > bestScore) {
                    bestScore = score;
                    best = latsChild;
                }
            }
        }
        return best;
    }

    /**
     * Returns true if this branch is worth exploring (not pruned, not failed).
     */
    public boolean isExplorable() {
        return status != BranchStatus.PRUNED && status != BranchStatus.FAILURE;
    }

    @Override
    public String toString() {
        return "LatsNode[action=" + action()
                + ", visits=" + visitCount()
                + ", reward=" + String.format("%.2f", totalReward())
                + ", valueScore=" + String.format("%.2f", valueScore)
                + ", prior=" + String.format("%.2f", prior)
                + ", status=" + status
                + ", hasReflection=" + hasReflection()
                + ", children=" + children().size()
                + ", untried=" + untriedActionCount() + ']';
    }
}
