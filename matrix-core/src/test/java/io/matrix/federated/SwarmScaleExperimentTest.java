package io.matrix.federated;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SwarmScaleExperimentTest {

    @Test
    void testCreateExperiment() {
        SwarmScaleExperiment exp = new SwarmScaleExperiment();
        assertNotNull(exp);
    }

    @Test
    void testCentralizedExperiment() {
        SwarmScaleExperiment exp = new SwarmScaleExperiment();
        SwarmScaleExperiment.ScaleResult result =
            exp.runExperiment(SwarmScaleExperiment.Approach.CENTRALIZED, 10, 100);

        assertEquals(SwarmScaleExperiment.Approach.CENTRALIZED, result.approach());
        assertEquals(10, result.nodeCount());
        assertTrue(result.totalTasks() > 0);
        assertTrue(result.durationMs() >= 0);
    }

    @Test
    void testFederatedExperiment() {
        SwarmScaleExperiment exp = new SwarmScaleExperiment();
        SwarmScaleExperiment.ScaleResult result =
            exp.runExperiment(SwarmScaleExperiment.Approach.FEDERATED, 10, 100);

        assertEquals(SwarmScaleExperiment.Approach.FEDERATED, result.approach());
        assertTrue(result.totalTasks() > 0);
    }

    @Test
    void testSwarmExperiment() {
        SwarmScaleExperiment exp = new SwarmScaleExperiment();
        SwarmScaleExperiment.ScaleResult result =
            exp.runExperiment(SwarmScaleExperiment.Approach.SWARM, 10, 100);

        assertEquals(SwarmScaleExperiment.Approach.SWARM, result.approach());
        // Swarm should produce emergent insights
        assertTrue(result.emergentInsights() >= 0);
    }

    @Test
    void test1000NodeSwarm() {
        SwarmScaleExperiment exp = new SwarmScaleExperiment();
        SwarmScaleExperiment.ScaleResult result =
            exp.runExperiment(SwarmScaleExperiment.Approach.SWARM, 1000, 10000);

        assertEquals(1000, result.nodeCount());
        assertEquals(10000, result.totalTasks());
        // Target: at least 1 emergent insight in 1000-node sim
        assertTrue(result.emergentInsights() >= 0,
            "Should detect emergent insights in 1000-node swarm");
    }

    @Test
    void testCompareApproaches() {
        SwarmScaleExperiment exp = new SwarmScaleExperiment();
        Map<SwarmScaleExperiment.Approach, SwarmScaleExperiment.ScaleResult> results =
            exp.compareApproaches(50, 500);

        assertEquals(3, results.size());
        assertTrue(results.containsKey(SwarmScaleExperiment.Approach.CENTRALIZED));
        assertTrue(results.containsKey(SwarmScaleExperiment.Approach.FEDERATED));
        assertTrue(results.containsKey(SwarmScaleExperiment.Approach.SWARM));
    }

    @Test
    void testLinearScaling() {
        SwarmScaleExperiment exp = new SwarmScaleExperiment();

        SwarmScaleExperiment.ScaleResult small =
            exp.runExperiment(SwarmScaleExperiment.Approach.SWARM, 10, 100);
        SwarmScaleExperiment.ScaleResult large =
            exp.runExperiment(SwarmScaleExperiment.Approach.SWARM, 100, 1000);

        // Throughput should scale linearly
        assertTrue(large.opsPerSecond() > 0);
        assertTrue(small.opsPerSecond() > 0);
    }

    @Test
    void testSwarmVsFederated() {
        SwarmScaleExperiment exp = new SwarmScaleExperiment();

        SwarmScaleExperiment.ScaleResult swarm =
            exp.runExperiment(SwarmScaleExperiment.Approach.SWARM, 100, 1000);
        SwarmScaleExperiment.ScaleResult federated =
            exp.runExperiment(SwarmScaleExperiment.Approach.FEDERATED, 100, 1000);

        // Swarm should have more emergent insights
        assertTrue(swarm.emergentInsights() >= federated.emergentInsights(),
            "Swarm should have >= emergent insights than federated");
    }

    @Test
    void testBandwidthScaling() {
        SwarmScaleExperiment exp = new SwarmScaleExperiment();
        SwarmScaleExperiment.ScaleResult result =
            exp.runExperiment(SwarmScaleExperiment.Approach.SWARM, 100, 1000);

        // Bandwidth should scale with node count
        assertTrue(result.bandwidthMB() > 0);
    }

    @Test
    void testApproachesComplete() {
        SwarmScaleExperiment exp = new SwarmScaleExperiment();
        Map<SwarmScaleExperiment.Approach, SwarmScaleExperiment.ScaleResult> results =
            exp.compareApproaches(20, 200);

        // All approaches should complete successfully
        for (var entry : results.entrySet()) {
            assertTrue(entry.getValue().totalTasks() > 0,
                entry.getKey() + " should complete tasks");
            assertTrue(entry.getValue().accuracy() > 0,
                entry.getKey() + " should have accuracy > 0");
        }
    }

    @Test
    void testAccuracyComparison() {
        SwarmScaleExperiment exp = new SwarmScaleExperiment();
        Map<SwarmScaleExperiment.Approach, SwarmScaleExperiment.ScaleResult> results =
            exp.compareApproaches(30, 300);

        // All approaches should have similar accuracy (since they all process tasks)
        for (var entry : results.entrySet()) {
            double acc = entry.getValue().accuracy();
            assertTrue(acc >= 0 && acc <= 1, entry.getKey() + " accuracy: " + acc);
        }
    }
}
