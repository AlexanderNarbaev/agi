package io.matrix.advanced;

import java.util.*;

/**
 * W881 — Synaptic Pruner.
 *
 * Implements entropy-based pruning to maintain constant memory size.
 * Deletes connections where Utility * Recency < threshold.
 */
public final class SynapticPruner {

    public record Connection(String id, double utility, long lastAccessed) {}

    public record PruneResult(int totalBefore, int pruned, int retained) {}

    private final Map<String, Connection> connections = new HashMap<>();
    private final double utilityThreshold;
    private final long recencyThresholdMs;

    public SynapticPruner(double utilityThreshold, long recencyThresholdMs) {
        this.utilityThreshold = utilityThreshold;
        this.recencyThresholdMs = recencyThresholdMs;
    }

    public SynapticPruner() {
        this(0.1, 86400000L); // 0.1 utility, 24h recency
    }

    public void addConnection(String id, double utility) {
        connections.put(id, new Connection(id, utility, System.currentTimeMillis()));
    }

    public void touch(String id) {
        Connection c = connections.get(id);
        if (c != null) {
            connections.put(id, new Connection(c.id(), c.utility(), System.currentTimeMillis()));
        }
    }

    /**
     * Prune connections that fall below the threshold.
     */
    public PruneResult prune() {
        long now = System.currentTimeMillis();
        int before = connections.size();
        List<String> toPrune = new ArrayList<>();

        for (Connection c : connections.values()) {
            long age = now - c.lastAccessed();
            double recencyFactor = Math.max(0, 1.0 - (double) age / recencyThresholdMs);
            double effectiveUtility = c.utility() * recencyFactor;

            if (effectiveUtility < utilityThreshold) {
                toPrune.add(c.id());
            }
        }

        for (String id : toPrune) {
            connections.remove(id);
        }

        return new PruneResult(before, toPrune.size(), connections.size());
    }

    public int getConnectionCount() {
        return connections.size();
    }
}
