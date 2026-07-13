package io.matrix.consensus;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ByzantineNodeTest {

    @Test
    void shouldBeHonestByDefault() {
        ByzantineNode node = new ByzantineNode("node-1");

        assertThat(node.nodeId()).isEqualTo("node-1");
        assertThat(node.state()).isEqualTo(ByzantineNode.State.HONEST);
        assertThat(node.isHonest()).isTrue();
        assertThat(node.isFaulty()).isFalse();
        assertThat(node.isSuspected()).isFalse();
    }

    @Test
    void shouldCreateFaultyNode() {
        ByzantineNode node = ByzantineNode.faulty("bad-node");

        assertThat(node.state()).isEqualTo(ByzantineNode.State.FAULTY);
        assertThat(node.isFaulty()).isTrue();
    }

    @Test
    void shouldRecordSentMessages() {
        ByzantineNode node = ByzantineNode.honest("node-1");
        ByzantineMessage msg = ByzantineMessage.propose("node-1", "value", 1);

        node.recordSent(msg);

        assertThat(node.sentMessages()).hasSize(1);
        assertThat(node.sentMessages().get(0).payload()).isEqualTo("value");
    }

    @Test
    void shouldRecordReceivedMessages() {
        ByzantineNode node = ByzantineNode.honest("node-1");
        ByzantineMessage msg = ByzantineMessage.propose("node-2", "value", 1);

        boolean suspected = node.recordReceived(msg);

        assertThat(suspected).isFalse();
        assertThat(node.receivedMessages()).hasSize(1);
        assertThat(node.messagesFrom("node-2")).hasSize(1);
    }

    @Test
    void shouldDetectContradictoryVotesInSameRound() {
        ByzantineNode node = ByzantineNode.honest("node-1");

        ByzantineMessage vote1 = ByzantineMessage.vote("node-2", "value", 1, true);
        ByzantineMessage vote2 = ByzantineMessage.vote("node-2", "value", 1, false);

        node.recordReceived(vote1);
        boolean suspected = node.recordReceived(vote2);

        assertThat(suspected).isTrue();
        assertThat(node.suspicionScore()).isGreaterThan(0);
    }

    @Test
    void shouldDetectHallucinations() {
        ByzantineNode node = ByzantineNode.honest("node-1");

        ByzantineMessage prop1 = ByzantineMessage.propose("node-2", "value-A", 1);
        ByzantineMessage prop2 = ByzantineMessage.propose("node-2", "value-B", 2);

        node.recordReceived(prop1);
        node.recordReceived(prop2);

        List<String> hallucinated = node.detectHallucinations();

        assertThat(hallucinated).contains("node-2");
    }

    @Test
    void shouldDetectContradictions() {
        ByzantineNode node = ByzantineNode.honest("node-1");

        ByzantineMessage proposal = ByzantineMessage.propose("node-2", "my-value", 1);
        ByzantineMessage vote = ByzantineMessage.vote("node-2", "my-value", 1, false);

        node.recordReceived(proposal);
        node.recordReceived(vote);

        List<String> contradicted = node.detectContradictions();

        assertThat(contradicted).contains("node-2");
    }

    @Test
    void shouldTransitionToSuspectedAfterThreshold() {
        ByzantineNode node = ByzantineNode.honest("node-1");

        for (int i = 0; i < 5; i++) {
            ByzantineMessage vote1 = ByzantineMessage.vote("node-2", "v", i, true);
            ByzantineMessage vote2 = ByzantineMessage.vote("node-2", "v", i, false);
            node.recordReceived(vote1);
            node.recordReceived(vote2);
        }

        assertThat(node.state()).isEqualTo(ByzantineNode.State.SUSPECTED);
    }

    @Test
    void shouldClearSuspicion() {
        ByzantineNode node = ByzantineNode.honest("node-1");
        node.transitionTo(ByzantineNode.State.SUSPECTED);

        node.clearSuspicion();

        assertThat(node.state()).isEqualTo(ByzantineNode.State.HONEST);
        assertThat(node.suspicionScore()).isEqualTo(0);
    }

    @Test
    void shouldTransitionState() {
        ByzantineNode node = ByzantineNode.honest("node-1");

        node.transitionTo(ByzantineNode.State.FAULTY);

        assertThat(node.state()).isEqualTo(ByzantineNode.State.FAULTY);
        assertThat(node.isFaulty()).isTrue();
    }

    @Test
    void shouldNotDetectHallucinationsForConsistentPayloads() {
        ByzantineNode node = ByzantineNode.honest("node-1");

        ByzantineMessage prop1 = ByzantineMessage.propose("node-2", "same", 1);
        ByzantineMessage prop2 = ByzantineMessage.propose("node-2", "same", 2);

        node.recordReceived(prop1);
        node.recordReceived(prop2);

        List<String> hallucinated = node.detectHallucinations();

        assertThat(hallucinated).isEmpty();
    }

    @Test
    void shouldEqualByNodeId() {
        ByzantineNode a = new ByzantineNode("node-1");
        ByzantineNode b = new ByzantineNode("node-1");
        ByzantineNode c = new ByzantineNode("node-2");

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
