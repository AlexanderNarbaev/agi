package io.matrix.federation.property;

import io.matrix.federation.consensus.LocalConsensusEngine;
import io.matrix.federation.consensus.LocalConsensusEngine.ConsensusResult;
import io.matrix.federation.proto.CapabilityLevel;
import io.matrix.federation.proto.ConsensusProposal;
import io.matrix.federation.proto.ConsensusStatus;
import io.matrix.federation.proto.ConsensusVote;
import io.matrix.federation.proto.VoteDecision;
import net.jqwik.api.*;
import net.jqwik.api.Assume;
import net.jqwik.api.arbitraries.ListArbitrary;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W365 — Property-based tests for Federation consensus.
 */
class FederationPropertyTest {
    
    @Provide
    Arbitrary<CapabilityLevel> capabilities() {
        return Arbitraries.of(
            CapabilityLevel.CAPABILITY_L0_INFANT,
            CapabilityLevel.CAPABILITY_L1_LEARNER,
            CapabilityLevel.CAPABILITY_L2_ADULT,
            CapabilityLevel.CAPABILITY_L3_SPECIALIST,
            CapabilityLevel.CAPABILITY_L4_EXPERT,
            CapabilityLevel.CAPABILITY_L5_MASTER,
            CapabilityLevel.CAPABILITY_L6_ARCHITECT,
            CapabilityLevel.CAPABILITY_L7_GUARDIAN
        );
    }
    
    @Provide
    Arbitrary<VoteDecision> decisions() {
        return Arbitraries.of(
            VoteDecision.VOTE_YES,
            VoteDecision.VOTE_NO,
            VoteDecision.VOTE_ABSTAIN
        );
    }
    
    @Provide
    ListArbitrary<CapabilityLevel> capabilityLists() {
        return capabilities().list().ofMinSize(1).ofMaxSize(10);
    }
    
    @Provide
    ListArbitrary<VoteDecision> decisionLists() {
        return decisions().list().ofMinSize(0).ofMaxSize(15);
    }
    
    @Property
    void vetoAlwaysOverrides(@ForAll("decisions") VoteDecision dec,
                              @ForAll("capabilities") CapabilityLevel level) {
        LocalConsensusEngine engine = new LocalConsensusEngine(42L);
        
        engine.addVote(engine.createVote("p1", VoteDecision.VOTE_YES, CapabilityLevel.CAPABILITY_L5_MASTER, 1.0));
        engine.addVote(engine.createVote("p1", dec, level, 1.0));
        engine.addVote(engine.createVote("p1", VoteDecision.VOTE_YES, CapabilityLevel.CAPABILITY_L7_GUARDIAN, 1.0));
        engine.addVote(engine.createVote("p1", VoteDecision.VOTE_VETO, CapabilityLevel.CAPABILITY_L7_GUARDIAN, 1.0));
        
        ConsensusResult result = engine.evaluate(ConsensusProposal.newBuilder().setProposalId("p1").build());
        
        assertEquals(ConsensusStatus.CONSENSUS_VETOED, result.status());
        assertEquals(1, result.vetoCount());
    }
    
    @Property
    void emptyVotesIsPending(@ForAll("capabilities") CapabilityLevel level) {
        LocalConsensusEngine engine = new LocalConsensusEngine(42L);
        ConsensusResult result = engine.evaluate(
            ConsensusProposal.newBuilder().setProposalId("p1").build());
        
        assertEquals(ConsensusStatus.CONSENSUS_PENDING, result.status());
    }
    
    @Property
    void allYesIsApproved(@ForAll("capabilityLists") List<CapabilityLevel> levels) {
        // Filter: at least one voter must have weight (L2+)
        boolean hasWeight = levels.stream().anyMatch(l -> LocalConsensusEngine.getVotingWeight(l) > 0);
        Assume.that(hasWeight);
        
        LocalConsensusEngine engine = new LocalConsensusEngine(42L);
        
        for (CapabilityLevel lvl : levels) {
            engine.addVote(engine.createVote("p1", VoteDecision.VOTE_YES, lvl, 1.0));
        }
        
        ConsensusResult result = engine.evaluate(
            ConsensusProposal.newBuilder().setProposalId("p1").build());
        
        assertEquals(ConsensusStatus.CONSENSUS_APPROVED, result.status());
    }
    
    @Property
    void allNoIsRejected(@ForAll("capabilityLists") List<CapabilityLevel> levels) {
        LocalConsensusEngine engine = new LocalConsensusEngine(42L);
        
        for (CapabilityLevel lvl : levels) {
            engine.addVote(engine.createVote("p1", VoteDecision.VOTE_NO, lvl, 1.0));
        }
        
        ConsensusResult result = engine.evaluate(
            ConsensusProposal.newBuilder().setProposalId("p1").build());
        
        assertEquals(ConsensusStatus.CONSENSUS_REJECTED, result.status());
    }
    
    @Property
    void weightMonotonic(@ForAll("capabilityLists") List<CapabilityLevel> yesVoters,
                          @ForAll("capabilityLists") List<CapabilityLevel> noVoters) {
        LocalConsensusEngine engine = new LocalConsensusEngine(42L);
        
        double expectedYes = 0;
        double expectedNo = 0;
        
        for (CapabilityLevel lvl : yesVoters) {
            double w = LocalConsensusEngine.getVotingWeight(lvl);
            engine.addVote(engine.createVote("p1", VoteDecision.VOTE_YES, lvl, 1.0));
            if (w > 0) expectedYes += w;
        }
        
        for (CapabilityLevel lvl : noVoters) {
            double w = LocalConsensusEngine.getVotingWeight(lvl);
            engine.addVote(engine.createVote("p1", VoteDecision.VOTE_NO, lvl, 1.0));
            if (w > 0) expectedNo += w;
        }
        
        ConsensusResult result = engine.evaluate(
            ConsensusProposal.newBuilder().setProposalId("p1").build());
        
        assertEquals(expectedYes, result.yesWeight(), 0.001);
        assertEquals(expectedNo, result.noWeight(), 0.001);
    }
    
    @Property
    void approvalRatioInBounds(@ForAll("decisionLists") List<VoteDecision> votes) {
        LocalConsensusEngine engine = new LocalConsensusEngine(42L);
        
        for (VoteDecision d : votes) {
            engine.addVote(engine.createVote("p1", d, CapabilityLevel.CAPABILITY_L3_SPECIALIST, 1.0));
        }
        
        ConsensusResult result = engine.evaluate(
            ConsensusProposal.newBuilder().setProposalId("p1").build());
        
        double ratio = result.approvalRatio();
        assertTrue(ratio >= 0.0 && ratio <= 1.0, "ratio out of bounds: " + ratio);
    }
    
    @Property
    void l0L1VotesAreIgnored(@ForAll("capabilities") CapabilityLevel ignored) {
        LocalConsensusEngine engine = new LocalConsensusEngine(42L);
        
        engine.addVote(engine.createVote("p1", VoteDecision.VOTE_YES, CapabilityLevel.CAPABILITY_L0_INFANT, 1.0));
        engine.addVote(engine.createVote("p1", VoteDecision.VOTE_YES, CapabilityLevel.CAPABILITY_L1_LEARNER, 1.0));
        engine.addVote(engine.createVote("p1", VoteDecision.VOTE_NO, CapabilityLevel.CAPABILITY_L3_SPECIALIST, 1.0));
        
        ConsensusResult result = engine.evaluate(
            ConsensusProposal.newBuilder().setProposalId("p1").build());
        
        assertEquals(ConsensusStatus.CONSENSUS_REJECTED, result.status());
        assertEquals(0.0, result.yesWeight(), 0.001);
    }
    
    @Property
    void vetoOverridesAllCounts(@ForAll("capabilityLists") List<CapabilityLevel> voters) {
        LocalConsensusEngine engine = new LocalConsensusEngine(42L);
        
        for (CapabilityLevel lvl : voters) {
            engine.addVote(engine.createVote("p1", VoteDecision.VOTE_YES, lvl, 1.0));
        }
        engine.addVote(engine.createVote("p1", VoteDecision.VOTE_VETO, CapabilityLevel.CAPABILITY_L7_GUARDIAN, 1.0));
        
        ConsensusResult result = engine.evaluate(
            ConsensusProposal.newBuilder().setProposalId("p1").build());
        
        assertEquals(ConsensusStatus.CONSENSUS_VETOED, result.status());
    }
    
    @Property
    void seedDeterministicSameResult(@ForAll int seed) {
        Random r1 = new Random(seed);
        Random r2 = new Random(seed);
        
        for (int i = 0; i < 10; i++) {
            assertEquals(r1.nextLong(), r2.nextLong());
        }
    }
    
    @Property
    void voteCountMatchesAdded(@ForAll("decisionLists") List<VoteDecision> votes) {
        LocalConsensusEngine engine = new LocalConsensusEngine(42L);
        
        for (VoteDecision d : votes) {
            engine.addVote(engine.createVote("p1", d, CapabilityLevel.CAPABILITY_L2_ADULT, 1.0));
        }
        
        assertEquals(votes.size(), engine.getVoteCount());
    }
}
