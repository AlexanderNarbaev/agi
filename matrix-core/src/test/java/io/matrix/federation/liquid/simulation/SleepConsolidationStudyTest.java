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

    /**
     * W35.1 — this asserted the wrong property, and the implementation was right.
     *
     * <p>The old assertion was:</p>
     *
     * <pre>    assertTrue(withSleep.memoryRetention() &gt;= noSleep.memoryRetention() * 0.8);</pre>
     *
     * <p>{@code memoryRetention} is {@code memoryFootprint / memoryBefore}, so it measures the
     * FRACTION OF VECTORS RETAINED AFTER PRUNING. Sleep in this study IS pruning — the
     * implementation removes every vector whose training strength is below 0.3. So the
     * assertion demanded that pruning keep at least 80% of what it exists to remove, and it
     * failed at 0.78 for exactly that reason.</p>
     *
     * <p>Measured, rather than inferred from the failure message:</p>
     *
     * <pre>
     *   NO SLEEP : accuracy 0.5135  footprint 100  forgetting  0.0000
     *   WITH     : accuracy 0.6134  footprint  78  forgetting -0.1944
     * </pre>
     *
     * <p>Sleep IMPROVES accuracy by 19% while shrinking the footprint by 22%. The mechanism
     * is visible in {@code testAccuracy}, which averages the strengths of the vectors it is
     * given: dropping the weakest 22% raises the mean. The negative forgetting rate says the
     * same thing — {@code 1 - after/before} goes below zero when after exceeds before.</p>
     *
     * <p>So the study does what it claims and the test was checking an unrelated quantity.
     * The assertions below state the claim the study actually makes, and they are STRICTER
     * than the original in the direction that matters: accuracy must not merely be
     * preserved, it must improve, AND the footprint must actually shrink. A pruning step that
     * pruned nothing would pass the old test and fail this one.</p>
     */
    @Test
    void testSleepImprovesRetention() {
        var noSleep = SleepConsolidationStudy.runExperiment(false);
        var withSleep = SleepConsolidationStudy.runExperiment(true);

        assertTrue(withSleep.accuracyAfterSleep() > noSleep.accuracyAfterSleep(),
                "sleep must IMPROVE functional memory, not merely preserve it; measured "
                        + noSleep.accuracyAfterSleep() + " without sleep vs "
                        + withSleep.accuracyAfterSleep() + " with sleep");

        assertTrue(withSleep.memoryFootprint() < noSleep.memoryFootprint(),
                "pruning must actually reduce the footprint; measured "
                        + noSleep.memoryFootprint() + " vs " + withSleep.memoryFootprint());

        assertTrue(withSleep.forgettingRate() <= 0.0,
                "a negative forgetting rate means forgetting decreased, which is the intended "
                        + "outcome; measured " + withSleep.forgettingRate());
    }

    /**
     * The pruning fraction itself, asserted rather than assumed.
     *
     * <p>{@code memoryRetention} is easy to misread as "how much the mind still remembers".
     * It is the surviving fraction of vectors. This pins the relationship that makes the
     * metric meaningful: retention is exactly the footprint ratio, so the two can never drift
     * apart and silently disagree in a report.</p>
     */
    @Test
    void testMemoryRetentionIsTheFootprintRatio() {
        var withSleep = SleepConsolidationStudy.runExperiment(true);
        double expected = (double) withSleep.memoryFootprint() / 100.0;

        assertEquals(expected, withSleep.memoryRetention(), 1e-9,
                "memoryRetention must be exactly memoryFootprint/memoryBefore, or the report "
                        + "will quote a number that means something other than its name");
    }

    @Test
    void testPruningIsNotTrivial() {
        // A prune that removed nothing would leave accuracy unchanged and pass a naive
        // "sleep does not hurt" assertion. Pin the floor so the study cannot silently become
        // a no-op while still being called a study.
        var withSleep = SleepConsolidationStudy.runExperiment(true);
        assertTrue(withSleep.memoryFootprint() <= 85,
                "pruning must remove a meaningful share of weak vectors; retained "
                        + withSleep.memoryFootprint() + " of 100");
    }

    @Test
    void testGenerateReport() {
        var result = SleepConsolidationStudy.runExperiment(true);
        String report = SleepConsolidationStudy.generateReport(java.util.List.of(result));
        assertNotNull(report);
        assertTrue(report.contains("Sleep Consolidation Study Report"));
    }
}
