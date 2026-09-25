package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W598 — Final Integration Test.
 *
 * Tests all components working together.
 */
class FinalIntegrationTest {

    @Test
    void testFullSystemIntegration() {
        // 1. Create modulators
        Map<String, KineticModulator> mods = new HashMap<>();
        mods.put("DOPAMINE", new KineticModulator("DOPAMINE", "D", 0.5, 0.1, 0, 1, 0.5, 1, 0.01, false));
        mods.put("CORTISOL", new KineticModulator("CORTISOL", "C", 0.3, 0.1, 0, 1, 0.3, 1, 0.01, false));

        // 2. Create homeostat
        CorridorHomeostat homeostat = CorridorHomeostat.createDefault();

        // 3. Create role assigner
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();
        for (long i = 1; i <= 5; i++) {
            assigner.registerNode(i);
            assigner.forceTransition(i, NodeRole.ADULT, "test");
            assigner.recordVote(i, true);
        }

        // 4. Create consensus engine
        CapabilityConsensusEngine consensus = new CapabilityConsensusEngine(assigner);

        // 5. Create cognitive router
        CognitiveRouter router = new CognitiveRouter(mods, 0.5, 0.5);

        // 6. Create thought visualizer
        ThoughtProcessVisualizer viz = new ThoughtProcessVisualizer();

        // 7. Process a message
        CognitiveRouter.RoutingDecision routing = router.route("Is it ethical to lie?", 0.3, 0.5, true);
        assertEquals(CognitiveRouter.CognitiveMode.DEEP_UNDERSTANDING, routing.mode());

        // 8. Record thought
        viz.recordRouting(routing.mode(), routing.reason());
        viz.recordBirRule("ethics-1", "lying", "unethical", 0.9);

        // 9. Run consensus
        String proposalId = consensus.createProposal(System.nanoTime() + 1_000_000_000L);
        for (long nodeId = 1; nodeId <= 5; nodeId++) {
            consensus.vote(proposalId, nodeId, CapabilityConsensusEngine.VoteDecision.YES, 0.9, "test");
        }
        var result = consensus.evaluate(proposalId);
        assertEquals(CapabilityConsensusEngine.ConsensusStatus.APPROVED, result.status());

        // 10. Update homeostat
        homeostat.update("cpu_load", 0.5);
        assertFalse(homeostat.hasViolations());

        // 11. Verify all components
        assertNotNull(routing);
        assertNotNull(result);
        assertEquals(2, viz.getStepCount());
    }

    @Test
    void testTelemetryIntegration() {
        FederationTelemetryV2 telemetry = new FederationTelemetryV2();

        // Record events
        telemetry.incrementCounter("consensus_rounds_total");
        telemetry.setGauge("cpu_load", 0.5);
        telemetry.recordModulatorLevels(Map.of("dopamine", 0.8));

        // Export
        String prometheus = telemetry.exportPrometheus();
        String json = telemetry.exportJson();

        assertTrue(prometheus.contains("consensus_rounds_total"));
        assertTrue(json.contains("cpu_load"));
    }

    @Test
    void testSleepAndLearning() {
        SleepEngine sleepEngine = new SleepEngine(0.3, 0.1, 5);

        Map<String, Double> hdcVectors = new HashMap<>();
        hdcVectors.put("strong", 0.8);
        hdcVectors.put("weak", 0.1);

        Map<String, List<String>> birChains = new HashMap<>();
        birChains.put("chain1", List.of("A", "B", "C"));

        var result = sleepEngine.sleep(hdcVectors, birChains);

        assertEquals(1, hdcVectors.size()); // weak pruned
        assertTrue(result.rehearsed() > 0);
    }
}
