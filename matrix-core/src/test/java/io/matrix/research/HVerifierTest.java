package io.matrix.research;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HVerifier} scaffolding (RUN 46).
 */
class HVerifierTest {

    @Test
    void verdictAcceptsAndExposesFields() {
        var v = HVerifier.accepted("H-TEST", "accuracy", 0.95, 0.90, "≥ 0.90");
        assertThat(v.isAccepted()).isTrue();
        assertThat(v.hypothesisId()).isEqualTo("H-TEST");
        assertThat(v.measured()).isEqualTo(0.95);
        assertThat(v.threshold()).isEqualTo(0.90);
        assertThat(v.criterion()).isEqualTo("≥ 0.90");
        assertThat(v.metric()).isEqualTo("accuracy");
    }

    @Test
    void rejectedVerdictReportsCorrectly() {
        var v = HVerifier.rejected("H-TEST", "ece", 0.5, 0.1, "≤ 0.1");
        assertThat(v.isAccepted()).isFalse();
        assertThat(v.outcome()).isEqualTo(HVerifier.HypothesisVerdict.REJECTED);
    }

    @Test
    void inconclusiveVerdictReportsCorrectly() {
        var v = HVerifier.inconclusive("H-X", "drift", 0.5, 0.5, "boundary case");
        assertThat(v.outcome()).isEqualTo(HVerifier.HypothesisVerdict.INCONCLUSIVE);
        assertThat(v.isAccepted()).isFalse();
    }

    @Test
    void errorVerdictReportsMessage() {
        var v = HVerifier.error("H-Y", "missing test data");
        assertThat(v.outcome()).isEqualTo(HVerifier.HypothesisVerdict.ERROR);
        assertThat(v.criterion()).isEqualTo("missing test data");
    }

    @Test
    void withExtraAddsFields() {
        var v = HVerifier.accepted("H-Z", "acc", 0.95, 0.90, "≥ 0.90");
        var v2 = HVerifier.withExtra(v, "samples", 100);
        assertThat(v2.extras()).containsEntry("samples", 100);
        // Original verdict unchanged.
        assertThat(v.extras()).doesNotContainKey("samples");
    }

    @Test
    void toSummaryStringContainsKeyFields() {
        var v = HVerifier.accepted("H-SUM", "acc", 0.95, 0.90, "≥ 0.90");
        String s = v.toSummaryString();
        assertThat(s).contains("H-SUM");
        assertThat(s).contains("ACCEPTED");
        assertThat(s).contains("0.9500");
        assertThat(s).contains("0.9000");
    }

    @Test
    void abstractRunExperimentCanBeImplemented() {
        // Verify that the abstract method can be implemented in a subclass.
        HVerifier verifier = new HVerifier() {
            @Override
            public Verdict runExperiment() {
                return accepted("H-IMPL", "m", 1.0, 0.5, "test");
            }
        };
        assertThat(verifier.runExperiment().isAccepted()).isTrue();
    }
}
