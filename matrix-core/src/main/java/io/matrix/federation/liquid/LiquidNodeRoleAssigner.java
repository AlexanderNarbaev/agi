package io.matrix.federation.liquid;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * W569 — Liquid Node Role Assigner.
 *
 * Manages dynamic role assignment for federation nodes.
 * Evaluates node metrics and promotes/demotes roles automatically.
 */
public final class LiquidNodeRoleAssigner {

    private final Map<Long, NodeMetrics> nodeMetrics = new ConcurrentHashMap<>();
    private final Map<Long, NodeRole> assignedRoles = new ConcurrentHashMap<>();
    private final List<RoleTransition> transitionLog = Collections.synchronizedList(new ArrayList<>());

    public static final class NodeMetrics {
        private long uptimeStartNs;
        private long totalUptimeNs;
        private int correctVotes;
        private int totalVotes;
        private int contributions;

        public NodeMetrics() {
            this.uptimeStartNs = System.nanoTime();
        }

        public double getUptimeRatio() {
            long now = System.nanoTime();
            long elapsed = now - uptimeStartNs + totalUptimeNs;
            long maxUptime = 24L * 3600 * 1_000_000_000L;
            return Math.min(1.0, (double) elapsed / maxUptime);
        }

        public double getAccuracy() {
            if (totalVotes == 0) return 0.0;
            return (double) correctVotes / totalVotes;
        }

        public int getContributions() { return contributions; }

        public void recordVote(boolean correct) {
            totalVotes++;
            if (correct) correctVotes++;
        }

        public void addContribution(int count) { contributions += count; }
    }

    public record RoleTransition(long nodeId, NodeRole fromRole, NodeRole toRole, String reason, long timestampNs) {}

    public void registerNode(long nodeId) {
        nodeMetrics.putIfAbsent(nodeId, new NodeMetrics());
        assignedRoles.putIfAbsent(nodeId, NodeRole.INFANT);
    }

    public void recordVote(long nodeId, boolean correct) {
        NodeMetrics m = nodeMetrics.get(nodeId);
        if (m != null) m.recordVote(correct);
    }

    public void addContributions(long nodeId, int count) {
        NodeMetrics m = nodeMetrics.get(nodeId);
        if (m != null) m.addContribution(count);
    }

    public RoleTransition evaluateRole(long nodeId, int capabilityLevel) {
        NodeMetrics metrics = nodeMetrics.get(nodeId);
        NodeRole currentRole = assignedRoles.get(nodeId);
        if (metrics == null || currentRole == null) return null;

        double uptime = metrics.getUptimeRatio();
        double accuracy = metrics.getAccuracy();
        int contributions = metrics.getContributions();

        if (currentRole.shouldDemote(uptime, accuracy, contributions)) {
            NodeRole lower = currentRole.previous();
            if (lower != null) return transitionTo(nodeId, currentRole, lower,
                String.format("demote: uptime=%.2f acc=%.2f contribs=%d", uptime, accuracy, contributions));
        }

        NodeRole ideal = NodeRole.evaluate(capabilityLevel, uptime, accuracy, contributions);
        if (ideal.ordinal() > currentRole.ordinal()) {
            return transitionTo(nodeId, currentRole, ideal,
                String.format("promote: level=%d uptime=%.2f acc=%.2f contribs=%d", capabilityLevel, uptime, accuracy, contributions));
        }
        return null;
    }

    public RoleTransition forceTransition(long nodeId, NodeRole newRole, String reason) {
        NodeRole current = assignedRoles.getOrDefault(nodeId, NodeRole.INFANT);
        return transitionTo(nodeId, current, newRole, reason);
    }

    private RoleTransition transitionTo(long nodeId, NodeRole from, NodeRole to, String reason) {
        assignedRoles.put(nodeId, to);
        RoleTransition t = new RoleTransition(nodeId, from, to, reason, System.nanoTime());
        transitionLog.add(t);
        return t;
    }

    public NodeRole getRole(long nodeId) { return assignedRoles.getOrDefault(nodeId, NodeRole.INFANT); }
    public NodeMetrics getMetrics(long nodeId) { return nodeMetrics.get(nodeId); }
    public Map<Long, NodeRole> getAllRoles() { return Collections.unmodifiableMap(assignedRoles); }
    public List<RoleTransition> getTransitionLog() { return Collections.unmodifiableList(transitionLog); }
    public int getNodeCount() { return nodeMetrics.size(); }

    public List<Long> getNodesByRole(NodeRole role) {
        List<Long> r = new ArrayList<>();
        for (var e : assignedRoles.entrySet()) if (e.getValue() == role) r.add(e.getKey());
        return r;
    }

    public double getVotingWeight(long nodeId) {
        NodeRole role = assignedRoles.getOrDefault(nodeId, NodeRole.INFANT);
        NodeMetrics m = nodeMetrics.get(nodeId);
        if (m == null || !role.canVote()) return 0.0;
        return role.votingWeight * m.getAccuracy();
    }

    public boolean hasVetoPower(long nodeId) {
        return assignedRoles.getOrDefault(nodeId, NodeRole.INFANT).hasVetoPower();
    }
}
