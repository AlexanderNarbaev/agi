package io.matrix.federation.liquid.biochemistry;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BiochemicalNetworkTest {

    @Test
    void testSynergyAmplifies() {
        BiochemicalNetwork network = new BiochemicalNetwork();
        network.addInteraction(new BiochemicalNetwork.Interaction(
                "A", "B", BiochemicalNetwork.InteractionType.SYNERGY, 0.5, 0.5));
        var snapshot = new BiochemicalNetwork.ModulatorSnapshot(Map.of("A", 0.5, "B", 0.5));
        var effects = network.computeInteractionEffects(snapshot);
        assertTrue(effects.containsKey("B"));
        assertTrue(effects.get("B") > 0, "Synergy should produce positive effect");
    }

    @Test
    void testAntagonismSuppresses() {
        BiochemicalNetwork network = new BiochemicalNetwork();
        network.addInteraction(new BiochemicalNetwork.Interaction(
                "A", "B", BiochemicalNetwork.InteractionType.ANTAGONISM, 0.7, 0.8));
        var snapshot = new BiochemicalNetwork.ModulatorSnapshot(Map.of("A", 0.8, "B", 0.5));
        var effects = network.computeInteractionEffects(snapshot);
        assertTrue(effects.get("B") < 0, "Antagonism should produce negative effect");
    }

    @Test
    void testCatalysisIsMultiplicative() {
        BiochemicalNetwork network = new BiochemicalNetwork();
        network.addInteraction(new BiochemicalNetwork.Interaction(
                "A", "B", BiochemicalNetwork.InteractionType.CATALYSIS, 0.5, 0.0));
        var snapshot = new BiochemicalNetwork.ModulatorSnapshot(Map.of("A", 0.6, "B", 0.4));
        var effects = network.computeInteractionEffects(snapshot);
        assertEquals(0.12, effects.get("B"), 0.01);
    }

    @Test
    void testStressCascade() {
        BiochemicalNetwork network = BiochemicalNetwork.createStressCascade();
        var snapshot = new BiochemicalNetwork.ModulatorSnapshot(
                Map.of("CORTISOL", 0.9, "DOPAMINE", 0.5, "SEROTONIN", 0.5, "NOREPINEPHRINE", 0.3));
        var effects = network.computeInteractionEffects(snapshot);
        assertTrue(effects.get("DOPAMINE") < 0, "Cortisol should suppress Dopamine");
    }

    @Test
    void testNonLinearSynergy() {
        BiochemicalNetwork network = new BiochemicalNetwork();
        network.addInteraction(new BiochemicalNetwork.Interaction(
                "A", "B", BiochemicalNetwork.InteractionType.SYNERGY, 0.5, 0.9));
        var low = new BiochemicalNetwork.ModulatorSnapshot(Map.of("A", 0.1, "B", 0.1));
        var lowEffects = network.computeInteractionEffects(low);
        var high = new BiochemicalNetwork.ModulatorSnapshot(Map.of("A", 0.9, "B", 0.9));
        var highEffects = network.computeInteractionEffects(high);
        assertTrue(highEffects.get("B") > lowEffects.get("B") * 9, "Non-linear synergy");
    }

    @Test
    void testAntagonismSaturation() {
        BiochemicalNetwork network = new BiochemicalNetwork();
        network.addInteraction(new BiochemicalNetwork.Interaction(
                "A", "B", BiochemicalNetwork.InteractionType.ANTAGONISM, 0.8, 0.9));
        var low = new BiochemicalNetwork.ModulatorSnapshot(Map.of("A", 0.5, "B", 0.1));
        var lowEffects = network.computeInteractionEffects(low);
        var high = new BiochemicalNetwork.ModulatorSnapshot(Map.of("A", 0.5, "B", 0.9));
        var highEffects = network.computeInteractionEffects(high);
        assertTrue(Math.abs(highEffects.get("B")) < Math.abs(lowEffects.get("B")), "Antagonism saturation");
    }

    @Test
    void testMultipleInteractionsCombine() {
        BiochemicalNetwork network = new BiochemicalNetwork();
        network.addInteraction(new BiochemicalNetwork.Interaction("A", "B", BiochemicalNetwork.InteractionType.ANTAGONISM, 0.5, 0.5));
        network.addInteraction(new BiochemicalNetwork.Interaction("C", "B", BiochemicalNetwork.InteractionType.SYNERGY, 0.3, 0.3));
        var snapshot = new BiochemicalNetwork.ModulatorSnapshot(Map.of("A", 0.5, "B", 0.5, "C", 0.5));
        var effects = network.computeInteractionEffects(snapshot);
        assertTrue(effects.containsKey("B"));
    }

    @Test
    void testStressCascadeDopamineDrop() {
        BiochemicalNetwork network = BiochemicalNetwork.createStressCascade();
        double dopamine = 0.7, cortisol = 0.1, serotonin = 0.6, norepinephrine = 0.2;
        for (int i = 0; i < 10; i++) {
            var snapshot = new BiochemicalNetwork.ModulatorSnapshot(
                    Map.of("CORTISOL", cortisol, "DOPAMINE", dopamine, "SEROTONIN", serotonin, "NOREPINEPHRINE", norepinephrine));
            var effects = network.computeInteractionEffects(snapshot);
            dopamine = Math.max(0, Math.min(1, dopamine + effects.getOrDefault("DOPAMINE", 0.0) * 0.1));
            cortisol = Math.min(1.0, cortisol + 0.1);
        }
        assertTrue(dopamine < 0.7, "Stress cascade should reduce dopamine");
    }

    @Test
    void testExportImport() {
        BiochemicalNetwork network = BiochemicalNetwork.createStressCascade();
        var exported = network.exportInteractions();
        BiochemicalNetwork imported = new BiochemicalNetwork();
        imported.importInteractions(exported);
        assertEquals(network.getInteractionCount(), imported.getInteractionCount());
    }

    @Test
    void testRemoveInteraction() {
        BiochemicalNetwork network = new BiochemicalNetwork();
        network.addInteraction(new BiochemicalNetwork.Interaction("A", "B", BiochemicalNetwork.InteractionType.SYNERGY, 0.5, 0.5));
        assertTrue(network.hasInteraction("A", "B"));
        network.removeInteraction("A", "B");
        assertFalse(network.hasInteraction("A", "B"));
    }
}
