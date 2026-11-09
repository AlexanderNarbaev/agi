package io.matrix.benchmark.w51;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W51 §10 — the harness itself, tested.
 *
 * <p>A measurement is only worth reading if the thing that produced it is right, and the
 * thing that produced it is a piece of code like any other. These tests check the three
 * properties the numbers depend on: that the warm-up is not counted as a sample, that the
 * percentile is the one the documentation names, and that the allocation counter counts
 * what was actually allocated.</p>
 */
class MicroBenchTest {

    /** The number of warm-up rounds the tests use. */
    private static final int WARMUP = 2;

    /** The number of measured rounds the tests use. */
    private static final int ROUNDS = 20;

    /** The size of the buffer the allocation test allocates per call. */
    private static final int ALLOCATION_BYTES = 4096;

    @Test
    @DisplayName("the measurement reports exactly the measured rounds, and not the warm-up")
    void theWarmUpIsNotMeasured() {
        BenchResult result = MicroBench.measure("warm-up", WARMUP, ROUNDS, () -> {
        });

        assertEquals(ROUNDS, result.samples());
    }

    @Test
    @DisplayName("the percentile is the nearest-rank one the documentation names")
    void thePercentileIsNearestRank() {
        long[] samples = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        assertEquals(5L, MicroBench.percentile(samples, MicroBench.MEDIAN_FRACTION));
        assertEquals(9L, MicroBench.percentile(samples, MicroBench.P90_FRACTION));
        assertEquals(10L, MicroBench.percentile(samples, MicroBench.P99_FRACTION));
        assertEquals(1L, MicroBench.percentile(samples, 0.1));
    }

    @Test
    @DisplayName("the percentile of a single sample is that sample")
    void thePercentileOfOneSample() {
        long[] samples = {42L};

        assertEquals(42L, MicroBench.percentile(samples, MicroBench.MEDIAN_FRACTION));
        assertEquals(42L, MicroBench.percentile(samples, MicroBench.P99_FRACTION));
    }

    @Test
    @DisplayName("a percentile outside the unit interval is refused")
    void aPercentileOutsideTheUnitIntervalIsRefused() {
        long[] samples = {1L, 2L};

        assertThrows(IllegalArgumentException.class, () -> MicroBench.percentile(samples, 0.0));
        assertThrows(IllegalArgumentException.class, () -> MicroBench.percentile(samples, 1.5));
        assertThrows(IllegalArgumentException.class,
                () -> MicroBench.percentile(new long[0], 0.5));
    }

    @Test
    @DisplayName("the allocation counter counts what the action allocated")
    void theAllocationCounterCountsWhatWasAllocated() {
        AllocationMeter meter = new AllocationMeter();
        org.junit.jupiter.api.Assumptions.assumeTrue(meter.supported(),
                "this JVM does not report the per-thread allocation");

        long before = meter.allocatedBytes();
        byte[] buffer = new byte[ALLOCATION_BYTES];
        long after = meter.allocatedBytes();

        assertTrue(after - before >= ALLOCATION_BYTES,
                "the counter must see at least the buffer that was allocated");
        assertTrue(buffer.length == ALLOCATION_BYTES);
    }

    @Test
    @DisplayName("the measured allocation per call is the allocation the action made")
    void theMeasuredAllocationPerCallIsTheAllocationsOfTheAction() {
        AllocationMeter meter = new AllocationMeter();
        org.junit.jupiter.api.Assumptions.assumeTrue(meter.supported(),
                "this JVM does not report the per-thread allocation");

        BenchResult result = MicroBench.measure("allocation", WARMUP, ROUNDS, () -> {
            byte[] buffer = new byte[ALLOCATION_BYTES];
            if (buffer.length == 0) {
                throw new IllegalStateException("the buffer is never empty");
            }
        });

        assertTrue(result.allocationMeasured());
        assertTrue(result.allocatedBytesPerCall() >= ALLOCATION_BYTES,
                "the per-call figure must include the buffer the action allocated, and it was "
                        + result.allocatedBytesPerCall());
    }

    @Test
    @DisplayName("a measurement without a name, without rounds or without an action is refused")
    void anImpossibleMeasurementIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> MicroBench.measure(" ", WARMUP, ROUNDS, () -> {
                }));
        assertThrows(IllegalArgumentException.class,
                () -> MicroBench.measure("name", WARMUP, 0, () -> {
                }));
        assertThrows(IllegalArgumentException.class,
                () -> MicroBench.measure("name", -1, ROUNDS, () -> {
                }));
        assertThrows(IllegalArgumentException.class,
                () -> MicroBench.measure("name", WARMUP, ROUNDS, null));
    }

    @Test
    @DisplayName("the numbers of a measurement are consistent with each other")
    void theNumbersAreConsistent() {
        BenchResult result = MicroBench.measure("consistency", WARMUP, ROUNDS, () -> {
        });

        assertTrue(result.minNanos() <= result.p50Nanos());
        assertTrue(result.p50Nanos() <= result.p90Nanos());
        assertTrue(result.p90Nanos() <= result.p99Nanos());
        assertTrue(result.p99Nanos() <= result.maxNanos());
        assertFalse(result.name().isBlank());
    }
}
