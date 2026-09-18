package io.matrix.federation.consensus;

import io.matrix.federation.proto.CapabilityLevel;
import io.matrix.federation.proto.ConsensusProposal;
import io.matrix.federation.proto.ConsensusStatus;
import io.matrix.federation.proto.ConsensusVote;
import io.matrix.federation.proto.NodeType;
import io.matrix.federation.proto.VoteDecision;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * W359 — Local consensus stub (single-node evaluator).
 * 
 * Computes consensus for a proposal based on collected votes.
 * Uses capability-weighted voting per DESIGN-67.
 * 
 * Per SPEC-013 R1 (consensus-only CRUD).
 * Per CONSTITUTION I v3: seeded Random used for tie-breaking.
 * Per CONSTITUTION II: voting weights are formal.
 */
public final class LocalConsensusEngine {
    
    /**
     * Standard proposal threshold (67% weighted approval).
     */
    public static final double STANDARD_THRESHOLD = 0.67;
    
    /**
     * Critical (safety) proposal threshold (80%).
     */
    public static final double CRITICAL_THRESHOLD = 0.80;
    
    /**
     * Emergency proposal threshold (50% + 2x L7 confirmation).
     */
    public static final double EMERGENCY_THRESHOLD = 0.50;
    
    /** Voting weight by capability level (L0-L1 = 0, no vote) */
    private static final Map<Integer, Double> VOTING_WEIGHTS = new HashMap<>();
    static {
        VOTING_WEIGHTS.put(0, 0.0);  // CAPABILITY_UNSPECIFIED
        VOTING_WEIGHTS.put(1, 0.0);  // L0_INFANT
        VOTING_WEIGHTS.put(2, 0.0);  // L1_LEARNER
        VOTING_WEIGHTS.put(3, 1.0);  // L2_ADULT
        VOTING_WEIGHTS.put(4, 1.5);  // L3_SPECIALIST
        VOTING_WEIGHTS.put(5, 2.0);  // L4_EXPERT
        VOTING_WEIGHTS.put(6, 3.0);  // L5_MASTER
        VOTING_WEIGHTS.put(7, 5.0);  // L6_ARCHITECT
        VOTING_WEIGHTS.put(8, 10.0); // L7_GUARDIAN + VETO
    }
    
    private final Random rng;
    private final List<ConsensusVote> votes = new ArrayList<>();
    
    public LocalConsensusEngine(long seed) {
        this.rng = new Random(seed);
    }
    
    /**
     * Add a vote for the current proposal.
     */
    public void addVote(ConsensusVote vote) {
        if (vote == null) {
            throw new IllegalArgumentException("vote cannot be null");
        }
        votes.add(vote);
        // Note: L7 confirmation tracking removed - the weight-based proxy
        // was spoofable by setting reputationWeight >= 10 on a non-L7 vote.
        // Emergency threshold feature deferred until voter ID is added.
    }
    
    /**
     * Compute consensus result based on collected votes.
     */
    public ConsensusResult evaluate(ConsensusProposal proposal) {
        if (votes.isEmpty()) {
            return new ConsensusResult(ConsensusStatus.CONSENSUS_PENDING, 0.0, 0.0, 0, 0);
        }
        
        double totalWeight = 0.0;
        double yesWeight = 0.0;
        double noWeight = 0.0;
        int vetoCount = 0;
        
        for (ConsensusVote v : votes) {
            double w = v.getReputationWeight();
            
            switch (v.getDecision()) {
                case VOTE_YES:
                    yesWeight += w;
                    totalWeight += w;
                    break;
                case VOTE_NO:
                    noWeight += w;
                    totalWeight += w;
                    break;
                case VOTE_VETO:
                    vetoCount++;
                    // VETO has weight but does not affect yes/no/total ratio
                    break;
                case VOTE_ABSTAIN:
                default:
                    // ABSTAIN does not contribute to weight
                    break;
            }
        }
        
        // Veto always wins
        if (vetoCount > 0) {
            return new ConsensusResult(
                ConsensusStatus.CONSENSUS_VETOED,
                yesWeight, noWeight, votes.size(), vetoCount);
        }
        
        // Determine threshold based on proposal type
        double threshold = STANDARD_THRESHOLD;
        // In future, check proposal.getType() for critical/emergency
        // For now use standard
        
        double yesRatio = totalWeight > 0 ? yesWeight / totalWeight : 0.0;
        
        ConsensusStatus status;
        if (yesRatio >= threshold) {
            status = ConsensusStatus.CONSENSUS_APPROVED;
        } else {
            status = ConsensusStatus.CONSENSUS_REJECTED;
        }
        
        return new ConsensusResult(status, yesWeight, noWeight, votes.size(), 0);
    }
    
    /**
     * Get voting weight for a capability level.
     */
    public static double getVotingWeight(CapabilityLevel level) {
        return VOTING_WEIGHTS.getOrDefault(level.getNumber(), 0.0);
    }
    
    /**
     * Create a vote for a node at a capability level.
     */
    public ConsensusVote createVote(String proposalId, VoteDecision decision, 
                                     CapabilityLevel level, double confidence) {
        double weight = getVotingWeight(level);
        return ConsensusVote.newBuilder()
            .setProposalId(proposalId)
            .setDecision(decision)
            .setConfidence((float) confidence)
            .setReputationWeight((float) weight)
            .setVotedAtNs(System.nanoTime())
            .build();
    }
    
    /**
     * Get number of votes collected.
     */
    public int getVoteCount() {
        return votes.size();
    }
    
    /**
     * Get number of L7 confirmations (for emergency threshold).
     */

    /**
     * Reset votes (start new proposal).
     */
    public void reset() {
        votes.clear();
    }
    
    /**
     * Result of consensus evaluation.
     */
    public record ConsensusResult(
        ConsensusStatus status,
        double yesWeight,
        double noWeight,
        int voteCount,
        int vetoCount
    ) {
        public boolean approved() {
            return status == ConsensusStatus.CONSENSUS_APPROVED;
        }
        
        public double approvalRatio() {
            double total = yesWeight + noWeight;
            return total > 0 ? yesWeight / total : 0.0;
        }
    }
}
