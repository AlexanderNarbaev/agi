package io.matrix.federation.liquid.biochemistry;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StressCascadeSimulationTest {

    @Test
    void testStressCascadeReducesDopamine() {
        BiochemicalOrchestrator orchestrator = BiochemicalOrchestrator.createDefault();
        orchestrator.getModulator("CORTISOL").applyNetworkEffect(0.7);
        double initialDopamine = orchestrator.getModulator("DOPAMINE").getCurrentLevel();
        for (int i = 0; i < 50; i++) orchestrator.tick(1.0);
        double finalDopamine = orchestrator.getModulator("DOPAMINE").getCurrentLevel();
        assertTrue(finalDopamine < initialDopamine);
    }

    @Test
    void testStressCascadeReducesSerotonin() {
        BiochemicalOrchestrator orchestrator = BiochemicalOrchestrator.createDefault();
        orchestrator.getModulator("CORTISOL").applyNetworkEffect(0.7);
        double initialSerotonin = orchestrator.getModulator("SEROTONIN").getCurrentLevel();
        for (int i = 0; i < 50; i++) orchestrator.tick(1.0);
        double finalSerotonin = orchestrator.getModulator("SEROTONIN").getCurrentLevel();
        assertTrue(finalSerotonin < initialSerotonin);
    }

    @Test
    void testNonLinearStressResponse() {
        BiochemicalNetwork linearNetwork = new BiochemicalNetwork();
        BiochemicalOrchestrator linearOrch = new BiochemicalOrchestrator(linearNetwork);
        linearOrch.registerModulator(new io.matrix.federation.liquid.KineticModulator(
                "CORTISOL", "Cortisol", 0.05, 0.03, 0, 1, 0.8, 1.0, 0.02, false));
        linearOrch.registerModulator(new io.matrix.federation.liquid.KineticModulator(
                "DOPAMINE", "Dopamine", 0.1, 0.05, 0, 1, 0.5, 1.0, 0.01, false));

        BiochemicalOrchestrator nonLinearOrch = BiochemicalOrchestrator.createDefault();
        nonLinearOrch.getModulator("CORTISOL").applyNetworkEffect(0.6);

        for (int i = 0; i < 30; i++) {
            linearOrch.tick(1.0);
            nonLinearOrch.tick(1.0);
        }

        double linearDopamine = linearOrch.getModulator("DOPAMINE").getCurrentLevel();
        double nonLinearDopamine = nonLinearOrch.getModulator("DOPAMINE").getCurrentLevel();
        assertTrue(nonLinearDopamine < linearDopamine);
    }

    @Test
    void testStressRecovery() {
        BiochemicalOrchestrator orchestrator = BiochemicalOrchestrator.createDefault();
        orchestrator.getModulator("CORTISOL").applyNetworkEffect(0.8);
        for (int i = 0; i < 30; i++) orchestrator.tick(1.0);
        double stressPeakDopamine = orchestrator.getModulator("DOPAMINE").getCurrentLevel();
        for (int i = 0; i < 100; i++) orchestrator.tick(1.0);
        double recoveredDopamine = orchestrator.getModulator("DOPAMINE").getCurrentLevel();
        assertTrue(recoveredDopamine > stressPeakDopamine);
    }

    @Test
    void testMoodTransitions() {
        BiochemicalOrchestrator orchestrator = BiochemicalOrchestrator.createDefault();
        String initialMood = orchestrator.getMood();
        assertNotNull(initialMood);
        orchestrator.getModulator("CORTISOL").applyNetworkEffect(0.8);
        for (int i = 0; i < 20; i++) orchestrator.tick(1.0);
        String stressedMood = orchestrator.getMood();
        assertNotNull(stressedMood);
    }
}
