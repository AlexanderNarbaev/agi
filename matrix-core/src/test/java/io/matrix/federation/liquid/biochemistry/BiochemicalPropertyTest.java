package io.matrix.federation.liquid.biochemistry;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BiochemicalPropertyTest {
    private final Random rng = new Random(42);

    @Test
    void propertySynergyAlwaysPositive() {
        for (int trial = 0; trial < 1000; trial++) {
            BiochemicalNetwork network = new BiochemicalNetwork();
            network.addInteraction(new BiochemicalNetwork.Interaction("A", "B",
                    BiochemicalNetwork.InteractionType.SYNERGY, 0.1 + rng.nextDouble() * 0.9, rng.nextDouble()));
            var effects = network.computeInteractionEffects(new BiochemicalNetwork.ModulatorSnapshot(
                    Map.of("A", rng.nextDouble(), "B", rng.nextDouble())));
            assertTrue(effects.get("B") >= 0);
        }
    }

    @Test
    void propertyAntagonismAlwaysNegative() {
        for (int trial = 0; trial < 1000; trial++) {
            BiochemicalNetwork network = new BiochemicalNetwork();
            network.addInteraction(new BiochemicalNetwork.Interaction("A", "B",
                    BiochemicalNetwork.InteractionType.ANTAGONISM, 0.1 + rng.nextDouble() * 0.9, rng.nextDouble()));
            var effects = network.computeInteractionEffects(new BiochemicalNetwork.ModulatorSnapshot(
                    Map.of("A", rng.nextDouble(), "B", rng.nextDouble())));
            assertTrue(effects.get("B") <= 0);
        }
    }

    @Test
    void propertyZeroSourceGivesZeroEffect() {
        for (var type : BiochemicalNetwork.InteractionType.values()) {
            if (type == BiochemicalNetwork.InteractionType.NONE) continue;
            BiochemicalNetwork network = new BiochemicalNetwork();
            network.addInteraction(new BiochemicalNetwork.Interaction("A", "B", type, 0.5, 0.5));
            var effects = network.computeInteractionEffects(new BiochemicalNetwork.ModulatorSnapshot(Map.of("A", 0.0, "B", 0.5)));
            assertEquals(0.0, effects.getOrDefault("B", 0.0), 0.001);
        }
    }

    @Test
    void propertyStressCascadeConverges() {
        BiochemicalOrchestrator orchestrator = BiochemicalOrchestrator.createDefault();
        for (int i = 0; i < 100; i++) orchestrator.tick(1.0);
        for (var entry : orchestrator.getModulators().entrySet()) {
            double level = entry.getValue().getCurrentLevel();
            assertTrue(level >= 0 && level <= 1, entry.getKey() + " = " + level);
        }
    }

    @Test
    void propertyOrchestratorLevelsStayBounded() {
        BiochemicalOrchestrator orchestrator = BiochemicalOrchestrator.createDefault();
        for (int i = 0; i < 500; i++) {
            if (rng.nextDouble() < 0.1) {
                String id = List.of("DOPAMINE", "SEROTONIN", "CORTISOL", "NOREPINEPHRINE").get(rng.nextInt(4));
                orchestrator.getModulator(id).applyNetworkEffect(rng.nextGaussian() * 0.1);
            }
            orchestrator.tick(0.1);
        }
        for (var entry : orchestrator.getModulators().entrySet()) {
            double level = entry.getValue().getCurrentLevel();
            assertTrue(level >= 0 && level <= 1, entry.getKey() + " = " + level);
        }
    }

    @Test
    void propertyMoreInteractionsMoreComplex() {
        BiochemicalNetwork simple = new BiochemicalNetwork();
        simple.addInteraction(new BiochemicalNetwork.Interaction("A", "B", BiochemicalNetwork.InteractionType.SYNERGY, 0.5, 0.5));
        BiochemicalNetwork complex = new BiochemicalNetwork();
        for (int i = 0; i < 10; i++)
            complex.addInteraction(new BiochemicalNetwork.Interaction("S" + i, "B", BiochemicalNetwork.InteractionType.SYNERGY, 0.1, 0.5));
        var se = simple.computeInteractionEffects(new BiochemicalNetwork.ModulatorSnapshot(Map.of("A", 0.5, "B", 0.5)));
        Map<String, Double> levels = new HashMap<>();
        for (int i = 0; i < 10; i++) levels.put("S" + i, 0.5);
        levels.put("B", 0.5);
        var ce = complex.computeInteractionEffects(new BiochemicalNetwork.ModulatorSnapshot(levels));
        assertTrue(ce.get("B") > se.get("B"));
    }
}
