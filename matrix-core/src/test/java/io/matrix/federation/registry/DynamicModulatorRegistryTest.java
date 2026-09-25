package io.matrix.federation.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W568 — Tests for DynamicModulatorRegistry.
 */
class DynamicModulatorRegistryTest {

    @Test
    void testDefaultModulators() {
        DynamicModulatorRegistry registry = new DynamicModulatorRegistry();

        // Should have 7 default modulators (3 dynamic + 4 FROZEN)
        assertEquals(7, registry.size());

        // Check FROZEN modulators exist
        assertNotNull(registry.getModulator("ETHICAL_FILTER"));
        assertNotNull(registry.getModulator("SAFETY_MONITOR"));
        assertNotNull(registry.getModulator("LIE_DETECTOR"));
        assertNotNull(registry.getModulator("CONSISTENCY_CHECKER"));

        // Check dynamic modulators exist
        assertNotNull(registry.getModulator("DOPAMINE"));
        assertNotNull(registry.getModulator("SEROTONIN"));
        assertNotNull(registry.getModulator("NOREPINEPHRINE"));
    }

    @Test
    void testGetSetValue() {
        DynamicModulatorRegistry registry = new DynamicModulatorRegistry();

        // Default value
        assertEquals(0.5, registry.getValue("DOPAMINE"), 0.001);

        // Set value
        assertTrue(registry.setValue("DOPAMINE", 0.8));
        assertEquals(0.8, registry.getValue("DOPAMINE"), 0.001);

        // Set value clamped to range
        assertTrue(registry.setValue("DOPAMINE", 2.0));
        assertEquals(1.0, registry.getValue("DOPAMINE"), 0.001);

        assertTrue(registry.setValue("DOPAMINE", -1.0));
        assertEquals(0.0, registry.getValue("DOPAMINE"), 0.001);
    }

    @Test
    void testAddModulator() {
        DynamicModulatorRegistry registry = new DynamicModulatorRegistry();
        int initialSize = registry.size();

        // Add new modulator
        DynamicModulatorRegistry.Modulator m = new DynamicModulatorRegistry.Modulator(
                "CUSTOM", "Custom modulator", 0.0, 1.0, 0.5, false);
        assertTrue(registry.addModulator(m));
        assertEquals(initialSize + 1, registry.size());
        assertEquals(0.5, registry.getValue("CUSTOM"), 0.001);
    }

    @Test
    void testRemoveModulator() {
        DynamicModulatorRegistry registry = new DynamicModulatorRegistry();

        // Add then remove
        registry.addModulator(new DynamicModulatorRegistry.Modulator(
                "TEMP", "Temporary", 0.0, 1.0, 0.5, false));
        assertTrue(registry.removeModulator("TEMP"));
        assertNull(registry.getModulator("TEMP"));
    }

    @Test
    void testCannotRemoveFrozen() {
        DynamicModulatorRegistry registry = new DynamicModulatorRegistry();

        // Cannot remove FROZEN modulators
        assertThrows(IllegalArgumentException.class, () ->
                registry.removeModulator("ETHICAL_FILTER"));
        assertThrows(IllegalArgumentException.class, () ->
                registry.removeModulator("SAFETY_MONITOR"));
        assertThrows(IllegalArgumentException.class, () ->
                registry.removeModulator("LIE_DETECTOR"));
        assertThrows(IllegalArgumentException.class, () ->
                registry.removeModulator("CONSISTENCY_CHECKER"));
    }

    @Test
    void testCannotMakeFrozenNonFrozen() {
        DynamicModulatorRegistry registry = new DynamicModulatorRegistry();

        // Cannot make a FROZEN modulator non-frozen
        assertThrows(IllegalArgumentException.class, () ->
                registry.addModulator(new DynamicModulatorRegistry.Modulator(
                        "ETHICAL_FILTER", "Ethical Filter", 0.0, 1.0, 1.0, false)));
    }

    @Test
    void testConstitutionCompliance() {
        DynamicModulatorRegistry registry = new DynamicModulatorRegistry();
        assertTrue(registry.isConstitutionCompliant());
    }

    @Test
    void testVersionIncrements() {
        DynamicModulatorRegistry registry = new DynamicModulatorRegistry();
        long v0 = registry.getVersion();

        registry.addModulator(new DynamicModulatorRegistry.Modulator(
                "V1", "Version test 1", 0.0, 1.0, 0.5, false));
        assertTrue(registry.getVersion() > v0);

        long v1 = registry.getVersion();
        registry.setValue("DOPAMINE", 0.9);
        // setValue doesn't increment version (only add/remove do)
        assertEquals(v1, registry.getVersion());
    }

    @Test
    void testSnapshot() {
        DynamicModulatorRegistry registry = new DynamicModulatorRegistry();

        var snap = registry.snapshot();
        assertEquals(7, snap.size());
        assertTrue(snap.containsKey("DOPAMINE"));
        assertTrue(snap.containsKey("ETHICAL_FILTER"));
    }

    @Test
    void testGetIds() {
        DynamicModulatorRegistry registry = new DynamicModulatorRegistry();

        var ids = registry.getIds();
        assertEquals(7, ids.size());
        assertTrue(ids.contains("DOPAMINE"));
        assertTrue(ids.contains("ETHICAL_FILTER"));
    }

    @Test
    void testFrozenModulatorsHaveCorrectValues() {
        DynamicModulatorRegistry registry = new DynamicModulatorRegistry();

        // All FROZEN modulators should default to 1.0 (fully active)
        for (String frozenId : DynamicModulatorRegistry.FROZEN_IDS) {
            DynamicModulatorRegistry.Modulator m = registry.getModulator(frozenId);
            assertNotNull(m, "FROZEN modulator missing: " + frozenId);
            assertTrue(m.isFrozen(), "Should be frozen: " + frozenId);
            assertEquals(1.0, m.currentValue(), 0.001, "FROZEN should default to 1.0: " + frozenId);
        }
    }

    @Test
    void testToString() {
        DynamicModulatorRegistry registry = new DynamicModulatorRegistry();
        String s = registry.toString();
        assertTrue(s.contains("version="));
        assertTrue(s.contains("size=7"));
        assertTrue(s.contains("compliant=true"));
    }
}
