package io.matrix.brain.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * TRUE-W11 iteration #11 — Graph-neural memory indexing.
 *
 * <p>Stores mind entries as nodes in a graph; co-occurrence builds
 * edges. Spread activation propagates from a query node along
 * weighted edges with decay. The result is a ranked list of
 * related-by-association entries.</p>
 */
public final class GraphMemoryIndex {

    private final Map<String, Map<String, Double>> edges = new HashMap<>();

    /** Add a co-occurrence relationship between two nodes. */
    public void addEdge(String from, String to) {
        if (from == null || to == null || from.equals(to)) return;
        edges.computeIfAbsent(from, k -> new HashMap<>())
            .merge(to, 1.0, Double::sum);
        // Symmetric
        edges.computeIfAbsent(to, k -> new HashMap<>())
            .merge(from, 1.0, Double::sum);
    }

    /** Spread activation from `start` with given decay (0..1) for `hops`. */
    public Map<String, Double> spread(String start, double decay, int hops) {
        if (decay <= 0 || decay >= 1) throw new IllegalArgumentException("decay in (0, 1)");
        if (hops <= 0) throw new IllegalArgumentException("hops > 0");

        Map<String, Double> activation = new LinkedHashMap<>();
        Set<String> frontier = new HashSet<>();
        if (start != null) {
            activation.put(start, 1.0);
            frontier.add(start);
        }
        Set<String> visited = new HashSet<>();
        for (int hop = 0; hop < hops; hop++) {
            Set<String> next = new HashSet<>();
            for (String node : frontier) {
                visited.add(node);
                Map<String, Double> nbrs = edges.get(node);
                if (nbrs == null) continue;
                for (var e : nbrs.entrySet()) {
                    if (visited.contains(e.getKey())) continue;
                    double contrib = activation.getOrDefault(node, 0.0)
                                   * e.getValue() * decay;
                    activation.merge(e.getKey(), contrib, Double::sum);
                    next.add(e.getKey());
                }
            }
            frontier = next;
            if (frontier.isEmpty()) break;
        }
        activation.remove(start);   // exclude the query itself
        return activation;
    }

    public int nodeCount() {
        Set<String> nodes = new HashSet<>();
        for (var e : edges.entrySet()) {
            nodes.add(e.getKey());
            nodes.addAll(e.getValue().keySet());
        }
        return nodes.size();
    }

    public int edgeCount() {
        int sum = 0;
        for (var m : edges.values()) sum += m.size();
        return sum / 2;   // symmetric
    }

    public Map<String, Double> neighbours(String node) {
        return edges.getOrDefault(node, new HashMap<>());
    }
}
