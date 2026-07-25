package io.matrix.verification;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.*;

class ContinuousVerifierTest {

    @Test
    void continuousVerifierStatsInitiallyZero() {
        var verifier = new RuntimeVerifier();
        var reporter = new VerificationReport();
        var continuous = new ContinuousVerifier();
        
        // Inject dependencies
        try {
            var verifierField = ContinuousVerifier.class.getDeclaredField("verifier");
            verifierField.setAccessible(true);
            verifierField.set(continuous, verifier);
            var reportField = ContinuousVerifier.class.getDeclaredField("report");
            reportField.setAccessible(true);
            reportField.set(continuous, reporter);
        } catch (Exception e) {
            fail("Failed to inject: " + e.getMessage());
        }
        
        var stats = continuous.getStats();
        assertEquals(0L, stats.get("totalChecks"));
        assertEquals(0L, stats.get("failedChecks"));
        assertEquals(1.0, (double) stats.get("successRate"), 0.001);
    }

    @Test
    void continuousVerifierPeriodicCheckPasses() {
        var verifier = new RuntimeVerifier();
        var reporter = new VerificationReport();
        var continuous = new ContinuousVerifier();
        
        try {
            var verifierField = ContinuousVerifier.class.getDeclaredField("verifier");
            verifierField.setAccessible(true);
            verifierField.set(continuous, verifier);
            var reportField = ContinuousVerifier.class.getDeclaredField("report");
            reportField.setAccessible(true);
            reportField.set(continuous, reporter);
        } catch (Exception e) {
            fail("Failed to inject: " + e.getMessage());
        }
        
        var result = continuous.periodicCheck();
        assertNotNull(result);
        assertTrue(result.isPassing());
    }

    @Test
    void propertyBasedVerifierRandomBoolean() {
        var verifier = new PropertyBasedVerifier();
        var rng = new java.util.Random(42);
        boolean[] result = verifier.generateRandomBoolean(50, rng);
        assertEquals(50, result.length);
    }

    @Test
    void propertyBasedVerifierMonotonicFitnessAcceptsEqual() {
        var verifier = new PropertyBasedVerifier();
        double[] fitness = {0.5, 0.5, 0.5, 0.5};
        assertTrue(verifier.verifyMonotonicFitness(fitness));
    }

    @Test
    void propertyBasedVerifierMonotonicFitnessRejectsDecrease() {
        var verifier = new PropertyBasedVerifier();
        double[] fitness = {0.5, 0.3, 0.6};
        assertFalse(verifier.verifyMonotonicFitness(fitness));
    }
}
