package io.matrix.federation.liquid.simulation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W609 — Sleep Consolidation Study Tests.
 */
class SleepConsolidationStudyTest {

    @Test
    void testWithoutSleep() {
        var result = SleepConsolidationStudy.runExperiment(false);
        assertNotNull(result);
        assertEquals("No Sleep", result.condition());
        assertTrue(result.accuracyBeforeSleep() > 0);
        assertTrue(result.accuracyAfterSleep() > 0);
        assertTrue(result.memoryRetention() > 0);
    }

    @Test
    void testWithSleep() {
        var result = SleepConsolidationStudy.runExperiment(true);
        assertNotNull(result);
        assertEquals("With Sleep", result.condition());
        assertTrue(result.accuracyBeforeSleep() > 0);
        assertTrue(result.accuracyAfterSleep() > 0);
        assertTrue(result.memoryRetention() > 0);
    }

    @Test
    void testSleepImprovesRetention() {
        var noSleep = SleepConsolidationStudy.runExperiment(false);
        var withSleep = SleepConsolidationStudy.runExperiment(true);

        // Sleep should preserve more memory
        assertTrue(withSleep.memoryRetention() >= noSleep.memoryRetention() * 0.8);
    }

    @Test
    void testGenerateReport() {
        var result = SleepConsolidationStudy.runExperiment(true);
        String report = SleepConsolidationStudy.generateReport(java.util.List.of(result));
        assertNotNull(report);
        assertTrue(report.contains("Sleep Consolidation Study Report"));
    }
}
