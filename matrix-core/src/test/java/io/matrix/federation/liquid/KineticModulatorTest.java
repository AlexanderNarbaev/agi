package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W576 — Tests for Kinetic Modulator.
 */
class KineticModulatorTest {

    @Test
    void testProductionDecay() {
        KineticModulator m = new KineticModulator(
                "TEST", "Test", 0.5, 0.1, 0.0, 1.0, 0.5, 1.0, 0.01, false);

        // Tick should increase level (production > decay at level 0.5)
        m.tick(1.0);
        assertTrue(m.getCurrentLevel() > 0.5);
    }

    @Test
    void testDecayDominates() {
        KineticModulator m = new KineticModulator(
                "TEST", "Test", 0.0, 0.5, 0.0, 1.0, 0.8, 1.0, 0.01, false);

        // With zero production, should decay
        m.tick(1.0);
        assertTrue(m.getCurrentLevel() < 0.8);
    }

    @Test
    void testLevelClamping() {
        KineticModulator m = new KineticModulator(
                "TEST", "Test", 10.0, 0.0, 0.0, 1.0, 0.5, 1.0, 0.01, false);

        // High production should clamp at max
        for (int i = 0; i < 100; i++) m.tick(1.0);
        assertTrue(m.getCurrentLevel() <= 1.0);
    }

    @Test
    void testReceptorSensitivity() {
        KineticModulator m = new KineticModulator(
                "TEST", "Test", 0.5, 0.1, 0.0, 1.0, 0.5, 1.0, 0.1, false);

        double initialSensitivity = m.getReceptorSensitivity();
        m.tick(1.0);

        // Sensitivity should change based on level
        assertNotEquals(initialSensitivity, m.getReceptorSensitivity(), 0.001);
    }

    @Test
    void testCrossTalk() {
        KineticModulator dopamine = new KineticModulator(
                "DOPAMINE", "Dopamine", 0.5, 0.1, 0.0, 1.0, 0.5, 1.0, 0.01, false);
        KineticModulator cortisol = new KineticModulator(
                "CORTISOL", "Cortisol", 0.3, 0.1, 0.0, 1.0, 0.5, 1.0, 0.01, false);

        // Cortisol inhibits dopamine
        dopamine.addCrossTalk("CORTISOL", -0.1);

        double before = dopamine.getCurrentLevel();
        dopamine.applyCrossTalk("CORTISOL", 0.8);
        assertTrue(dopamine.getCurrentLevel() < before);
    }

    @Test
    void testFrozenModulator() {
        KineticModulator m = new KineticModulator(
                "ETHICAL_FILTER", "Ethical Filter", 1.0, 0.0, 0.0, 1.0, 1.0, 1.0, 0.0, true);

        assertTrue(m.isFrozen());
        assertEquals(1.0, m.getCurrentLevel(), 0.001);
    }

    @Test
    void testMoodDerivation() {
        Map<String, KineticModulator> mods = new HashMap<>();

        // Happy mood: high dopamine + serotonin
        mods.put("DOPAMINE", new KineticModulator("DOPAMINE", "D", 0.8, 0.1, 0, 1, 0.8, 1, 0.01, false));
        mods.put("SEROTONIN", new KineticModulator("SEROTONIN", "S", 0.7, 0.1, 0, 1, 0.7, 1, 0.01, false));
        mods.put("CORTISOL", new KineticModulator("CORTISOL", "C", 0.2, 0.1, 0, 1, 0.2, 1, 0.01, false));
        mods.put("NOREPINEPHRINE", new KineticModulator("NOREPINEPHRINE", "N", 0.3, 0.1, 0, 1, 0.3, 1, 0.01, false));

        assertEquals("HAPPY", KineticModulator.deriveMood(mods));
    }

    @Test
    void testSleepNeed() {
        Map<String, KineticModulator> mods = new HashMap<>();
        mods.put("ADENOSINE", new KineticModulator("ADENOSINE", "A", 0.8, 0.1, 0, 1, 0.8, 1, 0.01, false));
        mods.put("CORTISOL", new KineticModulator("CORTISOL", "C", 0.3, 0.1, 0, 1, 0.3, 1, 0.01, false));

        double sleepNeed = KineticModulator.getSleepNeed(mods);
        assertTrue(sleepNeed > 0.5); // High adenosine → high sleep need
    }
}
