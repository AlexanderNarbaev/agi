package io.matrix.consensus;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConsensusTypesTest {

    @Test
    void shouldHaveCorrectLevelValues() {
        assertThat(ConsensusLevel.LEVEL_0.level()).isEqualTo(0);
        assertThat(ConsensusLevel.LEVEL_1.level()).isEqualTo(1);
        assertThat(ConsensusLevel.LEVEL_2.level()).isEqualTo(2);
        assertThat(ConsensusLevel.LEVEL_3.level()).isEqualTo(3);
    }

    @Test
    void shouldHaveScopeDescriptions() {
        assertThat(ConsensusLevel.LEVEL_0.scope()).isEqualTo("Single FNL");
        assertThat(ConsensusLevel.LEVEL_1.scope()).isEqualTo("Single cluster");
        assertThat(ConsensusLevel.LEVEL_2.scope()).isEqualTo("Single instance");
        assertThat(ConsensusLevel.LEVEL_3.scope()).isEqualTo("All instances");
    }

    @Test
    void shouldHaveAuthorityDescriptions() {
        assertThat(ConsensusLevel.LEVEL_0.authority()).contains("LobeMediator");
        assertThat(ConsensusLevel.LEVEL_3.authority()).contains("2/3 threshold");
    }

    @Test
    void shouldCreateProposal() {
        Proposal p = Proposal.create(ConsensusLevel.LEVEL_1, "cluster-1",
                "mutate", "neuron-42");

        assertThat(p.id()).isNotNull();
        assertThat(p.level()).isEqualTo(ConsensusLevel.LEVEL_1);
        assertThat(p.proposerId()).isEqualTo("cluster-1");
        assertThat(p.action()).isEqualTo("mutate");
        assertThat(p.payload()).isEqualTo("neuron-42");
        assertThat(p.timestamp()).isPositive();
    }

    @Test
    void shouldCreateUniqueProposals() {
        Proposal p1 = Proposal.create(ConsensusLevel.LEVEL_0, "a", "x", "y");
        Proposal p2 = Proposal.create(ConsensusLevel.LEVEL_0, "a", "x", "y");

        assertThat(p1.id()).isNotEqualTo(p2.id());
    }

    @Test
    void shouldCreateApproveVote() {
        UUID proposalId = UUID.randomUUID();
        Vote vote = Vote.approve(proposalId, "voter-1", 0.75);

        assertThat(vote.id()).isNotNull();
        assertThat(vote.proposalId()).isEqualTo(proposalId);
        assertThat(vote.voterId()).isEqualTo("voter-1");
        assertThat(vote.approve()).isTrue();
        assertThat(vote.weight()).isEqualTo(0.75);
        assertThat(vote.timestamp()).isPositive();
    }

    @Test
    void shouldCreateRejectVote() {
        UUID proposalId = UUID.randomUUID();
        Vote vote = Vote.reject(proposalId, "voter-2", 0.5);

        assertThat(vote.id()).isNotNull();
        assertThat(vote.proposalId()).isEqualTo(proposalId);
        assertThat(vote.voterId()).isEqualTo("voter-2");
        assertThat(vote.approve()).isFalse();
        assertThat(vote.weight()).isEqualTo(0.5);
        assertThat(vote.timestamp()).isPositive();
    }

    @Test
    void shouldDistinguishApproveFromReject() {
        UUID pid = UUID.randomUUID();
        Vote approve = Vote.approve(pid, "v", 1.0);
        Vote reject = Vote.reject(pid, "v", 1.0);

        assertThat(approve.approve()).isTrue();
        assertThat(reject.approve()).isFalse();
        assertThat(approve.id()).isNotEqualTo(reject.id());
    }
}
