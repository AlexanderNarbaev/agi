package io.matrix.federation.orchestration;

import io.matrix.federation.liquid.NodeRole;
import io.matrix.federation.orchestration.IntegratedFederation;
import io.matrix.federation.liquid.biochemistry.StigmergyProtocol;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W691 — Integrated Federation Tests.
 *
 * Verifies that all previously orphaned components work together:
 * - BiochemicalOrchestrator
 * - StigmergyProtocol
 * - DynamicModulatorRegistry
 * - LiquidNodeRoleAssigner
 * - CapabilityConsensusEngine
 */
class IntegratedFederationTest {

    @Test
    void testFederationInitialization() {
        IntegratedFederation fed = new IntegratedFederation(1L, 42L);
        assertEquals(1L, fed.getFederationId());
        assertNotNull(fed.getBiochemicalOrchestrator());
        assertNotNull(fed.getStigmergyProtocol());
        assertNotNull(fed.getRoleAssigner());
        assertNotNull(fed.getConsensusEngine());
    }

    @Test
    void testNodeRegistration() {
        IntegratedFederation fed = new IntegratedFederation(1L, 42L);
        fed.registerNode(1L, NodeRole.ADULT);
        fed.registerNode(2L, NodeRole.LEARNER);
        fed.registerNode(3L, NodeRole.GUARDIAN);

        assertEquals(NodeRole.ADULT, fed.getNodeRole(1L));
        assertEquals(NodeRole.LEARNER, fed.getNodeRole(2L));
        assertEquals(NodeRole.GUARDIAN, fed.getNodeRole(3L));
    }

    @Test
    void testStimulusInjection() {
        IntegratedFederation fed = new IntegratedFederation(1L, 42L);
        double beforeCortisol = fed.getBiochemicalOrchestrator().getModulator("CORTISOL").getCurrentLevel();

        // Inject stress
        fed.injectStimulus("CORTISOL", 0.5);
        double afterCortisol = fed.getBiochemicalOrchestrator().getModulator("CORTISOL").getCurrentLevel();

        assertTrue(afterCortisol > beforeCortisol,
            "Stimulus should increase cortisol: " + beforeCortisol + " -> " + afterCortisol);
    }

    @Test
    void testTickAdvancesAllComponents() {
        IntegratedFederation fed = new IntegratedFederation(1L, 42L);
        Map<String, Double> beforeLevels = new HashMap<>();
        for (var entry : fed.getBiochemicalOrchestrator().getModulators().entrySet()) {
            beforeLevels.put(entry.getKey(), entry.getValue().getCurrentLevel());
        }

        // Run several ticks
        for (int i = 0; i < 10; i++) {
            fed.tick(1.0);
        }

        Map<String, Double> afterLevels = new HashMap<>();
        for (var entry : fed.getBiochemicalOrchestrator().getModulators().entrySet()) {
            afterLevels.put(entry.getKey(), entry.getValue().getCurrentLevel());
        }

        // At least one modulator should have changed
        boolean changed = false;
        for (var entry : beforeLevels.entrySet()) {
            if (Math.abs(entry.getValue() - afterLevels.get(entry.getKey())) > 0.001) {
                changed = true;
                break;
            }
        }
        assertTrue(changed, "Modulators should change over ticks");
    }

    @Test
    void testStigmergyClusterEmergence() {
        IntegratedFederation fed = new IntegratedFederation(1L, 42L);

        // Register 10 nodes
        for (long i = 1; i <= 10; i++) {
            fed.registerNode(i, NodeRole.ADULT);
        }

        // Nodes discover a hot topic and deposit pheromones
        for (long i = 1; i <= 10; i++) {
            fed.depositPheromone(i, "emerging-pattern",
                StigmergyProtocol.PheromoneType.EXPLORATION, 0.5 + (i % 5) * 0.1);
        }

        // Tick to let pheromones aggregate
        for (int i = 0; i < 5; i++) {
            fed.tick(1.0);
        }

        // The hot topic should be detected
        List<String> hotTopics = fed.getHotTopics(5);
        assertFalse(hotTopics.isEmpty(), "Hot topics should be detected");
        assertEquals("emerging-pattern", hotTopics.get(0),
            "The emerging pattern should be the hottest topic");
    }

    @Test
    void testStressCascadePropagation() {
        IntegratedFederation fed = new IntegratedFederation(1L, 42L);

        // Inject high cortisol (stress)
        fed.injectStimulus("CORTISOL", 0.7);

        double initialDopamine = fed.getBiochemicalOrchestrator().getModulator("DOPAMINE").getCurrentLevel();

        // Run stress cascade
        for (int i = 0; i < 50; i++) {
            fed.tick(1.0);
        }

        double finalDopamine = fed.getBiochemicalOrchestrator().getModulator("DOPAMINE").getCurrentLevel();
        assertTrue(finalDopamine < initialDopamine,
            "Stress cascade should reduce dopamine: " + initialDopamine + " -> " + finalDopamine);
    }

    @Test
    void testMoodDerivation() {
        IntegratedFederation fed = new IntegratedFederation(1L, 42L);

        // High dopamine = happy
        fed.injectStimulus("DOPAMINE", 0.4);
        fed.injectStimulus("SEROTONIN", 0.3);

        // Run a few ticks
        for (int i = 0; i < 5; i++) {
            fed.tick(1.0);
        }

        String mood = fed.getCurrentMood();
        assertNotNull(mood);
        // Mood should be NEUTRAL or HAPPY
        assertTrue(mood.equals("HAPPY") || mood.equals("NEUTRAL") || mood.equals("FLOW"),
            "Expected positive mood, got: " + mood);
    }

    @Test
    void testMetricsCollection() {
        IntegratedFederation fed = new IntegratedFederation(1L, 42L);

        fed.tick(1.0);
        fed.tick(1.0);

        Map<String, Object> metrics = fed.getMetrics();
        assertNotNull(metrics);
        assertTrue(metrics.containsKey("startTime"));
        assertTrue(metrics.containsKey("modulatorLevels"));
    }

    @Test
    void testEndToEndFlow() {
        // Full integration test: node receives input -> modulators react -> stigmergy coordinates
        IntegratedFederation fed = new IntegratedFederation(1L, 42L);

        // Setup: 5 nodes, one as guardian
        fed.registerNode(1L, NodeRole.GUARDIAN);
        fed.registerNode(2L, NodeRole.ADULT);
        fed.registerNode(3L, NodeRole.ADULT);
        fed.registerNode(4L, NodeRole.LEARNER);
        fed.registerNode(5L, NodeRole.INFANT);

        // Event: ethical decision needed
        fed.depositPheromone(1L, "ethics-check",
            StigmergyProtocol.PheromoneType.COORDINATION, 0.9);

        // Event: stress detected
        fed.injectStimulus("CORTISOL", 0.5);

        // Run for a while
        for (int i = 0; i < 20; i++) {
            fed.tick(1.0);
        }

        // Verify everything is working
        assertEquals(NodeRole.GUARDIAN, fed.getNodeRole(1L));
        assertNotNull(fed.getCurrentMood());
        assertFalse(fed.getHotTopics(5).isEmpty());

        Map<String, Double> levels = fed.getBiochemicalOrchestrator().getModulators()
            .entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().getCurrentLevel()
            ));
        levels.values().forEach(v ->
            assertTrue(v >= 0.0 && v <= 1.0,
                "Modulator level must stay in [0,1]: " + v)
        );
    }
}
