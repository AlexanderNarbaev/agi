package io.matrix.consensus;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WeightedVotingTest {

    @Test
    void shouldDefaultToLinearStrategy() {
        WeightedVoting voting = new WeightedVoting();

        assertThat(voting.strategy()).isEqualTo(WeightedVoting.WeightStrategy.LINEAR);
    }

    @Test
    void shouldSupportConfidenceBasedStrategy() {
        WeightedVoting voting = new WeightedVoting(WeightedVoting.WeightStrategy.CONFIDENCE_BASED);

        assertThat(voting.strategy()).isEqualTo(WeightedVoting.WeightStrategy.CONFIDENCE_BASED);
    }

    @Test
    void shouldSupportLogarithmicStrategy() {
        WeightedVoting voting = new WeightedVoting(WeightedVoting.WeightStrategy.LOGARITHMIC);

        assertThat(voting.strategy()).isEqualTo(WeightedVoting.WeightStrategy.LOGARITHMIC);
    }

    @Test
    void shouldSupportRankedStrategy() {
        WeightedVoting voting = new WeightedVoting(WeightedVoting.WeightStrategy.RANKED);

        assertThat(voting.strategy()).isEqualTo(WeightedVoting.WeightStrategy.RANKED);
    }

    @Test
    void shouldCastVoteWithLinearWeight() {
        WeightedVoting voting = new WeightedVoting(WeightedVoting.WeightStrategy.LINEAR);
        UUID proposalId = UUID.randomUUID();

        WeightedVoting.WeightedVote vote = voting.castVote(proposalId, "voter-1", true, 0.8);

        assertThat(vote.voterId()).isEqualTo("voter-1");
        assertThat(vote.approve()).isTrue();
        assertThat(vote.rawConfidence()).isCloseTo(0.8, org.assertj.core.data.Offset.offset(0.01));
        assertThat(vote.computedWeight()).isCloseTo(0.8, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void shouldCastVoteWithConfidenceBasedWeight() {
        WeightedVoting voting = new WeightedVoting(WeightedVoting.WeightStrategy.CONFIDENCE_BASED);
        UUID proposalId = UUID.randomUUID();

        WeightedVoting.WeightedVote vote = voting.castVote(proposalId, "voter-1", true, 0.8);

        assertThat(vote.computedWeight()).isCloseTo(0.64, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void shouldCastVoteWithLogarithmicWeight() {
        WeightedVoting voting = new WeightedVoting(WeightedVoting.WeightStrategy.LOGARITHMIC);
        UUID proposalId = UUID.randomUUID();

        WeightedVoting.WeightedVote vote = voting.castVote(proposalId, "voter-1", true, 0.5);

        assertThat(vote.computedWeight()).isGreaterThan(0.0);
        assertThat(vote.computedWeight()).isLessThan(1.0);
    }

    @Test
    void shouldApproveWhenMajorityApproves() {
        WeightedVoting voting = new WeightedVoting();
        UUID proposalId = UUID.randomUUID();

        voting.castVote(proposalId, "v1", true, 0.8);
        voting.castVote(proposalId, "v2", true, 0.7);
        voting.castVote(proposalId, "v3", false, 0.5);

        WeightedVoting.VotingResult result = voting.evaluate(proposalId);

        assertThat(result.isApproved()).isTrue();
        assertThat(result.approveWeight()).isGreaterThan(result.rejectWeight());
    }

    @Test
    void shouldRejectWhenMajorityRejects() {
        WeightedVoting voting = new WeightedVoting();
        UUID proposalId = UUID.randomUUID();

        voting.castVote(proposalId, "v1", false, 0.8);
        voting.castVote(proposalId, "v2", false, 0.7);
        voting.castVote(proposalId, "v3", true, 0.5);

        WeightedVoting.VotingResult result = voting.evaluate(proposalId);

        assertThat(result.decision()).isEqualTo(WeightedVoting.VoteDecision.REJECTED);
    }

    @Test
    void shouldApproveWhenApproveWeightHigher() {
        WeightedVoting voting = new WeightedVoting();
        UUID proposalId = UUID.randomUUID();

        voting.castVote(proposalId, "v1", true, 0.9);
        voting.castVote(proposalId, "v2", false, 0.5);

        WeightedVoting.VotingResult result = voting.evaluate(proposalId);

        assertThat(result.isApproved()).isTrue();
        assertThat(result.approveWeight()).isGreaterThan(result.rejectWeight());
    }

    @Test
    void shouldRejectWhenRejectWeightHigher() {
        WeightedVoting voting = new WeightedVoting();
        UUID proposalId = UUID.randomUUID();

        voting.castVote(proposalId, "v1", true, 0.3);
        voting.castVote(proposalId, "v2", false, 0.9);

        WeightedVoting.VotingResult result = voting.evaluate(proposalId);

        assertThat(result.decision()).isEqualTo(WeightedVoting.VoteDecision.REJECTED);
        assertThat(result.rejectWeight()).isGreaterThan(result.approveWeight());
    }

    @Test
    void shouldBreakTieWhenConfidenceDiffers() {
        WeightedVoting voting = new WeightedVoting(WeightedVoting.WeightStrategy.CONFIDENCE_BASED);
        UUID proposalId = UUID.randomUUID();

        voting.castVote(proposalId, "v1", true, 0.7);
        voting.castVote(proposalId, "v2", false, 0.7);
        voting.castVote(proposalId, "v3", true, 0.9);

        WeightedVoting.VotingResult result = voting.evaluate(proposalId);

        assertThat(result.isApproved()).isTrue();
    }

    @Test
    void shouldReturnTiedWhenNoVotes() {
        WeightedVoting voting = new WeightedVoting();
        UUID proposalId = UUID.randomUUID();

        WeightedVoting.VotingResult result = voting.evaluate(proposalId);

        assertThat(result.decision()).isEqualTo(WeightedVoting.VoteDecision.TIED);
    }

    @Test
    void shouldReturnVotesForProposal() {
        WeightedVoting voting = new WeightedVoting();
        UUID proposalId = UUID.randomUUID();

        voting.castVote(proposalId, "v1", true, 0.8);
        voting.castVote(proposalId, "v2", false, 0.6);

        List<WeightedVoting.WeightedVote> votes = voting.getVotes(proposalId);

        assertThat(votes).hasSize(2);
    }

    @Test
    void shouldReturnEmptyVotesForUnknownProposal() {
        WeightedVoting voting = new WeightedVoting();

        List<WeightedVoting.WeightedVote> votes = voting.getVotes(UUID.randomUUID());

        assertThat(votes).isEmpty();
    }

    @Test
    void shouldMaintainAuditTrail() {
        WeightedVoting voting = new WeightedVoting();
        UUID proposalId = UUID.randomUUID();

        voting.castVote(proposalId, "v1", true, 0.8);
        voting.evaluate(proposalId);

        List<String> trail = voting.auditTrail();

        assertThat(trail).isNotEmpty();
        assertThat(trail.stream().anyMatch(e -> e.contains("VOTE"))).isTrue();
        assertThat(trail.stream().anyMatch(e -> e.contains("EVALUATE"))).isTrue();
    }

    @Test
    void shouldClearAllVotes() {
        WeightedVoting voting = new WeightedVoting();
        UUID proposalId = UUID.randomUUID();

        voting.castVote(proposalId, "v1", true, 0.8);
        voting.clear();

        assertThat(voting.getVotes(proposalId)).isEmpty();
        assertThat(voting.auditTrail()).isEmpty();
    }

    @Test
    void shouldComputeLinearWeight() {
        WeightedVoting voting = new WeightedVoting(WeightedVoting.WeightStrategy.LINEAR);

        assertThat(voting.computeWeight(0.0)).isEqualTo(0.0);
        assertThat(voting.computeWeight(0.5)).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.01));
        assertThat(voting.computeWeight(1.0)).isEqualTo(1.0);
    }

    @Test
    void shouldComputeConfidenceBasedWeight() {
        WeightedVoting voting = new WeightedVoting(WeightedVoting.WeightStrategy.CONFIDENCE_BASED);

        assertThat(voting.computeWeight(0.0)).isEqualTo(0.0);
        assertThat(voting.computeWeight(0.5)).isCloseTo(0.25, org.assertj.core.data.Offset.offset(0.01));
        assertThat(voting.computeWeight(1.0)).isEqualTo(1.0);
    }

    @Test
    void shouldComputeLogarithmicWeight() {
        WeightedVoting voting = new WeightedVoting(WeightedVoting.WeightStrategy.LOGARITHMIC);

        assertThat(voting.computeWeight(0.0)).isEqualTo(0.0);
        assertThat(voting.computeWeight(0.5)).isGreaterThan(0.0);
        assertThat(voting.computeWeight(1.0)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void shouldComputeRankedWeights() {
        List<Double> confidences = List.of(0.3, 0.9, 0.6);
        List<Double> weights = WeightedVoting.computeRankedWeights(confidences);

        assertThat(weights).hasSize(3);
        assertThat(weights.get(0)).isCloseTo(1.0 / 3, org.assertj.core.data.Offset.offset(0.01));
        assertThat(weights.get(1)).isCloseTo(3.0 / 3, org.assertj.core.data.Offset.offset(0.01));
        assertThat(weights.get(2)).isCloseTo(2.0 / 3, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void shouldHandleEmptyRankedWeights() {
        List<Double> weights = WeightedVoting.computeRankedWeights(List.of());

        assertThat(weights).isEmpty();
    }

    @Test
    void shouldClampConfidenceToRange() {
        WeightedVoting voting = new WeightedVoting();
        UUID proposalId = UUID.randomUUID();

        WeightedVoting.WeightedVote vote = voting.castVote(proposalId, "v1", true, 1.5);

        assertThat(vote.rawConfidence()).isEqualTo(1.0);
    }

    @Test
    void shouldLogTieBreakInAuditTrail() {
        WeightedVoting voting = new WeightedVoting();
        UUID proposalId = UUID.randomUUID();

        voting.castVote(proposalId, "v1", true, 0.5);
        voting.castVote(proposalId, "v2", false, 0.5);
        voting.castVote(proposalId, "v3", true, 0.8);
        voting.evaluate(proposalId);

        assertThat(voting.auditTrail()).isNotEmpty();
        assertThat(voting.auditTrail().stream()
                .anyMatch(e -> e.contains("EVALUATE"))).isTrue();
    }

    @Test
    void shouldRecordTimestampOnVotes() {
        WeightedVoting voting = new WeightedVoting();
        UUID proposalId = UUID.randomUUID();

        WeightedVoting.WeightedVote vote = voting.castVote(proposalId, "v1", true, 0.5);

        assertThat(vote.timestamp()).isPositive();
    }

    @Test
    void shouldHaveUniqueVoteIds() {
        WeightedVoting voting = new WeightedVoting();
        UUID proposalId = UUID.randomUUID();

        WeightedVoting.WeightedVote v1 = voting.castVote(proposalId, "v1", true, 0.5);
        WeightedVoting.WeightedVote v2 = voting.castVote(proposalId, "v2", true, 0.5);

        assertThat(v1.id()).isNotEqualTo(v2.id());
    }
}
