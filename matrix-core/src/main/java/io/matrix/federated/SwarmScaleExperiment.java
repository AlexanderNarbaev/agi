package io.matrix.federated;

import io.matrix.federation.liquid.NodeRole;
import io.matrix.federation.liquid.biochemistry.StigmergyProtocol;
import io.matrix.federation.orchestration.IntegratedFederation;
import java.util.*;

/**
 * W776 — Large-Scale Swarm Experiment.
 *
 * Compares Centralized vs Federated vs Swarm approaches:
 * - Centralized: all processing on one node
 * - Federated: distributed but coordinated
 * - Swarm: emergent, no central coordination
 */
public final class SwarmScaleExperiment {

    /**
     * Type of approach.
     */
    public enum Approach {
        CENTRALIZED,
        FEDERATED,
        SWARM
    }

    /**
     * Result of a scale experiment.
     */
    public record ScaleResult(
            Approach approach,
            int nodeCount,
            int totalTasks,
            long durationMs,
            double accuracy,
            double bandwidthMB,
            double opsPerSecond,
            int emergentInsights
    ) {}

    /**
     * Run a scale experiment with the given approach.
     */
    public ScaleResult runExperiment(Approach approach, int nodeCount, int taskCount) {
        long start = System.currentTimeMillis();

        Map<Long, IntegratedFederation> federations = new HashMap<>();
        for (long i = 1; i <= nodeCount; i++) {
            IntegratedFederation fed = new IntegratedFederation(i, 42L);
            fed.registerNode(i, NodeRole.ADULT);
            federations.put(i, fed);
        }

        // Distribute tasks across nodes
        Random rng = new Random(42);
        int tasksCompleted = 0;

        for (int t = 0; t < taskCount; t++) {
            long nodeId = (t % nodeCount) + 1;
            IntegratedFederation fed = federations.get(nodeId);

            switch (approach) {
                case CENTRALIZED -> {
                    // All tasks go to node 1
                    if (nodeId == 1) {
                        processTask(federations.get(1L), t);
                        tasksCompleted++;
                    }
                }
                case FEDERATED -> {
                    // Tasks distributed, coordinated via federation
                    processTask(fed, t);
                    if (rng.nextDouble() < 0.3) {
                        fed.depositPheromone(nodeId, "task-" + t,
                            StigmergyProtocol.PheromoneType.COORDINATION, 0.5);
                    }
                    tasksCompleted++;
                }
                case SWARM -> {
                    // Tasks distributed, emergent coordination
                    processTask(fed, t);
                    if (rng.nextDouble() < 0.5) {
                        fed.depositPheromone(nodeId, "swarm-" + t,
                            StigmergyProtocol.PheromoneType.EXPLORATION, 0.7);
                    }
                    tasksCompleted++;
                }
            }
        }

        // Run a few ticks for pheromones to propagate
        for (long i = 1; i <= nodeCount; i++) {
            federations.get(i).tick(1.0);
        }

        // Detect emergent insights for swarm
        int emergentInsights = 0;
        if (approach == Approach.SWARM) {
            EmergentBehaviorMonitor monitor = new EmergentBehaviorMonitor();
            EmergentBehaviorMonitor.MonitoringSummary summary = monitor.analyzeSwarm(federations);
            emergentInsights = summary.totalInsights();
        }

        long duration = System.currentTimeMillis() - start;
        double accuracy = (double) tasksCompleted / taskCount;
        double bandwidthMB = nodeCount * 0.1; // 100KB per node
        double opsPerSecond = taskCount * 1000.0 / Math.max(duration, 1);

        return new ScaleResult(
            approach, nodeCount, tasksCompleted, duration,
            accuracy, bandwidthMB, opsPerSecond, emergentInsights
        );
    }

    /**
     * Process a task on a node.
     */
    private void processTask(IntegratedFederation fed, int taskId) {
        // Simulate BIR-based task processing
        fed.injectStimulus("DOPAMINE", 0.01); // small reward
        fed.tick(0.01);
    }

    /**
     * Compare all approaches at a given scale.
     */
    public Map<Approach, ScaleResult> compareApproaches(int nodeCount, int taskCount) {
        Map<Approach, ScaleResult> results = new LinkedHashMap<>();
        for (Approach approach : Approach.values()) {
            results.put(approach, runExperiment(approach, nodeCount, taskCount));
        }
        return results;
    }
}
