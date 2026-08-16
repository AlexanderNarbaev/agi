package io.matrix.consensus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Weighted voting system with configurable weighting strategies.
 *
 * <p>Supports multiple weighting strategies:
 * <ul>
 * <li>{@link WeightStrategy#LINEAR} — weight proportional to confidence</li>
 * <li>{@link WeightStrategy#CONFIDENCE_BASED} — exponential confidence scaling</li>
 * <li>{@link WeightStrategy#LOGARITHMIC} — logarithmic dampening of high weights</li>
 * <li>{@link WeightStrategy#RANKED} — rank-based weights (1/n, 2/n, ...)</li>
 * </ul>
 *
 * <p>Includes tie-breaking via confidence ranking and full audit trail.
 *
 * <p>Thread-safe: all mutable state uses concurrent data structures.
 *
 * <p>Ref: L2_Iteraction_protocol.md §6.2, §6.3
 */
public final class WeightedVoting {

    public enum WeightStrategy {
        LINEAR, CONFIDENCE_BASED, LOGARITHMIC, RANKED
    }

    public enum VoteDecision {
        APPROVED, REJECTED, TIED
    }

    public record WeightedVote(
            UUID id,
            UUID proposalId,
            String voterId,
            boolean approve,
            double rawConfidence,
            double computedWeight,
            long timestamp
    ) {}

    public record VotingResult(
            VoteDecision decision,
            double approveWeight,
            double rejectWeight,
            double totalWeight,
            String tieBreakWinner,
            List<WeightedVote> votes,
            List<String> auditTrail
    ) {
        public boolean isApproved() { return decision == VoteDecision.APPROVED; }
    }

    private static final double APPROVAL_THRESHOLD = 0.5;

    private final WeightStrategy strategy;
    private final CopyOnWriteArrayList<String> auditTrail;
    private final ConcurrentHashMap<UUID, List<WeightedVote>> votesByProposal;

    public WeightedVoting() {
        this(WeightStrategy.LINEAR);
    }

    public WeightedVoting(WeightStrategy strategy) {
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.auditTrail = new CopyOnWriteArrayList<>();
        this.votesByProposal = new ConcurrentHashMap<>();
    }

    /**
     * Returns the current weighting strategy.
     */
    public WeightStrategy strategy() { return strategy; }

    /**
     * Casts a weighted vote on a proposal.
     *
     * @param proposalId the proposal to vote on
     * @param voterId the voter's identifier
     * @param approve whether the vote is approval or rejection
     * @param confidence the voter's confidence [0..1]
     * @return the weighted vote created
     */
    public WeightedVote castVote(UUID proposalId, String voterId,
                                  boolean approve, double confidence) {
        double clampedConfidence = clamp(confidence);
        double computedWeight = computeWeight(clampedConfidence);

        WeightedVote vote = new WeightedVote(
                UUID.randomUUID(), proposalId, voterId,
                approve, clampedConfidence, computedWeight,
                System.currentTimeMillis());

        votesByProposal.computeIfAbsent(proposalId, k -> new CopyOnWriteArrayList<>())
                .add(vote);

        audit("VOTE:" + voterId + " on " + proposalId
                + " approve=" + approve
                + " confidence=" + String.format("%.3f", clampedConfidence)
                + " weight=" + String.format("%.3f", computedWeight));

        return vote;
    }

    /**
     * Evaluates all votes on a proposal and returns the result.
     *
     * @param proposalId the proposal to evaluate
     * @return the voting result with tie-breaking and audit trail
     */
    public VotingResult evaluate(UUID proposalId) {
        List<WeightedVote> votes = votesByProposal.getOrDefault(proposalId, List.of());

        double approveWeight = 0;
        double rejectWeight = 0;
        double maxApproveConfidence = 0;
        double maxRejectConfidence = 0;
        String maxApproveVoter = null;
        String maxRejectVoter = null;

        for (WeightedVote vote : votes) {
            if (vote.approve()) {
                approveWeight += vote.computedWeight();
                if (vote.rawConfidence() > maxApproveConfidence) {
                    maxApproveConfidence = vote.rawConfidence();
                    maxApproveVoter = vote.voterId();
                }
            } else {
                rejectWeight += vote.computedWeight();
                if (vote.rawConfidence() > maxRejectConfidence) {
                    maxRejectConfidence = vote.rawConfidence();
                    maxRejectVoter = vote.voterId();
                }
            }
        }

        double totalWeight = approveWeight + rejectWeight;
        VoteDecision decision;
        String tieBreakWinner = null;

        if (totalWeight == 0) {
            decision = VoteDecision.TIED;
        } else if (Math.abs(approveWeight - rejectWeight) < 1e-9) {
            decision = VoteDecision.TIED;
            if (maxApproveConfidence > maxRejectConfidence) {
                decision = VoteDecision.APPROVED;
                tieBreakWinner = maxApproveVoter;
                audit("TIE_BREAK:approve wins by confidence " + maxApproveVoter);
            } else if (maxRejectConfidence > maxApproveConfidence) {
                decision = VoteDecision.REJECTED;
                tieBreakWinner = maxRejectVoter;
                audit("TIE_BREAK:reject wins by confidence " + maxRejectVoter);
            }
        } else if (approveWeight / totalWeight > APPROVAL_THRESHOLD) {
            decision = VoteDecision.APPROVED;
        } else {
            decision = VoteDecision.REJECTED;
        }

        audit("EVALUATE:" + proposalId + " → " + decision
                + " approve=" + String.format("%.3f", approveWeight)
                + " reject=" + String.format("%.3f", rejectWeight));

        return new VotingResult(decision, approveWeight, rejectWeight,
                totalWeight, tieBreakWinner, List.copyOf(votes), List.copyOf(auditTrail));
    }

    /**
     * Returns all votes for a proposal.
     */
    public List<WeightedVote> getVotes(UUID proposalId) {
        return List.copyOf(votesByProposal.getOrDefault(proposalId, List.of()));
    }

    /**
     * Returns the full audit trail.
     */
    public List<String> auditTrail() { return List.copyOf(auditTrail); }

    /**
     * Clears all votes and audit trail.
     */
    public void clear() {
        votesByProposal.clear();
        auditTrail.clear();
    }

    /**
     * Computes the effective weight based on the configured strategy.
     */
    double computeWeight(double confidence) {
        return switch (strategy) {
            case LINEAR -> confidence;
            case CONFIDENCE_BASED -> Math.pow(confidence, 2);
            case LOGARITHMIC -> confidence > 0
                    ? Math.log1p(confidence * 9) / Math.log(10)
                    : 0.0;
            case RANKED -> confidence;
        };
    }

    /**
     * Computes ranked weights based on confidence ordering.
     * Highest confidence gets weight n/n, next gets (n-1)/n, etc.
     *
     * @param confidences list of confidence values
     * @return list of ranked weights in same order
     */
    public static List<Double> computeRankedWeights(List<Double> confidences) {
        int n = confidences.size();
        if (n == 0) return List.of();

        List<Map.Entry<Integer, Double>> indexed = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            indexed.add(Map.entry(i, confidences.get(i)));
        }
        indexed.sort(Comparator.comparingDouble(Map.Entry::getValue));

        double[] weights = new double[n];
        for (int rank = 0; rank < n; rank++) {
            weights[indexed.get(rank).getKey()] = (double) (rank + 1) / n;
        }

        List<Double> result = new ArrayList<>(n);
        for (double w : weights) {
            result.add(w);
        }
        return result;
    }

    private void audit(String entry) {
        auditTrail.add("[" + System.currentTimeMillis() + "] " + entry);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
