package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W570 — Tests for Capability Consensus Engine.
 */
class CapabilityConsensusTest {

    @Test
    void testSimpleMajorityApproval() {
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();
        CapabilityConsensusEngine engine = new CapabilityConsensusEngine(assigner);

        // Register 5 Adult nodes
        for (long i = 1; i <= 5; i++) {
            assigner.registerNode(i);
            assigner.forceTransition(i, NodeRole.ADULT, "test");
            assigner.recordVote(i, true); // Give them accuracy
        }

        String proposalId = engine.createProposal(System.nanoTime() + 1_000_000_000L);

        // 4 YES, 1 NO → 80%% > 67%% threshold
        for (long i = 1; i <= 4; i++) {
            engine.vote(proposalId, i, CapabilityConsensusEngine.VoteDecision.YES, 0.9, "test");
        }
        
        engine.vote(proposalId, 5, CapabilityConsensusEngine.VoteDecision.NO, 0.9, "test");

        CapabilityConsensusEngine.ConsensusResult result = engine.evaluate(proposalId);
        assertEquals(CapabilityConsensusEngine.ConsensusStatus.APPROVED, result.status());
        assertTrue(result.yesWeight() > result.noWeight());
    }

    @Test
    void testRejection() {
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();
        CapabilityConsensusEngine engine = new CapabilityConsensusEngine(assigner);

        for (long i = 1; i <= 5; i++) {
            assigner.registerNode(i);
            assigner.forceTransition(i, NodeRole.ADULT, "test");
            assigner.recordVote(i, true);
        }

        String proposalId = engine.createProposal(System.nanoTime() + 1_000_000_000L);

        // 1 YES, 2 NO
        for (long i = 1; i <= 4; i++) {
            engine.vote(proposalId, i, CapabilityConsensusEngine.VoteDecision.YES, 0.9, "test");
        }
        engine.vote(proposalId, 2, CapabilityConsensusEngine.VoteDecision.NO, 0.9, "test");
        engine.vote(proposalId, 5, CapabilityConsensusEngine.VoteDecision.NO, 0.9, "test");

        CapabilityConsensusEngine.ConsensusResult result = engine.evaluate(proposalId);
        assertEquals(CapabilityConsensusEngine.ConsensusStatus.REJECTED, result.status());
    }

    @Test
    void testGuardianVeto() {
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();
        CapabilityConsensusEngine engine = new CapabilityConsensusEngine(assigner);

        assigner.registerNode(1);
        assigner.forceTransition(1, NodeRole.GUARDIAN, "test");
        assigner.recordVote(1, true);

        for (long i = 2; i <= 5; i++) {
            assigner.registerNode(i);
            assigner.forceTransition(i, NodeRole.ADULT, "test");
            assigner.recordVote(i, true);
        }

        String proposalId = engine.createProposal(System.nanoTime() + 1_000_000_000L);

        // 4 YES from Adults, 1 VETO from Guardian
        for (long i = 2; i <= 5; i++) {
            engine.vote(proposalId, i, CapabilityConsensusEngine.VoteDecision.YES, 0.9, "test");
        }
        engine.vote(proposalId, 1, CapabilityConsensusEngine.VoteDecision.VETO, 1.0, "ethics");

        CapabilityConsensusEngine.ConsensusResult result = engine.evaluate(proposalId);
        assertEquals(CapabilityConsensusEngine.ConsensusStatus.VETOED, result.status());
    }

    @Test
    void testInfantCannotVote() {
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();
        CapabilityConsensusEngine engine = new CapabilityConsensusEngine(assigner);

        assigner.registerNode(1);
        // Node 1 is INFANT by default

        String proposalId = engine.createProposal(System.nanoTime() + 1_000_000_000L);
        assertFalse(engine.vote(proposalId, 1, CapabilityConsensusEngine.VoteDecision.YES, 0.9, "test"));
    }

    @Test
    void testLearnerCannotVote() {
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();
        CapabilityConsensusEngine engine = new CapabilityConsensusEngine(assigner);

        assigner.registerNode(1);
        assigner.forceTransition(1, NodeRole.LEARNER, "test");

        String proposalId = engine.createProposal(System.nanoTime() + 1_000_000_000L);
        assertFalse(engine.vote(proposalId, 1, CapabilityConsensusEngine.VoteDecision.YES, 0.9, "test"));
    }

    @Test
    void testOnlyGuardianCanVeto() {
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();
        CapabilityConsensusEngine engine = new CapabilityConsensusEngine(assigner);

        assigner.registerNode(1);
        assigner.forceTransition(1, NodeRole.ADULT, "test");
        assigner.recordVote(1, true);

        String proposalId = engine.createProposal(System.nanoTime() + 1_000_000_000L);
        assertFalse(engine.vote(proposalId, 1, CapabilityConsensusEngine.VoteDecision.VETO, 1.0, "test"));
    }

    @Test
    void testAbstainDoesNotCount() {
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();
        CapabilityConsensusEngine engine = new CapabilityConsensusEngine(assigner);

        for (long i = 1; i <= 5; i++) {
            assigner.registerNode(i);
            assigner.forceTransition(i, NodeRole.ADULT, "test");
            assigner.recordVote(i, true);
        }

        String proposalId = engine.createProposal(System.nanoTime() + 1_000_000_000L);

        for (long i = 1; i <= 4; i++) {
            engine.vote(proposalId, i, CapabilityConsensusEngine.VoteDecision.YES, 0.9, "test");
        }
        engine.vote(proposalId, 2, CapabilityConsensusEngine.VoteDecision.ABSTAIN, 0.5, "test");
        engine.vote(proposalId, 5, CapabilityConsensusEngine.VoteDecision.NO, 0.9, "test");

        CapabilityConsensusEngine.ConsensusResult result = engine.evaluate(proposalId);
        // 1 YES vs 1 NO, should be rejected (below 2/3 threshold)
        assertEquals(CapabilityConsensusEngine.ConsensusStatus.REJECTED, result.status());
    }

    @Test
    void testResultLog() {
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();
        CapabilityConsensusEngine engine = new CapabilityConsensusEngine(assigner);

        assigner.registerNode(1);
        assigner.forceTransition(1, NodeRole.ADULT, "test");
        assigner.recordVote(1, true);

        String p1 = engine.createProposal(System.nanoTime() + 1_000_000_000L);
        engine.vote(p1, 1, CapabilityConsensusEngine.VoteDecision.YES, 0.9, "test");
        engine.evaluate(p1);

        String p2 = engine.createProposal(System.nanoTime() + 1_000_000_000L);
        engine.vote(p2, 1, CapabilityConsensusEngine.VoteDecision.NO, 0.9, "test");
        engine.evaluate(p2);

        assertEquals(2, engine.getResultLog().size());
    }
}
