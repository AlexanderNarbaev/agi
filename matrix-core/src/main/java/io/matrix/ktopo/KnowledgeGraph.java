package io.matrix.ktopo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Knowledge Graph — nodes are knowledge artifacts, edges are typed relations
 * with provenance.
 *
 * <p>Per SPEC-003: graph-based knowledge representation with Ricci flow
 * analysis for community detection, drift fingerprinting, and gap discovery.
 *
 * <p>Each node has: id, content, domain, provenance (how it was created),
 * accessCount, lastAccessed, importance. Each edge has: source, target,
 * relation type, weight, provenance.
 */
public final class KnowledgeGraph {

    private final Map<String, KnowledgeNode> nodes = new HashMap<>();
    private final List<KnowledgeEdge> edges = new ArrayList<>();
    private final Map<String, Set<String>> adjacency = new HashMap<>(); // nodeId → neighborIds

    public void addNode(KnowledgeNode node) {
        nodes.put(node.id(), node);
        adjacency.computeIfAbsent(node.id(), k -> new HashSet<>());
    }

    public void addEdge(KnowledgeEdge edge) {
        edges.add(edge);
        adjacency.computeIfAbsent(edge.source(), k -> new HashSet<>()).add(edge.target());
        adjacency.computeIfAbsent(edge.target(), k -> new HashSet<>()).add(edge.source());
    }

    public KnowledgeNode getNode(String id) { return nodes.get(id); }
    public List<KnowledgeEdge> edges() { return List.copyOf(edges); }
    public int nodeCount() { return nodes.size(); }
    public int edgeCount() { return edges.size(); }

    /** Get neighbors of a node. */
    public Set<String> neighbors(String nodeId) {
        return adjacency.getOrDefault(nodeId, Set.of());
    }

    /** Get all nodes in a domain. */
    public List<KnowledgeNode> nodesInDomain(String domain) {
        return nodes.values().stream()
                .filter(n -> domain.equals(n.domain()))
                .toList();
    }

    /** Compute degree distribution. */
    public Map<String, Integer> degreeDistribution() {
        Map<String, Integer> dist = new HashMap<>();
        for (var entry : adjacency.entrySet()) {
            dist.put(entry.getKey(), entry.getValue().size());
        }
        return dist;
    }

    /** Knowledge node. */
    public record KnowledgeNode(
            String id,
            String content,
            String domain,
            String provenance,
            int accessCount,
            long lastAccessed,
            double importance) {}

    /** Knowledge edge. */
    public record KnowledgeEdge(
            String source,
            String target,
            String relation,
            double weight,
            String provenance) {}
}
