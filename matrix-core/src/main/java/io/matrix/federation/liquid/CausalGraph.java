package io.matrix.federation.liquid;

import java.util.*;

/**
 * W578 — Causal Graph Engine.
 *
 * DAG of events (A → B) for causal reasoning.
 * Supports:
 * - explainCause(event): returns causal chain
 * - Counterfactual reasoning: "What if A didn't happen?"
 * - Integration with BIR for logical verification
 */
public final class CausalGraph {

    private final Map<String, Set<String>> adjacency = new HashMap<>(); // cause → effects
    private final Map<String, Set<String>> reverseAdjacency = new HashMap<>(); // effect → causes
    private final Map<String, Double> nodeWeights = new HashMap<>(); // event importance

    /**
     * Add a causal edge: cause → effect.
     */
    public void addEdge(String cause, String effect) {
        adjacency.computeIfAbsent(cause, k -> new HashSet<>()).add(effect);
        reverseAdjacency.computeIfAbsent(effect, k -> new HashSet<>()).add(cause);
    }

    /**
     * Add a node with weight.
     */
    public void addNode(String node, double weight) {
        nodeWeights.put(node, weight);
    }

    /**
     * Explain the causal chain leading to an event.
     *
     * @param effect the event to explain
     * @return causal chain from root cause to effect
     */
    public List<String> explainCause(String effect) {
        List<String> chain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        explainCauseRecursive(effect, chain, visited);
        Collections.reverse(chain); // Root cause first
        return chain;
    }

    private void explainCauseRecursive(String node, List<String> chain, Set<String> visited) {
        if (visited.contains(node)) return;
        visited.add(node);
        chain.add(node);

        Set<String> causes = reverseAdjacency.get(node);
        if (causes != null) {
            for (String cause : causes) {
                explainCauseRecursive(cause, chain, visited);
            }
        }
    }

    /**
     * Get all effects of an event (transitive).
     */
    public Set<String> getEffects(String cause) {
        Set<String> effects = new HashSet<>();
        Set<String> visited = new HashSet<>();
        getEffectsRecursive(cause, effects, visited);
        return effects;
    }

    private void getEffectsRecursive(String node, Set<String> effects, Set<String> visited) {
        if (visited.contains(node)) return;
        visited.add(node);

        Set<String> children = adjacency.get(node);
        if (children != null) {
            for (String child : children) {
                effects.add(child);
                getEffectsRecursive(child, effects, visited);
            }
        }
    }

    /**
     * Counterfactual: what would happen if event didn't occur?
     *
     * @param removedEvent the event to remove
     * @return events that would NOT happen
     */
    public Set<String> counterfactual(String removedEvent) {
        Set<String> wouldNotHappen = new HashSet<>();
        Set<String> visited = new HashSet<>();
        counterfactualRecursive(removedEvent, wouldNotHappen, visited);
        return wouldNotHappen;
    }

    private void counterfactualRecursive(String node, Set<String> removed, Set<String> visited) {
        if (visited.contains(node)) return;
        visited.add(node);

        Set<String> effects = adjacency.get(node);
        if (effects != null) {
            for (String effect : effects) {
                // Check if effect has other causes
                Set<String> otherCauses = reverseAdjacency.get(effect);
                if (otherCauses == null || otherCauses.size() <= 1) {
                    // Only cause removed → effect wouldn't happen
                    removed.add(effect);
                    counterfactualRecursive(effect, removed, visited);
                }
            }
        }
    }

    /**
     * Get direct causes of an event.
     */
    public Set<String> getCauses(String effect) {
        return reverseAdjacency.getOrDefault(effect, Collections.emptySet());
    }

    /**
     * Get direct effects of an event.
     */
    public Set<String> getDirectEffects(String cause) {
        return adjacency.getOrDefault(cause, Collections.emptySet());
    }

    /**
     * Check if graph has cycles (invalid DAG).
     */
    public boolean hasCycles() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String node : adjacency.keySet()) {
            if (hasCyclesDFS(node, visited, recursionStack)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCyclesDFS(String node, Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(node)) return true;
        if (visited.contains(node)) return false;

        visited.add(node);
        recursionStack.add(node);

        Set<String> children = adjacency.get(node);
        if (children != null) {
            for (String child : children) {
                if (hasCyclesDFS(child, visited, recursionStack)) {
                    return true;
                }
            }
        }

        recursionStack.remove(node);
        return false;
    }

    /**
     * Get all nodes in the graph.
     */
    public Set<String> getAllNodes() {
        Set<String> all = new HashSet<>(adjacency.keySet());
        all.addAll(reverseAdjacency.keySet());
        return all;
    }

    /**
     * Get node weight.
     */
    public double getNodeWeight(String node) {
        return nodeWeights.getOrDefault(node, 0.0);
    }

    /**
     * Number of edges.
     */
    public int getEdgeCount() {
        return adjacency.values().stream().mapToInt(Set::size).sum();
    }
}
