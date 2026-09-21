package io.matrix.pilots.smarthome;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmartHomeAgentTest {

    private final SmartHomeAgent agent = new SmartHomeAgent();

    @Test
    void testAnalyze() {
        var state = new SmartHomeAgent.HomeState(22.0, 5.0, 3.5, 2, true, false);
        var rec = agent.analyze(state);
        assertNotNull(rec.title());
        assertTrue(rec.estimatedSavingsKwh() > 0);
        assertNotNull(rec.severity());
    }

    @Test
    void testAnomalyDetectionNormal() {
        double[] history = {3.0, 3.1, 2.9, 3.0, 3.1};
        assertEquals(SmartHomeAgent.AnomalyLevel.NORMAL,
            agent.detectAnomaly(3.0, history));
    }

    @Test
    void testAnomalyDetectionCritical() {
        double[] history = {3.0, 3.1, 2.9, 3.0, 3.1};
        // Way out of distribution
        assertEquals(SmartHomeAgent.AnomalyLevel.CRITICAL,
            agent.detectAnomaly(15.0, history));
    }

    @Test
    void testAnomalyDetectionEmptyHistory() {
        assertEquals(SmartHomeAgent.AnomalyLevel.NORMAL,
            agent.detectAnomaly(5.0, new double[0]));
    }

    @Test
    void testAnomalyDetectionNullHistory() {
        assertEquals(SmartHomeAgent.AnomalyLevel.NORMAL,
            agent.detectAnomaly(5.0, null));
    }
}
