package io.matrix.federation.liquid;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * W570 — Capability Consensus Engine.
 *
 * Weighted voting where:
 * Vote_final = Sum(Vote_i * Weight_i)
 * Weight = f(Role, Reputation, DomainMatch)
 *
 * Thresholds:
 * - L0-L2 (Infant): cannot vote
 * - L3-L4 (Learner): 0.5 weight + simple majority
 * - L5 (Adult): 0.7 weight + 2/3 majority
 * - L6 (Specialist): 0.85 weight + 3/4 majority
 * - L7 (Guardian): 0.95 weight + unanimous (can veto)
 */
public final class CapabilityConsensusEngine {

    private final LiquidNodeRoleAssigner roleAssigner;
    private final Map<String, ProposalState> proposals = new ConcurrentHashMap<>();
    private final List<ConsensusResult> resultLog = Collections.synchronizedList(new ArrayList<>());

    public record Vote(long nodeId, VoteDecision decision, double confidence, String domain) {}

    public enum VoteDecision { YES, NO, ABSTAIN, VETO }

    public record ProposalState(
            String proposalId,
            Map<Long, Vote> votes,
            long deadlineNs,
            ConsensusStatus status
    ) {}

    public enum ConsensusStatus { PENDING, APPROVED, REJECTED, VETOED }

    public record ConsensusResult(
            String proposalId,
            ConsensusStatus status,
            double totalWeight,
            double yesWeight,
            double noWeight,
            int totalVotes,
            long timestampNs
    ) {}

    public CapabilityConsensusEngine(LiquidNodeRoleAssigner roleAssigner) {
        this.roleAssigner = Objects.requireNonNull(roleAssigner);
    }

    /**
     * Create a new proposal.
     */
    public String createProposal(long deadlineNs) {
        String id = "prop-" + UUID.randomUUID().toString().substring(0, 8);
        proposals.put(id, new ProposalState(id, new ConcurrentHashMap<>(), deadlineNs, ConsensusStatus.PENDING));
        return id;
    }

    /**
     * Cast a vote on a proposal.
     *
     * @return true if vote was accepted
     */
    public boolean vote(String proposalId, long nodeId, VoteDecision decision, double confidence, String domain) {
        ProposalState state = proposals.get(proposalId);
        if (state == null || state.status != ConsensusStatus.PENDING) return false;

        NodeRole role = roleAssigner.getRole(nodeId);
        if (!role.canVote()) return false;  // Infants and Learners cannot vote

        // Only Guardians can VETO
        if (decision == VoteDecision.VETO && !role.hasVetoPower()) return false;

        Vote vote = new Vote(nodeId, decision, confidence, domain);
        state.votes.put(nodeId, vote);
        return true;
    }

    /**
     * Evaluate a proposal and return the result.
     */
    public ConsensusResult evaluate(String proposalId) {
        ProposalState state = proposals.get(proposalId);
        if (state == null) {
            return new ConsensusResult(proposalId, ConsensusStatus.REJECTED, 0, 0, 0, 0, System.nanoTime());
        }

        double totalWeight = 0;
        double yesWeight = 0;
        double noWeight = 0;
        boolean hasVeto = false;

        for (Vote vote : state.votes.values()) {
            double weight = roleAssigner.getVotingWeight(vote.nodeId);
            totalWeight += weight;

            switch (vote.decision) {
                case YES -> yesWeight += weight;
                case NO -> noWeight += weight;
                case VETO -> hasVeto = true;
                case ABSTAIN -> { /* no weight added */ }
            }
        }

        ConsensusStatus status;
        if (hasVeto) {
            status = ConsensusStatus.VETOED;
        } else if (totalWeight == 0) {
            status = ConsensusStatus.REJECTED;
        } else {
            // Determine threshold based on highest participating role
            double threshold = getThreshold(state.votes.keySet());
            double yesRatio = yesWeight / totalWeight;
            status = (yesRatio >= threshold) ? ConsensusStatus.APPROVED : ConsensusStatus.REJECTED;
        }

        // Update state
        ProposalState updated = new ProposalState(state.proposalId, state.votes, state.deadlineNs, status);
        proposals.put(proposalId, updated);

        ConsensusResult result = new ConsensusResult(proposalId, status, totalWeight, yesWeight, noWeight, state.votes.size(), System.nanoTime());
        resultLog.add(result);
        return result;
    }

    /**
     * Get the voting threshold based on participating roles.
     */
    private double getThreshold(Set<Long> participatingNodes) {
        boolean hasGuardian = false;
        boolean hasSpecialist = false;
        boolean hasAdult = false;

        for (long nodeId : participatingNodes) {
            NodeRole role = roleAssigner.getRole(nodeId);
            if (role == NodeRole.GUARDIAN) hasGuardian = true;
            if (role == NodeRole.SPECIALIST) hasSpecialist = true;
            if (role == NodeRole.ADULT) hasAdult = true;
        }

        // Higher roles require higher thresholds
        if (hasGuardian) return 0.95;      // Near-unanimous
        if (hasSpecialist) return 0.75;    // 3/4 majority
        if (hasAdult) return 0.67;         // 2/3 majority
        return 0.5;                        // Simple majority
    }

    /**
     * Get a proposal state.
     */
    public ProposalState getProposal(String proposalId) {
        return proposals.get(proposalId);
    }

    /**
     * Get consensus result log.
     */
    public List<ConsensusResult> getResultLog() {
        return Collections.unmodifiableList(resultLog);
    }

    /**
     * Get number of active proposals.
     */
    public int getActiveProposalCount() {
        return (int) proposals.values().stream()
                .filter(p -> p.status == ConsensusStatus.PENDING)
                .count();
    }
}
