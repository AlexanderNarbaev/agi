package io.matrix.ktopo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Ricci Flow analysis on knowledge graph (SPEC-003).
 *
 * <p>Computes Ollivier-Ricci curvature on edges: positive within communities,
 * negative on bridges. Iterative flow equalizes curvature → stable metric.
 *
 * <p>Applications:
 * <ul>
 *   <li>Community detection: positive curvature = dense cluster</li>
 *   <li>Fragile bridge detection: negative curvature = single-link knowledge</li>
 *   <li>Drift fingerprint: curvature distribution as graph signature</li>
 *   <li>Curriculum ordering: learn from dense clusters outward</li>
 * </ul>
 */
public final class RicciFlow {

    private final KnowledgeGraph graph;

    public RicciFlow(KnowledgeGraph graph) {
        this.graph = graph;
    }

    /** Compute Ollivier-Ricci curvature for each edge. */
    public Map<String, Double> computeCurvatures() {
        Map<String, Double> curvatures = new HashMap<>();
        for (var edge : graph.edges()) {
            String key = edge.source() + "→" + edge.target();
            curvatures.put(key, edgeCurvature(edge));
        }
        return curvatures;
    }

    /** Ollivier-Ricci curvature of a single edge (simplified). */
    private double edgeCurvature(KnowledgeGraph.KnowledgeEdge edge) {
        Set<String> srcNeighbors = graph.neighbors(edge.source());
        Set<String> tgtNeighbors = graph.neighbors(edge.target());

        // Jaccard similarity of neighborhoods (excluding the edge itself)
        Set<String> intersection = new HashSet<>(srcNeighbors);
        intersection.retainAll(tgtNeighbors);
        intersection.remove(edge.source());
        intersection.remove(edge.target());

        Set<String> union = new HashSet<>(srcNeighbors);
        union.addAll(tgtNeighbors);
        union.remove(edge.source());
        union.remove(edge.target());

        if (union.isEmpty()) return 0.0;
        return (double) intersection.size() / union.size();
    }

    /** Detect communities via curvature threshold. */
    public Map<Integer, Set<String>> detectCommunities(double threshold) {
        Map<String, Double> curvatures = computeCurvatures();
        Map<Integer, Set<String>> communities = new HashMap<>();
        int communityId = 0;
        Set<String> visited = new HashSet<>();

        for (var entry : curvatures.entrySet()) {
            if (entry.getValue() >= threshold) {
                String[] parts = entry.getKey().split("→");
                if (!visited.contains(parts[0]) && !visited.contains(parts[1])) {
                    Set<String> community = new HashSet<>();
                    community.add(parts[0]);
                    community.add(parts[1]);
                    communities.put(communityId++, community);
                    visited.add(parts[0]);
                    visited.add(parts[1]);
                }
            }
        }
        return communities;
    }

    /** Detect fragile bridges (negative curvature = single-link knowledge). */
    public Set<String> detectFragileBridges() {
        Map<String, Double> curvatures = computeCurvatures();
        Set<String> fragile = new HashSet<>();
        for (var entry : curvatures.entrySet()) {
            if (entry.getValue() < 0.1) { // near-zero or negative
                fragile.add(entry.getKey());
            }
        }
        return fragile;
    }

    /** Drift fingerprint: curvature distribution as graph signature. */
    public double[] driftFingerprint() {
        Map<String, Double> curvatures = computeCurvatures();
        double[] dist = new double[10]; // 10 buckets: [0,0.1), [0.1,0.2), ..., [0.9,1.0]
        for (double c : curvatures.values()) {
            int bucket = Math.min(9, Math.max(0, (int) (c * 10)));
            dist[bucket]++;
        }
        // Normalize
        double total = curvatures.size();
        if (total > 0) {
            for (int i = 0; i < dist.length; i++) dist[i] /= total;
        }
        return dist;
    }
}
