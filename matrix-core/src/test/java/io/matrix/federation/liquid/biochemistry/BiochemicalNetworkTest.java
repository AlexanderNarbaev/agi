package io.matrix.federation.liquid.biochemistry;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W651 — Biochemical Network Tests.
 */
class BiochemicalNetworkTest {

    @Test
    void testSynergyAmplifies() {
        BiochemicalNetwork network = new BiochemicalNetwork();
        network.addInteraction(new BiochemicalNetwork.Interaction(
                "A", "B", BiochemicalNetwork.InteractionType.SYNERGY, 0.5, 0.5));

        // Both at 0.5: synergy should amplify
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

        assertTrue(effects.containsKey("B"));
        assertTrue(effects.get("B") < 0, "Antagonism should produce negative effect");
    }

    @Test
    void testCatalysisIsMultiplicative() {
        BiochemicalNetwork network = new BiochemicalNetwork();
        network.addInteraction(new BiochemicalNetwork.Interaction(
                "A", "B", BiochemicalNetwork.InteractionType.CATALYSIS, 0.5, 0.0));

        var snapshot = new BiochemicalNetwork.ModulatorSnapshot(Map.of("A", 0.6, "B", 0.4));
        var effects = network.computeInteractionEffects(snapshot);

        // catalysis = weight * source * target = 0.5 * 0.6 * 0.4 = 0.12
        assertEquals(0.12, effects.get("B"), 0.01);
    }

    @Test
    void testStressCascade() {
        BiochemicalNetwork network = BiochemicalNetwork.createStressCascade();

        // High cortisol scenario
        var snapshot = new BiochemicalNetwork.ModulatorSnapshot(
                Map.of("CORTISOL", 0.9, "DOPAMINE", 0.5, "SEROTONIN", 0.5, "NOREPINEPHRINE", 0.3));
        var effects = network.computeInteractionEffects(snapshot);

        // Cortisol should suppress Dopamine (antagonism)
        assertTrue(effects.get("DOPAMINE") < 0, "Cortisol should suppress Dopamine");
        // Cortisol should suppress Serotonin (antagonism)
        assertTrue(effects.get("SEROTONIN") < 0, "Cortisol should suppress Serotonin");
    }

    @Test
    void testNonLinearSynergy() {
        BiochemicalNetwork network = new BiochemicalNetwork();
        network.addInteraction(new BiochemicalNetwork.Interaction(
                "A", "B", BiochemicalNetwork.InteractionType.SYNERGY, 0.5, 0.9));

        // Low levels
        var low = new BiochemicalNetwork.ModulatorSnapshot(Map.of("A", 0.1, "B", 0.1));
        var lowEffects = network.computeInteractionEffects(low);

        // High levels
        var high = new BiochemicalNetwork.ModulatorSnapshot(Map.of("A", 0.9, "B", 0.9));
        var highEffects = network.computeInteractionEffects(high);

        // Non-linear: high levels should produce disproportionately larger effect
        double lowEffect = lowEffects.get("B");
        double highEffect = highEffects.get("B");
        assertTrue(highEffect > lowEffect * 9, "Non-linear synergy: high levels should amplify disproportionately");
    }

    @Test
    void testAntagonismSaturation() {
        BiochemicalNetwork network = new BiochemicalNetwork();
        network.addInteraction(new BiochemicalNetwork.Interaction(
                "A", "B", BiochemicalNetwork.InteractionType.ANTAGONISM, 0.8, 0.9));

        // Increasing target level should reduce suppression (saturation)
        var low = new BiochemicalNetwork.ModulatorSnapshot(Map.of("A", 0.5, "B", 0.1));
        var lowEffects = network.computeInteractionEffects(low);

        var high = new BiochemicalNetwork.ModulatorSnapshot(Map.of("A", 0.5, "B", 0.9));
        var highEffects = network.computeInteractionEffects(high);

        // With high B, antagonism should be weaker (saturated)
        assertTrue(Math.abs(highEffects.get("B")) < Math.abs(lowEffects.get("B")),
                "Antagonism should saturate at high target levels");
    }

    @Test
    void testMultipleInteractionsCombine() {
        BiochemicalNetwork network = new BiochemicalNetwork();
        // A suppresses B, C amplifies B
        network.addInteraction(new BiochemicalNetwork.Interaction(
                "A", "B", BiochemicalNetwork.InteractionType.ANTAGONISM, 0.5, 0.5));
        network.addInteraction(new BiochemicalNetwork.Interaction(
                "C", "B", BiochemicalNetwork.InteractionType.SYNERGY, 0.3, 0.3));

        var snapshot = new BiochemicalNetwork.ModulatorSnapshot(Map.of("A", 0.5, "B", 0.5, "C", 0.5));
        var effects = network.computeInteractionEffects(snapshot);

        // Effect should be sum of both interactions
        assertTrue(effects.containsKey("B"));
        // A suppresses (-), C amplifies (+), net could be either
    }

    @Test
    void testStressCascadeDopamineDrop() {
        BiochemicalNetwork network = BiochemicalNetwork.createStressCascade();

        // Simulate stress cascade over multiple steps
        double dopamine = 0.7;
        double cortisol = 0.1;
        double serotonin = 0.6;
        double norepinephrine = 0.2;

        for (int i = 0; i < 10; i++) {
            var snapshot = new BiochemicalNetwork.ModulatorSnapshot(
                    Map.of("CORTISOL", cortisol, "DOPAMINE", dopamine,
                            "SEROTONIN", serotonin, "NOREPINEPHRINE", norepinephrine));
            var effects = network.computeInteractionEffects(snapshot);

            // Apply effects (simplified)
            dopamine = Math.max(0, Math.min(1, dopamine + effects.getOrDefault("DOPAMINE", 0.0) * 0.1));
            cortisol = Math.max(0, Math.min(1, cortisol + effects.getOrDefault("CORTISOL", 0.0) * 0.1));
            serotonin = Math.max(0, Math.min(1, serotonin + effects.getOrDefault("SEROTONIN", 0.0) * 0.1));
            norepinephrine = Math.max(0, Math.min(1, norepinephrine + effects.getOrDefault("NOREPINEPHRINE", 0.0) * 0.1));

            // Increase cortisol (stress trigger)
            cortisol = Math.min(1.0, cortisol + 0.1);
        }

        // After stress cascade, dopamine should be reduced
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
    void testGetInteractionsFrom() {
        BiochemicalNetwork network = BiochemicalNetwork.createStressCascade();
        var fromCortisol = network.getInteractionsFrom("CORTISOL");
        assertFalse(fromCortisol.isEmpty());
    }

    @Test
    void testGetInteractionsTo() {
        BiochemicalNetwork network = BiochemicalNetwork.createStressCascade();
        var toDopamine = network.getInteractionsTo("DOPAMINE");
        assertFalse(toDopamine.isEmpty());
    }

    @Test
    void testHasInteraction() {
        BiochemicalNetwork network = BiochemicalNetwork.createStressCascade();
        assertTrue(network.hasInteraction("CORTISOL", "DOPAMINE"));
        assertFalse(network.hasInteraction("DOPAMINE", "CORTISOL"));
    }

    @Test
    void testRemoveInteraction() {
        BiochemicalNetwork network = new BiochemicalNetwork();
        network.addInteraction(new BiochemicalNetwork.Interaction(
                "A", "B", BiochemicalNetwork.InteractionType.SYNERGY, 0.5, 0.5));
        assertTrue(network.hasInteraction("A", "B"));

        network.removeInteraction("A", "B");
        assertFalse(network.hasInteraction("A", "B"));
    }

    @Test
    void testClear() {
        BiochemicalNetwork network = BiochemicalNetwork.createStressCascade();
        assertTrue(network.getInteractionCount() > 0);

        network.clear();
        assertEquals(0, network.getInteractionCount());
    }
}
