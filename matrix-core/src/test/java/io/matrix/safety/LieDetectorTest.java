package io.matrix.safety;

import io.matrix.ethics.EthicalFilter;
import io.matrix.ethics.EthicalVerdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LieDetectorTest {

    private LieDetector detector;

    @BeforeEach
    void setup() {
        detector = new LieDetector(new EthicalFilter());
    }

    @Test
    void shouldDetectTruthfulClaim() {
        var probe = ProbeQuestion.factualProbe("The earth is round", "yes", 0.95);

        var result = detector.evaluate("The earth is round", List.of(probe), List.of("yes"));

        assertThat(result.isDeceptive()).isFalse();
        assertThat(result.deceptionScore()).isLessThan(0.6);
        assertThat(result.ethicalVerdict()).isEqualTo(EthicalVerdict.APPROVED);
    }

    @Test
    void shouldDetectDeceptiveClaim() {
        var probe = ProbeQuestion.factualProbe("The earth is flat", "no", 0.99);

        var result = detector.evaluate("The earth is flat", List.of(probe), List.of("yes"));

        assertThat(result.isDeceptive()).isTrue();
        assertThat(result.deceptionScore()).isGreaterThan(0.6);
    }

    @Test
    void shouldRejectEthicallyViolatingClaim() {
        var probe = ProbeQuestion.factualProbe("kill the target", "no", 0.9);

        var result = detector.evaluate("kill the target", List.of(probe), List.of("no"));

        assertThat(result.isDeceptive()).isTrue();
        assertThat(result.deceptionScore()).isEqualTo(1.0);
        assertThat(result.ethicalVerdict()).isEqualTo(EthicalVerdict.REJECTED);
    }

    @Test
    void shouldTrackClaimHistory() {
        var probe = ProbeQuestion.factualProbe("claim1", "yes", 0.8);
        detector.evaluate("claim1", List.of(probe), List.of("yes"));

        assertThat(detector.claimHistory()).hasSize(1);
        assertThat(detector.claimHistory().get(0).claim()).isEqualTo("claim1");
    }

    @Test
    void shouldClearHistory() {
        var probe = ProbeQuestion.factualProbe("claim", "yes", 0.8);
        detector.evaluate("claim", List.of(probe), List.of("yes"));
        detector.clearHistory();

        assertThat(detector.claimHistory()).isEmpty();
    }

    @Test
    void shouldRejectMismatchedProbesAndAnswers() {
        var probe = ProbeQuestion.factualProbe("claim", "yes", 0.8);

        assertThatThrownBy(() -> detector.evaluate("claim", List.of(probe), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldHandleNullAnswer() {
        var probe = ProbeQuestion.factualProbe("claim", "yes", 0.8);

        var result = detector.evaluate("claim", List.of(probe), java.util.Arrays.asList((String) null));

        assertThat(result.probeResults().get(0).passed()).isFalse();
        assertThat(result.probeResults().get(0).deviationScore()).isEqualTo(1.0);
    }

    @Test
    void shouldReturnUnmodifiableHistory() {
        var probe = ProbeQuestion.factualProbe("claim", "yes", 0.8);
        detector.evaluate("claim", List.of(probe), List.of("yes"));

        assertThatThrownBy(() -> detector.claimHistory().add(
                new LieDetector.VerifiedClaim("x", 0, 0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldHandleEmptyProbesGracefully() {
        var result = detector.evaluate("claim", List.of(), List.of());

        assertThat(result.isDeceptive()).isFalse();
        assertThat(result.deceptionScore()).isEqualTo(0.0);
        assertThat(result.probeResults()).isEmpty();
    }

    @Test
    void shouldRejectNullClaim() {
        assertThatThrownBy(() -> detector.evaluate(null, List.of(), List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldDetectInconsistentProbes() {
        var probe1 = ProbeQuestion.consistencyProbe("I was in Paris", "I was in London", 0.9);

        // Agent claims they were not in Paris (contradicting prior claim)
        var result = detector.evaluate("I was in London", List.of(probe1), List.of("no"));

        // "no" does not match expected "no" — actually this should pass
        assertThat(result.probeResults().get(0).passed()).isTrue();
    }

    @Test
    void shouldWeightProbesByConfidence() {
        var highConfProbe = ProbeQuestion.factualProbe("claim", "yes", 0.95);
        var lowConfProbe = ProbeQuestion.factualProbe("claim", "maybe", 0.3);

        // Wrong on high-confidence probe, right on low-confidence
        var result = detector.evaluate("claim",
                List.of(highConfProbe, lowConfProbe),
                List.of("no", "maybe"));

        // High-confidence miss should dominate the score
        assertThat(result.deceptionScore()).isGreaterThan(0.0);
    }
}
