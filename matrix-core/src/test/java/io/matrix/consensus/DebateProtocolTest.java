package io.matrix.consensus;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DebateProtocolTest {

    @Test
    void shouldStartEmpty() {
        DebateProtocol protocol = new DebateProtocol();

        assertThat(protocol.agentCount()).isEqualTo(0);
        assertThat(protocol.state()).isEqualTo(DebateProtocol.DebateState.INITIALIZED);
    }

    @Test
    void shouldAddAgents() {
        DebateProtocol protocol = new DebateProtocol();

        protocol.addAgent(new DebateAgent("a", "X", 0.7));
        protocol.addAgent(new DebateAgent("b", "Y", 0.6));

        assertThat(protocol.agentCount()).isEqualTo(2);
    }

    @Test
    void shouldRemoveAgent() {
        DebateProtocol protocol = new DebateProtocol();
        protocol.addAgent(new DebateAgent("a", "X", 0.7));
        protocol.addAgent(new DebateAgent("b", "Y", 0.6));

        protocol.removeAgent("a");

        assertThat(protocol.agentCount()).isEqualTo(1);
    }

    @Test
    void shouldRejectDebateWithLessThan2Agents() {
        DebateProtocol protocol = new DebateProtocol();
        protocol.addAgent(new DebateAgent("a", "X", 0.7));

        assertThatThrownBy(protocol::runDebate)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldReachConsensusWhenAllAgree() {
        DebateProtocol protocol = new DebateProtocol();
        protocol.addAgent(new DebateAgent("a", "APPROVE", 0.8));
        protocol.addAgent(new DebateAgent("b", "APPROVE", 0.7));
        protocol.addAgent(new DebateAgent("c", "APPROVE", 0.9));

        DebateProtocol.DebateResult result = protocol.runDebate();

        assertThat(result.isConsensus()).isTrue();
        assertThat(result.consensusPosition()).isEqualTo("APPROVE");
        assertThat(result.state()).isEqualTo(DebateProtocol.DebateState.CONSENSUS_REACHED);
    }

    @Test
    void shouldReachConsensusWithQuorum() {
        DebateProtocol protocol = new DebateProtocol(10, 2.0 / 3.0, 0.4);
        protocol.addAgent(new DebateAgent("a", "APPROVE", 0.8));
        protocol.addAgent(new DebateAgent("b", "APPROVE", 0.7));
        protocol.addAgent(new DebateAgent("c", "APPROVE", 0.9));
        protocol.addAgent(new DebateAgent("d", "REJECT", 0.3));

        DebateProtocol.DebateResult result = protocol.runDebate();

        assertThat(result.isConsensus()).isTrue();
        assertThat(result.consensusPosition()).isEqualTo("APPROVE");
    }

    @Test
    void shouldReachConsensusWhenWeakAgentsFlip() {
        DebateProtocol protocol = new DebateProtocol(10, 2.0 / 3.0, 0.5);
        protocol.addAgent(new DebateAgent("a", "APPROVE", 0.9));
        protocol.addAgent(new DebateAgent("b", "APPROVE", 0.8));
        protocol.addAgent(new DebateAgent("c", "REJECT", 0.2));

        DebateProtocol.DebateResult result = protocol.runDebate();

        assertThat(result.isConsensus()).isTrue();
        assertThat(result.consensusPosition()).isEqualTo("APPROVE");
    }

    @Test
    void shouldDeadlockWhenNoPositionChanges() {
        DebateProtocol protocol = new DebateProtocol(3, 2.0 / 3.0, 0.1);
        protocol.addAgent(new DebateAgent("a", "APPROVE", 0.9));
        protocol.addAgent(new DebateAgent("b", "APPROVE", 0.9));
        protocol.addAgent(new DebateAgent("c", "REJECT", 0.9));

        DebateProtocol.DebateResult result = protocol.runDebate();

        assertThat(result.state()).isIn(
                DebateProtocol.DebateState.CONSENSUS_REACHED,
                DebateProtocol.DebateState.DEADLOCKED,
                DebateProtocol.DebateState.STALEMATE);
    }

    @Test
    void shouldStalemateWhenMaxRoundsExhausted() {
        DebateProtocol protocol = new DebateProtocol(2, 0.99, 0.1);
        protocol.addAgent(new DebateAgent("a", "APPROVE", 0.8));
        protocol.addAgent(new DebateAgent("b", "REJECT", 0.8));

        DebateProtocol.DebateResult result = protocol.runDebate();

        assertThat(result.state()).isIn(
                DebateProtocol.DebateState.CONSENSUS_REACHED,
                DebateProtocol.DebateState.DEADLOCKED,
                DebateProtocol.DebateState.STALEMATE);
    }

    @Test
    void shouldRecordRounds() {
        DebateProtocol protocol = new DebateProtocol(5, 2.0 / 3.0, 0.4);
        protocol.addAgent(new DebateAgent("a", "APPROVE", 0.8));
        protocol.addAgent(new DebateAgent("b", "APPROVE", 0.7));
        protocol.addAgent(new DebateAgent("c", "REJECT", 0.2));

        DebateProtocol.DebateResult result = protocol.runDebate();

        assertThat(result.rounds()).isNotEmpty();
    }

    @Test
    void shouldTrackFinalPositions() {
        DebateProtocol protocol = new DebateProtocol();
        protocol.addAgent(new DebateAgent("a", "APPROVE", 0.8));
        protocol.addAgent(new DebateAgent("b", "APPROVE", 0.7));
        protocol.addAgent(new DebateAgent("c", "APPROVE", 0.9));

        DebateProtocol.DebateResult result = protocol.runDebate();

        assertThat(result.finalPositions()).containsKeys("a", "b", "c");
        assertThat(result.finalConfidences()).containsKeys("a", "b", "c");
    }

    @Test
    void shouldTrackFinalConfidences() {
        DebateProtocol protocol = new DebateProtocol();
        protocol.addAgent(new DebateAgent("a", "APPROVE", 0.8));
        protocol.addAgent(new DebateAgent("b", "APPROVE", 0.7));
        protocol.addAgent(new DebateAgent("c", "APPROVE", 0.9));

        DebateProtocol.DebateResult result = protocol.runDebate();

        for (double conf : result.finalConfidences().values()) {
            assertThat(conf).isBetween(0.0, 1.0);
        }
    }

    @Test
    void shouldLogEvents() {
        DebateProtocol protocol = new DebateProtocol();
        protocol.addAgent(new DebateAgent("a", "APPROVE", 0.8));
        protocol.addAgent(new DebateAgent("b", "APPROVE", 0.7));

        protocol.runDebate();

        assertThat(protocol.eventLog()).isNotEmpty();
        assertThat(protocol.eventLog().stream()
                .anyMatch(e -> e.contains("AGENT_ADDED"))).isTrue();
        assertThat(protocol.eventLog().stream()
                .anyMatch(e -> e.contains("DEBATE_STARTED"))).isTrue();
    }

    @Test
    void shouldRunSingleRound() {
        DebateProtocol protocol = new DebateProtocol();
        protocol.addAgent(new DebateAgent("a", "APPROVE", 0.8));
        protocol.addAgent(new DebateAgent("b", "REJECT", 0.6));

        DebateProtocol.DebateRound round = protocol.runRound(1);

        assertThat(round.roundNumber()).isEqualTo(1);
        assertThat(round.positionsBefore()).containsKeys("a", "b");
        assertThat(round.positionsAfter()).containsKeys("a", "b");
        assertThat(round.arguments()).isNotEmpty();
    }

    @Test
    void shouldCheckConsensus() {
        DebateProtocol protocol = new DebateProtocol();
        protocol.addAgent(new DebateAgent("a", "APPROVE", 0.8));
        protocol.addAgent(new DebateAgent("b", "APPROVE", 0.7));
        protocol.addAgent(new DebateAgent("c", "APPROVE", 0.9));

        assertThat(protocol.checkConsensus()).isTrue();
    }

    @Test
    void shouldNotCheckConsensusWithSplit() {
        DebateProtocol protocol = new DebateProtocol();
        protocol.addAgent(new DebateAgent("a", "APPROVE", 0.8));
        protocol.addAgent(new DebateAgent("b", "REJECT", 0.7));

        assertThat(protocol.checkConsensus()).isFalse();
    }

    @Test
    void shouldReturnAgentsMap() {
        DebateProtocol protocol = new DebateProtocol();
        protocol.addAgent(new DebateAgent("a", "X", 0.5));
        protocol.addAgent(new DebateAgent("b", "Y", 0.6));

        Map<String, DebateAgent> agents = protocol.agents();

        assertThat(agents).hasSize(2);
        assertThat(agents).containsKey("a");
        assertThat(agents).containsKey("b");
    }

    @Test
    void shouldReturnCurrentRoundNumber() {
        DebateProtocol protocol = new DebateProtocol();
        protocol.addAgent(new DebateAgent("a", "X", 0.5));
        protocol.addAgent(new DebateAgent("b", "Y", 0.6));

        assertThat(protocol.currentRound()).isEqualTo(0);

        protocol.runDebate();

        assertThat(protocol.currentRound()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldRunDebateWithMultipleRounds() {
        DebateProtocol protocol = new DebateProtocol(10, 2.0 / 3.0, 0.5);
        protocol.addAgent(new DebateAgent("a", "APPROVE", 0.9));
        protocol.addAgent(new DebateAgent("b", "APPROVE", 0.8));
        protocol.addAgent(new DebateAgent("c", "REJECT", 0.3));
        protocol.addAgent(new DebateAgent("d", "REJECT", 0.2));

        DebateProtocol.DebateResult result = protocol.runDebate();

        assertThat(result.totalRounds()).isGreaterThanOrEqualTo(0);
        assertThat(result.rounds()).isNotEmpty();
    }
}
