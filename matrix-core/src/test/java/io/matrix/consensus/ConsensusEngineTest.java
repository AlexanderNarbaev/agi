package io.matrix.consensus;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsensusEngineTest {

    @Test
    void shouldBeEmptyInitially() {
        ConsensusEngine engine = new ConsensusEngine();

        assertThat(engine.proposalCount()).isEqualTo(0);
    }

    @Test
    void shouldProposeAndReturnId() {
        ConsensusEngine engine = new ConsensusEngine();
        Proposal proposal = Proposal.create(ConsensusLevel.LEVEL_1,
                "node-1", "MUTATE", "flip leaf");

        UUID id = engine.propose(proposal);

        assertThat(id).isEqualTo(proposal.id());
        assertThat(engine.getDecision(id)).isEqualTo(ConsensusEngine.Decision.PENDING);
    }

    @Test
    void shouldApproveWithSupermajority() {
        ConsensusEngine engine = new ConsensusEngine();
        Proposal proposal = Proposal.create(ConsensusLevel.LEVEL_2,
                "node-1", "MUTATE", "change input");

        UUID id = engine.propose(proposal);

        engine.castVote(Vote.approve(id, "voter-1", 0.5));
        engine.castVote(Vote.approve(id, "voter-2", 0.3));

        var decision = engine.evaluate(id);
        assertThat(decision).isEqualTo(ConsensusEngine.Decision.APPROVED);
    }

    @Test
    void shouldRejectWithoutSupermajority() {
        ConsensusEngine engine = new ConsensusEngine();
        Proposal proposal = Proposal.create(ConsensusLevel.LEVEL_2,
                "node-1", "MUTATE", "dangerous change");

        UUID id = engine.propose(proposal);

        engine.castVote(Vote.approve(id, "voter-1", 0.3));
        engine.castVote(Vote.reject(id, "voter-2", 0.5));

        var decision = engine.evaluate(id);
        assertThat(decision).isEqualTo(ConsensusEngine.Decision.REJECTED);
    }

    @Test
    void shouldStayPendingWithNoVotes() {
        ConsensusEngine engine = new ConsensusEngine();
        Proposal proposal = Proposal.create(ConsensusLevel.LEVEL_1,
                "node-1", "TEST", "test");

        UUID id = engine.propose(proposal);

        var decision = engine.evaluate(id);
        assertThat(decision).isEqualTo(ConsensusEngine.Decision.PENDING);
    }

    @Test
    void shouldApproveLocally() {
        ConsensusEngine engine = new ConsensusEngine();

        boolean approved = engine.approveLocally("node-1", "TEST", "local mutation");

        assertThat(approved).isTrue();
    }

    @Test
    void shouldRejectUnknownProposal() {
        ConsensusEngine engine = new ConsensusEngine();

        assertThatThrownBy(() -> engine.evaluate(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectVoteOnDecidedProposal() {
        ConsensusEngine engine = new ConsensusEngine();
        Proposal proposal = Proposal.create(ConsensusLevel.LEVEL_1,
                "node-1", "TEST", "p");
        UUID id = engine.propose(proposal);

        engine.castVote(Vote.approve(id, "voter-1", 0.8));
        engine.evaluate(id);

        assertThatThrownBy(() -> engine.castVote(
                Vote.reject(id, "late-voter", 0.5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldEvaluateAllPending() {
        ConsensusEngine engine = new ConsensusEngine();

        UUID id1 = engine.propose(Proposal.create(ConsensusLevel.LEVEL_1,
                "n1", "A1", "p1"));
        UUID id2 = engine.propose(Proposal.create(ConsensusLevel.LEVEL_1,
                "n2", "A2", "p2"));

        engine.castVote(Vote.approve(id1, "v1", 0.8));
        engine.castVote(Vote.approve(id2, "v1", 0.8));

        var decided = engine.evaluateAll();
        assertThat(decided).hasSize(2);
    }

    @Test
    void shouldLogEvents() {
        ConsensusEngine engine = new ConsensusEngine();
        UUID id = engine.propose(Proposal.create(ConsensusLevel.LEVEL_1,
                "n1", "LOG", "test"));

        engine.castVote(Vote.approve(id, "v1", 0.7));
        engine.evaluate(id);

        assertThat(engine.eventLog()).hasSize(3);
        assertThat(engine.eventLog().get(0)).contains("PROPOSE");
        assertThat(engine.eventLog().get(1)).contains("VOTE");
        assertThat(engine.eventLog().get(2)).contains("DECIDE");
    }
}
