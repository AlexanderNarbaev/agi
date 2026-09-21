package io.matrix.federated;

import io.matrix.federation.liquid.biochemistry.StigmergyProtocol;
import io.matrix.federation.orchestration.IntegratedFederation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * W771 — Emergent Behavior Monitor.
 *
 * Analyzes swarm activity for unexpected solutions/clusters:
 * - Uses StigmergyProtocol data to detect emergent patterns
 * - Records "aha!" moments where swarm solves problems no single node could
 * - Tracks cluster formation and dissolution
 */
public final class EmergentBehaviorMonitor {

    /**
     * A detected emergent behavior (insight/aha! moment).
     */
    public record EmergentInsight(
            long timestamp,
            String topic,
            int participatingNodes,
            double collectiveStrength,
            String description,
            double significance
    ) {}

    /**
     * Summary of emergent behavior monitoring.
     */
    public record MonitoringSummary(
            int totalInsights,
            int uniqueTopics,
            double averageClusterSize,
            int largestCluster,
            List<EmergentInsight> insights
    ) {}

    private final List<EmergentInsight> insights = new ArrayList<>();
    private final Map<String, Set<String>> topicNodes = new ConcurrentHashMap<>();

    /**
     * Analyze swarm activity and detect emergent behaviors.
     *
     * @param federations Map of node ID to IntegratedFederation
     * @return Monitoring summary with detected insights
     */
    public MonitoringSummary analyzeSwarm(Map<Long, IntegratedFederation> federations) {
        // Collect pheromone data from all federations
        Map<String, Double> topicStrengths = new HashMap<>();
        Map<String, Set<Long>> topicParticipants = new HashMap<>();

        for (var entry : federations.entrySet()) {
            long nodeId = entry.getKey();
            IntegratedFederation fed = entry.getValue();
            StigmergyProtocol stigmergy = fed.getStigmergyProtocol();

            for (String topic : stigmergy.getActiveTopics()) {
                StigmergyProtocol.PheromoneSignal signal = stigmergy.getSignal(topic);
                topicStrengths.merge(topic, signal.aggregatedStrength(), Double::sum);
                topicParticipants.computeIfAbsent(topic, k -> new HashSet<>()).add(nodeId);
            }
        }

        // Detect emergent insights: topics where many nodes cluster
        for (var entry : topicParticipants.entrySet()) {
            String topic = entry.getKey();
            Set<Long> participants = entry.getValue();
            double strength = topicStrengths.getOrDefault(topic, 0.0);

            // Emergent if cluster is large AND strength is high
            if (participants.size() >= Math.max(2, federations.size() / 4) && strength > 5.0) {
                double significance = calculateSignificance(participants.size(), strength, federations.size());
                EmergentInsight insight = new EmergentInsight(
                    System.currentTimeMillis(),
                    topic,
                    participants.size(),
                    strength,
                    "Cluster of " + participants.size() + " nodes forming around topic '" + topic + "'",
                    significance
                );
                insights.add(insight);
                topicNodes.computeIfAbsent(topic, k -> new HashSet<>())
                    .addAll(participants.stream().map(String::valueOf).toList());
            }
        }

        int uniqueTopics = (int) topicParticipants.values().stream()
            .mapToInt(Set::size).distinct().count();

        double avgClusterSize = topicParticipants.values().stream()
            .mapToInt(Set::size).average().orElse(0);

        int largestCluster = topicParticipants.values().stream()
            .mapToInt(Set::size).max().orElse(0);

        return new MonitoringSummary(
            insights.size(), uniqueTopics, avgClusterSize, largestCluster,
            new ArrayList<>(insights)
        );
    }

    /**
     * Calculate significance of an emergent insight.
     */
    private double calculateSignificance(int clusterSize, double strength, int totalNodes) {
        double sizeRatio = (double) clusterSize / totalNodes;
        double normalizedStrength = Math.min(1.0, strength / 20.0);
        return (sizeRatio + normalizedStrength) / 2.0;
    }

    /**
     * Log an "aha!" moment from a node.
     */
    public void logInsight(long nodeId, String description, double confidence) {
        EmergentInsight insight = new EmergentInsight(
            System.currentTimeMillis(),
            "node-" + nodeId,
            1,
            confidence,
            description,
            confidence
        );
        insights.add(insight);
    }

    public List<EmergentInsight> getInsights() {
        return new ArrayList<>(insights);
    }

    public void clear() {
        insights.clear();
        topicNodes.clear();
    }
}
