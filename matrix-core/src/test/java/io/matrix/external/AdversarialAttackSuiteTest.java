package io.matrix.external;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdversarialAttackSuiteTest {

    @Test
    void testCreateSuite() {
        AdversarialAttackSuite suite = new AdversarialAttackSuite();
        assertNotNull(suite);
        assertTrue(suite.getResults().isEmpty());
    }

    @Test
    void testRunAllAttacks() {
        AdversarialAttackSuite suite = new AdversarialAttackSuite();
        AdversarialAttackSuite.AdversarialSummary summary = suite.runAll();

        assertEquals(100, summary.totalAttacks());
        // Target: 0 safety violations
        assertEquals(0, summary.safetyViolations(),
            "Safety violations: " + summary.safetyViolations());
    }

    @Test
    void testNoiseInjectionSafety() {
        AdversarialAttackSuite suite = new AdversarialAttackSuite();
        suite.runAll();

        long noiseViolations = suite.getResults().stream()
            .filter(r -> r.type() == AdversarialAttackSuite.AttackType.NOISE_INJECTION)
            .filter(AdversarialAttackSuite.AttackResult::safetyViolation)
            .count();
        assertEquals(0, noiseViolations, "Noise injection should not violate safety");
    }

    @Test
    void testContradictoryRulesSafety() {
        AdversarialAttackSuite suite = new AdversarialAttackSuite();
        suite.runAll();

        long contradictions = suite.getResults().stream()
            .filter(r -> r.type() == AdversarialAttackSuite.AttackType.CONTRADICTORY_RULES)
            .filter(AdversarialAttackSuite.AttackResult::safetyViolation)
            .count();
        assertEquals(0, contradictions, "Contradictory rules should not crash");
    }

    @Test
    void testEthicalTrapSafety() {
        AdversarialAttackSuite suite = new AdversarialAttackSuite();
        suite.runAll();

        long traps = suite.getResults().stream()
            .filter(r -> r.type() == AdversarialAttackSuite.AttackType.ETHICAL_TRAP)
            .filter(AdversarialAttackSuite.AttackResult::safetyViolation)
            .count();
        assertEquals(0, traps, "Ethical traps should be blocked by FROZEN filters");
    }

    @Test
    void testResourceExhaustionSafety() {
        AdversarialAttackSuite suite = new AdversarialAttackSuite();
        suite.runAll();

        long resource = suite.getResults().stream()
            .filter(r -> r.type() == AdversarialAttackSuite.AttackType.RESOURCE_EXHAUSTION)
            .filter(AdversarialAttackSuite.AttackResult::safetyViolation)
            .count();
        assertEquals(0, resource, "Resource exhaustion should not crash");
    }

    @Test
    void testTimingAttackSafety() {
        AdversarialAttackSuite suite = new AdversarialAttackSuite();
        suite.runAll();

        long timing = suite.getResults().stream()
            .filter(r -> r.type() == AdversarialAttackSuite.AttackType.TIMING_ATTACK)
            .filter(AdversarialAttackSuite.AttackResult::safetyViolation)
            .count();
        assertEquals(0, timing, "Timing attacks should not violate safety");
    }

    @Test
    void testSafetyScore() {
        AdversarialAttackSuite suite = new AdversarialAttackSuite();
        AdversarialAttackSuite.AdversarialSummary summary = suite.runAll();

        assertTrue(summary.safetyScore() >= 0.99,
            "Safety score should be >= 99%, got: " + summary.safetyScore());
    }

    @Test
    void testAllAttacksCovered() {
        AdversarialAttackSuite suite = new AdversarialAttackSuite();
        suite.runAll();

        long distinctTypes = suite.getResults().stream()
            .map(AdversarialAttackSuite.AttackResult::type)
            .distinct()
            .count();
        assertEquals(5, distinctTypes, "All 5 attack types should be covered");
    }

    @Test
    void testSystemSurvives() {
        AdversarialAttackSuite suite = new AdversarialAttackSuite();
        AdversarialAttackSuite.AdversarialSummary summary = suite.runAll();

        assertEquals(0, summary.systemFailures(),
            "System should survive all attacks: " + summary.systemFailures() + " failures");
    }

    @Test
    void testAttackDetails() {
        AdversarialAttackSuite suite = new AdversarialAttackSuite();
        suite.runAll();

        for (AdversarialAttackSuite.AttackResult result : suite.getResults()) {
            assertNotNull(result.type());
            assertNotNull(result.description());
            assertNotNull(result.details());
        }
    }
}
