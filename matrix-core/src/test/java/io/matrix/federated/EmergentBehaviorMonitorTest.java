package io.matrix.federated;

import io.matrix.federation.liquid.NodeRole;
import io.matrix.federation.liquid.biochemistry.StigmergyProtocol;
import io.matrix.federation.orchestration.IntegratedFederation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EmergentBehaviorMonitorTest {

    @Test
    void testCreateMonitor() {
        EmergentBehaviorMonitor monitor = new EmergentBehaviorMonitor();
        assertNotNull(monitor);
        assertTrue(monitor.getInsights().isEmpty());
    }

    @Test
    void testDetectEmergentCluster() {
        EmergentBehaviorMonitor monitor = new EmergentBehaviorMonitor();
        Map<Long, IntegratedFederation> federations = new HashMap<>();

        // 10 nodes all deposit on the same topic — should detect cluster
        for (long i = 1; i <= 10; i++) {
            IntegratedFederation fed = new IntegratedFederation(i, 42L);
            fed.registerNode(i, NodeRole.ADULT);
            for (int j = 0; j < 3; j++) {
                fed.depositPheromone(i, "hot-topic",
                    StigmergyProtocol.PheromoneType.EXPLORATION, 0.8);
            }
            federations.put(i, fed);
        }

        EmergentBehaviorMonitor.MonitoringSummary summary = monitor.analyzeSwarm(federations);

        assertTrue(summary.totalInsights() > 0, "Should detect at least 1 emergent insight");
    }

    @Test
    void test100NodeSwarm() {
        EmergentBehaviorMonitor monitor = new EmergentBehaviorMonitor();
        Map<Long, IntegratedFederation> federations = new HashMap<>();

        for (long i = 1; i <= 100; i++) {
            IntegratedFederation fed = new IntegratedFederation(i, 42L);
            fed.registerNode(i, NodeRole.ADULT);
            // Half cluster on topic A
            if (i <= 50) {
                fed.depositPheromone(i, "topic-A",
                    StigmergyProtocol.PheromoneType.EXPLORATION, 0.9);
            } else {
                fed.depositPheromone(i, "topic-B",
                    StigmergyProtocol.PheromoneType.REWARD, 0.5);
            }
            federations.put(i, fed);
        }

        EmergentBehaviorMonitor.MonitoringSummary summary = monitor.analyzeSwarm(federations);

        // Target: >= 1 emergent insight in 100-node sim
        assertTrue(summary.totalInsights() >= 1,
            "Should detect >= 1 emergent insight, got: " + summary.totalInsights());
    }

    @Test
    void testSignificanceCalculation() {
        EmergentBehaviorMonitor monitor = new EmergentBehaviorMonitor();
        Map<Long, IntegratedFederation> federations = new HashMap<>();

        // 8 of 10 nodes cluster on one topic
        for (long i = 1; i <= 10; i++) {
            IntegratedFederation fed = new IntegratedFederation(i, 42L);
            fed.registerNode(i, NodeRole.ADULT);
            if (i <= 8) {
                for (int j = 0; j < 5; j++) {
                    fed.depositPheromone(i, "dominant-topic",
                        StigmergyProtocol.PheromoneType.EXPLORATION, 1.0);
                }
            }
            federations.put(i, fed);
        }

        monitor.analyzeSwarm(federations);

        // Most recent insight should have high significance
        List<EmergentBehaviorMonitor.EmergentInsight> insights = monitor.getInsights();
        assertFalse(insights.isEmpty());
        EmergentBehaviorMonitor.EmergentInsight last = insights.get(insights.size() - 1);
        assertTrue(last.significance() > 0.5,
            "Significance should be > 0.5 for dominant cluster: " + last.significance());
    }

    @Test
    void testNoEmergentBehavior() {
        EmergentBehaviorMonitor monitor = new EmergentBehaviorMonitor();
        Map<Long, IntegratedFederation> federations = new HashMap<>();

        // 3 nodes, each on different topic — no cluster
        for (long i = 1; i <= 3; i++) {
            IntegratedFederation fed = new IntegratedFederation(i, 42L);
            fed.registerNode(i, NodeRole.ADULT);
            fed.depositPheromone(i, "topic-" + i,
                StigmergyProtocol.PheromoneType.EXPLORATION, 0.3);
            federations.put(i, fed);
        }

        EmergentBehaviorMonitor.MonitoringSummary summary = monitor.analyzeSwarm(federations);

        assertEquals(0, summary.totalInsights(), "Should detect 0 insights for scattered nodes");
    }

    @Test
    void testLogInsight() {
        EmergentBehaviorMonitor monitor = new EmergentBehaviorMonitor();
        monitor.logInsight(1L, "Discovered pattern X", 0.9);
        monitor.logInsight(2L, "Found correlation Y", 0.7);

        List<EmergentBehaviorMonitor.EmergentInsight> insights = monitor.getInsights();
        assertEquals(2, insights.size());
        assertEquals("Discovered pattern X", insights.get(0).description());
    }

    @Test
    void testClearInsights() {
        EmergentBehaviorMonitor monitor = new EmergentBehaviorMonitor();
        monitor.logInsight(1L, "Test", 0.8);
        assertFalse(monitor.getInsights().isEmpty());

        monitor.clear();
        assertTrue(monitor.getInsights().isEmpty());
    }

    @Test
    void testLargestCluster() {
        EmergentBehaviorMonitor monitor = new EmergentBehaviorMonitor();
        Map<Long, IntegratedFederation> federations = new HashMap<>();

        for (long i = 1; i <= 10; i++) {
            IntegratedFederation fed = new IntegratedFederation(i, 42L);
            fed.registerNode(i, NodeRole.ADULT);
            // 7 of 10 cluster on one topic
            if (i <= 7) {
                for (int j = 0; j < 5; j++) {
                    fed.depositPheromone(i, "big-cluster",
                        StigmergyProtocol.PheromoneType.EXPLORATION, 1.0);
                }
            }
            federations.put(i, fed);
        }

        EmergentBehaviorMonitor.MonitoringSummary summary = monitor.analyzeSwarm(federations);

        assertTrue(summary.largestCluster() >= 7,
            "Largest cluster should be >= 7: " + summary.largestCluster());
    }

    @Test
    void testSummaryFields() {
        EmergentBehaviorMonitor monitor = new EmergentBehaviorMonitor();
        Map<Long, IntegratedFederation> federations = new HashMap<>();
        federations.put(1L, createFedWithPheromones(1L, "topic-A", 5));
        federations.put(2L, createFedWithPheromones(2L, "topic-A", 5));

        EmergentBehaviorMonitor.MonitoringSummary summary = monitor.analyzeSwarm(federations);

        assertTrue(summary.averageClusterSize() > 0);
        assertNotNull(summary.insights());
    }

    @Test
    void testEmptyFederations() {
        EmergentBehaviorMonitor monitor = new EmergentBehaviorMonitor();
        Map<Long, IntegratedFederation> federations = new HashMap<>();

        EmergentBehaviorMonitor.MonitoringSummary summary = monitor.analyzeSwarm(federations);
        assertEquals(0, summary.totalInsights());
    }

    private IntegratedFederation createFedWithPheromones(long id, String topic, int count) {
        IntegratedFederation fed = new IntegratedFederation(id, 42L);
        fed.registerNode(id, NodeRole.ADULT);
        for (int j = 0; j < count; j++) {
            fed.depositPheromone(id, topic,
                StigmergyProtocol.PheromoneType.EXPLORATION, 0.8);
        }
        return fed;
    }
}
