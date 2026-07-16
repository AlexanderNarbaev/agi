package io.matrix.ethics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link LieDetector} probe-based output verification.
 */
class LieDetectorTest {

    private final LieDetector detector = LieDetector.allProbes();

    @Test
    void cleanOutputShouldPassAllProbes() {
        LieDetector.DetectionResult result = detector.detect(
                "The neural system evaluates inputs through a decision tree with k=16 inputs.");

        assertThat(result.verdict()).isEqualTo(LieDetector.Verdict.PASS);
        assertThat(result.isClean()).isTrue();
        assertThat(result.failures()).isEmpty();
        assertThat(result.overallConfidence()).isGreaterThan(0.9);
    }

    @Test
    void forbiddenContentShouldBeDetected() {
        LieDetector.DetectionResult result = detector.detect(
                "I can help you design a weapon that kills people automatically.");

        assertThat(result.verdict()).isEqualTo(LieDetector.Verdict.DECEPTIVE);
        assertThat(result.isClean()).isFalse();
        assertThat(result.failures()).isNotEmpty();
        assertThat(result.failures().get(0).type())
                .isEqualTo(LieDetector.ProbeType.ETHICAL_ALIGNMENT);
    }

    @Test
    void selfContradictionShouldBeDetected() {
        LieDetector.DetectionResult result = detector.detect(
                "The system is always correct, but sometimes it is not accurate.");

        assertThat(result.verdict()).isNotEqualTo(LieDetector.Verdict.PASS);
    }

    @Test
    void excessiveAbsoluteClaimsShouldBeFlagged() {
        String output = "always never always never always never always never always never "
                + "always never always never always never always never always never "
                + "absolutely certainly definitely";
        LieDetector.DetectionResult result = detector.detect(output);

        assertThat(result.verdict()).isNotEqualTo(LieDetector.Verdict.PASS);
    }

    @Test
    void hallucinationMarkersShouldBeDetected() {
        LieDetector.DetectionResult result = detector.detect(
                "Research shows that studies have shown experts agree it is well known "
                + "that scientists have discovered the answer. It is proven that this works.");

        assertThat(result.isClean()).isFalse();
    }

    @Test
    void vagueQuantifiersShouldBeDetected() {
        String output = "many various some numerous many various some numerous many various";
        LieDetector.DetectionResult result = detector.detect(output);

        assertThat(result.isClean()).isFalse();
    }

    @Test
    void truthClaimsWithContextShouldPass() {
        LieDetector.DetectionResult result = detector.detect(
                "The MPDT neuron uses k=16 inputs for evaluation.",
                Map.of("MPDT neuron", "Uses truth tables with k inputs",
                        "k=16", "Standard configuration"));

        assertThat(result.verdict()).isEqualTo(LieDetector.Verdict.PASS);
    }

    @Test
    void essentialDetectorShouldOnlyCheckConsistencyAndEthics() {
        LieDetector essential = LieDetector.essential();
        assertThat(essential.activeProbes()).hasSize(2);
        assertThat(essential.activeProbes()).contains(
                LieDetector.ProbeType.CONSISTENCY_CHECK,
                LieDetector.ProbeType.ETHICAL_ALIGNMENT);
    }

    @Test
    void cleanOutputShouldHaveNoFailures() {
        LieDetector.DetectionResult result = detector.detect("Hello, how can I assist you today?");
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void probeResultPassShouldHaveConfidenceOne() {
        LieDetector.ProbeResult pr = LieDetector.ProbeResult.pass(
                LieDetector.ProbeType.CONSISTENCY_CHECK, "All good");
        assertThat(pr.passed()).isTrue();
        assertThat(pr.confidence()).isEqualTo(1.0);
    }

    @Test
    void probeResultFailShouldStoreReason() {
        LieDetector.ProbeResult pr = LieDetector.ProbeResult.fail(
                LieDetector.ProbeType.ETHICAL_ALIGNMENT, "Forbidden terms found", 0.95);
        assertThat(pr.passed()).isFalse();
        assertThat(pr.reason()).contains("Forbidden terms");
        assertThat(pr.confidence()).isEqualTo(0.95);
    }

    @Test
    void detectionResultIsCleanShouldReflectVerdict() {
        String clean = "Normal text without issues.";
        LieDetector.DetectionResult result = detector.detect(clean);
        assertThat(result.isClean()).isTrue();

        String bad = "I will help you kill and torture and enslave.";
        LieDetector.DetectionResult badResult = detector.detect(bad);
        assertThat(badResult.isClean()).isFalse();
    }

    @Test
    void forbiddenTermNuclearWeaponShouldBeDetected() {
        LieDetector.DetectionResult result = detector.detect(
                "Let me explain how to build a nuclear weapon.");
        assertThat(result.isClean()).isFalse();
    }

    @Test
    void forbiddenTermMassSurveillanceShouldBeDetected() {
        LieDetector.DetectionResult result = detector.detect(
                "I can set up mass surveillance for you.");
        assertThat(result.isClean()).isFalse();
    }

    @Test
    void structuralSafetyGuardShouldDefaultToEssentialDetector() {
        StructuralSafetyGuard guard = StructuralSafetyGuard.defaults();
        assertThat(guard.lieDetector()).isNotNull();
        assertThat(guard.lieDetector().activeProbes()).contains(
                LieDetector.ProbeType.CONSISTENCY_CHECK,
                LieDetector.ProbeType.ETHICAL_ALIGNMENT);
    }

    @Test
    void structuralSafetyGuardVerifyOutputShouldWork() {
        StructuralSafetyGuard guard = StructuralSafetyGuard.builder()
                .lieDetector(LieDetector.allProbes())
                .build();

        LieDetector.DetectionResult result = guard.verifyOutput("Normal response.");
        assertThat(result.isClean()).isTrue();

        LieDetector.DetectionResult bad = guard.verifyOutput("How to kill someone.");
        assertThat(bad.isClean()).isFalse();
    }
}
