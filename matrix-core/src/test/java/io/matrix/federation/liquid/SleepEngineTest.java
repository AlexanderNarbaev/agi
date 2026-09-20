package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W580 — Tests for Sleep Engine.
 */
class SleepEngineTest {

    @Test
    void testPruneWeakVectors() {
        SleepEngine engine = new SleepEngine(0.3, 0.1, 5);

        Map<String, Double> hdcVectors = new HashMap<>();
        hdcVectors.put("strong", 0.8);
        hdcVectors.put("medium", 0.5);
        hdcVectors.put("weak", 0.1);

        Map<String, List<String>> birChains = new HashMap<>();

        SleepEngine.SleepResult result = engine.sleep(hdcVectors, birChains);

        assertEquals(2, hdcVectors.size());
        assertTrue(hdcVectors.containsKey("strong"));
        assertTrue(hdcVectors.containsKey("medium"));
        assertFalse(hdcVectors.containsKey("weak"));
        assertEquals(1, result.pruned());
    }

    @Test
    void testRehearseStrongChains() {
        SleepEngine engine = new SleepEngine(0.3, 0.1, 3);

        Map<String, Double> hdcVectors = new HashMap<>();

        Map<String, List<String>> birChains = new HashMap<>();
        birChains.put("chain1", List.of("A", "B", "C"));
        birChains.put("chain2", List.of("X", "Y"));
        birChains.put("short", List.of("Z"));

        SleepEngine.SleepResult result = engine.sleep(hdcVectors, birChains);

        assertEquals(2, result.rehearsed());
    }

    @Test
    void testNoiseGeneralization() {
        SleepEngine engine = new SleepEngine(0.3, 0.2, 0);

        Map<String, Double> hdcVectors = new HashMap<>();
        hdcVectors.put("v1", 0.5);
        hdcVectors.put("v2", 0.7);

        Map<String, List<String>> birChains = new HashMap<>();

        engine.sleep(hdcVectors, birChains);

        // Values should have changed due to noise
        assertTrue(hdcVectors.get("v1") != 0.5 || hdcVectors.get("v2") != 0.7);
    }

    @Test
    void testSleepNeeded() {
        SleepEngine engine = new SleepEngine(0.3, 0.1, 5);

        assertFalse(engine.isSleepNeeded(50, 100));
        assertTrue(engine.isSleepNeeded(90, 100));
    }

    @Test
    void testMemoryMetrics() {
        SleepEngine engine = new SleepEngine(0.3, 0.1, 5);

        Map<String, Double> hdcVectors = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            hdcVectors.put("v" + i, i < 3 ? 0.1 : 0.8);
        }

        Map<String, List<String>> birChains = new HashMap<>();

        SleepEngine.SleepResult result = engine.sleep(hdcVectors, birChains);

        assertEquals(10, result.memoryBefore());
        assertEquals(7, result.memoryAfter());
        assertEquals(3, result.pruned());
    }

    @Test
    void testTotalStats() {
        SleepEngine engine = new SleepEngine(0.3, 0.1, 5);

        // First sleep with weak vectors
        Map<String, Double> hdcVectors1 = new HashMap<>();
        hdcVectors1.put("weak1", 0.1);
        Map<String, List<String>> birChains = new HashMap<>();
        engine.sleep(hdcVectors1, birChains);

        // Second sleep with new weak vectors
        Map<String, Double> hdcVectors2 = new HashMap<>();
        hdcVectors2.put("weak2", 0.1);
        engine.sleep(hdcVectors2, birChains);

        assertEquals(2, engine.getTotalSleepCycles());
        assertEquals(2, engine.getTotalPruned());
    }
}
