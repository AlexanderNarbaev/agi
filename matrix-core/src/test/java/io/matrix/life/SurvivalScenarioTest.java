package io.matrix.life;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SurvivalScenarioTest {

    @Test
    void testCreateScenario() {
        SurvivalScenario scenario = new SurvivalScenario(42L);
        assertNotNull(scenario);
        assertEquals(0, scenario.getCurrentState().day());
        assertEquals(100.0, scenario.getCurrentState().health());
    }

    @Test
    void testSimulateDay() {
        SurvivalScenario scenario = new SurvivalScenario(42L);
        SurvivalScenario.DayResult result = scenario.simulateDay();
        assertEquals(1, result.day());
        assertNotNull(result.activities());
    }

    @Test
    void testRun1000Days() {
        SurvivalScenario scenario = new SurvivalScenario(42L);
        var results = scenario.runDays(1000);
        assertTrue(results.size() >= 1, "Should survive at least some days");
        assertTrue(results.get(0).day() == 1);
    }

    @Test
    void testBuildShelterEarly() {
        SurvivalScenario scenario = new SurvivalScenario(42L);
        for (int i = 0; i < 3; i++) scenario.simulateDay();
        assertTrue(scenario.getCurrentState().hasShelter(),
            "Should build shelter within first 3 days");
    }

    @Test
    void testActivityLogging() {
        SurvivalScenario scenario = new SurvivalScenario(42L);
        for (int i = 0; i < 5; i++) scenario.simulateDay();
        assertFalse(scenario.getCurrentState().activityLog().isEmpty());
    }

    @Test
    void testSurvivedCheck() {
        SurvivalScenario scenario = new SurvivalScenario(42L);
        assertTrue(scenario.survived(3), "Should survive 3 days with starter items");
    }
}
