package io.matrix.consensus;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DebateAgentTest {

    @Test
    void shouldCreateAgentWithPositionAndConfidence() {
        DebateAgent agent = new DebateAgent("agent-1", "APPROVE", 0.8);

        assertThat(agent.agentId()).isEqualTo("agent-1");
        assertThat(agent.position()).isEqualTo("APPROVE");
        assertThat(agent.confidence()).isCloseTo(0.8, org.assertj.core.data.Offset.offset(0.01));
        assertThat(agent.evidence()).isEmpty();
        assertThat(agent.argumentHistory()).isEmpty();
    }

    @Test
    void shouldClampConfidenceToValidRange() {
        DebateAgent high = new DebateAgent("a", "X", 1.5);
        DebateAgent low = new DebateAgent("b", "Y", -0.5);

        assertThat(high.confidence()).isEqualTo(1.0);
        assertThat(low.confidence()).isEqualTo(0.0);
    }

    @Test
    void shouldTrackPositionHistory() {
        DebateAgent agent = new DebateAgent("a", "POS-A", 0.5);

        assertThat(agent.positionHistory()).containsExactly("POS-A");
    }

    @Test
    void shouldAddEvidence() {
        DebateAgent agent = new DebateAgent("a", "X", 0.5);

        agent.addEvidence("evidence-1");
        agent.addEvidence("evidence-2");

        assertThat(agent.evidence()).containsExactly("evidence-1", "evidence-2");
    }

    @Test
    void shouldArgueForPosition() {
        DebateAgent agent = new DebateAgent("a", "APPROVE", 0.7);
        agent.addEvidence("supporting data");

        DebateAgent.Argument arg = agent.argueFor(1);

        assertThat(arg.agentId()).isEqualTo("a");
        assertThat(arg.position()).isEqualTo("APPROVE");
        assertThat(arg.isSupporting()).isTrue();
        assertThat(arg.round()).isEqualTo(1);
        assertThat(arg.confidence()).isCloseTo(0.7, org.assertj.core.data.Offset.offset(0.01));
        assertThat(arg.evidence()).isEqualTo("supporting data");
    }

    @Test
    void shouldArgueAgainstPosition() {
        DebateAgent agent = new DebateAgent("a", "APPROVE", 0.7);
        agent.addEvidence("counter data");

        DebateAgent.Argument arg = agent.argueAgainst("REJECT", 1);

        assertThat(arg.agentId()).isEqualTo("a");
        assertThat(arg.position()).isEqualTo("REJECT");
        assertThat(arg.isSupporting()).isFalse();
        assertThat(arg.round()).isEqualTo(1);
    }

    @Test
    void shouldTrackArgumentHistory() {
        DebateAgent agent = new DebateAgent("a", "X", 0.5);

        agent.argueFor(1);
        agent.argueAgainst("Y", 1);
        agent.argueFor(2);

        assertThat(agent.argumentCount()).isEqualTo(3);
        assertThat(agent.argumentHistory()).hasSize(3);
    }

    @Test
    void shouldFlipPositionWhenCounterArgumentStronger() {
        DebateAgent agent = new DebateAgent("a", "APPROVE", 0.3);

        DebateAgent.Argument counter = new DebateAgent.Argument(
                "b", "REJECT", "strong evidence", 0.9, false, 1, System.currentTimeMillis());

        boolean flipped = agent.evaluateArgument(counter, 0.4);

        assertThat(flipped).isTrue();
        assertThat(agent.position()).isEqualTo("REJECT");
        assertThat(agent.hasFlipped()).isTrue();
        assertThat(agent.positionHistory()).containsExactly("APPROVE", "REJECT");
    }

    @Test
    void shouldNotFlipWhenConfidenceAboveThreshold() {
        DebateAgent agent = new DebateAgent("a", "APPROVE", 0.6);

        DebateAgent.Argument counter = new DebateAgent.Argument(
                "b", "REJECT", "evidence", 0.9, false, 1, System.currentTimeMillis());

        boolean flipped = agent.evaluateArgument(counter, 0.4);

        assertThat(flipped).isFalse();
        assertThat(agent.position()).isEqualTo("APPROVE");
    }

    @Test
    void shouldNotFlipWhenCounterConfidenceLower() {
        DebateAgent agent = new DebateAgent("a", "APPROVE", 0.3);

        DebateAgent.Argument counter = new DebateAgent.Argument(
                "b", "REJECT", "weak evidence", 0.2, false, 1, System.currentTimeMillis());

        boolean flipped = agent.evaluateArgument(counter, 0.4);

        assertThat(flipped).isFalse();
        assertThat(agent.position()).isEqualTo("APPROVE");
    }

    @Test
    void shouldNotFlipForSupportingArguments() {
        DebateAgent agent = new DebateAgent("a", "APPROVE", 0.3);

        DebateAgent.Argument supporting = new DebateAgent.Argument(
                "b", "APPROVE", "support", 0.9, true, 1, System.currentTimeMillis());

        boolean flipped = agent.evaluateArgument(supporting, 0.4);

        assertThat(flipped).isFalse();
        assertThat(agent.position()).isEqualTo("APPROVE");
    }

    @Test
    void shouldAdjustConfidence() {
        DebateAgent agent = new DebateAgent("a", "X", 0.5);

        agent.adjustConfidence(0.2);
        assertThat(agent.confidence()).isCloseTo(0.7, org.assertj.core.data.Offset.offset(0.01));

        agent.adjustConfidence(-0.3);
        assertThat(agent.confidence()).isCloseTo(0.4, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void shouldClampConfidenceOnAdjust() {
        DebateAgent agent = new DebateAgent("a", "X", 0.9);

        agent.adjustConfidence(0.5);
        assertThat(agent.confidence()).isEqualTo(1.0);

        agent.adjustConfidence(-2.0);
        assertThat(agent.confidence()).isEqualTo(0.0);
    }

    @Test
    void shouldResetForNewDebate() {
        DebateAgent agent = new DebateAgent("a", "OLD", 0.8);
        agent.addEvidence("old evidence");
        agent.argueFor(1);

        agent.reset("NEW", 0.5);

        assertThat(agent.position()).isEqualTo("NEW");
        assertThat(agent.confidence()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.01));
        assertThat(agent.evidence()).isEmpty();
        assertThat(agent.argumentHistory()).isEmpty();
        assertThat(agent.positionHistory()).containsExactly("NEW");
    }

    @Test
    void shouldReportHasNotFlippedInitially() {
        DebateAgent agent = new DebateAgent("a", "X", 0.5);

        assertThat(agent.hasFlipped()).isFalse();
    }

    @Test
    void shouldHandleNoEvidenceInArgument() {
        DebateAgent agent = new DebateAgent("a", "X", 0.5);

        DebateAgent.Argument arg = agent.argueFor(1);

        assertThat(arg.evidence()).isEqualTo("No evidence yet");
    }

    @Test
    void shouldEqualByAgentId() {
        DebateAgent a = new DebateAgent("agent-1", "X", 0.5);
        DebateAgent b = new DebateAgent("agent-1", "Y", 0.8);
        DebateAgent c = new DebateAgent("agent-2", "X", 0.5);

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void shouldRejectNullAgentId() {
        assertThatThrownBy(() -> new DebateAgent(null, "X", 0.5))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullPosition() {
        assertThatThrownBy(() -> new DebateAgent("a", null, 0.5))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldHaveReadableToString() {
        DebateAgent agent = new DebateAgent("agent-1", "APPROVE", 0.75);

        String str = agent.toString();

        assertThat(str).contains("agent-1", "APPROVE", "0.750");
    }
}
