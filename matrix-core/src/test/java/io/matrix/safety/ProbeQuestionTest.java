package io.matrix.safety;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProbeQuestionTest {

    @Test
    void shouldCreateFactualProbe() {
        var probe = ProbeQuestion.factualProbe("The sky is blue", "yes", 0.9);

        assertThat(probe.probeType()).isEqualTo(ProbeQuestion.ProbeType.FACTUAL);
        assertThat(probe.expectedAnswer()).isEqualTo("yes");
        assertThat(probe.confidence()).isEqualTo(0.9);
        assertThat(probe.context()).isEqualTo("The sky is blue");
        assertThat(probe.question()).contains("The sky is blue");
    }

    @Test
    void shouldCreateConsistencyProbe() {
        var probe = ProbeQuestion.consistencyProbe("I am 25 years old", "I am 30 years old", 0.8);

        assertThat(probe.probeType()).isEqualTo(ProbeQuestion.ProbeType.CONSISTENCY);
        assertThat(probe.expectedAnswer()).isEqualTo("no");
        assertThat(probe.question()).contains("I am 25 years old");
        assertThat(probe.question()).contains("I am 30 years old");
    }

    @Test
    void shouldCreateLogicalProbe() {
        var probe = ProbeQuestion.logicalProbe("All humans are mortal", "Socrates is mortal", 0.7);

        assertThat(probe.probeType()).isEqualTo(ProbeQuestion.ProbeType.LOGICAL);
        assertThat(probe.expectedAnswer()).isNull();
        assertThat(probe.context()).isEqualTo("Socrates is mortal");
        assertThat(probe.question()).contains("All humans are mortal");
        assertThat(probe.question()).contains("Socrates is mortal");
    }

    @Test
    void shouldRejectNullQuestion() {
        assertThatThrownBy(() -> new ProbeQuestion(null, "yes", 0.5,
                ProbeQuestion.ProbeType.FACTUAL, "ctx"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullProbeType() {
        assertThatThrownBy(() -> new ProbeQuestion("q?", "yes", 0.5, null, "ctx"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectConfidenceBelowZero() {
        assertThatThrownBy(() -> new ProbeQuestion("q?", "yes", -0.1,
                ProbeQuestion.ProbeType.FACTUAL, "ctx"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectConfidenceAboveOne() {
        assertThatThrownBy(() -> new ProbeQuestion("q?", "yes", 1.1,
                ProbeQuestion.ProbeType.FACTUAL, "ctx"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcceptBoundaryConfidenceValues() {
        var low = new ProbeQuestion("q?", "a", 0.0, ProbeQuestion.ProbeType.FACTUAL, "ctx");
        var high = new ProbeQuestion("q?", "a", 1.0, ProbeQuestion.ProbeType.FACTUAL, "ctx");

        assertThat(low.confidence()).isEqualTo(0.0);
        assertThat(high.confidence()).isEqualTo(1.0);
    }

    @Test
    void shouldSupportAllProbeTypes() {
        assertThat(ProbeQuestion.ProbeType.values()).containsExactlyInAnyOrder(
                ProbeQuestion.ProbeType.FACTUAL,
                ProbeQuestion.ProbeType.CONSISTENCY,
                ProbeQuestion.ProbeType.LOGICAL
        );
    }

    @Test
    void shouldBeImmutableRecord() {
        var probe = ProbeQuestion.factualProbe("claim", "yes", 0.8);

        // Records are immutable — fields are final
        assertThat(probe.question()).isEqualTo("Is the following claim true: 'claim'?");
        assertThat(probe.expectedAnswer()).isEqualTo("yes");
        assertThat(probe.confidence()).isEqualTo(0.8);
    }

    @Test
    void shouldHandleNullExpectedAnswerInFactualProbe() {
        var probe = ProbeQuestion.factualProbe("claim", null, 0.5);

        assertThat(probe.expectedAnswer()).isNull();
    }
}
