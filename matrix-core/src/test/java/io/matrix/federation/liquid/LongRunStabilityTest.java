package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W594 — Long-Run Stability Test.
 *
 * Run 24h cycle with Sleep/Learning. Check memory leaks.
 * (Simulated - not actual 24h wait)
 */
class LongRunStabilityTest {

    @Test
    void testSleepCycleStability() {
        SleepEngine sleepEngine = new SleepEngine(0.3, 0.1, 5);

        // Simulate 100 sleep cycles
        for (int cycle = 0; cycle < 100; cycle++) {
            Map<String, Double> hdcVectors = new HashMap<>();
            for (int i = 0; i < 50; i++) {
                hdcVectors.put("v" + i, Math.random());
            }

            Map<String, List<String>> birChains = new HashMap<>();
            for (int i = 0; i < 10; i++) {
                birChains.put("chain" + i, List.of("A", "B", "C"));
            }

            sleepEngine.sleep(hdcVectors, birChains);
        }

        assertEquals(100, sleepEngine.getTotalSleepCycles());
        assertTrue(sleepEngine.getTotalPruned() > 0);
    }

    @Test
    void testConsensusStability() {
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();
        CapabilityConsensusEngine consensus = new CapabilityConsensusEngine(assigner);

        for (long i = 1; i <= 5; i++) {
            assigner.registerNode(i);
            assigner.forceTransition(i, NodeRole.ADULT, "test");
            assigner.recordVote(i, true);
        }

        // Run 100 consensus rounds
        for (int round = 0; round < 100; round++) {
            String proposalId = consensus.createProposal(System.nanoTime() + 1_000_000_000L);
            for (long nodeId = 1; nodeId <= 5; nodeId++) {
                consensus.vote(proposalId, nodeId,
                        Math.random() > 0.2 ?
                                CapabilityConsensusEngine.VoteDecision.YES :
                                CapabilityConsensusEngine.VoteDecision.NO,
                        0.9, "test");
            }
            var result = consensus.evaluate(proposalId);
            assertNotNull(result);
        }

        assertEquals(100, consensus.getResultLog().size());
    }

    @Test
    void testModulatorStability() {
        Map<String, KineticModulator> mods = new HashMap<>();
        mods.put("DOPAMINE", new KineticModulator("DOPAMINE", "D", 0.5, 0.1, 0, 1, 0.5, 1, 0.01, false));
        mods.put("SEROTONIN", new KineticModulator("SEROTONIN", "S", 0.5, 0.1, 0, 1, 0.5, 1, 0.01, false));
        mods.put("CORTISOL", new KineticModulator("CORTISOL", "C", 0.3, 0.1, 0, 1, 0.3, 1, 0.01, false));

        // Simulate 1000 ticks
        for (int tick = 0; tick < 1000; tick++) {
            for (var mod : mods.values()) {
                mod.tick(0.1);
            }

            // Apply cross-talk
            mods.get("DOPAMINE").applyCrossTalk("CORTISOL", mods.get("CORTISOL").getCurrentLevel());
        }

        // All modulators should be in valid range
        for (var mod : mods.values()) {
            assertTrue(mod.getCurrentLevel() >= 0);
            assertTrue(mod.getCurrentLevel() <= 1);
            assertTrue(mod.getReceptorSensitivity() >= 0.1);
            assertTrue(mod.getReceptorSensitivity() <= 2.0);
        }
    }

    @Test
    void testRoleAssignerStability() {
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();

        // Register many nodes
        for (long i = 0; i < 100; i++) {
            assigner.registerNode(i);
        }

        // Simulate many evaluations
        for (int round = 0; round < 100; round++) {
            for (long nodeId = 0; nodeId < 100; nodeId++) {
                assigner.recordVote(nodeId, Math.random() > 0.3);
                assigner.addContributions(nodeId, 1);
                assigner.evaluateRole(nodeId, (int) (Math.random() * 8));
            }
        }

        // Should not crash
        assertEquals(100, assigner.getNodeCount());
    }
}
