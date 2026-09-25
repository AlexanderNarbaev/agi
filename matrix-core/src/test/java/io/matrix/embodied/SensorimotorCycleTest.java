package io.matrix.embodied;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SensorimotorCycleTest {

    @Test
    void testCreateCycle() {
        SensorimotorCycle cycle = new SensorimotorCycle(42L);
        assertNotNull(cycle);
        assertEquals(1.0, cycle.getTotalEnergy(), 0.001);
    }

    @Test
    void testRunCycle() {
        SensorimotorCycle cycle = new SensorimotorCycle(42L);
        SensorimotorCycle.CycleResult result = cycle.runCycle(
            "move_forward", java.util.Map.of("distance", 1.0));
        assertNotNull(result);
        assertNotNull(result.perception());
        assertNotNull(result.action());
        assertNotNull(result.feeling());
    }

    @Test
    void testEnergyDepletes() {
        SensorimotorCycle cycle = new SensorimotorCycle(42L);
        double initial = cycle.getTotalEnergy();
        for (int i = 0; i < 10; i++) {
            cycle.runCycle("test", java.util.Map.of());
        }
        assertTrue(cycle.getTotalEnergy() < initial, "Energy should decrease");
    }

    @Test
    void testHistoryGrows() {
        SensorimotorCycle cycle = new SensorimotorCycle(42L);
        for (int i = 0; i < 5; i++) {
            cycle.runCycle("test", java.util.Map.of());
        }
        assertEquals(5, cycle.getSensoryHistory().size());
        assertEquals(5, cycle.getMotorHistory().size());
        assertEquals(5, cycle.getFeelingHistory().size());
    }
}
