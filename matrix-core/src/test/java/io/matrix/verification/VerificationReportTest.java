package io.matrix.verification;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class VerificationReportTest {

    @Test
    void totalViolationsIncrements() {
        var report = new VerificationReport();
        assertEquals(0, report.getTotalViolations());
        assertEquals(0, report.getCriticalViolations());

        report.reportViolation(new PropertyViolation("test", "desc", java.util.Map.of(), 0));
        assertEquals(1, report.getTotalViolations());
        assertEquals(0, report.getCriticalViolations());

        report.reportViolation(new PropertyViolation("frozen_test", "desc", java.util.Map.of(), 0));
        assertEquals(2, report.getTotalViolations());
        assertEquals(1, report.getCriticalViolations());
    }

    @Test
    void resetClearsCounters() {
        var report = new VerificationReport();
        report.reportViolation(new PropertyViolation("test", "desc", java.util.Map.of(), 0));
        report.reset();
        assertEquals(0, report.getTotalViolations());
        assertEquals(0, report.getCriticalViolations());
    }

    @Test
    void passingResultReportsZero() {
        var report = new VerificationReport();
        var result = new VerificationResult(java.util.List.of());
        report.reportResult(result);
        assertEquals(0, report.getTotalViolations());
    }

    @Test
    void failingResultReportsViolations() {
        var report = new VerificationReport();
        var violation = new PropertyViolation("test", "desc", java.util.Map.of(), 0);
        var result = new VerificationResult(java.util.List.of(violation));
        report.reportResult(result);
        assertEquals(1, report.getTotalViolations());
    }
}
