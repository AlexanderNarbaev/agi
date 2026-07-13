package io.matrix.mcts;

import io.matrix.evolution.TreeWalker;
import io.matrix.memory.HierarchicalMemory;
import io.matrix.memory.HierarchicalMemory.Level;
import io.matrix.neuron.DecisionTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Generates reflections on failed branches in the LATS framework.
 *
 * <p>In LATS (arXiv:2310.04406), self-reflection is a key mechanism:
 * when a branch fails (low reward), the agent generates a reflection
 * explaining why it failed, which then guides future search away from
 * similar failures.
 *
 * <p>This implementation:
 * <ul>
 *   <li>Analyzes branch outcomes to determine success/failure</li>
 *   <li>Generates structured reflections based on tree properties</li>
 *   <li>Stores reflections in {@link HierarchicalMemory} as episodic entries</li>
 *   <li>Retrieves relevant past reflections to guide new search</li>
 * </ul>
 *
 * <p>Ref: LATS §3.3 — Self-Reflection
 */
public final class LatsReflector {

    /** Domain tag for MCTS/LATS reflections in memory. */
    public static final String REFLECTION_DOMAIN = "mcts-lats";

    /** Tag for failed branch reflections. */
    public static final String TAG_FAILURE = "failure";

    /** Tag for successful branch reflections. */
    public static final String TAG_SUCCESS = "success";

    /** Tag for structural analysis reflections. */
    public static final String TAG_STRUCTURAL = "structural";

    private final HierarchicalMemory memory;
    private final BiFunction<DecisionTree, DecisionTree, String> reflectionGenerator;
    private final double failureThreshold;
    private final Random rng;

    /**
     * Creates a new LATS reflector.
     *
     * @param memory              hierarchical memory for storing reflections
     * @param reflectionGenerator function to generate reflection text (parent state, current state) -> reflection
     * @param failureThreshold    reward threshold below which a branch is considered failed
     * @param rng                 random number generator
     */
    public LatsReflector(HierarchicalMemory memory,
                         BiFunction<DecisionTree, DecisionTree, String> reflectionGenerator,
                         double failureThreshold,
                         Random rng) {
        this.memory = Objects.requireNonNull(memory, "memory");
        this.reflectionGenerator = Objects.requireNonNull(reflectionGenerator, "reflectionGenerator");
        this.failureThreshold = failureThreshold;
        this.rng = Objects.requireNonNull(rng, "rng");
    }

    /**
     * Creates a reflector with default heuristic reflection generation.
     *
     * @param memory           hierarchical memory
     * @param failureThreshold reward threshold for failure
     * @param rng              random number generator
     */
    public LatsReflector(HierarchicalMemory memory, double failureThreshold, Random rng) {
        this(memory, LatsReflector::defaultReflectionGenerator, failureThreshold, rng);
    }

    /**
     * Reflects on a node, generating a reflection and updating its status.
     *
     * <p>Reflection is generated if:
     * <ul>
     *   <li>The node's reward is below the failure threshold (failure reflection)</li>
     *   <li>The node has been visited multiple times with mixed results</li>
     * </ul>
     *
     * @param node   the node to reflect on
     * @param parent the parent node (for context), may be null for root
     */
    public void reflect(LatsNode node, LatsNode parent) {
        Objects.requireNonNull(node, "node");

        double reward = node.meanReward();
        boolean isFailure = reward < failureThreshold && node.visitCount() > 0;

        if (isFailure) {
            node.setStatus(LatsNode.BranchStatus.FAILURE);
            String reflection = generateFailureReflection(node, parent);
            node.setReflection(reflection);
            storeReflection(node, reflection, TAG_FAILURE);
        } else if (node.visitCount() >= 3) {
            // Reflect on successful nodes too, but only after enough visits
            node.setStatus(LatsNode.BranchStatus.SUCCESS);
            String reflection = generateSuccessReflection(node, parent);
            node.setReflection(reflection);
            storeReflection(node, reflection, TAG_SUCCESS);
        }
    }

    /**
     * Generates a reflection for a failed branch.
     */
    private String generateFailureReflection(LatsNode node, LatsNode parent) {
        DecisionTree currentState = node.state();
        DecisionTree parentState = (parent != null) ? parent.state() : null;

        StringBuilder sb = new StringBuilder();

        // Use the custom generator if available
        if (parentState != null) {
            String customReflection = reflectionGenerator.apply(parentState, currentState);
            if (customReflection != null && !customReflection.isEmpty()) {
                sb.append(customReflection);
            }
        }

        // Add structural analysis
        sb.append(structuralAnalysis(currentState));

        // Add ancestor context
        List<String> ancestorReflections = node.ancestorReflections();
        if (!ancestorReflections.isEmpty()) {
            sb.append(" Previous insights: ");
            sb.append(String.join("; ", ancestorReflections.subList(
                    Math.max(0, ancestorReflections.size() - 3), ancestorReflections.size())));
        }

        return sb.toString().trim();
    }

    /**
     * Generates a reflection for a successful branch.
     */
    private String generateSuccessReflection(LatsNode node, LatsNode parent) {
        DecisionTree currentState = node.state();
        StringBuilder sb = new StringBuilder();
        sb.append("Branch successful (reward=").append(String.format("%.2f", node.meanReward())).append("). ");
        sb.append(structuralAnalysis(currentState));
        return sb.toString().trim();
    }

    /**
     * Performs structural analysis of a decision tree.
     */
    private String structuralAnalysis(DecisionTree tree) {
        int depth = tree.depth();
        int nodes = TreeWalker.totalNodes(tree);
        int leaves = TreeWalker.countLeaves(tree);
        int splits = TreeWalker.countSplits(tree);

        StringBuilder analysis = new StringBuilder();
        analysis.append("Tree: depth=").append(depth)
                .append(", nodes=").append(nodes)
                .append(", leaves=").append(leaves)
                .append(", splits=").append(splits).append(". ");

        if (depth > 5) {
            analysis.append("Tree is deep — consider pruning. ");
        }
        if (nodes > 15) {
            analysis.append("Tree is large — may be overfitting. ");
        }
        if (leaves == 1) {
            analysis.append("Tree is trivial (single leaf). ");
        }

        return analysis.toString();
    }

    /**
     * Stores a reflection in hierarchical memory.
     */
    private void storeReflection(LatsNode node, String reflection, String tag) {
        Set<String> tags = Set.of(tag, TAG_STRUCTURAL, "lats");
        memory.store(Level.L1_PATTERN, reflection, REFLECTION_DOMAIN, tags);
    }

    /**
     * Retrieves relevant past reflections for guiding search.
     *
     * @param query the search query (e.g., based on current state properties)
     * @param limit maximum number of reflections to return
     * @return list of relevant reflection texts
     */
    public List<String> retrieveRelevantReflections(String query, int limit) {
        return memory.search(query, limit).stream()
                .filter(e -> e.domain().equals(REFLECTION_DOMAIN))
                .map(HierarchicalMemory.MemoryEntry::content)
                .toList();
    }

    /**
     * Retrieves recent failure reflections.
     *
     * @param limit maximum number of reflections
     * @return list of failure reflection texts
     */
    public List<String> retrieveRecentFailures(int limit) {
        return memory.searchAtLevel(Level.L1_PATTERN, TAG_FAILURE, limit).stream()
                .filter(e -> e.domain().equals(REFLECTION_DOMAIN))
                .map(HierarchicalMemory.MemoryEntry::content)
                .toList();
    }

    /**
     * Returns the number of stored reflections.
     */
    public int reflectionCount() {
        return (int) memory.entriesInDomain(REFLECTION_DOMAIN).stream()
                .filter(e -> e.tags().contains(TAG_FAILURE) || e.tags().contains(TAG_SUCCESS))
                .count();
    }

    /**
     * Returns the failure threshold.
     */
    public double failureThreshold() {
        return failureThreshold;
    }

    // ── Default reflection generator ──

    /**
     * Default reflection generator that produces structured reflections
     * based on tree comparison.
     */
    static String defaultReflectionGenerator(DecisionTree parentState, DecisionTree currentState) {
        int parentNodes = TreeWalker.totalNodes(parentState);
        int currentNodes = TreeWalker.totalNodes(currentState);
        int parentDepth = parentState.depth();
        int currentDepth = currentState.depth();

        StringBuilder sb = new StringBuilder();

        if (currentNodes < parentNodes) {
            sb.append("Mutation reduced tree size (").append(parentNodes)
                    .append(" -> ").append(currentNodes).append(" nodes). ");
        } else if (currentNodes > parentNodes) {
            sb.append("Mutation increased tree size (").append(parentNodes)
                    .append(" -> ").append(currentNodes).append(" nodes). ");
        }

        if (currentDepth < parentDepth) {
            sb.append("Depth decreased — potential over-pruning. ");
        } else if (currentDepth > parentDepth) {
            sb.append("Depth increased — may need balancing. ");
        }

        return sb.toString();
    }
}
