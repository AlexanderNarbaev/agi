package io.matrix.federation.consensus;

import io.matrix.federation.proto.CapabilityLevel;
import io.matrix.federation.proto.ConsensusProposal;
import io.matrix.federation.proto.ConsensusStatus;
import io.matrix.federation.proto.ConsensusVote;
import io.matrix.federation.proto.VoteDecision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W359 — Tests for LocalConsensusEngine.
 */
class LocalConsensusEngineTest {
    
    @Test
    void testVotingWeights() {
        // Per DESIGN-67: L0-L1 = 0, L2 = 1.0, ..., L7 = 10.0
        assertEquals(0.0, LocalConsensusEngine.getVotingWeight(CapabilityLevel.CAPABILITY_L0_INFANT));
        assertEquals(0.0, LocalConsensusEngine.getVotingWeight(CapabilityLevel.CAPABILITY_L1_LEARNER));
        assertEquals(1.0, LocalConsensusEngine.getVotingWeight(CapabilityLevel.CAPABILITY_L2_ADULT));
        assertEquals(1.5, LocalConsensusEngine.getVotingWeight(CapabilityLevel.CAPABILITY_L3_SPECIALIST));
        assertEquals(2.0, LocalConsensusEngine.getVotingWeight(CapabilityLevel.CAPABILITY_L4_EXPERT));
        assertEquals(3.0, LocalConsensusEngine.getVotingWeight(CapabilityLevel.CAPABILITY_L5_MASTER));
        assertEquals(5.0, LocalConsensusEngine.getVotingWeight(CapabilityLevel.CAPABILITY_L6_ARCHITECT));
        assertEquals(10.0, LocalConsensusEngine.getVotingWeight(CapabilityLevel.CAPABILITY_L7_GUARDIAN));
    }
    
    @Test
    void testCreateVote() {
        LocalConsensusEngine engine = new LocalConsensusEngine(42L);
        ConsensusVote vote = engine.createVote("prop-1", VoteDecision.VOTE_YES, 
                                                CapabilityLevel.CAPABILITY_L3_SPECIALIST, 0.9);
        
        assertEquals("prop-1", vote.getProposalId());
        assertEquals(VoteDecision.VOTE_YES, vote.getDecision());
        assertEquals(0.9f, vote.getConfidence(), 0.001f);
        assertEquals(1.5f, vote.getReputationWeight(), 0.001f);
    }
    
    @Test
    void testConsensusApproved() {
        LocalConsensusEngine engine = new LocalConsensusEngine(42L);
        
        // 3 L2 votes (1.0 each) + 1 L3 (1.5) = 4.5 total, all YES
        engine.addVote(engine.createVote("p1", VoteDecision.VOTE_YES, CapabilityLevel.CAPABILITY_L2_ADULT, 1.0));
        engine.addVote(engine.createVote("p1", VoteDecision.VOTE_YES, CapabilityLevel.CAPABILITY_L2_ADULT, 1.0));
        engine.addVote(engine.createVote("p1", VoteDecision.VOTE_YES, CapabilityLevel.CAPABILITY_L2_ADULT, 1.0));
        engine.addVote(engine.createVote("p1", VoteDecision.VOTE_YES, CapabilityLevel.CAPABILITY_L3_SPECIALIST, 1.0));
        
        var result = engine.evaluate(ConsensusProposal.newBuilder().setProposalId("p1").build());
        
        assertEquals(ConsensusStatus.CONSENSUS_APPROVED, result.status());
        assertEquals(4.5, result.yesWeight(), 0.001);
        assertTrue(result.approved());
    }
    
    @Test
    void testConsensusRejected() {
        LocalConsensusEngine engine = new LocalConsensusEngine(42L);
        
        // 1 L2 YES (1.0), 3 L2 NO (3.0) — 75% NO, > 50% threshold for YES
        engine.addVote(engine.createVote("p1", VoteDecision.VOTE_YES, CapabilityLevel.CAPABILITY_L2_ADULT, 1.0));
        engine.addVote(engine.createVote("p1", VoteDecision.VOTE_NO, CapabilityLevel.CAPABILITY_L2_ADULT, 1.0));
        engine.addVote(engine.createVote("p1", VoteDecision.VOTE_NO, CapabilityLevel.CAPABILITY_L2_ADULT, 1.0));
        engine.addVote(engine.createVote("p1", VoteDecision.VOTE_NO, CapabilityLevel.CAPABILITY_L2_ADULT, 1.0));
        
        var result = engine.evaluate(ConsensusProposal.newBuilder().setProposalId("p1").build());
        
        assertEquals(ConsensusStatus.CONSENSUS_REJECTED, result.status());
        assertFalse(result.approved());
    }
    
    @Test
    void testL7VetoAlwaysWins() {
        LocalConsensusEngine engine = new LocalConsensusEngine(42L);
        
        // Many YES votes from L2-L6, but 1 L7 VETO
        for (int i = 0; i < 10; i++) {
            engine.addVote(engine.createVote("p1", VoteDecision.VOTE_YES, 
                CapabilityLevel.CAPABILITY_L3_SPECIALIST, 1.0));
        }
        engine.addVote(engine.createVote("p1", VoteDecision.VOTE_VETO, 
            CapabilityLevel.CAPABILITY_L7_GUARDIAN, 1.0));
        
        var result = engine.evaluate(ConsensusProposal.newBuilder().setProposalId("p1").build());
        
        assertEquals(ConsensusStatus.CONSENSUS_VETOED, result.status());
        assertEquals(1, result.vetoCount());
    }
    
    @Test
    void testEmptyVotes() {
        LocalConsensusEngine engine = new LocalConsensusEngine(42L);
        var result = engine.evaluate(ConsensusProposal.newBuilder().setProposalId("p1").build());
        
        assertEquals(ConsensusStatus.CONSENSUS_PENDING, result.status());
        assertEquals(0, result.voteCount());
    }
    
    @Test
    void testL0L1CannotVote() {
        LocalConsensusEngine engine = new LocalConsensusEngine(42L);
        
        // L0 + L1 votes don't contribute weight
        engine.addVote(engine.createVote("p1", VoteDecision.VOTE_YES, CapabilityLevel.CAPABILITY_L0_INFANT, 1.0));
        engine.addVote(engine.createVote("p1", VoteDecision.VOTE_YES, CapabilityLevel.CAPABILITY_L1_LEARNER, 1.0));
        engine.addVote(engine.createVote("p1", VoteDecision.VOTE_NO, CapabilityLevel.CAPABILITY_L2_ADULT, 1.0));
        
        var result = engine.evaluate(ConsensusProposal.newBuilder().setProposalId("p1").build());
        
        // 0 YES, 1.0 NO → REJECTED
        assertEquals(ConsensusStatus.CONSENSUS_REJECTED, result.status());
    }
    
    @Test
    void testApprovalRatio() {
        LocalConsensusEngine engine = new LocalConsensusEngine(42L);
        
        // 2 YES (1.0 each), 1 NO (2.0) → 2/4 = 0.5 ratio
        engine.addVote(engine.createVote("p1", VoteDecision.VOTE_YES, CapabilityLevel.CAPABILITY_L2_ADULT, 1.0));
        engine.addVote(engine.createVote("p1", VoteDecision.VOTE_YES, CapabilityLevel.CAPABILITY_L2_ADULT, 1.0));
        engine.addVote(engine.createVote("p1", VoteDecision.VOTE_NO, CapabilityLevel.CAPABILITY_L4_EXPERT, 1.0));
        
        var result = engine.evaluate(ConsensusProposal.newBuilder().setProposalId("p1").build());
        
        assertEquals(0.5, result.approvalRatio(), 0.001);
    }
    
    @Test
    void testReset() {
        LocalConsensusEngine engine = new LocalConsensusEngine(42L);
        
        engine.addVote(engine.createVote("p1", VoteDecision.VOTE_YES, CapabilityLevel.CAPABILITY_L2_ADULT, 1.0));
        assertEquals(1, engine.getVoteCount());
        
        engine.reset();
        assertEquals(0, engine.getVoteCount());
    }
    
    @Test
    void testThresholds() {
        // Verify standard threshold is 67%
        assertEquals(0.67, LocalConsensusEngine.STANDARD_THRESHOLD, 0.001);
        assertEquals(0.80, LocalConsensusEngine.CRITICAL_THRESHOLD, 0.001);
        assertEquals(0.50, LocalConsensusEngine.EMERGENCY_THRESHOLD, 0.001);
    }
}
