package io.matrix.api.federation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WAVE T-02 — In-memory federation node registry.
 *
 * <p>Tracks federation nodes that have joined via POST /v1/federate.
 * T-02.5 will replace with sharded cross-cluster discovery.</p>
 *
 * <p><b>CONSTITUTION:</b> No LLM is invoked. Pure structural registry.</p>
 */
public final class FederationRegistry {

    public record Node(String nodeId, String region, int shardCapacity, Instant joinedAt) {}

    private final ConcurrentHashMap<String, Node> nodes = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<String> joinOrder = new CopyOnWriteArrayList<>();

    public Node join(String region, int shardCapacity) {
        String id = "node_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Node node = new Node(id, region, shardCapacity, Instant.now());
        nodes.put(id, node);
        joinOrder.add(id);
        return node;
    }

    public Node get(String nodeId) {
        return nodes.get(nodeId);
    }

    public int size() {
        return nodes.size();
    }

    public List<Node> list() {
        return joinOrder.stream().map(nodes::get).toList();
    }

    public boolean leave(String nodeId) {
        if (nodes.remove(nodeId) != null) {
            joinOrder.remove(nodeId);
            return true;
        }
        return false;
    }
}
