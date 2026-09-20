package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W573 — Tests for Corridor Homeostat.
 */
class CorridorHomeostatTest {

    @Test
    void testNormalState() {
        CorridorHomeostat homeostat = CorridorHomeostat.createDefault();
        assertEquals(CorridorHomeostat.CorridorState.NORMAL, homeostat.update("cpu_load", 0.5));
        assertFalse(homeostat.hasViolations());
    }

    @Test
    void testWarningState() {
        CorridorHomeostat homeostat = CorridorHomeostat.createDefault();
        assertEquals(CorridorHomeostat.CorridorState.WARNING, homeostat.update("cpu_load", 0.85));
        assertTrue(homeostat.hasWarnings());
    }

    @Test
    void testViolatedState() {
        CorridorHomeostat homeostat = CorridorHomeostat.createDefault();
        assertEquals(CorridorHomeostat.CorridorState.VIOLATED, homeostat.update("cpu_load", 1.5));
        assertTrue(homeostat.hasViolations());
        assertEquals(1, homeostat.getViolations().size());
    }

    @Test
    void testThrottleFactor() {
        CorridorHomeostat homeostat = CorridorHomeostat.createDefault();

        // At center → throttle ≈ 1.0
        homeostat.update("cpu_load", 0.5);
        double throttle1 = homeostat.getThrottleFactor();
        assertTrue(throttle1 > 0.8);

        // At edge → throttle < 1.0
        homeostat.update("cpu_load", 0.9);
        double throttle2 = homeostat.getThrottleFactor();
        assertTrue(throttle2 < throttle1);
    }

    @Test
    void testMultipleCorridors() {
        CorridorHomeostat homeostat = CorridorHomeostat.createDefault();

        homeostat.update("cpu_load", 0.5);
        homeostat.update("memory_usage", 0.6);
        homeostat.update("error_rate", 0.01);

        assertEquals(4, homeostat.getAllCorridors().size());
        assertFalse(homeostat.hasViolations());
    }

    @Test
    void testVersionTracking() {
        CorridorHomeostat homeostat = CorridorHomeostat.createDefault();

        homeostat.update("cpu_load", 0.5);
        homeostat.update("cpu_load", 0.6);
        homeostat.update("cpu_load", 0.7);

        CorridorHomeostat.Corridor corridor = homeostat.getCorridor("cpu_load");
        assertEquals(3, corridor.getVersion());
    }

    @Test
    void testViolationHistory() {
        CorridorHomeostat homeostat = CorridorHomeostat.createDefault();

        homeostat.update("cpu_load", 1.5);
        homeostat.update("memory_usage", 2.0);

        assertEquals(2, homeostat.getViolations().size());
    }
}
