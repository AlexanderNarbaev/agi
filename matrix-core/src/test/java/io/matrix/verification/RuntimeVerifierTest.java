package io.matrix.verification;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

class RuntimeVerifierTest {

    @Test
    void verifyReturnsPassingForNonMatchingState() {
        var verifier = new RuntimeVerifier();
        var result = verifier.verify(Map.of("key", "value"));
        assertTrue(result.isPassing());
    }

    @Test
    void verifyReturnsPassingForEmptyState() {
        var verifier = new RuntimeVerifier();
        var result = verifier.verify(Map.of());
        assertTrue(result.isPassing());
    }

    @Test
    void verifyPropertyReturnsEmptyForEmptyState() {
        var verifier = new RuntimeVerifier();
        var result = verifier.verifyProperty("any_property", Map.of());
        assertTrue(result.isEmpty());
    }
}

class VerificationResultTest {

    @Test
    void passingResult() {
        var result = new VerificationResult(List.of());
        assertTrue(result.isPassing());
    }

    @Test
    void violationsResult() {
        var violation = new PropertyViolation("test", "desc", Map.of(), 0);
        var result = new VerificationResult(List.of(violation));
        assertFalse(result.isPassing());
        assertEquals(1, result.violations().size());
    }

    @Test
    void getCriticalViolationsFilters() {
        var frozen = new PropertyViolation("frozen_test", "desc", Map.of(), 0);
        var normal = new PropertyViolation("normal_test", "desc", Map.of(), 0);
        var result = new VerificationResult(List.of(frozen, normal));
        var critical = result.getCriticalViolations();
        assertEquals(1, critical.size());
    }

    @Test
    void getSummaryIsCorrect() {
        var violation = new PropertyViolation("test", "desc", Map.of(), 0);
        var result = new VerificationResult(List.of(violation));
        assertTrue(result.getSummary().contains("1 violations"));
    }
}

class PropertyViolationTest {

    @Test
    void formatting() {
        var violation = new PropertyViolation("test_prop", "test_desc", Map.of("key", "value"), 0);
        String report = violation.toReport();
        assertTrue(report.contains("test_prop"));
        assertTrue(report.contains("test_desc"));
    }

    @Test
    void criticalDetection() {
        assertTrue(new PropertyViolation("frozen_neurons", "", Map.of(), 0).isCritical());
        assertTrue(new PropertyViolation("safety_check", "", Map.of(), 0).isCritical());
        assertFalse(new PropertyViolation("normal_check", "", Map.of(), 0).isCritical());
    }
}
