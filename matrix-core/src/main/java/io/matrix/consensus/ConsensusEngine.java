package io.matrix.consensus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Proof-of-Accuracy consensus engine with multi-strategy support.
 *
 * <p>Manages proposals, collects votes, and decides by configurable thresholds.
 * Decisions are classified by {@link ConsensusLevel} impact scope.
 *
 * <p>Supports multiple consensus strategies:
 * <ul>
 * <li>{@link ConsensusStrategy#SIMPLE_MAJORITY} — original 2/3 supermajority</li>
 * <li>{@link ConsensusStrategy#WEIGHTED} — weighted voting based on agent confidence</li>
 * <li>{@link ConsensusStrategy#DEBATE} — adversarial debate protocol</li>
 * </ul>
 *
 * <p>Includes tie-breaking mechanism via confidence ranking.
 *
 * <p>Ref: L2_Iteraction_protocol.md §6.3, §6.4
 */
public final class ConsensusEngine {

    public enum Decision {
        PENDING, APPROVED, REJECTED, TIMED_OUT
    }

    /**
     * Consensus strategy selection.
     */
    public enum ConsensusStrategy {
        SIMPLE_MAJORITY,
        WEIGHTED,
        DEBATE
    }

    private static final double APPROVAL_THRESHOLD = 2.0 / 3.0;

    private final Map<UUID, Proposal> proposals = new HashMap<>();
    private final Map<UUID, List<Vote>> votes = new HashMap<>();
    private final Map<UUID, Decision> decisions = new HashMap<>();
    private final List<String> eventLog = new ArrayList<>();
    private final WeightedVoting weightedVoting;
    private final DebateProtocol debateProtocol;
    private ConsensusStrategy activeStrategy;

    public ConsensusEngine() {
        this(ConsensusStrategy.SIMPLE_MAJORITY);
    }

    public ConsensusEngine(ConsensusStrategy strategy) {
        this.activeStrategy = strategy;
        this.weightedVoting = new WeightedVoting(WeightedVoting.WeightStrategy.LINEAR);
        this.debateProtocol = new DebateProtocol();
    }

    /**
     * Returns the active consensus strategy.
     */
    public ConsensusStrategy activeStrategy() { return activeStrategy; }

    /**
     * Sets the active consensus strategy.
     */
    public void setStrategy(ConsensusStrategy strategy) {
        this.activeStrategy = strategy;
    }

    /**
     * Returns the underlying weighted voting system.
     */
    public WeightedVoting weightedVoting() { return weightedVoting; }

    /**
     * Returns the underlying debate protocol.
     */
    public DebateProtocol debateProtocol() { return debateProtocol; }

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
                + " level=" + proposal.level() + " strategy=" + activeStrategy);
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
     * Evaluates a proposal using the active strategy and returns the decision.
     */
    public Decision evaluate(UUID proposalId) {
        return switch (activeStrategy) {
            case SIMPLE_MAJORITY -> evaluateSimpleMajority(proposalId);
            case WEIGHTED -> evaluateWeighted(proposalId);
            case DEBATE -> evaluateDebate(proposalId);
        };
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

    /**
     * Runs a debate-based consensus on a proposal with the given agents.
     *
     * @param proposalId the proposal to debate
     * @param debateAgents the agents participating in the debate
     * @return the decision
     */
    public Decision runDebate(UUID proposalId, List<DebateAgent> debateAgents) {
        if (!proposals.containsKey(proposalId)) {
            throw new IllegalArgumentException("Unknown proposal: " + proposalId);
        }

        DebateProtocol protocol = new DebateProtocol();
        for (DebateAgent agent : debateAgents) {
            protocol.addAgent(agent);
        }

        DebateProtocol.DebateResult result = protocol.runDebate();

        Decision decision;
        if (result.isConsensus()) {
            decision = Decision.APPROVED;
        } else {
            decision = Decision.REJECTED;
        }

        decisions.put(proposalId, decision);
        eventLog.add("DEBATE:" + proposalId + " → " + decision
                + " rounds=" + result.totalRounds()
                + " state=" + result.state());
        return decision;
    }

    /**
     * Resolves a tie using confidence-based tie-breaking.
     *
     * @param proposalId the tied proposal
     * @param tiedVotes the tied votes to break
     * @return the tie-breaking decision
     */
    public Decision breakTie(UUID proposalId, List<Vote> tiedVotes) {
        double maxApproveConfidence = 0;
        double maxRejectConfidence = 0;

        for (Vote vote : tiedVotes) {
            if (vote.approve() && vote.weight() > maxApproveConfidence) {
                maxApproveConfidence = vote.weight();
            } else if (!vote.approve() && vote.weight() > maxRejectConfidence) {
                maxRejectConfidence = vote.weight();
            }
        }

        Decision decision = maxApproveConfidence >= maxRejectConfidence
                ? Decision.APPROVED : Decision.REJECTED;

        decisions.put(proposalId, decision);
        eventLog.add("TIE_BREAK:" + proposalId + " → " + decision
                + " approveConfidence=" + String.format("%.3f", maxApproveConfidence)
                + " rejectConfidence=" + String.format("%.3f", maxRejectConfidence));
        return decision;
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

    /**
     * Simple majority evaluation (original 2/3 supermajority).
     */
    private Decision evaluateSimpleMajority(UUID proposalId) {
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
        } else if (Math.abs(approveWeight - (totalWeight - approveWeight)) < 1e-9) {
            result = breakTie(proposalId, proposalVotes);
        } else {
            result = Decision.REJECTED;
        }

        if (result != Decision.PENDING) {
            decisions.put(proposalId, result);
        }
        eventLog.add("DECIDE:" + proposalId + " → " + result
                + " approve=" + String.format("%.3f", approveWeight)
                + " total=" + String.format("%.3f", totalWeight));
        return result;
    }

    /**
     * Weighted voting evaluation.
     */
    private Decision evaluateWeighted(UUID proposalId) {
        Proposal proposal = proposals.get(proposalId);
        if (proposal == null) {
            throw new IllegalArgumentException("Unknown proposal: " + proposalId);
        }

        Decision current = decisions.get(proposalId);
        if (current != Decision.PENDING) {
            return current;
        }

        List<Vote> proposalVotes = votes.get(proposalId);
        for (Vote vote : proposalVotes) {
            weightedVoting.castVote(proposalId, vote.voterId(),
                    vote.approve(), vote.weight());
        }

        WeightedVoting.VotingResult result = weightedVoting.evaluate(proposalId);

        Decision decision = switch (result.decision()) {
            case APPROVED -> Decision.APPROVED;
            case REJECTED -> Decision.REJECTED;
            case TIED -> Decision.REJECTED;
        };

        decisions.put(proposalId, decision);
        eventLog.add("WEIGHTED_DECIDE:" + proposalId + " → " + decision
                + " approveWeight=" + String.format("%.3f", result.approveWeight())
                + " rejectWeight=" + String.format("%.3f", result.rejectWeight()));
        return decision;
    }

    /**
     * Debate-based evaluation.
     */
    private Decision evaluateDebate(UUID proposalId) {
        Proposal proposal = proposals.get(proposalId);
        if (proposal == null) {
            throw new IllegalArgumentException("Unknown proposal: " + proposalId);
        }

        Decision current = decisions.get(proposalId);
        if (current != Decision.PENDING) {
            return current;
        }

        List<Vote> proposalVotes = votes.get(proposalId);
        if (proposalVotes.isEmpty()) {
            return Decision.PENDING;
        }

        List<DebateAgent> agents = new ArrayList<>();
        for (Vote vote : proposalVotes) {
            DebateAgent agent = new DebateAgent(
                    vote.voterId(),
                    vote.approve() ? "APPROVE" : "REJECT",
                    vote.weight());
            agents.add(agent);
        }

        return runDebate(proposalId, agents);
    }
}
