package io.matrix.observability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealthCheckTest {

    @Test
    void testAllUp() {
        HealthCheck check = new HealthCheck();
        check.registerSupplier("a", () -> true);
        check.registerSupplier("b", () -> true);
        HealthCheck.Report report = check.runAll();
        assertEquals(HealthCheck.Status.UP, report.overall());
        assertEquals(2, report.checks().size());
    }

    @Test
    void testOneDown() {
        HealthCheck check = new HealthCheck();
        check.registerSupplier("a", () -> true);
        check.registerSupplier("b", () -> false);
        HealthCheck.Report report = check.runAll();
        assertEquals(HealthCheck.Status.DOWN, report.overall());
    }

    @Test
    void testAllDown() {
        HealthCheck check = new HealthCheck();
        check.registerSupplier("a", () -> false);
        check.registerSupplier("b", () -> false);
        HealthCheck.Report report = check.runAll();
        assertEquals(HealthCheck.Status.DOWN, report.overall());
    }

    @Test
    void testExceptionCountsAsDown() {
        HealthCheck check = new HealthCheck();
        check.registerSupplier("explodes", () -> { throw new RuntimeException("boom"); });
        HealthCheck.Report report = check.runAll();
        assertEquals(HealthCheck.Status.DOWN, report.overall());
    }

    @Test
    void testEmptyHealthIsUp() {
        HealthCheck check = new HealthCheck();
        assertEquals(HealthCheck.Status.UP, check.runAll().overall());
    }

    @Test
    void testReportIncludesTimestamp() {
        HealthCheck check = new HealthCheck();
        HealthCheck.Report report = check.runAll();
        assertNotNull(report.timestamp());
    }
}
