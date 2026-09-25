package io.matrix.federation.liquid.simulation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W606 — Federation Simulation Tests.
 */
class FederationSimulationTest {

    @Test
    void testResilience10Percent() {
        var result = FederationSimulation.testResilience(100, 0.1);
        assertNotNull(result);
        assertEquals(100, result.totalNodes());
        assertTrue(result.aliveNodes() > 0);
        assertTrue(result.consensusRate() > 0);
    }

    @Test
    void testResilience50Percent() {
        var result = FederationSimulation.testResilience(100, 0.5);
        assertNotNull(result);
        assertEquals(100, result.totalNodes());
        assertTrue(result.aliveNodes() > 0);
    }

    @Test
    void testConsensusConvergence() {
        var result = FederationSimulation.testConsensusConvergence(100);
        assertNotNull(result);
        assertEquals(100, result.totalNodes());
        assertTrue(result.consensusRate() > 0);
    }

    @Test
    void testSybilAttack() {
        var result = FederationSimulation.testSybilAttack(100, 0.2);
        assertNotNull(result);
        assertEquals(100, result.totalNodes());
        assertTrue(result.consensusRate() >= 0);
    }

    @Test
    void testLargeScale() {
        var result = FederationSimulation.testResilience(1000, 0.1);
        assertNotNull(result);
        assertEquals(1000, result.totalNodes());
        assertTrue(result.aliveNodes() > 0);
    }

    @Test
    void testGenerateReport() {
        var result = FederationSimulation.testResilience(100, 0.1);
        String report = FederationSimulation.generateReport(java.util.List.of(result));
        assertNotNull(report);
        assertTrue(report.contains("Federation Scale Simulation Report"));
    }
}
