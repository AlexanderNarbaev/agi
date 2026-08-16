package io.matrix.consensus;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ByzantineConsensusTest {

    @Test
    void shouldStartEmpty() {
        ByzantineConsensus consensus = new ByzantineConsensus();

        assertThat(consensus.networkSize()).isEqualTo(0);
        assertThat(consensus.state()).isEqualTo(ByzantineConsensus.ConsensusState.IDLE);
    }

    @Test
    void shouldAddNodes() {
        ByzantineConsensus consensus = new ByzantineConsensus();

        consensus.addNode(ByzantineNode.honest("node-1"));
        consensus.addNode(ByzantineNode.honest("node-2"));
        consensus.addNode(ByzantineNode.honest("node-3"));

        assertThat(consensus.networkSize()).isEqualTo(3);
    }

    @Test
    void shouldRejectProposalWithLessThan3Nodes() {
        ByzantineConsensus consensus = new ByzantineConsensus();
        consensus.addNode(ByzantineNode.honest("node-1"));
        consensus.addNode(ByzantineNode.honest("node-2"));

        assertThatThrownBy(() -> consensus.propose("node-1", "value"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldCommitWithNoFaultyNodes() {
        ByzantineConsensus consensus = new ByzantineConsensus();
        for (int i = 0; i < 4; i++) {
            consensus.addNode(ByzantineNode.honest("node-" + i));
        }

        ByzantineConsensus.ConsensusResult result = consensus.runConsensus("node-0", "value");

        assertThat(result.state()).isEqualTo(ByzantineConsensus.ConsensusState.COMMITTED);
        assertThat(result.decidedValue()).isEqualTo("value");
        assertThat(result.agreeVotes()).isEqualTo(4);
        assertThat(result.disagreeVotes()).isEqualTo(0);
    }

    @Test
    void shouldCommitWithOneFaultyNodeIn4NodeNetwork() {
        ByzantineConsensus consensus = new ByzantineConsensus();
        consensus.addNode(ByzantineNode.honest("node-0"));
        consensus.addNode(ByzantineNode.honest("node-1"));
        consensus.addNode(ByzantineNode.honest("node-2"));
        consensus.addNode(ByzantineNode.faulty("node-3"));

        ByzantineConsensus.ConsensusResult result = consensus.runConsensus("node-0", "value");

        assertThat(result.state()).isEqualTo(ByzantineConsensus.ConsensusState.COMMITTED);
        assertThat(result.decidedValue()).isEqualTo("value");
        assertThat(result.agreeVotes()).isEqualTo(3);
        assertThat(result.disagreeVotes()).isEqualTo(1);
    }

    @Test
    void shouldCommitWithMaxTolerableFaultyNodes() {
        ByzantineConsensus consensus = new ByzantineConsensus();
        consensus.addNode(ByzantineNode.honest("node-0"));
        consensus.addNode(ByzantineNode.honest("node-1"));
        consensus.addNode(ByzantineNode.honest("node-2"));
        consensus.addNode(ByzantineNode.faulty("node-3"));

        assertThat(consensus.maxFaultyTolerated()).isEqualTo(1);
        assertThat(consensus.canTolerateFaults()).isTrue();

        ByzantineConsensus.ConsensusResult result = consensus.runConsensus("node-0", "value");

        assertThat(result.state()).isEqualTo(ByzantineConsensus.ConsensusState.COMMITTED);
    }

    @Test
    void shouldRejectWhenTooManyFaultyNodes() {
        ByzantineConsensus consensus = new ByzantineConsensus();
        consensus.addNode(ByzantineNode.honest("node-0"));
        consensus.addNode(ByzantineNode.faulty("node-1"));
        consensus.addNode(ByzantineNode.faulty("node-2"));
        consensus.addNode(ByzantineNode.faulty("node-3"));

        assertThat(consensus.maxFaultyTolerated()).isEqualTo(1);
        assertThat(consensus.canTolerateFaults()).isFalse();

        ByzantineConsensus.ConsensusResult result = consensus.runConsensus("node-0", "value");

        assertThat(result.state()).isEqualTo(ByzantineConsensus.ConsensusState.REJECTED);
    }

    @Test
    void shouldCommitWithRecoveryAfterLeaderFailure() {
        ByzantineConsensus consensus = new ByzantineConsensus();
        consensus.addNode(ByzantineNode.honest("node-0"));
        consensus.addNode(ByzantineNode.honest("node-1"));
        consensus.addNode(ByzantineNode.honest("node-2"));
        consensus.addNode(ByzantineNode.faulty("node-3"));

        ByzantineConsensus.ConsensusResult result = consensus.runConsensusWithRecovery(
                "node-3", "recovered-value");

        assertThat(result.state()).isEqualTo(ByzantineConsensus.ConsensusState.COMMITTED);
    }

    @Test
    void shouldCalculateQuorumThreshold() {
        ByzantineConsensus consensus = new ByzantineConsensus();
        for (int i = 0; i < 7; i++) {
            consensus.addNode(ByzantineNode.honest("node-" + i));
        }

        assertThat(consensus.quorumThreshold()).isEqualTo(5);
    }

    @Test
    void shouldCalculateMaxFaultyTolerated() {
        ByzantineConsensus consensus = new ByzantineConsensus();
        for (int i = 0; i < 10; i++) {
            consensus.addNode(ByzantineNode.honest("node-" + i));
        }

        assertThat(consensus.maxFaultyTolerated()).isEqualTo(3);
    }

    @Test
    void shouldLogEvents() {
        ByzantineConsensus consensus = new ByzantineConsensus();
        for (int i = 0; i < 4; i++) {
            consensus.addNode(ByzantineNode.honest("node-" + i));
        }

        consensus.runConsensus("node-0", "value");

        assertThat(consensus.eventLog()).isNotEmpty();
        assertThat(consensus.eventLog().stream()
                .anyMatch(e -> e.contains("PROPOSE"))).isTrue();
        assertThat(consensus.eventLog().stream()
                .anyMatch(e -> e.contains("VOTE"))).isTrue();
    }

    @Test
    void shouldResetForNewRound() {
        ByzantineConsensus consensus = new ByzantineConsensus();
        for (int i = 0; i < 4; i++) {
            consensus.addNode(ByzantineNode.honest("node-" + i));
        }

        consensus.runConsensus("node-0", "value-1");
        int roundAfterFirst = consensus.currentRound();
        consensus.reset();

        assertThat(consensus.state()).isEqualTo(ByzantineConsensus.ConsensusState.IDLE);
        assertThat(consensus.currentRound()).isEqualTo(roundAfterFirst);

        ByzantineConsensus.ConsensusResult result = consensus.runConsensus("node-1", "value-2");
        assertThat(consensus.currentRound()).isEqualTo(roundAfterFirst + 1);
        assertThat(result.state()).isEqualTo(ByzantineConsensus.ConsensusState.COMMITTED);
        assertThat(result.decidedValue()).isEqualTo("value-2");
    }

    @Test
    void shouldRemoveNode() {
        ByzantineConsensus consensus = new ByzantineConsensus();
        consensus.addNode(ByzantineNode.honest("node-0"));
        consensus.addNode(ByzantineNode.honest("node-1"));
        consensus.addNode(ByzantineNode.honest("node-2"));

        consensus.removeNode("node-1");

        assertThat(consensus.networkSize()).isEqualTo(2);
    }

    @Test
    void shouldRun7NodeConsensusWith2Faulty() {
        ByzantineConsensus consensus = new ByzantineConsensus();
        for (int i = 0; i < 5; i++) {
            consensus.addNode(ByzantineNode.honest("node-" + i));
        }
        consensus.addNode(ByzantineNode.faulty("faulty-0"));
        consensus.addNode(ByzantineNode.faulty("faulty-1"));

        assertThat(consensus.maxFaultyTolerated()).isEqualTo(2);
        assertThat(consensus.canTolerateFaults()).isTrue();

        ByzantineConsensus.ConsensusResult result = consensus.runConsensus("node-0", "value");

        assertThat(result.state()).isEqualTo(ByzantineConsensus.ConsensusState.COMMITTED);
        assertThat(result.totalNodes()).isEqualTo(7);
        assertThat(result.agreeVotes()).isEqualTo(5);
        assertThat(result.disagreeVotes()).isEqualTo(2);
    }
}
