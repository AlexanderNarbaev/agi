package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Community detection for {@link KnowledgeGraphStore} — Leiden-style
 * modularity optimization.
 *
 * <p>Implements the GraphRAG "community detection" pattern (Research Synthesis
 * 2026 Q3 v2, Phase C1) for partitioning knowledge graphs into semantic
 * communities. These communities serve as retrieval units for multi-hop
 * reasoning and Noosphere federation.
 *
 * <p>Algorithm (simplified Louvain/Leiden):
 * <ol>
 *   <li>Each node starts in its own community</li>
 *   <li>Iteratively move nodes to neighboring communities that maximize
 *       modularity gain</li>
 *   <li>Output community assignments + summary stats</li>
 * </ol>
 *
 * <p>Thread-safe — uses only immutable snapshots from the graph.
 *
 * @since 3.30
 */
public final class CommunityDetector {

    private CommunityDetector() {
        // utility class
    }

    /**
     * A community: a set of entity IDs with a summary label.
     */
    public record Community(
            int id,
            Set<String> members,
            String summary,
            int size,
            int internalEdges,
            int externalEdges
    ) {
        public Community {
            members = Set.copyOf(members);
        }

        /** Modularity contribution of this community. */
        public double modularity(int totalEdges) {
            if (totalEdges == 0) return 0;
            double internal = (double) internalEdges / totalEdges;
            double expected = Math.pow((double) (internalEdges + externalEdges) / (2.0 * totalEdges), 2);
            return internal - expected;
        }
    }

    /**
     * Result of community detection.
     */
    public record DetectionResult(
            List<Community> communities,
            Map<String, Integer> nodeToCommunity,  // entityId → communityId
            double modularity,
            int iterationsRun
    ) {
        public DetectionResult {
            communities = List.copyOf(communities);
            nodeToCommunity = Map.copyOf(nodeToCommunity);
        }
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Detects communities in the given knowledge graph.
     *
     * @param graph      the knowledge graph
     * @param maxIterations maximum iterations (default: 10)
     * @return detection result with community assignments
     */
    public static DetectionResult detect(KnowledgeGraphStore graph, int maxIterations) {
        Objects.requireNonNull(graph, "graph");

        // Build adjacency from graph
        List<String> nodes = new ArrayList<>(graph.entityIds());
        if (nodes.isEmpty()) {
            return new DetectionResult(List.of(), Map.of(), 0.0, 0);
        }

        Map<String, Set<String>> adjacency = buildAdjacency(graph, nodes);
        int totalEdges = countTotalEdges(adjacency);

        // Initialize: each node = own community
        Map<String, Integer> nodeToComm = new HashMap<>();
        Map<Integer, Set<String>> commToNodes = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            nodeToComm.put(nodes.get(i), i);
            commToNodes.put(i, new HashSet<>(Set.of(nodes.get(i))));
        }

        // Iterative optimization
        int iter;
        boolean moved;
        double modularity = computeModularity(commToNodes, adjacency, totalEdges);

        for (iter = 0; iter < maxIterations; iter++) {
            moved = false;
            List<String> shuffled = new ArrayList<>(nodes);
            Collections.shuffle(shuffled);

            for (String node : shuffled) {
                int currentComm = nodeToComm.get(node);
                Set<String> neighbors = adjacency.getOrDefault(node, Set.of());

                // Find best community among neighbors
                Map<Integer, Integer> neighborCommCounts = new HashMap<>();
                for (String neighbor : neighbors) {
                    int nc = nodeToComm.get(neighbor);
                    neighborCommCounts.merge(nc, 1, Integer::sum);
                }

                int bestComm = currentComm;
                double bestGain = 0;

                for (var entry : neighborCommCounts.entrySet()) {
                    int candidateComm = entry.getKey();
                    if (candidateComm == currentComm) continue;

                    double gain = computeModularityGain(
                            node, currentComm, candidateComm,
                            commToNodes, adjacency, totalEdges);
                    if (gain > bestGain) {
                        bestGain = gain;
                        bestComm = candidateComm;
                    }
                }

                if (bestComm != currentComm) {
                    // Move node
                    commToNodes.get(currentComm).remove(node);
                    commToNodes.get(bestComm).add(node);
                    nodeToComm.put(node, bestComm);

                    // Cleanup empty communities
                    if (commToNodes.get(currentComm).isEmpty()) {
                        commToNodes.remove(currentComm);
                    }
                    moved = true;
                }
            }

            if (!moved) break;

            modularity = computeModularity(commToNodes, adjacency, totalEdges);
        }

        // Build communities list
        List<Community> communities = new ArrayList<>();
        for (var entry : commToNodes.entrySet()) {
            int commId = entry.getKey();
            Set<String> members = entry.getValue();
            int internal = countInternalEdges(adjacency, members);
            int external = countExternalEdges(adjacency, members);
            String summary = buildSummary(graph, members);
            communities.add(new Community(commId, members, summary,
                    members.size(), internal, external));
        }

        // Sort by size descending
        communities.sort((a, b) -> Integer.compare(b.size(), a.size()));

        return new DetectionResult(communities, nodeToComm, modularity, iter);
    }

    /**
     * Detects communities with default iterations.
     */
    public static DetectionResult detect(KnowledgeGraphStore graph) {
        return detect(graph, 10);
    }

    // ── Internal ──────────────────────────────────────────────────────────

    private static Map<String, Set<String>> buildAdjacency(
            KnowledgeGraphStore graph, List<String> nodes) {
        Map<String, Set<String>> adj = new HashMap<>();
        for (String node : nodes) {
            adj.put(node, new HashSet<>());
        }
        for (KnowledgeGraphStore.Relation rel : graph.allRelations()) {
            adj.computeIfAbsent(rel.fromId(), k -> new HashSet<>()).add(rel.toId());
            adj.computeIfAbsent(rel.toId(), k -> new HashSet<>()).add(rel.fromId());
        }
        return adj;
    }

    private static int countTotalEdges(Map<String, Set<String>> adjacency) {
        int total = 0;
        for (var entry : adjacency.entrySet()) {
            total += entry.getValue().size();
        }
        return total / 2; // undirected
    }

    private static double computeModularity(
            Map<Integer, Set<String>> commToNodes,
            Map<String, Set<String>> adjacency,
            int totalEdges) {
        if (totalEdges == 0) return 0;
        double q = 0;
        for (var entry : commToNodes.entrySet()) {
            Set<String> members = entry.getValue();
            int internal = countInternalEdges(adjacency, members);
            int external = countExternalEdges(adjacency, members);
            int degree = internal + external;
            q += (double) internal / totalEdges
                    - Math.pow((double) degree / (2.0 * totalEdges), 2);
        }
        return q;
    }

    private static double computeModularityGain(
            String node,
            int fromComm, int toComm,
            Map<Integer, Set<String>> commToNodes,
            Map<String, Set<String>> adjacency,
            int totalEdges) {
        if (totalEdges == 0) return 0;

        Set<String> neighbors = adjacency.getOrDefault(node, Set.of());
        Set<String> toMembers = commToNodes.get(toComm);
        Set<String> fromMembers = commToNodes.get(fromComm);

        int k_i = neighbors.size();
        int k_i_in = (int) neighbors.stream().filter(toMembers::contains).count();
        int k_i_from = (int) neighbors.stream().filter(fromMembers::contains).count();
        int sigma_tot = computeDegreeSum(adjacency, toMembers);

        // Modularity gain for moving node i from C_from to C_to
        double gain = (double) (k_i_in - k_i_from) / totalEdges
                - (double) k_i * (sigma_tot - computeDegreeSum(adjacency, fromMembers))
                / (2.0 * totalEdges * totalEdges);
        return gain;
    }

    private static int computeDegreeSum(Map<String, Set<String>> adjacency, Set<String> nodes) {
        int sum = 0;
        for (String node : nodes) {
            sum += adjacency.getOrDefault(node, Set.of()).size();
        }
        return sum;
    }

    private static int countInternalEdges(Map<String, Set<String>> adjacency,
                                           Set<String> members) {
        int count = 0;
        for (String node : members) {
            for (String neighbor : adjacency.getOrDefault(node, Set.of())) {
                if (members.contains(neighbor)) {
                    count++;
                }
            }
        }
        return count / 2; // each counted twice
    }

    private static int countExternalEdges(Map<String, Set<String>> adjacency,
                                           Set<String> members) {
        int count = 0;
        for (String node : members) {
            for (String neighbor : adjacency.getOrDefault(node, Set.of())) {
                if (!members.contains(neighbor)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static String buildSummary(KnowledgeGraphStore graph, Set<String> members) {
        if (members.size() <= 3) {
            return String.join(", ", members);
        }
        // Find most central node as label
        String best = members.iterator().next();
        int maxDegree = 0;
        for (String node : members) {
            int degree = graph.neighbors(node).size();
            if (degree > maxDegree) {
                maxDegree = degree;
                best = node;
            }
        }
        return best + " (+" + (members.size() - 1) + " more)";
    }
}
