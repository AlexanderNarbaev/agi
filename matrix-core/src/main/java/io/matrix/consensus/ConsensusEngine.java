package io.matrix.consensus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Proof-of-Accuracy consensus engine.
 *
 * <p>Manages proposals, collects votes, and decides by 2/3 supermajority.
 * Decisions are classified by {@link ConsensusLevel} impact scope.
 *
 * <p>Ref: L2_Iteraction_protocol.md §6.3, §6.4
 */
public final class ConsensusEngine {

    public enum Decision {
        PENDING, APPROVED, REJECTED, TIMED_OUT
    }

    private static final double APPROVAL_THRESHOLD = 2.0 / 3.0;

    private final Map<UUID, Proposal> proposals = new HashMap<>();
    private final Map<UUID, List<Vote>> votes = new HashMap<>();
    private final Map<UUID, Decision> decisions = new HashMap<>();
    private final List<String> eventLog = new ArrayList<>();

    /**
     * Submits a proposal for voting.
     *
     * @return the proposal id
     */
    public UUID propose(Proposal proposal) {
        proposals.put(proposal.id(), proposal);
        votes.put(proposal.id(), new ArrayList<>());
        decisions.put(proposal.id(), Decision.PENDING);
        eventLog.add("PROPOSE:" + proposal.action() + " by " + proposal.proposerId()
                + " level=" + proposal.level());
        return proposal.id();
    }

    /**
     * Casts a vote on a proposal.
     *
     * @throws IllegalArgumentException if the proposal doesn't exist or is already decided
     */
    public void castVote(Vote vote) {
        if (!proposals.containsKey(vote.proposalId())) {
            throw new IllegalArgumentException("Unknown proposal: " + vote.proposalId());
        }
        Decision current = decisions.get(vote.proposalId());
        if (current != Decision.PENDING) {
            throw new IllegalArgumentException("Proposal already decided: "
                    + vote.proposalId());
        }

        votes.get(vote.proposalId()).add(vote);
        eventLog.add("VOTE:" + vote.voterId() + " on " + vote.proposalId()
                + " approve=" + vote.approve() + " weight="
                + String.format("%.3f", vote.weight()));
    }

    /**
     * Evaluates a proposal and returns the decision.
     */
    public Decision evaluate(UUID proposalId) {
        Proposal proposal = proposals.get(proposalId);
        if (proposal == null) {
            throw new IllegalArgumentException("Unknown proposal: " + proposalId);
        }

        Decision current = decisions.get(proposalId);
        if (current != Decision.PENDING) {
            return current;
        }

        List<Vote> proposalVotes = votes.get(proposalId);
        double totalWeight = proposalVotes.stream()
                .mapToDouble(Vote::weight).sum();
        double approveWeight = proposalVotes.stream()
                .filter(Vote::approve)
                .mapToDouble(Vote::weight).sum();

        Decision result;
        if (totalWeight == 0) {
            result = Decision.PENDING;
        } else if (approveWeight / totalWeight >= APPROVAL_THRESHOLD) {
            result = Decision.APPROVED;
        } else {
            result = Decision.REJECTED;
        }

        decisions.put(proposalId, result);
        eventLog.add("DECIDE:" + proposalId + " → " + result
                + " approve=" + String.format("%.3f", approveWeight)
                + " total=" + String.format("%.3f", totalWeight));
        return result;
    }

    /**
     * Evaluates all pending proposals.
     */
    public List<UUID> evaluateAll() {
        List<UUID> decided = new ArrayList<>();
        for (UUID id : proposals.keySet()) {
            if (decisions.get(id) == Decision.PENDING) {
                Decision result = evaluate(id);
                if (result != Decision.PENDING) {
                    decided.add(id);
                }
            }
        }
        return decided;
    }

    public Proposal getProposal(UUID id) { return proposals.get(id); }

    public List<Vote> getVotes(UUID id) {
        return List.copyOf(votes.getOrDefault(id, List.of()));
    }

    public Decision getDecision(UUID id) {
        return decisions.getOrDefault(id, Decision.PENDING);
    }

    public List<String> eventLog() { return List.copyOf(eventLog); }

    public int proposalCount() { return proposals.size(); }

    /**
     * Quick approval check for single-voter scenarios (Level 0).
     */
    public boolean approveLocally(String proposerId, String action, String payload) {
        Proposal prop = Proposal.create(ConsensusLevel.LEVEL_0, proposerId, action, payload);
        UUID id = propose(prop);
        castVote(Vote.approve(id, proposerId, 1.0));
        return evaluate(id) == Decision.APPROVED;
    }
}
