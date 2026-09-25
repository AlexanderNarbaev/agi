package io.matrix.federation.liquid.biochemistry;

import io.matrix.federation.liquid.KineticModulator;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W652 — Biochemical Orchestrator Tests.
 */
class BiochemicalOrchestratorTest {

    @Test
    void testCreateDefault() {
        BiochemicalOrchestrator orchestrator = BiochemicalOrchestrator.createDefault();
        assertNotNull(orchestrator);
        assertEquals(4, orchestrator.getModulators().size());
        assertNotNull(orchestrator.getModulator("DOPAMINE"));
        assertNotNull(orchestrator.getModulator("CORTISOL"));
    }

    @Test
    void testTickUpdatesAll() {
        BiochemicalOrchestrator orchestrator = BiochemicalOrchestrator.createDefault();

        double before = orchestrator.getModulator("DOPAMINE").getCurrentLevel();
        orchestrator.tick(1.0);
        double after = orchestrator.getModulator("DOPAMINE").getCurrentLevel();

        // Level should change after tick
        assertNotEquals(before, after, 0.0001);
    }

    @Test
    void testStressCascadeReducesDopamine() {
        BiochemicalOrchestrator orchestrator = BiochemicalOrchestrator.createDefault();

        // Set high cortisol
        orchestrator.getModulator("CORTISOL").applyNetworkEffect(0.8);
        double initialDopamine = orchestrator.getModulator("DOPAMINE").getCurrentLevel();

        // Run several ticks
        for (int i = 0; i < 20; i++) {
            orchestrator.tick(1.0);
        }

        double finalDopamine = orchestrator.getModulator("DOPAMINE").getCurrentLevel();
        assertTrue(finalDopamine < initialDopamine,
                "Stress cascade should reduce dopamine over time");
    }

    @Test
    void testMoodDerivation() {
        BiochemicalOrchestrator orchestrator = BiochemicalOrchestrator.createDefault();

        // Set high dopamine and serotonin for HAPPY mood
        orchestrator.getModulator("DOPAMINE").applyNetworkEffect(0.5);
        orchestrator.getModulator("SEROTONIN").applyNetworkEffect(0.3);

        String mood = orchestrator.getMood();
        // Mood should be derived from levels
        assertNotNull(mood);
    }

    @Test
    void testSleepNeed() {
        BiochemicalOrchestrator orchestrator = BiochemicalOrchestrator.createDefault();
        double sleepNeed = orchestrator.getSleepNeed();
        assertTrue(sleepNeed >= 0 && sleepNeed <= 1.0);
    }

    @Test
    void testRegisterUnregister() {
        BiochemicalOrchestrator orchestrator = BiochemicalOrchestrator.createDefault();

        KineticModulator custom = new KineticModulator(
                "CUSTOM", "Custom", 0.1, 0.05, 0, 1, 0.5, 1.0, 0.01, false);
        orchestrator.registerModulator(custom);
        assertEquals(5, orchestrator.getModulators().size());

        orchestrator.unregisterModulator("CUSTOM");
        assertEquals(4, orchestrator.getModulators().size());
    }

    @Test
    void testNonLinearInteraction() {
        BiochemicalNetwork network = new BiochemicalNetwork();
        network.addInteraction(new BiochemicalNetwork.Interaction(
                "A", "B", BiochemicalNetwork.InteractionType.SYNERGY, 0.8, 0.9));

        BiochemicalOrchestrator orchestrator = new BiochemicalOrchestrator(network);
        orchestrator.registerModulator(new KineticModulator(
                "A", "A", 0.1, 0.05, 0, 1, 0.8, 1.0, 0.01, false));
        orchestrator.registerModulator(new KineticModulator(
                "B", "B", 0.1, 0.05, 0, 1, 0.8, 1.0, 0.01, false));

        double beforeA = orchestrator.getModulator("A").getCurrentLevel();
        double beforeB = orchestrator.getModulator("B").getCurrentLevel();

        orchestrator.tick(1.0);

        // Both should have changed due to synergy
        assertNotEquals(beforeA, orchestrator.getModulator("A").getCurrentLevel(), 0.0001);
        assertNotEquals(beforeB, orchestrator.getModulator("B").getCurrentLevel(), 0.0001);
    }
}
